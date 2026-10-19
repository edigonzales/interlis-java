const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, 'diagram-adapter.js'), 'utf8');
const sandbox = {window: {}};
vm.runInNewContext(source, sandbox);
const adapter = sandbox.window.interlisDiagramAdapter;

const anchor = {source: 'memory:/a.ili', range: {startOffset: 0, endOffset: 1}};
const document = {
  schemaVersion: '1.0',
  snapshotId: 'snap-1',
  sourceRevisions: {'memory:/a.ili': '1'},
  completeness: 'COMPLETE',
  containers: [{id: 'c1'}],
  nodes: [{id: 'n1', label: 'A', canonicalName: 'M.A', kind: 'CLASS', status: 'OK', compartments: [], anchor}],
  edges: [
    {id: 'e1', kind: 'ASSOCIATION_ROLE', source: {nodeId: 'n1'}, target: {nodeId: 'n2'},
      sourceEnd: {cardinality: '1'}, targetEnd: {cardinality: '0..*'}, label: 'a', origin: {symbol: 'assoc-1'}},
    {id: 'e2', kind: 'ASSOCIATION_ROLE', source: {nodeId: 'n2'}, target: {nodeId: 'n3'},
      sourceEnd: {cardinality: '1'}, targetEnd: {cardinality: '0..*'}, label: 'b', origin: {symbol: 'assoc-1'}}
  ],
  notices: []
};

const view = adapter.adapt(document);
if (view.associations.get('assoc-1').length !== 2) throw new Error('n-ary edges lost');
if (view.nodes[0].anchor.source !== 'memory:/a.ili') throw new Error('source anchor lost');
if (adapter.isStale(view, 'snap-2') !== true) throw new Error('stale check failed');
if (adapter.isStale(view, 'snap-1') !== false) throw new Error('current check failed');
let rejected = false;
try { adapter.adapt({...document, schemaVersion: '2.0'}); } catch (error) { rejected = true; }
if (!rejected) throw new Error('unsupported schema accepted');
console.log('DIAGRAM_ADAPTER_PASS n-ary=2');
