'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');const {evaluate}=require('./check.cjs');
function fixture(){
 const assessment=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../docs/evidence/p3/full-corpus/assessment.json'))),budgets=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../docs/evidence/p3/full-corpus/budgets.json')));
 const sourceFingerprint='fixture-product',manifestSha256=assessment.manifestSha256;
 const roots=assessment.roots.map(r=>({...r,openMs:1,firstCompileMs:2,warmCompileMs:[1,1,1],semantic:{compile:r.outcome==='rejected'?{rejection:{reasons:r.reasons,diagnosticIds:r.diagnosticIds},diagnostics:r.diagnostics}:{outcome:'succeeded'},diagram:{completeness:'COMPLETE'},properties:[]}}));
 const reports=['jvm','wasm','chromium','firefox','webkit','safari'].map(target=>({gitCommit:'fixture-commit',dirty:false,target,java:'25.0.3',node:'v24.22.0',os:'darwin',arch:target==='jvm'?'aarch64':'arm64',browserVersion:'fixture-version',sourceFingerprint,manifestSha256,captureStatus:'complete',wasmSha256:'fixture-module',roots:structuredClone(roots),coldProcessMs:1,peakHeapBytes:1000,coldLoadMs:1,maxRssBytes:1000,externalRequests:[]}));
 const editors=['jvm','wasm'].map(target=>({gitCommit:'fixture-commit',dirty:false,target,sourceFingerprint,manifestSha256,captureStatus:'complete',edits:1000,samplesMs:Array(1000).fill(1),closureUtf16:247130,p95Ms:1,maxMs:1,maxRetainedSnapshots:4,releasedDuringEdits:996,staleRevisionRejections:1000,staleHandleAcceptances:0,recoveredAfterAbort:true,abortCheckpoints:1001,abortMs:1,maxRssBytes:1000}));
 return {productCommit:'fixture-commit',machine:structuredClone(budgets.referenceMachine),assessment,budgets,sourceFingerprint,manifestSha256,reports,editors,artifacts:{captureStatus:'complete',sourceFingerprint,files:Array.from({length:30},(_,i)=>({path:'asset-'+i,sha256:'fixture-sha',rawBytes:1000,gzipBytes:500})),wasm:{sha256:'fixture-module',rawBytes:1000,gzipBytes:500},totalRawBytes:30000,totalGzipBytes:15000},observation:{status:'passed',sourceFingerprint,compilerRuns:1000,queueCapacity:32,retained:32,offered:10000,dropped:9968,secondInstanceUnaffected:true}};
}
test('complete exact six-runtime profile and two real edit sequences satisfy checker',()=>assert.equal(evaluate(fixture()).status,'passed'));
for(const [name,mutate]of [
 ['foreign source commit',f=>f.reports[0].gitCommit='other'],['missing commit state',f=>delete f.editors[1].dirty],['wrong hardware',f=>f.machine.memoryBytes=8000000000],['missing Safari',f=>f.reports.pop()],['historical thirteen roots',f=>f.reports[1].roots=f.reports[1].roots.slice(0,13)],
 ['stale source binding',f=>f.reports[2].sourceFingerprint='old'],['different module',f=>f.reports[3].wasmSha256='debug'],
 ['partial capture',f=>f.reports[4].captureStatus='running'],['runtime trap',f=>f.reports[5].error='RangeError'],
 ['changed outcome',f=>f.reports[0].roots[0].outcome='rejected'],['missing gate diagnosis',f=>{const row=f.reports[0].roots.find(r=>r.outcome==='rejected');row.semantic.compile.diagnostics.pop();}],
 ['warm samples omitted',f=>f.reports[0].roots[0].warmCompileMs=[]],['unreliable timing',f=>f.reports[0].roots[0].firstCompileMs=NaN],
 ['slow compile',f=>f.reports[0].roots[0].firstCompileMs=6000],['oversized module',f=>f.artifacts.wasm.rawBytes=99999999],
 ['incomplete long session',f=>f.editors[0].edits=999],['incorrect percentile',f=>f.editors[0].p95Ms=0],
 ['stale handle accepted',f=>f.editors[1].staleHandleAcceptances=1],['retention overflow',f=>f.editors[1].maxRetainedSnapshots=5],
 ['abort not exercised',f=>f.editors[0].abortCheckpoints=0],['unaccounted events',f=>f.observation.dropped--],
 ['observer interference',f=>f.observation.secondInstanceUnaffected=false],['stale assets',f=>f.artifacts.sourceFingerprint='old']
])test('checker fails closed: '+name,()=>{const f=fixture();mutate(f);assert.throws(()=>evaluate(f));});
