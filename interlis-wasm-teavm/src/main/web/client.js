import {verifyBrowserClient} from './browser-client-contract.js';
import {CompilerClient} from './compiler-client.js';

const source=document.getElementById('source'),output=document.getElementById('output');
const picker=document.getElementById('sources');
const drafts=new Map([['memory:/browser.ili',source.value]]);
let activeSource='memory:/browser.ili',session=null,snapshot=null,schema=null,diagramDocument=null,generation=0,sequence=Promise.resolve(),activeRequest=null;
const client=new CompilerClient({onRestored:pairs=>{
  const pair=pairs.find(p=>p.previous===session);session=pair?.current??null;invalidate(false);
},onFailure:error=>{invalidate(false);output.textContent=error.code+': '+error.message;}});
function invalidate(release=true){const old=snapshot,handle=session;generation++;snapshot=null;schema=null;diagramDocument=null;if(release&&old&&handle)void client.releaseSnapshot(handle,old).catch(displayError);}
function serial(operation){const pending=sequence.then(operation);sequence=pending.catch(()=>{});return pending;}
function displayError(error){output.textContent=JSON.stringify({code:error.code??'HOST_FAILURE',message:error.message},null,2);}
function captured(){return JSON.stringify([...drafts]);}
async function ensureSession(){if(!session)session=await client.openSession();return session;}
async function discard(){const old=snapshot,handle=session;invalidate(false);if(old&&handle)await client.releaseSnapshot(handle,old);}
async function synchronize(){
  const handle=await ensureSession();await discard();const state=client.state(handle),updates=[];
  for(const [sourceId,text] of drafts)if(client.sourceText(handle,sourceId)!==text)updates.push({sourceId,expectation:state.sourceRevisions[sourceId]?{kind:'at',revision:state.sourceRevisions[sourceId]}:{kind:'absent'},change:{kind:'replace',text}});
  if(updates.length)await client.updateSources(handle,{expectedWorkspaceRevision:state.workspaceRevision,updates});
  for(const sourceId of Object.keys(client.state(handle).sourceRevisions))if(!drafts.has(sourceId))await client.removeSource(handle,{sourceId,expectedRevision:client.state(handle).sourceRevisions[sourceId]});
  return handle;
}
function request(handle){return {expectedWorkspaceRevision:client.state(handle).workspaceRevision,roots:[{kind:'bySource',source:activeSource}]};}
async function execute(operation){
  return serial(async()=>{
    const handle=await synchronize(),mark=generation,text=captured(),requestId='ui-'+Date.now()+'-'+generation;activeRequest=requestId;
    try{
      const result=await client[operation](handle,request(handle),{requestId});
      if(mark!==generation||text!==captured()||handle!==session){if(result.snapshotId)await client.releaseSnapshot(handle,result.snapshotId);return {status:'ok',result,stale:true};}
      snapshot=result.snapshotId??null;schema=result.schemaId??null;output.textContent=JSON.stringify(result,null,2);
      return {status:'ok',result};
    }finally{if(activeRequest===requestId)activeRequest=null;}
  });
}
const analyze=()=>execute('analyze'),compile=()=>execute('compile');
function navigate(id){
  const anchor=diagramDocument?.nodes.find(node=>node.id===id)?.anchor;
  const range=anchor?.nameRange;
  if(!range||!session||anchor.snapshotId!==snapshot||diagramDocument.sourceRevisions[range.source]!==range.revision
      ||client.state(session).sourceRevisions[range.source]!==range.revision
      ||drafts.get(range.source)!==client.sourceText(session,range.source)){
    return {kind:'stale',code:'STALE_SELECTION'};
  }
  activeSource=range.source;refreshPicker();source.value=drafts.get(activeSource);
  source.focus();source.setSelectionRange(range.startOffset,range.endOffset);
  return {kind:'resolved',range};
}
function refreshPicker(){picker.replaceChildren(...[...drafts.keys()].map(id=>{const option=document.createElement('option');option.value=id;option.textContent=id;option.selected=id===activeSource;return option;}));}
async function diagram(options){
  const compiled=await compile();
  if(compiled.stale||!compiled.result.snapshotId)return compiled;
  return serial(async()=>{
    const handle=session,mark=generation,current=snapshot,text=captured();
    if(current!==compiled.result.snapshotId)return {...compiled,stale:true};
    const requestId='diagram-'+Date.now()+'-'+mark;activeRequest=requestId;
    try {
      const result=await client.diagram(handle,{...(schema?{schemaId:schema}:{snapshotId:current}),...(options?{options}:{})},{requestId});
      if(mark!==generation||current!==snapshot||handle!==session||text!==captured())return {status:'ok',result,stale:true};
      diagramDocument=result;output.textContent=JSON.stringify({document:result},null,2);
      return {status:'ok',result};
    }finally{if(activeRequest===requestId)activeRequest=null;}
  });
}
async function restartWorker(){await client.restart();return compile();}
async function cancel(){if(!activeRequest)return 'alreadyCompleted';return client.cancel(activeRequest);}
async function benchmark(text,modelName,expectation){
  const handle=await client.openSession({sources:[{sourceId:'memory:/benchmark.ili',text}]});
  try{
    const req={expectedWorkspaceRevision:client.state(handle).workspaceRevision,roots:[{kind:'byName',modelName}]};
    const analyzed=await client.analyze(handle,req);await client.releaseSnapshot(handle,analyzed.snapshotId);
    const start=performance.now(),result=await client.compile(handle,req),elapsed=performance.now()-start;
    if(result.outcome!==(expectation?.outcome??'succeeded'))throw new Error('benchmark outcome: '+JSON.stringify(result));
    if(expectation){
      if(JSON.stringify(result.rejection?.reasons)!=='["SOURCE_ERRORS"]'||result.rejection.unsupportedFeatureIds.length)throw new Error('benchmark rejection mismatch');
      const diagnostics=structuredClone(result.diagnostics);
      for(const diagnostic of diagnostics){if(diagnostic.range?.source!=='memory:/benchmark.ili'||diagnostic.range.revision!==client.state(handle).sourceRevisions['memory:/benchmark.ili'])throw new Error('benchmark revision mismatch');delete diagnostic.id;delete diagnostic.range.source;delete diagnostic.range.revision;}
      const canonical=v=>Array.isArray(v)?v.map(canonical):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,canonical(v[k])])):v;
      if(JSON.stringify(canonical(diagnostics))!==JSON.stringify(canonical(expectation.diagnostics)))throw new Error('benchmark diagnostics mismatch: '+JSON.stringify(diagnostics));
    }
    if(result.snapshotId)await client.releaseSnapshot(handle,result.snapshotId);return elapsed;
  }finally{await client.closeSession(handle);}
}
async function runClientContract(){
  const assert=(condition,message)=>{if(!condition)throw new Error(message);};
  window.interlisContractProgress="transport";
  const transportChecks=await verifyBrowserClient();
  window.interlisContractProgress="UI";
  const initial=source.value;
  const analyzed=await analyze();assert(analyzed.result.snapshotId,'analysis snapshot missing');
  drafts.set(activeSource,initial.replace('height : 0..999;','height : ;'));
  const rejected=await compile();assert(rejected.result.outcome==='rejected','invalid source accepted');
  const partial=await diagram();assert(partial.result.inputKind==='ANALYSIS'&&partial.result.completeness==='PARTIAL','partial analysis is not readable as a partial diagram');
  drafts.set(activeSource,initial);
  const succeeded=await compile();assert(succeeded.result.outcome==='succeeded','valid source rejected: '+JSON.stringify(succeeded));
  const drawn=await diagram();assert(!drawn.stale&&drawn.result.nodes.length>0,'diagram missing');
  assert(JSON.stringify(drawn.result).includes('9007199254740993.0010'),'decimal lost');
  const node=drawn.result.nodes.find(n=>n.anchor);
  assert(navigate(node.id).kind==='resolved','source navigation failed');
  const revision=drawn.result.sourceRevisions[node.anchor.nameRange.source];
  source.value=initial+'\n!! newer revision';source.dispatchEvent(new Event('input'));
  assert(navigate(node.id).kind==='stale','old diagram navigated after source edit');
  const changed=await diagram();
  assert(!changed.stale&&changed.result.sourceRevisions[node.anchor.nameRange.source]!==revision,'new diagram did not use edited revision');
  const nodeCount=drawn.result.nodes.length,edgeCount=drawn.result.edges.length;
  const associationCount=drawn.result.nodes.filter(n=>n.kind==='ASSOCIATION').length;
  const base='INTERLIS 2.4;MODEL Base AT "x" VERSION "1"=DOMAIN D=TEXT;END Base.';
  const root='INTERLIS 2.4;MODEL Root AT "x" VERSION "1"=IMPORTS Base;TOPIC T=CLASS C=a:Base.D;END C;END T;END Root.';
  let restored;const local=new CompilerClient({onRestored:pairs=>{restored=pairs;}});
  let checks=0;
  try{
    let a=await local.openSession({sources:[{sourceId:'memory:/base',text:base},{sourceId:'memory:/root',text:root}],limits:{maxRetainedSnapshots:1},configuration:{warningPolicy:'REJECT_WARNINGS'}});
    let b=await local.openSession({sources:[{sourceId:'memory:/base',text:base}],configuration:{allowedLanguages:['2.3']}});
    const req=(h,name)=>({expectedWorkspaceRevision:local.state(h).workspaceRevision,roots:[{kind:'byName',modelName:name}]});
    let result=await local.compile(a,req(a,'Root'));assert(result.outcome==='succeeded','multifile compile failed');checks++;
    try{await local.analyze(a,req(a,'Root'));throw new Error('retention limit ignored');}catch(error){assert(error.code==='LIMIT_EXCEEDED','wrong retention error');checks++;}
    await local.releaseSnapshot(a,result.snapshotId);
    const fingerprint=local.state(b).configurationFingerprint,oldHandle=a;
    await local.restart();a=restored.find(p=>p.previous===a).current;b=restored.find(p=>p.previous===b).current;
    assert(local.state(b).configurationFingerprint===fingerprint&&local.sourceText(a,'memory:/root')===root,'restart lost state');checks++;
    try{local.state(oldHandle);throw new Error('old handle accepted');}catch(error){assert(error.code==='SESSION_EXPIRED','wrong old handle error');checks++;}
    result=await local.compile(a,req(a,'Root'));assert(result.outcome==='succeeded','restored compile failed');await local.releaseSnapshot(a,result.snapshotId);checks++;
    assert((await local.compile(b,req(b,'Base'))).outcome==='rejected','configuration not replayed');checks++;
    await local.closeSession(a);await local.closeSession(a);checks++;

  }finally{await local.dispose();}
  const restarted=await restartWorker();assert(restarted.result.outcome==='succeeded','UI restart failed');

  return {status:'ok',rejectedOutcome:rejected.result.outcome,compiledOutcome:succeeded.result.outcome,diagramNodes:nodeCount,diagramEdges:edgeCount,diagramAssociations:associationCount,diagnostics:succeeded.result.diagnostics.length,exactDecimal:true,workerRestartOutcome:restarted.result.outcome,clientContractChecks:checks,transportChecks};
}
source.addEventListener('input',()=>{drafts.set(activeSource,source.value);invalidate();});
picker.onchange=()=>{drafts.set(activeSource,source.value);activeSource=picker.value;source.value=drafts.get(activeSource);};
document.getElementById('add-source').onclick=()=>{const input=document.getElementById('source-name'),id=input.value.trim();if(!id||drafts.has(id)){output.textContent='Enter a unique source URI.';return;}drafts.set(id,'');activeSource=id;source.value='';refreshPicker();invalidate();};
document.getElementById('remove-source').onclick=()=>{if(drafts.size<=1)return;invalidate();drafts.delete(activeSource);activeSource=drafts.keys().next().value;source.value=drafts.get(activeSource);refreshPicker();void serial(synchronize).catch(displayError);};
document.getElementById('configuration').onclick=()=>void serial(async()=>{const configuration=JSON.parse(document.getElementById('configuration-text').value);const handle=await synchronize();await client.setConfiguration(handle,{expectedWorkspaceRevision:client.state(handle).workspaceRevision,configuration});output.textContent='Configuration accepted.';}).catch(displayError);
for(const [id,action]of [['analyze',analyze],['compile',compile],['diagram',diagram],['restart',restartWorker],['cancel',cancel]])document.getElementById(id).onclick=()=>void action().catch(displayError);
window.addEventListener('pagehide',()=>{void client.dispose();});
refreshPicker();
window.interlisBrowserTest={runClientContract,analyze,compile,diagram,benchmark,restartWorker,client,get session(){return session;},navigate};
