'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),cp=require('node:child_process');const probe=require('./editor-compatibility.cjs');
(async()=>{
 const [root,extensionRoot,referenceBundle,java]=process.argv.slice(2);const prepared=process.env.INTERLIS_EDITOR_PREPARED,editor=process.env.INTERLIS_TEST_CODE;
 if(!prepared||!editor||!path.isAbsolute(prepared)||!path.isAbsolute(editor))throw Error('Set INTERLIS_EDITOR_PREPARED to an existing unchanged prepareDev result, and INTERLIS_TEST_CODE to the isolated host executable. No Gradle preparation occurs inside editor-check.');
 const before=probe.inspectConsumer(extensionRoot);const state={root,commit:cp.execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),status:cp.execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'})};
 fs.mkdirSync(path.join(root,'target/jenesis-jvm/editor-compat'),{recursive:true});
 const scratchRoot=fs.mkdtempSync(path.join(root,'target/jenesis-jvm/editor-compat/run-'));
 for(const file of before.files.filter(f=>f.path.startsWith('src/')||f.path==='package-lock.json')){
  const candidate=file.path==='package-lock.json'?path.join(path.dirname(path.dirname(prepared)),file.path):path.join(prepared,file.path);
  const bytes=fs.readFileSync(candidate);assert.equal(require('node:crypto').createHash('sha256').update(bytes).digest('hex'),file.sha256,'Prepared consumer differs: '+file.path);
 }
 const artifacts=JSON.parse(fs.readFileSync(path.join(root,'target/jenesis-jvm/delivery/runtime-inventory.json')));
 const shadow=probe.assembleShadowBundle({referenceBundle,serverInventory:artifacts['interlis-lsp'],outputRoot:path.join(scratchRoot,'shadow'),coreState:state});
 // Keep the existing prepared checkout and its node resolution intact. Only this
 // fresh development-copy subtree receives replacement artifacts and a testlock.
 const copy=path.join(scratchRoot,'consumer');fs.mkdirSync(path.join(copy,'build'),{recursive:true});fs.cpSync(prepared,path.join(copy,'build/dev-extension'),{recursive:true,dereference:false});
 fs.cpSync(path.join(path.dirname(path.dirname(prepared)),'scripts'),path.join(copy,'scripts'),{recursive:true});fs.symlinkSync(path.join(path.dirname(path.dirname(prepared)),'node_modules'),path.join(copy,'node_modules'),'dir');
 const fixture=probe.prepareConsumerCopy({preparedExtension:path.join(copy,'build/dev-extension'),shadowBundle:shadow});
 const integrity=await probe.verifyLaunchAndIntegrity(fixture,java);const host=probe.runExtensionHostSmoke(fixture,before,editor,scratchRoot,java);
 probe.verifyIsolation(before,probe.inspectConsumer(extensionRoot));assert.deepEqual(probe.inventory(path.join(fixture.preparedExtension,'artifacts')),probe.inventory(shadow.outputRoot));
 fs.writeFileSync(path.join(scratchRoot,'receipt.json'),JSON.stringify({consumer:before,core:state,shadow,integrity,host,source:'explicit nonpackaged development runtime override',isolation:'original consumer and prepared input unchanged'},null,2));
 console.log('EDITOR_CHECK_PASS '+scratchRoot);
})().catch(e=>{console.error(e);process.exitCode=1;});
