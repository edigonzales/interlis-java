'use strict';
const jvmProfile=require('../../testdata/verification/jvm-profile.json');
const {definitions,browser,compilerCliCases,diagnosticCatalogVersion}=require('./evidence.cjs');
function platformLogicalReports(){
 const files=new Set([...jvmProfile.requiredLogicalReports,...jvmProfile.requiredDirectories.map(d=>d+'/TEST-fixture.Required.xml')]);
 for(let i=0;files.size<jvmProfile.minimumSuites;i++)files.add(jvmProfile.requiredDirectories[0]+'/TEST-fixture.Suite'+i+'.xml');
 return [...files];
}
function platformXml(sha){return Object.fromEntries(jvmProfile.requiredDirectories.map(d=>[d+'/TEST-junit-jupiter.xml',sha]));}
function fixture(versionOverride){
 const candidate='1'.repeat(40),sourceFingerprint='2'.repeat(64),sha='3'.repeat(64),currentArtifacts={};
 const files=new Set([...Object.values(definitions).flatMap(d=>d.artifacts),...browser,'interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js','interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js.wasm']);for(const file of files)currentArtifacts[file]=sha;
 const bound=(files,status='passed')=>({status,gitCommit:candidate,dirty:false,sourceFingerprint,artifacts:Object.fromEntries(files.map(f=>[f,sha]))});
 const suite={stabilizationCases:3560,corpusRegressionCases:24};const transport={...suite,transportChecks:29,clientContractChecks:7,exactDecimal:true,workerRestartOutcome:'succeeded'};
 const proofs={};for(const [name,d]of Object.entries(definitions))proofs[name]={...bound(d.artifacts,d.status),...suite,tests:d.minimum||1,failures:0,errors:0,skipped:0};
 const names=['chromium','firefox','webkit'];proofs.browsers=names.map(browserName=>({...bound(browser),browser:browserName,browserVersion:'fixture',externalRequests:[],result:{...transport}}));
 proofs.nativeBrowsers=names.map(browserName=>({...bound(['interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js','interlis-wasm-webimage/build/webimage-browser/interlis-compiler.js.wasm']),browser:browserName,browserVersion:'fixture',runtime:'graalvm-web-image',result:{...suite,outcome:'succeeded'}}));
 proofs.safari={...bound(browser),browser:'Safari',driverVersion:'fixture',result:{...transport}};
 proofs.ui=names.map(browserName=>({...bound([browser[0],browser[1],browser[3]]),browser:browserName,observations:Array(5).fill('actual observation')}));
 const versions=versionOverride||{version:'0.2.0-SNAPSHOT',apiVersion:'0.2',protocol:'interlis.compiler/1.0',diagnosticCatalogVersion,formatVersion:'1.0',diagramSchemaVersion:'1.0'};
 const sdk={status:'passed',archiveSha256:sha,versions};
 const expectedBrowserResources=Object.fromEntries([...Array.from({length:36},(_,i)=>['file'+i,{sha256:sha,bytes:1}]),...['interlis-compiler.wasm','interlis-compiler.wasm-runtime.js'].map(file=>[file,{sha256:sha,bytes:1}])]);
 const expectedShippedFiles=[...Object.keys(expectedBrowserResources).map(file=>'interlis-wasm-teavm/build/web/'+file),'interlis-wasm-teavm/build/web/build-manifest.json',...Array.from({length:45},(_,i)=>'file'+i),...definitions.js.artifacts,...definitions.wasm.artifacts];
 const platforms=[['Linux','amd64'],['Windows Server 2025','amd64'],['Mac OS X','aarch64']].map(([os,arch])=>({profile:jvmProfile.profile,status:'passed',gitCommit:candidate,dirty:false,sourceFingerprint,java:'25.0.3+9',os,arch,tests:jvmProfile.minimumTests,suites:platformLogicalReports().length,logicalReports:platformLogicalReports(),rawReportFiles:jvmProfile.requiredDirectories.length,failures:0,errors:0,skipped:0,xmlHashes:platformXml(sha),sdkCommands:9,compilerCliCases:[...compilerCliCases],compilerCliColorVerified:true,sdkArchiveSha256:sha}));
 const input={candidate,sourceFingerprint,currentArtifacts,expectedShippedFiles,expectedBrowserResources,proofs,platforms,sdk,github:{profile:'P3',repository:'edigonzales/interlis-java',gitCommit:candidate,run:{headSha:candidate,status:'completed',conclusion:'success',path:'.github/workflows/p3-platforms.yml'},jobs:[{status:'completed',conclusion:'success'},{status:'completed',conclusion:'success'}],artifacts:[{platform:'linux-x64',digest:'sha256:'+sha,proof:platforms[0]},{platform:'windows-x64',digest:'sha256:'+sha,proof:platforms[1]}]},performance:{status:'passed',gitCommit:candidate,dirty:false,sourceFingerprint,roots:347,runtimes:6,realCorpusEdits:2000,skipped:0,semanticMismatches:0},conformance:{status:'passed',gitCommit:candidate,dirty:false,sourceFingerprint,summary:{summary:{PASS:577,XFAIL:16,FAIL:0,XPASS:0,ERROR:0,TOTAL:593},normativeAssessments:Array(21).fill({})}},reproducibility:{status:'passed',candidate,sourceFingerprint,cleanBuilds:2,shippedFiles:84,artifacts:Object.fromEntries(expectedShippedFiles.map(file=>[file,{sha256:sha}]))},browserManifest:{schemaVersion:'1.0',gitCommit:candidate,dirty:false,sourceFingerprint,toolchain:{core:structuredClone(require('../../gradle/teavm-core.json'))},artifacts:{'interlis-wasm-teavm/build/web/interlis-compiler.wasm':sha,'interlis-wasm-teavm/build/web/interlis-compiler.wasm-runtime.js':sha},resources:structuredClone(expectedBrowserResources),versions:{...versions}},guard:{status:'running',gitHead:candidate,dirty:'',guardPid:123,active:true,probes:[{command:'/fixture/interpreter',error:'EPERM',status:null}]}};
 const artifacts=Object.fromEntries(expectedShippedFiles.map(file=>[file,{sha256:sha,bytes:1,mode:0o644}]));
 artifacts['interlis-wasm-teavm/build/web/build-manifest.json']=require('./artifact-comparison.cjs').manifestMetadata(input.browserManifest);
 const build={artifacts,browserManifest:structuredClone(input.browserManifest),proofs:{js:structuredClone(proofs.js),wasm:structuredClone(proofs.wasm)}};
 input.reproducibility={...input.reproducibility,shippedFiles:expectedShippedFiles.length,artifacts,builds:[build,structuredClone(build)]};
 return input;
}
module.exports={fixture};
