import type {DiagramDocumentDto} from './types';
import type {DiagramSettings} from './layout';
import {paintedBounds,svgId,type Rectangle} from './geometry';

export type ExportScope='FULL'|'VISIBLE';
export interface SvgArtifact {svg:string;width:number;height:number;viewBox:Rectangle;scope:ExportScope;snapshotId:string;}
const svgNamespace='http://www.w3.org/2000/svg',inkscapeNamespace='http://www.inkscape.org/namespaces/inkscape';
const paints=['visibility','display','fill','fill-opacity','stroke','stroke-width','stroke-opacity','stroke-dasharray','stroke-linecap','stroke-linejoin','font-family','font-size','font-weight','font-style','text-anchor','dominant-baseline','opacity','color','paint-order','vector-effect'] as const;

/** Serializes the actual painted view. It never reprojects or invokes ELK. */
export function exportSvg(element:HTMLElement,document:DiagramDocumentDto,settings:DiagramSettings,scope:ExportScope,viewport:{zoom:number;x:number;y:number}):SvgArtifact {
 const source=element.querySelector<SVGSVGElement>('svg.sprotty-graph');if(!source)throw Error('There is no current diagram to export');
 const bounds=paintedBounds(source),box=scope==='FULL'?{x:bounds.x-24,y:bounds.y-24,width:bounds.width+48,height:bounds.height+48}:{x:viewport.x,y:viewport.y,width:element.clientWidth/viewport.zoom,height:element.clientHeight/viewport.zoom};
 const width=Math.max(1,Math.ceil(scope==='VISIBLE'?element.clientWidth:box.width)),height=Math.max(1,Math.ceil(scope==='VISIBLE'?element.clientHeight:box.height));
 const clone=source.cloneNode(true) as SVGSVGElement;const old=[source,...source.querySelectorAll<SVGElement>('*')],next=[clone,...clone.querySelectorAll<SVGElement>('*')];
 for(let i=0;i<old.length;i++){
  const style=getComputedStyle(old[i]);next[i].removeAttribute('style');for(const name of paints){const value=style.getPropertyValue(name);if(value&&!/url\s*\(/i.test(value))next[i].style.setProperty(name,value);}
  for(const attribute of [...next[i].attributes])if(/^on/i.test(attribute.name))next[i].removeAttribute(attribute.name);
 }
 clone.querySelectorAll('[data-handle-kind],.interlis-edge-hit,script,foreignObject').forEach(e=>e.remove());
 const ids=new Map<string,string>();for(const e of [clone,...clone.querySelectorAll<SVGElement>('[id]')])if(e.id){const id=svgId(e.id);ids.set(e.id,id);e.id=id;}
 for(const e of [clone,...clone.querySelectorAll<SVGElement>('*')])for(const attr of [...e.attributes])if(attr.value.includes('url('))e.setAttribute(attr.name,attr.value.replace(/url\(["']?#([^\s"')]+)["']?\)/g,(_all,id:string)=>'url(#'+(ids.get(id)??id)+')'));
 clone.setAttribute('xmlns',svgNamespace);clone.setAttribute('width',String(width));clone.setAttribute('height',String(height));clone.setAttribute('viewBox',`${box.x} ${box.y} ${box.width} ${box.height}`);clone.setAttribute('role','img');clone.removeAttribute('tabindex');
 const graph=clone.querySelector('g');graph?.removeAttribute('transform');
 const metadata=window.document.createElementNS(svgNamespace,'metadata');metadata.textContent=JSON.stringify({schemaVersion:1,snapshotId:document.snapshotId,sourceRevisions:document.sourceRevisions,completeness:document.completeness,scope,filtered:true,renderingTarget:settings.renderingTarget,edgeRouting:settings.edgeRouting});clone.prepend(metadata);
 const background=window.document.createElementNS(svgNamespace,'rect');background.setAttribute('x',String(box.x));background.setAttribute('y',String(box.y));background.setAttribute('width',String(box.width));background.setAttribute('height',String(box.height));let color=getComputedStyle(element).getPropertyValue('--diagram-background').trim()||getComputedStyle(element).backgroundColor;if(!color||color==='rgba(0, 0, 0, 0)'||/url\s*\(/i.test(color))color='white';background.setAttribute('fill',color);background.setAttribute('class','interlis-export-background');clone.insertBefore(background,graph);
 const nodes=new Map<string,SVGElement>();for(const node of clone.querySelectorAll<SVGElement>('[data-kind][data-element-id]')){
  const shape=[...node.children].find(c=>c.tagName.toLowerCase()==='rect'&&c.classList.contains('interlis-frame'));if(shape){shape.id=svgId('shape:'+node.getAttribute('data-element-id'));nodes.set(node.getAttribute('data-element-id')!,shape as SVGElement);}
  if(settings.renderingTarget==='INKSCAPE'){node.setAttributeNS(inkscapeNamespace,'inkscape:groupmode','group');node.setAttributeNS(inkscapeNamespace,'inkscape:label',node.getAttribute('data-canonical-name')??node.getAttribute('data-label')??'');}
 }
 if(settings.renderingTarget==='INKSCAPE'){
  clone.setAttribute('xmlns:inkscape',inkscapeNamespace);
  for(const edge of clone.querySelectorAll<SVGElement>('[data-edge-id][data-source][data-target]')){const line=edge.querySelector('path.interlis-edge-line'),start=nodes.get(edge.getAttribute('data-source')!),end=nodes.get(edge.getAttribute('data-target')!);if(!line||!start||!end)continue;
   if(settings.edgeRouting!=='SPLINES'){line.setAttributeNS(inkscapeNamespace,'inkscape:connector-type',settings.edgeRouting==='ORTHOGONAL'?'orthogonal':'polyline');line.setAttributeNS(inkscapeNamespace,'inkscape:connection-start','#'+start.id);line.setAttributeNS(inkscapeNamespace,'inkscape:connection-end','#'+end.id);}
   else {line.setAttribute('data-interlis-static-start','#'+start.id);line.setAttribute('data-interlis-static-end','#'+end.id);}
  }
 }
 if(document.completeness!=='COMPLETE'){const text=window.document.createElementNS(svgNamespace,'text');text.setAttribute('x',String(box.x+8));text.setAttribute('y',String(box.y+16));text.setAttribute('fill',getComputedStyle(element).color||'black');text.setAttribute('font-size','12');text.textContent=document.completeness+' diagram';clone.append(text);}
 const result=new XMLSerializer().serializeToString(clone);if(result.length>16_000_000)throw Error('SVG exceeds export size limit; narrow the visible view');
 const parsed=new DOMParser().parseFromString(result,'image/svg+xml');if(parsed.querySelector('parsererror,script,foreignObject')||/<!DOCTYPE|<!ENTITY/i.test(result))throw Error('Unsafe or malformed SVG export');
 for(const element of parsed.querySelectorAll('*'))for(const attr of [...element.attributes]){if(/^on/i.test(attr.name)||(/(?:^|:)href$/.test(attr.name)&&!attr.value.startsWith('#'))||(/url\s*\(/i.test(attr.value)&&!/^(?:url\(#[A-Za-z0-9_-]+\))$/.test(attr.value)))throw Error('SVG contains an external or active resource');}
 return {svg:result,width,height,viewBox:box,scope,snapshotId:document.snapshotId};
}
export async function exportPng(svg:SvgArtifact,scale=1):Promise<{data:Uint8Array;width:number;height:number}> {
 if(!Number.isFinite(scale)||scale<.5||scale>3)throw Error('Invalid PNG scale');const width=Math.ceil(svg.width*scale),height=Math.ceil(svg.height*scale);if(width>8192||height>8192||width*height>32_000_000)throw Error('PNG exceeds pixel limits; narrow the view or choose SVG');
 const url=URL.createObjectURL(new Blob([svg.svg],{type:'image/svg+xml'}));try{const image=new Image();image.src=url;await image.decode();const canvas=window.document.createElement('canvas');canvas.width=width;canvas.height=height;const context=canvas.getContext('2d');if(!context)throw Error('PNG canvas unavailable');context.drawImage(image,0,0,width,height);const blob=await new Promise<Blob>((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(Error('PNG encoding failed')),'image/png'));return {data:new Uint8Array(await blob.arrayBuffer()),width,height};}finally{URL.revokeObjectURL(url);}
}
