import Ajv from 'ajv/dist/2020';
import schema from '../../../../interlis-projection/src/main/resources/guru/interlis/projection/diagram-document-1.0.schema.json';
import type * as D from './compiler-protocol';
import type {DiagramNode as LegacyNode, DiagramEdge as LegacyEdge, SourceRange as LegacyRange} from './vendor/ilic-diagram/compiler-contract';
import {layoutAndRenderDiagram, renderSvg, toSprottyModel, defaultDiagramSettings as upstreamDefaultSettings, type DiagramSettings, type LayoutDiagram} from './vendor/ilic-diagram/index';

export const defaultDiagramSettings=Object.freeze({...upstreamDefaultSettings});

export interface HostSource {readonly text:string;readonly revision:string;}
export type HostSources=ReadonlyMap<string,HostSource>;
export interface PresentationNotice {readonly code:'UNSUPPORTED_PRESENTATION';readonly message:string; readonly elementId?:string;}
export interface DiagramView {
  readonly document:D.DiagramDocumentDto; readonly snapshotId:string;
  readonly associations:ReadonlyMap<string,readonly D.DiagramEdgeDto[]>;
  readonly notices:readonly PresentationNotice[];
}
export type NavigationResult =
  {readonly kind:'resolved';readonly snapshotId:string;readonly range:D.SourceRangeDto;readonly editorRange:LegacyRange} |
  {readonly kind:'stale';readonly code:'STALE_SELECTION'} |
  {readonly kind:'unavailable';readonly code:'NO_SOURCE_ANCHOR'};
interface Details {
  anchors:Map<string,D.SourceAnchorDto>; symbols:Map<string,string[]>;
  sources:Map<string,HostSource>; coordinates:Map<string,Coordinates>;
}
const details=new WeakMap<DiagramView,Details>();
const validate=new Ajv({strict:true,allErrors:true}).compile<D.DiagramDocumentDto>(schema);
function freeze<T>(value:T):T {
  if(value && typeof value==='object'){Object.freeze(value);Object.values(value).forEach(freeze);}return value;
}
function detached<T>(value:T):T {return JSON.parse(JSON.stringify(value)) as T;}
class ReadonlyGroups<K,V> implements ReadonlyMap<K,V> {
  #data:Map<K,V>;
  constructor(entries:Iterable<readonly[K,V]>){this.#data=new Map(entries);Object.freeze(this);}
  get size(){return this.#data.size;}
  get(key:K){return this.#data.get(key);}
  has(key:K){return this.#data.has(key);}
  entries(){return this.#data.entries();}
  keys(){return this.#data.keys();}
  values(){return this.#data.values();}
  [Symbol.iterator](){return this.#data[Symbol.iterator]();}
  forEach(callback:(value:V,key:K,map:ReadonlyMap<K,V>)=>void,thisArg?:unknown){this.#data.forEach((v,k)=>callback.call(thisArg,v,k,this));}
}
class Coordinates {
  private starts=[0];
  private bytes:Uint32Array;
  constructor(readonly text:string){
    this.bytes=new Uint32Array(text.length+1);let bytes=0;
    // UTF-8 offsets describe this Unicode text, not unavailable original file
    // bytes. Lone surrogates follow TextEncoder's explicit U+FFFD convention.
    for(let i=0;i<text.length;i++){
      const c=text.charCodeAt(i);
      if(c>=0xd800 && c<=0xdbff && i+1<text.length && text.charCodeAt(i+1)>=0xdc00 && text.charCodeAt(i+1)<=0xdfff){
        this.bytes[i+1]=0xffffffff;bytes+=4;i++;this.bytes[i+1]=bytes;
      }else{bytes+=c<=0x7f?1:c<=0x7ff?2:3;this.bytes[i+1]=bytes;}
    }
    for(let i=0;i<text.length;i++){if(text[i]==='\r'){if(text[i+1]==='\n')i++;this.starts.push(i+1);}else if(text[i]==='\n')this.starts.push(i+1);}
  }
  position(offset:number){
    if(!Number.isSafeInteger(offset)||offset<0||offset>this.text.length)throw new TypeError('diagram range outside its source');
    if(this.bytes[offset]===0xffffffff)throw new TypeError('diagram range splits a Unicode surrogate pair');
    let low=0,high=this.starts.length;
    while(low+1<high){const middle=Math.floor((low+high)/2);if(this.starts[middle]<=offset)low=middle;else high=middle;}
    return {line:low,character:offset-this.starts[low],byteOffset:this.bytes[offset]};
  }
}
function symbolKey(ref:D.SymbolRefDto){return ref.snapshot+'\u0000'+ref.symbol;}
function known(view:DiagramView):Details {const value=details.get(view);if(!value)throw new TypeError('foreign diagram view');return value;}
function addAnchor(data:Details,document:D.DiagramDocumentDto,id:string,anchor:D.SourceAnchorDto|null){
  if(!anchor)return;
  if(anchor.snapshotId!==document.snapshotId)throw new TypeError('diagram anchor snapshot mismatch');
  for(const range of [anchor.nameRange,anchor.declarationRange])if(range){
    if(range.revision!==document.sourceRevisions[range.source] || range.endOffset<range.startOffset)throw new TypeError('diagram anchor source revision mismatch');
    const source=data.sources.get(range.source);if(source && source.revision===range.revision){
      data.coordinates.get(range.source)!.position(range.startOffset);data.coordinates.get(range.source)!.position(range.endOffset);
    }
  }
  data.anchors.set(id,anchor);
}
function addSymbol(data:Details,id:string,ref:D.SymbolRefDto|null){if(ref){const key=symbolKey(ref);const ids=data.symbols.get(key)??[];ids.push(id);data.symbols.set(key,ids);}}

export function adapt(raw:unknown,sources:HostSources=new Map()):DiagramView {
  if(!validate(raw))throw new TypeError('diagram schema mismatch: '+JSON.stringify(validate.errors));
  const document=freeze(detached(raw));
  const data:Details={anchors:new Map(),symbols:new Map(),sources:new Map(),coordinates:new Map()};
  for(const [id,source] of sources){data.sources.set(id,Object.freeze({text:source.text,revision:source.revision}));data.coordinates.set(id,new Coordinates(source.text));}
  const ids=new Set<string>(),containers=new Map(document.containers.map(container=>[container.id,container])),nodes=new Map(document.nodes.map(node=>[node.id,node]));
  const addId=(id:string)=>{if(!id || ids.has(id))throw new TypeError('duplicate or empty diagram identity');ids.add(id);};
  const symbol=(id:string,ref:D.SymbolRefDto|null)=>{if(ref && ref.snapshot!==document.snapshotId)throw new TypeError('diagram symbol snapshot mismatch');addSymbol(data,id,ref);};
  for(const container of document.containers){
    addId(container.id);addAnchor(data,document,container.id,container.anchor);symbol(container.id,container.symbol);
    let current:D.DiagramContainerDto|undefined=container;const path=new Set<string>();
    while(current){if(path.has(current.id))throw new TypeError('cyclic diagram containers');path.add(current.id);
      if(current.parentId && !containers.has(current.parentId))throw new TypeError('missing diagram parent');current=current.parentId?containers.get(current.parentId):undefined;}
  }
  const notices:PresentationNotice[]=[];
  for(const node of document.nodes){
    addId(node.id);if(!containers.has(node.containerId))throw new TypeError('missing node container');
    addAnchor(data,document,node.id,node.anchor);symbol(node.id,node.symbol);
    if(!node.modifiers)notices.push({code:'UNSUPPORTED_PRESENTATION',message:'This older diagram producer supplies no declaration modifiers.'});
    for(const compartment of node.compartments){addId(compartment.id);for(const member of compartment.members){
      addId(member.id);addAnchor(data,document,member.id,member.anchor);symbol(member.id,{snapshot:document.snapshotId,symbol:member.declaredSymbol});
    }}
  }
  const groups=new Map<string,D.DiagramEdgeDto[]>();
  for(const edge of document.edges){
    if(edge.sourceEnd.aggregation!=='NONE'||edge.targetEnd.aggregation!=='NONE')notices.push({code:'UNSUPPORTED_PRESENTATION',elementId:edge.id,message:'The pinned renderer displays this relationship but supplies no aggregation/composition end markers. Exact end information remains in the diagram document.'});
    addId(edge.id);const source=nodes.get(edge.source.elementId)??containers.get(edge.source.elementId),target=nodes.get(edge.target.elementId)??containers.get(edge.target.elementId);
    if(!source||!target)throw new TypeError('missing diagram edge endpoint');
    for(const [endpoint,node] of [[edge.source,source],[edge.target,target]] as const)if(endpoint.memberId && (!('compartments' in node) || !node.compartments.some(c=>c.members.some(m=>m.id===endpoint.memberId))))throw new TypeError('missing endpoint member');
    addAnchor(data,document,edge.id,edge.anchor);symbol(edge.id,edge.origin);
    if(edge.kind==='ASSOCIATION_ROLE' && source.kind==='ASSOCIATION'){
      const origin=source.symbol?symbolKey(source.symbol):source.id;const group=groups.get(origin)??[];group.push(edge);groups.set(origin,group);
    }
  }
  const view:DiagramView=Object.freeze({document,snapshotId:document.snapshotId,associations:new ReadonlyGroups([...groups].map(([key,value])=>[key,Object.freeze(value)] as const)),notices:freeze(notices)});
  details.set(view,data);return view;
}

export function isStale(view:DiagramView,currentSnapshotId:string):boolean {return view.snapshotId!==currentSnapshotId;}
export function resolveNavigation(view:DiagramView,elementId:string,currentSnapshotId:string,currentSources:HostSources):NavigationResult {
  const data=known(view);if(isStale(view,currentSnapshotId))return {kind:'stale',code:'STALE_SELECTION'};
  const anchor=data.anchors.get(elementId);if(!anchor)return {kind:'unavailable',code:'NO_SOURCE_ANCHOR'};
  const range=anchor.nameRange,current=currentSources.get(range.source),original=data.sources.get(range.source);
  if(!current||!original)return {kind:'unavailable',code:'NO_SOURCE_ANCHOR'};
  if(current.revision!==range.revision || current.text!==original.text || anchor.snapshotId!==currentSnapshotId)return {kind:'stale',code:'STALE_SELECTION'};
  const coordinates=data.coordinates.get(range.source)!;
  return freeze({kind:'resolved',snapshotId:currentSnapshotId,range,editorRange:{uri:range.source,start:coordinates.position(range.startOffset),end:coordinates.position(range.endOffset)}});
}
export function elementsForSymbol(view:DiagramView,ref:D.SymbolRefDto):readonly string[] {
  if(ref.snapshot!==view.snapshotId)return Object.freeze([]);
  return Object.freeze([...(known(view).symbols.get(symbolKey(ref))??[])]);
}
function legacyRange(view:DiagramView,id:string):LegacyRange|null {
  const data=known(view),result=resolveNavigation(view,id,view.snapshotId,data.sources);
  return result.kind==='resolved'?detached(result.editorRange):null;
}
function cardinality(value:D.DiagramEdgeEndDto['cardinality']):string {
  if(!value)return '';const maximum=value.maximum.kind==='unbounded'?'*':value.maximum.value;
  return value.minimum===maximum?maximum:value.minimum+'..'+maximum;
}

/** Exact shape consumed by the pinned @ilic/diagram source, with no source-language inference. */
export function legacyProjection(view:DiagramView):{nodes:LegacyNode[];edges:LegacyEdge[]} {
  known(view);const document=view.document;
  const nodes:LegacyNode[]=document.containers.map(container=>({id:container.id,containerId:container.parentId??'',label:container.label,kind:container.kind.toLowerCase(),abstract:false,
    range:legacyRange(view,container.id),members:[],enumValues:[]}));
  const declaredOwners=new Map(document.nodes.flatMap(node=>node.compartments.flatMap(c=>c.members.filter(m=>!m.inherited).map(m=>[m.declaredSymbol,node.canonicalName] as const))));
  for(const node of document.nodes){
    const members:LegacyNode['members']=[],operations:string[]=[],enumValues:string[]=[];
    for(const compartment of node.compartments)for(const member of compartment.members){
      if(compartment.kind==='CONSTRAINTS')operations.push(member.label+(member.typeLabel?' : '+member.typeLabel:''));
      else if(compartment.kind==='ENUM_VALUES')enumValues.push(member.label);
      else members.push({name:member.label+(member.overridden?' (EXTENDED)':''),type:member.typeLabel??'',...(member.cardinality?{cardinality:member.cardinality}:{}),
        declaringType:declaredOwners.get(member.declaredSymbol)??member.declaredSymbol,inherited:member.inherited});
    }
    nodes.push({id:node.id,containerId:node.containerId,label:node.label,kind:node.kind.toLowerCase(),abstract:node.modifiers?.includes('ABSTRACT')??false,
      range:legacyRange(view,node.id),stereotypes:node.status==='RESOLVED'?[]:[node.status],members,enumValues,operations});
  }
  const edges:LegacyEdge[]=document.edges.map(edge=>({id:edge.id,sourceId:edge.source.elementId,targetId:edge.target.elementId,kind:edge.kind==='ASSOCIATION_ROLE'?'association':edge.kind.toLowerCase(),
    label:edge.label,cardinality:cardinality(edge.targetEnd.cardinality),sourceCardinality:cardinality(edge.sourceEnd.cardinality),targetCardinality:cardinality(edge.targetEnd.cardinality)}));
  return {nodes,edges};
}
export interface RenderedDiagram {readonly snapshotId:string;readonly layout:LayoutDiagram;readonly svg:string;readonly sprotty:ReturnType<typeof import('./vendor/ilic-diagram/index').toSprottyModel>;}
const renderedSignatures=new WeakMap<RenderedDiagram,string>();
function geometryKey(projection:ReturnType<typeof legacyProjection>,settings:DiagramSettings):string {
  return JSON.stringify({settings,nodes:projection.nodes.map(n=>({id:n.id,containerId:n.containerId,kind:n.kind,members:n.members,operations:n.operations,enumValues:n.enumValues})),edges:projection.edges});
}
/** Reuses one accepted host layout only when geometry and settings still agree.
 * Labels and source ranges are refreshed from the new immutable projection;
 * no Core value or revision is mutated. Callers publish only current snapshots.
 */
export async function render(view:DiagramView,settings:DiagramSettings=defaultDiagramSettings,previous?:RenderedDiagram):Promise<RenderedDiagram> {
  const effective={...settings,...(view.notices.length?{deemphasizeAbstractTypes:false}:{})};
  const projection=legacyProjection(view),signature=geometryKey(projection,effective);
  let result:Omit<RenderedDiagram,'snapshotId'>;
  if(previous && renderedSignatures.get(previous)===signature){
    const byId=new Map(projection.nodes.map(node=>[node.id,node]));
    const layout:LayoutDiagram={...previous.layout,nodes:previous.layout.nodes.map(node=>({...node,source:byId.get(node.id)!}))};
    result={layout,svg:renderSvg(layout,effective),sprotty:toSprottyModel(layout)};
  }else result=await layoutAndRenderDiagram(projection,effective);
  const rendered=freeze({snapshotId:view.snapshotId,...result});renderedSignatures.set(rendered,signature);return rendered;
}
/** Stateless typed adapter over the pinned renderer; all views remain snapshot-bound. */
export class SprottyDiagramAdapter {
  readonly adapt=adapt;
  readonly isStale=isStale;
  readonly resolveNavigation=resolveNavigation;
  readonly elementsForSymbol=elementsForSymbol;
  readonly legacyProjection=legacyProjection;
  readonly render=render;
  readonly defaultDiagramSettings=defaultDiagramSettings;
}
export const diagramAdapter=Object.freeze(new SprottyDiagramAdapter());
