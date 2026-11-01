'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const webPrefix='interlis-wasm-teavm/build/web/';

/** The actual P3 delivery inventory, independently of any verification report. */
function distributionFiles(root) {
  const version=fs.readFileSync(path.join(root,'gradle.properties'),'utf8').match(/^projectVersion=(.+)$/m)?.[1].trim();
  if(!version||!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(version))throw Error('Missing/invalid distribution version');
  const cli='interlis-compiler-cli/build/install/interlis-compiler',teavm='interlis-wasm-teavm/build';
  for(const forbidden of ['bin/interlis-cli','bin/interlis-cli.bat','bin/ili2c','bin/ili2c.bat','compat/ili2c.jar']) {
    try { fs.lstatSync(path.join(root,cli,forbidden)); } catch(error) { if(error.code==='ENOENT')continue; throw error; }
    throw Error('Forbidden legacy distribution entry: '+forbidden);
  }
  const mandatory=[...['interlis-compiler','interlis-compiler.bat'].map(name=>cli+'/bin/'+name),
    ...['compiler-cli','compiler','model','projection','repository','workspace'].map(name=>cli+'/lib/interlis-'+name+'-'+version+'.jar'),
    'build/releases/interlis-java-sdk-'+version+'.zip',teavm+'/generated/teavm/js/interlis-compiler.js',
    teavm+'/generated/teavm/wasm-gc/interlis-compiler.wasm',teavm+'/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js'];
  for(const file of mandatory)if(!fs.statSync(path.join(root,file)).isFile())throw Error('Missing distribution file: '+file);
  if(process.platform!=='win32' && (fs.statSync(path.join(root,cli,'bin/interlis-compiler')).mode & 0o111)!==0o111)
    throw Error('Compiler launcher is not executable');
  const walk=directory=>fs.readdirSync(path.join(root,directory),{withFileTypes:true}).flatMap(entry=>{
    const file=directory+'/'+entry.name;
    if(entry.isDirectory())return walk(file);
    if(!entry.isFile())throw Error('Unsupported distribution entry: '+file);
    return [file];
  });
  const files=[...mandatory,...[cli,teavm+'/browser-tools',teavm+'/web'].flatMap(directory=>{
    const contents=walk(directory);if(!contents.length)throw Error('Empty distribution directory: '+directory);return contents;
  })];
  return [...new Set(files)].sort();
}
/** Actual served resources exclude only the manifest's self-reference. */
function browserResources(root, files=distributionFiles(root)) {
  return Object.fromEntries(files.filter(file=>file.startsWith(webPrefix)&&file!==webPrefix+'build-manifest.json').map(file=>{
    const bytes=fs.readFileSync(path.join(root,file));
    return [file.slice(webPrefix.length),{sha256:crypto.createHash('sha256').update(bytes).digest('hex'),bytes:bytes.length}];
  }));
}
module.exports={distributionFiles,browserResources};
