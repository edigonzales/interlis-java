import type {Point} from 'sprotty-protocol';

/** Exact encoding for local SVG IDs; no collisions from punctuation or Unicode. */
export function svgId(value:string):string {let result='';for(let i=0;i<value.length;i++)result+=value.charCodeAt(i).toString(16).padStart(4,'0');return 'interlis-'+result;}
/** Visible escape notation prevents bidi/control characters from spoofing labels. */
export function displayText(value:string):string {return value.replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f\u202a-\u202e\u2066-\u2069]/g,c=>'⟪U+'+c.charCodeAt(0).toString(16).toUpperCase().padStart(4,'0')+'⟫');}
export function curvePath(points:readonly Point[]):string {
 if(!points.length)return '';let result=`M ${points[0].x},${points[0].y}`;
 for(let i=0;i<points.length-1;i++){const a=points[Math.max(0,i-1)],b=points[i],c=points[i+1],d=points[Math.min(points.length-1,i+2)];result+=` C ${b.x+(c.x-a.x)/6},${b.y+(c.y-a.y)/6} ${c.x-(d.x-b.x)/6},${c.y-(d.y-b.y)/6} ${c.x},${c.y}`;}return result;
}
export function curveSamples(points:readonly Point[]):Point[] {
 const result:Point[]=[];if(points.length)result.push({...points[0]});
 for(let i=0;i<points.length-1;i++){const a=points[Math.max(0,i-1)],b=points[i],c=points[i+1],d=points[Math.min(points.length-1,i+2)],p={x:b.x+(c.x-a.x)/6,y:b.y+(c.y-a.y)/6},q={x:c.x-(d.x-b.x)/6,y:c.y-(d.y-b.y)/6};for(let step=1;step<=12;step++){const t=step/12,u=1-t;result.push({x:u*u*u*b.x+3*u*u*t*p.x+3*u*t*t*q.x+t*t*t*c.x,y:u*u*u*b.y+3*u*u*t*p.y+3*u*t*t*q.y+t*t*t*c.y});}}return result;
}
interface Segment {edge:string;a:Point;b:Point;minX:number;maxX:number;minY:number;maxY:number;}
/** Bounded geometric decoration only. Dense views keep all semantic edges. */
export function crossingGaps(routes:ReadonlyMap<string,readonly Point[]>,maxChecks=200_000):{gaps:Map<string,Point[]>;limited:boolean} {
 const segments:Segment[]=[];for(const [edge,points]of routes)for(let i=1;i<points.length;i++){const a=points[i-1],b=points[i];if(Math.hypot(a.x-b.x,a.y-b.y)>.01)segments.push({edge,a,b,minX:Math.min(a.x,b.x),maxX:Math.max(a.x,b.x),minY:Math.min(a.y,b.y),maxY:Math.max(a.y,b.y)});}
 segments.sort((a,b)=>a.minX-b.minX);const gaps=new Map<string,Point[]>();let checks=0;
 for(let i=0;i<segments.length;i++)for(let j=i+1;j<segments.length&&segments[j].minX<=segments[i].maxX;j++){
  if(++checks>maxChecks)return {gaps,limited:true};const a=segments[i],b=segments[j];if(a.edge===b.edge||a.maxY<b.minY||a.minY>b.maxY)continue;
  const ax=a.b.x-a.a.x,ay=a.b.y-a.a.y,bx=b.b.x-b.a.x,by=b.b.y-b.a.y,den=ax*by-ay*bx;if(Math.abs(den)<1e-8)continue;
  const dx=b.a.x-a.a.x,dy=b.a.y-a.a.y,t=(dx*by-dy*bx)/den,u=(dx*ay-dy*ax)/den;
  if(t<=0||t>=1||u<=0||u>=1||Math.min(t,1-t)*Math.hypot(ax,ay)<4||Math.min(u,1-u)*Math.hypot(bx,by)<4)continue;
  const point={x:a.a.x+t*ax,y:a.a.y+t*ay},id=a.edge.localeCompare(b.edge)>0?a.edge:b.edge,values=gaps.get(id)??[];
  if(values.length<64&&!values.some(p=>Math.hypot(p.x-point.x,p.y-point.y)<6))values.push(point);gaps.set(id,values);
 }
 return {gaps,limited:false};
}

export interface Rectangle {x:number;y:number;width:number;height:number;}
/** Bounds of painted graph leaves, excluding handles, hit surfaces and definitions. */
export function paintedBounds(svg:SVGSVGElement):Rectangle {
 const root=svg.querySelector('g');if(!root)throw Error('Diagram has no rendered graph');const matrix=root.getCTM();if(!matrix)throw Error('Diagram is not visible');const inverse=matrix.inverse();let minX=Infinity,minY=Infinity,maxX=-Infinity,maxY=-Infinity;
 for(const leaf of svg.querySelectorAll<SVGGraphicsElement>('rect,circle,text,path,polygon,polyline,line')){
  if(leaf.closest('defs,mask,clipPath,[data-handle-kind]')||leaf.classList.contains('interlis-edge-hit')||leaf.classList.contains('interlis-export-background'))continue;
  const style=getComputedStyle(leaf);if(style.display==='none'||style.visibility==='hidden')continue;
  const box=leaf.getBBox(),ctm=leaf.getCTM();if(!ctm)continue;const transform=inverse.multiply(ctm);
  for(const [x,y]of [[box.x,box.y],[box.x+box.width,box.y],[box.x,box.y+box.height],[box.x+box.width,box.y+box.height]]){const p=new DOMPoint(x,y).matrixTransform(transform);minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);}
 }
 if(![minX,minY,maxX,maxY].every(Number.isFinite))return {x:0,y:0,width:1,height:1};return {x:minX,y:minY,width:Math.max(1,maxX-minX),height:Math.max(1,maxY-minY)};
}

/** The convex hull of cubic control points contains every painted curve point. */
export function curveEnvelope(points:readonly Point[]):Point[] {const result=points.map(p=>({...p}));for(let i=0;i<points.length-1;i++){const a=points[Math.max(0,i-1)],b=points[i],c=points[i+1],d=points[Math.min(points.length-1,i+2)];result.push({x:b.x+(c.x-a.x)/6,y:b.y+(c.y-a.y)/6},{x:c.x-(d.x-b.x)/6,y:c.y-(d.y-b.y)/6});}return result;}
