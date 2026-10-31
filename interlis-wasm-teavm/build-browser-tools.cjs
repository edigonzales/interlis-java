const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto'),{pathToFileURL}=require('node:url');
const esbuild=require('esbuild');
const root=__dirname,output=path.join(root,'build/browser-tools'),types=path.join(output,'types');
const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
const read=p=>fs.readFileSync(path.join(root,p));
const pin=JSON.parse(read('../docs/p3/diagram-adapter-pin.json'));
for(const [file,identity]of Object.entries(pin.vendoredFiles))if(hash(read('../'+file))!==identity.sha256)throw new Error('vendored diagram contract changed: '+file);
if(hash(read('../'+pin.diagramSchema.file))!==pin.diagramSchema.sha256)throw new Error('diagram schema pin mismatch');
if(hash(read('../'+pin.diagramOptionsSchema.file))!==pin.diagramOptionsSchema.sha256)throw new Error('diagram options schema pin mismatch');
function run(args){cp.execFileSync(process.execPath,[path.join(root,'node_modules/typescript/bin/tsc'),...args],{cwd:root,stdio:'inherit'});}
fs.rmSync(output,{recursive:true,force:true});fs.mkdirSync(output,{recursive:true});
run(['-p','tsconfig.json']);
run(['-p','tsconfig.json','--noEmit','false','--declaration','--emitDeclarationOnly','--outDir',types,'--rootDir','src/main/typescript']);
const options={bundle:true,target:'es2022',legalComments:'linked',tsconfigRaw:{compilerOptions:{target:'ES2022',esModuleInterop:true}},logLevel:'warning'};
for(const [entry,name] of [['compiler-client','compiler-client'],['diagram-adapter','compiler-diagram']]){
  esbuild.buildSync({...options,entryPoints:[path.join(root,'src/main/typescript',entry+'.ts')],platform:'browser',format:'esm',external:entry==='diagram-adapter'?['libavoid-js']:[],outfile:path.join(output,name+'.js')});
}
for(const name of ['compiler-client','worker-host','diagram-adapter']){
  esbuild.buildSync({...options,entryPoints:[path.join(root,'src/main/typescript',name+'.ts')],platform:'node',format:'cjs',external:['libavoid-js'],
    define:{'import.meta.url':JSON.stringify(pathToFileURL(path.join(root,'build',name+'.cjs')).href)},outfile:path.join(root,'build',name+'.cjs')});
}
const vendor=path.join(types,'vendor/ilic-diagram');fs.mkdirSync(vendor,{recursive:true});
for(const name of ['compiler-contract.d.ts','elk-contract.d.ts','libavoid-js.d.ts','LICENSE','ELK-LICENSE.md'])fs.copyFileSync(path.join(root,'src/main/typescript/vendor/ilic-diagram',name),path.join(vendor,name));
// A type-only workspace alias is replaced with the identical shipped contract;
// declarations remain usable without the unpublished upstream npm package.
const index=path.join(vendor,'index.d.ts');fs.writeFileSync(index,fs.readFileSync(index,'utf8').replaceAll('"@ilic/language-service"','"./compiler-contract"'));
const avoid=path.join(output,'vendor/libavoid');fs.mkdirSync(avoid,{recursive:true});
for(const name of ['index.js','index.js.map','libavoid.wasm'])fs.copyFileSync(path.join(root,'node_modules/libavoid-js/dist',name),path.join(avoid,name));
fs.copyFileSync(path.join(root,'node_modules/libavoid-js/LICENSE'),path.join(avoid,'LICENSE'));
const schemas=path.join(output,'schemas');fs.mkdirSync(schemas,{recursive:true});
for(const item of [pin.diagramSchema,pin.diagramOptionsSchema])fs.copyFileSync(path.resolve(root,'../'+item.file),path.join(schemas,path.basename(item.file)));
const licenses=path.join(output,'licenses');fs.mkdirSync(licenses,{recursive:true});
fs.copyFileSync(path.join(root,'src/main/typescript/vendor/ilic-diagram/LICENSE'),path.join(licenses,'ilic-diagram-MIT.txt'));
fs.copyFileSync(path.join(root,'node_modules/elkjs/LICENSE.md'),path.join(licenses,'elkjs-LICENSE.md'));
for(const [packageName,file] of [['ajv','LICENSE'],['sprotty-protocol','LICENSE']])fs.copyFileSync(path.join(root,'node_modules',packageName,file),path.join(licenses,packageName+'-LICENSE'));
fs.writeFileSync(path.join(output,'package.json'),JSON.stringify({name:'@interlis/compiler-browser',version:'0.2.0-SNAPSHOT',type:'module',exports:{'.':{types:'./types/compiler-client.d.ts',import:'./compiler-client.js'},'./diagram':{types:'./types/diagram-adapter.d.ts',import:'./compiler-diagram.js'}},dependencies:{'sprotty-protocol':'1.4.0'}},null,2)+'\n');
const consumer=path.join(output,'consumer.ts');
fs.writeFileSync(consumer,`import {CompilerClient,type WorkspaceConfigurationDto,type QueryResultDto} from './types/compiler-client';
import {diagramAdapter,type DiagramView} from './types/diagram-adapter';
const client=new CompilerClient();
const configuration:WorkspaceConfigurationDto={allowedLanguages:['2.3','2.4'],analysisBudget:{maxTokens:'9007199254740993'}};
async function inspect(view:DiagramView){const handle=await client.openSession({configuration});const state=client.state(handle);const result=await client.analyze(handle,{expectedWorkspaceRevision:state.workspaceRevision,roots:[{kind:'bySource',source:'memory:/a'}]});const query:QueryResultDto=await client.query(handle,{snapshotId:result.snapshotId,query:{kind:'symbolAt',sourceId:'memory:/a',revision:'1',startOffset:0,endOffset:0}});if(query.kind==='known'){const ids=diagramAdapter.elementsForSymbol(view,{snapshot:query.snapshotId,symbol:query.symbolId});void ids;}await client.releaseSnapshot(handle,result.snapshotId);await client.closeSession(handle);await client.dispose();}
void inspect;
`);
fs.writeFileSync(path.join(output,'consumer-tsconfig.json'),JSON.stringify({compilerOptions:{strict:true,noEmit:true,target:'ES2022',module:'ESNext',moduleResolution:'Bundler',lib:['ES2022','DOM'],types:[]},files:['consumer.ts']}));
run(['-p',path.join(output,'consumer-tsconfig.json')]);
fs.rmSync(consumer);fs.rmSync(path.join(output,'consumer-tsconfig.json'));
const files=directory=>fs.readdirSync(directory,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?files(path.join(directory,entry.name)):[path.join(directory,entry.name)]);
let commit='unavailable',dirty='unavailable';try{commit=cp.execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();dirty=cp.execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'}).trim();}catch{}
const manifest={status:'passed',scope:'TypeScript strict checking, declarations and bundles; not a browser acceptance',node:process.version,commit,dirty,
  versions:Object.fromEntries(['typescript','esbuild','ajv','elkjs','sprotty-protocol','libavoid-js'].map(name=>[name,JSON.parse(fs.readFileSync(path.join(root,'node_modules',name,'package.json'),'utf8')).version])),
  inputs:Object.fromEntries([...files(path.join(root,'src/main/typescript')),...files(path.join(root,'src/main/web')),...['build-browser-tools.cjs','package.json','package-lock.json','tsconfig.json'].map(p=>path.join(root,p)),path.resolve(root,'../interlis-projection/src/main/resources/guru/interlis/projection/diagram-document-1.0.schema.json'),path.resolve(root,'../'+pin.diagramOptionsSchema.file),path.resolve(root,'../docs/p3/diagram-adapter-pin.json')].map(p=>[path.relative(root,p),hash(fs.readFileSync(p))])),
  outputs:Object.fromEntries(files(output).map(p=>[path.relative(output,p),hash(fs.readFileSync(p))]))};
fs.writeFileSync(path.join(root,'build/browser-tools-result.json'),JSON.stringify(manifest,null,2)+'\n');
console.log('BROWSER_TOOLS_PASS '+JSON.stringify({node:manifest.node,versions:manifest.versions,outputs:Object.keys(manifest.outputs).length}));
