'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {fixture}=require('./test-fixtures.cjs');
const {js,wasm,variableFiles,manifestProjection,compareBuilds,validateComparison}=require('./artifact-comparison.cjs');
function check(f){validateComparison(f.reproducibility,f.expectedShippedFiles,f.candidate,f.sourceFingerprint);}
function vary(build){
 const hash='4'.repeat(64);
 for(const file of variableFiles)build.artifacts[file]={...build.artifacts[file],sha256:hash,bytes:2};
 build.browserManifest.resources['interlis-compiler.wasm']={sha256:hash,bytes:2};
 build.browserManifest.artifacts['interlis-wasm-teavm/build/web/interlis-compiler.wasm']=hash;
 build.proofs.js.artifacts[js]=hash;build.proofs.wasm.artifacts[wasm]=hash;
 build.artifacts['interlis-wasm-teavm/build/web/build-manifest.json']=require('./artifact-comparison.cjs').manifestMetadata(build.browserManifest);
}
test('two independently tested generated programs may differ; JVM/SDK still match',()=>{const f=fixture();vary(f.reproducibility.builds[1]);check(f);});
for(const file of ['sdk.jar','sdk.zip','browser.js','browser.json','interlis-wasm-teavm/build/web/app.js','interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js'])test('bytes remain mandatory: '+file,()=>{
 const f=fixture(),[a,b]=f.reproducibility.builds;
 for(const build of [a,b])build.artifacts[file]={sha256:'3'.repeat(64),bytes:1,mode:0o644};
 b.artifacts[file].sha256='4'.repeat(64);assert.throws(()=>compareBuilds(a,b));
});
for(const [name,mutate]of [
 ['missing JS',b=>delete b.artifacts[js]],
 ['foreign inventory',b=>{b.artifacts.foreign=b.artifacts[js];delete b.artifacts[js];}],
 ['size',b=>b.browserManifest.resources['interlis-compiler.wasm'].bytes++],
 ['permissions',b=>b.artifacts[js].mode=0o755],
 ['artifact replaced after test',b=>b.artifacts[js].sha256='4'.repeat(64)],
 ['manifest updated but stale test',b=>{b.artifacts[wasm].sha256='4'.repeat(64);b.artifacts['interlis-wasm-teavm/build/web/interlis-compiler.wasm'].sha256='4'.repeat(64);b.browserManifest.resources['interlis-compiler.wasm'].sha256='4'.repeat(64);b.browserManifest.artifacts['interlis-wasm-teavm/build/web/interlis-compiler.wasm']='4'.repeat(64);}],
 ['foreign report',b=>b.proofs.js.gitCommit='5'.repeat(40)],
 ['missing report',b=>delete b.proofs.wasm],
 ['failed report',b=>b.proofs.js.status='failed'],
 ['incomplete report',b=>b.proofs.wasm.stabilizationCases--],
 ['skipped report',b=>b.proofs.js.skipped=1],
 ['semantic difference',b=>b.proofs.wasm.compiledOutcome='rejected'],
 ['missing upstream binding',b=>delete b.browserManifest.toolchain],
 ['wrong upstream core',b=>b.browserManifest.toolchain.core.sha256='4'.repeat(64)],
 ['non-generated manifest field',b=>b.browserManifest.compilerCapabilities={foreign:true}],
])test('rejects '+name,()=>{const f=fixture();mutate(f.reproducibility.builds[1]);assert.throws(()=>check(f));});
test('projection preserves every unrelated manifest field',()=>{const f=fixture(),m=f.browserManifest,p=manifestProjection(m);assert.deepEqual(p.versions,m.versions);assert.deepEqual(p.toolchain,m.toolchain);assert.deepEqual(m.resources['interlis-compiler.wasm'],{sha256:'3'.repeat(64),bytes:1});});
