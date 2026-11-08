const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto'),{pathToFileURL}=require('node:url');
const esbuild=require('esbuild');
const root=__dirname,output=path.join(root,'build/browser-tools'),types=path.join(output,'types');
const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
const schemaSources = [
  '../interlis-projection/src/main/resources/guru/interlis/projection/diagram-document-1.0.schema.json',
  '../interlis-wasm-bridge/src/main/resources/guru/interlis/bridge/diagram-options-1.0.schema.json'
];
const diagramTypes = '../interlis-projection/src/main/typescript/diagram-document.ts';
function run(args){cp.execFileSync(process.execPath,[path.join(root,'node_modules/typescript/bin/tsc'),...args],{cwd:root,stdio:'inherit'});}
fs.rmSync(output,{recursive:true,force:true});fs.mkdirSync(output,{recursive:true});
run(['-p','tsconfig.json']);
run(['-p','tsconfig.json','--noEmit','false','--declaration','--emitDeclarationOnly','--outDir',types,'--rootDir',path.resolve(root,'..')]);
const options={bundle:true,target:'es2022',legalComments:'linked',tsconfigRaw:{compilerOptions:{target:'ES2022',esModuleInterop:true}},logLevel:'warning'};
esbuild.buildSync({...options,entryPoints:[path.join(root,'src/main/typescript/compiler-client.ts')],
  platform:'browser',format:'esm',outfile:path.join(output,'compiler-client.js')});
for(const name of ['compiler-client','worker-host']){
  esbuild.buildSync({...options,entryPoints:[path.join(root,'src/main/typescript',name+'.ts')],platform:'node',format:'cjs',
    define:{'import.meta.url':JSON.stringify(pathToFileURL(path.join(root,'build',name+'.cjs')).href)},outfile:path.join(root,'build',name+'.cjs')});
}
const schemas=path.join(output,'schemas');fs.mkdirSync(schemas,{recursive:true});
for(const file of schemaSources)fs.copyFileSync(path.resolve(root,file),path.join(schemas,path.basename(file)));
fs.writeFileSync(path.join(output,'package.json'),JSON.stringify({
  name:'@interlis/compiler-browser',version:'0.2.0-SNAPSHOT',type:'module',
  exports:{'.':{types:'./types/interlis-wasm-teavm/src/main/typescript/compiler-client.d.ts',import:'./compiler-client.js'}}
},null,2)+'\n');
const consumer=path.join(output,'consumer.ts');
fs.writeFileSync(consumer,`import {CompilerClient,type WorkspaceConfigurationDto,type DiagramDocumentDto,type QueryResultDto} from './types/interlis-wasm-teavm/src/main/typescript/compiler-client';
const client=new CompilerClient();
const configuration:WorkspaceConfigurationDto={allowedLanguages:['2.3','2.4'],analysisBudget:{maxTokens:'9007199254740993'}};
async function inspect(){const handle=await client.openSession({configuration});const state=client.state(handle);const result=await client.analyze(handle,{expectedWorkspaceRevision:state.workspaceRevision,roots:[{kind:'bySource',source:'memory:/a'}]});const query:QueryResultDto=await client.query(handle,{snapshotId:result.snapshotId,query:{kind:'symbolAt',sourceId:'memory:/a',revision:'1',startOffset:0,endOffset:0}});if(query.kind==='known'){const diagram:DiagramDocumentDto=await client.diagram(handle,{snapshotId:query.snapshotId,selection:[{snapshot:query.snapshotId,symbol:query.symbolId}]});void diagram;}await client.releaseSnapshot(handle,result.snapshotId);await client.closeSession(handle);await client.dispose();}
void inspect;
`);
fs.writeFileSync(path.join(output,'consumer-tsconfig.json'),JSON.stringify({compilerOptions:{strict:true,noEmit:true,target:'ES2022',module:'ESNext',moduleResolution:'Bundler',lib:['ES2022','DOM'],types:[]},files:['consumer.ts']}));
run(['-p',path.join(output,'consumer-tsconfig.json')]);
fs.rmSync(consumer);fs.rmSync(path.join(output,'consumer-tsconfig.json'));
const files=directory=>fs.readdirSync(directory,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?files(path.join(directory,entry.name)):[path.join(directory,entry.name)]);
let commit='unavailable',dirty='unavailable';try{commit=cp.execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();dirty=cp.execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'}).trim();}catch{}
const manifest={status:'passed',scope:'TypeScript strict checking, declarations and bundles; not a browser acceptance',node:process.version,commit,dirty,
  versions:Object.fromEntries(['typescript','esbuild'].map(name=>[name,JSON.parse(fs.readFileSync(path.join(root,'node_modules',name,'package.json'),'utf8')).version])),
  inputs:Object.fromEntries([...files(path.join(root,'src/main/typescript')),...files(path.join(root,'src/main/web')),
    ...['build-browser-tools.cjs','package.json','package-lock.json','tsconfig.json',diagramTypes,...schemaSources].map(file=>path.resolve(root,file))]
    .map(file=>[path.relative(root,file),hash(fs.readFileSync(file))])),
  outputs:Object.fromEntries(files(output).map(p=>[path.relative(output,p),hash(fs.readFileSync(p))]))};
fs.writeFileSync(path.join(root,'build/browser-tools-result.json'),JSON.stringify(manifest,null,2)+'\n');
console.log('BROWSER_TOOLS_PASS '+JSON.stringify({node:manifest.node,versions:manifest.versions,outputs:Object.keys(manifest.outputs).length}));
