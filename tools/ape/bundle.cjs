// Inventory/transport checks are not platform or runtime success claims.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {hash,record}=require('./record.cjs');const root=path.resolve(__dirname,'../..');
function regularFiles(directory){const files=[];function walk(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,e.name);assert.ok(!e.isSymbolicLink(),'Symlink in delivered bundle');if(e.isDirectory())walk(file);else{assert.ok(e.isFile(),'Nonregular bundle entry');files.push(path.relative(directory,file).split(path.sep).join('/'));}}}walk(directory);return files.sort();}
function writeNotices(directory,installed){
 const notices=path.join(directory,'notices');fs.mkdirSync(notices);const origin=[];
 function copy(source,relative,scope){const destination=path.join(notices,relative);fs.mkdirSync(path.dirname(destination),{recursive:true});fs.copyFileSync(source,destination,fs.constants.COPYFILE_EXCL);origin.push({path:'notices/'+relative,source,scope,sha256:hash(destination),bytes:fs.statSync(destination).size});}
 copy(path.join(root,'LICENSE'),'interlis-LICENSE','product');
 copy(path.join(root,'docs/evidence/stabilization/registries/UNICODE-LICENSE.txt'),'UNICODE-LICENSE.txt','compiler Unicode tables');
 const antlr=path.join(__dirname,'notices/ANTLR-LICENSE.txt');assert.equal(hash(antlr),'3db1fb3ee79a4b4f9918fc4d0f6133bf18a3cf787f126cd22f8aa9b862281c0c');copy(antlr,'ANTLR-LICENSE.txt','ANTLR4.13.2; https://github.com/antlr/antlr4/blob/4.13.2/LICENSE.txt');
 const graal=path.join(installed,'cosmogvm/graalvm-b0d7b51f55-java25-25.2.4-dev');
 for(const file of ['LICENSE.txt','LICENSE_NATIVEIMAGE.txt','THIRD_PARTY_LICENSE.txt','3rd_party_license_llvm-toolchain.txt'])copy(path.join(graal,file),'graal-builder/'+file,'complete builder notice source; not a claim every component is embedded');
 function legal(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,e.name);if(e.isDirectory())legal(file);else copy(file,'graal-builder/legal/'+path.relative(path.join(graal,'legal'),file).split(path.sep).join('/'),'complete vendor JDK legal inventory; includes modules beyond runtime closure');}}
 legal(path.join(graal,'legal'));
 const cosmo=path.join(installed,'cosmocc/cosmocc');for(const file of ['LICENSE.gpl2','LICENSE.gpl3','LICENSE.lgpl2','LICENSE.lgpl3','README.md'])copy(path.join(cosmo,file),'cosmocc-builder/'+file,'vendor compiler/runtime notice sources; no blanket legal approval');
 return origin;
}
async function prepareLoaders(directory,tools){
 const loaders=[];
 const linuxSource=path.join(tools.installed,'cosmocc/cosmocc/bin/ape-x86_64.elf'),linux=path.join(directory,'ape-loader-linux-x64');fs.copyFileSync(linuxSource,linux,fs.constants.COPYFILE_EXCL);fs.chmodSync(linux,0o755);
 loaders.push({path:'ape-loader-linux-x64',platform:'linux-x64',sha256:hash(linux),bytes:fs.statSync(linux).size,mode:0o755,origin:{kind:'unmodified pinned vendor loader',sourceSha256:hash(linuxSource)}});
 if(process.platform==='darwin'&&process.arch==='arm64'){
  const source=path.join(tools.installed,'cosmocc/cosmocc/bin/ape-m1.c'),sourceSha256=hash(source),binary=path.join(directory,'ape-loader-macos-arm64');
  const toolPath=require('node:child_process').execFileSync('/usr/bin/xcrun',['--find','clang'],{encoding:'utf8'}).trim(),compilerSha256=hash(toolPath);
  const sdk=fs.realpathSync(require('node:child_process').execFileSync('/usr/bin/xcrun',['--show-sdk-path'],{encoding:'utf8'}).trim()),sdkSettings=path.join(sdk,'SDKSettings.json'),sdkSettingsSha256=hash(sdkSettings);
  const version=await record(directory,'mac-loader-compiler-version',[toolPath,'--version']);assert.equal(version.exitCode,0);
  const compile=await record(directory,'mac-loader-compile',[toolPath,'-isysroot',sdk,'-w','-O','-o',binary,source],{timeout:120000});assert.equal(compile.exitCode,0);assert.equal(compile.timedOut,false);assert.equal(hash(source),sourceSha256,'Vendor loader source changed');
  const type=await record(directory,'mac-loader-type',['/usr/bin/file','-b',binary]);assert.equal(type.exitCode,0);assert.match(fs.readFileSync(type.stdout.path,'utf8'),/Mach-O 64-bit executable arm64/);fs.chmodSync(binary,0o755);
  assert.equal(hash(toolPath),compilerSha256,'Host compiler changed');assert.equal(hash(sdkSettings),sdkSettingsSha256,'SDK metadata changed');
  loaders.push({path:'ape-loader-macos-arm64',platform:'macos-arm64',sha256:hash(binary),bytes:fs.statSync(binary).size,mode:0o755,origin:{kind:'compiled unmodified pinned vendor ape-m1.c; no toolchain patch',sourceSha256,compilerPath:toolPath,compilerSha256,sdk,sdkSettingsSha256,compilerVersionSha256:version.stdout.sha256,processReceipt:path.join(directory,'mac-loader-compile.json')}});
 }
 return loaders;
}
function loadCandidate(manifestFile){
 assert.ok(path.isAbsolute(manifestFile));const bundle=JSON.parse(fs.readFileSync(manifestFile)),directory=path.dirname(manifestFile);
 assert.equal(bundle.schemaVersion,'1.0');assert.equal(bundle.kind,'interlis-compiler-ape-bundle');assert.match(bundle.sha256,/^[a-f0-9]{64}$/);assert.equal(path.basename(directory),bundle.sha256,'Candidate directory/hash mismatch');
 assert.equal(bundle.mode,0o755);const binary=path.join(directory,'interlis-compiler.com');assert.equal(fs.statSync(binary).mode&0o777,bundle.mode,'Executable mode changed');assert.equal(hash(binary),bundle.sha256);assert.equal(fs.statSync(binary).size,bundle.bytes);
 assert.ok(Array.isArray(bundle.notices)&&bundle.notices.length>0);assert.ok(Array.isArray(bundle.runtimeAdditions),'Runtime inventory required');
 const expected=['bundle-manifest.json','interlis-compiler.com'];
 for(const loader of bundle.runtimeAdditions){assert.ok(['ape-loader-linux-x64','ape-loader-macos-arm64'].includes(loader.path),'Unknown runtime component');assert.equal(loader.platform,loader.path==='ape-loader-linux-x64'?'linux-x64':'macos-arm64','Wrong loader platform');assert.ok(!expected.includes(loader.path),'Duplicate runtime component');expected.push(loader.path);const file=path.join(directory,loader.path);assert.equal(hash(file),loader.sha256);assert.equal(fs.statSync(file).size,loader.bytes);assert.equal(loader.mode,0o755);assert.equal(fs.statSync(file).mode&0o777,loader.mode);}
 for(const notice of bundle.notices){assert.match(notice.path,/^notices\/[A-Za-z0-9_./-]+$/);assert.ok(!notice.path.split('/').includes('..'));assert.ok(!expected.includes(notice.path),'Duplicate bundle entry');expected.push(notice.path);const file=path.join(directory,notice.path);assert.equal(hash(file),notice.sha256);assert.equal(fs.statSync(file).size,notice.bytes);}
 assert.deepEqual(regularFiles(directory),expected.sort(),'Unexpected/missing delivered files');return {bundle,binary,directory};
}
async function packageAndVerify(manifestFile,destination){
 const candidate=loadCandidate(manifestFile);assert.ok(path.isAbsolute(destination)&&!fs.existsSync(destination));fs.mkdirSync(destination,{recursive:true});const archive=path.join(destination,'interlis-compiler-ape.tar.gz');
 const create=await record(destination,'create',['/usr/bin/tar','--format','pax','-czf',archive,'-C',path.dirname(candidate.directory),path.basename(candidate.directory)],{env:{...process.env,COPYFILE_DISABLE:'1'},timeout:120000});assert.equal(create.exitCode,0);assert.equal(create.timedOut,false);
 const list=await record(destination,'list',['/usr/bin/tar','-tf',archive]);assert.equal(list.exitCode,0);for(const name of fs.readFileSync(list.stdout.path,'utf8').trim().split('\n'))require('./toolchain.cjs').validateEntry(name);
 const extracted=path.join(destination,'extracted');fs.mkdirSync(extracted);const extract=await record(destination,'extract',['/usr/bin/tar','-xf',archive,'-C',extracted],{timeout:120000});assert.equal(extract.exitCode,0);assert.equal(extract.timedOut,false);
 const unpacked=loadCandidate(path.join(extracted,path.basename(candidate.directory),'bundle-manifest.json'));assert.equal(unpacked.bundle.sha256,candidate.bundle.sha256);assert.equal(hash(manifestFile),hash(path.join(unpacked.directory,'bundle-manifest.json')));
 const result={status:'PASSED',scope:'exact archive transport only; no runtime/platform gate',archive,archiveSha256:hash(archive),bytes:fs.statSync(archive).size,binarySha256:candidate.bundle.sha256,manifestSha256:hash(manifestFile)};fs.writeFileSync(path.join(destination,'package.json'),JSON.stringify(result,null,2));return result;
}
module.exports={regularFiles,writeNotices,prepareLoaders,loadCandidate,packageAndVerify};
if(require.main===module)packageAndVerify(process.argv[2],process.argv[3]).then(r=>console.log(r)).catch(e=>{console.error(e);process.exitCode=1;});
