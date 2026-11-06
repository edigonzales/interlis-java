'use strict';
// Explicit small integration check: actual local CLI/SDK producers in an otherwise
// synthetic release envelope. This validates interoperability, not a P3 release.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {root,hash,fingerprint,commitInfo}=require('../verification/source-fingerprint.cjs');
const {validate,compilerCliCases,diagnosticCatalogVersion}=require('./evidence.cjs');
const {fixture}=require('./test-fixtures.cjs');
function verify(){
 const inputs=['reports/p3/compiler-cli/processes.json','reports/sdk/archive-consumer.json','reports/sdk/version-manifest.json'];
 const [cli,sdk,versions]=inputs.map(file=>JSON.parse(fs.readFileSync(require('../verification/layout.cjs').inputPath(root,file),'utf8')));
 const state=commitInfo();
 assert.equal(cli.status,'passed');assert.equal(cli.gitCommit,state.gitCommit);
 assert.equal(cli.sourceFingerprint,fingerprint(),'CLI report must bind the current source inputs');
 assert.equal(cli.profile,'compiler-cli/v1');assert.equal(sdk.status,'passed');
 const names=Object.keys(cli.cases).sort();assert.deepEqual(names,compilerCliCases);
 assert.deepEqual(sdk.versions,versions);assert.equal(versions.diagnosticCatalogVersion,diagnosticCatalogVersion);
 const f=fixture(versions);f.sdk={status:sdk.status,archiveSha256:sdk.archiveSha256,versions};
 for(const platform of f.platforms){platform.compilerCliCases=[...names];platform.sdkArchiveSha256=sdk.archiveSha256;}
 assert.equal(validate(f).status,'passed');
 const lost=structuredClone(f);lost.platforms[0].compilerCliCases.pop();assert.throws(()=>validate(lost),/incomplete actual JVM/);
 const obsolete=structuredClone(f);obsolete.sdk.versions.diagnosticCatalogVersion='interlis-diagnostics/v1';
 assert.throws(()=>validate(obsolete),/missing delivered SDK/);
 return {status:'passed',scope:'Actual current CLI/SDK producer interoperability in synthetic release envelope; no platform/browser/release certification',producerState:state,cliCases:names.length,diagnosticCatalogVersion,archiveSha256:sdk.archiveSha256,
  inputs:inputs.map(file=>({file,sha256:hash(fs.readFileSync(require('../verification/layout.cjs').inputPath(root,file)))})),negativeControls:['missing current CLI case','obsolete diagnostic catalog']};
}
module.exports={verify};
if(require.main===module)console.log(JSON.stringify(verify(),null,2));
