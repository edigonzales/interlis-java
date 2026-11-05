'use strict';
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
/** Explicit Java-properties host layout, hash-bound by its caller. No Gradle fallback. */
function readVerificationInputs(root, file = process.env.VERIFICATION_LAYOUT, expectedHash = process.env.VERIFICATION_LAYOUT_SHA256) {
  root = path.resolve(root);
  if (!file) return {root, paths: {reports: 'target/jenesis-jvm/reports', compiler: 'target/jenesis-jvm/delivery/compiler', lsp: 'target/jenesis-jvm/delivery/lsp', sdk: 'target/jenesis-jvm/delivery/sdk.zip', conformance: 'target/jenesis-jvm/conformance'}};
  if (!path.isAbsolute(file) || !/^[a-f0-9]{64}$/.test(expectedHash || '')) throw Error('Explicit layout reference required');
  if (!path.normalize(file).startsWith(path.join(root,'target/jenesis-jvm') + path.sep) || fs.lstatSync(file).isSymbolicLink()) throw Error('Foreign layout file');
  const bytes = fs.readFileSync(file);if (hash(bytes) !== expectedHash) throw Error('Changed verification layout');
  // Properties emitted by java.util.Properties.store(Writer), including escaped Windows paths.
  const decode = text => text.replace(/\\u([0-9a-fA-F]{4})|\\(.)/g, (_, hex, char) => hex ? String.fromCharCode(parseInt(hex, 16)) : ({t:'\t',r:'\r',n:'\n',f:'\f'}[char] || char));
  const data = {};
  for (const line of bytes.toString('utf8').split(/\r?\n/)) {
    if (!line || /^[#!]/.test(line)) continue;
    const match = line.match(/^((?:\\.|[^=])+?)=(.*)$/);if (!match) throw Error('Invalid properties layout');
    const key = decode(match[1]);if (Object.hasOwn(data, key)) throw Error('Duplicate layout key');data[key] = decode(match[2]);
  }
  if (data.schemaVersion !== '1' || data.builder !== 'jenesis' || data.root !== root) throw Error('Foreign verification layout');
  const paths = Object.fromEntries(Object.entries(data).filter(([key]) => !['schemaVersion','builder','root'].includes(key)));
  for (const key of Object.keys(paths)) {
    if (!['compiler','lsp','sdk','reports','norms','conformance','reference'].includes(key) && !/^junit\.interlis-[a-z-]+\.(test|contractTest|contractAssertionsTest)$/.test(key) && !/^manifest\.interlis-[a-z-]+$/.test(key)) throw Error('Unknown layout role');
    resolveRole({root, paths}, key);
  }
  return {root, paths};
}
function resolveRole(input, role, relative = '') {
  const value = input.paths[role];if (!value) throw Error('Missing explicit layout role: ' + role);
  for (const part of [value, relative]) if (path.isAbsolute(part) || part.includes('\\') || part.includes(':') || part.split('/').includes('..')) throw Error('Unsafe verification path');
  const boundary = path.join(input.root, 'target/jenesis-jvm'), target = path.resolve(input.root, value, relative);
  if (!target.startsWith(boundary + path.sep)) throw Error('Verification output escapes target');
  for (let cursor = target; cursor !== input.root; cursor = path.dirname(cursor)) if (fs.lstatSync(cursor, {throwIfNoEntry:false})?.isSymbolicLink()) throw Error('Symlink verification output');
  return target;
}
function report(root, relative) { return resolveRole(readVerificationInputs(root), 'reports', relative); }
module.exports = {readVerificationInputs, resolveRole, report};
/** Known host roles and literal backend-relative artifacts share one confined input reader. */
function inputPath(root, relative) {
  if (relative.startsWith('reports/')) return resolveRole(readVerificationInputs(root), 'reports', relative.slice(8));
  if (relative.startsWith('conformance/')) return resolveRole(readVerificationInputs(root), 'conformance', relative.slice(12));
  if (relative === 'sdk') return resolveRole(readVerificationInputs(root), 'sdk');
  if (path.isAbsolute(relative) || relative.includes('\\') || relative.split('/').includes('..')) throw Error('Unsafe input role');
  return path.join(path.resolve(root), relative);
}
module.exports.inputPath = inputPath;
