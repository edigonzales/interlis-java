import type {DiagramDocumentDto} from './types';
import type {Point} from 'sprotty-protocol';
export interface PresentationGeometry {positions:Record<string,Point>;routes:Record<string,Point[]>;}
export interface PresentationKeys {nodes:Map<string,string>;edges:Map<string,string>;}
/** Exact presentation identity only; never used for semantic lookup or navigation. Ambiguous keys are discarded. */
function unique(entries:readonly (readonly [string,string|null])[]):Map<string,string> {
 const counts=new Map<string,number>();for(const [,key]of entries)if(key)counts.set(key,(counts.get(key)??0)+1);
 return new Map(entries.flatMap(([id,key])=>key&&counts.get(key)===1?[[id,key]]:[]));
}
export function presentationKeys(document:DiagramDocumentDto):PresentationKeys {
 const nodes=unique([...document.containers,...document.nodes].map(node=>[node.id,node.anchor?JSON.stringify([node.anchor.nameRange.source,node.kind,node.canonicalName]):null] as const));
 const edges=unique(document.edges.map(edge=>{
  const source=nodes.get(edge.source.elementId),target=nodes.get(edge.target.elementId);
  const key=source&&target&&edge.anchor&&!edge.source.memberId&&!edge.target.memberId
   ?JSON.stringify([edge.anchor.nameRange.source,edge.kind,source,target,edge.label,edge.sourceEnd,edge.targetEnd]):null;
  return [edge.id,key] as const;
 }));return {nodes,edges};
}
/** Snapshot-local IDs may be reassigned. Across snapshots retain geometry only for unique exact source/kind/canonical keys. */
export function reconcileGeometry(previous:DiagramDocumentDto|undefined,next:DiagramDocumentDto,state:PresentationGeometry):PresentationGeometry {
 const result:PresentationGeometry={positions:Object.create(null),routes:Object.create(null)};if(!previous)return result;
 if(previous.snapshotId===next.snapshotId){
  for(const node of [...next.containers,...next.nodes])if(Object.hasOwn(state.positions,node.id))result.positions[node.id]={...state.positions[node.id]};
  for(const edge of next.edges)if(Object.hasOwn(state.routes,edge.id))result.routes[edge.id]=state.routes[edge.id].map(point=>({...point}));
  return result;
 }
 const old=presentationKeys(previous),current=presentationKeys(next),oldNodes=new Map([...old.nodes].map(([id,key])=>[key,id])),oldEdges=new Map([...old.edges].map(([id,key])=>[key,id]));
 for(const [id,key]of current.nodes){const oldId=oldNodes.get(key);if(oldId&&Object.hasOwn(state.positions,oldId))result.positions[id]={...state.positions[oldId]};}
 for(const [id,key]of current.edges){const oldId=oldEdges.get(key);if(oldId&&Object.hasOwn(state.routes,oldId))result.routes[id]=state.routes[oldId].map(point=>({...point}));}
 return result;
}
