const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

function serve(root) {
  const types = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.wasm': 'application/wasm'
  };
  return new Promise((resolve) => {
    const server = http.createServer((request, response) => {
      const relative = request.url === '/' ? '/index.html' : request.url.split('?')[0];
      const file = path.join(root, relative);
      if (!file.startsWith(root) || !fs.existsSync(file)) {
        response.writeHead(404);
        response.end('not found');
        return;
      }
      response.writeHead(200, {'content-type': types[path.extname(file)] || 'application/octet-stream'});
      fs.createReadStream(file).pipe(response);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

(async () => {
  fs.rmSync('build/browser-results.json', {force: true});
  const {chromium, firefox, webkit} = require('playwright');
  const root = path.resolve('build/web');
  const server = await serve(root);
  const url = `http://127.0.0.1:${server.address().port}/`;
  const matrix = [
    ['chromium', chromium],
    ['firefox', firefox],
    ['webkit', webkit]
  ];
  const results = [];
  for (const [name, type] of matrix) {
    let browser;
    try {
      browser = await type.launch({headless: true});
      const page = await browser.newPage();
      await page.goto(url, {waitUntil: 'load'});
      await page.waitForFunction(() => Boolean(window.interlisBrowserTest), {timeout: 30000});
      let timeout;
      const result = await Promise.race([
        page.evaluate(() => window.interlisBrowserTest.runContract()),
        new Promise((_, reject) => {
          timeout = setTimeout(() => reject(new Error('browser contract exceeded 60 seconds')), 60000);
        })
      ]).finally(() => clearTimeout(timeout));
      results.push({browser: name, browserVersion: browser.version(), node: process.version,
        playwright: require('playwright/package.json').version,
        status: 'passed', result});
      console.log(`${name.toUpperCase()} PASS ${JSON.stringify(result)}`);
    } catch (error) {
      results.push({browser: name, status: 'failed', error: String(error)});
      console.log(`${name.toUpperCase()} FAIL ${error}`);
    } finally {
      if (browser) await browser.close();
    }
  }
  server.close();
  fs.writeFileSync('build/browser-results.json', JSON.stringify(results, null, 2));
  if (results.some(r => r.status !== 'passed')) process.exitCode = 1;
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
