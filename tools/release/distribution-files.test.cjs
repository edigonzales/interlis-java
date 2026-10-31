'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {distributionFiles,browserResources}=require('./distribution-files.cjs');
function fixture(t){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'interlis inventory '));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 fs.writeFileSync(path.join(root,'gradle.properties'),'projectVersion=1-test\n');
 const cli='interlis-cli/build/install/interlis-cli',tea='interlis-wasm-teavm/build';
 const files=[...['interlis-cli','interlis-cli.bat','ili2c','ili2c.bat'].map(n=>cli+'/bin/'+n),cli+'/compat/ili2c.jar',
  ...['cli','compiler','model','projection','repository','workspace'].map(n=>cli+'/lib/interlis-'+n+'-1-test.jar'),
  'build/releases/interlis-java-sdk-1-test.zip',tea+'/generated/teavm/js/interlis-compiler.js',tea+'/generated/teavm/wasm-gc/interlis-compiler.wasm',tea+'/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js',
  tea+'/browser-tools/z/nested.js',tea+'/browser-tools/a.js',tea+'/web/index.html',tea+'/web/build-manifest.json'];
 for(const file of [...files].reverse()){const output=path.join(root,file);fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,'fixture');}
 return {root,files,cli,tea};
}
test('inventory includes all actual shipped files once in deterministic relative order',t=>{
 const f=fixture(t);assert.deepEqual(distributionFiles(f.root),[...f.files].sort());
});
test('an additional actual library is independently required in the report inventory',t=>{
 const f=fixture(t),extra=f.cli+'/lib/additional.jar';fs.writeFileSync(path.join(f.root,extra),'library');
 assert.deepEqual(distributionFiles(f.root),[...f.files,extra].sort());
});
test('browser resources come from all actual files with hashes/sizes and no self-reference',t=>{
 const f=fixture(t),extra=f.tea+'/web/new-resource.js';fs.writeFileSync(path.join(f.root,extra),'new bytes');
 const resources=browserResources(f.root);assert.deepEqual(Object.keys(resources),['index.html','new-resource.js']);
 assert.equal(resources['new-resource.js'].bytes,9);assert.match(resources['new-resource.js'].sha256,/^[a-f0-9]{64}$/);
 assert.equal(resources['build-manifest.json'],undefined);
});
for(const [name,file]of [['launcher','interlis-cli/build/install/interlis-cli/bin/interlis-cli'],['public SDK library','interlis-cli/build/install/interlis-cli/lib/interlis-model-1-test.jar'],['SDK ZIP','build/releases/interlis-java-sdk-1-test.zip'],['Wasm','interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm']])test('missing actual '+name+' is not silently omitted',t=>{
 const f=fixture(t);fs.unlinkSync(path.join(f.root,file));assert.throws(()=>distributionFiles(f.root));
});
test('missing browser tree fails',t=>{const f=fixture(t);fs.rmSync(path.join(f.root,f.tea+'/web'),{recursive:true});assert.throws(()=>distributionFiles(f.root));});
test('empty browser tree fails',t=>{const f=fixture(t);fs.rmSync(path.join(f.root,f.tea+'/web'),{recursive:true});fs.mkdirSync(path.join(f.root,f.tea+'/web'));assert.throws(()=>distributionFiles(f.root));});
test('invalid distribution version fails before path traversal',t=>{const f=fixture(t);fs.writeFileSync(path.join(f.root,'gradle.properties'),'projectVersion=../wrong\n');assert.throws(()=>distributionFiles(f.root));});
