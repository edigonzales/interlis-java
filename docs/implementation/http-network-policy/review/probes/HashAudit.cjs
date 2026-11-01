// Reviewer-owned hash audit for the candidate manifest and supplied distribution.
const fs = require('node:fs');
const crypto = require('node:crypto');
const path = require('node:path');

const root = path.resolve(__dirname, '../../..');
const manifestPath = path.join(root, 'docs/implementation/http-network-policy/evidence/manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const bad = [];
const missing = [];
for (const entry of [...manifest.product, ...manifest.evidence]) {
    try {
        const actual = hash(path.join(root, entry.path));
        if (actual !== entry.sha256) bad.push(entry.path);
    } catch (error) {
        missing.push(entry.path);
    }
}
const jarBad = [];
for (const jar of manifest.jars) {
    const actual = hash(path.join(root, 'build/reviewer/distribution/lib', jar.name));
    if (actual !== jar.sha256) jarBad.push(jar.name);
}
console.log('manifest_product=' + manifest.product.length
    + ' manifest_evidence=' + manifest.evidence.length
    + ' bad=' + bad.length + ' missing=' + missing.length);
console.log('distribution_jars=' + manifest.jars.length + ' bad=' + jarBad.length);
if (bad.length) console.log('bad_paths=' + bad.join(','));
if (missing.length) console.log('missing_paths=' + missing.join(','));
if (jarBad.length) console.log('bad_jars=' + jarBad.join(','));
process.exitCode = (bad.length || missing.length || jarBad.length) ? 1 : 0;
