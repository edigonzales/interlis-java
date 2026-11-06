'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');const {validate,validatePreflight,definitions,browser}=require('./evidence.cjs');
const {fixture}=require('./test-fixtures.cjs');
const jvmProfile=require('../../testdata/verification/jvm-profile.json');
test('complete current compiler candidate evidence validates',()=>assert.equal(validate(fixture()).status,'passed'));
test('real guard combines unavailable PATH names with absolute OS execution denials',()=>{
 const f=fixture();f.guard.probes.unshift({command:'absent-command',status:null,error:'ENOENT'});
 assert.equal(validate(f).status,'passed');assert.equal(validatePreflight(f.candidate,f.sourceFingerprint,f.github,f.guard).status,'passed');
});
test('preflight accepts current CI and active guard before runtime results exist',()=>{
 const f=fixture();assert.equal(validatePreflight(f.candidate,f.sourceFingerprint,f.github,f.guard).status,'passed');
});
for(const [name,mutate]of [
 ['old profile',f=>f.github.profile='P3'],['old workflow path',f=>f.github.run.path='.github/workflows/p3-platforms.yml'],
 ['previous candidate',f=>f.github.gitCommit='old'],['stale CI sources',f=>f.github.artifacts[0].proof.sourceFingerprint='old'],
 ['missing Windows evidence',f=>f.github.artifacts.pop()],['old finished wrapper',f=>f.guard.status='passed'],['inactive wrapper',f=>f.guard.active=false]
])test('preflight rejects '+name,()=>{const f=fixture();mutate(f);assert.throws(()=>validatePreflight(f.candidate,f.sourceFingerprint,f.github,f.guard));});
for(const [name,mutate]of [
 ['missing Node proof',f=>delete f.proofs.js],['stale source',f=>f.proofs.wasm.sourceFingerprint='old'],['foreign commit',f=>f.proofs.native.gitCommit='9'.repeat(40)],
 ['dirty proof',f=>f.proofs.client.dirty=true],['foreign module',f=>f.proofs.js.artifacts[Object.keys(f.proofs.js.artifacts)[0]]='other'],['missing module',f=>delete f.proofs.wasm.artifacts[Object.keys(f.proofs.wasm.artifacts)[0]]],
 ['missing extra regressions',f=>delete f.proofs.js.corpusRegressionCases],['incomplete original suite',f=>f.proofs.native.stabilizationCases=3559],['skipped Node case',f=>f.proofs.diagram.skipped=1],
 ['missing browser',f=>f.proofs.browsers.pop()],['fake Safari from WebKit',f=>f.proofs.safari.browser='webkit'],['missing actual version',f=>delete f.proofs.browsers[0].browserVersion],
 ['declared browser skip despite full case count',f=>f.proofs.browsers[0].result.skipped=1],
 ['declared Safari error despite passed report',f=>f.proofs.safari.result.errors=1],
 ['declared native skip despite full case count',f=>f.proofs.nativeBrowsers[0].skipped=1],
 ['declared suite cancellation',f=>f.proofs.js.cancelled=1],
 ['failed browser result despite passed capture',f=>f.proofs.browsers[0].result.status='failed'],
 ['failed browser compile despite complete case count',f=>f.proofs.browsers[0].result.compiledOutcome='rejected'],
 ['failed Node compile despite passed report',f=>f.proofs.js.outcome='failed'],
 ['invalid source unexpectedly succeeds in browser control',f=>f.proofs.browsers[0].result.rejectedOutcome='succeeded'],
 ['failed native browser',f=>f.proofs.nativeBrowsers[1].status='failed'],['old transport',f=>f.proofs.safari.result.transportChecks=20],['missing late layout UI',f=>f.proofs.ui[0].observations=[]],
 ['historical thirteen-root performance',f=>f.performance.roots=13],['partial edits',f=>f.performance.realCorpusEdits=1000],['semantic mismatch',f=>f.performance.semanticMismatches=1],
 ['unexpected conformance result',f=>f.conformance.summary.summary.FAIL=1],['removed normative control',f=>f.conformance.summary.normativeAssessments.pop()],
 ['missing Windows',f=>f.platforms.splice(1,1)],['wrong architecture',f=>f.platforms[1].arch='aarch64'],['skipped JVM',f=>f.platforms[0].skipped=1],['incomplete SDK processes',f=>f.platforms[0].sdkCommands=8],
 ['missing COLOR JVM cases',f=>f.platforms[0].tests=jvmProfile.minimumTests-1],['missing COLOR suite',f=>f.platforms[0].suites=jvmProfile.minimumSuites-1],['missing suite XML',f=>delete f.platforms[0].xmlHashes[Object.keys(f.platforms[0].xmlHashes)[0]]],
 ['old report path instead of new',f=>{const p=f.platforms[0];p.xmlHashes['interlis-compiler-cli/build/test-results/verificationTest/TEST-Old.xml']=p.xmlHashes[Object.keys(f.platforms[0].xmlHashes)[0]];delete p.xmlHashes[Object.keys(f.platforms[0].xmlHashes)[0]];}],
 ['extra old duplicate report',f=>f.platforms[0].xmlHashes['interlis-compiler-cli/build/test-results/evidenceTest/TEST-Old.xml']='3'.repeat(64)],
 ['changed XML hash shape',f=>f.platforms[0].xmlHashes[Object.keys(f.platforms[0].xmlHashes)[0]]='changed'],
 ['missing named logical suite',f=>{f.platforms[0].logicalReports=f.platforms[0].logicalReports.filter(p=>p!==jvmProfile.requiredLogicalReports[0]);f.platforms[0].suites--;}],['duplicate logical suite',f=>f.platforms[0].logicalReports[1]=f.platforms[0].logicalReports[0]],['wrong raw file count',f=>f.platforms[0].rawReportFiles++],
 ['missing standard process',f=>f.platforms[0].compilerCliCases.pop()],['foreign standard process',f=>f.platforms[0].compilerCliCases[0]='foreign'],
 ['ANSI in machine platform report',f=>f.platforms[0].extra='\u001b[31mbad\u001b[0m'],['missing color verification',f=>delete f.platforms[0].compilerCliColorVerified],
 ['old profile',f=>f.github.profile='P3'],['old workflow path',f=>f.github.run.path='.github/workflows/p3-platforms.yml'],
 ['old JVM profile',f=>f.platforms[0].profile='P3-jvm-distribution-v3-jpms'],
 ['foreign workflow',f=>f.github.repository='other/project'],['failed workflow',f=>f.github.run.conclusion='failure'],['failed job',f=>f.github.jobs[0].conclusion='failure'],['unhashed artifact',f=>f.github.artifacts[0].digest=''],
 ['inconsistent SDK version',f=>f.sdk.versions.apiVersion='0.1'],['different platform package',f=>f.platforms[2].sdkArchiveSha256='old'],['single build',f=>f.reproducibility.cleanBuilds=1],['empty repro comparison',f=>f.reproducibility.artifacts={}],
 ['partial repro with self-consistent reduced count',f=>{delete f.reproducibility.artifacts.file0;f.reproducibility.shippedFiles--;}],
 ['foreign file replacing omitted delivery',f=>{delete f.reproducibility.artifacts.file0;f.reproducibility.artifacts.other={sha256:'3'.repeat(64)};}],
 ['missing independent delivery inventory',f=>delete f.expectedShippedFiles],
 ['partial browser manifest above old minimum',f=>delete f.browserManifest.resources.file0],
 ['foreign resource in browser manifest',f=>f.browserManifest.resources.foreign={sha256:'3'.repeat(64),bytes:1}],
 ['wrong browser resource size',f=>f.browserManifest.resources.file0.bytes++],
 ['wrong browser resource hash',f=>f.browserManifest.resources.file0.sha256='4'.repeat(64)],
 ['missing independent browser inventory',f=>delete f.expectedBrowserResources],
 ['stale browser manifest',f=>f.browserManifest.gitCommit='old'],['unshipped version mismatch',f=>f.browserManifest.versions.protocol='other'],
 ['unbound compiler tool',f=>delete f.browserManifest.toolchain],['foreign compiler tool',f=>f.browserManifest.toolchain.core.sha256='other'],
 ['old completed guard',f=>f.guard.status='passed'],['dead guard',f=>f.guard.active=false],['unproved execution denial',f=>f.guard.probes=[]],['successful interpreter',f=>f.guard.probes[0].status=0],
 ['absolute absence without execution denial',f=>f.guard.probes[0].error='ENOENT'],['unexpected PATH execution error',f=>f.guard.probes.push({command:'unavailable',status:null,error:'EACCES'})]
])test('Compiler release fails closed: '+name,()=>{const f=fixture();mutate(f);assert.throws(()=>validate(f));});

test('downloaded platform XML must match the recorded hashes',()=>{
 const {validateJvmReports}=require('./evidence.cjs'),{hash}=require('../verification/source-fingerprint.cjs');
 const p=fixture().platforms[0],bytes=Buffer.from('archived XML fixture');
 for(const file of Object.keys(p.xmlHashes))p.xmlHashes[file]=hash(bytes);
 validateJvmReports(p,()=>bytes);
 assert.throws(()=>validateJvmReports(p,()=>Buffer.from('tampered')),/changed archived JVM XML/);
 assert.throws(()=>validateJvmReports(p,()=>{throw Error('missing XML');}),/missing XML/);
});

test('release profile matches current Java CLI case and diagnostic producers',()=>{
 const fs=require('node:fs'),path=require('node:path'),{root}=require('../verification/source-fingerprint.cjs');
 const {compilerCliCases,diagnosticCatalogVersion}=require('./evidence.cjs');
 const cli=fs.readFileSync(path.join(root,'interlis-verification/src/main/java/guru/interlis/verification/CompilerCliProcesses.java'),'utf8');
 const declaration=cli.match(/static final Set<String> REQUIRED = Set\.of\(([\s\S]*?)\);/);
 assert.ok(declaration,'Missing concrete CLI REQUIRED declaration');
 const actual=[...declaration[1].matchAll(/"([^"\n]+)"/g)].map(m=>m[1]).sort();
 assert.deepEqual(compilerCliCases,actual);
 const catalog=fs.readFileSync(path.join(root,'interlis-model/src/main/java/guru/interlis/diagnostics/DiagnosticCatalog.java'),'utf8');
 assert.equal(diagnosticCatalogVersion,catalog.match(/public static final String VERSION = "([^"]+)";/)[1]);
});
