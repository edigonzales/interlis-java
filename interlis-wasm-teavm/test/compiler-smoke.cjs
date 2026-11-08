'use strict';

const assert = require('node:assert/strict');
const {before, test} = require('node:test');

function protocol(api) {
  assert.equal(typeof api.handle, 'function');
  let sequence = 0;
  return (operation, params = {}, sessionId) => {
    const requestId = 'smoke-' + ++sequence;
    const response = JSON.parse(api.handle(JSON.stringify({
      protocol: 'interlis.compiler', protocolVersion: '1.0', requestId, operation, params, sessionId
    })));
    assert.equal(response.requestId, requestId);
    return response;
  };
}

function ok(response) {
  assert.equal(response.status, 'ok', JSON.stringify(response));
  return response.result;
}

function withSession(call, sources, check) {
  const opened = ok(call('openSession', {sources}));
  const request = (operation, params) => call(operation, params, opened.sessionId);
  try {
    check(request, opened.workspaceRevision);
  } finally {
    ok(request('closeSession'));
  }
}

function compile(request, revision, modelName) {
  return ok(request('compile', {
    expectedWorkspaceRevision: revision,
    roots: [{kind: 'byName', modelName}]
  }));
}

function registerCompilerTests(load) {
  let api, call;
  before(async () => {
    api = await load();
    call = protocol(api);
  });

  test('compiles, exposes exact decimal precision and recovers from source errors', () => {
    const capabilities = ok(call('capabilities'));
    assert.equal(capabilities.protocolVersion, '1.0');
    assert.deepEqual(capabilities.languages, ['2.3', '2.4']);
    if (process.env.INTERLIS_PRODUCT_VERSION) assert.equal(capabilities.toolVersion, process.env.INTERLIS_PRODUCT_VERSION);
    const sourceId = 'memory:/precision.ili';
    const text = [
      'INTERLIS 2.4;', '!! Grüezi 🧭',
      'MODEL Exact AT "x" VERSION "1" =',
      'DOMAIN Big = 9007199254740993.0010..9007199254740993.0010;',
      'TOPIC Data = CLASS Item = exact : Big; END Item; END Data;', 'END Exact.'
    ].join('\r\n');
    withSession(call, [], (request) => {
      const broken = text.replace('exact : Big', 'exact : Missing');
      const update = ok(request('updateSource', {
        sourceId, expectation: {kind: 'absent'}, change: {kind: 'replace', text: broken}
      }));
      const rejected = compile(request, update.workspaceRevision, 'Exact');
      assert.equal(rejected.outcome, 'rejected');
      assert.deepEqual(rejected.rejection.reasons, ['SOURCE_ERRORS']);
      const diagnostic = rejected.diagnostics.find(d => d.code === 'ILI-SEM-0001');
      assert.deepEqual(diagnostic.range, {
        source: sourceId, revision: '1', startOffset: broken.indexOf('Missing'), endOffset: broken.indexOf('Missing') + 7
      });
      const repaired = ok(request('updateSource', {
        sourceId, expectation: {kind: 'at', revision: '1'}, change: {kind: 'replace', text}
      }));
      const compiled = compile(request, repaired.workspaceRevision, 'Exact');
      assert.equal(compiled.outcome, 'succeeded');
      assert.deepEqual(compiled.diagnostics, []);
      const diagram = ok(request('diagram', {schemaId: compiled.schemaId}));
      assert.match(JSON.stringify(diagram), /9007199254740993\.0010/);
      assert.equal(diagram.sourceRevisions[sourceId], '2');
    });
  });

  test('binds imports and updates the computed type while retaining the previous schema', () => {
    const baseId = 'memory:/base.ili';
    const surveyId = 'memory:/survey.ili';
    const base = 'INTERLIS 2.4; MODEL Base AT "x" VERSION "1" = DOMAIN Count = 0..999; END Base.';
    const survey = `INTERLIS 2.4; MODEL Survey AT "x" VERSION "1" =
      IMPORTS Base;
      TOPIC Data =
        CLASS Building = height : Base.Count; END Building;
        VIEW Heights PROJECTION OF b~Building; =
          doubled := b->height+b->height;
        END Heights;
      END Data;
    END Survey.`;
    withSession(call, [{sourceId: baseId, text: base}, {sourceId: surveyId, text: survey}], (request, revision) => {
      const before = compile(request, revision, 'Survey');
      assert.equal(before.outcome, 'succeeded');
      const offset = survey.indexOf('Count');
      const target = ok(request('query', {
        snapshotId: before.snapshotId,
        query: {kind: 'semanticProperties', sourceId: surveyId, revision: '1', startOffset: offset, endOffset: offset}
      }));
      assert.equal(target.qualifiedName, 'Base.Count');
      const doubled = schemaId => {
        const view = ok(request('diagram', {schemaId})).nodes.find(node => node.canonicalName === 'Survey.Data.Heights');
        return view.compartments.flatMap(section => section.members).find(member => member.label === 'doubled').typeLabel;
      };
      assert.equal(doubled(before.schemaId), '0..1998');
      const changed = ok(request('updateSource', {
        sourceId: baseId, expectation: {kind: 'at', revision: '1'},
        change: {kind: 'replace', text: base.replace('999', '1000')}
      }));
      const stale = request('compile', {
        expectedWorkspaceRevision: revision, roots: [{kind: 'byName', modelName: 'Survey'}]
      });
      assert.equal(stale.error?.code, 'VERSION_CONFLICT', JSON.stringify(stale));
      const after = compile(request, changed.workspaceRevision, 'Survey');
      assert.equal(after.outcome, 'succeeded');
      assert.notEqual(after.schemaId, before.schemaId);
      // The diagram renders the normalized exact BigDecimal in exponent form.
      assert.equal(doubled(after.schemaId), '0..2E+3');
      assert.equal(doubled(before.schemaId), '0..1998');
    });
  });

  test('reports missing-import causes with source offsets and no repository IO', () => {
    const sourceId = 'memory:/root.ili';
    const text = 'INTERLIS 2.4;\r\n!! 😀 ä\r\nMODEL Root AT "x" VERSION "1" = IMPORTS Missing; STRUCTURE P=p:Missing.Point; q:Other; END P; END Root.';
    withSession(call, [{sourceId, text}], (request, revision) => {
      const result = compile(request, revision, 'Root');
      assert.equal(result.outcome, 'rejected');
      assert.ok(result.rejection.reasons.includes('MISSING_INPUT'));
      const missing = result.diagnostics.find(d => d.code === 'ILI-RES-0001');
      const dependent = result.diagnostics.find(d => d.code === 'ILI-SEM-0001' && d.arguments.some(a => a.value === 'Missing.Point'));
      const independent = result.diagnostics.find(d => d.code === 'ILI-SEM-0001' && d.arguments.some(a => a.value === 'Other'));
      assert.equal(missing.range.startOffset, text.indexOf('Missing'));
      assert.equal(missing.range.revision, '1');
      assert.equal(dependent.causeId, missing.id);
      assert.equal(independent.causeId, undefined);
      assert.ok(result.diagnostics.every(d => !['ILI-RES-0010', 'ILI-RES-0011', 'ILI-RES-0012', 'ILI-RES-0013'].includes(d.code)));
    });
  });

  test('keeps same-named enumeration values bound to their own domains', () => {
    const sourceId = 'memory:/enum.ili';
    const text = 'INTERLIS 2.4; MODEL Enums AT "x" VERSION "1" = DOMAIN E1=(red,blue); E2=(red,green); TOPIC T=CLASS C=a:E1;b:E2; MANDATORY CONSTRAINT a==#red AND b==#red; END C; END T; END Enums.';
    withSession(call, [{sourceId, text}], (request, revision) => {
      const compiled = compile(request, revision, 'Enums');
      assert.equal(compiled.outcome, 'succeeded');
      const first = text.indexOf('#red') + 1;
      const second = text.indexOf('#red', first) + 1;
      const definition = offset => ok(request('query', {
        snapshotId: compiled.snapshotId,
        query: {kind: 'definition', sourceId, revision: '1', startOffset: offset, endOffset: offset}
      }));
      const left = definition(first), right = definition(second);
      assert.equal(left.kind, 'definition');
      assert.equal(right.kind, 'definition');
      assert.notEqual(left.symbolId, right.symbolId);
    });
  });

  test('applies temporal bounds in the generated runtime', () => {
    const sourceId = 'memory:/clock.ili';
    const text = 'INTERLIS 2.4; MODEL Clock AT "x" VERSION "1" = DOMAIN Time = FORMAT INTERLIS.XMLTime "00:00:00.000" .. "23:59:59.999"; END Clock.';
    withSession(call, [{sourceId, text}], (request, revision) => {
      assert.equal(compile(request, revision, 'Clock').outcome, 'succeeded');
    });
    withSession(call, [{sourceId, text: text.replace('23:59:59.999', '24:00:00.000')}], (request, revision) => {
      const invalid = compile(request, revision, 'Clock');
      assert.equal(invalid.outcome, 'rejected');
      assert.deepEqual(invalid.rejection.reasons, ['SOURCE_ERRORS']);
      assert.ok(invalid.diagnostics.some(d => d.code.startsWith('ILI-SEM-')));
    });
  });

  test('cancels on numeric resource limits and remains usable', () => {
    const sourceId = 'memory:/limit.ili';
    for (const version of ['2.3', '2.4']) {
      for (const value of ['1e100001', '1e-100001', '1'.repeat(4097)]) {
        const text = `INTERLIS ${version}; MODEL Limit AT "x" VERSION "1" = TOPIC T = CLASS C = CONSTRAINT >= ${value}% #true; END C; END T; END Limit.`;
        withSession(call, [{sourceId, text}], (request, revision) => {
          const limited = compile(request, revision, 'Limit');
          assert.equal(limited.outcome, 'cancelled', JSON.stringify(limited));
          assert.equal(limited.cancellation.reason, 'RESOURCE_LIMIT');
          assert.equal(limited.schemaId, undefined);
        });
      }
    }
    withSession(call, [{sourceId, text: 'INTERLIS 2.3; MODEL Limit AT "x" VERSION "1" = END Limit.'}], (request, revision) => {
      assert.equal(compile(request, revision, 'Limit').outcome, 'succeeded');
    });
  });

  test('keeps long trivia offsets and contextual reference checks portable', async () => {
    await require('../token-classification-contract.cjs')(api);
  });
}

module.exports = {registerCompilerTests, protocol, withSession, compile};
