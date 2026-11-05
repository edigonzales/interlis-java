'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const evidence=require('./runtime-evidence.cjs'),root=path.resolve(__dirname,'../..');
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const files={
  'teavm-js':['interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js'],
  'teavm-wasm':['interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm','interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js'],
  webimage:['interlis-wasm-webimage/build/webimage/interlis-compiler.js','interlis-wasm-webimage/build/webimage/interlis-compiler.js.wasm']
};
(async()=>{
  const [backend,manifest,expected]=process.argv.slice(2);
  if(!files[backend]||!manifest||!path.isAbsolute(manifest)||!/^[a-f0-9]{64}$/.test(expected||''))throw Error('Expected backend, absolute manifest and producer manifest hash');
  const encoded=fs.readFileSync(manifest);assert.equal(sha(encoded),expected);
  const inputs=JSON.parse(encoded),versionFile=path.join(path.dirname(manifest),'resources/version-manifest.json');
  const versionBytes=fs.readFileSync(versionFile);assert.equal(sha(versionBytes),inputs.files['resources/version-manifest.json'].sha256);
  const version=JSON.parse(versionBytes),stamp=evidence.begin(files[backend]);
  let handle;
  if(backend==='teavm-js')handle=require(path.join(root,files[backend][0])).handle;
  else if(backend==='teavm-wasm'){
    require(path.join(root,files[backend][1]));
    handle=(await TeaVM.wasmGC.load(path.join(root,files[backend][0]))).exports.handle;
  }else{
    require(path.join(root,files[backend][0]));
    await new Promise(resolve=>setTimeout(resolve,500));handle=globalThis.interlisCompilerHandle;
  }
  assert.equal(typeof handle,'function');let sequence=0;
  const call=(operation,params={},sessionId)=>{
    const request={protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'smoke-'+(++sequence),operation,params};
    if(sessionId)request.sessionId=sessionId;
    const response=JSON.parse(handle(JSON.stringify(request)));assert.equal(response.status,'ok',JSON.stringify(response));return response.result;
  };
  assert.equal(call('capabilities').toolVersion,version.version);
  const model='INTERLIS 2.4;\n!! Unicode ä😀\nMODEL Probe AT "https://example.interlis.ch" VERSION "1" = TOPIC Data = CLASS Item = label: TEXT*20; END Item; END Data; END Probe.\n';
  const cases=[];
  for(const invalid of [false,true]){
    const session=call('openSession').sessionId;
    const updated=call('updateSource',{sourceId:'memory:/probe.ili',expectation:{kind:'absent'},change:{kind:'replace',text:invalid?model.replace('TEXT*20','MissingType'):model}},session);
    const compiled=call('compile',{expectedWorkspaceRevision:updated.workspaceRevision,roots:[{kind:'byName',modelName:'Probe'}]},session);
    assert.equal(compiled.outcome,invalid?'rejected':'succeeded');
    call('closeSession',{},session);cases.push({name:invalid?'invalid-type':'valid-unicode',outcome:compiled.outcome});
  }
  assert.equal(sha(fs.readFileSync(manifest)),expected);
  const result={...evidence.finish(stamp),status:'passed',scope:'consumer-smoke-only',backend,node:process.version,
    manifestSha256:expected,snapshotDigest:inputs.snapshotDigest,protocolRequests:sequence,cases};
  const output=path.join(root,backend==='webimage'?'interlis-wasm-webimage':'interlis-wasm-teavm','build','consumer-smoke-'+backend+'.json');
  fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n');console.log('BACKEND_SMOKE_PASS '+backend);
})().catch(error=>{console.error(error);process.exitCode=1;});
