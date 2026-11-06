'use strict';

const assert = require('node:assert/strict');
const {test} = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {verify} = require('./repository-hygiene.cjs');

function fixture(t,files) {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'interlis hygiene '));
  t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  for(const [file,text] of Object.entries(files)) {
    const destination=path.join(root,file);
    fs.mkdirSync(path.dirname(destination),{recursive:true});
    fs.writeFileSync(destination,text);
  }
  return verify(root,Object.keys(files));
}

test('current paths, encoded links and anchors are accepted',t=>{
  const result=fixture(t,{
    'docs/usage/intro.md':'[Relative](../Contract%20A.md#aktuell)\n[Heading](../Contract%20A.md#ökologie)\n\n```sh\n[Example](not-a-link.md)\n```\n',
    'docs/Contract A.md':'<a id="aktuell"></a>\n# Ökologie\n\nT.101-G2 is a character set.',
    'compiler/src/test/java/example/LanguageProfileContractTest.java':'',
    'compiler/build.jenesis/test-inputs.properties':'input.0=docs/Contract A.md\n'
  });
  assert.equal(result.links,2);
  assert.deepEqual(result.errors,[]);
});

test('broken link, invalid encoding and missing explicit anchor fail',t=>{
  const result=fixture(t,{'docs/contract.md':'[Missing](absent.md)\n[Anchor](#absent)\n[Invalid](%q)\n'});
  assert.match(result.errors[0],/missing or foreign link absent/);
  assert.match(result.errors[1],/missing anchor/);
  assert.match(result.errors[2],/invalid encoded link/);
});

test('retired docs, phase test names and missing declared inputs fail',t=>{
  const result=fixture(t,{
    'docs/archive/notes.md':'',
    'compiler/src/test/java/example/G2AClosureTest.java':'',
    'compiler/build.jenesis/test-inputs.properties':'input.0=absent.ili\n'
  });
  assert.match(result.errors[0],/retired documentation/);
  assert.match(result.errors[1],/delivery phase/);
  assert.match(result.errors[2],/missing or foreign input/);
});

test('missing pinned reference fixture fails',t=>{
  const result=fixture(t,{
    'testdata/verification/reference-fixtures.properties':'testdata/models/missing.ili='+'1'.repeat(64)+'\n'
  });
  assert.deepEqual(result.errors,[
    'testdata/verification/reference-fixtures.properties: missing or foreign input testdata/models/missing.ili'
  ]);
});
