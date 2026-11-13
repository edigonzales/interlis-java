'use strict';

const assert = require('node:assert/strict');
const {readFile} = require('node:fs/promises');
const {createServer} = require('node:http');
const path = require('node:path');
const {test} = require('node:test');
const {chromium} = require('playwright');

test('Chromium compiles a model and validates neutral data in WasmGC', {timeout: 75000}, async () => {
  const root = path.resolve(__dirname, '..');
  const model = await readFile(path.join(__dirname, 'LocalValidation.ili'), 'utf8');
  const assets = new Map([
    ['/', ['text/html', Buffer.from('<!doctype html><script src="/runtime.js"></script>')]],
    ['/runtime.js', ['text/javascript', await readFile(path.join(root,
      'build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js'))]],
    ['/local-validation.wasm', ['application/wasm', await readFile(path.join(root,
      'build/validation-probe/local-validation.wasm'))]]
  ]);
  const server = createServer((request, response) => {
    const asset = assets.get(request.url);
    if (!asset) {
      response.writeHead(404).end();
      return;
    }
    response.writeHead(200, {'content-type': asset[0]}).end(asset[1]);
  });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  let browser;
  try {
    const base = 'http://127.0.0.1:' + server.address().port;
    browser = await chromium.launch();
    const page = await browser.newPage();
    const requests = [];
    await page.route('**/*', route => {
      requests.push(route.request().url());
      return route.request().url().startsWith(base + '/') ? route.continue() : route.abort();
    });
    await page.goto(base);
    assert.equal(await page.evaluate(async () => {
      const runtime = await globalThis.TeaVM.wasmGC.load('/local-validation.wasm');
      globalThis.validateExample = runtime.exports.validateExample;
      return runtime.instance instanceof WebAssembly.Instance && runtime.module instanceof WebAssembly.Module;
    }), true);
    assert.ok(requests.includes(base + '/local-validation.wasm'));
    const loaded = requests.length;
    async function validate(source, amount, includeLabel = true) {
      return page.evaluate(([source, amount, includeLabel]) =>
        JSON.parse(globalThis.validateExample(source, amount, 'label', includeLabel)),
      [source, amount, includeLabel]);
    }
    assert.deepEqual(await validate(model, '20.00'),
      {result: 'PASSED_CHECKED_SCOPE', violations: 0, diagnostics: []});
    for (const [source, amount, includeLabel, code, fieldPath] of [
      [model, '200.00', true, 'VALUE_OUT_OF_RANGE', ['amount']],
      [model, '5.00', true, 'MANDATORY_CONSTRAINT_FALSE', []],
      [model, '20.00', false, 'REQUIRED_VALUE_MISSING', ['label']],
      [model.replace('>= 10.00', '>= 25.00'), '20.00', true, 'MANDATORY_CONSTRAINT_FALSE', []]
    ]) {
      assert.deepEqual(await validate(source, amount, includeLabel), {
        result: 'FAILED_CHECKED_SCOPE', violations: 1, diagnostics: [{code, path: fieldPath}]
      });
    }
    const invalid = await validate(model.replace('CLASS Item', 'CLASS = Item'), '20.00');
    assert.equal(invalid.result, 'COMPILATION_REJECTED');
    assert.ok(invalid.diagnostics.some(code => code.startsWith('ILI-SYN-')));
    assert.equal(requests.length, loaded, 'validations must not make HTTP requests');
  } finally {
    try {
      await browser?.close();
    } finally {
      server.closeAllConnections();
      await new Promise(resolve => server.close(resolve));
    }
  }
});
