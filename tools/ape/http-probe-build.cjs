const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {verifiedInstallation}=require('./toolchain.cjs'),{record,hash}=require('./record.cjs');
async function build(lockFile,provider,sourceClass='HttpProbe'){
 assert.ok(['HttpProbe','ClockProbe'].includes(sourceClass));const nameStem=sourceClass==='ClockProbe'?'clock-probe':'http-probe';
 const root=path.resolve(__dirname,'../..'),tools=verifiedInstallation(lockFile,path.join(root,'target/compiler-ape/toolchains')),dir=fs.mkdtempSync(path.join(root,'target/compiler-ape/'+nameStem+'-'));
 fs.copyFileSync(path.join(__dirname,'probes/'+sourceClass+'.java'),path.join(dir,sourceClass+'.java'));fs.mkdirSync(path.join(dir,'classes'));
 const base=['docker','run','--rm','--platform','linux/amd64','--network','none','--read-only','--memory','7g','--cpus','4','--tmpfs','/tmp:rw,exec,size=2g','--env','HOME=/tmp','--env','PATH='+tools.cosmo+'/bin:'+tools.graal+'/bin:/usr/bin:/bin','--mount','type=bind,src='+tools.installed+',dst=/vendor,readonly','--mount','type=bind,src='+dir+',dst=/work','--workdir','/work'];
 let r=await record(dir,'javac',[...base,'--entrypoint',tools.graal+'/bin/javac',tools.lock.builderImage,'--release','25','-d','classes',sourceClass+'.java']);assert.equal(r.exitCode,0);
 const args=[...(provider?['-Djava.nio.channels.spi.SelectorProvider='+provider]:[]),'-O0','-march=compatibility','--parallelism=2','-J-Xmx3g','-H:+UnlockExperimentalVMOptions','--libc=cosmo','-H:TargetPlatform=linux-aarch64','-H:CLibraryPath='+tools.cosmo+'/graal-libs/aarch64,'+tools.cosmo+'/labsjdk-libs/aarch64','-H:+UseCAPCache','-H:CAPCacheDir='+tools.cosmo+'/graal-caps/aarch64','-cp','classes',sourceClass,nameStem];
 const name='ape-http-'+path.basename(dir);r=await record(dir,'native',[...base,'--name',name,'--entrypoint',tools.graal+'/bin/native-image',tools.lock.builderImage,...args],{timeout:900000});if(r.timedOut)await record(dir,'cleanup',['docker','rm','--force',name]);assert.equal(r.exitCode,0);assert.equal(r.timedOut,false);
 fs.writeFileSync(path.join(dir,'probe.json'),JSON.stringify({scope:'minimal Java runtime diagnostic only; not compiler delivery',sourceSha256:hash(path.join(dir,sourceClass+'.java')),lockSha256:hash(lockFile),binarySha256:hash(path.join(dir,nameStem)),args},null,2));console.log(dir);
}
if(require.main===module)build(process.argv[2],process.argv[3],process.argv[4]).catch(e=>{console.error(e);process.exitCode=1;});
