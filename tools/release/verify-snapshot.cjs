'use strict';

const {createHash} = require('node:crypto');
const {execFileSync} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const snapshots = 'https://jars.interlis.guru/snapshots/';
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const assert = (condition, message) => { if (!condition) throw new Error(message); };

const [artifactId, bundle] = process.argv.slice(2);
const compiler = artifactId === 'interlis-compiler-bindist';
assert((compiler || artifactId === 'interlis-lsp-distribution') && bundle && process.argv.length === 4,
    'Supply the artifact ID and verified snapshot ZIP directory');
const files = fs.readdirSync(bundle);
const zip = files.find(name => new RegExp('^' + artifactId + '-\\d+\\.\\d+\\.\\d+-SNAPSHOT\\.zip$').test(name));
assert(zip && files.length === (compiler ? 2 : 1), 'Expected only the verified snapshot files');
const version = zip.slice(artifactId.length + 1, -4);
const checksum = sha256(fs.readFileSync(path.resolve(bundle, zip)));
if (compiler) {
    assert(fs.readFileSync(path.join(bundle, 'SHA256SUMS'), 'utf8').trim() === checksum + '  ' + zip,
        'Compiler ZIP checksum mismatch');
}

const base = new URL('guru/interlis/' + artifactId + '/' + version + '/', snapshots);
function read(name) {
    const url = new URL(name, base);
    return execFileSync('curl', ['--fail', '--silent', '--show-error', '--location', '--max-time', '60', url.href],
        {maxBuffer: 64 * 1024 * 1024});
}
const metadata = read('maven-metadata.xml').toString('utf8');
const entries = [...metadata.matchAll(/<snapshotVersion>([\s\S]*?)<\/snapshotVersion>/g)];
const zipEntries = entries.filter(([, entry]) => /<extension>\s*zip\s*<\/extension>/.test(entry)
    && !/<classifier>/.test(entry));
const publishedVersion = zipEntries[0]?.[1].match(/<value>\s*([0-9.\-]+)\s*<\/value>/)?.[1];
assert(zipEntries.length === 1 && publishedVersion?.startsWith(version.slice(0, -8)), 'Missing published ZIP version');
const publishedName = artifactId + '-' + publishedVersion + '.zip';
assert(sha256(read(publishedName)) === checksum, 'Published ZIP checksum mismatch');
const url = new URL(publishedName, base).href;
console.log('Verified snapshot: ' + url);
if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
        artifactId + ' snapshot for `' + process.env.GITHUB_SHA + '`\n\n[' + publishedName + '](' + url
        + ')\n\nSHA-256 `' + checksum + '`\n');
}
