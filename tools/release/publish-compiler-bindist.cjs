'use strict';

const {createHash} = require('node:crypto');
const {spawnSync, execFileSync} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');

const snapshots = 'https://jars.interlis.guru/snapshots/';
const artifactId = 'interlis-compiler-bindist';
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const assert = (condition, message) => { if (!condition) throw new Error(message); };

const [bundle, localRepository] = process.argv.slice(2);
assert(bundle && process.argv.length <= 4, 'Usage: node publish-compiler-bindist.cjs BUNDLE [LOCAL_REPOSITORY]');
const repository = localRepository
    ? pathToFileURL(path.resolve(localRepository) + path.sep).href : snapshots;
const files = fs.readdirSync(bundle);
const zip = files.find(name => /^interlis-compiler-bindist-\d+\.\d+\.\d+-SNAPSHOT\.zip$/.test(name));
assert(zip && files.length === 2 && files.includes('SHA256SUMS'), 'Expected only the compiler snapshot ZIP and its checksum');
const version = zip.slice(artifactId.length + 1, -4);
const file = path.resolve(bundle, zip);
const checksum = sha256(fs.readFileSync(file));
assert(fs.readFileSync(path.join(bundle, 'SHA256SUMS'), 'utf8').trim() === checksum + '  ' + zip,
    'Compiler ZIP checksum mismatch');

const deploy = spawnSync('mvn', ['-B', '-ntp', 'org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file',
    '-DrepositoryId=compiler-snapshots', '-Durl=' + repository, '-Dfile=' + file,
    '-DgroupId=guru.interlis', '-DartifactId=' + artifactId, '-Dversion=' + version,
    '-Dpackaging=zip', '-DgeneratePom=true'], {stdio: 'inherit'});
if (deploy.error) throw deploy.error;
assert(deploy.status === 0, 'Maven snapshot upload failed');

const base = new URL('guru/interlis/' + artifactId + '/' + version + '/', repository);
function read(name) {
    const url = new URL(name, base);
    if (url.protocol === 'file:') return fs.readFileSync(url);
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
assert(sha256(read(publishedName)) === checksum, 'Published compiler ZIP checksum mismatch');
const url = new URL(publishedName, base).href;
console.log('Verified compiler snapshot: ' + url);
if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
        'Compiler snapshot for `' + process.env.GITHUB_SHA + '`\n\n[' + publishedName + '](' + url
        + ')\n\nSHA-256 `' + checksum + '`\n');
}
