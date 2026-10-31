'use strict';
const fs=require('node:fs'),path=require('node:path');const {root,hash}=require('../performance/profile.cjs'),runtimeEvidence=require('../verification/runtime-evidence.cjs');
(async()=>{
 const web=path.resolve(process.argv[2]||path.join(root,'interlis-wasm-teavm/build/web'));if(!web.startsWith(root+path.sep))throw Error('browser output outside project');
 const compiler=path.join(web,'interlis-compiler.wasm'),runtime=path.join(web,'interlis-compiler.wasm-runtime.js');
 const stamp=runtimeEvidence.begin([compiler,runtime].map(p=>path.relative(root,p).split(path.sep).join('/')));
 require(runtime);const api=(await TeaVM.wasmGC.load(compiler)).exports;
 const capabilities=JSON.parse(api.handle(JSON.stringify({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'build-manifest',operation:'capabilities',params:{}})));
 if(capabilities.status!=='ok'||capabilities.protocolVersion!=='1.0')throw Error('cannot obtain actual compiled capabilities');
 const walk=directory=>fs.readdirSync(directory,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(directory,e.name)):[path.join(directory,e.name)]);
 const resources={};for(const file of walk(web).sort()){const name=path.relative(web,file).split(path.sep).join('/');if(name==='build-manifest.json')continue;const bytes=fs.readFileSync(file);resources[name]={sha256:hash(bytes),bytes:bytes.length};}
 const versions=JSON.parse(fs.readFileSync(path.join(root,'build/reports/sdk/version-manifest.json'),'utf8'));
 if(versions.version!==capabilities.result.toolVersion||versions.protocol!==capabilities.result.protocol+'/'+capabilities.result.protocolVersion)throw Error('actual CLI/compiler version manifests disagree');
 const patchFile=path.join(root,'interlis-wasm-teavm/build/tooling/teavm-ordered/patch-info.json');
 const toolchainPatch={...JSON.parse(fs.readFileSync(patchFile,'utf8')),compiledCoreSha256:hash(fs.readFileSync(path.join(path.dirname(patchFile),'teavm-core-0.16.0-ordered.jar')))};
 const result={schemaVersion:'1.0',versions,...runtimeEvidence.finish(stamp),compilerCapabilities:capabilities.result,toolchainPatch,resources};
 const canonical=value=>Array.isArray(value)?value.map(canonical):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(k=>[k,canonical(value[k])])):value;
 fs.writeFileSync(path.join(web,'build-manifest.json'),JSON.stringify(canonical(result),null,2)+'\n');console.log('BROWSER_BUILD_MANIFEST_PASS',Object.keys(resources).length,stamp.gitCommit);
})().catch(error=>{console.error(error);process.exitCode=1;});
