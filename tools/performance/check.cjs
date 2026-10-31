'use strict';
const fs=require('node:fs'),path=require('node:path');const {root,hash,fingerprint}=require('./profile.cjs');
const assert=(condition,message)=>{if(!condition)throw Error(message);};
const canonical=value=>Array.isArray(value)?value.map(canonical):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(k=>[k,canonical(value[k])])):value;
const equal=(a,b)=>JSON.stringify(canonical(a))===JSON.stringify(canonical(b));
function evaluate({assessment,budgets,manifestSha256,sourceFingerprint,reports,editors,artifacts,observation,machine,productCommit}){
 assert(typeof productCommit==='string'&&productCommit.length>0,'missing source commit');
 assert(machine&&machine.os===budgets.referenceMachine.os&&machine.arch===budgets.referenceMachine.arch&&machine.cpu===budgets.referenceMachine.cpu&&machine.logicalCpus===budgets.referenceMachine.logicalCpus&&machine.memoryBytes===budgets.referenceMachine.memoryBytes,'wrong reference hardware');
 assert(assessment.manifestSha256===manifestSha256&&assessment.roots.length===347,'missing/stale assessment');
 assert(equal(assessment.summary,{roots:347,succeeded:262,rejected:85})&&assessment.roots.filter(r=>r.outcome==='succeeded').length===262&&assessment.roots.filter(r=>r.outcome==='rejected').length===85,'changed assessed corpus profile');
 const ruleIds=new Set(assessment.rules.map(r=>r.id));for(const row of assessment.roots)assert(row.outcome==='succeeded'?row.assessedRules.length===0:row.assessedRules.length>0&&row.assessedRules.every(r=>ruleIds.has(r))&&row.diagnostics.length>0,'unassessed negative expectation');
 assert(budgets.corpus.roots===347&&budgets.corpus.files===366&&budgets.corpus.allowedSkips===0,'changed corpus budget profile');
 assert(reports.length===6&&equal(reports.map(r=>r.target).sort(),['chromium','firefox','jvm','safari','wasm','webkit']),'missing required runtime');
 const jvm=reports.find(r=>r.target==='jvm');
 for(const report of reports){
  if(report.target==='jvm')assert(report.java.includes('25.0.3')&&report.arch==='aarch64','unexpected JVM runtime');else assert(report.node==='v24.22.0'&&report.os==='darwin'&&report.arch==='arm64','unexpected portable host runtime');
  if(!['jvm','wasm'].includes(report.target))assert(typeof report.browserVersion==='string'&&report.browserVersion.length>0,'missing browser version');
  assert(report.gitCommit===productCommit&&typeof report.dirty==='boolean','foreign/missing runtime source commit');
  assert(report.sourceFingerprint===sourceFingerprint&&report.manifestSha256===manifestSha256,'stale runtime '+report.target);
  assert(report.captureStatus==='complete'&&!report.error&&report.roots.length===347,'incomplete runtime '+report.target);
  assert((report.externalRequests||[]).length===0,'external browser requests');
  const runtime=budgets.runtime[report.target==='jvm'?'jvm':report.target==='wasm'?'wasm':'browser'];
  for(let i=0;i<347;i++){
   const expected=assessment.roots[i],row=report.roots[i],compiled=row.semantic.compile;
   assert(row.model===expected.model&&row.version===expected.version&&row.file===expected.file&&row.outcome===expected.outcome,'unexpected root outcome '+row.model);
   assert(equal(row.semantic,jvm.roots[i].semantic),'runtime semantic mismatch '+report.target+'/'+row.model);
   if(expected.outcome==='rejected'){
    assert(equal(compiled.rejection.reasons,expected.reasons)&&equal(compiled.rejection.diagnosticIds,expected.diagnosticIds),'changed gate reasons/IDs '+row.model);
    assert(equal(compiled.diagnostics.filter(d=>compiled.rejection.diagnosticIds.includes(d.id)),expected.diagnostics),'changed rejection diagnostic vector '+row.model);
   }
   assert(row.semantic.diagram.completeness!=='LIMITED','reduced corpus projection');
   assert(Number.isFinite(row.firstCompileMs)&&row.firstCompileMs>=0&&row.firstCompileMs<=runtime.maxFirstCompileMs,'first compile budget '+report.target+'/'+row.model);
   assert(Number.isFinite(row.openMs)&&row.openMs>=0&&row.openMs<=runtime.maxOpenMs,'opening budget');
   assert(row.warmCompileMs.length===3&&row.warmCompileMs.every(ms=>Number.isFinite(ms)&&ms>=0&&ms<=runtime.maxWarmCompileMs),'warm compile budget '+report.target+'/'+row.model);
  }
  if(report.target!=='jvm')assert(report.wasmSha256===artifacts.wasm.sha256,'stale/different compiler module');
  if(report.target==='jvm')assert(report.coldProcessMs>0&&report.coldProcessMs<=runtime.maxColdProcessMs&&report.peakHeapBytes>0&&report.peakHeapBytes<=runtime.maxPeakHeapBytes,'JVM cold/memory budget');
  else assert(report.coldLoadMs>=0&&report.coldLoadMs<=runtime.maxColdLoadMs,'cold module budget');
  if(report.target==='wasm')assert(report.maxRssBytes>0&&report.maxRssBytes<=runtime.maxRssBytes,'Wasm RSS budget');
 }
 assert(editors.length===2&&equal(editors.map(e=>e.target).sort(),['jvm','wasm']),'missing long session target');
 for(const editor of editors){
  assert(editor.gitCommit===productCommit&&typeof editor.dirty==='boolean','foreign/missing editor source commit');
  assert(editor.sourceFingerprint===sourceFingerprint&&editor.manifestSha256===manifestSha256&&editor.captureStatus==='complete','stale/incomplete editor');
  assert(editor.edits===1000&&editor.samplesMs.length===1000&&editor.closureUtf16>=200000,'incomplete real corpus edit sequence');
  const sorted=[...editor.samplesMs].sort((a,b)=>a-b);assert(sorted.every(n=>Number.isFinite(n)&&n>=0),'invalid edit measurements');
  assert(editor.p95Ms===sorted[949]&&editor.maxMs===sorted.at(-1),'invalid edit statistics');
  assert(editor.p95Ms<=(editor.target==='jvm'?budgets.editor.jvmP95Ms:budgets.editor.portableP95Ms)&&editor.maxMs<=budgets.editor.maxEditMs,'editor timing budget');
  assert(editor.maxRetainedSnapshots<=4&&editor.releasedDuringEdits===996&&editor.staleRevisionRejections===1000&&editor.staleHandleAcceptances===0,'editor retention/stale contract');
  if(editor.target==='jvm')assert(editor.recoveredAfterAbort&&editor.abortCheckpoints>1000&&editor.abortMs>=0,'missing real cooperative abort and recovery');
  else assert(editor.maxRssBytes>0&&editor.maxRssBytes<=budgets.runtime.wasm.maxRssBytes,'long Wasm memory budget');
 }
 assert(artifacts.captureStatus==='complete'&&artifacts.sourceFingerprint===sourceFingerprint&&artifacts.files.length>20,'missing/stale assets');
 assert(new Set(artifacts.files.map(r=>r.path)).size===artifacts.files.length&&artifacts.files.every(r=>typeof r.sha256==='string'&&r.rawBytes>0&&r.gzipBytes>0)&&artifacts.totalRawBytes===artifacts.files.reduce((n,r)=>n+r.rawBytes,0)&&artifacts.totalGzipBytes===artifacts.files.reduce((n,r)=>n+r.gzipBytes,0),'incomplete asset measurements');
 assert(artifacts.wasm.rawBytes>0&&artifacts.wasm.gzipBytes>0&&artifacts.wasm.rawBytes<=budgets.artifacts.wasmRawBytes&&artifacts.wasm.gzipBytes<=budgets.artifacts.wasmGzipBytes&&artifacts.totalRawBytes<=budgets.artifacts.browserPayloadRawBytes&&artifacts.totalGzipBytes<=budgets.artifacts.browserPayloadGzipBytes,'artifact size budget');
 assert(observation.status==='passed'&&observation.sourceFingerprint===sourceFingerprint&&observation.compilerRuns===1000&&observation.queueCapacity===32&&observation.retained===32&&observation.offered>32&&observation.dropped===observation.offered-32&&observation.secondInstanceUnaffected,'missing/inconsistent observation pressure');
 return {status:'passed',gitCommit:productCommit,dirty:reports.some(r=>r.dirty)||editors.some(r=>r.dirty),sourceFingerprint,manifestSha256,roots:347,runtimes:6,realCorpusEdits:2000,skipped:0,semanticMismatches:0};
}
function main(){
 const directory=path.join(root,'build/reports/performance/full-corpus'),read=p=>JSON.parse(fs.readFileSync(p,'utf8')),output=path.join(directory,'result.json');fs.rmSync(output,{force:true});
 const assessment=read(path.join(root,'docs/evidence/p3/full-corpus/assessment.json')),budgets=read(path.join(root,'docs/evidence/p3/full-corpus/budgets.json'));
 for(const rule of assessment.rules){assert(hash(fs.readFileSync(path.join(root,rule.control.source)))===rule.control.sourceSha256,'stale assessed control '+rule.id);}
 const reports=['jvm','wasm','chromium','firefox','webkit','safari'].map(target=>({...read(path.join(directory,target+'.json')),target}));
 const editors=['jvm','wasm'].map(target=>({...read(path.join(directory,'editor-'+target+'.json')),target}));
 const result=evaluate({assessment,budgets,manifestSha256:hash(fs.readFileSync(path.join(root,'docs/evidence/p3/full-corpus/manifest.json'))),sourceFingerprint:fingerprint(),productCommit:require('./profile.cjs').commitInfo().gitCommit,machine:{os:process.platform,arch:process.arch,cpu:require('node:os').cpus()[0].model,logicalCpus:require('node:os').cpus().length,memoryBytes:require('node:os').totalmem()},reports,editors,artifacts:read(path.join(directory,'artifacts.json')),observation:read(path.join(directory,'observation-pressure.json'))});
 fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n');console.log('FULL_CORPUS_PERFORMANCE_PASS',JSON.stringify(result));
}
module.exports={evaluate};if(require.main===module)try{main();}catch(error){console.error('FULL_CORPUS_PERFORMANCE_FAIL',error);process.exitCode=1;}
