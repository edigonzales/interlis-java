// Loopback-only repository and proxy fixtures. All keys are public disposable test data.
const http=require('node:http'),https=require('node:https'),net=require('node:net'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root='INTERLIS 2.4; MODEL Root AT "x" VERSION "1" = IMPORTS Dep; TOPIC T = CLASS Item = label : Dep.Label; END Item; END T; END Root.\n';
const dep='INTERLIS 2.4; MODEL Dep AT "x" VERSION "1" = DOMAIN Label = TEXT*20; END Dep.\n';
const index='<TRANSFER>'+[['Root','Root.ili'],['Dep','Dep.ili']].map(([name,file])=>'<ModelMetadata><Name>'+name+'</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>'+file+'</File></ModelMetadata>').join('')+'</TRANSFER>';
const sha=body=>crypto.createHash('sha256').update(body).digest('hex');
function handler(state,requests,kind){return(req,res)=>{
 const uri=new URL(req.url,'http://fixture.invalid'),entry={kind,method:req.method,url:req.url,bytesQueued:0,finished:false};requests.push(entry);res.on('finish',()=>entry.finished=true);
 const name=uri.pathname.split('/').at(-1);let body=name==='ilimodels.xml'?index:name==='Root.ili'?root:name==='Dep.ili'?dep:null;
 if(uri.pathname.includes('/redirect/')){res.writeHead(302,{Location:uri.pathname.replace('/redirect/','/repo/')}).end();entry.status=302;return;}
 if(state.mode==='missing'&&name==='Root.ili')body=null;
 if(body===null){res.writeHead(404).end();entry.status=404;return;}
 if(state.mode==='corrupt'&&name==='Root.ili'){res.writeHead(200,{'Content-Encoding':'gzip'}).end(body);entry.bytesQueued=Buffer.byteLength(body);entry.status=200;return;}
 entry.responseSha256=sha(body);entry.status=200;
 if(state.mode==='stall'&&name==='Root.ili')return;
 if(state.mode==='stream'&&name==='Root.ili'){
  res.writeHead(200);res.flushHeaders();let at=0;const timer=setInterval(()=>{const chunk=body.slice(at,at+=5);res.write(chunk);entry.bytesQueued+=Buffer.byteLength(chunk);if(at>=body.length){clearInterval(timer);res.end();}},75);res.on('close',()=>clearInterval(timer));return;
 }
 res.writeHead(200);res.end(body);entry.bytesQueued=Buffer.byteLength(body);
};}
async function start(server,requests,state,kind){
 const sockets=new Set();server.on('connection',socket=>{sockets.add(socket);socket.on('close',()=>sockets.delete(socket));});await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
 return {server,requests,state,sockets,base:(kind==='https'?'https:':'http:')+'//127.0.0.1:'+server.address().port+'/repo/',port:server.address().port};
}
function startRepositoryServer(){const requests=[],state={mode:'normal'};return start(http.createServer(handler(state,requests,'http')),requests,state,'http');}
function startTlsRepositoryServer(tlsDirectory,certificate='valid'){const requests=[],state={mode:'normal'};return start(https.createServer({key:fs.readFileSync(path.join(tlsDirectory,certificate+'.key.pem')),cert:fs.readFileSync(path.join(tlsDirectory,certificate+'.pem'))},handler(state,requests,'https')),requests,state,'https');}
async function startConnectProxy(){
 const requests=[],state={mode:'normal'},server=http.createServer(handler(state,requests,'proxy-http'));const fixture=await start(server,requests,state,'http');
 server.on('connect',(req,client,head)=>{requests.push({kind:'CONNECT',target:req.url});const [host,port]=req.url.split(':');const remote=net.connect(Number(port),host,()=>{client.write('HTTP/1.1 200 Connection Established\r\n\r\n');if(head.length)remote.write(head);client.pipe(remote);remote.pipe(client);});fixture.sockets.add(remote);remote.on('close',()=>fixture.sockets.delete(remote));remote.on('error',()=>client.destroy());client.on('error',()=>remote.destroy());client.on('close',()=>remote.destroy());});return fixture;
}
async function stopFixture(fixture){if(!fixture||!fixture.server.listening)return;for(const socket of fixture.sockets)socket.destroy();await new Promise(resolve=>fixture.server.close(resolve));}
module.exports={startRepositoryServer,startTlsRepositoryServer,startConnectProxy,stopFixture,models:{root,dep,index}};
