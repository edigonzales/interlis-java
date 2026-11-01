// Focused shared compiler regression; repository IO remains a JVM host concern.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const evidenceTools = require('../tools/verification/runtime-evidence.cjs');
const evidence = evidenceTools.begin([
  'interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js',
  'interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm',
  'interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js'
]);
function probe(api) {
  let sequence = 0, sessionId;
  const handle = (operation, params = {}) => {
    const result = JSON.parse(api.handle(JSON.stringify({protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'repo-' + sequence++, operation, sessionId, params})));
    assert.equal(result.status, 'ok', JSON.stringify(result)); return result.result;
  };
  sessionId = handle('openSession').sessionId;
  const text = 'INTERLIS 2.4;\n!! 😀 ä\nMODEL Root AT "x" VERSION "1" = IMPORTS Missing; STRUCTURE P=p:Missing.Point; q:Other; END P; END Root.';
  const revision = handle('updateSource', {sourceId: 'memory:/root.ili', expectation: {kind: 'absent'}, change: {kind: 'replace', text}}).workspaceRevision;
  const result = handle('compile', {expectedWorkspaceRevision: revision, roots: [{kind: 'byName', modelName: 'Root'}]});
  assert.equal(result.outcome, 'rejected'); assert.ok(result.rejection.reasons.includes('MISSING_INPUT'));
  const missing = result.diagnostics.find(d => d.code === 'ILI-RES-0001');
  const dependent = result.diagnostics.find(d => d.code === 'ILI-SEM-0001' && d.arguments.some(a => a.value === 'Missing.Point'));
  const independent = result.diagnostics.find(d => d.code === 'ILI-SEM-0001' && d.arguments.some(a => a.value === 'Other'));
  assert.equal(missing.range.startOffset, text.indexOf('Missing'));
  assert.equal(dependent.causeId, missing.id); assert.equal(independent.causeId, undefined);
  handle('closeSession'); return result.diagnostics;
}
(async () => {
  const js = require(path.resolve(__dirname, 'build/generated/teavm/js/interlis-compiler.js'));
  const jsResult = probe(js);
  require(path.resolve(__dirname, 'build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js'));
  const wasm = (await globalThis.TeaVM.wasmGC.load(path.resolve(__dirname, 'build/generated/teavm/wasm-gc/interlis-compiler.wasm'))).exports;
  const wasmResult = probe(wasm); assert.deepEqual(wasmResult, jsResult);
  const result = {...evidenceTools.finish(evidence), status: 'passed', cases: 2, node: process.version, diagnostics: jsResult};
  fs.writeFileSync(path.resolve(__dirname, 'build/repository-diagnostic-result.json'), JSON.stringify(result, null, 2));
  console.log('repository-diagnostic PASS: JS and WasmGC diagnostics and causes agree');
  process.exit(0); // TeaVM keeps runtime handles alive after the assertions finish.
})().catch(error => { console.error(error); process.exit(1); });
