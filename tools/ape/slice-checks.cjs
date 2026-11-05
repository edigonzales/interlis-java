// Scratch copies only. Tests integrity validation using a real completed native producer.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {readSlice,sealSlice}=require('./compiler-native.cjs'),{hash}=require('./record.cjs');
function checks(original,lockFile,out){
 assert.ok(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});const originalDirectory=path.dirname(original),source=readSlice(original,'x86_64',lockFile);
 const originals=Object.fromEntries(['compiler','slice.json','plan.json','native-image.json','native-image.stdout','native-image.stderr','producer-seal.json'].map(f=>[f,hash(path.join(originalDirectory,f))]));
 const results=[];
 for(const mode of ['control','binary','rehash','plan','mixed-input']){
  const dir=path.join(out,mode);fs.cpSync(originalDirectory,dir,{recursive:true,errorOnExist:true,force:false});
  const file=path.join(dir,'slice.json'),data=JSON.parse(fs.readFileSync(file));data.binary=path.join(dir,'compiler');data.processReceipt=path.join(dir,'native-image.json');fs.writeFileSync(file,JSON.stringify(data,null,2));
  const receipt=JSON.parse(fs.readFileSync(data.processReceipt));for(const stream of ['stdout','stderr'])receipt[stream].path=path.join(dir,'native-image.'+stream);fs.writeFileSync(data.processReceipt,JSON.stringify(receipt,null,2));
  fs.unlinkSync(path.join(dir,'producer-seal.json'));sealSlice(file);readSlice(file,'x86_64',lockFile);
  if(mode==='control'){results.push({id:mode,status:'PASSED',observation:'Relocated real bytes and producer logs accepted before mutation'});continue;}
  if(mode==='binary'||mode==='rehash'){fs.appendFileSync(data.binary,'mutation');if(mode==='rehash'){data.sha256=hash(data.binary);data.bytes=fs.statSync(data.binary).size;fs.writeFileSync(file,JSON.stringify(data,null,2));}}
  if(mode==='plan'){const plan=JSON.parse(fs.readFileSync(path.join(dir,'plan.json')));plan.args.push('-H:IncludeResources=foreign');fs.writeFileSync(path.join(dir,'plan.json'),JSON.stringify(plan,null,2));}
  if(mode==='mixed-input'){data.common.inputManifestSha256='0'.repeat(64);fs.writeFileSync(file,JSON.stringify(data,null,2));}
  let message;try{readSlice(file,'x86_64',lockFile);}catch(error){message=error.message;}assert.ok(message,'Mutation was accepted: '+mode);results.push({id:mode,status:'PASSED',observation:'Rejected: '+message});
 }
 for(const [file,expected] of Object.entries(originals))assert.equal(hash(path.join(originalDirectory,file)),expected,'Original producer changed');
 fs.writeFileSync(path.join(out,'checks.json'),JSON.stringify({status:'PASSED',sourceBinarySha256:source.sha256,originalHashes:originals,results},null,2));console.log('SLICE_CHECKS_PASS');
}
module.exports={checks};if(require.main===module)checks(...process.argv.slice(2));
