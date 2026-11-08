const {test}=require('node:test'),assert=require('node:assert/strict');
const {CompilerClient}=require('./build/compiler-client.cjs');
const api=require('./build/generated/teavm/js/interlis-compiler.js');
const SOURCE='!! Grüezi 😀\r\nINTERLIS 2.3;MODEL F AT "x" VERSION "1"=DOMAIN State=(first,group(child));TOPIC T (ABSTRACT)=CLASS A (ABSTRACT)=n:0..999;END A;CLASS B EXTENDS A=END B;CLASS C=END C;ASSOCIATION Link=one--{1} A;two--{0..*} B;three--{1} C;note:TEXT;END Link;ASSOCIATION Self=parent--{0..1}A;children--{*}A;END Self;ASSOCIATION Owns=owner-<#>{1}A;items--{*}B;END Owns;ASSOCIATION Parallel=fromA--{1}A;toB--{*}B;END Parallel;END T;END F.';
class Port{postMessage(m){if(m.kind==='bootstrap'){this.epoch=m.epoch;queueMicrotask(()=>this.onmessage?.({data:{kind:'ready',epoch:m.epoch}}));}else if(m.kind==='request')queueMicrotask(()=>this.onmessage?.({data:{kind:'response',epoch:this.epoch,requestId:m.requestId,response:api.handle(m.request)}}));}terminate(){}}
async function fixture(action,options){const client=new CompilerClient({workerFactory:()=>new Port()});try{const s=await client.openSession({sources:[{sourceId:'memory:/f',text:SOURCE}]});const result=await client.compile(s,{expectedWorkspaceRevision:client.state(s).workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});assert.equal(result.outcome,'succeeded',JSON.stringify(result));const doc=await client.diagram(s,{schemaId:result.schemaId,...(options?{options}:{})});return await action(doc,new Map([['memory:/f',{text:SOURCE,revision:client.state(s).sourceRevisions['memory:/f']}]]));}finally{await client.dispose();}}
test('portable diagram preserves members, bound roles, exact cardinalities and anchors',()=>fixture(doc=>{
 const values=doc.nodes.find(node=>node.label==='State').compartments.find(c=>c.kind==='ENUM_VALUES').members;
 assert.deepEqual(values.map(member=>member.label),['first','group','group.child']);
 for(const value of values){
  assert.equal(value.kind,'ENUM_VALUE');
  assert.equal(SOURCE.slice(value.anchor.nameRange.startOffset,value.anchor.nameRange.endOffset),value.label.split('.').at(-1));
 }
 const link=doc.nodes.find(node=>node.label==='Link');
 const roles=doc.edges.filter(edge=>edge.kind==='ASSOCIATION_ROLE'&&edge.source.elementId===link.id);
 assert.deepEqual(roles.map(edge=>[edge.targetEnd.roleName,edge.targetEnd.cardinality]),[
  ['one',{minimum:'1',maximum:{kind:'bounded',value:'1'}}],
  ['two',{minimum:'0',maximum:{kind:'unbounded'}}],
  ['three',{minimum:'1',maximum:{kind:'bounded',value:'1'}}]
 ]);
 assert.equal(link.compartments.flatMap(c=>c.members).filter(m=>m.label==='note').length,1);
 const base=doc.nodes.find(node=>node.label==='A'),derived=doc.nodes.find(node=>node.label==='B');
 assert.ok(base.modifiers.includes('ABSTRACT'));
 const member=derived.compartments.flatMap(c=>c.members).find(m=>m.label==='n');
 assert.ok(member.inherited);
 assert.equal(member.effectiveOwner,derived.symbol.symbol);
 assert.ok(doc.edges.some(edge=>edge.kind==='INHERITANCE'&&edge.source.elementId===derived.id&&edge.target.elementId===base.id));
 assert.equal(base.anchor.snapshotId,doc.snapshotId);
 const range=base.anchor.nameRange;
 assert.equal(range.revision,doc.sourceRevisions[range.source]);
 assert.equal(range.startOffset,SOURCE.indexOf('CLASS A')+'CLASS '.length);
 assert.equal(SOURCE.slice(range.startOffset,range.endOffset),'A');
}));
test('portable compact associations preserve self and parallel endpoints, role ends, origin and n-ary data',()=>fixture(doc=>{
 const binary=doc.edges.filter(edge=>edge.kind==='ASSOCIATION');
 assert.equal(binary.length,3);
 const self=binary.find(edge=>edge.label==='Self');
 assert.deepEqual(self.source,self.target);
 assert.equal(self.sourceEnd.roleName,'parent');
 assert.equal(self.targetEnd.roleName,'children');
 assert.deepEqual(self.sourceEnd.cardinality,{minimum:'0',maximum:{kind:'bounded',value:'1'}});
 assert.deepEqual(self.targetEnd.cardinality,{minimum:'0',maximum:{kind:'unbounded'}});
 assert.equal(self.origin.snapshot,doc.snapshotId);
 const range=self.anchor.nameRange;
 assert.equal(SOURCE.slice(range.startOffset,range.endOffset),'Self');
 const owns=binary.find(edge=>edge.label==='Owns'),parallel=binary.find(edge=>edge.label==='Parallel');
 assert.deepEqual(owns.source,parallel.source);
 assert.deepEqual(owns.target,parallel.target);
 assert.equal(owns.sourceEnd.aggregation,'COMPOSITION');
 assert.equal(owns.targetEnd.aggregation,'NONE');
 assert.ok(doc.nodes.some(node=>node.label==='Link'&&node.compartments.some(c=>c.members.some(m=>m.label==='note'))));
},{schemaVersion:'1.0',associationPresentation:'COMPACT_BINARY'}));
test('actual snapshot identity changes preserve only exact current declarations',async()=>{
 const client=new CompilerClient({workerFactory:()=>new Port()});
 const initial='INTERLIS 2.4;MODEL LayoutKeyCase AT "x" VERSION "1"=TOPIC T=CLASS BeforeNode=END BeforeNode;CLASS UnchangedNode=END UnchangedNode;END T;END LayoutKeyCase.';
 const changed=initial.replace('CLASS BeforeNode=END BeforeNode;','CLASS ReplacementNode=END ReplacementNode;');
 try{const session=await client.openSession({sources:[{sourceId:'memory:/identity.ili',text:initial}]});
 const project=async(target=session)=>{const result=await client.compile(target,{expectedWorkspaceRevision:client.state(target).workspaceRevision,roots:[{kind:'byName',modelName:'LayoutKeyCase'}]});assert.equal(result.outcome,'succeeded');return client.diagram(target,{schemaId:result.schemaId});};
 const before=await project(),first=before.nodes.find(node=>node.label==='BeforeNode'),second=before.nodes.find(node=>node.label==='UnchangedNode');
 await client.updateSource(session,{sourceId:'memory:/identity.ili',expectation:{kind:'at',revision:client.state(session).sourceRevisions['memory:/identity.ili']},change:{kind:'replace',text:changed}});
 const after=await project(),replacement=after.nodes.find(node=>node.label==='ReplacementNode'),currentSecond=after.nodes.find(node=>node.label==='UnchangedNode');assert.notEqual(before.snapshotId,after.snapshotId);assert.notEqual(first.id,replacement.id,'Renamed declaration must receive its own identity');
 assert.equal(currentSecond.anchor.nameRange.revision,after.sourceRevisions['memory:/identity.ili']);
 const fresh=await client.openSession({sources:[{sourceId:'memory:/identity.ili',text:changed}]}),freshDocument=await project(fresh),freshSecond=freshDocument.nodes.find(node=>node.label==='UnchangedNode');
 assert.notEqual(freshSecond.id,second.id,'Fresh compilation fixture must exercise different opaque IDs');
 assert.notEqual(freshDocument.snapshotId,before.snapshotId);
 }finally{await client.dispose();}
});
