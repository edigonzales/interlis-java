// One explicitly pinned build-only replacement. No route auto-selection or vendor mutation.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const {record,hash}=require('./record.cjs'),{verifiedInstallation}=require('./toolchain.cjs'),{verifyReceipt}=require('./evidence.cjs');
const ROOT=path.resolve(__dirname,'../..'),PATCH=path.join(__dirname,'compat/cosmogvm-clock/patch.json');
const CLASS='com/oracle/svm/core/posix/cosmo/Target_java_lang_System_Cosmo',REPLACEMENT='guru/interlis/ape/compat/Target_java_lang_System_ApeClock';
const canonical=v=>JSON.stringify(v,(_,value)=>value&&typeof value==='object'&&!Array.isArray(value)?Object.fromEntries(Object.entries(value).sort(([a],[b])=>a.localeCompare(b))):value);
const digest=v=>crypto.createHash('sha256').update(canonical(v)).digest('hex');
function confined(base,relative){assert.ok(typeof relative==='string'&&relative&&!path.isAbsolute(relative)&&!relative.split(/[\\/]/).includes('..'));const file=path.join(base,relative),real=fs.realpathSync(file);assert.ok(real.startsWith(fs.realpathSync(base)+path.sep),'External compatibility path');assert.ok(fs.lstatSync(file).isFile(),'Expected regular file, not symlink');return file;}
function files(base){let result=[];function visit(dir){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,entry.name);assert.ok(!entry.isSymbolicLink(),'Compatibility symlinks are forbidden');if(entry.isDirectory())visit(file);else{assert.ok(entry.isFile());result.push(path.relative(base,file));}}}visit(base);return result.sort();}
/** Parse a policy, never learn a new expected vendor identity from current bytes. */
function readPatchSpec(file){
 assert.ok(path.isAbsolute(file));const spec=JSON.parse(fs.readFileSync(file));
 assert.equal(spec.schemaVersion,'1.0');assert.equal(spec.patchId,'cosmogvm-25.2i-r2-current-time-millis-realtime-v1');assert.equal(spec.strategy,'SUBSTITUTION_JAR');
 assert.equal(spec.classEntry,CLASS+'.class');assert.equal(spec.methodName,'currentTimeMillis');assert.equal(spec.methodDescriptor,'()J');assert.equal(spec.originalClock,'CLOCK_MONOTONIC');assert.equal(spec.replacementClock,'CLOCK_REALTIME');assert.equal(spec.disableTarget,CLASS.replaceAll('/','.')+'.currentTimeMillis');assert.equal(spec.replacementEntry,REPLACEMENT+'.class');
 assert.equal(spec.builderJarRelativePath,'cosmogvm/graalvm-b0d7b51f55-java25-25.2.4-dev/lib/svm/builder/svm.jar');
 for(const field of ['baseLockSha256','installedTreeSha256','builderJarSha256','targetClassSha256','nanoTimeDigest'])assert.match(spec[field],/^[0-9a-f]{64}$/);
 const expected=['build/jenesis/Json.java','tools/ape/compat/cosmogvm-clock/inspector/InspectCosmoClock.java','tools/ape/compat/cosmogvm-clock/src/'+REPLACEMENT+'.java'];assert.deepEqual(Object.keys(spec.sources).sort(),expected.sort());
 for(const [relative,pin] of Object.entries(spec.sources)){assert.match(pin,/^[0-9a-f]{64}$/);assert.equal(hash(confined(ROOT,relative)),pin,'Changed pinned patch source: '+relative);}
 return spec;
}
/** Validate actual class facts, including the original method and the preserved nanoTime. */
function requireOriginal(facts,spec){
 assert.equal(facts.className,CLASS);assert.equal(facts.classSha256,spec.targetClassSha256,'Unknown/already corrected vendor class');assert.equal(facts.jarSha256,spec.builderJarSha256);assert.equal(facts.signedArchive,false,'Signed builder archive requires explicit investigation');
 const methods=facts.methods.filter(m=>m.name===spec.methodName);assert.equal(methods.length,1,'Ambiguous/missing target');const method=methods[0];assert.equal(method.descriptor,'()J');
 assert.equal(method.invokes.filter(i=>i.owner.endsWith('/headers/Time')&&i.name==='CLOCK_MONOTONIC').length,1,'Already fixed or ambiguous clock');assert.ok(!method.invokes.some(i=>i.name==='CLOCK_REALTIME'));
 assert.deepEqual(method,spec.originalMethod,'Unexpected original method semantics or annotations');assert.equal(facts.methods.find(m=>m.name==='nanoTime'&&m.descriptor==='()J')?.structuralDigest,spec.nanoTimeDigest);assert.deepEqual(facts.annotations,spec.targetAnnotations);
 return method;
}
function requireReplacement(facts,spec){
 assert.equal(facts.className,REPLACEMENT);assert.equal(facts.major,69);assert.equal(facts.minor,0);assert.deepEqual(facts.fields,[]);assert.deepEqual(facts.annotations,spec.targetAnnotations);
 assert.deepEqual(facts.methods.map(m=>m.name).sort(),['<init>','currentTimeMillis']);const method=facts.methods.find(m=>m.name==='currentTimeMillis');
 assert.equal(method.descriptor,'()J');assert.equal(method.flags,spec.originalMethod.flags);assert.equal(method.exceptionHandlers,0);assert.deepEqual(method.annotations,spec.originalMethod.annotations);
 assert.deepEqual(method.instructions,spec.originalMethod.instructions.map(s=>s.replace('CLOCK_MONOTONIC','CLOCK_REALTIME')),'Replacement must have exactly the allowed instruction delta');
 const constructor=facts.methods.find(m=>m.name==='<init>');assert.equal(constructor.descriptor,'()V');assert.deepEqual(constructor.instructions,['ALOAD_0 0','INVOKESPECIAL java/lang/Object.<init>()V','RETURN']);
 return method;
}
function java(){const executable=process.env.INTERLIS_APE_JAVA||'java';return executable;}
async function run(dir,name,argv,timeout=120000){const result=await record(dir,name,argv,{cwd:ROOT,timeout});assert.equal(result.exitCode,0,'Compatibility process failed: '+name+'; see '+dir);assert.equal(result.timedOut,false);assert.equal(result.signal,null);return result;}
async function inspector(runDirectory){
 const j=java(),dir=path.join(runDirectory,'inspector-classes');fs.mkdirSync(dir);
 const version=await run(runDirectory,'host-java-version',[j,'-version']);assert.match(fs.readFileSync(version.stderr.path,'utf8'),/version "25[.\"]/,'Inspector requires Java25');
 const javac=path.isAbsolute(j)?path.join(path.dirname(j),'javac'):'javac';await run(runDirectory,'compile-inspector',[javac,'-d',dir,path.join(ROOT,'tools/ape/compat/cosmogvm-clock/inspector/InspectCosmoClock.java'),path.join(ROOT,'build/jenesis/Json.java')]);
 return async(name,args)=>{const file=path.join(runDirectory,name+'.json');await run(runDirectory,name,[j,'-cp',dir,'InspectCosmoClock',...args,file]);return JSON.parse(fs.readFileSync(file));};
}
/** Verify the complete original installation before running its compiler or builder. */
async function inspectVendor(lockFile,specFile,runDirectory){
 const spec=readPatchSpec(specFile);assert.equal(hash(lockFile),spec.baseLockSha256);const tools=verifiedInstallation(lockFile,path.join(ROOT,'target/compiler-ape/toolchains'));assert.equal(tools.lock.installedTreeSha256,spec.installedTreeSha256);
 const jar=confined(tools.installed,spec.builderJarRelativePath);assert.equal(hash(jar),spec.builderJarSha256);
 const inspect=await inspector(runDirectory),facts=await inspect('vendor-inspection',['inspect',jar,spec.classEntry]);requireOriginal(facts,spec);return {spec,tools,inspect,facts,jar};
}
const compileJars=tools=>['svm.jar','svm-shared.jar','native-image-base.jar'].map(n=>tools.graal+'/lib/svm/builder/'+n);
function container(tools,dir,name,program){return ['docker','run','--rm','--name',name,'--platform','linux/amd64','--network','none','--read-only','--memory','7g','--cpus','4','--tmpfs','/tmp:rw,exec,size=2g','--env','HOME=/tmp','--mount','type=bind,src='+tools.installed+',dst=/vendor,readonly','--mount','type=bind,src='+dir+',dst=/work','--workdir','/work','--entrypoint',program,tools.lock.builderImage];}
/** Produce a deterministic one-class JAR; original vendor and seven product JARs are untouched. */
async function prepareCompatibility(lockFile,specFile,outputRoot){
 assert.ok(path.isAbsolute(outputRoot));fs.mkdirSync(outputRoot,{recursive:true});const directory=fs.mkdtempSync(path.join(outputRoot,'.preparing-'));
 const {spec,tools,inspect}=await inspectVendor(lockFile,specFile,directory),sourceFile=path.join(directory,'Target_java_lang_System_ApeClock.java');
 fs.copyFileSync(path.join(ROOT,'tools/ape/compat/cosmogvm-clock/src/'+REPLACEMENT+'.java'),sourceFile);const classes=path.join(directory,'compat-classes');fs.mkdirSync(classes);
 const args=['--add-modules','org.graalvm.nativeimage,org.graalvm.word','-cp',compileJars(tools).join(':'),'-g:none','-source','25','-target','25','-d','compat-classes','Target_java_lang_System_ApeClock.java'];
 const javacFile=confined(tools.installed,tools.graal.slice('/vendor/'.length)+'/bin/javac');const javacSha256=hash(javacFile);
 const name='ape-clock-compat-'+path.basename(directory).replace('.','');await run(directory,'vendor-javac',[...container(tools,directory,name,tools.graal+'/bin/javac'),...args]);
 const classFile=confined(classes,spec.replacementEntry);assert.deepEqual(files(classes),[spec.replacementEntry]);const replacement=await inspect('replacement-inspection',['class',classFile]);requireReplacement(replacement,spec);
 const jar=path.join(directory,'compat.jar');await run(directory,'pack',[java(),'-cp',path.join(directory,'inspector-classes'),'InspectCosmoClock','pack',classFile,spec.replacementEntry,jar]);
 const jarFacts=await inspect('jar-inspection',['inspect',jar,spec.replacementEntry]);assert.deepEqual(jarFacts.entries,[spec.replacementEntry]);requireReplacement(jarFacts,spec);
 const identity={patchSpecSha256:hash(specFile),strategy:spec.strategy,sources:spec.sources,generatorSha256:hash(__filename),baseLockSha256:hash(lockFile),baseInventorySha256:spec.installedTreeSha256,baseJarSha256:spec.builderJarSha256,baseClassSha256:spec.targetClassSha256,javacSha256,compileArguments:args,jarSha256:hash(jar),jarBytes:fs.statSync(jar).size,replacementClassSha256:replacement.classSha256};
 readPatchSpec(specFile);assert.equal(hash(javacFile),javacSha256);assert.equal(hash(confined(tools.installed,spec.builderJarRelativePath)),spec.builderJarSha256);
 const inventory=Object.fromEntries(files(directory).map(relative=>[relative,hash(path.join(directory,relative))]));
 const receipt={schemaVersion:'1.0',kind:'interlis-ape-clock-compatibility',patchId:spec.patchId,strategy:spec.strategy,identity,compatibilityDigest:digest(identity),inventory,output:{relativePath:'compat.jar',sha256:identity.jarSha256,bytes:identity.jarBytes,entries:jarFacts.entries},producerReceipts:['host-java-version.json','compile-inspector.json','vendor-inspection.json','vendor-javac.json','replacement-inspection.json','pack.json','jar-inspection.json'].map(file=>({file,sha256:hash(path.join(directory,file))}))};
 // Recorders contain original absolute run paths. Freeze in place; never rewrite historical argv.
 const file=path.join(directory,'compat-receipt.json');fs.writeFileSync(file,JSON.stringify(receipt,null,2),{flag:'wx'});return file;
}
/** Check policy, source, complete file set, actual producer exits, class delta and vendor identity. */
async function verifyCompatibilityReceipt(file,lockFile,specFile=PATCH){
 assert.ok(path.isAbsolute(file));const directory=path.dirname(file),receipt=JSON.parse(fs.readFileSync(file)),spec=readPatchSpec(specFile);
 assert.equal(receipt.schemaVersion,'1.0');assert.equal(receipt.kind,'interlis-ape-clock-compatibility');assert.equal(receipt.strategy,'SUBSTITUTION_JAR');assert.equal(receipt.patchId,spec.patchId);assert.equal(receipt.compatibilityDigest,digest(receipt.identity));
 const id=receipt.identity;assert.equal(id.patchSpecSha256,hash(specFile));assert.equal(id.generatorSha256,hash(__filename));assert.deepEqual(id.sources,spec.sources);assert.equal(id.baseLockSha256,hash(lockFile));assert.equal(id.baseInventorySha256,spec.installedTreeSha256);assert.equal(id.baseJarSha256,spec.builderJarSha256);assert.equal(id.baseClassSha256,spec.targetClassSha256);
 const tools=verifiedInstallation(lockFile,path.join(ROOT,'target/compiler-ape/toolchains'));assert.equal(hash(confined(tools.installed,spec.builderJarRelativePath)),spec.builderJarSha256);assert.equal(hash(confined(tools.installed,tools.graal.slice('/vendor/'.length)+'/bin/javac')),id.javacSha256);
 assert.deepEqual(files(directory),[...Object.keys(receipt.inventory),'compat-receipt.json'].sort());for(const [relative,pin] of Object.entries(receipt.inventory))assert.equal(hash(confined(directory,relative)),pin,'Changed compatibility output: '+relative);
 for(const producer of receipt.producerReceipts){const p=confined(directory,producer.file);assert.equal(hash(p),producer.sha256);const result=verifyReceipt(p);assert.equal(result.exitCode,0);for(const stream of ['stdout','stderr']){const local=path.join(directory,path.basename(result[stream].path));assert.equal(hash(local),result[stream].sha256);}}
 requireOriginal(JSON.parse(fs.readFileSync(confined(directory,'vendor-inspection.json'))),spec);
 requireReplacement(JSON.parse(fs.readFileSync(confined(directory,'replacement-inspection.json'))),spec);
 const inspected=JSON.parse(fs.readFileSync(confined(directory,'jar-inspection.json')));requireReplacement(inspected,spec);assert.deepEqual(inspected.entries,[spec.replacementEntry]);
 assert.equal(receipt.output.relativePath,'compat.jar');const jar=confined(directory,'compat.jar');assert.equal(hash(jar),id.jarSha256);assert.equal(hash(jar),inspected.jarSha256);assert.equal(fs.statSync(jar).size,id.jarBytes);assert.ok(id.jarBytes>0);assert.equal(receipt.output.sha256,id.jarSha256);assert.equal(receipt.output.bytes,id.jarBytes);assert.deepEqual(receipt.output.entries,[spec.replacementEntry]);
 return {receipt,file,jar,spec,tools,directory};
}
/** Extra build inputs; never returns builder or compatibility classes as product libraries. */
function compatibilityArguments(verified,jarPath){assert.equal(verified.receipt.strategy,'SUBSTITUTION_JAR');assert.equal(verified.spec.disableTarget,CLASS.replaceAll('/','.')+'.currentTimeMillis');assert.ok(jarPath);return {flags:['-H:DisableSubstitution='+verified.spec.disableTarget],classpathEntry:jarPath};}
module.exports={readPatchSpec,requireOriginal,requireReplacement,inspectVendor,prepareCompatibility,verifyCompatibilityReceipt,compatibilityArguments,digest,files,confined,PATCH};
if(require.main===module){const [lock,spec,...extra]=process.argv.slice(2);assert.equal(extra.length,0);prepareCompatibility(lock,spec,path.join(ROOT,'target/compiler-ape/compat')).then(file=>console.log(file)).catch(e=>{console.error(e);process.exitCode=1;});}
