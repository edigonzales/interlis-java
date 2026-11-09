'use strict';

const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const {createServer} = require('node:http');
const {readFile} = require('node:fs/promises');
const path = require('node:path');
const {test} = require('node:test');
const {chromium, firefox, webkit} = require('playwright');

const web = path.resolve(__dirname, '../build/web');
const types = {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.wasm': 'application/wasm'};
const browsers = process.env.INTERLIS_BROWSER_CHECK === '1' ? {chromium, firefox, webkit} : {chromium};

test('browser manifest describes the packaged compiler', async () => {
  const manifest = JSON.parse(await readFile(path.join(web, 'build-manifest.json'), 'utf8'));
  const versions = JSON.parse(await readFile(path.join(web, '../version-manifest.json'), 'utf8'));
  assert.equal(manifest.schemaVersion, '3.0');
  assert.equal(Object.hasOwn(manifest, 'toolchain'), false);
  assert.deepEqual(manifest.versions, versions);
  assert.equal(manifest.gitCommit, versions.gitCommit);
  const capabilities = manifest.compilerCapabilities;
  assert.equal(capabilities.toolVersion, versions.version);
  assert.equal(capabilities.protocol + '/' + capabilities.protocolVersion, versions.protocol);
  for (const name of ['interlis-compiler.wasm', 'interlis-compiler.wasm-runtime.js']) {
    const bytes = await readFile(path.join(web, name));
    assert.deepEqual(manifest.resources[name], {
      bytes: bytes.length,
      sha256: createHash('sha256').update(bytes).digest('hex')
    });
  }
});

for (const [name, type] of Object.entries(browsers)) {
  test(name + ' runs the packaged compiler through the real worker and recovers after cancellation', {timeout: 75000}, async () => {
    const server = createServer(async (request, response) => {
      try {
        const pathname = new URL(request.url, 'http://localhost').pathname;
        const file = path.resolve(web, pathname === '/' ? 'index.html' : pathname.slice(1));
        if (!file.startsWith(web + path.sep)) throw new Error('Invalid asset path');
        const bytes = await readFile(file);
        response.writeHead(200, {'content-type': types[path.extname(file)] || 'application/octet-stream'});
        response.end(bytes);
      } catch {
        response.writeHead(404).end();
      }
    });
    await new Promise((resolve, reject) => {
      server.once('error', reject);
      server.listen(0, '127.0.0.1', resolve);
    });
    let browser;
    try {
      const url = new URL('http://127.0.0.1:' + server.address().port);
      browser = await type.launch({headless: true});
      const page = await browser.newPage();
      const errors = [], wasmRequests = [];
      await page.route('**/*', route => {
        if (new URL(route.request().url()).origin === url.origin) return route.continue();
        errors.push('External request: ' + route.request().url());
        return route.abort();
      });
      page.on('pageerror', error => errors.push(String(error)));
      page.on('response', response => {
        if (response.url().endsWith('.wasm')) wasmRequests.push(response.status());
        if (response.status() >= 400 && !response.url().endsWith('/favicon.ico')) errors.push(response.url());
      });
      await page.goto(url.href);
      let timeout;
      const result = await Promise.race([
        page.evaluate(() => window.interlisBrowserTest.runClientContract()),
        new Promise((_, reject) => {
          timeout = setTimeout(() => reject(new Error('Browser worker smoke exceeded 45 seconds')), 45000);
        })
      ]).finally(() => clearTimeout(timeout));
      assert.equal(result.rejectedOutcome, 'rejected');
      assert.equal(result.compiledOutcome, 'succeeded');
      assert.equal(result.workerRestartOutcome, 'succeeded');
      assert.equal(result.exactDecimal, true);
      assert.ok(wasmRequests.includes(200), 'No generated Wasm was loaded');
      await page.evaluate(() => {
        const {client} = window.interlisBrowserTest;
        const diagram = client.diagram.bind(client);
        client.diagram = async (...args) => {
          const response = await diagram(...args);
          client.diagram = diagram;
          await new Promise(resolve => { window.releaseDiagram = resolve; });
          return response;
        };
        window.lateDiagram = window.interlisBrowserTest.diagram();
      });
      await page.waitForFunction(() => typeof window.releaseDiagram === 'function');
      const oldOutput = await page.locator('#output').textContent();
      const source = page.locator('#source');
      await source.fill(await source.inputValue() + '\n!! another revision');
      const late = await page.evaluate(() => {
        window.releaseDiagram();
        return window.lateDiagram;
      });
      assert.equal(late.stale, true);
      assert.equal(await page.locator('#output').textContent(), oldOutput);
      const current = await page.evaluate(() => window.interlisBrowserTest.diagram());
      assert.equal(current.stale, undefined);
      assert.notEqual(current.result.snapshotId, late.result.snapshotId);
      assert.notDeepEqual(current.result.sourceRevisions, late.result.sourceRevisions);
      assert.deepEqual(errors, []);
    } finally {
      await browser?.close();
      server.closeAllConnections();
      await new Promise(resolve => server.close(resolve));
    }
  });
}
