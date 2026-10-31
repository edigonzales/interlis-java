'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');const {validate,validatePreflight,definitions,browser}=require('./evidence.cjs');
function fixture(){
 const candidate='1'.repeat(40),sourceFingerprint='2'.repeat(64),sha='3'.repeat(64),currentArtifacts={};
 const files=new Set([...Object.values(definitions).flatMap(d=>d.artifacts),...browser,'interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js','interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js.wasm']);for(const file of files)currentArtifacts[file]=sha;
 const bound=(files,status='passed')=>({status,gitCommit:candidate,dirty:false,sourceFingerprint,artifacts:Object.fromEntries(files.map(f=>[f,sha]))});
 const suite={stabilizationCases:3548,corpusRegressionCases:24};const transport={...suite,transportChecks:29,clientContractChecks:7,exactDecimal:true,workerRestartOutcome:'succeeded'};
 const proofs={};for(const [name,d]of Object.entries(definitions))proofs[name]={...bound(d.artifacts,d.status),...suite,tests:d.minimum||1,failures:0,errors:0,skipped:0};
 const names=['chromium','firefox','webkit'];proofs.browsers=names.map(browserName=>({...bound(browser),browser:browserName,browserVersion:'fixture',externalRequests:[],result:{...transport}}));
 proofs.nativeBrowsers=names.map(browserName=>({...bound(['interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js','interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js.wasm']),browser:browserName,browserVersion:'fixture',runtime:'graalvm-web-image',result:{...suite,outcome:'succeeded'}}));
 proofs.safari={...bound(browser),browser:'Safari',driverVersion:'fixture',result:{...transport}};
 proofs.ui=names.map(browserName=>({...bound([browser[0],browser[1],browser[3]]),browser:browserName,observations:Array(5).fill('actual observation')}));
 const versions={version:'0.2.0-SNAPSHOT',apiVersion:'0.2',protocol:'interlis.compiler/1.0',diagnosticCatalogVersion:'interlis-diagnostics/v1',formatVersion:'1.0',diagramSchemaVersion:'1.0'};
 const sdk={status:'passed',archiveSha256:sha,versions};
 const platforms=[['Linux','amd64'],['Windows Server 2025','amd64'],['Mac OS X','aarch64']].map(([os,arch])=>({profile:'P3-jvm-distribution-v1',status:'passed',gitCommit:candidate,dirty:false,sourceFingerprint,java:'25.0.3+9',os,arch,tests:1186,suites:223,failures:0,errors:0,skipped:0,xmlHashes:Object.fromEntries(Array.from({length:223},(_,i)=>['suite'+i,sha])),sdkCommands:9,legacyCases:54,sdkArchiveSha256:sha}));
 return {candidate,sourceFingerprint,currentArtifacts,proofs,platforms,sdk,github:{profile:'P3',repository:'edigonzales/interlis-java',gitCommit:candidate,run:{headSha:candidate,status:'completed',conclusion:'success',path:'.github/workflows/p3-platforms.yml'},jobs:[{status:'completed',conclusion:'success'},{status:'completed',conclusion:'success'}],artifacts:[{platform:'linux-x64',digest:'sha256:'+sha,proof:platforms[0]},{platform:'windows-x64',digest:'sha256:'+sha,proof:platforms[1]}]},performance:{status:'passed',gitCommit:candidate,dirty:false,sourceFingerprint,roots:347,runtimes:6,realCorpusEdits:2000,skipped:0,semanticMismatches:0},conformance:{status:'passed',gitCommit:candidate,dirty:false,sourceFingerprint,summary:{summary:{PASS:577,XFAIL:16,FAIL:0,XPASS:0,ERROR:0,TOTAL:593},normativeAssessments:Array(21).fill({})}},reproducibility:{status:'passed',candidate,sourceFingerprint,cleanBuilds:2,shippedFiles:31,artifacts:Object.fromEntries(Array.from({length:31},(_,i)=>['file'+i,{sha256:sha}]))},browserManifest:{schemaVersion:'1.0',gitCommit:candidate,dirty:false,sourceFingerprint,toolchainPatch:{id:'teavm-0.16.0-ordered-class-source-v1',originalCoreSha256:'c94539912c1b951fa741aecfcb6b4acad75c2e2360dc8859b1edd0c44175608a',compiledCoreSha256:sha},resources:Object.fromEntries(Array.from({length:21},(_,i)=>['file'+i,{sha256:sha}])),versions:{...versions}},guard:{status:'running',gitHead:candidate,dirty:'',guardPid:123,active:true,probes:[{command:'/fixture/interpreter',error:'EPERM',status:null}]}};
}
test('complete current P3 candidate evidence validates',()=>assert.equal(validate(fixture()).status,'passed'));
test('real guard combines unavailable PATH names with absolute OS execution denials',()=>{
 const f=fixture();f.guard.probes.unshift({command:'absent-command',status:null,error:'ENOENT'});
 assert.equal(validate(f).status,'passed');assert.equal(validatePreflight(f.candidate,f.sourceFingerprint,f.github,f.guard).status,'passed');
});
test('preflight accepts current CI and active guard before runtime results exist',()=>{
 const f=fixture();assert.equal(validatePreflight(f.candidate,f.sourceFingerprint,f.github,f.guard).status,'passed');
});
for(const [name,mutate]of [
 ['previous candidate',f=>f.github.gitCommit='old'],['stale CI sources',f=>f.github.artifacts[0].proof.sourceFingerprint='old'],
 ['missing Windows evidence',f=>f.github.artifacts.pop()],['old finished wrapper',f=>f.guard.status='passed'],['inactive wrapper',f=>f.guard.active=false]
])test('preflight rejects '+name,()=>{const f=fixture();mutate(f);assert.throws(()=>validatePreflight(f.candidate,f.sourceFingerprint,f.github,f.guard));});
for(const [name,mutate]of [
 ['missing Node proof',f=>delete f.proofs.js],['stale source',f=>f.proofs.wasm.sourceFingerprint='old'],['foreign commit',f=>f.proofs.native.gitCommit='9'.repeat(40)],
 ['dirty proof',f=>f.proofs.client.dirty=true],['foreign module',f=>f.proofs.js.artifacts[Object.keys(f.proofs.js.artifacts)[0]]='other'],['missing module',f=>delete f.proofs.wasm.artifacts[Object.keys(f.proofs.wasm.artifacts)[0]]],
 ['missing extra regressions',f=>delete f.proofs.js.corpusRegressionCases],['incomplete original suite',f=>f.proofs.native.stabilizationCases=3547],['skipped Node case',f=>f.proofs.diagram.skipped=1],
 ['missing browser',f=>f.proofs.browsers.pop()],['fake Safari from WebKit',f=>f.proofs.safari.browser='webkit'],['missing actual version',f=>delete f.proofs.browsers[0].browserVersion],
 ['failed native browser',f=>f.proofs.nativeBrowsers[1].status='failed'],['old transport',f=>f.proofs.safari.result.transportChecks=20],['missing late layout UI',f=>f.proofs.ui[0].observations=[]],
 ['historical thirteen-root performance',f=>f.performance.roots=13],['partial edits',f=>f.performance.realCorpusEdits=1000],['semantic mismatch',f=>f.performance.semanticMismatches=1],
 ['unexpected conformance result',f=>f.conformance.summary.summary.FAIL=1],['removed normative control',f=>f.conformance.summary.normativeAssessments.pop()],
 ['missing Windows',f=>f.platforms.splice(1,1)],['wrong architecture',f=>f.platforms[1].arch='aarch64'],['skipped JVM',f=>f.platforms[0].skipped=1],['incomplete SDK processes',f=>f.platforms[0].sdkCommands=8],
 ['foreign workflow',f=>f.github.repository='other/project'],['failed workflow',f=>f.github.run.conclusion='failure'],['failed job',f=>f.github.jobs[0].conclusion='failure'],['unhashed artifact',f=>f.github.artifacts[0].digest=''],
 ['inconsistent SDK version',f=>f.sdk.versions.apiVersion='0.1'],['different platform package',f=>f.platforms[2].sdkArchiveSha256='old'],['single build',f=>f.reproducibility.cleanBuilds=1],['empty repro comparison',f=>f.reproducibility.artifacts={}],
 ['stale browser manifest',f=>f.browserManifest.gitCommit='old'],['unshipped version mismatch',f=>f.browserManifest.versions.protocol='other'],
 ['unbound compiler tool',f=>delete f.browserManifest.toolchainPatch],['foreign compiler tool',f=>f.browserManifest.toolchainPatch.originalCoreSha256='other'],
 ['old completed guard',f=>f.guard.status='passed'],['dead guard',f=>f.guard.active=false],['unproved execution denial',f=>f.guard.probes=[]],['successful interpreter',f=>f.guard.probes[0].status=0],
 ['absolute absence without execution denial',f=>f.guard.probes[0].error='ENOENT'],['unexpected PATH execution error',f=>f.guard.probes.push({command:'unavailable',status:null,error:'EACCES'})]
])test('P3 fails closed: '+name,()=>{const f=fixture();mutate(f);assert.throws(()=>validate(f));});
