// Local executable-distribution checks for HTTP network policy; never uses public repositories.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),cp=require('node:child_process'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'target/verification/http-network');fs.mkdirSync(out,{recursive:true});
const launcher=process.argv[2]||path.join(require('./layout.cjs').resolveRole(require('./layout.cjs').readVerificationInputs(root),'compiler'),'bin/interlis-compiler'+(process.platform==='win32'?'.bat':''));
const dir=fs.mkdtempSync(path.join(out,'run-')),home=path.join(dir,'home');fs.mkdirSync(home);
const model='INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
const index='<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
let mode='normal';const requests=[],cases=[];
function handler(kind){return (req,res)=>{
 const url=new URL(req.url,'http://localhost');requests.push({kind,path:url.pathname,absolute:req.url.startsWith('http:')});
 const body=url.pathname==='/ilimodels.xml'?index:url.pathname==='/Basis.ili'?model:null;
 if(body===null){res.writeHead(404).end();return;}
 if(mode==='slow' && url.pathname==='/Basis.ili'){const timer=setTimeout(()=>res.writeHead(200).end(body),1600);res.on('close',()=>clearTimeout(timer));return;}
 if(mode==='stream' && url.pathname==='/Basis.ili'){
  res.writeHead(200);res.flushHeaders();let at=0;const timer=setInterval(()=>{res.write(body.slice(at,at+=5));if(at>=body.length){clearInterval(timer);res.end();}},140);res.on('close',()=>clearInterval(timer));return;
 }
 res.writeHead(200,{'ETag':'"'+crypto.createHash('sha256').update(body).digest('hex')+'"'}).end(body);
};}
const origin=http.createServer(handler('origin')),proxy=http.createServer(handler('proxy'));
async function run(name,args,expected=0,javaOptions=''){
 const start=requests.length,startedAt=new Date().toISOString();const result=await new Promise((resolve,reject)=>{
  const child=cp.spawn(launcher,args,{cwd:dir,env:{...process.env,JAVA_OPTS:'-Duser.home='+home+' '+javaOptions},stdio:['ignore','pipe','pipe']});let stdout='',stderr='';
  child.stdout.on('data',x=>stdout+=x);child.stderr.on('data',x=>stderr+=x);const timer=setTimeout(()=>child.kill('SIGKILL'),20000);
  child.on('error',reject);child.on('close',(exit,signal)=>{clearTimeout(timer);resolve({exit,signal,stdout,stderr});});
 });
 const record={name,command:[launcher,...args],javaOptions,startedAt,finishedAt:new Date().toISOString(),...result,requests:requests.slice(start)};cases.push(record);
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({passed:false,cases},null,2));
 assert.equal(result.signal,null,name);assert.equal(result.exit,expected,name+': '+result.stderr);return record;
}
(async()=>{
 await Promise.all([new Promise(r=>origin.listen(0,'127.0.0.1',r)),new Promise(r=>proxy.listen(0,'127.0.0.1',r))]);
 const base='http://127.0.0.1:'+origin.address().port+'/',remote='http://unreachable.invalid/',port=String(proxy.address().port);
 const common=['--root','Basis','--repository',base,'--no-cache','--json'];
 let r=await run('direct-overrides-jvm',['check',...common,'--no-proxy'],0,'-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1 -Dhttp.nonProxyHosts=');assert.ok(r.requests.some(x=>x.kind==='origin'));
 for(const command of ['check','compile','analyze','diagram']){
  const args=[command,'--root','Basis','--repository',remote,'--proxy','127.0.0.1','--proxyPort',port,'--no-cache'];
  if(command==='diagram')args.push('--format','json');else args.push('--json');
  r=await run('explicit-proxy-'+command,args);assert.ok(r.requests.length>0);assert.ok(r.requests.every(x=>x.kind==='proxy'&&x.absolute));
 }
 r=await run('system-jvm-proxy',['check','--root','Basis','--repository',remote,'--no-cache','--json'],0,'-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort='+port+' -Dhttp.nonProxyHosts=');assert.ok(r.requests.every(x=>x.kind==='proxy'));
 fs.writeFileSync(path.join(dir,'network.properties'),'proxy=127.0.0.1\nproxyPort='+port+'\nconnect-timeout=3\nread-timeout=2\nrequest-timeout=off\n');
 r=await run('properties-proxy',['check','--root','Basis','--repository',remote,'--config','network.properties','--json']);assert.ok(r.requests.every(x=>x.kind==='proxy'));
 r=await run('file-proxy-overridden-direct',['check',...common,'--config','network.properties','--no-proxy']);assert.ok(r.requests.every(x=>x.kind==='origin'));
 r=await run('explain-defaults',['check','--explain-config']);assert.equal(JSON.parse(r.stdout).settings['--read-timeout'].values[0],'40');assert.equal(r.requests.length,0);
 r=await run('usage-invalid-timeout',['check',...common,'--read-timeout','0'],2);assert.equal(r.requests.length,0);
 r=await run('usage-proxy-conflict',['check',...common,'--proxy','127.0.0.1','--no-proxy'],2);assert.equal(r.requests.length,0);
 r=await run('usage-orphan-port',['check','--proxyPort','80','--explain-config'],2);assert.equal(r.requests.length,0);
 mode='slow';r=await run('read-timeout',['check',...common,'--no-proxy','--read-timeout','1'],1);assert.ok(r.stdout.includes('TIMEOUT'));
 mode='stream';await run('progressing-stream',['check',...common,'--no-proxy','--read-timeout','1']);
 r=await run('request-total-timeout',['check',...common,'--no-proxy','--read-timeout','1','--request-timeout','1'],1);assert.ok(r.stdout.includes('TIMEOUT'));
 mode='normal';const cache=path.join(dir,'cache');
 await run('warm-proxy-cache',['check','--root','Basis','--repository',remote,'--proxy','127.0.0.1','--proxyPort',port,'--cache',cache,'--json']);
 r=await run('offline-does-not-contact-proxy',['check','--root','Basis','--repository',remote,'--proxy','127.0.0.1','--proxyPort',port,'--cache',cache,'--offline','--json']);assert.equal(r.requests.length,0);
 const lib=path.resolve(path.dirname(launcher),'../lib');const jars=Object.fromEntries(fs.readdirSync(lib).filter(f=>f.endsWith('.jar')).sort().map(f=>[f,crypto.createHash('sha256').update(fs.readFileSync(path.join(lib,f))).digest('hex')]));
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({passed:true,node:process.version,launcher,jars,cases},null,2));console.log('HTTP_NETWORK_PROCESSES_PASS '+cases.length);
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>{origin.closeAllConnections();proxy.closeAllConnections();origin.close();proxy.close();});
