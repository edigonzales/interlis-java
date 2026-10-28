(() => {
  let worker;
  let nextId = 1;
  let workerGeneration = 0;
  const pending = new Map();
  function attach(candidate) {
    const generation = workerGeneration;
    candidate.onmessage = (event) => {
      if (candidate !== worker || generation !== workerGeneration) return;
      const {id, response, error} = event.data;
      const entry = pending.get(id);
      if (!entry) return;
      pending.delete(id);
      if (error) entry.reject(new Error(error));
      else entry.resolve(JSON.parse(response));
    };
  }
  function newWorker() {
    workerGeneration++;
    worker = new Worker('worker.js');
    attach(worker);
    return worker;
  }
  newWorker();
  function call(request) {
    const id = nextId++;
    return new Promise((resolve, reject) => {
      pending.set(id, {resolve, reject});
      worker.postMessage({id, request: JSON.stringify(request)});
    });
  }
  function envelope(operation, sessionId, params) {
    const request = {
      protocol: 'interlis.compiler',
      protocolVersion: '1.0',
      requestId: 'w-' + (nextId++),
      operation,
      params: params || {}
    };
    if (sessionId) request.sessionId = sessionId;
    return request;
  }

  const output = document.getElementById('output');
  const source = document.getElementById('source');
  const state = {sessionId: null, revision: '0', sourceRevision: null, snapshotId: null, schemaId: null, hasSource: false, diagramView: null, benchmarkRevision: null};

  function resetState() {
    state.sessionId = null;
    state.revision = '0';
    state.sourceRevision = null;
    state.snapshotId = null;
    state.schemaId = null;
    state.hasSource = false;
    state.benchmarkRevision = null;
  }

  async function restartWorker() {
    worker.terminate();
    pending.forEach(entry => entry.reject(new Error('worker restarted')));
    pending.clear();
    newWorker();
    resetState();
    await openRevision();
    const compiled = await call(envelope('compile', state.sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName: 'Browser'}]
    }));
    return compiled;
  }

  async function openSession() {
    if (state.sessionId) return state.sessionId;
    const response = await call(envelope('openSession'));
    if (response.status !== 'ok') throw new Error(JSON.stringify(response));
    state.sessionId = response.result.sessionId;
    state.revision = response.result.workspaceRevision;
    return state.sessionId;
  }

  async function discardResult() {
    const snapshotId = state.snapshotId;
    state.snapshotId = null;
    state.schemaId = null;
    state.diagramView = null;
    if (snapshotId && state.sessionId) {
      const response = await call(envelope('releaseSnapshot', state.sessionId, {snapshotId}));
      if (response.status !== 'ok') throw new Error('snapshot release failed');
    }
  }

  async function replaceSource(text) {
    const sessionId = await openSession();
    const response = await call(envelope('updateSource', sessionId, {
      sourceId: 'memory:/browser.ili',
      expectation: {kind: 'at', revision: state.sourceRevision},
      change: {kind: 'replace', text}
    }));
    if (response.status !== 'ok') throw new Error(JSON.stringify(response));
    state.revision = response.result.workspaceRevision;
    state.sourceRevision = response.result.sourceRevision;
    state.hasSource = true;
    await discardResult();
    return response;
  }

  async function openRevision() {
    const sessionId = await openSession();
    const response = await call(envelope('updateSource', sessionId, {
      sourceId: 'memory:/browser.ili',
      expectation: {kind: 'absent'},
      change: {kind: 'replace', text: source.value}
    }));
    if (response.status !== 'ok') throw new Error(JSON.stringify(response));
    state.revision = response.result.workspaceRevision;
    state.sourceRevision = response.result.sourceRevision;
    state.hasSource = true;
    await discardResult();
  }

  async function analyze() {
    const inputText = source.value;
    if (!state.hasSource) await openRevision();
    else await replaceSource(inputText);
    const generation = workerGeneration;
    const revision = state.revision;
    const text = inputText;
    const response = await call(envelope('analyze', state.sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName: 'Browser'}]
    }));
    if (generation !== workerGeneration || revision !== state.revision || text !== source.value) {
      if (generation === workerGeneration && response.result?.snapshotId
          && response.result.snapshotId !== state.snapshotId) {
        await call(envelope('releaseSnapshot', state.sessionId, {snapshotId: response.result.snapshotId}));
      }
      return response;
    }
    if (response.status === 'ok') {
      state.snapshotId = response.result.snapshotId;
      output.textContent = JSON.stringify(response.result, null, 2);
    } else {
      output.textContent = JSON.stringify(response, null, 2);
    }
    return response;
  }

  async function compile() {
    const inputText = source.value;
    if (!state.hasSource) await openRevision();
    else await replaceSource(inputText);
    const generation = workerGeneration;
    const revision = state.revision;
    const text = inputText;
    const response = await call(envelope('compile', state.sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName: 'Browser'}]
    }));
    if (generation !== workerGeneration || revision !== state.revision || text !== source.value) {
      if (generation === workerGeneration && response.result?.snapshotId
          && response.result.snapshotId !== state.snapshotId) {
        await call(envelope('releaseSnapshot', state.sessionId, {snapshotId: response.result.snapshotId}));
      }
      return response;
    }
    if (response.status === 'ok' && response.result.outcome === 'succeeded') {
      state.schemaId = response.result.schemaId;
      state.snapshotId = response.result.snapshotId;
    }
    output.textContent = JSON.stringify(response, null, 2);
    return response;
  }

  async function diagram() {
    if (!state.schemaId) {
      const compiled = await compile();
      if (!compiled.result || compiled.result.outcome !== 'succeeded') return compiled;
    }
    const response = await call(envelope('diagram', state.sessionId, {
      schemaId: state.schemaId
    }));
    if (response.status === 'ok' && window.interlisDiagramAdapter) {
      state.diagramView = window.interlisDiagramAdapter.adapt(response.result);
    }
    output.textContent = JSON.stringify(response, null, 2);
    return response;
  }

  async function benchmark(text, modelName) {
    const sessionId = await openSession();
    const sourceId = 'memory:/benchmark.ili';
    const updated = await call(envelope('updateSource', sessionId, {
      sourceId,
      expectation: state.benchmarkRevision === null ? {kind: 'absent'}
        : {kind: 'at', revision: state.benchmarkRevision},
      change: {kind: 'replace', text}
    }));
    if (updated.status !== 'ok') throw new Error('benchmark update failed: ' + JSON.stringify(updated));
    state.benchmarkRevision = updated.result.sourceRevision;
    state.revision = updated.result.workspaceRevision;
    const analyzed = await call(envelope('analyze', sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName}]
    }));
    if (analyzed.status !== 'ok') throw new Error('benchmark analyze failed: ' + JSON.stringify(analyzed));
    const started = performance.now();
    const compiled = await call(envelope('compile', sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName}]
    }));
    const elapsed = performance.now() - started;
    if (compiled.status !== 'ok' || compiled.result.outcome !== 'succeeded') {
      throw new Error('benchmark compile failed: ' + JSON.stringify(compiled));
    }
    return elapsed;
  }

  async function runContract() {
    const capabilities = await call(envelope('capabilities'));
    if (capabilities.status !== 'ok') throw new Error('capabilities failed');
    const sessionId = await openSession();
    await openRevision();
    const analyzed = await call(envelope('analyze', sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName: 'Browser'}]
    }));
    if (analyzed.status !== 'ok') throw new Error('analyze failed: ' + JSON.stringify(analyzed));
    const broken = source.value.replace('height : 0..999;', 'height : ;');
    await replaceSource(broken);
    const rejected = await call(envelope('compile', sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName: 'Browser'}]
    }));
    if (rejected.status !== 'ok' || rejected.result.outcome !== 'rejected') {
      throw new Error('invalid model was not rejected: ' + JSON.stringify(rejected));
    }
    await replaceSource(source.value);
    const succeeded = await call(envelope('compile', sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName: 'Browser'}]
    }));
    if (succeeded.status !== 'ok' || succeeded.result.outcome !== 'succeeded') {
      throw new Error('compile failed: ' + JSON.stringify(succeeded));
    }
    const diagram = await call(envelope('diagram', sessionId, {
      schemaId: succeeded.result.schemaId
    }));
    if (diagram.status !== 'ok' || !diagram.result.nodes || diagram.result.nodes.length === 0) {
      throw new Error('diagram failed');
    }
    const diagramText = JSON.stringify(diagram);
    if (!diagramText.includes('9007199254740993.0010')) {
      throw new Error('exact decimal literal missing from diagram');
    }
    if (!window.interlisDiagramAdapter) throw new Error('diagram TypeScript adapter missing');
    const diagramView = window.interlisDiagramAdapter.adapt(diagram.result);
    if (diagramView.snapshotId !== diagram.result.snapshotId) {
      throw new Error('diagram adapter snapshot mismatch');
    }
    if (window.interlisDiagramAdapter.isStale(diagramView, 'stale-snapshot')) {
      // expected stale; no-op branch keeps the check explicit
    } else {
      throw new Error('diagram adapter stale check failed');
    }
    const restarted = await restartWorker();
    if (restarted.status !== 'ok' || restarted.result.outcome !== 'succeeded') {
      throw new Error('compile after restart failed: ' + JSON.stringify(restarted));
    }
    const stabilizationCases = await window.verifyInterlisStabilization({
      handle: async request => JSON.stringify(await call(JSON.parse(request)))
    });
    return {
      status: 'ok',
      stabilizationCases,
      capabilities: capabilities.result.toolVersion,
      analyzeRevision: state.revision,
      rejectedOutcome: rejected.result ? rejected.result.outcome : rejected.status,
      compiledOutcome: succeeded.result.outcome,
      diagramNodes: diagram.result.nodes.length,
      diagramEdges: diagramView.edges.length,
      diagramAssociations: diagramView.associations.size,
      diagnostics: succeeded.result.diagnostics.length,
      exactDecimal: true,
      workerRestartOutcome: restarted.result ? restarted.result.outcome : restarted.status
    };
  }

  source.addEventListener('input', () => { discardResult().catch(error => { output.textContent = String(error); }); });

  document.getElementById('analyze').onclick = () => analyze();
  document.getElementById('compile').onclick = () => compile();
  document.getElementById('diagram').onclick = () => diagram();

  window.interlisBrowserTest = {runContract, analyze, compile, diagram, benchmark, restartWorker};
})();
