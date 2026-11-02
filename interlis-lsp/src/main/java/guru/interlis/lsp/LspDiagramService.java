package guru.interlis.lsp;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.projection.*;
import guru.interlis.source.SourceId;
import java.util.*;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

/** Read-only projection and navigation registry. Captures enter through the owner; projection runs on bounded query workers. */
final class LspDiagramService {
    record Context(AnalysisSnapshot analysis,SourceId source,String uri,int version,String workspaceRevision,Map<String,Integer> versions) { }
    private record Published(DiagramResponse response,Map<String,SourceAnchor> anchors) { }
    private final String instance=UUID.randomUUID().toString();
    private final LinkedHashMap<String,Published> published=new LinkedHashMap<>(16,.75f,true);
    String serverInstanceId() {return instance;}
    DiagramResponse project(DiagramRequest request,Context context,CancellationToken token) {
        if(request.protocolVersion()!=1||request.requestId()==null||request.requestId().isBlank()||request.requestId().length()>128)
            throw LspFailures.error(ResponseErrorCode.InvalidParams,"unsupported diagram protocol or request identity");
        var selected=new ArrayList<SymbolRef>();
        if(request.symbolIds()==null||request.symbolIds().isEmpty()) {
            for(var symbol:context.analysis().semanticGraph().declarationsInOrder())if(symbol instanceof ModelSymbol model
                    && model.nameRange().map(range->range.source().equals(context.source())).orElse(false))selected.add(new SymbolRef(context.analysis().snapshotId(),model.id()));
        } else {
            if(request.symbolIds().size()>500||new HashSet<>(request.symbolIds()).size()!=request.symbolIds().size())throw LspFailures.error(ResponseErrorCode.InvalidParams,"invalid diagram selection");
            for(String id:request.symbolIds()) {if(id==null||id.isBlank()||id.length()>4096)throw LspFailures.error(ResponseErrorCode.InvalidParams,"invalid diagram symbol identity");selected.add(new SymbolRef(context.analysis().snapshotId(),new SymbolId(id)));}
        }
        if(selected.isEmpty())throw LspFailures.error(ResponseErrorCode.RequestFailed,"No model declaration is available in this source");
        var options=DiagramOptionsDecoder.decode(request.options(),new DiagramSelection(selected));
        var outcome=DiagramProjectors.create().project(new DiagramInput.FromAnalysis(context.analysis()),options,token);
        if(outcome instanceof DiagramProjectionOutcome.Rejected rejected)throw LspFailures.error(ResponseErrorCode.RequestFailed,"Diagram projection rejected: "+rejected.reasons());
        var document=((DiagramProjectionOutcome.Produced)outcome).document();token.throwIfCancellationRequested();
        long members=document.nodes().stream().flatMap(node->node.compartments().stream()).mapToLong(compartment->compartment.members().size()).sum();
        if(document.containers().size()>500||members>10_000||new DiagramJsonCodec().encode(document).length()>4_000_000)
            throw LspFailures.error(ResponseErrorCode.RequestFailed,"Diagram exceeds host presentation limits; narrow the selection");
        var response=new DiagramResponse(1,instance,request.requestId(),UUID.randomUUID().toString(),context.uri(),context.version(),context.workspaceRevision(),document.snapshotId().value(),context.versions(),selected.stream().map(ref->ref.symbol().value()).toList(),document);
        var anchors=new LinkedHashMap<String,SourceAnchor>();
        document.containers().forEach(value->value.anchor().ifPresent(anchor->anchors.put(value.id(),anchor)));
        document.nodes().forEach(value->{value.anchor().ifPresent(anchor->anchors.put(value.id(),anchor));value.compartments().forEach(c->c.members().forEach(member->member.anchor().ifPresent(anchor->anchors.put(member.id(),anchor))));});
        document.edges().forEach(value->value.anchor().ifPresent(anchor->anchors.put(value.id(),anchor)));
        synchronized(published) {published.put(response.diagramId(),new Published(response,Map.copyOf(anchors)));while(published.size()>16)published.remove(published.keySet().iterator().next());}
        return response;
    }
    SourceAnchor navigation(DiagramNavigationParams request,Context current) {
        if(request.protocolVersion()!=1||request.diagramId()==null||request.diagramId().length()>128||request.elementId()==null||request.elementId().length()>4096)
            throw LspFailures.error(ResponseErrorCode.InvalidParams,"invalid diagram navigation identity");
        var previous=verify(request.protocolVersion(),request.serverInstanceId(),request.diagramId(),request.snapshotId(),current);
        var anchor=previous.anchors().get(request.elementId());if(anchor==null)throw LspFailures.error(ResponseErrorCode.RequestFailed,"Diagram element has no source anchor");
        return anchor;
    }
    private Published verify(int protocol,String server,String diagram,String snapshot,Context current) {
        if(protocol!=1||diagram==null||diagram.isBlank()||diagram.length()>128||snapshot==null||snapshot.length()>128)
            throw LspFailures.error(ResponseErrorCode.InvalidParams,"invalid diagram selection identity");
        Published previous;synchronized(published) {previous=published.get(diagram);}
        if(previous==null||!instance.equals(server)||!previous.response().snapshotId().equals(snapshot)
                ||!current.analysis().snapshotId().value().equals(snapshot)||!previous.response().rootUri().equals(current.uri())
                ||previous.response().documentVersion()!=current.version()||!previous.response().workspaceRevision().equals(current.workspaceRevision()))
            throw LspFailures.error(ResponseErrorCode.ContentModified,"Diagram source or server changed; refresh before navigating");
        return previous;
    }
    DiagramSelectionResult selection(DiagramSelectionParams request,Context current,SymbolRef selected) {
        var previous=verify(request.protocolVersion(),request.serverInstanceId(),request.diagramId(),request.snapshotId(),current);
        var ids=new LinkedHashSet<String>();
        if(selected!=null) {
            if(!selected.snapshot().value().equals(request.snapshotId()))throw LspFailures.error(ResponseErrorCode.ContentModified,"Source selection snapshot changed");
            var document=previous.response().document();
            document.containers().forEach(c->{if(c.symbol().filter(selected::equals).isPresent())ids.add(c.id());});
            document.nodes().forEach(n->{if(n.symbol().filter(selected::equals).isPresent())ids.add(n.id());n.compartments().forEach(c->c.members().forEach(m->{if(m.declaredSymbol().equals(selected.symbol()))ids.add(m.id());}));});
            document.edges().forEach(e->{if(e.origin().filter(selected::equals).isPresent())ids.add(e.id());});
        }
        return new DiagramSelectionResult(1,instance,request.diagramId(),request.snapshotId(),current.uri(),current.version(),
                request.sourceDocument().getUri(),request.sourceDocument().getVersion(),List.copyOf(ids),selected==null?List.of():List.of(selected.symbol().value()));
    }
    void close() {synchronized(published) {published.clear();}}
}
