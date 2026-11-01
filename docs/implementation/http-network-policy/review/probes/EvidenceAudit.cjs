// Reviewer-owned audit of the retained JUnit, process, SDK and execution evidence.
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../..');
const evidence = path.join(root, 'docs/implementation/http-network-policy/evidence');
const failures = [];
const check = (condition, message) => { if (!condition) failures.push(message); };

const suiteDirs = ['interlis-repository', 'interlis-compiler-cli'];
let suites = 0;
let tests = 0;
let skipped = 0;
let failuresCount = 0;
let errors = 0;
for (const directory of suiteDirs) {
    for (const file of fs.readdirSync(path.join(evidence, directory)).filter(name => name.endsWith('.xml'))) {
        const text = fs.readFileSync(path.join(evidence, directory, file), 'utf8');
        const suite = text.match(/<testsuite[^>]*>/);
        check(suite !== null, 'missing testsuite header: ' + file);
        if (!suite) continue;
        const attribute = name => {
            const match = suite[0].match(new RegExp(name + '="([^"]*)"'));
            return match ? Number(match[1]) : NaN;
        };
        suites++;
        tests += attribute('tests');
        skipped += attribute('skipped');
        failuresCount += attribute('failures');
        errors += attribute('errors');
    }
}
check(suites === 22, 'JUnit suite count ' + suites);
check(tests === 126, 'JUnit test count ' + tests);
check(skipped === 0, 'JUnit skipped ' + skipped);
check(failuresCount === 0, 'JUnit failures ' + failuresCount);
check(errors === 0, 'JUnit errors ' + errors);

const summary = JSON.parse(fs.readFileSync(path.join(evidence, 'junit-summary.json'), 'utf8'));
check(summary.suites === suites, 'summary suites');
check(summary.tests === tests, 'summary tests');
check(summary.skipped === skipped, 'summary skipped');
check(summary.failures === failuresCount, 'summary failures');
check(summary.errors === errors, 'summary errors');

const processes = JSON.parse(fs.readFileSync(path.join(evidence, 'processes.json'), 'utf8'));
const expectedExit = {
    'direct-overrides-jvm': 0,
    'explicit-proxy-check': 0,
    'explicit-proxy-compile': 0,
    'explicit-proxy-analyze': 0,
    'explicit-proxy-diagram': 0,
    'system-jvm-proxy': 0,
    'properties-proxy': 0,
    'file-proxy-overridden-direct': 0,
    'explain-defaults': 0,
    'usage-invalid-timeout': 2,
    'usage-proxy-conflict': 2,
    'usage-orphan-port': 2,
    'read-timeout': 1,
    'progressing-stream': 0,
    'request-total-timeout': 1,
    'warm-proxy-cache': 0,
    'offline-does-not-contact-proxy': 0
};
check(processes.passed === true, 'processes passed flag');
check(processes.cases.length === 17, 'process cases ' + processes.cases.length);
for (const item of processes.cases) {
    check(expectedExit[item.name] === item.exit, 'process exit ' + item.name + '=' + item.exit);
    check(item.signal === null, 'process signal ' + item.name);
    check(expectedExit[item.name] !== undefined, 'unexpected process ' + item.name);
}
const expectedKind = {
    'direct-overrides-jvm': 'origin',
    'explicit-proxy-check': 'proxy',
    'explicit-proxy-compile': 'proxy',
    'explicit-proxy-analyze': 'proxy',
    'explicit-proxy-diagram': 'proxy',
    'system-jvm-proxy': 'proxy',
    'properties-proxy': 'proxy',
    'file-proxy-overridden-direct': 'origin',
    'read-timeout': 'origin',
    'progressing-stream': 'origin',
    'request-total-timeout': 'origin',
    'warm-proxy-cache': 'proxy'
};
const noRequest = new Set(['explain-defaults', 'usage-invalid-timeout', 'usage-proxy-conflict',
    'usage-orphan-port', 'offline-does-not-contact-proxy']);
for (const item of processes.cases) {
    if (expectedKind[item.name]) {
        check(item.requests.length > 0, 'missing requests ' + item.name);
        for (const request of item.requests) {
            check(request.kind === expectedKind[item.name], 'request kind ' + item.name);
            check(request.absolute === (expectedKind[item.name] === 'proxy'), 'request shape ' + item.name);
        }
    }
    if (noRequest.has(item.name)) check(item.requests.length === 0, 'unexpected requests ' + item.name);
}
check(Object.keys(processes.jars).length === 7, 'process jar count');

const manifest = JSON.parse(fs.readFileSync(path.join(evidence, 'manifest.json'), 'utf8'));
for (const jar of manifest.jars) {
    check(processes.jars[jar.name] === jar.sha256, 'process jar hash ' + jar.name);
}

const sdk = JSON.parse(fs.readFileSync(path.join(evidence, 'sdk-processes.json'), 'utf8'));
check(Array.isArray(sdk) && sdk.length === 2, 'SDK process count');
check(sdk.every(item => item.exitCode === 0), 'SDK exit codes');
check(sdk.some(item => item.command[0].endsWith('javac')), 'SDK javac command');
check(sdk.some(item => item.command[0].endsWith('/java') || item.command[0].endsWith('\\java')), 'SDK java command');

const executions = JSON.parse(fs.readFileSync(path.join(evidence, 'executions.json'), 'utf8'));
const expectedLogs = {
    'compile.log': 0,
    'network-tests.log': 1,
    'network-tests-r2.log': 1,
    'header-probe.log': 0,
    'targeted-tests.log': 0,
    'distribution-build.log': 0,
    'processes.log': 1,
    'processes-r2.log': 0,
    'cli-final-tests.log': 1,
    'cli-final-tests-r2.log': 0,
    'processes-final.log': 0
};
check(executions.runs.length === 11, 'execution log count');
for (const run of executions.runs) {
    check(expectedLogs[run.log] === run.exitCode, 'execution exit ' + run.log + '=' + run.exitCode);
}
for (const log of ['processes-final.log', 'processes-r2.log']) {
    const text = fs.readFileSync(path.join(evidence, log), 'utf8');
    check(text.includes('HTTP_NETWORK_PROCESSES_PASS 17'), log + ' pass marker');
}

console.log('junit suites=' + suites + ' tests=' + tests + ' skipped=' + skipped
    + ' failures=' + failuresCount + ' errors=' + errors);
console.log('cli_processes=' + processes.cases.length + ' passed=' + processes.passed);
console.log('sdk_processes=' + sdk.length + ' exits=' + sdk.map(item => item.exitCode).join(','));
console.log('execution_logs=' + executions.runs.length);
if (failures.length) {
    console.log('FAILURES=' + failures.join(' | '));
    process.exitCode = 1;
} else {
    console.log('EVIDENCE_AUDIT_PASS');
}
