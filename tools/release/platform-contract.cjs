'use strict';

const profile = 'compiler';
const repository = 'edigonzales/interlis-java';
const workflowPath = '.github/workflows/compiler-platforms.yml';
const collectedRole = 'platform/github';
const platformReport = 'reports/platform/result.json';
const platformCommit = 'reports/platform/commit.txt';
const remotePlatforms = ['linux-x64', 'windows-x64'];
const verificationStep = 'Verify JVM, CLI and installed SDK processes';
const artifactName = (platform, commit) => 'compiler-' + platform + '-' + commit;
const jobName = platform => 'JVM and distribution (' + platform + ')';

function isCandidateRun(run, candidate) {
  return run.head_sha === candidate && run.path === workflowPath &&
      run.status === 'completed' && run.conclusion === 'success';
}

module.exports = {profile, repository, workflowPath, collectedRole, platformReport, platformCommit,
  remotePlatforms, verificationStep, artifactName, jobName, isCandidateRun};
