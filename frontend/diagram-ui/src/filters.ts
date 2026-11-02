import type {DiagramDocumentDto} from './types';

/** Presentation switches over authoritative DTO categories; no names are resolved here. */
export interface DiagramFilters {
 kinds:Record<string,boolean>;
 edges:Record<string,boolean>;
 attributes:boolean;roles:boolean;types:boolean;cardinalities:boolean;
 constraints:boolean;enumerations:boolean;modelFrames:boolean;topicFrames:boolean;
 directImports:boolean;indirectImports:boolean;
}
export const nodeKinds=['CLASS','STRUCTURE','ASSOCIATION','VIEW','DOMAIN','EXTERNAL_REFERENCE','GRAPHIC','DRAWING_RULE','LINE_FORM','METADATA_BASKET','METAOBJECT'] as const;
export const edgeKinds=['INHERITANCE','ASSOCIATION_ROLE','ASSOCIATION','REFERENCE','STRUCTURE_COMPOSITION','DOMAIN_USE','VIEW_DERIVATION','MODEL_IMPORT','TRANSLATION'] as const;
export function defaultFilters():DiagramFilters {return {kinds:Object.fromEntries(nodeKinds.map(k=>[k,true])),edges:Object.fromEntries(edgeKinds.map(k=>[k,true])),attributes:true,roles:true,types:true,cardinalities:true,constraints:true,enumerations:true,modelFrames:true,topicFrames:true,directImports:true,indirectImports:true};}

/** Distances follow only explicit Core-projected MODEL_IMPORT edges. */
export function importDepths(document:DiagramDocumentDto):Map<string,number> {
 const containers=new Map(document.containers.map(c=>[c.id,c]));
 const modelOf=(id:string):string|undefined=>{let current=containers.get(id)??containers.get(document.nodes.find(n=>n.id===id)?.containerId??'');for(let i=0;current&&i<64;i++){if(current.kind==='MODEL')return current.id;current=containers.get(current.parentId??'');}return undefined;};
 const depths=new Map<string,number>();
 for(const selection of document.selection){const value=[...document.containers,...document.nodes].find(n=>n.symbol?.snapshot===selection.snapshot&&n.symbol.symbol===selection.symbol);const model=value&&modelOf(value.id);if(model)depths.set(model,0);}
 const queue=[...depths.keys()];for(let i=0;i<queue.length;i++){const id=queue[i],depth=depths.get(id)!;for(const edge of document.edges)if(edge.kind==='MODEL_IMPORT'&&edge.source.elementId===id&&!depths.has(edge.target.elementId)){depths.set(edge.target.elementId,depth+1);queue.push(edge.target.elementId);}}
 for(const container of document.containers){const model=modelOf(container.id);if(model&&depths.has(model))depths.set(container.id,depths.get(model)!);}
 return depths;
}

export function visibleDocument(document:DiagramDocumentDto,filters:DiagramFilters,hiddenIds:ReadonlySet<string>=new Set()):DiagramDocumentDto {
 const containers=new Map(document.containers.map(c=>[c.id,c])),depths=importDepths(document);
 const allowedParent=(id:string):boolean=>{let current=containers.get(id);for(let i=0;current&&i<64;i++){if(hiddenIds.has(current.id))return false;const depth=depths.get(current.id);if(depth===1&&!filters.directImports||depth!==undefined&&depth>1&&!filters.indirectImports)return false;current=containers.get(current.parentId??'');}return true;};
 let nodes=document.nodes.filter(n=>filters.kinds[n.kind]!==false&&!hiddenIds.has(n.id)&&allowedParent(n.containerId));
 let ids=new Set([...nodes.map(n=>n.id),...document.containers.filter(c=>!hiddenIds.has(c.id)&&allowedParent(c.id)).map(c=>c.id)]);
 let edges=document.edges.filter(e=>filters.edges[e.kind]!==false&&ids.has(e.source.elementId)&&ids.has(e.target.elementId));
 // External stubs exist only while a surviving visible connection needs them.
 const connected=new Set(nodes.filter(n=>n.kind!=='EXTERNAL_REFERENCE').map(n=>n.id));
 const nodeIds=new Set(nodes.map(n=>n.id)),adjacent=new Map<string,string[]>();
 for(const edge of edges)for(const [a,b]of [[edge.source.elementId,edge.target.elementId],[edge.target.elementId,edge.source.elementId]])if(nodeIds.has(a)&&nodeIds.has(b)){const targets=adjacent.get(a)??[];targets.push(b);adjacent.set(a,targets);}
 const reachable=[...connected];for(let i=0;i<reachable.length;i++)for(const id of adjacent.get(reachable[i])??[])if(!connected.has(id)){connected.add(id);reachable.push(id);}
 nodes=nodes.filter(n=>n.kind!=='EXTERNAL_REFERENCE'||connected.has(n.id));
 ids=new Set(nodes.map(n=>n.id));
 // Frames without visible descendants disappear with their incident frame edges.
 const needed=new Set<string>();for(const node of nodes){let current=containers.get(node.containerId);for(let i=0;current&&i<64;i++){needed.add(current.id);current=containers.get(current.parentId??'');}}
 for(const id of needed)ids.add(id);
 edges=edges.filter(e=>ids.has(e.source.elementId)&&ids.has(e.target.elementId));
 const visibleNodes=nodes.map(node=>({...node,compartments:node.compartments.filter(c=>!(c.kind==='ATTRIBUTES'&&!filters.attributes||c.kind==='ROLES'&&!filters.roles||c.kind==='CONSTRAINTS'&&!filters.constraints||c.kind==='ENUM_VALUES'&&!filters.enumerations)).map(c=>({...c,members:c.members.map(m=>({...m,typeLabel:filters.types?m.typeLabel:null,cardinality:filters.cardinalities?m.cardinality:null}))}))}));
 return {...document,nodes:visibleNodes,containers:document.containers.filter(c=>needed.has(c.id)),edges:edges.map(edge=>({...edge,sourceEnd:{...edge.sourceEnd,roleName:filters.roles?edge.sourceEnd.roleName:null,cardinality:filters.cardinalities?edge.sourceEnd.cardinality:null},targetEnd:{...edge.targetEnd,roleName:filters.roles?edge.targetEnd.roleName:null,cardinality:filters.cardinalities?edge.targetEnd.cardinality:null}}))};
}

export class DiagramFilterController {
 filters=defaultFilters();
 hiddenIds=new Set<string>();
 set(path:string,value:boolean):void {
  const [group,key]=path.split('.');
  if(group==='kinds'||group==='edges'){const target=this.filters[group];if(!Object.hasOwn(target,key))throw Error('Unknown filter');target[key]=value;}
  else if(Object.hasOwn(this.filters,path)&&typeof this.filters[path as keyof DiagramFilters]==='boolean')(this.filters as unknown as Record<string,unknown>)[path]=value;
  else throw Error('Unknown filter');
 }
 setAll(value:boolean):void {const next=defaultFilters();for(const key of Object.keys(next) as (keyof DiagramFilters)[]){if(key==='kinds'||key==='edges')for(const kind of Object.keys(next[key]))next[key][kind]=value;else next[key]=value;}this.filters=next;this.hiddenIds.clear();}
 reset():void {this.filters=defaultFilters();this.hiddenIds.clear();}
 visible(document:DiagramDocumentDto):DiagramDocumentDto {return visibleDocument(document,this.filters,this.hiddenIds);}
}
