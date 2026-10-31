'use strict';
const fs=require('node:fs'),path=require('node:path');const {root,hash,fingerprint,commitInfo}=require('../performance/profile.cjs');
const assert=(condition,message)=>{if(!condition)throw Error(message);};
const equal=(a,b)=>JSON.stringify(Object.keys(a).sort().map(k=>[k,a[k]]))===JSON.stringify(Object.keys(b).sort().map(k=>[k,b[k]]));
const js='interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js',wasm='interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm',runtime=wasm.replace('.wasm','.wasm-runtime.js');
const browser=['interlis-wasm-teavm/build/web/build-manifest.json','interlis-wasm-teavm/build/web/interlis-compiler.wasm','interlis-wasm-teavm/build/web/interlis-compiler.wasm-runtime.js','interlis-wasm-teavm/build/diagram-adapter.cjs'];
const definitions={
 js:{file:'interlis-wasm-teavm/build/js-contract-result.json',artifacts:[js],status:'passed',suite:true},
 wasm:{file:'interlis-wasm-teavm/build/wasm-contract-result.json',artifacts:[wasm,runtime],status:'passed',suite:true},
 native:{file:'interlis-wasm-webimage/build/webimage/result.json',artifacts:['interlis-wasm-webimage/build/webimage/interlis-compiler.js','interlis-wasm-webimage/build/webimage/interlis-compiler.js.wasm'],status:'ok',suite:true},
 client:{file:'interlis-wasm-teavm/build/node-client-result.json',artifacts:[js,'interlis-wasm-teavm/build/compiler-client.cjs','interlis-wasm-teavm/build/diagram-adapter.cjs'],status:'passed',minimum:18},
 diagram:{file:'interlis-wasm-teavm/build/node-diagram-result.json',artifacts:[js,'interlis-wasm-teavm/build/compiler-client.cjs','interlis-wasm-teavm/build/diagram-adapter.cjs'],status:'passed',minimum:4}
};
function activeGuard(guard,candidate){
 return guard.status==='running'&&guard.gitHead===candidate&&guard.dirty.trim()===''&&guard.active===true&&Number.isInteger(guard.guardPid)&&guard.guardPid>0&&guard.probes.length>0
  &&guard.probes.every(p=>p.status!==0&&(p.error==='EPERM'||p.status===126||(!path.isAbsolute(p.command)&&p.error==='ENOENT')))
  &&guard.probes.some(p=>path.isAbsolute(p.command)&&p.error==='EPERM');
}
/** Preconditions deliberately exclude runtime results produced by this invocation. */
function validatePreflight(candidate,sourceFingerprint,github,guard){
 assert(/^[a-f0-9]{40}$/.test(candidate)&&/^[a-f0-9]{64}$/.test(sourceFingerprint),'missing exact candidate binding');
 assert(github.gitCommit===candidate&&github.run.headSha===candidate&&github.run.status==='completed'&&github.run.conclusion==='success'&&github.run.path==='.github/workflows/p3-platforms.yml','collect successful current-candidate GitHub evidence before releaseCheckP3');
 assert(github.artifacts.length===2&&github.artifacts.map(a=>a.platform).sort().join(',')==='linux-x64,windows-x64'&&github.artifacts.every(a=>a.proof.gitCommit===candidate&&a.proof.dirty===false&&a.proof.sourceFingerprint===sourceFingerprint&&a.proof.status==='passed'),'foreign/incomplete platform preflight');
 assert(activeGuard(guard,candidate),'run releaseCheckP3 through the active OS interpreter guard');
 return {status:'passed',gitCommit:candidate};
}
function read(file){return JSON.parse(fs.readFileSync(path.join(root,file),'utf8'));}
function githubEvidence(){
 const github=read('build/reports/p3/platform-evidence/result.json');
 for(const a of github.artifacts){
  assert('sha256:'+hash(fs.readFileSync(path.resolve(root,a.zip)))===a.digest,'changed downloaded artifact');
  assert(hash(fs.readFileSync(path.resolve(root,a.report)))===a.reportSha256,'changed platform report');
  assert(equal(read(a.report),a.proof),'embedded platform proof differs from verified report');
 }
 return github;
}
function guardEvidence(){
 const guard=read('build/reports/python-free/environment.json');
 try{process.kill(guard.guardPid,0);guard.active=fs.statSync(guard.path).isDirectory();}catch{guard.active=false;}
 assert(hash(guard.profile)===guard.profileSha256,'changed OS guard profile');return guard;
}
function preflight(){
 fs.rmSync(path.join(root,'build/reports/p3/release/result.json'),{force:true});
 const candidate=commitInfo();assert(!candidate.dirty,'P3 release candidate must be clean');
 validatePreflight(candidate.gitCommit,fingerprint(),githubEvidence(),guardEvidence());
 console.log('P3_RELEASE_PREFLIGHT_PASS',candidate.gitCommit);
}
function validate(input){
 const {candidate,sourceFingerprint,currentArtifacts,proofs,platforms,github,performance,conformance,sdk,reproducibility,browserManifest,guard}=input;
 assert(/^[a-f0-9]{40}$/.test(candidate)&&/^[a-f0-9]{64}$/.test(sourceFingerprint),'missing exact candidate binding');
 const bound=(report,files,status='passed')=>{assert(report&&report.status===status&&report.gitCommit===candidate&&report.dirty===false&&report.sourceFingerprint===sourceFingerprint,'stale/dirty/unsuccessful proof');assert(equal(report.artifacts||{},Object.fromEntries(files.map(f=>[f,currentArtifacts[f]])))&&files.every(f=>/^[a-f0-9]{64}$/.test(currentArtifacts[f]||'')),'foreign/missing runtime artifact');};
 const suite=r=>assert(r.stabilizationCases===3548&&r.corpusRegressionCases===24,'incomplete semantic regressions');
 for(const [name,definition]of Object.entries(definitions)){const r=proofs[name];bound(r,definition.artifacts,definition.status);if(definition.suite)suite(r);else assert(r.tests>=definition.minimum&&r.failures===0&&r.errors===0&&r.skipped===0,'incomplete/skipped Node contracts');}
 const names=['chromium','firefox','webkit'];
 for(const [rows,files,isNative]of [[proofs.browsers,browser,false],[proofs.nativeBrowsers,['interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js','interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js.wasm'],true]]){
  assert(Array.isArray(rows)&&rows.length===3&&rows.map(r=>r.browser).sort().join(',')===names.join(','),'missing actual browser');
  for(const row of rows){bound(row,files);assert(typeof row.browserVersion==='string'&&row.browserVersion.length>0,'missing actual browser version');suite(row.result);if(isNative)assert(row.result.outcome==='succeeded'&&row.runtime==='graalvm-web-image','invalid native browser');else assert(row.result.transportChecks===29&&row.result.clientContractChecks===7&&row.result.exactDecimal===true&&row.result.workerRestartOutcome==='succeeded'&&row.externalRequests.length===0,'incomplete real client/transport contract');}
 }
 bound(proofs.safari,browser);assert(proofs.safari.browser==='Safari'&&typeof proofs.safari.driverVersion==='string','missing actual Safari');suite(proofs.safari.result);assert(proofs.safari.result.transportChecks===29&&proofs.safari.result.clientContractChecks===7,'incomplete Safari client');
 assert(proofs.ui.length===3&&proofs.ui.map(r=>r.browser).sort().join(',')===names.join(','),'missing real UI browser');for(const row of proofs.ui){bound(row,[browser[0],browser[1],browser[3]]);assert(row.observations.length>=5,'missing actual UI observations');}
 assert(performance.status==='passed'&&performance.gitCommit===candidate&&performance.dirty===false&&performance.sourceFingerprint===sourceFingerprint&&performance.roots===347&&performance.runtimes===6&&performance.realCorpusEdits===2000&&performance.skipped===0&&performance.semanticMismatches===0,'missing current complete performance profile');
 assert(conformance.status==='passed'&&conformance.gitCommit===candidate&&!conformance.dirty&&conformance.sourceFingerprint===sourceFingerprint,'stale conformance');const counts=conformance.summary.summary;assert(counts.PASS===577&&counts.XFAIL===16&&counts.FAIL===0&&counts.XPASS===0&&counts.ERROR===0&&counts.TOTAL===593&&conformance.summary.normativeAssessments.length===21,'changed/incomplete conformance contract');
 assert(platforms.length===3,'missing JVM target');const targets=new Set();for(const p of platforms){assert(p.status==='passed'&&p.profile==='P3-jvm-distribution-v1'&&p.gitCommit===candidate&&!p.dirty&&p.sourceFingerprint===sourceFingerprint,'stale/foreign platform');assert(p.java.startsWith('25.0.3')&&p.tests>=1186&&p.suites>=223&&p.failures===0&&p.errors===0&&p.skipped===0&&Object.keys(p.xmlHashes).length>=223&&p.sdkCommands===9&&p.legacyCases>=50,'incomplete actual JVM/distribution processes');const target=p.os.startsWith('Windows')&&['amd64','x86_64'].includes(p.arch)?'windows':p.os==='Linux'&&['amd64','x86_64'].includes(p.arch)?'linux':p.os==='Mac OS X'&&['aarch64','arm64'].includes(p.arch)?'mac':null;assert(target&&!targets.has(target),'wrong/duplicate JVM target');targets.add(target);assert(p.sdkArchiveSha256===sdk.archiveSha256,'platform SDK archive differs');}
 assert(targets.has('windows')&&targets.has('linux')&&targets.has('mac'),'incomplete supported platform matrix');
 assert(github.profile==='P3'&&github.repository==='edigonzales/interlis-java'&&github.gitCommit===candidate&&github.run.headSha===candidate&&github.run.status==='completed'&&github.run.conclusion==='success'&&github.run.path==='.github/workflows/p3-platforms.yml','invalid GitHub run provenance');
 assert(github.jobs.length===2&&github.jobs.every(j=>j.status==='completed'&&j.conclusion==='success')&&github.artifacts.map(a=>a.platform).sort().join(',')==='linux-x64,windows-x64','incomplete actual GitHub jobs');
 assert(github.artifacts.length===2&&github.artifacts.every(a=>/^sha256:[a-f0-9]{64}$/.test(a.digest)&&a.proof.gitCommit===candidate),'missing verified GitHub artifacts');
 assert(sdk.status==='passed'&&sdk.versions.apiVersion==='0.2'&&sdk.versions.protocol==='interlis.compiler/1.0'&&sdk.versions.diagnosticCatalogVersion==='interlis-diagnostics/v1'&&sdk.versions.formatVersion==='1.0'&&sdk.versions.diagramSchemaVersion==='1.0','missing delivered SDK/version separation');
 assert(reproducibility.status==='passed'&&reproducibility.candidate===candidate&&reproducibility.cleanBuilds===2&&reproducibility.shippedFiles>30&&reproducibility.shippedFiles===Object.keys(reproducibility.artifacts).length,'missing two clean reproducible builds');
 assert(browserManifest.schemaVersion==='1.0'&&browserManifest.gitCommit===candidate&&!browserManifest.dirty&&browserManifest.sourceFingerprint===sourceFingerprint&&Object.keys(browserManifest.resources).length>20,'missing current shipped browser build manifest');
 assert(equal(browserManifest.versions,sdk.versions),'inconsistent delivered version manifests');
 assert(activeGuard(guard,candidate),'missing active inherited OS interpreter guard');
 return {status:'passed',profile:'P3',gitCommit:candidate,sourceFingerprint,runtimes:6,platforms:['linux-x64','windows-x64','macos-arm64'],note:'Internal gate passed; formal acceptance additionally requires finished outer guard/independent review, both with exit0 and passed manifests.'};
}
function main(){
 const out=path.join(root,'build/reports/p3/release/result.json');fs.rmSync(out,{force:true});const read=file=>JSON.parse(fs.readFileSync(path.join(root,file),'utf8'));const commit=commitInfo();assert(!commit.dirty,'P3 release candidate must be clean');
 const currentArtifacts={};for(const file of new Set([...Object.values(definitions).flatMap(d=>d.artifacts),...browser,'interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js','interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js.wasm']))currentArtifacts[file]=hash(fs.readFileSync(path.join(root,file)));
 const github=githubEvidence();
 const repro=read('build/reports/p3/P3-5/reproducibility/result.json');for(const [file,meta]of Object.entries(repro.artifacts)){const absolute=path.resolve(root,file);assert(absolute.startsWith(root+path.sep)&&hash(fs.readFileSync(absolute))===meta.sha256,'reproducible artifact differs from delivered output');}
 const manifest=read('interlis-wasm-teavm/build/web/build-manifest.json');for(const [file,meta]of Object.entries(manifest.resources)){assert(repro.artifacts['interlis-wasm-teavm/build/web/'+file]?.sha256===meta.sha256,'browser resource absent from clean reproducibility comparison');const base=path.join(root,'interlis-wasm-teavm/build/web'),absolute=path.resolve(base,file);assert(absolute.startsWith(base+path.sep)&&hash(fs.readFileSync(absolute))===meta.sha256,'shipped browser resource changed');}
 const sdk=read('build/reports/sdk/archive-consumer.json');assert(/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(sdk.versions.version)&&hash(fs.readFileSync(path.join(root,'build/releases/interlis-java-sdk-'+sdk.versions.version+'.zip')))===sdk.archiveSha256,'changed delivered SDK archive');
 const conformance=read('build/reports/conformance/candidate.json');assert(hash(fs.readFileSync(path.join(root,'build/reports/conformance/conformance-summary.json')))===conformance.summarySha256&&hash(fs.readFileSync(path.join(root,'build/conformance/report.json')))===conformance.rawReportSha256,'changed conformance raw evidence');
 const guard=guardEvidence();
 const result=validate({candidate:commit.gitCommit,sourceFingerprint:fingerprint(),currentArtifacts,proofs:{...Object.fromEntries(Object.entries(definitions).map(([name,d])=>[name,read(d.file)])),browsers:read('interlis-wasm-teavm/build/browser-results.json'),nativeBrowsers:read('interlis-wasm-webimage/build/webimage-browser/browser-results.json'),safari:read('interlis-wasm-teavm/build/safari-result.json'),ui:read('interlis-wasm-teavm/build/ui-projection-results.json')},performance:read('build/reports/performance/full-corpus/result.json'),conformance,platforms:[read('build/reports/p3/platform/result.json'),...github.artifacts.map(a=>a.proof)],github,sdk,reproducibility:repro,browserManifest:manifest,guard});
 fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n');console.log('P3_RELEASE_EVIDENCE_PASS',result.gitCommit);
}
module.exports={validate,validatePreflight,definitions,browser};if(require.main===module)try{if(process.argv.includes('--preflight'))preflight();else main();}catch(error){console.error('P3_RELEASE_EVIDENCE_FAIL',error);process.exitCode=1;}
