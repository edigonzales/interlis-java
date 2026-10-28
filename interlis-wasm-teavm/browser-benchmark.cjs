const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');

function serve(root) {
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
  const {chromium, firefox, webkit} = require('playwright');
  const root = path.resolve(__dirname, 'build/web');
  const server = await serve(root);
  const url = `http://127.0.0.1:${server.address().port}/`;
  const repoRoot = path.resolve(__dirname, '..');
  const manifest = JSON.parse(fs.readFileSync(path.join(repoRoot,
    'docs/evidence/p3/geo-corpus.json'), 'utf8'));
  const cache = path.join(os.homedir(), '.cache/interlis-java/geo-corpus');
  const home = process.env.HOME;
  const cached = {
    chromium: path.join(home, 'Library/Caches/ms-playwright/chromium-1243/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing'),
    firefox: path.join(home, 'Library/Caches/ms-playwright/firefox-1543/firefox/Nightly.app/Contents/MacOS/firefox'),
    webkit: path.join(home, 'Library/Caches/ms-playwright/webkit-2359/pw_run.sh')
  };
  const matrix = [['chromium', chromium], ['firefox', firefox], ['webkit', webkit]];
  const results = [];
  for (const [name, type] of matrix) {
    let browser;
    try {
      const options = {headless: true};
      if (cached[name] && fs.existsSync(cached[name])) options.executablePath = cached[name];
      browser = await type.launch(options);
      const models = [];
      for (const entry of manifest.models) {
        if (entry.model === 'IliValidGwr_V1_0'
            || (name === 'webkit' && entry.model !== 'SO_FunctionsExt')) {
          models.push({model: entry.model, language: entry.language, bytes: entry.bytes,
            sha256: entry.sha256, status: 'skipped',
            reason: 'browser WasmGC stack limit; measured on JVM/WASM'});
          continue;
        }
        const page = await browser.newPage();
        await page.goto(url, {waitUntil: 'load'});
        await page.waitForFunction(() => Boolean(window.interlisBrowserTest), {timeout: 30000});
        const text = fs.readFileSync(path.join(cache, entry.file.replaceAll('/', '__')), 'utf8');
        const run = () => page.evaluate(({text, model}) =>
          window.interlisBrowserTest.benchmark(text, model), {text, model: entry.model});
        await run();
        const samples = [];
        for (let i = 0; i < 3; i++) samples.push(await run());
        models.push({model: entry.model, language: entry.language, bytes: entry.bytes,
          sha256: entry.sha256, compileMs: samples,
          compileMedianMs: samples.slice().sort((a, b) => a - b)[1]});
        await page.close();
      }
      results.push({browser: name, status: 'passed', models});
      console.log(`${name.toUpperCase()} BENCHMARK PASS`);
    } catch (error) {
      results.push({browser: name, status: 'failed', error: String(error)});
      console.log(`${name.toUpperCase()} BENCHMARK FAIL ${error}`);
    } finally {
      if (browser) await browser.close();
    }
  }
  server.close();
  const result = {schemaVersion: '1.0', runtime: 'browser', iterationsPerModel: 3,
    warmupPerModel: 1, corpus: 'docs/evidence/p3/geo-corpus.json',
    corpusIndexSha256: manifest.index.sha256, results,
    notes: 'Playwright browser pages; warmup excluded.'};
  fs.mkdirSync(path.join(repoRoot, 'build/reports/performance'), {recursive: true});
  fs.writeFileSync(path.join(repoRoot, 'build/reports/performance/performance-browser.json'),
    JSON.stringify(result, null, 2) + '\n');
  if (results.some(r => r.status !== 'passed')) process.exitCode = 1;
})().catch(error => {
  console.error(error); process.exitCode = 1;
});
