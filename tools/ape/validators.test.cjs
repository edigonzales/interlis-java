const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {readLock,verifyArchive,validateEntry,verifyTree}=require('./toolchain.cjs'),{compareCase,requireCases}=require('./evidence.cjs'),{hash}=require('./record.cjs');
test('lock rejects missing tools, moving version and malformed hash',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ape-lock-')),file=path.join(dir,'lock.json'),original=require('../../build/interlis/ape/toolchain.lock.json');
 try{fs.writeFileSync(file,JSON.stringify(original));readLock(file);
 for(const mutate of [d=>d.archives[0].sha256='bad',d=>d.archives[0].version='latest',d=>d.requiredTools.pop(),d=>d.builderPlatform='darwin-arm64']){const d=structuredClone(original);mutate(d);fs.writeFileSync(file,JSON.stringify(d));assert.throws(()=>readLock(file));}}
 finally{fs.rmSync(dir,{recursive:true});}
});
test('actual archive bytes are checked before extraction',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ape-hash-')),file=path.join(dir,'archive');try{fs.writeFileSync(file,'vendor');const sha256=hash(file);assert.equal(verifyArchive(file,{id:'fixture',sha256}),sha256);fs.appendFileSync(file,'changed');assert.throws(()=>verifyArchive(file,{id:'fixture',sha256}));}finally{fs.rmSync(dir,{recursive:true});}
});
test('archive paths and external symlinks fail closed; internal links retained',()=>{
 for(const name of ['/absolute','a/../escape','a\\escape','C:/escape','a\nentry'])assert.throws(()=>validateEntry(name));validateEntry('cosmo/bin/apelink');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ape-links-'));try{fs.writeFileSync(path.join(dir,'file'),'ok');fs.symlinkSync('file',path.join(dir,'internal'));verifyTree(dir);fs.symlinkSync('/etc/hosts',path.join(dir,'external'));assert.throws(()=>verifyTree(dir));}finally{fs.rmSync(dir,{recursive:true});}
});
test('case completeness refuses missing, duplicate, skipped and failed cases',()=>{
 requireCases(['a','b'],[{id:'a',status:'PASSED'},{id:'b',status:'PASSED'}]);
 for(const rows of [[],[{id:'a',status:'PASSED'}],[{id:'a',status:'PASSED'},{id:'a',status:'PASSED'}],[{id:'a',status:'PASSED'},{id:'b',status:'NOT_RUN_BLOCKED'}]])assert.throws(()=>requireCases(['a','b'],rows));
});
test('exact comparator preserves diagnoses, array order, decimal precision and exits',()=>{
 const r={exitCode:1,signal:null,timedOut:false,stdout:'{"value":12345678901234567890.1234,"diagnostics":["a","b"]}',stderr:'failure'};assert.equal(compareCase(r,{...r}),true);
 for(const c of [{...r,exitCode:0},{...r,stdout:r.stdout.replace('1234','1235')},{...r,stdout:r.stdout.replace('["a","b"]','["b","a"]')},{...r,stderr:''},{...r,timedOut:true}])assert.equal(compareCase(r,c),false);
});
test('pinned installed inventory rejects omitted, extra, missing and changed files',()=>{
 const crypto=require('node:crypto'),{verifiedInstallation}=require('./toolchain.cjs');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ape-inventory-')),lockFile=path.join(dir,'lock.json');
 try{
  const lock=structuredClone(require('../../build/interlis/ape/toolchain.lock.json'));
  const staged=path.join(dir,'staged');fs.mkdirSync(path.join(staged,'cosmogvm'),{recursive:true});fs.mkdirSync(path.join(staged,'cosmocc'));
  fs.writeFileSync(path.join(staged,'cosmogvm/tool'),'vendor');fs.writeFileSync(path.join(staged,'cosmocc/tool'),'vendor');
  for(const tool of lock.requiredTools){const base=['bin/native-image','bin/java','bin/javac'].includes(tool)?'cosmogvm/graalvm-b0d7b51f55-java25-25.2.4-dev':'cosmocc/cosmocc';const file=path.join(staged,base,tool);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,'vendor',{mode:tool.endsWith('.c')?0o644:0o755});}
  const inventories={cosmogvm:verifyTree(path.join(staged,'cosmogvm')),cosmocc:verifyTree(path.join(staged,'cosmocc'))};
  lock.installedTreeSha256=crypto.createHash('sha256').update(JSON.stringify(inventories)).digest('hex');fs.writeFileSync(lockFile,JSON.stringify(lock));
  const installed=path.join(dir,'installed-'+hash(lockFile));fs.renameSync(staged,installed);
  const inventory={lockSha256:hash(lockFile),inventories};fs.writeFileSync(path.join(installed,'inventory.json'),JSON.stringify(inventory));
  verifiedInstallation(lockFile,dir);
  const executable=path.join(installed,'cosmogvm/graalvm-b0d7b51f55-java25-25.2.4-dev/bin/native-image');fs.chmodSync(executable,0o644);assert.throws(()=>verifiedInstallation(lockFile,dir));fs.chmodSync(executable,0o755);
  fs.writeFileSync(path.join(installed,'cosmogvm/extra'),'extra');assert.throws(()=>verifiedInstallation(lockFile,dir));fs.unlinkSync(path.join(installed,'cosmogvm/extra'));
  fs.writeFileSync(path.join(installed,'cosmogvm/tool'),'changed');assert.throws(()=>verifiedInstallation(lockFile,dir));fs.writeFileSync(path.join(installed,'cosmogvm/tool'),'vendor');
  const omitted=structuredClone(inventory);omitted.inventories.cosmogvm=[];fs.writeFileSync(path.join(installed,'inventory.json'),JSON.stringify(omitted));assert.throws(()=>verifiedInstallation(lockFile,dir));fs.writeFileSync(path.join(installed,'inventory.json'),JSON.stringify(inventory));
  fs.unlinkSync(path.join(installed,'cosmogvm/tool'));assert.throws(()=>verifiedInstallation(lockFile,dir));
 }finally{fs.rmSync(dir,{recursive:true});}
});
test('runtime normalization accepts only the one explicit known version line',()=>{
 const {normalizeRuntimeVersion}=require('./core-smoke.cjs');
 const a='tool: compiler\njava: 25.0.3+9-LTS\ndiagnostic: exact\n';
 const b='tool: compiler\njava: 25.0.4+7-jvmci-25.2-b20\ndiagnostic: exact\n';
 assert.equal(normalizeRuntimeVersion(a,'java: 25.0.3+9-LTS'),normalizeRuntimeVersion(b,'java: 25.0.4+7-jvmci-25.2-b20'));
 assert.throws(()=>normalizeRuntimeVersion(a,'java: unknown'));
 assert.throws(()=>normalizeRuntimeVersion(a+'java: duplicate\n','java: 25.0.3+9-LTS'));
 assert.notEqual(normalizeRuntimeVersion(a,'java: 25.0.3+9-LTS'),normalizeRuntimeVersion(b.replace('diagnostic: exact','diagnostic: changed'),'java: 25.0.4+7-jvmci-25.2-b20'));
});
test('formatter IO normalization covers only the exact known temporary filename',()=>{
 const {normalizeWriteFailure:n}=require('./cli-probes.cjs');
 assert.equal(n('format write failed: /blocked/.interlis-output-123.tmp\n','/blocked'),n('format write failed: /blocked/.interlis-output-456.tmp\n','/blocked'));
 for(const value of ['other failure: /blocked/.interlis-output-123.tmp\n','format write failed: /other/.interlis-output-123.tmp\n','format write failed: /blocked/.interlis-output-123.tmp\nextra diagnostic'])assert.throws(()=>n(value,'/blocked'));
});
