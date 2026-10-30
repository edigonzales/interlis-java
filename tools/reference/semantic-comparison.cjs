// Compare actual portable public projections to this run's JVM and pinned-reference output.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
  const root=path.resolve(__dirname,'../..'),cases=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
  const js=require(path.join(root,'interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js'));
  const dir=path.join(root,'interlis-wasm-teavm/build/generated/teavm/wasm-gc');
  require(path.join(dir,'interlis-compiler.wasm-runtime.js'));
  const wasm=(await globalThis.TeaVM.wasmGC.load(path.join(dir,'interlis-compiler.wasm'))).exports;
  const results=[];
  for(const [backend,api] of [['js',js],['wasmgc',wasm]])for(const entry of cases){
    const text=fs.readFileSync(path.join(root,entry.source),'utf8'),sourceId='memory:/a3.ili';
    const call=(operation,sessionId,params={})=>{
      const r=JSON.parse(api.handle(JSON.stringify({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'a3',operation,sessionId,params})));
      assert.equal(r.status,'ok',JSON.stringify(r));return r.result;
    };
    const {sessionId}=call('openSession',undefined,{sources:[{sourceId,text}]});
    try{
      const compiled=call('compile',sessionId,{expectedWorkspaceRevision:'1',roots:[{kind:'byName',modelName:'F'}]});
      const actual={outcome:compiled.outcome,elements:{}};
      if(compiled.outcome==='succeeded')for(const name of ['F.Child','F.T.Link','F.U.Link']){
        const simple=name.slice(name.lastIndexOf('.')+1);let start=name==='F.Child'?0:text.indexOf(name.includes('.U.')?'TOPIC U':'TOPIC T');
        start=text.indexOf(simple+' ',start);
        const p=call('query',sessionId,{snapshotId:compiled.snapshotId,query:{kind:'semanticProperties',sourceId,revision:'1',startOffset:start,endOffset:start}});
        assert.equal(p.qualifiedName,name);actual.elements[name]=p.effectiveMembers.map(m=>({name:m.name,targets:m.targets}));
      }
      assert.deepEqual(actual,entry.jvm);assert.deepEqual(actual,entry.reference);results.push({backend,source:entry.source,actual});
    }finally{call('closeSession',sessionId);}
  }
  fs.writeFileSync(path.join(root,'build/reports/g2/a3-portable.json'),JSON.stringify({status:'passed',cases:results},null,2)+'\n');
  console.log('A3_PORTABLE_PASS '+results.length+' actual projections compared to fresh JVM/reference');
})().catch(e=>{console.error(e);process.exitCode=1;});
