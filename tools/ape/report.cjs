// Explicit read-only evidence inventory. Never runs tests or grants a product gate.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {hash}=require('./record.cjs'),{loadCandidate}=require('./bundle.cjs'),{verifyReceipt,requireCases}=require('./evidence.cjs');
function report(directory){
 assert.ok(path.isAbsolute(directory)&&fs.statSync(directory).isDirectory());
 const requestFile=path.join(directory,'report-request.json'),request=JSON.parse(fs.readFileSync(requestFile));
 assert.equal(request.schemaVersion,'1.0');assert.ok(request.entries.length>0);const seen=new Set(),entries=[];
 const candidate=loadCandidate(request.candidateManifest);
 for(const entry of request.entries){
  assert.ok(path.isAbsolute(entry.path)&&!seen.has(entry.path));seen.add(entry.path);assert.equal(hash(entry.path),entry.sha256,'Changed original evidence');
  const value=JSON.parse(fs.readFileSync(entry.path));
  if(entry.kind==='receipt'){verifyReceipt(entry.path);assert.equal(value.exitCode,entry.expectedExitCode);}
  else if(entry.kind==='cases'){assert.equal(value.candidateSha256||value.sha256,candidate.bundle.sha256);assert.equal(value.status,'PASSED');requireCases(entry.requiredCases,value.cases);}
  else assert.equal(entry.kind,'facts','Unknown evidence kind');
  entries.push({path:entry.path,sha256:entry.sha256,kind:entry.kind,...(entry.kind==='cases'?{cases:value.cases.length,status:value.status}:{}),...(entry.kind==='receipt'?{exitCode:value.exitCode}: {})});
 }
 return {schemaVersion:'1.0',scope:'validated selected evidence inventory; no automatic feasibility or platform verdict',requestSha256:hash(requestFile),artifactSha256:candidate.bundle.sha256,bundleManifestSha256:hash(request.candidateManifest),entries};
}
module.exports={report};if(require.main===module){try{console.log(JSON.stringify(report(process.argv[2]),null,2));}catch(error){console.error(error);process.exitCode=1;}}
