import type {DiagramDocumentDto} from './types';
import {presentationKeys,type PresentationGeometry} from './identity';
import {defaultDiagramSettings,type DiagramSettings} from './layout';
import {defaultFilters,type DiagramFilters} from './filters';

export interface SavedViewState {
 version:1;
 /** SHA-256 presentation keys: no source text, URI credentials or labels are stored. */
 positions:Record<string,{x:number;y:number}>;
 routes:Record<string,{x:number;y:number}[]>;
 pins:string[];manualNodes:string[];manualRoutes:string[];hiddenNodes:string[];
 filters:DiagramFilters;settings:DiagramSettings;
 viewport:{zoom:number;x:number;y:number};
}
export interface RuntimeViewState {
 geometry:PresentationGeometry;pins:Set<string>;manualNodes:Set<string>;manualRoutes:Set<string>;hiddenNodes:Set<string>;
 filters:DiagramFilters;settings:DiagramSettings;viewport:{zoom:number;x:number;y:number};
}
const cache=new WeakMap<DiagramDocumentDto,Promise<{nodes:Map<string,string>;edges:Map<string,string>}>>();
async function identities(document:DiagramDocumentDto):Promise<{nodes:Map<string,string>;edges:Map<string,string>}> {
 let cached=cache.get(document);if(cached)return cached;
 cached=(async()=>{const source=presentationKeys(document),digest=async(key:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(key))),v=>v.toString(16).padStart(2,'0')).join('');const convert=async(map:Map<string,string>)=>new Map(await Promise.all([...map].map(async([id,key])=>[id,await digest(key)] as const)));return {nodes:await convert(source.nodes),edges:await convert(source.edges)};})();cache.set(document,cached);return cached;
}
const hashPattern=/^[a-f0-9]{64}$/;
const fail=():never=>{throw Error('Invalid or oversized diagram view state');};
function object(value:unknown):Record<string,unknown>{if(!value||typeof value!=='object'||Array.isArray(value)||(Object.getPrototypeOf(value)!==Object.prototype&&Object.getPrototypeOf(value)!==null))return fail();return value as Record<string,unknown>;}
function keys(value:Record<string,unknown>,allowed:string[]):void {if(Object.keys(value).length!==allowed.length||Object.keys(value).some(k=>!allowed.includes(k)))fail();}
function finite(value:unknown,min=-1e6,max=1e6):number {if(typeof value!=='number'||!Number.isFinite(value)||value<min||value>max)return fail();return value;}
function point(value:unknown):{x:number;y:number}{const p=object(value);keys(p,['x','y']);return {x:finite(p.x),y:finite(p.y)};}
function hashes(value:unknown,max=1000):string[]{if(!Array.isArray(value)||value.length>max||value.some(v=>typeof v!=='string'||!hashPattern.test(v))||new Set(value).size!==value.length)return fail();return [...value];}
function switches(value:unknown,defaults:Record<string,boolean>):Record<string,boolean>{const result=object(value);keys(result,Object.keys(defaults));for(const value of Object.values(result))if(typeof value!=='boolean')fail();return {...result} as Record<string,boolean>;}
export function validateFilters(value:unknown):DiagramFilters {
 const input=object(value),defaults=defaultFilters();keys(input,Object.keys(defaults));const output={...defaults,kinds:switches(input.kinds,defaults.kinds),edges:switches(input.edges,defaults.edges)};
 for(const name of Object.keys(defaults).filter(k=>k!=='kinds'&&k!=='edges')){if(typeof input[name]!=='boolean')fail();(output as unknown as Record<string,unknown>)[name]=input[name];}return output;
}
export function validateViewSettings(value:unknown):DiagramSettings {
 const input=object(value);const allowed=[...Object.keys(defaultDiagramSettings),'direction'];
 if(Object.keys(input).some(k=>!allowed.includes(k)))fail();
 const output={...defaultDiagramSettings,...input};
 const enums:Record<string,string[]>={nodePlacement:['SIMPLE','INTERACTIVE','LINEAR_SEGMENTS','BRANDES_KOEPF','NETWORK_SIMPLEX'],edgeRouting:['ORTHOGONAL','POLYLINE','SPLINES'],renderingTarget:['STANDARD','INKSCAPE'],edgeCrossingStyle:['PLAIN','GAPS'],attributeMode:['NONE','OWN','OWN_AND_INHERITED'],direction:['RIGHT','LEFT','DOWN','UP']};
 for(const [name,choices]of Object.entries(enums)){const value=(output as unknown as Record<string,unknown>)[name];if(name==='direction'&&value===undefined)continue;if(typeof value!=='string'||!choices.includes(value))fail();}
 for(const name of Object.keys(defaultDiagramSettings).filter(k=>!(k in enums)))if(typeof input[name]!=='boolean')fail();
 return output as DiagramSettings;
}
/** Strict, bounded transport/storage boundary. Unknown fields never enter workspace storage. */
export function validateSavedViewState(value:unknown):SavedViewState {
 const encoded=JSON.stringify(value);if(!encoded||encoded.length>1_000_000)fail();const input=object(value);keys(input,['version','positions','routes','pins','manualNodes','manualRoutes','hiddenNodes','filters','settings','viewport']);if(input.version!==1)fail();
 const positions=object(input.positions),routes=object(input.routes);if(Object.keys(positions).length>1000||Object.keys(routes).length>1000)fail();
 const cleanPositions:SavedViewState['positions']=Object.create(null),cleanRoutes:SavedViewState['routes']=Object.create(null);
 for(const [key,value]of Object.entries(positions)){if(!hashPattern.test(key))fail();cleanPositions[key]=point(value);}
 for(const [key,value]of Object.entries(routes)){if(!hashPattern.test(key)||!Array.isArray(value)||value.length>256)fail();cleanRoutes[key]=(value as unknown[]).map(point);}
 const viewport=object(input.viewport);keys(viewport,['zoom','x','y']);
 return {version:1,positions:cleanPositions,routes:cleanRoutes,pins:hashes(input.pins),manualNodes:hashes(input.manualNodes),manualRoutes:hashes(input.manualRoutes),hiddenNodes:hashes(input.hiddenNodes),filters:validateFilters(input.filters),settings:validateViewSettings(input.settings),viewport:{zoom:finite(viewport.zoom,.08,4),x:finite(viewport.x),y:finite(viewport.y)}};
}
export async function saveViewState(document:DiagramDocumentDto,state:RuntimeViewState):Promise<SavedViewState> {
 const keys=await identities(document),positions:SavedViewState['positions']=Object.create(null),routes:SavedViewState['routes']=Object.create(null);
 for(const [id,key]of keys.nodes)if(Object.hasOwn(state.geometry.positions,id)){const p=state.geometry.positions[id];positions[key]={x:p.x,y:p.y};}
 for(const [id,key]of keys.edges)if(Object.hasOwn(state.geometry.routes,id))routes[key]=state.geometry.routes[id].map(p=>({x:p.x,y:p.y}));
 const map=(values:Set<string>,keys:Map<string,string>)=>[...values].flatMap(id=>keys.has(id)?[keys.get(id)!]:[]);
 return validateSavedViewState({version:1,positions,routes,pins:map(state.pins,keys.nodes),manualNodes:map(state.manualNodes,keys.nodes),manualRoutes:map(state.manualRoutes,keys.edges),hiddenNodes:map(state.hiddenNodes,keys.nodes),filters:state.filters,settings:state.settings,viewport:state.viewport});
}
export async function restoreViewState(document:DiagramDocumentDto,value:unknown):Promise<RuntimeViewState> {
 const saved=validateSavedViewState(value),keys=await identities(document),positions:PresentationGeometry['positions']=Object.create(null),routes:PresentationGeometry['routes']=Object.create(null);
 for(const [id,key]of keys.nodes)if(Object.hasOwn(saved.positions,key))positions[id]={...saved.positions[key]};
 for(const [id,key]of keys.edges)if(Object.hasOwn(saved.routes,key))routes[id]=saved.routes[key].map(p=>({...p}));
 const map=(values:string[],keys:Map<string,string>)=>new Set([...keys].filter(([,key])=>values.includes(key)).map(([id])=>id));
 return {geometry:{positions,routes},pins:map(saved.pins,keys.nodes),manualNodes:map(saved.manualNodes,keys.nodes),manualRoutes:map(saved.manualRoutes,keys.edges),hiddenNodes:map(saved.hiddenNodes,keys.nodes),filters:saved.filters,settings:saved.settings,viewport:saved.viewport};
}
