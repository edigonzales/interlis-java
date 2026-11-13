package guru.interlis.compiler.internal;

import guru.interlis.model.AlignmentType;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.BlackboxType;
import guru.interlis.model.BooleanType;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.ClassValueType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ConstraintDefinition;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.FormattedType;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.model.UnitSymbol;
import guru.interlis.model.ViewSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** Checks and completes translation correspondences on the bound graph. */
final class TranslationBindings {
    private final IndexedDeclarations index;
    private final SemanticGraphRead state;
    private final SemanticWork work;
    private final SemanticDiagnostics diagnostics;
    private final Function<TypeId, TypeDescriptor> effectiveDescriptor;
    private final BiFunction<SymbolId, String, Optional<SymbolId>> findMember;
    private final ViewBuilder views;
    private final Consumer<ModelSymbol> replaceModel;

    TranslationBindings(IndexedDeclarations index, SemanticGraphRead state, SemanticWork work,
                        SemanticDiagnostics diagnostics,
                        Function<TypeId, TypeDescriptor> effectiveDescriptor,
                        BiFunction<SymbolId, String, Optional<SymbolId>> findMember,
                        ViewBuilder views, Consumer<ModelSymbol> replaceModel) {
        this.index = index;
        this.state = state;
        this.work = work;
        this.diagnostics = diagnostics;
        this.effectiveDescriptor = effectiveDescriptor;
        this.findMember = findMember;
        this.views = views;
        this.replaceModel = replaceModel;
    }

    void complete() {
        List<ModelSymbol> models = state.symbolsInBuildOrder().stream()
                .filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast).toList();
        Map<SymbolId, SymbolId> originals = new LinkedHashMap<>();
        for (var model : models) model.translation().ifPresent(translation -> {
            var candidates = models.stream().filter(candidate -> candidate.name().value().equals(translation.sourceModel()))
                    .filter(candidate -> translation.sourceVersion().isEmpty() || translation.sourceVersion().orElseThrow().equals(candidate.modelVersion())).toList();
            if(translation.sourceTarget().isPresent()) originals.put(model.id(),translation.sourceTarget().orElseThrow());
            else if (candidates.size() == 1) originals.put(model.id(), candidates.getFirst().id());
        });
        for (ModelSymbol model : models) {
            work.checkpoint();
            if (model.translation().isEmpty()) {
                continue;
            }
            guru.interlis.model.ModelTranslation translation = model.translation().orElseThrow();
            ModelSymbol source = (ModelSymbol) state.symbol(originals.get(model.id()));
            DeclInfo decl = index.byId(model.id()).orElse(null);
            if (decl != null && translationRoot(model.id(), originals) == null) {
                diagnostics.emitRule(decl, "translation dependency has no original model", "TRANSLATION_CYCLE");
                continue;
            }
            if (source == null) {
                if (decl != null) {
                    diagnostics.emitRule(decl, "translation source model not found", "TRANSLATION_SOURCE");
                }
                continue;
            }
            if (translation.sourceVersion().isPresent()
                    && !translation.sourceVersion().orElseThrow().equals(source.modelVersion())) {
                if (decl != null) {
                    diagnostics.emitRule(decl, "translation source version mismatch", "TRANSLATION_VERSION");
                }
            }
            if (decl != null) {
                Map<SymbolId, SymbolId> correspondence = new LinkedHashMap<>();
                if (!correspondenceWithImports(source, model, originals, correspondence, new HashSet<>())) correspondence.clear();
                if (correspondence.isEmpty()
                        || !sameModelProfile(source, model)) {
                    diagnostics.emitRule(decl, "translation syntax does not match source model",
                            "TRANSLATION_STRUCTURE");
                }
                if (!correspondence.isEmpty()) {
                    var checked = new LinkedHashSet<SymbolId>();
                    while (true) {
                        work.checkpoint();
                        var pending = correspondence.entrySet().stream().filter(e -> !checked.contains(e.getKey()))
                                .map(e -> new guru.interlis.model.TranslationLink(e.getKey(), e.getValue())).toList();
                        if (pending.isEmpty()) break;
                        for (var pair : pending) {
                            work.checkpoint();
                            checked.add(pair.original());
                            var left=state.symbol(pair.original());var right=state.symbol(pair.translated());
                            if(left!=null && right!=null)compareSymbolReferences(left,right,correspondence,decl);
                        }
                    }
                }
                // Expression/formation checks may add verified contextualized declarations.
                // Publish the final immutable links only after all newly added pairs were checked.
                var bound = new guru.interlis.model.ModelTranslation(translation.sourceModel(), translation.sourceVersion(), translation.language(),
                        Optional.of(source.id()), correspondence.entrySet().stream().map(e -> new guru.interlis.model.TranslationLink(e.getKey(), e.getValue())).toList(),translation.origin());
                replaceModel.accept(new ModelSymbol(model.header(), model.modelKind(), model.contracted(), model.modelVersion(),
                        model.issuer(), model.imports(), model.members(), Optional.of(bound), model.options(), model.transferNamespace()));
            }
        }
    }

    private boolean sameModelProfile(ModelSymbol source, ModelSymbol target) {
        if (source.modelKind() != target.modelKind()
                || source.header().languageVersion() == LanguageVersion.ILI23 && source.contracted() != target.contracted()
                || source.options().incrementalTransferAllowed() != target.options().incrementalTransferAllowed()
                || !source.options().characterSet().map(v -> CharacterSets.canonical(v.value()).orElse(v.value()))
                        .equals(target.options().characterSet().map(v -> CharacterSets.canonical(v.value()).orElse(v.value())))
                || source.imports().size() != target.imports().size()) {
            return false;
        }
        return true;
    }

    private boolean correspondenceWithImports(ModelSymbol source, ModelSymbol target,
            Map<SymbolId, SymbolId> originals, Map<SymbolId, SymbolId> map,
            Set<List<SymbolId>> visited) {
        work.checkpoint();
        if (!visited.add(List.of(source.id(), target.id()))) return true;
        if (!correspondenceInto(source, target, map) || source.imports().size() != target.imports().size()) return false;
        Set<Integer> used = new HashSet<>();
        for (var imported : source.imports()) {
            work.checkpoint();
            List<Integer> exact = new ArrayList<>(), translated = new ArrayList<>();
            for (int i = 0; i < target.imports().size(); i++) {
                if (used.contains(i)) continue;
                var candidate = target.imports().get(i);
                if (candidate.unqualified() != imported.unqualified()) continue;
                if (imported.targetModel().isEmpty() || candidate.targetModel().isEmpty()) {
                    if (imported.importedModel().equals("INTERLIS") && candidate.importedModel().equals("INTERLIS")) exact.add(i);
                } else if (imported.targetModel().equals(candidate.targetModel())) exact.add(i);
                else {
                    SymbolId root = translationRoot(imported.targetModel().orElseThrow(), originals);
                    if (root != null && root.equals(translationRoot(candidate.targetModel().orElseThrow(), originals))) translated.add(i);
                }
            }
            var candidates = exact.isEmpty() ? translated : exact;
            if (candidates.size() != 1) return false;
            int selected = candidates.getFirst(); used.add(selected);
            if (imported.targetModel().isPresent()) {
                var a = state.symbol(imported.targetModel().orElseThrow());
                var b = state.symbol(target.imports().get(selected).targetModel().orElseThrow());
                if (!(a instanceof ModelSymbol x) || !(b instanceof ModelSymbol y)
                        || !correspondenceWithImports(x, y, originals, map, visited)) return false;
            }
        }
        return true;
    }

    private SymbolId translationRoot(SymbolId model, Map<SymbolId, SymbolId> originals) {
        var visited = new LinkedHashSet<SymbolId>();
        var current = model;
        while (originals.containsKey(current)) {
            work.checkpoint();
            if (!visited.add(current)) return null;
            current = originals.get(current);
        }
        return current;
    }

    private boolean correspondenceInto(Symbol source, Symbol target,
                                       Map<SymbolId, SymbolId> map) {
        if (source.kind() != target.kind()) {
            return false;
        }
        SymbolId previous = map.putIfAbsent(source.id(), target.id());
        if (previous != null && !previous.equals(target.id())) return false;
        if (source instanceof DomainSymbol || source instanceof AttributeSymbol) {
            var a = index.children(source.id()).stream().filter(d -> d.kind() == SymbolKind.ENUM_ELEMENT).map(DeclInfo::id).toList();
            var b = index.children(target.id()).stream().filter(d -> d.kind() == SymbolKind.ENUM_ELEMENT).map(DeclInfo::id).toList();
            if (!childCorrespondence(a, b, map)) return false;
        }
        var sourceConstraints=index.children(source.id()).stream().filter(d->d.kind()==SymbolKind.CONSTRAINT).map(DeclInfo::id).toList();
        var targetConstraints=index.children(target.id()).stream().filter(d->d.kind()==SymbolKind.CONSTRAINT).map(DeclInfo::id).toList();
        if(!constraintCorrespondence(sourceConstraints,targetConstraints,map))return false;
        if (source instanceof ModelSymbol sourceModel && target instanceof ModelSymbol targetModel) {
            return childCorrespondence(sourceModel.members(), targetModel.members(), map);
        }
        if (source instanceof guru.interlis.model.GraphicSymbol a && target instanceof guru.interlis.model.GraphicSymbol b)
            return childCorrespondence(a.rules(), b.rules(), map);
        if (source instanceof guru.interlis.model.MetadataBasketSymbol a && target instanceof guru.interlis.model.MetadataBasketSymbol b)
            return childCorrespondence(a.objects(), b.objects(), map);
        if (source instanceof TopicSymbol sourceTopic && target instanceof TopicSymbol targetTopic) {
            return childCorrespondence(sourceTopic.members(), targetTopic.members(), map);
        }
        if (source instanceof ClassSymbol sourceClass && target instanceof ClassSymbol targetClass) {
            if (!childCorrespondence(sourceClass.members(), targetClass.members(), map)) {
                return false;
            }
            return constraintCorrespondence(sourceClass.constraints(), targetClass.constraints(), map);
        }
        if (source instanceof StructureSymbol sourceStructure
                && target instanceof StructureSymbol targetStructure) {
            if (!childCorrespondence(sourceStructure.members(), targetStructure.members(), map)) {
                return false;
            }
            return constraintCorrespondence(sourceStructure.constraints(),
                    targetStructure.constraints(), map);
        }
        if (source instanceof ViewSymbol a && target instanceof ViewSymbol b) {
            if (a.formation().orElse(null) instanceof ViewSymbol.Aggregation
                    && b.formation().orElse(null) instanceof ViewSymbol.Aggregation) {
                var left = findMember.apply(a.id(), "AGGREGATES");
                var right = findMember.apply(b.id(), "AGGREGATES");
                if (left.isEmpty() || right.isEmpty()) return false;
                var existingAggregate = map.putIfAbsent(left.orElseThrow(), right.orElseThrow());
                if (existingAggregate != null && !existingAggregate.equals(right.orElseThrow())) return false;
            }
            return childCorrespondence(a.bases(), b.bases(), map)
                    && childCorrespondence(a.members(), b.members(), map)
                    && constraintCorrespondence(a.constraints(), b.constraints(), map);
        }
        if (source instanceof AssociationSymbol sourceAssociation
                && target instanceof AssociationSymbol targetAssociation) {
            if (sourceAssociation.derivedFrom().isPresent() != targetAssociation.derivedFrom().isPresent()) return false;
            if (sourceAssociation.derivedFrom().isPresent()) {
                var existingAssociation = map.putIfAbsent(sourceAssociation.derivedFrom().orElseThrow(), targetAssociation.derivedFrom().orElseThrow());
                if (existingAssociation != null && !existingAssociation.equals(targetAssociation.derivedFrom().orElseThrow())) return false;
            }
            if (!childCorrespondence(sourceAssociation.roles(), targetAssociation.roles(), map)
                    || !childCorrespondence(sourceAssociation.attributes(),
                    targetAssociation.attributes(), map)) {
                return false;
            }
            return constraintCorrespondence(sourceAssociation.constraints(),
                    targetAssociation.constraints(), map);
        }
        return true;
    }

    private boolean childCorrespondence(List<SymbolId> sourceIds, List<SymbolId> targetIds,
                                        Map<SymbolId, SymbolId> map) {
        if (sourceIds.size() != targetIds.size()) {
            return false;
        }
        for (int i = 0; i < sourceIds.size(); i++) {
            work.checkpoint();
            Symbol source = state.symbol(sourceIds.get(i));
            Symbol target = state.symbol(targetIds.get(i));
            if (source == null || target == null || !correspondenceInto(source, target, map)) {
                return false;
            }
        }
        return true;
    }

    private boolean constraintCorrespondence(List<SymbolId> sourceIds, List<SymbolId> targetIds,
                                             Map<SymbolId, SymbolId> map) {
        if (sourceIds.size() != targetIds.size()) {
            return false;
        }
        for (int i = 0; i < sourceIds.size(); i++) {
            work.checkpoint();
            var previous = map.putIfAbsent(sourceIds.get(i), targetIds.get(i));
            if (previous != null && !previous.equals(targetIds.get(i))) return false;
        }
        return true;
    }

    private void compareSymbolReferences(Symbol source, Symbol target,
                                         Map<SymbolId, SymbolId> correspondence,
                                         DeclInfo translationDecl) {
        if(!source.header().modifiers().equals(target.header().modifiers()))
            diagnostics.emitRule(translationDecl,"translation declaration modifiers differ","TRANSLATION_STRUCTURE");
        if(source instanceof AttributeSymbol a && target instanceof AttributeSymbol b
                && (a.mandatory()!=b.mandatory() || a.transientAttribute()!=b.transientAttribute()
                    || a.continuous()!=b.continuous() || a.subdivision()!=b.subdivision()))
            diagnostics.emitRule(translationDecl,"translation attribute flags differ","TRANSLATION_REFERENCE");
        if(source instanceof RoleSymbol a && target instanceof RoleSymbol b
                && (!a.cardinality().equals(b.cardinality()) || a.aggregation()!=b.aggregation()
                    || a.ordered()!=b.ordered() || a.external()!=b.external() || a.hiding()!=b.hiding()))
            diagnostics.emitRule(translationDecl,"translation role properties differ","TRANSLATION_REFERENCE");
        if(source instanceof AssociationSymbol a && target instanceof AssociationSymbol b
                && (!a.cardinality().equals(b.cardinality()) || !sameMapped(a.derivedFrom(),b.derivedFrom(),correspondence)))
            diagnostics.emitRule(translationDecl,"translation association properties differ","TRANSLATION_REFERENCE");
        if(source instanceof DomainSymbol a && target instanceof DomainSymbol b && a.generic()!=b.generic())
            diagnostics.emitRule(translationDecl,"translation domain generic flag differs","TRANSLATION_REFERENCE");
        if(source instanceof TopicSymbol a && target instanceof TopicSymbol b
                && !new HashSet<>(a.dependencies().stream().map(id->correspondence.getOrDefault(id,id)).toList()).equals(new HashSet<>(b.dependencies())))
            diagnostics.emitRule(translationDecl,"translation topic dependencies differ","TRANSLATION_REFERENCE");
        if(source instanceof ConstraintSymbol a && target instanceof ConstraintSymbol b
                && !constraintHasErrors(a.definition()) && !constraintHasErrors(b.definition())) {
            boolean same = sameMapped(a.target(),b.target(),correspondence)
                    && sameConstraint(a.definition(),b.definition(),correspondence);
            var targets=constraintReferenceTargets(a.definition()).stream().map(id->correspondence.getOrDefault(id,id)).toList();
            if(!sameMapped(a.target(),b.target(),correspondence) || !targets.equals(constraintReferenceTargets(b.definition())))
                diagnostics.emitRule(translationDecl,"translation constraint targets differ","TRANSLATION_REFERENCE");
            else if(!same)
                diagnostics.emitRule(translationDecl,"translation constraint structure differs","TRANSLATION_CONSTRAINT");
        }
        var sourceCrs=Optional.ofNullable(source.header().metaAttributes().values().get("CRS"));
        var targetCrs=Optional.ofNullable(target.header().metaAttributes().values().get("CRS"));
        if(effectiveDescriptor.apply(state.declaredType(source.id())) instanceof CoordinateType
                && !sourceCrs.map(MetaAttributeSet.Value::text).equals(targetCrs.map(MetaAttributeSet.Value::text))) {
            var location=targetCrs.map(MetaAttributeSet.Value::origin).filter(Origin.DeclaredOrigin.class::isInstance).map(Origin.DeclaredOrigin.class::cast)
                    .map(Origin.DeclaredOrigin::range).orElseGet(()->target.origin() instanceof Origin.DeclaredOrigin declared?declared.range():translationDecl.syntax().range());
            diagnostics.emitRule(translationDecl,"translation coordinate reference systems differ","TRANSLATION_COORDINATE",location);
        }
        if (source instanceof ViewSymbol a && target instanceof ViewSymbol b) {
            if (!sameMapped(a.baseView(), b.baseView(), correspondence)
                    || !viewFormationShape(a).equals(viewFormationShape(b))
                    || !sameExpressionLists(a.selections(), b.selections(), correspondence)
                    || !sameExpressionLists(formationExpressions(a), formationExpressions(b), correspondence)
                    || !a.baseExtensions().stream().map(e -> List.of(correspondence.getOrDefault(e.base(), e.base()),
                            e.extensions().stream().map(id -> correspondence.getOrDefault(id, id)).toList())).toList()
                        .equals(b.baseExtensions().stream().map(e -> List.of(e.base(), e.extensions())).toList()))
                diagnostics.emitRule(translationDecl, "translation view formation or selection differs", "TRANSLATION_REFERENCE");
        }
        if (source instanceof ViewSymbol a && target instanceof ViewSymbol b
                && a.formation().orElse(null) instanceof ViewSymbol.Inspection x
                && b.formation().orElse(null) instanceof ViewSymbol.Inspection y
                && (!sameExpressionLists(List.of(x.path()),List.of(y.path()),correspondence)
                    || !sameInspectionTarget(x.element(),y.element(),correspondence)
                    || !sameInspectionTarget(x.parent(),y.parent(),correspondence)))
            diagnostics.emitRule(translationDecl,"translation inspection context differs","TRANSLATION_REFERENCE");
        if (source instanceof ViewSymbol.Base a && target instanceof ViewSymbol.Base b
                && (a.orNull() != b.orNull() || !correspondence.getOrDefault(a.target(), a.target()).equals(b.target())))
            diagnostics.emitRule(translationDecl, "translation view basis differs", "TRANSLATION_REFERENCE");
        if (source instanceof guru.interlis.model.GraphicSymbol a && target instanceof guru.interlis.model.GraphicSymbol b
                && (!sameMapped(a.baseGraphic(),b.baseGraphic(),correspondence) || !sameMapped(a.basis(),b.basis(),correspondence)
                    || !sameExpressionLists(a.selections(),b.selections(),correspondence)))
            diagnostics.emitRule(translationDecl,"translated graphic basis or selections differ","TRANSLATION_GRAPHIC");
        if (source instanceof guru.interlis.model.DrawingRuleSymbol a && target instanceof guru.interlis.model.DrawingRuleSymbol b) {
            boolean same=sameMapped(a.signatureClass(),b.signatureClass(),correspondence) && sameMapped(a.overrides(),b.overrides(),correspondence)
                    && a.clauses().size()==b.clauses().size();
            for(int i=0;same && i<a.clauses().size();i++) {
                var left=a.clauses().get(i); var right=b.clauses().get(i);
                same=sameExpressionLists(left.condition().stream().toList(),right.condition().stream().toList(),correspondence) && left.assignments().size()==right.assignments().size();
                for(int j=0;same && j<left.assignments().size();j++) {
                    var x=left.assignments().get(j);var y=right.assignments().get(j);
                    same=sameMapped(x.parameter(),y.parameter(),correspondence) && sameDrawingValue(x.value(),y.value(),correspondence);
                }
            }
            if(!same) diagnostics.emitRule(translationDecl,"translated drawing rule bindings or assignments differ","TRANSLATION_DRAWING_RULE");
        }
        if (source instanceof FunctionSymbol a && target instanceof FunctionSymbol b) {
            boolean same = a.parameters().size() == b.parameters().size()
                    && a.explanation().ranges().isEmpty()==b.explanation().ranges().isEmpty() && a.returnMandatory()==b.returnMandatory() && sameMappedType(a.returnType(), b.returnType(), correspondence);
            for (int i = 0; same && i < a.parameters().size(); i++)
                same = a.parameters().get(i).mandatory()==b.parameters().get(i).mandatory() && sameMappedType(a.parameters().get(i).typeId(), b.parameters().get(i).typeId(), correspondence);
            if (!same) diagnostics.emitRule(translationDecl, "translation function argument targets differ", "TRANSLATION_FUNCTION");
        }
        if (source instanceof guru.interlis.model.ParameterSymbol a && target instanceof guru.interlis.model.ParameterSymbol b
                && (a.mandatory() != b.mandatory() || !sameMapped(a.overrides(),b.overrides(),correspondence) || !sameMappedType(a.typeId(), b.typeId(), correspondence)
                   ))
            diagnostics.emitRule(translationDecl, "translation parameter type or mandatory flag differs", "TRANSLATION_PARAMETER");
        if (source instanceof AttributeSymbol a && target instanceof AttributeSymbol b
                && (!sameExpressionLists(a.derivations(), b.derivations(), correspondence)
                    || !sameMapped(a.overrides(), b.overrides(), correspondence)
                    || !sameMappedType(a.declaredType(), b.declaredType(), correspondence)))
            diagnostics.emitRule(translationDecl, "translation attribute type, override or derivation differs", "TRANSLATION_REFERENCE");
        if (source instanceof RoleSymbol a && target instanceof RoleSymbol b
                && !sameExpressionLists(a.derivation().stream().toList(), b.derivation().stream().toList(), correspondence))
            diagnostics.emitRule(translationDecl, "translation role derivation differs", "TRANSLATION_REFERENCE");
        var sourceRules = source.header().oidRules(); var targetRules = target.header().oidRules();
        if (sourceRules.size() != targetRules.size()) diagnostics.emitRule(translationDecl, "translation OID rules differ", "TRANSLATION_REFERENCE");
        for (int i = 0; i < Math.min(sourceRules.size(), targetRules.size()); i++) {
            var a = sourceRules.get(i); var b = targetRules.get(i);
            boolean equivalent = sameMapped(a.domain(), b.domain(), correspondence);
            if (!equivalent && a.domain().isPresent() && b.domain().isPresent()) {
                Symbol left = state.symbol(a.domain().orElseThrow()), right = state.symbol(b.domain().orElseThrow());
                equivalent = left != null && right != null && left.header().origin() instanceof Origin.SyntheticOrigin
                        && right.header().origin() instanceof Origin.SyntheticOrigin && left.name().equals(right.name());
            }
            if (a.basket() != b.basket() || !equivalent)
                diagnostics.emitRule(translationDecl, "translation OID domain differs", "TRANSLATION_REFERENCE");
        }
        if (source instanceof ClassSymbol sourceClass && target instanceof ClassSymbol targetClass) {
            if (!sameMapped(sourceClass.baseType(), targetClass.baseType(), correspondence)) {
                diagnostics.emitRule(translationDecl, "translation class base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof StructureSymbol sourceStructure
                && target instanceof StructureSymbol targetStructure) {
            if (!sameMapped(sourceStructure.baseType(), targetStructure.baseType(), correspondence)) {
                diagnostics.emitRule(translationDecl, "translation structure base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof TopicSymbol sourceTopic && target instanceof TopicSymbol targetTopic) {
            if (!sameMapped(sourceTopic.baseTopic(), targetTopic.baseTopic(), correspondence)) {
                diagnostics.emitRule(translationDecl, "translation topic base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof AssociationSymbol sourceAssociation && target instanceof AssociationSymbol targetAssociation
                && !sameMapped(sourceAssociation.baseAssociation(), targetAssociation.baseAssociation(), correspondence)) {
            diagnostics.emitRule(translationDecl, "translation association base differs", "TRANSLATION_REFERENCE");
        }
        if (source instanceof RoleSymbol sourceRole && target instanceof RoleSymbol targetRole) {
            boolean same = sourceRole.targets().size() == targetRole.targets().size();
            for (int i = 0; same && i < sourceRole.targets().size(); i++) {
                var left = sourceRole.targets().get(i);
                var right = targetRole.targets().get(i);
                same = sameMapped(Optional.of(left.base()), Optional.of(right.base()), correspondence)
                        && left.restrictions().stream().map(id -> correspondence.getOrDefault(id, id)).toList()
                        .equals(right.restrictions());
            }
            if (!same) {
                diagnostics.emitRule(translationDecl, "translation role target differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof guru.interlis.model.MetadataBasketSymbol a && target instanceof guru.interlis.model.MetadataBasketSymbol b
                && (a.basketKind() != b.basketKind() || !sameMapped(a.topic(), b.topic(), correspondence) || !sameMapped(a.baseBasket(), b.baseBasket(), correspondence)))
            diagnostics.emitRule(translationDecl, "translated metadata basket has different bindings", "TRANSLATION_METADATA_BASKET");
        if (source instanceof guru.interlis.model.MetaObjectSymbol a && target instanceof guru.interlis.model.MetaObjectSymbol b
                && (!a.name().equals(b.name()) || !sameMapped(a.objectClass(), b.objectClass(), correspondence)))
            diagnostics.emitRule(translationDecl, "translated metadata object has a different name or class", "TRANSLATION_METAOBJECT");
        if (source instanceof guru.interlis.model.LineFormSymbol a && target instanceof guru.interlis.model.LineFormSymbol b
                && !sameMapped(a.segmentStructure(), b.segmentStructure(), correspondence))
            diagnostics.emitRule(translationDecl, "translated line form targets a different segment structure", "TRANSLATION_LINE_FORM");
        if (source instanceof UnitSymbol sourceUnit && target instanceof UnitSymbol targetUnit) {
            boolean same = sourceUnit.abstractUnit() == targetUnit.abstractUnit()
                    && sameMapped(sourceUnit.base(), targetUnit.base(), correspondence)
                    && sameMapped(sourceUnit.derivedFrom(), targetUnit.derivedFrom(), correspondence)
                    && sourceUnit.components().size() == targetUnit.components().size()
                    && sameUnitConversion(sourceUnit, targetUnit);
            if (same) for (int i = 0; i < sourceUnit.components().size(); i++) {
                var a = sourceUnit.components().get(i); var b = targetUnit.components().get(i);
                if (a.operator() != b.operator() || !sameMapped(Optional.of(a.unit()), Optional.of(b.unit()), correspondence)) same = false;
            }
            if (!same) {
                diagnostics.emitRule(translationDecl, "translation unit base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof guru.interlis.model.ContextSymbol original && target instanceof guru.interlis.model.ContextSymbol translated) {
            boolean same = original.mappings().size() == translated.mappings().size();
            for (int i = 0; same && i < original.mappings().size(); i++) {
                var left = original.mappings().get(i); var right = translated.mappings().get(i);
                same = correspondence.getOrDefault(left.genericDomain(), left.genericDomain()).equals(right.genericDomain())
                        && left.alternatives().stream().map(id -> correspondence.getOrDefault(id, id)).toList().equals(right.alternatives());
            }
            if (!same) diagnostics.emitRule(translationDecl, "translation context targets differ", "TRANSLATION_REFERENCE");
        }
        if (source instanceof TopicSymbol original && target instanceof TopicSymbol translated
                && !original.deferredGenerics().stream().map(id -> correspondence.getOrDefault(id, id)).toList().equals(translated.deferredGenerics()))
            diagnostics.emitRule(translationDecl, "translation deferred domains differ", "TRANSLATION_REFERENCE");
        if (source instanceof DomainSymbol sourceDomain
                && target instanceof DomainSymbol targetDomain) {
            if (!sameMapped(sourceDomain.baseDomain(), targetDomain.baseDomain(),
                    correspondence)) {
                diagnostics.emitRule(translationDecl, "translation domain base differs",
                        "TRANSLATION_REFERENCE");
            }
            if (!sameMappedType(sourceDomain.typeId(), targetDomain.typeId(),
                    correspondence)) {
                diagnostics.emitRule(translationDecl, "translation domain type differs",
                        "TRANSLATION_REFERENCE");
            }
        }
    }

    private boolean sameExpressionLists(List<ExpressionId> source, List<ExpressionId> target,
            Map<SymbolId, SymbolId> correspondence) {
        if (source.size() != target.size()) return false;
        for (int i = 0; i < source.size(); i++) {
            work.checkpoint();
            if (!sameExpression(source.get(i),target.get(i),correspondence)) return false;
            var expected = expressionReferenceTargets(source.get(i)).stream()
                    .map(id -> correspondence.getOrDefault(id, id)).toList();
            if (!expected.equals(expressionReferenceTargets(target.get(i)))) return false;
        }
        return true;
    }

    private List<ExpressionId> formationExpressions(ViewSymbol view) {
        return view.formation().map(f -> switch (f) {
            case ViewSymbol.Aggregation a -> a.equal();
            case ViewSymbol.Inspection i -> List.of(i.path());
            default -> List.<ExpressionId>of();
        }).orElse(List.of());
    }

    private String viewFormationShape(ViewSymbol view) {
        return view.formation().map(f -> switch (f) {
            case ViewSymbol.Projection ignored -> "PROJECTION";
            case ViewSymbol.Join j -> "JOIN:" + j.bases().size();
            case ViewSymbol.Union u -> "UNION:" + u.bases().size();
            case ViewSymbol.Aggregation a -> "AGGREGATION:" + a.equal().size();
            case ViewSymbol.Inspection i -> "INSPECTION:" + i.area();
        }).orElse("NONE");
    }

    private boolean sameMapped(Optional<SymbolId> source, Optional<SymbolId> target,
                               Map<SymbolId, SymbolId> correspondence) {
        if (source.isEmpty() || target.isEmpty()) {
            return source.isEmpty() && target.isEmpty();
        }
        SymbolId mapped = correspondence.getOrDefault(source.orElseThrow(), source.orElseThrow());
        return mapped.equals(target.orElseThrow());
    }

    private boolean sameAttributePathOwner(Optional<guru.interlis.model.AttributePathType.Owner> a,
            Optional<guru.interlis.model.AttributePathType.Owner> b, Map<SymbolId, SymbolId> correspondence) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        if (a.orElseThrow() instanceof guru.interlis.model.AttributePathType.ClassAttribute x
                && b.orElseThrow() instanceof guru.interlis.model.AttributePathType.ClassAttribute y)
            return sameExpressionLists(List.of(x.path()), List.of(y.path()), correspondence);
        if (a.orElseThrow() instanceof guru.interlis.model.AttributePathType.Argument x
                && b.orElseThrow() instanceof guru.interlis.model.AttributePathType.Argument y)
            return x.index() == y.index() && sameMapped(Optional.of(x.function()), Optional.of(y.function()), correspondence);
        return a.orElseThrow() instanceof guru.interlis.model.AttributePathType.Viewable x
                && b.orElseThrow() instanceof guru.interlis.model.AttributePathType.Viewable y
                && sameMapped(Optional.of(x.target()), Optional.of(y.target()), correspondence);
    }

    private boolean sameMappedType(TypeId source, TypeId target,
                                   Map<SymbolId, SymbolId> correspondence) {
        work.checkpoint();
        TypeDescriptor sourceType = state.type(source);
        TypeDescriptor targetType = state.type(target);
        if(sourceType==null || targetType==null || sourceType.getClass()!=targetType.getClass())return false;
        if(sourceType instanceof TextType a && targetType instanceof TextType b)
            return a.textKind()==b.textKind() && a.maxLength().equals(b.maxLength());
        if(sourceType instanceof AlignmentType a && targetType instanceof AlignmentType b)return a.alignment()==b.alignment();
        if(sourceType instanceof BlackboxType a && targetType instanceof BlackboxType b)return a.blackboxKind()==b.blackboxKind();
        if(sourceType instanceof guru.interlis.model.EnumArgumentType a && targetType instanceof guru.interlis.model.EnumArgumentType b)return a.treeValues()==b.treeValues();
        if(sourceType instanceof guru.interlis.model.GenericType a && targetType instanceof guru.interlis.model.GenericType b)return a.name().equals(b.name());
        if(sourceType instanceof EnumerationType a && targetType instanceof EnumerationType b)
            return a.ordered()==b.ordered() && a.circular()==b.circular() && a.treeValues()==b.treeValues()
                    && sameEnumBase(a.baseType(),b.baseType(),correspondence) && enumParents(a).equals(enumParents(b)) && enumFinals(a).equals(enumFinals(b));
        if(sourceType instanceof FormattedType a && targetType instanceof FormattedType b) {
            if(!a.predefined().equals(b.predefined()) || !sameMapped(a.baseDomain(),b.baseDomain(),correspondence)
                    || !sameMapped(a.structure(),b.structure(),correspondence) || !a.minimum().equals(b.minimum())
                    || !a.maximum().equals(b.maximum()) || a.parts().size()!=b.parts().size())return false;
            for(int i=0;i<a.parts().size();i++) {
                work.checkpoint();var x=a.parts().get(i);var y=b.parts().get(i);
                if(!sameMapped(x.attribute(),y.attribute(),correspondence) || !sameMapped(x.format(),y.format(),correspondence)
                        || !x.width().equals(y.width()) || !x.literal().equals(y.literal()))return false;
            }
            return true;
        }
        if(sourceType instanceof guru.interlis.model.OidType a && targetType instanceof guru.interlis.model.OidType b)
            return a.oidKind()==b.oidKind() && a.valueType().isPresent()==b.valueType().isPresent()
                    && (a.valueType().isEmpty() || sameMappedType(a.valueType().orElseThrow(),b.valueType().orElseThrow(),correspondence));
        if(sourceType instanceof guru.interlis.model.MetaObjectType a && targetType instanceof guru.interlis.model.MetaObjectType b) return sameMapped(a.target(),b.target(),correspondence);
        if (sourceType instanceof guru.interlis.model.AttributePathType a && targetType instanceof guru.interlis.model.AttributePathType b) {
            if (!sameAttributePathOwner(a.owner(), b.owner(), correspondence)
                    || a.restrictions().size() != b.restrictions().size()) return false;
            for (int i = 0; i < a.restrictions().size(); i++)
                if (!sameMappedType(a.restrictions().get(i), b.restrictions().get(i), correspondence)) return false;
            return true;
        }
        if (sourceType instanceof guru.interlis.model.ObjectArgumentType a && targetType instanceof guru.interlis.model.ObjectArgumentType b)
            return a.objects() == b.objects() && sameMapped(a.base(), b.base(), correspondence)
                    && a.restrictions().stream().map(id -> correspondence.getOrDefault(id, id)).toList().equals(b.restrictions());
        if (sourceType instanceof ClassValueType a && targetType instanceof ClassValueType b)
            return a.structureValue() == b.structureValue() && a.restrictions().stream()
                    .map(id -> correspondence.getOrDefault(id, id)).toList().equals(b.restrictions());
        if (sourceType instanceof ReferenceType sourceReference
                && targetType instanceof ReferenceType targetReference) {
            return sourceReference.external() == targetReference.external()
                    && sameMapped(sourceReference.target(), targetReference.target(), correspondence)
                    && sourceReference.restrictions().size() == targetReference.restrictions().size()
                    && java.util.stream.IntStream.range(0, sourceReference.restrictions().size()).allMatch(i ->
                        sameMapped(Optional.of(sourceReference.restrictions().get(i)),
                            Optional.of(targetReference.restrictions().get(i)), correspondence));
        }
        if (sourceType instanceof StructureValueType sourceStructure
                && targetType instanceof StructureValueType targetStructure) {
            return sameMapped(sourceStructure.target(), targetStructure.target(), correspondence)
                    && sourceStructure.restrictions().stream().map(t -> correspondence.getOrDefault(t,t)).toList().equals(targetStructure.restrictions());
        }
        if (sourceType instanceof DomainReferenceType sourceDomain
                && targetType instanceof DomainReferenceType targetDomain) {
            return sameMapped(Optional.of(sourceDomain.targetDomain()),
                    Optional.of(targetDomain.targetDomain()), correspondence);
        }
        if (sourceType instanceof CollectionType sourceCollection
                && targetType instanceof CollectionType targetCollection) {
            return sourceCollection.collectionKind()==targetCollection.collectionKind()
                    && sourceCollection.cardinality().equals(targetCollection.cardinality())
                    && sameMappedType(sourceCollection.elementType(), targetCollection.elementType(),
                    correspondence);
        }
        if (sourceType instanceof GeometryType sourceGeometry
                && targetType instanceof GeometryType targetGeometry) {
            return sourceGeometry.geometryKind()==targetGeometry.geometryKind()
                    && sameMapped(sourceGeometry.vertexDomain(), targetGeometry.vertexDomain(), correspondence)
                    && sameMapped(sourceGeometry.lineAttributes(), targetGeometry.lineAttributes(), correspondence)
                    && sourceGeometry.directed() == targetGeometry.directed()
                    && sourceGeometry.withoutOverlaps() == targetGeometry.withoutOverlaps()
                    && sameGeometryTolerance(sourceGeometry.overlap(), targetGeometry.overlap(), correspondence)
                    && sourceGeometry.lineForms().stream().map(x -> correspondence.getOrDefault(x, x))
                            .collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(targetGeometry.lineForms()));
        }
        if (sourceType instanceof NumericType a && targetType instanceof NumericType b)
            return sameNumeric(a.range(), b.range(), correspondence);
        if (sourceType instanceof CoordinateType sourceCoordinate
                && targetType instanceof CoordinateType targetCoordinate) {
            if (sourceCoordinate.multiCoordinate()!=targetCoordinate.multiCoordinate() || !sourceCoordinate.rotation().equals(targetCoordinate.rotation()) || sourceCoordinate.dimensions() != targetCoordinate.dimensions()
                    || !sourceCoordinate.referenceSystem().equals(
                    targetCoordinate.referenceSystem())) {
                return false;
            }
            for (int i = 0; i < sourceCoordinate.axes().size(); i++) {
                work.checkpoint();
                NumericRange sourceAxis = sourceCoordinate.axes().get(i);
                NumericRange targetAxis = targetCoordinate.axes().get(i);
                if (!sameNumeric(sourceAxis, targetAxis, correspondence)) {
                    return false;
                }
            }
            return true;
        }
        return sourceType instanceof BooleanType;
    }

    private boolean sameEnumBase(Optional<TypeId> source,Optional<TypeId> target,Map<SymbolId,SymbolId> map) {
        if(source.isEmpty() || target.isEmpty())return source.isEmpty() && target.isEmpty();
        var a=state.declaredTypesInBuildOrder().stream().filter(e->e.getValue().equals(source.orElseThrow())).map(Map.Entry::getKey).findFirst();
        var b=state.declaredTypesInBuildOrder().stream().filter(e->e.getValue().equals(target.orElseThrow())).map(Map.Entry::getKey).findFirst();
        return a.isPresent() && b.isPresent() ? sameMapped(a,b,map) : sameMappedType(source.orElseThrow(),target.orElseThrow(),map);
    }

    /** Enum shape uses parent ordinals, never translated names or an arbitrary nesting limit. */
    private List<Integer> enumParents(EnumerationType enumeration) {
        var paths=enumeration.values().stream().map(EnumerationValue::path).toList();var result=new ArrayList<Integer>();
        for(var path:paths){work.checkpoint();int dot=path.lastIndexOf('.');result.add(dot<0?-1:paths.indexOf(path.substring(0,dot)));}
        return result;
    }
    private Set<Integer> enumFinals(EnumerationType enumeration) {
        var paths=enumeration.values().stream().map(EnumerationValue::path).toList();var result=new HashSet<Integer>();
        for(var path:enumeration.finalSubtrees()){work.checkpoint();result.add(path.isEmpty()?-1:paths.indexOf(path));}
        return result;
    }

    private boolean sameDecimal(Optional<guru.interlis.model.DecimalLiteral> a,Optional<guru.interlis.model.DecimalLiteral> b) {
        if(a.isEmpty() || b.isEmpty())return a.isEmpty() && b.isEmpty();
        var x=a.orElseThrow();var y=b.orElseThrow();
        return x.exactValue().compareTo(y.exactValue())==0
                && (long)x.precision().exponent()-x.precision().fractionalDigits()==(long)y.precision().exponent()-y.precision().fractionalDigits();
    }

    private boolean sameGeometryTolerance(Optional<GeometryType.Tolerance> source, Optional<GeometryType.Tolerance> target,
            Map<SymbolId, SymbolId> correspondence) {
        if (source.orElse(null) instanceof GeometryType.TrackWidth a && target.orElse(null) instanceof GeometryType.TrackWidth b)
            return correspondence.getOrDefault(a.coordinateDomain(), a.coordinateDomain()).equals(b.coordinateDomain());
        if (source.orElse(null) instanceof GeometryType.Explicit a && target.orElse(null) instanceof GeometryType.Explicit b)
            return a.value().exactValue().compareTo(b.value().exactValue()) == 0;
        return source.isEmpty() && target.isEmpty();
    }

    private boolean sameNumeric(NumericRange a, NumericRange b, Map<SymbolId, SymbolId> map) {
        if (!sameDecimal(a.minimum(),b.minimum()) || !sameDecimal(a.maximum(),b.maximum()) || a.circular() != b.circular()
                || a.direction() != b.direction() || !sameMapped(a.unit(), b.unit(), map) || a.referenceSystem().isPresent() != b.referenceSystem().isPresent()) return false;
        return sameReferenceSystem(a.referenceSystem(),b.referenceSystem(),map);
    }
    private boolean sameReferenceSystem(Optional<NumericRange.ReferenceSystem> a,Optional<NumericRange.ReferenceSystem> b,Map<SymbolId,SymbolId> map) {
        if(a.isEmpty() || b.isEmpty())return a.isEmpty() && b.isEmpty();
        var x=a.orElseThrow();var y=b.orElseThrow();
        return x.domain() == y.domain() && x.axis().equals(y.axis()) && map.getOrDefault(x.target(), x.target()).equals(y.target());
    }

    private List<SymbolId> constraintReferenceTargets(ConstraintDefinition definition) {
        List<ExpressionId> ids = new ArrayList<>();
        List<SymbolId> targets = new ArrayList<>();
        switch (definition) {
            case MandatoryConstraint c -> ids.add(c.condition());
            case DomainConstraint c -> ids.add(c.condition());
            case PlausibilityConstraint c -> ids.add(c.condition());
            case guru.interlis.model.SetConstraint c -> { c.where().ifPresent(ids::add); ids.add(c.condition()); }
            case UniquenessConstraint c -> {
                c.where().ifPresent(ids::add); ids.addAll(c.keys());
                for (var local : c.localKeys()) { ids.add(local.prefix()); ids.addAll(local.fields()); }
            }
            case guru.interlis.model.ExistenceConstraint c -> {
                ids.add(c.source());
                for (var target : c.targets()) { targets.add(target.viewable()); ids.add(target.path()); }
            }
        }
        for (var id : ids) targets.addAll(expressionReferenceTargets(id));
        return targets;
    }

    private List<SymbolId> expressionReferenceTargets(ExpressionId id) {
        if (id == null) {
            return List.of();
        }
        TypedExpression expression = state.expression(id);
        if (expression == null) {
            return List.of();
        }
        List<SymbolId> targets = new ArrayList<>();
        if (expression instanceof guru.interlis.model.PathExpression path) {
            for (guru.interlis.model.PathStep step : path.path().steps()) {
                work.checkpoint();
                if (step instanceof guru.interlis.model.PathStep.ViewBaseStep base) {
                    targets.add(base.base()); targets.add(base.target());
                } else if (step instanceof guru.interlis.model.PathStep.InspectionStep inspected) {
                    targets.add(inspected.view()); targets.add(inspected.target());
                } else if (step instanceof guru.interlis.model.PathStep.AttributeStep attribute) {
                    targets.add(attribute.target());
                } else if (step instanceof guru.interlis.model.PathStep.RoleStep role) {
                    targets.add(role.target());
                } else if (step instanceof guru.interlis.model.PathStep.AssociationAccessStep access) {
                    targets.add(access.association()); targets.add(access.sourceRole()); targets.add(access.targetRole());
                } else if (step instanceof guru.interlis.model.PathStep.AssociationSourceStep access) {
                    targets.add(access.association()); targets.add(access.sourceRole());
                }
            }
        }
        if(expression instanceof guru.interlis.model.FunctionCallExpression call) {
            call.targetFunction().ifPresent(targets::add);
            for(var path:call.argumentPaths().values())path.binding().ifPresent(id2->targets.addAll(expressionReferenceTargets(id2)));
        }
        if (expression instanceof guru.interlis.model.SymbolValueExpression value) targets.add(value.target());
        if (expression instanceof guru.interlis.model.InspectionExpression inspected) {
            inspected.view().ifPresent(targets::add); targets.add(inspected.source()); targets.add(inspected.element());
        }
        if (expression instanceof guru.interlis.model.ObjectSetExpression
                && state.type(expression.typeId()) instanceof guru.interlis.model.ObjectArgumentType set) {
            set.base().ifPresent(targets::add); targets.addAll(set.restrictions());
        }
        for (ExpressionId child : expression.children()) {
            work.checkpoint();
            targets.addAll(expressionReferenceTargets(child));
        }
        return targets;
    }

    private boolean sameConstraint(ConstraintDefinition source,ConstraintDefinition target,Map<SymbolId,SymbolId> map) {
        if(source.getClass()!=target.getClass())return false;
        if(!sameExpressionLists(constraintExpressions(source),constraintExpressions(target),map))return false;
        if(source instanceof PlausibilityConstraint a && target instanceof PlausibilityConstraint b
                && (a.direction()!=b.direction() || a.percentage().compareTo(b.percentage())!=0))return false;
        if(source instanceof guru.interlis.model.SetConstraint a && target instanceof guru.interlis.model.SetConstraint b
                && (a.scope()!=b.scope() || a.where().isPresent()!=b.where().isPresent()))return false;
        if(source instanceof UniquenessConstraint a && target instanceof UniquenessConstraint b
                && (a.basket()!=b.basket() || a.where().isPresent()!=b.where().isPresent()
                    || a.keys().size()!=b.keys().size() || a.localKeys().size()!=b.localKeys().size()
                    || !a.localKeys().stream().map(k->k.fields().size()).toList().equals(b.localKeys().stream().map(k->k.fields().size()).toList())))return false;
        if(source instanceof guru.interlis.model.ExistenceConstraint a && target instanceof guru.interlis.model.ExistenceConstraint b
                && a.targets().size()!=b.targets().size())return false;
        var originalTargets=constraintReferenceTargets(source).stream().map(id->map.getOrDefault(id,id)).toList();
        if(!originalTargets.equals(constraintReferenceTargets(target)))return false;
        return true;
    }

    private boolean constraintHasErrors(ConstraintDefinition definition) {
        return constraintExpressions(definition).stream().map(state::expression)
                .anyMatch(e->e!=null&&e.typeState()==guru.interlis.model.ExpressionTypeState.ERROR);
    }

    private List<ExpressionId> constraintExpressions(ConstraintDefinition definition) {
        var ids=new ArrayList<ExpressionId>();
        switch(definition) {
            case MandatoryConstraint c -> ids.add(c.condition());
            case DomainConstraint c -> ids.add(c.condition());
            case PlausibilityConstraint c -> ids.add(c.condition());
            case guru.interlis.model.SetConstraint c -> {c.where().ifPresent(ids::add);ids.add(c.condition());}
            case UniquenessConstraint c -> {c.where().ifPresent(ids::add);ids.addAll(c.keys());for(var k:c.localKeys()){ids.add(k.prefix());ids.addAll(k.fields());}}
            case guru.interlis.model.ExistenceConstraint c -> {ids.add(c.source());for(var t:c.targets())ids.add(t.path());}
        }
        return ids;
    }

    /** Structural expression comparison uses bound identities; documentary spelling is irrelevant. */
    boolean sameBoundExpression(ExpressionId source,ExpressionId target) {
        return sameExpression(source,target,Map.of());
    }

    private boolean sameExpression(ExpressionId source,ExpressionId target,Map<SymbolId,SymbolId> map) {
        work.checkpoint();var a=state.expression(source);var b=state.expression(target);
        if(a==null || b==null || a.getClass()!=b.getClass())return false;
        if(a instanceof guru.interlis.model.LiteralExpression x && b instanceof guru.interlis.model.LiteralExpression y) {
            if(x.literalKind()!=y.literalKind())return false;
            return switch(x.literalKind()) {
                case NUMBER -> x.decimal().isPresent() && y.decimal().isPresent()
                        && x.decimal().orElseThrow().exactValue().compareTo(y.decimal().orElseThrow().exactValue())==0
                        && sameNumericContext(x.typeId(),y.typeId(),map);
                case TEXT -> x.textValue().equals(y.textValue());
            };
        }
        if(a instanceof guru.interlis.model.BinaryExpression x && b instanceof guru.interlis.model.BinaryExpression y && x.operator()!=y.operator())return false;
        if(a instanceof guru.interlis.model.UnaryExpression x && b instanceof guru.interlis.model.UnaryExpression y && x.operator()!=y.operator())return false;
        if(a instanceof guru.interlis.model.FunctionCallExpression x && b instanceof guru.interlis.model.FunctionCallExpression y) {
            if(!sameMapped(x.targetFunction(),y.targetFunction(),map)
                    || x.targetFunction().isEmpty() && !x.functionName().equals(y.functionName())
                    || !x.argumentPaths().keySet().equals(y.argumentPaths().keySet()) || x.children().size()!=y.children().size())return false;
            for(int i=0;i<x.children().size();i++) {
                var p=x.argumentPaths().get(i);var q=y.argumentPaths().get(i);
                if(p==null) {if(!sameExpression(x.children().get(i),y.children().get(i),map))return false;}
                else {
                    if(p.rootArgument()!=q.rootArgument() || p.binding().isPresent()!=q.binding().isPresent())return false;
                    if(p.binding().isPresent()) {if(!sameExpression(p.binding().orElseThrow(),q.binding().orElseThrow(),map))return false;}
                    else if(!p.steps().stream().map(v->List.of(v.name(),v.associationAccess(),v.associationQualifier(),v.index())).toList()
                            .equals(q.steps().stream().map(v->List.of(v.name(),v.associationAccess(),v.associationQualifier(),v.index())).toList()))return false;
                }
            }
            return true;
        }
        if(a instanceof guru.interlis.model.PathExpression x && b instanceof guru.interlis.model.PathExpression y) {
            if(x.path().steps().size()!=y.path().steps().size())return false;
            for(int i=0;i<x.path().steps().size();i++)if(!samePathStep(x.path().steps().get(i),y.path().steps().get(i),map))return false;
            return true;
        }
        if(a instanceof guru.interlis.model.SymbolValueExpression x && b instanceof guru.interlis.model.SymbolValueExpression y)
            return map.getOrDefault(x.target(),x.target()).equals(y.target());
        if(a instanceof guru.interlis.model.ConstantExpression x && b instanceof guru.interlis.model.ConstantExpression y) {
            var at=effectiveDescriptor.apply(x.typeId());var bt=effectiveDescriptor.apply(y.typeId());
            if(at instanceof EnumerationType e && bt instanceof EnumerationType f && x.constantName().startsWith("#") && y.constantName().startsWith("#"))
                return sameMappedType(e.id(),f.id(),map) && enumValueIndex(e,x.constantName().substring(1))==enumValueIndex(f,y.constantName().substring(1));
            return x.constantName().equals(y.constantName()) && sameNumericContext(x.typeId(),y.typeId(),map);
        }
        if (a instanceof guru.interlis.model.ObjectSetExpression x && b instanceof guru.interlis.model.ObjectSetExpression y)
            return x.explicitSelection() == y.explicitSelection() && sameMappedType(a.typeId(), b.typeId(), map);
        if(a instanceof guru.interlis.model.InspectionExpression x && b instanceof guru.interlis.model.InspectionExpression y) {
            if(x.area()!=y.area() || !sameMapped(x.view(),y.view(),map) || !map.getOrDefault(x.source(),x.source()).equals(y.source())
                    || x.restriction().isPresent()!=y.restriction().isPresent())return false;
            if(!sameExpressionLists(x.restriction().stream().toList(),y.restriction().stream().toList(),map)
                    || !sameExpression(x.path(),y.path(),map))return false;
            return sameInspectionTarget(x.element(),y.element(),map);
        }
        if(a instanceof guru.interlis.model.ErrorExpression)return false;
        return sameExpressionLists(a.children(),b.children(),map);
    }

    private boolean sameNumericContext(TypeId source,TypeId target,Map<SymbolId,SymbolId> map) {
        var a=effectiveDescriptor.apply(source);var b=effectiveDescriptor.apply(target);
        if(a instanceof NumericType x && b instanceof NumericType y)
            return sameMapped(x.range().unit(),y.range().unit(),map)
                    && sameReferenceSystem(x.range().referenceSystem(),y.range().referenceSystem(),map);
        return a!=null && b!=null && a.getClass()==b.getClass();
    }
    private int enumValueIndex(EnumerationType enumeration,String path) {
        return enumeration.values().stream().map(EnumerationValue::path).toList().indexOf(path);
    }
    private boolean samePathStep(guru.interlis.model.PathStep source,guru.interlis.model.PathStep target,Map<SymbolId,SymbolId> map) {
        work.checkpoint();if(source.getClass()!=target.getClass())return false;
        return switch(source) {
            case guru.interlis.model.PathStep.AttributeStep a -> sameInspectionTarget(a.target(),((guru.interlis.model.PathStep.AttributeStep)target).target(),map);
            case guru.interlis.model.PathStep.RoleStep a -> sameInspectionTarget(a.target(),((guru.interlis.model.PathStep.RoleStep)target).target(),map);
            case guru.interlis.model.PathStep.IndexStep a -> a.index().equals(((guru.interlis.model.PathStep.IndexStep)target).index());
            case guru.interlis.model.PathStep.ViewBaseStep a -> {var b=(guru.interlis.model.PathStep.ViewBaseStep)target;yield sameInspectionTarget(a.base(),b.base(),map) && sameInspectionTarget(a.target(),b.target(),map);}
            case guru.interlis.model.PathStep.InspectionStep a -> {var b=(guru.interlis.model.PathStep.InspectionStep)target;yield a.kind()==b.kind() && sameInspectionTarget(a.view(),b.view(),map) && sameInspectionTarget(a.target(),b.target(),map);}
            case guru.interlis.model.PathStep.AssociationAccessStep a -> {var b=(guru.interlis.model.PathStep.AssociationAccessStep)target;yield a.associationObject()==b.associationObject() && a.cardinality().equals(b.cardinality())
                    && sameInspectionTarget(a.association(),b.association(),map) && sameInspectionTarget(a.sourceRole(),b.sourceRole(),map) && sameInspectionTarget(a.targetRole(),b.targetRole(),map);}
            case guru.interlis.model.PathStep.AssociationSourceStep a -> {var b=(guru.interlis.model.PathStep.AssociationSourceStep)target;yield a.cardinality().equals(b.cardinality()) && sameInspectionTarget(a.association(),b.association(),map) && sameInspectionTarget(a.sourceRole(),b.sourceRole(),map);}
            case guru.interlis.model.PathStep.ThisStep ignored -> true;
            case guru.interlis.model.PathStep.ParentStep ignored -> true;
            case guru.interlis.model.PathStep.UnresolvedStep ignored -> false;
        };
    }

    /** A registered geometry context may extend correspondence only after its actual
     * bound geometry agrees. No generated identifier or display name is interpreted. */
    private boolean sameInspectionTarget(SymbolId source, SymbolId target, Map<SymbolId,SymbolId> map) {
        if (map.getOrDefault(source,source).equals(target)) return true;
        var a=views.geometryContext(source);var b=views.geometryContext(target);
        if (a==null || b==null
                || !map.getOrDefault(a.segments().contextOwner(),a.segments().contextOwner()).equals(b.segments().contextOwner())
                || !sameMappedType(a.geometry().id(),b.geometry().id(),map)) return false;
        var left=a.segments().contextualSymbols();var right=b.segments().contextualSymbols();
        if(left.size()!=right.size())return false;
        var candidate=new LinkedHashMap<>(map);
        for(var pair:left.entrySet()) {
            work.checkpoint();
            var original=map.getOrDefault(pair.getKey(),pair.getKey());
            var translated=right.get(original);
            if(translated==null)return false;
            var previous=candidate.putIfAbsent(pair.getValue(),translated);
            if(previous!=null && !previous.equals(translated))return false;
        }
        var leftWrappers=views.geometryWrappers(a);var rightWrappers=views.geometryWrappers(b);
        if(!leftWrappers.keySet().equals(rightWrappers.keySet()))return false;
        for(var role:leftWrappers.keySet()) {
            work.checkpoint();
            var original=state.symbol(leftWrappers.get(role));var translated=state.symbol(rightWrappers.get(role));
            if(original==null || translated==null || !correspondenceInto(original,translated,candidate))return false;
        }
        if(!candidate.getOrDefault(source,source).equals(target))return false;
        // New context copies are bijective. Existing ordinary correspondences are
        // never replaced, and two different copies cannot collapse onto one target.
        var usedTargets = new HashMap<SymbolId,SymbolId>();
        map.forEach((original,translated) -> usedTargets.putIfAbsent(translated,original));
        for(var pair:candidate.entrySet()) if(!map.containsKey(pair.getKey())) {
            work.checkpoint();var previous=usedTargets.putIfAbsent(pair.getValue(),pair.getKey());
            if(previous!=null && !previous.equals(pair.getKey()))return false;
        }
        // Validation is transactional: a rejected context cannot poison later comparisons.
        candidate.forEach(map::putIfAbsent);
        return true;
    }

    private boolean sameUnitConversion(UnitSymbol left, UnitSymbol right) {
        if (left.conversionFunction().isPresent() != right.conversionFunction().isPresent()
                || left.conversionFactors().size() != right.conversionFactors().size()) return false;
        for (int i = 0; i < left.conversionFactors().size(); i++) {
            work.checkpoint();
            var a = left.conversionFactors().get(i); var b = right.conversionFactors().get(i);
            if (a.operator() != b.operator() || a.kind() != b.kind()
                    || a.decimal().isPresent() != b.decimal().isPresent()
                    || a.decimal().isPresent() && a.decimal().get().exactValue().compareTo(b.decimal().get().exactValue()) != 0)
                return false;
        }
        return true;
    }

    private boolean sameDrawingValue(guru.interlis.model.DrawingRuleSymbol.Value left,guru.interlis.model.DrawingRuleSymbol.Value right,Map<SymbolId,SymbolId> map) {
        if(left instanceof guru.interlis.model.DrawingRuleSymbol.ExpressionValue a && right instanceof guru.interlis.model.DrawingRuleSymbol.ExpressionValue b)
            return sameExpressionLists(List.of(a.expression()),List.of(b.expression()),map);
        if(left instanceof guru.interlis.model.DrawingRuleSymbol.MetaObjectValue a && right instanceof guru.interlis.model.DrawingRuleSymbol.MetaObjectValue b)
            return sameMapped(a.object(),b.object(),map);
        if(left instanceof guru.interlis.model.DrawingRuleSymbol.AccordingValue a && right instanceof guru.interlis.model.DrawingRuleSymbol.AccordingValue b) {
            if(!sameExpressionLists(List.of(a.selector()),List.of(b.selector()),map) || a.cases().size()!=b.cases().size()) return false;
            var at=effectiveDescriptor.apply(state.expression(a.selector()).typeId()); var bt=effectiveDescriptor.apply(state.expression(b.selector()).typeId());
            if(!(at instanceof EnumerationType ae && bt instanceof EnumerationType be)) return false;
            var ap=ae.values().stream().map(guru.interlis.model.EnumerationValue::path).toList(); var bp=be.values().stream().map(guru.interlis.model.EnumerationValue::path).toList();
            for(int i=0;i<a.cases().size();i++) {
                var x=a.cases().get(i); var y=b.cases().get(i);
                if(ap.indexOf(x.first())!=bp.indexOf(y.first()) || !x.last().map(ap::indexOf).equals(y.last().map(bp::indexOf)) || !sameDrawingValue(x.value(),y.value(),map)) return false;
            }
            return true;
        }
        return false;
    }
}
