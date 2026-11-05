// Native generation consumes frozen Jenesis JARs; no javac, resolver, Gradle or fat JAR.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {verifiedInstallation}=require('./toolchain.cjs'),{record,hash}=require('./record.cjs');
const root=path.resolve(__dirname,'../..'),output=path.join(root,'target/compiler-ape');
const digest=value=>crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
const NATIVE_CONFIGURATION={flags:['-Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.PollSelectorProvider','-O0','-march=compatibility','--parallelism=2','-J-Xmx5g','-H:+UnlockExperimentalVMOptions','--libc=cosmo'],target:'linux-{amd64|aarch64}',libraries:'graal-libs/{arch},labsjdk-libs/{arch}',capCache:'graal-caps/{arch}',environment:{HOME:'/tmp',network:'none'},resources:[],reflection:[],initialization:[]};
function inputs(file){
 assert.ok(path.isAbsolute(file));const data=JSON.parse(fs.readFileSync(file));assert.equal(data.kind,'interlis-compiler-ape-inputs');assert.equal(data.mainClass,'guru.interlis.cli.Main');
 assert.equal(data.orderedRuntime.length,7);const names=new Set();
 for(const jar of data.orderedRuntime){assert.match(jar.path,/^runtime\/[A-Za-z0-9_.-]+\.jar$/);assert.ok(!names.has(jar.path));names.add(jar.path);const target=path.join(path.dirname(file),jar.path);assert.ok(fs.realpathSync(target).startsWith(fs.realpathSync(path.dirname(file))+path.sep));assert.equal(hash(target),jar.sha256);assert.equal(fs.statSync(target).size,jar.bytes);}
 assert.deepEqual(fs.readdirSync(path.join(path.dirname(file),'runtime')).sort(),[...names].map(n=>n.slice(8)).sort());return data;
}
function sourceUnchanged(data){for(const [relative,expected] of Object.entries(data.buildInputs)){const file=path.resolve(root,relative);assert.ok(file.startsWith(root+path.sep));assert.equal(hash(file),expected,'Source drift: '+relative);}}
function container(tools,run,extra=[]){return ['docker','run','--rm','--platform','linux/amd64','--network','none','--read-only','--memory','7g','--cpus','4','--tmpfs','/tmp:rw,exec,size=2g','--env','HOME=/tmp','--env','PATH='+tools.cosmo+'/bin:'+tools.graal+'/bin:/usr/bin:/bin','--env','COSMO_HOME='+tools.cosmo,'--mount','type=bind,src='+tools.installed+',dst=/vendor,readonly','--mount','type=bind,src='+run+',dst=/work','--workdir','/work',...extra];}
function nativeArguments(data,arch,cosmo){
 const classpath=data.orderedRuntime.map(j=>'/inputs/'+j.path).join(':');return [...NATIVE_CONFIGURATION.flags,'-H:TargetPlatform=linux-'+(arch==='x86_64'?'amd64':'aarch64'),'-H:CLibraryPath='+cosmo+'/graal-libs/'+arch+','+cosmo+'/labsjdk-libs/'+arch,'-H:+UseCAPCache','-H:CAPCacheDir='+cosmo+'/graal-caps/'+arch,'-cp',classpath,data.mainClass,'compiler'];
}
async function build(manifestFile,arch,lockFile){
 assert.ok(['x86_64','aarch64'].includes(arch));const data=inputs(manifestFile);sourceUnchanged(data);
 const tools=verifiedInstallation(lockFile,path.join(output,'toolchains'));
 const base=path.join(output,'builds');fs.mkdirSync(base,{recursive:true});const run=fs.mkdtempSync(path.join(base,arch+'-'));
 const common={inputManifestSha256:hash(manifestFile),inputDigest:data.productInputDigest,lockSha256:hash(lockFile),nativeConfigurationSha256:digest(NATIVE_CONFIGURATION),mainClass:data.mainClass,optimization:'O0',heap:'5g',parallelism:2,cpu:'compatibility'};
 const args=nativeArguments(data,arch,tools.cosmo);
 const buildKey=digest({common,arch,args}),name='ape-'+arch+'-'+path.basename(run);
 const command=[...container(tools,run,['--name',name,'--mount','type=bind,src='+path.dirname(manifestFile)+',dst=/inputs,readonly']),'--entrypoint',tools.graal+'/bin/native-image',tools.lock.builderImage,...args];
 fs.writeFileSync(path.join(run,'plan.json'),JSON.stringify({common,arch,buildKey,command,args,orchestratorSourceSha256:hash(__filename)},null,2));
 const result=await record(run,'native-image',command,{timeout:1800000});
 if(result.timedOut){const cleanup=await record(run,'timeout-cleanup',['docker','rm','--force',name]);assert.equal(cleanup.exitCode,0);}
 assert.equal(result.exitCode,0,'Native compiler build failed; see '+run);assert.equal(result.signal,null);assert.equal(result.timedOut,false);
 inputs(manifestFile);sourceUnchanged(data);assert.equal(hash(manifestFile),common.inputManifestSha256);
 const binary=path.join(run,'compiler');assert.ok(fs.statSync(binary).size>0);
 const slice={kind:'interlis-compiler-native-slice',schemaVersion:'1.0',compilerInputs:manifestFile,common,commonDigest:digest(common),arch,buildKey,binary,sha256:hash(binary),bytes:fs.statSync(binary).size,planSha256:hash(path.join(run,'plan.json')),processReceipt:path.join(run,'native-image.json')};
 fs.writeFileSync(path.join(run,'slice.json'),JSON.stringify(slice,null,2),{flag:'wx'});sealSlice(path.join(run,'slice.json'));console.log('Native compiler slice: '+path.join(run,'slice.json'));return slice;
}
function producer(file){
 const slice=JSON.parse(fs.readFileSync(file)),directory=path.dirname(file),planFile=path.join(directory,'plan.json'),receiptFile=path.join(directory,'native-image.json');
 assert.equal(slice.binary,path.join(directory,'compiler'),'Foreign slice binary path');assert.equal(slice.processReceipt,receiptFile);
 assert.equal(hash(planFile),slice.planSha256);const plan=JSON.parse(fs.readFileSync(planFile));assert.deepEqual(plan.common,slice.common);assert.equal(plan.arch,slice.arch);assert.equal(plan.buildKey,slice.buildKey);assert.equal(slice.buildKey,digest({common:slice.common,arch:slice.arch,args:plan.args}));
 const receipt=JSON.parse(fs.readFileSync(receiptFile));for(const stream of ['stdout','stderr'])assert.equal(receipt[stream].path,path.join(directory,'native-image.'+stream),'Foreign producer stream');
 require('./evidence.cjs').verifyReceipt(receiptFile);assert.equal(receipt.exitCode,0);assert.deepEqual(receipt.argv,plan.command);
 assert.equal(hash(slice.binary),slice.sha256);assert.equal(fs.statSync(slice.binary).size,slice.bytes);return {slice,plan,receipt,receiptFile};
}
function sealSlice(file){
 const {slice,receiptFile}=producer(file);const seal={kind:'native-producer-seal',createdAt:new Date().toISOString(),sliceManifestSha256:hash(file),binarySha256:hash(slice.binary),planSha256:slice.planSha256,processReceiptSha256:hash(receiptFile)};
 fs.writeFileSync(path.join(path.dirname(file),'producer-seal.json'),JSON.stringify(seal,null,2),{flag:'wx'});return seal;
}
function readSlice(file,arch,lockFile){
 const {slice,receiptFile}=producer(file);const seal=JSON.parse(fs.readFileSync(path.join(path.dirname(file),'producer-seal.json')));assert.equal(seal.kind,'native-producer-seal');assert.equal(seal.sliceManifestSha256,hash(file));assert.equal(seal.binarySha256,hash(slice.binary));assert.equal(seal.processReceiptSha256,hash(receiptFile));assert.equal(seal.planSha256,slice.planSha256);assert.equal(slice.kind,'interlis-compiler-native-slice');assert.equal(slice.schemaVersion,'1.0');assert.equal(slice.arch,arch);assert.equal(slice.commonDigest,digest(slice.common));assert.equal(slice.common.lockSha256,hash(lockFile));assert.equal(slice.common.nativeConfigurationSha256,digest(NATIVE_CONFIGURATION));assert.equal(hash(slice.binary),slice.sha256);assert.equal(fs.statSync(slice.binary).size,slice.bytes);assert.equal(hash(slice.compilerInputs),slice.common.inputManifestSha256);const data=inputs(slice.compilerInputs);assert.equal(slice.common.inputDigest,data.productInputDigest);assert.deepEqual(producer(file).plan.args,nativeArguments(data,arch,'/vendor/cosmocc/cosmocc'),'Unexpected native recipe');sourceUnchanged(data);return slice;
}
async function fuse(x64File,armFile,lockFile){
 const x64=readSlice(x64File,'x86_64',lockFile),arm=readSlice(armFile,'aarch64',lockFile);assert.equal(x64.commonDigest,arm.commonDigest,'Mixed native inputs');
 const tools=verifiedInstallation(lockFile,path.join(output,'toolchains')),base=path.join(output,'builds');const run=fs.mkdtempSync(path.join(base,'fused-'));
 for(const slice of [x64,arm])fs.copyFileSync(slice.binary,path.join(run,'compiler.'+slice.arch));
 async function tool(name,program,args){const containerName='ape-fuse-'+path.basename(run)+'-'+name;const r=await record(run,name,[...container(tools,run,['--name',containerName]),'--entrypoint','/bin/sh',tools.lock.builderImage,tools.cosmo+'/bin/'+program,...args]);if(r.timedOut){const cleanup=await record(run,name+'-cleanup',['docker','rm','--force',containerName]);assert.equal(cleanup.exitCode,0);}assert.equal(r.exitCode,0);assert.equal(r.signal,null);assert.equal(r.timedOut,false);}
 for(const slice of [x64,arm]){await tool('fixup-'+slice.arch,'fixupobj',['compiler.'+slice.arch]);assert.equal(hash(slice.binary),slice.sha256,'Original slice changed');}
 await tool('apelink','apelink',['-l',tools.cosmo+'/bin/ape-x86_64.elf','-l',tools.cosmo+'/bin/ape-aarch64.elf','-M',tools.cosmo+'/bin/ape-m1.c','-o','interlis-compiler.com','compiler.x86_64','compiler.aarch64']);
 const loaders=await require('./bundle.cjs').prepareLoaders(run,tools);
 const binary=path.join(run,'interlis-compiler.com');fs.chmodSync(binary,0o755);const sha256=hash(binary),candidates=path.join(output,'candidates');fs.mkdirSync(candidates,{recursive:true});const destination=path.join(candidates,sha256);assert.ok(!fs.existsSync(destination),'Candidate already exists; never overwrite');
 const staging=fs.mkdtempSync(path.join(candidates,'.freezing-'));fs.copyFileSync(binary,path.join(staging,'interlis-compiler.com'));fs.chmodSync(path.join(staging,'interlis-compiler.com'),0o755);
 for(const loader of loaders){fs.copyFileSync(path.join(run,loader.path),path.join(staging,loader.path));fs.chmodSync(path.join(staging,loader.path),loader.mode);}
 const notices=require('./bundle.cjs').writeNotices(staging,tools.installed);
 const bundle={notices,runtimeAdditions:loaders,schemaVersion:'1.0',kind:'interlis-compiler-ape-bundle',sha256,bytes:fs.statSync(binary).size,mode:0o755,common:x64.common,slices:[{manifest:x64File,sha256:x64.sha256},{manifest:armFile,sha256:arm.sha256}],fusionRun:run,platformEvidence:[],status:'UNTESTED'};
 fs.writeFileSync(path.join(staging,'bundle-manifest.json'),JSON.stringify(bundle,null,2));assert.equal(hash(path.join(staging,'interlis-compiler.com')),sha256);fs.renameSync(staging,destination);require('./bundle.cjs').loadCandidate(path.join(destination,'bundle-manifest.json'));console.log('Untested compiler APE candidate: '+destination);return destination;
}
module.exports={build,fuse,inputs,readSlice,sealSlice};
if(require.main===module){const [operation,a,b,lock,...extra]=process.argv.slice(2);assert.equal(extra.length,0);(operation==='build'?build(a,b,lock):operation==='fuse'?fuse(a,b,lock):Promise.reject(Error('Expected build or fuse'))).catch(e=>{console.error(e);process.exitCode=1;});}
