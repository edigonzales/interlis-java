'use strict';

/** Reads the pinned Node24 reporter's flat test profile, including its summaries.
 * Nested suites are deliberately rejected rather than counted twice. */
function parseJUnit(text, minimum) {
  const requireValue = (value, message) => { if (!value) throw Error(message); };
  requireValue(/^<\?xml[^>]+>\s*<testsuites>/.test(text) && /<\/testsuites>\s*$/.test(text), 'missing/truncated Node JUnit report');
  requireValue(!/<testsuite\b|<!DOCTYPE|<!ENTITY/.test(text), 'unexpected non-flat Node JUnit report');
  const counters = {};
  for (const key of ['tests', 'suites', 'pass', 'fail', 'cancelled', 'skipped', 'todo']) {
    const matches = [...text.matchAll(new RegExp('<!-- '+key+' (\\d+) -->', 'g'))];
    requireValue(matches.length === 1, 'missing/duplicate Node summary '+key);
    counters[key] = Number(matches[0][1]);
  }
  const opened = [...text.matchAll(/<testcase\b/g)].length;
  const completed = [...text.matchAll(/<testcase\b[^>]*(?:\/>|>[\s\S]*?<\/testcase>)/g)].length;
  requireValue(opened === completed && opened === counters.tests && opened >= minimum, 'incomplete executed Node cases');
  requireValue(counters.suites === 0 && counters.pass === opened && counters.fail === 0 && counters.cancelled === 0 && counters.skipped === 0 && counters.todo === 0, 'failed/cancelled/skipped Node cases');
  requireValue(!/<(?:failure|error|skipped)\b/.test(text), 'unsuccessful Node JUnit case');
  return {tests:opened, failures:0, errors:0, skipped:0};
}

module.exports = {parseJUnit};
