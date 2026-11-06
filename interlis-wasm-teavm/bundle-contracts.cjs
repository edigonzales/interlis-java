'use strict';

const path = require('node:path');
const esbuild = require('esbuild');

esbuild.buildSync({
  entryPoints: [path.join(__dirname, 'contracts/runner.cjs')],
  outfile: process.argv[2],
  bundle: true,
  platform: 'browser',
  format: 'iife',
  globalName: 'interlisPortableContracts'
});
