#!/usr/bin/env node
'use strict';
// Independent reviewer counterprobes: async local HTTP server + real launcher.
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const cp = require('node:child_process');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');

const root = process.argv[2];
const launcher = path.join(root, 'build/reviewer/distribution/bin/interlis-compiler');
const out = path.join(root, 'build/reviewer/probes/results');
fs.mkdirSync(out, { recursive: true });
const dir = fs.mkdtempSync(path.join(out, 'run-'));
const homeShared = path.join(dir, 'home-shared');
const homeFresh = path.join(dir, 'home-fresh');
fs.mkdirSync(homeShared); fs.mkdirSync(homeFresh);
const input = path.join(dir, 'Projekt.ili');
fs.writeFileSync(input, 'INTERLIS 2.4; MODEL Projekt AT "x" VERSION "1" = IMPORTS Basis; END Projekt.');
const model = 'INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
const index = '<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';

let mode = 'strong';
const requests = [];
const cases = [];
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const body = url.pathname === '/ilimodels.xml' ? index : url.pathname === '/Basis.ili' ? model : null;
  if (body === null) { requests.push({ path: url.pathname, status: 404 }); res.writeHead(404).end(); return; }
  const tag = '"' + crypto.createHash('sha256').update(body).digest('hex') + '"';
  const conditional = req.headers['if-none-match'];
  const isModel = url.pathname === '/Basis.ili';
  if (isModel && mode === 'bad304' && conditional) { requests.push({ path: url.pathname, status: 304, tag: null }); res.writeHead(304).end(); return; }
  if (isModel && mode === 'always304') { requests.push({ path: url.pathname, status: 304, tag: null }); res.writeHead(304).end(); return; }
  if (mode === 'no-store') res.setHeader('Cache-Control', 'no-store');
  res.setHeader('ETag', tag);
  const status = conditional === tag ? 304 : 200;
  requests.push({ path: url.pathname, status, conditional: conditional || null });
  res.writeHead(status).end(status === 200 ? body : undefined);
});

function run(name, args, expected, home) {
  const start = requests.length;
  return new Promise((resolve, reject) => {
    const child = cp.spawn(launcher, args, {
      cwd: dir,
      env: { ...process.env, JAVA_OPTS: '-Duser.home=' + home },
      stdio: ['ignore', 'pipe', 'pipe']
    });
    let stdout = '', stderr = '';
    child.stdout.on('data', b => stdout += b);
    child.stderr.on('data', b => stderr += b);
    const timer = setTimeout(() => { child.kill('SIGTERM'); reject(new Error('timeout ' + name)); }, 60000);
    child.on('error', reject);
    child.on('close', (exit, signal) => {
      clearTimeout(timer);
      const result = { name, args, exit, signal, stdout, stderr, http: requests.slice(start) };
      cases.push(result);
      fs.writeFileSync(path.join(out, name + '.json'), JSON.stringify(result, null, 2));
      if (expected !== undefined) assert.equal(exit, expected, name + ': exit\n' + stderr);
      resolve(result);
    });
  });
}

const infos = s => s.split('\n').filter(l => l.includes('[ILI-RES-0010]'));
const lines = s => s.split('\n').filter(Boolean);

(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const base = 'http://127.0.0.1:' + server.address().port + '/';
  const common = ['--repository', base, input];

  // 1. Cold: downloads then completion-check 304s; every actual access once.
  const cold = await run('cold', ['check', ...common, '--verbose', '--write-lock', path.join(dir, 'lock.xml')], 0, homeShared);
  assert.equal(infos(cold.stderr).length, 4);
  assert.deepEqual(cold.http.map(r => r.status), [200, 200, 304, 304]);
  const coldI = infos(cold.stderr);
  assert.ok(coldI.every(l => l.startsWith("INFO [ILI-RES-0010] Cache access for source '")));
  assert.equal(coldI.filter(l => l.includes('source downloaded')).length, 2);
  assert.equal(coldI.filter(l => l.includes('content used from cache (HTTP 304)')).length, 2);
  assert.ok(coldI.some(l => l.includes("'" + base + "ilimodels.xml'")));
  assert.ok(coldI.some(l => l.includes("'" + base + "Basis.ili'")));

  // 2. Warm: all four accesses are confirmed 304, no JSON or non-verbose leakage.
  const warm = await run('warm', ['check', ...common, '--verbose', '--warnings', 'reject'], 0, homeShared);
  assert.equal(infos(warm.stderr).length, 4);
  assert.ok(warm.http.every(r => r.status === 304));
  assert.ok(infos(warm.stderr).every(l => l.includes('content used from cache (HTTP 304)')));

  const compact = await run('compact', ['check', ...common], 0, homeShared);
  assert.equal(infos(compact.stderr).length, 0);

  // Verbose presentation must not change a command's exported artifact.
  const compilePlain = await run('compile-plain', ['compile', ...common], 0, homeShared);
  const compileVerbose = await run('compile-verbose', ['compile', ...common, '--verbose'], 0, homeShared);
  assert.equal(compileVerbose.stdout, compilePlain.stdout, 'verbose changed compile stdout');
  assert.equal(infos(compilePlain.stderr).length, 0);
  assert.equal(infos(compileVerbose.stderr).length, 4);
  const diagramPlain = await run('diagram-plain', ['diagram', ...common, '--format', 'mermaid'], 0, homeShared);
  const diagramVerbose = await run('diagram-verbose', ['diagram', ...common, '--format', 'mermaid', '--verbose'], 0, homeShared);
  assert.ok(diagramPlain.stdout.length > 0, 'diagram produced no export');
  assert.equal(diagramVerbose.stdout, diagramPlain.stdout, 'verbose changed diagram export');
  assert.equal(infos(diagramPlain.stderr).length, 0);
  assert.equal(infos(diagramVerbose.stderr).length, 4);

  const json = await run('json', ['check', ...common, '--json'], 0, homeShared);
  const jsonVerbose = await run('json-verbose', ['check', ...common, '--json', '--verbose'], 0, homeShared);
  assert.equal(infos(jsonVerbose.stderr).length, 0);
  assert.equal(jsonVerbose.stderr, json.stderr);
  assert.deepEqual(JSON.parse(jsonVerbose.stdout), JSON.parse(json.stdout));
  assert.ok(!jsonVerbose.stdout.includes('ILI-RES-0010'));

  const de = await run('de', ['check', ...common, '--verbose', '--diagnostic-language', 'de'], 0, homeShared);
  assert.ok(de.stderr.includes('Information [ILI-RES-0010]'));
  assert.ok(de.stderr.includes('Inhalt aus Cache verwendet (HTTP 304)'));
  assert.ok(!de.stderr.includes('INFO [ILI-RES-0010]'));

  const color = await run('color', ['check', ...common, '--verbose', '--color', 'always'], 0, homeShared);
  assert.ok(color.stderr.includes('\x1b[36mINFO\x1b[0m'));
  assert.ok(color.stderr.includes('\x1b[90mILI-RES-0010\x1b[0m'));
  const never = await run('color-never', ['check', ...common, '--verbose', '--color', 'never'], 0, homeShared);
  assert.ok(!never.stderr.includes('\x1b'));

  // 3. Unconfirmed 304: neutral miss then download, no invented hit/retry promise.
  mode = 'bad304';
  const unconfirmed = await run('unconfirmed', ['check', ...common, '--verbose'], 0, homeShared);
  const uModel = infos(unconfirmed.stderr).filter(l => l.includes('Basis.ili'));
  assert.ok(uModel.some(l => l.includes('cache could not be used')));
  assert.ok(uModel.some(l => l.includes('source downloaded')));
  assert.ok(!uModel.some(l => l.includes('content used from cache')));
  assert.ok(!infos(unconfirmed.stderr).some(l => /retry|erneut|erneut|wiederholt/i.test(l)));
  assert.ok(unconfirmed.http.some(r => r.path === '/Basis.ili' && r.status === 304));
  assert.ok(unconfirmed.http.some(r => r.path === '/Basis.ili' && r.status === 200));

  // 4. Second 304 (never confirmable): two neutral misses, no hit, run fails.
  mode = 'always304';
  const second = await run('second', ['check', ...common, '--verbose'], 1, homeShared);
  const sModel = infos(second.stderr).filter(l => l.includes('Basis.ili'));
  assert.equal(sModel.filter(l => l.includes('cache could not be used')).length, 2);
  assert.equal(sModel.filter(l => l.includes('content used from cache')).length, 0);
  assert.equal(second.http.filter(r => r.path === '/Basis.ili' && r.status === 304).length, 2);

  // 5. Both offline paths, zero network; optional site vs required index.
  mode = 'strong';
  const offNoLock = await run('offline-no-lock', ['compile', ...common, '--offline', '--verbose'], 0, homeShared);
  const offLock = await run('offline-lock', ['compile', ...common, '--offline', '--read-lock', path.join(dir, 'lock.xml'), '--verbose'], 0, homeShared);
  for (const r of [offNoLock, offLock]) {
    assert.equal(r.http.length, 0);
    assert.equal(infos(r.stderr).length, 2);
    assert.ok(infos(r.stderr).every(l => l.includes('content used from cache offline')));
  }
  const optional = await run('optional-site', ['check', '--repository', base, '--root', 'Absent', '--offline', '--verbose'], 1, homeShared);
  assert.equal(optional.http.length, 0);
  assert.ok(optional.stderr.includes('ilisite.xml'));
  assert.ok(optional.stderr.includes('source unavailable in offline cache'));
  assert.ok(optional.stderr.includes('ILI-RES-0001'));
  assert.ok(!optional.stderr.includes('ILI-RES-0003'));
  const required = await run('required-index', ['check', ...common, '--offline', '--verbose'], 1, homeFresh);
  assert.equal(required.http.length, 0);
  assert.ok(required.stderr.includes('ilimodels.xml'));
  assert.ok(required.stderr.includes('source unavailable in offline cache'));
  assert.ok(required.stderr.includes('ILI-RES-0003'), required.stderr);

  // 6. Corrupt cached bytes must yield a neutral miss and a fresh download, never a hit.
  const homeCorrupt = path.join(dir, 'home-corrupt');
  fs.mkdirSync(homeCorrupt);
  await run('corrupt-warm', ['check', ...common, '--verbose', '--write-lock', path.join(dir, 'corrupt-lock.xml')], 0, homeCorrupt);
  const digest = crypto.createHash('sha256').update(model).digest('hex');
  const contentFile = path.join(homeCorrupt, '.interlis-cache', digest);
  assert.ok(fs.existsSync(contentFile), 'expected cached model bytes at ' + contentFile);
  fs.writeFileSync(contentFile, 'corrupt');
  const corrupt = await run('corrupt-cache', ['check', ...common, '--verbose'], 0, homeCorrupt);
  const cModel = infos(corrupt.stderr).filter(l => l.includes('Basis.ili'));
  assert.ok(cModel.some(l => l.includes('cache could not be used')), corrupt.stderr);
  assert.ok(cModel.some(l => l.includes('source downloaded')), corrupt.stderr);
  assert.ok(cModel.some(l => l.includes('content used from cache (HTTP 304)')), corrupt.stderr);
  assert.ok(cModel.indexOf(cModel.find(l => l.includes('cache could not be used')))
          < cModel.indexOf(cModel.find(l => l.includes('source downloaded'))), 'miss must precede re-download');
  assert.ok(fs.readFileSync(contentFile).length > 10, 'valid bytes were not restored');

  // 7. Cache disabled and no-store: download wording only, never a hit.
  const noCache = await run('no-cache', ['check', ...common, '--no-cache', '--verbose'], 0, homeShared);
  assert.equal(infos(noCache.stderr).length, 4);
  assert.ok(infos(noCache.stderr).every(l => l.includes('source downloaded')));
  assert.ok(!noCache.stderr.includes('content used from cache'));
  mode = 'no-store';
  const noStore = await run('no-store', ['check', ...common, '--cache', path.join(dir, 'nostore-cache'), '--verbose'], 0, homeShared);
  assert.equal(infos(noStore.stderr).length, 4);
  assert.ok(infos(noStore.stderr).every(l => l.includes('source downloaded')));
  assert.ok(!noStore.stderr.includes('content used from cache'));

  const summary = { passed: true, node: process.version, launcher, cases: cases.map(c => ({ name: c.name, exit: c.exit, infos: infos(c.stderr).length, http: c.http.map(r => r.status) })) };
  fs.writeFileSync(path.join(out, 'summary.json'), JSON.stringify(summary, null, 2));
  console.log('COUNTERPROBES_PASS ' + cases.length);
})().catch(e => { console.error(e); fs.writeFileSync(path.join(out, 'failed.json'), JSON.stringify({ error: String(e && e.stack || e), cases }, null, 2)); process.exitCode = 1; }).finally(() => server.close());
