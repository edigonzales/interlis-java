'use strict';

const assert = require('node:assert/strict');
const {stat} = require('node:fs/promises');
const {setTimeout: delay} = require('node:timers/promises');
const {test} = require('node:test');
const {protocol, withSession, compile} = require('../../interlis-wasm-teavm/test/compiler-smoke.cjs');

test('Web Image compiles Unicode sources and distinguishes source errors', {timeout: 15000}, async () => {
  const bundle = require.resolve('../build/webimage/interlis-compiler.js');
  assert.ok((await stat(bundle + '.wasm')).isFile());
  require(bundle);
  for (let i = 0; i < 100 && typeof globalThis.interlisCompilerHandle !== 'function'; i++) {
    await delay(50);
  }
  const call = protocol({handle: globalThis.interlisCompilerHandle});
  const capabilities = call('capabilities');
  assert.equal(capabilities.status, 'ok');
  assert.equal(capabilities.result.protocolVersion, '1.0');
  if (process.env.INTERLIS_PRODUCT_VERSION) assert.equal(capabilities.result.toolVersion, process.env.INTERLIS_PRODUCT_VERSION);
  const sourceId = 'memory:/probe.ili';
  const text = 'INTERLIS 2.4;\n!! ä😀\nMODEL Probe AT "x" VERSION "1" = TOPIC Data = CLASS Item = label: TEXT*20; END Item; END Data; END Probe.';
  withSession(call, [{sourceId, text}], (request, revision) => {
    const compiled = compile(request, revision, 'Probe');
    assert.equal(compiled.outcome, 'succeeded');
    assert.deepEqual(compiled.diagnostics, []);
  });
  const broken = text.replace('TEXT*20', 'Missing');
  withSession(call, [{sourceId, text: broken}], (request, revision) => {
    const rejected = compile(request, revision, 'Probe');
    assert.equal(rejected.outcome, 'rejected');
    assert.deepEqual(rejected.rejection.reasons, ['SOURCE_ERRORS']);
    const diagnostic = rejected.diagnostics.find(d => d.code === 'ILI-SEM-0001');
    assert.equal(diagnostic.range.startOffset, broken.indexOf('Missing'));
  });
});
