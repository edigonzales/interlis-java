const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const bundle = path.resolve(__dirname, 'build/generated/teavm/js/interlis-compiler.js');
  if (!fs.existsSync(bundle)) throw new Error('TeaVM JavaScript bundle not found');
  const api = require(bundle);
  if (typeof api.handle !== 'function') throw new Error('TeaVM JavaScript export handle missing');
  const handle = request => JSON.parse(api.handle(JSON.stringify(request)));
  const envelope = (operation, sessionId, params) => {
    const body = {protocol: 'interlis.compiler', protocolVersion: '1.0',
      requestId: 'js-' + operation, operation, params: params || {}};
    if (sessionId) body.sessionId = sessionId;
    return body;
  };
  const capabilities = handle(envelope('capabilities'));
  if (capabilities.status !== 'ok') throw new Error('capabilities failed');
  const opened = handle(envelope('openSession'));
  const sessionId = opened.result.sessionId;
  const model = `INTERLIS 2.4;
MODEL JsTarget AT "https://example.interlis.ch" VERSION "1.0" =
  DOMAIN Big = 9007199254740993.0010..9007199254740993.0010;
  TOPIC Data =
    CLASS Item =
      height : 0..999;
      exact : Big;
    END Item;
  END Data;
END JsTarget.
`;
  const updated = handle(envelope('updateSource', sessionId, {
    sourceId: 'memory:/js.ili', expectation: {kind: 'absent'},
    change: {kind: 'replace', text: model}
  }));
  if (updated.status !== 'ok') throw new Error('update failed');
  const compiled = handle(envelope('compile', sessionId, {
    expectedWorkspaceRevision: updated.result.workspaceRevision,
    roots: [{kind: 'byName', modelName: 'JsTarget'}]
  }));
  if (compiled.status !== 'ok' || compiled.result.outcome !== 'succeeded') {
    throw new Error('compile failed: ' + JSON.stringify(compiled));
  }
  handle(envelope('closeSession', sessionId));
  const stabilizationCases = await require('./stabilization-contract.cjs')(api);
  const corpusRegressionCases = await require('./token-classification-contract.cjs')(api);
  const result = {stabilizationCases, corpusRegressionCases, status: 'passed', tool: capabilities.result.toolVersion,
    outcome: compiled.result ? compiled.result.outcome : compiled.status,
    runtime: 'teavm-js', node: process.version};
  fs.writeFileSync(path.resolve(__dirname, 'build/js-contract-result.json'),
    JSON.stringify(result, null, 2));
  console.log('js-contract PASS ' + JSON.stringify(result));
  process.exitCode = 0;
})().catch(error => {
  console.error('js-contract FAIL', error);
  try {
    fs.writeFileSync(path.resolve(__dirname, 'build/js-contract-result.json'),
      JSON.stringify({status: 'failed', error: String(error), runtime: 'teavm-js'}, null, 2));
  } catch (ignored) {
  }
  process.exitCode = 1;
});
