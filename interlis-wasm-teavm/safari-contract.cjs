const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const {spawn, execFileSync} = require('node:child_process');

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

async function request(base, method, route, body, timeout = 60000) {
  const response = await fetch(base + route, {
    method,
    headers: body === undefined ? {} : {'content-type': 'application/json'},
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(timeout)
  });
  const text = await response.text();
  let json;
  try { json = JSON.parse(text); } catch { json = {value: text}; }
  if (!response.ok) throw new Error(`${method} ${route} -> ${response.status} ${text}`);
  if (json.value && json.value.error) {
    throw new Error(`${method} ${route} -> ${json.value.error}: ${json.value.message || ''}`);
  }
  return json.value;
}

(async () => {
  fs.rmSync('build/safari-result.json', {force: true});
  const driver = '/System/Cryptexes/App/usr/bin/safaridriver';
  if (!fs.existsSync(driver)) throw new Error('safaridriver is not installed');

  const driverVersion = execFileSync(driver, ['--version'], {encoding: 'utf8'}).trim();
  const webRoot = path.resolve('build/web');
  const server = await serve(webRoot);
  const url = `http://127.0.0.1:${server.address().port}/`;
  const port = 42000 + Math.floor(Math.random() * 2000);
  const base = `http://127.0.0.1:${port}`;
  const child = spawn(driver, ['-p', String(port)], {stdio: ['ignore', 'ignore', 'pipe']});
  let stderr = '';
  child.stderr.on('data', chunk => { stderr += chunk.toString(); });
  let sessionId = null;
  let result;
  let step="driver";
  try {
    let ready = false;
    let lastStatus;
    const readyDeadline = Date.now() + 30000;
    while (Date.now() < readyDeadline && !ready) {
      if (child.exitCode !== null) throw new Error(`safaridriver exited with ${child.exitCode}: ${stderr}`);
      await new Promise(resolve => setTimeout(resolve, 250));
      try {
        const status = await request(base, 'GET', '/status', undefined, 1000);
        lastStatus = status;
        ready = Boolean(status && status.ready);
      } catch (error) { lastStatus = String(error); }
    }
    if (!ready) throw new Error('safaridriver did not become ready: ' + JSON.stringify(lastStatus));

    step='create session';
    const created = await request(base, 'POST', '/session', {
      capabilities: {alwaysMatch: {browserName: 'safari'}}
    });
    sessionId = created.sessionId;
    await request(base, 'POST', `/session/${sessionId}/timeouts`, {script: 60000});
    step='navigate';
    await request(base, 'POST', `/session/${sessionId}/url`, {url});
    step='await client';
    for (let i = 0; i < 80; i++) {
      const available = await request(base, 'POST', `/session/${sessionId}/execute/sync`, {
        script: 'return Boolean(window.interlisBrowserTest);', args: []
      });
      if (available) break;
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    step='run contract';
    result = await request(base, 'POST', `/session/${sessionId}/execute/async`, {
      script: 'const done = arguments[arguments.length - 1]; ' +
        'window.interlisBrowserTest.runClientContract()' +
        '.then(value => done(value))' +
        '.catch(error => done({status: "error", error: String(error), stack: error && error.stack}));',
      args: []
    });
    const clientFinished=Date.now();
    step='stabilization';
    const stabilization=await request(base,'POST',`/session/${sessionId}/execute/async`,{
      script:'const done=arguments[arguments.length-1];window.interlisBrowserTest.runStabilizationContract().then(done).catch(error=>done({error:String(error)}));',args:[]
    });
    if(stabilization?.stabilizationCases!==3548)throw new Error('Safari stabilization failed: '+JSON.stringify(stabilization));
    result={...result,...stabilization};
    const stabilizationElapsedMs=Date.now()-clientFinished;
    if (!result || result.status !== 'ok' || result.exactDecimal !== true
        || !(result.diagramNodes > 0) || result.workerRestartOutcome !== 'succeeded') {
      throw new Error('Safari contract returned an unexpected result: ' + JSON.stringify(result));
    }
    fs.mkdirSync('build', {recursive: true});
    fs.writeFileSync('build/safari-result.json', JSON.stringify({
      status: 'passed', browser: 'Safari', driverVersion, node: process.version,
      result, stabilizationElapsedMs, driverStderr: stderr.trim()
    }, null, 2));
    console.log('SAFARI PASS ' + JSON.stringify(result));
  } catch (error) {
    let progress;try{if(sessionId)progress=await request(base,'POST',`/session/${sessionId}/execute/sync`,{script:'return window.interlisContractProgress;',args:[]},5000);}catch{}
    fs.mkdirSync('build', {recursive: true});
    fs.writeFileSync('build/safari-result.json', JSON.stringify({
      status: sessionId ? 'failed' : 'notRun', browser: 'Safari', driverVersion,
      node: process.version, error: String(error),
      partialResult: result, step, progress, driverStderr: stderr.trim()
    }, null, 2));
    console.error('SAFARI NOT_RUN ' + String(error));
    if (stderr.trim()) console.error(stderr.trim());
    process.exitCode = 1;
  } finally {
    if (sessionId) {
      try { await request(base, 'DELETE', `/session/${sessionId}`); } catch { /* already gone */ }
    }
    child.kill('SIGTERM');
    server.close();
  }
})().catch(error => {
  console.error('SAFARI NOT_RUN ' + String(error));
  process.exitCode = 1;
});
