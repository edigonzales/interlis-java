const runtimeEvidence=require('../tools/verification/runtime-evidence.cjs');
const evidence=runtimeEvidence.begin(['interlis-wasm-teavm/build/web/build-manifest.json','interlis-wasm-teavm/build/web/interlis-compiler.wasm','interlis-wasm-teavm/build/diagram-adapter.cjs']);
'use strict';
const {test,before,after}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium,firefox,webkit}=require('playwright');
let server,url;const results=[],web=path.resolve(__dirname,'build/web'),output=path.resolve(__dirname,'build/ui-projection-results.json');
const text='INTERLIS 2.4;MODEL English(en) AT "x" VERSION "1"=DOMAIN Identifier=TEXT*10;TOPIC Data=CLASS Road=name:Identifier;END Road;END Data;END English.'+
  'MODEL French(fr) AT "x" VERSION "1" TRANSLATION OF English["1"]=DOMAIN Identifiant=TEXT*10;TOPIC Donnees=CLASS Route=nom:Identifiant;END Route;END Donnees;END French.';
before(async()=>{fs.rmSync(output,{force:true});server=http.createServer((req,res)=>{const name=new URL(req.url,'http://localhost').pathname,file=path.resolve(web,name==='/'?'index.html':name.slice(1));if(!file.startsWith(web+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}res.writeHead(200,{'content-type':file.endsWith('.wasm')?'application/wasm':file.endsWith('.html')?'text/html; charset=utf-8':file.endsWith('.css')?'text/css; charset=utf-8':'text/javascript; charset=utf-8'});fs.createReadStream(file).pipe(res);});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));url='http://127.0.0.1:'+server.address().port+'/';});
after(async()=>{server?.close();fs.writeFileSync(output,JSON.stringify(results,null,2)+'\n');});
for(const [name,type]of [['chromium',chromium],['firefox',firefox],['webkit',webkit]])test(name+' UI language/focus/depth and delayed actual layout remain snapshot-safe',{timeout:60000},async()=>{
  let browser;const report={...evidence,browser:name,status:'failed'};
  try{
    browser=await type.launch({headless:true});report.version=browser.version();const page=await browser.newPage();const errors=[];page.on('pageerror',error=>errors.push(String(error)));page.on('console',message=>{if(message.type()==='error'&&!message.location().url.endsWith('/favicon.ico'))errors.push(message.text());});
    // Test-only wrapper waits after the unchanged real pinned renderer finishes.
    // The original bundle is served byte-for-byte via a query URL.
    await page.route(/\/diagram-ui\.js$/,route=>route.fulfill({contentType:'text/javascript',body:`import {DiagramCanvas as RealCanvas,ElkLayoutService} from './diagram-ui.js?unmodified-renderer';
export {diagramAdapter} from './diagram-ui.js?unmodified-renderer';
export class DiagramCanvas extends RealCanvas {
 constructor(element,callbacks,url){const real=new ElkLayoutService(url);const delayed={layout:async(...args)=>{const result=await real.layout(...args);if(window.__holdProjection){window.__holdProjection=false;window.__layoutHeld=true;await new Promise(resolve=>window.__releaseProjection=resolve);}return result;},cancel:()=>real.cancel(),close:()=>real.close()};super(element,callbacks,url,delayed);}
}`}));
    await page.goto(url);await page.waitForFunction(()=>Boolean(window.interlisBrowserTest));await page.locator('#source').fill(text);
    const initial=await page.evaluate(()=>interlisBrowserTest.diagram());assert.equal(initial.stale,undefined);const doc=initial.result,model=doc.containers.find(c=>c.canonicalName==='French').symbol,road=doc.nodes.find(n=>n.canonicalName==='English.Data.Road');
    await page.selectOption('#diagram-language',model.symbol);
    await page.waitForFunction(()=>{try{let d=JSON.parse(document.getElementById('output').textContent).document;return document.getElementById('diagram-view').dataset.stale==='false'&&d.nodes.find(n=>n.canonicalName==='English.Data.Road')?.label==='Route';}catch{return false;}});
    const bounds=await page.locator('#diagram-view').boundingBox(),viewport=await page.locator('#diagram-view svg.sprotty-graph').boundingBox();assert.ok(Math.abs(bounds.width-viewport.width)<2&&Math.abs(bounds.height-viewport.height)<2,'Shared viewer CSS did not fill its actual browser canvas');
    const translated=await page.evaluate(()=>JSON.parse(document.getElementById('output').textContent).document);assert.deepEqual(translated.nodes.map(n=>n.id),doc.nodes.map(n=>n.id));assert.deepEqual(translated.nodes.find(n=>n.id===road.id).anchor,road.anchor);
    await page.selectOption('#diagram-focus',road.symbol.symbol);await page.selectOption('#diagram-scope','NEIGHBOURHOOD');
    await page.waitForFunction(()=>{try{let d=JSON.parse(document.getElementById('output').textContent).document;return document.getElementById('diagram-view').dataset.stale==='false'&&d.optionsFingerprint.includes('depth=1')&&d.nodes.filter(n=>n.kind!=='EXTERNAL_REFERENCE').length===2;}catch{return false;}});
    await page.locator('#diagram-depth').fill('0');await page.locator('#diagram-depth').dispatchEvent('change');
    await page.waitForFunction(()=>{try{let d=JSON.parse(document.getElementById('output').textContent).document;return document.getElementById('diagram-view').dataset.stale==='false'&&d.optionsFingerprint.includes('depth=0')&&d.nodes.filter(n=>n.kind!=='EXTERNAL_REFERENCE').length===1;}catch{return false;}});
    await page.selectOption('#diagram-focus','');await page.selectOption('#diagram-scope','SELECTED');await page.selectOption('#diagram-language','');
    await page.waitForFunction(()=>{try{let d=JSON.parse(document.getElementById('output').textContent).document;return document.getElementById('diagram-view').dataset.stale==='false'&&!d.optionsFingerprint.includes('translation=')&&d.optionsFingerprint.includes('scope=SELECTED')&&d.nodes.length===4;}catch{return false;}});
    // Drain queued option changes before holding this particular new layout.
    await page.evaluate(()=>interlisBrowserTest.diagram());
    const before=await page.locator('#diagram-view').innerHTML();await page.evaluate(()=>{window.__layoutHeld=false;window.__holdProjection=true;window.__delayedProjection=interlisBrowserTest.diagram();});await page.waitForFunction(()=>window.__layoutHeld===true);
    await page.locator('#source').fill(text+'\n!! newer source revision');const late=await page.evaluate(()=>{window.__releaseProjection();return window.__delayedProjection;});assert.equal(late.stale,true);assert.equal(await page.locator('#diagram-view').innerHTML(),before);assert.equal(await page.locator('#diagram-view').getAttribute('data-stale'),'true');
    const current=await page.evaluate(()=>interlisBrowserTest.diagram());assert.equal(current.stale,undefined);assert.equal(await page.locator('#diagram-view').getAttribute('data-stale'),'false');const currentRoad=current.result.nodes.find(n=>n.canonicalName==='English.Data.Road');
    const navigation=await page.evaluate(id=>interlisBrowserTest.navigate(id),currentRoad.id);assert.equal(navigation.kind,'resolved');assert.equal(navigation.range.revision,current.result.sourceRevisions[navigation.range.source]);
    await page.locator('#source').fill('INTERLIS 2.4;MODEL SelfCase AT "x" VERSION "1"=TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;ASSOCIATION Self=parent--{0..1}A;children--{*}A;END Self;END T;END SelfCase.');
    const selfDocument=await page.evaluate(()=>interlisBrowserTest.diagram());assert.equal(selfDocument.stale,undefined);
    await page.selectOption('#diagram-associations','COMPACT_BINARY');
    await page.waitForFunction(()=>{try{const d=JSON.parse(document.getElementById('output').textContent).document;return document.getElementById('diagram-view').dataset.stale==='false'&&d.edges.some(e=>e.kind==='ASSOCIATION'&&e.source.elementId===e.target.elementId);}catch{return false;}});
    await page.locator('#fit-diagram').click();
    await page.waitForFunction(()=>Boolean(document.querySelector('#diagram-view svg .sprotty-edge')));
    const compact=await page.evaluate(()=>JSON.parse(document.getElementById('output').textContent).document),self=compact.edges.find(e=>e.kind==='ASSOCIATION');
    assert.equal(self.source.elementId,self.target.elementId);assert.equal(compact.nodes.some(n=>n.kind==='ASSOCIATION'),false);
    const rendered=await page.locator('#diagram-view').innerText();assert.match(rendered,/parent/);assert.match(rendered,/children/);
    const image=path.resolve(__dirname,'build',name+'-compact-self.png');await page.locator('#diagram-view').screenshot({path:image});report.compactSelfImage=image;
    assert.deepEqual(errors,[],'Browser renderer or asset errors');Object.assign(report,runtimeEvidence.finish(evidence));report.status='passed';report.observations=['actual UI translation changes labels while preserving ids/anchors','focus does not expand unrelated siblings','neighbourhood depth0/1 applied','late actual SVG/ELK layout discarded after source edit','current source navigation succeeds','actual Wasm producer and shared renderer show compact self association with both roles'];
  }catch(error){report.error=String(error);throw error;}finally{results.push(report);await browser?.close();}
});
