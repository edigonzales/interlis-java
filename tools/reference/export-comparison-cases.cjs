'use strict';

const {referenceCases} = require('../../interlis-wasm-teavm/contracts/reference-cases.cjs');

const cases = referenceCases(process.argv[2]);
if (cases.length === 0) throw new Error('Empty comparison cases');
process.stdout.write(JSON.stringify(cases.map(({id, sources, roots, expected, referenceSourceId}) => ({
  id, roots, expected,
  sources: referenceSourceId ? sources.map(source => ({...source, sourceId: referenceSourceId})) : sources
}))));
