import {CompilerHostError, CompilerWorkerHost, type RunOptions, type CancelDisposition, type WorkerHostOptions} from './worker-host';
import type * as D from './compiler-protocol';
export * from './compiler-protocol';
export {CompilerHostError, CompilerWorkerHost};
export type {RunOptions, CancelDisposition, WorkerPort, WorkerHostOptions} from './worker-host';

interface Source {text: string; revision: string;}
interface Schema {raw: string; snapshot: string;}
interface Mirror {
  handle: D.SessionHandle; serverId: string; revision: string;
  sources: Map<string, Source>; configuration: D.WorkspaceConfigurationDto;
  limits: D.SessionLimits; fingerprint: string;
  snapshots: Map<string, string>; schemas: Map<string, Schema>;
}
interface Opened {sessionId: string; workspaceRevision: string; sourceRevisions: Record<string,string>; limits: D.SessionLimits; configurationFingerprint: string;}
let clientSequence=0;
export interface ClientOptions extends Omit<WorkerHostOptions, 'restore' | 'restored'> {
  onRestored?: (sessions: readonly {readonly previous: D.SessionHandle; readonly current: D.SessionHandle}[]) => void;
}
function copy<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T; }
function freeze<T>(value: T): T {
  if (value && typeof value === 'object') { Object.freeze(value); for (const child of Object.values(value)) freeze(child); }
  return value;
}


/** Confirmed host mirror. Every semantic value still comes from the shared compiler. */
export class CompilerClient {
  private readonly namespace='client-'+(++clientSequence)+'-'+Math.random().toString(36).slice(2);
  private readonly host: CompilerWorkerHost;
  private sessions = new Map<string, Mirror>();
  private counter = 0;
  private disposed = false;
  private readonly closed=new WeakSet<D.SessionHandle>();
  private readonly closing=new WeakMap<D.SessionHandle,Promise<void>>();
  private restoredSessions:readonly {readonly previous:D.SessionHandle;readonly current:D.SessionHandle}[]=[];
  constructor(private readonly options: ClientOptions = {}) {
    this.host = new CompilerWorkerHost({...options, restore: () => this.restore(),restored:() => {
      const sessions=this.restoredSessions;this.restoredSessions=[];this.options.onRestored?.(sessions);
    }});
  }
  currentSessions(): readonly D.SessionHandle[] { return Object.freeze([...this.sessions.values()].map(m => m.handle)); }
  state(handle: D.SessionHandle): D.SessionState {
    const m = this.require(handle);
    return freeze({workspaceRevision:m.revision, sourceRevisions:Object.fromEntries([...m.sources].map(([id,s]) => [id,s.revision])),
      limits:copy(m.limits), configurationFingerprint:m.fingerprint});
  }
  sourceText(handle: D.SessionHandle, sourceId: string): string | undefined { return this.require(handle).sources.get(sourceId)?.text; }

  async openSession(options: D.SessionOptions = {}): Promise<D.SessionHandle> {
    if (this.disposed) throw new CompilerHostError('DISPOSED', 'compiler client is disposed');
    const captured = copy(options);
    const opened = await this.send<Opened>('openSession',undefined,captured);
    const mirror = this.makeMirror(opened,captured.configuration ?? {});
    for (const source of captured.sources ?? []) {
      const value = opened.sourceRevisions[source.sourceId];
      if (value === undefined) throw this.invalidRuntime('missing initial source revision');
      mirror.sources.set(source.sourceId,{text:source.text,revision:this.revision(value)});
    }
    this.sessions.set(mirror.handle.id,mirror);
    return mirror.handle;
  }
  async updateSource(handle: D.SessionHandle, update: D.SourceUpdateDto): Promise<D.UpdateReceiptDto> {
    const m=this.require(handle), captured=copy(update);
    return this.send<D.UpdateReceiptDto>('updateSource',m,captured).then(receipt => {
      this.require(handle);this.checkPrevious(m,captured.sourceId,receipt);this.apply(m,captured.sourceId,this.materialize(m,captured),receipt);return freeze(receipt);
    });
  }
  async updateSources(handle: D.SessionHandle, request: D.BatchSourceUpdateDto): Promise<D.BatchUpdateReceiptDto> {
    const m=this.require(handle),captured=copy(request);
    return this.send<D.BatchUpdateReceiptDto>('updateSources',m,captured).then(receipt => {
      this.require(handle);
      if(receipt.updates.length!==captured.updates.length) throw this.invalidRuntime('incomplete batch receipt');
      const workspaceRevision=this.revision(receipt.workspaceRevision);
      if(receipt.previousWorkspaceRevision!==m.revision)throw this.invalidRuntime('batch receipt has wrong previous workspace revision');
      receipt.updates.forEach((value,i) => {
        this.checkPrevious(m,captured.updates[i].sourceId,value);
        if(value.sourceRevision===null)throw this.invalidRuntime('batch update has no source revision');
        this.revision(value.sourceRevision);this.revision(value.workspaceRevision);
      });
      const texts=captured.updates.map(update => this.materialize(m,update));
      receipt.updates.forEach((value,i) => this.apply(m,captured.updates[i].sourceId,texts[i],value));
      m.revision=workspaceRevision;return freeze(receipt);
    });
  }
  async removeSource(handle: D.SessionHandle, request: D.RemoveSourceDto): Promise<D.UpdateReceiptDto> {
    const m=this.require(handle),captured=copy(request);
    return this.send<D.UpdateReceiptDto>('removeSource',m,captured).then(receipt => {
      this.require(handle);this.checkPrevious(m,captured.sourceId,receipt);if(receipt.sourceId!==captured.sourceId || receipt.sourceRevision!==null) throw this.invalidRuntime('invalid removal receipt');
      const workspaceRevision=this.revision(receipt.workspaceRevision);
      m.sources.delete(captured.sourceId);m.revision=workspaceRevision;return freeze(receipt);
    });
  }
  async setConfiguration(handle: D.SessionHandle, request: D.SetConfigurationDto): Promise<D.ConfigurationReceiptDto> {
    const m=this.require(handle),captured=copy(request);
    return this.send<D.ConfigurationReceiptDto>('setConfiguration',m,captured).then(receipt => {
      this.require(handle);const workspaceRevision=this.revision(receipt.workspaceRevision);
      if(receipt.previousWorkspaceRevision!==m.revision||typeof receipt.configurationFingerprint!=='string')throw this.invalidRuntime('configuration receipt does not match confirmed state');
      m.configuration=captured.configuration;m.fingerprint=receipt.configurationFingerprint;
      m.revision=workspaceRevision;return freeze(receipt);
    });
  }
  async analyze(handle: D.SessionHandle, request: D.AnalysisRequestDto, run?: RunOptions): Promise<D.AnalysisResultDto> {
    const m=this.require(handle);
    return this.send<D.AnalysisResultDto>('analyze',m,copy(request),run).then(result => {
      this.require(handle);return this.result(m,result);
    });
  }
  async compile(handle: D.SessionHandle, request: D.CompileRequestDto, run?: RunOptions): Promise<D.CompilationResultDto> {
    const m=this.require(handle);
    return this.send<D.CompilationResultDto>('compile',m,copy(request),run).then(result => {
      this.require(handle);return this.result(m,result);
    });
  }
  async query(handle: D.SessionHandle, request: D.QueryRequestDto, run?: RunOptions): Promise<D.QueryResultDto> {
    const m=this.require(handle),captured=copy(request);
    const raw=this.snapshot(m,captured.snapshotId);
    return this.send<D.QueryResultDto>('query',m,{...captured,snapshotId:raw},run).then(result => {
      this.require(handle);return this.result(m,result);
    });
  }
  async diagram(handle: D.SessionHandle, request: D.DiagramRequestDto, run?: RunOptions): Promise<D.DiagramDocumentDto> {
    const m=this.require(handle),captured=copy(request);
    const params: {snapshotId?:string;schemaId?:string;selection?:{snapshot:string;symbol:string}[];options?:D.DiagramOptionsDto}={};
    if(captured.snapshotId)params.snapshotId=this.snapshot(m,captured.snapshotId);
    if(captured.schemaId) {
      const schema=m.schemas.get(captured.schemaId);if(!schema)throw new CompilerHostError('SNAPSHOT_EXPIRED','schema handle expired');
      params.schemaId=schema.raw;
    }
    if(captured.options)params.options={...captured.options,...(captured.options.translationModel?{translationModel:{...captured.options.translationModel,snapshot:this.snapshot(m,captured.options.translationModel.snapshot)}}:{})};
    if(captured.selection)params.selection=captured.selection.map(ref => ({snapshot:this.snapshot(m,ref.snapshot),symbol:ref.symbol}));
    return this.send<D.DiagramDocumentDto>('diagram',m,params,run).then(result => {
      this.require(handle);return this.result(m,result);
    });
  }
  async releaseSnapshot(handle: D.SessionHandle, snapshotId: string): Promise<void> {
    const m=this.require(handle),raw=m.snapshots.get(snapshotId);
    if(raw===undefined) {
      if(!snapshotId.startsWith(m.handle.id+'/snapshot/'))throw new CompilerHostError('SNAPSHOT_EXPIRED','foreign snapshot handle');
      return;
    }
    await this.send('releaseSnapshot',m,{snapshotId:raw});this.require(handle);
    m.snapshots.delete(snapshotId);for(const [id,schema] of m.schemas)if(schema.snapshot===snapshotId)m.schemas.delete(id);
  }
  async closeSession(handle:D.SessionHandle):Promise<void> {
    if(this.disposed)throw new CompilerHostError('DISPOSED','compiler client is disposed');
    if(this.closed.has(handle))return;
    const previous=this.closing.get(handle);if(previous)return previous;
    const m=this.require(handle);
    const close=(async()=>{
      await this.send('closeSession',m,{});this.require(handle);
      this.sessions.delete(handle.id);m.sources.clear();m.schemas.clear();m.snapshots.clear();this.closed.add(handle);
    })().finally(()=>this.closing.delete(handle));
    this.closing.set(handle,close);return close;
  }

  cancel(requestId: string): Promise<CancelDisposition> { return this.host.cancel(requestId); }
  restart(): Promise<void> { return this.host.restart(); }
  async dispose(): Promise<void> { this.disposed=true;await this.host.dispose();this.sessions.clear(); }

  private invalidRuntime(message:string):CompilerHostError {
    const error=new CompilerHostError('RUNTIME_FAILURE',message);
    try{this.options.onFailure?.(error);}catch{/* disconnected observer */}
    void this.host.restart('RUNTIME_FAILURE').catch(()=>{});return error;
  }
  private revision(value:string):string {
    if(typeof value!=='string'||!/^(0|[1-9][0-9]*)$/.test(value)||value.length>19||BigInt(value)>9223372036854775807n)throw this.invalidRuntime('invalid revision in compiler response');
    return value;
  }
  private require(handle: D.SessionHandle): Mirror {
    if(this.disposed)throw new CompilerHostError('DISPOSED','compiler client is disposed');
    const m=this.sessions.get(handle.id);
    if(!m || m.handle!==handle || handle.epoch!==this.host.epoch)throw new CompilerHostError('SESSION_EXPIRED','session handle belongs to an expired worker epoch');
    return m;
  }
  private makeMirror(opened: Opened, configuration: D.WorkspaceConfigurationDto): Mirror {
    if(typeof opened.sessionId!=='string' || !opened.limits || typeof opened.configurationFingerprint!=='string')throw this.invalidRuntime('invalid session opening response');
    return {handle:Object.freeze({id:`${this.namespace}-e${this.host.epoch}-s${++this.counter}`,epoch:this.host.epoch}),serverId:opened.sessionId,
      revision:this.revision(opened.workspaceRevision),sources:new Map(),configuration:copy(configuration),limits:copy(opened.limits),fingerprint:opened.configurationFingerprint,snapshots:new Map(),schemas:new Map()};
  }
  private send<T extends object>(operation: string,m: Mirror | undefined,params: object,run: RunOptions = {},restoring=false): Promise<T> {
    const request={protocol:'interlis.compiler' as const,protocolVersion:'1.0' as const,requestId:run.requestId ?? this.host.requestId(),operation,params,...(m?{sessionId:m.serverId}:{})};
    const openingLimit=operation==='openSession'?(params as D.SessionOptions).limits?.maxMessageUtf16Length:undefined;
    const limit=m?.limits.maxMessageUtf16Length ?? (openingLimit && Number.isSafeInteger(openingLimit) && openingLimit>0?Math.min(openingLimit,16777216):4194304);
    if(JSON.stringify(request).length>limit)return Promise.reject(new CompilerHostError('LIMIT_EXCEEDED','session message UTF-16 limit reached'));
    return this.host.dispatch<{status:string;result?:T;error?:{code:string;messageKey:string;details?:object};cancellation?:object}>(request,run,restoring).then(response => {
      if(response.status==='error')throw new CompilerHostError(response.error?.code ?? 'RUNTIME_FAILURE',response.error?.messageKey ?? 'invalid compiler error',response.error?.details);
      if(response.status==='cancelled')throw new CompilerHostError('CANCELLED','compiler operation cancelled',response.cancellation);
      if(!response.result || typeof response.result!=='object')throw this.invalidRuntime('missing compiler result');
      return response.result;
    });
  }
  private materialize(m: Mirror,update: D.SourceUpdateDto): string {
    if(update.change.kind==='replace')return update.change.text;
    const source=m.sources.get(update.sourceId);if(!source)throw new CompilerHostError('VERSION_CONFLICT','source is missing');
    const edits=[...update.change.edits].sort((a,b) => b.startOffset-a.startOffset || b.endOffset-a.endOffset);
    let text=source.text,following=source.text.length;
    for(const edit of edits) {
      if(!Number.isSafeInteger(edit.startOffset) || !Number.isSafeInteger(edit.endOffset) || edit.startOffset<0 || edit.endOffset<edit.startOffset || edit.endOffset>following)
        throw new CompilerHostError('INVALID_REQUEST','invalid or overlapping UTF-16 edits');
      text=text.slice(0,edit.startOffset)+edit.text+text.slice(edit.endOffset);following=edit.startOffset;
    }
    return text;
  }
  private apply(m: Mirror,id: string,text: string,receipt: D.UpdateReceiptDto): void {
    if(receipt.sourceId!==id || receipt.sourceRevision===null)throw this.invalidRuntime('invalid source update receipt');
    const sourceRevision=this.revision(receipt.sourceRevision),workspaceRevision=this.revision(receipt.workspaceRevision);
    m.sources.set(id,{text,revision:sourceRevision});m.revision=workspaceRevision;
  }
  private checkPrevious(m: Mirror,id: string,receipt: D.UpdateReceiptDto): void {
    if(receipt.previousWorkspaceRevision!==m.revision || receipt.sourceId!==id || receipt.previousSourceRevision!==(m.sources.get(id)?.revision ?? null))
      throw this.invalidRuntime('source receipt does not match the confirmed host state');
  }
  private snapshot(m: Mirror,handle: string): string {
    const raw=m.snapshots.get(handle);if(raw===undefined)throw new CompilerHostError('SNAPSHOT_EXPIRED','snapshot handle expired');return raw;
  }
  private publicSnapshot(m: Mirror,raw: string): string {
    if(m.snapshots.has(raw))return raw;
    const id=m.handle.id+'/snapshot/'+raw;m.snapshots.set(id,raw);return id;
  }
  private result<T extends object>(m: Mirror,value: T): T {
    const result=copy(value) as T & {snapshotId?:string;schemaId?:string};
    if(result.snapshotId) {
      result.snapshotId=this.publicSnapshot(m,result.snapshotId);
      if(result.schemaId) {const raw=result.schemaId;result.schemaId=m.handle.id+'/schema/'+raw;m.schemas.set(result.schemaId,{raw,snapshot:result.snapshotId});}
    }
    const refs=(value: unknown): void => {
      if(!value || typeof value!=='object')return;
      if(Array.isArray(value)){value.forEach(refs);return;}
      const record=value as Record<string,unknown>;
      if(typeof record.snapshot==='string' && typeof record.symbol==='string') record.snapshot=this.publicSnapshot(m,record.snapshot);
      if(typeof record.snapshotId==='string' && record.nameRange && typeof record.nameRange==='object')record.snapshotId=this.publicSnapshot(m,record.snapshotId);
      Object.values(record).forEach(refs);
    };
    refs(result);return freeze(result);
  }

  private async restore(): Promise<void> {
    const old=[...this.sessions.values()],restored=new Map<string,Mirror>();
    const replacements: {previous:D.SessionHandle;current:D.SessionHandle}[]=[];
    for(const previous of old) {
      const opened=await this.send<Opened>('openSession',undefined,{configuration:previous.configuration,limits:previous.limits},{},true);
      const current=this.makeMirror(opened,previous.configuration);
      for(const [sourceId,source] of previous.sources)await this.restoreSource(current,sourceId,source.text);
      restored.set(current.handle.id,current);replacements.push({previous:previous.handle,current:current.handle});
    }
    if(this.disposed)return;
    this.sessions=restored;
    this.restoredSessions=freeze(replacements);
  }
  private async restoreSource(m: Mirror,sourceId: string,text: string): Promise<void> {
    const send=async(update:D.SourceUpdateDto) => {
      const receipt=await this.send<D.UpdateReceiptDto>('updateSource',m,update,{},true);
      this.checkPrevious(m,sourceId,receipt);
      const next=update.change.kind==='replace'?update.change.text:this.materialize(m,update);this.apply(m,sourceId,next,receipt);
    };
    const replacement:D.SourceUpdateDto={sourceId,expectation:{kind:'absent'},change:{kind:'replace',text}};
    const estimate=(params:object)=>JSON.stringify({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'x'.repeat(80),operation:'updateSource',sessionId:m.serverId,params}).length;
    if(estimate(replacement)<=m.limits.maxMessageUtf16Length){await send(replacement);return;}
    await send({...replacement,change:{kind:'replace',text:''}});
    let offset=0;
    while(offset<text.length) {
      let size=Math.min(262144,text.length-offset),update:D.SourceUpdateDto;
      for(;;) {
        const source=m.sources.get(sourceId)!;
        update={sourceId,expectation:{kind:'at',revision:source.revision},change:{kind:'edits',edits:[{startOffset:source.text.length,endOffset:source.text.length,text:text.slice(offset,offset+size)}]}};
        if(estimate(update)<=m.limits.maxMessageUtf16Length)break;
        if(size===1)throw new CompilerHostError('LIMIT_EXCEEDED','session message limit cannot replay its source');
        size=Math.max(1,Math.floor(size/2));
      }
      await send(update);offset+=size;
    }
  }
}
