// Narrow executable-distribution probes; no external repository or user cache is used.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),cp=require('node:child_process'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'build/cache-logging/processes');fs.mkdirSync(out,{recursive:true});
const launcher=process.argv[2]||path.join(root,'interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler');
const dir=fs.mkdtempSync(path.join(out,'run-')),home=path.join(dir,'home');fs.mkdirSync(home);
const model='INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
const index='<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
const input=path.join(dir,'Projekt.ili');fs.writeFileSync(input,'INTERLIS 2.4; MODEL Projekt AT "x" VERSION "1" = IMPORTS Basis; END Projekt.');
let mode='strong';const requests=[],cases=[];
const server=http.createServer((req,res)=>{
  const location=new URL(req.url,'http://localhost');const body=location.pathname==='/ilimodels.xml'?index:location.pathname==='/Basis.ili'?model:null;
  if(body===null){requests.push({path:location.pathname,status:404});res.writeHead(404).end();return;}
  const tag='"'+crypto.createHash('sha256').update(body).digest('hex')+'"';const conditional=req.headers['if-none-match'];
  const bad=location.pathname==='/Basis.ili' && ['bad304','always304'].includes(mode);
  if(bad && (conditional||mode==='always304')){requests.push({path:location.pathname,status:304,confirmed:false});res.writeHead(304).end();return;}
  if(mode==='no-store')res.setHeader('Cache-Control','no-store');
  res.setHeader('ETag',mode==='weak'?'W/'+tag:tag);
  const status=conditional===tag?304:200;requests.push({path:location.pathname,status,condition:conditional||null});res.writeHead(status).end(status===200?body:undefined);
});
async function run(name,args,expected=0,extraEnv={}) {
  const start=requests.length;const command=[launcher,...args];
  const result=await new Promise((resolve,reject)=>{
    const child=cp.spawn(launcher,args,{cwd:dir,env:{...process.env,JAVA_OPTS:'-Duser.home='+home,...extraEnv},stdio:['ignore','pipe','pipe']});
    let stdout='',stderr='';child.stdout.on('data',b=>stdout+=b);child.stderr.on('data',b=>stderr+=b);const timer=setTimeout(()=>{child.kill('SIGTERM');reject(Error('timeout: '+name));},45000);
    child.on('error',reject);child.on('close',(exit,signal)=>{clearTimeout(timer);resolve({name,command,exit,signal,stdout,stderr,http:requests.slice(start)});});
  });cases.push(result);fs.writeFileSync(path.join(out,name+'.stdout'),result.stdout);fs.writeFileSync(path.join(out,name+'.stderr'),result.stderr);
  assert.equal(result.exit,expected,name+': '+result.stderr);return result;
}
const infos=s=>s.split('\n').filter(line=>line.includes('[ILI-RES-0010]'));
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base='http://127.0.0.1:'+server.address().port+'/';
 const common=['--repository',base,input];
 const cold=await run('cold',['check',...common,'--verbose','--write-lock',path.join(dir,'lock.xml')]);assert.equal(infos(cold.stderr).length,4);assert.ok(cold.stderr.includes('source downloaded'));assert.ok(cold.stderr.includes('HTTP 304'));
 for(const command of ['check','compile','analyze','diagram']) {
  const hot=await run('hot-'+command,[command,...common,'--verbose','--warnings','reject']);assert.equal(infos(hot.stderr).length,4);assert.ok(hot.http.every(r=>r.status===304));
  assert.ok(infos(hot.stderr).every(s=>s.startsWith('INFO [ILI-RES-0010]')&&s.includes(base)));
  const de=await run('de-'+command,[command,...common,'--verbose','--diagnostic-language','de']);assert.ok(de.stderr.includes('Information [ILI-RES-0010]'));
  const compact=await run('compact-'+command,[command,...common]);assert.equal(infos(compact.stderr).length,0);
  if(command!=='diagram') {
   const a=await run('json-'+command,[command,...common,'--json']);const b=await run('json-verbose-'+command,[command,...common,'--json','--verbose']);
   assert.deepEqual(JSON.parse(a.stdout),JSON.parse(b.stdout));assert.equal(b.stderr,a.stderr);assert.ok(!b.stdout.includes('ILI-RES-0010'));
  }
 }
 for(const lock of [false,true]) {
  const offline=await run('offline-'+lock,['compile',...common,'--verbose','--offline',...(lock?['--read-lock',path.join(dir,'lock.xml')]:[])]);
  assert.equal(offline.http.length,0);assert.equal(infos(offline.stderr).length,2);assert.ok(offline.stderr.includes('content used from cache offline'));
 }
 const optional=await run('optional-site',['check','--repository',base,'--root','Absent','--offline','--verbose'],1);assert.equal(optional.http.length,0);assert.ok(optional.stderr.includes('ILI-RES-0001'));assert.ok(!optional.stderr.includes('ILI-RES-0003'));
 mode='bad304';const retry=await run('unconfirmed-304',['check',...common,'--verbose']);assert.ok(retry.stderr.includes('cache could not be used'));assert.ok(retry.stderr.includes('source downloaded'));
 mode='always304';const failed=await run('second-304',['check',...common,'--verbose'],1);assert.equal(infos(failed.stderr).filter(s=>s.includes('Basis.ili')&&s.includes('cache could not be used')).length,2);assert.ok(!infos(failed.stderr).some(s=>s.includes('Basis.ili')&&s.includes('content used from cache')));
 mode='strong';const disabled=await run('no-cache',['check',...common,'--no-cache','--verbose']);assert.equal(infos(disabled.stderr).length,4);assert.ok(!disabled.stderr.includes('content used from cache'));
 mode='no-store';const nostore=await run('no-store',['check',...common,'--cache',path.join(dir,'private-cache'),'--verbose']);assert.equal(infos(nostore.stderr).length,4);assert.ok(!nostore.stderr.includes('content used from cache'));
 mode='strong';const color=await run('color-always',['check',...common,'--verbose','--color','always']);assert.ok(color.stderr.includes('\x1b[36mINFO\x1b[0m'));
 const plain=await run('color-never',['check',...common,'--verbose','--color','never']);assert.ok(!plain.stderr.includes('\x1b'));
 const version=await run('version',['version','--json']);assert.equal(JSON.parse(version.stdout).diagnosticCatalogVersion,'interlis-diagnostics/v3');
 const jars=Object.fromEntries(fs.readdirSync(path.join(path.dirname(launcher),'../lib')).filter(f=>f.endsWith('.jar')).sort().map(f=>[f,crypto.createHash('sha256').update(fs.readFileSync(path.join(path.dirname(launcher),'../lib',f))).digest('hex')]));
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({passed:true,node:process.version,javaHome:process.env.JAVA_HOME,launcher,cases,jars},null,2));console.log('CACHE_LOG_PROCESSES_PASS '+cases.length);
})().catch(error=>{fs.writeFileSync(path.join(out,'failed.json'),JSON.stringify({error:String(error),cases},null,2));console.error(error);process.exitCode=1;}).finally(()=>server.close());
