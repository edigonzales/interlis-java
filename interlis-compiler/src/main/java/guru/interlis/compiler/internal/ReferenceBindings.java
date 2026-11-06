package guru.interlis.compiler.internal;

import guru.interlis.model.BindingResolution;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.NameReference;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.ReferenceSegment;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.ScopeId;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.UnresolvedReason;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.AssociationDeclarationSyntax;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.CollectionTypeSyntax;
import guru.interlis.syntax.DomainDeclarationSyntax;
import guru.interlis.syntax.DomainReferenceSyntax;
import guru.interlis.syntax.NameSyntax;
import guru.interlis.syntax.StructureDeclarationSyntax;
import guru.interlis.syntax.TopicSyntax;
import guru.interlis.syntax.TypeSyntax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Registers resolved and unresolved references in their original build order. */
final class ReferenceBindings {
    private static final BindingResolution NAME_NOT_FOUND =
            new BindingResolution.UnresolvedTarget(UnresolvedReason.NAME_NOT_FOUND, List.of());

    private final IndexedDeclarations index;
    private final SemanticGraphRead state;
    private final SemanticWork work;
    private final Consumer<NameReference> storeReference;
    private final Function<LanguageVersion, SymbolId> ensurePredefined;
    private final Set<RecordedReference> recordedReferences = new HashSet<>();
    private final Map<ReferenceSite, BindingResolution.UnresolvedTarget> ambiguousSites = new HashMap<>();
    private int referenceCounter;

    ReferenceBindings(IndexedDeclarations index, SemanticGraphRead state, SemanticWork work,
                      Consumer<NameReference> storeReference,
                      Function<LanguageVersion, SymbolId> ensurePredefined) {
        this.index = index;
        this.state = state;
        this.work = work;
        this.storeReference = storeReference;
        this.ensurePredefined = ensurePredefined;
    }

    void complete() {
        // Walk the complete declaration tree: attributes and roles also carry
        // navigable type/role references, not only model-level declarations.
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            work.checkpoint();
            if (state.symbol(entry.id()) instanceof ModelSymbol model) {
                for (var imported : model.imports()) {
                    var name = new NameSyntax(
                            new guru.interlis.syntax.SyntaxNodeId("import-" + referenceCounter),
                            imported.range(), List.of(new NameSyntax.NameSegmentSyntax(
                                    imported.importedModel(), imported.range())));
                    recordReference(entry.declaration(), name, imported.targetModel().orElse(null), ReferenceUsage.IMPORT);
                }
            }
            referenceTree(entry.id());
        }
    }

    private void referenceTree(SymbolId owner) {
        for (DeclInfo child : index.children(owner)) {
            work.checkpoint();
            referenceFor(child);
            referenceTree(child.id());
        }
    }

    private void referenceFor(DeclInfo decl) {
        if (decl.syntax() instanceof ClassDeclarationSyntax clazz && clazz.baseType().isPresent()) {
            completeBaseReference(decl, clazz.baseType().get());
        } else if (decl.syntax() instanceof StructureDeclarationSyntax structure
                && structure.baseType().isPresent()) {
            completeBaseReference(decl, structure.baseType().get());
        } else if (decl.syntax() instanceof TopicSyntax topic && topic.baseTopic().isPresent()) {
            completeBaseReference(decl, topic.baseTopic().get());
        } else if (decl.syntax() instanceof DomainDeclarationSyntax domain
                && domain.baseDomain().isPresent()) {
            completeBaseReference(decl, domain.baseDomain().get());
        } else if (decl.syntax() instanceof AssociationDeclarationSyntax association
                && association.baseAssociation().isPresent()) {
            completeBaseReference(decl, association.baseAssociation().orElseThrow());
        } else if (decl.syntax() instanceof AttributeDeclarationSyntax attribute) {
            referenceForType(decl, attribute.type());
        }
    }

    private void referenceForType(DeclInfo decl, TypeSyntax syntax) {
        if (syntax instanceof DomainReferenceSyntax reference) {
            TypeDescriptor descriptor = state.type(state.declaredType(decl.id()));
            if (descriptor instanceof DomainReferenceType domain) {
                recordReference(decl, reference.name(), domain.targetDomain(),
                        ReferenceUsage.TYPE_USE);
            }
        } else if (syntax instanceof CollectionTypeSyntax collection) {
            referenceForType(decl, collection.elementType());
        }
    }

    private void completeBaseReference(DeclInfo owner, NameSyntax name) {
        var site = new ReferenceSite(owner.id(), name.range(), ReferenceUsage.BASE_TYPE);
        var ambiguity = ambiguousSites.get(site);
        if (ambiguity == null)
            recordReference(owner, name, state.baseOf(owner.id()), ReferenceUsage.BASE_TYPE);
        else
            record(owner, name, ambiguity, ReferenceUsage.BASE_TYPE);
    }

    /** Topic and association base references are published by the final reference walk. */
    void deferBaseAmbiguity(DeclInfo owner, NameSyntax name, NameResolver.Resolution resolution) {
        if (resolution.status() == NameResolver.Status.AMBIGUOUS)
            ambiguousSites.putIfAbsent(new ReferenceSite(owner.id(), name.range(), ReferenceUsage.BASE_TYPE),
                    ambiguous(resolution));
    }

    private record ReferenceSite(SymbolId owner, SourceRange range, ReferenceUsage usage) { }

    private record RecordedReference(ReferenceSite site, BindingResolution resolution) { }

    void recordReference(DeclInfo owner, NameSyntax name,
                                 SymbolId target, ReferenceUsage usage) {
        record(owner, name, target == null ? NAME_NOT_FOUND : new BindingResolution.BoundTarget(target), usage);
    }

    void recordResolution(DeclInfo owner, NameSyntax name, NameResolver.Resolution resolution, ReferenceUsage usage) {
        BindingResolution binding = switch (resolution.status()) {
            case BOUND -> new BindingResolution.BoundTarget(resolution.target().orElseThrow());
            case UNRESOLVED -> NAME_NOT_FOUND;
            case AMBIGUOUS -> ambiguous(resolution);
        };
        record(owner, name, binding, usage);
    }

    private static BindingResolution.UnresolvedTarget ambiguous(NameResolver.Resolution resolution) {
        return new BindingResolution.UnresolvedTarget(UnresolvedReason.AMBIGUOUS_NAME, resolution.candidates());
    }

    private void record(DeclInfo owner, NameSyntax name, BindingResolution resolution, ReferenceUsage usage) {
        var site = new ReferenceSite(owner.id(), name.range(), usage);
        if (usage == ReferenceUsage.BASE_TYPE && resolution instanceof BindingResolution.UnresolvedTarget unresolved
                && unresolved.reason() == UnresolvedReason.AMBIGUOUS_NAME)
            ambiguousSites.putIfAbsent(site, unresolved);
        ReferenceId id = reserveReference(site, resolution);
        if (id == null) return;
        List<ReferenceSegment> segments = new ArrayList<>();
        SymbolId current = resolution instanceof BindingResolution.BoundTarget bound ? bound.target() : null;
        for (int i = name.segments().size() - 1; i >= 0; i--) {
            work.checkpoint();
            var segment = name.segments().get(i);
            if (i < name.segments().size() - 1) {
                current = referenceOwner(current);
                while (current != null && !referenceName(current).equals(segment.text())) {
                    work.checkpoint();
                    current = referenceOwner(current);
                }
            }
            segments.addFirst(new ReferenceSegment(segment.text(), segment.range(), Optional.ofNullable(current)));
        }
        storeReference.accept(new NameReference(id, new ScopeId(owner.id().value()), usage,
                segments, name.range(), resolution));
    }

    private SymbolId referenceOwner(SymbolId symbol) {
        if (symbol == null) return null;
        var declaration = index.byId(symbol);
        if (declaration.isPresent()) return declaration.orElseThrow().owner().orElse(null);
        var value = state.symbol(symbol);
        if (value == null) return null;
        if (value.owner().isPresent()) return value.owner().orElseThrow();
        return value.origin() instanceof Origin.PredefinedOrigin predefined && predefined.key().startsWith("INTERLIS.")
                ? ensurePredefined.apply(predefined.language()) : null;
    }

    private String referenceName(SymbolId symbol) {
        return index.byId(symbol).map(DeclInfo::name).orElseGet(() -> {
            String name = state.symbol(symbol).name().value(); return name.substring(name.lastIndexOf('.') + 1);
        });
    }


    void recordEnumReference(SymbolId owner, SourceRange range, List<ReferenceSegment> segments, SymbolId target) {
        if (target == null) return;
        var binding = new BindingResolution.BoundTarget(target);
        var id = reserveReference(new ReferenceSite(owner, range, ReferenceUsage.EXPRESSION), binding);
        if (id == null) return;
        storeReference.accept(new NameReference(id, new ScopeId(owner.value()), ReferenceUsage.EXPRESSION,
                segments, range, binding));
    }

    private ReferenceId reserveReference(ReferenceSite site, BindingResolution resolution) {
        if (!recordedReferences.add(new RecordedReference(site, resolution))) return null;
        return new ReferenceId("ref-" + (++referenceCounter));
    }
}
