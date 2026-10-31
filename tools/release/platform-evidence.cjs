'use strict';
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');const {root,hash,commitInfo}=require('../performance/profile.cjs');
const repository='edigonzales/interlis-java',directory=path.join(root,'build/reports/p3/platform-evidence'),output=path.join(directory,'result.json'),gh=process.env.GH_EXECUTABLE||'gh';
const assert=(value,message)=>{if(!value)throw Error(message);};
function api(endpoint){return JSON.parse(cp.execFileSync(gh,['api',endpoint],{cwd:root,encoding:'utf8',maxBuffer:16*1024*1024}));}
function download(endpoint,file){const fd=fs.openSync(file,'w');try{const r=cp.spawnSync(gh,['api',endpoint],{cwd:root,stdio:['ignore',fd,'pipe'],encoding:'utf8'});assert(r.status===0,'GitHub artifact download failed: '+String(r.stderr).slice(-1000));}finally{fs.closeSync(fd);}}
try{
 fs.mkdirSync(directory,{recursive:true});fs.rmSync(output,{force:true});const candidate=commitInfo();assert(!candidate.dirty,'Platform collection requires a clean committed candidate');
 const runs=api('repos/'+repository+'/actions/runs?head_sha='+candidate.gitCommit+'&per_page=100').workflow_runs;
 const run=runs.filter(r=>r.head_sha===candidate.gitCommit&&r.path==='.github/workflows/p3-platforms.yml'&&r.status==='completed'&&r.conclusion==='success').sort((a,b)=>Date.parse(b.created_at)-Date.parse(a.created_at))[0];
 assert(run,'No successful completed P3 workflow for current candidate; await/fix the actual run');
 const jobs=api('repos/'+repository+'/actions/runs/'+run.id+'/jobs?filter=latest&per_page=100').jobs;
 const artifacts=api('repos/'+repository+'/actions/runs/'+run.id+'/artifacts?per_page=100').artifacts;
 const selected=[];for(const platform of ['linux-x64','windows-x64']){
  const job=jobs.find(j=>j.name==='JVM and distribution ('+platform+')');assert(job&&job.status==='completed'&&job.conclusion==='success','Missing/unsuccessful '+platform+' job');
  assert(job.steps.some(s=>s.name==='Verify JVM, CLI and installed SDK processes'&&s.conclusion==='success'),'Missing executed platform process step');
  const artifact=artifacts.filter(a=>a.name==='p3-'+platform+'-'+candidate.gitCommit&&!a.expired).sort((a,b)=>Date.parse(b.created_at)-Date.parse(a.created_at))[0];
  assert(artifact&&artifact.workflow_run.head_sha===candidate.gitCommit&&/^sha256:[a-f0-9]{64}$/.test(artifact.digest),'Missing/foreign/unhashed platform artifact');
  const folder=path.join(directory,String(run.id),platform,String(artifact.id));fs.mkdirSync(folder,{recursive:true});const zip=path.join(folder,'artifact.zip');download('repos/'+repository+'/actions/artifacts/'+artifact.id+'/zip',zip);
  const digest=hash(fs.readFileSync(zip));assert('sha256:'+digest===artifact.digest,'GitHub artifact digest mismatch');
  const entries=cp.execFileSync('/usr/bin/unzip',['-Z1',zip],{encoding:'utf8',maxBuffer:8*1024*1024}).split('\n').filter(Boolean);
  assert(entries.length>0&&new Set(entries).size===entries.length&&entries.every(name=>!path.isAbsolute(name)&&!name.includes('\\')&&!name.includes(':')&&!name.split('/').includes('..')),'Unsafe/duplicate artifact entry');
  const extracted=path.join(folder,'extracted');fs.rmSync(extracted,{recursive:true,force:true});fs.mkdirSync(extracted);cp.execFileSync('/usr/bin/unzip',['-q',zip,'-d',extracted]);
  const report=path.join(extracted,'build/reports/p3/platform/result.json');const proof=JSON.parse(fs.readFileSync(report,'utf8'));const commit=fs.readFileSync(path.join(extracted,'build/reports/p3/platform/commit.txt'),'utf8').replace(/^\uFEFF/,'').trim();
  assert(commit===candidate.gitCommit&&proof.gitCommit===candidate.gitCommit&&!proof.dirty&&proof.status==='passed','Foreign/dirty/unsuccessful platform proof');
  selected.push({platform,jobId:job.id,artifactId:artifact.id,digest:artifact.digest,zip:path.relative(root,zip),report:path.relative(root,report),reportSha256:hash(fs.readFileSync(report)),proof});
 }
 const result={schemaVersion:'1.0',profile:'P3',repository,gitCommit:candidate.gitCommit,run:{id:run.id,url:run.html_url,status:run.status,conclusion:run.conclusion,headSha:run.head_sha,attempt:run.run_attempt,path:run.path},jobs:jobs.map(j=>({id:j.id,name:j.name,status:j.status,conclusion:j.conclusion})),artifacts:selected};
 fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n');console.log('GITHUB_PLATFORM_EVIDENCE_PASS',run.id,candidate.gitCommit);
}catch(error){console.error(error);process.exitCode=1;}
