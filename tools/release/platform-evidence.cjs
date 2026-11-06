'use strict';

const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const assert = require('node:assert/strict');
const {root, hash, commitInfo, fingerprint} = require('../verification/source-fingerprint.cjs');
const {report} = require('../verification/layout.cjs');
const {validateJvmReports} = require('./evidence.cjs');
const contract = require('./platform-contract.cjs');

function collect({directory, candidate, sourceFingerprint, api, download}) {
  const {repository, profile, remotePlatforms, platformReport, platformCommit, isCandidateRun} = contract;
  const output = path.join(directory, 'result.json');
  fs.mkdirSync(directory, {recursive:true});
  fs.rmSync(output, {force:true});
  assert.equal(candidate.dirty, false, 'Platform collection requires a clean committed candidate');

  const endpoint = 'repos/' + repository + '/actions/';
  const latest = (rows, predicate) => rows.filter(predicate).sort((a,b)=>Date.parse(b.created_at)-Date.parse(a.created_at))[0];
  const runs = api(endpoint + 'runs?head_sha=' + candidate.gitCommit + '&per_page=100').workflow_runs;
  const run = latest(runs, item => isCandidateRun(item, candidate.gitCommit));
  assert.ok(run, 'No successful compiler workflow for current candidate');
  const jobs = api(endpoint + 'runs/' + run.id + '/jobs?filter=latest&per_page=100').jobs;
  const artifacts = api(endpoint + 'runs/' + run.id + '/artifacts?per_page=100').artifacts;
  const selected = [];
  for (const platform of remotePlatforms) {
    const job = jobs.find(item => item.name === contract.jobName(platform));
    assert.ok(job?.status === 'completed' && job.conclusion === 'success', 'Missing/unsuccessful ' + platform + ' job');
    assert.ok(job.steps.some(step => step.name === contract.verificationStep && step.conclusion === 'success'),
        'Missing executed platform process step');
    const name = contract.artifactName(platform, candidate.gitCommit);
    const artifact = latest(artifacts, item => item.name === name && !item.expired);
    assert.ok(artifact?.workflow_run?.head_sha === candidate.gitCommit && /^sha256:[a-f0-9]{64}$/.test(artifact.digest),
        'Missing/foreign/unhashed platform artifact');

    const folder = path.join(directory, String(run.id), platform, String(artifact.id));
    fs.mkdirSync(folder, {recursive:true});
    const zip = path.join(folder, 'artifact.zip');
    download(endpoint + 'artifacts/' + artifact.id + '/zip', zip);
    assert.equal('sha256:' + hash(fs.readFileSync(zip)), artifact.digest, 'GitHub artifact digest mismatch');
    const entries = cp.execFileSync('/usr/bin/unzip', ['-Z1', zip], {encoding:'utf8',maxBuffer:8*1024*1024}).split('\n').filter(Boolean);
    assert.ok(entries.length > 0 && new Set(entries).size === entries.length &&
        entries.every(name => !path.isAbsolute(name) && !name.includes('\\') && !name.includes(':') && !name.split('/').includes('..')),
        'Unsafe/duplicate artifact entry');
    const extracted = path.join(folder, 'extracted');
    fs.rmSync(extracted, {recursive:true,force:true});
    fs.mkdirSync(extracted);
    cp.execFileSync('/usr/bin/unzip', ['-q',zip,'-d',extracted]);

    const report = path.join(extracted, platformReport);
    const proofBytes = fs.readFileSync(report);
    const proof = JSON.parse(proofBytes);
    const commit = fs.readFileSync(path.join(extracted,platformCommit),'utf8').replace(/^\uFEFF/,'').trim();
    assert.ok(commit === candidate.gitCommit && proof.gitCommit === commit && proof.dirty === false &&
        proof.status === 'passed' && proof.sourceFingerprint === sourceFingerprint, 'Foreign/dirty/unsuccessful platform proof');
    validateJvmReports(proof, file => fs.readFileSync(path.join(extracted,'reports',file)));
    selected.push({platform,jobId:job.id,artifactId:artifact.id,name,digest:artifact.digest,
      zip:path.relative(directory,zip),report:path.relative(directory,report),reportSha256:hash(proofBytes),proof});
  }

  const result = {schemaVersion:'1.0',profile,repository,gitCommit:candidate.gitCommit,
    run:{id:run.id,url:run.html_url,status:run.status,conclusion:run.conclusion,headSha:run.head_sha,attempt:run.run_attempt,path:run.path},
    jobs:jobs.map(({id,name,status,conclusion})=>({id,name,status,conclusion})),artifacts:selected};
  fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n');
  return result;
}

function main() {
  const gh = process.env.GH_EXECUTABLE || 'gh';
  function api(endpoint) {
    return JSON.parse(cp.execFileSync(gh,['api',endpoint],{cwd:root,encoding:'utf8',maxBuffer:16*1024*1024}));
  }
  function download(endpoint,file) {
    const fd = fs.openSync(file,'w');
    try {
      const result = cp.spawnSync(gh,['api',endpoint],{cwd:root,stdio:['ignore',fd,'pipe'],encoding:'utf8'});
      assert.equal(result.status,0,'GitHub artifact download failed: ' + String(result.stderr).slice(-1000));
    } finally { fs.closeSync(fd); }
  }
  const result = collect({directory:report(root,contract.collectedRole),candidate:commitInfo(),sourceFingerprint:fingerprint(),api,download});
  console.log('GITHUB_PLATFORM_EVIDENCE_PASS',result.run.id,result.gitCommit);
}

module.exports = {collect};
if(require.main === module) try { main(); } catch(error) { console.error(error); process.exitCode = 1; }
