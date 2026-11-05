'use strict';
const {test} = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), crypto = require('node:crypto');
const {readVerificationInputs, resolveRole} = require('./layout.cjs');
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
test('explicit host layout binds root, hash, roles and output boundaries', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'interlis layout 😀 '));t.after(() => fs.rmSync(root, {recursive:true,force:true}));
  const directory = path.join(root, 'target/jenesis-jvm');fs.mkdirSync(directory, {recursive:true});
  const file = path.join(directory, 'layout.properties');
  const valid = `schemaVersion=1\nbuilder=jenesis\nroot=${root}\nreports=target/jenesis-jvm/host/run/reports\n`;
  const read = contents => {fs.writeFileSync(file, contents);return readVerificationInputs(root, file, hash(contents));};
  const input = read(valid);assert.equal(resolveRole(input,'reports','sdk/result.json'),path.join(directory,'host/run/reports/sdk/result.json'));
  assert.throws(() => resolveRole(input,'compiler'), /Missing explicit/);
  assert.throws(() => readVerificationInputs(root,file,'0'.repeat(64)),/Changed/);
  for (const changed of [valid.replace('builder=jenesis','builder=gradle'),valid.replace('schemaVersion=1','schemaVersion=99'),valid.replace('target/jenesis-jvm/host/run/reports','build/reports'),valid+'reports=target/jenesis-jvm/other\n',valid+'unknown=target/jenesis-jvm/other\n']) assert.throws(() => read(changed));
  read(valid);for(const relative of ['../outside','/outside','a/../../b','C:\\out']) assert.throws(() => resolveRole(input,'reports',relative));
  fs.symlinkSync(os.tmpdir(),path.join(directory,'host'));assert.throws(() => read(valid),/Symlink/);
});
