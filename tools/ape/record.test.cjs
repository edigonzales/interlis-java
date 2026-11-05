const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {record}=require('./record.cjs'),{verifyReceipt}=require('./evidence.cjs');
test('recorder retains negative exits and streams; changed logs and timeouts are rejected',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ape-recorder-'));try{
  const negative=await record(dir,'negative',[process.execPath,'-e',"process.stdout.write('out');process.stderr.write('err');process.exitCode=1"]);assert.equal(negative.exitCode,1);assert.equal(fs.readFileSync(negative.stdout.path,'utf8'),'out');assert.equal(fs.readFileSync(negative.stderr.path,'utf8'),'err');verifyReceipt(path.join(dir,'negative.json'));
  fs.appendFileSync(negative.stdout.path,'mutation');assert.throws(()=>verifyReceipt(path.join(dir,'negative.json')));
  const timeout=await record(dir,'timeout',[process.execPath,'-e','setInterval(()=>{},100)'],{timeout:100});assert.equal(timeout.timedOut,true);assert.equal(timeout.signal,'SIGKILL');assert.throws(()=>verifyReceipt(path.join(dir,'timeout.json')));
 }finally{fs.rmSync(dir,{recursive:true});}
});
test('explicit CLI timeout is parsed and invalid limits fail before execution',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ape-record-cli-')),cp=require('node:child_process');
 try{
  const result=cp.spawnSync(process.execPath,[path.join(__dirname,'record.cjs'),dir,'cli','--timeout-ms','100',process.execPath,'-e','setInterval(()=>{},100)'],{encoding:'utf8'});assert.equal(result.status,1);const receipt=JSON.parse(fs.readFileSync(path.join(dir,'cli.json')));assert.equal(receipt.timeoutMs,100);assert.equal(receipt.timedOut,true);assert.equal(receipt.signal,'SIGKILL');
  const invalid=cp.spawnSync(process.execPath,[path.join(__dirname,'record.cjs'),dir,'invalid','--timeout-ms','invalid',process.execPath],{encoding:'utf8'});assert.notEqual(invalid.status,0);assert.equal(fs.existsSync(path.join(dir,'invalid.json')),false);
 }finally{fs.rmSync(dir,{recursive:true});}
});
