'use strict';
// Independent reviewer counterprobes. Node only; local HTTP server + provided CLI distribution.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const cp = require('node:child_process');

const root = process.cwd();
const baseDir = path.join(root, 'build/reviewer/counterprobes/own-probes');
const work = path.join(baseDir, 'work');
const cli = path.join(root, 'build/reviewer/distribution/bin/interlis-compiler');
fs.rmSync(work, { recursive: true, force: true });
fs.mkdirSync(work, { recursive: true });

const A = 'INTERLIS 2.4; MODEL Dep AT "https://example.org" VERSION "1" = END Dep.';
const B = 'INTERLIS 2.4; MODEL Dep AT "https://example.org" VERSION "1" = DOMAIN D = TEXT; END Dep.';
const BASIS = 'INTERLIS 2.4; MODEL Basis AT "https://example.org" VERSION "1" = END Basis.';
const INDEX_DEP = '<TRANSFER><ModelMetadata><Name>Dep</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Dep.ili</File></ModelMetadata></TRANSFER>';
const INDEX_BASIS = '<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
const sha = (s) => crypto.createHash('sha256').update(Buffer.from(s, 'utf8')).digest('hex');
const A_SHA = sha(A);
const B_SHA = sha(B);
const BASIS_SHA = sha(BASIS);

const state = {
  optional: { index: INDEX_BASIS, model: BASIS },
  weakChange: { model: A, switched: false, etag: 'W/"dep-logical-1"' },
  weakLock: { model: A, etag: 'W/"dep-logical-1"' },
  lmChange: { model: A, switched: false, lastModified: 'Sun, 01 Nov 2026 16:43:26 GMT' },
  strong: { model: A, etag: '"model-v1"', mode: 'normal' },
};
let requests = 0;
let bodies = 0;
const log = [];

function send(res, status, body, headers = {}, meta = {}) {
  const bytes = body == null ? null : Buffer.from(body, 'utf8');
  for (const [k, v] of Object.entries(headers)) res.setHeader(k, v);
  res.statusCode = status;
  if (bytes == null) res.end();
  else { res.setHeader('Content-Length', bytes.length); res.end(bytes); }
  if (bytes != null) bodies += 1;
  log.push({
    path: meta.path,
    status,
    ifNoneMatch: meta.ifNoneMatch || null,
    ifModifiedSince: meta.ifModifiedSince || null,
    responseEtag: headers.ETag || null,
    responseLastModified: headers['Last-Modified'] || null,
    bodySent: bytes == null ? 0 : bytes.length,
    bodySha256: bytes == null ? null : sha(bytes.toString('utf8')),
    variant: meta.variant || null,
  });
}

function serveStrong(req, res, body, etag, p, variant) {
  const inm = req.headers['if-none-match'] || null;
  if (inm === etag) send(res, 304, null, { ETag: etag }, { path: p, ifNoneMatch: inm, variant });
  else send(res, 200, body, { ETag: etag }, { path: p, ifNoneMatch: inm, variant });
}

function serveWeak(req, res, body, etag, p, variant) {
  const inm = req.headers['if-none-match'] || null;
  const opaque = (v) => v.startsWith('W/') ? v.slice(2) : v;
  const weakEqual = inm && opaque(inm) === opaque(etag);
  if (weakEqual) send(res, 304, null, { ETag: etag }, { path: p, ifNoneMatch: inm, variant });
  else send(res, 200, body, { ETag: etag }, { path: p, ifNoneMatch: inm, variant });
}

const server = http.createServer((req, res) => {
  requests += 1;
  const p = new URL(req.url, 'http://127.0.0.1').pathname;
  const inm = req.headers['if-none-match'] || null;
  const ims = req.headers['if-modified-since'] || null;
  const parts = p.split('/').filter(Boolean);
  const repo = parts[0];
  const file = parts[1];
  if (!repo || !file) { send(res, 404, null, {}, { path: p }); return; }

  if (repo === 'optional') {
    if (file === 'ilimodels.xml') return serveStrong(req, res, state.optional.index, '"opt-index"', p, 'index');
    if (file === 'Basis.ili') return serveStrong(req, res, state.optional.model, '"basis-v1"', p, 'basis');
    send(res, 404, null, {}, { path: p, ifNoneMatch: inm });
    return;
  }
  if (repo === 'weak-change') {
    if (file === 'ilimodels.xml') return serveStrong(req, res, INDEX_DEP, '"weak-index"', p, 'index');
    if (file === 'Dep.ili') {
      const s = state.weakChange;
      const variant = s.switched ? 'B' : 'A';
      const body = s.switched ? B : A;
      if (inm) {
        // Correct weak comparison: if the cache incorrectly sends W/"dep-logical-1",
        // this server returns 304 exactly as the old implementation allowed.
        if (inm && ((v) => v.startsWith('W/') ? v.slice(2) : v)(inm) === s.etag.slice(2)) {
          log.push({ path: p, status: 304, ifNoneMatch: inm, ifModifiedSince: ims, responseEtag: s.etag, bodySent: 0, bodySha256: null, variant });
          res.setHeader('ETag', s.etag); res.statusCode = 304; res.end(); return;
        }
      }
      const isFirstBody = !s.switched;
      send(res, 200, body, { ETag: s.etag }, { path: p, ifNoneMatch: inm, variant });
      if (isFirstBody) s.switched = true;
      return;
    }
  }
  if (repo === 'weak-lock') {
    if (file === 'ilimodels.xml') return serveStrong(req, res, INDEX_DEP, '"weak-index"', p, 'index');
    if (file === 'Dep.ili') {
      const s = state.weakLock;
      const variant = s.model === A ? 'A' : 'B';
      if (inm && ((v) => v.startsWith('W/') ? v.slice(2) : v)(inm) === s.etag.slice(2)) {
        log.push({ path: p, status: 304, ifNoneMatch: inm, ifModifiedSince: ims, responseEtag: s.etag, bodySent: 0, bodySha256: null, variant });
        res.setHeader('ETag', s.etag); res.statusCode = 304; res.end(); return;
      }
      send(res, 200, s.model, { ETag: s.etag }, { path: p, ifNoneMatch: inm, variant });
      return;
    }
  }
  if (repo === 'lm-change') {
    if (file === 'ilimodels.xml') return serveStrong(req, res, INDEX_DEP, '"lm-index"', p, 'index');
    if (file === 'Dep.ili') {
      const s = state.lmChange;
      const variant = s.switched ? 'B' : 'A';
      const body = s.switched ? B : A;
      if (ims && Date.parse(ims) >= Date.parse(s.lastModified)) {
        log.push({ path: p, status: 304, ifNoneMatch: inm, ifModifiedSince: ims, responseEtag: null, responseLastModified: s.lastModified, bodySent: 0, bodySha256: null, variant });
        res.setHeader('Last-Modified', s.lastModified); res.statusCode = 304; res.end(); return;
      }
      const isFirstBody = !s.switched;
      send(res, 200, body, { 'Last-Modified': s.lastModified }, { path: p, ifNoneMatch: inm, ifModifiedSince: ims, variant });
      if (isFirstBody) s.switched = true;
      return;
    }
  }
  if (repo === 'strong') {
    const s = state.strong;
    if (file === 'ilimodels.xml') return serveStrong(req, res, INDEX_DEP, '"strong-index"', p, 'index');
    if (file === 'Dep.ili') {
      if (s.mode === 'mismatch304' && inm) {
        // A 304 that does not confirm the requested strong tag must not authorize old bytes.
        log.push({ path: p, status: 304, ifNoneMatch: inm, ifModifiedSince: ims, responseEtag: '"other"', bodySent: 0, bodySha256: null, variant: 'A' });
        res.setHeader('ETag', '"other"'); res.statusCode = 304; res.end(); return;
      }
      if (s.mode === 'strongToWeak' && inm) {
        // Transition: old cache has a strong tag, new representation only a weak one.
        log.push({ path: p, status: 304, ifNoneMatch: inm, ifModifiedSince: ims, responseEtag: 'W/"model-v2"', bodySent: 0, bodySha256: null, variant: 'B' });
        res.setHeader('ETag', 'W/"model-v2"'); res.statusCode = 304; res.end(); return;
      }
      const etag = s.mode === 'strongToWeak' ? 'W/"model-v2"' : s.etag;
      if (!(s.mode === 'mismatch304' || s.mode === 'strongToWeak') && inm === etag) {
        send(res, 304, null, { ETag: etag }, { path: p, ifNoneMatch: inm, variant: 'A' });
      } else send(res, 200, s.model, { ETag: etag }, { path: p, ifNoneMatch: inm, variant: s.model === A ? 'A' : 'B' });
      return;
    }
  }
  send(res, 404, null, {}, { path: p, ifNoneMatch: inm });
});

function run(name, args, home) {
  const beforeRequests = requests, beforeBodies = bodies, beforeLog = log.length;
  fs.mkdirSync(home, { recursive: true });
  const child = cp.spawn(cli, args, {
    cwd: work,
    env: { ...process.env, HOME: home, JAVA_OPTS: `-Duser.home=${home}` },
  });
  let stdout = '', stderr = '';
  child.stdout.on('data', (c) => { stdout += c.toString('utf8'); });
  child.stderr.on('data', (c) => { stderr += c.toString('utf8'); });
  return new Promise((resolve) => {
    const timer = setTimeout(() => { child.kill('SIGKILL'); }, 60000);
    child.on('close', (code) => {
      clearTimeout(timer);
      const entries = log.slice(beforeLog);
      let json = null;
      try { json = JSON.parse(stdout.trim()); } catch (_) { }
      resolve({
        name,
        args,
        exitCode: code,
        stdout,
        stderr,
        parsedStatus: json && json.status,
        diagnostics: json && json.diagnostics ? json.diagnostics.map((d) => ({ code: d.code, severity: d.severity, message: d.message, arguments: d.arguments })) : [],
        models: json && json.resolution && json.resolution.models ? json.resolution.models : [],
        steps: json && json.resolution && json.resolution.steps ? json.resolution.steps : [],
        http: {
          requests: requests - beforeRequests,
          bodies: bodies - beforeBodies,
          entries,
        },
      });
    });
  });
}

function contentPath(home, text) { return path.join(home, '.interlis-cache', sha(text)); }

async function main() {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}/`;
  const results = { origin, sha256: { A: A_SHA, B: B_SHA, basis: BASIS_SHA, indexDep: sha(INDEX_DEP), indexBasis: sha(INDEX_BASIS) }, probes: {} };

  // P1: optional ilisite.xml missing offline; then required index absent offline.
  {
    const home = path.join(work, 'home-optional');
    const repo = origin + 'optional/';
    const warm = await run('optional-warm-online', ['check', '--root', 'Basis', '--repository', repo, '--json'], home);
    const offline = await run('optional-offline-missing-model', ['check', '--root', 'Absent', '--repository', repo, '--offline', '--json'], home);
    const indexBody = contentPath(home, INDEX_BASIS);
    const indexBodyExisted = fs.existsSync(indexBody);
    const siteMetadata = path.join(home, '.interlis-cache', 'metadata-v1', sha(repo + 'ilisite.xml') + '.xml');
    const siteBodyPresent = false;
    fs.rmSync(indexBody, { force: true });
    const required = await run('required-index-body-removed-offline', ['check', '--root', 'Absent', '--repository', repo, '--offline', '--json'], home);
    results.probes.optionalSite = { warm, offline, required, paths: { indexBody, siteMetadata, indexBodyExisted, siteMetadataExisted: fs.existsSync(siteMetadata) } };
  }

  // P2: weak ETag change during the read window -> CHANGED_SOURCE; no conditional model GET.
  {
    const home = path.join(work, 'home-weak-change');
    const lock = path.join(work, 'weak-change.lock.xml');
    fs.rmSync(lock, { force: true });
    const r = await run('weak-change-write-lock', ['check', '--root', 'Dep', '--repository', origin + 'weak-change/', '--cache', path.join(home, '.interlis-cache'), '--write-lock', lock, '--json'], home);
    results.probes.weakChange = { run: r, lockExists: fs.existsSync(lock), lock: fs.existsSync(lock) ? fs.readFileSync(lock, 'utf8') : null };
  }

  // P3: weak ETag with cached old lock; online LOCK_MISMATCH, source PIN_MISMATCH, offline exact/no HTTP.
  {
    const home = path.join(work, 'home-weak-lock');
    const lock = path.join(work, 'weak-lock.lock.xml');
    fs.rmSync(lock, { force: true });
    const cache = path.join(home, '.interlis-cache');
    const first = await run('weak-lock-first-write', ['check', '--root', 'Dep', '--repository', origin + 'weak-lock/', '--cache', cache, '--write-lock', lock, '--json'], home);
    state.weakLock.model = B;
    const mismatch = await run('weak-lock-online-mismatch', ['check', '--root', 'Dep', '--repository', origin + 'weak-lock/', '--cache', cache, '--read-lock', lock, '--json'], home);
    const pinMismatch = await run('weak-source-pin-online-mismatch', ['check', '--root', 'Dep', '--repository', origin + 'weak-lock/', '--cache', cache, '--source-pin', origin + 'weak-lock/Dep.ili', A_SHA, '--json'], home);
    const beforeOffline = requests;
    const offline = await run('weak-lock-offline-replay', ['check', '--root', 'Dep', '--repository', origin + 'weak-lock/', '--cache', cache, '--read-lock', lock, '--offline', '--json'], home);
    results.probes.weakLock = { first, mismatch, pinMismatch, offline, networkDeltaOffline: requests - beforeOffline, lockExists: fs.existsSync(lock), lock: fs.existsSync(lock) ? fs.readFileSync(lock, 'utf8') : null };
  }

  // P4: Last-Modified same-second change -> CHANGED_SOURCE; no If-Modified-Since.
  {
    const home = path.join(work, 'home-lm-change');
    const lock = path.join(work, 'lm-change.lock.xml');
    fs.rmSync(lock, { force: true });
    const r = await run('lm-change-write-lock', ['check', '--root', 'Dep', '--repository', origin + 'lm-change/', '--cache', path.join(home, '.interlis-cache'), '--write-lock', lock, '--json'], home);
    results.probes.lmChange = { run: r, lockExists: fs.existsSync(lock) };
  }

  // P5: strong ETag 304 hit, unconfirmed 304 rejected, strong->weak transition not trusted.
  {
    const home = path.join(work, 'home-strong');
    const cache = path.join(home, '.interlis-cache');
    const first = await run('strong-first-write', ['check', '--root', 'Dep', '--repository', origin + 'strong/', '--cache', cache, '--write-lock', path.join(work, 'strong.lock.xml'), '--json'], home);
    const hit = await run('strong-cache-hit', ['check', '--root', 'Dep', '--repository', origin + 'strong/', '--cache', cache, '--json'], home);
    state.strong.mode = 'mismatch304';
    const mismatch304 = await run('strong-unconfirmed-304-reloads', ['check', '--root', 'Dep', '--repository', origin + 'strong/', '--cache', cache, '--json'], home);
    state.strong.mode = 'strongToWeak';
    state.strong.model = B;
    const transition = await run('strong-to-weak-transition', ['check', '--root', 'Dep', '--repository', origin + 'strong/', '--cache', cache, '--json'], home);
    results.probes.strong = { first, hit, mismatch304, transition };
  }

  results.serverTotals = { requests, bodies };
  fs.writeFileSync(path.join(baseDir, 'http.jsonl'), log.map((e) => JSON.stringify(e)).join('\n') + '\n');
  fs.writeFileSync(path.join(baseDir, 'results.json'), JSON.stringify(results, null, 2) + '\n');
  console.log(JSON.stringify({
    sha256: results.sha256,
    serverTotals: results.serverTotals,
    summary: Object.fromEntries(Object.entries(results.probes).map(([k, v]) => [k, Object.fromEntries(Object.entries(v).filter(([kk]) => kk !== 'paths' && kk !== 'lock').map(([kk, vv]) => [kk, vv && vv.exitCode !== undefined ? { exitCode: vv.exitCode, status: vv.parsedStatus, codes: vv.diagnostics.map((d) => d.code), http: { requests: vv.http.requests, bodies: vv.http.bodies } } : vv]))])),
  }, null, 2));
  server.close();
}

main().catch((error) => { console.error(error); try { server.close(); } catch (_) { } process.exit(1); });
