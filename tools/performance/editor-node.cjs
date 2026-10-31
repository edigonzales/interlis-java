'use strict';
const sourceFingerprint=require('./profile.cjs').fingerprint(),commitInfo=require('./profile.cjs').commitInfo();
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
(async()=>{
 const directory=path.resolve(__dirname,'../../build/reports/performance/full-corpus'),prepared=JSON.parse(fs.readFileSync(directory+'/prepared.json')),root=prepared.roots.reduce((a,b)=>a.sources.reduce((n,s)=>n+s.text.length,0)>b.sources.reduce((n,s)=>n+s.text.length,0)?a:b),runtime=path.resolve(__dirname,'../../interlis-wasm-teavm/build/generated/teavm/wasm-gc');
 require(runtime+'/interlis-compiler.wasm-runtime.js');const api=(await TeaVM.wasmGC.load(runtime+'/interlis-compiler.wasm')).exports;
 globalThis.editorProgress=n=>console.log('EDITOR_WASM',n,'rss',process.memoryUsage().rss);
 const report=await require('./editor-contract.js')(request=>api.handle(request),root,process.env.EDITOR_SMOKE==='1'?2:1000);
 Object.assign(report,{...commitInfo,wasmSha256:require('./profile.cjs').hash(fs.readFileSync(path.join(runtime,'interlis-compiler.wasm'))),sourceFingerprint,runtime:'teavm-wasm-gc-node',manifestSha256:prepared.manifestSha256,node:process.version,os:os.platform(),arch:os.arch(),rssBytes:process.memoryUsage().rss,maxRssBytes:process.resourceUsage().maxRSS*1024});
 fs.writeFileSync(directory+(process.env.EDITOR_SMOKE==='1'?'/editor-wasm-smoke.json':'/editor-wasm.json'),JSON.stringify(report)+'\n');console.log('EDITOR_WASM_COMPLETE',report.edits,report.p95Ms,report.maxRssBytes);
})().catch(error=>{console.error(error);process.exitCode=1;});
