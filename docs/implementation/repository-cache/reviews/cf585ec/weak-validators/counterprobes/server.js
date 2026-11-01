'use strict';
// Minimal local repository server for the weak-validator counterprobe.
// Usage: node server.js <readyFile> <logFile> <mode> <scenarioDir>
// Modes:
//   weak-window : first Dep.ili body is a.ili, then b.ili. Constant weak ETag.
//   lm-window   : first Dep.ili body is a.ili, then b.ili. Constant Last-Modified
//                 (whole-second mtime), so a change in the same second is legitimately
//                 invisible to If-Modified-Since.
// It never fakes a strong ETag for different bytes.

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const [readyFile, logFile, mode, scenarioDir] = process.argv.slice(2);
if (!readyFile || !logFile || !mode || !scenarioDir) {
  console.error('missing arguments');
  process.exit(2);
}

const read = (name) => fs.readFileSync(path.join(scenarioDir, name));
const a = read('a.ili');
const b = read('b.ili');
const index = fs.readFileSync(path.join(scenarioDir, '..', 'index.xml'));
const INDEX_ETAG = '"idx-v1"';
const WEAK_ETAG = 'W/"dep-logical-1"';

let switched = false;
let lastModified = null;

function sha256(bytes) {
  return crypto.createHash('sha256').update(bytes).digest('hex');
}

function weakMatch(header, etag) {
  if (!header) return false;
  const bare = etag.startsWith('W/') ? etag.slice(2) : etag;
  return header.split(',').map((v) => v.trim()).some((v) => {
    const candidate = v.startsWith('W/') ? v.slice(2) : v;
    return candidate === bare;
  });
}

function append(entry) {
  fs.appendFileSync(logFile, JSON.stringify(entry) + '\n');
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  const p = url.pathname;
  const inm = req.headers['if-none-match'] || null;
  const ims = req.headers['if-modified-since'] || null;
  let status = 404;
  let body = null;
  let variant = null;
  let etag = null;
  let lm = null;

  if (p === '/ilimodels.xml') {
    variant = 'index';
    etag = INDEX_ETAG;
    if (inm && weakMatch(inm, INDEX_ETAG)) {
      status = 304;
    } else {
      status = 200;
      body = index;
    }
  } else if (p === '/Dep.ili') {
    variant = switched ? 'B' : 'A';
    const bytes = switched ? b : a;
    if (mode === 'weak-window') {
      etag = WEAK_ETAG;
      if (inm && weakMatch(inm, WEAK_ETAG)) {
        status = 304;
      } else {
        status = 200;
        body = bytes;
      }
    } else if (mode === 'lm-window') {
      if (lastModified === null) {
        // Whole-second modification time of the first representation.
        lastModified = new Date(Math.floor(Date.now() / 1000) * 1000).toUTCString();
      }
      lm = lastModified;
      if (ims && Date.parse(ims) >= Date.parse(lm)) {
        status = 304;
      } else {
        status = 200;
        body = bytes;
      }
    } else {
      status = 500;
    }
    if (status === 200 && variant === 'A' && !switched) {
      // The repository content changes immediately after the first body,
      // i.e. within the same whole second. This is a correct server for a
      // weak/revalidated resource; a later unconditional GET returns B.
      switched = true;
    }
  }

  const headers = {};
  if (etag) headers['ETag'] = etag;
  if (lm) headers['Last-Modified'] = lm;
  if (body) headers['Content-Length'] = body.length;
  res.writeHead(status, headers);
  if (body) res.end(body); else res.end();

  append({
    path: p,
    status,
    ifNoneMatch: inm,
    ifModifiedSince: ims,
    bodySent: body ? body.length : 0,
    variant,
    sha256: body ? sha256(body) : null,
    etag,
    lastModified: lm,
    switchedAfter: switched,
  });
});

server.listen(0, '127.0.0.1', () => {
  const port = server.address().port;
  fs.writeFileSync(readyFile, String(port));
  append({ event: 'listening', port, mode, scenarioDir });
});

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT', () => server.close(() => process.exit(0)));
