(function(scope){
'use strict';
const assert=(value,message)=>{if(!value)throw Error(message);};
/** Actual public session edits of the largest pinned closure; originals stay immutable. */
async function editor(handle,root,edits=1000){
  let sequence=0;const raw=async(operation,sessionId,params={})=>JSON.parse(await handle(JSON.stringify({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'editor-'+ ++sequence,operation,params,...(sessionId?{sessionId}:{})})));
  const call=async(...args)=>{const result=await raw(...args);assert(result.status==='ok','editor protocol failure: '+JSON.stringify(result));return result.result;};
  const source=root.sources.find(s=>root.roots.some(r=>r.sourceHint===s.sourceId))||root.sources.reduce((a,b)=>a.text.length>b.text.length?a:b);
  const closureUtf16=root.sources.reduce((n,s)=>n+s.text.length,0);assert(closureUtf16>=200000,'editor closure too small');
  const configuration={...root.configuration,cacheLimits:{maxSyntaxEntries:4,maxAnalysisEntries:4,maxQueryEntries:32,maxRetainedSourceUtf16Units:2000000}};
  const opened=await call('openSession',null,{sources:root.sources.map(({sourceId,text})=>({sourceId,text})),configuration,limits:{maxRetainedSnapshots:4}}),session=opened.sessionId;
  let revision='1',workspace=opened.workspaceRevision,retained=[],maxRetained=0,released=0,staleRejected=0;const samples=[];
  try{
    for(let i=0;i<edits;i++){
      const start=performance.now(),previous=workspace;
      const receipt=await call('updateSource',session,{sourceId:source.sourceId,expectation:{kind:'at',revision},change:{kind:'replace',text:source.text+'\n!! editor '+i+'\n'}});
      revision=receipt.sourceRevision;workspace=receipt.workspaceRevision;assert(previous!==workspace,'edit did not advance workspace');
      if(retained.length===4){const expired=retained.shift();await call('releaseSnapshot',session,{snapshotId:expired});const probe=await raw('diagram',session,{snapshotId:expired});assert(probe.status==='error'&&probe.error.code==='SNAPSHOT_EXPIRED','released snapshot was accepted');released++;}
      const stale=await raw('compile',session,{expectedWorkspaceRevision:previous,roots:root.roots});assert(stale.status==='error'&&stale.error.code==='VERSION_CONFLICT','stale workspace probe: '+JSON.stringify(stale));staleRejected++;
      const compiled=await call('compile',session,{expectedWorkspaceRevision:workspace,roots:root.roots});assert(['succeeded','rejected'].includes(compiled.outcome),'unexpected edit outcome');assert(Object.hasOwn(compiled,'schemaId')===(compiled.outcome==='succeeded'),'edit schema gate violated');
      retained.push(compiled.snapshotId);maxRetained=Math.max(maxRetained,retained.length);samples.push(performance.now()-start);
      if(i%100===99&&typeof scope.editorProgress==='function')scope.editorProgress(i+1);
    }
    for(const snapshotId of retained)await call('releaseSnapshot',session,{snapshotId});
    const sorted=[...samples].sort((a,b)=>a-b);
    return {captureStatus:'complete',model:root.model,closureUtf16,editedSource:source.sourceId,edits,samplesMs:samples,p95Ms:sorted[Math.ceil(.95*sorted.length)-1],maxMs:sorted.at(-1),maxRetainedSnapshots:maxRetained,releasedDuringEdits:released,staleRevisionRejections:staleRejected,staleHandleAcceptances:0,cacheLimits:configuration.cacheLimits};
  }finally{await call('closeSession',session);}
}
if(typeof module!=='undefined')module.exports=editor;else scope.fullCorpusEditor=editor;
})(globalThis);
