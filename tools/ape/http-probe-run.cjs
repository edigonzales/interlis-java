// Reproduce the isolated HTTP boundary, keeping uncaught native exception detail.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {record,hash}=require('./record.cjs'),{startRepositoryServer,stopFixture}=require('./fixture-server.cjs');
async function run(java,probeDirectory,loader,out,provider){
 assert.ok(!fs.existsSync(out));const server=await startRepositoryServer();
 try{
  const url=server.base+'Root.ili';
  const jvm=await record(out,'jvm',[java,'-cp',path.join(probeDirectory,'classes'),'HttpProbe',url],{timeout:10000});
  const native=await record(out,'native',[loader,path.join(probeDirectory,'http-probe'),...(provider?['-Djava.nio.channels.spi.SelectorProvider='+provider]:[]),url],{timeout:10000});
  assert.equal(jvm.exitCode,0);assert.equal(jvm.timedOut,false);assert.equal(jvm.signal,null);assert.match(fs.readFileSync(jvm.stdout.path,'utf8'),/^HTTP_PROBE 200 /);
  const result={scope:'diagnostic observations, not a compiler or TLS gate',probeSha256:hash(path.join(probeDirectory,'http-probe')),loaderSha256:hash(loader),jvmExit:jvm.exitCode,nativeExit:native.exitCode,nativeTimedOut:native.timedOut,nativeSignal:native.signal,requests:server.requests};
  fs.writeFileSync(path.join(out,'diagnosis.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }finally{await stopFixture(server);}
}
if(require.main===module)run(...process.argv.slice(2)).catch(error=>{console.error(error);process.exitCode=1;});
