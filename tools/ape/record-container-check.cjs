// Explicit Docker process-control regression, never a compiler/runtime gate.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {record}=require('./record.cjs');
async function check(out){
 assert.ok(path.isAbsolute(out)&&!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});
 const name='ape-recorder-check-'+require('node:crypto').randomBytes(6).toString('hex'),image=require('../../build/interlis/ape/toolchain.lock.json').builderImage;
 const child=path.join(out,'nested.cjs');fs.writeFileSync(child,"require("+JSON.stringify(path.join(__dirname,'record.cjs'))+").record("+JSON.stringify(out)+",'inner',['docker','run','--rm','--name',"+JSON.stringify(name)+",'--network','none','--entrypoint','/bin/sleep',"+JSON.stringify(image)+",'120'],{timeout:150000}).then(()=>{});\n");
 const result=await record(out,'outer',[process.execPath,child],{timeout:2000});assert.equal(result.timedOut,true);assert.equal(result.signal,'SIGKILL');assert.deepEqual(result.containerCleanup.names,[name]);assert.equal(result.containerCleanup.exitCode,0);
 const inspection=await record(out,'absent',['docker','inspect',name]);assert.notEqual(inspection.exitCode,0);fs.writeFileSync(path.join(out,'checks.json'),JSON.stringify({status:'PASSED',scope:'Nested recorder outer timeout removes only its registered named Docker container; original timeout remains failure',name},null,2));console.log('NESTED_CONTAINER_CLEANUP_PASS');
}
if(require.main===module)check(process.argv[2]).catch(e=>{console.error(e);process.exitCode=1;});
