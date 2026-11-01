'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const cp = require('node:child_process');

const root = process.cwd();
const base = path.join(root, 'build/reviewer/counterprobes/optional-site');
const work = path.join(base, 'work');
const cli = path.join(root, 'build/reviewer/distribution/bin/interlis-compiler');
fs.rmSync(work, { recursive: true, force: true });
fs.mkdirSync(work, { recursive: true });

const sha256 = (text) => crypto.createHash('sha256').update(text, 'utf8').digest('hex');
const index = '<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
const basis = 'INTERLIS 2.4; MODEL Basis AT "https://example.org" VERSION "1" = END Basis.';
let requests = 0;
let bodies = 0;

const server = http.createServer((req, res) => {
  requests += 1;
  const pathname = new URL(req.url, 'http://127.0.0.1').pathname;
  let body = null;
  if (pathname === '/repo/ilimodels.xml') body = index;
  else if (pathname === '/repo/Basis.ili') body = basis;
  // /repo/ilisite.xml is deliberately absent (optional metadata).
  if (body === null) {
    res.statusCode = 404;
    res.end();
    return;
  }
  bodies += 1;
  const bytes = Buffer.from(body, 'utf8');
  res.setHeader('Content-Length', bytes.length);
  res.end(bytes);
});

async function run(name, args, home) {
  const before = { requests, bodies };
  const child = cp.spawn(cli, args, {
    cwd: work,
    env: { ...process.env, JAVA_OPTS: `-Duser.home=${home}`, HOME: home }
  });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', (chunk) => { stdout += chunk.toString('utf8'); });
  child.stderr.on('data', (chunk) => { stderr += chunk.toString('utf8'); });
  const exit = await new Promise((resolve) => {
    const timer = setTimeout(() => { child.kill('SIGKILL'); resolve(null); }, 60000);
    child.on('close', (code) => { clearTimeout(timer); resolve(code); });
    child.on('error', () => { clearTimeout(timer); resolve(null); });
  });
  return {
    name,
    args,
    exit,
    stdout,
    stderr,
    http: { requests: requests - before.requests, bodies: bodies - before.bodies }
  };
}

async function main() {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}/`;
  const repo = origin + 'repo/';
  const home = path.join(work, 'home');

  fs.writeFileSync(path.join(work, 'UsesBasis.ili'), 'INTERLIS 2.4; MODEL UsesBasis AT "https://example.org" VERSION "1" = IMPORTS Basis; END UsesBasis.');
  fs.writeFileSync(path.join(work, 'Missing.ili'), 'INTERLIS 2.4; MODEL MissingProject AT "https://example.org" VERSION "1" = IMPORTS Absent; END MissingProject.');

  const warm = await run('warm-cache-online', ['check', 'UsesBasis.ili', '--repository', repo], home);
  const offlineMissing = await run('offline-missing-model', ['check', 'Missing.ili', '--repository', repo, '--offline'], home);
  const onlineMissing = await run('online-missing-model', ['check', 'Missing.ili', '--repository', repo], home);

  const metadata = path.join(home, '.interlis-cache', 'metadata-v1');
  const result = {
    repository: repo,
    cacheMetadataFiles: fs.existsSync(metadata) ? fs.readdirSync(metadata) : [],
    hasIndexMetadata: fs.existsSync(path.join(metadata, sha256(repo + 'ilimodels.xml') + '.xml')),
    hasSiteMetadata: fs.existsSync(path.join(metadata, sha256(repo + 'ilisite.xml') + '.xml')),
    cases: [warm, offlineMissing, onlineMissing],
    interpretation: {
      expectedOffline: 'Optional ilisite.xml may be absent with a visible warning; the missing model should be reported as NOT_FOUND (ILI-RES-0001), as online does.',
      observedOffline: 'The optional ilisite.xml absence is promoted to the required-source failure ILI-RES-0003 and names ilisite.xml.',
      observedOnline: 'The same repository and the same missing model report ILI-RES-0001, because the site 404 is ignored.'
    }
  };
  fs.writeFileSync(path.join(base, 'results.json'), JSON.stringify(result, null, 2) + '\n');
  server.close();
  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => { console.error(error); try { server.close(); } catch (_) { } process.exit(1); });
