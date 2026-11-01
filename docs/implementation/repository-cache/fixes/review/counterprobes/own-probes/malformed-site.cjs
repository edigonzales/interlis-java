'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const root = process.cwd();
const base = path.join(root, 'build/reviewer/counterprobes/own-probes/malformed-site');
fs.rmSync(base, { recursive: true, force: true });
fs.mkdirSync(path.join(base, 'home'), { recursive: true });
const index = '<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
const basis = 'INTERLIS 2.4; MODEL Basis AT "https://example.org" VERSION "1" = END Basis.';
let requests = 0, bodies = 0;
const log = [];
const server = http.createServer((req, res) => {
  requests += 1;
  const p = new URL(req.url, 'http://127.0.0.1').pathname;
  let body = null, status = 404;
  if (p === '/repo/ilimodels.xml') { body = index; status = 200; }
  else if (p === '/repo/Basis.ili') { body = basis; status = 200; }
  else if (p === '/repo/ilisite.xml') { body = '<Site>'; status = 200; }
  log.push({ path: p, status, body: body === null ? 0 : Buffer.byteLength(body) });
  if (body === null) { res.statusCode = 404; res.end(); return; }
  bodies += 1;
  res.setHeader('Content-Length', Buffer.byteLength(body));
  res.end(body);
});
const cli = path.join(root, 'build/reviewer/distribution/bin/interlis-compiler');
server.listen(0, '127.0.0.1', () => {
  const repo = `http://127.0.0.1:${server.address().port}/repo/`;
  const home = path.join(base, 'home');
  const child = cp.spawn(cli, ['check', '--root', 'Absent', '--repository', repo, '--json'], {
    cwd: base, env: { ...process.env, HOME: home, JAVA_OPTS: `-Duser.home=${home}` },
  });
  let stdout = '', stderr = '';
  child.stdout.on('data', (c) => { stdout += c; });
  child.stderr.on('data', (c) => { stderr += c; });
  child.on('close', (code) => {
    let json = null;
    try { json = JSON.parse(stdout.trim()); } catch (_) { }
    const result = {
      command: [cli, 'check', '--root', 'Absent', '--repository', repo, '--json'],
      exitCode: code,
      status: json && json.status,
      diagnostics: json && json.diagnostics ? json.diagnostics.map((d) => ({ code: d.code, severity: d.severity, message: d.message, arguments: d.arguments })) : [],
      http: { requests, bodies, entries: log },
      stderr,
    };
    fs.writeFileSync(path.join(base, 'results.json'), JSON.stringify(result, null, 2) + '\n');
    console.log(JSON.stringify(result, null, 2));
    server.close();
  });
});
