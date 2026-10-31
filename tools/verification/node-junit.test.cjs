'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {parseJUnit}=require('./node-junit.cjs');
// The real Node24 junit reporter emits flat testcases and trailing comments
// for files using test() without describe()/nested test suites.
const report=`<?xml version="1.0" encoding="utf-8"?>
<testsuites>
 <testcase name="positive &amp; negative" time="0.1" classname="test" file="/fixture/contract.test.cjs"/>
 <testcase name="second" time="0.1" classname="test" file="/fixture/contract.test.cjs"/>
 <!-- tests 2 -->
 <!-- suites 0 -->
 <!-- pass 2 -->
 <!-- fail 0 -->
 <!-- cancelled 0 -->
 <!-- skipped 0 -->
 <!-- todo 0 -->
 <!-- duration_ms 200 -->
</testsuites>
`;
test('actual flat Node24 report shape counts executed cases',()=>assert.deepEqual(parseJUnit(report,2),{tests:2,failures:0,errors:0,skipped:0}));
for(const [name,mutate]of [
 ['truncated report',s=>s.replace('</testsuites>','')],
 ['removed executed case',s=>s.replace(/ <testcase[^\n]+\n/,'')],
 ['missing summary',s=>s.replace('<!-- tests 2 -->','')],
 ['duplicate summary',s=>s.replace('<!-- pass 2 -->','<!-- pass 2 --><!-- pass 2 -->')],
 ['skipped zero-exit test',s=>s.replace('<!-- skipped 0 -->','<!-- skipped 1 -->')],
 ['todo zero-exit test',s=>s.replace('<!-- todo 0 -->','<!-- todo 1 -->')],
 ['cancelled test',s=>s.replace('<!-- cancelled 0 -->','<!-- cancelled 1 -->')],
 ['failure body',s=>s.replace('name="second" time="0.1" classname="test" file="/fixture/contract.test.cjs"/>','name="second"><failure message="failure"/></testcase>')],
 ['nested suite',s=>s.replace('<testsuites>','<testsuites><testsuite tests="2">')]
])test('Node report rejects '+name,()=>assert.throws(()=>parseJUnit(mutate(report),2)));
test('Node report rejects insufficient cases',()=>assert.throws(()=>parseJUnit(report,3)));
