'use strict';
// Test harness only. The product Worker/Promise transport has its own real
// browser contracts; semantic regressions run next to the same Wasm compiler
// to avoid thousands of WebDriver/background-tab message round trips.
self.onmessage=async event=>{
  if(event.data?.kind!=='verify')return;
  self.onmessage=null;
  let requests=0;const started=performance.now();
  try{
    importScripts('interlis-compiler.wasm-runtime.js','stabilization-contract.js');
    const api=(await self.TeaVM.wasmGC.load('interlis-compiler.wasm')).exports;
    const capabilities=JSON.parse(api.handle(JSON.stringify({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'browser-suite-capabilities',operation:'capabilities',params:{}})));
    if(capabilities.status!=='ok')throw new Error('compiler capabilities unavailable');
    const stabilizationCases=await verifyStabilization({handle:request=>{
      requests++;if(requests%1000===0)self.postMessage({kind:'progress',requests,elapsedMs:performance.now()-started});
      return api.handle(request);
    }});
    self.postMessage({kind:'result',result:{stabilizationCases,toolVersion:capabilities.result.toolVersion,protocolVersion:capabilities.protocolVersion,requests,elapsedMs:performance.now()-started,execution:'same TeaVM WasmGC compiler inside a real browser Worker; transport tested separately'}});
  }catch(error){self.postMessage({kind:'error',message:String(error)});}
};
