// F1 only: separate Java program; never compiles INTERLIS sources.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {readLock,verifyTree}=require('./toolchain.cjs'),{record,hash}=require('./record.cjs');
async function probe(lockFile){
 const root=path.resolve(__dirname,'../..'),lock=readLock(lockFile),output=path.join(root,'target/compiler-ape');
 const installed=path.join(output,'toolchains','installed-'+hash(lockFile));
 const inventory=JSON.parse(fs.readFileSync(path.join(installed,'inventory.json')));assert.equal(inventory.lockSha256,hash(lockFile));
 assert.equal(require('node:crypto').createHash('sha256').update(JSON.stringify(inventory.inventories)).digest('hex'),lock.installedTreeSha256,'Changed vendor inventory');
 for(const [id,files] of Object.entries(inventory.inventories))assert.deepEqual(verifyTree(path.join(installed,id)),files,'Changed/missing/extra vendor files');
 const g='cosmogvm/graalvm-b0d7b51f55-java25-25.2.4-dev',c='cosmocc/cosmocc',graal='/vendor/'+g,cosmo='/vendor/'+c;
 const identities={};for(const tool of lock.requiredTools){const relative=(['bin/native-image','bin/java','bin/javac'].includes(tool)?g:c)+'/'+tool;assert.ok(fs.existsSync(path.join(installed,relative)),'Missing tool '+tool);identities[relative]=hash(path.join(installed,relative));}
 const run=fs.mkdtempSync(path.join(output,'probe-'));fs.mkdirSync(path.join(run,'classes'));
 fs.copyFileSync(path.join(root,'build/interlis/ape/probes/ToolchainProbe.java'),path.join(run,'ToolchainProbe.java'));
 fs.copyFileSync(path.join(root,'build/interlis/ape/probes/probe.txt'),path.join(run,'classes/probe.txt'));
 const manifest={lockSha256:hash(lockFile),builderImage:lock.builderImage,emulation:'linux/amd64 on Docker Desktop aarch64',identities,sourceSha256:hash(path.join(run,'ToolchainProbe.java')),resourceSha256:hash(path.join(run,'classes/probe.txt')),results:[]};
 fs.writeFileSync(path.join(run,'probe.json'),JSON.stringify(manifest,null,2));
 const base=['docker','run','--rm','--platform','linux/amd64','--network','none','--read-only','--memory','7g','--cpus','4','--tmpfs','/tmp:rw,exec,size=2g','--mount','type=bind,src='+installed+',dst=/vendor,readonly','--mount','type=bind,src='+run+',dst=/work','--workdir','/work','--env','PATH='+cosmo+'/bin:'+graal+'/bin:/usr/bin:/bin','--env','COSMO_HOME='+cosmo,'--env','HOME=/tmp'];
 async function execute(name,program,args,timeout=120000){const r=await record(run,name,[...base,'--name',path.basename(run)+'-'+name,'--entrypoint',program,lock.builderImage,...args],{timeout});if(r.timedOut) { const cleanup=await record(run,name+'-cleanup',['docker','rm','--force',path.basename(run)+'-'+name]); assert.equal(cleanup.exitCode,0,'Own timed-out container cleanup failed'); } manifest.results.push({name,receipt:name+'.json',exitCode:r.exitCode,timedOut:r.timedOut,signal:r.signal});fs.writeFileSync(path.join(run,'probe.json'),JSON.stringify(manifest,null,2));return r;}
 let r=await execute('version',graal+'/bin/native-image',['--version']);assert.equal(r.exitCode,0);
 r=await execute('options',graal+'/bin/native-image',['--expert-options-all']);assert.equal(r.exitCode,0);
 r=await execute('javac',graal+'/bin/javac',['--release','25','-d','classes','ToolchainProbe.java']);assert.equal(r.exitCode,0);
 r=await execute('jvm-control',graal+'/bin/java',['-cp','classes','ToolchainProbe','/work']);assert.equal(r.exitCode,0);assert.ok(fs.readFileSync(r.stdout.path,'utf8').includes('PROBE_PASS'));
 for(const [arch,platform] of [['x86_64','amd64'],['aarch64','aarch64']]){
  for(const dir of ['graal-libs','labsjdk-libs','graal-caps'])assert.ok(fs.statSync(path.join(installed,c,dir,arch)).isDirectory(),'Missing '+dir+'/'+arch);
  const args=['-O0','-march=compatibility','--parallelism=2','-J-Xmx3g','-H:+UnlockExperimentalVMOptions','--libc=cosmo','-H:TargetPlatform=linux-'+platform,'-H:CLibraryPath='+cosmo+'/graal-libs/'+arch+','+cosmo+'/labsjdk-libs/'+arch,'-H:+UseCAPCache','-H:CAPCacheDir='+cosmo+'/graal-caps/'+arch,'-H:IncludeResources=probe.txt','-cp','classes','ToolchainProbe','probe.'+arch];
  r=await execute('native-'+arch,graal+'/bin/native-image',args,900000);
 }
 console.log('Probe evidence: '+run);
 // Nonzero build exits must never be represented as a completed capability gate.
 assert.ok(manifest.results.filter(r=>r.name.startsWith('native-')).every(r=>r.exitCode===0&&!r.timedOut),'Native capability gate failed; see '+run);
 await require('./finish-probe.cjs').finish(lockFile,run);
 return run;
}
module.exports={probe};
if(require.main===module){if(process.argv.length!==3)throw Error('Expected absolute lock');probe(process.argv[2]).catch(e=>{console.error(e);process.exitCode=1;});}
