const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {record,hash}=require('./record.cjs');
async function main(){
 const [java,manifest,out]=process.argv.slice(2),root=path.resolve(__dirname,'../..');fs.mkdirSync(out,{recursive:true});
 const data=JSON.parse(fs.readFileSync(manifest)),cp=data.orderedRuntime.map(r=>path.join(path.dirname(manifest),r.path)).join(path.delimiter);
 const cases=[];
 async function run(name,argv,expected=0){const r=await record(out,name,argv,{cwd:root,timeout:120000});cases.push({name,receipt:name+'.json'});assert.equal(r.timedOut,false);assert.equal(r.signal,null);assert.equal(r.exitCode,expected,fs.readFileSync(r.stderr.path,'utf8'));return fs.readFileSync(r.stdout.path,'utf8');}
 const version=JSON.parse(await run('jvm-version',[java,'-cp',cp,data.mainClass,'version','--json']));assert.equal(version.version,'0.2.0-SNAPSHOT');
 const valid=await run('jvm-valid',[java,'-cp',cp,data.mainClass,'check',path.join(root,'testdata/ape/Valid.ili'),'--json','--no-cache']);assert.deepEqual(JSON.parse(valid),{status:'ok',diagnostics:[]});
 const invalid=await run('jvm-invalid',[java,'-cp',cp,data.mainClass,'check',path.join(root,'testdata/ape/Invalid.ili'),'--json','--no-cache'],1);assert.ok(invalid.includes('ILI-SEM-'));
 const before=fs.existsSync(path.join(root,'target/compiler-ape'))?fs.readdirSync(path.join(root,'target/compiler-ape')).sort():[];
 const inspect=JSON.parse(await run('inspect',[java,'build/InterlisJvmBuild.java','ape','inspect']));assert.equal(inspect.mainClass,data.mainClass);
 assert.deepEqual(fs.readdirSync(path.join(root,'target/compiler-ape')).sort(),before);
 await run('compile-input-checks',[path.join(path.dirname(java),'javac'),'-d',path.join(out,'classes'),'-sourcepath',root,path.join(root,'tools/ape/InputChecks.java'),path.join(root,'tools/ape/EntryChecks.java')]);
 await run('existing-entry',[java,'-cp',path.join(out,'classes'),'build.EntryChecks']);
 const checks=await run('input-checks',[java,'-cp',path.join(out,'classes'),'InputChecks',root,manifest,path.join(out,'mutations')]);assert.ok(checks.includes('INPUT_CHECKS_PASS'));
 fs.writeFileSync(path.join(out,'gate.json'),JSON.stringify({status:'PASSED',inputManifest:manifest,inputSha256:hash(manifest),cases,fixtures:Object.fromEntries(['Valid','Invalid'].map(n=>[n,hash(path.join(root,'testdata/ape',n+'.ili'))])),host:process.platform+'/'+process.arch},null,2));console.log('F0_GATE_PASS');
}
main().catch(e=>{console.error(e);process.exitCode=1;});
