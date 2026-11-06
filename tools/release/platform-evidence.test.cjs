'use strict';

const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {execFileSync} = require('node:child_process');
const {hash} = require('../verification/source-fingerprint.cjs');
const {collect} = require('./platform-evidence.cjs');
const {validatePreflight} = require('./evidence.cjs');
const {fixture} = require('./test-fixtures.cjs');
const {workflowPath, artifactName, jobName, verificationStep} = require('./platform-contract.cjs');

function archivedPlatform(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(),'interlis collector '));
  t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const f = fixture();
  const endpoint = 'repos/edigonzales/interlis-java/actions/';
  const candidate = {gitCommit:f.candidate,dirty:false};
  const current = {id:42,head_sha:f.candidate,path:workflowPath,status:'completed',
    conclusion:'success',created_at:'2026-01-01T00:00:00Z',run_attempt:1,html_url:'https://example.invalid/run/42'};
  const former = {...current,id:43,path:'.github/workflows/p3-platforms.yml',created_at:'2026-02-01T00:00:00Z'};
  const responses = {
    [endpoint+'runs?head_sha='+f.candidate+'&per_page=100']:{workflow_runs:[former,current]},
    [endpoint+'runs/42/jobs?filter=latest&per_page=100']:{jobs:f.github.jobs.map(job=>({...job,
      steps:[{name:verificationStep,conclusion:'success'}]}))},
    [endpoint+'runs/42/artifacts?per_page=100']:{artifacts:[]}
  };
  const archive = new Map();
  for(const [index,platform] of ['linux-x64','windows-x64'].entries()){
    const folder = path.join(root,platform);
    const output = path.join(root,platform+'.zip');
    const proof = structuredClone(f.platforms[index]);
    for(const file of Object.keys(proof.xmlHashes)){
      const xml = Buffer.from('fixture XML for '+platform+' '+file);
      const actual = path.join(folder,'reports',file);
      fs.mkdirSync(path.dirname(actual),{recursive:true});
      fs.writeFileSync(actual,xml);
      proof.xmlHashes[file] = hash(xml);
    }
    fs.mkdirSync(path.join(folder,'reports/platform'),{recursive:true});
    fs.writeFileSync(path.join(folder,'reports/platform/result.json'),JSON.stringify(proof));
    fs.writeFileSync(path.join(folder,'reports/platform/commit.txt'),f.candidate+'\n');
    execFileSync('/usr/bin/zip',['-qr',output,'reports'],{cwd:folder});
    const id = 200+index;
    archive.set(endpoint+'artifacts/'+id+'/zip',output);
    responses[endpoint+'runs/42/artifacts?per_page=100'].artifacts.push({
      id,name:artifactName(platform,f.candidate),expired:false,workflow_run:{head_sha:f.candidate},
      digest:'sha256:'+hash(fs.readFileSync(output))
    });
  }
  const calls = [];
  const directory = path.join(root,'collected');
  const collectReports = () => collect({directory,candidate,sourceFingerprint:f.sourceFingerprint,
    api(route){calls.push(route);assert.ok(Object.hasOwn(responses,route),route);return responses[route];},
    download(route,to){calls.push(route);assert.ok(archive.has(route),route);fs.copyFileSync(archive.get(route),to);}
  });
  return {root,directory,f,endpoint,candidate,responses,archive,calls,collectReports};
}

test('recorded API shapes select the current workflow and deliver exact archived report roles',t=>{
  const f=archivedPlatform(t);
  const collected=f.collectReports();
  assert.deepEqual(f.calls,[...Object.keys(f.responses),...f.archive.keys()]);
  assert.equal(collected.run.path,workflowPath);
  assert.deepEqual(collected.jobs.map(j=>j.name),[jobName('linux-x64'),jobName('windows-x64')]);
  for(const a of collected.artifacts){
    assert.equal(a.report,`42/${a.platform}/${a.artifactId}/extracted/reports/platform/result.json`);
    assert.equal(a.reportSha256,hash(fs.readFileSync(path.join(f.directory,a.report))));
  }
  assert.equal(validatePreflight(f.f.candidate,f.f.sourceFingerprint,collected,f.f.guard).status,'passed');
  const workflow=fs.readFileSync(path.resolve(__dirname,'../..',workflowPath),'utf8');
  assert.match(workflow,/name: compiler-\$\{\{ matrix\.platform \}\}-\$\{\{ github\.sha \}\}/);
  assert.match(workflow,/name: JVM and distribution \(\$\{\{ matrix\.platform \}\}\)/);
});

for(const [label,mutate,expected] of [
  ['old workflow only',f=>{Object.values(f.responses)[0].workflow_runs.pop();},/No successful compiler workflow/],
  ['legacy artifact',f=>{Object.values(f.responses)[2].artifacts[0].name='p3-linux-x64-'+f.candidate.gitCommit;},/Missing\/foreign/],
  ['missing Windows artifact',f=>{Object.values(f.responses)[2].artifacts.pop();},/Missing\/foreign/],
  ['foreign artifact commit',f=>{Object.values(f.responses)[2].artifacts[0].workflow_run.head_sha='3'.repeat(40);},/Missing\/foreign/],
  ['changed downloaded archive',f=>{Object.values(f.responses)[2].artifacts[0].digest='sha256:'+'0'.repeat(64);},/digest mismatch/]
])test('collector rejects '+label,t=>{
  const f=archivedPlatform(t);mutate(f);
  assert.throws(f.collectReports,expected);
  assert.equal(fs.existsSync(path.join(f.directory,'result.json')),false);
});
