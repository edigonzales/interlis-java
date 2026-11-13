package guru.interlis.documentation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;

import java.util.*;

/** Pure projection of the gate-accepted schema. No syntax, compiler invocation or host IO. */
public final class DocumentationProjector {
    public DocumentationDocument project(
            CompiledSchema schema, DocumentationOptions options, CancellationToken token) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(options);
        Objects.requireNonNull(token);
        var graph = schema.semanticGraph();
        var declarations = graph.declarationsInOrder();
        var children = new LinkedHashMap<SymbolId, List<Symbol>>();
        for (var declaration : declarations) {
            token.throwIfCancellationRequested();
            declaration
                    .owner()
                    .ifPresent(
                            owner ->
                                    children.computeIfAbsent(owner, key -> new ArrayList<>())
                                            .add(declaration));
        }
        var selected =
                options.roots().isEmpty()
                        ? schema.rootModels().stream().map(SymbolRef::symbol).toList()
                        : options.roots();
        for (var id : selected)
            if (!schema.models().stream().anyMatch(r -> r.symbol().equals(id)))
                throw new IllegalArgumentException("documentation root is not a checked model");
        var text = new SemanticDescriptions(graph, token);
        var sections = new ArrayList<DocumentationDocument.Section>();
        int count = 0;
        long characters = 0;
        for (var symbol : declarations) {
            token.throwIfCancellationRequested();
            if (!(symbol.origin() instanceof Origin.DeclaredOrigin)
                    || !belongs(graph, symbol, selected)) continue;
            if (!Set.of(
                            SymbolKind.MODEL,
                            SymbolKind.TOPIC,
                            SymbolKind.CLASS,
                            SymbolKind.STRUCTURE,
                            SymbolKind.ASSOCIATION,
                            SymbolKind.VIEW,
                            SymbolKind.GRAPHIC,
                            SymbolKind.DOMAIN,
                            SymbolKind.UNIT,
                            SymbolKind.FUNCTION)
                    .contains(symbol.kind())) continue;
            var members = new ArrayList<DocumentationDocument.Member>();
            var inherited = new ArrayList<DocumentationDocument.Member>();
            var effective = graph.effectiveMembers(symbol.id());
            var ids = new LinkedHashSet<SymbolId>();
            for (var m : effective) ids.add(m.declaredSymbol());
            for (var s : children.getOrDefault(symbol.id(), List.of()))
                if (Set.of(
                                SymbolKind.ATTRIBUTE,
                                SymbolKind.ROLE,
                                SymbolKind.PARAMETER,
                                SymbolKind.ENUM_ELEMENT)
                        .contains(s.kind())) ids.add(s.id());
            for (var id : ids) {
                token.throwIfCancellationRequested();
                var member = graph.symbol(id).orElseThrow();
                var owner = member.owner().flatMap(graph::symbol).orElse(symbol);
                boolean isInherited = !owner.id().equals(symbol.id());
                var m =
                        new DocumentationDocument.Member(
                                id.value(),
                                member.name().value(),
                                member.kind().name(),
                                text.symbolType(member),
                                text.cardinality(member),
                                text.name(owner.id()),
                                member.header().documentation().text(),
                                text.references(member));
                characters +=
                        m.name().length()
                                + m.type().length()
                                + m.origin().length()
                                + m.description().length()
                                + m.references().stream().mapToInt(String::length).sum();
                if (characters > 4_000_000)
                    throw new IllegalArgumentException("documentation text budget exceeded");
                boolean inline =
                        options.inheritance() == DocumentationOptions.Inheritance.INLINE
                                || options.inheritance()
                                                == DocumentationOptions.Inheritance
                                                        .INLINE_ABSTRACT_ONLY
                                        && owner.header().has(Modifier.ABSTRACT);
                if (isInherited && !inline) inherited.add(m);
                else members.add(m);
                if (++count > options.maxEntries())
                    throw new IllegalArgumentException(
                            "documentation entry limit exceeded; narrow roots");
            }
            var constraints = new ArrayList<String>();
            for (var c : children.getOrDefault(symbol.id(), List.of()))
                if (c instanceof ConstraintSymbol constraint) {
                    token.throwIfCancellationRequested();
                    String description = text.constraint(constraint.definition());
                    characters += description.length();
                    if (characters > 4_000_000)
                        throw new IllegalArgumentException("documentation text budget exceeded");
                    constraints.add(description);
                }
            characters +=
                    symbol.header().documentation().text().length()
                            + text.name(symbol.id()).length()
                            + text.symbolType(symbol).length();
            if (characters > 4_000_000)
                throw new IllegalArgumentException("documentation text budget exceeded");
            sections.add(
                    new DocumentationDocument.Section(
                            symbol.id().value(),
                            text.name(symbol.id()),
                            symbol.kind().name(),
                            symbol.header().has(Modifier.ABSTRACT),
                            symbol.header().documentation().text(),
                            text.symbolType(symbol),
                            text.base(symbol),
                            members,
                            inherited,
                            constraints));
            if (++count > options.maxEntries())
                throw new IllegalArgumentException(
                        "documentation entry limit exceeded; narrow roots");
        }
        var revisions = new TreeMap<String, String>();
        schema.sourceRevisions()
                .forEach((k, v) -> revisions.put(k.value(), Long.toString(v.value())));
        return new DocumentationDocument(schema.snapshotId().value(), revisions, options, sections);
    }

    private static boolean belongs(SemanticGraph graph, Symbol s, List<SymbolId> roots) {
        var seen = new HashSet<SymbolId>();
        while (s != null && seen.add(s.id())) {
            if (roots.contains(s.id())) return true;
            s = s.owner().flatMap(graph::symbol).orElse(null);
        }
        return false;
    }
}
