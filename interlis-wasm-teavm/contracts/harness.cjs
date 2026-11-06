'use strict';

function createHarness(api) {
  let counter = 0;
  const call = async (operation, sessionId, params = {}) => {
    const response = JSON.parse(await api.handle(JSON.stringify({
      protocol: 'interlis.compiler', protocolVersion: '1.0',
      requestId: `stabilization-${++counter}`, operation, sessionId, params
    })));
    if (response.status !== 'ok') throw new Error(JSON.stringify(response));
    return response.result;
  };
  const verifySources = async (sources, expected, roots = ['F'], inspect) => {
    const {sessionId} = await call('openSession');
    try {
      let updated;
      for (const {sourceId, text} of sources) {
        updated = await call('updateSource', sessionId, {
          sourceId, expectation: {kind: 'absent'}, change: {kind: 'replace', text}
        });
      }
      const compiled = await call('compile', sessionId, {
        expectedWorkspaceRevision: updated.workspaceRevision,
        roots: roots.map(modelName => ({kind: 'byName', modelName}))
      });
      if (compiled.outcome !== expected) {
        throw new Error(`expected ${expected} for ${JSON.stringify(sources)}: ${JSON.stringify(compiled)}`);
      }
      if(inspect) await inspect(sessionId,compiled);
    } finally {
      await call('closeSession', sessionId);
    }
  };
  const verify = (text, expected) => verifySources([{sourceId: 'memory:/stabilization.ili', text}], expected);
  return {call, verify, verifySources};
}

module.exports = {createHarness};
