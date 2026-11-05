// Explicit acquisition only. No extraction or execution of unverified vendor bytes.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {record,hash}=require('./record.cjs');
function readLock(file){
 assert.ok(path.isAbsolute(file),'Absolute lock required');const lock=JSON.parse(fs.readFileSync(file));
 assert.equal(lock.schemaVersion,'1.0');assert.equal(lock.builderPlatform,'linux-x86_64');
 assert.match(lock.reference,/^https:\/\/github.com\/elide-dev\/madura\/tree\/[a-f0-9]{40}$/);
 assert.match(lock.builderImage,/^sha256:[a-f0-9]{64}$/);
 assert.match(lock.installedTreeSha256,/^[a-f0-9]{64}$/);
 assert.deepEqual(lock.archives.map(a=>a.id),['cosmogvm','cosmocc']);
 for(const a of lock.archives){assert.match(a.sha256,/^[a-f0-9]{64}$/);assert.ok(a.version&&!a.version.includes('latest'));assert.equal(new URL(a.uri).protocol,'https:');assert.ok(['tar.gz','zip'].includes(a.format));}
 assert.deepEqual(lock.requiredTools,['bin/native-image','bin/java','bin/javac','bin/cosmocc','bin/cosmoc++','bin/fixupobj','bin/apelink','bin/ape-x86_64.elf','bin/ape-aarch64.elf','bin/ape-m1.c']);return lock;
}
function verifyArchive(file,pin){const actual=hash(file);assert.equal(actual,pin.sha256,'Vendor archive SHA-256 mismatch: '+pin.id);return actual;}
async function acquire(lockFile,output){
 const lock=readLock(lockFile);fs.mkdirSync(output,{recursive:true});const run=fs.mkdtempSync(path.join(output,'acquire-')),results=[];
 for(const pin of lock.archives){
  const target=path.join(output,pin.sha256+'.'+pin.format);let receipt=null,reused=false,file=target;
  if(fs.existsSync(target)){verifyArchive(target,pin);reused=true;}
  else {file=path.join(run,pin.id+'.partial');receipt=await record(run,pin.id,['/usr/bin/curl','--fail','--location','--silent','--show-error','--proto','=https','--proto-redir','=https','--connect-timeout','20','--max-time','600','--max-filesize','2147483648','--output',file,pin.uri],{timeout:610000});}
  const actual=fs.existsSync(file)?hash(file):null;
  const passed=(reused||receipt.exitCode===0&&!receipt.timedOut)&&actual===pin.sha256;
  results.push({id:pin.id,expectedSha256:pin.sha256,actualSha256:actual,bytes:fs.existsSync(file)?fs.statSync(file).size:null,status:passed?'PASSED':'FAILED',reused,receipt,file});
  fs.writeFileSync(path.join(run,'acquisition.json'),JSON.stringify({lockFile,lockSha256:hash(lockFile),results},null,2));
  assert.ok(passed,'Archive acquisition/pin failed for '+pin.id+'; see '+run);
  if(!reused)fs.renameSync(file,target);
 }
 console.log('Verified archives: '+run);return run;
}
module.exports={readLock,verifyArchive,acquire};
if(require.main===module){const [lockFile,output,...extra]=process.argv.slice(2);if(extra.length||!output)throw Error('Expected absolute lock and isolated archive directory');acquire(lockFile,path.join(output,'archives')).then(()=>install(lockFile,output)).catch(e=>{console.error(e);process.exitCode=1;});}

function validateEntry(name){
 assert.ok(name&&!name.startsWith('/')&&!name.includes('\\')&&!name.includes(':')&&!/[\x00-\x1f]/.test(name),'Unsafe archive entry');
 assert.ok(!name.split('/').includes('..'),'Archive traversal');
}
function verifyTree(directory){
 const base=fs.realpathSync(directory),files=[];
 function walk(dir){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){
  const file=path.join(dir,entry.name),real=fs.realpathSync(file);
  assert.ok(real===base||real.startsWith(base+path.sep),'External archive symlink');
  if(entry.isDirectory())walk(file);else files.push({path:path.relative(directory,file),sha256:hash(file),symlink:entry.isSymbolicLink()?fs.readlinkSync(file):null});
 }}walk(directory);return files;
}
async function install(lockFile,output){
 const lock=readLock(lockFile),destination=path.join(output,'installed-'+hash(lockFile));
 assert.ok(!fs.existsSync(destination),'Existing installation must not be overwritten');
 const work=fs.mkdtempSync(path.join(output,'.installing-')),inventories={};
 for(const pin of lock.archives){
  const archive=path.join(output,'archives',pin.sha256+'.'+pin.format);verifyArchive(archive,pin);
  const list=await record(work,pin.id+'-list',['/usr/bin/tar','-tf',archive]);assert.equal(list.exitCode,0);
  const entries=fs.readFileSync(list.stdout.path,'utf8').trim().split('\n');entries.forEach(validateEntry);
  assert.equal(new Set(entries).size,entries.length,'Duplicate archive entries');
  const dir=path.join(work,pin.id);fs.mkdirSync(dir);
  // macOS libarchive tar defaults reject absolute paths, .. and writes through symlinks.
  const extract=await record(work,pin.id+'-extract',['/usr/bin/tar','-xf',archive,'-C',dir],{timeout:180000});assert.equal(extract.exitCode,0);
  inventories[pin.id]=verifyTree(dir);
 }
 assert.equal(require('node:crypto').createHash('sha256').update(JSON.stringify(inventories)).digest('hex'),lock.installedTreeSha256,'Installed vendor tree differs from pinned verified inventory');
 fs.writeFileSync(path.join(work,'inventory.json'),JSON.stringify({lockSha256:hash(lockFile),inventories},null,2));
 fs.renameSync(work,destination);console.log('Installed verified archives: '+destination);return destination;
}
module.exports.install=install;module.exports.validateEntry=validateEntry;module.exports.verifyTree=verifyTree;

function verifiedInstallation(lockFile,output){
 const lock=readLock(lockFile),installed=path.join(output,'installed-'+hash(lockFile));
 const inventory=JSON.parse(fs.readFileSync(path.join(installed,'inventory.json')));
 assert.equal(inventory.lockSha256,hash(lockFile));
 assert.equal(require('node:crypto').createHash('sha256').update(JSON.stringify(inventory.inventories)).digest('hex'),lock.installedTreeSha256,'Changed vendor inventory');
 for(const [id,files] of Object.entries(inventory.inventories))assert.deepEqual(verifyTree(path.join(installed,id)),files,'Changed/missing/extra vendor files');
 const graal='/vendor/cosmogvm/graalvm-b0d7b51f55-java25-25.2.4-dev',cosmo='/vendor/cosmocc/cosmocc';
 for(const tool of lock.requiredTools){
  const base=['bin/native-image','bin/java','bin/javac'].includes(tool)?graal:cosmo;
  const file=path.join(installed,base.slice('/vendor/'.length),tool);assert.ok(fs.statSync(file).isFile(),'Missing tool file: '+tool);
  fs.accessSync(file,tool.endsWith('.c')?fs.constants.R_OK:fs.constants.X_OK);
 }
 return {lock,installed,graal,cosmo};
}
module.exports.verifiedInstallation=verifiedInstallation;
