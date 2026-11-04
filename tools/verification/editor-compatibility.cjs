'use strict';
// Explicit scratch-only consumer probe. Existing extension code owns launch/runtime validation.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process'),assert=require('node:assert/strict');
const hash=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
function inventory(root){const result=[];function visit(dir){for(const item of fs.readdirSync(dir,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name,'en'))){const file=path.join(dir,item.name);if(item.isSymbolicLink())throw Error('Symlink in bundle');if(item.isDirectory())visit(file);else if(item.isFile())result.push({path:path.relative(root,file).split(path.sep).join('/'),sha256:hash(file)});else throw Error('Nonregular bundle entry');}}visit(root);return result;}
function inspectConsumer(extensionRoot){const git=(...args)=>cp.execFileSync('git',args,{cwd:extensionRoot,encoding:'utf8'});return {root:extensionRoot,commit:git('rev-parse','HEAD').trim(),tree:git('rev-parse','HEAD^{tree}').trim(),status:git('status','--porcelain'),files:git('ls-files','-z').split('\0').filter(Boolean).map(p=>({path:p,sha256:hash(path.join(extensionRoot,p))}))};}
function assembleShadowBundle({referenceBundle,serverInventory,outputRoot,coreState}){
 if(fs.existsSync(outputRoot))throw Error('Shadow output must be fresh');fs.mkdirSync(outputRoot,{recursive:true});
 const manifest=JSON.parse(fs.readFileSync(path.join(referenceBundle,'manifest.json')));const actual=inventory(referenceBundle).filter(f=>f.path!=='manifest.json');assert.deepEqual(new Map(actual.map(f=>[f.path,f.sha256])),new Map(manifest.files.map(f=>[f.path,f.sha256])));
 const nonserver=manifest.files.filter(f=>!f.path.startsWith('server/'));
 for(const file of nonserver){if(path.isAbsolute(file.path)||file.path.split('/').some(s=>s==='..'||s===''))throw Error('Unsafe reference entry');const dest=path.join(outputRoot,file.path);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(path.join(referenceBundle,file.path),dest);assert.equal(hash(dest),file.sha256);}
 const names=new Set();for(const file of serverInventory){const name=path.basename(file.file);if(!/^[A-Za-z0-9_.-]+\.jar$/.test(name)||names.has(name))throw Error('Unsafe/duplicate server artifact');names.add(name);const source=path.resolve(coreState.root,file.file);if(!source.startsWith(path.join(coreState.root,'target/jenesis-jvm/') )||hash(source)!==file.sha256)throw Error('Foreign/changed Jenesis artifact');const dest=path.join(outputRoot,'server/lib',name);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(source,dest);}
 const files=inventory(outputRoot);fs.writeFileSync(path.join(outputRoot,'manifest.json'),JSON.stringify({...manifest,files},null,2)+'\n');return {outputRoot,files,nonserver,serverInventory,coreState};
}
function prepareConsumerCopy({preparedExtension,shadowBundle}){
 const previous=JSON.parse(fs.readFileSync(path.join(preparedExtension,'release/editor-artifacts.lock.json')));if(fs.existsSync(path.join(preparedExtension,'release/distribution.json')))throw Error('Use existing nonpackaged development mode');
 // Only an explicitly prepared scratch extension may be replaced.
 if(!fs.existsSync(path.join(preparedExtension,'build/launch.cjs'))||!fs.existsSync(path.join(preparedExtension,'out/extension.cjs')))throw Error('Run unchanged prepareDev first');
 fs.rmSync(path.join(preparedExtension,'artifacts'),{recursive:true});fs.cpSync(shadowBundle.outputRoot,path.join(preparedExtension,'artifacts'),{recursive:true});
 const files=shadowBundle.files.filter(f=>f.path.startsWith('server/')).map(f=>({path:f.path.slice(7),sha256:f.sha256}));
 fs.writeFileSync(path.join(preparedExtension,'release/editor-artifacts.lock.json'),JSON.stringify({...previous,files,rendererFiles:shadowBundle.nonserver},null,2)+'\n');return {preparedExtension,shadowBundle};
}
async function verifyLaunchAndIntegrity({preparedExtension,shadowBundle},java){
 const {resolveLaunchPlan}=require(path.join(preparedExtension,'build/launch.cjs'));const verify=()=>resolveLaunchPlan(preparedExtension,java);
 const plan=await verify();assert.deepEqual(plan.args,['-cp',path.join(preparedExtension,'artifacts/server/lib','*'),'guru.interlis.lsp.Main']);assert.equal(plan.runtimeSource,'developer override');
 const lib=path.join(preparedExtension,'artifacts/server/lib'),first=fs.readdirSync(lib).sort()[0],file=path.join(lib,first),bytes=fs.readFileSync(file);const results=[];
 fs.unlinkSync(file);try{await assert.rejects(verify);results.push('missing rejected');}finally{fs.writeFileSync(file,bytes);}
 fs.appendFileSync(file,'changed');try{await assert.rejects(verify,/digest mismatch/);results.push('changed rejected');}finally{fs.writeFileSync(file,bytes);}
 const extra=path.join(lib,'unexpected.jar');fs.writeFileSync(extra,bytes);try{await assert.rejects(verify,/Unexpected or missing/);results.push('extra rejected');}finally{fs.unlinkSync(extra);}
 const finalPlan=await verify();assert.deepEqual(finalPlan,plan);
 assert.deepEqual(new Map(inventory(path.join(preparedExtension,'artifacts')).map(f=>[f.path,f.sha256])),new Map(inventory(shadowBundle.outputRoot).map(f=>[f.path,f.sha256])));return {plan,results,loaded:inventory(lib),launchSourceSha256:hash(path.join(preparedExtension,'src/host/launch.ts'))};
}
function runExtensionHostSmoke({preparedExtension},consumer,editorExecutable,scratchRoot,java){
 if(!path.isAbsolute(editorExecutable)||!fs.existsSync(editorExecutable))throw Error('Explicit isolated editor executable required');
 const harness=path.join(scratchRoot,'harness');fs.mkdirSync(path.join(harness,'test/extension-host'),{recursive:true});
 fs.cpSync(path.join(consumer.root,'test/fixtures'),path.join(harness,'test/fixtures'),{recursive:true});
 fs.copyFileSync(path.join(__dirname,'editor-host-smoke.cjs'),path.join(harness,'test/extension-host/index.cjs'));
 const receipt=path.join(scratchRoot,'host-receipt.json');
 const args=[path.join(path.dirname(path.dirname(preparedExtension)),'scripts/extension-host.cjs')];
 const env={...process.env,INTERLIS_TEST_CODE:editorExecutable,INTERLIS_TEST_EXTENSION_ROOT:preparedExtension,INTERLIS_JAVA:java,INTERLIS_EDITOR_HOST_RECEIPT:receipt};
 fs.writeFileSync(path.join(scratchRoot,'host-command.json'),JSON.stringify({command:[process.execPath,...args],cwd:harness,environment:{INTERLIS_TEST_CODE:editorExecutable,INTERLIS_TEST_EXTENSION_ROOT:preparedExtension,INTERLIS_JAVA:java,INTERLIS_EDITOR_HOST_RECEIPT:receipt}},null,2));
 const result=cp.spawnSync(process.execPath,args,{cwd:harness,env,encoding:'utf8',timeout:90000,maxBuffer:8*1024*1024,killSignal:'SIGKILL'});
 fs.writeFileSync(path.join(scratchRoot,'host.log'),(result.stdout||'')+(result.stderr||''));fs.writeFileSync(path.join(scratchRoot,'host-exit.json'),JSON.stringify({exitCode:result.status,signal:result.signal,error:result.error?.message}));
 if(result.status!==0||result.signal||result.error)throw Error('Extension host failed; see '+scratchRoot);
 return JSON.parse(fs.readFileSync(receipt));
}
function verifyIsolation(before,after){assert.deepEqual(after,before);return true;}
module.exports={inspectConsumer,assembleShadowBundle,prepareConsumerCopy,verifyLaunchAndIntegrity,runExtensionHostSmoke,verifyIsolation,inventory};
if(require.main===module){(async()=>{const [root,preparedExtension,referenceBundle,outputRoot,java]=process.argv.slice(2);if(!java)throw Error('Arguments: core prepared-development-extension reference-bundle fresh-shadow java25');const serverInventory=JSON.parse(fs.readFileSync(path.join(root,'target/jenesis-jvm/delivery/runtime-inventory.json')))['interlis-lsp'];const coreState={root,commit:cp.execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),status:cp.execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'})};const shadowBundle=assembleShadowBundle({referenceBundle,serverInventory,outputRoot,coreState});const fixture=prepareConsumerCopy({preparedExtension,shadowBundle});const result=await verifyLaunchAndIntegrity(fixture,java);fs.writeFileSync(outputRoot+'.receipt.json',JSON.stringify({shadowBundle,result},null,2));console.log('EDITOR_INTEGRITY_PASS: positive, missing, changed, extra, restored; '+result.plan.runtimeSource);})().catch(e=>{console.error(e);process.exitCode=1;});}
