/** A serialized, bounded transport. No compiler or model semantics live here. */
export interface WorkerPort {
  onmessage: ((event: MessageEvent) => void) | null;
  onerror: ((event: ErrorEvent) => void) | null;
  postMessage(message: object): void;
  terminate(): void;
}
export interface RunOptions { requestId?: string; signal?: AbortSignal; }
export type CancelDisposition = 'notStarted' | 'cooperative' | 'workerRestarted' | 'alreadyCompleted' | 'unknownRequest';
export interface WireRequest {
  protocol: 'interlis.compiler'; protocolVersion: '1.0'; requestId: string;
  operation: string; sessionId?: string; params: object;
}
export class CompilerHostError extends Error {
  constructor(readonly code: string, message: string, readonly details?: object) { super(message); this.name = 'CompilerHostError'; }
}
interface Pending {
  request: WireRequest; epoch: number; resolve: (value: object) => void; reject: (error: Error) => void;
  cleanup: () => void;
}
export interface WorkerHostOptions {
  workerFactory?: () => WorkerPort;
  maxQueuedRequests?: number;
  startupTimeoutMs?: number;
  restore?: () => Promise<void>;
  restored?: () => void;
  onFailure?: (error: CompilerHostError) => void;
}

export class CompilerWorkerHost {
  private worker!: WorkerPort;
  private queue: Pending[] = [];
  private active: Pending | null = null;
  private completed = new Set<string>();
  private ready = false;
  private failed:CompilerHostError|null=null;
  private disposed = false;
  private resolveReady!: () => void;
  private rejectReady!: (error: Error) => void;
  private readyPromise!: Promise<void>;
  private recovery: Promise<void> = Promise.resolve();
  private restarting = false;
  private serial = 0;
  private currentEpoch = 0;
  private readonly factory: () => WorkerPort;
  private readonly maxQueued: number;
  private startupTimer?: ReturnType<typeof setTimeout>;
  constructor(private readonly options: WorkerHostOptions = {}) {
    this.factory = options.workerFactory ?? (() => new Worker(new URL('./worker.js',import.meta.url)));
    this.maxQueued = options.maxQueuedRequests ?? 128;
    if (!Number.isSafeInteger(this.maxQueued) || this.maxQueued < 1 || this.maxQueued > 1024)
      throw new RangeError('maxQueuedRequests must be in 1..1024');
    if (options.startupTimeoutMs !== undefined && (!Number.isSafeInteger(options.startupTimeoutMs)
        || options.startupTimeoutMs < 1 || options.startupTimeoutMs > 120000))
      throw new RangeError('startupTimeoutMs must be in 1..120000');
    this.boot();
  }
  get epoch(): number { return this.currentEpoch; }
  get pendingCount(): number { return this.queue.length + (this.active ? 1 : 0); }
  requestId(): string { return `e${this.currentEpoch}-r${++this.serial}`; }
  settled(): Promise<void> { return this.recovery; }

  dispatch<T extends object>(request: WireRequest, run: RunOptions = {}, restoring = false): Promise<T> {
    if (this.disposed) return Promise.reject(new CompilerHostError('DISPOSED', 'compiler host is disposed'));
    if(this.failed)return Promise.reject(this.failed);
    if (!restoring && this.restarting) return this.recovery.then(() => {
      throw new CompilerHostError('WORKER_RESTARTED', 'request belongs to the previous worker epoch');
    });
    if (run.signal?.aborted) return Promise.reject(new CompilerHostError('CANCELLED', 'request cancelled before dispatch', {reason: 'USER_REQUESTED'}));
    let captured:WireRequest;
    try {
      const encoded=JSON.stringify(request);
      if(encoded.length>16777216)return Promise.reject(new CompilerHostError('LIMIT_EXCEEDED','transport message UTF-16 limit reached'));
      captured=JSON.parse(encoded) as WireRequest;
    }catch{return Promise.reject(new CompilerHostError('INVALID_REQUEST','request is not JSON data'));}
    if (captured.requestId !== request.requestId || !captured.requestId || captured.requestId.length > 1024)
      return Promise.reject(new CompilerHostError('INVALID_REQUEST', 'invalid request identity'));
    if (this.completed.has(captured.requestId) || this.active?.request.requestId === captured.requestId
        || this.queue.some(item => item.request.requestId === captured.requestId))
      return Promise.reject(new CompilerHostError('INVALID_REQUEST', 'request identity already used'));
    if (this.queue.length >= this.maxQueued) return Promise.reject(new CompilerHostError('LIMIT_EXCEEDED', 'worker request queue is full'));
    return new Promise<T>((resolve, reject) => {
      const abort = () => { void this.cancel(captured.requestId).catch(error => this.report(error)); };
      const pending: Pending = {request: captured, epoch: this.currentEpoch,
        resolve: value => resolve(value as T), reject,
        cleanup: () => run.signal?.removeEventListener('abort', abort)};
      this.queue.push(pending);
      run.signal?.addEventListener('abort', abort, {once: true});
      if (run.signal?.aborted) abort();
      this.pump();
    });
  }

  async cancel(requestId: string): Promise<CancelDisposition> {
    const index = this.queue.findIndex(item => item.request.requestId === requestId);
    if (index >= 0) {
      const [pending] = this.queue.splice(index, 1);
      this.finish(pending, undefined, new CompilerHostError('CANCELLED', 'request cancelled before execution', {reason: 'USER_REQUESTED'}));
      return 'notStarted';
    }
    if (this.active?.request.requestId === requestId) {
      await this.restart('USER_REQUESTED');
      return 'workerRestarted';
    }
    return this.completed.has(requestId) ? 'alreadyCompleted' : 'unknownRequest';
  }

  async restart(reason = 'HOST_RESTARTED'): Promise<void> {
    if (this.disposed) throw new CompilerHostError('DISPOSED', 'compiler host is disposed');
    if (this.restarting) return this.recovery;
    this.restarting = true;
    this.stop(new CompilerHostError('CANCELLED', 'worker restarted; previous handles expired', {reason}));
    this.completed.clear();
    this.currentEpoch++;
    this.boot();
    this.recovery = (async () => {
      await this.readyPromise;
      await this.options.restore?.();
      this.restarting=false;
      try {this.options.restored?.();}catch {this.report(new CompilerHostError('HOST_CALLBACK_FAILURE','worker restoration callback failed'));}
    })().catch(error => {
      if(this.disposed)throw new CompilerHostError('CANCELLED','compiler host is closing',{reason:'HOST_SHUTDOWN'});
      this.stop(new CompilerHostError('RUNTIME_FAILURE', 'worker recovery failed'));
      this.report(error);
      throw error;
    }).finally(() => { this.restarting = false; this.pump(); });
    // A host error may initiate recovery without an awaiting caller.
    void this.recovery.catch(() => {});
    return this.recovery;
  }

  async dispose(): Promise<void> {
    if (this.disposed) return;
    this.disposed = true;
    this.stop(new CompilerHostError('CANCELLED', 'compiler host is closing', {reason: 'HOST_SHUTDOWN'}));
    this.completed.clear();
  }

  private boot(): void {
    this.ready = false;this.failed=null;
    this.readyPromise = new Promise<void>((resolve, reject) => { this.resolveReady = resolve; this.rejectReady = reject; });
    void this.readyPromise.catch(() => {});
    const epoch = this.currentEpoch;
    let worker:WorkerPort;
    try{worker=this.factory();}catch{
      const error=new CompilerHostError('RUNTIME_FAILURE','compiler worker could not start');
      this.failed=error;this.rejectReady(error);this.report(error);return;
    }
    this.worker=worker;
    this.startupTimer = setTimeout(() => {
      if (worker === this.worker && epoch === this.currentEpoch && !this.ready && !this.disposed) this.runtimeFailure();
    }, this.options.startupTimeoutMs ?? 30000);
    worker.onmessage = event => {
      if (this.disposed || worker !== this.worker || epoch !== this.currentEpoch) return;
      if(!event.data || typeof event.data!=='object'){this.runtimeFailure();return;}
      const message = event.data as {epoch?: number; kind?: string; requestId?: string; response?: string};
      if (message.epoch !== epoch) return;
      if (message.kind === 'ready') { clearTimeout(this.startupTimer); this.ready = true; this.resolveReady(); this.pump(); return; }
      if (message.kind === 'started') return;
      if (message.kind === 'fatal') { this.runtimeFailure(); return; }
      const pending = this.active;
      if(!pending)return;
      if(message.requestId !== pending.request.requestId || pending.epoch !== epoch){this.runtimeFailure();return;}
      this.active = null;
      try {
        if (typeof message.response !== 'string') throw new Error('missing compiler response');
        const response = JSON.parse(message.response) as {protocol?: string; protocolVersion?: string; requestId?: string; status?: string};
        if (response.protocol !== 'interlis.compiler' || response.protocolVersion !== '1.0'
            || response.requestId !== pending.request.requestId || !['ok','error','cancelled'].includes(response.status ?? ''))
          throw new Error('invalid compiler response envelope');
        this.finish(pending, response);
      } catch {
        this.finish(pending, undefined, new CompilerHostError('RUNTIME_FAILURE', 'invalid worker response'));
        this.runtimeFailure(); return;
      }
      this.pump();
    };
    worker.onerror = () => { if (worker === this.worker && epoch === this.currentEpoch && !this.disposed) this.runtimeFailure(); };
    worker.postMessage({kind: 'bootstrap', epoch});
  }

  private runtimeFailure(): void {
    const error = new CompilerHostError('RUNTIME_FAILURE', 'compiler runtime failed');
    const wasReady = this.ready;
    if (this.active) { const pending = this.active; this.active = null; this.finish(pending, undefined, error); }
    this.report(error);
    if (wasReady && !this.restarting) void this.restart('RUNTIME_FAILURE').catch(() => {});
    else {this.failed=error;this.stop(error);}
  }
  private stop(error: CompilerHostError): void {
    clearTimeout(this.startupTimer);
    this.ready = false;
    this.worker?.terminate(); this.rejectReady?.(error);
    if (this.active) { const pending = this.active; this.active = null; this.finish(pending, undefined, error); }
    for (const pending of this.queue.splice(0)) this.finish(pending, undefined, error);
  }
  private report(error:unknown):void {
    try{this.options.onFailure?.(error instanceof CompilerHostError?error:new CompilerHostError('RUNTIME_FAILURE','compiler host failed'));}catch{/* disconnected observer */}
  }
  private finish(pending: Pending, value?: object, error?: Error): void {
    pending.cleanup();
    this.completed.add(pending.request.requestId);
    if (this.completed.size > 256) this.completed.delete(this.completed.values().next().value!);
    if (error) pending.reject(error); else pending.resolve(value!);
  }
  private pump(): void {
    if (!this.ready || this.disposed || this.active || this.queue.length === 0) return;
    const pending = this.queue.shift()!;
    if (pending.epoch !== this.currentEpoch) { this.finish(pending, undefined, new CompilerHostError('WORKER_RESTARTED', 'stale request epoch')); this.pump(); return; }
    this.active = pending;
    try { this.worker.postMessage({kind: 'request', epoch: this.currentEpoch, requestId: pending.request.requestId, request: JSON.stringify(pending.request)}); }
    catch { this.runtimeFailure(); }
  }
}
