package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.model.DerivationKind;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Modifier;
import guru.interlis.model.Origin;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.AssociationDeclarationSyntax;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.ConstraintDeclarationSyntax;
import guru.interlis.syntax.DeclarationSyntax;
import guru.interlis.syntax.DomainDeclarationSyntax;
import guru.interlis.syntax.ExternalConstraintSyntax;
import guru.interlis.syntax.FunctionDeclarationSyntax;
import guru.interlis.syntax.EnumerationTypeSyntax;
import guru.interlis.syntax.ModelSyntax;
import guru.interlis.syntax.RecoveredSyntax;
import guru.interlis.syntax.RoleDeclarationSyntax;
import guru.interlis.syntax.StructureDeclarationSyntax;
import guru.interlis.syntax.SyntaxNode;
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.syntax.TopicSyntax;
import guru.interlis.syntax.UnitDeclarationSyntax;
import guru.interlis.syntax.ViewDeclarationSyntax;
import guru.interlis.syntax.TypeSyntax;
import guru.interlis.syntax.UnsupportedDeclarationSyntax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Indexes syntax declarations and reuses only unambiguous stable anchors. */
final class DeclarationIndexer {
    private int counter;
    private final Map<String, SymbolId> previous = new HashMap<>();
    private final Set<SymbolId> usedPrevious = new HashSet<>();
    private final Set<SymbolId> reservedPrevious = new HashSet<>();
    private final Set<SymbolId> assigned = new HashSet<>();
    private final Map<SymbolId, DeclInfo> byId = new LinkedHashMap<>();
    private final Map<SymbolId, List<DeclInfo>> children = new LinkedHashMap<>();
    private final Map<String, List<DeclInfo>> byQualifiedName = new LinkedHashMap<>();
    private final Map<SymbolId, SymbolId> modelOf = new LinkedHashMap<>();
    private final List<IndexedDeclarations.ModelEntry> modelEntries = new ArrayList<>();

    IndexedDeclarations index(List<SyntaxSnapshot> snapshots, AnalysisReuse reuse) {
        preparePrevious(reuse);
        List<SyntaxSnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort(Comparator.comparing(s -> s.source().id().value()));
        for (SyntaxSnapshot snapshot : sorted) {
            for (ModelSyntax model : snapshot.root().models()) {
                SourceId sourceId = snapshot.source().id();
                String name = model.name();
                DeclInfo declaration = create(SymbolKind.MODEL, name, Optional.empty(),
                        model.range(), Optional.of(model.range()), model.languageVersion(),
                        Set.of(), model, sourceId, name, Optional.empty(), model.range().startOffset());
                modelEntries.add(new IndexedDeclarations.ModelEntry(
                        new ModelDecl(model, snapshot.source()), declaration));
                register(snapshot.source().id(), declaration, null);
                walkModel(model, declaration, snapshot.source().id());
            }
        }
        return new IndexedDeclarations(modelEntries, byId, children, byQualifiedName, modelOf);
    }

    private void preparePrevious(AnalysisReuse reuse) {
        reuse.previousAnalysis().ifPresent(analysis -> {
            var graph = analysis.semanticGraph();
            Map<String, Integer> ordinals = new HashMap<>();
            List<Symbol> symbols = new ArrayList<>(graph.symbols());
            symbols.sort(Comparator.comparing(Symbol::declarationOrder)
                    .thenComparing(s -> s.id().value()));
            for (Symbol symbol : symbols) {
                if (!(symbol.origin() instanceof Origin.DeclaredOrigin declared)) {
                    continue;
                }
                String qname = graph.qualifiedNames().entrySet().stream()
                        .filter(e -> e.getValue().contains(symbol.id()))
                        .map(e -> e.getKey().toString())
                        .findFirst().orElse(symbol.name().value());
                String base = declared.range().source().value() + "|" + symbol.kind() + "|" + qname;
                int ordinal = ordinals.merge(base, 1, Integer::sum) - 1;
                previous.putIfAbsent(base + "|" + ordinal, symbol.id());
                reservedPrevious.add(symbol.id());
            }
        });
    }

    private void walkModel(ModelSyntax model, DeclInfo modelDecl, SourceId sourceId) {
        for (SyntaxNode member : model.members()) {
            walkMember(member, modelDecl, sourceId, model.languageVersion());
        }
    }

    private void walkMember(SyntaxNode node, DeclInfo owner, SourceId sourceId,
                            LanguageVersion language) {
        if (node instanceof RecoveredSyntax recovered) {
            for (SyntaxNode child : recovered.children()) {
                walkMember(child, owner, sourceId, language);
            }
            return;
        }
        if (node instanceof TopicSyntax topic) {
            DeclInfo declaration = create(SymbolKind.TOPIC, topic.name(), Optional.of(owner.id()),
                    topic.range(), Optional.empty(), language, topic.modifiers(), topic,
                    sourceId, owner.qualifiedName() + "." + topic.name(),
                    Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            for (SyntaxNode child : topic.members()) {
                walkMember(child, declaration, sourceId, language);
            }
            return;
        }
        if (node instanceof ClassDeclarationSyntax clazz) {
            DeclInfo declaration = create(SymbolKind.CLASS, clazz.name(), Optional.of(owner.id()),
                    clazz.range(), clazz.nameRange(), language, clazz.modifiers(), clazz,
                    sourceId, owner.qualifiedName() + "." + clazz.name(),
                    Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            for (SyntaxNode child : clazz.members()) {
                walkMember(child, declaration, sourceId, language);
            }
            return;
        }
        if (node instanceof StructureDeclarationSyntax structure) {
            DeclInfo declaration = create(SymbolKind.STRUCTURE, structure.name(),
                    Optional.of(owner.id()), structure.range(), structure.nameRange(), language,
                    structure.modifiers(), structure, sourceId,
                    owner.qualifiedName() + "." + structure.name(), Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            for (SyntaxNode child : structure.members()) {
                walkMember(child, declaration, sourceId, language);
            }
            return;
        }
        if (node instanceof AssociationDeclarationSyntax association) {
            DeclInfo declaration = create(SymbolKind.ASSOCIATION, association.name(),
                    Optional.of(owner.id()), association.range(), association.nameRange(), language,
                    association.modifiers(), association, sourceId,
                    owner.qualifiedName() + "." + association.name(), Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            for (RoleDeclarationSyntax role : association.roles()) {
                walkMember(role, declaration, sourceId, language);
            }
            for (AttributeDeclarationSyntax attribute : association.attributes()) {
                walkMember(attribute, declaration, sourceId, language);
            }
            for (ConstraintDeclarationSyntax constraint : association.constraints()) {
                walkMember(constraint, declaration, sourceId, language);
            }
            return;
        }
        if (node instanceof ViewDeclarationSyntax view) {
            DeclInfo declaration = create(SymbolKind.VIEW, view.name(), Optional.of(owner.id()),
                    view.range(), view.nameRange(), language, view.modifiers(), view, sourceId,
                    owner.qualifiedName() + "." + view.name(), Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            return;
        }
        if (node instanceof FunctionDeclarationSyntax function) {
            DeclInfo declaration = create(SymbolKind.FUNCTION, function.name(),
                    Optional.of(owner.id()), function.range(), function.nameRange(), language,
                    Set.of(), function, sourceId,
                    owner.qualifiedName() + "." + function.name(), Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            return;
        }
        if (node instanceof UnitDeclarationSyntax unit) {
            DeclInfo declaration = create(SymbolKind.UNIT, unit.name(), Optional.of(owner.id()),
                    unit.range(), unit.nameRange(), language,
                    unit.abstractUnit() ? Set.of(Modifier.ABSTRACT) : Set.of(), unit,
                    sourceId, owner.qualifiedName() + "." + unit.name(),
                    Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            return;
        }
        if (node instanceof DomainDeclarationSyntax domain) {
            DeclInfo declaration = create(SymbolKind.DOMAIN, domain.name(), Optional.of(owner.id()),
                    domain.range(), domain.nameRange(), language, domain.modifiers(), domain,
                    sourceId, owner.qualifiedName() + "." + domain.name(), Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            if (domain.type() instanceof EnumerationTypeSyntax enumeration) {
                int ordinal = 0;
                for (EnumerationTypeSyntax.EnumerationValueSyntax value : enumeration.values()) {
                    DeclInfo element = create(SymbolKind.ENUM_ELEMENT, value.path(),
                            Optional.of(declaration.id()), value.range(), Optional.of(value.range()),
                            language, Set.of(), domain, sourceId,
                            declaration.qualifiedName() + "." + value.path(), Optional.empty(),
                            ordinal++);
                    register(sourceId, element, declaration.id());
                }
            }
            for (ConstraintDeclarationSyntax constraint : domain.constraints()) {
                walkMember(constraint, declaration, sourceId, language);
            }
            return;
        }
        if (node instanceof AttributeDeclarationSyntax attribute) {
            DeclInfo declaration = create(SymbolKind.ATTRIBUTE, attribute.name(),
                    Optional.of(owner.id()), attribute.range(), attribute.nameRange(), language,
                    attribute.modifiers(), attribute, sourceId,
                    owner.qualifiedName() + "." + attribute.name(), Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            return;
        }
        if (node instanceof RoleDeclarationSyntax role) {
            DeclInfo declaration = create(SymbolKind.ROLE, role.name(), Optional.of(owner.id()),
                    role.range(), role.nameRange(), language, role.modifiers(), role, sourceId,
                    owner.qualifiedName() + "." + role.name(), Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            return;
        }
        if (node instanceof ExternalConstraintSyntax external) {
            String name = external.constraint().name().orElse(
                    "constraint@" + external.range().startOffset());
            DeclInfo declaration = create(SymbolKind.CONSTRAINT, name, Optional.of(owner.id()),
                    external.range(), Optional.empty(), language, Set.of(), external, sourceId,
                    owner.qualifiedName() + "#" + name, Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            return;
        }
        if (node instanceof ConstraintDeclarationSyntax constraint) {
            String name = constraint.name().orElse("constraint@" + constraint.range().startOffset());
            DeclInfo declaration = create(SymbolKind.CONSTRAINT, name, Optional.of(owner.id()),
                    constraint.range(), Optional.empty(), language, Set.of(), constraint, sourceId,
                    owner.qualifiedName() + "#" + name, Optional.empty(), 0);
            register(sourceId, declaration, owner.id());
            return;
        }
        if (node instanceof UnsupportedDeclarationSyntax unsupported) {
            String name = "unsupported@" + unsupported.range().startOffset();
            DeclInfo declaration = create(SymbolKind.UNSUPPORTED, name, Optional.of(owner.id()),
                    unsupported.range(), Optional.empty(), language, Set.of(), unsupported, sourceId,
                    owner.qualifiedName() + "#" + name, Optional.of(unsupported.featureId()), 0);
            register(sourceId, declaration, owner.id());
            return;
        }
        // Unknown node: keep it visible as unsupported declaration.
        String name = "unsupported@" + node.range().startOffset();
        DeclInfo declaration = create(SymbolKind.UNSUPPORTED, name, Optional.of(owner.id()),
                node.range(), Optional.empty(), language, Set.of(), node, sourceId,
                owner.qualifiedName() + "#" + name, Optional.of("UNKNOWN_SYNTAX"), 0);
        register(sourceId, declaration, owner.id());
    }

    private DeclInfo create(SymbolKind kind, String name, Optional<SymbolId> owner,
                            SourceRange range, Optional<SourceRange> nameRange,
                            LanguageVersion language, Set<Modifier> modifiers, SyntaxNode syntax,
                            SourceId sourceId, String qualifiedName,
                            Optional<String> featureId, int localOrdinal) {
        String key = sourceId.value() + "|" + kind + "|" + qualifiedName + "|" + localOrdinal;
        SymbolId id = previous.get(key);
        if (id == null || !usedPrevious.add(id) || assigned.contains(id)) {
            do {
                id = new SymbolId("sym-" + (++counter));
            } while (reservedPrevious.contains(id) || assigned.contains(id));
        }
        assigned.add(id);
        return new DeclInfo(id, kind, name, owner, counter, new Origin.DeclaredOrigin(range),
                nameRange, language, modifiers, syntax, sourceId, qualifiedName, featureId);
    }

    private void register(SourceId sourceId, DeclInfo declaration, SymbolId owner) {
        byId.put(declaration.id(), declaration);
        children.computeIfAbsent(declaration.id(), ignored -> new ArrayList<>());
        if (owner != null) {
            children.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(declaration);
            modelOf.put(declaration.id(), modelOf.getOrDefault(owner, owner));
        } else {
            modelOf.put(declaration.id(), declaration.id());
        }
        byQualifiedName.computeIfAbsent(declaration.qualifiedName(),
                ignored -> new ArrayList<>()).add(declaration);
    }
}
