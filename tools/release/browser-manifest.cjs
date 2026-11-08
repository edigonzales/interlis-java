'use strict';
const fs=require('node:fs'),path=require('node:path');const {createHash}=require('node:crypto');
const root=fs.realpathSync(path.resolve(__dirname,'../..'));
const hash=value=>createHash('sha256').update(value).digest('hex');
(async()=>{
 const web=path.resolve(process.argv[2]||path.join(root,'interlis-wasm-teavm/build/web'));if(!web.startsWith(root+path.sep))throw Error('browser output outside project');
 const compiler=path.join(web,'interlis-compiler.wasm'),runtime=path.join(web,'interlis-compiler.wasm-runtime.js');
 const artifacts=Object.fromEntries([compiler,runtime].map(file=>[path.relative(root,file).split(path.sep).join('/'),hash(fs.readFileSync(file))]));
 require(runtime);const api=(await TeaVM.wasmGC.load(compiler)).exports;
 const capabilities=JSON.parse(api.handle(JSON.stringify({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:'build-manifest',operation:'capabilities',params:{}})));
 if(capabilities.status!=='ok'||capabilities.protocolVersion!=='1.0')throw Error('cannot obtain actual compiled capabilities');
 const walk=directory=>fs.readdirSync(directory,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(directory,e.name)):[path.join(directory,e.name)]);
 const resources={};for(const file of walk(web).sort()){const name=path.relative(web,file).split(path.sep).join('/');if(name==='build-manifest.json')continue;const bytes=fs.readFileSync(file);resources[name]={sha256:hash(bytes),bytes:bytes.length};}
 const versionPath=process.argv[3];
 if(!versionPath||!path.isAbsolute(versionPath))throw Error('explicit CLI version manifest required');
 const versionBytes=fs.readFileSync(versionPath);
 const versions=JSON.parse(versionBytes);
 if(versions.version!==capabilities.result.toolVersion||versions.protocol!==capabilities.result.protocol+'/'+capabilities.result.protocolVersion)throw Error('actual CLI/compiler version manifests disagree');
 const toolchain=JSON.parse(fs.readFileSync(path.join(root,'interlis-wasm-teavm/build/tooling/teavm/generateWasmGC.json'),'utf8'));
 const pin=JSON.parse(fs.readFileSync(path.join(root,'gradle/teavm-core.json'),'utf8'));
 if(JSON.stringify(toolchain.core)!==JSON.stringify(pin)||toolchain.artifactSha256!==hash(fs.readFileSync(compiler)))throw Error('unbound upstream TeaVM generation');
 for(const [file,sha256] of Object.entries(artifacts))if(hash(fs.readFileSync(path.join(root,file)))!==sha256)throw Error('browser artifact changed during preparation');
 const result={schemaVersion:'2.0',versions,gitCommit:versions.gitCommit,artifacts,compilerCapabilities:capabilities.result,toolchain:{core:toolchain.core},resources};
 fs.writeFileSync(path.join(web,'build-manifest.json'),JSON.stringify(result,null,2)+'\n');console.log('BROWSER_BUILD_MANIFEST_PASS',Object.keys(resources).length,versions.gitCommit);
})().catch(error=>{console.error(error);process.exitCode=1;});
