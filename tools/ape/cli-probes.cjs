// Additional F3 CLI and host cases; original binary and production sources remain untouched.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {record,hash}=require('./record.cjs'),{loadCandidate}=require('./bundle.cjs'),{compareCase,requireCases,verifyReceipt}=require('./evidence.cjs');
const source=fs.readFileSync(__filename),sourceSha256=require('node:crypto').createHash('sha256').update(source).digest('hex');
const REQUIRED=['K06-json','K06-mermaid','K06-plantuml','K07-check','K07-de','K07-en','K08-option','K08-file','K08-config','K09-never','K09-always','K09-json','F04-lf','F04-crlf','F05','F06','H01','H02','H03','H04','H05'];
function normalizeWriteFailure(stderr,directory){
 const prefix='format write failed: '+path.join(directory,'.interlis-output-');
 assert.ok(stderr.startsWith(prefix),'Unexpected IO failure prefix');
 assert.match(stderr.slice(prefix.length),/^[0-9]+\.tmp\n$/,'Unexpected IO failure suffix');
 return prefix+'<temporary-id>.tmp\n';
}
async function cli(binary,inputFile,java,out,resumeDirectory){
 assert.equal(process.platform,'darwin');assert.equal(process.arch,'arm64');assert.ok(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'cli-source-'+sourceSha256+'.cjs'),source);
 const original=loadCandidate(path.join(path.dirname(binary),'bundle-manifest.json')),install=path.join(out,'Installation ä 🌍',original.bundle.sha256);fs.cpSync(original.directory,install,{recursive:true});const delivery=loadCandidate(path.join(install,'bundle-manifest.json'));
 const payload=delivery.binary,loader=path.join(install,'ape-loader-macos-arm64'),inputs=JSON.parse(fs.readFileSync(inputFile)),classpath=inputs.orderedRuntime.map(row=>path.join(path.dirname(inputFile),row.path)).join(path.delimiter);
 const model='INTERLIS 2.4;\nCONTRACTED MODEL Details AT "x" VERSION "1" =\nFUNCTION f(value: BOOLEAN): BOOLEAN // Erklärung 🌍\n  zweite Zeile //;\nTOPIC T =\nCLASS Parent =\n n : 0.00..9007199254740993.99;\nEND Parent;\nCLASS Child EXTENDS Parent =\n label : TEXT*40; !! Grüsse 🌍\n MANDATORY CONSTRAINT label == "Grüezi 🌍";\nEND Child;\nEND T;\nEND Details.\n';
 const invalid=fs.readFileSync(path.join(__dirname,'../../testdata/ape/Invalid.ili'),'utf8'),syntax='INTERLIS 2.4; MODEL Broken AT "x" VERSION "1" = END Broken. TRAILING\n';
 fs.writeFileSync(path.join(out,'Details ä 🌍.ili'),model);fs.writeFileSync(path.join(out,'Invalid.ili'),invalid);fs.writeFileSync(path.join(out,'Broken.ili'),syntax);fs.writeFileSync(path.join(out,'invalid.properties'),'read-timeout=0\n');
 const cases=[];let active;
 const previous=resumeDirectory?JSON.parse(fs.readFileSync(path.join(resumeDirectory,'cli-report.json'))):null;
 if(previous){assert.equal(previous.candidateSha256,hash(binary));assert.equal(previous.bundleManifestSha256,hash(path.join(original.directory,'bundle-manifest.json')));for(const file of ['Details ä 🌍.ili','Invalid.ili','Broken.ili','invalid.properties'])assert.equal(hash(path.join(resumeDirectory,file)),hash(path.join(out,file)));}

 function report(status,error){fs.writeFileSync(path.join(out,'cli-report.json'),JSON.stringify({status,error,candidateSha256:hash(binary),bundleManifestSha256:hash(path.join(original.directory,'bundle-manifest.json')),harnessSha256:sourceSha256,host:{platform:process.platform,arch:process.arch},emulation:false,cases,remaining:REQUIRED.filter(id=>!cases.some(c=>c.id===id)).map(id=>({id,status:id===active?'FAILED':'NOT_RUN_BLOCKED'}))},null,2));}
 async function pair(id,args,expected=0,options={}){
  active=id;const observed=[];const reuse=previous?.cases.some(c=>c.id===id&&c.status==='PASSED');
  for(const label of ['jvm','native']){
   options.before?.(label);const home=path.join(out,'home-'+label);fs.mkdirSync(home,{recursive:true});const env={PATH:'/usr/bin:/bin:/usr/sbin:/sbin',HOME:home,TMPDIR:options.temp||out,LANG:'en_US.UTF-8'};
   const command=label==='jvm'?[java,'-Duser.home='+home,...(options.properties||[]),'-cp',classpath,inputs.mainClass,...args]:[loader,payload,...(options.properties||[]),...args];
   const r=reuse?verifyReceipt(path.join(resumeDirectory,id+'-'+label+'.json')):await record(out,id+'-'+label,command,{cwd:options.cwd||out,env,timeout:20000});const value={...r,stdout:fs.readFileSync(r.stdout.path,'utf8'),stderr:fs.readFileSync(r.stderr.path,'utf8')};assert.equal(r.timedOut,false,id);assert.equal(r.signal,null,id);assert.equal(r.exitCode,expected,id+' '+label+': '+value.stderr+' '+value.stdout);options.observe?.(value,label);observed.push(value);
  }
  const compared=options.normalize?observed.map(r=>({...r,stderr:options.normalize(r.stderr)})):observed;assert.equal(compareCase(compared[0],compared[1]),true,id+' output differs; inspect original streams');cases.push({id,status:'PASSED',execution:reuse?'REUSED':'EXECUTED',...(reuse?{originReport:path.join(resumeDirectory,'cli-report.json'),originReportSha256:hash(path.join(resumeDirectory,'cli-report.json'))}:{})});active=null;report('INCOMPLETE');return observed;
 }
 try{
  for(const format of ['json','mermaid','plantuml'])await pair('K06-'+format,['diagram','Details ä 🌍.ili','--format',format,'--no-cache'],0,{observe:r=>{if(format==='json')assert.match(r.stdout,/"edges":\[\s*\{/);}});
  await pair('K07-check',['check','Details ä 🌍.ili','--json','--no-cache']);
  for(const language of ['de','en'])await pair('K07-'+language,['check','Invalid.ili','--diagnostic-language',language,'--color','never','--no-cache'],1,{observe:r=>assert.ok(r.stderr.includes('ILI-SEM-'))});
  await pair('K08-option',['check','--unknown-option'],2);
  await pair('K08-file',['check','absent.ili','--no-cache'],1);
  await pair('K08-config',['check','--config','invalid.properties','--explain-config'],2);
  await pair('K09-never',['check','Invalid.ili','--color','never','--no-cache'],1,{observe:r=>assert.ok(!r.stderr.includes('\x1b['))});
  await pair('K09-always',['check','Invalid.ili','--color','always','--no-cache'],1,{observe:r=>assert.ok(r.stderr.includes('\x1b['))});
  await pair('K09-json',['check','Invalid.ili','--json','--color','always','--no-cache'],1,{observe:r=>assert.ok(!r.stdout.includes('\x1b[')&&!r.stderr.includes('\x1b['))});
  for(const ending of ['lf','crlf'])await pair('F04-'+ending,['format','Details ä 🌍.ili','--indent','4','--line-ending',ending],0,{observe:r=>{assert.ok(r.stdout.includes('9007199254740993.99'));assert.ok(r.stdout.includes('// Erklärung 🌍\n  zweite Zeile //'));assert.ok(r.stdout.includes('"Grüezi 🌍"'));}});
  const broken=path.join(out,'Broken.ili'),brokenHash=hash(broken);await pair('F05',['format','--write','Broken.ili'],1);assert.equal(hash(broken),brokenHash);
  const blocked=path.join(out,'blocked');fs.mkdirSync(blocked);const readonly=path.join(blocked,'Original.ili');fs.writeFileSync(readonly,model);fs.chmodSync(blocked,0o555);let denied=false;try{fs.writeFileSync(path.join(blocked,'denial-control'),'must fail');}catch{denied=true;}assert.ok(denied,'Readonly directory must be a real IO barrier');
  try{const before=hash(readonly);await pair('F06',['format','--write',readonly],1,{normalize:s=>normalizeWriteFailure(s,blocked)});assert.equal(hash(readonly),before);}finally{fs.chmodSync(blocked,0o755);}
  const cwd=path.join(out,'another cwd');fs.mkdirSync(cwd);await pair('H01',['check','../Details ä 🌍.ili','--json','--no-cache'],0,{cwd});
  await pair('H02',['check',path.join(out,'Details ä 🌍.ili'),'--json','--no-cache']);
  active='H03';const sandbox=path.join(out,'no-builder.sb'),repo=path.resolve(__dirname,'../..');
  const deniedPaths=[repo,'/Users/stefan/.sdkman','/Library/Java','/System/Library/Java','/usr/lib/jvm','/opt/java','/vendor','/Library/Developer','/Users/stefan/sources/interlis-java-ape-evidence/toolchains','/Users/stefan/sources/interlis-java-ape-evidence/inputs'];
  fs.writeFileSync(sandbox,'(version 1)\n(allow default)\n(deny network*)\n(deny file-read-data '+deniedPaths.map(p=>'(subpath '+JSON.stringify(p)+')').join(' ')+')\n(deny process-exec (regex #".*/java$") (regex #".*/javac$") (regex #".*/cc$") (regex #".*/clang$") (regex #".*/xcrun$"))\n');
  const witness=path.join(path.dirname(inputFile),inputs.orderedRuntime[0].path);
  const denial=await record(out,'H03-denial-control',['/usr/bin/sandbox-exec','-f',sandbox,'/bin/cat',witness],{timeout:10000});assert.notEqual(denial.exitCode,0);assert.match(fs.readFileSync(denial.stderr.path,'utf8'),/Operation not permitted|Permission denied/);
  const home=path.join(out,'fresh home'),temp=path.join(out,'fresh temp');fs.mkdirSync(home);fs.mkdirSync(temp);
  const isolated=await record(out,'H03-native',['/usr/bin/sandbox-exec','-f',sandbox,loader,payload,'check',path.join(out,'Details ä 🌍.ili'),'--json','--no-cache'],{cwd,env:{PATH:'/usr/bin:/bin',HOME:home,TMPDIR:temp,LANG:'en_US.UTF-8'},timeout:20000});assert.equal(isolated.exitCode,0);assert.equal(isolated.timedOut,false);assert.equal(isolated.signal,null);assert.equal(fs.readdirSync(temp).length,0);cases.push({id:'H03',status:'PASSED',observation:'Actual source/JDK/SDK read and JVM/CC execution barriers; delivered loader only'});active=null;report('INCOMPLETE');
  const noTemp=path.join(out,'readonly temp');fs.mkdirSync(noTemp);fs.chmodSync(noTemp,0o555);try{await pair('H04',['check','Details ä 🌍.ili','--json','--no-cache'],0,{temp:noTemp,properties:['-Djava.io.tmpdir='+noTemp]});assert.equal(fs.readdirSync(noTemp).length,0);}finally{fs.chmodSync(noTemp,0o755);}
  active='H05';const large=path.join(out,'Large.ili');fs.writeFileSync(large,'INTERLIS 2.4; MODEL Large AT "x" VERSION "1" = TOPIC T =\n'+Array.from({length:10000},(_,i)=>'CLASS C'+i+' = value : TEXT*20; END C'+i+';\n').join('')+'END T; END Large.\n');
  const killed=await record(out,'H05-native',[loader,payload,'check',large,'--no-cache','--json'],{cwd:out,timeout:150});assert.equal(killed.timedOut,true);assert.equal(killed.signal,'SIGKILL');assert.equal(killed.exitCode,null);cases.push({id:'H05',status:'PASSED',kind:'process-control',observation:'Intended own-process termination; not a compiler cancellation/success exit'});active=null;
  requireCases(REQUIRED,cases);assert.equal(hash(binary),original.bundle.sha256);assert.equal(hash(payload),original.bundle.sha256);report('PASSED');console.log('CLI_HOST_GATE_PASS '+out);
 }catch(error){report('FAILED',error.message);throw error;}
}
module.exports={cli,normalizeWriteFailure};if(require.main===module)cli(...process.argv.slice(2)).catch(e=>{console.error(e);process.exitCode=1;});
