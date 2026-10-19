(() => {
  'use strict';

  function fail(message) {
    throw new TypeError('diagram adapter: ' + message);
  }

  function array(value, name) {
    if (!Array.isArray(value)) fail(name + ' must be an array');
    return value;
  }

  function adapt(raw) {
    if (!raw || typeof raw !== 'object') fail('document must be an object');
    if (raw.schemaVersion !== '1.0') {
      fail('unsupported schemaVersion: ' + String(raw.schemaVersion));
    }
    if (typeof raw.snapshotId !== 'string' || raw.snapshotId.length === 0) {
      fail('snapshotId is required');
    }
    const nodes = array(raw.nodes, 'nodes');
    const edges = array(raw.edges, 'edges');
    const containers = array(raw.containers, 'containers');
    const notices = array(raw.notices, 'notices');
    for (const node of nodes) {
      if (!node || typeof node.id !== 'string' || typeof node.label !== 'string'
          || typeof node.canonicalName !== 'string') {
        fail('node requires id, label and canonicalName');
      }
    }
    for (const edge of edges) {
      if (!edge || typeof edge.id !== 'string' || typeof edge.kind !== 'string'
          || !edge.source || !edge.target || !edge.sourceEnd || !edge.targetEnd) {
        fail('edge requires id, kind, source, target and ends');
      }
    }
    const byOrigin = new Map();
    for (const edge of edges) {
      const origin = edge.origin && edge.origin.symbol ? String(edge.origin.symbol) : edge.id;
      const group = byOrigin.get(origin) || [];
      group.push(edge);
      byOrigin.set(origin, group);
    }
    const revisions = raw.sourceRevisions && typeof raw.sourceRevisions === 'object'
      ? Object.freeze(Object.assign({}, raw.sourceRevisions)) : Object.freeze({});
    return Object.freeze({
      schemaVersion: '1.0',
      snapshotId: raw.snapshotId,
      sourceRevisions: revisions,
      completeness: String(raw.completeness || 'UNKNOWN'),
      nodes: Object.freeze(nodes.slice()),
      containers: Object.freeze(containers.slice()),
      edges: Object.freeze(edges.slice()),
      associations: byOrigin,
      notices: Object.freeze(notices.slice())
    });
  }

  function isStale(view, currentSnapshotId) {
    return !view || view.snapshotId !== currentSnapshotId;
  }

  window.interlisDiagramAdapter = Object.freeze({adapt, isStale});
})();
