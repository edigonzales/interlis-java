(function(scope){
'use strict';
const assert=(value,message)=>{if(!value)throw Error(message);};
function normalize(value,snapshot){
  if(Array.isArray(value))return value.map(item=>normalize(item,snapshot));
  if(value===null||typeof value!=='object')return value;
  const result={};for(const key of Object.keys(value).sort()){
    if(key==='schemaId')continue;
    if(key==='snapshotId'||key==='snapshot'){assert(value[key]===snapshot,'foreign snapshot in canonical capture');result[key]='CURRENT';}
    else result[key]=normalize(value[key],snapshot);
  }return result;
}
async function capture(handle,root,measuredRuns=3){
  let counter=0;
  const call=async(operation,sessionId,params={})=>{
    const response=JSON.parse(await handle(JSON.stringify({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'full-'+ ++counter,operation,params,...(sessionId?{sessionId}:{})})));
    assert(response.status==='ok','unexpected public protocol error: '+JSON.stringify(response));return response.result;
  };
  const openStart=performance.now();
  const opened=await call('openSession',null,{sources:root.sources.map(({sourceId,text})=>({sourceId,text})),configuration:root.configuration});
  const openMs=performance.now()-openStart,session=opened.sessionId;
  try{
    const request={expectedWorkspaceRevision:opened.workspaceRevision,roots:root.roots},first=performance.now();
    const compiled=await call('compile',session,request),firstCompileMs=performance.now()-first;
    assert(['succeeded','rejected'].includes(compiled.outcome),'unexpected corpus outcome');
    assert(Object.hasOwn(compiled,'schemaId')===(compiled.outcome==='succeeded'),'schema gate mismatch');
    const snapshot=compiled.snapshotId,diagram=await call('diagram',session,{snapshotId:snapshot});
    assert(diagram.completeness!=='LIMITED','full corpus projection exceeded default budget');
    const properties=[];
    for(const node of diagram.nodes){
      if(!node.anchor||!node.symbol)continue;
      const range=node.anchor.nameRange;
      const property=await call('query',session,{snapshotId:snapshot,query:{kind:'semanticProperties',sourceId:range.source,revision:range.revision,startOffset:range.startOffset,endOffset:range.startOffset}});
      assert(property.kind==='semanticProperties'&&property.symbolId===node.symbol.symbol,'diagram anchor does not denote its published symbol');properties.push(property);
    }
    const semantic=normalize({compile:compiled,diagram,properties},snapshot),warmCompileMs=[];
    for(let i=0;i<measuredRuns;i++){
      const started=performance.now(),repeated=await call('compile',session,request);warmCompileMs.push(performance.now()-started);
      assert(JSON.stringify(normalize(repeated,repeated.snapshotId))===JSON.stringify(normalize(compiled,snapshot)),'unstable warm corpus compile');
      await call('releaseSnapshot',session,{snapshotId:repeated.snapshotId});
    }
    return {model:root.model,version:root.version,file:root.file,outcome:compiled.outcome,openMs,firstCompileMs,warmCompileMs,semantic};
  }finally{await call('closeSession',session);}
}
const api={capture,normalize};if(typeof module!=='undefined')module.exports=api;else scope.fullCorpusContract=api;
})(globalThis);
