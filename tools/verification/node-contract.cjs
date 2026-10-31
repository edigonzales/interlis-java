'use strict';
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');const evidence=require('./runtime-evidence.cjs');
const profiles={client:{files:['worker-host.test.cjs','compiler-client.test.cjs'],minimum:18},diagram:{files:['diagram-adapter.test.cjs'],minimum:4}};
const profile=profiles[process.argv[2]];if(!profile)throw Error('unknown Node contract profile');
const stamp=evidence.begin(['interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js','interlis-wasm-teavm/build/compiler-client.cjs','interlis-wasm-teavm/build/diagram-adapter.cjs']);
const output=path.resolve('build/node-'+process.argv[2]+'-result.json'),xml=path.resolve('build/node-'+process.argv[2]+'-junit.xml');fs.rmSync(output,{force:true});fs.rmSync(xml,{force:true});
const child=cp.spawn(process.execPath,['--test','--test-reporter=spec','--test-reporter=junit','--test-reporter-destination=stdout','--test-reporter-destination='+xml,...profile.files],{stdio:'inherit'});
child.on('error',error=>{console.error(error);process.exitCode=1;});child.on('exit',(code,signal)=>{
 try{
  if(code!==0||signal)throw Error('Node contract failed: '+code+'/'+signal);
  const text=fs.readFileSync(xml,'utf8'),suites=[...text.matchAll(/<testsuite\s[^>]*>/g)].map(m=>m[0]);if(!suites.length)throw Error('missing executed Node suites');
  const counts={tests:0,failures:0,errors:0,skipped:0};for(const suite of suites)for(const key of Object.keys(counts)){const match=suite.match(new RegExp('\\b'+key+'="(\\d+)"'));if(!match)throw Error('missing Node JUnit '+key);counts[key]+=+match[1];}
  if(counts.tests<profile.minimum||counts.failures||counts.errors||counts.skipped)throw Error('incomplete/skipped Node contracts');
  fs.writeFileSync(output,JSON.stringify({...evidence.finish(stamp),status:'passed',profile:process.argv[2],...counts,junitSha256:require('../performance/profile.cjs').hash(text)},null,2)+'\n');
 }catch(error){console.error(error);process.exitCode=1;}
});
