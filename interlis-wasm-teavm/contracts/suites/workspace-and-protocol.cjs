'use strict';

async function requestDiagnosticScope({call, verifySources}) {
  let requestDiagnosticCases = 0;
  for (const version of ['2.3', '2.4']) {
    const model = (name, body) => 'MODEL ' + name + ' AT "x" VERSION "1"=' + body + 'END ' + name + '.';
    const good = model('Good', '');
    const bad = model('Bad', 'TOPIC T=CLASS C=value:Missing;END C;END T;');
    const wrap = body => '!! 🦊\r\nINTERLIS ' + version + ';\r\n' + body;
    const sourceId = 'memory:/cli2-ä 🦊.ili';
    await verifySources([{sourceId, text: wrap(good + bad)}], 'succeeded', ['Good'], async (sessionId, first) => {
      if (!first.diagnostics.some(d => d.code === 'ILI-SEM-0001')) throw new Error('CLI2 full IDE diagnostics lost');
      const compile = modelName => call('compile', sessionId, {expectedWorkspaceRevision:'1', roots:[{kind:'byName',modelName}]});
      if ((await compile('Bad')).outcome !== 'rejected' || (await compile('Good')).outcome !== 'succeeded') throw new Error('CLI2 stale root selection');
    }); requestDiagnosticCases++;
    await verifySources([{sourceId, text: wrap(good + model('Other', 'TOPIC T(ABSTRACT,ABSTRACT)=END T;'))}], 'succeeded', ['Good'], async (sessionId) => {
      const config = await call('setConfiguration', sessionId, {expectedWorkspaceRevision:'1',warningPolicy:'REJECT_WARNINGS'});
      const compile = modelName => call('compile', sessionId, {expectedWorkspaceRevision:config.workspaceRevision,roots:[{kind:'byName',modelName}]});
      const clean = await compile('Good'), warning = await compile('Other');
      if (clean.outcome !== 'succeeded' || !clean.diagnostics.some(d => d.severity === 'WARNING') || warning.outcome !== 'rejected' || !warning.rejection.reasons.includes('WARNINGS_REJECTED')) throw new Error('CLI2 warning scope ' + JSON.stringify({clean,warning}));
    }); requestDiagnosticCases++;
    await verifySources([{sourceId, text:wrap(model('F','IMPORTS Bad;'))}, {sourceId:'memory:/bad.ili',text:wrap(bad)}], 'rejected', ['F'], async (sessionId, compiled) => {
      if (!compiled.diagnostics.some(d => d.code === 'ILI-SEM-0001' && d.range?.source === 'memory:/bad.ili')) throw new Error('CLI2 imported cause lost');
    }); requestDiagnosticCases++;
    await verifySources([{sourceId,text:wrap(good)},{sourceId:'memory:/broken.ili',text:wrap(bad)+' GARBAGE'}], 'succeeded', ['Good']); requestDiagnosticCases++;
    await verifySources([{sourceId,text:wrap(model('F','TOPIC Office=CLASS Office=END Office;END Office;'))}], 'rejected', ['F'], async (sessionId, compiled) => {
      if (!compiled.diagnostics.some(d => d.code === 'ILI-SEM-0010' && d.arguments.some(a => a.name === 'rule' && a.value.endsWith('/SCOPE_OUTER_TYPE_NAME')) && d.range.revision === '1')) throw new Error('CLI2 namespace identity lost');
    }); requestDiagnosticCases++;
  }
  // A requested source remains relevant when recovery cannot build any model.
  for (const version of ['2.3', '2.4']) {
    const {sessionId} = await call('openSession');
    try {
      const sourceId = 'memory:/no-model-ä.ili';
      const updated = await call('updateSource', sessionId, {sourceId, expectation:{kind:'absent'}, change:{kind:'replace',text:'INTERLIS '+version+';\r\nGARBAGE\r\n'}});
      const compiled = await call('compile', sessionId, {expectedWorkspaceRevision:updated.workspaceRevision,roots:[{kind:'bySource',source:sourceId}]});
      if (compiled.outcome !== 'rejected' || !compiled.diagnostics.some(d=>d.code==='ILI-SYN-0004') || !compiled.rejection.diagnosticIds.length) throw new Error('CLI2 lost requested source syntax '+JSON.stringify(compiled));
    } finally { await call('closeSession', sessionId); }
    requestDiagnosticCases++;
  }
  // L4-1: duplicate-safe declaration continuity and the newly closed role rules.
  return requestDiagnosticCases;
}

async function declarationIdentityAndRoleRules({call, verify}) {
  let phase4Cases=0;
  for(const version of ['2.3','2.4']) {
    const wrap=body=>`INTERLIS ${version};MODEL F AT "https://example.org" VERSION "1"=${body}END F.`;
    for(const [body,expected] of [
      ['DOMAIN X=TEXT;TOPIC T=CLASS X=END X;END T;','rejected'],
      ['TOPIC T=CLASS T=END T;END T;','rejected'],
      ['TOPIC Base=STRUCTURE S=END S;END Base;TOPIC Child EXTENDS Base=STRUCTURE S=END S;END Child;','rejected'],
      ['DOMAIN X=TEXT;TOPIC Base=STRUCTURE S=value:F.X;END S;END Base;TOPIC Child EXTENDS Base=STRUCTURE S(EXTENDED)=other:BOOLEAN;END S;END Child;','succeeded']]) {
      await verify(wrap(body),expected);phase4Cases++;
    }
    for(const [issuer,expected] of [['x','succeeded'],['../models?x=1#v','succeeded'],['a'.repeat(1023),'succeeded'],['a b','rejected'],['%xy','rejected'],['https://[broken','rejected'],['https://example.invalid/ä','rejected'],['a'.repeat(1024),'rejected']]) {
      await verify(`INTERLIS ${version};MODEL F AT "${issuer}" VERSION "1"=END F.`,expected);phase4Cases++;
    }
    const declarations='DOMAIN E=(red,green,blue);STRUCTURE C=a:TEXT;END C;';
    const sourceId='memory:/identity-chain.ili';
    const {sessionId}=await call('openSession');
    try {
      let revision='0',oldIds,ambiguousIds,deletedIds;
      for(let step=1;step<=6;step++) {
        let body=declarations;
        if(step===2)body='!! comment\nDOMAIN Before=TEXT;'+body;
        if(step===3)body+='STRUCTURE C=b:BOOLEAN;END C;';
        if(step===5)body='DOMAIN E=(red,green,blue);';
        const text=wrap(body);
        const update=await call('updateSource',sessionId,{sourceId,expectation:step===1?{kind:'absent'}:{kind:'at',revision},change:{kind:'replace',text}});
        revision=update.sourceRevision;
        const compiled=await call('compile',sessionId,{expectedWorkspaceRevision:update.workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});
        if(compiled.outcome!==(step===3?'rejected':'succeeded'))throw new Error('L4 identity gate '+JSON.stringify(compiled));
        const ids=[];
        for(const token of ['MODEL F','E=','red','green','blue',...(step===5?[]:['C=','a:TEXT'])]) {
          const offset=text.indexOf(token)+(token==='MODEL F'?6:0);
          const found=await call('query',sessionId,{snapshotId:compiled.snapshotId,query:{kind:'symbolAt',sourceId,revision,startOffset:offset,endOffset:offset}});
          if(found.kind!=='known')throw new Error('Missing identity declaration '+token+' '+JSON.stringify(found));ids.push(found.symbolId);
        }
        if(step===2&&JSON.stringify(oldIds)!==JSON.stringify(ids))throw new Error('Unique enum/member identity was lost');
        if(step===3) {if(ids[5]===oldIds[5]||ids[6]===oldIds[6])throw new Error('Duplicate owner invented continuity');ambiguousIds=ids;}
        if(step===4) {if(ids[5]===ambiguousIds[5]||ids[6]===ambiguousIds[6])throw new Error('Ambiguous previous owner reused');deletedIds=ids;}
        if(step===6&&(ids[5]===deletedIds[5]||ids[6]===deletedIds[6]))throw new Error('Deleted/recreated declaration recycled identity');
        if(step===1)oldIds=ids;
      }
    } finally {await call('closeSession',sessionId);}phase4Cases++;
    const prefix='TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=END B;CLASS C=END C;';
    for(const [body,expected] of [
      ['ASSOCIATION Link=a-<>{1}A;b--{*}B;END Link;','succeeded'],
      ['ASSOCIATION Link=a-<>{1}A;b--{*}B;c--{*}C;END Link;','rejected'],
      ['ASSOCIATION Base=a--{1}A;b(ORDERED)--{*}B;END Base;ASSOCIATION Sub EXTENDS Base=a(EXTENDED)--{1}A;b(EXTENDED)--{*}B;END Sub;','rejected'],
      ['ASSOCIATION Base=a--{1}A;b(ORDERED)--{*}B;END Base;ASSOCIATION Sub EXTENDS Base=a(EXTENDED)--{1}A;b(EXTENDED,ORDERED)--{*}B;END Sub;','succeeded']]) {
      await verify(wrap(prefix+body+'END T;'),expected);phase4Cases++;
    }
    for(const [body,expected] of [
      ['DOMAIN E=(a,b) CIRCULAR;Next EXTENDS E=(c);','rejected'],
      ['DOMAIN E=(a,b);Next EXTENDS E=(c) CIRCULAR;','rejected'],
      ['DOMAIN E=(a,b:FINAL);Next EXTENDS E=(c);','rejected'],
      ['DOMAIN E=(a,b) ORDERED;Next EXTENDS E=(c);','succeeded']]) {
      await verify(wrap(body),expected);phase4Cases++;
    }
    await verify(wrap('TOPIC T=CLASS C=MANDATORY CONSTRAINT '+'('.repeat(150)+'#true'+')'.repeat(150)+';END C;END T;'),'cancelled');phase4Cases++;
    if(version==='2.3') {
      await verify(wrap('CLASS Outside=END Outside;TOPIC T=CLASS Local=END Local;ASSOCIATION Link=a--{1}Outside;b--{*}Local;END Link;END T;'),'succeeded');phase4Cases++;
    }
  }
  // L4-1: actual inherited member order and full role target identities in every backend.
  return phase4Cases;
}

async function semanticOverlay({call, verifySources}) {
  let semanticOverlayCases=0;
  for(const version of ['2.3','2.4']) {
    const sourceId='memory:/overlay.ili',text=`INTERLIS ${version};MODEL F AT "https://example.org" VERSION "1"=DOMAIN P=COORD 0..10,0..10;END F.`;
    await verifySources([{sourceId,text}],'succeeded',['F'],async(sessionId,plain)=>{
      const props=snapshotId=>call('query',sessionId,{snapshotId,query:{kind:'semanticProperties',sourceId,revision:'1',startOffset:text.indexOf('P=COORD'),endOffset:text.indexOf('P=COORD')}});
      const compile=revision=>call('compile',sessionId,{expectedWorkspaceRevision:revision,roots:[{kind:'byName',modelName:'F'}]});
      const config=async(overlays,expectedWorkspaceRevision)=>call('setConfiguration',sessionId,{expectedWorkspaceRevision,overlays});
      const entry=value=>({kind:'meta-attribute',id:'project',priority:10,target:{name:'F.P',modelVersion:'1'},attributes:{label:[value]}});
      if((await props(plain.snapshotId)).metadata.length!==0)throw new Error('Unconfigured metadata leak');
      let changed=await config([entry('first'),{kind:'crs',id:'project',priority:10,target:'F.P',crs:'EPSG:2056'}],'1');
      const first=await compile(changed.workspaceRevision);if(first.outcome!=='succeeded')throw new Error('Actual overlay rejected '+JSON.stringify(first));
      const properties=await props(first.snapshotId);
      if(properties.metadata.length!==2||!properties.metadata.some(v=>v.key==='label'&&v.text==='first'&&v.origin.kind==='overlay'&&v.origin.overlayId==='project')||!properties.metadata.some(v=>v.key==='CRS'&&v.text==='EPSG:2056'))throw new Error('Overlay not exposed '+JSON.stringify(properties));
      changed=await config([entry('second')],changed.workspaceRevision);const second=await compile(changed.workspaceRevision);
      if(second.outcome!=='succeeded'||first.snapshotId===second.snapshotId||(await props(second.snapshotId)).metadata[0].text!=='second')throw new Error('Overlay edit did not invalidate');
      if((await props(first.snapshotId)).metadata.find(v=>v.key==='label').text!=='first')throw new Error('Published snapshot mutated');
      changed=await config([entry('x'),entry('y')],changed.workspaceRevision);const conflict=await compile(changed.workspaceRevision);
      if(conflict.outcome!=='rejected'||!conflict.diagnostics.some(d=>d.code==='ILI-SEM-0011'))throw new Error('Overlay conflict accepted');
      changed=await config([],changed.workspaceRevision);const clear=await compile(changed.workspaceRevision);
      if(clear.outcome!=='succeeded'||(await props(clear.snapshotId)).metadata.length!==0)throw new Error('Cleared overlay leaked');
    });semanticOverlayCases++;
    const model=(name,body)=>`INTERLIS ${version};MODEL ${name} AT "https://example.org" VERSION "1"=${body}END ${name}.`;
    const sources=[{sourceId:'memory:/f.ili',text:model('F','IMPORTS Leaf;DOMAIN Point=COORD 0..10,0..10;')},
      {sourceId:'memory:/original.ili',text:model('Original','IMPORTS Leaf;DOMAIN P=COORD 0..10,0..10;')},
      {sourceId:'memory:/leaf.ili',text:model('Leaf','DOMAIN N=0..10;')}];
    await verifySources(sources,'succeeded',['F'],async(sessionId,plain)=>{
      const changed=await call('setConfiguration',sessionId,{expectedWorkspaceRevision:'3',overlays:[{kind:'translation',id:'translations',priority:1,target:'F',original:'Original'}]});
      const result=await call('compile',sessionId,{expectedWorkspaceRevision:changed.workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});
      if(result.outcome!=='succeeded')throw new Error('External translation original closure failed '+JSON.stringify(result));
      const properties=await call('query',sessionId,{snapshotId:result.snapshotId,query:{kind:'semanticProperties',sourceId:'memory:/f.ili',revision:'1',startOffset:sources[0].text.indexOf('MODEL F')+6,endOffset:sources[0].text.indexOf('MODEL F')+6}});
      if(properties.translation?.sourceModel!=='Original'||properties.translation.origin?.overlayId!=='translations'||properties.translation.correspondences.length<3)throw new Error('External translation graph lost '+JSON.stringify(properties));
      const old=await call('query',sessionId,{snapshotId:plain.snapshotId,query:{kind:'semanticProperties',sourceId:'memory:/f.ili',revision:'1',startOffset:sources[0].text.indexOf('MODEL F')+6,endOffset:sources[0].text.indexOf('MODEL F')+6}});
      if(old.translation)throw new Error('Translation leaked into old snapshot');
    });semanticOverlayCases++;
  }
  // L2-3: source abstraction and cross-feature import/translation identities.
  return semanticOverlayCases;
}

async function numericResourceBudget({call, verifySources}) {
  const e5NumericBudgetCases = [{"name":"numeric-budget-2.3-8","text":"INTERLIS 2.3;MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=CONSTRAINT >= 1e100001% #true;END C;END T;END F.","expected":"cancelled"},{"name":"numeric-budget-2.3-9","text":"INTERLIS 2.3;MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=CONSTRAINT >= 1e-100001% #true;END C;END T;END F.","expected":"cancelled"},{"name":"numeric-budget-2.3-4097","text":"INTERLIS 2.3;MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=CONSTRAINT >= 11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111% #true;END C;END T;END F.","expected":"cancelled"},{"name":"numeric-budget-2.4-8","text":"INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=CONSTRAINT >= 1e100001% #true;END C;END T;END F.","expected":"cancelled"},{"name":"numeric-budget-2.4-9","text":"INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=CONSTRAINT >= 1e-100001% #true;END C;END T;END F.","expected":"cancelled"},{"name":"numeric-budget-2.4-4097","text":"INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=CONSTRAINT >= 11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111% #true;END C;END T;END F.","expected":"cancelled"}];
  for(const c of e5NumericBudgetCases) await verifySources([{sourceId:'memory:/numeric-budget.ili',text:c.text}],'cancelled',['F'],async(sessionId,compiled)=>{
    if(compiled.cancellation?.reason!=='RESOURCE_LIMIT')throw new Error('Uncontrolled numeric limit '+JSON.stringify(compiled));
    const updated=await call('updateSource',sessionId,{sourceId:'memory:/numeric-budget.ili',expectation:{kind:'at',revision:'1'},change:{kind:'replace',text:'INTERLIS 2.4;MODEL F AT "https://example.org" VERSION "1"=DOMAIN D=0..10;END F.'}});
    const recovered=await call('compile',sessionId,{expectedWorkspaceRevision:updated.workspaceRevision,roots:[{kind:'byName',modelName:'F'}]});
    if(recovered.outcome!=='succeeded')throw new Error('Numeric budget poisoned session '+JSON.stringify(recovered));
  });
  return e5NumericBudgetCases.length;
}

module.exports = {
  'request-diagnostic-scope': requestDiagnosticScope,
  'declaration-identity-and-role-rules': declarationIdentityAndRoleRules,
  'semantic-overlay': semanticOverlay,
  'numeric-resource-budget': numericResourceBudget,
};
