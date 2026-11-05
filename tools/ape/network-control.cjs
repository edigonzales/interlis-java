// F3 diagnosis: same final APE and repository bytes, different host/cache controls.
const fs=require('node:fs'),path=require('node:path'),{record,hash}=require('./record.cjs'),{startRepositoryServer,stopFixture}=require('./fixture-server.cjs');
async function control(binary,out,provider){fs.mkdirSync(out,{recursive:true});const server=await startRepositoryServer();try{
 const loader=path.join(path.dirname(binary),process.platform==='darwin'?'ape-loader-macos-arm64':'ape-loader-linux-x64');
 const result=await record(out,'http-no-cache',[loader,binary,...(provider?['-Djava.nio.channels.spi.SelectorProvider='+provider]:[]),'check','--root','Root','--repository',server.base,'--no-cache','--no-proxy','--json'],{timeout:15000});
 fs.writeFileSync(path.join(out,'control.json'),JSON.stringify({candidateSha256:hash(binary),host:{platform:process.platform,arch:process.arch},exitCode:result.exitCode,signal:result.signal,timedOut:result.timedOut,requests:server.requests},null,2));
 }finally{await stopFixture(server);}}
if(require.main===module)control(process.argv[2],process.argv[3],process.argv[4]).catch(e=>{console.error(e);process.exitCode=1;});
