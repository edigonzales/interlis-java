'use strict';

const path = require('node:path');
const {registerCompilerTests} = require('./compiler-smoke.cjs');

registerCompilerTests(async () => {
  require('../build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js');
  const wasm = path.resolve(__dirname, '../build/generated/teavm/wasm-gc/interlis-compiler.wasm');
  return (await globalThis.TeaVM.wasmGC.load(wasm)).exports;
});
