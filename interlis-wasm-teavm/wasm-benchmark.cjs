const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

function runtimePath() {
  const candidates = ['build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js'];
  for (const candidate of candidates) {
    const resolved = path.resolve(__dirname, candidate);
    if (fs.existsSync(resolved)) return resolved;
  }
  throw new Error('TeaVM WasmGC runtime not found');
}

function wasmPath() {
  const candidates = ['build/generated/teavm/wasm-gc/interlis-compiler.wasm'];
  for (const candidate of candidates) {
    const resolved = path.resolve(__dirname, candidate);
    if (fs.existsSync(resolved)) return resolved;
  }
  throw new Error('TeaVM WasmGC module not found');
}

(async () => {
  const root = path.resolve(__dirname, '..');
  const manifest = JSON.parse(fs.readFileSync(path.join(root,
    'docs/evidence/p3/geo-corpus.json'), 'utf8'));
  const cache = path.resolve(root, process.env.GEO_CORPUS_CACHE || process.env.CONFORMANCE_CACHE || path.join(os.homedir(), '.cache/interlis-java/geo-corpus'));
  require(runtimePath());
  const api = (await globalThis.TeaVM.wasmGC.load(wasmPath())).exports;
  let counter = 0;
  const handle = request => JSON.parse(api.handle(JSON.stringify(request)));
  const envelope = (operation, sessionId, params) => {
    const request = {protocol: 'interlis.compiler', protocolVersion: '1.0',
      requestId: 'bench-' + (++counter), operation, params: params || {}};
    if (sessionId) request.sessionId = sessionId;
    return request;
  };
  const open = handle(envelope('openSession'));
  if (open.status !== 'ok') throw new Error('openSession failed');
  const sessionId = open.result.sessionId;
  const models = [];
  for (const entry of manifest.models) {
    if (!entry.runtimes.includes('wasm')) continue;
    const file = path.join(cache, entry.file.replaceAll('/', '__'));
    const bytes = fs.readFileSync(file);
    if (bytes.length !== entry.bytes || crypto.createHash('sha256').update(bytes).digest('hex') !== entry.sha256) throw new Error('Changed corpus bytes: ' + entry.file);
    const text = bytes.toString('utf8');
    const sourceId = 'memory:/bench/' + entry.model + '.ili';
    let sourceRevision = null;
    const run = () => {
      const updated = handle(envelope('updateSource', sessionId, {
        sourceId, expectation: sourceRevision === null ? {kind: 'absent'}
            : {kind: 'at', revision: sourceRevision}, change: {kind: 'replace', text}
      }));
      if (updated.status !== 'ok') throw new Error('updateSource failed: '
        + JSON.stringify(updated));
      sourceRevision = updated.result.sourceRevision;
      const started = performance.now();
      const compiled = handle(envelope('compile', sessionId, {
        expectedWorkspaceRevision: updated.result.workspaceRevision,
        roots: [{kind: 'byName', modelName: entry.model}]
      }));
      const elapsed = performance.now() - started;
      if (compiled.status !== 'ok' || compiled.result.outcome !== 'succeeded') {
        throw new Error('compile failed: ' + JSON.stringify(compiled));
      }
      return elapsed;
    };
    run();
    const samples = [run(), run(), run()];
    models.push({model: entry.model, language: entry.language, bytes: entry.bytes,
      sha256: entry.sha256, compileMs: samples,
      compileMedianMs: samples.slice().sort((a, b) => a - b)[1]});
  }
  const result = {schemaVersion: '1.0', runtime: 'teavm-wasm-gc', node: process.version,
    iterationsPerModel: 3, warmupPerModel: 1,
    corpus: 'docs/evidence/p3/geo-corpus.json',
    corpusIndexSha256: manifest.index.sha256,
    wasmBytes: fs.statSync(wasmPath()).size, models,
    notes: 'In-process TeaVM WasmGC API measurements; warmup excluded.'};
  fs.mkdirSync(path.join(root, 'build/reports/performance'), {recursive: true});
  fs.writeFileSync(path.join(root, 'build/reports/performance/performance-wasm.json'),
    JSON.stringify(result, null, 2) + '\n');
  console.log('WASM_BENCHMARK_PASS ' + JSON.stringify({models: models.length}));
  const closed = handle(envelope('closeSession', sessionId));
  if (closed.status !== 'ok') throw new Error('closeSession failed');
  process.exitCode = 0;
})().catch(error => {
  console.error('WASM_BENCHMARK_FAIL', error);
  process.exit(1);
});
