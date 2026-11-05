'use strict';
const fs=require('node:fs'),path=require('node:path');const {root,hash,fingerprint,commitInfo}=require('../performance/profile.cjs');
const comparison=require('./artifact-comparison.cjs');
const jvmProfile=require('../../testdata/verification/jvm-profile.json');
const assert=(condition,message)=>{if(!condition)throw Error(message);};
const equal=(a,b)=>JSON.stringify(Object.keys(a).sort().map(k=>[k,a[k]]))===JSON.stringify(Object.keys(b).sort().map(k=>[k,b[k]]));
const js='interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js',wasm='interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm',runtime=wasm.replace('.wasm','.wasm-runtime.js');
const browser=['interlis-wasm-teavm/build/web/build-manifest.json','interlis-wasm-teavm/build/web/interlis-compiler.wasm','interlis-wasm-teavm/build/web/interlis-compiler.wasm-runtime.js','interlis-wasm-teavm/build/diagram-adapter.cjs'];
const compilerCliCases=["analyze","bare-file","color-always","color-always-override","color-analyze","color-auto","color-compile","color-diagram-always","color-diagram-never","color-duplicate","color-empty-no-color","color-format-always","color-format-check-control","color-format-error","color-format-never","color-invalid","color-json-always","color-json-auto","color-json-never","color-missing-inline","color-never","color-never-override","color-no-color","color-term-dumb","color-usage-control","compact","compile","diagnostic-de","diagnostic-json","diagram","empty","explicit-import","foreign-warning","format-utf8","help","invalid-language","invalid-option","legacy-option","missing-file","missing-import","multiple-models","no-model-root","option-end","relevant-warning","repository-http","repository-http-analyze","repository-http-compile","repository-http-diagram","root-isolation","semantic","syntax","unknown-command","valid","verbose","version"];
const diagnosticCatalogVersion='interlis-diagnostics/v4';
const definitions={
 js:{file:'interlis-wasm-teavm/build/js-contract-result.json',artifacts:[js],status:'passed',suite:true},
 wasm:{file:'interlis-wasm-teavm/build/wasm-contract-result.json',artifacts:[wasm,runtime],status:'passed',suite:true},
 native:{file:'interlis-wasm-webimage/build/webimage/result.json',artifacts:['interlis-wasm-webimage/build/webimage/interlis-compiler.js','interlis-wasm-webimage/build/webimage/interlis-compiler.js.wasm'],status:'ok',suite:true},
 client:{file:'interlis-wasm-teavm/build/node-client-result.json',artifacts:[js,'interlis-wasm-teavm/build/compiler-client.cjs','interlis-wasm-teavm/build/diagram-adapter.cjs'],status:'passed',minimum:18},
 diagram:{file:'interlis-wasm-teavm/build/node-diagram-result.json',artifacts:[js,'interlis-wasm-teavm/build/compiler-client.cjs','interlis-wasm-teavm/build/diagram-adapter.cjs'],status:'passed',minimum:4}
};
function validateJvmReports(proof,readBytes){
 const xmls=Object.keys(proof.xmlHashes),logical=proof.logicalReports;
 assert(proof.profile===jvmProfile.profile&&Number.isSafeInteger(proof.tests)&&proof.tests>=jvmProfile.minimumTests&&Number.isSafeInteger(proof.suites)&&proof.suites>=jvmProfile.minimumSuites&&Array.isArray(logical)&&proof.suites===logical.length&&new Set(logical).size===logical.length&&proof.rawReportFiles===xmls.length,'incomplete current JVM profile');
 const directory=file=>file.slice(0,file.lastIndexOf('/'));
 const directories=files=>new Set(files.map(directory));
 assert(jvmProfile.requiredLogicalReports.every(f=>logical.includes(f))&&logical.every(f=>/^TEST-[A-Za-z_$][A-Za-z0-9_.$]*\.xml$/.test(f.slice(f.lastIndexOf('/')+1))&&jvmProfile.requiredDirectories.includes(directory(f)))&&directories(logical).size===jvmProfile.requiredDirectories.length,'foreign/missing current JVM logical suites');
 assert(xmls.every(f=>/^[a-f0-9]{64}$/.test(proof.xmlHashes[f])&&/^TEST-[A-Za-z0-9_.$-]+\.xml$/.test(f.slice(f.lastIndexOf('/')+1))&&jvmProfile.requiredDirectories.includes(directory(f)))&&directories(xmls).size===jvmProfile.requiredDirectories.length,'foreign/missing current JVM report paths');
 if(readBytes)for(const file of xmls)assert(hash(readBytes(file))===proof.xmlHashes[file],'changed archived JVM XML: '+file);
}
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
function read(file){return JSON.parse(fs.readFileSync(require('../verification/layout.cjs').inputPath(root,file),'utf8'));}
function githubEvidence(){
 const github=read('reports/p3/platform-evidence/result.json');
 for(const a of github.artifacts){
  assert('sha256:'+hash(fs.readFileSync(path.resolve(root,a.zip)))===a.digest,'changed downloaded artifact');
  assert(hash(fs.readFileSync(path.resolve(root,a.report)))===a.reportSha256,'changed platform report');
  assert(equal(read(a.report),a.proof),'embedded platform proof differs from verified report');
 }
 return github;
}
function guardEvidence(){
 const guard=read('reports/python-free/environment.json');
 try{process.kill(guard.guardPid,0);guard.active=fs.statSync(guard.path).isDirectory();}catch{guard.active=false;}
 assert(hash(guard.profile)===guard.profileSha256,'changed OS guard profile');return guard;
}
function preflight(){
 fs.rmSync(require('../verification/layout.cjs').report(root,'p3/release/result.json'),{force:true});
 const candidate=commitInfo();assert(!candidate.dirty,'P3 release candidate must be clean');
 validatePreflight(candidate.gitCommit,fingerprint(),githubEvidence(),guardEvidence());
 console.log('P3_RELEASE_PREFLIGHT_PASS',candidate.gitCommit);
}
function validate(input){
 const {candidate,sourceFingerprint,currentArtifacts,expectedShippedFiles,expectedBrowserResources,proofs,platforms,github,performance,conformance,sdk,reproducibility,browserManifest,guard}=input;
 assert(/^[a-f0-9]{40}$/.test(candidate)&&/^[a-f0-9]{64}$/.test(sourceFingerprint),'missing exact candidate binding');
 const noFailedCases=report=>assert(['skipped','failures','errors','cancelled','todo'].every(key=>!(key in report)||report[key]===0),'failed/cancelled/skipped runtime cases');
 const bound=(report,files,status='passed')=>{assert(report&&report.status===status&&report.gitCommit===candidate&&report.dirty===false&&report.sourceFingerprint===sourceFingerprint,'stale/dirty/unsuccessful proof');noFailedCases(report);assert(equal(report.artifacts||{},Object.fromEntries(files.map(f=>[f,currentArtifacts[f]])))&&files.every(f=>/^[a-f0-9]{64}$/.test(currentArtifacts[f]||'')),'foreign/missing runtime artifact');};
 const suite=r=>{assert(r&&r.stabilizationCases===3560&&r.corpusRegressionCases===24,'incomplete semantic regressions');noFailedCases(r);assert(!('status'in r)||['ok','passed'].includes(r.status),'failed semantic runtime result');assert(['outcome','compiledOutcome','workerRestartOutcome'].every(key=>!(key in r)||r[key]==='succeeded')&&(!('rejectedOutcome'in r)||r.rejectedOutcome==='rejected'),'contradictory semantic runtime outcomes');};
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
 assert(platforms.length===3,'missing JVM target');const targets=new Set();for(const p of platforms){assert(p.status==='passed'&&p.profile===jvmProfile.profile&&p.gitCommit===candidate&&!p.dirty&&p.sourceFingerprint===sourceFingerprint,'stale/foreign platform');assert(p.java.startsWith('25.0.3')&&p.tests>=jvmProfile.minimumTests&&p.suites>=jvmProfile.minimumSuites&&p.failures===0&&p.errors===0&&p.skipped===0&&Array.isArray(p.logicalReports)&&p.logicalReports.length===p.suites&&p.sdkCommands===9&&Array.isArray(p.compilerCliCases)&&JSON.stringify([...p.compilerCliCases].sort())===JSON.stringify(compilerCliCases),'incomplete actual JVM/distribution processes');validateJvmReports(p);assert(p.compilerCliColorVerified===true&&!JSON.stringify(p).includes('\\u001b'),'unverified color processes or ANSI in machine platform proof');const target=p.os.startsWith('Windows')&&['amd64','x86_64'].includes(p.arch)?'windows':p.os==='Linux'&&['amd64','x86_64'].includes(p.arch)?'linux':p.os==='Mac OS X'&&['aarch64','arm64'].includes(p.arch)?'mac':null;assert(target&&!targets.has(target),'wrong/duplicate JVM target');targets.add(target);assert(p.sdkArchiveSha256===sdk.archiveSha256,'platform SDK archive differs');}
 assert(targets.has('windows')&&targets.has('linux')&&targets.has('mac'),'incomplete supported platform matrix');
 assert(github.profile==='P3'&&github.repository==='edigonzales/interlis-java'&&github.gitCommit===candidate&&github.run.headSha===candidate&&github.run.status==='completed'&&github.run.conclusion==='success'&&github.run.path==='.github/workflows/p3-platforms.yml','invalid GitHub run provenance');
 assert(github.jobs.length===2&&github.jobs.every(j=>j.status==='completed'&&j.conclusion==='success')&&github.artifacts.map(a=>a.platform).sort().join(',')==='linux-x64,windows-x64','incomplete actual GitHub jobs');
 assert(github.artifacts.length===2&&github.artifacts.every(a=>/^sha256:[a-f0-9]{64}$/.test(a.digest)&&a.proof.gitCommit===candidate),'missing verified GitHub artifacts');
 assert(sdk.status==='passed'&&sdk.versions.apiVersion==='0.2'&&sdk.versions.protocol==='interlis.compiler/1.0'&&sdk.versions.diagnosticCatalogVersion===diagnosticCatalogVersion&&sdk.versions.formatVersion==='2.0'&&sdk.versions.diagramSchemaVersion==='1.0','missing delivered SDK/version separation');
 comparison.validateComparison(reproducibility,expectedShippedFiles,candidate,sourceFingerprint);
 assert(browserManifest.schemaVersion==='1.0'&&browserManifest.gitCommit===candidate&&!browserManifest.dirty&&browserManifest.sourceFingerprint===sourceFingerprint&&Object.keys(browserManifest.resources).length>20,'missing current shipped browser build manifest');
 const webPrefix='interlis-wasm-teavm/build/web/';
 const resourceNames=expectedShippedFiles.filter(file=>file.startsWith(webPrefix)&&file!==webPrefix+'build-manifest.json').map(file=>file.slice(webPrefix.length)).sort();
 assert(expectedBrowserResources&&resourceNames.length>20&&JSON.stringify(Object.keys(expectedBrowserResources).sort())===JSON.stringify(resourceNames)&&JSON.stringify(Object.keys(browserManifest.resources).sort())===JSON.stringify(resourceNames),'incomplete/foreign browser resource inventory');
 for(const file of resourceNames){const actual=expectedBrowserResources[file],declared=browserManifest.resources[file];assert(/^[a-f0-9]{64}$/.test(actual.sha256)&&Number.isSafeInteger(actual.bytes)&&actual.bytes>=0&&declared.sha256===actual.sha256&&declared.bytes===actual.bytes,'browser resource hash/size differs from delivery');}
 assert(equal(browserManifest.versions,sdk.versions),'inconsistent delivered version manifests');
 assert(equal(browserManifest.toolchain?.core||{},require('../../gradle/teavm-core.json')),'missing pinned upstream compiler-tool binding');
 assert(activeGuard(guard,candidate),'missing active inherited OS interpreter guard');
 return {status:'passed',profile:'P3',gitCommit:candidate,sourceFingerprint,runtimes:6,platforms:['linux-x64','windows-x64','macos-arm64'],note:'Internal gate passed; formal acceptance additionally requires finished outer guard/independent review, both with exit0 and passed manifests.'};
}
function main(){
 const out=require('../verification/layout.cjs').report(root,'p3/release/result.json');fs.rmSync(out,{force:true});const read=file=>JSON.parse(fs.readFileSync(require('../verification/layout.cjs').inputPath(root,file),'utf8'));const commit=commitInfo();assert(!commit.dirty,'P3 release candidate must be clean');
 const currentArtifacts={};for(const file of new Set([...Object.values(definitions).flatMap(d=>d.artifacts),...browser,'interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js','interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js.wasm']))currentArtifacts[file]=hash(fs.readFileSync(require('../verification/layout.cjs').inputPath(root,file)));
 const github=githubEvidence();
 const repro=read('reports/p3/P3-5/reproducibility/result.json');for(const [file,meta]of Object.entries(repro.artifacts)){const absolute=require('../verification/layout.cjs').inputPath(root,file);assert(absolute.startsWith(root+path.sep),'unsafe comparison path');const bytes=fs.readFileSync(absolute);assert((fs.statSync(absolute).mode&0o777)===meta.mode,'changed delivered permissions');if(!comparison.variableFiles.has(file)&&file!==comparison.manifestFile)assert(hash(bytes)===meta.sha256&&bytes.length===meta.bytes,'reproducible artifact differs from delivered output');}
 const manifest=read('interlis-wasm-teavm/build/web/build-manifest.json');for(const [file,meta]of Object.entries(manifest.resources)){const base=path.join(root,'interlis-wasm-teavm/build/web'),absolute=path.resolve(base,file);assert(absolute.startsWith(base+path.sep)&&hash(fs.readFileSync(absolute))===meta.sha256,'shipped browser resource changed');}
 comparison.compareBuilds(repro.builds[0],{artifacts:Object.fromEntries(Object.keys(repro.artifacts).map(file=>{const absolute=require('../verification/layout.cjs').inputPath(root,file),bytes=fs.readFileSync(absolute);return [file,{sha256:hash(bytes),bytes:bytes.length,mode:fs.statSync(absolute).mode&0o777}];})),browserManifest:manifest});
 const sdk=read('reports/sdk/archive-consumer.json');assert(/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(sdk.versions.version)&&hash(fs.readFileSync(require('../verification/layout.cjs').resolveRole(require('../verification/layout.cjs').readVerificationInputs(root),'sdk')))===sdk.archiveSha256,'changed delivered SDK archive');
 const conformance=read('reports/conformance/candidate.json');assert(hash(fs.readFileSync(require('../verification/layout.cjs').report(root,'conformance/conformance-summary.json')))===conformance.summarySha256&&hash(fs.readFileSync(require('../verification/layout.cjs').inputPath(root,'conformance/report.json')))===conformance.rawReportSha256,'changed conformance raw evidence');
 const guard=guardEvidence();
 const result=validate({candidate:commit.gitCommit,sourceFingerprint:fingerprint(),currentArtifacts,expectedShippedFiles:require('./distribution-files.cjs').distributionFiles(root),expectedBrowserResources:require('./distribution-files.cjs').browserResources(root),proofs:{...Object.fromEntries(Object.entries(definitions).map(([name,d])=>[name,read(d.file)])),browsers:read('interlis-wasm-teavm/build/browser-results.json'),nativeBrowsers:read('interlis-wasm-webimage/build/webimage-browser/browser-results.json'),safari:read('interlis-wasm-teavm/build/safari-result.json'),ui:read('interlis-wasm-teavm/build/ui-projection-results.json')},performance:read('reports/performance/full-corpus/result.json'),conformance,platforms:[read('reports/p3/platform/result.json'),...github.artifacts.map(a=>a.proof)],github,sdk,reproducibility:repro,browserManifest:manifest,guard});
 fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n');console.log('P3_RELEASE_EVIDENCE_PASS',result.gitCommit);
}
module.exports={compilerCliCases,diagnosticCatalogVersion,validateJvmReports,validate,validatePreflight,definitions,browser};if(require.main===module)try{if(process.argv.includes('--preflight'))preflight();else main();}catch(error){console.error('P3_RELEASE_EVIDENCE_FAIL',error);process.exitCode=1;}
