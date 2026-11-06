'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..');
const handoffPath=process.env.INTERLIS_SNAPSHOT_HANDOFF;
if(!handoffPath||!path.isAbsolute(handoffPath))throw Error('INTERLIS_SNAPSHOT_HANDOFF must be the absolute wasm-export receipt path');
const handoff=JSON.parse(fs.readFileSync(handoffPath));
const original=JSON.parse(fs.readFileSync(handoff.manifest));
const os=require('node:os'),scratch=fs.mkdtempSync(path.join(os.tmpdir(),'interlis-snapshot-test-'));
test.after(()=>fs.rmSync(scratch,{recursive:true,force:true}));
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin','java'):'java';
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const canonical=value=>Array.isArray(value)?value.map(canonical):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(key=>[key,canonical(value[key])])):value;
const payloadKeys=['schemaVersion','kind','projectVersion','productInputDigest','enginePinDigest','artifacts','closures','files'];
function fixture(name){const dir=path.join(scratch,name);fs.cpSync(path.dirname(handoff.manifest),dir,{recursive:true});return {dir,manifest:path.join(dir,'consumer-manifest.json'),data:structuredClone(original)};}
function write(f,recompute=true){if(recompute)f.data.snapshotDigest=sha(JSON.stringify(canonical(Object.fromEntries(payloadKeys.map(k=>[k,f.data[k]])))));fs.writeFileSync(f.manifest,JSON.stringify(canonical(f.data))+'\n');return sha(fs.readFileSync(f.manifest));}
function check(manifest,expected,accept){const result=cp.spawnSync(java,['build/interlis/ConsumerSnapshot.java','verify',manifest,expected],{cwd:root,encoding:'utf8',timeout:20000});assert.equal(result.signal,null);assert.equal(result.error,undefined);if(accept)assert.equal(result.status,0,result.stderr);else assert.notEqual(result.status,0,'Invalid snapshot accepted');return result;}
test('real producer snapshot is accepted',()=>check(handoff.manifest,handoff.manifestSha256,true));
test('independent expected hash is mandatory',()=>check(handoff.manifest,'0'.repeat(64),false));
test('unknown schema fails even with a matching local hash',()=>{const f=fixture('schema');f.data.schemaVersion=2;check(f.manifest,write(f),false);});
test('empty required closure fails',()=>{const f=fixture('empty');f.data.closures.teavm=[];check(f.manifest,write(f),false);});
test('missing actual JAR fails',()=>{const f=fixture('missing');fs.unlinkSync(path.join(f.dir,f.data.artifacts[0].jar.path));check(f.manifest,handoff.manifestSha256,false);});
test('extra JAR is rejected by the exact inventory',()=>{const f=fixture('extra');fs.writeFileSync(path.join(f.dir,'injected.jar'),'extra');check(f.manifest,handoff.manifestSha256,false);});
test('JAR and narrow local hash cannot replace producer reference',()=>{const f=fixture('rehashed'),row=f.data.artifacts[0],file=path.join(f.dir,row.jar.path);fs.appendFileSync(file,'changed');row.jar.sha256=sha(fs.readFileSync(file));row.jar.bytes=fs.statSync(file).size;f.data.files[row.jar.path]={sha256:row.jar.sha256,bytes:row.jar.bytes};write(f);check(f.manifest,handoff.manifestSha256,false);});
test('sources are required for every own product',()=>{const f=fixture('sources');delete f.data.artifacts.find(a=>a.projectId).sources;check(f.manifest,write(f),false);});
test('POM GAV must match its coordinate',()=>{const f=fixture('pom'),row=f.data.artifacts.find(a=>a.projectId==='interlis-model'),file=path.join(f.dir,row.pom.path);const before=fs.readFileSync(file,'utf8'),after=before.replace('<artifactId>interlis-model</artifactId>','<artifactId>foreign-model</artifactId>');assert.notEqual(after,before);fs.writeFileSync(file,after);row.pom.sha256=sha(fs.readFileSync(file));row.pom.bytes=fs.statSync(file).size;f.data.files[row.pom.path]={sha256:row.pom.sha256,bytes:row.pom.bytes};check(f.manifest,write(f),false);});
test('required parent POM cannot be omitted from a closed repository',()=>{const f=fixture('parent'),parent=Object.keys(f.data.files).find(p=>p.endsWith('/oss-parent-9.pom'));assert.ok(parent);fs.unlinkSync(path.join(f.dir,parent));delete f.data.files[parent];check(f.manifest,write(f),false);});
test('path traversal is rejected before external input can be used',()=>{const f=fixture('traversal');f.data.files['../outside.jar']=f.data.files[f.data.artifacts[0].jar.path];check(f.manifest,write(f),false);});
test('absolute inventory paths are rejected',()=>{const f=fixture('absolute');f.data.files[path.join(scratch,'outside.jar')]=f.data.files[f.data.artifacts[0].jar.path];check(f.manifest,write(f),false);});
test('same-byte symlink escape is rejected',()=>{const f=fixture('symlink'),file=path.join(f.dir,f.data.artifacts[0].jar.path),outside=path.join(scratch,'outside.jar');fs.copyFileSync(file,outside);fs.unlinkSync(file);fs.symlinkSync(outside,file);check(f.manifest,handoff.manifestSha256,false);});

test('own module descriptor metadata cannot claim an automatic module',()=>{const f=fixture('own-automatic');f.data.artifacts.find(a=>a.projectId).descriptorKind='automatic';check(f.manifest,write(f),false);});
