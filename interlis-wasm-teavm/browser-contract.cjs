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
  const {chromium, firefox, webkit} = require('playwright');
  const root = path.resolve('build/web');
  const server = await serve(root);
  const url = `http://127.0.0.1:${server.address().port}/`;
  const home = process.env.HOME;
  const cached = {
    chromium: path.join(home, 'Library/Caches/ms-playwright/chromium-1243/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing'),
    firefox: path.join(home, 'Library/Caches/ms-playwright/firefox-1543/firefox/Nightly.app/Contents/MacOS/firefox'),
    webkit: path.join(home, 'Library/Caches/ms-playwright/webkit-2359/pw_run.sh')
  };
  const matrix = [
    ['chromium', chromium],
    ['firefox', firefox],
    ['webkit', webkit]
  ];
  const results = [];
  for (const [name, type] of matrix) {
    let browser;
    try {
      const options = {headless: true};
      if (cached[name] && fs.existsSync(cached[name])) options.executablePath = cached[name];
      browser = await type.launch(options);
      const page = await browser.newPage();
      await page.goto(url, {waitUntil: 'load'});
      await page.waitForFunction(() => Boolean(window.interlisBrowserTest), {timeout: 30000});
      const result = await page.evaluate(() => window.interlisBrowserTest.runContract());
      results.push({browser: name, status: 'passed', result});
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
