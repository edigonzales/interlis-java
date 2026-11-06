'use strict';

async function viewFoundations({call, verify, verifySources}) {
  let viewFoundationCases=0;
  for(const version of ['2.3','2.4']) {
    const wrap=body=>`INTERLIS ${version};CONTRACTED MODEL F AT "https://example.org" VERSION "1"=${body}END F.`;
    for(const area of [true,false]) for(const middle of [false,true]) {
      const text=wrap('DOMAIN P=COORD 0.00..100.00,0.00..100.00;TOPIC T=CLASS A=shape:'+(area?'AREA':'SURFACE')+' WITH(STRAIGHTS) VERTEX P WITHOUT OVERLAPS>0.01;END A;'
        +'VIEW V '+(area?'AREA ':'')+'INSPECTION OF edge~A->shape;=END V;'+(middle?'VIEW W EXTENDS V=END W;':'')
        +'VIEW Z EXTENDS '+(middle?'W':'V')+'=ALL OF edge;END Z;END T;');
      await verify(text,area?'rejected':'succeeded');viewFoundationCases++;
    }
    await verify(wrap('TOPIC T=CLASS A=code:TEXT;END A;CLASS B=code:TEXT;END B;VIEW V UNION OF a~A,b~B;=END V;VIEW W EXTENDS V=ALL OF a;END W;END T;'),'rejected');viewFoundationCases++;
    const text=wrap('TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=name:TEXT;END B;ASSOCIATION Link=a--{1}A;b--{*}B;END Link;'
      +'VIEW V PROJECTION OF rel~Link;=ALL OF rel;END V;VIEW W PROJECTION OF src~V;=name:=src->b->name;END W;END T;');
    await verifySources([{sourceId:'memory:/stabilization.ili',text}],version==='2.4'?'succeeded':'rejected',['F'],async(sessionId,compiled)=>{
      if(version==='2.3')return; // ALL OF does not publish the role in this version.
      const query=(offset,kind)=>call('query',sessionId,{snapshotId:compiled.snapshotId,query:{kind,sourceId:'memory:/stabilization.ili',revision:'1',startOffset:offset,endOffset:offset}});
      const declaration=await query(text.indexOf('name:TEXT'),'symbolAt');
      const reference=await query(text.indexOf('->b->name')+5,'definition');
      if(declaration.kind!=='known'||reference.kind!=='definition'||declaration.symbolId!==reference.symbolId)
        throw new Error('Copied view participant path lost its actual target '+JSON.stringify({declaration,reference}));
    });viewFoundationCases++;
  }
  // L2-1: approved profiles and a SET condition without individual object values.
  return viewFoundationCases;
}

async function referenceViewCases({verifySources}) {
  const cases = require('../cases/reference-views.json');
  for (const entry of cases) await verifySources(entry.sources, entry.expected, entry.roots);
  return cases.length;
}

async function originalViewModels({verifySources}) {
  const originalViewFixtures = require('../cases/original-views.json');
  for (const fixture of originalViewFixtures) {
    await verifySources([{sourceId: `memory:/b1-b3/${fixture.testId}.ili`, text: fixture.text}], 'succeeded', fixture.roots);
  }
  return originalViewFixtures.length;
}

module.exports = {
  'view-foundations': viewFoundations,
  'reference-view-cases': referenceViewCases,
  'original-view-models': originalViewModels,
};
