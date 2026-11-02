package guru.interlis.projection;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.Cardinality;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.SemanticGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lossless presentation reduction of plain, resolved binary associations; never binds or guesses a target. */
final class CompactAssociationProjection {
    private CompactAssociationProjection() { }
    static void apply(SemanticGraph graph,Map<String,DiagramNode> nodes,List<DiagramEdge> edges,CancellationToken token) {
        for(var node:List.copyOf(nodes.values())) {
            token.throwIfCancellationRequested();
            if(node.kind()!=DiagramNodeKind.ASSOCIATION||node.status()!=DiagramNodeStatus.RESOLVED||node.symbol().isEmpty())continue;
            var symbol=graph.symbol(node.symbol().orElseThrow().symbol()).orElse(null);
            if(!(symbol instanceof AssociationSymbol association)||association.roles().size()!=2
                    ||!association.attributes().isEmpty()||!association.constraints().isEmpty()
                    ||association.baseAssociation().isPresent()||association.derivedFrom().isPresent()
                    ||!association.header().modifiers().isEmpty()||!association.cardinality().equals(Cardinality.exactly(1)))continue;
            var roleEdges=new ArrayList<DiagramEdge>();
            for(var roleId:association.roles()) {
                var candidate=graph.symbol(roleId).orElse(null);
                if(!(candidate instanceof RoleSymbol role)||role.targets().size()!=1
                        ||!role.targets().getFirst().restrictions().isEmpty()||role.targetRoots().size()!=1
                        ||role.ordered()||role.external()||role.hiding()||role.derivation().isPresent())break;
                var matches=edges.stream().filter(edge->edge.kind()==DiagramEdge.EdgeKind.ASSOCIATION_ROLE
                        &&edge.source().elementId().equals(node.id())&&edge.origin().map(ref->ref.symbol().equals(roleId)).orElse(false)).toList();
                if(matches.size()!=1)break;
                var target=nodes.get(matches.getFirst().target().elementId());
                if(target==null||target.status()!=DiagramNodeStatus.RESOLVED)break;
                roleEdges.add(matches.getFirst());
            }
            if(roleEdges.size()!=2||edges.stream().anyMatch(edge->!roleEdges.contains(edge)
                    &&(edge.source().elementId().equals(node.id())||edge.target().elementId().equals(node.id()))))continue;
            var first=roleEdges.get(0);var second=roleEdges.get(1);
            var compact=new DiagramEdge("edge-"+association.id().value()+"-binary",DiagramEdge.EdgeKind.ASSOCIATION,
                    first.target(),second.target(),node.symbol(),first.targetEnd(),second.targetEnd(),node.label(),node.anchor());
            // Two edges become one, so no new edge-budget admission is required.
            edges.removeAll(roleEdges);nodes.remove(node.id());edges.add(compact);
        }
    }
}
