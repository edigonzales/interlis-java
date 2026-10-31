import {CompilerClient} from './compiler-client.js';
import {diagramAdapter} from './compiler-diagram.js';

/** Exercises actual Workers, including an interrupted synchronous Wasm request. */
export async function verifyBrowserClient(){
  window.interlisContractProgress="transport: opening";
  let checks=0;const assert=(condition,message)=>{if(!condition)throw new Error(message);checks++;};
  const text='!! 😀\r\nINTERLIS 2.3;MODEL Wire AT "x" VERSION "1"=TOPIC T=CLASS A=value:TEXT;END A;CLASS B=END B;CLASS C=END C;ASSOCIATION Link=a--A;b--B;c--C;note:TEXT;END Link;END T;END Wire.';
  let current,restored,arm=false,interrupted,resolveStarted,port;
  let client;
  const started=new Promise(resolve=>{resolveStarted=resolve;});
  client=new CompilerClient({onRestored:pairs=>{restored=pairs;current=pairs.find(p=>p.previous===current)?.current??current;},workerFactory:()=>{
    const worker=new Worker(new URL('./worker.js',import.meta.url));
    const candidate={onmessage:null,onerror:null,postMessage:value=>worker.postMessage(arm&&value.kind==='request'?{...value,notifyStarted:true}:value),terminate:()=>worker.terminate()};port=candidate;
    worker.onmessage=event=>{
      candidate.onmessage?.(event);
      if(arm&&event.data.kind==='started'&&event.data.requestId==='interrupt-real'){
        arm=false;interrupted=client.cancel('interrupt-real');resolveStarted();
      }
    };
    worker.onerror=event=>candidate.onerror?.(event);return candidate;
  }});
  const req=()=>({expectedWorkspaceRevision:client.state(current).workspaceRevision,roots:[{kind:'byName',modelName:'Wire'}]});
  try{
    current=await client.openSession({sources:[{sourceId:'memory:/wire',text}]});
    window.interlisContractProgress="transport: compiling";
    const initial=await client.compile(current,req());assert(initial.outcome==='succeeded','actual browser n-ary fixture rejected: '+JSON.stringify(initial));
    const doc=await client.diagram(current,{schemaId:initial.schemaId});const sources=new Map([['memory:/wire',{text,revision:client.state(current).sourceRevisions['memory:/wire']}]]);
    const view=diagramAdapter.adapt(doc,sources),link=doc.nodes.find(n=>n.kind==='ASSOCIATION');
    assert(view.associations.get(link.symbol.snapshot+'\0'+link.symbol.symbol).length===3,'n-ary role edges lost');
    const member=link.compartments.flatMap(c=>c.members).find(m=>m.label==='note');assert(diagramAdapter.resolveNavigation(view,member.id,doc.snapshotId,sources).kind==='resolved','member source anchor missing');
    const rendered=await diagramAdapter.render(view);assert(rendered.svg.includes('data-edge-id=')&&rendered.sprotty.children.length>0,'actual SVG/Sprotty missing');
    await client.releaseSnapshot(current,initial.snapshotId);
    // Cancellation observes the actual Worker's started event. The host rejects
    // every old-epoch response and replays acknowledged inputs into a new Worker.
    const old=current;arm=true;
    const result=client.analyze(current,req(),{requestId:'interrupt-real'}).then(value=>({value}),error=>({error}));
    window.interlisContractProgress="transport: cancelling";
    await started;assert(await interrupted==='workerRestarted','active request did not restart worker');
    assert((await result).error?.code==='CANCELLED','active cancellation not distinct');
    assert(current!==old&&restored.length===1&&client.sourceText(current,'memory:/wire')===text,'confirmed source not restored');
    assert((await client.compile(current,req())).outcome==='succeeded','compile after cancellation failed');
    try{await client.diagram(current,{schemaId:initial.schemaId});throw new Error('old schema accepted');}catch(error){assert(error.code==='SNAPSHOT_EXPIRED','old schema not expired');}
    const before=client.state(current),changed=text+'\r\n!! edit';
    await client.updateSource(current,{sourceId:'memory:/wire',expectation:{kind:'at',revision:before.sourceRevisions['memory:/wire']},change:{kind:'replace',text:changed}});
    assert(diagramAdapter.resolveNavigation(view,member.id,view.snapshotId,new Map([['memory:/wire',{text:changed,revision:client.state(current).sourceRevisions['memory:/wire']}]] )).kind==='stale','old member offset navigated into edited source');
    const controller=new AbortController();controller.abort();
    try{await client.analyze(current,req(),{signal:controller.signal});throw new Error('aborted request ran');}catch(error){assert(error.code==='CANCELLED','pre-dispatch cancellation mismatch');}

    const partialText='INTERLIS 2.4;MODEL Partial AT "x" VERSION "1"=IMPORTS Missing;TOPIC T=CLASS C=x:Missing.D;END C;CLASS Good=n:TEXT;END Good;END T;END Partial.';
    const partialSession=await client.openSession({sources:[{sourceId:'memory:/partial',text:partialText}]});
    const partialAnalysis=await client.analyze(partialSession,{expectedWorkspaceRevision:client.state(partialSession).workspaceRevision,roots:[{kind:'byName',modelName:'Partial'}]});
    const partial=await client.diagram(partialSession,{snapshotId:partialAnalysis.snapshotId});
    assert(partial.completeness==='PARTIAL'&&partial.nodes.some(n=>n.status==='UNRESOLVED'&&n.symbol===null),'partial diagram hides its missing import');
    const partialView=diagramAdapter.adapt(partial,new Map([['memory:/partial',{text:partialText,revision:client.state(partialSession).sourceRevisions['memory:/partial']}]]));
    assert((await diagramAdapter.render(partialView)).svg.includes('UNRESOLVED'),'partial stub is invisible in actual SVG');
    const limited=await client.diagram(partialSession,{snapshotId:partialAnalysis.snapshotId,options:{schemaVersion:'1.0',budget:{maxNodes:1}}});
    assert(limited.completeness==='LIMITED'&&limited.nodes.length===1,'wire budget ignored');
    try{await client.diagram(partialSession,{snapshotId:partialAnalysis.snapshotId,selection:[]});throw new Error('empty selection silently defaulted');}catch(error){assert(error.code==='INVALID_REQUEST','empty selection error mismatch');}
    await client.closeSession(partialSession);await client.closeSession(partialSession);assert(client.currentSessions().length===1,'repeated close changed another session');
  }finally{await client.dispose();}
  // A real worker script failure has its own failure kind and may recover all
  // confirmed sessions. No JavaScript stack text is a compiler diagnostic.
  let injectFailure=false,recoveredResolve;const recovered=new Promise(resolve=>{recoveredResolve=resolve;});const failures=[];
  const runtime=new CompilerClient({onFailure:error=>failures.push(error.code),onRestored:recoveredResolve,workerFactory:()=>{
    const worker=new Worker(new URL('./worker.js',import.meta.url));
    const candidate={onmessage:null,onerror:null,terminate:()=>worker.terminate(),postMessage:message=>{
      if(injectFailure&&message.kind==='request'){injectFailure=false;worker.postMessage({...message,request:7});}
      else worker.postMessage(message);
    }};
    worker.onmessage=event=>candidate.onmessage?.(event);worker.onerror=event=>candidate.onerror?.(event);return candidate;
  }});
  try{
    const session=await runtime.openSession({sources:[{sourceId:'memory:/wire',text}]});
    window.interlisContractProgress="transport: runtime failure";
    injectFailure=true;
    const failed=await runtime.analyze(session,{expectedWorkspaceRevision:runtime.state(session).workspaceRevision,roots:[{kind:'byName',modelName:'Wire'}]}).then(value=>({value}),error=>({error}));
    assert(failed.error?.code==='RUNTIME_FAILURE','runtime failure became a model diagnostic');
    window.interlisContractProgress="transport: runtime recovery";
    const pairs=await recovered;assert(failures.includes('RUNTIME_FAILURE')&&pairs.length===1,'runtime failure did not restore the acknowledged session');
    const replacement=pairs[0].current;
    assert((await runtime.compile(replacement,{expectedWorkspaceRevision:runtime.state(replacement).workspaceRevision,roots:[{kind:'byName',modelName:'Wire'}]})).outcome==='succeeded','runtime recovery failed');
    await runtime.dispose();try{await runtime.analyze(session,{expectedWorkspaceRevision:'1',roots:[]});throw new Error('disposed client ran');}catch(error){assert(error.code==='DISPOSED','dispose is not explicit');}
  }finally{await runtime.dispose();}
  return checks;
}
