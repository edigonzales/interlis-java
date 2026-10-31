'use strict';
let ready;let lastOperation;let firstFailure;
self.onmessage=async event=>{
  try{
    if(event.data.kind==='load'){
      const started=performance.now();ready=(async()=>{
        importScripts('interlis-compiler.wasm-runtime.js','full-corpus-contract.js');
        return (await TeaVM.wasmGC.load('interlis-compiler.wasm')).exports;
      })();await ready;self.postMessage({kind:'ready',coldLoadMs:performance.now()-started});return;
    }
    if(event.data.kind!=='capture'||!ready)return;
    const api=await ready,result=await fullCorpusContract.capture(request=>{lastOperation=JSON.parse(request).operation;try{return api.handle(request);}catch(error){firstFailure??={operation:lastOperation,message:String(error),stack:String(error.stack??'').split('\n').slice(-35).join('\n')};throw error;} },event.data.root);
    self.postMessage({kind:'result',result});
  }catch(error){self.postMessage({kind:'error',message:String(error),firstFailure,operation:lastOperation,stack:String(error.stack??'').split('\n').slice(-35).join('\n')});}
};
