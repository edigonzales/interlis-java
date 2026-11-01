// Focused real-distribution regression for invalid HTTP redirect addresses.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),http=require('node:http'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'build/http-redirect-fix/processes');fs.mkdirSync(out,{recursive:true});
const launcher=process.argv[2]||path.join(root,'interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler');
const dir=fs.mkdtempSync(path.join(out,'run-')),home=path.join(dir,'home');fs.mkdirSync(home);
const model='INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
const index='<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
let mode='invalid',target='http://user:marked-secret@[';const cases=[];
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost');
 if(mode==='valid'&&url.pathname==='/index.xml'){res.writeHead(200).end(index);return;}
 if(mode==='valid'&&url.pathname==='/Basis.ili'){res.writeHead(200).end(model);return;}
 res.writeHead(302,{Location:mode==='valid'?'index.xml':target}).end();
});
async function run(name,command,expected,reason){
 const args=[command,'--root','Basis','--repository','http://127.0.0.1:'+server.address().port+'/', '--no-cache','--no-proxy'];
 args.push(...(command==='diagram'?['--format','json']:['--json']));
 const result=await new Promise((resolve,reject)=>{
  const child=cp.spawn(launcher,args,{cwd:dir,env:{...process.env,JAVA_OPTS:'-Duser.home='+home},stdio:['ignore','pipe','pipe']});let stdout='',stderr='';
  const timer=setTimeout(()=>child.kill('SIGKILL'),15000);child.stdout.on('data',x=>stdout+=x);child.stderr.on('data',x=>stderr+=x);child.on('error',reject);
  child.on('close',(exitCode,signal)=>{clearTimeout(timer);resolve({exitCode,signal,stdout,stderr});});
 });
 cases.push({name,command:[launcher,...args],...result});fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({passed:false,cases},null,2));
 assert.equal(result.signal,null,name);assert.equal(result.exitCode,expected,name+': '+result.stderr);
 const text=result.stdout+result.stderr;if(reason)assert.ok(text.includes(command==='diagram'?'ILI-RES-0003':reason),text);
 assert.ok(!text.includes('marked-secret'),text);assert.ok(!text.includes('IllegalArgumentException'),text);
}
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 for(const value of ['http://user:marked-secret@[','http:/missing-host']){target=value;for(const command of ['check','compile','analyze','diagram'])await run('invalid-'+(value.includes('secret')?'syntax':'target')+'-'+command,command,1,'IO_FAILURE');}
 target='file:///not-http';await run('unsupported-scheme','check',1,'IO_FAILURE');
 target='ilimodels.xml';await run('redirect-limit','check',1,'REDIRECT_LIMIT');
 mode='valid';await run('relative-redirect-success','check',0);
 const lib=path.resolve(path.dirname(launcher),'../lib');const jars=Object.fromEntries(fs.readdirSync(lib).filter(x=>x.endsWith('.jar')).sort().map(x=>[x,crypto.createHash('sha256').update(fs.readFileSync(path.join(lib,x))).digest('hex')]));
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({passed:true,node:process.version,javaHome:process.env.JAVA_HOME,cases,jars},null,2));console.log('HTTP_REDIRECT_PROCESSES_PASS '+cases.length);
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>{server.closeAllConnections();server.close();});
