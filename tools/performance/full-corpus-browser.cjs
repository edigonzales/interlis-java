'use strict';
const sourceFingerprint=require('./profile.cjs').fingerprint(),commitInfo=require('./profile.cjs').commitInfo();
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),os=require('node:os'),cp=require('node:child_process');
const root=path.resolve(__dirname,'../..'),directory=path.join(root,'build/reports/performance/full-corpus'),web=process.env.FULL_CORPUS_WEB_ROOT?path.resolve(process.env.FULL_CORPUS_WEB_ROOT):path.join(root,'interlis-wasm-teavm/build/web');
const prepared=JSON.parse(fs.readFileSync(path.join(directory,'prepared.json'),'utf8')),baseline=JSON.parse(fs.readFileSync(path.join(directory,'jvm.json'),'utf8'));
if(baseline.captureStatus!=='complete'||baseline.manifestSha256!==prepared.manifestSha256||baseline.roots.length!==347||prepared.roots.length!==347)throw Error('missing/stale/incomplete JVM parity capture');
function setup(){
  let active;
  const worker=new Worker('full-corpus-worker.js');
  worker.onmessage=event=>{if(!active)return;const {resolve,reject,timer}=active;active=null;clearTimeout(timer);event.data.kind==='error'?reject(Error(JSON.stringify(event.data))):resolve(event.data);};
  worker.onerror=event=>{if(!active)return;clearTimeout(active.timer);active.reject(Error(event.message));active=null;};
  const call=message=>new Promise((resolve,reject)=>{if(active)return reject(Error('overlapping corpus request'));active={resolve,reject,timer:setTimeout(()=>{active=null;worker.terminate();reject(Error('full corpus Worker exceeded 120s operation deadline'));},120000)};worker.postMessage(message);});
  window.fullCorpus={load:()=>call({kind:'load'}),capture:root=>call({kind:'capture',root}),close:()=>worker.terminate()};
}
function canonical(value){if(Array.isArray(value))return value.map(canonical);if(value&&typeof value==='object')return Object.fromEntries(Object.keys(value).sort().map(key=>[key,canonical(value[key])]));return value;}
function parity(row,index){if(row.model!==baseline.roots[index].model||JSON.stringify(canonical(row.semantic))!==JSON.stringify(canonical(baseline.roots[index].semantic)))throw Error('JVM/browser semantic parity failed: '+row.model);}
async function serve(){return new Promise(resolve=>{const server=http.createServer((request,response)=>{
  const pathname=new URL(request.url,'http://localhost').pathname,relative=pathname==='/'?'index.html':decodeURIComponent(pathname.slice(1)),file=path.resolve(web,relative);
  if(!file.startsWith(web+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){response.writeHead(404);response.end();return;}
  response.writeHead(200,{'content-type':file.endsWith('.wasm')?'application/wasm':file.endsWith('.html')?'text/html; charset=utf-8':'text/javascript; charset=utf-8'});fs.createReadStream(file).pipe(response);
});server.listen(0,'127.0.0.1',()=>resolve(server));});}
async function playwright(url){
  const {chromium,firefox,webkit}=require(path.join(root,'interlis-wasm-teavm/node_modules/playwright'));
  const results=[];
  const selected=process.argv[2];if(selected&&!['chromium','firefox','webkit'].includes(selected))throw Error('unknown browser target');
  for(const [name,type]of [['chromium',chromium],['firefox',firefox],['webkit',webkit]].filter(([name])=>!selected||selected===name)){
    const out=path.join(directory,name+'.json');fs.rmSync(out,{force:true});let browser;
    const report={...commitInfo,wasmSha256:require('./profile.cjs').hash(fs.readFileSync(path.join(web,'interlis-compiler.wasm'))),sourceFingerprint,runtime:'teavm-wasm-gc-browser',browser:name,node:process.version,os:os.platform(),arch:os.arch(),profile:prepared.profile,manifestSha256:prepared.manifestSha256,roots:[]};const started=performance.now();
    try{
      browser=await type.launch({headless:true});report.browserVersion=browser.version();const context=await browser.newContext();const external=[];
      await context.route('**/*',route=>{if(new URL(route.request().url()).origin!==new URL(url).origin){external.push(route.request().url());return route.abort();}return route.continue();});
      const page=await context.newPage();await page.goto(url);await page.evaluate(setup);report.coldLoadMs=(await page.evaluate(()=>fullCorpus.load())).coldLoadMs;
      for(let i=0;i<prepared.roots.length;i++){
        const message=await page.evaluate(root=>fullCorpus.capture(root),prepared.roots[i]);if(message.kind!=='result')throw Error('unexpected Worker reply');const row=message.result;report.roots.push(row);parity(row,i);
        if((i+1)%10===0)fs.writeFileSync(out,JSON.stringify(report)+'\n');console.log('FULL_CORPUS_BROWSER',name,i+1,row.model,row.outcome);
      }
      if(external.length)throw Error('external browser requests: '+JSON.stringify(external));report.externalRequests=external;report.captureStatus='complete';report.elapsedMs=performance.now()-started;results.push(report);
    }catch(error){report.error=String(error);throw error;}finally{fs.writeFileSync(out,JSON.stringify(report)+'\n');await browser?.close();}
  }return results;
}
async function safari(url){
  const out=path.join(directory,'safari.json');fs.rmSync(out,{force:true});const driver='/System/Cryptexes/App/usr/bin/safaridriver';
  if(!fs.existsSync(driver))throw Error('real Safari WebDriver unavailable');const report={...commitInfo,sourceFingerprint,wasmSha256:require('./profile.cjs').hash(fs.readFileSync(path.join(web,'interlis-compiler.wasm'))),runtime:'teavm-wasm-gc-browser',browser:'Safari',driverVersion:cp.execFileSync(driver,['--version'],{encoding:'utf8'}).trim(),node:process.version,os:os.platform(),arch:os.arch(),profile:prepared.profile,manifestSha256:prepared.manifestSha256,roots:[]};
  const port=44000+Math.floor(Math.random()*1000),base='http://127.0.0.1:'+port,child=cp.spawn(driver,['-p',String(port)],{stdio:['ignore','ignore','pipe']});let stderr='',session;child.stderr.on('data',b=>stderr+=b.toString());
  async function request(method,route,body,timeout=150000){const response=await fetch(base+route,{method,headers:{'content-type':'application/json'},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(timeout)}),json=await response.json();if(!response.ok||json.value?.error)throw Error('Safari '+route+': '+JSON.stringify(json));return json.value;}
  const started=performance.now();
  try{
    let ready=false;const deadline=Date.now()+30000;while(Date.now()<deadline&&!ready){if(child.exitCode!==null)throw Error('Safari driver exited: '+stderr);try{ready=Boolean((await request('GET','/status',undefined,1000))?.ready);}catch{}if(!ready)await new Promise(r=>setTimeout(r,250));}if(!ready)throw Error('Safari driver not ready');
    const created=await request('POST','/session',{capabilities:{alwaysMatch:{browserName:'safari'}}});session=created.sessionId;report.browserVersion=created.capabilities.browserVersion;
    await request('POST',`/session/${session}/timeouts`,{script:125000});await request('POST',`/session/${session}/url`,{url});
    await request('POST',`/session/${session}/execute/sync`,{script:'('+setup.toString()+')();return true;',args:[]});
    const execute=(script,args=[])=>request('POST',`/session/${session}/execute/async`,{script:'const done=arguments[arguments.length-1];('+script+').then(done).catch(error=>done({kind:"error",message:String(error)}));',args});
    const loaded=await execute('fullCorpus.load()');if(loaded.kind==='error')throw Error(loaded.message);report.coldLoadMs=loaded.coldLoadMs;
    for(let i=0;i<prepared.roots.length;i++){
      const message=await execute('fullCorpus.capture(arguments[0])',[prepared.roots[i]]);if(message.kind!=='result')throw Error(JSON.stringify(message));const row=message.result;report.roots.push(row);parity(row,i);
      if((i+1)%10===0)fs.writeFileSync(out,JSON.stringify(report)+'\n');console.log('FULL_CORPUS_BROWSER Safari',i+1,row.model,row.outcome);
    }
    report.captureStatus='complete';report.elapsedMs=performance.now()-started;
  }catch(error){report.error=String(error);throw error;}finally{if(session)try{await request('DELETE',`/session/${session}`,undefined,5000);}catch{}child.kill();report.driverStderr=stderr.trim();fs.writeFileSync(out,JSON.stringify(report)+'\n');}
}
(async()=>{const server=await serve();try{const url='http://127.0.0.1:'+server.address().port+'/';if(process.argv[2]==='safari')await safari(url);else await playwright(url);}finally{server.close();}})().catch(error=>{console.error(error);process.exitCode=1;});
