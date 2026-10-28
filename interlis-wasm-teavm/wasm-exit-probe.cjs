// Diagnostic only: no forced exit and no changes to the production loader.
const fs = require('node:fs');
const path = require('node:path');
const mode = process.argv[2] || 'load';
const wasm = path.join(__dirname, 'build/generated/teavm/wasm-gc/interlis-compiler.wasm');
const report = event => console.log(JSON.stringify({
  event, mode, node: process.version, v8: process.versions.v8,
  resources: process.getActiveResourcesInfo()
}));
process.on('beforeExit', () => report('beforeExit'));
process.on('exit', () => report('exit'));
(async () => {
  if (mode === 'compile') {
    await WebAssembly.compile(fs.readFileSync(wasm));
  } else {
    require(wasm + '-runtime.js');
    const api = (await globalThis.TeaVM.wasmGC.load(wasm)).exports;
    if (mode === 'capabilities') {
      const result = JSON.parse(api.handle(JSON.stringify({
        protocol: 'interlis.compiler', protocolVersion: '1.0', requestId: 'probe',
        operation: 'capabilities', params: {}
      })));
      if (result.status !== 'ok') throw new Error(JSON.stringify(result));
    } else if (mode === 'semantics') {
      const cases = await require('./stabilization-contract.cjs')(api);
      console.log(JSON.stringify({semanticCases: cases}));
    } else if (mode !== 'load') {
      throw new Error(`Unknown probe mode: ${mode}`);
    }
  }
  report('operation-complete');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
