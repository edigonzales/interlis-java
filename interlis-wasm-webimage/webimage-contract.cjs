const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const bundle = path.resolve('build/webimage/interlis-compiler.js');
  if (!fs.existsSync(bundle)) throw new Error('Web Image bundle not found');
  require(bundle);
  await new Promise(resolve => setTimeout(resolve, 500));
  if (typeof globalThis.interlisCompilerHandle !== 'function') {
    throw new Error('Web Image bootstrap did not export interlisCompilerHandle');
  }
  const handle = request => JSON.parse(globalThis.interlisCompilerHandle(JSON.stringify(request)));
  const envelope = (operation, sessionId, params) => {
    const body = {protocol: 'interlis.compiler', protocolVersion: '1.0',
      requestId: 'wimg-' + operation, operation, params: params || {}};
    if (sessionId) body.sessionId = sessionId;
    return body;
  };
  const capabilities = handle(envelope('capabilities'));
  if (capabilities.status !== 'ok') throw new Error('capabilities failed');
  const opened = handle(envelope('openSession'));
  const sessionId = opened.result.sessionId;
  const model = `INTERLIS 2.4;
MODEL WebImage AT "https://example.interlis.ch" VERSION "1.0" =
  TOPIC Data =
    CLASS Item =
      height : 0..999;
    END Item;
  END Data;
END WebImage.
`;
  const updated = handle(envelope('updateSource', sessionId, {
    sourceId: 'memory:/webimage.ili', expectation: {kind: 'absent'},
    change: {kind: 'replace', text: model}
  }));
  if (updated.status !== 'ok') throw new Error('update failed: ' + JSON.stringify(updated));
  const compiled = handle(envelope('compile', sessionId, {
    expectedWorkspaceRevision: updated.result.workspaceRevision,
    roots: [{kind: 'byName', modelName: 'WebImage'}]
  }));
  if (compiled.status !== 'ok' || compiled.result.outcome !== 'succeeded') {
    throw new Error('compile failed: ' + JSON.stringify(compiled));
  }
  const closed = handle(envelope('closeSession', sessionId));
  if (closed.status !== 'ok') throw new Error('closeSession failed');
  const stabilizationCases = await require('../interlis-wasm-teavm/stabilization-contract.cjs')({
    handle: globalThis.interlisCompilerHandle
  });
  const result = {
    stabilizationCases,
    status: capabilities.status,
    tool: capabilities.result.toolVersion,
    outcome: compiled.result ? compiled.result.outcome : compiled.status,
    runtime: 'graalvm-web-image',
    node: process.version
  };
  fs.mkdirSync('build/webimage', {recursive: true});
  fs.writeFileSync('build/webimage/result.json', JSON.stringify(result, null, 2));
  console.log('webimage-contract PASS ' + JSON.stringify(result));
  process.exitCode = 0;
})().catch(error => {
  console.error('webimage-contract FAIL', error);
  try {
    fs.mkdirSync('build/webimage', {recursive: true});
    fs.writeFileSync('build/webimage/result.json', JSON.stringify({
      status: 'blocked', error: String(error), runtime: 'graalvm-web-image'
    }, null, 2));
  } catch (ignored) {
  }
  process.exitCode = 1;
});
