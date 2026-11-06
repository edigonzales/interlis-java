'use strict';

const {createHarness} = require('./harness.cjs');
const {suites, orderedCases} = require('./registry.cjs');

function select(values, available, label) {
  if (values === undefined) return new Set(available);
  if (!Array.isArray(values) || values.length === 0 || new Set(values).size !== values.length ||
      values.some(value => !available.has(value))) {
    throw new Error('Unknown, duplicate or empty ' + label + ' selection');
  }
  return new Set(values);
}

function plan(selection = {}) {
  if (selection === null || typeof selection !== 'object' || Array.isArray(selection) ||
      Object.keys(selection).some(key => key !== 'suiteIds' && key !== 'caseIds')) {
    throw new Error('Unknown portable selection option');
  }
  const {suiteIds, caseIds} = selection;
  const registered = new Set();
  for (const [suite, entries] of Object.entries(suites)) {
    const cases = Object.keys(entries);
    if (cases.length === 0) throw new Error('Empty required portable suite: ' + suite);
    for (const id of cases) {
      if (registered.has(id)) throw new Error('Duplicate portable case: ' + id);
      registered.add(id);
    }
  }
  const ids = new Set(orderedCases.map(([, id]) => id));
  if (ids.size !== orderedCases.length || ids.size !== registered.size ||
      orderedCases.some(([suite, id]) => typeof suites[suite]?.[id] !== 'function')) {
    throw new Error('Incomplete or duplicate portable case inventory');
  }
  const chosenSuites = select(suiteIds, new Set(Object.keys(suites)), 'suite');
  const chosenCases = select(caseIds, ids, 'case');
  const selected = orderedCases.filter(([suite, id]) => chosenSuites.has(suite) && chosenCases.has(id));
  if (selected.length === 0 || (caseIds && selected.length !== chosenCases.size)) {
    throw new Error('Portable selection does not contain every requested case');
  }
  return selected;
}

async function runContracts(api, selection) {
  const harness = createHarness(api);
  let total = 0;
  for (const [suite, id] of plan(selection)) {
    const count = await suites[suite][id](harness);
    if (!Number.isSafeInteger(count) || count <= 0) throw new Error('Empty portable case: ' + id);
    total += count;
  }
  return total;
}

module.exports = {runContracts, plan};
