// Independent CLI-level counterprobes for the MD5/TTL cache migration.
// Uses the supplied launcher from build/reviewer/distribution and a local HTTP server.
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const cp = require('node:child_process'), crypto = require('node:crypto'), assert = require('node:assert/strict');

const root = path.resolve(__dirname, '../../..');
const launcher = path.join(root, 'build/reviewer/distribution/bin/interlis-compiler');
const work = path.join(root, 'build/reviewer/probes/launcher-run');
fs.rmSync(work, {recursive: true, force: true});
const home = path.join(work, 'home');
fs.mkdirSync(home, {recursive: true});
const input = path.join(work, 'Projekt.ili');
const project = 'INTERLIS 2.4; MODEL Projekt AT "x" VERSION "1" = IMPORTS Basis; END Projekt.';
fs.writeFileSync(input, project);
let model = 'INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
const md5 = text => crypto.createHash('md5').update(text).digest('hex');
const sha256 = text => crypto.createHash('sha256').update(text).digest('hex');
let index = `<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File><md5>${md5(model)}</md5></ModelMetadata></TRANSFER>`;
let noStore = false;
const requests = [];
const cases = [];
const server = http.createServer((req, res) => {
    const body = req.url === '/ilimodels.xml' ? index : req.url === '/Basis.ili' ? model : null;
    requests.push({url: req.url, status: body === null ? 404 : 200,
        ifNoneMatch: req.headers['if-none-match'] || null,
        ifModifiedSince: req.headers['if-modified-since'] || null,
        acceptEncoding: req.headers['accept-encoding'] || null});
    if (body === null) { res.writeHead(404).end(); return; }
    res.setHeader('Vary', 'Accept-Encoding');
    res.setHeader('ETag', '"' + sha256(body) + '"');
    res.setHeader('Last-Modified', 'Wed, 21 Oct 2015 07:28:00 GMT');
    if (noStore) res.setHeader('Cache-Control', 'no-store');
    res.writeHead(200).end(body);
});

async function run(name, cache, args, expectedExit = 0) {
    const start = requests.length;
    const result = await new Promise((resolve, reject) => {
        const child = cp.spawn(launcher, args, {
            cwd: work,
            env: {...process.env, JAVA_OPTS: '-Duser.home=' + home},
            stdio: ['ignore', 'pipe', 'pipe']
        });
        let stdout = '', stderr = '';
        const timer = setTimeout(() => child.kill('SIGKILL'), 30000);
        child.stdout.on('data', chunk => stdout += chunk);
        child.stderr.on('data', chunk => stderr += chunk);
        child.on('error', reject);
        child.on('close', (status, signal) => { clearTimeout(timer); resolve({status, signal, stdout, stderr}); });
    });
    const row = {name, exitCode: result.status, signal: result.signal, stdout: result.stdout,
        stderr: result.stderr, http: requests.slice(start)};
    cases.push(row);
    assert.equal(result.status, expectedExit, name + ': ' + result.stderr);
    assert.ok(row.http.every(r => r.ifNoneMatch === null && r.ifModifiedSince === null),
        name + ': conditional header sent ' + JSON.stringify(row.http));
    assert.ok(row.http.every(r => r.acceptEncoding === 'identity'),
        name + ': missing identity coding ' + JSON.stringify(row.http));
    return row;
}
function infos(row) {
    return row.stderr.split('\n').filter(line => line.startsWith('INFO ['));
}
function codes(row) {
    return infos(row).map(line => line.match(/\[(ILI-RES-\d+)\]/)[1]);
}
function metadataDir(cache) { return path.join(cache, 'metadata-v2'); }
function entry(cache, url) { return path.join(metadataDir(cache), sha256(url) + '.xml'); }
function legacyDir(cache) { return path.join(cache, 'metadata-v1'); }

(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = 'http://127.0.0.1:' + server.address().port + '/';
    const common = [input, '--repository', base, '--no-proxy'];
    const cacheA = path.join(work, 'cache-a');

    const cold = await run('cold', cacheA, ['check', ...common, '--cache', cacheA, '--verbose']);
    assert.deepEqual(cases.at(-1).http.map(r => r.status), [200, 200], 'cold downloads index and model');
    assert.deepEqual(codes(cold), ['ILI-RES-0011', 'ILI-RES-0010', 'ILI-RES-0012', 'ILI-RES-0010']);
    assert.ok(fs.readdirSync(metadataDir(cacheA)).filter(f => f.endsWith('.xml')).length === 2,
        'v2 metadata published for both URLs');
    const snapshot = new Map(fs.readdirSync(metadataDir(cacheA)).filter(f => f.endsWith('.xml'))
        .map(f => [f, fs.readFileSync(path.join(metadataDir(cacheA), f), 'utf8')]));
    for (const [name, text] of snapshot) assert.ok(text.includes('downloaded-at="'), name + ' has downloaded-at');
    assert.ok(![...snapshot.values()].some(text => text.includes('etag') || text.includes('last-modified')),
        'no HTTP validators persisted in v2');

    const warm = await run('warm', cacheA, ['check', ...common, '--cache', cacheA, '--verbose']);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), [], 'warm run performs zero HTTP requests');
    assert.deepEqual(codes(warm), ['ILI-RES-0011', 'ILI-RES-0010', 'ILI-RES-0012', 'ILI-RES-0010']);
    assert.equal(infos(warm).filter(line => line.includes('fresh cache')).length, 2, 'two truthful FRESH_HIT lines');
    assert.ok(!warm.stderr.includes('ILI-RES-0013') && !warm.stderr.includes('HTTP 304'), 'reserved verification code absent');
    for (const [name, text] of snapshot)
        assert.equal(fs.readFileSync(path.join(metadataDir(cacheA), name), 'utf8'), text,
            'hit did not renew downloaded-at');

    // Future timestamps must never be treated as fresh.
    for (const name of snapshot.keys()) {
        const file = path.join(metadataDir(cacheA), name);
        fs.writeFileSync(file, fs.readFileSync(file, 'utf8')
            .replace(/downloaded-at="[^"]+"/, 'downloaded-at="2100-01-01T00:00:00Z"'));
    }
    const future = await run('future-timestamp', cacheA, ['check', ...common, '--cache', cacheA, '--verbose']);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), ['/ilimodels.xml', '/Basis.ili'],
        'future metadata is not fresh');

    // Expire only the index; a changed published MD5 must refresh the still-fresh model.
    model += '\n!! changed model bytes';
    index = index.replace(/<md5>[^<]+<\/md5>/, `<md5>${md5(model)}</md5>`);
    const indexEntry = entry(cacheA, base + 'ilimodels.xml');
    fs.writeFileSync(indexEntry, fs.readFileSync(indexEntry, 'utf8')
        .replace(/downloaded-at="[^"]+"/, 'downloaded-at="2000-01-01T00:00:00Z"'));
    const changed = await run('changed-md5', cacheA, ['check', ...common, '--cache', cacheA, '--verbose']);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), ['/ilimodels.xml', '/Basis.ili'],
        'new index MD5 refreshes the model inside its TTL');
    assert.ok(changed.stderr.includes('cache could not be used'), 'MD5 mismatch is a truthful CACHE_MISS');
    assert.equal(codes(changed).filter(code => code === 'ILI-RES-0010').length, 3, 'CACHE_MISS and DOWNLOAD both reported');

    // Post-download MD5: a changed model is downloaded once and then rejected
    // because its bytes no longer match the still-published index MD5.
    model = 'INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
    index = `<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File><md5>${md5(model)}</md5></ModelMetadata></TRANSFER>`;
    const cacheP = path.join(work, 'cache-post-md5');
    await run('post-md5-cold', cacheP, ['check', ...common, '--cache', cacheP, '--verbose']);
    model = model + '\n!! post-download mismatch';
    const postEntry = entry(cacheP, base + 'Basis.ili');
    fs.writeFileSync(postEntry, fs.readFileSync(postEntry, 'utf8')
        .replace(/downloaded-at="[^"]+"/, 'downloaded-at="2000-01-01T00:00:00Z"'));
    const postMismatch = await run('post-md5-mismatch', cacheP,
        ['check', ...common, '--cache', cacheP, '--verbose'], 1);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), ['/Basis.ili'],
        'changed model is downloaded once before post-download MD5 rejection');
    assert.ok(postMismatch.stderr.includes('ERROR'), 'post-download MD5 mismatch is an error');

    // v1 metadata is offline-only and still resolves the last local stand.
    const cacheV = path.join(work, 'cache-v1');
    fs.cpSync(cacheA, cacheV, {recursive: true});
    fs.mkdirSync(legacyDir(cacheV), {recursive: true});
    for (const name of fs.readdirSync(metadataDir(cacheV)).filter(f => f.endsWith('.xml'))) {
        const source = path.join(metadataDir(cacheV), name);
        const converted = fs.readFileSync(source, 'utf8')
            .replace('version="2"', 'version="1"')
            .replace(/\s+downloaded-at="[^"]*"/, '');
        fs.writeFileSync(path.join(legacyDir(cacheV), name), converted);
    }
    fs.rmSync(metadataDir(cacheV), {recursive: true, force: true});
    const migrated = await run('v1-offline', cacheV, ['check', ...common, '--cache', cacheV, '--offline', '--verbose']);
    assert.deepEqual(cases.at(-1).http, [], 'v1 offline fallback performs zero HTTP requests');
    assert.ok(infos(migrated).some(line => line.includes('offline')), 'offline hit is reported');
    assert.ok(!fs.existsSync(metadataDir(cacheV)), 'offline v1 use does not publish v2');

    // Corrupt v2 must not silently fall back to the still-valid v1 entry.
    fs.mkdirSync(metadataDir(cacheV), {recursive: true});
    fs.writeFileSync(entry(cacheV, base + 'Basis.ili'), 'corrupt');
    const corrupt = await run('corrupt-v2-offline', cacheV, ['check', ...common, '--cache', cacheV, '--offline', '--verbose'], 1);
    assert.ok(corrupt.stderr.includes('ERROR'), 'corrupt v2 causes an offline error');

    // Lock edge: a fresh cache satisfies an online lock replay; a changed model
    // source must not be silently accepted under the stored SHA-256 pin.
    model = 'INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
    index = `<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File><md5>${md5(model)}</md5></ModelMetadata></TRANSFER>`;
    const cacheL = path.join(work, 'cache-lock');
    const lockL = path.join(work, 'project.lock');
    await run('lock-cold', cacheL, ['check', ...common, '--cache', cacheL, '--write-lock', lockL, '--verbose']);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), ['/ilimodels.xml', '/Basis.ili']);
    assert.ok(fs.existsSync(lockL), 'lock file written');
    await run('lock-offline', cacheL, ['check', ...common, '--cache', cacheL, '--read-lock', lockL, '--offline', '--verbose']);
    assert.deepEqual(cases.at(-1).http, [], 'offline lock replay performs zero HTTP requests');
    await run('lock-warm-online', cacheL, ['check', ...common, '--cache', cacheL, '--read-lock', lockL, '--verbose']);
    assert.deepEqual(cases.at(-1).http, [], 'online lock replay inside TTL performs zero HTTP requests');

    // Changed model with an unchanged index: the expired model is downloaded once,
    // then the published MD5 / lock pin rejects it.
    model = model + '\n!! lock-changed bytes';
    const lockModelEntry = entry(cacheL, base + 'Basis.ili');
    fs.writeFileSync(lockModelEntry, fs.readFileSync(lockModelEntry, 'utf8')
        .replace(/downloaded-at="[^"]+"/, 'downloaded-at="2000-01-01T00:00:00Z"'));
    const lockModelMismatch = await run('lock-changed-model', cacheL,
        ['check', ...common, '--cache', cacheL, '--read-lock', lockL, '--verbose'], 1);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), ['/Basis.ili'],
        'changed model is downloaded once before the pin rejects it');
    assert.ok(lockModelMismatch.stderr.includes('ERROR'), 'model pin mismatch is an error');

    // Changed index bytes under a lock: the frozen index digest is checked before
    // any model load, so only the index is fetched and the replay is rejected.
    model = 'INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
    index = `<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File><md5>${md5(model)}</md5></ModelMetadata></TRANSFER>`;
    const cacheL2 = path.join(work, 'cache-lock-2');
    const lockL2 = path.join(work, 'project-2.lock');
    await run('lock2-cold', cacheL2, ['check', ...common, '--cache', cacheL2, '--write-lock', lockL2, '--verbose']);
    model = model + '\n!! index-changed bytes';
    index = index.replace(/<md5>[^<]+<\/md5>/, `<md5>${md5(model)}</md5>`);
    const lock2IndexEntry = entry(cacheL2, base + 'ilimodels.xml');
    fs.writeFileSync(lock2IndexEntry, fs.readFileSync(lock2IndexEntry, 'utf8')
        .replace(/downloaded-at="[^"]+"/, 'downloaded-at="2000-01-01T00:00:00Z"'));
    const lockIndexMismatch = await run('lock-changed-index', cacheL2,
        ['check', ...common, '--cache', cacheL2, '--read-lock', lockL2, '--verbose'], 1);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), ['/ilimodels.xml'],
        'changed index is fetched once and rejected by the lock pin');
    assert.ok(lockIndexMismatch.stderr.includes('ERROR'), 'index lock mismatch is an error');

    // no-store is never persisted and never becomes a warm hit.
    const cacheN = path.join(work, 'cache-no-store');
    noStore = true;
    const private1 = await run('no-store-1', cacheN, ['check', ...common, '--cache', cacheN, '--verbose']);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), ['/ilimodels.xml', '/Basis.ili']);
    const private2 = await run('no-store-2', cacheN, ['check', ...common, '--cache', cacheN, '--verbose']);
    assert.deepEqual(cases.at(-1).http.map(r => r.url), ['/ilimodels.xml', '/Basis.ili'],
        'no-store does not create a warm hit');
    assert.ok(!fs.existsSync(metadataDir(cacheN)), 'no-store writes no metadata-v2');
    noStore = false;

    fs.writeFileSync(path.join(work, 'results.json'),
        JSON.stringify({passed: true, launcher, cases}, null, 2));
    console.log('LAUNCHER_PROBE_PASS ' + cases.length);
})().catch(error => {
    fs.writeFileSync(path.join(work, 'failed.json'), JSON.stringify({error: String(error), cases}, null, 2));
    console.error(error);
    process.exitCode = 1;
}).finally(() => { server.closeAllConnections(); server.close(); });
