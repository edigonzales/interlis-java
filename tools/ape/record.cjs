// Explicit process receipts. Captures only selected non-secret environment values.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto'),os=require('node:os');
const recorderSource=fs.readFileSync(__filename),recorderSha256=crypto.createHash('sha256').update(recorderSource).digest('hex');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
async function record(directory,name,argv,{cwd=process.cwd(),env=process.env,timeout=60000}={}) {
 fs.mkdirSync(directory,{recursive:true});const sourceCopy=path.join(directory,'record-source-'+recorderSha256+'.cjs');if(!fs.existsSync(sourceCopy))fs.writeFileSync(sourceCopy,recorderSource,{flag:'wx'});assertSource();function assertSource(){if(hash(sourceCopy)!==recorderSha256)throw Error('Recorder source snapshot changed');} const base=path.join(directory,name);
 const out=fs.openSync(base+'.stdout','wx'),err=fs.openSync(base+'.stderr','wx'),startedAt=new Date().toISOString();
 // Nested recorders register named APE containers with the outer supervisor.
 // Docker containers outlive a killed CLI process and are not POSIX descendants.
 const inherited=env.INTERLIS_APE_CONTAINER_REGISTRY,registry=inherited||base+'.containers';
 if(!inherited)fs.writeFileSync(registry,'',{flag:'wx'});
 const childEnv={...env,INTERLIS_APE_CONTAINER_REGISTRY:registry};
 const nameIndex=argv.indexOf('--name');
 if(path.basename(argv[0])==='docker'&&argv[1]==='run'&&nameIndex>=0){
  const name=argv[nameIndex+1];if(!/^ape-[A-Za-z0-9_.-]+$/.test(name))throw Error('Expected isolated APE container name');
  fs.appendFileSync(registry,name+'\n');
 }
 let child,timedOut=false,containerCleanup;
 function cleanupContainers(){
  const names=[...new Set(fs.readFileSync(registry,'utf8').trim().split('\n').filter(Boolean))];
  if(!names.length)return;for(const name of names)if(!/^ape-[A-Za-z0-9_.-]+$/.test(name))throw Error('Invalid owned container registry');
  const result=cp.spawnSync('docker',['rm','--force',...names],{encoding:'utf8',timeout:15000});
  containerCleanup={names,exitCode:result.status,signal:result.signal,stdout:result.stdout,stderr:result.stderr,error:result.error?.message};
 }

 const result=await new Promise(resolve=>{
  try {child=cp.spawn(argv[0],argv.slice(1),{cwd,env:childEnv,detached:true,stdio:['ignore',out,err],shell:false});}
  catch(error){resolve({exitCode:null,signal:null,error:error.message});return;}
  const timer=setTimeout(()=>{timedOut=true;try{process.kill(-child.pid,'SIGKILL');}catch{}cleanupContainers();},timeout);
  child.on('error',error=>{clearTimeout(timer);resolve({exitCode:null,signal:null,error:error.message});});
  child.on('close',(exitCode,signal)=>{clearTimeout(timer);resolve({exitCode,signal});});
 });fs.closeSync(out);fs.closeSync(err);
 const resolved=path.isAbsolute(argv[0])?argv[0]:(env.PATH||'').split(path.delimiter).map(dir=>path.join(dir,argv[0])).find(file=>{try{fs.accessSync(file,fs.constants.X_OK);return fs.statSync(file).isFile();}catch{return false;}});
 const receipt={argv,executable:resolved?fs.realpathSync(resolved):null,harness:{nodeVersion:process.version,nodeExecutable:process.execPath,sourcePath:sourceCopy,sourceSha256:recorderSha256},cwd,startedAt,endedAt:new Date().toISOString(),timeoutMs:timeout,timedOut,...result,...(containerCleanup?{containerCleanup}:{}),
  host:{platform:process.platform,arch:process.arch,release:os.release()},environment:Object.fromEntries(['JAVA_HOME','LANG','LC_ALL'].filter(k=>env[k]).map(k=>[k,env[k]])),
  stdout:{path:base+'.stdout',sha256:hash(base+'.stdout')},stderr:{path:base+'.stderr',sha256:hash(base+'.stderr')}};
 fs.writeFileSync(base+'.json',JSON.stringify(receipt,null,2)+'\n',{flag:'wx'}); return receipt;
}
module.exports={record,hash};
if(require.main===module) {const [dir,name,...raw]=process.argv.slice(2);let timeout=60000,argv=raw;if(raw[0]==='--timeout-ms'){timeout=Number(raw[1]);if(!Number.isInteger(timeout)||timeout<1||timeout>3600000)throw Error('Invalid timeout');argv=raw.slice(2);}record(dir,name,argv,{timeout}).then(r=>{console.log(JSON.stringify(r));process.exitCode=r.exitCode===0&&!r.timedOut?0:1;});}
