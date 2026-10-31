const runtimeEvidence=require('../tools/verification/runtime-evidence.cjs');
const evidence=runtimeEvidence.begin(["interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm","interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js"]);
const fs = require('node:fs');
const path = require('node:path');

function findRuntime() {
  const candidates = [
    'build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js',
    'build/generated/teavm/wasm-gc/classes.wasm-runtime.js',
    'build/teavm/wasm/interlis-compiler.wasm-runtime.js'
  ];
  for (const candidate of candidates) {
    const resolved = path.resolve(__dirname, candidate);
    if (fs.existsSync(resolved)) return resolved;
  }
  throw new Error('TeaVM WasmGC runtime not found');
}

function findWasm() {
  const candidates = [
    'build/generated/teavm/wasm-gc/interlis-compiler.wasm',
    'build/generated/teavm/wasm-gc/classes.wasm',
    'build/teavm/wasm/interlis-compiler.wasm'
  ];
  for (const candidate of candidates) {
    const resolved = path.resolve(__dirname, candidate);
    if (fs.existsSync(resolved)) return resolved;
  }
  throw new Error('TeaVM WasmGC module not found');
}

(async () => {
  require(path.resolve(findRuntime()));
  const api = (await globalThis.TeaVM.wasmGC.load(path.resolve(findWasm()))).exports;
  const capabilities = JSON.parse(api.handle(JSON.stringify({
    protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'c1',
    operation: 'capabilities', params: {}
  })));
  if (capabilities.status !== 'ok') throw new Error('capabilities failed: ' + JSON.stringify(capabilities));
  const model = `INTERLIS 2.4;
MODEL Wasm AT "https://example.interlis.ch" VERSION "1.0" =
  DOMAIN Big = 9007199254740993.0010..9007199254740993.0010;
  TOPIC Data =
    CLASS Item =
      height : 0..999;
      exact : Big;
    END Item;
  END Data;
END Wasm.
`;
  const open = JSON.parse(api.handle(JSON.stringify({
    protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'c2',
    operation: 'openSession', params: {}
  })));
  if (open.status !== 'ok') throw new Error('openSession failed: ' + JSON.stringify(open));
  const sessionId = open.result.sessionId;
  const update = JSON.parse(api.handle(JSON.stringify({
    protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'c3',
    sessionId, operation: 'updateSource', params: {
      sourceId: 'memory:/wasm.ili', expectation: {kind: 'absent'},
      change: {kind: 'replace', text: model}
    }
  })));
  if (update.status !== 'ok') throw new Error('updateSource failed: ' + JSON.stringify(update));
  const analyzed = JSON.parse(api.handle(JSON.stringify({
    protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'c4a',
    sessionId, operation: 'analyze', params: {
      expectedWorkspaceRevision: String(update.result.workspaceRevision),
      roots: [{kind: 'byName', modelName: 'Wasm'}]
    }
  })));
  const compile = JSON.parse(api.handle(JSON.stringify({
    protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'c4',
    sessionId, operation: 'compile', params: {
      expectedWorkspaceRevision: String(update.result.workspaceRevision),
      roots: [{kind: 'byName', modelName: 'Wasm'}]
    }
  })));
  if (compile.status !== 'ok' || compile.result.outcome !== 'succeeded') {
    throw new Error('compile failed: ' + JSON.stringify(compile));
  }
  const diagram = JSON.parse(api.handle(JSON.stringify({
    protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'c5',
    sessionId, operation: 'diagram', params: {schemaId: compile.result.schemaId}
  })));
  if (diagram.status !== 'ok' || diagram.result.nodes.length === 0) {
    throw new Error('diagram failed: ' + JSON.stringify(diagram));
  }
  if (!JSON.stringify(diagram).includes('9007199254740993.0010')) {
    throw new Error('exact decimal literal missing from diagram');
  }
  const closed = JSON.parse(api.handle(JSON.stringify({
    protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'close',
    sessionId, operation: 'closeSession', params: {}
  })));
  if (closed.status !== 'ok') throw new Error('closeSession failed');
  const stabilizationCases = await require('./stabilization-contract.cjs')(api);
  const corpusRegressionCases = await require('./token-classification-contract.cjs')(api);
  const summary = {...runtimeEvidence.finish(evidence),
    stabilizationCases,
    corpusRegressionCases,
    status: 'passed',
    tool: capabilities.result.toolVersion,
    nodes: diagram.result.nodes.length,
    outcome: compile.result.outcome,
    runtime: 'teavm-wasm-gc',
    node: process.version
  };
  fs.mkdirSync('build', {recursive: true});
  fs.writeFileSync('build/wasm-contract-result.json', JSON.stringify(summary, null, 2));
  console.log('wasm-contract PASS ' + JSON.stringify(summary));
  process.exitCode = 0;
})().catch(error => {
  console.error('wasm-contract FAIL', error);
  try {
    fs.mkdirSync('build', {recursive: true});
    fs.writeFileSync('build/wasm-contract-result.json', JSON.stringify({
      status: 'failed', error: String(error), runtime: 'teavm-wasm-gc', node: process.version
    }, null, 2));
  } catch (ignored) {
  }
  process.exitCode = 1;
});
