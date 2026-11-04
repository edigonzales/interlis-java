'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process');
const root=path.resolve(__dirname,'../..'),hash=value=>crypto.createHash('sha256').update(value).digest('hex');
function fingerprint(){
 const files=[...new Set(cp.execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z'],{cwd:root,encoding:'utf8',maxBuffer:16*1024*1024}).split('\0').filter(file=>file.endsWith('/.jenesis.skip')||file.startsWith('testdata/verification/')||file.startsWith('frontend/diagram-ui/')||file.startsWith('interlis-')||file.startsWith('tools/')||file.startsWith('gradle/')||file.startsWith('.github/workflows/')||file.startsWith('docs/spec/')||file.startsWith('docs/evidence/p3/full-corpus/')||file.startsWith('build.jenesis/')||file.startsWith('build/jenesis/')||file.startsWith('build/interlis/')||['pom.xml','jenesis.properties','jenesis-contracts.properties','build/InterlisJvmBuild.java','build/JENESIS-UPSTREAM.json','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat'].includes(file)))].sort();
 if(files.length<500)throw Error('incomplete verification source binding');
 return hash(files.map(file=>file+'\0'+(fs.existsSync(path.join(root,file))?hash(fs.readFileSync(path.join(root,file))):'<deleted>')+'\n').join(''));
}
function commitInfo(){return {gitCommit:cp.execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),dirty:cp.execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'}).trim()!==''};}
module.exports={root,hash,fingerprint,commitInfo};
