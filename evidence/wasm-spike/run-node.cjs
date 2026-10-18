const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const backend = process.argv[2] || 'wasm';
  let api;
  if (backend === 'wasm') {
    require('./target/wasm/classes.wasm-runtime.js');
    api = (await globalThis.TeaVM.wasmGC.load(path.resolve(__dirname, 'target/wasm/interlis.wasm'))).exports;
    api.main([]);
  } else if (backend === 'js') {
    api = require('./target/js/interlis.js');
    await new Promise((resolve, reject) => api.main([], result => result instanceof Error ? reject(result) : resolve(result)));
  } else {
    throw new Error('Expected wasm or js');
  }
  const result = JSON.parse(api.compile('INTERLIS 2.3; MODEL FromJS AT "https://interlis.guru" VERSION "2026-10-18" = END FromJS.'));
  if (result.errors !== 0 || result.eof !== true || result.models[0] !== 'FromJS:0') throw new Error('JS export bridge failed: ' + JSON.stringify(result));
  console.log('export ' + JSON.stringify(result));
})().catch(error => { console.error(error); process.exitCode = 1; });
