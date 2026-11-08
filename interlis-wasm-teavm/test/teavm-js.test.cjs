'use strict';

const {registerCompilerTests} = require('./compiler-smoke.cjs');

registerCompilerTests(() => require('../build/generated/teavm/js/interlis-compiler.js'));
