// Small exact comparator and receipt validator. No semantic normalization.
const fs=require('node:fs'),assert=require('node:assert/strict');
const {hash}=require('./record.cjs');
function compareCase(reference,candidate){
 return reference.exitCode===candidate.exitCode && reference.signal===candidate.signal &&
  reference.timedOut===false && candidate.timedOut===false &&
  reference.stdout===candidate.stdout && reference.stderr===candidate.stderr;
}
function requireCases(required,cases){
 assert.ok(required.length>0&&new Set(required).size===required.length,'Empty/duplicate required cases');
 assert.equal(cases.length,required.length,'Missing/extra cases');
 const seen=new Set();for(const c of cases){assert.ok(required.includes(c.id)&&!seen.has(c.id),'Foreign/duplicate case');seen.add(c.id);assert.equal(c.status,'PASSED','Case not passed');}
}
function verifyReceipt(file){
 const r=JSON.parse(fs.readFileSync(file));assert.ok(Array.isArray(r.argv)&&r.argv.length>0);assert.ok(r.cwd&&r.host&&r.startedAt&&r.endedAt);
 assert.equal(r.timedOut,false);assert.equal(r.signal,null);assert.ok(Number.isInteger(r.exitCode));assert.ok(!r.error);
 for(const stream of ['stdout','stderr'])assert.equal(hash(r[stream].path),r[stream].sha256,'Changed raw stream');return r;
}
module.exports={compareCase,requireCases,verifyReceipt};
