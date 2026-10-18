let ready = null;
let api = null;

function load() {
  if (ready) return ready;
  ready = (async () => {
    importScripts('interlis-compiler.wasm-runtime.js');
    api = (await globalThis.TeaVM.wasmGC.load('interlis-compiler.wasm')).exports;
    return true;
  })();
  return ready;
}

self.onmessage = async (event) => {
  const {id, request} = event.data;
  try {
    await load();
    const response = api.handle(request);
    self.postMessage({id, response});
  } catch (error) {
    self.postMessage({id, error: String(error && error.message ? error.message : error)});
  }
};
