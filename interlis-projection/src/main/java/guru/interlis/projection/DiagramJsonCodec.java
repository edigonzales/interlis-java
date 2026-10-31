package guru.interlis.projection;

import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit deterministic JSON encoding of DiagramDocument 1.0. */
public final class DiagramJsonCodec {
    /**
     * Executes {@code encode}.
     *
     * @param document the {@code document} argument
     *
     * @return the result
     */
    public String encode(DiagramDocument document) {
        Objects.requireNonNull(document, "document");
        StringBuilder out = new StringBuilder();
        out.append('{');
        field(out, "schemaVersion", string(document.schemaVersion())).append(',');
        field(out, "snapshotId", string(document.snapshotId().value())).append(',');
        out.append("\"sourceRevisions\":{");
        boolean first = true;
        for (Map.Entry<guru.interlis.source.SourceId, guru.interlis.source.SourceRevision> entry
                : document.sourceRevisions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(
                        guru.interlis.source.SourceId::value))).toList()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(string(entry.getKey().value())).append(':')
                    .append(string(Long.toString(entry.getValue().value())));
        }
        out.append("},");
        field(out, "inputKind", string(document.inputKind().name())).append(',');
        field(out, "completeness", string(document.completeness().name())).append(',');
        out.append("\"selection\":[");
        for (int i = 0; i < document.selection().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            SymbolRef ref = document.selection().get(i);
            out.append('{');
            field(out, "snapshot", string(ref.snapshot().value())).append(',');
            field(out, "symbol", string(ref.symbol().value()));
            out.append('}');
        }
        out.append("],");
        field(out, "optionsFingerprint", string(document.optionsFingerprint())).append(',');
        out.append("\"containers\":[");
        for (int i = 0; i < document.containers().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            container(out, document.containers().get(i));
        }
        out.append("],\"nodes\":[");
        for (int i = 0; i < document.nodes().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            node(out, document.nodes().get(i));
        }
        out.append("],\"edges\":[");
        for (int i = 0; i < document.edges().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            edge(out, document.edges().get(i));
        }
        out.append("],\"notices\":[");
        for (int i = 0; i < document.notices().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            notice(out, document.notices().get(i));
        }
        out.append("]}");
        return out.toString();
    }

    private void container(StringBuilder out, DiagramContainer container) {
        out.append('{');
        field(out, "id", string(container.id())).append(',');
        field(out, "kind", string(container.kind().name())).append(',');
        optionalField(out, "parentId", container.parentId().map(DiagramJsonCodec::string)).append(',');
        field(out, "label", string(container.label())).append(',');
        field(out, "canonicalName", string(container.canonicalName())).append(',');
        optionalSymbol(out, "symbol", container.symbol()).append(',');
        optionalAnchor(out, "anchor", container.anchor());
        out.append('}');
    }

    private void node(StringBuilder out, DiagramNode node) {
        out.append('{');
        field(out, "id", string(node.id())).append(',');
        field(out, "kind", string(node.kind().name())).append(',');
        field(out, "containerId", string(node.containerId())).append(',');
        field(out, "label", string(node.label())).append(',');
        field(out, "canonicalName", string(node.canonicalName())).append(',');
        field(out, "status", string(node.status().name())).append(',');
        out.append("\"modifiers\":[");
        var modifiers=node.modifiers().stream().map(Enum::name).sorted().toList();
        for(int i=0;i<modifiers.size();i++) { if(i>0)out.append(',');out.append(string(modifiers.get(i))); }
        out.append("],");
        optionalSymbol(out, "symbol", node.symbol()).append(',');
        out.append("\"compartments\":[");
        for (int i = 0; i < node.compartments().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            compartment(out, node.compartments().get(i));
        }
        out.append("],");
        optionalAnchor(out, "anchor", node.anchor());
        out.append('}');
    }

    private void compartment(StringBuilder out, DiagramCompartment compartment) {
        out.append('{');
        field(out, "id", string(compartment.id())).append(',');
        field(out, "kind", string(compartment.kind().name())).append(',');
        out.append("\"members\":[");
        for (int i = 0; i < compartment.members().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            member(out, compartment.members().get(i));
        }
        out.append("]}");
    }

    private void member(StringBuilder out, DiagramMember member) {
        out.append('{');
        field(out, "id", string(member.id())).append(',');
        field(out, "kind", string(member.kind().name())).append(',');
        field(out, "label", string(member.label())).append(',');
        optionalField(out, "typeLabel", member.typeLabel().map(DiagramJsonCodec::string)).append(',');
        optionalField(out, "cardinality", member.cardinality().map(DiagramJsonCodec::string)).append(',');
        field(out, "declaredSymbol", string(member.declaredSymbol().value())).append(',');
        field(out, "effectiveOwner", string(member.effectiveOwner().value())).append(',');
        field(out, "inherited", Boolean.toString(member.inherited())).append(',');
        field(out, "overridden", Boolean.toString(member.overridden())).append(',');
        optionalAnchor(out, "anchor", member.anchor());
        out.append('}');
    }

    private void edge(StringBuilder out, DiagramEdge edge) {
        out.append('{');
        field(out, "id", string(edge.id())).append(',');
        field(out, "kind", string(edge.kind().name())).append(',');
        endpoint(out, "source", edge.source()).append(',');
        endpoint(out, "target", edge.target()).append(',');
        optionalSymbol(out, "origin", edge.origin()).append(',');
        edgeEnd(out, "sourceEnd", edge.sourceEnd()).append(',');
        edgeEnd(out, "targetEnd", edge.targetEnd()).append(',');
        field(out, "label", string(edge.label())).append(',');
        optionalAnchor(out, "anchor", edge.anchor());
        out.append('}');
    }

    private StringBuilder endpoint(StringBuilder out, String name, DiagramEndpoint endpoint) {
        out.append('"').append(name).append("\":{");
        field(out, "elementId", string(endpoint.elementId())).append(',');
        optionalField(out, "memberId", endpoint.memberId().map(DiagramJsonCodec::string));
        out.append('}');
        return out;
    }

    private StringBuilder edgeEnd(StringBuilder out, String name, DiagramEdgeEnd end) {
        out.append('"').append(name).append("\":{");
        optionalField(out, "roleName", end.roleName().map(DiagramJsonCodec::string)).append(',');
        optionalField(out, "cardinality",
                end.cardinality().map(c -> cardinality(c))).append(',');
        field(out, "aggregation", string(end.aggregation().name()));
        out.append('}');
        return out;
    }

    private static String cardinality(guru.interlis.model.Cardinality cardinality) {
        String upper = cardinality.upper() instanceof guru.interlis.model.CardinalityUpper.Finite finite
                ? finite.value().toString() : "*";
        return "{\"minimum\":\"" + cardinality.lower() + "\",\"maximum\":"
                + (cardinality.upper() instanceof guru.interlis.model.CardinalityUpper.Finite finite
                ? "{\"kind\":\"bounded\",\"value\":\"" + finite.value() + "\"}" : "{\"kind\":\"unbounded\"}")
                + "}";
    }

    private void notice(StringBuilder out, ProjectionNotice notice) {
        out.append('{');
        field(out, "code", string(notice.code().name())).append(',');
        field(out, "severity", string(notice.severity().name())).append(',');
        field(out, "messageKey", string(notice.messageKey())).append(',');
        out.append("\"arguments\":[");
        for (int i = 0; i < notice.arguments().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(string(notice.arguments().get(i)));
        }
        out.append("],");
        optionalSymbol(out, "symbol", notice.symbol()).append(',');
        optionalRange(out, "range", notice.range());
        out.append('}');
    }

    private StringBuilder optionalSymbol(StringBuilder out, String name, Optional<SymbolRef> symbol) {
        out.append('"').append(name).append("\":");
        if (symbol.isEmpty()) {
            out.append("null");
            return out;
        }
        out.append('{');
        field(out, "snapshot", string(symbol.get().snapshot().value())).append(',');
        field(out, "symbol", string(symbol.get().symbol().value()));
        out.append('}');
        return out;
    }

    private void optionalAnchor(StringBuilder out, String name, Optional<SourceAnchor> anchor) {
        out.append('"').append(name).append("\":");
        if (anchor.isEmpty()) {
            out.append("null");
            return;
        }
        SourceAnchor value = anchor.get();
        out.append('{');
        field(out, "snapshotId", string(value.snapshotId().value())).append(',');
        range(out, "nameRange", value.nameRange()).append(',');
        out.append("\"declarationRange\":");
        if (value.declarationRange().isEmpty()) {
            out.append("null");
        } else {
            rangeObject(out, value.declarationRange().get());
        }
        out.append('}');
    }

    private void optionalRange(StringBuilder out, String name, Optional<guru.interlis.source.SourceRange> range) {
        out.append('"').append(name).append("\":");
        if (range.isEmpty()) {
            out.append("null");
        } else {
            rangeObject(out, range.get());
        }
    }

    private StringBuilder range(StringBuilder out, String name, guru.interlis.source.SourceRange range) {
        out.append('"').append(name).append("\":");
        rangeObject(out, range);
        return out;
    }

    private void rangeObject(StringBuilder out, guru.interlis.source.SourceRange range) {
        out.append('{');
        field(out, "source", string(range.source().value())).append(',');
        field(out, "revision", string(Long.toString(range.revision().value()))).append(',');
        field(out, "startOffset", Integer.toString(range.startOffset())).append(',');
        field(out, "endOffset", Integer.toString(range.endOffset()));
        out.append('}');
    }

    private StringBuilder optionalField(StringBuilder out, String name, Optional<String> value) {
        out.append('"').append(name).append("\":");
        return out.append(value.orElse("null"));
    }

    private StringBuilder field(StringBuilder out, String name, String value) {
        out.append('"').append(name).append("\":").append(value);
        return out;
    }

    private static String string(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
