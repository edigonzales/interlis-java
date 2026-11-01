import 'reflect-metadata';
import {Container,ContainerModule,inject,injectable} from 'inversify';
import {TYPES,loadDefaultModules,configureModelElement,configureViewerOptions,configureCommandStackOptions,
 configureCommand,edgeEditModule,labelEditModule,labelEditUiModule,SwitchEditModeCommand,
 LocalModelSource,SGraphImpl,SGraphView,SNodeImpl,SLabelImpl,SEdgeImpl,ShapeView,PolylineEdgeView,
 SRoutingHandleImpl,SRoutingHandleView,PolylineEdgeRouter,ManhattanEdgeRouter,SRoutableElementImpl,
 MouseListener,SModelElementImpl,SModelRootImpl,CommandStack,selectFeature,editFeature,deletableFeature,
 boundsFeature,hoverFeedbackFeature,svg,type RenderingContext,type IActionDispatcher} from 'sprotty';
import {FitToScreenAction,SelectAction,UndoAction,RedoAction,type Action,type SModelRoot,type SNode,type SEdge,type Point} from 'sprotty-protocol';
import type {VNode} from 'snabbdom';
import {adapt,legacyProjection,type DiagramView} from './adapter';
import {defaultDiagramSettings,type DiagramSettings,type LayoutDiagram} from './layout';
import type {DiagramDocumentDto,DiagramEdgeDto,DiagramNodeDto,DiagramContainerDto} from './types';
import {ElkLayoutService,type LayoutServicePort} from './elk-layout-service';
import {presentation,endLabel} from './presentation';

export const defaultInteractiveSettings:DiagramSettings=Object.freeze({...defaultDiagramSettings,attributeMode:'OWN_AND_INHERITED'});
export type DiagramStatus='EMPTY'|'LOADING'|'FRESH'|'PARTIAL'|'STALE'|'ERROR';
export interface DiagramViewState {positions:Record<string,Point>;routes:Record<string,Point[]>;}
export interface DiagramCallbacks {navigate:(id:string)=>void;status?:(status:DiagramStatus,message:string)=>void;changed?:(state:DiagramViewState)=>void;singleClickNavigation?:boolean;}
const OBSERVER=Symbol('InterlisViewObserver'),CALLBACKS=Symbol('InterlisViewCallbacks'),LABELS=Symbol('InterlisLabelPlacement');
interface NodeData {label:string;kind:string;canonicalName:string;titleLines:string[];abstract:boolean;status:string;}

@injectable()
class InterlisGraph extends SGraphImpl { generation=0; }
@injectable()
class InterlisNode extends SNodeImpl {
 static override readonly DEFAULT_FEATURES=SNodeImpl.DEFAULT_FEATURES.filter(feature=>feature!==deletableFeature);
 data!:NodeData;
 override get anchorKind():string {return 'rectangular';}
 override canConnect():boolean {return false;}
}
@injectable()
class InterlisMember extends SLabelImpl {
 static override readonly DEFAULT_FEATURES=[boundsFeature,selectFeature,hoverFeedbackFeature];
 elementId!:string;
 compartmentKind!:string;
}
@injectable()
class InterlisEdge extends SEdgeImpl {
 static override readonly DEFAULT_FEATURES=[...SEdgeImpl.DEFAULT_FEATURES.filter(feature=>feature!==deletableFeature),editFeature];
 data!:DiagramEdgeDto;
}
@injectable()
class InterlisNodeView extends ShapeView {
 render(node:Readonly<InterlisNode>,context:RenderingContext):VNode {
  const container=node.type==='node:container',data=node.data;
  return svg('g',{'class-interlis-node':true,'class-interlis-container':container,'class-selected':node.selected,
   'class-abstract':data.abstract,'data-element-id':node.id,'data-symbol-id':node.id,'data-kind':data.kind,'data-label':data.label},
   svg('title',{},data.canonicalName),
   svg('rect',{width:node.size.width,height:node.size.height,rx:container?7:3,'class-interlis-frame':true}),
   ...data.titleLines.map((line,index)=>svg('text',{x:12,y:22+index*18,'class-interlis-title':true},line)),
   ...(!container&&data.kind!=='CLASS'?[svg('text',{x:12,y:data.titleLines.length*18+20,'class-interlis-stereotype':true},'«'+data.kind.toLowerCase()+'»')]:[]),
   ...(data.abstract?[svg('text',{x:12,y:data.titleLines.length*18+20+(data.kind!=='CLASS'?20:0),'class-interlis-stereotype':true},'«abstract»')]:[]),
   ...(data.status==='RESOLVED'?[]:[svg('text',{x:12,y:node.size.height-7,'class-interlis-warning':true},data.status)]),
   ...context.renderChildren(node));
 }
}
@injectable()
class InterlisContainerView extends InterlisNodeView { }
@injectable()
class InterlisMemberView extends ShapeView {
 render(member:Readonly<InterlisMember>):VNode {
  return svg('text',{'class-interlis-member':true,'class-selected':member.selected,'data-element-id':member.elementId,'data-member-id':member.elementId,'data-compartment':member.compartmentKind,x:0,y:0},member.text);
 }
}
function pointAt(points:Point[],fraction:number):Point {
 const lengths=points.slice(1).map((p,i)=>Math.hypot(p.x-points[i].x,p.y-points[i].y));let remaining=lengths.reduce((a,b)=>a+b,0)*fraction;
 for(let i=0;i<lengths.length;i++){if(remaining<=lengths[i]||i===lengths.length-1){const t=lengths[i]?remaining/lengths[i]:0;return {x:points[i].x+(points[i+1].x-points[i].x)*t,y:points[i].y+(points[i+1].y-points[i].y)*t};}remaining-=lengths[i];}return points[0]??{x:0,y:0};
}
function marker(at:Point,previous:Point,aggregation:string,inheritance=false):VNode[] {
 if(aggregation==='NONE'&&!inheritance)return [];
 const angle=Math.atan2(at.y-previous.y,at.x-previous.x)*180/Math.PI;
 return [svg('polygon',{points:inheritance?'0,0 -16,-8 -16,8':'0,0 -8,-5 -16,0 -8,5',transform:`translate(${at.x} ${at.y}) rotate(${angle})`,
  'class-interlis-marker':true,'class-composition':aggregation==='COMPOSITION','data-aggregation':aggregation,fill:aggregation==='COMPOSITION'?'currentColor':'var(--diagram-background,white)'})];
}
interface LabelBox {x:number;y:number;width:number;height:number;}
class LabelPlacement {
 private readonly boxes=new Map<string,LabelBox>();
 clear():void {this.boxes.clear();}
 place(edge:InterlisEdge,key:string,point:Point,text:string,offset:number):Point {
  const overlaps=(a:LabelBox,b:LabelBox)=>a.x<b.x+b.width+4&&a.x+a.width+4>b.x&&a.y<b.y+b.height+4&&a.y+a.height+4>b.y;
  const obstacles:LabelBox[]=[];
  for(const element of edge.root.index.all())if(element instanceof InterlisNode){let x=element.position.x,y=element.position.y;let parent=element.parent;while(parent instanceof InterlisNode){x+=parent.position.x;y+=parent.position.y;parent=parent.parent;}obstacles.push({x,y,width:element.size.width,height:element.type==='node:container'?30:element.size.height});}
  const id=edge.id+':'+key,width=[...text].length*6.7,height=14;
  let chosen={x:point.x-width/2,y:point.y+offset-height,width,height};
  const shifts=[0,-22,22,-44,44,-66,66,-88,88,-110,110];let found=false;
  for(const dx of [0,-40,40,-80,80,-140,140]){for(const dy of shifts){const candidate={x:point.x-width/2+dx,y:point.y+offset-height+dy,width,height};if(!obstacles.some(box=>overlaps(candidate,box))&&![...this.boxes].some(([other,box])=>other!==id&&overlaps(candidate,box))){chosen=candidate;found=true;break;}}if(found)break;}
  this.boxes.set(id,chosen);return {x:chosen.x+width/2,y:chosen.y+height};
 }
}
@injectable()
class InterlisEdgeView extends PolylineEdgeView {
 @inject(LABELS) private labels!:LabelPlacement;
 protected override renderAdditionals(edge:InterlisEdge,route:Point[]):VNode[] {
  if(route.length<2)return [];
  const data=edge.data,center=pointAt(route,.5),first=pointAt(route,.16),last=pointAt(route,.84);
  return [
   ...marker(route[0],route[1],data.sourceEnd.aggregation),
   ...marker(route[route.length-1],route[route.length-2],data.targetEnd.aggregation,data.kind==='INHERITANCE'),
   ...(data.label&&data.label!==data.sourceEnd.roleName&&data.label!==data.targetEnd.roleName?[svg('text',{x:center.x,y:center.y-10,'text-anchor':'middle','class-interlis-edge-label':true,'data-element-id':edge.id},data.label)]:[]),
   ...[[data.sourceEnd,first,-8],[data.targetEnd,last,16]].flatMap(([end,at,offset])=>{
    const value=endLabel(end as DiagramEdgeDto['sourceEnd']),point=this.labels.place(edge,end===data.sourceEnd?'source':'target',at as Point,value,Number(offset));
    return value?[svg('text',{x:point.x,y:point.y,'text-anchor':'middle','class-interlis-end-label':true,'data-element-id':edge.id},value)]:[];
   })];
 }
 override render(edge:Readonly<InterlisEdge>,context:RenderingContext,args?:Parameters<PolylineEdgeView['render']>[2]):VNode|undefined {
  const result=super.render(edge,context,args);if(result){result.data??={};result.data.attrs={...result.data.attrs,'data-element-id':edge.id,'data-edge-id':edge.id,'data-source':edge.sourceId,'data-target':edge.targetId};}return result;
 }
}
@injectable()
class InteriorPolylineRouter extends PolylineEdgeRouter {
 override createRoutingHandles(edge:SRoutableElementImpl):void {super.createRoutingHandles(edge);edge.removeAll(child=>child instanceof SRoutingHandleImpl&&(child.kind==='source'||child.kind==='target'));}
 override applyReconnect():void {throw new Error('Semantic reconnect is unavailable');}
}
@injectable()
class InteriorOrthogonalRouter extends ManhattanEdgeRouter {
 override createRoutingHandles(edge:SRoutableElementImpl):void {super.createRoutingHandles(edge);edge.removeAll(child=>child instanceof SRoutingHandleImpl&&(child.kind==='source'||child.kind==='target'));}
 override applyReconnect():void {throw new Error('Semantic reconnect is unavailable');}
}
@injectable()
class InteriorHandleView extends SRoutingHandleView {
 override render(handle:Readonly<SRoutingHandleImpl>,context:RenderingContext,args?:Parameters<SRoutingHandleView['render']>[2]):VNode {
  const result=super.render(handle,context,args);result.data??={};result.data.attrs={...result.data.attrs,'data-handle-kind':handle.kind,'data-edge-id':handle.parent.id};return result;
 }
}
@injectable()
class DiagramMouseListener extends MouseListener {
 @inject(CALLBACKS) private callbacks!:DiagramCallbacks;
 private start?:Point;
 private lastDrag=0;
 override mouseDown(_target:SModelElementImpl,event:MouseEvent):Action[] {this.start={x:event.clientX,y:event.clientY};return [];}
 override mouseUp(target:SModelElementImpl,event:MouseEvent):Action[] {
  const moved=this.start&&Math.hypot(event.clientX-this.start.x,event.clientY-this.start.y)>3;
  if(moved)this.lastDrag=Date.now();else if(this.callbacks.singleClickNavigation)this.navigate(target);
  return [];
 }
 override doubleClick(target:SModelElementImpl):Action[] {if(Date.now()-this.lastDrag>300)this.navigate(target);return [];}
 private navigate(target:SModelElementImpl):void {
  if(target instanceof InterlisMember)this.callbacks.navigate(target.elementId);
  else if(target instanceof InterlisNode||target instanceof InterlisEdge)this.callbacks.navigate(target.id);
 }
}
@injectable()
class ObservedCommandStack extends CommandStack {
 @inject(OBSERVER) private observe!:(root:SModelRootImpl)=>void;
 override update(model:SModelRootImpl,cause?:Action):void {this.observe(model);super.update(model,cause);}
}
function collectState(root:SModelRootImpl):DiagramViewState {
 const state:DiagramViewState={positions:{},routes:{}};
 for(const element of root.index.all()){
  if(element instanceof InterlisNode)state.positions[element.id]={...element.position};
  if(element instanceof InterlisEdge)state.routes[element.id]=element.routingPoints.map(point=>({...point}));
 }
 return state;
}
function build(document:DiagramDocumentDto,layout:LayoutDiagram,state:DiagramViewState,settings:DiagramSettings,generation:number):SModelRoot {
 const geometry=new Map(layout.nodes.map(node=>[node.id,node])),semantic=new Map<string,DiagramNodeDto|DiagramContainerDto>([...document.containers,...document.nodes].map(node=>[node.id,node]));
 const nodes=new Map<string,SNode>();
 for(const node of layout.nodes){
  const value=semantic.get(node.id);if(!value)throw new Error('Unknown layout node');const p=presentation(value,settings),isContainer='parentId' in value;
  const parent=node.parentId?geometry.get(node.parentId):undefined;
  const position=state.positions[node.id]??{x:node.x-(parent?.x??0),y:node.y-(parent?.y??0)};
  const data:NodeData={label:value.label,canonicalName:value.canonicalName,kind:value.kind,titleLines:p.titleLines,abstract:'modifiers' in value&&!!value.modifiers?.includes('ABSTRACT'),status:'status' in value?value.status:'RESOLVED'};
  nodes.set(node.id,{type:isContainer?'node:container':'node:interlis',id:node.id,position:{...position},size:{width:node.width,height:node.height},data,
   children:isContainer?[]:p.lines.map((line,index)=>({type:'label:member',id:line.id+':line:'+index,elementId:line.id,compartmentKind:line.kind,text:line.text,position:{x:12,y:line.y},size:{width:node.width-24,height:18}}))} as SNode);
 }
 // Reconcile only presentation geometry. Stable identities preserve manual moves; new
 // siblings are placed without covering the retained view, and containers grow to fit.
 const groups=new Map<string,SNode[]>();
 for(const item of layout.nodes){const group=groups.get(item.parentId??'')??[];group.push(nodes.get(item.id)!);groups.set(item.parentId??'',group);}
 const overlaps=(a:SNode,b:SNode)=>a.position!.x<b.position!.x+b.size!.width+18&&a.position!.x+a.size!.width+18>b.position!.x&&a.position!.y<b.position!.y+b.size!.height+18&&a.position!.y+a.size!.height+18>b.position!.y;
 for(const siblings of groups.values()){const placed=siblings.filter(node=>!!state.positions[node.id]);for(const node of siblings.filter(node=>!state.positions[node.id])){if(placed.some(other=>overlaps(node,other)))node.position={x:Math.max(0,...placed.map(other=>other.position!.x+other.size!.width))+36,y:node.position!.y};placed.push(node);}}
 const grow=(node:SNode,depth=0):void=>{if(depth>64)throw new Error('Container nesting limit exceeded');for(const item of layout.nodes.filter(item=>item.parentId===node.id)){const child=nodes.get(item.id)!;grow(child,depth+1);node.size={width:Math.max(node.size!.width,child.position!.x+child.size!.width+24),height:Math.max(node.size!.height,child.position!.y+child.size!.height+24)};}};
 for(const item of layout.nodes.filter(item=>!item.parentId))grow(nodes.get(item.id)!);
 const roots:SNode[]=[];for(const node of layout.nodes){const current=nodes.get(node.id)!;const parent=node.parentId?nodes.get(node.parentId):undefined;if(parent)parent.children!.push(current);else roots.push(current);}
 const byEdge=new Map(layout.edges.map(edge=>[edge.id,edge]));
 const edges=document.edges.map(edge=>{
  const route=byEdge.get(edge.id);if(!route)throw new Error('Layout omitted an edge');
  const routingPoints=state.routes[edge.id]??route.points.slice(1,-1).map(point=>({...point}));
  return {type:'edge:interlis',id:edge.id,sourceId:edge.source.elementId,targetId:edge.target.elementId,routerKind:settings.edgeRouting==='ORTHOGONAL'?'manhattan':'polyline',routingPoints,data:edge,children:[]} as SEdge;
 });
 return {type:'graph',id:'interlis-diagram',generation,children:[...roots,...edges],zoom:1,scroll:{x:0,y:0}} as SModelRoot;
}

/** Real Sprotty viewer. Semantic inputs stay frozen; Sprotty commands modify only its separate view model. */
export class DiagramCanvas {
 private readonly container=new Container();
 private readonly source:LocalModelSource;
 private readonly dispatcher:IActionDispatcher;
 private readonly worker:LayoutServicePort;
 private document?:DiagramDocumentDto;
 private root?:SModelRootImpl;
 private view?:DiagramView;
 private state:DiagramViewState={positions:{},routes:{}};
 private status:DiagramStatus='EMPTY';
 private generation=0;
 private sessionKey='';
 private closed=false;
 constructor(private readonly element:HTMLElement,private readonly callbacks:DiagramCallbacks,workerUrl:URL|string,layoutService?:LayoutServicePort) {
  if(!element.id)element.id='interlis-view-'+crypto.randomUUID();this.worker=layoutService??new ElkLayoutService(workerUrl);
  const viewer=document.createElement('div');viewer.id=element.id+'-viewer';viewer.className='interlis-viewer';element.append(viewer);
  loadDefaultModules(this.container,{exclude:[edgeEditModule,labelEditModule,labelEditUiModule]});
  const labels=new LabelPlacement();
  const observe=(root:SModelRootImpl)=>{
   labels.clear();
   if(this.closed||!(root instanceof InterlisGraph)||root.generation!==this.generation)return;this.root=root;
   for(const edge of root.index.all())if(edge instanceof InterlisEdge){const semantic=this.document?.edges.find(e=>e.id===edge.id);if(semantic&&(edge.sourceId!==semantic.source.elementId||edge.targetId!==semantic.target.elementId))throw new Error('Semantic endpoints changed');}
   this.state=collectState(root);callbacks.changed?.(this.getViewState());
  };
  const events:DiagramCallbacks={...callbacks,navigate:id=>{if(this.status==='FRESH'||this.status==='PARTIAL')callbacks.navigate(id);}};
  this.container.load(new ContainerModule((bind,unbind,isBound,rebind)=>{
   const context={bind,unbind,isBound,rebind};bind(LABELS).toConstantValue(labels);bind(OBSERVER).toConstantValue(observe);bind(CALLBACKS).toConstantValue(events);
   rebind(TYPES.ICommandStack).to(ObservedCommandStack).inSingletonScope();bind(TYPES.ModelSource).to(LocalModelSource).inSingletonScope();
   configureCommand(context,SwitchEditModeCommand);
   configureModelElement(context,'graph',InterlisGraph,SGraphView);
   configureModelElement(context,'node:interlis',InterlisNode,InterlisNodeView);
   configureModelElement(context,'node:container',InterlisNode,InterlisContainerView);
   configureModelElement(context,'label:member',InterlisMember,InterlisMemberView);
   configureModelElement(context,'edge:interlis',InterlisEdge,InterlisEdgeView);
   configureModelElement(context,'routing-point',SRoutingHandleImpl,InteriorHandleView);
   configureModelElement(context,'volatile-routing-point',SRoutingHandleImpl,InteriorHandleView);
   unbind(TYPES.IEdgeRouter);bind(TYPES.IEdgeRouter).to(InteriorPolylineRouter).inSingletonScope();bind(TYPES.IEdgeRouter).to(InteriorOrthogonalRouter).inSingletonScope();
   bind(TYPES.MouseListener).to(DiagramMouseListener).inSingletonScope();
   configureViewerOptions(context,{baseDiv:viewer.id,baseClass:'interlis-viewer',hiddenDiv:viewer.id+'-hidden',hiddenClass:'interlis-hidden',popupDiv:viewer.id+'-popup',needsClientLayout:false,needsServerLayout:false,zoomLimits:{min:.08,max:4}});
   configureCommandStackOptions(context,{defaultDuration:0,undoHistoryLimit:100});
  }));
  this.source=this.container.get<LocalModelSource>(TYPES.ModelSource);this.dispatcher=this.container.get<IActionDispatcher>(TYPES.IActionDispatcher);
 }
 async accept(raw:unknown,sessionKey:string,settings:DiagramSettings=defaultInteractiveSettings):Promise<boolean> {
  if(this.closed)return false;
  if(settings.edgeRouting==='SPLINES'||settings.renderingTarget!=='STANDARD')throw new Error('This interactive viewer currently supports standard orthogonal and polyline routing');
  const view=adapt(raw);const document=view.document;
  if(document.nodes.length>500||document.edges.length>1000||document.nodes.reduce((sum,node)=>sum+node.compartments.reduce((sum,c)=>sum+c.members.length,0),0)>10000)throw new Error('Diagram exceeds presentation limits');
  if(this.sessionKey!==sessionKey){this.state={positions:{},routes:{}};this.sessionKey=sessionKey;}
  const mark=++this.generation;this.setStatus('LOADING','Updating diagram…');
  const projection=legacyProjection(view);for(const node of projection.nodes){const value=[...document.nodes,...document.containers].find(value=>value.id===node.id);if(value){const p=presentation(value,settings);node.layoutSize=p;node.headerHeight=p.titleLines.length*18+22;}}
  try {
   const layout=await this.worker.layout(projection,settings);if(this.closed||mark!==this.generation)return false;
   for(const node of layout.nodes)if(![node.x,node.y,node.width,node.height].every(Number.isFinite)||node.width<=0||node.height<=0)throw new Error('Invalid layout bounds');
   for(const edge of layout.edges)if(!edge.points.every(point=>Number.isFinite(point.x)&&Number.isFinite(point.y)))throw new Error('Invalid edge geometry');
   const first=!this.document;this.document=document;this.view=view;
   const model=build(document,layout,this.state,settings,mark);
   if(first)await this.source.setModel(model);else await this.source.updateModel(model);
   if(this.closed||mark!==this.generation)return false;
   if(first){await new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve())));if(this.closed||mark!==this.generation)return false;await this.fit();}
   this.setStatus(document.completeness==='COMPLETE'?'FRESH':'PARTIAL',document.completeness==='COMPLETE'?'Diagram is current':document.completeness==='LIMITED'?'Limited diagram — narrow the selection or increase the explicit projection budget':'Partial diagram — check Problems');
   return true;
  }catch(error){if(mark!==this.generation||this.closed)return false;this.setStatus('ERROR',error instanceof Error?error.message:'Diagram failed');throw error;}
 }
 invalidate(message='Source changed — updating diagram'):void {if(!this.closed){this.generation++;this.worker.cancel();this.setStatus('STALE',message);}}
 private setStatus(status:DiagramStatus,message:string):void {this.status=status;this.element.dataset.status=status;this.callbacks.status?.(status,message);}
 getViewState():DiagramViewState {return structuredClone(this.state);}
 getDocument():DiagramDocumentDto|undefined {return this.document;}
 async fit():Promise<void> {await this.dispatcher.dispatch(FitToScreenAction.create([],{padding:30,maxZoom:1,animate:false}));}
 async select(ids:string[]):Promise<void> {await this.dispatcher.dispatch(SelectAction.create({selectedElementsIDs:ids,deselectedElementsIDs:[]}));}
 async undo():Promise<void> {await this.dispatcher.dispatch(UndoAction.create());}
 async redo():Promise<void> {await this.dispatcher.dispatch(RedoAction.create());}
 dispose():void {if(this.closed)return;this.closed=true;this.generation++;this.worker.close();this.container.unbindAll();this.element.replaceChildren();this.document=undefined;this.root=undefined;this.view=undefined;this.state={positions:{},routes:{}};}
}
export {adapt,diagramAdapter,defaultDiagramSettings} from './adapter';
export type {DiagramDocumentDto} from './types';
export {ElkLayoutService} from './elk-layout-service';
export type {LayoutServicePort} from './elk-layout-service';
