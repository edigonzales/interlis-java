const {test}=require('node:test'),assert=require('node:assert/strict');
const {CompilerClient}=require('./build/compiler-client.cjs');
const adapter=require('./build/diagram-adapter.cjs');
const api=require('./build/generated/teavm/js/interlis-compiler.js');
const SOURCE='!! Grüezi 😀\r\nINTERLIS 2.3;MODEL F AT "x" VERSION "1"=TOPIC T (ABSTRACT)=CLASS A (ABSTRACT)=n:0..999;END A;CLASS B EXTENDS A=END B;CLASS C=END C;ASSOCIATION Link=one--{1} A;two--{0..*} B;three--{1} C;note:TEXT;END Link;END T;END F.';
class Port{postMessage(m){if(m.kind==='bootstrap'){this.epoch=m.epoch;queueMicrotask(()=>this.onmessage?.({data:{kind:'ready',epoch:m.epoch}}));}else if(m.kind==='request')queueMicrotask(()=>this.onmessage?.({data:{kind:'response',epoch:this.epoch,requestId:m.requestId,response:api.handle(m.request)}}));}terminate(){}}
async function fixture(action){const client=new CompilerClient({workerFactory:()=>new Port()});try{const s=await client.openSession({sources:[{sourceId:'memory:/f',text:SOURCE}]});const result=await client.compile(s,{expectedWorkspaceRevision:client.state(s).workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});assert.equal(result.outcome,'succeeded',JSON.stringify(result));const doc=await client.diagram(s,{schemaId:result.schemaId});return await action(doc,new Map([['memory:/f',{text:SOURCE,revision:client.state(s).sourceRevisions['memory:/f']}]]));}finally{await client.dispose();}}
test('actual compiler diagram retains n-ary attribution, inheritance, symbols, exact cardinalities and immutable values',()=>fixture((doc,sources)=>{
 const view=adapter.adapt(doc,sources),link=doc.nodes.find(n=>n.kind==='ASSOCIATION');assert.ok(link);assert.equal(view.associations.get(link.symbol.snapshot+'\0'+link.symbol.symbol).length,3);
 assert.equal(link.compartments.flatMap(c=>c.members).filter(m=>m.label==='note').length,1);
 assert.ok(doc.nodes.find(n=>n.label==='A').modifiers.includes('ABSTRACT'));
 const derived=doc.nodes.find(n=>n.label==='B'),member=derived.compartments.flatMap(c=>c.members).find(m=>m.label==='n');assert.ok(member.inherited);assert.equal(member.effectiveOwner,derived.symbol.symbol);
 const ids=doc.nodes.flatMap(n=>n.compartments.flatMap(c=>c.members.map(m=>m.id)));assert.equal(new Set(ids).size,ids.length);
 assert.ok(adapter.elementsForSymbol(view,{snapshot:doc.snapshotId,symbol:member.declaredSymbol}).includes(member.id));
 assert.ok(Object.isFrozen(view.document.nodes));assert.equal(view.associations.set,undefined);
 const mutable=JSON.parse(JSON.stringify(doc)),detached=adapter.adapt(mutable,sources);mutable.nodes[0].label='corrupt';assert.notEqual(detached.document.nodes[0].label,'corrupt');
 const legacy=adapter.legacyProjection(view);assert.ok(legacy.edges.some(e=>e.targetCardinality==='0..*'));
 const exact=JSON.parse(JSON.stringify(doc));exact.edges[0].targetEnd.cardinality={minimum:'9007199254740993',maximum:{kind:'bounded',value:'9007199254740994'}};
 assert.equal(adapter.legacyProjection(adapter.adapt(exact,sources)).edges[0].targetCardinality,'9007199254740993..9007199254740994');
}));
test('source navigation uses UTF-16 coordinates and derived UTF-8 offsets, and rejects changed text/revisions/snapshots',()=>fixture((doc,sources)=>{
 const view=adapter.adapt(doc,sources),node=doc.nodes.find(n=>n.label==='A'),result=adapter.resolveNavigation(view,node.id,doc.snapshotId,sources);
 assert.equal(result.kind,'resolved');assert.equal(SOURCE.slice(result.range.startOffset,result.range.endOffset),'A');
 assert.equal(result.editorRange.start.line,1);assert.equal(result.editorRange.start.character,result.range.startOffset-SOURCE.indexOf('INTERLIS'));
 assert.equal(result.editorRange.start.byteOffset,Buffer.byteLength(SOURCE.slice(0,result.range.startOffset),'utf8'));
 assert.equal(adapter.resolveNavigation(view,node.id,'other',sources).kind,'stale');
 assert.equal(adapter.resolveNavigation(view,node.id,doc.snapshotId,new Map([['memory:/f',{revision:'1',text:SOURCE+'!'}]])).kind,'stale');
 assert.equal(adapter.resolveNavigation(view,node.id,doc.snapshotId,new Map([['memory:/f',{revision:'2',text:SOURCE}]])).kind,'stale');
}));
test('schema and semantic shape checks reject malformed actual diagrams rather than silently dropping data',()=>fixture((doc,sources)=>{
 const fails=change=>{const bad=JSON.parse(JSON.stringify(doc));change(bad);assert.throws(()=>adapter.adapt(bad,sources));};
 fails(d=>d.schemaVersion='2.0');fails(d=>d.nodes[0].anchor.nameRange.source='missing');fails(d=>d.nodes[0].symbol.snapshot='other');
 fails(d=>d.nodes[0].containerId='missing');fails(d=>d.nodes.push(d.nodes[0]));fails(d=>d.edges[0].target.elementId='missing');fails(d=>d.containers[0].parentId=d.containers[0].id);
 fails(d=>d.nodes[0].anchor.nameRange.startOffset=SOURCE.indexOf('😀')+1);
}));
test('pinned upstream actually produces layout, SVG, and Sprotty from the compiler projection',()=>fixture(async(doc,sources)=>{
 const view=adapter.adapt(doc,sources),rendered=await adapter.render(view,{...adapter.diagramAdapter.defaultDiagramSettings,attributeMode:'OWN_AND_INHERITED'});
 const again=await adapter.render(view,{...adapter.diagramAdapter.defaultDiagramSettings,attributeMode:'OWN_AND_INHERITED'},rendered);assert.deepEqual(again.layout.nodes.map(n=>[n.id,n.x,n.y]),rendered.layout.nodes.map(n=>[n.id,n.x,n.y]));assert.ok(Object.isFrozen(again.layout.nodes));
 assert.equal(rendered.snapshotId,doc.snapshotId);assert.ok(rendered.svg.includes('<svg'));assert.ok(rendered.svg.includes('data-symbol-id='));assert.ok(rendered.layout.nodes.length>=doc.nodes.length);assert.ok(rendered.sprotty.children.length);
 const mutable=JSON.parse(JSON.stringify(doc));mutable.nodes.find(n=>n.label==='C').label='<script>alert(1)</script>';const escaped=await adapter.render(adapter.adapt(mutable,sources));assert.ok(!escaped.svg.includes('<script>'));assert.ok(escaped.svg.includes('&lt;script>'));
}));
