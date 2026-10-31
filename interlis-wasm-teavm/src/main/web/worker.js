'use strict';
let epoch = null;
let api = null;
let ready = null;
let queue = Promise.resolve();

function fatal(requestId) {
  self.postMessage({kind: 'fatal', epoch, requestId});
}
function bootstrap(value) {
  if (epoch !== null) return;
  epoch = value;
  ready = (async () => {
    importScripts('interlis-compiler.wasm-runtime.js');
    api = (await globalThis.TeaVM.wasmGC.load('interlis-compiler.wasm')).exports;
    if (typeof api.handle !== 'function') throw new Error('missing compiler entry point');
    self.postMessage({kind: 'ready', epoch});
  })();
  ready.catch(() => fatal());
}
self.onmessage = event => {
  const message = event.data;
  if (message.kind === 'bootstrap') { bootstrap(message.epoch); return; }
  if (message.kind !== 'request' || message.epoch !== epoch || !ready) return;
  queue = queue.then(async () => {
    await ready;
    if (typeof message.request !== 'string') throw new Error('invalid compiler request');
    if(message.notifyStarted===true)self.postMessage({kind: 'started', epoch, requestId: message.requestId});
    const response = api.handle(message.request);
    if (typeof response !== 'string') throw new Error('invalid compiler response');
    self.postMessage({kind: 'response', epoch, requestId: message.requestId, response});
  }).catch(() => fatal(message.requestId));
};
