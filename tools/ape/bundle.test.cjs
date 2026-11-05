const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {hash}=require('./record.cjs'),{loadCandidate,packageAndVerify}=require('./bundle.cjs');
test('bundle transport preserves exact inventory and executable mode; mutations fail',async()=>{
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'ape-bundle-'));
 try{
  // Synthetic bytes exercise inventory/transport only, never an APE runtime gate.
  const temporary=path.join(base,'temporary');fs.writeFileSync(temporary,'inventory fixture, not executable code');const sha256=hash(temporary),dir=path.join(base,sha256);fs.mkdirSync(dir);fs.mkdirSync(path.join(dir,'notices'));fs.renameSync(temporary,path.join(dir,'interlis-compiler.com'));fs.chmodSync(path.join(dir,'interlis-compiler.com'),0o755);fs.writeFileSync(path.join(dir,'notices/LICENSE'),'fixture notice');
  const binary=path.join(dir,'interlis-compiler.com'),notice=path.join(dir,'notices/LICENSE'),manifest=path.join(dir,'bundle-manifest.json');
  fs.writeFileSync(manifest,JSON.stringify({schemaVersion:'1.0',kind:'interlis-compiler-ape-bundle',sha256,mode:0o755,bytes:fs.statSync(binary).size,runtimeAdditions:[],notices:[{path:'notices/LICENSE',sha256:hash(notice),bytes:fs.statSync(notice).size}]}));
  loadCandidate(manifest);await packageAndVerify(manifest,path.join(base,'package'));
  fs.chmodSync(binary,0o644);assert.throws(()=>loadCandidate(manifest));fs.chmodSync(binary,0o755);
  fs.writeFileSync(path.join(dir,'extra.jar'),'foreign runtime');assert.throws(()=>loadCandidate(manifest));fs.unlinkSync(path.join(dir,'extra.jar'));
  fs.appendFileSync(binary,'changed');assert.throws(()=>loadCandidate(manifest));
  const data=JSON.parse(fs.readFileSync(manifest));data.sha256=hash(binary);data.bytes=fs.statSync(binary).size;fs.writeFileSync(manifest,JSON.stringify(data));assert.throws(()=>loadCandidate(manifest),'Rehash cannot preserve the original content address');
 }finally{fs.rmSync(base,{recursive:true});}
});
