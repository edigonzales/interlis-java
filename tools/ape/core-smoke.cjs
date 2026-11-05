// F2 core comparisons: exact bytes, same visible absolute fixture paths, no normalization.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {record,hash}=require('./record.cjs'),{compareCase,requireCases}=require('./evidence.cjs');
const root=path.resolve(__dirname,'../..');
const harnessSource=fs.readFileSync(__filename),harnessSha256=require('node:crypto').createHash('sha256').update(harnessSource).digest('hex');
const CORE_CASES=['K01-help','K01-version','K01-version-json','K01-capabilities','K02-check-Valid.ili','K02-check-Valid23.ili','K02-compile-Valid.ili','K02-compile-Valid23.ili','K03-Syntax.ili','K03-Invalid.ili','K04-imports','K05-analyze','F01-stdout','F02-check-before','F03-write','F02-check-after'];
async function smoke(binary,manifestFile,java,out,mode){
 assert.ok(path.isAbsolute(out)&&!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'core-smoke-source-'+harnessSha256+'.cjs'),harnessSource,{flag:'wx'});
 const inputs=JSON.parse(fs.readFileSync(manifestFile));const classpath=inputs.orderedRuntime.map(j=>path.join(path.dirname(manifestFile),j.path)).join(path.delimiter);
 const binaryHash=hash(binary);const local=path.join(out,mode==='linux-slice'?'compiler':'interlis-compiler.com');fs.copyFileSync(binary,local);fs.chmodSync(local,0o755);
 const fixtures={
  'Valid.ili':fs.readFileSync(path.join(root,'testdata/ape/Valid.ili'),'utf8'),
  'Invalid.ili':fs.readFileSync(path.join(root,'testdata/ape/Invalid.ili'),'utf8'),
  'Valid23.ili':'INTERLIS 2.3; MODEL Valid23 AT "https://example.invalid" VERSION "1" = END Valid23.\n',
  'Syntax.ili':'INTERLIS 2.4; MODEL Syntax AT "x" VERSION "1" = END Syntax. TRAILING\n',
  'Base.ili':'INTERLIS 2.3; MODEL Base AT "x" VERSION "1" = DOMAIN Label = TEXT*20; END Base.\n',
  'Uses.ili':'INTERLIS 2.4; MODEL Uses AT "x" VERSION "1" = IMPORTS Base; TOPIC T = CLASS Item = label : Base.Label; END Item; END T; END Uses.\n',
  'Format ä 🌍.ili':'INTERLIS 2.4; MODEL Format AT "x" VERSION "1" = TOPIC T = CLASS Item = label : TEXT*20; END Item; END T; END Format.\n'
 };
 for(const [file,text] of Object.entries(fixtures))fs.writeFileSync(path.join(out,file),text);
 const fixtureHashes=Object.fromEntries(Object.keys(fixtures).map(f=>[f,hash(path.join(out,f))]));
 const jvm=[java,'-Duser.home='+path.join(out,'jvm-home'),'-cp',classpath,inputs.mainClass];
 let native,loader;
 if(mode==='macos-loader'||mode==='linux-loader'){
  const delivery=require('./bundle.cjs').loadCandidate(path.join(path.dirname(binary),'bundle-manifest.json'));
  const component=delivery.bundle.runtimeAdditions.find(row=>row.platform===(mode==='macos-loader'?'macos-arm64':'linux-x64'));assert.ok(component,'Missing declared loader');
  loader=path.join(out,component.path);fs.copyFileSync(path.join(delivery.directory,component.path),loader);fs.chmodSync(loader,component.mode);assert.equal(hash(loader),component.sha256);
 }
 const image=require('../../build/interlis/ape/toolchain.lock.json').builderImage;
 if(mode==='macos-shell')native=['/bin/sh',local];
 else if(mode==='macos-loader')native=[loader,local];
 else if(mode==='linux-slice'||mode==='linux-fused'||mode==='linux-loader')native=['docker','run','--rm','--platform','linux/amd64','--network','none','--read-only','--tmpfs','/tmp:rw,exec,size=1g','--env','HOME=/tmp','--mount','type=bind,src='+out+',dst='+out,'--workdir',out,'--entrypoint',...(mode==='linux-fused'?['/bin/sh',image,local]:mode==='linux-loader'?[loader,image,local]:[local,image])];
 else throw Error('Unknown smoke launch mode');
 const cases=[],allowedDifferences=[];
 async function pair(id,args,expected=0,after){
  assert.ok(CORE_CASES.includes(id));const results=[];let expectedFile;
  for(const [label,prefix] of [['jvm',jvm],['native',native]]){
   if(after)fs.writeFileSync(path.join(out,'Format ä 🌍.ili'),fixtures['Format ä 🌍.ili']);
   const invocation=[...prefix,...args];
   const containerName='ape-core-'+require('node:crypto').createHash('sha256').update(out+id).digest('hex').slice(0,16);
   if(label==='native'&&mode.startsWith('linux'))invocation.splice(2,0,'--name',containerName);
   const r=await record(out,id+'-'+label,invocation,{cwd:out,timeout:60000});
   if(r.timedOut&&label==='native'&&mode.startsWith('linux')){const cleanup=await record(out,id+'-cleanup',['docker','rm','--force',containerName]);assert.equal(cleanup.exitCode,0);}
   assert.equal(r.signal,null,id);assert.equal(r.timedOut,false,id);assert.equal(r.exitCode,expected,id+' '+label+': '+fs.readFileSync(r.stderr.path,'utf8'));
   results.push({...r,stdout:fs.readFileSync(r.stdout.path,'utf8'),stderr:fs.readFileSync(r.stderr.path,'utf8')});
   if(after){const bytes=fs.readFileSync(path.join(out,'Format ä 🌍.ili'));if(label==='jvm')expectedFile=bytes;else assert.deepEqual(bytes,expectedFile,id+' file bytes');}
  }
  if(id==='K01-version'){
   const reference='java: '+inputs.builder.jdk,candidate='java: 25.0.4+7-jvmci-25.2-b20';
   results[0].stdout=normalizeRuntimeVersion(results[0].stdout,reference);
   results[1].stdout=normalizeRuntimeVersion(results[1].stdout,candidate);
   allowedDifferences.push({id,field:'explicit java runtime version line only',reference,candidate});
  }
  assert.equal(compareCase(results[0],results[1]),true,id+' differs; inspect raw streams');cases.push({id,status:'PASSED',expectedExit:expected});
  fs.writeFileSync(path.join(out,'progress.json'),JSON.stringify({status:'INCOMPLETE',cases},null,2));
 }
 for(const [id,args] of [['K01-help',['help']],['K01-version',['version']],['K01-version-json',['version','--json']],['K01-capabilities',['capabilities']]])await pair(id,args);
 for(const command of ['check','compile'])for(const file of ['Valid.ili','Valid23.ili'])await pair('K02-'+command+'-'+file,[command,file,'--json','--no-cache']);
 for(const file of ['Syntax.ili','Invalid.ili'])await pair('K03-'+file,['check',file,'--json','--no-cache'],1);
 await pair('K04-imports',['check','Base.ili','Uses.ili','--root','Uses','--json','--no-cache']);
 await pair('K05-analyze',['analyze','Valid.ili','--json','--no-cache']);
 await pair('F01-stdout',['format','Format ä 🌍.ili']);assert.equal(hash(path.join(out,'Format ä 🌍.ili')),fixtureHashes['Format ä 🌍.ili']);
 await pair('F02-check-before',['format','--check','Format ä 🌍.ili'],1);
 await pair('F03-write',['format','--write','Format ä 🌍.ili'],0,true);
 await pair('F02-check-after',['format','--check','Format ä 🌍.ili']);
 requireCases(CORE_CASES,cases);assert.equal(hash(binary),binaryHash);assert.equal(hash(local),binaryHash);
 const report={status:'PASSED',scope:'F2 core only',binary,sha256:binaryHash,manifestFile,inputManifestSha256:hash(manifestFile),harnessSha256,fixtureHashes,allowedDifferences,mode,host:{platform:process.platform,arch:process.arch},emulation:mode.startsWith('linux'),image:mode.startsWith('linux')?image:null,cases};
 fs.writeFileSync(path.join(out,'core-gate.json'),JSON.stringify(report,null,2));console.log('CORE_GATE_PASS '+out);return report;
}
function normalizeRuntimeVersion(text,expected){
 const lines=text.split('\n');assert.equal(lines.filter(line=>line.startsWith('java: ')).length,1,'Exactly one runtime line required');
 assert.ok(lines.includes(expected),'Unexpected runtime version');return lines.map(line=>line===expected?'java: <runtime>':line).join('\n');
}
module.exports={smoke,normalizeRuntimeVersion};
if(require.main===module)smoke(...process.argv.slice(2)).catch(e=>{console.error(e);process.exitCode=1;});
