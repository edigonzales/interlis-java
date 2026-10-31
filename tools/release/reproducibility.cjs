'use strict';
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process'),crypto=require('node:crypto');
const root=process.cwd(),output=path.join(root,'build/reports/p3/P3-5/reproducibility'),hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const git=(...args)=>cp.execFileSync('git',args,{cwd:root,encoding:'utf8'}).trim();
const walk=directory=>fs.readdirSync(directory,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(directory,e.name)):[path.join(directory,e.name)]);
async function run(cwd,command,args,log){
 return new Promise((resolve,reject)=>{const fd=fs.openSync(log,'w'),child=cp.spawn(command,args,{cwd,env:process.env,stdio:['ignore',fd,fd]});child.on('error',reject);child.on('exit',(code,signal)=>{fs.closeSync(fd);code===0?resolve():reject(Error('repro build failed: '+command+' exit='+code+' signal='+signal+'; '+log));});});
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
    await run(cwd,path.join(cwd,'gradlew'),['--no-daemon','sdkPackage',':interlis-wasm-teavm:buildWasmGC',':interlis-wasm-teavm:generateJavaScript',':interlis-wasm-teavm:prepareBrowserHarness','-PnodeExecutable='+process.execPath],path.join(record,'build.log'));
    if(cp.execFileSync('git',['status','--porcelain'],{cwd,encoding:'utf8'}).trim())throw Error('Clean build changed tracked inputs; checkout retained at '+cwd);
    const version=fs.readFileSync(path.join(cwd,'gradle.properties'),'utf8').match(/^projectVersion=(.+)$/m)[1].trim();
    const files=[...walk(path.join(cwd,'interlis-cli/build/install/interlis-cli')),
      path.join(cwd,'build/releases/interlis-java-sdk-'+version+'.zip'),
      path.join(cwd,'interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm'),
      path.join(cwd,'interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js'),
      path.join(cwd,'interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js'),
      ...walk(path.join(cwd,'interlis-wasm-teavm/build/browser-tools')),...walk(path.join(cwd,'interlis-wasm-teavm/build/web'))].sort();
    const manifest={};for(const file of files){const key=path.relative(cwd,file).split(path.sep).join('/');const bytes=fs.readFileSync(file);manifest[key]={sha256:hash(bytes),bytes:bytes.length};const copy=path.join(record,'artifacts',key);fs.mkdirSync(path.dirname(copy),{recursive:true});fs.writeFileSync(copy,bytes);}
    fs.writeFileSync(path.join(record,'artifacts.json'),JSON.stringify(manifest,null,2)+'\n');manifests.push(manifest);
   }finally{
    if(!cp.execFileSync('git',['status','--porcelain'],{cwd,encoding:'utf8'}).trim())git('worktree','remove',cwd);
   }
  }
  const keys=Object.keys(manifests[0]),differences=keys.filter(k=>JSON.stringify(manifests[0][k])!==JSON.stringify(manifests[1][k]));
  if(JSON.stringify(keys)!==JSON.stringify(Object.keys(manifests[1]))||differences.length)throw Error('Non-reproducible shipped files: '+differences.join(', '));
  if(git('rev-parse','HEAD')!==candidate||git('status','--porcelain')||require('../performance/profile.cjs').fingerprint()!==sourceFingerprint)throw Error('Candidate changed during two-clean-build verification');
  const result={status:'passed',candidate,sourceFingerprint,node:process.version,os:process.platform,arch:process.arch,cleanBuilds:2,shippedFiles:keys.length,artifacts:manifests[0],scope:'Two separate clean checkouts, independent Java/ANTLR/TeaVM/TypeScript generation. All shipped CLI files, SDK ZIP, TeaVM JS/Wasm/runtime and browser tools byte-identical; no native OS-image claim.'};
  fs.writeFileSync(path.join(output,'result.json'),JSON.stringify(result,null,2)+'\n');console.log('SDK_REPRODUCIBILITY_PASS',keys.length,candidate);
 }finally{try{fs.rmdirSync(temp);}catch{}}
})().catch(error=>{console.error(error);process.exitCode=1;});
