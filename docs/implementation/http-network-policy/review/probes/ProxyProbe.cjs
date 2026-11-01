// Reviewer-owned narrow launcher counterprobes for proxy, precedence and timeout behavior.
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const cp = require('node:child_process');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '../../..');
const launcher = path.join(root, 'build/reviewer/distribution/bin/interlis-compiler');
const out = path.join(__dirname, 'proxy-probe-results.json');
const dir = fs.mkdtempSync(path.join(__dirname, 'node-run-'));
const home = path.join(dir, 'home');
fs.mkdirSync(home);

const model = 'INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
const index = '<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage>'
    + '<Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
let mode = 'normal';
const requests = [];
const cases = [];

function handler(kind) {
    return (req, res) => {
        res.on('error', () => { });
        const absolute = req.url.startsWith('http://') || req.url.startsWith('https://');
        const url = new URL(req.url, 'http://localhost');
        requests.push({ kind, path: url.pathname, absolute, at: new Date().toISOString() });
        const body = url.pathname === '/ilimodels.xml' ? index
            : url.pathname === '/Basis.ili' ? model : null;
        if (body === null) {
            res.writeHead(404).end();
            return;
        }
        if (kind === 'origin' && mode === 'stream' && url.pathname === '/Basis.ili') {
            res.writeHead(200, { 'Content-Type': 'text/plain' });
            res.flushHeaders();
            let at = 0;
            const timer = setInterval(() => {
                if (res.destroyed || res.writableEnded) {
                    clearInterval(timer);
                    return;
                }
                if (at >= body.length) {
                    clearInterval(timer);
                    res.end();
                    return;
                }
                const next = Math.min(at + 8, body.length);
                res.write(body.slice(at, next));
                at = next;
                if (at >= body.length) {
                    clearInterval(timer);
                    res.end();
                }
            }, 300);
            res.on('close', () => clearInterval(timer));
            return;
        }
        res.writeHead(200, {
            ETag: '"' + crypto.createHash('sha256').update(body).digest('hex') + '"'
        }).end(body);
    };
}

const origin = http.createServer(handler('origin'));
const proxy = http.createServer(handler('proxy'));

async function run(name, args, options = {}) {
    const expected = options.expected ?? 0;
    const javaOptions = options.javaOptions ?? '';
    const start = requests.length;
    const started = Date.now();
    const result = await new Promise((resolve, reject) => {
        const child = cp.spawn(launcher, args, {
            cwd: dir,
            env: {
                ...process.env,
                HOME: home,
                JAVA_OPTS: '-Duser.home=' + home + (javaOptions ? ' ' + javaOptions : '')
            },
            stdio: ['ignore', 'pipe', 'pipe']
        });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', chunk => { stdout += chunk; });
        child.stderr.on('data', chunk => { stderr += chunk; });
        const timer = setTimeout(() => child.kill('SIGKILL'), 30000);
        child.on('error', reject);
        child.on('close', (exit, signal) => {
            clearTimeout(timer);
            resolve({ exit, signal, stdout, stderr, elapsedMs: Date.now() - started });
        });
    });
    const record = {
        name,
        command: [launcher, ...args],
        javaOptions,
        exit: result.exit,
        signal: result.signal,
        elapsedMs: result.elapsedMs,
        stdout: result.stdout,
        stderr: result.stderr,
        requests: requests.slice(start)
    };
    cases.push(record);
    fs.writeFileSync(out, JSON.stringify({ passed: false, cases }, null, 2));
    assert.equal(result.signal, null, name + ' signal');
    assert.equal(result.exit, expected, name + ': ' + result.stderr);
    return record;
}

function setting(settings, name) {
    const value = settings['--' + name];
    assert.ok(value, 'missing setting ' + name);
    return value;
}

(async () => {
    try {
        await Promise.all([
            new Promise(resolve => origin.listen(0, '127.0.0.1', resolve)),
            new Promise(resolve => proxy.listen(0, '127.0.0.1', resolve))
        ]);
        const base = 'http://127.0.0.1:' + origin.address().port + '/';
        const remote = 'http://unreachable.invalid/';
        const port = String(proxy.address().port);
        const v4 = '-Djava.net.preferIPv4Stack=true';
        const common = ['--root', 'Basis', '--repository', base, '--no-cache', '--json'];

        let result = await run('explain-defaults', ['check', '--explain-config']);
        let settings = JSON.parse(result.stdout).settings;
        assert.equal(setting(settings, 'connect-timeout').values[0], '15');
        assert.equal(setting(settings, 'read-timeout').values[0], '40');
        assert.equal(setting(settings, 'request-timeout').values[0], 'off');
        assert.equal(setting(settings, 'proxy-mode').values[0], 'system');
        assert.equal(setting(settings, 'connect-timeout').origin, 'PROFILE_DEFAULT');
        assert.equal(result.requests.length, 0);

        result = await run('explicit-proxy-hostname', ['check', '--root', 'Basis', '--repository', remote,
            '--proxy', 'localhost', '--proxyPort', port, '--no-cache', '--json'], { javaOptions: v4 });
        assert.ok(result.requests.length > 0);
        assert.ok(result.requests.every(request => request.kind === 'proxy' && request.absolute));

        result = await run('system-proxy', ['check', '--root', 'Basis', '--repository', remote,
            '--no-cache', '--json'], {
            javaOptions: '-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=' + port + ' -Dhttp.nonProxyHosts= ' + v4
        });
        assert.ok(result.requests.length > 0);
        assert.ok(result.requests.every(request => request.kind === 'proxy' && request.absolute));

        result = await run('direct-overrides-system', ['check', ...common, '--no-proxy'], {
            javaOptions: '-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1 -Dhttp.nonProxyHosts= ' + v4
        });
        assert.ok(result.requests.length > 0);
        assert.ok(result.requests.every(request => request.kind === 'origin' && !request.absolute));

        fs.writeFileSync(path.join(dir, 'network.properties'),
            'connect-timeout=7\nread-timeout=9\nrequest-timeout=25\nproxy=old.invalid\nproxyPort=1\n');
        result = await run('config-precedence', ['check', '--config', 'network.properties',
            '--proxy', '127.0.0.1', '--proxyPort', port, '--read-timeout', '8',
            '--request-timeout', 'off', '--explain-config']);
        settings = JSON.parse(result.stdout).settings;
        assert.equal(setting(settings, 'connect-timeout').values[0], '7');
        assert.equal(setting(settings, 'connect-timeout').origin, 'CONFIGURATION_FILE');
        assert.equal(setting(settings, 'read-timeout').values[0], '8');
        assert.equal(setting(settings, 'read-timeout').origin, 'CLI');
        assert.equal(setting(settings, 'request-timeout').values[0], 'off');
        assert.equal(setting(settings, 'request-timeout').origin, 'CLI');
        assert.equal(setting(settings, 'proxy').values[0], '127.0.0.1');
        assert.equal(setting(settings, 'proxy').origin, 'CLI');
        assert.equal(setting(settings, 'proxyPort').values[0], port);
        assert.equal(setting(settings, 'proxyPort').origin, 'CLI');
        assert.equal(setting(settings, 'proxy-mode').values[0], 'http');
        assert.equal(setting(settings, 'proxy-mode').origin, 'CLI');
        assert.equal(result.requests.length, 0);

        fs.writeFileSync(path.join(dir, 'network.properties'),
            'proxy=127.0.0.1\nproxyPort=' + port + '\nconnect-timeout=3\nread-timeout=2\nrequest-timeout=off\n');
        result = await run('properties-proxy', ['check', '--root', 'Basis', '--repository', remote,
            '--config', 'network.properties', '--json'], { javaOptions: v4 });
        assert.ok(result.requests.length > 0);
        assert.ok(result.requests.every(request => request.kind === 'proxy' && request.absolute));

        result = await run('file-proxy-overridden-direct', ['check', ...common,
            '--config', 'network.properties', '--no-proxy']);
        assert.ok(result.requests.length > 0);
        assert.ok(result.requests.every(request => request.kind === 'origin' && !request.absolute));

        result = await run('invalid-timeout-no-network', ['check', '--read-timeout', '0',
            '--explain-config'], { expected: 2 });
        assert.equal(result.requests.length, 0);

        result = await run('proxy-conflict', ['check', '--proxy', '127.0.0.1',
            '--no-proxy', '--explain-config'], { expected: 2 });
        assert.equal(result.requests.length, 0);

        result = await run('orphan-port', ['check', '--proxyPort', '80',
            '--explain-config'], { expected: 2 });
        assert.equal(result.requests.length, 0);

        mode = 'stream';
        result = await run('total-timeout-progressing', ['check', ...common, '--no-proxy',
            '--read-timeout', '3', '--request-timeout', '1'], { expected: 1 });
        assert.ok(result.stdout.includes('TIMEOUT'));
        assert.ok(result.elapsedMs < 3000, 'total timeout took ' + result.elapsedMs);
        assert.ok(result.requests.length > 0);
        assert.ok(result.requests.every(request => request.kind === 'origin'));

        result = await run('read-inactivity-progressing', ['check', ...common, '--no-proxy',
            '--read-timeout', '1'], { expected: 0 });
        assert.ok(result.elapsedMs >= 2000, 'progressive read completed too quickly: ' + result.elapsedMs);
        assert.ok(result.stdout.includes('"status":"ok"'));
        assert.ok(result.requests.length > 0);
        assert.ok(result.requests.every(request => request.kind === 'origin'));
        mode = 'normal';

        const lib = path.join(root, 'build/reviewer/distribution/lib');
        const jars = Object.fromEntries(fs.readdirSync(lib).filter(name => name.endsWith('.jar')).sort()
            .map(name => [name, crypto.createHash('sha256').update(fs.readFileSync(path.join(lib, name))).digest('hex')]));
        fs.writeFileSync(out, JSON.stringify({
            passed: true,
            node: process.version,
            launcher,
            jars,
            cases
        }, null, 2));
        console.log('PROXY_PROBE_PASS ' + cases.length);
    } finally {
        origin.closeAllConnections?.();
        proxy.closeAllConnections?.();
        origin.close();
        proxy.close();
    }
})().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
