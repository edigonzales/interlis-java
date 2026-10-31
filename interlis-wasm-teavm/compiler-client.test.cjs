const {test}=require('node:test');
const assert=require('node:assert/strict');
const path=require('node:path');
const {CompilerClient}=require('./build/compiler-client.cjs');
const bundle=path.resolve(__dirname,'build/generated/teavm/js/interlis-compiler.js');
const BASE='INTERLIS 2.4;MODEL Base AT "x" VERSION "1"=DOMAIN D=TEXT;END Base.';
const ROOT='INTERLIS 2.4;MODEL Root AT "x" VERSION "1"=IMPORTS Base;TOPIC T=CLASS C=value:Base.D;END C;END T;END Root.';
const F='INTERLIS 2.4;MODEL F AT "x" VERSION "1"=END F.';
const flush=()=>new Promise(resolve=>setImmediate(resolve));
const outcome=p=>p.then(value=>({value}),error=>({error}));
class Port {
  onmessage=null;onerror=null;epoch=0;terminated=false;requests=[];
  constructor(blockAnalyze=false){delete require.cache[require.resolve(bundle)];this.api=require(bundle);this.blockAnalyze=blockAnalyze;}
  postMessage(message){
    if(message.kind==='bootstrap'){this.epoch=message.epoch;queueMicrotask(()=>this.onmessage?.({data:{kind:'ready',epoch:this.epoch}}));return;}
    if(message.kind!=='request')return;
    const request=JSON.parse(message.request);this.requests.push(request);
    if(this.blockAnalyze&&request.operation==='analyze')return;
    setImmediate(()=>{if(!this.terminated){const response=this.api.handle(message.request);this.onmessage?.({data:{kind:'response',epoch:this.epoch,requestId:message.requestId,response}});}});
  }
  terminate(){this.terminated=true;}
}
function create(options={},blockFirstAnalyze=false){const ports=[];const client=new CompilerClient({...options,workerFactory:()=>{const port=new Port(blockFirstAnalyze&&ports.length===0);ports.push(port);return port;}});return {client,ports};}
const roots=(client,s,modelName='F')=>({expectedWorkspaceRevision:client.state(s).workspaceRevision,roots:[{kind:'byName',modelName}]});
const replace=(sourceId,text,revision)=>({sourceId,expectation:revision?{kind:'at',revision}:{kind:'absent'},change:{kind:'replace',text}});

test('the real portable compiler and confirmed mirror agree for queued dependent edits and batch sources',async()=>{
  const {client}=create();
  try {
    const session=await client.openSession({sources:[{sourceId:'memory:/base',text:BASE},{sourceId:'memory:/root',text:ROOT}]});
    const a='\n!! first 😀',b='\n!! second';
    const first=client.updateSource(session,{sourceId:'memory:/root',expectation:{kind:'at',revision:'1'},change:{kind:'edits',edits:[{startOffset:ROOT.length,endOffset:ROOT.length,text:a}]}});
    const second=client.updateSource(session,{sourceId:'memory:/root',expectation:{kind:'at',revision:'2'},change:{kind:'edits',edits:[{startOffset:ROOT.length+a.length,endOffset:ROOT.length+a.length,text:b}]}});
    await Promise.all([first,second]);assert.equal(client.sourceText(session,'memory:/root'),ROOT+a+b);
    assert.equal(client.state(session).sourceRevisions['memory:/root'],'3');
    const compiled=await client.compile(session,roots(client,session,'Root'));assert.equal(compiled.outcome,'succeeded');
    const diagram=await client.diagram(session,{schemaId:compiled.schemaId});assert.equal(diagram.snapshotId,compiled.snapshotId);
    assert.ok(diagram.nodes.length>0);assert.ok(Object.isFrozen(diagram.nodes));
    assert.ok(diagram.nodes.filter(n=>n.anchor).every(n=>n.anchor.snapshotId===compiled.snapshotId));
    await client.releaseSnapshot(session,compiled.snapshotId);
    await assert.rejects(async()=>client.diagram(session,{schemaId:compiled.schemaId}),{code:'SNAPSHOT_EXPIRED'});
    await client.closeSession(session);assert.equal(client.currentSessions().length,0);
  }finally{await client.dispose();}
});

test('running cancellation restores every confirmed source and full configuration with new session and result handles',async()=>{
  let replacements;const {client,ports}=create({onRestored:value=>{replacements=value;}},true);
  try {
    const first=await client.openSession({configuration:{allowedLanguages:['2.3'],warningPolicy:'REJECT_WARNINGS',resolverCatalog:{Pinned:'memory:/catalogue'},cacheLimits:{maxSyntaxEntries:0}},sources:[{sourceId:'memory:/f',text:F}]});
    const second=await client.openSession({configuration:{allowedLanguages:['2.3']},sources:[{sourceId:'memory:/f',text:F.replace('2.4','2.3')}]});
    const rejected=await client.compile(first,roots(client,first));assert.equal(rejected.outcome,'rejected');
    const fingerprint=client.state(first).configurationFingerprint;
    const active=outcome(client.analyze(first,roots(client,first),{requestId:'cancel-active'}));
    const queued=outcome(client.compile(second,roots(client,second),{requestId:'old-queued'}));
    await flush();assert.equal(await client.cancel('cancel-active'),'workerRestarted');
    assert.equal((await active).error.code,'CANCELLED');assert.equal((await queued).error.code,'CANCELLED');
    assert.equal(ports[0].terminated,true);assert.equal(replacements.length,2);
    const a=replacements.find(r=>r.previous===first).current,b=replacements.find(r=>r.previous===second).current;
    assert.notEqual(a.id,first.id);assert.equal(client.state(a).configurationFingerprint,fingerprint);
    assert.equal(client.sourceText(a,'memory:/f'),F);
    await assert.rejects(async()=>client.compile(first,roots(client,a)),{code:'SESSION_EXPIRED'});
    await assert.rejects(async()=>client.query(a,{snapshotId:rejected.snapshotId,query:{kind:'symbolAt',sourceId:'memory:/f',revision:'1',startOffset:20,endOffset:20}}),{code:'SNAPSHOT_EXPIRED'});
    assert.equal((await client.compile(a,roots(client,a))).outcome,'rejected');
    assert.equal((await client.compile(b,roots(client,b))).outcome,'succeeded');
  }finally{await client.dispose();}
});

test('replay chunks a source grown through small edits beyond the accepted message limit',async()=>{
  let restored;const {client,ports}=create({onRestored:value=>{restored=value[0].current;}});
  try {
    let session=await client.openSession({limits:{maxMessageUtf16Length:800,maxSourceUtf16Length:2000,maxTotalSourceUtf16Length:'4000'},sources:[{sourceId:'memory:/f',text:F}]});
    let text=F;
    for(let i=0;i<8;i++) {
      const next='\n!! '+String(i)+'x'.repeat(120);
      await client.updateSource(session,{sourceId:'memory:/f',expectation:{kind:'at',revision:client.state(session).sourceRevisions['memory:/f']},change:{kind:'edits',edits:[{startOffset:text.length,endOffset:text.length,text:next}]}});
      text+=next;
    }
    await client.restart();session=restored;assert.equal(client.sourceText(session,'memory:/f'),text);
    assert.ok(ports[1].requests.some(r=>r.operation==='updateSource'&&r.params.change.kind==='edits'));
    assert.equal((await client.compile(session,roots(client,session))).outcome,'succeeded');
  }finally{await client.dispose();}
});

test('failed batches and retained-result limits do not mutate the accepted mirror',async()=>{
  const {client}=create();
  try {
    const session=await client.openSession({limits:{maxSources:1,maxRetainedSnapshots:1},sources:[{sourceId:'memory:/f',text:F}]});
    const before=client.state(session);
    await assert.rejects(client.updateSources(session,{expectedWorkspaceRevision:before.workspaceRevision,updates:[replace('memory:/f',F+'\n!! change','1'),replace('memory:/extra',F)]}),{code:'LIMIT_EXCEEDED'});
    assert.deepEqual(client.state(session),before);assert.equal(client.sourceText(session,'memory:/f'),F);
    const compiled=await client.compile(session,roots(client,session));assert.equal(compiled.outcome,'succeeded');
    await assert.rejects(client.analyze(session,roots(client,session)),{code:'LIMIT_EXCEEDED'});
    await client.releaseSnapshot(session,compiled.snapshotId);await client.releaseSnapshot(session,compiled.snapshotId);
    assert.ok((await client.analyze(session,roots(client,session))).snapshotId);
  }finally{await client.dispose();}
});

test('restoration observers can immediately use new handles and identical separate clients cannot exchange result handles',async()=>{
  let observed;const first=create({onRestored:pairs=>{const current=pairs[0].current;observed=first.client.compile(current,roots(first.client,current));}}),second=create();
  try {
    const options={sources:[{sourceId:'memory:/f',text:F}]};
    const a=await first.client.openSession(options),b=await second.client.openSession(options);
    const one=await first.client.compile(a,roots(first.client,a)),two=await second.client.compile(b,roots(second.client,b));
    assert.notEqual(one.snapshotId,two.snapshotId);
    await assert.rejects(second.client.query(b,{snapshotId:one.snapshotId,query:{kind:'symbolAt',sourceId:'memory:/f',revision:'1',startOffset:20,endOffset:20}}),{code:'SNAPSHOT_EXPIRED'});
    await first.client.restart();assert.equal((await observed).outcome,'succeeded');
  }finally{await first.client.dispose();await second.client.dispose();}
});


test('position query and explicit diagram selections preserve one client snapshot identity',async()=>{
  const {client}=create();
  try {
    const id='memory:/root',session=await client.openSession({sources:[{sourceId:'memory:/base',text:BASE},{sourceId:id,text:ROOT}]});
    const result=await client.compile(session,roots(client,session,'Root'));assert.equal(result.outcome,'succeeded');
    const cursor={sourceId:id,revision:'1',startOffset:ROOT.indexOf('Base.D')+5,endOffset:ROOT.indexOf('Base.D')+5};
    for(const kind of ['symbolAt','definition','hover','semanticProperties','completion']){
      const query=await client.query(session,{snapshotId:result.snapshotId,query:{kind,...cursor}});
      assert.equal(query.snapshotId,result.snapshotId,kind+' has mismatched snapshot');
    }
    const doc=await client.diagram(session,{schemaId:result.schemaId});
    const selected=await client.diagram(session,{snapshotId:result.snapshotId,selection:doc.selection});
    assert.equal(selected.snapshotId,result.snapshotId);assert.deepEqual(selected.selection,doc.selection);
    await client.releaseSnapshot(session,result.snapshotId);
  }finally{await client.dispose();}
});

test('a corrupt acknowledgement restarts from the complete confirmed mirror without admitting partial batch changes',async()=>{
  let inject=false,resolveRecovery;const recovered=new Promise(resolve=>{resolveRecovery=resolve;});
  const client=new CompilerClient({onRestored:resolveRecovery,workerFactory:()=>{
    const port=new Port();const deliver=port.postMessage.bind(port);
    port.postMessage=message=>{
      if(inject&&message.kind==='request'){
        inject=false;const parsed=JSON.parse(message.request),response=JSON.parse(port.api.handle(message.request));
        assert.equal(parsed.operation,'updateSources');response.result.updates[1].sourceRevision='invalid';
        setImmediate(()=>port.onmessage?.({data:{kind:'response',epoch:port.epoch,requestId:message.requestId,response:JSON.stringify(response)}}));
      }else deliver(message);
    };return port;
  }});
  try{
    const session=await client.openSession({sources:[{sourceId:'memory:/base',text:BASE},{sourceId:'memory:/root',text:ROOT}]});
    inject=true;
    await assert.rejects(client.updateSources(session,{expectedWorkspaceRevision:client.state(session).workspaceRevision,updates:[replace('memory:/base',BASE+'\n!! changed','1'),replace('memory:/root',ROOT+'\n!! changed','1')]}),{code:'RUNTIME_FAILURE'});
    const current=(await recovered)[0].current;
    assert.equal(client.sourceText(current,'memory:/base'),BASE);assert.equal(client.sourceText(current,'memory:/root'),ROOT);
    assert.equal((await client.compile(current,roots(client,current,'Root'))).outcome,'succeeded');
  }finally{await client.dispose();}
});

test('closing the same owned handle is idempotent, concurrent calls coalesce, and foreign handles remain invalid',async()=>{
  const {client,ports}=create(),other=create();
  try{
    const handle=await client.openSession({sources:[{sourceId:'memory:/f',text:F}]});
    await Promise.all([client.closeSession(handle),client.closeSession(handle)]);await client.closeSession(handle);
    assert.equal(ports[0].requests.filter(r=>r.operation==='closeSession').length,1);
    assert.equal(client.currentSessions().length,0);
    assert.throws(()=>client.state(handle),{code:'SESSION_EXPIRED'});
    await assert.rejects(other.client.closeSession(handle),{code:'SESSION_EXPIRED'});
  }finally{await client.dispose();await other.client.dispose();}
});

test('versioned diagram options reach the real compiler and partial snapshots remain visibly partial',async()=>{
  const {client}=create();const sourceId='memory:/partial',text='INTERLIS 2.4;MODEL F AT "x" VERSION "1"=IMPORTS Missing;TOPIC T=CLASS C=x:Missing.D;END C;CLASS Good=n:TEXT;END Good;END T;END F.';
  try{
    const handle=await client.openSession({sources:[{sourceId,text}]});const analysis=await client.analyze(handle,roots(client,handle));
    const doc=await client.diagram(handle,{snapshotId:analysis.snapshotId});assert.equal(doc.completeness,'PARTIAL');assert.ok(doc.nodes.some(n=>n.status==='UNRESOLVED'&&n.symbol===null));
    await assert.rejects(client.diagram(handle,{snapshotId:analysis.snapshotId,selection:[]}),{code:'INVALID_REQUEST'});
    const limited=await client.diagram(handle,{snapshotId:analysis.snapshotId,options:{schemaVersion:'1.0',budget:{maxNodes:1}}});assert.equal(limited.completeness,'LIMITED');assert.equal(limited.nodes.length,1);
    await assert.rejects(client.diagram(handle,{snapshotId:analysis.snapshotId,options:{schemaVersion:'1.0',scope:'NEIGHBOURHOOD'}}),{code:'UNSUPPORTED_OPERATION'});
    await client.releaseSnapshot(handle,analysis.snapshotId);await client.closeSession(handle);await client.closeSession(handle);
  }finally{await client.dispose();}
});

test('translation labels and bounded neighbourhoods preserve canonical ids and source navigation',async()=>{
  const {client}=create();
  const text='INTERLIS 2.4;MODEL English(en) AT "x" VERSION "1"=DOMAIN Identifier=TEXT*10;TOPIC Data=CLASS Road=name:Identifier;END Road;END Data;END English.'+
    'MODEL French(fr) AT "x" VERSION "1" TRANSLATION OF English["1"]=DOMAIN Identifiant=TEXT*10;TOPIC Donnees=CLASS Route=nom:Identifiant;END Route;END Donnees;END French.';
  try{
    const session=await client.openSession({sources:[{sourceId:'memory:/languages',text}]});
    const compiled=await client.compile(session,{expectedWorkspaceRevision:client.state(session).workspaceRevision,roots:[{kind:'bySource',source:'memory:/languages'}]});assert.equal(compiled.outcome,'succeeded',JSON.stringify(compiled));
    const all=await client.diagram(session,{schemaId:compiled.schemaId}),original=all.containers.find(c=>c.canonicalName==='English').symbol,translationModel=all.containers.find(c=>c.canonicalName==='French').symbol;
    const canonical=await client.diagram(session,{schemaId:compiled.schemaId,selection:[original]});
    const translated=await client.diagram(session,{schemaId:compiled.schemaId,selection:[original],options:{schemaVersion:'1.0',labelPolicy:'TRANSLATED',translationModel}});
    assert.deepEqual(translated.nodes.map(n=>n.id),canonical.nodes.map(n=>n.id));
    const road=canonical.nodes.find(n=>n.label==='Road'),route=translated.nodes.find(n=>n.id===road.id);assert.equal(route.label,'Route');assert.equal(route.canonicalName,'English.Data.Road');assert.deepEqual(route.anchor,road.anchor);
    const member=route.compartments.flatMap(c=>c.members).find(m=>m.label==='nom');assert.equal(member.typeLabel,'Identifiant');
    const adapter=require('./build/diagram-adapter.cjs'),sources=new Map([['memory:/languages',{text,revision:'1'}]]),view=adapter.adapt(translated,sources);
    assert.equal(adapter.resolveNavigation(view,route.id,compiled.snapshotId,sources).kind,'resolved');assert.ok((await adapter.render(view)).svg.includes('Route'));
    const near=await client.diagram(session,{schemaId:compiled.schemaId,selection:[road.symbol],options:{schemaVersion:'1.0',scope:'NEIGHBOURHOOD',neighbourhoodDepth:0}});
    assert.deepEqual(near.nodes.filter(n=>n.kind!=='EXTERNAL_REFERENCE').map(n=>n.label),['Road']);
    const wide=await client.diagram(session,{schemaId:compiled.schemaId,selection:[road.symbol],options:{schemaVersion:'1.0',scope:'NEIGHBOURHOOD',neighbourhoodDepth:1}});
    assert.deepEqual(wide.nodes.filter(n=>n.kind!=='EXTERNAL_REFERENCE').map(n=>n.label).sort(),['Identifier','Road']);
    await assert.rejects(()=>client.diagram(session,{schemaId:compiled.schemaId,options:{schemaVersion:'1.0',scope:'NEIGHBOURHOOD',neighbourhoodDepth:33}}),{code:'INVALID_REQUEST'});
    await assert.rejects(()=>client.diagram(session,{schemaId:compiled.schemaId,options:{schemaVersion:'1.0',labelPolicy:'TRANSLATED',translationModel:{...translationModel,snapshot:'foreign'}}}),{code:'SNAPSHOT_EXPIRED'});
  }finally{await client.dispose();}
});

test('rejected compile roots are structured original and resolved selectors on the real portable backend',async()=>{
  const {client}=create();
  try{
    const session=await client.openSession({sources:[{sourceId:'memory:/bad',text:'INTERLIS 2.4;MODEL Bad AT "x" VERSION "v1"=STRUCTURE S=n:Missing;END S;END Bad.'}]});
    for(const roots of [[{kind:'byName',modelName:'Bad'}],[{kind:'byName',modelName:'Bad',modelVersion:'v1',sourceHint:'memory:/bad'}],[{kind:'bySource',source:'memory:/bad'}]]){
      const compiled=await client.compile(session,{expectedWorkspaceRevision:client.state(session).workspaceRevision,roots});assert.equal(compiled.outcome,'rejected');assert.deepEqual(compiled.rejection.requestedRoots,roots);
      assert.equal(compiled.rejection.resolvedRoots[0].kind,roots[0].kind);assert.equal(typeof compiled.rejection.resolvedRoots[0],'object');
    }
  }finally{await client.dispose();}
});
