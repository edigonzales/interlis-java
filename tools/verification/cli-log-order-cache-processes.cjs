// Deterministic probes of the installed CLI; uses an isolated home and local HTTP server.
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const cp = require('node:child_process'), crypto = require('node:crypto'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const out = path.join(root, 'build/cli-log-order-cache/processes');
fs.mkdirSync(out, {recursive: true});
const launcher = process.argv[2] || path.join(root, 'interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler');
const dir = fs.mkdtempSync(path.join(out, 'run-')), home = path.join(dir, 'home');
fs.mkdirSync(home);
const input = path.join(dir, 'Projekt.ili');
const project = 'INTERLIS 2.4; MODEL Projekt AT "x" VERSION "1" = IMPORTS Basis; END Projekt.';
fs.writeFileSync(input, project);
const model = 'INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
const index = '<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
let mode = 'identity';
const requests = [], cases = [];
const server = http.createServer((req, res) => {
    const body = req.url === '/ilimodels.xml' ? index : req.url === '/Basis.ili' ? model : null;
    const tag = '"' + crypto.createHash('sha256').update(body || '').digest('hex') + '"';
    const status = body === null ? 404 : req.headers['if-none-match'] === tag ? 304 : 200;
    requests.push({url: req.url, status, encoding: req.headers['accept-encoding'], condition: req.headers['if-none-match'] || null});
    res.setHeader('Vary', 'Accept-Encoding');
    if (mode === 'no-store') res.setHeader('Cache-Control', 'no-store');
    if (mode === 'gzip') res.setHeader('Content-Encoding', 'gzip');
    if (body !== null) res.setHeader('ETag', tag);
    res.writeHead(status).end(status === 200 ? body : undefined);
});
async function run(name, args, exit = 0) {
    const start = requests.length;
    const result = await new Promise((resolve, reject) => {
        const child = cp.spawn(launcher, args, {cwd: dir, env: {...process.env, JAVA_OPTS: '-Duser.home=' + home}, stdio: ['ignore', 'pipe', 'pipe']});
        let stdout = '', stderr = '';
        const timer = setTimeout(() => child.kill('SIGKILL'), 20000);
        child.stdout.on('data', x => stdout += x); child.stderr.on('data', x => stderr += x);
        child.on('error', reject);
        child.on('close', (exitCode, signal) => { clearTimeout(timer); resolve({exitCode, signal, stdout, stderr}); });
    });
    const row = {name, command: [launcher, ...args], ...result, http: requests.slice(start)};
    cases.push(row);
    fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({passed: false, cases}, null, 2));
    assert.equal(result.signal, null, name); assert.equal(result.exitCode, exit, name + ': ' + result.stderr);
    assert.ok(row.http.every(r => r.encoding === 'identity'), JSON.stringify(row.http));
    return row;
}
function ordered(row) {
    const infos = row.stderr.split('\n').filter(line => line.startsWith('INFO '));
    assert.deepEqual(infos.map(line => line.match(/\[(ILI-RES-\d+)\]/)[1]),
        ['ILI-RES-0011', 'ILI-RES-0010', 'ILI-RES-0012', 'ILI-RES-0010', 'ILI-RES-0013', 'ILI-RES-0010', 'ILI-RES-0010'], row.stderr);
    assert.ok(infos[1].includes('ilimodels.xml')); assert.ok(infos[3].includes('Basis.ili'));
}
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = 'http://127.0.0.1:' + server.address().port + '/';
    const common = [input, '--repository', base, '--no-proxy'];
    const lock = path.join(dir, 'project.lock');
    const cold = await run('cold', ['check', ...common, '--verbose', '--write-lock', lock]);
    ordered(cold); assert.deepEqual(cold.http.map(r => r.status), [200, 200, 304, 304]); assert.equal(cold.stdout, '');
    for (const command of ['check', 'compile', 'analyze', 'diagram']) {
        const hot = await run('warm-' + command, [command, ...common, '--verbose', '--warnings', 'reject', ...(command === 'diagram' ? ['--format', 'json'] : [])]);
        ordered(hot); assert.deepEqual(hot.http.map(r => r.status), [304, 304, 304, 304]);
        if (command === 'diagram') assert.ok(JSON.parse(hot.stdout)); else assert.equal(hot.stdout, '');
    }
    const de = await run('german', ['compile', ...common, '--verbose', '--diagnostic-language', 'de']);
    ordered(de); assert.ok(de.stderr.includes('INFO [ILI-RES-0011] Modellsuche:')); assert.ok(de.stderr.includes('INFO [ILI-RES-0013] Abschlussprüfung:'));
    const compact = await run('compact', ['check', ...common]); assert.equal(compact.stderr, ''); assert.equal(compact.stdout, '');
    const json = await run('json', ['check', ...common, '--json']);
    const jsonVerbose = await run('json-verbose', ['check', ...common, '--json', '--verbose']);
    assert.deepEqual(JSON.parse(json.stdout), JSON.parse(jsonVerbose.stdout)); assert.equal(jsonVerbose.stderr, '');
    for (const locked of [false, true]) {
        const row = await run('offline-' + locked, ['compile', ...common, '--offline', '--verbose', ...(locked ? ['--read-lock', lock] : [])]);
        assert.equal(row.http.length, 0); assert.ok(!row.stderr.includes('ILI-RES-0013'));
        if (locked) assert.ok(!row.stderr.includes('ILI-RES-0011') && !row.stderr.includes('ILI-RES-0012'));
    }
    const local = path.join(dir, 'Local.ili'); fs.writeFileSync(local, 'INTERLIS 2.4; MODEL Local AT "x" VERSION "1" = END Local.');
    const noWork = await run('no-work', ['check', local, '--repository', base, '--verbose']);
    assert.equal(noWork.http.length, 0); assert.equal(noWork.stderr, '');
    const missing = await run('missing', ['check', '--root', 'Absent', '--repository', base, '--offline', '--verbose'], 1);
    assert.ok(missing.stderr.indexOf('INFO [ILI-RES-0011]') >= 0);
    assert.ok(missing.stderr.indexOf('INFO [ILI-RES-0011]') < missing.stderr.indexOf('ERROR [')); assert.equal(missing.http.length, 0);
    fs.writeFileSync(input, project.replace('END Projekt.', 'DOMAIN D = Missing; END Projekt.'));
    const bad = await run('compiler-error', ['compile', ...common, '--verbose', '--diagnostic-language', 'de'], 1);
    assert.ok(bad.stderr.indexOf('ILI-RES-0013') < bad.stderr.indexOf('ERROR ['));
    assert.ok(bad.stderr.split('\n').some(line => /^ERROR \[ILI-SEM-\d+\] .*Projekt\.ili:\d+:\d+:/.test(line)), bad.stderr);
    assert.equal(bad.stdout, ''); fs.writeFileSync(input, project);
    const disabled = await run('no-cache', ['check', ...common, '--no-cache', '--verbose']);
    ordered(disabled); assert.deepEqual(disabled.http.map(r => r.status), [200, 200, 200, 200]);
    mode = 'no-store';
    const privateRun = await run('no-store', ['check', ...common, '--cache', path.join(dir, 'private-cache'), '--verbose']);
    ordered(privateRun); assert.deepEqual(privateRun.http.map(r => r.status), [200, 200, 200, 200]);
    mode = 'gzip';
    const compressed = await run('unexpected-compression', ['check', ...common, '--cache', path.join(dir, 'gzip-cache'), '--verbose'], 1);
    assert.ok(compressed.stderr.includes('ILI-RES-0003') && compressed.stderr.includes('input/output failure while loading'));
    assert.ok(!fs.existsSync(path.join(dir, 'gzip-cache', 'metadata-v1'))); assert.ok(!compressed.stderr.includes('source downloaded'));
    mode = 'identity';
    const color = await run('color-always', ['check', ...common, '--verbose', '--color', 'always']);
    assert.ok(color.stderr.startsWith('\x1b[36mINFO\x1b[0m')); assert.ok(color.stderr.includes('\x1b[90mILI-RES-0013\x1b[0m'));
    const plain = await run('color-never', ['check', ...common, '--verbose', '--color', 'never']);
    assert.ok(!plain.stderr.includes('\x1b')); ordered(plain);
    const version = await run('version', ['version', '--json']);
    assert.equal(JSON.parse(version.stdout).diagnosticCatalogVersion, 'interlis-diagnostics/v4');
    const lib = path.resolve(path.dirname(launcher), '../lib');
    const jars = Object.fromEntries(fs.readdirSync(lib).filter(f => f.endsWith('.jar')).sort()
        .map(f => [f, crypto.createHash('sha256').update(fs.readFileSync(path.join(lib, f))).digest('hex')]));
    fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({passed: true, node: process.version, javaHome: process.env.JAVA_HOME, launcher, cases, jars}, null, 2));
    console.log('CLI_LOG_ORDER_CACHE_PROCESSES_PASS ' + cases.length);
})().catch(error => {
    fs.writeFileSync(path.join(out, 'failed.json'), JSON.stringify({error: String(error), cases}, null, 2));
    console.error(error); process.exitCode = 1;
}).finally(() => { server.closeAllConnections(); server.close(); });
