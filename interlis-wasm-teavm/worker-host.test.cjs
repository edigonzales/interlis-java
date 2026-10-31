const {test} = require('node:test');
const assert = require('node:assert/strict');
const {CompilerWorkerHost} = require('./build/worker-host.cjs');
const flush = () => new Promise(resolve => setImmediate(resolve));
const request = id => ({protocol:'interlis.compiler',protocolVersion:'1.0',requestId:id,operation:'capabilities',params:{}});
const outcome = promise => promise.then(value=>({value}),error=>({error}));
class Port {
  onmessage=null;onerror=null;sent=[];terminated=false;epoch=0;
  constructor(autoReady=true){this.autoReady=autoReady;}
  postMessage(value){this.sent.push(value);if(value.kind==='bootstrap'){this.epoch=value.epoch;if(this.autoReady)queueMicrotask(()=>this.ready());}}
  ready(){this.onmessage?.({data:{kind:'ready',epoch:this.epoch}});}
  reply(id,result={accepted:true}){this.onmessage?.({data:{epoch:this.epoch,requestId:id,response:JSON.stringify({...request(id),status:'ok',result})}});}
  terminate(){this.terminated=true;}
}
function host(options={},autoReady=true){const ports=[];const value=new CompilerWorkerHost({...options,workerFactory:()=>{const port=new Port(autoReady);ports.push(port);return port;}});return {value,ports};}

test('a queued cancellation never dispatches and an already aborted signal starts nothing',async()=>{
  const {value,ports}=host({},false);
  const first=value.dispatch(request('one'));const second=outcome(value.dispatch(request('two')));
  assert.equal(await value.cancel('two'),'notStarted');assert.equal((await second).error.code,'CANCELLED');
  const controller=new AbortController();controller.abort();
  await assert.rejects(value.dispatch(request('three'),{signal:controller.signal}),{code:'CANCELLED'});
  assert.equal(ports[0].sent.filter(x=>x.kind==='request').length,0);
  ports[0].ready();assert.deepEqual(ports[0].sent.filter(x=>x.kind==='request').map(x=>x.requestId),['one']);
  ports[0].reply('one');assert.equal((await first).status,'ok');
  assert.equal(await value.cancel('one'),'alreadyCompleted');assert.equal(await value.cancel('absent'),'unknownRequest');
  await value.dispose();
});

test('a running cancellation replaces the worker and rejects every old epoch handle',async()=>{
  let restored=0;const {value,ports}=host({restore:async()=>{restored++;}});await flush();
  const first=outcome(value.dispatch(request('running'))),second=outcome(value.dispatch(request('queued')));
  assert.equal(await value.cancel('running'),'workerRestarted');assert.equal(restored,1);
  assert.equal((await first).error.code,'CANCELLED');assert.equal((await second).error.code,'CANCELLED');
  assert.equal(ports.length,2);assert.equal(ports[0].terminated,true);assert.equal(value.epoch,1);
  const current=value.dispatch(request('current'));ports[0].reply('current',{stale:true});assert.equal(value.pendingCount,1);
  ports[1].reply('current',{fresh:true});assert.deepEqual((await current).result,{fresh:true});
  await value.dispose();
});

test('runtime failure differs from cancellation and a fresh worker can answer again',async()=>{
  const errors=[];const {value,ports}=host({onFailure:error=>errors.push(error.code)});await flush();
  const failed=outcome(value.dispatch(request('trap')));
  ports[0].onmessage({data:{kind:'fatal',epoch:0}});assert.equal((await failed).error.code,'RUNTIME_FAILURE');
  await value.settled();assert.deepEqual(errors,['RUNTIME_FAILURE']);assert.equal(ports.length,2);
  const recovered=value.dispatch(request('recovered'));ports[1].reply('recovered');assert.equal((await recovered).status,'ok');
  await value.dispose();
});

test('queue pressure and shutdown stay bounded and settle all pending work',async()=>{
  const {value,ports}=host({maxQueuedRequests:2},false);
  const a=outcome(value.dispatch(request('a'))),b=outcome(value.dispatch(request('b')));
  await assert.rejects(value.dispatch(request('c')),{code:'LIMIT_EXCEEDED'});
  assert.equal(value.pendingCount,2);await value.dispose();
  assert.equal((await a).error.details.reason,'HOST_SHUTDOWN');assert.equal((await b).error.details.reason,'HOST_SHUTDOWN');
  assert.equal(value.pendingCount,0);assert.equal(ports[0].terminated,true);
  await assert.rejects(value.dispatch(request('after')),{code:'DISPOSED'});await value.dispose();
});

test('a malformed or mismatched worker response cannot become a successful result',async()=>{
  const {value,ports}=host();await flush();const pending=outcome(value.dispatch(request('bad')));
  ports[0].onmessage({data:{epoch:0,requestId:'bad',response:'{"status":"ok","result":{}}'}});
  assert.equal((await pending).error.code,'RUNTIME_FAILURE');await value.settled();assert.equal(ports.length,2);
  await value.dispose();
});

test('bootstrap failure rejects future requests until explicit restart instead of leaving a dead queue',async()=>{
  const ports=[];const host=new CompilerWorkerHost({workerFactory:()=>{const port=new Port(false);ports.push(port);return port;}});
  try{
    const initial=host.dispatch(request('boot-fail'));
    ports[0].onmessage({data:{kind:'fatal',epoch:host.epoch}});
    await assert.rejects(initial,{code:'RUNTIME_FAILURE'});
    const future=host.dispatch(request('after-failed-boot'));
    const guard=new Promise((_,reject)=>setTimeout(()=>reject(new Error('dead bootstrap queue')),100));
    await assert.rejects(Promise.race([future,guard]),{code:'RUNTIME_FAILURE'});
    const recovery=host.restart();ports[1].ready();await recovery;
    const active=host.dispatch(request('after-explicit-restart'));ports[1].reply('after-explicit-restart');await active;
  }finally{await host.dispose();}
});
