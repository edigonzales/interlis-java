// Export the already executed shared contracts for host process comparisons.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
(async () => {
  const kind = process.argv[2];
  if (!['views', 'units'].includes(kind)) throw new Error('Expected views or units');
  const source = fs.readFileSync(path.join(__dirname, '../../interlis-wasm-teavm/stabilization-contract.cjs'), 'utf8');
  const begin = kind === 'views' ? '  let viewCases = 0;' : '  let numericUnitCases = 0;';
  const end = kind === 'views' ? '  let parameterCases = 0;' : '  let temporalCases = 0;';
  const first = source.indexOf(begin), last = source.indexOf(end, first + begin.length);
  if (first < 0 || last <= first) throw new Error('Missing shared comparison block');
  const cases = [];
  const verify = async (text, expected) => cases.push({sources: [{sourceId: 'memory:/case.ili', text}], expected});
  const verifySources = async (sources, expected) => cases.push({sources, expected});
  const script = '(async () => {' + source.slice(first, last) + '})()';
  await vm.runInNewContext(script, {verify, verifySources});
  if (!cases.length) throw new Error('Empty comparison cases');
  process.stdout.write(JSON.stringify(cases));
})().catch(error => {console.error(error); process.exitCode = 1;});
