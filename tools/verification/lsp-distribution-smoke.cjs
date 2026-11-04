'use strict';
const cp=require('node:child_process'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
 const [java,distribution,output]=process.argv.slice(2);if(!output)throw Error('Expected java25, LSP distribution, receipt');
 const command=[java,'-cp',path.join(distribution,'lib','*'),'guru.interlis.lsp.Main'];const child=cp.spawn(command[0],command.slice(1),{stdio:['pipe','pipe','pipe']});
 const messages=[],pending=[];let buffer=Buffer.alloc(0),stderr='';const timer=setTimeout(()=>{child.kill('SIGKILL');},20000);
 const receive=()=>new Promise((resolve,reject)=>{if(messages.length)return resolve(messages.shift());pending.push({resolve,reject});});
 child.stdout.on('data',chunk=>{try{buffer=Buffer.concat([buffer,chunk]);while(buffer.length){const end=buffer.indexOf('\r\n\r\n');if(end<0)break;const header=buffer.subarray(0,end).toString('ascii');assert.match(header,/^Content-Length: [0-9]+$/i);const length=Number(header.split(':')[1]);if(buffer.length<end+4+length)break;const value=JSON.parse(buffer.subarray(end+4,end+4+length).toString('utf8'));buffer=buffer.subarray(end+4+length);if(pending.length)pending.shift().resolve(value);else messages.push(value);}}catch(e){pending.forEach(p=>p.reject(e));child.kill('SIGKILL');}});
 child.stderr.on('data',chunk=>{stderr+=chunk;});const exited=new Promise((resolve,reject)=>{child.on('error',reject);child.on('exit',(code,signal)=>{pending.forEach(p=>p.reject(Error('Server exited before response')));resolve({code,signal});});});
 const send=value=>{const bytes=Buffer.from(JSON.stringify(value));child.stdin.write('Content-Length: '+bytes.length+'\r\n\r\n');child.stdin.write(bytes);};
 try {
  send({jsonrpc:'2.0',id:1,method:'initialize',params:{capabilities:{}}});let value;do{value=await receive();}while(value.id!==1);assert.ok(value.result.capabilities.textDocumentSync);
  send({jsonrpc:'2.0',method:'initialized',params:{}});send({jsonrpc:'2.0',id:2,method:'shutdown',params:null});do{value=await receive();}while(value.id!==2);assert.equal(value.result,null);
  send({jsonrpc:'2.0',method:'exit'});child.stdin.end();const exit=await exited;assert.equal(exit.code,0);assert.equal(buffer.length,0);
  fs.writeFileSync(output,JSON.stringify({status:'passed',command,initialize:true,initialized:true,shutdown:true,exit,framing:'Content-Length; no startup output',stderr},null,2));console.log('LSP_DISTRIBUTION_PASS');
 }finally{clearTimeout(timer);if(child.exitCode===null)child.kill('SIGKILL');}
})().catch(e=>{console.error(e);process.exitCode=1;});
