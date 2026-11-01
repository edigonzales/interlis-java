'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const cp = require('node:child_process');

const root = process.cwd();
const base = path.join(root, 'build/reviewer/counterprobes/final-probes');
const cli = path.join(root, 'build/reviewer/distribution/bin/interlis-compiler');

fs.rmSync(path.join(base, 'work'), { recursive: true, force: true });
fs.mkdirSync(path.join(base, 'work'), { recursive: true });
const work = path.join(base, 'work');

const sha256 = (text) => crypto.createHash('sha256').update(text, 'utf8').digest('hex');

const basicModelV1 = 'INTERLIS 2.4; MODEL Basis AT "https://example.org" VERSION "1" = END Basis.';
const basicModelV2 = 'INTERLIS 2.4; MODEL Basis AT "https://example.org" VERSION "1" = DOMAIN D = TEXT; END Basis.';
const bothModel = 'INTERLIS 2.4; MODEL Both AT "https://example.org" VERSION "1" = END Both.';
const modifiedModel = 'INTERLIS 2.4; MODEL Modified AT "https://example.org" VERSION "1" = END Modified.';
const noStoreModel = 'INTERLIS 2.4; MODEL NoStore AT "https://example.org" VERSION "1" = END NoStore.';
const varyModel = 'INTERLIS 2.4; MODEL VaryModel AT "https://example.org" VERSION "1" = END VaryModel.';

const state = {
  requests: 0,
  bodies: 0,
  log: [],
  basicModel: basicModelV1,
  basicEtag: '"basic-v1"',
  evilDtdRequests: 0
};

const index = (name) => `<TRANSFER><ModelMetadata><Name>${name}</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>${name}.ili</File></ModelMetadata></TRANSFER>`;

function send(res, status, body, headers = {}) {
  const bytes = body == null ? null : Buffer.from(body, 'utf8');
  for (const [k, v] of Object.entries(headers)) res.setHeader(k, v);
  res.statusCode = status;
  if (bytes == null) { res.end(); return; }
  res.setHeader('Content-Length', bytes.length);
  res.end(bytes);
}

function serveModel(req, res, model, etag, extraHeaders = {}, stateRef = null) {
  const inm = req.headers['if-none-match'] || null;
  const ims = req.headers['if-modified-since'] || null;
  state.log.push({
    path: req.url,
    ifNoneMatch: inm,
    ifModifiedSince: ims,
    etag: etag || null,
    lastModified: extraHeaders['Last-Modified'] || null
  });
  if (etag) {
    if (inm === etag) { send(res, 304, null, { ETag: etag, ...extraHeaders }); return; }
  } else if (ims) {
    // Last-Modified revalidation for the fixed synthetic date.
    if (ims === 'Wed, 21 Oct 2015 07:28:00 GMT') { send(res, 304, null, { ...extraHeaders }); return; }
  }
  state.bodies += 1;
  send(res, 200, model, { ...(etag ? { ETag: etag } : {}), ...extraHeaders });
}

const server = http.createServer((req, res) => {
  state.requests += 1;
  const pathname = new URL(req.url, 'http://127.0.0.1').pathname;
  if (pathname === '/evil.dtd') {
    state.evilDtdRequests += 1;
    send(res, 200, '<!ELEMENT interlis-cache ANY>');
    return;
  }
  const parts = pathname.split('/').filter(Boolean);
  const repo = parts[0];
  const file = parts[1];
  if (!file) { send(res, 404, null); return; }
  try {
    if (repo === 'basic') {
      if (file === 'ilimodels.xml') { serveModel(req, res, index('Basis'), '"basic-index"', { 'Last-Modified': 'Wed, 21 Oct 2015 07:28:00 GMT' }); return; }
      if (file === 'Basis.ili') { serveModel(req, res, state.basicModel, state.basicEtag); return; }
    }
    if (repo === 'both') {
      if (file === 'ilimodels.xml') { serveModel(req, res, index('Both'), '"both-index"', { 'Last-Modified': 'Wed, 21 Oct 2015 07:28:00 GMT' }); return; }
      if (file === 'Both.ili') { serveModel(req, res, bothModel, '"both-model"', { 'Last-Modified': 'Wed, 21 Oct 2015 07:28:00 GMT' }); return; }
    }
    if (repo === 'modified') {
      if (file === 'ilimodels.xml') { serveModel(req, res, index('Modified'), null, { 'Last-Modified': 'Wed, 21 Oct 2015 07:28:00 GMT' }); return; }
      if (file === 'Modified.ili') { serveModel(req, res, modifiedModel, null, { 'Last-Modified': 'Wed, 21 Oct 2015 07:28:00 GMT' }); return; }
    }
    if (repo === 'nostore') {
      if (file === 'ilimodels.xml') { serveModel(req, res, index('NoStore'), '"nostore-index"'); return; }
      if (file === 'NoStore.ili') { serveModel(req, res, noStoreModel, '"nostore-model"', { 'Cache-Control': 'no-store' }); return; }
    }
    if (repo === 'vary') {
      if (file === 'ilimodels.xml') { serveModel(req, res, index('VaryModel'), '"vary-index"'); return; }
      if (file === 'VaryModel.ili') { serveModel(req, res, varyModel, '"vary-model"', { Vary: 'Accept-Language' }); return; }
    }
    send(res, 404, null);
  } catch (error) {
    send(res, 500, String(error));
  }
});

const results = [];
const checks = [];
function check(name, condition, detail) {
  checks.push({ name, passed: !!condition, detail });
}
async function run(name, args, home, cwd = work) {
  const before = { requests: state.requests, bodies: state.bodies, log: state.log.length };
  const child = cp.spawn(cli, args, {
    cwd,
    env: { ...process.env, JAVA_OPTS: `-Duser.home=${home}`, HOME: home }
  });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', (chunk) => { stdout += chunk.toString('utf8'); });
  child.stderr.on('data', (chunk) => { stderr += chunk.toString('utf8'); });
  const result = await new Promise((resolve) => {
    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      resolve({ status: null, signal: 'SIGKILL', error: 'timeout after 60000 ms' });
    }, 60000);
    child.on('close', (code, signal) => { clearTimeout(timer); resolve({ status: code, signal, error: null }); });
    child.on('error', (error) => { clearTimeout(timer); resolve({ status: null, signal: null, error: String(error) }); });
  });
  const after = { requests: state.requests, bodies: state.bodies, log: state.log.length };
  const record = {
    name,
    args,
    home,
    exit: result.status,
    signal: result.signal || null,
    error: result.error || null,
    stdout,
    stderr,
    httpDelta: { requests: after.requests - before.requests, bodies: after.bodies - before.bodies },
    requestLog: state.log.slice(before.log)
  };
  results.push(record);
  return record;
}
function metadataPath(home, url) { return path.join(home, '.interlis-cache', 'metadata-v1', sha256(url) + '.xml'); }
function contentPath(home, text) { return path.join(home, '.interlis-cache', sha256(text)); }
function exists(p) { return fs.existsSync(p); }
function writeProject(name, imported) {
  fs.writeFileSync(path.join(work, name), `INTERLIS 2.4; MODEL Projekt AT "https://example.org" VERSION "1" = IMPORTS ${imported}; END Projekt.`);
}

async function main() {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const origin = `http://127.0.0.1:${port}/`;
  const basic = origin + 'basic/';
  const both = origin + 'both/';
  const modified = origin + 'modified/';
  const nostore = origin + 'nostore/';
  const vary = origin + 'vary/';

  writeProject('Projekt.ili', 'Basis');
  writeProject('BothProject.ili', 'Both');
  writeProject('ModifiedProject.ili', 'Modified');
  writeProject('NoStoreProject.ili', 'NoStore');
  writeProject('VaryProject.ili', 'VaryModel');

  const homeBasic = path.join(work, 'home-basic');
  const homeBoth = path.join(work, 'home-both');
  const homeModified = path.join(work, 'home-modified');
  const homeNoStore = path.join(work, 'home-nostore');
  const homeVary = path.join(work, 'home-vary');
  const homeExplain = path.join(work, 'home-explain');
  const homeNoCache = path.join(work, 'home-nocache');
  const homeLock = path.join(work, 'home-lock');

  // 1. Default cache, first download and 304 revalidation.
  const first = await run('basic-first-download-lock', ['check', 'Projekt.ili', '--repository', basic, '--write-lock', 'basic.lock'], homeBasic);
  check('basic-first exit 0', first.exit === 0, first.stderr);
  check('basic-first two bodies', first.httpDelta.bodies === 2, JSON.stringify(first.httpDelta));
  check('default cache metadata created', exists(path.join(homeBasic, '.interlis-cache', 'metadata-v1')), homeBasic);

  const second = await run('basic-second-online-304', ['check', 'Projekt.ili', '--repository', basic], homeBasic);
  check('basic-second exit 0', second.exit === 0, second.stderr);
  check('basic-second no new body', second.httpDelta.bodies === 0, JSON.stringify(second.httpDelta));
  const basicConditional = second.requestLog.filter((e) => e.path.includes('/basic/'));
  check('basic-second sent If-None-Match everywhere', basicConditional.length > 0 && basicConditional.every((e) => e.ifNoneMatch !== null), JSON.stringify(basicConditional));
  check('basic-second never sent If-Modified-Since', basicConditional.every((e) => e.ifModifiedSince === null), JSON.stringify(basicConditional));

  // 2. Offline without and with lock must not issue HTTP.
  const offlineNoLock = await run('basic-offline-without-lock', ['check', 'Projekt.ili', '--repository', basic, '--offline'], homeBasic);
  check('offline without lock exit 0', offlineNoLock.exit === 0, offlineNoLock.stderr);
  check('offline without lock zero HTTP', offlineNoLock.httpDelta.requests === 0, JSON.stringify(offlineNoLock.httpDelta));
  const offlineLock = await run('basic-offline-with-lock', ['compile', 'Projekt.ili', '--repository', basic, '--read-lock', 'basic.lock', '--offline'], homeBasic);
  check('offline with lock exit 0', offlineLock.exit === 0, offlineLock.stderr);
  check('offline with lock zero HTTP', offlineLock.httpDelta.requests === 0, JSON.stringify(offlineLock.httpDelta));

  // 3. --no-cache does not persist and offline fails with a concrete source.
  const noCache = await run('nocache-online', ['check', 'Projekt.ili', '--repository', basic, '--no-cache'], homeNoCache);
  check('no-cache online exit 0', noCache.exit === 0, noCache.stderr);
  check('no-cache online downloaded four bodies', noCache.httpDelta.bodies === 4, JSON.stringify(noCache.httpDelta));
  check('no-cache home absent', !exists(homeNoCache), homeNoCache);
  const noCacheOffline = await run('nocache-offline', ['check', 'Projekt.ili', '--repository', basic, '--offline', '--no-cache'], homeNoCache);
  check('no-cache offline exit 1', noCacheOffline.exit === 1, noCacheOffline.stderr);
  check('no-cache offline zero HTTP', noCacheOffline.httpDelta.requests === 0, JSON.stringify(noCacheOffline.httpDelta));

  // 4. Explain and custom path disk effects.
  const explain = await run('explain-default-fresh-home', ['check', '--explain-config'], homeExplain);
  check('explain exit 0', explain.exit === 0, explain.stderr);
  check('explain mentions injected home cache', explain.stdout.includes(path.join(homeExplain, '.interlis-cache')), explain.stdout);
  check('explain creates no home', !exists(homeExplain), homeExplain);
  const custom = await run('custom-cache-relative', ['check', 'Projekt.ili', '--repository', basic, '--cache', 'custom-cache'], path.join(work, 'home-custom'));
  check('custom cache exit 0', custom.exit === 0, custom.stderr);
  check('custom cache directory used', exists(path.join(work, 'custom-cache', 'metadata-v1')), path.join(work, 'custom-cache'));
  check('custom home absent', !exists(path.join(work, 'home-custom')), path.join(work, 'home-custom'));

  // 5. ETag is preferred over Last-Modified when both exist.
  const bothFirst = await run('both-first', ['check', 'BothProject.ili', '--repository', both], homeBoth);
  check('both-first exit 0', bothFirst.exit === 0, bothFirst.stderr);
  const bothSecond = await run('both-second', ['check', 'BothProject.ili', '--repository', both], homeBoth);
  check('both-second exit 0', bothSecond.exit === 0, bothSecond.stderr);
  check('both-second no new body', bothSecond.httpDelta.bodies === 0, JSON.stringify(bothSecond.httpDelta));
  const bothConditional = bothSecond.requestLog;
  check('both-second sent If-None-Match', bothConditional.length > 0 && bothConditional.every((e) => e.ifNoneMatch !== null), JSON.stringify(bothConditional));
  check('both-second did not send If-Modified-Since', bothConditional.every((e) => e.ifModifiedSince === null), JSON.stringify(bothConditional));

  // 6. Last-Modified fallback when no ETag exists.
  const modifiedFirst = await run('modified-first', ['check', 'ModifiedProject.ili', '--repository', modified], homeModified);
  check('modified-first exit 0', modifiedFirst.exit === 0, modifiedFirst.stderr);
  const modifiedSecond = await run('modified-second', ['check', 'ModifiedProject.ili', '--repository', modified], homeModified);
  check('modified-second exit 0', modifiedSecond.exit === 0, modifiedSecond.stderr);
  check('modified-second no new body', modifiedSecond.httpDelta.bodies === 0, JSON.stringify(modifiedSecond.httpDelta));
  check('modified-second sent If-Modified-Since', modifiedSecond.requestLog.length > 0 && modifiedSecond.requestLog.every((e) => e.ifModifiedSince !== null), JSON.stringify(modifiedSecond.requestLog));
  check('modified-second did not send If-None-Match', modifiedSecond.requestLog.every((e) => e.ifNoneMatch === null), JSON.stringify(modifiedSecond.requestLog));

  // 7. no-store must not produce a persistent hit or fallback.
  const noStoreFirst = await run('nostore-first', ['check', 'NoStoreProject.ili', '--repository', nostore], homeNoStore);
  check('no-store first exit 0', noStoreFirst.exit === 0, noStoreFirst.stderr);
  const noStoreUrl = nostore + 'NoStore.ili';
  check('no-store model metadata absent', !exists(metadataPath(homeNoStore, noStoreUrl)), metadataPath(homeNoStore, noStoreUrl));
  check('no-store model content absent', !exists(contentPath(homeNoStore, noStoreModel)), contentPath(homeNoStore, noStoreModel));
  const noStoreOffline = await run('nostore-offline', ['check', 'NoStoreProject.ili', '--repository', nostore, '--offline'], homeNoStore);
  check('no-store offline exit 1', noStoreOffline.exit === 1, noStoreOffline.stderr);
  check('no-store offline zero HTTP', noStoreOffline.httpDelta.requests === 0, JSON.stringify(noStoreOffline.httpDelta));
  check('no-store offline names source and OFFLINE', (noStoreOffline.stderr + noStoreOffline.stdout).includes('unavailable offline') && (noStoreOffline.stderr + noStoreOffline.stdout).includes('NoStore.ili'), noStoreOffline.stderr + noStoreOffline.stdout);

  // 8. Vary must not produce a persistent hit.
  const varyFirst = await run('vary-first', ['check', 'VaryProject.ili', '--repository', vary], homeVary);
  check('vary first exit 0', varyFirst.exit === 0, varyFirst.stderr);
  const varyUrl = vary + 'VaryModel.ili';
  check('vary model metadata absent', !exists(metadataPath(homeVary, varyUrl)), metadataPath(homeVary, varyUrl));
  check('vary model content absent', !exists(contentPath(homeVary, varyModel)), contentPath(homeVary, varyModel));
  const varyOffline = await run('vary-offline', ['check', 'VaryProject.ili', '--repository', vary, '--offline'], homeVary);
  check('vary offline exit 1', varyOffline.exit === 1, varyOffline.stderr);
  check('vary offline zero HTTP', varyOffline.httpDelta.requests === 0, JSON.stringify(varyOffline.httpDelta));

  // 9. Strict metadata parser: a DOCTYPE/external DTD is a miss and never fetched.
  const basicModelMd = metadataPath(homeBasic, basic + 'Basis.ili');
  const originalMd = fs.readFileSync(basicModelMd, 'utf8');
  const digest = sha256(state.basicModel);
  const malicious = `<!DOCTYPE interlis-cache SYSTEM "${origin}evil.dtd"><interlis-cache version="1" source="${basic + 'Basis.ili'}" response-source="${basic + 'Basis.ili'}" sha256="${digest}" etag="&quot;basic-v1&quot;"/>`;
  fs.writeFileSync(basicModelMd, malicious);
  const evilBefore = state.evilDtdRequests;
  const maliciousOffline = await run('basic-offline-malicious-metadata', ['check', 'Projekt.ili', '--repository', basic, '--offline'], homeBasic);
  check('malicious metadata offline exit 1', maliciousOffline.exit === 1, maliciousOffline.stderr);
  check('malicious metadata never fetched external DTD', state.evilDtdRequests === evilBefore, String(state.evilDtdRequests));
  check('malicious metadata zero HTTP', maliciousOffline.httpDelta.requests === 0, JSON.stringify(maliciousOffline.httpDelta));
  fs.writeFileSync(basicModelMd, originalMd);
  const restoredOffline = await run('basic-offline-restored-metadata', ['check', 'Projekt.ili', '--repository', basic, '--offline'], homeBasic);
  check('restored metadata offline exit 0', restoredOffline.exit === 0, restoredOffline.stderr);

  // 10. Pins/locks keep old bytes after the URL points at newer content.
  const lockFirst = await run('lock-first', ['check', 'Projekt.ili', '--repository', basic, '--write-lock', 'precedence.lock'], homeLock);
  check('lock-first exit 0', lockFirst.exit === 0, lockFirst.stderr);
  state.basicModel = basicModelV2;
  state.basicEtag = '"basic-v2"';
  const lockUpdate = await run('lock-online-after-url-change', ['check', 'Projekt.ili', '--repository', basic], homeLock);
  check('lock-online after change exit 0', lockUpdate.exit === 0, lockUpdate.stderr);
  check('new URL content was downloaded', lockUpdate.httpDelta.bodies >= 1, JSON.stringify(lockUpdate.httpDelta));
  const lockOffline = await run('lock-offline-old-bytes', ['compile', 'Projekt.ili', '--repository', basic, '--read-lock', 'precedence.lock', '--offline'], homeLock);
  check('lock offline old bytes exit 0', lockOffline.exit === 0, lockOffline.stderr);
  check('lock offline old bytes zero HTTP', lockOffline.httpDelta.requests === 0, JSON.stringify(lockOffline.httpDelta));
  const lockOnline = await run('lock-online-old-bytes', ['check', 'Projekt.ili', '--repository', basic, '--read-lock', 'precedence.lock'], homeLock);
  check('lock online old bytes exit 1', lockOnline.exit === 1, lockOnline.stderr);
  check('lock online old bytes reports LOCK_MISMATCH', (lockOnline.stderr + lockOnline.stdout).includes('does not match the lock') || (lockOnline.stderr + lockOnline.stdout).includes('ILI-RES-0009'), lockOnline.stderr + lockOnline.stdout);

  // 11. CLI conflict is a usage error and does no HTTP.
  const conflict = await run('cache-conflict', ['check', 'Projekt.ili', '--repository', basic, '--cache', 'x', '--no-cache'], homeBasic);
  check('cache conflict exit 2', conflict.exit === 2, conflict.stderr);
  check('cache conflict zero HTTP', conflict.httpDelta.requests === 0, JSON.stringify(conflict.httpDelta));

  // 12. Configuration file no-cache true yields no cache setting; CLI --cache overrides it.
  fs.writeFileSync(path.join(work, 'settings.properties'), 'no-cache=true\n');
  const configExplain = await run('config-nocache-explain', ['check', '--config', 'settings.properties', '--explain-config'], path.join(work, 'home-config'));
  check('config no-cache explain exit 0', configExplain.exit === 0, configExplain.stderr);
  check('config no-cache omits cache setting', !configExplain.stdout.includes('"--cache"'), configExplain.stdout);
  const overrideExplain = await run('config-nocache-overridden-by-cache', ['check', '--config', 'settings.properties', '--cache', 'chosen', '--explain-config'], path.join(work, 'home-config'));
  check('cache override explain exit 0', overrideExplain.exit === 0, overrideExplain.stderr);
  check('cache override shows chosen path', overrideExplain.stdout.includes('chosen'), overrideExplain.stdout);
  check('cache override omits no-cache', !overrideExplain.stdout.includes('"--no-cache"'), overrideExplain.stdout);

  server.close();
  const summary = {
    candidateSha: cp.execSync('git rev-parse HEAD', { cwd: root, encoding: 'utf8' }).trim(),
    distributionJars: fs.readdirSync(path.join(root, 'build/reviewer/distribution/lib')).sort().map((name) => ({
      name,
      sha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(root, 'build/reviewer/distribution/lib', name))).digest('hex')
    })),
    checks,
    failures: checks.filter((c) => !c.passed).map((c) => c.name),
    results
  };
  fs.writeFileSync(path.join(base, 'results.json'), JSON.stringify(summary, null, 2) + '\n');
  console.log(JSON.stringify({ checks: checks.length, failures: summary.failures, output: path.join(base, 'results.json') }, null, 2));
}

main().catch((error) => {
  console.error(error);
  try { server.close(); } catch (_) { }
  process.exit(1);
});
