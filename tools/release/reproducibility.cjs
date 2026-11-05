'use strict';
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process'),crypto=require('node:crypto');
const root=process.cwd(),output=require('../verification/layout.cjs').report(root,'p3/P3-5/reproducibility'),hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const git=(...args)=>cp.execFileSync('git',args,{cwd:root,encoding:'utf8'}).trim();
const {compareBuilds,validateBuild}=require('./artifact-comparison.cjs');
const {distributionFiles}=require('./distribution-files.cjs');
async function run(cwd,command,args,log){
 return new Promise((resolve,reject)=>{const fd=fs.openSync(log,'w'),env={...process.env};delete env.VERIFICATION_LAYOUT;delete env.VERIFICATION_LAYOUT_SHA256;const child=cp.spawn(command,args,{cwd,env,stdio:['ignore',fd,fd]});child.on('error',reject);child.on('exit',(code,signal)=>{fs.closeSync(fd);code===0?resolve():reject(Error('repro build failed: '+command+' exit='+code+' signal='+signal+'; '+log));});});
}
(async()=>{
 if(process.platform==='win32')throw Error('This two-clean-build profile runs on the documented macOS reference host; Windows has a separate SDK process gate');
 if(git('status','--porcelain'))throw Error('Reproducibility requires a clean committed candidate');
 const candidate=git('rev-parse','HEAD'),sourceFingerprint=require('../performance/profile.cjs').fingerprint();fs.mkdirSync(output,{recursive:true});fs.rmSync(path.join(output,'result.json'),{force:true});const temp=fs.mkdtempSync(path.join(os.tmpdir(),'interlis-p3-repro-'));const manifests=[];
 try{
  for(const name of ['first','second']){
   const cwd=path.join(temp,name),record=path.join(output,name);fs.mkdirSync(record,{recursive:true});git('worktree','add','--detach',cwd,candidate);
   try{
    await run(path.join(cwd,'interlis-wasm-teavm'),'npm',['ci'],path.join(record,'npm-ci.log'));
    const java=path.join(process.env.JAVA_HOME||'', 'bin/java');if(!path.isAbsolute(java))throw Error('Select JAVA_HOME for clean JVM generations');
    await run(cwd,java,['build/InterlisJvmBuild.java','sdk-package'],path.join(record,'jvm-build.log'));
    await run(cwd,java,['build/InterlisJvmBuild.java','wasm-export'],path.join(record,'snapshot.log'));
    const snapshot=JSON.parse(fs.readFileSync(path.join(cwd,'target/jenesis-jvm/reports/wasm-export.json')));
    for(const profile of ['teavm-contracts','browser-prepare'])await run(cwd,java,['build/InterlisJvmBuild.java','wasm','--profile',profile,'--inputs',snapshot.manifest,'--inputs-sha256',snapshot.manifestSha256],path.join(record,profile+'.log'));
    if(cp.execFileSync('git',['status','--porcelain'],{cwd,encoding:'utf8'}).trim())throw Error('Clean build changed tracked inputs; checkout retained at '+cwd);
    const childLayout=path.join(cwd,'target/jenesis-jvm/delivery/layout.properties'), childInputs=require('../verification/layout.cjs').readVerificationInputs(cwd,childLayout,hash(fs.readFileSync(childLayout)));
    const files=distributionFiles(cwd,childInputs).map(file=>path.join(cwd,file));
    const manifest={};for(const file of files){const key=path.relative(cwd,file).split(path.sep).join('/');const bytes=fs.readFileSync(file);manifest[key]={sha256:hash(bytes),bytes:bytes.length,mode:fs.statSync(file).mode&0o777};const copy=path.join(record,'artifacts',key);fs.mkdirSync(path.dirname(copy),{recursive:true});fs.writeFileSync(copy,bytes);}
    fs.writeFileSync(path.join(record,'artifacts.json'),JSON.stringify(manifest,null,2)+'\n');const read=file=>JSON.parse(fs.readFileSync(path.join(cwd,file),'utf8'));
    const build={artifacts:manifest,browserManifest:read('interlis-wasm-teavm/build/web/build-manifest.json'),proofs:{js:read('interlis-wasm-teavm/build/js-contract-result.json'),wasm:read('interlis-wasm-teavm/build/wasm-contract-result.json')}};
    fs.writeFileSync(path.join(record,'build-evidence.json'),JSON.stringify(build,null,2)+'\n');validateBuild(build,candidate,sourceFingerprint);manifests.push(build);
   }finally{
    if(!cp.execFileSync('git',['status','--porcelain'],{cwd,encoding:'utf8'}).trim())git('worktree','remove',cwd);
   }
  }
  const keys=Object.keys(manifests[0].artifacts);compareBuilds(...manifests);
  if(git('rev-parse','HEAD')!==candidate||git('status','--porcelain')||require('../performance/profile.cjs').fingerprint()!==sourceFingerprint)throw Error('Candidate changed during two-clean-build verification');
  const result={status:'passed',candidate,sourceFingerprint,node:process.version,os:process.platform,arch:process.arch,cleanBuilds:2,shippedFiles:keys.length,artifacts:manifests[0].artifacts,builds:manifests,scope:'Two separate clean checkouts, independent Java/ANTLR/TeaVM/TypeScript generation. All shipped files inventoried; JVM/SDK, copied runtime and browser tools byte-identical. Exact TeaVM program paths and their manifest hash/size fields may differ. Each build has its own functional JS/Wasm proofs; no native OS-image claim.'};
  fs.writeFileSync(path.join(output,'result.json'),JSON.stringify(result,null,2)+'\n');console.log('SDK_REPRODUCIBILITY_PASS',keys.length,candidate);
 }finally{try{fs.rmdirSync(temp);}catch{}}
})().catch(error=>{console.error(error);process.exitCode=1;});
