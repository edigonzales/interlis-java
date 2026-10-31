'use strict';
const sourceFingerprint=require('./profile.cjs').fingerprint(),commitInfo=require('./profile.cjs').commitInfo();
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),os=require('node:os');
const {capture}=require('./full-corpus-contract.js');
(async()=>{
  const root=path.resolve(__dirname,'../..'),directory=path.join(root,'build/reports/performance/full-corpus'),out=path.join(directory,'wasm.json');fs.rmSync(out,{force:true});
  const prepared=JSON.parse(fs.readFileSync(path.join(directory,'prepared.json'),'utf8')),baseline=JSON.parse(fs.readFileSync(path.join(directory,'jvm.json'),'utf8'));
  if(baseline.captureStatus!=='complete'||baseline.manifestSha256!==prepared.manifestSha256||baseline.roots.length!==347||prepared.roots.length!==347)throw Error('missing/stale/incomplete JVM parity capture');
  const started=performance.now(),runtime=path.join(root,'interlis-wasm-teavm/build/generated/teavm/wasm-gc');
  require(runtime+'/interlis-compiler.wasm-runtime.js');const api=(await TeaVM.wasmGC.load(runtime+'/interlis-compiler.wasm')).exports,coldLoadMs=performance.now()-started;
  const report={...commitInfo,wasmSha256:require('./profile.cjs').hash(fs.readFileSync(path.join(runtime,'interlis-compiler.wasm'))),sourceFingerprint,runtime:'teavm-wasm-gc-node',node:process.version,os:os.platform(),arch:os.arch(),processors:os.cpus().length,profile:prepared.profile,manifestSha256:prepared.manifestSha256,coldLoadMs,roots:[]};
  try{for(let i=0;i<prepared.roots.length;i++){
    const row=await capture(request=>api.handle(request),prepared.roots[i]);report.roots.push(row);
    if(row.model!==baseline.roots[i].model||JSON.stringify(row.semantic)!==JSON.stringify(baseline.roots[i].semantic))throw Error('JVM/WasmGC semantic parity failed: '+row.model);
    if((i+1)%10===0)fs.writeFileSync(out,JSON.stringify(report)+'\n');console.log('FULL_CORPUS_WASM',i+1,row.model,row.outcome);
  }report.maxRssBytes=process.resourceUsage().maxRSS*1024;report.captureStatus='complete';report.elapsedMs=performance.now()-started;}finally{fs.writeFileSync(out,JSON.stringify(report)+'\n');}
  console.log('FULL_CORPUS_WASM_CAPTURE_COMPLETE 347; acceptance and budgets are separate');
})().catch(error=>{console.error(error);process.exitCode=1;});
