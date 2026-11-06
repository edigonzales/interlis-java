'use strict';

async function qualifiedClosureAndScope({call, verify, verifySources}) {
  let a3CompleteCases=0;
  for(const version of ['2.3','2.4']) {
    const text=`INTERLIS ${version};
MODEL F AT "https://example.org" VERSION "1" =
  STRUCTURE Base = first: TEXT*20; second: BOOLEAN; END Base;
  STRUCTURE Child EXTENDS Base = third: 0..10; END Child;
  TOPIC T = OID AS INTERLIS.UUIDOID;
    CLASS A = label: TEXT; END A;
    CLASS B = label: TEXT; END B;
    ASSOCIATION Link = a -- {1} A; b -- {0..*} B; END Link;
  END T;
  TOPIC U = OID AS INTERLIS.UUIDOID;
    CLASS A = label: TEXT; END A;
    CLASS B = label: TEXT; END B;
    ASSOCIATION Link = a -- {1} A; b -- {0..*} B; END Link;
  END U;
END F.
`;
    await verifySources([{sourceId:'memory:/a3-complete.ili',text}],'succeeded',['F'],async(sessionId,compiled)=>{
      for(const name of ['F.Child','F.T.Link','F.U.Link']) {
        const simple=name.slice(name.lastIndexOf('.')+1);let start=name==='F.Child'?0:text.indexOf(name.includes('.U.')?'TOPIC U':'TOPIC T');
        start=text.indexOf(simple+' ',start);
        const p=await call('query',sessionId,{snapshotId:compiled.snapshotId,query:{kind:'semanticProperties',sourceId:'memory:/a3-complete.ili',revision:'1',startOffset:start,endOffset:start}});
        const expected=name==='F.Child'?['first','second','third']:['a','b'];
        if(p.qualifiedName!==name||JSON.stringify(p.effectiveMembers.map(m=>m.name))!==JSON.stringify(expected))throw new Error('A3 ordered effective members '+JSON.stringify(p));
        for(let i=0;i<p.effectiveMembers.length;i++) {
          const member=p.effectiveMembers[i];if(member.position!==i)throw new Error('A3 noncanonical position');
          if(name!=='F.Child'&&JSON.stringify(member.targets)!==JSON.stringify([name.slice(0,-4)+(i===0?'A':'B')]))throw new Error('A3 wrong same-named full target '+JSON.stringify(member));
        }
      }
    });a3CompleteCases++;
    await verify(text.replace('third: 0..10','third: 10..0'),'rejected');a3CompleteCases++;
  }
  // L3-2: the portable workspace uses the same exact selection and priority policy.
  return a3CompleteCases;
}

async function resolutionAndLexicalPolicy({call, verifySources}) {
  let resolutionPolicyCases=0;
  for(const version of ['2.3','2.4']) {
    const model=(name,v,body)=>`INTERLIS ${version};MODEL ${name} AT "https://example.org" VERSION "${v}"=${body}END ${name}.`;
    const rootId='memory:/resolve-root.ili',oneId='memory:/dep-one.ili',twoId='memory:/dep-two.ili';
    const root=model('F','1','IMPORTS Dep;DOMAIN P EXTENDS Dep.P=TEXT*7;');
    const sources=[{sourceId:rootId,text:root},{sourceId:oneId,text:model('Dep','1','IMPORTS Leaf;DOMAIN P=TEXT*7;')},
      {sourceId:twoId,text:model('Dep','2','IMPORTS Missing;DOMAIN P=TEXT*99;')},
      {sourceId:'memory:/leaf.ili',text:model('Leaf','1','DOMAIN N=0..10;')}];
    await verifySources(sources,'rejected',['F'],async(sessionId)=>{
      const compile=(revision,name='F')=>call('compile',sessionId,{expectedWorkspaceRevision:revision,roots:[{kind:'byName',modelName:name}]});
      const query=snapshotId=>call('query',sessionId,{snapshotId,query:{kind:'definition',sourceId:rootId,revision:'1',startOffset:root.indexOf('Dep.P')+4,endOffset:root.indexOf('Dep.P')+4}});
      let changed=await call('setConfiguration',sessionId,{expectedWorkspaceRevision:'4',resolutionPolicy:{
        modelSelections:[{modelName:'Dep',modelVersion:'1',sourceHint:oneId}],sourcePriorities:[{sourceId:oneId,priority:2},{sourceId:twoId,priority:0}]}});
      const first=await compile(changed.workspaceRevision);
      if(first.outcome!=='succeeded')throw new Error('Exact version/source did not override priority '+JSON.stringify(first));
      const definition=await query(first.snapshotId);
      if(definition.kind!=='definition'||definition.nameRange.source!==oneId)throw new Error('Wrong bound dependency '+JSON.stringify(definition));
      const selectedRoot=await compile(changed.workspaceRevision,'Dep');
      if(selectedRoot.outcome!=='succeeded')throw new Error('Root policy was ignored '+JSON.stringify(selectedRoot));
      changed=await call('setConfiguration',sessionId,{expectedWorkspaceRevision:changed.workspaceRevision,resolutionPolicy:{modelSelections:[{modelName:'Dep',modelVersion:'2',sourceHint:twoId}]}});
      const missing=await compile(changed.workspaceRevision);
      if(missing.outcome!=='rejected'||!missing.diagnostics.some(d=>d.code==='ILI-RES-0001'))throw new Error('Selected transitive missing dependency disappeared '+JSON.stringify(missing));
      if((await query(first.snapshotId)).nameRange.source!==oneId)throw new Error('Old dependency snapshot mutated');
      changed=await call('setConfiguration',sessionId,{expectedWorkspaceRevision:changed.workspaceRevision,resolutionPolicy:{sourcePriorities:[{sourceId:oneId,priority:0},{sourceId:twoId,priority:1}]}});
      const ranked=await compile(changed.workspaceRevision);
      if(ranked.outcome!=='succeeded'||(await query(ranked.snapshotId)).nameRange.source!==oneId)throw new Error('Priority selection failed');
      changed=await call('setConfiguration',sessionId,{expectedWorkspaceRevision:changed.workspaceRevision});
      const clear=await compile(changed.workspaceRevision);
      if(clear.outcome!=='rejected'||!clear.diagnostics.some(d=>d.code==='ILI-RES-0002'))throw new Error('Cleared policy retained a selection');
    });resolutionPolicyCases++;
    await verifySources([{sourceId:oneId,text:model('Dep','1','DOMAIN P=TEXT;')},{sourceId:twoId,text:model('Dep','1','DOMAIN P=TEXT;')}],'rejected',['Dep'],async(sessionId)=>{
      let changed=await call('setConfiguration',sessionId,{expectedWorkspaceRevision:'2',resolutionPolicy:{modelSelections:[{modelName:'Dep',sourceHint:oneId}]}});
      let result=await call('compile',sessionId,{expectedWorkspaceRevision:changed.workspaceRevision,roots:[{kind:'byName',modelName:'Dep'}]});
      if(result.outcome!=='succeeded')throw new Error('Same-version root source hint failed '+JSON.stringify(result));
      changed=await call('setConfiguration',sessionId,{expectedWorkspaceRevision:changed.workspaceRevision,resolutionPolicy:{modelSelections:[{modelName:'Dep',modelVersion:'absent',sourceHint:oneId}]}});
      result=await call('compile',sessionId,{expectedWorkspaceRevision:changed.workspaceRevision,roots:[{kind:'byName',modelName:'Dep'}]});
      if(result.outcome!=='rejected')throw new Error('Missing pinned version silently fell back');
    });resolutionPolicyCases++;
  }
  // Exact original selection must reach the shared translation checker, not stop at discovery.
  for(const version of ['2.3','2.4'])for(const external of [false,true]) {
    const wrap=(name,relation,body)=>`INTERLIS ${version};MODEL ${name} AT "https://example.org" VERSION "1" ${relation}=${body}END ${name}.`;
    const chosenId='memory:/chosen-original.ili',otherId='memory:/other-original.ili',rootId='memory:/resolved-translation.ili';
    const original=wrap('Original','','DOMAIN D=TEXT;');
    const root=wrap('F',external?'':'TRANSLATION OF Original["1"]','DOMAIN D=TEXT;');
    await verifySources([{sourceId:chosenId,text:original},{sourceId:otherId,text:wrap('Original','','DOMAIN D=TEXT*7;')},{sourceId:rootId,text:root}],external?'succeeded':'rejected',['F'],async sessionId=>{
      const changed=await call('setConfiguration',sessionId,{expectedWorkspaceRevision:'3',resolutionPolicy:{modelSelections:[{modelName:'Original',modelVersion:'1',sourceHint:chosenId}]},
        overlays:external?[{kind:'translation',id:'selected',priority:1,target:'F',original:'Original'}]:[]});
      const result=await call('compile',sessionId,{expectedWorkspaceRevision:changed.workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});
      if(result.outcome!=='succeeded')throw new Error('Selected original lost after resolution '+JSON.stringify(result));
      const query=(kind,sourceId,text,name)=>call('query',sessionId,{snapshotId:result.snapshotId,query:{kind,sourceId,revision:'1',startOffset:text.indexOf('MODEL '+name)+6,endOffset:text.indexOf('MODEL '+name)+6}});
      const chosen=await query('symbolAt',chosenId,original,'Original');
      const properties=await query('semanticProperties',rootId,root,'F');
      if(chosen.kind!=='known'||properties.translation?.sourceTarget!==chosen.symbolId||properties.translation.correspondences.length<2)
        throw new Error('Wrong public original identity '+JSON.stringify({chosen,properties}));
      if(external&&properties.translation.origin?.overlayId!=='selected')throw new Error('Original overlay provenance lost');
    });resolutionPolicyCases++;
  }
  // L3-1: actual overlay data, revision isolation and transitive originals.
  return resolutionPolicyCases;
}

async function transitiveSemanticClosure({call, verify, verifySources}) {
  let phase2ClosureCases=0;
  for(const version of ['2.3','2.4']) {
    const model=(name,original,body)=>`CONTRACTED MODEL ${name} AT "https://example.org" VERSION "1" ${original?'TRANSLATION OF '+original+'["1"] ':''}=${body}END ${name}.`;
    const wrap=body=>`INTERLIS ${version};`+model('F',null,body);
    for(const body of ['TOPIC T=CLASS C=p:INTERLIS.LineCoord;END C;END T;',
      'TOPIC T(ABSTRACT)=CLASS C(ABSTRACT)=p:INTERLIS.LineCoord;END C;END T;',
      'STRUCTURE S EXTENDS INTERLIS.LineSegment=p:INTERLIS.LineCoord;END S;LINE FORM Curve:S;']) {
      await verify(wrap(body),'rejected');phase2ClosureCases++;
    }
    await verify(wrap('PARAMETER runtimePoint:INTERLIS.LineCoord;STRUCTURE S(ABSTRACT) EXTENDS INTERLIS.LineSegment=p(ABSTRACT):INTERLIS.LineCoord;END S;LINE FORM Curve:S;DOMAIN P=COORD 0.00..10.00,0.00..10.00;TOPIC T=CLASS A=line:POLYLINE WITH(Curve) VERTEX P;END A;VIEW V INSPECTION OF seg~A->line;=point:=seg->Segments[FIRST]->SegmentEndPoint;END V;END T;'),'succeeded');phase2ClosureCases++;
    const inherited=wrap('TOPIC Base=OID AS INTERLIS.UUIDOID;CLASS A=flag:BOOLEAN;END A;CLASS B=name:TEXT;END B;ASSOCIATION Link=a--{1}A;b--{*}B;label:TEXT;END Link;END Base;TOPIC Local EXTENDS Base=CLASS A(EXTENDED)=local:TEXT;END A;VIEW V PROJECTION OF src~Link;=ALL OF src;END V;END Local;');
    await verifySources([{sourceId:'memory:/stabilization.ili',text:inherited}],'succeeded',['F'],async(sessionId,compiled)=>{
      const query=(offset,kind)=>call('query',sessionId,{snapshotId:compiled.snapshotId,query:{kind,sourceId:'memory:/stabilization.ili',revision:'1',startOffset:offset,endOffset:offset}});
      const declaration=await query(inherited.indexOf('Link=a'),'symbolAt');
      const reference=await query(inherited.indexOf('src~Link')+4,'definition');
      if(declaration.kind!=='known'||reference.kind!=='definition'||declaration.symbolId!==reference.symbolId)throw new Error('Inherited association basis lost its actual declaration '+JSON.stringify({declaration,reference}));
    });phase2ClosureCases++;
    const shared='TOPIC Systems=CLASS CS EXTENDS INTERLIS.COORDSYSTEM=END CS;END Systems;REFSYSTEM BASKET Library~Systems OBJECTS OF CS:World,Other;STRUCTURE Segment EXTENDS INTERLIS.LineSegment=tag:TEXT;END Segment;LINE FORM Curve:Segment;DOMAIN P=COORD 0.00..10.00{Library.World[1]},0.00..10.00{Library.World[2]};Q=COORD 0.00..10.00{Library.World[1]},0.00..10.00{Library.World[2]};'+(version==='2.4'?'G(GENERIC)=COORD NUMERIC,NUMERIC;CONTEXT ctx=G=P;':'');
    const base='IMPORTS Original;DOMAIN Line=POLYLINE WITH(Original.Curve) VERTEX Original.P;TOPIC Data=CLASS C=position:Original.'+(version==='2.4'?'G':'P')+';line:Line;END C;END Data;';
    const use=base.replaceAll('Original.','Translated.').replace('IMPORTS Original;','IMPORTS Translated;');
    const prefix=`INTERLIS ${version};`+model('Original',null,shared)+model('Translated','Original',shared)+model('Base',null,base);
    const good=prefix+model('F','Base',use);
    await verifySources([{sourceId:'memory:/stabilization.ili',text:good}],'succeeded',['F'],async(sessionId,compiled)=>{
      const query=(offset,kind)=>call('query',sessionId,{snapshotId:compiled.snapshotId,query:{kind,sourceId:'memory:/stabilization.ili',revision:'1',startOffset:offset,endOffset:offset}});
      const declaration=await query(good.indexOf('P=COORD',good.indexOf('MODEL Translated')),'symbolAt');
      const reference=await query(good.lastIndexOf('VERTEX Translated.P')+18,'definition');
      if(declaration.kind!=='known'||reference.kind!=='definition'||declaration.symbolId!==reference.symbolId)throw new Error('Translated geometric vertex lost its bound import identity '+JSON.stringify({declaration,reference}));
    });phase2ClosureCases++;
    await verify(prefix+model('F','Base',use.replace('VERTEX Translated.P','VERTEX Translated.Q')),'rejected');phase2ClosureCases++;
  }
  for(const wrong of [false,true]) {
    const shared='DOMAIN G(GENERIC)=COORD NUMERIC,NUMERIC;A=COORD 0..10,0..10;B=COORD 20..30,20..30;CONTEXT ctx=G=A OR B;';
    const model=(name,body)=>`MODEL ${name} AT "https://example.org" VERSION "1"=${body}END ${name}.`;
    await verify('INTERLIS 2.4;'+model('West',shared)+model('East',shared)+model('F','IMPORTS West,East;TOPIC T=DEFERRED GENERICS West.G,East.G;CLASS C=p:'+(wrong?'East':'West')+'.G;q:East.G;END C;END T;'),wrong?'rejected':'succeeded');phase2ClosureCases++;
  }
  // L2-2: inherited formation restrictions and actual copied participant paths.
  return phase2ClosureCases;
}

async function pathAndObjectScopes({verify}) {
  const paths = [
    ['value > 0', 'succeeded'],
    ['THIS', 'rejected'],
    ['DEFINED(THIS)', 'succeeded'],
    ['value->value > 0', 'rejected'],
    ['DEFINED(value->THIS)', 'rejected'],
    ['\\value > 0', 'rejected'],
    ['value[FIRST] > 0', 'rejected']
  ];
  for (const version of ['2.3', '2.4']) {
    for (const [expression, expected] of paths) {
      await verify(`INTERLIS ${version}; MODEL F AT "x" VERSION "1" =
TOPIC T = CLASS C = value: 0..10;
MANDATORY CONSTRAINT ${expression}; END C; END T; END F.`, expected);
    }
  }
  const objects = [
    ['A', 'B', 'f(THIS)', 'succeeded'],
    ['F.T.A', 'B', 'f(THIS->THIS)', 'succeeded'],
    ['ANYCLASS', 'B', 'f(THIS)', 'succeeded'],
    ['A RESTRICTION (B)', 'B', 'f(THIS)', 'succeeded'],
    ['ANYCLASS RESTRICTION (B)', 'B', 'f(THIS)', 'succeeded'],
    ['A', 'B', 'f(identity(THIS))', 'succeeded'],
    ['A', 'B', 'identity(THIS) == THIS', 'succeeded'],
    ['A', 'B', 'identity(THIS) < THIS', 'rejected'],
    ['A', 'B', 'f(1)', 'rejected'],
    ['A', 'B', 'f(ALL)', 'rejected'],
    ['Other', 'B', 'f(THIS)', 'rejected'],
    ['A RESTRICTION (B)', 'A', 'f(THIS)', 'rejected'],
    ['A RESTRICTION (Other)', 'A', 'f(THIS)', 'rejected'],
    ['Wrong.A', 'A', 'f(THIS)', 'rejected']
  ];
  for (const version of ['2.3', '2.4']) {
    for (const [type, target, expression, expected] of objects) {
      await verify(`INTERLIS ${version}; CONTRACTED MODEL F AT "x" VERSION "1" =
TOPIC T = CLASS A = value: 0..10; END A; CLASS B EXTENDS A = END B;
CLASS Other = END Other;
FUNCTION f(v: OBJECT OF ${type}): BOOLEAN;
FUNCTION identity(v: OBJECT OF A): OBJECT OF A;
CONSTRAINTS OF ${target} = MANDATORY CONSTRAINT ${expression}; END; END T; END F.`, expected);
    }
    for (const cardinality of ['1', '*']) {
      await verify(`INTERLIS ${version}; CONTRACTED MODEL F AT "x" VERSION "1" =
TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = END A; FUNCTION f(v: OBJECT OF A): BOOLEAN;
ASSOCIATION Link = a -- {${cardinality}} A; b -- {1} A;
MANDATORY CONSTRAINT f(a); END Link; END T; END F.`, 'succeeded');
    }
  }
  const roleSets = [
    ['1', 'single(a)', 'succeeded'], ['*', 'single(a)', 'rejected'],
    ['0..1', 'many(a)', 'succeeded'], ['*', 'many(a)', 'succeeded'],
    ['*', 'INTERLIS.objectCount(a) >= 0', 'succeeded'],
    ['1', '\\a->flag', 'succeeded'], ['*', '\\a->flag', 'rejected'],
    ['1', 'a->flag', 'succeeded'], ['*', 'single(a->THIS)', 'rejected'],
    ['1', 'single(a->THIS)', 'succeeded'], ['*', 'a == a', 'rejected'],
    ['1', 'single(\\a)', 'rejected'], ['1', 'INTERLIS.objectCount(1) > 0', 'rejected'],
    ['1', 'many(ALL(A))', 'succeeded'], ['1', 'many(ALL(B))', 'succeeded'],
    ['1', 'many(result(ALL(B)))', 'succeeded'],
    ['1', 'restricted(ALL(A RESTRICTION (B)))', 'succeeded'],
    ['1', 'INTERLIS.objectCount(ALL) > 0', 'succeeded'],
    ['1', 'many(ALL)', 'rejected'], ['1', 'many(ALL(Other))', 'rejected'],
    ['1', 'many(ALL(ANYCLASS))', 'rejected'], ['1', 'many(ALL(Wrong.A))', 'rejected'],
    ['1', 'restricted(ALL(A))', 'rejected'], ['1', 'single(ALL(A))', 'rejected'],
    ['1', 'result(ALL(A)) == result(ALL(A))', 'rejected'],
    ['1', 'many(ALL(A RESTRICTION (Other)))', 'rejected']
  ];
  for (const version of ['2.3', '2.4']) {
    for (const [cardinality, expression, expected] of roleSets) {
      await verify(`INTERLIS ${version}; CONTRACTED MODEL F AT "x" VERSION "1" =
TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = flag: BOOLEAN; END A; CLASS B EXTENDS A = END B;
CLASS Other = END Other; CLASS Owner = END Owner;
FUNCTION single(v: OBJECT OF A): BOOLEAN; FUNCTION many(v: OBJECTS OF A): BOOLEAN;
FUNCTION restricted(v: OBJECTS OF A RESTRICTION (B)): BOOLEAN;
FUNCTION result(v: OBJECTS OF A): OBJECTS OF A;
ASSOCIATION Link = a -- {${cardinality}} A; owner -- {1} Owner; flag: BOOLEAN; END Link;
CONSTRAINTS OF Owner = MANDATORY CONSTRAINT ${expression}; END; END T; END F.`, expected);
    }
  }
  const scopes = [
    ['EXTENDS Base', 'A', 'succeeded'],
    ['', 'A', 'rejected'],
    ['', 'Base.A', 'rejected'],
    ['EXTENDS Base', 'Missing', 'rejected']
  ];
  await verify(`INTERLIS 2.4; MODEL F AT "x" VERSION "1" =
TOPIC T = OID AS INTERLIS.UUIDOID; CLASS B = END B; FUNCTION f(v: OBJECT OF B): BOOLEAN;
CLASS A = link: REFERENCE TO B; MANDATORY CONSTRAINT f(link->THIS);
END A; END T; END F.`, 'succeeded');
  for (const version of ['2.3', '2.4']) {
    for (const [inheritance, target, expected] of scopes) {
      await verify(`INTERLIS ${version}; MODEL F AT "x" VERSION "1" =
TOPIC Base = CLASS A = value: 0..10; END A; END Base;
TOPIC T ${inheritance} = CONSTRAINTS OF ${target} =
MANDATORY CONSTRAINT #true; END; END T; END F.`, expected);
    }
  }
  return (paths.length + scopes.length + objects.length + roleSets.length + 2) * 2 + 1;
}

async function roleInheritance({verify}) {
  let roleInheritanceCases = 0;
  for (const version of ['2.3', '2.4']) {
    const roleModel = (target, expression) => `INTERLIS ${version}; CONTRACTED MODEL F AT "x" VERSION "1" =
TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = flag: BOOLEAN; END A; CLASS A1 EXTENDS A = END A1;
CLASS B = END B; CLASS B1 EXTENDS B = END B1; CLASS Owner = END Owner;
FUNCTION allKinds(v: OBJECTS OF ANYCLASS): BOOLEAN; FUNCTION onlyA(v: OBJECTS OF A): BOOLEAN;
ASSOCIATION Link = target -- {1} ${target}; owner -- {1} Owner; END Link;
CONSTRAINTS OF Owner = MANDATORY CONSTRAINT ${expression}; END; END T; END F.`;
    for (const [target, expression, expected] of [
      ['A OR B', 'allKinds(target)', 'succeeded'],
      ['A RESTRICTION (A1) OR B RESTRICTION (B1)', 'allKinds(target)', 'succeeded'],
      ['ANYCLASS RESTRICTION (A1; B1)', 'allKinds(target->THIS)', 'succeeded'],
      ['A RESTRICTION (A1)', 'onlyA(target)', 'succeeded'],
      ['ANYCLASS RESTRICTION (A; A1)', 'target->flag', 'succeeded'],
      ['A OR B', 'onlyA(target)', 'rejected'], ['A OR B', 'target->flag', 'rejected'],
      ['A OR A1', '#true', 'rejected'], ['A1 OR A', '#true', 'rejected'],
      ['A OR A', '#true', 'rejected'], ['A RESTRICTION (B)', '#true', 'rejected'],
      ['Missing', '#true', 'rejected'], ['Wrong.A', '#true', 'rejected']
    ]) { await verify(roleModel(target, expression), expected); roleInheritanceCases++; }
    const inherited = `INTERLIS ${version}; CONTRACTED MODEL F AT "x" VERSION "1" =
TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = flag: BOOLEAN; END A; CLASS A1 EXTENDS A = END A1;
CLASS B = END B; CLASS Owner = END Owner; CLASS Child EXTENDS Owner = END Child;
FUNCTION onlyA1(v: OBJECT OF A1): BOOLEAN;
ASSOCIATION Link = target -- {1} A; owner -- {1} Owner; END Link;
ASSOCIATION Narrow EXTENDS Link = target (EXTENDED) -- A1; owner (EXTENDED, HIDING) -- Child; END Narrow;
CONSTRAINTS OF Child = MANDATORY CONSTRAINT onlyA1(target); END; END T; END F.`;
    for (const [text, expected] of [
      [inherited, 'succeeded'], [inherited.replace('EXTENDS Link', 'EXTENDS Wrong.Link'), 'rejected'], [inherited.replace('EXTENDED, HIDING', 'EXTENDED'), 'rejected'],
      [inherited.replace('CONSTRAINTS OF Child', 'CONSTRAINTS OF Owner'), 'rejected'],
      [inherited.replace('target (EXTENDED) -- A1', 'target (EXTENDED) -- B'), 'rejected'],
      [inherited.replace('target (EXTENDED) -- A1', 'target (EXTENDED) -- {*} A1'), 'rejected'],
      [inherited.replace('ASSOCIATION Link =', 'ASSOCIATION Link (FINAL) ='), 'rejected'],
      [inherited.replace('target -- {1} A;', 'target (FINAL) -- {1} A;'), 'rejected'],
      [inherited.replace('target (EXTENDED)', 'target'), 'rejected'],
      [inherited.replace('EXTENDED, HIDING', 'EXTENDED, HIDING, EXTERNAL'), 'rejected'],
      [inherited.replace('END Narrow;', 'extra -- A; END Narrow;'), 'rejected'],
      [inherited.replace('CONSTRAINTS OF Child', 'ASSOCIATION Sibling EXTENDS Link = owner (EXTENDED) -- Child; END Sibling; CONSTRAINTS OF Child'), 'rejected'],
      [inherited.replace('owner (EXTENDED, HIDING) -- Child;', '').replace('onlyA1(target)', 'DEFINED(target)'), 'succeeded']
    ]) { await verify(text, expected); roleInheritanceCases++; }
    const multiple = inherited.replace('ASSOCIATION Link =', 'ASSOCIATION Link (OID) =')
      .replace('owner -- {1} Owner; END Link;', 'owner -- {1} Owner; CARDINALITY = {1..3}; END Link;')
      .replace('onlyA1(target)', 'INTERLIS.objectCount(\\target) > 0');
    for (const [text, expected] of [
      [multiple, 'succeeded'], [multiple.replace('END Narrow;', 'CARDINALITY = {1..4}; END Narrow;'), 'rejected'],
      [inherited.replace('EXTENDED, HIDING', 'EXTENDED').replace('onlyA1(target)', 'target->flag')
        .replace('CONSTRAINTS OF Child', 'ASSOCIATION Sibling EXTENDS Link = owner (EXTENDED) -- Child; END Sibling; CONSTRAINTS OF Child'), 'succeeded']
    ]) { await verify(text, expected); roleInheritanceCases++; }
    const context = `INTERLIS ${version}; CONTRACTED MODEL F AT "x" VERSION "1" =
TOPIC Base = OID AS INTERLIS.UUIDOID; CLASS A = END A; CLASS B = END B;
ASSOCIATION Link = a -- {1} A; b -- {1} B; END Link; END Base;
TOPIC Local EXTENDS Base = CLASS A (EXTENDED) = flag: BOOLEAN; END A;
CONSTRAINTS OF B = MANDATORY CONSTRAINT a->flag; END; END Local; END F.`;
    for (const [text, expected] of [
      [context, 'succeeded'], [context.replace('CLASS A (EXTENDED)', 'CLASS Unrelated'), 'rejected'],
      [context.replace('CONSTRAINTS OF B', 'ASSOCIATION Link (EXTENDED) = END Link; CONSTRAINTS OF B'), 'succeeded']
    ]) { await verify(text, expected); roleInheritanceCases++; }
  }
  return roleInheritanceCases;
}

async function qualifiedPaths({verify}) {
  let qualifiedPathCases = 0;
  for (const version of ['2.3', '2.4']) {
    const model = (nary, cardinality, expression) => `INTERLIS ${version}; CONTRACTED MODEL F AT "x" VERSION "1" =
TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = flag: BOOLEAN; END A; CLASS B = END B;
CLASS Other = END Other; CLASS Owner = END Owner;
FUNCTION one(v: OBJECT OF A): BOOLEAN; FUNCTION many(v: OBJECTS OF A): BOOLEAN;
FUNCTION manyB(v: OBJECTS OF B): BOOLEAN;
ASSOCIATION Link = a -- {${cardinality}} A; owner -- {1} Owner; ${nary ? 'other -- {1} Other;' : ''} END Link;
ASSOCIATION Chain = sender -- {1} A; b -- {1} B; END Chain;
CONSTRAINTS OF Owner = MANDATORY CONSTRAINT ${expression}; END; END T; END F.`;
    const rows = [
      [model(true, '1', 'many(a)'), 'succeeded'], [model(true, '1', 'one(a)'), 'rejected'],
      [model(true, '1', 'a->flag'), 'rejected'],
      [model(true, '1', 'many(owner[Link]->a)'), 'succeeded'],
      [model(false, '1', 'one(owner[Link]->a)'), 'succeeded'],
      [model(true, '1', 'many(owner->a)'), 'succeeded'],
      [model(true, '1', 'many(a[Link])'), 'rejected'],
      [model(true, '1', 'many(owner[Missing]->a)'), 'rejected'],
      [model(true, '1', 'many(owner[Owner]->a)'), 'rejected'],
      [model(true, '1', 'many(owner[Chain]->a)'), 'rejected'],
      [model(true, '1', 'many(owner[Link]->a)').replace('owner --', 'owner (EXTERNAL) --'), 'rejected']
    ];
    for (const nary of [false, true]) {
      for (const expression of ['manyB(a->b)', 'manyB(\\a->a->b)', 'many(owner[Link]->a)', 'many(a->THIS)'])
        rows.push([model(nary, '*', expression), 'succeeded']);
      for (const expression of ['one(owner[Link]->a)', 'DEFINED(a->flag)', 'a->flag == #true'])
        rows.push([model(nary, '*', expression), 'rejected']);
    }
    const union = model(false, '*', 'manyB(a->b)').replace('a -- {*} A', 'a -- {*} A OR Other')
      .replace('sender -- {1} A', 'sender -- {1} A OR Other');
    rows.push([union, 'succeeded'], [union.replace('sender -- {1} A OR Other', 'sender -- {1} A'), 'rejected']);
    const hiding = model(false, '1', 'many(owner[Narrow]->a)').replace('CONSTRAINTS OF Owner',
      'CLASS Child EXTENDS Owner = END Child; ASSOCIATION Narrow EXTENDS Link = owner (EXTENDED,HIDING) -- {1} Child; END Narrow; CONSTRAINTS OF Child');
    rows.push([hiding, 'succeeded'], [hiding.replace('owner[Narrow]', 'owner[Link]'), 'rejected']);
    const ambiguous = model(true, '1', 'many(owner[Link]->a)').replace('other -- {1} Other', 'other -- {1} Owner');
    rows.push([ambiguous, 'succeeded'], [ambiguous.replace('owner[Link]->a', 'a'), 'rejected']);
    if (version === '2.4') rows.push([model(false, '*', 'manyB(a->ref)').replace('flag: BOOLEAN;', 'flag: BOOLEAN; ref: REFERENCE TO B;'), 'succeeded']);
    for (const [text, expected] of rows) { await verify(text, expected); qualifiedPathCases++; }
  }
  return qualifiedPathCases;
}

async function constraintPaths({verify}) {
  let constraintPathCases = 0;
  for (const version of ['2.3', '2.4']) {
    const model = body => `INTERLIS ${version}; CONTRACTED MODEL M AT "https://example.org" VERSION "1" = ${body} END M.`;
    // The shared harness selects F.
    const wrap = body => model(body).replace('MODEL M ', 'MODEL F ').replace('END M.', 'END F.').replaceAll('M.', 'F.');
    const simple = rule => `TOPIC T = STRUCTURE S = code: 0..100; other: TEXT*20; END S;
CLASS A = detail: S; code: TEXT*20; ${rule} END A; END T;`;
    const rows = [
      [simple('UNIQUE WHERE detail->code > 0: detail->code, code;'), 'succeeded'],
      [simple('UNIQUE detail->missing;'), 'rejected'], [simple('UNIQUE WHERE code: detail->code;'), 'rejected'],
      [simple('UNIQUE detail;'), 'rejected'], [simple('UNIQUE code->code;'), 'rejected'],
      [simple('UNIQUE detail[FIRST]->code;'), 'rejected'],
      [simple('EXISTENCE CONSTRAINT detail[FIRST]->code REQUIRED IN A:code;'), 'rejected'],
      [simple('EXISTENCE CONSTRAINT THIS REQUIRED IN A:code;'), 'rejected'],
      [simple('UNIQUE code;').replace('CLASS A', 'CLASS Unrelated = code: BAG OF S; END Unrelated; CLASS A'), 'succeeded'],
      [simple('UNIQUE detail->code;').replace('detail: S', 'detail: BAG {1} OF S'), 'succeeded'],
      [simple('UNIQUE detail->code;').replace('detail: S', 'detail: BAG OF S'), 'rejected'],
      [simple('UNIQUE (BASKET) Key: code;'), version === '2.4' ? 'succeeded' : 'rejected'],
      [simple('UNIQUE (BASKET) (LOCAL) detail: code;'), 'rejected']
    ];
    const local = `TOPIC T = STRUCTURE Item = code: 0..10; name: TEXT*10; END Item;
STRUCTURE Group = entries: BAG OF Item; END Group; CLASS A = groups: LIST OF Group; active: BOOLEAN;
UNIQUE WHERE active: (LOCAL) groups->entries: code, name; END A; END T;`;
    rows.push([local, 'succeeded'], [local.replace('groups->entries:', 'groups->missing:'), 'rejected'],
      [local.replace('code, name;', 'missing;'), 'rejected'], [local.replace('groups->entries:', 'active:'), 'rejected'],
      [local.replace('groups->entries: code, name', 'groups->entries'), 'rejected']);
    const role = `TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = code: TEXT*10; END A; CLASS Owner = END Owner;
ASSOCIATION Link = a -- {1} A; owner -- {1} Owner; END Link;
CONSTRAINTS OF Owner = UNIQUE owner[Link]->a->code; END; END T;`;
    rows.push([role, 'succeeded'], [role.replace('a -- {1}', 'a -- {*}'), 'rejected'], [role.replace('[Link]', '[Missing]'), 'rejected']);
    const existence = `TOPIC T = STRUCTURE S = code: TEXT*20; END S; CLASS A = detail: S; END A;
CLASS B = short: TEXT*10; EXISTENCE CONSTRAINT short REQUIRED IN A:detail->code OR B:short; END B; END T;`;
    rows.push([existence, 'succeeded'], [existence.replace('OR B:short', 'OR B:missing'), 'rejected'],
      [existence.replace('A:detail->code', 'Wrong.A:detail->code'), 'rejected'],
      [existence.replace('short: TEXT*10', 'short: TEXT*30'), 'succeeded']);
    const dependency = `TOPIC Other = CLASS A = code: 0..100; END A; END Other;
TOPIC T = DEPENDS ON Other; CLASS B = code: 0..10; EXISTENCE CONSTRAINT code REQUIRED IN M.Other.A:code; END B; END T;`;
    rows.push([dependency.replace('M.Other.A', 'Other.A'), 'rejected'], [dependency, 'succeeded'], [dependency.replace('DEPENDS ON Other;', ''), 'rejected'],
      [dependency.replace('code: 0..10; EXISTENCE', 'code: 0..200; EXISTENCE'), 'succeeded'],
      ['TOPIC T = CLASS A = code: 0..100; END A; CLASS B = code: 0..10; EXISTENCE CONSTRAINT code REQUIRED IN M.T.A:code; END B; END T;', 'succeeded']);
    const structure = `TOPIC T = STRUCTURE S = code: TEXT; END S; STRUCTURE R EXTENDS S = END R;
STRUCTURE Wrong = code: TEXT; END Wrong; CLASS A = value: S; END A;
CLASS B = value: R; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;`;
    rows.push([structure, 'succeeded'], [structure.replace('value: R; EXISTENCE', 'value: Wrong; EXISTENCE'), 'rejected']);
    const geometry = `TOPIC T = DOMAIN C = COORD 0..100, 0..100; D = COORD 0..100, 0..100, 0..100;
CLASS A = coord: C; END A; CLASS B = line: POLYLINE WITH (STRAIGHTS) VERTEX C;
EXISTENCE CONSTRAINT line REQUIRED IN A:coord; END B; END T;`;
    rows.push([geometry, 'succeeded'], [geometry.replace('coord: C', 'coord: D'), 'rejected']);
    for (const type of ['(one, two)', 'BAG OF S']) rows.push([
      `TOPIC T = STRUCTURE S = code: TEXT; END S; CLASS A = value: ${type}; END A;
CLASS B = value: ${type}; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;`, 'succeeded']);
    const exists = (source, target) => `TOPIC T = CLASS A = value: ${target}; END A;
CLASS B = value: ${source}; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;`;
    for (const type of ['HALIGNMENT', 'VALIGNMENT', 'BLACKBOX XML', 'BLACKBOX BINARY', '(one, two(sub, other))'])
      rows.push([exists(type, type), 'succeeded']);
    rows.push([exists('HALIGNMENT', 'VALIGNMENT'), 'rejected'], [exists('BLACKBOX XML', 'BLACKBOX BINARY'), 'rejected'],
      [exists('0..999', '0..10'), 'succeeded'], [exists('0..10', '0..999'), 'succeeded'],
      [exists('COORD 0..999, 0..999', 'COORD 0..10, 0..10'), 'succeeded']);
    if (version === '2.4') for (const type of ['DATE', 'TIMEOFDAY', 'DATETIME'])
      rows.push([exists(type, type), 'succeeded'], [exists(type, type === 'DATE' ? 'TIMEOFDAY' : 'DATE'), 'rejected']);
    const list = selection => `TOPIC T = STRUCTURE S = code: TEXT*20; END S;
STRUCTURE Group = items: LIST {0..3} OF S; END Group;
CLASS A = value: TEXT*10; END A; CLASS B = groups: LIST OF Group;
UNIQUE groups[FIRST]->items[${selection}]->code;
EXISTENCE CONSTRAINT groups[LAST]->items[${selection}]->code REQUIRED IN A:value; END B; END T;`;
    for (const selection of ['FIRST', 'LAST', '1', '3']) rows.push([list(selection), 'succeeded']);
    for (const selection of ['0', '4', '999999999999999999999999999999999999']) rows.push([list(selection), 'rejected']);
    rows.push([list('FIRST').replace('items: LIST', 'items: BAG'), 'rejected'],
      [list('FIRST').replace('{0..3}', '{0..0}'), 'rejected'],
      [list('999999999999999999999999999999999999').replace('{0..3}', '{*}'), 'succeeded'],
      [list('1').replace('groups[FIRST]', 'groups'), 'rejected'],
      [list('1').replace('->code REQUIRED', '->missing REQUIRED'), 'rejected']);
    const axis = `TOPIC T = CLASS A = value: 0..10; END A; CLASS B = point: COORD 0..999, 5..20;
UNIQUE point[1], point[2]; EXISTENCE CONSTRAINT point[2] REQUIRED IN A:value; END B; END T;`;
    rows.push([axis, 'succeeded']);
    for (const selection of ['0', '3', 'FIRST', 'LAST', '999999999999999999999999999999999'])
      rows.push([axis.replaceAll('point[2]', `point[${selection}]`), 'rejected']);
    rows.push([axis.replace('point[2] REQUIRED', 'point[2]->THIS REQUIRED'), 'rejected']);
    const selected = `TOPIC T = STRUCTURE S = code: TEXT; END S; STRUCTURE Wrong = code: TEXT; END Wrong;
CLASS A = value: S; END A; CLASS B = value: LIST {0..2} OF S;
EXISTENCE CONSTRAINT value[LAST] REQUIRED IN A:value; END B; END T;`;
    rows.push([selected, 'succeeded'], [selected.replace('CLASS A = value: S', 'CLASS A = value: Wrong'), 'rejected'],
      [selected.replace('value[LAST]', 'value').replace('LIST {0..2}', 'BAG {0..1}'), 'succeeded'],
      [selected.replace('EXISTENCE CONSTRAINT value[LAST] REQUIRED IN A:value', 'UNIQUE value[LAST]'), 'rejected']);
    for (const [body, expected] of rows) { await verify(wrap(body), expected); constraintPathCases++; }
  }
  return constraintPathCases;
}

async function topicDependencies({verify}) {
  let dependencyCases = 0;
  for (const version of ['2.3', '2.4']) {
    const wrap = body => `INTERLIS ${version}; CONTRACTED MODEL F AT "https://example.org" VERSION "1" = ${body} END F.`;
    const targets = 'TOPIC A = OID AS INTERLIS.UUIDOID; CLASS Target = END Target; END A; TOPIC B = OID AS INTERLIS.UUIDOID; CLASS Other = END Other; END B; ';
    const reference = target => `STRUCTURE S = ref: REFERENCE TO (EXTERNAL) ${target}; END S; `;
    const role = target => `CLASS Owner = OID AS INTERLIS.UUIDOID; END Owner; ASSOCIATION Link = ref (EXTERNAL) -- ${target}; owner -- Owner; END Link; `;
    const rows = [];
    for (const member of [reference('F.A.Target'), role('F.A.Target')]) {
      const direct = targets + 'TOPIC T = ' + member + 'END T;';
      rows.push([direct,'rejected'], [direct.replace('TOPIC T =','TOPIC T = DEPENDS ON A;'),'succeeded'],
        [direct.replace('TOPIC T =','TOPIC T = DEPENDS ON B;'),'rejected'],
        [direct.replace('TOPIC T =','TOPIC T = DEPENDS ON A;').replace('(EXTERNAL)',''),'rejected']);
      const inherited = targets + 'TOPIC Base = DEPENDS ON A; END Base; TOPIC Middle EXTENDS Base = END Middle; TOPIC T EXTENDS Middle = ' + member + 'END T;';
      rows.push([inherited,'succeeded'],[inherited.replace('TOPIC T EXTENDS Middle =','TOPIC T = DEPENDS ON Middle;'),'rejected'],
        [inherited.replace('TOPIC T EXTENDS Middle =','TOPIC T = DEPENDS ON Middle,A;'),'succeeded']);
    }
    for (const member of [reference('ANYCLASS RESTRICTION (F.A.Target; F.B.Other)'),role('ANYCLASS RESTRICTION (F.A.Target; F.B.Other)'),role('F.A.Target OR F.B.Other')]) {
      const body = targets + 'TOPIC T = DEPENDS ON A; ' + member + 'END T;';
      rows.push([body,'rejected'],[body.replace('DEPENDS ON A;','DEPENDS ON A,B;'),'succeeded']);
    }
    for (const member of [reference('F.A.Target RESTRICTION (F.D.Child)'),role('F.A.Target RESTRICTION (F.D.Child)')]) {
      const body = targets + 'TOPIC D EXTENDS A = CLASS Child EXTENDS Target = END Child; END D; TOPIC T = DEPENDS ON D; ' + member + 'END T;';
      rows.push([body,'succeeded'],[body.replace('DEPENDS ON D;','DEPENDS ON A;'),'rejected']);
    }
    const inheritedMember = targets + 'TOPIC Base = DEPENDS ON A; ' + reference('F.A.Target') + 'END Base; TOPIC T = STRUCTURE Child EXTENDS F.Base.S = END Child; END T;';
    rows.push([inheritedMember,'rejected'],[inheritedMember.replace('TOPIC T =','TOPIC T = DEPENDS ON A;'),'succeeded'],
      [inheritedMember.replace('TOPIC T =','TOPIC T EXTENDS Base ='),'succeeded']);
    const mandatory = inheritedMember.replace('STRUCTURE Child EXTENDS F.Base.S = END Child;', 'STRUCTURE Child EXTENDS F.Base.S = ref (EXTENDED): MANDATORY; END Child;');
    rows.push([mandatory,'rejected'],[mandatory.replace('TOPIC T =','TOPIC T = DEPENDS ON A;'),'succeeded']);
    const inheritedRole = targets + 'TOPIC Base = DEPENDS ON A; ' + role('F.A.Target') + 'END Base; TOPIC T EXTENDS Base = ASSOCIATION Link (EXTENDED) = END Link; END T;';
    rows.push([inheritedRole,'succeeded'],[inheritedRole.replace('DEPENDS ON A;','DEPENDS ON B;'),'rejected']);
    const local = targets + 'TOPIC T EXTENDS A = ' + reference('Target').replace('(EXTERNAL) ','') + 'END T;';
    const collision = local.replace('CLASS Other','CLASS Target').replace('END Other','END Target').replace('REFERENCE TO Target','REFERENCE TO F.B.Target');
    rows.push([local,'succeeded'],[collision,'rejected'],[collision.replace('TOPIC T EXTENDS A =','TOPIC T EXTENDS A = DEPENDS ON B;'),'rejected'],
      [collision.replace('TOPIC T EXTENDS A =','TOPIC T EXTENDS A = DEPENDS ON B;').replace('REFERENCE TO F.B.Target','REFERENCE TO (EXTERNAL) F.B.Target'),'succeeded']);
    for (const body of ['TOPIC A = DEPENDS ON A; END A;', 'TOPIC A = DEPENDS ON B; END A; TOPIC B = DEPENDS ON A; END B;',
      'TOPIC A = DEPENDS ON B; END A; TOPIC B EXTENDS A = END B;',
      targets + 'TOPIC T = DEPENDS ON Wrong.A; END T;', targets + 'TOPIC T = DEPENDS ON A.Target; END T;']) rows.push([body,'rejected']);
    const nested = targets + 'STRUCTURE Shared = ref: REFERENCE TO (EXTERNAL) F.A.Target; END Shared; STRUCTURE Box = items: BAG OF Shared; END Box; TOPIC T = CLASS Holder = value: Box; END Holder; END T;';
    rows.push([nested,'rejected'],[nested.replace('TOPIC T =','TOPIC T = DEPENDS ON A;'),'succeeded']);
    if (version === '2.4') {
      const collection = targets + 'TOPIC T = DEPENDS ON F.A; ' + reference('F.A.Target').replace('ref: REFERENCE','ref: LIST OF REFERENCE') + 'END T;';
      rows.push([collection,'succeeded'],[collection.replace('DEPENDS ON F.A;',''),'rejected']);
    }
    for (const [body,expected] of rows) { await verify(wrap(body),expected); dependencyCases++; }
  }
  return dependencyCases;
}

async function importedDomains({verifySources}) {
  let importedDomainCases = 0;
  for (const version of ['2.3', '2.4']) {
    const wrap = (name, body) => `INTERLIS ${version}; CONTRACTED MODEL ${name} AT "https://example.org" VERSION "1" = ${body} END ${name}.`;
    const rows = [];
    const base = 'DOMAIN Flag = BOOLEAN; TOPIC T = STRUCTURE S = flag: Base.Flag; END S; END T;';
    const child = 'TOPIC T = STRUCTURE Child EXTENDS Base.T.S = flag (EXTENDED): MANDATORY; MANDATORY CONSTRAINT flag == #true; END Child; END T;';
    rows.push([base,child,'succeeded'], [base.replace('Flag = BOOLEAN','Flag = TEXT*20'),child,'rejected']);
    const scalars = 'DOMAIN Flag = BOOLEAN; Number = 0..100; Label = TEXT*20;';
    const scalarUse = 'FUNCTION truth(v: Base.Flag): Base.Flag; TOPIC T = CLASS C = flag: Base.Flag; n: Base.Number; label: Base.Label; MANDATORY CONSTRAINT truth(flag) AND n > 0 AND INTERLIS.len(label) > 0; END C; END T;';
    rows.push([scalars,scalarUse,'succeeded'], [scalars,scalarUse.replace('truth(flag)','truth(n)'),'rejected'], [scalars,scalarUse.replace('n > 0','label > 0'),'rejected']);
    const importedFunction = scalarUse.replace('FUNCTION truth(v: Base.Flag): Base.Flag; ', '').replace('truth(flag)', 'Base.truth(flag)');
    const functionDefinitions = scalars + ' FUNCTION truth(v: Base.Flag): Base.Flag;';
    rows.push([functionDefinitions,importedFunction,'succeeded'], [functionDefinitions,importedFunction.replace('Base.truth(flag)','Base.truth(n)'),'rejected']);
    if (version === '2.4') {
      const constrained = 'DOMAIN Number = 0..100 CONSTRAINTS Positive: THIS > 0; FUNCTION identity(v: Base.Number): Base.Number;';
      const constraintUse = 'TOPIC T = CLASS C = n: Base.Number; MANDATORY CONSTRAINT Base.identity(n) > 0; END C; END T;';
      rows.push([constrained,constraintUse,'succeeded'], [constrained.replace('THIS > 0','INTERLIS.len(THIS) > 0'),constraintUse,'rejected']);
    }
    const structures = 'DOMAIN Number = 0..100; STRUCTURE Item = code: Base.Number; END Item;';
    const paths = 'TOPIC T = CLASS Required = code: Base.Number; END Required; CLASS C = detail: Base.Item; items: LIST {1..3} OF Base.Item; MANDATORY CONSTRAINT items[1]->code > 0; UNIQUE detail->code; UNIQUE (LOCAL) items: code; EXISTENCE CONSTRAINT detail->code REQUIRED IN Required: code; END C; END T;';
    rows.push([structures,paths,'succeeded'], [structures,paths.replace('items[1]','items[4]'),'rejected'], [structures.replace('Number = 0..100','Number = TEXT*20'),paths,'rejected']);
    const enums = 'DOMAIN Color = (red, green) ORDERED;';
    const enumUse = 'DOMAIN More EXTENDS Base.Color = (blue); TOPIC T = CLASS C = color: More; MANDATORY CONSTRAINT color == #red; END C; END T;';
    rows.push([enums,enumUse,'succeeded'], [enums,enumUse.replace('#red','#missing'),'rejected']);
    const formats = 'DOMAIN Number = 0..99; STRUCTURE Parts = n: Base.Number; END Parts; DOMAIN Code = FORMAT BASED ON Parts (n/2) "00" .. "99";';
    const formatUse = 'TOPIC T = CLASS C = code: Base.Code; MANDATORY CONSTRAINT code == "05"; END C; END T;';
    rows.push([formats,formatUse,'succeeded'], [formats,formatUse.replace('"05"','"5"'),'rejected']);
    rows.push(['DOMAIN Flag = BOOLEAN;','TOPIC T = CLASS C = flag: Wrong.Flag; END C; END T;','rejected'],
      ['DOMAIN Flag = BOOLEAN;','TOPIC T = CLASS C = flag: Flag; END C; END T;','rejected'],
      ['DOMAIN Flag = BOOLEAN;','TOPIC T = DOMAIN Flag = TEXT*10; CLASS C = flag: Flag; MANDATORY CONSTRAINT INTERLIS.len(flag) > 0; END C; END T;','succeeded'],
      ['UNIT BaseUnit (ABSTRACT); Wrapped (ABSTRACT) = (BaseUnit); DOMAIN One = (one);','TOPIC T = CLASS C = value: Base.One; MANDATORY CONSTRAINT value == #one; END C; END T;','succeeded']);
    for (const cycle of ['DOMAIN A EXTENDS B = (a); B EXTENDS A = (b);', 'DOMAIN A = FORMAT B "00" .. "99"; B = FORMAT A "00" .. "99";']) {
      rows.push([cycle,'TOPIC T = CLASS C = value: Base.A; END C; END T;','rejected']);
    }
    for (const baseFirst of [true, false]) for (const [definitions, consumer, expected] of rows) {
      await verifySources([
        {sourceId: `memory:/${baseFirst ? 'a' : 'z'}-base.ili`, text: wrap('Base', definitions)},
        {sourceId: `memory:/${baseFirst ? 'z' : 'a'}-importer.ili`, text: wrap('F', 'IMPORTS Base; ' + consumer)}
      ], expected);
      importedDomainCases++;
    }
  }
  return importedDomainCases;
}

async function unqualifiedPredefinedNames({call, verify, verifySources}) {
  const e1UnqualifiedNameCases = [{"name":"oid-explicit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS.UUIDOID;END S;END F.","expected":"succeeded"},{"name":"oid-bare-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:UUIDOID;END S;END F.","expected":"rejected"},{"name":"oid-unqualified-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S=value:UUIDOID;END S;END F.","expected":"succeeded"},{"name":"date-explicit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS.XMLDate;END S;END F.","expected":"succeeded"},{"name":"date-bare-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:XMLDate;END S;END F.","expected":"rejected"},{"name":"date-unqualified-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S=value:XMLDate;END S;END F.","expected":"succeeded"},{"name":"legacydate-explicit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS.INTERLIS_1_DATE;END S;END F.","expected":"succeeded"},{"name":"legacydate-bare-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS_1_DATE;END S;END F.","expected":"rejected"},{"name":"legacydate-unqualified-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S=value:INTERLIS_1_DATE;END S;END F.","expected":"succeeded"},{"name":"unit-explicit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=DOMAIN Dist=0..100 [INTERLIS.m];END F.","expected":"succeeded"},{"name":"unit-bare-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=DOMAIN Dist=0..100 [m];END F.","expected":"rejected"},{"name":"unit-unqualified-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;DOMAIN Dist=0..100 [m];END F.","expected":"succeeded"},{"name":"structure-explicit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS.TimeOfDay;END S;END F.","expected":"succeeded"},{"name":"structure-bare-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:TimeOfDay;END S;END F.","expected":"rejected"},{"name":"structure-unqualified-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S=value:TimeOfDay;END S;END F.","expected":"succeeded"},{"name":"structure-base-explicit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S(ABSTRACT) EXTENDS INTERLIS.LineSegment=END S;END F.","expected":"succeeded"},{"name":"structure-base-bare-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S(ABSTRACT) EXTENDS LineSegment=END S;END F.","expected":"rejected"},{"name":"structure-base-unqualified-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S(ABSTRACT) EXTENDS LineSegment=END S;END F.","expected":"succeeded"},{"name":"metadata-base-explicit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T(ABSTRACT)=CLASS R(ABSTRACT) EXTENDS INTERLIS.SCALSYSTEM=END R;END T;END F.","expected":"succeeded"},{"name":"metadata-base-bare-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T(ABSTRACT)=CLASS R(ABSTRACT) EXTENDS SCALSYSTEM=END R;END T;END F.","expected":"rejected"},{"name":"metadata-base-unqualified-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;TOPIC T(ABSTRACT)=CLASS R(ABSTRACT) EXTENDS SCALSYSTEM=END R;END T;END F.","expected":"succeeded"},{"name":"function-explicit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=MANDATORY CONSTRAINT INTERLIS.len(\"abc\")>0;END C;END T;END F.","expected":"succeeded"},{"name":"function-bare-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0;END C;END T;END F.","expected":"rejected"},{"name":"function-unqualified-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0;END C;END T;END F.","expected":"succeeded"},{"name":"local-function-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;FUNCTION len(v:BOOLEAN):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(#true);END C;END T;END F.","expected":"succeeded"},{"name":"two-competing-functions-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL Other AT \"https://example.org\" VERSION \"1\"=FUNCTION len(v:BOOLEAN):BOOLEAN;END Other.CONTRACTED MODEL More AT \"https://example.org\" VERSION \"1\"=FUNCTION len(v:BOOLEAN):BOOLEAN;END More.CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED Other, UNQUALIFIED More, UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0;END C;END T;END F.","expected":"rejected"},{"name":"oid-explicit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS.UUIDOID;END S;END F.","expected":"succeeded"},{"name":"oid-bare-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:UUIDOID;END S;END F.","expected":"rejected"},{"name":"oid-unqualified-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S=value:UUIDOID;END S;END F.","expected":"succeeded"},{"name":"date-explicit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS.XMLDate;END S;END F.","expected":"succeeded"},{"name":"date-bare-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:XMLDate;END S;END F.","expected":"rejected"},{"name":"date-unqualified-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S=value:XMLDate;END S;END F.","expected":"succeeded"},{"name":"legacydate-explicit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS.INTERLIS_1_DATE;END S;END F.","expected":"succeeded"},{"name":"legacydate-bare-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS_1_DATE;END S;END F.","expected":"rejected"},{"name":"legacydate-unqualified-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S=value:INTERLIS_1_DATE;END S;END F.","expected":"succeeded"},{"name":"unit-explicit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=DOMAIN Dist=0..100 [INTERLIS.m];END F.","expected":"succeeded"},{"name":"unit-bare-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=DOMAIN Dist=0..100 [m];END F.","expected":"rejected"},{"name":"unit-unqualified-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;DOMAIN Dist=0..100 [m];END F.","expected":"succeeded"},{"name":"structure-explicit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:INTERLIS.TimeOfDay;END S;END F.","expected":"succeeded"},{"name":"structure-bare-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=value:TimeOfDay;END S;END F.","expected":"rejected"},{"name":"structure-unqualified-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S=value:TimeOfDay;END S;END F.","expected":"succeeded"},{"name":"structure-base-explicit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S(ABSTRACT) EXTENDS INTERLIS.LineSegment=END S;END F.","expected":"succeeded"},{"name":"structure-base-bare-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S(ABSTRACT) EXTENDS LineSegment=END S;END F.","expected":"rejected"},{"name":"structure-base-unqualified-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE S(ABSTRACT) EXTENDS LineSegment=END S;END F.","expected":"succeeded"},{"name":"metadata-base-explicit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T(ABSTRACT)=CLASS R(ABSTRACT) EXTENDS INTERLIS.SCALSYSTEM=END R;END T;END F.","expected":"succeeded"},{"name":"metadata-base-bare-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T(ABSTRACT)=CLASS R(ABSTRACT) EXTENDS SCALSYSTEM=END R;END T;END F.","expected":"rejected"},{"name":"metadata-base-unqualified-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;TOPIC T(ABSTRACT)=CLASS R(ABSTRACT) EXTENDS SCALSYSTEM=END R;END T;END F.","expected":"succeeded"},{"name":"function-explicit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=MANDATORY CONSTRAINT INTERLIS.len(\"abc\")>0;END C;END T;END F.","expected":"succeeded"},{"name":"function-bare-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0;END C;END T;END F.","expected":"rejected"},{"name":"function-unqualified-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0;END C;END T;END F.","expected":"succeeded"},{"name":"local-function-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;FUNCTION len(v:BOOLEAN):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(#true);END C;END T;END F.","expected":"succeeded"},{"name":"two-competing-functions-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL Other AT \"https://example.org\" VERSION \"1\"=FUNCTION len(v:BOOLEAN):BOOLEAN;END Other.CONTRACTED MODEL More AT \"https://example.org\" VERSION \"1\"=FUNCTION len(v:BOOLEAN):BOOLEAN;END More.CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED Other, UNQUALIFIED More, UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0;END C;END T;END F.","expected":"rejected"},{"name":"local-domain-oid-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;DOMAIN UUIDOID=BOOLEAN;STRUCTURE S=value:UUIDOID;END S;END F.","expected":"succeeded"},{"name":"local-domain-date-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;DOMAIN XMLDate=BOOLEAN;STRUCTURE S=value:XMLDate;END S;END F.","expected":"succeeded"},{"name":"local-structure-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE TimeOfDay=flag:BOOLEAN;END TimeOfDay;STRUCTURE S=value:TimeOfDay;END S;END F.","expected":"succeeded"},{"name":"local-unit-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;UNIT m;DOMAIN Distance=0..10[m];END F.","expected":"succeeded"},{"name":"primitive-keywords-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=flag:BOOLEAN;url:URI;name:NAME;horizontal:HALIGNMENT;vertical:VALIGNMENT;END S;END F.","expected":"succeeded"},{"name":"bare-range-query-source-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;DOMAIN Distance=0..10[m];STRUCTURE S=id:UUIDOID;date:XMLDate;clock:TimeOfDay;END S;FUNCTION isId(v:UUIDOID):BOOLEAN;END F.","expected":"succeeded"},{"name":"explicit-import-disambiguation-2.3","text":"INTERLIS 2.3;CONTRACTED MODEL Other AT \"https://example.org\" VERSION \"1\"=FUNCTION len(v:BOOLEAN):BOOLEAN;END Other.CONTRACTED MODEL More AT \"https://example.org\" VERSION \"1\"=FUNCTION len(v:BOOLEAN):BOOLEAN;END More.CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED Other,UNQUALIFIED More,UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT Other.len(#true) AND INTERLIS.len(\"abc\")>0;END C;END T;END F.","expected":"succeeded"},{"name":"local-domain-oid-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;DOMAIN UUIDOID=BOOLEAN;STRUCTURE S=value:UUIDOID;END S;END F.","expected":"succeeded"},{"name":"local-domain-date-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;DOMAIN XMLDate=BOOLEAN;STRUCTURE S=value:XMLDate;END S;END F.","expected":"succeeded"},{"name":"local-structure-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;STRUCTURE TimeOfDay=flag:BOOLEAN;END TimeOfDay;STRUCTURE S=value:TimeOfDay;END S;END F.","expected":"succeeded"},{"name":"local-unit-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;UNIT m;DOMAIN Distance=0..10[m];END F.","expected":"succeeded"},{"name":"primitive-keywords-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=flag:BOOLEAN;url:URI;name:NAME;horizontal:HALIGNMENT;vertical:VALIGNMENT;END S;END F.","expected":"succeeded"},{"name":"bare-range-query-source-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED INTERLIS;DOMAIN Distance=0..10[m];STRUCTURE S=id:UUIDOID;date:XMLDate;clock:TimeOfDay;END S;FUNCTION isId(v:UUIDOID):BOOLEAN;END F.","expected":"succeeded"},{"name":"explicit-import-disambiguation-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL Other AT \"https://example.org\" VERSION \"1\"=FUNCTION len(v:BOOLEAN):BOOLEAN;END Other.CONTRACTED MODEL More AT \"https://example.org\" VERSION \"1\"=FUNCTION len(v:BOOLEAN):BOOLEAN;END More.CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS UNQUALIFIED Other,UNQUALIFIED More,UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT Other.len(#true) AND INTERLIS.len(\"abc\")>0;END C;END T;END F.","expected":"succeeded"},{"name":"primitive-temporal-keywords-2.4","text":"INTERLIS 2.4;CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE S=date:DATE;time:TIMEOFDAY;timestamp:DATETIME;END S;END F.","expected":"succeeded"}];
  for(const c of e1UnqualifiedNameCases) await verify(c.text,c.expected);
  for(const version of ['2.3','2.4']) {
    const sourceId='memory:/unqualified-names.ili';
    const body='DOMAIN Distance=0..10[m];STRUCTURE S=id:UUIDOID;date:XMLDate;clock:TimeOfDay;END S;FUNCTION isId(v:UUIDOID):BOOLEAN;';
    const text=`INTERLIS ${version};CONTRACTED MODEL F AT "https://example.org" VERSION "1"=IMPORTS UNQUALIFIED INTERLIS;${body}END F.`;
    await verifySources([{sourceId,text}],'succeeded',['F'],async(sessionId,compiled)=>{
      const inspect=async(snapshotId,revision)=>{
        const words=['m','UUIDOID','XMLDate','TimeOfDay'];
        for(const word of words) {
          const offset=word==='m'?text.indexOf('[m]')+1:text.indexOf(':'+word)+1;
          const query={sourceId,revision,startOffset:offset,endOffset:offset};
          const symbol=await call('query',sessionId,{snapshotId,query:{...query,kind:'symbolAt'}});
          const definition=await call('query',sessionId,{snapshotId,query:{...query,kind:'definition'}});
          if(symbol.kind!=='known' || definition.kind!=='definition' || symbol.symbolId!==definition.symbolId)throw new Error('Bare predefined query mismatch '+JSON.stringify({word,symbol,definition}));
        }
      };
      await inspect(compiled.snapshotId,'1');
      let updated=await call('updateSource',sessionId,{sourceId,expectation:{kind:'at',revision:'1'},change:{kind:'replace',text:text.replace('IMPORTS UNQUALIFIED INTERLIS','IMPORTS INTERLIS')}});
      const rejected=await call('compile',sessionId,{expectedWorkspaceRevision:updated.workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});
      if(rejected.outcome!=='rejected')throw new Error('Plain import exposed bare predefined names');
      updated=await call('updateSource',sessionId,{sourceId,expectation:{kind:'at',revision:updated.sourceRevision},change:{kind:'replace',text}});
      const recovered=await call('compile',sessionId,{expectedWorkspaceRevision:updated.workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});
      if(recovered.outcome!=='succeeded')throw new Error('Bare name edit did not recover');
      await inspect(recovered.snapshotId,updated.sourceRevision);
    });
  }
  return e1UnqualifiedNameCases.length + 2;
}

async function enumTargetsInheritanceAndOrder({call, verifySources}) {
  let a3CounterChecks = 0;
  for (const version of ['2.3', '2.4']) {
    const targetText = `INTERLIS ${version}; MODEL F AT "https://example.org" VERSION "1" = DOMAIN E1=(red,blue); DOMAIN E2=(red,green); TOPIC T=CLASS C=a:E1;b:E2; MANDATORY CONSTRAINT a==#red AND b==#red; END C; END T; END F.`;
    await verifySources([{sourceId: 'memory:/a3-target.ili', text: targetText}], 'succeeded', ['F'], async (sessionId, compiled) => {
      const sourceId = 'memory:/a3-target.ili';
      const first = targetText.indexOf('#red', targetText.indexOf('MANDATORY CONSTRAINT')) + 1;
      const second = targetText.indexOf('#red', first) + 1;
      const query = async (kind, offset) => call('query', sessionId, {snapshotId: compiled.snapshotId,
        query: {kind, sourceId, revision: '1', startOffset: offset, endOffset: offset}});
      const firstUse = await query('symbolAt', first);
      const secondUse = await query('symbolAt', second);
      const firstDefinition = await query('definition', first);
      const secondDefinition = await query('definition', second);
      if (firstUse.kind !== 'known' || secondUse.kind !== 'known' ||
          firstDefinition.kind !== 'definition' || secondDefinition.kind !== 'definition') {
        throw new Error('A3 same-named enum target query failed ' + JSON.stringify({firstUse, secondUse, firstDefinition, secondDefinition}));
      }
      if (firstUse.symbolId === secondUse.symbolId || firstDefinition.symbolId === secondDefinition.symbolId) {
        throw new Error('A3 same-named enum elements lost their domain target ' + JSON.stringify({firstUse, secondUse}));
      }
    });
    a3CounterChecks++;
    const lostText = `INTERLIS ${version}; MODEL F AT "https://example.org" VERSION "1" = TOPIC T=CLASS Base=inherited:TEXT;END Base;CLASS Child EXTENDS Base=own:TEXT;END Child;END T;END F.`;
    await verifySources([{sourceId: 'memory:/a3-lost-member.ili', text: lostText}], 'succeeded', ['F'], async (sessionId, compiled) => {
      const completion = await call('query', sessionId, {snapshotId: compiled.snapshotId, query: {
        kind: 'completion', sourceId: 'memory:/a3-lost-member.ili', revision: '1',
        startOffset: lostText.indexOf('own:TEXT'), endOffset: lostText.indexOf('own:TEXT'), prefix: 'inherited', limit: 20}});
      const labels = (completion.candidates || []).map(candidate => candidate.label);
      if (!labels.includes('inherited')) throw new Error('A3 inherited member missing from public completion ' + JSON.stringify(completion));
    });
    a3CounterChecks++;
    const orderText = `INTERLIS ${version}; CONTRACTED MODEL Original AT "https://example.org" VERSION "1" = DOMAIN A1=TEXT; DOMAIN A2=BOOLEAN; END Original. CONTRACTED MODEL F AT "https://example.org" VERSION "1" TRANSLATION OF Original["1"] = DOMAIN B1=BOOLEAN; DOMAIN B2=TEXT; END F.`;
    await verifySources([{sourceId: 'memory:/a3-order.ili', text: orderText}], 'rejected', ['F']);
    await verifySources([{sourceId: 'memory:/a3-order.ili', text: orderText.replace('DOMAIN B1=BOOLEAN; DOMAIN B2=TEXT;', 'DOMAIN B1=TEXT; DOMAIN B2=BOOLEAN;')}], 'succeeded', ['F']);
    a3CounterChecks += 2;
  }
  return a3CounterChecks;
}

module.exports = {
  'qualified-closure-and-scope': qualifiedClosureAndScope,
  'resolution-and-lexical-policy': resolutionAndLexicalPolicy,
  'transitive-semantic-closure': transitiveSemanticClosure,
  'path-and-object-scopes': pathAndObjectScopes,
  'role-inheritance': roleInheritance,
  'qualified-paths': qualifiedPaths,
  'constraint-paths': constraintPaths,
  'topic-dependencies': topicDependencies,
  'imported-domains': importedDomains,
  'unqualified-predefined-names': unqualifiedPredefinedNames,
  'enum-targets-inheritance-and-order': enumTargetsInheritanceAndOrder,
};
