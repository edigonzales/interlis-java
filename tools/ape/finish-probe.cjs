// Complete F1 on immutable slices, preserving original native outputs.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {readLock}=require('./toolchain.cjs'),{record,hash}=require('./record.cjs');
async function finish(lockFile,run){
 const lock=readLock(lockFile),root=path.resolve(__dirname,'../..'),installed=path.join(root,'target/compiler-ape/toolchains','installed-'+hash(lockFile));
 const original=JSON.parse(fs.readFileSync(path.join(run,'probe.json')));const originalInventory=JSON.parse(fs.readFileSync(path.join(root,'target/compiler-ape/toolchains','installed-'+original.lockSha256,'inventory.json')));
 assert.equal(originalInventory.lockSha256,original.lockSha256);
 assert.equal(require('node:crypto').createHash('sha256').update(JSON.stringify(originalInventory.inventories)).digest('hex'),lock.installedTreeSha256,'Original slices use different vendor bytes');
 for(const [relative,expected] of Object.entries(original.identities))assert.equal(hash(path.join(installed,relative)),expected,'Different fusion tools');
 for(const arch of ['x86_64','aarch64']){const r=JSON.parse(fs.readFileSync(path.join(run,'native-'+arch+'.json')));assert.equal(r.exitCode,0);assert.equal(r.timedOut,false);assert.equal(r.signal,null);assert.ok(fs.statSync(path.join(run,'probe.'+arch)).size>0);}
 const output=fs.mkdtempSync(path.join(path.dirname(run),'fused-probe-')),raw={};
 for(const arch of ['x86_64','aarch64']){raw[arch]=hash(path.join(run,'probe.'+arch));fs.copyFileSync(path.join(run,'probe.'+arch),path.join(output,'probe.'+arch));}
 const common=['docker','run','--rm','--platform','linux/amd64','--network','none','--read-only','--tmpfs','/tmp:rw,exec,size=1g','--env','HOME=/tmp','--mount','type=bind,src='+installed+',dst=/vendor,readonly','--mount','type=bind,src='+output+',dst=/work','--workdir','/work','--entrypoint','/bin/sh',lock.builderImage];
 const steps=[];
 for(const arch of ['x86_64','aarch64']){const r=await record(output,'fixup-'+arch,[...common,'/vendor/cosmocc/cosmocc/bin/fixupobj','probe.'+arch]);assert.equal(r.exitCode,0);steps.push(r);assert.equal(hash(path.join(run,'probe.'+arch)),raw[arch]);}
 const r=await record(output,'apelink',[...common,'/vendor/cosmocc/cosmocc/bin/apelink','-l','/vendor/cosmocc/cosmocc/bin/ape-x86_64.elf','-l','/vendor/cosmocc/cosmocc/bin/ape-aarch64.elf','-M','/vendor/cosmocc/cosmocc/bin/ape-m1.c','-o','probe.com','probe.x86_64','probe.aarch64']);assert.equal(r.exitCode,0);steps.push(r);
 const binary=path.join(output,'probe.com');fs.chmodSync(binary,0o755);const sha256=hash(binary);
 const runResult=await record(output,'run-emulated-linux-x64',['docker','run','--rm','--platform','linux/amd64','--network','none','--read-only','--tmpfs','/tmp:rw,exec,size=1g','--env','HOME=/tmp','--mount','type=bind,src='+output+',dst=/work','--workdir','/work','--entrypoint','/bin/sh',lock.builderImage,'/work/probe.com','/work']);
 assert.equal(runResult.exitCode,0);assert.ok(fs.readFileSync(runResult.stdout.path,'utf8').includes('PROBE_PASS Grüezi 🌍 resource/file/thread/exception'));
 assert.equal(hash(binary),sha256);
 const hostRuns=[];
 if(process.platform==='darwin'&&process.arch==='arm64'){
  const home=path.join(output,'private-home'),temp=path.join(output,'private-temp');fs.mkdirSync(home);fs.mkdirSync(temp);
  const env={PATH:'/usr/bin:/bin:/usr/sbin:/sbin',HOME:home,TMPDIR:temp,LANG:'en_US.UTF-8'};
  hostRuns.push(await record(output,'macos-direct',[binary,output],{env,timeout:60000}));
  hostRuns.push(await record(output,'macos-shell',['/bin/sh',binary,output],{env,timeout:60000}));
  assert.equal(hash(binary),sha256);
 }
 fs.writeFileSync(path.join(output,'gate.json'),JSON.stringify({status:'PASSED',scope:'F1 Java probe only; emulated Linux x64 execution is not a native target-platform proof',sourceRun:run,lockSha256:hash(lockFile),rawSlices:raw,sha256,steps:steps.map(r=>r.argv),runReceipt:runResult,hostRuns,originalLockSha256:original.lockSha256},null,2));console.log('F1_PROBE_GATE_PASS '+output);return output;
}
module.exports={finish};
if(require.main===module)finish(process.argv[2],process.argv[3]).catch(e=>{console.error(e);process.exitCode=1;});
