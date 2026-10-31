const runtimeEvidence=require('../tools/verification/runtime-evidence.cjs');
const evidence=runtimeEvidence.begin(["interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js","interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js.wasm"]);
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const browserRequire = require('node:module').createRequire(
  path.resolve(__dirname, '../interlis-wasm-teavm/package.json'));
const {chromium, firefox, webkit} = browserRequire('playwright');

async function serve(root) {
  const types = {'.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
    '.wasm': 'application/wasm'};
  return new Promise(resolve => {
    const server = http.createServer((request, response) => {
      const relative = request.url === '/' ? '/index.html' : request.url.split('?')[0];
      const file = path.join(root, relative);
      if (!file.startsWith(root) || !fs.existsSync(file)) {
        response.writeHead(404); response.end('not found'); return;
      }
      response.writeHead(200, {'content-type': types[path.extname(file)] || 'application/octet-stream'});
      fs.createReadStream(file).pipe(response);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

(async () => {
  fs.rmSync('build/webimage-browser/browser-results.json', {force: true});
  const root = path.resolve('build/webimage-browser');
  const server = await serve(root);
  const url = `http://127.0.0.1:${server.address().port}/`;
  const results = [];
  for (const [name, type] of [['chromium', chromium], ['firefox', firefox], ['webkit', webkit]]) {
    let browser;
    try {
      browser = await type.launch({headless: true});
      const page = await browser.newPage();
      await page.goto(url, {waitUntil: 'load'});
      let timeout;
      const result = await Promise.race([
        page.evaluate(() => window.webImageProbe),
        new Promise((_, reject) => {
          timeout = setTimeout(() => reject(new Error('Web Image browser probe exceeded 60 seconds')), 60000);
        })
      ]).finally(() => clearTimeout(timeout));
      if (!result || result.status !== 'ok' || result.outcome !== 'succeeded') {
        throw new Error('Web Image browser compilation failed: ' + JSON.stringify(result));
      }
      results.push({...runtimeEvidence.finish(evidence),browser: name, browserVersion: browser.version(), node: process.version,
        playwright: browserRequire('playwright/package.json').version,
        runtime: 'graalvm-web-image', status: 'passed', result});
      console.log(`${name.toUpperCase()} WEBIMAGE PASS ${JSON.stringify(result)}`);
    } catch (error) {
      results.push({browser: name, runtime: 'graalvm-web-image', status: 'failed',
        error: String(error)});
      console.log(`${name.toUpperCase()} WEBIMAGE FAIL ${error}`);
    } finally {
      if (browser) await browser.close();
    }
  }
  server.close();
  fs.writeFileSync(path.resolve('build/webimage-browser/browser-results.json'),
      JSON.stringify(results, null, 2));
  if (results.some(result => result.status !== 'passed')) process.exitCode = 1;
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
