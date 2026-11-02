import 'reflect-metadata';
import {Container,ContainerModule,inject,injectable} from 'inversify';
import {TYPES,loadDefaultModules,configureModelElement,configureViewerOptions,configureCommandStackOptions,
 configureCommand,edgeEditModule,labelEditModule,labelEditUiModule,SwitchEditModeCommand,
 LocalModelSource,SGraphImpl,SGraphView,SNodeImpl,SLabelImpl,SEdgeImpl,ShapeView,PolylineEdgeView,
 SRoutingHandleImpl,SRoutingHandleView,PolylineEdgeRouter,ManhattanEdgeRouter,DefaultAnchors,SRoutableElementImpl,
 MouseListener,SModelElementImpl,SModelRootImpl,CommandStack,ModelViewer,selectFeature,editFeature,deletableFeature,
 boundsFeature,hoverFeedbackFeature,svg,InitializeCanvasBoundsAction,EdgeRouterRegistry,type RenderingContext,type IActionDispatcher} from 'sprotty';
import {SetViewportAction,SelectAction,UndoAction,RedoAction,type Action,type SModelRoot,type SNode,type SEdge,type Point} from 'sprotty-protocol';
import type {VNode} from 'snabbdom';
import {adapt,legacyProjection,type DiagramView} from './adapter';
import {defaultDiagramSettings,type DiagramSettings,type LayoutDiagram} from './layout';
import type {DiagramDocumentDto,DiagramEdgeDto,DiagramNodeDto,DiagramContainerDto} from './types';
import {ElkLayoutService,type LayoutServicePort} from './elk-layout-service';
import {reconcileGeometry,presentationKeys} from './identity';
import {presentation,endLabel} from './presentation';
import {DiagramFilterController,type DiagramFilters} from './filters';
import {saveViewState,restoreViewState,validateViewSettings,type SavedViewState} from './view-state';
import {curvePath,curveSamples,curveEnvelope,crossingGaps,svgId,paintedBounds,displayText} from './geometry';
import {exportSvg,exportPng,type ExportScope,type SvgArtifact} from './export';

export const defaultInteractiveSettings:DiagramSettings=Object.freeze({...defaultDiagramSettings});
export type DiagramStatus='EMPTY'|'LOADING'|'FRESH'|'PARTIAL'|'STALE'|'ERROR';
export interface DiagramViewState {positions:Record<string,Point>;routes:Record<string,Point[]>;}
interface HistoryFrame {geometry:DiagramViewState;pins:string[];manualNodes:string[];manualRoutes:string[];}
export interface DiagramCallbacks {navigate:(id:string)=>void;status?:(status:DiagramStatus,message:string)=>void;changed?:(state:DiagramViewState)=>void;singleClickNavigation?:boolean;}
const OBSERVER=Symbol('InterlisViewObserver'),CALLBACKS=Symbol('InterlisViewCallbacks'),LABELS=Symbol('InterlisLabelPlacement');
interface NodeData {label:string;kind:string;canonicalName:string;titleLines:string[];abstract:boolean;status:string;}

@injectable()
class InterlisGraph extends SGraphImpl { generation=0; settings:DiagramSettings=defaultInteractiveSettings; filters:DiagramFilters=new DiagramFilterController().filters; pins:string[]=[]; }
/** Only native pointer/keyboard actions may acquire focus in an embedded viewer. */
@injectable()
class HostAwareModelViewer extends ModelViewer {
 protected override restoreFocus(_focus:boolean):void {
  // The host can activate a source editor while this iframe retains activeElement.
  // A background projection/selection patch must never focus the SVG again.
 }
}
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
   'class-abstract':data.abstract,'data-element-id':node.id,'data-symbol-id':node.id,'data-kind':data.kind,'data-label':data.label,'data-canonical-name':data.canonicalName,'data-pinned':(node.root as InterlisGraph).pins.includes(node.id),
   'class-frame-hidden':container&&!(data.kind==='MODEL'?(node.root as InterlisGraph).filters.modelFrames:(node.root as InterlisGraph).filters.topicFrames),'class-deemphasized':data.abstract&&(node.root as InterlisGraph).settings.deemphasizeAbstractTypes},
   svg('title',{},displayText(data.canonicalName)),
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
 gaps?:Map<string,Point[]>;
 clear():void {this.boxes.clear();this.gaps=undefined;}
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
 @inject(EdgeRouterRegistry) private routers!:EdgeRouterRegistry;
 override renderLine(edge:InterlisEdge,route:Point[],context:RenderingContext,args?:Parameters<PolylineEdgeView['renderLine']>[3]):VNode {
  const line=super.renderLine(edge,route,context,args);line.data??={};line.data.attrs={...line.data.attrs,'vector-effect':'non-scaling-stroke',class:'interlis-edge-line',...((edge.root as InterlisGraph).settings.edgeRouting==='SPLINES'?{d:curvePath(route)}:{})};
  let mask:VNode|undefined;const settings=(edge.root as InterlisGraph).settings;
  if(settings.edgeCrossingStyle==='GAPS'){
   if(!this.labels.gaps){const routes=new Map<string,Point[]>();for(const value of edge.root.index.all())if(value instanceof InterlisEdge){const points=this.routers.get(value.routerKind).route(value);routes.set(value.id,settings.edgeRouting==='SPLINES'?curveSamples(points):points);}this.labels.gaps=crossingGaps(routes).gaps;}
   const gaps=this.labels.gaps.get(edge.id);if(gaps?.length){const id=svgId('gaps:'+edge.id),extent=settings.edgeRouting==='SPLINES'?curveEnvelope(route):route,xs=extent.map(p=>p.x),ys=extent.map(p=>p.y),x=Math.min(...xs)-40,y=Math.min(...ys)-40;line.data.attrs.mask='url(#'+id+')';mask=svg('defs',{},svg('mask',{id,maskUnits:'userSpaceOnUse',x,y,width:Math.max(...xs)-x+40,height:Math.max(...ys)-y+40},svg('rect',{x,y,width:Math.max(...xs)-x+40,height:Math.max(...ys)-y+40,fill:'white'}),...gaps.map(p=>svg('circle',{cx:p.x,cy:p.y,r:5,fill:'black'}))));}
  }
  return svg('g',{},...(mask?[mask]:[]),svg('path',{d:line.data.attrs['d'],'class-interlis-edge-hit':true,'vector-effect':'non-scaling-stroke'}),line);
 }
 protected override renderAdditionals(edge:InterlisEdge,route:Point[]):VNode[] {
  if(route.length<2)return [];
  const settings=(edge.root as InterlisGraph).settings,data=edge.data,labelRoute=settings.edgeRouting==='SPLINES'?curveSamples(route):route,center=this.labels.place(edge,'association',pointAt(labelRoute,.5),displayText(data.label),-10),first=pointAt(labelRoute,.16),last=pointAt(labelRoute,.84);
  return [
   ...marker(route[0],route[1],data.sourceEnd.aggregation),
   ...marker(route[route.length-1],route[route.length-2],data.targetEnd.aggregation,data.kind==='INHERITANCE'),
   ...(settings.showAssociationNames&&(data.kind!=='ASSOCIATION_ROLE'||(edge.root as InterlisGraph).filters.roles)&&data.label&&data.label!==data.sourceEnd.roleName&&data.label!==data.targetEnd.roleName?[svg('text',{x:center.x,y:center.y,'text-anchor':'middle','class-interlis-edge-label':true,'data-element-id':edge.id},displayText(data.label))]:[]),
   ...[[data.sourceEnd,first,-8],[data.targetEnd,last,16]].flatMap(([end,at,offset])=>{
    const e=end as DiagramEdgeDto['sourceEnd'],value=endLabel({...e,cardinality:settings.showRoleCardinalities?e.cardinality:null}),point=this.labels.place(edge,end===data.sourceEnd?'source':'target',at as Point,value,Number(offset));
    return value?[svg('text',{x:point.x,y:point.y,'text-anchor':'middle','class-interlis-end-label':true,'data-element-id':edge.id},displayText(value))]:[];
   })];
 }
 override isVisible():boolean {return true;}
 override render(edge:Readonly<InterlisEdge>,context:RenderingContext,args?:Parameters<PolylineEdgeView['render']>[2]):VNode|undefined {
  const result=super.render(edge,context,args);if(result){result.data??={};result.data.attrs={...result.data.attrs,'data-element-id':edge.id,'data-edge-id':edge.id,'data-source':edge.sourceId,'data-target':edge.targetId};}return result;
 }
}
@injectable()
class InteriorPolylineRouter extends PolylineEdgeRouter {
 override route(edge:SRoutableElementImpl) {
  if(edge.source&&edge.source===edge.target&&edge.routingPoints.length===0)
   edge.routingPoints=this.calculateDefaultCorners(edge,new DefaultAnchors(edge.source,edge.parent,'source'),new DefaultAnchors(edge.target,edge.parent,'target'),this.getOptions(edge));
  return super.route(edge);
 }
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
 const state:DiagramViewState={positions:Object.create(null),routes:Object.create(null)};
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
  // ELK's self-edge ports are distinct, while Sprotty anchors attach to shapes.
  // Reusing those port bends can fold both ends onto the same anchor. Let the
  // native router construct its self loop until the user supplies route bends.
  const routingPoints=state.routes[edge.id]??(edge.source.elementId===edge.target.elementId?[]:route.points.slice(1,-1).map(point=>({...point})));
  return {type:'edge:interlis',id:edge.id,sourceId:edge.source.elementId,targetId:edge.target.elementId,routerKind:settings.edgeRouting==='ORTHOGONAL'?'manhattan':'polyline',routingPoints,data:edge,children:[]} as SEdge;
 });
 return {type:'graph',id:'interlis-diagram',generation,children:[...edges,...roots],zoom:1,scroll:{x:0,y:0}} as SModelRoot;
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
 private current=false;
 private optionsRevision=0;
 private settings:DiagramSettings={...defaultInteractiveSettings};
 readonly filters=new DiagramFilterController();
 private pins=new Set<string>();
 private manualNodes=new Set<string>();
 private manualRoutes=new Set<string>();
 private history:HistoryFrame[]=[];
 private future:HistoryFrame[]=[];
 private dragStart?:HistoryFrame;
 private rendered?:DiagramDocumentDto;
 private resize?:ResizeObserver;
 private cleanup:Array<()=>void>=[];
 private revision=0;
 private frame():HistoryFrame {return {geometry:this.getViewState(),pins:[...this.pins],manualNodes:[...this.manualNodes],manualRoutes:[...this.manualRoutes]};}
 private trimHistory():void {while(this.history.length+this.future.length>40||JSON.stringify([this.history,this.future]).length>8_000_000){if(this.history.length)this.history.shift();else if(this.future.length)this.future.shift();else break;}}
 private remember(frame=this.frame()):void {this.history.push(frame);this.future=[];this.trimHistory();}
 private applyFrame(frame:HistoryFrame):void {this.state=frame.geometry;this.pins=new Set(frame.pins);this.manualNodes=new Set(frame.manualNodes);this.manualRoutes=new Set(frame.manualRoutes);}
 get elementId():string {return this.element.id;}
 getViewport():{zoom:number;x:number;y:number} {return this.viewport();}
 private viewport():{zoom:number;x:number;y:number} {const root=this.root as InterlisGraph|undefined;return {zoom:root?.zoom??1,x:root?.scroll.x??0,y:root?.scroll.y??0};}
 constructor(private readonly element:HTMLElement,private readonly callbacks:DiagramCallbacks,workerUrl:URL|string,layoutService?:LayoutServicePort) {
  if(!element.id)element.id='interlis-view-'+crypto.randomUUID();this.worker=layoutService??new ElkLayoutService(workerUrl);
  const viewer=document.createElement('div');viewer.id=element.id+'-viewer';viewer.className='interlis-viewer';element.append(viewer);
  loadDefaultModules(this.container,{exclude:[edgeEditModule,labelEditModule,labelEditUiModule]});
  const labels=new LabelPlacement();
  const observe=(root:SModelRootImpl)=>{
   labels.clear();
   if(this.closed||!(root instanceof InterlisGraph)||root.generation!==this.generation)return;this.root=root;
   if(root instanceof InterlisGraph){const nodes=[...root.index.all()].filter((e):e is InterlisNode=>e instanceof InterlisNode);if(nodes.length){const points=nodes.map(node=>{let x=node.position.x,y=node.position.y,parent=node.parent;while(parent instanceof InterlisNode){x+=parent.position.x;y+=parent.position.y;parent=parent.parent;}return {x,y,width:node.size.width,height:node.size.height};});const zoom=Math.max(.08,Math.min(4,root.zoom));root.zoom=zoom;root.scroll={x:Math.max(Math.min(...points.map(p=>p.x))-this.element.clientWidth/zoom+64/zoom,Math.min(Math.max(...points.map(p=>p.x+p.width))-64/zoom,root.scroll.x)),y:Math.max(Math.min(...points.map(p=>p.y))-this.element.clientHeight/zoom+64/zoom,Math.min(Math.max(...points.map(p=>p.y+p.height))-64/zoom,root.scroll.y))};}}
   for(const edge of root.index.all())if(edge instanceof InterlisEdge){const semantic=this.document?.edges.find(e=>e.id===edge.id);if(semantic&&(edge.sourceId!==semantic.source.elementId||edge.targetId!==semantic.target.elementId))throw new Error('Semantic endpoints changed');}
   const visible=collectState(root);this.state={positions:{...this.state.positions,...visible.positions},routes:{...this.state.routes,...visible.routes}};this.revision++;callbacks.changed?.(this.getViewState());
  };
  const events:DiagramCallbacks={...callbacks,navigate:id=>{if(this.status==='FRESH'||this.status==='PARTIAL')callbacks.navigate(id);}};
  this.container.load(new ContainerModule((bind,unbind,isBound,rebind)=>{
   const context={bind,unbind,isBound,rebind};bind(LABELS).toConstantValue(labels);bind(OBSERVER).toConstantValue(observe);bind(CALLBACKS).toConstantValue(events);
   rebind(TYPES.ICommandStack).to(ObservedCommandStack).inSingletonScope();rebind(ModelViewer).to(HostAwareModelViewer).inSingletonScope();bind(TYPES.ModelSource).to(LocalModelSource).inSingletonScope();
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
  const on=<K extends keyof HTMLElementEventMap>(type:K,listener:(event:HTMLElementEventMap[K])=>void,options?:AddEventListenerOptions)=>{element.addEventListener(type,listener,options);this.cleanup.push(()=>element.removeEventListener(type,listener,options));};
  on('pointerdown',()=>{this.dragStart=this.frame();});
  on('pointerup',()=>{const before=this.dragStart;this.dragStart=undefined;if(!before)return;requestAnimationFrame(()=>{if(this.closed)return;let changed=false;for(const [id,p]of Object.entries(this.state.positions))if(before.geometry.positions[id]&&JSON.stringify(p)!==JSON.stringify(before.geometry.positions[id])){this.manualNodes.add(id);changed=true;}for(const [id,p]of Object.entries(this.state.routes))if(before.geometry.routes[id]&&JSON.stringify(p)!==JSON.stringify(before.geometry.routes[id])){this.manualRoutes.add(id);changed=true;}if(changed){this.remember(before);}});});
  on('wheel',event=>{event.preventDefault();event.stopImmediatePropagation();void this.zoom(Math.exp(-Math.max(-200,Math.min(200,event.deltaY))*.003),event.clientX,event.clientY);},{capture:true,passive:false});
  on('keydown',event=>{if(!(event.target instanceof SVGElement))return;const key=event.key.toLowerCase();if((event.ctrlKey||event.metaKey)&&key==='z'){event.preventDefault();event.stopImmediatePropagation();void(event.shiftKey?this.redo():this.undo());}else if((event.ctrlKey||event.metaKey)&&key==='y'){event.preventDefault();event.stopImmediatePropagation();void this.redo();}else if(['arrowleft','arrowright','arrowup','arrowdown'].includes(key)){event.preventDefault();event.stopImmediatePropagation();void this.pan((key==='arrowright'?40:key==='arrowleft'?-40:0),(key==='arrowdown'?40:key==='arrowup'?-40:0));}else if(key==='home'){event.preventDefault();void this.fit();}},{capture:true});
  this.resize=new ResizeObserver(()=>{const box=viewer.getBoundingClientRect();void this.dispatcher.dispatch(InitializeCanvasBoundsAction.create({x:box.x,y:box.y,width:box.width,height:box.height}));});this.resize.observe(viewer);
 }
 async accept(raw:unknown,sessionKey:string,settings:DiagramSettings=defaultInteractiveSettings,local=false):Promise<boolean> {
  if(this.closed)return false;
  this.settings=validateViewSettings(settings);
  if(!local)this.current=false;const optionsRevision=this.optionsRevision;
  if(!local){this.history=[];this.future=[];}
  const full=adapt(raw).document;
  if(this.document&&this.document.snapshotId!==full.snapshotId&&this.sessionKey===sessionKey){
   const old=presentationKeys(this.document),next=presentationKeys(full);
   const remap=(set:Set<string>,before:Map<string,string>,after:Map<string,string>)=>{const keys=new Set([...set].flatMap(id=>before.has(id)?[before.get(id)!]:[]));return new Set([...after].filter(([,key])=>keys.has(key)).map(([id])=>id));};
   this.pins=remap(this.pins,old.nodes,next.nodes);this.manualNodes=remap(this.manualNodes,old.nodes,next.nodes);this.manualRoutes=remap(this.manualRoutes,old.edges,next.edges);this.filters.hiddenIds=remap(this.filters.hiddenIds,old.nodes,next.nodes);
  }
  const document=this.filters.visible(full);const view=adapt(document);this.rendered=document;
  if(document.nodes.length>500||document.edges.length>1000||document.nodes.reduce((sum,node)=>sum+node.compartments.reduce((sum,c)=>sum+c.members.length,0),0)>10000)throw new Error('Diagram exceeds presentation limits');
  if(this.sessionKey!==sessionKey){this.state={positions:{},routes:{}};this.history=[];this.future=[];this.pins.clear();this.manualNodes.clear();this.manualRoutes.clear();this.sessionKey=sessionKey;this.document=undefined;this.root=undefined;this.view=undefined;}
  const mark=++this.generation;this.setStatus('LOADING','Updating diagram…');
  const projection=legacyProjection(view);for(const node of projection.nodes){const value=[...document.nodes,...document.containers].find(value=>value.id===node.id);if(value){const p=presentation(value,settings);node.layoutSize=p;node.headerHeight=p.titleLines.length*18+22;}}
  try {
   const layout=await this.worker.layout(projection,{...settings,renderingTarget:"STANDARD"});if(this.closed||mark!==this.generation)return false;if(optionsRevision!==this.optionsRevision)return this.accept(full,sessionKey,this.settings,local);
   for(const node of layout.nodes)if(![node.x,node.y,node.width,node.height].every(Number.isFinite)||node.width<=0||node.height<=0)throw new Error('Invalid layout bounds');
   for(const edge of layout.edges)if(!edge.points.every(point=>Number.isFinite(point.x)&&Number.isFinite(point.y)))throw new Error('Invalid edge geometry');
   const first=!this.document;const reconciled=reconcileGeometry(this.document,full,this.state);
   const model=build(document,layout,reconciled,settings,mark);Object.assign(model,{settings,filters:this.filters.filters,pins:[...this.pins]});this.state=reconciled;
   if(!first&&this.root instanceof InterlisGraph)Object.assign(model,{zoom:this.root.zoom,scroll:{...this.root.scroll}});
   this.document=full;this.view=view;
   // Native UpdateModelCommand is undoable. A source/projection response must be a
   // history barrier: only layout commands on the current immutable document may undo.
   await this.source.setModel(model);
   if(this.closed||mark!==this.generation)return false;
   if(first){await new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve())));if(this.closed||mark!==this.generation)return false;await this.fit();}
   this.current=true;
   if(!document.nodes.length&&!document.containers.length){this.setStatus('EMPTY','No visible elements — change or reset filters');return true;}
   this.setStatus(document.completeness==='COMPLETE'?'FRESH':'PARTIAL',document.completeness==='COMPLETE'?'Diagram is current':document.completeness==='LIMITED'?'Limited diagram — narrow the selection or increase the explicit projection budget':'Partial diagram — check Problems');
   return true;
  }catch(error){if(mark!==this.generation||this.closed)return false;this.setStatus('ERROR',error instanceof Error?error.message:'Diagram failed');throw error;}
 }
 invalidate(message='Source changed — updating diagram'):void {if(!this.closed){this.current=false;this.generation++;this.worker.cancel();this.setStatus('STALE',message);}}
 private setStatus(status:DiagramStatus,message:string):void {this.status=status;this.element.dataset.status=status;this.callbacks.status?.(status,message);}
 getViewState():DiagramViewState {return structuredClone(this.state);}
 getDocument():DiagramDocumentDto|undefined {return this.document;}
 async fit():Promise<void> {
  const graph=this.element.querySelector('svg.sprotty-graph') as SVGSVGElement|null;
  if(!graph||this.closed)return;const bounds=paintedBounds(graph),width=this.element.clientWidth,height=this.element.clientHeight;
  if(width<=60||height<=60||bounds.width<=0||bounds.height<=0)return;
  const zoom=Math.max(.08,Math.min(1,(width-60)/bounds.width,(height-60)/bounds.height));
  await this.dispatcher.dispatch(SetViewportAction.create('interlis-diagram',{zoom,scroll:{x:bounds.x+bounds.width/2-width/(2*zoom),y:bounds.y+bounds.height/2-height/(2*zoom)}},{animate:false}));
 }
 async select(ids:string[]):Promise<void> {const wanted=new Set(ids),elements=this.root?[...this.root.index.all()]:[];const selected=elements.filter(e=>wanted.has(e instanceof InterlisMember?e.elementId:e.id)).map(e=>e.id),deselected=elements.filter(e=>(e instanceof InterlisNode||e instanceof InterlisEdge||e instanceof InterlisMember)&&e.selected&&!selected.includes(e.id)).map(e=>e.id);await this.dispatcher.dispatch(SelectAction.create({selectedElementsIDs:selected,deselectedElementsIDs:deselected}));}
 async undo():Promise<void> {const state=this.history.pop();if(state){this.future.push(this.frame());this.applyFrame(state);this.trimHistory();await this.repaint();}}
 async redo():Promise<void> {const state=this.future.pop();if(state){this.history.push(this.frame());this.applyFrame(state);this.trimHistory();await this.repaint();}}
 private async repaint():Promise<void> {if(this.document&&this.current)await this.accept(this.document,this.sessionKey,this.settings,true);}
 selectedSymbolIds():string[] {const ids=new Set(this.selectedIds()),document=this.document;if(!document)return [];return [...new Set([...document.containers,...document.nodes].flatMap(node=>ids.has(node.id)&&node.symbol?[node.symbol.symbol]:[]).concat(document.edges.flatMap(edge=>ids.has(edge.id)&&edge.origin?[edge.origin.symbol]:[])))];}
 getScrollBounds():{minX:number;maxX:number;minY:number;maxY:number} {const svg=this.element.querySelector<SVGSVGElement>('svg.sprotty-graph');if(!svg)return {minX:0,maxX:1,minY:0,maxY:1};const b=paintedBounds(svg),v=this.viewport();return {minX:b.x-this.element.clientWidth/v.zoom+64/v.zoom,maxX:b.x+b.width-64/v.zoom,minY:b.y-this.element.clientHeight/v.zoom+64/v.zoom,maxY:b.y+b.height-64/v.zoom};}
 getSettings():DiagramSettings {return {...this.settings};}
 async configure(settings:DiagramSettings):Promise<void> {this.settings=validateViewSettings(settings);this.optionsRevision++;await this.settle();}
 async filter(path:string,value:boolean):Promise<void> {this.filters.set(path,value);this.optionsRevision++;await this.repaint();}
 async filterAll(value:boolean):Promise<void> {this.filters.setAll(value);this.optionsRevision++;await this.repaint();}
 async resetFilters():Promise<void> {this.filters.reset();this.optionsRevision++;await this.repaint();}
 async hide(ids:string[],hidden=true):Promise<void> {this.optionsRevision++;for(const id of ids)if(hidden)this.filters.hiddenIds.add(id);else this.filters.hiddenIds.delete(id);await this.repaint();}
 selectedIds():string[] {return this.root?[...this.root.index.all()].filter(e=>(e instanceof InterlisNode||e instanceof InterlisEdge)&&e.selected).map(e=>e.id):[];}
 async pinSelected(value:boolean):Promise<void> {for(const id of this.selectedIds())if(this.state.positions[id]){if(value)this.pins.add(id);else this.pins.delete(id);}await this.repaint();}
 async settle():Promise<void> {this.remember();const visible=new Set([...this.rendered?.nodes??[],...this.rendered?.containers??[]].map(n=>n.id)),edges=new Set(this.rendered?.edges.map(e=>e.id));this.state={positions:Object.fromEntries(Object.entries(this.state.positions).filter(([id])=>this.pins.has(id)||this.manualNodes.has(id)||!visible.has(id))),routes:Object.fromEntries(Object.entries(this.state.routes).filter(([id])=>this.manualRoutes.has(id)||!edges.has(id)))};await this.repaint();}
 async resetLayout():Promise<void> {this.remember();this.state={positions:{},routes:{}};this.pins.clear();this.manualNodes.clear();this.manualRoutes.clear();await this.repaint();await this.fit();}
 async pan(dx:number,dy:number):Promise<void> {const v=this.viewport();await this.setViewport({...v,x:v.x+dx/v.zoom,y:v.y+dy/v.zoom});}
 async setViewport(v:{zoom:number;x:number;y:number}):Promise<void> {if(![v.zoom,v.x,v.y].every(Number.isFinite))return;const svg=this.element.querySelector<SVGSVGElement>('svg.sprotty-graph');if(!svg)return;const b=paintedBounds(svg),zoom=Math.max(.08,Math.min(4,v.zoom));const x=Math.max(b.x-this.element.clientWidth/zoom+64/zoom,Math.min(b.x+b.width-64/zoom,v.x)),y=Math.max(b.y-this.element.clientHeight/zoom+64/zoom,Math.min(b.y+b.height-64/zoom,v.y));await this.dispatcher.dispatch(SetViewportAction.create('interlis-diagram',{zoom,scroll:{x,y}},{animate:false}));}
 async zoom(factor:number,clientX?:number,clientY?:number):Promise<void> {const v=this.viewport(),r=this.element.getBoundingClientRect(),x=clientX===undefined?r.width/2:clientX-r.left,y=clientY===undefined?r.height/2:clientY-r.top,zoom=Math.max(.08,Math.min(4,v.zoom*factor));await this.setViewport({zoom,x:v.x+x/v.zoom-x/zoom,y:v.y+y/v.zoom-y/zoom});}
 async saveState():Promise<SavedViewState|undefined> {if(!this.document)return;return saveViewState(this.document,{geometry:this.state,pins:this.pins,manualNodes:this.manualNodes,manualRoutes:this.manualRoutes,hiddenNodes:this.filters.hiddenIds,filters:this.filters.filters,settings:this.settings,viewport:this.viewport()});}
 async restoreState(value:unknown):Promise<void> {if(!this.document)throw Error('No diagram for saved state');const document=this.document,generation=this.generation,state=await restoreViewState(document,value);if(this.closed||generation!==this.generation||this.document!==document)return;this.state=state.geometry;this.pins=state.pins;this.manualNodes=state.manualNodes;this.manualRoutes=state.manualRoutes;this.filters.hiddenIds=state.hiddenNodes;this.filters.filters=state.filters;this.settings=state.settings;await this.repaint();await this.setViewport(state.viewport);}
 exportSvg(scope:ExportScope):SvgArtifact {if(!this.current||!this.rendered||!['FRESH','PARTIAL','EMPTY'].includes(this.status))throw Error('Wait for a current diagram before exporting');return exportSvg(this.element,this.rendered,this.settings,scope,this.viewport());}
 async exportPng(scope:ExportScope,scale=1):Promise<{data:Uint8Array;width:number;height:number}> {const revision=this.revision,generation=this.generation,artifact=await exportPng(this.exportSvg(scope),scale);if(this.closed||generation!==this.generation||revision!==this.revision)throw Error('View changed during export; retry');return artifact;}
 dispose():void {if(this.closed)return;this.closed=true;this.generation++;this.resize?.disconnect();for(const cleanup of this.cleanup)cleanup();this.worker.close();this.container.unbindAll();this.element.replaceChildren();this.document=undefined;this.root=undefined;this.view=undefined;this.state={positions:{},routes:{}};}
}
export {adapt,diagramAdapter,defaultDiagramSettings} from './adapter';
export type {DiagramDocumentDto} from './types';
export {ElkLayoutService} from './elk-layout-service';
export type {LayoutServicePort} from './elk-layout-service';

export {mountDiagramControls} from "./controls";
export type {ControlHost} from "./controls";
