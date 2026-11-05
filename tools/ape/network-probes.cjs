const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {record,hash}=require('./record.cjs'),{loadCandidate}=require('./bundle.cjs'),{compareCase,requireCases}=require('./evidence.cjs'),fixtures=require('./fixture-server.cjs');
const harnessSource=fs.readFileSync(__filename),harnessSha256=require('node:crypto').createHash('sha256').update(harnessSource).digest('hex');
const required=['N01','N02-offline','N02-miss','N02-corrupt','N03','N04-untrusted','N04-host','N03-after','N06-http','N06-connect','N06-direct','N07','N08-progress','N08-total','N09-redirect','N09-missing','N09-corrupt','N05'];
async function network(binary,manifestFile,java,out,mode){
 assert.ok(mode===undefined||mode==='reference-only');
 assert.equal(process.platform,'darwin','Primary F3 profile currently runs on the real macOS host');assert.equal(process.arch,'arm64');assert.ok(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'network-source-'+harnessSha256+'.cjs'),harnessSource);
 const delivery=loadCandidate(path.join(path.dirname(binary),'bundle-manifest.json')),candidateSha256=delivery.bundle.sha256,manifest=JSON.parse(fs.readFileSync(manifestFile)),cases=[],servers=[];
 const loader=delivery.bundle.runtimeAdditions.find(row=>row.platform==='macos-arm64');assert.ok(loader);
 const native=[path.join(delivery.directory,loader.path),binary],classpath=manifest.orderedRuntime.map(row=>path.join(path.dirname(manifestFile),row.path)).join(path.delimiter),jvm=[java,'-cp',classpath,manifest.mainClass];
 const tls=path.join(__dirname,'../../testdata/ape/tls'),pins=JSON.parse(fs.readFileSync(path.join(tls,'fixtures.json')));for(const [name,pin] of Object.entries(pins.files))assert.equal(hash(path.join(tls,name)),pin.sha256);
 const trust=path.join(out,'isolated-test-ca.p12'),keytool=path.join(path.dirname(java),'keytool');
 const imported=await record(out,'test-truststore',[keytool,'-importcert','-noprompt','-alias','public-ape-test-ca','-file',path.join(tls,'ca.pem'),'-keystore',trust,'-storetype','PKCS12','-storepass','changeit']);assert.equal(imported.exitCode,0);
 const trustArgs=['-Djavax.net.ssl.trustStore='+trust,'-Djavax.net.ssl.trustStorePassword=changeit','-Djavax.net.ssl.trustStoreType=PKCS12'];
 function state(){return servers.flatMap((s,i)=>s.requests.map(r=>({server:i,...r})));}
 async function pair(id,argsFor,expected,options={}){
  const observed=[];
  for(const [label,prefix] of (mode==='reference-only'?[['jvm',jvm]]:[['jvm',jvm],['native',native]])){
   const before=servers.map(s=>s.requests.length),args=argsFor(label),home=path.join(out,'home-'+label);fs.mkdirSync(home,{recursive:true});
   let command=label==='jvm'?[java,'-Duser.home='+home,...(options.trust?trustArgs:[]),...jvm.slice(1),...args]:[...native,...(options.trust?trustArgs:[]),...args];
   const env={PATH:'/usr/bin:/bin:/usr/sbin:/sbin',HOME:home,TMPDIR:out,LANG:'en_US.UTF-8'};
   const receipt=await record(out,id+'-'+label,command,{cwd:out,env,timeout:30000});
   const requests=servers.flatMap((s,i)=>s.requests.slice(before[i]).map(r=>({server:i,...r})));fs.writeFileSync(path.join(out,id+'-'+label+'-requests.json'),JSON.stringify(requests,null,2));
   const result={...receipt,stdout:fs.readFileSync(receipt.stdout.path,'utf8'),stderr:fs.readFileSync(receipt.stderr.path,'utf8'),requests};
   assert.equal(receipt.timedOut,false,id);assert.equal(receipt.signal,null,id);assert.equal(receipt.exitCode,expected,id+' '+label+': '+result.stderr+' '+result.stdout);options.observe?.(result,label);observed.push(result);
  }
  if(mode!=='reference-only')assert.equal(compareCase(observed[0],observed[1]),true,id+' JVM/native output differs; original streams retained');cases.push({id,status:'PASSED',expectedExit:expected});write('INCOMPLETE');
 }
 function write(status,error){fs.writeFileSync(path.join(out,'network-report.json'),JSON.stringify({status,error,scope:mode==='reference-only'?'JVM fixture/expectation baseline only; no native gate':'JVM/native network comparison',nativeExecuted:mode!=='reference-only',candidateSha256,bundleManifestSha256:hash(path.join(delivery.directory,'bundle-manifest.json')),inputManifestSha256:hash(manifestFile),harnessSha256,host:{platform:process.platform,arch:process.arch},emulation:false,truststoreSha256:hash(trust),testCaInStandardCandidate:false,required,cases,requests:state()},null,2));}
 try{
  const origin=await fixtures.startRepositoryServer();servers.push(origin);const secure=await fixtures.startTlsRepositoryServer(tls);servers.push(secure);const wrong=await fixtures.startTlsRepositoryServer(tls,'wrong-host');servers.push(wrong);const proxy=await fixtures.startConnectProxy();servers.push(proxy);
  const noCache=base=>['check','--root','Root','--repository',base,'--no-cache','--no-proxy','--json'];
  await pair('N01',label=>['check','--root','Root','--repository',origin.base,'--cache',path.join(out,'cache-'+label),'--no-proxy','--json'],0,{observe:r=>{assert.ok(r.requests.some(q=>q.url.endsWith('/Root.ili')));assert.ok(r.requests.some(q=>q.url.endsWith('/Dep.ili')));}});
  await fixtures.stopFixture(origin);
  const offline=label=>['check','--root','Root','--repository',origin.base,'--cache',path.join(out,'cache-'+label),'--proxy','127.0.0.1','--proxyPort',String(proxy.port),'--offline','--json'];
  await pair('N02-offline',offline,0,{observe:r=>assert.equal(r.requests.length,0)});
  await pair('N02-miss',label=>offline(label).map(v=>v===path.join(out,'cache-'+label)?path.join(out,'empty-cache-'+label):v),1,{observe:r=>assert.equal(r.requests.length,0)});
  for(const label of (mode==='reference-only'?['jvm']:['jvm','native'])){
   const from=path.join(out,'cache-'+label),to=path.join(out,'corrupt-cache-'+label);fs.cpSync(from,to,{recursive:true});
   const rootDigest=require('node:crypto').createHash('sha256').update(fixtures.models.root).digest('hex');fs.appendFileSync(path.join(to,rootDigest),'deliberate corruption');
  }
  await pair('N02-corrupt',label=>offline(label).map(v=>v===path.join(out,'cache-'+label)?path.join(out,'corrupt-cache-'+label):v),1,{observe:r=>assert.equal(r.requests.length,0)});
  await pair('N03',()=>noCache(secure.base),0,{trust:true,observe:r=>assert.ok(r.requests.some(q=>q.kind==='https'))});
  await pair('N04-untrusted',()=>noCache(secure.base),1);
  await pair('N04-host',()=>noCache(wrong.base),1,{trust:true});
  await pair('N03-after',()=>noCache(secure.base),0,{trust:true});
  await pair('N06-http',()=>['check','--root','Root','--repository','http://unreachable.invalid/repo/','--proxy','127.0.0.1','--proxyPort',String(proxy.port),'--no-cache','--json'],0,{observe:r=>assert.ok(r.requests.some(q=>q.kind==='proxy-http'))});
  await pair('N06-connect',()=>['check','--root','Root','--repository',secure.base,'--proxy','127.0.0.1','--proxyPort',String(proxy.port),'--no-cache','--json'],0,{trust:true,observe:r=>assert.ok(r.requests.some(q=>q.kind==='CONNECT'))});
  const direct=await fixtures.startRepositoryServer();servers.push(direct);const config=path.join(out,'proxy.properties');fs.writeFileSync(config,'proxy=127.0.0.1\nproxyPort='+proxy.port+'\n');
  await pair('N06-direct',()=>[...noCache(direct.base),'--config',config],0,{observe:r=>{assert.ok(r.requests.some(q=>q.kind==='http'));assert.ok(r.requests.every(q=>q.kind!=='proxy-http'&&q.kind!=='CONNECT'));}});
  direct.state.mode='stall';await pair('N07',()=>[...noCache(direct.base),'--read-timeout','1'],1,{observe:r=>assert.ok(r.stdout.includes('TIMEOUT'))});
  direct.state.mode='stream';await pair('N08-progress',()=>[...noCache(direct.base),'--read-timeout','1'],0);
  await pair('N08-total',()=>[...noCache(direct.base),'--read-timeout','1','--request-timeout','1'],1,{observe:r=>assert.ok(r.stdout.includes('TIMEOUT'))});
  direct.state.mode='normal';await pair('N09-redirect',()=>noCache(direct.base.replace('/repo/','/redirect/')),0);
  direct.state.mode='missing';await pair('N09-missing',()=>noCache(direct.base),1);
  direct.state.mode='corrupt';await pair('N09-corrupt',()=>noCache(direct.base),1);
  await pair('N05',label=>['check','--root','Units','--repository','https://models.interlis.ch/','--cache',path.join(out,'public-cache-'+label),'--write-lock',path.join(out,'public-lock-'+label+'.json'),'--connect-timeout','10','--read-timeout','10','--request-timeout','30','--no-proxy','--json'],0);
  requireCases(required,cases);assert.equal(hash(binary),candidateSha256);write('PASSED');console.log('NETWORK_GATE_PASS '+out);
 }catch(error){write('FAILED',error.message);throw error;}finally{for(const server of servers)await fixtures.stopFixture(server);}
}
module.exports={network};if(require.main===module)network(...process.argv.slice(2)).catch(error=>{console.error(error);process.exitCode=1;});
