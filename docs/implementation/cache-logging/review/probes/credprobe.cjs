#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),cp=require('node:child_process'),crypto=require('node:crypto');
const root=process.argv[2],launcher=path.join(root,'build/reviewer/distribution/bin/interlis-compiler');
const out=path.join(root,'build/reviewer/probes/results');fs.mkdirSync(out,{recursive:true});
const dir=fs.mkdtempSync(path.join(out,'cred-'));const home=path.join(dir,'home');fs.mkdirSync(home);
const input=path.join(dir,'Projekt.ili');fs.writeFileSync(input,'INTERLIS 2.4; MODEL Projekt AT "x" VERSION "1" = IMPORTS Basis; END Projekt.');
const model='INTERLIS 2.4; MODEL Basis AT "x" VERSION "1" = END Basis.';
const index='<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>';
const requests=[];
const server=http.createServer((req,res)=>{const u=new URL(req.url,'http://localhost');const body=u.pathname==='/ilimodels.xml'?index:u.pathname==='/Basis.ili'?model:null;requests.push({path:u.pathname,status:body?200:404});if(!body){res.writeHead(404).end();return;}res.writeHead(200,{'Content-Type':'text/plain','ETag':'"'+crypto.createHash('sha256').update(body).digest('hex')+'"'}).end(body);});
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url='http://user:password@127.0.0.1:'+server.address().port+'/';
 const child=cp.spawn(launcher,['check','--repository',url,input,'--verbose'],{cwd:dir,env:{...process.env,JAVA_OPTS:'-Duser.home='+home},stdio:['ignore','pipe','pipe']});
 let stdout='',stderr='';child.stdout.on('data',b=>stdout+=b);child.stderr.on('data',b=>stderr+=b);
 child.on('close',exit=>{const result={exit,stdout,stderr,requests};fs.writeFileSync(path.join(out,'credprobe.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));server.close();});
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
