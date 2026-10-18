const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {chromium, firefox, webkit} = require('../interlis-wasm-teavm/node_modules/playwright');

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
  const home = process.env.HOME;
  const executables = {
    chromium: path.join(home, 'Library/Caches/ms-playwright/chromium-1243/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing'),
    firefox: path.join(home, 'Library/Caches/ms-playwright/firefox-1543/firefox/Nightly.app/Contents/MacOS/firefox'),
    webkit: path.join(home, 'Library/Caches/ms-playwright/webkit-2359/pw_run.sh')
  };
  const root = path.resolve('build/webimage-browser');
  const server = await serve(root);
  const url = `http://127.0.0.1:${server.address().port}/`;
  const results = [];
  for (const [name, type] of [['chromium', chromium], ['firefox', firefox], ['webkit', webkit]]) {
    let browser;
    try {
      const options = {headless: true};
      if (fs.existsSync(executables[name])) options.executablePath = executables[name];
      browser = await type.launch(options);
      const page = await browser.newPage();
      await page.goto(url, {waitUntil: 'load'});
      const result = await page.evaluate(() => window.webImageProbe);
      results.push({browser: name, runtime: 'graalvm-web-image', status: 'passed', result});
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
