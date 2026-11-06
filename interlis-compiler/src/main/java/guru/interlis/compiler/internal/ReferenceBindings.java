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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Registers resolved and unresolved references in their original build order. */
final class ReferenceBindings {
    private final IndexedDeclarations index;
    private final SemanticGraphRead state;
    private final SemanticWork work;
    private final Consumer<NameReference> storeReference;
    private final Function<LanguageVersion, SymbolId> ensurePredefined;
    private final Set<ReferenceSite> recordedReferences = new HashSet<>();
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
            recordReference(decl, clazz.baseType().get(), state.baseOf(decl.id()), ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof StructureDeclarationSyntax structure
                && structure.baseType().isPresent()) {
            recordReference(decl, structure.baseType().get(), state.baseOf(decl.id()),
                    ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof TopicSyntax topic && topic.baseTopic().isPresent()) {
            recordReference(decl, topic.baseTopic().get(), state.baseOf(decl.id()),
                    ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof DomainDeclarationSyntax domain
                && domain.baseDomain().isPresent()) {
            recordReference(decl, domain.baseDomain().get(), state.baseOf(decl.id()),
                    ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof AssociationDeclarationSyntax association
                && association.baseAssociation().isPresent()) {
            recordReference(decl, association.baseAssociation().orElseThrow(), state.baseOf(decl.id()), ReferenceUsage.BASE_TYPE);
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

    private record ReferenceSite(SymbolId owner, SourceRange range, ReferenceUsage usage, Optional<SymbolId> target) { }

    void recordReference(DeclInfo owner, NameSyntax name,
                                 SymbolId target, ReferenceUsage usage) {
        ReferenceId id = reserveReference(owner.id(), name.range(), usage, target);
        if (id == null) return;
        List<ReferenceSegment> segments = new ArrayList<>();
        SymbolId current = target;
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
        BindingResolution resolution = target == null
                ? new BindingResolution.UnresolvedTarget(guru.interlis.model.UnresolvedReason.NAME_NOT_FOUND,
                List.of())
                : new BindingResolution.BoundTarget(target);
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
        var id = reserveReference(owner, range, ReferenceUsage.EXPRESSION, target);
        if (id == null) return;
        storeReference.accept(new NameReference(id, new ScopeId(owner.value()), ReferenceUsage.EXPRESSION,
                segments, range, new BindingResolution.BoundTarget(target)));
    }

    private ReferenceId reserveReference(SymbolId owner, SourceRange range, ReferenceUsage usage, SymbolId target) {
        if (!recordedReferences.add(new ReferenceSite(owner, range, usage, Optional.ofNullable(target)))) return null;
        return new ReferenceId("ref-" + (++referenceCounter));
    }
}
