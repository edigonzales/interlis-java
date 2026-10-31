package guru.interlis.projection;

import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceRange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Structural and snapshot validation of DiagramDocument 1.0; no source I/O. */
public final class DiagramValidator {
    /** Returns ordered errors without mutating the document. Empty means valid. */
    public List<String> validate(DiagramDocument document) {
        Objects.requireNonNull(document, "document");
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Map<String, DiagramContainer> containers = new HashMap<>();
        Map<String, Set<String>> endpointMembers = new HashMap<>();
        for (var ref : document.selection()) checkSymbol(document, ref, "selection", errors);
        for (var container : document.containers()) {
            addId(ids, container.id(), errors);
            containers.put(container.id(), container);
            endpointMembers.put(container.id(), Set.of());
            container.symbol().ifPresent(ref -> checkSymbol(document, ref, container.id(), errors));
            checkAnchor(document, container.anchor(), container.id(), errors);
        }
        for (var container : document.containers()) {
            Set<String> path = new HashSet<>();
            var current = Optional.of(container.id());
            while (current.isPresent()) {
                String id = current.orElseThrow();
                if (!path.add(id)) { errors.add("container cycle " + id); break; }
                var parent = containers.get(id);
                if (parent == null) { errors.add("missing parent " + id); break; }
                current = parent.parentId();
            }
        }
        for (var node : document.nodes()) {
            addId(ids, node.id(), errors);
            if (!containers.containsKey(node.containerId())) errors.add("missing container " + node.containerId());
            node.symbol().ifPresent(ref -> checkSymbol(document, ref, node.id(), errors));
            checkAnchor(document, node.anchor(), node.id(), errors);
            Set<String> members = new HashSet<>();
            for (var compartment : node.compartments()) {
                addId(ids, compartment.id(), errors);
                for (var member : compartment.members()) {
                    addId(ids, member.id(), errors); members.add(member.id());
                    checkAnchor(document, member.anchor(), member.id(), errors);
                }
            }
            endpointMembers.put(node.id(), members);
        }
        for (var edge : document.edges()) {
            addId(ids, edge.id(), errors);
            for (var end : List.of(edge.source(), edge.target())) {
                var members = endpointMembers.get(end.elementId());
                if (members == null) errors.add("missing endpoint " + end.elementId());
                else if (end.memberId().isPresent() && !members.contains(end.memberId().orElseThrow()))
                    errors.add("missing endpoint member " + end.memberId().orElseThrow());
            }
            edge.origin().ifPresent(ref -> checkSymbol(document, ref, edge.id(), errors));
            checkAnchor(document, edge.anchor(), edge.id(), errors);
        }
        for (var notice : document.notices()) {
            notice.symbol().ifPresent(ref -> checkSymbol(document, ref, "notice", errors));
            notice.range().ifPresent(range -> checkRange(document, range, "notice", errors));
        }
        return List.copyOf(errors);
    }
    private static void addId(Set<String> ids, String id, List<String> errors) {
        if (id.isBlank() || !ids.add(id)) errors.add("duplicate or blank id " + id);
    }
    private static void checkSymbol(DiagramDocument document, SymbolRef ref, String id, List<String> errors) {
        if (!ref.snapshot().equals(document.snapshotId())) errors.add("symbol snapshot mismatch " + id);
    }
    private static void checkAnchor(DiagramDocument document, Optional<SourceAnchor> anchor, String id, List<String> errors) {
        anchor.ifPresent(value -> {
            if (!value.snapshotId().equals(document.snapshotId())) errors.add("anchor snapshot mismatch " + id);
            checkRange(document, value.nameRange(), id, errors);
            value.declarationRange().ifPresent(range -> checkRange(document, range, id, errors));
        });
    }
    private static void checkRange(DiagramDocument document, SourceRange range, String id, List<String> errors) {
        if (!range.revision().equals(document.sourceRevisions().get(range.source()))) errors.add("anchor source revision mismatch " + id);
    }
}
