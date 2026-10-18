(() => {
  let worker;
  let nextId = 1;
  const pending = new Map();
  function attach(candidate) {
    candidate.onmessage = (event) => {
      const {id, response, error} = event.data;
      const entry = pending.get(id);
      if (!entry) return;
      pending.delete(id);
      if (error) entry.reject(new Error(error));
      else entry.resolve(JSON.parse(response));
    };
  }
  function newWorker() {
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
  const state = {sessionId: null, revision: '0', snapshotId: null, schemaId: null, hasSource: false};

  function resetState() {
    state.sessionId = null;
    state.revision = '0';
    state.snapshotId = null;
    state.schemaId = null;
    state.hasSource = false;
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

  async function replaceSource(text) {
    const sessionId = await openSession();
    const response = await call(envelope('updateSource', sessionId, {
      sourceId: 'memory:/browser.ili',
      expectation: {kind: 'at', revision: state.revision},
      change: {kind: 'replace', text}
    }));
    if (response.status !== 'ok') throw new Error(JSON.stringify(response));
    state.revision = response.result.workspaceRevision;
    state.hasSource = true;
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
    state.hasSource = true;
  }

  async function analyze() {
    if (!state.hasSource) await openRevision();
    else await replaceSource(source.value);
    const response = await call(envelope('analyze', state.sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName: 'Browser'}]
    }));
    if (response.status === 'ok') {
      state.snapshotId = response.result.snapshotId;
      output.textContent = JSON.stringify(response.result, null, 2);
    } else {
      output.textContent = JSON.stringify(response, null, 2);
    }
    return response;
  }

  async function compile() {
    if (!state.hasSource) await openRevision();
    else await replaceSource(source.value);
    const response = await call(envelope('compile', state.sessionId, {
      expectedWorkspaceRevision: state.revision,
      roots: [{kind: 'byName', modelName: 'Browser'}]
    }));
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
    output.textContent = JSON.stringify(response, null, 2);
    return response;
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
    const restarted = await restartWorker();
    return {
      status: 'ok',
      capabilities: capabilities.result.toolVersion,
      analyzeRevision: state.revision,
      rejectedOutcome: rejected.result ? rejected.result.outcome : rejected.status,
      compiledOutcome: succeeded.result.outcome,
      diagramNodes: diagram.result.nodes.length,
      diagnostics: succeeded.result.diagnostics.length,
      exactDecimal: true,
      workerRestartOutcome: restarted.result ? restarted.result.outcome : restarted.status
    };
  }

  document.getElementById('analyze').onclick = () => analyze();
  document.getElementById('compile').onclick = () => compile();
  document.getElementById('diagram').onclick = () => diagram();

  window.interlisBrowserTest = {runContract, analyze, compile, diagram, restartWorker};
})();
