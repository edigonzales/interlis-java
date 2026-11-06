'use strict';

const views = require('./cases/reference-views.json');
const units = require('./cases/reference-units.json');
const originals = require('./cases/original-views.json');

function referenceCases(kind) {
  if (kind === 'views') return views;
  if (kind === 'units') {
    // The comparison profile also exercises these pinned upstream view models.
    return [...units, ...originals.map(fixture => ({
      id: fixture.testId,
      sources: [{sourceId: `memory:/b1-b3/${fixture.testId}.ili`, text: fixture.text}],
      roots: fixture.roots,
      expected: 'succeeded'
    }))];
  }
  throw new Error('Expected views or units');
}

module.exports = {referenceCases};
