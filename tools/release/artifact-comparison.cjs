'use strict';
const assert=require('node:assert/strict');
const {createHash}=require('node:crypto');
const canonical=value=>Array.isArray(value)?value.map(canonical):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(key=>[key,canonical(value[key])])):value;
const manifestBytes=manifest=>JSON.stringify(canonical(manifest),null,2)+'\n';
const manifestMetadata=manifest=>{const bytes=Buffer.from(manifestBytes(manifest));return {sha256:createHash('sha256').update(bytes).digest('hex'),bytes:bytes.length,mode:0o644};};
const prefix='interlis-wasm-teavm/build/';
const js=prefix+'generated/teavm/js/interlis-compiler.js';
const wasm=prefix+'generated/teavm/wasm-gc/interlis-compiler.wasm';
const runtime=prefix+'generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js';
const manifestFile=prefix+'web/build-manifest.json';
// Exact generated program paths only. Upstream's copied runtime remains byte-reproducible.
const variableFiles=new Set([js,wasm,prefix+'web/interlis-compiler.wasm']);
function manifestProjection(manifest){
 const result=structuredClone(manifest);
 assert.match(result.resources?.['interlis-compiler.wasm']?.sha256||'',/^[a-f0-9]{64}$/);
 assert.ok(Number.isInteger(result.resources['interlis-compiler.wasm'].bytes)&&result.resources['interlis-compiler.wasm'].bytes>0);
 result.resources['interlis-compiler.wasm']={sha256:'<generated>',bytes:'<generated>'};
 const loaded=prefix+'web/interlis-compiler.wasm';
 assert.match(result.artifacts?.[loaded]||'',/^[a-f0-9]{64}$/);
 result.artifacts[loaded]='<generated>';
 return result;
}
function functionalProof(proof,files,build,candidate,sourceFingerprint){
 assert.ok(proof?.status==='passed'&&proof.gitCommit===candidate&&proof.dirty===false&&proof.sourceFingerprint===sourceFingerprint,'foreign/failed independent runtime proof');
 assert.equal(proof.stabilizationCases,3560);assert.equal(proof.corpusRegressionCases,24);
 for(const key of ['failures','errors','skipped','cancelled','todo'])if(key in proof)assert.equal(proof[key],0);
 for(const key of ['outcome','compiledOutcome','workerRestartOutcome'])if(key in proof)assert.equal(proof[key],'succeeded');
 if('rejectedOutcome'in proof)assert.equal(proof.rejectedOutcome,'rejected');
 assert.deepEqual(proof.artifacts,Object.fromEntries(files.map(file=>[file,build.artifacts[file]?.sha256])),'independent runtime artifact mismatch');
}
function validateBuild(build,candidate,sourceFingerprint){
 assert.ok(build&&build.artifacts&&build.browserManifest,'missing independent build');
 for(const meta of Object.values(build.artifacts)){assert.match(meta.sha256||'',/^[a-f0-9]{64}$/);assert.ok(Number.isInteger(meta.bytes)&&meta.bytes>0);assert.ok(Number.isInteger(meta.mode));}
 const manifest=build.browserManifest;
 const raw=manifestMetadata(manifest);assert.equal(build.artifacts[manifestFile]?.sha256,raw.sha256,'independent manifest content hash');assert.equal(build.artifacts[manifestFile]?.bytes,raw.bytes,'independent manifest content size');
 assert.ok(manifest.gitCommit===candidate&&manifest.dirty===false&&manifest.sourceFingerprint===sourceFingerprint,'foreign independent manifest');
 const resourceFiles=Object.keys(build.artifacts).filter(file=>file.startsWith(prefix+'web/')&&file!==manifestFile).sort();
 assert.deepEqual(Object.keys(manifest.resources).map(file=>prefix+'web/'+file).sort(),resourceFiles,'independent browser inventory');
 for(const file of resourceFiles){const meta=build.artifacts[file],declared=manifest.resources[file.slice((prefix+'web/').length)];assert.equal(meta.sha256,declared.sha256);assert.equal(meta.bytes,declared.bytes);}
 assert.deepEqual(manifest.artifacts,Object.fromEntries(['interlis-compiler.wasm','interlis-compiler.wasm-runtime.js'].map(file=>[prefix+'web/'+file,build.artifacts[prefix+'web/'+file]?.sha256])),'independent manifest loaded-artifact aliases differ from delivery');
 assert.deepEqual(manifest.toolchain?.core,require('../../gradle/teavm-core.json'));
 functionalProof(build.proofs?.js,[js],build,candidate,sourceFingerprint);
 functionalProof(build.proofs?.wasm,[wasm,runtime],build,candidate,sourceFingerprint);
 assert.equal(build.artifacts[wasm].sha256,build.artifacts[prefix+'web/interlis-compiler.wasm'].sha256);
}
/** Compare bytes where promised, retaining the complete inventories of BOTH builds. */
function compareBuilds(first,second){
 const keys=Object.keys(first.artifacts).sort();assert.deepEqual(keys,Object.keys(second.artifacts).sort(),'different delivery inventories');
 for(const file of keys){
  const a=first.artifacts[file],b=second.artifacts[file];assert.equal(a.mode,b.mode,'changed file permissions: '+file);
  if(file===manifestFile)assert.deepEqual(manifestProjection(first.browserManifest),manifestProjection(second.browserManifest),'non-generated browser manifest difference');
  else if(!variableFiles.has(file))assert.deepEqual(a,b,'non-reproducible delivery: '+file);
 }
}
function validateComparison(report,expectedFiles,candidate,sourceFingerprint){
 assert.ok(report?.status==='passed'&&report.candidate===candidate&&report.sourceFingerprint===sourceFingerprint&&report.cleanBuilds===2,'missing two clean builds');
 assert.equal(report.builds?.length,2);
 const expected=[...expectedFiles].sort();assert.ok(expected.length>30&&new Set(expected).size===expected.length);
 for(const build of report.builds){assert.deepEqual(Object.keys(build.artifacts).sort(),expected,'incomplete/foreign independent delivery inventory');validateBuild(build,candidate,sourceFingerprint);}
 assert.equal(report.shippedFiles,expected.length);assert.deepEqual(report.artifacts,report.builds[0].artifacts);
 compareBuilds(...report.builds);
}
module.exports={manifestBytes,manifestMetadata,js,wasm,runtime,manifestFile,variableFiles,manifestProjection,validateBuild,compareBuilds,validateComparison};
