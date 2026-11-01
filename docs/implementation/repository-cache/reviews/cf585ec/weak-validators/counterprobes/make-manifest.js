'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const root = __dirname;
const sha256 = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const dist = '/Users/stefan/sources/interlis-java/build/review/repository-cache/r1/worktree/build/reviewer/distribution';
const cli = dist + '/bin/interlis-compiler';

const definitions = {
  'run-weak': {
    mode: 'weak-window', scenario: 'scenarios/weak', commands: [
      { args: '--cache run-weak/cache --write-lock run-weak/lock.xml', exitCode: 0 },
      { args: '--cache run-weak/cache --read-lock run-weak/lock.xml', exitCode: 0 },
      { args: '--no-cache --read-lock run-weak/lock.xml', exitCode: 1 },
    ],
  },
  'run-weak-nocache': {
    mode: 'weak-window', scenario: 'scenarios/weak', commands: [
      { args: '--no-cache --write-lock run-weak-nocache/lock.xml', exitCode: 1 },
    ],
  },
  'run-lm': {
    mode: 'lm-window', scenario: 'scenarios/lm', commands: [
      { args: '--cache run-lm/cache --write-lock run-lm/lock.xml', exitCode: 0 },
      { args: '--cache run-lm/cache --read-lock run-lm/lock.xml', exitCode: 0 },
      { args: '--no-cache --read-lock run-lm/lock.xml', exitCode: 1 },
    ],
  },
  'run-lm-nocache': {
    mode: 'lm-window', scenario: 'scenarios/lm', commands: [
      { args: '--no-cache --write-lock run-lm-nocache/lock.xml', exitCode: 1 },
    ],
  },
  'smoke': {
    mode: 'weak-window', scenario: 'scenarios/weak', commands: [
      { args: '--cache smoke/cache --write-lock smoke/lock.xml', exitCode: 0 },
    ],
  },
};

function readEntries(dir) {
  const log = path.join(root, dir, 'http.jsonl');
  return fs.readFileSync(log, 'utf8').trim().split('\n').filter(Boolean).map((l) => JSON.parse(l));
}

function runInfo(dir, def) {
  const entries = readEntries(dir);
  const listening = entries.find((e) => e.event === 'listening');
  const port = listening ? listening.port : null;
  const base = port ? `http://127.0.0.1:${port}/` : null;
  const requests = entries.filter((e) => e.path);
  const files = fs.readdirSync(path.join(root, dir))
    .filter((f) => /^out\d+\.json$/.test(f) || /^err\d+\.txt$/.test(f) || f === 'lock.xml')
    .sort()
    .map((f) => ({ file: path.join(dir, f), sha256: sha256(path.join(root, dir, f)) }));
  const commands = base === null ? [] : def.commands.map((c) => ({
    command: `${cli} check --root Dep --repository ${base} ${c.args} --json`,
    exitCode: c.exitCode,
  }));
  return {
    directory: dir,
    mode: def.mode,
    scenario: def.scenario,
    port,
    base,
    httpLog: path.join(dir, 'http.jsonl'),
    requests: requests.length,
    status200: requests.filter((e) => e.status === 200).length,
    status304: requests.filter((e) => e.status === 304).length,
    bodyResponses: requests.filter((e) => e.bodySent > 0).length,
    modelBodies: requests.filter((e) => e.path === '/Dep.ili' && e.bodySent > 0)
      .map((e) => ({ variant: e.variant, sha256: e.sha256, bytes: e.bodySent })),
    conditionalModelRequests: requests.filter((e) => e.path === '/Dep.ili' && (e.ifNoneMatch || e.ifModifiedSince))
      .map((e) => ({ status: e.status, ifNoneMatch: e.ifNoneMatch, ifModifiedSince: e.ifModifiedSince, variant: e.variant })),
    commands,
    files,
  };
}

const manifest = {
  candidateSha: 'cf585ec1ea2270bafed4083a8ebfa2e68de05133',
  baseSha: 'fd07e194f14c8ee46debe930871a6c18fb72242d',
  distributionJars: {
    'interlis-repository-0.2.0-SNAPSHOT.jar': sha256(path.join(dist, 'lib/interlis-repository-0.2.0-SNAPSHOT.jar')),
    'interlis-compiler-cli-0.2.0-SNAPSHOT.jar': sha256(path.join(dist, 'lib/interlis-compiler-cli-0.2.0-SNAPSHOT.jar')),
  },
  scenarios: {
    'scenarios/weak/a.ili': sha256(path.join(root, 'scenarios/weak/a.ili')),
    'scenarios/weak/b.ili': sha256(path.join(root, 'scenarios/weak/b.ili')),
    'scenarios/lm/a.ili': sha256(path.join(root, 'scenarios/lm/a.ili')),
    'scenarios/lm/b.ili': sha256(path.join(root, 'scenarios/lm/b.ili')),
    'scenarios/index.xml': sha256(path.join(root, 'scenarios/index.xml')),
  },
  runs: Object.fromEntries(Object.entries(definitions).map(([dir, def]) => [dir, runInfo(dir, def)])),
};

fs.writeFileSync(path.join(root, 'evidence-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify(manifest, null, 2));
