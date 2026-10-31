'use strict';
async function verifyTokenClassification(api){
  let sequence=0,checks=0;
  const assert=(condition,message)=>{if(!condition)throw Error(message);checks++;};
  const call=async(operation,sessionId,params={})=>JSON.parse(await api.handle(JSON.stringify({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'token-'+ ++sequence,operation,params,...(sessionId?{sessionId}:{})})));
  for(const version of ['2.3','2.4']){
    const sourceId='memory:/token-classification',text='INTERLIS '+version+';\n!!'+'-'.repeat(100000)+'\n!! mixed A\n!! 🧭\nMODEL F AT "x" VERSION "1"=TOPIC T=CLASS C=value:TEXT;END C;END T;END F.';
    const opened=await call('openSession',null,{sources:[{sourceId,text}]});assert(opened.status==='ok','long trivia source opening failed');const session=opened.result.sessionId;
    try{
      const compiled=await call('compile',session,{expectedWorkspaceRevision:opened.result.workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});
      assert(compiled.status==='ok'&&compiled.result.outcome==='succeeded','long punctuation trivia broke compiler: '+JSON.stringify(compiled));
      const drawn=await call('diagram',session,{schemaId:compiled.result.schemaId});assert(drawn.status==='ok','long trivia projection failed');const node=drawn.result.nodes.find(n=>n.canonicalName==='F.T.C');
      assert(node?.anchor.nameRange.startOffset===text.lastIndexOf('CLASS C')+6&&node.anchor.nameRange.endOffset===text.lastIndexOf('CLASS C')+7,'UTF-16 anchor changed after long/Unicode trivia');
      assert(node.anchor.nameRange.source===sourceId&&node.anchor.nameRange.revision==='1','source identity/revision lost after long trivia');
    }finally{const closed=await call('closeSession',session);assert(closed.status==='ok','long trivia session did not close');}
  }
  const definitions='DOMAIN G(GENERIC)=COORD NUMERIC,NUMERIC;A=COORD 0.00..100.00[INTERLIS.m],0.00..100.00[INTERLIS.m],ROTATION 2->1;Azimuth=0.000..359.999 CIRCULAR[INTERLIS.rad]<G>;';
  for(const [body,expected] of [[definitions,'succeeded'],[definitions+'CONTEXT c=G=A;TOPIC T=CLASS C=a:Azimuth;END C;END T;','succeeded'],[definitions+'TOPIC T=CLASS C=a:Azimuth;END C;END T;','rejected'],[definitions.replace(',ROTATION 2->1','')+'CONTEXT c=G=A;TOPIC T=CLASS C=a:Azimuth;END C;END T;','rejected']]){
    const opened=await call('openSession',null,{sources:[{sourceId:'memory:/generic-reference',text:'INTERLIS 2.4;MODEL F AT "x" VERSION "1"='+body+'END F.'}]});const session=opened.result.sessionId;
    try {const compiled=await call('compile',session,{expectedWorkspaceRevision:opened.result.workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});assert(compiled.status==='ok'&&compiled.result.outcome===expected,'generic reference deferred/concrete validation failed: '+JSON.stringify(compiled));assert(Object.hasOwn(compiled.result,'schemaId')===(expected==='succeeded'),'generic reference bypassed schema gate');}
    finally {assert((await call('closeSession',session)).status==='ok','generic reference session did not close');}
  }
  return checks;
}
if(typeof module!=='undefined')module.exports=verifyTokenClassification;
else globalThis.verifyTokenClassification=verifyTokenClassification;
