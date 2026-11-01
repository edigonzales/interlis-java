#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const worktree = process.argv[2];
const dist = process.argv[3];
const manifestPath = path.join(worktree, 'docs/implementation/cache-logging/evidence/manifest.json');
const m = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));

function sha256(file) {
  const h = crypto.createHash('sha256');
  h.update(fs.readFileSync(file));
  return h.digest('hex');
}

function check(map, resolve) {
  let ok = 0, bad = [], missing = [];
  for (const [rel, expected] of Object.entries(map)) {
    const p = resolve(rel);
    if (!fs.existsSync(p)) { missing.push(rel); continue; }
    const actual = sha256(p);
    if (actual === expected) ok++; else bad.push({ rel, expected, actual });
  }
  return { total: Object.keys(map).length, ok, bad, missing };
}

const files = check(m.files_sha256, rel => path.join(worktree, rel));
const testSources = check(m.test_sources_sha256, rel => path.join(worktree, rel));
const jars = check(m.runtime_jars_sha256, rel => path.join(dist, 'lib', path.basename(rel)));

console.log(JSON.stringify({ files, testSources, jars, junit: m.junit, suites: m.suites }, null, 2));
const allOk = [files, testSources, jars].every(r => r.bad.length === 0 && r.missing.length === 0);
process.exitCode = allOk ? 0 : 1;
