package guru.interlis.compiler.internal;

import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.AnalysisCoverage;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.BindingResolution;
import guru.interlis.model.Cardinality;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.ClassValueType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.FormattedType;
import guru.interlis.model.internal.FormattedValues;
import guru.interlis.model.GenericType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ConstraintDefinition;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.CoverageStatus;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DeclaredName;
import guru.interlis.model.Documentation;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveMemberOrigin;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.EnumElementSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.ErrorExpression;
import guru.interlis.model.ErrorType;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.ModelImport;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.NameReference;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.PendingReason;
import guru.interlis.model.PendingType;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.ReferenceSegment;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.ScopeId;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.UnitSymbol;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.AlignmentType;
import guru.interlis.model.BlackboxType;
import guru.interlis.model.BooleanType;
import guru.interlis.model.FunctionParameter;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.model.UnsupportedSymbol;
import guru.interlis.model.ViewSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.AssociationDeclarationSyntax;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.BinaryExpressionSyntax;
import guru.interlis.syntax.BooleanTypeSyntax;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.AlignmentTypeSyntax;
import guru.interlis.syntax.BlackboxTypeSyntax;
import guru.interlis.syntax.ClassValueTypeSyntax;
import guru.interlis.syntax.CollectionTypeSyntax;
import guru.interlis.syntax.OidTypeSyntax;
import guru.interlis.syntax.ConstantExpressionSyntax;
import guru.interlis.syntax.ConstraintDeclarationSyntax;
import guru.interlis.syntax.CoordinateTypeSyntax;
import guru.interlis.syntax.DefinedExpressionSyntax;
import guru.interlis.syntax.DomainDeclarationSyntax;
import guru.interlis.syntax.ExternalConstraintSyntax;
import guru.interlis.syntax.FormattedTypeSyntax;
import guru.interlis.syntax.BuiltinFunctionExpressionSyntax;
import guru.interlis.syntax.ClassConstantExpressionSyntax;
import guru.interlis.syntax.GenericConstantExpressionSyntax;
import guru.interlis.syntax.GenericTypeSyntax;
import guru.interlis.syntax.EnumTreeValueSyntax;
import guru.interlis.syntax.EnumerationConstantExpressionSyntax;
import guru.interlis.syntax.FunctionCallExpressionSyntax;
import guru.interlis.syntax.LineTypeSyntax;
import guru.interlis.syntax.ViewDeclarationSyntax;
import guru.interlis.syntax.UnitDeclarationSyntax;
import guru.interlis.syntax.NumericTypeSyntax;
import guru.interlis.syntax.FunctionDeclarationSyntax;
import guru.interlis.syntax.DomainReferenceSyntax;
import guru.interlis.syntax.EnumerationTypeSyntax;
import guru.interlis.syntax.ExpressionSyntax;
import guru.interlis.syntax.LiteralExpressionSyntax;
import guru.interlis.syntax.ParenthesizedExpressionSyntax;
import guru.interlis.syntax.PathExpressionSyntax;
import guru.interlis.syntax.ReferenceTypeSyntax;
import guru.interlis.syntax.RoleDeclarationSyntax;
import guru.interlis.syntax.StructureDeclarationSyntax;
import guru.interlis.syntax.TemporalTypeSyntax;
import guru.interlis.syntax.TextTypeSyntax;
import guru.interlis.syntax.TopicSyntax;
import guru.interlis.syntax.TypeSyntax;
import guru.interlis.syntax.UnaryExpressionSyntax;
import guru.interlis.syntax.UndefinedExpressionSyntax;
import guru.interlis.syntax.UnsupportedExpressionSyntax;

import java.math.BigInteger;
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

/** Builds the immutable semantic graph for the parsed declarations. */
final class GraphBuilder {
    final IndexedDeclarations index;
    private final SourceSet sourceSet;
    private final Closure closure;
    private final guru.interlis.model.SnapshotId snapshotId;
    final NameResolver resolver;
    private final DiagnosticCollector collector;
    private final Map<SourceId, Set<String>> syntaxUnsupportedBySource;
    final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
    final Map<TypeId, TypeDescriptor> types = new LinkedHashMap<>();
    final Map<ExpressionId, TypedExpression> expressions = new LinkedHashMap<>();
    private final Map<ReferenceId, NameReference> references = new LinkedHashMap<>();
    private final Set<ReferenceSite> recordedReferences = new HashSet<>();
    final Map<SymbolId, TypeId> declaredTypes = new LinkedHashMap<>();
    private final Map<SymbolId, EffectiveType> effectiveTypes = new LinkedHashMap<>();
    final Map<SymbolId, List<EffectiveMember>> effectiveMembers = new LinkedHashMap<>();
    private final Map<SymbolId, SymbolId> externalConstraintTargets = new LinkedHashMap<>();
    private final Set<SymbolId> resolvedObjectBases = new HashSet<>();
    private final Set<SymbolId> resolvingDomainTypes = new HashSet<>();
    private final Set<SymbolId> resolvingAttributeTypes = new HashSet<>();
    private final Set<SymbolId> buildingAssociations = new HashSet<>();
    final Map<SymbolId, SymbolId> baseOf = new LinkedHashMap<>();
    private final Map<SymbolId, SymbolId> modelIdByDecl = new LinkedHashMap<>();
    private final Map<SourceId, Set<String>> unsupportedBySource = new LinkedHashMap<>();
    private final Map<String, DeclInfo> modelNames = new LinkedHashMap<>();
    private int typeCounter;
    private int expressionCounter;
    private int referenceCounter;
    private final guru.interlis.execution.CancellationToken cancellation;
    private final List<guru.interlis.diagnostics.Diagnostic> syntaxErrors;
    private final long maximumWork;
    private long work;
    private final Map<guru.interlis.model.LanguageVersion,TypeId> booleanTypes = new java.util.EnumMap<>(guru.interlis.model.LanguageVersion.class);

    GraphBuilder(IndexedDeclarations index, SourceSet sourceSet, Closure closure,
                 DiagnosticCollector collector, guru.interlis.model.SnapshotId snapshotId,
                 Map<SourceId, Set<String>> syntaxUnsupportedBySource,
                 guru.interlis.execution.CancellationToken cancellation,
                 guru.interlis.compiler.AnalysisBudget budget) {
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.maximumWork = Objects.requireNonNull(budget, "budget").maxSemanticWorkUnits();
        this.index = Objects.requireNonNull(index, "index");
        this.sourceSet = Objects.requireNonNull(sourceSet, "sourceSet");
        this.closure = Objects.requireNonNull(closure, "closure");
        this.collector = Objects.requireNonNull(collector, "collector");
        this.syntaxErrors = collector.snapshot().stream().filter(d -> d.severity() == Severity.ERROR
                && (d.phase() == Phase.LEXING || d.phase() == Phase.PARSING)).toList();
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.syntaxUnsupportedBySource = Map.copyOf(
                Objects.requireNonNull(syntaxUnsupportedBySource, "syntaxUnsupportedBySource"));
        this.resolver = new NameResolver(index, closure);
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            checkWork();
            modelIdByDecl.put(entry.declaration().id(), entry.id());
        }
    }

    void checkWork() {
        cancellation.throwIfCancellationRequested();
        if (++work > maximumWork) {
            throw new AnalysisBudgetExceededException(
                    "maxSemanticWorkUnits", maximumWork);
        }
    }

    final LineForms lines = new LineForms(this);
    final GraphicBuilder graphics = new GraphicBuilder(this);
    final MetadataBindings metadata = new MetadataBindings(this);
    final StructureBindings structures = new StructureBindings(this);
    final FunctionBindings functions = new FunctionBindings(this);
    final PredefinedTemporal temporal = new PredefinedTemporal(this);

    GraphResult build() {
        syntaxUnsupportedBySource.forEach((source, features) -> {
            Set<String> known = unsupportedBySource.computeIfAbsent(source,
                    ignored -> new LinkedHashSet<>());
            for (String feature : features) {
                checkWork();
                if (known.add(feature)) {
                    SourceText text = sourceSet.sources().get(source);
                    SourceRange range = text == null ? null : text.range(0, 0);
                    emitUnsupported(source, range, feature, feature);
                }
            }
        });
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            checkWork();
            for (DeclInfo child : index.children(entry.id())) {
                checkWork();
                buildSymbol(child);
            }
        }
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            checkWork();
            buildModel(entry);
        }
        validateUnits();
        resolveOverridesAndEffectiveMembers();
        structures.validate();
        lines.validateConcreteMembers();
        metadata.validate();
        validateGenericSystem();
        bindOidRules();
        validateTopicDependencies();
        views.validateDependencies();
        graphics.validateDependencies();
        validateReferenceTargets();
        buildEffectiveTypes();
        validateFormattedUses();
        validateNumericUses();
        buildReferences();
        validateAssociationRules();
        validateAttributeOverrides();
        validateClassExtensions();
        validateConstraintNames();
        validateTranslations();
        bindTransferNamespaces();
        Set<SymbolId> modelIds = closure.models().stream()
                .map(m -> findModelId(m.name(), m.declarationOffset(), m.sourceId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<SymbolId> rootIds = closure.roots().stream()
                .map(m -> findModelId(m.name(), m.declarationOffset(), m.sourceId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        Set<guru.interlis.model.LanguageVersion> languages = new LinkedHashSet<>();
        for (ModelSymbol model : symbols.values().stream()
                .filter(s -> s instanceof ModelSymbol)
                .map(s -> (ModelSymbol) s)
                .filter(s -> modelIds.contains(s.id()))
                .toList()) {
            checkWork();
            languages.add(model.header().languageVersion());
        }
        PredefinedModels.finish(this, languages);
        SemanticGraph graph = new SemanticGraph(
                snapshotId,
                symbols.values(), types.values(), expressions.values().stream().toList(),
                references.values(), declaredTypes, effectiveTypes, effectiveMembers,
                rootIds, new ArrayList<>(modelIds), languages,
                sourceSet.sources().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().revision())));
        AnalysisCoverage coverage = buildCoverage();
        return new GraphResult(graph, coverage, rootIds, new ArrayList<>(modelIds),
                unsupportedBySource);
    }

    /** Named constraints share the declaring viewable's local namespace, including
     * same-topic CONSTRAINTS OF blocks. Inherited-topic contexts remain distinct. */
    private void validateConstraintNames() {
        record ConstraintScope(SymbolId target, SymbolId topic) {}
        Map<ConstraintScope, Set<String>> names = new LinkedHashMap<>();
        var declarations = index.all().stream()
                .filter(d -> d.kind() == SymbolKind.CONSTRAINT && d.language() == guru.interlis.model.LanguageVersion.ILI24)
                .sorted(Comparator.comparing((DeclInfo d) -> d.sourceId().value())
                        .thenComparingInt(d -> d.syntax().range().startOffset())).toList();
        for (var declaration : declarations) {
            checkWork();
            var syntax = declaration.syntax() instanceof ExternalConstraintSyntax external
                    ? external.constraint() : (ConstraintDeclarationSyntax) declaration.syntax();
            if (syntax.name().isEmpty()) continue;
            SymbolId target = externalConstraintTargets.getOrDefault(declaration.id(), declaration.owner().orElse(null));
            var symbol = symbols.get(target);
            if (!(symbol instanceof ClassSymbol || symbol instanceof StructureSymbol
                    || symbol instanceof AssociationSymbol || symbol instanceof ViewSymbol)) continue;
            var scope = new ConstraintScope(target, scopeRoot(declaration.id()));
            if (!names.computeIfAbsent(scope, ignored -> new LinkedHashSet<>()).add(syntax.name().orElseThrow()))
                emitRule(declaration, "constraint names must be unique within their declaring class",
                        "CONSTRAINT_NAME", declaration.nameRange().orElse(syntax.range()));
        }
    }

    private void validateTranslations() {
        List<ModelSymbol> models = symbols.values().stream()
                .filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast).toList();
        Map<SymbolId, SymbolId> originals = new LinkedHashMap<>();
        for (var model : models) model.translation().ifPresent(translation -> {
            var candidates = models.stream().filter(candidate -> candidate.name().value().equals(translation.sourceModel()))
                    .filter(candidate -> translation.sourceVersion().isEmpty() || translation.sourceVersion().orElseThrow().equals(candidate.modelVersion())).toList();
            if (candidates.size() == 1) originals.put(model.id(), candidates.getFirst().id());
        });
        for (ModelSymbol model : models) {
            checkWork();
            if (model.translation().isEmpty()) {
                continue;
            }
            guru.interlis.model.ModelTranslation translation = model.translation().orElseThrow();
            ModelSymbol source = (ModelSymbol) symbols.get(originals.get(model.id()));
            DeclInfo decl = index.byId(model.id()).orElse(null);
            if (decl != null && translationRoot(model.id(), originals) == null) {
                emitRule(decl, "translation dependency has no original model", "TRANSLATION_CYCLE");
                continue;
            }
            if (source == null) {
                if (decl != null) {
                    emitRule(decl, "translation source model not found", "TRANSLATION_SOURCE");
                }
                continue;
            }
            if (translation.sourceVersion().isPresent()
                    && !translation.sourceVersion().orElseThrow().equals(source.modelVersion())) {
                if (decl != null) {
                    emitRule(decl, "translation source version mismatch", "TRANSLATION_VERSION");
                }
            }
            if (decl != null) {
                Map<SymbolId, SymbolId> correspondence = new LinkedHashMap<>();
                if (!correspondenceWithImports(source, model, originals, correspondence, new HashSet<>())) correspondence.clear();
                if (correspondence.isEmpty()
                        || !sameModelProfile(source, model)) {
                    emitRule(decl, "translation syntax does not match source model",
                            "TRANSLATION_STRUCTURE");
                }
                if (!correspondence.isEmpty()) {
                    var checked = new LinkedHashSet<SymbolId>();
                    while (true) {
                        checkWork();
                        var pending = correspondence.entrySet().stream().filter(e -> !checked.contains(e.getKey()))
                                .map(e -> new guru.interlis.model.TranslationLink(e.getKey(), e.getValue())).toList();
                        if (pending.isEmpty()) break;
                        for (var pair : pending) {
                            checkWork();
                            checked.add(pair.original());
                            var left=symbols.get(pair.original());var right=symbols.get(pair.translated());
                            if(left!=null && right!=null)compareSymbolReferences(left,right,correspondence,decl);
                        }
                    }
                }
                // Expression/formation checks may add verified contextualized declarations.
                // Publish the final immutable links only after all newly added pairs were checked.
                var bound = new guru.interlis.model.ModelTranslation(translation.sourceModel(), translation.sourceVersion(), translation.language(),
                        Optional.of(source.id()), correspondence.entrySet().stream().map(e -> new guru.interlis.model.TranslationLink(e.getKey(), e.getValue())).toList());
                symbols.put(model.id(), new ModelSymbol(model.header(), model.modelKind(), model.contracted(), model.modelVersion(),
                        model.issuer(), model.imports(), model.members(), Optional.of(bound), model.options(), model.transferNamespace()));
            }
        }
    }

    private boolean sameModelProfile(ModelSymbol source, ModelSymbol target) {
        if (source.modelKind() != target.modelKind()
                || source.header().languageVersion() == guru.interlis.model.LanguageVersion.ILI23 && source.contracted() != target.contracted()
                || source.options().incrementalTransferAllowed() != target.options().incrementalTransferAllowed()
                || !source.options().characterSet().map(v -> CharacterSets.canonical(v.value()).orElse(v.value()))
                        .equals(target.options().characterSet().map(v -> CharacterSets.canonical(v.value()).orElse(v.value())))
                || source.imports().size() != target.imports().size()) {
            return false;
        }
        return true;
    }

    Optional<SymbolId> baseTopicMember(SymbolId context, String name,
                                               Set<SymbolKind> expected) {
        SymbolId current = scopeRoot(context);
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            checkWork();
            DeclInfo topicDecl = index.byId(current).orElse(null);
            if (topicDecl == null || !(topicDecl.syntax() instanceof guru.interlis.syntax.TopicSyntax syntax)
                    || syntax.baseTopic().isEmpty()) {
                return Optional.empty();
            }
            Optional<SymbolId> base = tryResolve(topicDecl, syntax.baseTopic().orElseThrow(),
                    Set.of(SymbolKind.TOPIC));
            if (base.isEmpty()) {
                return Optional.empty();
            }
            for (DeclInfo child : index.children(base.orElseThrow())) {
                checkWork();
                if (expected.contains(child.kind()) && child.name().equals(name)) {
                    return Optional.of(child.id());
                }
            }
            current = base.orElseThrow();
        }
        return Optional.empty();
    }

    /** Import pairs follow their bound targets, never the available language variants. */
    private boolean correspondenceWithImports(ModelSymbol source, ModelSymbol target,
            Map<SymbolId, SymbolId> originals, Map<SymbolId, SymbolId> map,
            Set<List<SymbolId>> visited) {
        checkWork();
        if (!visited.add(List.of(source.id(), target.id()))) return true;
        if (!correspondenceInto(source, target, map) || source.imports().size() != target.imports().size()) return false;
        Set<Integer> used = new HashSet<>();
        for (var imported : source.imports()) {
            checkWork();
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
                var a = symbols.get(imported.targetModel().orElseThrow());
                var b = symbols.get(target.imports().get(selected).targetModel().orElseThrow());
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
            checkWork();
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
                var left = findMember(a.id(), "AGGREGATES");
                var right = findMember(b.id(), "AGGREGATES");
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
            checkWork();
            Symbol source = symbols.get(sourceIds.get(i));
            Symbol target = symbols.get(targetIds.get(i));
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
            checkWork();
            var previous = map.putIfAbsent(sourceIds.get(i), targetIds.get(i));
            if (previous != null && !previous.equals(targetIds.get(i))) return false;
        }
        return true;
    }

    private void compareSymbolReferences(Symbol source, Symbol target,
                                         Map<SymbolId, SymbolId> correspondence,
                                         DeclInfo translationDecl) {
        if(!source.header().modifiers().equals(target.header().modifiers()))
            emitRule(translationDecl,"translation declaration modifiers differ","TRANSLATION_STRUCTURE");
        if(source instanceof AttributeSymbol a && target instanceof AttributeSymbol b
                && (a.mandatory()!=b.mandatory() || a.transientAttribute()!=b.transientAttribute()
                    || a.continuous()!=b.continuous() || a.subdivision()!=b.subdivision()))
            emitRule(translationDecl,"translation attribute flags differ","TRANSLATION_REFERENCE");
        if(source instanceof RoleSymbol a && target instanceof RoleSymbol b
                && (!a.cardinality().equals(b.cardinality()) || a.aggregation()!=b.aggregation()
                    || a.ordered()!=b.ordered() || a.external()!=b.external() || a.hiding()!=b.hiding()))
            emitRule(translationDecl,"translation role properties differ","TRANSLATION_REFERENCE");
        if(source instanceof AssociationSymbol a && target instanceof AssociationSymbol b
                && (!a.cardinality().equals(b.cardinality()) || !sameMapped(a.derivedFrom(),b.derivedFrom(),correspondence)))
            emitRule(translationDecl,"translation association properties differ","TRANSLATION_REFERENCE");
        if(source instanceof DomainSymbol a && target instanceof DomainSymbol b && a.generic()!=b.generic())
            emitRule(translationDecl,"translation domain generic flag differs","TRANSLATION_REFERENCE");
        if(source instanceof TopicSymbol a && target instanceof TopicSymbol b
                && !new HashSet<>(a.dependencies().stream().map(id->correspondence.getOrDefault(id,id)).toList()).equals(new HashSet<>(b.dependencies())))
            emitRule(translationDecl,"translation topic dependencies differ","TRANSLATION_REFERENCE");
        if(source instanceof ConstraintSymbol a && target instanceof ConstraintSymbol b) {
            boolean same = sameMapped(a.target(),b.target(),correspondence)
                    && sameConstraint(a.definition(),b.definition(),correspondence);
            var targets=constraintReferenceTargets(a.definition()).stream().map(id->correspondence.getOrDefault(id,id)).toList();
            if(!sameMapped(a.target(),b.target(),correspondence) || !targets.equals(constraintReferenceTargets(b.definition())))
                emitRule(translationDecl,"translation constraint targets differ","TRANSLATION_REFERENCE");
            else if(!same)
                emitRule(translationDecl,"translation constraint structure differs","TRANSLATION_CONSTRAINT");
        }
        var sourceCrs=Optional.ofNullable(source.header().metaAttributes().values().get("CRS"));
        var targetCrs=Optional.ofNullable(target.header().metaAttributes().values().get("CRS"));
        if(effectiveDescriptor(declaredTypes.get(source.id())) instanceof CoordinateType
                && !sourceCrs.map(MetaAttributeSet.Value::text).equals(targetCrs.map(MetaAttributeSet.Value::text))) {
            var location=targetCrs.map(MetaAttributeSet.Value::origin).filter(Origin.DeclaredOrigin.class::isInstance).map(Origin.DeclaredOrigin.class::cast)
                    .map(Origin.DeclaredOrigin::range).orElseGet(()->target.origin() instanceof Origin.DeclaredOrigin declared?declared.range():translationDecl.syntax().range());
            emitRule(translationDecl,"translation coordinate reference systems differ","TRANSLATION_COORDINATE",location);
        }
        if (source instanceof ViewSymbol a && target instanceof ViewSymbol b) {
            if (!sameMapped(a.baseView(), b.baseView(), correspondence)
                    || !viewFormationShape(a).equals(viewFormationShape(b))
                    || !sameExpressionLists(a.selections(), b.selections(), correspondence)
                    || !sameExpressionLists(formationExpressions(a), formationExpressions(b), correspondence)
                    || !a.baseExtensions().stream().map(e -> List.of(correspondence.getOrDefault(e.base(), e.base()),
                            e.extensions().stream().map(id -> correspondence.getOrDefault(id, id)).toList())).toList()
                        .equals(b.baseExtensions().stream().map(e -> List.of(e.base(), e.extensions())).toList()))
                emitRule(translationDecl, "translation view formation or selection differs", "TRANSLATION_REFERENCE");
        }
        if (source instanceof ViewSymbol a && target instanceof ViewSymbol b
                && a.formation().orElse(null) instanceof ViewSymbol.Inspection x
                && b.formation().orElse(null) instanceof ViewSymbol.Inspection y
                && (!sameExpressionLists(List.of(x.path()),List.of(y.path()),correspondence)
                    || !sameInspectionTarget(x.element(),y.element(),correspondence)
                    || !sameInspectionTarget(x.parent(),y.parent(),correspondence)))
            emitRule(translationDecl,"translation inspection context differs","TRANSLATION_REFERENCE");
        if (source instanceof ViewSymbol.Base a && target instanceof ViewSymbol.Base b
                && (a.orNull() != b.orNull() || !correspondence.getOrDefault(a.target(), a.target()).equals(b.target())))
            emitRule(translationDecl, "translation view basis differs", "TRANSLATION_REFERENCE");
        if (source instanceof guru.interlis.model.GraphicSymbol a && target instanceof guru.interlis.model.GraphicSymbol b
                && (!sameMapped(a.baseGraphic(),b.baseGraphic(),correspondence) || !sameMapped(a.basis(),b.basis(),correspondence)
                    || !sameExpressionLists(a.selections(),b.selections(),correspondence)))
            emitRule(translationDecl,"translated graphic basis or selections differ","TRANSLATION_GRAPHIC");
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
            if(!same) emitRule(translationDecl,"translated drawing rule bindings or assignments differ","TRANSLATION_DRAWING_RULE");
        }
        if (source instanceof FunctionSymbol a && target instanceof FunctionSymbol b) {
            boolean same = a.parameters().size() == b.parameters().size()
                    && a.explanation().ranges().isEmpty()==b.explanation().ranges().isEmpty() && a.returnMandatory()==b.returnMandatory() && sameMappedType(a.returnType(), b.returnType(), correspondence);
            for (int i = 0; same && i < a.parameters().size(); i++)
                same = a.parameters().get(i).mandatory()==b.parameters().get(i).mandatory() && sameMappedType(a.parameters().get(i).typeId(), b.parameters().get(i).typeId(), correspondence);
            if (!same) emitRule(translationDecl, "translation function argument targets differ", "TRANSLATION_FUNCTION");
        }
        if (source instanceof guru.interlis.model.ParameterSymbol a && target instanceof guru.interlis.model.ParameterSymbol b
                && (a.mandatory() != b.mandatory() || !sameMapped(a.overrides(),b.overrides(),correspondence) || !sameMappedType(a.typeId(), b.typeId(), correspondence)
                   ))
            emitRule(translationDecl, "translation parameter type or mandatory flag differs", "TRANSLATION_PARAMETER");
        if (source instanceof AttributeSymbol a && target instanceof AttributeSymbol b
                && (!sameExpressionLists(a.derivations(), b.derivations(), correspondence)
                    || !sameMapped(a.overrides(), b.overrides(), correspondence)
                    || !sameMappedType(a.declaredType(), b.declaredType(), correspondence)))
            emitRule(translationDecl, "translation attribute type, override or derivation differs", "TRANSLATION_REFERENCE");
        if (source instanceof RoleSymbol a && target instanceof RoleSymbol b
                && !sameExpressionLists(a.derivation().stream().toList(), b.derivation().stream().toList(), correspondence))
            emitRule(translationDecl, "translation role derivation differs", "TRANSLATION_REFERENCE");
        var sourceRules = source.header().oidRules(); var targetRules = target.header().oidRules();
        if (sourceRules.size() != targetRules.size()) emitRule(translationDecl, "translation OID rules differ", "TRANSLATION_REFERENCE");
        for (int i = 0; i < Math.min(sourceRules.size(), targetRules.size()); i++) {
            var a = sourceRules.get(i); var b = targetRules.get(i);
            boolean equivalent = sameMapped(a.domain(), b.domain(), correspondence);
            if (!equivalent && a.domain().isPresent() && b.domain().isPresent()) {
                Symbol left = symbols.get(a.domain().orElseThrow()), right = symbols.get(b.domain().orElseThrow());
                equivalent = left != null && right != null && left.header().origin() instanceof Origin.SyntheticOrigin
                        && right.header().origin() instanceof Origin.SyntheticOrigin && left.name().equals(right.name());
            }
            if (a.basket() != b.basket() || !equivalent)
                emitRule(translationDecl, "translation OID domain differs", "TRANSLATION_REFERENCE");
        }
        if (source instanceof ClassSymbol sourceClass && target instanceof ClassSymbol targetClass) {
            if (!sameMapped(sourceClass.baseType(), targetClass.baseType(), correspondence)) {
                emitRule(translationDecl, "translation class base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof StructureSymbol sourceStructure
                && target instanceof StructureSymbol targetStructure) {
            if (!sameMapped(sourceStructure.baseType(), targetStructure.baseType(), correspondence)) {
                emitRule(translationDecl, "translation structure base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof TopicSymbol sourceTopic && target instanceof TopicSymbol targetTopic) {
            if (!sameMapped(sourceTopic.baseTopic(), targetTopic.baseTopic(), correspondence)) {
                emitRule(translationDecl, "translation topic base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof AssociationSymbol sourceAssociation && target instanceof AssociationSymbol targetAssociation
                && !sameMapped(sourceAssociation.baseAssociation(), targetAssociation.baseAssociation(), correspondence)) {
            emitRule(translationDecl, "translation association base differs", "TRANSLATION_REFERENCE");
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
                emitRule(translationDecl, "translation role target differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof guru.interlis.model.MetadataBasketSymbol a && target instanceof guru.interlis.model.MetadataBasketSymbol b
                && (a.basketKind() != b.basketKind() || !sameMapped(a.topic(), b.topic(), correspondence) || !sameMapped(a.baseBasket(), b.baseBasket(), correspondence)))
            emitRule(translationDecl, "translated metadata basket has different bindings", "TRANSLATION_METADATA_BASKET");
        if (source instanceof guru.interlis.model.MetaObjectSymbol a && target instanceof guru.interlis.model.MetaObjectSymbol b
                && (!a.name().equals(b.name()) || !sameMapped(a.objectClass(), b.objectClass(), correspondence)))
            emitRule(translationDecl, "translated metadata object has a different name or class", "TRANSLATION_METAOBJECT");
        if (source instanceof guru.interlis.model.LineFormSymbol a && target instanceof guru.interlis.model.LineFormSymbol b
                && !sameMapped(a.segmentStructure(), b.segmentStructure(), correspondence))
            emitRule(translationDecl, "translated line form targets a different segment structure", "TRANSLATION_LINE_FORM");
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
                emitRule(translationDecl, "translation unit base differs",
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
            if (!same) emitRule(translationDecl, "translation context targets differ", "TRANSLATION_REFERENCE");
        }
        if (source instanceof TopicSymbol original && target instanceof TopicSymbol translated
                && !original.deferredGenerics().stream().map(id -> correspondence.getOrDefault(id, id)).toList().equals(translated.deferredGenerics()))
            emitRule(translationDecl, "translation deferred domains differ", "TRANSLATION_REFERENCE");
        if (source instanceof DomainSymbol sourceDomain
                && target instanceof DomainSymbol targetDomain) {
            if (!sameMapped(sourceDomain.baseDomain(), targetDomain.baseDomain(),
                    correspondence)) {
                emitRule(translationDecl, "translation domain base differs",
                        "TRANSLATION_REFERENCE");
            }
            if (!sameMappedType(sourceDomain.typeId(), targetDomain.typeId(),
                    correspondence)) {
                emitRule(translationDecl, "translation domain type differs",
                        "TRANSLATION_REFERENCE");
            }
        }
    }

    private boolean sameExpressionLists(List<ExpressionId> source, List<ExpressionId> target,
            Map<SymbolId, SymbolId> correspondence) {
        if (source.size() != target.size()) return false;
        for (int i = 0; i < source.size(); i++) {
            checkWork();
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
        checkWork();
        TypeDescriptor sourceType = types.get(source);
        TypeDescriptor targetType = types.get(target);
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
                checkWork();var x=a.parts().get(i);var y=b.parts().get(i);
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
                checkWork();
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
        var a=declaredTypes.entrySet().stream().filter(e->e.getValue().equals(source.orElseThrow())).map(Map.Entry::getKey).findFirst();
        var b=declaredTypes.entrySet().stream().filter(e->e.getValue().equals(target.orElseThrow())).map(Map.Entry::getKey).findFirst();
        return a.isPresent() && b.isPresent() ? sameMapped(a,b,map) : sameMappedType(source.orElseThrow(),target.orElseThrow(),map);
    }

    /** Enum shape uses parent ordinals, never translated names or an arbitrary nesting limit. */
    private List<Integer> enumParents(EnumerationType enumeration) {
        var paths=enumeration.values().stream().map(EnumerationValue::path).toList();var result=new ArrayList<Integer>();
        for(var path:paths){checkWork();int dot=path.lastIndexOf('.');result.add(dot<0?-1:paths.indexOf(path.substring(0,dot)));}
        return result;
    }
    private Set<Integer> enumFinals(EnumerationType enumeration) {
        var paths=enumeration.values().stream().map(EnumerationValue::path).toList();var result=new HashSet<Integer>();
        for(var path:enumeration.finalSubtrees()){checkWork();result.add(path.isEmpty()?-1:paths.indexOf(path));}
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
        TypedExpression expression = expressions.get(id);
        if (expression == null) {
            return List.of();
        }
        List<SymbolId> targets = new ArrayList<>();
        if (expression instanceof guru.interlis.model.PathExpression path) {
            for (guru.interlis.model.PathStep step : path.path().steps()) {
                checkWork();
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
                && types.get(expression.typeId()) instanceof guru.interlis.model.ObjectArgumentType set) {
            set.base().ifPresent(targets::add); targets.addAll(set.restrictions());
        }
        for (ExpressionId child : expression.children()) {
            checkWork();
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
    private boolean sameExpression(ExpressionId source,ExpressionId target,Map<SymbolId,SymbolId> map) {
        checkWork();var a=expressions.get(source);var b=expressions.get(target);
        if(a==null || b==null || a.getClass()!=b.getClass())return false;
        if(a instanceof guru.interlis.model.LiteralExpression x && b instanceof guru.interlis.model.LiteralExpression y) {
            if(x.literalKind()!=y.literalKind())return false;
            return switch(x.literalKind()) {
                case NUMBER -> x.decimal().isPresent() && y.decimal().isPresent()
                        && x.decimal().orElseThrow().exactValue().compareTo(y.decimal().orElseThrow().exactValue())==0
                        && sameNumericContext(x.typeId(),y.typeId(),map);
                case TEXT -> x.textValue().equals(y.textValue());
                case BOOLEAN -> x.lexeme().equals(y.lexeme());
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
            var at=effectiveDescriptor(x.typeId());var bt=effectiveDescriptor(y.typeId());
            if(at instanceof EnumerationType e && bt instanceof EnumerationType f && x.constantName().startsWith("#") && y.constantName().startsWith("#"))
                return sameMappedType(e.id(),f.id(),map) && enumValueIndex(e,x.constantName().substring(1))==enumValueIndex(f,y.constantName().substring(1));
            return x.constantName().equals(y.constantName()) && sameNumericContext(x.typeId(),y.typeId(),map);
        }
        if(a instanceof guru.interlis.model.ObjectSetExpression)return sameMappedType(a.typeId(),b.typeId(),map);
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
        var a=effectiveDescriptor(source);var b=effectiveDescriptor(target);
        if(a instanceof NumericType x && b instanceof NumericType y)
            return sameMapped(x.range().unit(),y.range().unit(),map)
                    && sameReferenceSystem(x.range().referenceSystem(),y.range().referenceSystem(),map);
        return a!=null && b!=null && a.getClass()==b.getClass();
    }
    private int enumValueIndex(EnumerationType enumeration,String path) {
        return enumeration.values().stream().map(EnumerationValue::path).toList().indexOf(path);
    }
    private boolean samePathStep(guru.interlis.model.PathStep source,guru.interlis.model.PathStep target,Map<SymbolId,SymbolId> map) {
        checkWork();if(source.getClass()!=target.getClass())return false;
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
            checkWork();
            var original=map.getOrDefault(pair.getKey(),pair.getKey());
            var translated=right.get(original);
            if(translated==null)return false;
            var previous=candidate.putIfAbsent(pair.getValue(),translated);
            if(previous!=null && !previous.equals(translated))return false;
        }
        var leftWrappers=views.geometryWrappers(a);var rightWrappers=views.geometryWrappers(b);
        if(!leftWrappers.keySet().equals(rightWrappers.keySet()))return false;
        for(var role:leftWrappers.keySet()) {
            checkWork();
            var original=symbols.get(leftWrappers.get(role));var translated=symbols.get(rightWrappers.get(role));
            if(original==null || translated==null || !correspondenceInto(original,translated,candidate))return false;
        }
        if(!candidate.getOrDefault(source,source).equals(target))return false;
        // New context copies are bijective. Existing ordinary correspondences are
        // never replaced, and two different copies cannot collapse onto one target.
        var usedTargets = new HashMap<SymbolId,SymbolId>();
        map.forEach((original,translated) -> usedTargets.putIfAbsent(translated,original));
        for(var pair:candidate.entrySet()) if(!map.containsKey(pair.getKey())) {
            checkWork();var previous=usedTargets.putIfAbsent(pair.getValue(),pair.getKey());
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
            checkWork();
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
            var at=effectiveDescriptor(expressions.get(a.selector()).typeId()); var bt=effectiveDescriptor(expressions.get(b.selector()).typeId());
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

    private void validateClassExtensions() {
        Set<SymbolId> extendedBases = new LinkedHashSet<>();
        Map<SymbolId, List<SymbolId>> subclasses = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (!(symbol instanceof ClassSymbol clazz) || clazz.baseType().isEmpty()) {
                continue;
            }
            SymbolId base = clazz.baseType().orElseThrow();
            subclasses.computeIfAbsent(base, ignored -> new ArrayList<>()).add(clazz.id());
            Symbol baseSymbol = symbols.get(base);
            if (baseSymbol != null && baseSymbol.header().modifiers()
                    .contains(guru.interlis.model.Modifier.EXTENDED)) {
                extendedBases.add(base);
            }
        }
        Map<String, SymbolId> extendedByTopic = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (symbol instanceof ClassSymbol clazz
                    && clazz.header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                SymbolId topic = scopeRoot(clazz.id());
                if (topic != null) {
                    extendedByTopic.put(topic + ":" + clazz.name().value(), clazz.id());
                }
            }
        }
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (!(symbol instanceof ClassSymbol clazz) || clazz.baseType().isEmpty()) {
                continue;
            }
            SymbolId base = clazz.baseType().orElseThrow();
            Symbol baseSymbol = symbols.get(base);
            SymbolId topic = scopeRoot(clazz.id());
            if (baseSymbol != null && topic != null) {
                SymbolId localExtended = extendedByTopic.get(topic + ":" + baseSymbol.name().value());
                if (localExtended != null && !localExtended.equals(base) && !localExtended.equals(clazz.id())) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            emitRule(decl, "subclass must extend the local EXTENDED class",
                                    "EXTENDED_BASE_CLASS"));
                } else if (localExtended != null
                        && symbols.get(topic) instanceof TopicSymbol topicSymbol
                        && topicSymbol.baseTopic().isEmpty()) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            emitRule(decl, "EXTENDED class may only be extended in a derived topic",
                                    "EXTENDED_BASE_CLASS"));
                }
            }
        }
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (!(symbol instanceof ClassSymbol clazz)
                    || !clazz.header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                continue;
            }
            SymbolId topic = scopeRoot(clazz.id());
            if (!(symbols.get(topic) instanceof TopicSymbol topicSymbol)
                    || topicSymbol.baseTopic().isEmpty()) {
                continue;
            }
            SymbolId baseTopic = topicSymbol.baseTopic().orElseThrow();
            for (DeclInfo baseChild : index.children(baseTopic)) {
                checkWork();
                boolean specialized = index.children(baseTopic).stream()
                        .filter(child -> child.kind() == SymbolKind.CLASS)
                        .anyMatch(child -> baseOf.get(child.id()) != null
                                && baseOf.get(child.id()).equals(baseChild.id()));
                if (baseChild.kind() == SymbolKind.CLASS
                        && baseChild.name().equals(clazz.name().value()) && specialized) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            emitRule(decl, "EXTENDED class conflicts with a specialized base class",
                                    "EXTENDED_CLASS_CONFLICT"));
                }
            }
        }
    }

    private void validateAttributeOverrides() {
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (!(symbol instanceof ClassSymbol) && !(symbol instanceof StructureSymbol)) {
                continue;
            }
            SymbolId base = baseOf.get(symbol.id());
            if (base == null) {
                continue;
            }
            List<SymbolId> members = symbol instanceof ClassSymbol classSymbol
                    ? classSymbol.members() : ((StructureSymbol) symbol).members();
            for (SymbolId member : members) {
                checkWork();
                Symbol memberSymbol = symbols.get(member);
                if (!(memberSymbol instanceof AttributeSymbol attribute)) {
                    continue;
                }
                DeclInfo attributeDecl = index.byId(member).orElse(null);
                if (attributeDecl == null
                        || !attributeDecl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                    continue;
                }
                AttributeSymbol baseAttribute = findBaseAttribute(base, attributeDecl.name());
                if (baseAttribute == null) {
                    continue;
                }
                validateAttributeOverrideProperties(attributeDecl, attribute, baseAttribute);
                TypeDescriptor baseType = effectiveDescriptor(baseAttribute.declaredType());
                TypeDescriptor derivedType = effectiveDescriptor(attribute.declaredType());
                if (baseType != null && derivedType != null) {
                    String problem = overrideProblem(baseType, derivedType, attributeDecl.language());
                    if (problem != null) {
                        emitRule(attributeDecl, problem, "ATTRIBUTE_OVERRIDE_TYPE");
                    }
                }
            }
        }
    }

    /** Shared mandatory, transient and domain-identity rules for attribute overrides. */
    void validateAttributeOverrideProperties(DeclInfo attributeDecl, AttributeSymbol attribute, AttributeSymbol baseAttribute) {
        if (baseAttribute.mandatory() && !attribute.mandatory()) {
            emitRule(attributeDecl, "extended attribute may not remove MANDATORY",
                    "ATTRIBUTE_OVERRIDE_MANDATORY");
        }
        if (baseAttribute.transientAttribute() != attribute.transientAttribute()) {
            emitRule(attributeDecl, "extended attribute changes TRANSIENT mode",
                    "ATTRIBUTE_OVERRIDE_TRANSIENT");
        }
        if (types.get(baseAttribute.declaredType()) instanceof DomainReferenceType baseDomainRef
                && types.get(attribute.declaredType()) instanceof DomainReferenceType derivedDomainRef
                && !domainExtends(derivedDomainRef.targetDomain(), baseDomainRef.targetDomain())) {
            emitRule(attributeDecl, "extended attribute changes domain",
                    "ATTRIBUTE_OVERRIDE_TYPE");
        }
    }

    private AttributeSymbol findBaseAttribute(SymbolId base, String name) {
        SymbolId current = base;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            checkWork();
            Symbol symbol = symbols.get(current);
            List<SymbolId> members = symbol instanceof ClassSymbol classSymbol
                    ? classSymbol.members()
                    : symbol instanceof StructureSymbol structure ? structure.members() : List.of();
            for (SymbolId member : members) {
                checkWork();
                Symbol memberSymbol = symbols.get(member);
                if (memberSymbol instanceof AttributeSymbol attribute && attribute.name().value().equals(name)) {
                    return attribute;
                }
            }
            current = baseOf.get(current);
        }
        return null;
    }

    private boolean domainExtends(SymbolId derived, SymbolId base) {
        SymbolId current = derived;
        Set<SymbolId> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            checkWork();
            if (current.equals(base)) {
                return true;
            }
            current = baseOf.get(current);
        }
        return false;
    }

    private String overrideProblem(TypeDescriptor base, TypeDescriptor derived, guru.interlis.model.LanguageVersion language) {
        var relation=extension(derived,base,language);
        return relation.decision()==guru.interlis.model.RelationDecision.ALLOWED?null:relation.reason();
    }

    private guru.interlis.model.TypeRelation extension(TypeDescriptor derived, TypeDescriptor base, guru.interlis.model.LanguageVersion language) {
        return guru.interlis.model.internal.TypeCompatibility.extend(derived,base,language,this::effectiveDescriptor,
                baseOf::get,id->effectiveDescriptor(declaredTypes.get(id)),this::extensionUnit);
    }

    private void validateAssociationRules() {
        Map<SymbolId, Set<String>> classAttributes = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (symbol instanceof ClassSymbol clazz) {
                Set<String> names = new LinkedHashSet<>();
                SymbolId current = clazz.id();
                Set<SymbolId> seen = new HashSet<>();
                while (current != null && seen.add(current)) {
                    checkWork();
                    Symbol currentSymbol = symbols.get(current);
                    if (currentSymbol instanceof ClassSymbol || currentSymbol instanceof StructureSymbol) {
                        var inheritedMembers=currentSymbol instanceof ClassSymbol c?c.members():((StructureSymbol)currentSymbol).members();
                        for (SymbolId member : inheritedMembers) {
                            checkWork();
                            Symbol memberSymbol = symbols.get(member);
                            if (memberSymbol instanceof AttributeSymbol attribute) {
                                names.add(attribute.name().value());
                            }
                        }
                    }
                    current = baseOf.get(current);
                }
                classAttributes.put(clazz.id(), names);
            }
        }
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (symbol instanceof AssociationSymbol association) {
                Set<String> roleNames = new LinkedHashSet<>();
                for (SymbolId roleId : association.roles()) {
                    checkWork();
                    Symbol role = symbols.get(roleId);
                    if (role != null) {
                        roleNames.add(role.name().value());
                    }
                }
                for (SymbolId memberId : association.attributes()) {
                    checkWork();
                    Symbol member = symbols.get(memberId);
                    if (member != null && roleNames.contains(member.name().value())) {
                        DeclInfo associationDecl = index.byId(association.id()).orElse(null);
                        if (associationDecl != null) {
                            emitRule(associationDecl,
                                    "role and attribute share a name in the association",
                                    "ROLE_ATTRIBUTE_CONFLICT");
                        }
                    }
                }
            }
        }
        Map<SymbolId, Set<String>> topicRoleNames = new LinkedHashMap<>();
        Map<SymbolId, Set<String>> topicAttributeNames = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (symbol instanceof AssociationSymbol association) {
                SymbolId scope = scopeRoot(association.id());
                if (scope != null && !association.name().value().startsWith("<anonymous")) {
                    for (SymbolId roleId : association.roles()) {
                        checkWork();
                        Symbol role = symbols.get(roleId);
                        if (role != null) {
                            topicRoleNames.computeIfAbsent(scope, ignored -> new LinkedHashSet<>())
                                    .add(role.name().value());
                        }
                    }
                }
            } else if (symbol instanceof ClassSymbol clazz) {
                SymbolId scope = scopeRoot(clazz.id());
                if (scope != null) {
                    for (SymbolId member : clazz.members()) {
                        checkWork();
                        Symbol memberSymbol = symbols.get(member);
                        if (memberSymbol instanceof AttributeSymbol attribute) {
                            topicAttributeNames.computeIfAbsent(scope,
                                    ignored -> new LinkedHashSet<>()).add(attribute.name().value());
                        }
                    }
                }
            }
        }
        for (Map.Entry<SymbolId, Set<String>> entry : topicRoleNames.entrySet()) {
            checkWork();
            Set<String> attributes = topicAttributeNames.getOrDefault(entry.getKey(), Set.of());
            for (String roleName : entry.getValue()) {
                checkWork();
                if (attributes.contains(roleName)) {
                    DeclInfo scope = index.byId(entry.getKey()).orElse(null);
                    if (scope != null) {
                        emitRule(scope, "role name conflicts with an attribute name in the topic",
                                "ROLE_ATTRIBUTE_CONFLICT");
                    }
                }
            }
        }
        Map<SymbolId, Map<String, DeclInfo>> roleNamesByTarget = new LinkedHashMap<>();
        for (AssociationSymbol association : symbols.values().stream()
                .filter(AssociationSymbol.class::isInstance)
                .map(AssociationSymbol.class::cast).toList()) {
            checkWork();
            DeclInfo decl = index.byId(association.id()).orElse(null);
            if (decl == null) {
                continue;
            }
            guru.interlis.syntax.AssociationDeclarationSyntax syntax =
                    (guru.interlis.syntax.AssociationDeclarationSyntax) decl.syntax();
            List<RoleSymbol> effectiveRoles = associationRoles(association.id());
            if (effectiveRoles.size() < 2) emitRule(decl, "association requires at least two effective roles", "ASSOCIATION_ROLES");
            for (RoleSymbol role : effectiveRoles) {
                if (role.targetRoots().stream().anyMatch(t -> targetSpecializes(association.id(), t)
                        && index.byId(t).map(DeclInfo::kind).orElse(null) == SymbolKind.ASSOCIATION)) {
                    emitRule(decl, "association may not specialize a role target", "ASSOCIATION_TARGET_BASE");
                }
            }
            for (RoleSymbol hiding : effectiveRoles) {
                if (!hiding.hiding()) continue;
                for (Symbol other : symbols.values()) {
                    checkWork();
                    if (!(other instanceof AssociationSymbol sibling)
                            || baseChain(association.id()).contains(sibling.id())
                            || baseChain(sibling.id()).contains(association.id())
                            || baseChain(association.id()).stream().noneMatch(baseChain(sibling.id())::contains)) continue;
                    for (RoleSymbol role : associationRoles(sibling.id())) {
                        if (!role.hiding() && role.name().equals(hiding.name())
                                && role.targetRoots().stream().anyMatch(t -> hiding.targetRoots().stream()
                                .anyMatch(h -> targetSpecializes(t, h) || targetSpecializes(h, t)))) {
                            emitRule(decl, "HIDING conflicts with a non-hiding sibling role", "ROLE_HIDING_CONFLICT");
                        }
                    }
                }
            }
            for (SymbolId roleId : ((AssociationSymbol) symbols.get(decl.id())).roles()) {
                checkWork();
                Symbol roleSymbol = symbols.get(roleId);
                if (roleSymbol instanceof RoleSymbol role) {
                    for (Map.Entry<SymbolId, Set<String>> entry : classAttributes.entrySet()) {
                        checkWork();
                        SymbolId classId = entry.getKey();
                        boolean subtype = roleAccepts(role, classId);
                        if (subtype && entry.getValue().contains(role.name().value())) {
                            emitRule(decl, "role name conflicts with an attribute in its target hierarchy",
                                    "ROLE_ATTRIBUTE_CONFLICT");
                            break;
                        }
                    }
                }
            }
            long aggregations = effectiveRoles.stream()
                    .filter(role -> role.aggregation() != RoleAggregation.NONE).count();
            if (aggregations > 1) {
                emitRule(decl, "only one aggregation role is allowed", "MULTIPLE_AGGREGATION");
            }
            boolean associationAbstract = syntax.modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT);
            for (guru.interlis.syntax.RoleDeclarationSyntax roleSyntax : syntax.roles()) {
                checkWork();
                if (roleSyntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                        && !associationAbstract) {
                    DeclInfo roleDecl = index.children(decl.id()).stream()
                            .filter(child -> child.kind() == SymbolKind.ROLE
                                    && child.name().equals(roleSyntax.name()))
                            .findFirst().orElse(decl);
                    emitRule(roleDecl, "abstract role requires an abstract association",
                            "ABSTRACT_ROLE");
                }
                if (roleSyntax.aggregation() != RoleAggregation.COMPOSITION
                        || !roleSyntax.explicitCardinality()) {
                    continue;
                }
                boolean one = roleSyntax.cardinality().lower().equals(java.math.BigInteger.ONE)
                        && roleSyntax.cardinality().upper()
                        instanceof guru.interlis.model.CardinalityUpper.Finite finite
                        && finite.value().equals(java.math.BigInteger.ONE);
                if (!one) {
                    DeclInfo roleDecl = index.children(decl.id()).stream()
                            .filter(child -> child.kind() == SymbolKind.ROLE
                                    && child.name().equals(roleSyntax.name()))
                            .findFirst().orElse(decl);
                    emitRule(roleDecl, "composition role cardinality must be 1", "COMPOSITION_CARDINALITY");
                }
            }
            List<RoleSymbol> roles = association.roles().stream()
                    .map(symbols::get).filter(RoleSymbol.class::isInstance)
                    .map(RoleSymbol.class::cast).toList();
            if (roles.size() == 2) {
                roles.get(0).targetRoots().forEach(t -> addRoleNamespace(roleNamesByTarget, t, roles.get(1)));
                roles.get(1).targetRoots().forEach(t -> addRoleNamespace(roleNamesByTarget, t, roles.get(0)));
            }
        }
        for (ClassSymbol classSymbol : symbols.values().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast).toList()) {
            checkWork();
            Set<SymbolId> chain = new LinkedHashSet<>();
            SymbolId current = classSymbol.id();
            Set<SymbolId> seen = new HashSet<>();
            while (current != null && chain.add(current)) {
                checkWork();
                current = baseOf.get(current);
            }
            for (SymbolId classId : chain) {
                checkWork();
                Map<String, DeclInfo> names = roleNamesByTarget.get(classId);
                if (names == null) {
                    continue;
                }
                ClassSymbol currentClass = (ClassSymbol) symbols.get(classId);
                if (currentClass == null) {
                    continue;
                }
                for (SymbolId member : currentClass.members()) {
                    checkWork();
                    DeclInfo memberDecl = index.byId(member).orElse(null);
                    if (memberDecl == null || memberDecl.kind() != SymbolKind.ATTRIBUTE) {
                        continue;
                    }
                    boolean conflict = names.containsKey(memberDecl.name());
                    SymbolId memberTopic = scopeRoot(classSymbol.id());
                    for (AssociationSymbol association : symbols.values().stream()
                            .filter(AssociationSymbol.class::isInstance)
                            .map(AssociationSymbol.class::cast).toList()) {
                        checkWork();
                        for (SymbolId roleId : association.roles()) {
                            checkWork();
                            DeclInfo roleDecl = index.byId(roleId).orElse(null);
                            if (roleDecl == null || !roleDecl.name().equals(memberDecl.name())) {
                                continue;
                            }
                            if (memberTopic != null && memberTopic.equals(scopeRoot(roleId))) {
                                conflict = true;
                            }
                        }
                    }
                    for (AssociationSymbol association : symbols.values().stream()
                            .filter(AssociationSymbol.class::isInstance)
                            .map(AssociationSymbol.class::cast).toList()) {
                        checkWork();
                        for (SymbolId roleId : association.roles()) {
                            checkWork();
                            if (!(symbols.get(roleId) instanceof RoleSymbol role)
                                    || !roleAccepts(role, classId)) {
                                continue;
                            }
                            DeclInfo roleDecl = index.byId(roleId).orElse(null);
                            if (roleDecl != null && roleDecl.name().equals(memberDecl.name())) {
                                conflict = true;
                            }
                        }
                    }
                    if (conflict) {
                        emitRule(memberDecl, "attribute name conflicts with role name",
                                "ATTRIBUTE_ROLE_CONFLICT");
                    }
                }
            }
        }
    }

    private void addRoleNamespace(Map<SymbolId, Map<String, DeclInfo>> namesByTarget,
                                  SymbolId target, RoleSymbol role) {
        DeclInfo roleDecl = index.byId(role.id()).orElse(null);
        if (roleDecl == null) {
            return;
        }
        Map<String, DeclInfo> names = namesByTarget.computeIfAbsent(target,
                ignored -> new LinkedHashMap<>());
        DeclInfo previous = names.putIfAbsent(roleDecl.name(), roleDecl);
        if (previous != null && !previous.id().equals(roleDecl.id())) {
            SymbolId firstTopic = scopeRoot(role.id());
            SymbolId secondTopic = scopeRoot(previous.id());
            boolean sharedBase = firstTopic != null && secondTopic != null
                    && !firstTopic.equals(secondTopic)
                    && shareBaseTopic(firstTopic, secondTopic);
            SymbolId currentOwner = role.header().owner().orElseThrow();
            SymbolId previousOwner = previous.owner().orElseThrow();
            boolean sameFamily = baseChain(currentOwner).stream().anyMatch(baseChain(previousOwner)::contains);
            if (!sharedBase && !sameFamily) {
                emitRule(roleDecl, "role name conflicts with another role in the same namespace",
                        "ROLE_NAMESPACE_CONFLICT");
            }
        }
    }


    private guru.interlis.model.ContextSymbol buildContext(DeclInfo decl) {
        var syntax = (guru.interlis.syntax.ContextDeclarationSyntax) decl.syntax();
        if (index.byId(decl.owner().orElseThrow()).orElseThrow().kind() != SymbolKind.MODEL)
            emitRule(decl, "CONTEXT must be declared at model level", "CONTEXT_SCOPE");
        if (index.children(decl.owner().orElseThrow()).stream()
                .anyMatch(d -> !d.id().equals(decl.id()) && d.name().equals(decl.name())))
            emitRule(decl, "context name must be unique", "CONTEXT_NAME_UNIQUE");
        List<guru.interlis.model.ContextSymbol.Mapping> mappings = new ArrayList<>();
        Set<SymbolId> own = new HashSet<>();
        for (var mapping : syntax.mappings()) {
            checkWork();
            var generic = bindTarget(decl, mapping.genericDomain(), Set.of(SymbolKind.DOMAIN),
                    ReferenceUsage.TYPE_USE, new ArrayList<>());
            List<SymbolId> alternatives = new ArrayList<>();
            for (var name : mapping.alternatives()) bindTarget(decl, name, Set.of(SymbolKind.DOMAIN),
                    ReferenceUsage.TYPE_USE, new ArrayList<>()).ifPresent(alternatives::add);
            if (generic.isEmpty()) continue;
            if (!own.add(generic.get())) emitRule(decl, "duplicate generic mapping in context", "CONTEXT_DUPLICATE");
            if (new HashSet<>(alternatives).size() != alternatives.size())
                emitRule(decl, "duplicate concrete alternative", "CONTEXT_DUPLICATE");
            mappings.add(new guru.interlis.model.ContextSymbol.Mapping(generic.get(), alternatives, mapping.genericDomain().range()));
        }
        return new guru.interlis.model.ContextSymbol(header(decl), mappings);
    }

    /** Bound contexts are model-local; their effects flow only along explicit import edges. */
    private void validateGenericSystem() {
        for (Symbol symbol : symbols.values()) {
            checkWork();
            if (symbol instanceof DomainSymbol d && d.generic()
                    && !(effectiveDescriptor(d.typeId()) instanceof CoordinateType))
                emitRule(index.byId(d.id()).orElseThrow(), "GENERIC requires a coordinate domain", "GENERIC_TYPE");
            if (!(symbol instanceof guru.interlis.model.ContextSymbol context)) continue;
            DeclInfo decl = index.byId(context.id()).orElseThrow();
            for (var mapping : context.mappings()) {
                var generic = (DomainSymbol) symbols.get(mapping.genericDomain());
                if (!generic.generic() || !(effectiveDescriptor(generic.typeId()) instanceof CoordinateType base)) {
                    emitRule(decl, "CONTEXT domain must be GENERIC coordinates", "CONTEXT_GENERIC"); continue;
                }
                for (SymbolId id : mapping.alternatives()) {
                    checkWork();
                    var concrete = (DomainSymbol) symbols.get(id);
                    if (concrete.generic() || concrete.header().modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                            || !(effectiveDescriptor(concrete.typeId()) instanceof CoordinateType target)
                            || target.axes().stream().anyMatch(a -> a.minimum().isEmpty() || a.maximum().isEmpty())) {
                        emitRule(decl, "CONTEXT alternative must be concrete coordinates", "CONTEXT_CONCRETE"); continue;
                    }
                    for (SymbolId parent : baseChain(concrete.id()).stream().skip(1).toList()) {
                        if (symbols.get(parent) instanceof DomainSymbol ancestor
                                && effectiveDescriptor(ancestor.typeId()) instanceof CoordinateType inherited
                                && !guru.interlis.model.internal.TypeCompatibility.coordinateRefines(target, inherited))
                            emitRule(decl, "CONTEXT alternative widens its declared coordinate base", "CONTEXT_ALTERNATIVE_EXTENSION");
                    }
                    if (base.dimensions() != target.dimensions() || base.multiCoordinate() != target.multiCoordinate())
                        emitRule(decl, "CONTEXT target dimensions differ", "GENERIC_DIMENSIONS");
                    else if (!guru.interlis.model.internal.TypeCompatibility.coordinateRefines(target, base))
                        emitRule(decl, "CONTEXT target must refine the generic coordinates", "GENERIC_RANGE");
                }
            }
        }
        Map<SymbolId, Map<SymbolId, TopicSymbol.GenericBinding>> contexts = new LinkedHashMap<>();
        for (var model : index.models()) modelContexts(model.id(), contexts, new HashSet<>());
        for (Symbol symbol : List.copyOf(symbols.values())) {
            checkWork();
            if (!(symbol instanceof TopicSymbol topic) || index.byId(topic.id()).isEmpty()) continue;
            DeclInfo decl = index.byId(topic.id()).orElseThrow();
            SymbolId model = index.modelOfSymbol(topic.id()).orElseThrow();
            Set<SymbolId> visible = importedModels(model);
            Set<SymbolId> used = new LinkedHashSet<>();
            Set<SymbolId> viewables = new LinkedHashSet<>();
            for (SymbolId id : baseChain(topic.id())) for (DeclInfo child : index.children(id))
                if (Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW).contains(child.kind())) viewables.add(child.id());
            // EXTENDED replaces the inherited declaration in this topic's effective population.
            var overridden = new HashSet<SymbolId>();
            for (SymbolId id : viewables) if (symbols.get(id).header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED))
                overridden.addAll(baseChain(id).stream().filter(base -> !base.equals(id)).toList());
            viewables.removeAll(overridden);
            Set<TypeId> seen = new HashSet<>();
            for (SymbolId id : viewables) collectViewableGenerics(id, visible, topic.id(), used, seen);
            var available = contexts.getOrDefault(model, Map.of());
            Set<SymbolId> declaredDeferred = new LinkedHashSet<>();
            for (SymbolId id : baseChain(topic.id())) if (symbols.get(id) instanceof TopicSymbol t) declaredDeferred.addAll(t.deferredGenerics());
            for (SymbolId id : topic.deferredGenerics()) {
                if (!(symbols.get(id) instanceof DomainSymbol domain) || !domain.generic())
                    emitRule(decl, "DEFERRED GENERICS references a non-generic domain", "GENERIC_DEFERRED_UNKNOWN");
                if (!used.contains(id)) emitRule(decl, "DEFERRED GENERICS domain is unused", "GENERIC_DEFERRED_UNUSED");
                if (!available.containsKey(id)) emitRule(decl, "DEFERRED GENERICS domain has no CONTEXT", "GENERIC_CONTEXT_MISSING");
            }
            List<TopicSymbol.GenericBinding> bindings = new ArrayList<>();
            for (SymbolId generic : used) {
                var binding = available.get(generic);
                if (binding == null) {
                    if (topic.header().modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))
                        bindings.add(new TopicSymbol.GenericBinding(generic, List.of(), List.of(), TopicSymbol.BindingState.ABSTRACT));
                    else emitRule(decl, "generic domain is used without an effective CONTEXT", "GENERIC_CONTEXT_MISSING");
                } else if (binding.alternatives().size() > 1 && !declaredDeferred.contains(generic)) {
                    emitRule(decl, "multiple CONTEXT alternatives require DEFERRED GENERICS", "GENERIC_DEFERRED_REQUIRED");
                } else if (!binding.alternatives().isEmpty()) bindings.add(binding);
            }
            symbols.put(topic.id(), new TopicSymbol(topic.header(), topic.baseTopic(), topic.members(), topic.dependencies(),
                    topic.deferredGenerics(), bindings));
        }
    }

    private Map<SymbolId, TopicSymbol.GenericBinding> modelContexts(SymbolId model,
            Map<SymbolId, Map<SymbolId, TopicSymbol.GenericBinding>> completed, Set<SymbolId> active) {
        if (symbols.get(model) instanceof ModelSymbol predefined && predefined.origin() instanceof Origin.PredefinedOrigin) return Map.of();
        if (completed.containsKey(model)) return completed.get(model);
        if (!active.add(model)) return Map.of(); // ImportBinder diagnoses cycles.
        Map<SymbolId, TopicSymbol.GenericBinding> result = new LinkedHashMap<>();
        ModelSymbol owner = (ModelSymbol) symbols.get(model);
        DeclInfo decl = index.byId(model).orElseThrow();
        for (var imported : owner.imports()) if (imported.targetModel().isPresent()) {
            checkWork();
            for (var entry : modelContexts(imported.targetModel().get(), completed, active).entrySet()) {
                var previous = result.get(entry.getKey()); var incoming = entry.getValue();
                if (previous == null) result.put(entry.getKey(), incoming);
                else {
                    var candidates = new LinkedHashSet<>(previous.alternatives()); candidates.addAll(incoming.alternatives());
                    candidates.removeIf(id -> previous.alternatives().stream().noneMatch(base -> domainExtends(id, base))
                            || incoming.alternatives().stream().noneMatch(base -> domainExtends(id, base)));
                    var origins = new LinkedHashSet<>(previous.contexts()); origins.addAll(incoming.contexts());
                    if (candidates.isEmpty()) emitRule(decl, "imported contexts have incompatible alternatives", "CONTEXT_CONFLICT");
                    result.put(entry.getKey(), genericBinding(entry.getKey(), List.copyOf(candidates), List.copyOf(origins)));
                }
            }
        }
        for (SymbolId member : owner.members()) if (symbols.get(member) instanceof guru.interlis.model.ContextSymbol context) {
            for (var mapping : context.mappings()) {
                checkWork();
                var previous = result.get(mapping.genericDomain());
                if (previous != null && mapping.alternatives().stream().anyMatch(id ->
                        previous.alternatives().stream().noneMatch(base -> domainExtends(id, base))))
                    emitRule(index.byId(context.id()).orElseThrow(), "CONTEXT target must extend an existing alternative", "GENERIC_CONTEXT_EXTENDS");
                result.put(mapping.genericDomain(), genericBinding(mapping.genericDomain(), mapping.alternatives(), List.of(context.id())));
            }
        }
        active.remove(model); completed.put(model, result); return result;
    }

    private TopicSymbol.GenericBinding genericBinding(SymbolId generic, List<SymbolId> alternatives, List<SymbolId> contexts) {
        // Empty alternatives can occur only in a diagnosed partial analysis, not in a valid schema.
        return new TopicSymbol.GenericBinding(generic, alternatives, contexts, alternatives.isEmpty()
                ? TopicSymbol.BindingState.ABSTRACT : alternatives.size() == 1 ? TopicSymbol.BindingState.CONCRETE : TopicSymbol.BindingState.DEFERRED);
    }

    Set<SymbolId> importedModels(SymbolId model) {
        Set<SymbolId> result = new LinkedHashSet<>(); var pending = new java.util.ArrayDeque<SymbolId>(); pending.add(model);
        while (!pending.isEmpty()) {
            checkWork(); SymbolId next = pending.removeFirst();
            if (result.add(next) && symbols.get(next) instanceof ModelSymbol m)
                m.imports().forEach(i -> i.targetModel().ifPresent(pending::add));
        }
        return result;
    }

    private void collectViewableGenerics(SymbolId viewable, Set<SymbolId> visible, SymbolId topic,
            Set<SymbolId> used, Set<TypeId> seen) {
        for (var member : effectiveMembers.getOrDefault(viewable, List.of())) {
            checkWork();
            if (symbols.get(member.declaredSymbol()) instanceof AttributeSymbol attr) collectGenericUses(attr.declaredType(), visible, topic, used, seen);
        }
    }

    private void collectGenericUses(TypeId type, Set<SymbolId> visible, SymbolId topic, Set<SymbolId> used, Set<TypeId> seen) {
        if (type == null || !seen.add(type)) return;
        checkWork(); TypeDescriptor descriptor = types.get(type);
        if (descriptor instanceof DomainReferenceType ref && symbols.get(ref.targetDomain()) instanceof DomainSymbol domain) {
            if (domain.generic()) used.add(domain.id());
            collectGenericUses(domain.typeId(), visible, topic, used, seen);
        } else if (descriptor instanceof GeometryType geometry) {
            geometry.vertexDomain().map(symbols::get).filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).ifPresent(domain -> {
                if (domain.generic()) used.add(domain.id()); collectGenericUses(domain.typeId(), visible, topic, used, seen);
            });
        } else if (descriptor instanceof CollectionType collection) {
            collectGenericUses(collection.elementType(), visible, topic, used, seen);
        } else if (descriptor instanceof StructureValueType structure) {
            for (Symbol symbol : symbols.values()) if (symbol instanceof StructureSymbol candidate
                    && visible.contains(index.modelOfSymbol(candidate.id()).orElse(null))
                    && (structure.targetRoots().isEmpty() || structure.targetRoots().stream().anyMatch(root -> baseChain(candidate.id()).contains(root)))
                    && (!(symbols.get(scopeRoot(candidate.id())) instanceof TopicSymbol) || baseChain(topic).contains(scopeRoot(candidate.id()))))
                collectViewableGenerics(candidate.id(), visible, topic, used, seen);
        }
    }

    private String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    private void buildModel(IndexedDeclarations.ModelEntry entry) {
        DeclInfo decl = entry.declaration();
        guru.interlis.syntax.ModelSyntax modelSyntax = entry.model().syntax();
        if (modelSyntax.endName().isPresent()
                && !modelSyntax.endName().orElseThrow().equals(modelSyntax.name())) {
            emitRule(decl, "MODEL end name does not match declaration", "MODEL_END_NAME");
        }
        if (modelSyntax.modelKind() == guru.interlis.model.ModelKind.SYMBOLOGY
                && modelSyntax.languageVersion() == guru.interlis.model.LanguageVersion.ILI23 && !modelSyntax.contracted())
            emitRule(decl, "SYMBOLOGY MODEL requires CONTRACTED in INTERLIS 2.3", "SYMBOLOGY_CONTRACTED");
        if (modelSyntax.modelKind() == guru.interlis.model.ModelKind.TYPE) {
            for (var child : index.children(decl.id())) {
                checkWork();
                if (Set.of(SymbolKind.TOPIC, SymbolKind.PARAMETER, SymbolKind.METADATA_BASKET).contains(child.kind())
                        || child.kind() == SymbolKind.CLASS && !child.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))
                    emitRule(child, "TYPE MODEL may only declare type definitions", "TYPE_MODEL_CONTENT");
            }
        }
        // The inventory may retain several versions. Uniqueness belongs to the
        // selected closure, not to every available source in the workspace.
        if (closure.models().contains(entry.model())) {
            DeclInfo previousModel = modelNames.putIfAbsent(modelSyntax.name(), decl);
            if (previousModel != null && !previousModel.id().equals(decl.id())) {
                emitRule(decl, "model name must be unique in the selected closure", "MODEL_NAME_UNIQUE");
            }
        }
        Set<String> parameterNames = new LinkedHashSet<>();
        for (var child : index.children(decl.id())) {
            if (child.kind() == SymbolKind.PARAMETER && !parameterNames.add(child.name()))
                emitRule(child, "runtime parameter name must be unique in its model", "PARAMETER_NAME_UNIQUE");
        }
        Set<String> modelLevelNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            if (child.kind() == SymbolKind.DOMAIN || child.kind() == SymbolKind.TOPIC) {
                if (!modelLevelNames.add(child.name())) {
                    emitRule(child, "topic/domain name must be unique in a model",
                            "MODEL_MEMBER_NAME_UNIQUE");
                }
            }
            if (child.kind() == SymbolKind.TOPIC) {
                Set<String> classNames = new LinkedHashSet<>();
                for (DeclInfo topicChild : index.children(child.id())) {
                    checkWork();
                    if (topicChild.kind() == SymbolKind.CLASS
                            && !classNames.add(topicChild.name())) {
                        emitRule(topicChild, "class name must be unique in a topic",
                                "TOPIC_CLASS_NAME_UNIQUE");
                    }
                }
            }
        }
        List<SymbolId> members = index.children(decl.id()).stream().map(DeclInfo::id).toList();
        List<ModelImport> imports = new ArrayList<>();
        for (guru.interlis.syntax.ImportSyntax site : entry.model().syntax().imports()) {
            checkWork();
            SymbolId target = null;
            for (Closure.ImportEdge edge : closure.importEdges()) {
                checkWork();
                if (edge.importer().sourceId().equals(entry.model().sourceId())
                        && edge.importer().declarationOffset() == entry.model().declarationOffset()
                        && edge.site().range().equals(site.range())) {
                    target = findModelId(edge.target().name(), edge.target().declarationOffset(),
                            edge.target().sourceId()).orElse(null);
                    break;
                }
            }
            if (site.modelName().equals("INTERLIS")) target = PredefinedModels.ensure(this, entry.declaration().language());
            imports.add(new ModelImport(site.modelName(), site.unqualified(), site.range(),
                    Optional.ofNullable(target)));
        }
        var modelOptions = entry.model().syntax().options();
        modelOptions.characterSet().ifPresent(option -> {
            if (CharacterSets.canonical(option.value()).isEmpty()) emitRule(decl, "CHARSET name is not registered by IANA", "MODEL_CHARSET", ((Origin.DeclaredOrigin)option.origin()).range());
        });
        modelOptions.xmlNamespace().ifPresent(option -> {
            try {
                var uri = new java.net.URI(option.value());
                if (option.value().isEmpty() || option.value().equals("http://www.w3.org/2000/xmlns/")
                        || option.value().equals("http://www.w3.org/XML/1998/namespace"))
                    throw new java.net.URISyntaxException(option.value(), "reserved or empty namespace");
            } catch (java.net.URISyntaxException invalid) {
                emitRule(decl, "XMLNS must be a nonempty, unreserved namespace URI reference", "MODEL_XMLNS", ((Origin.DeclaredOrigin)option.origin()).range());
            }
        });
        guru.interlis.syntax.ModelSyntax syntax = entry.model().syntax();
        Optional<guru.interlis.model.ModelTranslation> translation = syntax.translationSource()
                .map(source -> new guru.interlis.model.ModelTranslation(source.text(),
                        syntax.translationVersion(), syntax.language(), Optional.empty(), List.of()));
        symbols.put(decl.id(), new ModelSymbol(header(decl), syntax.modelKind(),
                syntax.contracted(), entry.model().version(), syntax.issuer(), imports, members,
                translation, syntax.options(), modelNamespace(syntax)));
    }

    private String modelNamespace(guru.interlis.syntax.ModelSyntax syntax) {
        return syntax.languageVersion() == guru.interlis.model.LanguageVersion.ILI23 ? "http://www.interlis.ch/INTERLIS2.3"
                : syntax.options().xmlNamespace().map(guru.interlis.model.ModelOptions.TextValue::value)
                        .orElse("http://www.interlis.ch/xtf/2.4/" + syntax.name());
    }

    /** Translations share the original transfer vocabulary, independent of file/declaration order. */
    private void bindTransferNamespaces() {
        for (var symbol : List.copyOf(symbols.values())) {
            if (!(symbol instanceof ModelSymbol model) || model.translation().isEmpty()) continue;
            Set<SymbolId> seen = new HashSet<>();
            ModelSymbol original = model;
            while (original.translation().flatMap(guru.interlis.model.ModelTranslation::sourceTarget).isPresent()
                    && seen.add(original.id())) {
                checkWork();
                var next = symbols.get(original.translation().orElseThrow().sourceTarget().orElseThrow());
                if (!(next instanceof ModelSymbol base)) break;
                original = base;
            }
            String namespace = original.transferNamespace();
            if (model.options().xmlNamespace().filter(value -> !value.value().equals(namespace)).isPresent())
                emitRule(index.byId(model.id()).orElseThrow(), "translated XMLNS conflicts with the original transfer namespace", "TRANSLATION_XMLNS");
            symbols.put(model.id(), new ModelSymbol(model.header(), model.modelKind(), model.contracted(), model.modelVersion(),
                    model.issuer(), model.imports(), model.members(), model.translation(), model.options(), namespace));
        }
    }

    Symbol buildSymbol(DeclInfo decl) {
        checkWork();
        Symbol existing = symbols.get(decl.id());
        if (existing != null) {
            return existing;
        }
        Symbol symbol = switch (decl.kind()) {
            case TOPIC -> buildTopic(decl);
            case CLASS -> buildClass(decl);
            case STRUCTURE -> buildStructure(decl);
            case ASSOCIATION -> buildAssociation(decl);
            case DOMAIN -> buildDomain(decl);
            case UNIT -> buildUnit(decl);
            case LINE_FORM -> lines.buildForm(decl);
            case METADATA_BASKET -> metadata.basket(decl);
            case METAOBJECT -> metadata.object(decl);
            case FUNCTION -> buildFunction(decl);
            case PARAMETER -> buildParameter(decl);
            case CONTEXT -> buildContext(decl);
            case ATTRIBUTE -> buildAttribute(decl);
            case ROLE -> buildRole(decl);
            case CONSTRAINT -> buildConstraint(decl);
            case ENUM_ELEMENT -> buildEnumElement(decl);
            case UNSUPPORTED -> buildUnsupported(decl);
            case GRAPHIC -> graphics.build(decl);
            case DRAWING_RULE -> {
                buildSymbol(index.byId(decl.owner().orElseThrow()).orElseThrow());
                yield symbols.get(decl.id());
            }
            case VIEW -> buildView(decl);
            case VIEW_BASE -> views.buildBase(decl);
            case MODEL -> throw new IllegalStateException("model symbols are built separately");
        };
        symbols.put(decl.id(), symbol);
        return symbol;
    }

    private TopicSymbol buildTopic(DeclInfo decl) {
        TopicSyntax syntax = (TopicSyntax) decl.syntax();
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            buildSymbol(child);
        }
        if (syntax.endName().isPresent() && !syntax.endName().orElseThrow().equals(syntax.name())) {
            emitRule(decl, "TOPIC end name does not match declaration", "TOPIC_END_NAME");
        }
        Optional<SymbolId> base = syntax.baseTopic().flatMap(name -> {
            Optional<SymbolId> resolved = tryResolve(decl, name, Set.of(SymbolKind.TOPIC));
            if (resolved.isEmpty()) {
                List<SymbolId> matches = symbols.values().stream()
                        .filter(TopicSymbol.class::isInstance).map(TopicSymbol.class::cast)
                        .filter(topic -> topic.name().value().equals(name.text()))
                        .map(TopicSymbol::id).toList();
                if (matches.size() == 1) {
                    return Optional.of(matches.getFirst());
                }
            }
            return resolved;
        });
        base.ifPresent(id -> baseOf.put(decl.id(), id));
        if (base.isPresent() && symbols.get(base.orElseThrow()) instanceof TopicSymbol baseTopic) {
            boolean abstractBase = baseTopic.header().modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT);
            for (SymbolId member : baseTopic.members()) {
                checkWork();
                if (symbols.get(member) instanceof ClassSymbol baseClass
                        && baseClass.header().modifiers()
                        .contains(guru.interlis.model.Modifier.ABSTRACT)) {
                    abstractBase = true;
                }
            }
            Set<String> baseDomains = new LinkedHashSet<>();
            for (SymbolId member : baseTopic.members()) {
                checkWork();
                if (symbols.get(member) instanceof DomainSymbol domain) {
                    baseDomains.add(domain.name().value());
                }
            }
            for (DeclInfo child : index.children(decl.id())) {
                checkWork();
                if (child.kind() == SymbolKind.DOMAIN && baseDomains.contains(child.name())) {
                    emitRule(child, "domain name conflicts with an inherited topic member",
                            "INHERITED_NAME_CONFLICT");
                }
            }
            boolean baseHasConcrete = base.flatMap(index::byId).map(baseDecl -> {
                for (DeclInfo child : index.children(baseDecl.id())) {
                    checkWork();
                    if (child.kind() == SymbolKind.STRUCTURE) {
                        return true;
                    }
                    if (child.kind() == SymbolKind.CLASS
                            && !child.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
                        return true;
                    }
                }
                return false;
            }).orElse(false);
            boolean derivedHasContent = !index.children(decl.id()).isEmpty()
                    || (decl.syntax() instanceof guru.interlis.syntax.TopicSyntax topicSyntax
                    && !topicSyntax.oidRules().isEmpty())
                    || !syntax.deferredGenerics().isEmpty();
            if (abstractBase && !derivedHasContent && !baseHasConcrete && !syntax.modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT)) {
                emitRule(decl, "empty topic extending an abstract topic must be abstract",
                        "ABSTRACT_TOPIC_EXTENSION");
            }
            for (DeclInfo child : index.children(decl.id())) {
                checkWork();
                if (child.kind() != SymbolKind.CLASS
                        || !(symbols.get(child.id()) instanceof ClassSymbol derivedClass)
                        || derivedClass.baseType().isEmpty()) {
                    continue;
                }
                for (SymbolId member : baseTopic.members()) {
                    checkWork();
                    if (!(symbols.get(member) instanceof ClassSymbol baseClass)
                            || !baseClass.name().equals(derivedClass.name())) {
                        continue;
                    }
                    if (!child.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                        emitRule(child, "class in an extending topic may not extend the "
                                + "same-named base class without EXTENDED", "TOPIC_CLASS_EXTENSION");
                    }
                    if (baseClass.header().modifiers()
                            .contains(guru.interlis.model.Modifier.EXTENDED)) {
                        emitRule(child, "cannot extend a class marked EXTENDED",
                                "EXTENDED_BASE_CLASS");
                    }
                }
            }
        }
        List<SymbolId> dependencies = new ArrayList<>();
        for (guru.interlis.syntax.NameSyntax dependency : syntax.dependencies()) {
            checkWork();
            Optional<SymbolId> resolved = resolver.resolve(decl.id(), dependency, Set.of(SymbolKind.TOPIC)).target();
            if (resolved.isEmpty()) unresolved(dependency);
            resolved.ifPresent(dependencies::add);
            recordReference(decl, dependency, resolved.orElse(null), ReferenceUsage.TYPE_USE);
        }
        Set<String> memberNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            if (child.kind() == SymbolKind.CLASS || child.kind() == SymbolKind.DOMAIN) {
                if (!memberNames.add(child.name())) {
                    emitRule(child, "name conflict in topic", "TOPIC_MEMBER_NAME");
                }
            }
        }
        if (!syntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            List<DeclInfo> classes = index.children(decl.id()).stream()
                    .filter(child -> child.kind() == SymbolKind.CLASS).toList();
            for (DeclInfo child : classes) {
                checkWork();
                if (child.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                        && classes.stream().noneMatch(candidate ->
                            !candidate.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                            && baseChain(candidate.id()).contains(child.id())))
                    emitRule(decl, "abstract class requires an abstract topic or a concrete specialization",
                            "ABSTRACT_TOPIC");
            }
        }
        List<SymbolId> members = index.children(decl.id()).stream().map(DeclInfo::id).toList();
        List<SymbolId> deferred = new ArrayList<>();
        for (var name : syntax.deferredGenerics()) bindTarget(decl, name, Set.of(SymbolKind.DOMAIN),
                ReferenceUsage.TYPE_USE, new ArrayList<>()).ifPresent(deferred::add);
        return new TopicSymbol(header(decl), base, members, dependencies, deferred, List.of());
    }

    private ClassSymbol buildClass(DeclInfo decl) {
        ClassDeclarationSyntax syntax = (ClassDeclarationSyntax) decl.syntax();
        if (syntax.endName().isPresent() && !syntax.endName().orElseThrow().equals(syntax.name())) {
            emitRule(decl, "CLASS end name does not match declaration", "CLASS_END_NAME");
        }
        Optional<SymbolId> base = objectInheritanceBase(decl.id());
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            buildSymbol(child);
        }
        List<SymbolId> members = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE || child.kind() == SymbolKind.PARAMETER)
                .map(DeclInfo::id).toList();
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        for (SymbolId member : members) {
            checkWork();
            DeclInfo memberDecl = index.byId(member).orElse(null);
            if (memberDecl != null && memberDecl.kind() == SymbolKind.ATTRIBUTE) {
                TypeId typeId = declaredTypes.get(member);
                if (decl.language() != guru.interlis.model.LanguageVersion.ILI24
                        && typeId != null && types.get(typeId) instanceof ReferenceType) {
                    emitRule(memberDecl, "reference attribute is only allowed in a structure",
                            "REFERENCE_ATTRIBUTE_CLASS");
                }
            }
        }
        return new ClassSymbol(header(decl), base, members, constraints);
    }

    private StructureSymbol buildStructure(DeclInfo decl) {
        StructureDeclarationSyntax syntax = (StructureDeclarationSyntax) decl.syntax();
        Optional<SymbolId> base = objectInheritanceBase(decl.id());
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            buildSymbol(child);
        }
        List<SymbolId> members = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE || child.kind() == SymbolKind.PARAMETER)
                .map(DeclInfo::id).toList();
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        return new StructureSymbol(header(decl), base, members, constraints);
    }

    /** Bind inheritance before members, including members reached from another source. */
    Optional<SymbolId> objectInheritanceBase(SymbolId owner) {
        DeclInfo decl = index.byId(owner).orElse(null);
        if (decl == null || (decl.kind() != SymbolKind.CLASS && decl.kind() != SymbolKind.STRUCTURE)
                || !resolvedObjectBases.add(owner)) return Optional.ofNullable(baseOf.get(owner));
        Optional<guru.interlis.syntax.NameSyntax> name = decl.syntax() instanceof ClassDeclarationSyntax c
                ? c.baseType() : ((StructureDeclarationSyntax) decl.syntax()).baseType();
        if (name.isPresent() && decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED))
            emitRule(decl, "EXTENDED and EXTENDS cannot be combined in one declaration", "EXTENDED_EXPLICIT_BASE");
        Set<SymbolKind> kinds = decl.kind() == SymbolKind.CLASS
                ? Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE) : Set.of(SymbolKind.STRUCTURE);
        Optional<SymbolId> base=name.flatMap(n->bindTarget(decl,n,kinds,ReferenceUsage.BASE_TYPE,new ArrayList<>()));

        if (base.isEmpty() && decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
            base = baseTopicMember(owner, decl.name(), Set.of(decl.kind()));
            if (base.isEmpty()) emitRule(decl, "EXTENDED needs a same-named inherited declaration", "EXTENDED_BASE");
        }
        base.ifPresent(id -> {
            baseOf.put(owner, id);
            if (symbols.containsKey(id) && symbols.get(id).header().has(guru.interlis.model.Modifier.FINAL))
                emitRule(decl, "cannot extend FINAL object type", "OBJECT_FINAL");
        });
        return base;
    }

    private AssociationSymbol buildAssociation(DeclInfo decl) {
        AssociationDeclarationSyntax syntax = (AssociationDeclarationSyntax) decl.syntax();
        buildingAssociations.add(decl.id());
        Optional<SymbolId> base = syntax.baseAssociation().flatMap(name -> {
            var resolved = resolver.resolve(decl.id(), name, Set.of(SymbolKind.ASSOCIATION)).target();
            if (resolved.isEmpty()) unresolved(name);
            return resolved;
        });
        if (base.isEmpty() && syntax.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
            base = baseTopicMember(decl.id(), decl.name(), Set.of(SymbolKind.ASSOCIATION));
            if (base.isEmpty()) emitRule(decl, "EXTENDED association needs an inherited base", "ASSOCIATION_BASE");
        }
        if (base.isPresent()) {
            SymbolId parent = base.orElseThrow();
            if (buildingAssociations.contains(parent)) {
                emitRule(decl, "cyclic association inheritance", "INHERITANCE_CYCLE");
                base = Optional.empty();
            } else {
                buildSymbol(index.byId(parent).orElseThrow());
                baseOf.put(decl.id(), parent);
                if (symbols.get(parent).header().modifiers().contains(guru.interlis.model.Modifier.FINAL)) {
                    emitRule(decl, "cannot extend FINAL association", "ASSOCIATION_FINAL");
                }
            }
        }
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            buildSymbol(child);
        }
        buildingAssociations.remove(decl.id());
        if (syntax.nameRange().isPresent() != syntax.endName().isPresent()) {
            emitRule(decl, "association name must be present at both begin and END", "ASSOCIATION_NAME");
        }
        Set<String> roleNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            if (child.kind() == SymbolKind.ROLE && !roleNames.add(child.name())) {
                emitRule(child, "duplicate role name", "DUPLICATE_ROLE");
            }
        }
        List<SymbolId> roles = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ROLE)
                .map(DeclInfo::id).toList();
        List<SymbolId> attributes = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE)
                .map(DeclInfo::id).toList();
        if (roles.isEmpty() && attributes.isEmpty() && base.isEmpty()) {
            emitRule(decl, "association requires at least two roles", "ASSOCIATION_ROLES");
        }
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        Cardinality inheritedCardinality = base.map(symbols::get).map(AssociationSymbol.class::cast)
                .map(AssociationSymbol::cardinality).orElse(Cardinality.exactly(1));
        Cardinality effectiveCardinality = syntax.cardinality().orElse(inheritedCardinality);
        if (base.isPresent() && (effectiveCardinality.lower().compareTo(inheritedCardinality.lower()) < 0
                || widerUpper(effectiveCardinality.upper(), inheritedCardinality.upper()))) {
            emitRule(decl, "association extension widens link cardinality", "ASSOCIATION_CARDINALITY_EXTENSION");
        }
        return new AssociationSymbol(header(decl), base, effectiveCardinality, roles, attributes, constraints,
                index.children(decl.id()).stream().filter(d -> d.kind() == SymbolKind.VIEW_BASE).map(DeclInfo::id).findFirst());
    }

    private guru.interlis.model.ParameterSymbol buildParameter(DeclInfo decl) {
        var syntax = (guru.interlis.syntax.ParameterDeclarationSyntax) decl.syntax();
        var model = index.modelOfSymbol(decl.id()).flatMap(index::byId)
                .map(m -> (guru.interlis.syntax.ModelSyntax) m.syntax()).orElseThrow();
        if (index.byId(decl.owner().orElseThrow()).orElseThrow().kind() == SymbolKind.MODEL
                && decl.language() == guru.interlis.model.LanguageVersion.ILI23 && !model.contracted())
            emitRule(decl, "runtime parameters require CONTRACTED in INTERLIS 2.3", "PARAMETER_CONTRACTED");
        TypeId type = buildType(syntax.type(), decl.id(), decl.sourceId());
        if (index.byId(decl.owner().orElseThrow()).orElseThrow().kind() == SymbolKind.MODEL && containsArea(type, new HashSet<>()))
            emitRule(decl, "runtime parameters cannot contain AREA values", "PARAMETER_AREA");
        declaredTypes.put(decl.id(), type);
        return new guru.interlis.model.ParameterSymbol(header(decl), type, syntax.mandatory() || mandatoryType(type), Optional.empty());
    }

    private boolean containsArea(TypeId type, Set<TypeId> seen) {
        if (!seen.add(type)) return false;
        TypeDescriptor descriptor = effectiveDescriptor(type);
        if (descriptor instanceof GeometryType geometry)
            return geometry.geometryKind() == guru.interlis.model.GeometryKind.AREA
                    || geometry.geometryKind() == guru.interlis.model.GeometryKind.MULTIAREA;
        if (descriptor instanceof CollectionType collection) return containsArea(collection.elementType(), seen);
        if (descriptor instanceof StructureValueType structure) {
            for(SymbolId root:structure.targetRoots()) {
                Set<SymbolId> visited=new HashSet<>();
                for(SymbolId current=root;current!=null && visited.add(current);current=objectInheritanceBase(current).orElse(null)) {
                    checkWork();
                    if(symbols.get(current) instanceof StructureSymbol known) {
                        for(SymbolId member:known.members())
                            if(symbols.get(member) instanceof AttributeSymbol attribute && containsArea(attribute.declaredType(),seen))return true;
                    } else for(var child:index.children(current))
                        if(child.kind()==SymbolKind.ATTRIBUTE && containsArea(attributeType(child),seen))return true;
                }
            }
        }
        return false;
    }

    private FunctionSymbol buildFunction(DeclInfo decl) {
        FunctionDeclarationSyntax syntax = (FunctionDeclarationSyntax) decl.syntax();
        var model = index.modelOfSymbol(decl.id()).flatMap(index::byId)
                .map(m -> (guru.interlis.syntax.ModelSyntax) m.syntax());
        if (decl.language() == guru.interlis.model.LanguageVersion.ILI23
                && model.isPresent() && !model.orElseThrow().contracted()) {
            emitRule(decl, "functions require CONTRACTED in INTERLIS 2.3", "FUNCTION_CONTRACTED");
        }
        Set<String> names = new LinkedHashSet<>();
        for (var parameter : syntax.parameters()) {
            checkWork();
            if (!names.add(parameter.name())) {
                emitRule(decl, "duplicate function parameter", "FUNCTION_PARAMETER_DUPLICATE");
            }
        }
        List<FunctionParameter> parameters = java.util.stream.IntStream.range(0, syntax.parameters().size())
                .mapToObj(i -> new FunctionParameter(syntax.parameters().get(i).name(), functionArgumentType(decl, i), syntax.parameters().get(i).mandatory() || mandatoryType(functionArgumentType(decl,i)))).toList();
        TypeId result=buildType(syntax.returnType(), decl.id(), decl.sourceId());
        return new FunctionSymbol(header(decl), parameters,result,syntax.returnMandatory() || mandatoryType(result),syntax.explanation().map(e->new Documentation(e.text(),List.of(e.range()))).orElseGet(Documentation::empty));
    }

    private final Map<guru.interlis.model.AttributePathType.Argument, TypeId> functionArgumentTypes = new LinkedHashMap<>();
    private final Set<guru.interlis.model.AttributePathType.Argument> resolvingFunctionArguments = new HashSet<>();

    private TypeId functionArgumentType(DeclInfo function, int position) {
        var key = new guru.interlis.model.AttributePathType.Argument(function.id(), position);
        TypeId bound = functionArgumentTypes.get(key);
        if (bound != null) return bound;
        if (!resolvingFunctionArguments.add(key)) {
            TypeId error = nextTypeId();
            types.put(error, new ErrorType(error, function.origin(), List.of(emitRule(function,
                    "cyclic attribute-path argument dependency", "FUNCTION_ARGUMENT_CYCLE"))));
            return error;
        }
        var syntax = (FunctionDeclarationSyntax) function.syntax();
        TypeId type = buildType(syntax.parameters().get(position).type(), function.id(), function.sourceId());
        functionArgumentTypes.put(key, type); resolvingFunctionArguments.remove(key);
        return type;
    }

    private UnitSymbol buildUnit(DeclInfo decl) {
        UnitDeclarationSyntax syntax = (UnitDeclarationSyntax) decl.syntax();
        Optional<SymbolId> base = syntax.base().flatMap(name -> bindUnit(decl, name));
        Optional<SymbolId> derived = syntax.derivedFrom().flatMap(name -> bindUnit(decl, name));
        List<UnitSymbol.Component> components = new ArrayList<>();
        for (var component : syntax.components()) {
            checkWork();
            bindUnit(decl, component.unit()).ifPresent(id -> components.add(new UnitSymbol.Component(component.operator(), id)));
        }
        return new UnitSymbol(header(decl), syntax.designation(), syntax.abstractUnit(), base, derived,
                components, syntax.conversionFactors(), syntax.conversionFunction(), syntax.definition().isBlank() ? Optional.empty() : Optional.of(syntax.definition()));
    }

    Optional<SymbolId> bindUnit(DeclInfo decl, guru.interlis.syntax.NameSyntax name) {
        return bindUnit(decl,name,new ArrayList<>());
    }

    Optional<SymbolId> bindUnit(DeclInfo decl, guru.interlis.syntax.NameSyntax name, List<DiagnosticId> errors) {
        return bindTarget(decl,name,Set.of(SymbolKind.UNIT),ReferenceUsage.TYPE_USE,errors,false);
    }

    Optional<SymbolId> builtinUnit(SymbolId context, String name) {
        if (!name.startsWith("INTERLIS.")) return Optional.empty();
        var timeUnit = temporal.find(index.byId(context).orElseThrow(), name, Set.of(SymbolKind.UNIT));
        if (timeUnit.isPresent()) return timeUnit;
        String shortName = name.substring("INTERLIS.".length());
        String parent = switch (shortName) {
            case "ANYUNIT" -> "";
            case "DIMENSIONLESS", "LENGTH", "MASS", "TIME", "ELECTRIC_CURRENT", "TEMPERATURE",
                    "AMOUNT_OF_MATTER", "ANGLE", "SOLID_ANGLE", "LUMINOUS_INTENSITY", "MONEY" -> "ANYUNIT";
            case "m" -> "LENGTH"; case "kg" -> "MASS"; case "s" -> "TIME";
            case "A" -> "ELECTRIC_CURRENT"; case "K" -> "TEMPERATURE"; case "mol" -> "AMOUNT_OF_MATTER";
            case "rad" -> "ANGLE"; case "sr" -> "SOLID_ANGLE"; case "cd" -> "LUMINOUS_INTENSITY";
            default -> null;
        };
        if (parent == null) return Optional.empty();
        var language = index.byId(context).orElseThrow().language();
        SymbolId id = new SymbolId("predefined:" + language + ":unit:" + name);
        if (symbols.containsKey(id)) return Optional.of(id);
        Optional<SymbolId> base = parent.isEmpty() ? Optional.empty() : builtinUnit(context, "INTERLIS." + parent);
        boolean abstractUnit = parent.isEmpty() || parent.equals("ANYUNIT");
        String designation = switch (shortName) {
            case "m" -> "METER"; case "kg" -> "KILOGRAMM"; case "s" -> "SECOND";
            case "A" -> "AMPERE"; case "K" -> "DEGREE_KELVIN"; case "mol" -> "MOLE";
            case "rad" -> "RADIAN"; case "sr" -> "STERADIAN"; case "cd" -> "CANDELA";
            default -> shortName;
        };
        Origin origin = new Origin.PredefinedOrigin(language, name);
        symbols.put(id, new UnitSymbol(new SymbolHeader(id, SymbolKind.UNIT, DeclaredName.synthetic(name),
                Optional.empty(), 0, origin, Optional.empty(), language,
                Documentation.empty(), MetaAttributeSet.empty(), abstractUnit
                    ? Set.of(guru.interlis.model.Modifier.ABSTRACT) : Set.of(), List.of()),
                designation, abstractUnit, base, Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty()));
        return Optional.of(id);
    }

    /** Validate bound dependencies after all declarations exist; file order is immaterial. */
    private void validateUnits() {
        for (Symbol symbol : symbols.values()) {
            if (!(symbol instanceof UnitSymbol unit)) continue;
            DeclInfo decl = index.byId(unit.id()).orElse(null);
            if (decl == null) continue;
            checkWork();
            var syntax = (UnitDeclarationSyntax) decl.syntax();
            for (var factor : unit.conversionFactors()) {
                checkWork();
                if (factor.decimal().filter(value -> value.exactValue().signum() == 0).isPresent()) {
                    boolean divide = factor.operator() == UnitSymbol.Operator.DIVIDE;
                    emitRule(decl, divide ? "unit conversion divides by zero" : "unit conversion multiplies by zero",
                            divide ? "UNIT_CONVERSION_ZERO_DIVISOR" : "UNIT_CONVERSION_ZERO_FACTOR", factor.range());
                }
            }
            if (index.children(decl.owner().orElseThrow()).stream()
                    .anyMatch(other -> !other.id().equals(decl.id()) && other.name().equals(decl.name())))
                emitRule(decl, "unit short name is not unique in its scope", "UNIT_NAME_UNIQUE");
            var pending = new java.util.ArrayDeque<SymbolId>(unitDependencies(unit));
            Set<SymbolId> seen = new HashSet<>();
            while (!pending.isEmpty()) {
                checkWork();
                SymbolId next = pending.removeFirst();
                if (next.equals(unit.id())) {
                    emitRule(decl, "cyclic unit dependency", "UNIT_CYCLE"); break;
                }
                if (seen.add(next) && symbols.get(next) instanceof UnitSymbol dependency)
                    pending.addAll(unitDependencies(dependency));
            }
            for (var component : unit.components()) {
                UnitSymbol target = (UnitSymbol) symbols.get(component.unit());
                if (target.abstractUnit() != unit.abstractUnit())
                    emitRule(decl, "composed unit and all components must agree on abstraction", "UNIT_COMPONENT_ABSTRACT");
            }
            if (unit.derivedFrom().isPresent()) {
                UnitSymbol target = (UnitSymbol) symbols.get(unit.derivedFrom().orElseThrow());
                if (unit.abstractUnit() || target.abstractUnit())
                    emitRule(decl, "derived units require a concrete conversion target and are concrete", "UNIT_DERIVED_ABSTRACT");
            }
            UnitSymbol base = unit.base().map(symbols::get).filter(UnitSymbol.class::isInstance).map(UnitSymbol.class::cast).orElse(null);
            if (base == null) continue;
            if (!base.abstractUnit()) emitRule(decl, "only abstract units can be extended", "UNIT_BASE_ABSTRACT");
            if (!syntax.components().isEmpty()) {
                boolean compatible = !base.components().isEmpty() && base.components().size() == unit.components().size();
                if (compatible) for (int i = 0; i < unit.components().size(); i++) {
                    var actual = unit.components().get(i); var expected = base.components().get(i);
                    if (actual.operator() != expected.operator() || !unitExtends(actual.unit(), expected.unit())) compatible = false;
                }
                if (!compatible) emitRule(decl, "composition must specialize the base component by component", "UNIT_COMPOSITION_EXTENSION");
            } else if (unit.derivedFrom().isPresent() && !unitExtends(unit.derivedFrom().orElseThrow(), base.id())) {
                emitRule(decl, "conversion target must specialize the declared abstract base", "UNIT_DERIVED_EXTENSION");
            }
        }
    }

    private List<SymbolId> unitDependencies(UnitSymbol unit) {
        List<SymbolId> result = new ArrayList<>();
        unit.base().ifPresent(result::add); unit.derivedFrom().ifPresent(result::add);
        unit.components().forEach(c -> result.add(c.unit())); return result;
    }

    boolean unitExtends(SymbolId actual, SymbolId expected) {
        UnitSymbol target = extensionUnit(expected);
        if (target == null) return false;
        if (target.name().value().equals("INTERLIS.ANYUNIT")) return true;
        var pending = new java.util.ArrayDeque<SymbolId>(); pending.add(actual);
        Set<SymbolId> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            checkWork(); SymbolId next = pending.removeFirst();
            if (next.equals(expected)) return true;
            if (!seen.add(next)) continue;
            UnitSymbol unit = extensionUnit(next);
            if (unit == null) continue;
            if (unit.header().origin() instanceof Origin.SyntheticOrigin
                    && target.header().origin() instanceof Origin.SyntheticOrigin && unit.name().equals(target.name())) return true;
            unit.base().ifPresent(pending::add); unit.derivedFrom().ifPresent(pending::add);
        }
        return false;
    }

    boolean unitsConvertible(SymbolId actual, SymbolId expected) {
        return guru.interlis.model.internal.TypeCompatibility.unitRelation(actual, expected, this::extensionUnit)
                .filter(relation -> relation.decision() == guru.interlis.model.RelationDecision.ALLOWED).isPresent();
    }

    private DomainSymbol buildDomain(DeclInfo decl) {
        TypeId type = domainType(decl);
        Set<String> constraintNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            if (child.kind() == SymbolKind.CONSTRAINT && !constraintNames.add(child.name())) {
                emitRule(child, "domain constraint names must be unique", "DOMAIN_CONSTRAINT_NAME");
            }
            buildSymbol(child);
        }
        return new DomainSymbol(header(decl), type, Optional.ofNullable(baseOf.get(decl.id())),
                decl.modifiers().contains(guru.interlis.model.Modifier.GENERIC));
    }

    /** Bind referenced types before expressions without recursively building their constraints. */
    private TypeId domainType(DeclInfo decl) {
        TypeId existing = declaredTypes.get(decl.id());
        if (existing != null) return existing;
        checkWork();
        if (!resolvingDomainTypes.add(decl.id())) {
            TypeId error = nextTypeId();
            types.put(error, new ErrorType(error, decl.origin(),
                    List.of(emitRule(decl, "cyclic domain type dependency", "DOMAIN_TYPE_CYCLE"))));
            declaredTypes.put(decl.id(), error);
            return error;
        }
        try {
            DomainDeclarationSyntax syntax = (DomainDeclarationSyntax) decl.syntax();
            Optional<SymbolId> base=syntax.baseDomain().flatMap(name->bindTarget(decl,name,Set.of(SymbolKind.DOMAIN),ReferenceUsage.BASE_TYPE,new ArrayList<>()));

            base.ifPresent(id -> {
                baseOf.put(decl.id(), id);
                index.byId(id).ifPresent(this::domainType);
                if ((symbols.get(id) != null && symbols.get(id).header().has(guru.interlis.model.Modifier.FINAL))
                        || index.byId(id).filter(d -> d.modifiers().contains(guru.interlis.model.Modifier.FINAL)).isPresent())
                    emitRule(decl, "cannot extend FINAL domain", "DOMAIN_FINAL");
            });
            TypeId type;
            if (syntax.type() instanceof GenericTypeSyntax generic && "INHERITED".equals(generic.name()) && base.isPresent()) {
                type = nextTypeId();
                types.put(type, new DomainReferenceType(type, new Origin.DeclaredOrigin(syntax.type().range()), base.orElseThrow()));
            } else type = buildType(syntax.type(), decl.id(), decl.sourceId());
            TypeDescriptor inherited = base.map(declaredTypes::get).map(this::effectiveDescriptor).orElse(null);
            inheritTypeProperties(type, syntax.type(), inherited,decl);
            if (syntax.type() instanceof LineTypeSyntax) lines.effective(type, inherited, decl);
            else if (inherited instanceof GeometryType && !(effectiveDescriptor(type) instanceof GeometryType))
                emitRule(decl, "geometry base requires a geometric extension", "GEOMETRY_EXTENSION");
            if (effectiveDescriptor(type) instanceof NumericType numeric && inherited instanceof NumericType parent) {
                var relation = numericExtension(numeric, parent, decl.language());
                if (relation.decision() == guru.interlis.model.RelationDecision.DISALLOWED)
                    emitRule(decl, "numeric type does not specialize its base: " + relation.reason(), relation.reason().startsWith("numeric-unit") || relation.reason().contains("unit-") ? "NUMERIC_UNIT_EXTENSION" : "NUMERIC_EXTENSION");
            }
            declaredTypes.putIfAbsent(decl.id(), type);
            if (syntax.type() instanceof EnumerationTypeSyntax enumeration) {
                if (base.isPresent() && inherited == null) emitRule(decl, "enumeration base must already be defined", "ENUM_BASE");
                types.put(type, effectiveEnumeration(type, enumeration, inherited, decl));
            }
            TypeDescriptor own=effectiveDescriptor(type);
            if(inherited!=null && !(inherited instanceof NumericType && own instanceof NumericType)
                    && !(own instanceof GeometryType) && !(inherited instanceof guru.interlis.model.OidType)
                    && !(own instanceof ErrorType) && !(inherited instanceof ErrorType)) {
                var relation=extension(own,inherited,decl.language());
                if(relation.decision()==guru.interlis.model.RelationDecision.DISALLOWED)
                    emitRule(decl,"domain does not specialize its base: "+relation.reason(),"TYPE_EXTENSION");
            }
            if (base.isPresent() && effectiveDescriptor(declaredTypes.get(base.orElseThrow())) instanceof guru.interlis.model.OidType oid
                    && (Optional.ofNullable(symbols.get(base.orElseThrow()))
                            .filter(s -> s.name().value().equals("INTERLIS.NOOID")).isPresent()
                    || oid.oidKind() != guru.interlis.model.OidKind.ANY
                    || !(effectiveDescriptor(type) instanceof guru.interlis.model.OidType derived)
                    || derived.oidKind() == guru.interlis.model.OidKind.ANY))
                emitRule(decl, "concrete OID domain cannot be extended", "OID_EXTENSION");
            return declaredTypes.get(decl.id());
        } finally {
            resolvingDomainTypes.remove(decl.id());
        }
    }

    private EnumerationType effectiveEnumeration(TypeId id, EnumerationTypeSyntax syntax,
            TypeDescriptor inherited, DeclInfo declaration) {
        EnumerationType base = inherited instanceof EnumerationType e ? e : null;
        if (inherited != null && (base == null || base.treeValues())) emitRule(declaration, "enumeration definition requires an enumeration definition as base", "ENUM_BASE");
        if (base != null && base.id().equals(id)) emitRule(declaration, "cyclic enumeration inheritance", "ENUM_BASE");
        if (base != null && base.circular()) emitRule(declaration, "circular enumeration cannot be extended", "ENUM_CIRCULAR");
        if (base != null && syntax.circular()) emitRule(declaration, "enumeration extension cannot introduce circular ordering", "ENUM_ORDER");
        Map<String, EnumerationValue> values = new LinkedHashMap<>();
        if (base != null) base.values().forEach(v -> values.put(v.path(), v));
        Set<String> own = new LinkedHashSet<>();
        Set<String> finals = new LinkedHashSet<>(base == null ? Set.of() : base.finalSubtrees());
        for (var value : syntax.values()) {
            checkWork();
            String path = value.path();
            String parent = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
            if (value.qualified() && (base == null || base.values().stream().noneMatch(v -> v.path().equals(path))))
                emitRule(declaration, "qualified enum name must identify an inherited element", "ENUM_PARENT");
            if (!own.add(path)) emitRule(declaration, "duplicate enumeration value " + path, "ENUM_VALUE_UNIQUE");
            if (values.containsKey(path)) {
                if (!value.hasChildren()) emitRule(declaration, "extended enumeration repeats inherited value " + path, "ENUM_VALUE_UNIQUE");
            } else {
                if (!parent.isEmpty() && !values.containsKey(parent)) emitRule(declaration, "unknown enumeration parent " + parent, "ENUM_PARENT");
                if (finals.contains(parent)) emitRule(declaration, "enumeration sibling list is FINAL: " + parent, "ENUM_FINAL");
                values.put(path, new EnumerationValue(path, 0, Optional.of(value.range())));
            }
        }
        finals.addAll(syntax.finalSubtrees());
        // Preserve inherited sibling order and place new descendants under their parent.
        List<EnumerationValue> ordered = new ArrayList<>();
        appendEnumerationChildren("", values, ordered);
        return new EnumerationType(id, new Origin.DeclaredOrigin(syntax.range()), ordered,
                syntax.ordered() || syntax.circular() || base != null && base.ordered(), syntax.circular(), finals, Optional.ofNullable(base).map(EnumerationType::id), false);
    }

    private void appendEnumerationChildren(String parent, Map<String, EnumerationValue> values, List<EnumerationValue> result) {
        for (var value : values.values()) {
            checkWork();
            String path = value.path();
            String candidate = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
            if (candidate.equals(parent)) {
                result.add(new EnumerationValue(path, result.size(), value.range()));
                appendEnumerationChildren(path, values, result);
            }
        }
    }

    private UnitSymbol extensionUnit(SymbolId id) {
        checkWork();
        Symbol symbol = symbols.get(id);
        if (symbol == null) symbol = index.byId(id).map(this::buildSymbol).orElse(null);
        return symbol instanceof UnitSymbol unit ? unit : null;
    }

    private guru.interlis.model.TypeRelation numericExtension(NumericType actual, NumericType base, guru.interlis.model.LanguageVersion language) {
        return guru.interlis.model.internal.TypeCompatibility.numericExtension(actual, base, language, this::extensionUnit);
    }

    private List<DiagnosticId> numericErrors(NumericTypeSyntax syntax, DeclInfo declaration) {
        List<DiagnosticId> errors = new ArrayList<>();
        if (syntax.minimum().isPresent() && syntax.maximum().isPresent()) {
            var minimum = syntax.minimum().orElseThrow(); var maximum = syntax.maximum().orElseThrow();
            if (minimum.exactValue().compareTo(maximum.exactValue()) > 0)
                errors.add(emitRule(declaration, "numeric minimum exceeds maximum", "NUMERIC_BOUNDS", syntax.range()));
            if (minimum.precision().fractionalDigits() != maximum.precision().fractionalDigits())
                errors.add(emitRule(declaration, "numeric bounds must have the same precision", "NUMERIC_PRECISION", syntax.range()));
        }
        return List.copyOf(errors);
    }

    private void validateNumericUnit(NumericRange range,DeclInfo declaration,SourceRange sourceRange,List<DiagnosticId> errors) {
        if(range.minimum().isPresent() && range.maximum().isPresent() && range.unit().isPresent()) {
            UnitSymbol unit=extensionUnit(range.unit().orElseThrow());
            if(unit!=null && unit.abstractUnit())errors.add(emitRule(declaration,"concrete numeric range requires a concrete unit","NUMERIC_ABSTRACT_UNIT",sourceRange));
        }
    }

    private void validateNumericUses() {
        for(var entry:declaredTypes.entrySet()) {
            checkWork();var declaration=index.byId(entry.getKey()).orElse(null);
            if(declaration==null || declaration.kind()!=SymbolKind.DOMAIN && declaration.kind()!=SymbolKind.ATTRIBUTE)continue;
            var type=effectiveDescriptor(entry.getValue());
            while(type instanceof CollectionType collection)type=effectiveDescriptor(collection.elementType());
            boolean incomplete=type instanceof NumericType number && (number.range().minimum().isEmpty() || number.range().maximum().isEmpty())
                    || type instanceof CoordinateType coordinate && coordinate.axes().stream().anyMatch(axis->axis.minimum().isEmpty() || axis.maximum().isEmpty());
            if(!incomplete || declaration.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))continue;
            if(declaration.syntax() instanceof AttributeDeclarationSyntax attribute && attribute.type() instanceof GenericTypeSyntax inferred && inferred.name().equals("INFERRED"))continue;
            // LineCoord is a vertex-dependent placeholder in custom segment structures,
            // specialized by the shared inspection contextualizer, not a concrete domain.
            if(type instanceof CoordinateType && type.origin() instanceof Origin.PredefinedOrigin predefined && predefined.key().equals("INTERLIS.LineCoord"))continue;
            boolean generic=declaration.language()==guru.interlis.model.LanguageVersion.ILI24 && type instanceof CoordinateType
                    && (declaration.modifiers().contains(guru.interlis.model.Modifier.GENERIC) || genericCoordinateType(entry.getValue(),new HashSet<>()));
            if(!generic)emitRule(declaration,"incomplete numeric or coordinate declaration requires ABSTRACT","NUMERIC_ABSTRACT");
        }
    }

    private boolean genericCoordinateType(TypeId id,Set<TypeId> seen) {
        if(!seen.add(id))return false;
        if(types.get(id) instanceof DomainReferenceType reference) {
            var domain=symbols.get(reference.targetDomain());
            return domain instanceof DomainSymbol d && (d.generic() || genericCoordinateType(d.typeId(),seen));
        }
        return false;
    }

    private boolean mandatoryDomain(SymbolId domain) {
        Set<SymbolId> seen = new HashSet<>();
        for (SymbolId current = domain; current != null && seen.add(current); current = baseOf.get(current)) {
            checkWork();
            if (index.byId(current).filter(d -> d.modifiers().contains(guru.interlis.model.Modifier.MANDATORY)).isPresent()) return true;
            if (symbols.get(current) instanceof DomainSymbol d && d.mandatory()) return true;
        }
        return false;
    }

    private boolean mandatoryType(TypeId type) {
        Set<TypeId> seen = new HashSet<>();
        while (type != null && seen.add(type) && types.get(type) instanceof DomainReferenceType reference) {
            if (mandatoryDomain(reference.targetDomain())) return true;
            type = declaredTypes.get(reference.targetDomain());
        }
        return false;
    }

    /** Complete only owned inline types; referenced alias descriptors are never mutated. */
    private void inheritTypeProperties(TypeId id, TypeSyntax syntax, TypeDescriptor base,DeclInfo declaration) {
        if (syntax instanceof NumericTypeSyntax own && types.get(id) instanceof NumericType numeric && base instanceof NumericType parent) {
            var range=inheritRange(numeric.range(),parent.range(),own);var errors=new ArrayList<DiagnosticId>();
            validateNumericUnit(range,declaration,syntax.range(),errors);
            types.put(id,errors.isEmpty()?new NumericType(id,numeric.origin(),range):new ErrorType(id,numeric.origin(),errors));
        }
        else if(syntax instanceof CoordinateTypeSyntax own && types.get(id) instanceof CoordinateType coordinate && base instanceof CoordinateType parent) {
            List<NumericRange> axes=new ArrayList<>(coordinate.axes());
            for(int i=0;i<Math.min(axes.size(),parent.axes().size());i++)axes.set(i,inheritRange(axes.get(i),parent.axes().get(i),own.axes().get(i)));
            var errors=new ArrayList<DiagnosticId>();
            for(int i=0;i<axes.size();i++)validateNumericUnit(axes.get(i),declaration,own.axes().get(i).range(),errors);
            types.put(id,errors.isEmpty()?new CoordinateType(id,coordinate.origin(),coordinate.dimensions(),axes,coordinate.multiCoordinate(),
                    coordinate.referenceSystem().isPresent()?coordinate.referenceSystem():parent.referenceSystem(),
                    coordinate.rotation().isPresent()?coordinate.rotation():parent.rotation()):new ErrorType(id,coordinate.origin(),errors));
        } else if(syntax instanceof CollectionTypeSyntax own && types.get(id) instanceof CollectionType collection && base instanceof CollectionType parent)
            inheritTypeProperties(collection.elementType(),own.elementType(),effectiveDescriptor(parent.elementType()),declaration);
    }

    private NumericRange inheritRange(NumericRange own, NumericRange base, NumericTypeSyntax syntax) {
        return new NumericRange(own.minimum(),own.maximum(),own.circular()||base.circular(),
                syntax.direction()==guru.interlis.model.NumericDirection.NONE?base.direction():own.direction(),
                syntax.unit().isEmpty()?base.unit():own.unit(),syntax.referenceSystem().isEmpty()?base.referenceSystem():own.referenceSystem());
    }

    private AttributeSymbol buildAttribute(DeclInfo decl) {
        AttributeDeclarationSyntax syntax = (AttributeDeclarationSyntax) decl.syntax();
        TypeId type = attributeType(decl);
        for(var child:index.children(decl.id()))if(child.kind()==SymbolKind.ENUM_ELEMENT)buildSymbol(child);
        if (syntax.transientAttribute() && !syntax.assigned()
                && !syntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            emitRule(decl, "transient attribute requires an assignment", "TRANSIENT_ASSIGNMENT");
        }
        return new AttributeSymbol(header(decl), type, syntax.mandatory() || mandatoryType(type),
                syntax.transientAttribute(), syntax.continuous(), syntax.subdivision(),
                Optional.empty(), bindAttributeDerivations(decl, syntax, type));
    }

    private final Map<SymbolId, List<ExpressionId>> inferredDerivations = new LinkedHashMap<>();

    boolean assignable(TypeId actual, TypeId expected) {
        if(mandatoryType(expected) && guru.interlis.model.internal.TypeCompatibility.isUndefined(effectiveDescriptor(actual)))return false;
        return guru.interlis.model.internal.TypeCompatibility.assign(effectiveDescriptor(actual), effectiveDescriptor(expected), this::effectiveDescriptor, baseOf::get, domain -> effectiveDescriptor(declaredTypes.get(domain))).decision() == guru.interlis.model.RelationDecision.ALLOWED;
    }

    private List<ExpressionId> bindAttributeDerivations(DeclInfo decl, AttributeDeclarationSyntax syntax, TypeId type) {
        if (inferredDerivations.containsKey(decl.id())) return inferredDerivations.get(decl.id());
        List<ExpressionId> result = new ArrayList<>();
        for (var expression : syntax.derivations()) {
            var bound = contextualValue(buildExpression(expression, decl.owner().orElseThrow()),type,decl.id());
            result.add(bound.id());
            if(!bound.error() && (syntax.mandatory() || mandatoryType(type))
                    && guru.interlis.model.internal.TypeCompatibility.isUndefined(effectiveDescriptor(bound.typeId())))
                emitRule(decl,"MANDATORY attribute excludes an explicitly undefined derived value","MANDATORY_VALUE",expression.range());
            if (!bound.error() && !assignable(bound.typeId(), type))
                emitTypeMismatch(decl.sourceId(), expression.range(), typeName(bound.typeId()), typeName(type));
        }
        return List.copyOf(result);
    }

    /** Resolve once so every expression and the published attribute share the same type. */
    TypeId attributeType(DeclInfo decl) {
        TypeId existing = declaredTypes.get(decl.id());
        if (existing != null) return existing;
        checkWork();
        AttributeDeclarationSyntax syntax = (AttributeDeclarationSyntax) decl.syntax();
        TypeId type;
        if (!resolvingAttributeTypes.add(decl.id())) {
            type = nextTypeId();
            types.put(type, new ErrorType(type, decl.origin(),
                    List.of(emitRule(decl, "cyclic inherited attribute type", "INHERITANCE_CYCLE"))));
            return type;
        }
        try {
            decl.owner().flatMap(this::objectInheritanceBase).flatMap(base->findMember(base,decl.name()))
                    .filter(base->symbols.get(base) instanceof AttributeSymbol || index.byId(base).filter(d->d.kind()==SymbolKind.ATTRIBUTE).isPresent())
                    .ifPresent(base->baseOf.put(decl.id(),base));
            if (syntax.type() instanceof GenericTypeSyntax inferred && "INFERRED".equals(inferred.name())) {
                var result = buildExpression(syntax.derivations().getFirst(), decl.owner().orElseThrow());
                inferredDerivations.put(decl.id(), List.of(result.id()));
                type = result.typeId();
            } else if (syntax.type() instanceof GenericTypeSyntax generic && "INHERITED".equals(generic.name())) {
                Optional<SymbolId> base = decl.owner().flatMap(this::objectInheritanceBase)
                        .flatMap(owner -> findMember(owner, decl.name()));
                DeclInfo inherited = base.flatMap(index::byId).orElse(null);
                if (inherited != null && inherited.kind() == SymbolKind.ATTRIBUTE) {
                    type = attributeType(inherited);
                } else if (base.map(symbols::get).orElse(null) instanceof AttributeSymbol attribute) {
                    // ALL OF members have a bound type but no separate syntax declaration.
                    type = attribute.declaredType();
                } else {
                    type = nextTypeId();
                    types.put(type, new ErrorType(type, decl.origin(), List.of(emitRule(decl,
                            "MANDATORY without a type requires an inherited attribute", "ATTRIBUTE_INHERITED_TYPE"))));
                }
            } else {
                type = buildType(syntax.type(), decl.id(), decl.sourceId());
                TypeDescriptor inheritedGeometry = null;
                if (decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                    var base = decl.owner().flatMap(this::objectInheritanceBase).flatMap(owner -> findMember(owner, decl.name()));
                    TypeId baseType = base.flatMap(index::byId).filter(d -> d.kind() == SymbolKind.ATTRIBUTE)
                            .map(this::attributeType).orElseGet(() -> base.map(symbols::get)
                                    .filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                                    .map(AttributeSymbol::declaredType).orElse(null));
                    inheritTypeProperties(type, syntax.type(), effectiveDescriptor(baseType),decl);
                    inheritedGeometry = effectiveDescriptor(baseType);
                }
                if (syntax.type() instanceof LineTypeSyntax) lines.effective(type, inheritedGeometry, decl);
            }
            declaredTypes.put(decl.id(), type);
            return type;
        } finally {
            resolvingAttributeTypes.remove(decl.id());
        }
    }

    private RoleSymbol buildRole(DeclInfo decl) {
        RoleDeclarationSyntax syntax = (RoleDeclarationSyntax) decl.syntax();
        List<guru.interlis.model.RoleTarget> targets = new ArrayList<>();
        for (var target : syntax.targets()) {
            checkWork();
            SymbolId base = bindRoleTarget(decl, target.base());
            List<SymbolId> restrictions = target.restrictions().stream()
                    .map(name -> bindRoleTarget(decl, name)).toList();
            for (SymbolId restriction : restrictions) {
                if (!targetSpecializes(restriction, base)) {
                    emitRule(decl, "restriction must specialize its role target", "ROLE_TARGET_RESTRICTION");
                }
            }
            for (var previous : targets) {
                if (targetSpecializes(base, previous.base()) || targetSpecializes(previous.base(), base)) {
                    emitRule(decl, "role alternatives must not specialize each other", "ROLE_TARGET_OVERLAP");
                }
            }
            targets.add(new guru.interlis.model.RoleTarget(base, restrictions));
        }
        Cardinality cardinality = syntax.cardinality();
        SymbolId associationBase = baseOf.get(decl.owner().orElseThrow());
        RoleSymbol inherited = associationBase == null ? null : associationRoles(associationBase).stream()
                .filter(r -> r.name().value().equals(decl.name())).findFirst().orElse(null);
        if (inherited == null) {
            if (associationBase != null) emitRule(decl, "extended association must not add roles", "ASSOCIATION_EXTENDED_ROLE");
            if (decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)
                    || decl.modifiers().contains(guru.interlis.model.Modifier.HIDING)) {
                emitRule(decl, "role extension requires an inherited role", "ROLE_EXTENSION_BASE");
            }
        } else {
            if (!decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                emitRule(decl, "role override requires EXTENDED", "ROLE_EXTENSION");
            }
            if (inherited.header().modifiers().contains(guru.interlis.model.Modifier.FINAL)) {
                emitRule(decl, "cannot override FINAL role", "ROLE_FINAL");
            }
            if (!syntax.explicitCardinality()) cardinality = inherited.cardinality();
            if (cardinality.lower().compareTo(inherited.cardinality().lower()) < 0
                    || widerUpper(cardinality.upper(), inherited.cardinality().upper())
                    || syntax.aggregation().ordinal() < inherited.aggregation().ordinal()) {
                emitRule(decl, "extended role widens cardinality or weakens aggregation", "ROLE_EXTENSION");
            }
            if (inherited.external() != decl.modifiers().contains(guru.interlis.model.Modifier.EXTERNAL)) {
                emitRule(decl, "EXTERNAL must remain unchanged in an extended role", "ROLE_EXTERNAL_EXTENSION");
            }
            if (targets.stream().flatMap(t -> t.allowedRoots().stream()).anyMatch(t -> !roleAccepts(inherited, t))) {
                emitRule(decl, "extended role widens its target set", "ROLE_TARGET_EXTENSION");
            }
        }
        return new RoleSymbol(header(decl), targets, cardinality, syntax.aggregation(),
                decl.modifiers().contains(guru.interlis.model.Modifier.ORDERED),
                decl.modifiers().contains(guru.interlis.model.Modifier.EXTERNAL),
                decl.modifiers().contains(guru.interlis.model.Modifier.HIDING), bindRoleDerivation(decl, syntax, targets));
    }

    private Optional<ExpressionId> bindRoleDerivation(DeclInfo decl, RoleDeclarationSyntax syntax, List<guru.interlis.model.RoleTarget> targets) {
        return syntax.derivation().map(expression -> {
            var bound = buildExpression(expression, decl.owner().orElseThrow());
            var actual = effectiveDescriptor(bound.typeId());
            if (!bound.error()) {
                var roots = targets.stream().flatMap(t -> t.allowedRoots().stream()).toList();
                var expected = new guru.interlis.model.ObjectArgumentType(nextTypeId(), decl.origin(), Optional.empty(), roots, false);
                types.put(expected.id(), expected);
                if (!assignable(bound.typeId(), expected.id())) emitTypeMismatch(decl.sourceId(), expression.range(), typeName(bound.typeId()), "single role target");
            }
            return bound.id();
        });
    }

    private List<RoleSymbol> associationRoles(SymbolId association) {
        Map<String, RoleSymbol> roles = new LinkedHashMap<>();
        List<SymbolId> chain = baseChain(association);
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (DeclInfo child : index.children(chain.get(i))) {
                checkWork();
                if (child.kind() == SymbolKind.ROLE) {
                    roles.put(child.name(), (RoleSymbol) buildSymbol(child));
                }
            }
        }
        return List.copyOf(roles.values());
    }

    boolean targetSpecializes(SymbolId candidate, SymbolId base) {
        return objectBase(base).isEmpty()
                || guru.interlis.model.internal.TypeCompatibility.specializes(candidate, base, baseOf::get);
    }

    private boolean roleAccepts(RoleSymbol role, SymbolId candidate) {
        return role.targetRoots().stream().anyMatch(base -> targetSpecializes(candidate, base));
    }

    private SymbolId bindObjectTarget(DeclInfo decl, guru.interlis.syntax.NameSyntax name, ReferenceUsage usage) {
        Optional<SymbolId> target = name.text().equals("ANYCLASS") ? Optional.of(anyClass(decl))
                : resolver.resolve(decl.id(), name, Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION)).target();
        if (target.isEmpty() && name.segments().size() == 1) {
            target = baseTopicMember(decl.id(), name.text(), Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
        }
        if (target.isEmpty()) unresolved(name);
        recordReference(decl, name, target.orElse(null), usage);
        return target.orElseGet(() -> syntheticClass(decl, name));
    }

    private SymbolId bindRoleTarget(DeclInfo decl, guru.interlis.syntax.NameSyntax name) {
        return bindObjectTarget(decl, name, ReferenceUsage.ROLE_TARGET);
    }

    List<SymbolId> topicDependencies(SymbolId topic) {
        Set<SymbolId> result = new LinkedHashSet<>();
        for (SymbolId id : baseChain(topic)) {
            checkWork();
            if (symbols.get(id) instanceof TopicSymbol owner) result.addAll(owner.dependencies());
        }
        return List.copyOf(result);
    }

    private void validateTopicDependencies() {
        for (Symbol symbol : symbols.values()) {
            if (!(symbol instanceof TopicSymbol topic)) continue;
            checkWork();
            Set<SymbolId> seen = new HashSet<>();
            var pending = new java.util.ArrayDeque<>(topicDependencies(topic.id()));
            while (!pending.isEmpty()) {
                checkWork();
                SymbolId next = pending.removeFirst();
                if (next.equals(topic.id())) {
                    emitRule(index.byId(topic.id()).orElseThrow(), "cyclic topic dependency", "TOPIC_DEPENDENCY_CYCLE");
                    break;
                }
                if (seen.add(next)) pending.addAll(topicDependencies(next));
            }
        }
    }

    private void validateReferenceScope(DeclInfo site, SymbolId sourceTopic, SymbolId target,
                                        boolean external, String externalRule) {
        SymbolId targetTopic = scopeRoot(target);
        if (!(symbols.get(sourceTopic) instanceof TopicSymbol)
                || !(symbols.get(targetTopic) instanceof TopicSymbol)
                || isTopicSubtype(sourceTopic, targetTopic)) return;
        if (!external) emitRule(site, "cross-topic reference requires EXTERNAL", externalRule);
        if (!topicDependencies(sourceTopic).contains(targetTopic))
            emitRule(site, "reference target topic requires DEPENDS ON", "REFERENCE_DEPENDS_ON");
    }

    private boolean shareBaseTopic(SymbolId first, SymbolId second) {
        Set<SymbolId> firstBases = new LinkedHashSet<>();
        SymbolId current = first;
        int guard = 0;
        while (current != null && guard++ < 32) {
            checkWork();
            firstBases.add(current);
            current = symbols.get(current) instanceof TopicSymbol topic
                    ? topic.baseTopic().orElse(null) : null;
        }
        current = second;
        guard = 0;
        while (current != null && guard++ < 32) {
            checkWork();
            if (firstBases.contains(current)) {
                return true;
            }
            current = symbols.get(current) instanceof TopicSymbol topic
                    ? topic.baseTopic().orElse(null) : null;
        }
        return false;
    }

    boolean isTopicSubtype(SymbolId candidate, SymbolId base) {
        SymbolId current = candidate;
        Set<SymbolId> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            checkWork();
            if (current.equals(base)) {
                return true;
            }
            Symbol symbol = symbols.get(current);
            if (symbol instanceof TopicSymbol topic) {
                current = topic.baseTopic().orElse(null);
            } else {
                return false;
            }
        }
        return false;
    }

    private SymbolId anyClass(DeclInfo owner) {
        SymbolId model = index.modelOfSymbol(owner.id()).orElseThrow();
        SymbolId id = new SymbolId(model.value() + ":predefined:ANYCLASS");
        symbols.computeIfAbsent(id, ignored -> new ClassSymbol(new SymbolHeader(id, SymbolKind.CLASS,
                DeclaredName.declared("ANYCLASS"), Optional.of(model), 0,
                new Origin.PredefinedOrigin(owner.language(), "INTERLIS.ANYCLASS"), Optional.empty(),
                owner.language(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of()),
                Optional.empty(), List.of(), List.of()));
        return id;
    }

    private Optional<SymbolId> objectBase(SymbolId target) {
        Symbol symbol = symbols.get(target);
        return symbol != null && symbol.origin() instanceof Origin.PredefinedOrigin predefined
                && predefined.key().equals("INTERLIS.ANYCLASS") ? Optional.empty() : Optional.of(target);
    }

    private SymbolId syntheticClass(DeclInfo owner, guru.interlis.syntax.NameSyntax name) {
        SymbolId id = new SymbolId("sym-unresolved-" + (++typeCounter));
        SymbolId model = index.modelOfSymbol(owner.id()).orElse(owner.id());
        SymbolHeader header = new SymbolHeader(id, SymbolKind.CLASS,
                DeclaredName.synthetic("<unresolved-role-target>"),
                Optional.of(model), 0,
                new Origin.SyntheticOrigin(guru.interlis.model.SyntheticReason.MISSING_REFERENCE,
                        Optional.of(name.range())),
                Optional.of(name.range()), owner.language(), Documentation.empty(),
                MetaAttributeSet.empty(), Set.of(), List.of());
        ClassSymbol synthetic = new ClassSymbol(header, Optional.empty(),
                List.of(), List.of());
        symbols.put(id, synthetic);
        return id;
    }

    private ConstraintSymbol buildConstraint(DeclInfo decl) {
        ConstraintDeclarationSyntax syntax;
        Optional<SymbolId> externalTarget = Optional.empty();
        if (decl.syntax() instanceof ExternalConstraintSyntax external) {
            syntax = external.constraint();
            externalTarget = tryResolve(decl, external.target(),
                    Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
            if (externalTarget.isEmpty() && external.target().segments().size() == 1) {
                externalTarget = baseTopicMember(decl.id(), external.target().text(),
                        Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
            }
            if (externalTarget.isEmpty()) {
                unresolved(external.target());
            } else {
                SymbolId target = externalTarget.orElseThrow();
                recordReference(decl, external.target(), target, ReferenceUsage.EXPRESSION);
                boolean inherited = baseTopicMember(decl.id(),
                        index.byId(target).orElseThrow().name(),
                        Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION)).filter(target::equals).isPresent();
                if (!Objects.equals(scopeRoot(decl.id()), scopeRoot(target)) && !inherited) {
                    emitRule(decl, "external constraints must be declared in the target's topic",
                            "CONSTRAINT_TARGET_TOPIC");
                }
            }
        } else {
            syntax = (ConstraintDeclarationSyntax) decl.syntax();
        }
        externalTarget.ifPresent(target -> externalConstraintTargets.put(decl.id(), target));
        SymbolId context = contextualObject(expressionContext(decl.id()), scopeRoot(decl.id()));
        if (syntax.existence().isPresent()) {
            var existence = syntax.existence().orElseThrow();
            ExpressionResult source = constraintPath(existence.source(), decl, context, false);
            requireConstraintValue(decl, source, false);
            List<guru.interlis.model.ExistenceConstraint.ExistenceTarget> targets = new ArrayList<>();
            for (var target : existence.targets()) {
                checkWork();
                var resolved = resolver.resolve(decl.id(), target.viewable(),
                        Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW), false).target();
                if (resolved.isEmpty() && target.viewable().segments().size() == 1)
                    resolved = baseTopicMember(decl.id(), target.viewable().text(),
                            Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW));
                recordReference(decl, target.viewable(), resolved.orElse(null), ReferenceUsage.EXPRESSION);
                if (resolved.isEmpty()) { unresolved(target.viewable()); continue; }
                SymbolId targetScope = resolved.orElseThrow();
                SymbolId ownerTopic = scopeRoot(decl.id());
                SymbolId targetTopic = scopeRoot(targetScope);
                boolean depends = ownerTopic == null || targetTopic == null
                        || index.byId(ownerTopic).map(d -> d.kind() != SymbolKind.TOPIC).orElse(true)
                        || index.byId(targetTopic).map(d -> d.kind() != SymbolKind.TOPIC).orElse(true)
                        || topicAvailable(ownerTopic, targetTopic)
                        || baseChain(ownerTopic).stream().map(index::byId).flatMap(Optional::stream)
                        .filter(d -> d.syntax() instanceof TopicSyntax)
                        .anyMatch(d -> ((TopicSyntax) d.syntax()).dependencies().stream()
                                .anyMatch(n -> resolver.resolve(d.id(), n, Set.of(SymbolKind.TOPIC)).target()
                                        .filter(targetTopic::equals).isPresent()));
                if (!depends) emitRule(decl, "EXISTENCE target topic requires DEPENDS ON", "EXISTENCE_DEPENDS");
                ExpressionResult path = constraintPath(target.path(), decl, targetScope, false);
                requireConstraintValue(decl, path, false);
                if (!source.error() && !path.error() && !existenceCompatible(source.typeId(), path.typeId())) {
                    TypeDescriptor from = effectiveDescriptor(source.typeId()), to = effectiveDescriptor(path.typeId());
                    boolean familyPending = from.kind() == to.kind() && switch (from.kind()) {
                        case GEOMETRY, ENUMERATION, FORMATTED,
                                CLASS_VALUE, GENERIC, DEFERRED, PENDING -> true;
                        default -> false;
                    };
                    if (familyPending) emitUnsupported(decl.sourceId(), target.path().range(),
                            "EXISTENCE_TYPE_COMPATIBILITY", "EXISTENCE compatibility for " + from.kind());
                    else emitTypeMismatch(decl.sourceId(), target.path().range(), typeName(source.typeId()), typeName(path.typeId()));
                }
                targets.add(new guru.interlis.model.ExistenceConstraint.ExistenceTarget(targetScope, path.id()));
            }
            return new ConstraintSymbol(header(decl), new guru.interlis.model.ExistenceConstraint(
                    syntax.name(), source.id(), targets), externalTarget);
        }
        if (syntax.constraintKind() == guru.interlis.syntax.ConstraintKind.SET
                && decl.owner().flatMap(index::byId).filter(d -> d.kind() == SymbolKind.STRUCTURE).isPresent())
            emitRule(decl, "SET CONSTRAINT is not allowed in a structure", "SET_IN_STRUCTURE");
        if (syntax.constraintKind() == guru.interlis.syntax.ConstraintKind.UNIQUENESS) {
            Optional<ExpressionId> where = syntax.expression().map(e -> {
                ExpressionResult condition = buildExpression(e, decl.id());
                if (!condition.error() && !isBoolean(condition.typeId()))
                    emitTypeMismatch(decl.sourceId(), e.range(), typeName(condition.typeId()), "BOOLEAN");
                return condition.id();
            });
            List<ExpressionId> keys = new ArrayList<>();
            List<UniquenessConstraint.LocalUniqueness> localKeys = new ArrayList<>();
            if (syntax.localPrefix().isPresent()) {
                if (syntax.basket()) emitRule(decl, "UNIQUE cannot combine BASKET and LOCAL", "UNIQUE_KEY");
                ExpressionResult prefix = constraintPath(syntax.localPrefix().orElseThrow(), decl, context, true);
                SymbolId structure = structures.target(effectiveDescriptor(prefix.typeId()));
                if (!prefix.error() && structure == null)
                    emitRule(decl, "LOCAL prefix must select a structure attribute", "UNIQUE_LOCAL_PREFIX");
                for (var key : syntax.uniqueKeys()) {
                    ExpressionResult field = constraintPath(key, decl, structure, false);
                    requireConstraintValue(decl, field, true);
                    keys.add(field.id());
                }
                localKeys.add(new UniquenessConstraint.LocalUniqueness(prefix.id(), keys));
                keys = List.of();
            } else {
                if (context != null && index.byId(context).filter(d -> d.kind() == SymbolKind.STRUCTURE).isPresent())
                    emitRule(decl, "global UNIQUE requires identifiable objects", "UNIQUE_STRUCTURE");
                for (var key : syntax.uniqueKeys()) {
                    ExpressionResult path = constraintPath(key, decl, context, false);
                    requireConstraintValue(decl, path, true);
                    keys.add(path.id());
                }
            }
            return new ConstraintSymbol(header(decl), new UniquenessConstraint(syntax.name(),
                    syntax.basket(), where, keys, localKeys), externalTarget);
        }
        Optional<ExpressionId> setWhere=syntax.where().map(e->{
            var condition=buildExpression(e,decl.id());
            if(!condition.error() && !isBoolean(condition.typeId()))emitTypeMismatch(decl.sourceId(),e.range(),typeName(condition.typeId()),"BOOLEAN");
            return condition.id();
        });
        if(syntax.constraintKind()==guru.interlis.syntax.ConstraintKind.PLAUSIBILITY && syntax.plausibility().isEmpty()) {
            var error=missingConstraintExpression(decl,syntax.range());
            return new ConstraintSymbol(header(decl),new MandatoryConstraint(syntax.name(),error.id()),externalTarget);
        }
        SymbolId expressionOwner = decl.id();
        ExpressionResult expression = syntax.expression()
                .map(e -> buildExpression(e, expressionOwner))
                .orElseGet(() -> missingConstraintExpression(decl,syntax.range()));
        if (!expression.error() && !isBoolean(expression.typeId())) {
            emitTypeMismatch(decl.sourceId(), syntax.range(), typeName(expression.typeId()),
                    "BOOLEAN");
        }
        ConstraintDefinition definition = switch (syntax.constraintKind()) {
            case MANDATORY -> new MandatoryConstraint(syntax.name(), expression.id());
            case PLAUSIBILITY -> new PlausibilityConstraint(syntax.name(),
                    syntax.plausibility().orElseThrow().percentage().exactValue(), syntax.plausibility().orElseThrow().comparison(),
                    expression.id());
            case DOMAIN -> new DomainConstraint(expression.id());
            case SET -> new guru.interlis.model.SetConstraint(syntax.name(),
                    syntax.basket()?guru.interlis.model.SetConstraint.SetConstraintScope.BASKET: guru.interlis.model.SetConstraint.SetConstraintScope.GLOBAL, setWhere,
                    expression.id());
            default -> new MandatoryConstraint(syntax.name(), expression.id());
        };
        return new ConstraintSymbol(header(decl), definition, externalTarget);
    }

    private boolean widerUpper(guru.interlis.model.CardinalityUpper left,
                               guru.interlis.model.CardinalityUpper right) {
        if (left instanceof guru.interlis.model.CardinalityUpper.Unbounded) {
            return !(right instanceof guru.interlis.model.CardinalityUpper.Unbounded);
        }
        if (right instanceof guru.interlis.model.CardinalityUpper.Unbounded) {
            return false;
        }
        if (left instanceof guru.interlis.model.CardinalityUpper.Finite leftFinite
                && right instanceof guru.interlis.model.CardinalityUpper.Finite rightFinite) {
            return leftFinite.value().compareTo(rightFinite.value()) > 0;
        }
        return false;
    }

    private ExpressionResult constraintPath(ExpressionSyntax syntax, DeclInfo owner,
            SymbolId start, boolean localPrefix) {
        if (syntax instanceof PathExpressionSyntax path)
            return pathExpression(new ExpressionId("expr-" + (++expressionCounter)), path, owner.id(), start, localPrefix);
        return buildExpression(syntax, owner.id()); // Unsupported operators keep their explicit diagnostic.
    }

    private void requireConstraintValue(DeclInfo owner, ExpressionResult result, boolean unique) {
        if (result.error()) return;
        TypedExpression expression = expressions.get(result.id());
        TypeDescriptor type = effectiveDescriptor(result.typeId());
        boolean invalid = unique && type instanceof CollectionType || type instanceof guru.interlis.model.ObjectArgumentType o && o.objects();
        if (unique) invalid |= type instanceof StructureValueType;
        else invalid |= !(expression instanceof guru.interlis.model.PathExpression p
                && (p.path().steps().getLast() instanceof guru.interlis.model.PathStep.AttributeStep
                || p.path().steps().getLast() instanceof guru.interlis.model.PathStep.IndexStep));
        if (invalid) emitRule(owner, unique ? "UNIQUE key must be a singular non-structure value"
                : "EXISTENCE path must select one attribute value", unique ? "UNIQUE_KEY" : "EXISTENCE_PATH");
    }

    // EXISTENCE compares values, it does not assign the complete source domain.
    // Numeric/text bounds are data restrictions, not an assignment precondition.
    private boolean existenceCompatible(TypeId source, TypeId target) {
        TypeDescriptor actual = effectiveDescriptor(source);
        TypeDescriptor expected = effectiveDescriptor(target);
        if (actual instanceof CollectionType a && expected instanceof CollectionType b)
            return a.collectionKind() == b.collectionKind() && existenceCompatible(a.elementType(), b.elementType());
        if (actual instanceof CollectionType a && !isMany(a.cardinality())) actual = effectiveDescriptor(a.elementType());
        if (expected instanceof CollectionType b && !isMany(b.cardinality())) expected = effectiveDescriptor(b.elementType());
        if (actual == null || expected == null) return false;
        if (actual instanceof guru.interlis.model.OidType a && expected instanceof guru.interlis.model.OidType b)
            return a.oidKind() == guru.interlis.model.OidKind.ANY || b.oidKind() == guru.interlis.model.OidKind.ANY
                    || a.oidKind() == b.oidKind() && (a.valueType().isEmpty() && b.valueType().isEmpty()
                    || a.valueType().isPresent() && b.valueType().isPresent()
                    && existenceCompatible(a.valueType().orElseThrow(), b.valueType().orElseThrow()));
        if (actual.id().equals(expected.id())) return true;
        if (actual instanceof GeometryType geometry && expected instanceof CoordinateType) {
            actual = geometry.vertexDomain().map(declaredTypes::get).map(this::effectiveDescriptor).orElse(null);
        }
        if (actual instanceof NumericType a && expected instanceof NumericType b)
            return numericValueCompatible(a.range(), b.range());
        if (actual instanceof TextType && expected instanceof TextType) return true;
        if (actual instanceof CoordinateType a && expected instanceof CoordinateType b) {
            if (a.dimensions() != b.dimensions() || a.multiCoordinate() != b.multiCoordinate()
                    || !a.referenceSystem().equals(b.referenceSystem())) return false;
            for (int i = 0; i < a.dimensions(); i++)
                if (!numericValueCompatible(a.axes().get(i), b.axes().get(i))) return false;
            return true;
        }
        if (actual instanceof guru.interlis.model.AlignmentType a && expected instanceof guru.interlis.model.AlignmentType b)
            return a.alignment() == b.alignment();
        if (actual instanceof guru.interlis.model.BlackboxType a && expected instanceof guru.interlis.model.BlackboxType b)
            return a.blackboxKind() == b.blackboxKind();
        if (actual instanceof FormattedType a && expected instanceof FormattedType b) {
            if (a.predefined().isPresent() || b.predefined().isPresent()) return a.predefined().equals(b.predefined());
            return a.structure().equals(b.structure()) && a.parts().stream().map(p -> List.of(p.attribute(), p.width(), p.format(), p.literal())).toList()
                    .equals(b.parts().stream().map(p -> List.of(p.attribute(), p.width(), p.format(), p.literal())).toList());
        }
        if (actual instanceof EnumerationType a && expected instanceof EnumerationType b)
            return guru.interlis.model.internal.TypeCompatibility.relatedEnumerations(a, b, this::effectiveDescriptor) || a.ordered() == b.ordered() && a.circular() == b.circular()
                    && a.values().stream().map(EnumerationValue::path).toList()
                    .equals(b.values().stream().map(EnumerationValue::path).toList());
        return guru.interlis.model.internal.TypeCompatibility.assign(actual, expected,
                this::effectiveDescriptor, baseOf::get, domain -> effectiveDescriptor(declaredTypes.get(domain))).decision() == guru.interlis.model.RelationDecision.ALLOWED;
    }

    private boolean numericValueCompatible(NumericRange a, NumericRange b) {
        return a.unit().equals(b.unit()) && a.referenceSystem().equals(b.referenceSystem())
                && a.direction() == b.direction() && a.circular() == b.circular();
    }

    private EnumElementSymbol buildEnumElement(DeclInfo decl) {
        String path = decl.name();
        List<String> segments = List.of(path.split("\\."));
        TypeDescriptor type = decl.owner().map(declaredTypes::get).map(this::effectiveDescriptor).orElse(null);
        int ordinal = type instanceof EnumerationType enumeration ? enumeration.values().stream()
                .filter(value -> value.path().equals(path)).mapToInt(EnumerationValue::order).findFirst().orElse(-1) : -1;
        if (ordinal < 0) {
            // Erroneous partial types still retain local declaration order, never
            // the unrelated global declaration counter.
            ordinal = index.children(decl.owner().orElseThrow()).stream().filter(d -> d.kind() == SymbolKind.ENUM_ELEMENT)
                    .map(DeclInfo::id).toList().indexOf(decl.id());
        }
        return new EnumElementSymbol(header(decl), segments, ordinal);
    }

    private UnsupportedSymbol buildUnsupported(DeclInfo decl) {
        String feature = decl.featureId().orElse("UNKNOWN_SYNTAX");
        unsupportedBySource.computeIfAbsent(decl.sourceId(), ignored -> new LinkedHashSet<>())
                .add(feature);
        emitUnsupported(decl.sourceId(), decl.origin() instanceof Origin.DeclaredOrigin d
                ? d.range() : null, feature, decl.name());
        return new UnsupportedSymbol(header(decl), feature, decl.name());
    }

    private final ViewBuilder views = new ViewBuilder(this);

    private ViewSymbol buildView(DeclInfo decl) { return views.build(decl); }



    private void bindOidRules() {
        Map<SymbolId, List<guru.interlis.model.OidRule>> bound = new LinkedHashMap<>();
        for (Symbol symbol : List.copyOf(symbols.values())) {
            if (symbol instanceof ClassSymbol || symbol instanceof TopicSymbol || symbol instanceof AssociationSymbol)
                bindOidRules(symbol.id(), bound, new HashSet<>());
        }
    }

    private List<guru.interlis.model.OidRule> bindOidRules(SymbolId id,
            Map<SymbolId, List<guru.interlis.model.OidRule>> bound, Set<SymbolId> active) {
        checkWork();
        if (bound.containsKey(id)) return bound.get(id);
        if (!active.add(id)) return List.of(); // Existing inheritance checks diagnose the cycle.
        Symbol symbol = symbols.get(id);
        DeclInfo decl = index.byId(id).orElse(null);
        if (decl == null || !(symbol instanceof ClassSymbol || symbol instanceof TopicSymbol || symbol instanceof AssociationSymbol))
            return List.of();
        List<guru.interlis.syntax.OidRuleSyntax> declared = switch (decl.syntax()) {
            case ClassDeclarationSyntax c -> c.oidRules();
            case TopicSyntax t -> t.oidRules();
            case AssociationDeclarationSyntax a -> a.oidRules();
            default -> List.of();
        };
        List<guru.interlis.model.OidRule> inherited = baseOf.containsKey(id)
                ? bindOidRules(baseOf.get(id), bound, active) : List.of();
        List<guru.interlis.model.OidRule> rules = new ArrayList<>(inherited);
        if (!(symbol instanceof TopicSymbol) && rules.isEmpty()) {
            SymbolId topic = scopeRoot(id);
            if (topic != null && !topic.equals(id) && symbols.get(topic) instanceof TopicSymbol)
                bindOidRules(topic, bound, active).stream().filter(r -> !r.basket()).forEach(rules::add);
        }
        for (var rule : declared) {
            checkWork();
            if (symbol instanceof TopicSymbol && rule.domain().isEmpty()) {
                emitRule(decl, "NO OID is only allowed on classes and associations", "OID_TOPIC");
                continue;
            }
            Optional<SymbolId> domain = Optional.empty();
            if (rule.domain().isPresent()) {
                var name = rule.domain().orElseThrow();
                domain=bindTarget(decl,name,Set.of(SymbolKind.DOMAIN),ReferenceUsage.TYPE_USE,new ArrayList<>());
                if (domain.isEmpty()) continue;
                if (!(effectiveDescriptor(declaredTypes.get(domain.orElseThrow())) instanceof guru.interlis.model.OidType)) {
                    emitRule(decl, "OID AS must bind an OID domain", "OID_DOMAIN"); continue;
                }
            }
            var own = new guru.interlis.model.OidRule(rule.basket(), domain, new Origin.DeclaredOrigin(rule.range()));
            var previous = inherited.stream().filter(r -> r.basket() == own.basket()).findFirst();
            if (previous.isPresent() && !oidOverrideAllowed(previous.orElseThrow(), own, decl.language(), symbol instanceof TopicSymbol))
                emitRule(decl, "OID rule cannot change its inherited domain", "OID_OVERRIDE");
            rules.removeIf(r -> r.basket() == own.basket()); rules.add(own);
        }
        // Only 2.4 defines an omitted class rule as inherited NO OID.
        if (decl.language() == guru.interlis.model.LanguageVersion.ILI24
                && symbols.get(baseOf.get(id)) instanceof ClassSymbol && inherited.isEmpty()
                && rules.stream().anyMatch(r -> !r.basket() && oidState(r) == 2))
            emitRule(decl, "inherited NO OID must first be concretized as ANYOID", "OID_OVERRIDE");
        rules.sort(Comparator.comparing(r -> !r.basket()));
        for (var rule : rules) {
            if (oidState(rule) == 1 && !decl.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))
                emitRule(decl, "ANYOID requires an abstract topic, class or association", "OID_ABSTRACT");
        }
        if (symbol instanceof AssociationSymbol association) {
            Cardinality cardinality = association.cardinality();
            boolean multiple = !(cardinality.upper() instanceof guru.interlis.model.CardinalityUpper.Finite finite)
                    || finite.value().compareTo(java.math.BigInteger.ONE) > 0;
            boolean identified = rules.isEmpty()
                    ? baseChain(id).stream().map(index::byId).flatMap(Optional::stream)
                        .anyMatch(d -> d.modifiers().contains(guru.interlis.model.Modifier.OID))
                    : rules.stream().anyMatch(r -> !r.basket() && oidState(r) != 0);
            if (multiple && !identified)
                emitRule(decl, "multiple links for the same participants require OID", "ASSOCIATION_CARDINALITY_OID");
        }
        var h = symbol.header();
        var header = new SymbolHeader(h.id(), h.kind(), h.name(), h.owner(), h.declarationOrder(), h.origin(),
                h.nameRange(), h.languageVersion(), h.documentation(), h.metaAttributes(), h.modifiers(), rules);
        Symbol replacement = switch (symbol) {
            case ClassSymbol c -> new ClassSymbol(header, c.baseType(), c.members(), c.constraints());
            case TopicSymbol t -> new TopicSymbol(header, t.baseTopic(), t.members(), t.dependencies(), t.deferredGenerics(), t.genericBindings());
            case AssociationSymbol a -> new AssociationSymbol(header, a.baseAssociation(), a.cardinality(), a.roles(), a.attributes(), a.constraints(), a.derivedFrom());
            default -> symbol;
        };
        symbols.put(id, replacement); active.remove(id);
        var result = List.copyOf(rules); bound.put(id, result); return result;
    }

    private void validateReferenceTargets() {
        for (var entry : effectiveMembers.entrySet()) {
            checkWork();
            SymbolId sourceOwner = entry.getKey();
            while (sourceOwner != null && index.byId(sourceOwner).isEmpty()) {
                checkWork();
                sourceOwner = symbols.get(sourceOwner).owner().orElse(null);
            }
            if (sourceOwner == null) continue; // Predefined declarations are checked at their source uses.
            DeclInfo owner = index.byId(sourceOwner).orElseThrow();
            SymbolId topic = scopeRoot(owner.id());
            Set<SymbolId> seen = new HashSet<>();
            var pending = new java.util.ArrayDeque<SymbolId>();
            pending.add(entry.getKey());
            while (!pending.isEmpty()) {
                checkWork();
                SymbolId container = pending.removeFirst();
                if (!seen.add(container)) continue;
                for (EffectiveMember member : effectiveMembers.getOrDefault(container, List.of())) {
                    checkWork();
                    Symbol declaration = symbols.get(member.declaredSymbol());
                    List<SymbolId> roots = List.of();
                    boolean external = false;
                    if (declaration instanceof RoleSymbol role) {
                        roots = role.targetRoots(); external = role.external();
                    }
                    else if (declaration instanceof AttributeSymbol attribute) {
                        TypeDescriptor type = effectiveDescriptor(attribute.declaredType());
                        while (type instanceof CollectionType collection) {
                            checkWork(); type = effectiveDescriptor(collection.elementType());
                        }
                        if (type instanceof StructureValueType structure) pending.addAll(structure.targetRoots());
                        if (type instanceof ReferenceType reference) {
                            roots = reference.targetRoots(); external = reference.external();
                        }
                    }
                    DeclInfo site = declaration != null && declaration.header().owner().filter(owner.id()::equals).isPresent()
                            ? index.byId(declaration.id()).orElse(owner) : owner;
                    for (SymbolId root : roots) {
                        checkWork();
                        Symbol target = symbols.get(contextualObject(root, topic));
                        if (target == null || index.byId(target.id()).isEmpty()) continue; // ANYCLASS/unresolved: no invented OID.
                        validateReferenceScope(site, topic, target.id(), external,
                                declaration instanceof RoleSymbol ? "ROLE_EXTERNAL" : "REFERENCE_EXTERNAL");
                        boolean objectOid = target.header().oidRules().stream().anyMatch(r -> !r.basket() && oidState(r) != 0);
                        if (target instanceof ClassSymbol && owner.language() == guru.interlis.model.LanguageVersion.ILI24 && !objectOid)
                            emitRule(site, "reference target " + target.name().value() + " has NO OID in INTERLIS 2.4", "REFERENCE_TARGET_NO_OID");
                        if (target instanceof AssociationSymbol) {
                            boolean identified = target.header().oidRules().isEmpty()
                                    ? baseChain(target.id()).stream().map(index::byId).flatMap(Optional::stream)
                                        .anyMatch(d -> d.modifiers().contains(guru.interlis.model.Modifier.OID)) : objectOid;
                            if (!identified) emitRule(site, "association target " + target.name().value() + " requires its own OID", "REFERENCE_ASSOCIATION_OID");
                        }
                    }
                }
            }
        }
    }

    private int oidState(guru.interlis.model.OidRule rule) {
        if (rule.domain().isEmpty()) return 0;
        Symbol domain = symbols.get(rule.domain().orElseThrow());
        if (domain != null && domain.name().value().equals("INTERLIS.NOOID")) return 0;
        return effectiveDescriptor(declaredTypes.get(rule.domain().orElseThrow())) instanceof guru.interlis.model.OidType oid
                && oid.oidKind() == guru.interlis.model.OidKind.ANY ? 1 : 2;
    }

    private boolean oidOverrideAllowed(guru.interlis.model.OidRule base, guru.interlis.model.OidRule derived,
            guru.interlis.model.LanguageVersion language, boolean topic) {
        if (base.domain().equals(derived.domain())) return true;
        if (base.domain().isPresent() && derived.domain().isPresent()) {
            Symbol a = symbols.get(base.domain().orElseThrow()), b = symbols.get(derived.domain().orElseThrow());
            if (a != null && b != null && a.header().origin() instanceof Origin.SyntheticOrigin
                    && b.header().origin() instanceof Origin.SyntheticOrigin && a.name().equals(b.name())) return true;
        }
        int from = oidState(base), to = oidState(derived);
        if (topic) return false; // A topic assignment is fixed, including ANYOID.
        return from == 0 && to == 0 || from == 1 && to == 2
                || language == guru.interlis.model.LanguageVersion.ILI24 && from == 0 && to == 1;
    }

    SymbolHeader header(DeclInfo decl) {
        var values=new LinkedHashMap<String,MetaAttributeSet.Value>();
        var entries=new ArrayList<MetaAttributeSet.Entry>();
        for(var attribute:decl.metadata().attributes()) {
            var value=new MetaAttributeSet.Value(attribute.text(),new Origin.DeclaredOrigin(attribute.range()));
            entries.add(new MetaAttributeSet.Entry(attribute.key(),value));
            var previous=values.putIfAbsent(attribute.key(),value);
            boolean repeatable=attribute.key().equals("fn.param") && decl.syntax() instanceof FunctionDeclarationSyntax;
            if(previous!=null && !previous.text().equals(attribute.text()) && !repeatable)emitRule(decl,"conflicting source metaattribute: "+attribute.key(),"METAATTRIBUTE_CONFLICT",attribute.range());
        }
        var documentation=new Documentation(decl.metadata().documentation().stream().map(guru.interlis.syntax.SourceMetadata.Text::text).collect(java.util.stream.Collectors.joining("\n\n")),
                decl.metadata().documentation().stream().map(guru.interlis.syntax.SourceMetadata.Text::range).toList());
        DeclaredName declaredName = isSyntheticName(decl.name())
                ? DeclaredName.synthetic(decl.name()) : DeclaredName.declared(decl.name());
        Set<guru.interlis.model.Modifier> modifiers = new LinkedHashSet<>(decl.modifiers());
        if (decl.syntax() instanceof AttributeDeclarationSyntax attribute
                && attribute.transientAttribute()) {
            modifiers.add(guru.interlis.model.Modifier.TRANSIENT);
        }
        if (decl.kind() == SymbolKind.DOMAIN && mandatoryDomain(decl.id())) modifiers.add(guru.interlis.model.Modifier.MANDATORY);
        return new SymbolHeader(decl.id(), decl.kind(), declaredName, decl.owner(),
                decl.order(), decl.origin(), decl.nameRange(), decl.language(),
                documentation, new MetaAttributeSet(values,entries), modifiers, List.of());
    }

    private static boolean isSyntheticName(String name) {
        return name.startsWith("<") || name.startsWith("constraint@")
                || name.startsWith("unsupported@") || name.startsWith("Association@");
    }

    // ---- types -----------------------------------------------------------------

    /** Simple predefined domains allowed by DomainRef. No fallback for unknown names. */
    private Optional<SymbolId> builtinSimpleDomain(DeclInfo context, String name) {
        if (!Set.of("INTERLIS.INTERLIS_1_DATE", "INTERLIS.HALIGNMENT", "INTERLIS.VALIGNMENT").contains(name))
            return Optional.empty();
        SymbolId symbol = new SymbolId("predefined:" + context.language() + ":domain:" + name);
        if (symbols.containsKey(symbol)) return Optional.of(symbol);
        Origin origin = new Origin.PredefinedOrigin(context.language(), name);
        TypeId type = nextTypeId();
        types.put(type, name.equals("INTERLIS.INTERLIS_1_DATE")
                ? new TextType(type, origin, TextKind.TEXT, java.util.OptionalInt.of(8))
                : new guru.interlis.model.AlignmentType(type, origin, name.equals("INTERLIS.HALIGNMENT")
                        ? guru.interlis.model.AlignmentKind.HORIZONTAL : guru.interlis.model.AlignmentKind.VERTICAL));
        declaredTypes.put(symbol, type);
        symbols.put(symbol, new DomainSymbol(new SymbolHeader(symbol, SymbolKind.DOMAIN,
                DeclaredName.synthetic(name), Optional.empty(), 0, origin, Optional.empty(), context.language(),
                Documentation.empty(), MetaAttributeSet.empty(), Set.of(guru.interlis.model.Modifier.FINAL), List.of()),
                type, Optional.empty(), false));
        return Optional.of(symbol);
    }

    private Optional<SymbolId> builtinOidDomain(SymbolId context, String name) {
        if (!Set.of("INTERLIS.NOOID", "INTERLIS.ANYOID", "INTERLIS.I32OID", "INTERLIS.STANDARDOID", "INTERLIS.UUIDOID").contains(name))
            return Optional.empty();
        var language = index.byId(context).orElseThrow().language();
        if (name.equals("INTERLIS.NOOID") && language == guru.interlis.model.LanguageVersion.ILI23)
            return Optional.empty();
        SymbolId symbol = new SymbolId("predefined:" + language + ":domain:" + name);
        if (symbols.containsKey(symbol)) return Optional.of(symbol);
        Optional<SymbolId> base = language == guru.interlis.model.LanguageVersion.ILI23 || name.equals("INTERLIS.NOOID")
                ? Optional.empty()
                : builtinOidDomain(context, name.equals("INTERLIS.ANYOID") ? "INTERLIS.NOOID" : "INTERLIS.ANYOID");
        Origin origin = new Origin.PredefinedOrigin(language, name);
        var kind = name.equals("INTERLIS.I32OID") ? guru.interlis.model.OidKind.NUMERIC
                : name.equals("INTERLIS.UUIDOID") ? guru.interlis.model.OidKind.UUID
                : name.equals("INTERLIS.STANDARDOID") ? guru.interlis.model.OidKind.TEXT : guru.interlis.model.OidKind.ANY;
        Optional<TypeId> carrier = Optional.empty();
        if (kind != guru.interlis.model.OidKind.ANY) {
            TypeId value = nextTypeId();
            types.put(value, kind == guru.interlis.model.OidKind.NUMERIC
                    ? new NumericType(value, origin, NumericRange.of(guru.interlis.model.DecimalLiteral.parse("0"), guru.interlis.model.DecimalLiteral.parse("2147483647")))
                    : new TextType(value, origin, TextKind.TEXT, java.util.OptionalInt.of(kind == guru.interlis.model.OidKind.UUID ? 36 : 16)));
            carrier = Optional.of(value);
        }
        TypeId type = nextTypeId(); types.put(type, new guru.interlis.model.OidType(type, origin, kind, carrier));
        declaredTypes.put(symbol, type); base.ifPresent(b -> baseOf.put(symbol, b));
        symbols.put(symbol, new DomainSymbol(new SymbolHeader(symbol, SymbolKind.DOMAIN, DeclaredName.synthetic(name),
                Optional.empty(), 0, origin, Optional.empty(), language,
                Documentation.empty(), MetaAttributeSet.empty(), language == guru.interlis.model.LanguageVersion.ILI24 && name.equals("INTERLIS.ANYOID")
                        ? Set.of(guru.interlis.model.Modifier.ABSTRACT) : Set.of(), List.of()), type, base, false));
        return Optional.of(symbol);
    }

    private Optional<SymbolId> bindFormatName(SymbolId context,guru.interlis.syntax.NameSyntax name,Set<SymbolKind> kinds) {
        var target=bindTarget(index.byId(context).orElseThrow(),name,kinds,ReferenceUsage.TYPE_USE,new ArrayList<>());
        target.flatMap(index::byId).filter(d->d.kind()==SymbolKind.DOMAIN).ifPresent(this::domainType);
        return target;
    }

    private TypeDescriptor buildFormattedType(TypeId id, FormattedTypeSyntax syntax, SymbolId context) {
        DeclInfo owner = index.byId(context).orElseThrow();
        List<DiagnosticId> errors=new ArrayList<>();
        Optional<SymbolId> domain = syntax.basedOnStructure() || syntax.base().isEmpty()
                ? Optional.ofNullable(baseOf.get(context)) : Optional.empty();
        Optional<SymbolId> structure = Optional.empty();
        Optional<guru.interlis.model.TemporalKind> predefined = Optional.empty();
        if (syntax.base().isPresent()) {
            var name = syntax.base().orElseThrow();
            if (syntax.basedOnStructure()) structure = bindFormatName(context, name, Set.of(SymbolKind.STRUCTURE));
            else domain = bindFormatName(context, name, Set.of(SymbolKind.DOMAIN));
        }
        TypeDescriptor inherited = domain.map(declaredTypes::get).map(this::effectiveDescriptor).orElse(null);
        FormattedType base = inherited instanceof FormattedType f ? f : null;
        List<FormattedType.Part> parts = new ArrayList<>();
        if (!syntax.basedOnStructure()) {
            if (base == null) errors.add(emitRule(owner, "FORMAT requires a bound formatted base domain", "FORMAT_BASE"));
            else { structure = base.structure(); predefined = base.predefined(); parts.addAll(base.parts()); }
        } else if (syntax.inheritance()) {
            if (base == null || structure.isEmpty() || base.structure().isEmpty()
                    || !targetSpecializes(structure.orElseThrow(), base.structure().orElseThrow()))
                errors.add(emitRule(owner, "FORMAT INHERITANCE requires the base structure or a specialization", "FORMAT_INHERITANCE"));
            else for (var part : base.parts()) {
                // INHERITANCE keeps the layout and provenance, but uses the effective
                // attribute of the specialized structure (including its precision).
                Optional<SymbolId> attribute = part.attribute().isEmpty() ? Optional.empty()
                        : findMember(structure.orElseThrow(), symbols.get(part.attribute().orElseThrow()).name().value());
                parts.add(new FormattedType.Part(attribute, part.width(), part.format(), part.literal(), part.origin()));
            }
        } else if (base != null) errors.add(emitRule(owner, "extended FORMAT must retain its inherited prefix", "FORMAT_INHERITANCE"));
        for (var part : syntax.parts()) {
            checkWork();
            Optional<SymbolId> attribute = Optional.empty(), nested = Optional.empty();
            if (part.attribute().isEmpty()) {
                if (!part.literal().isEmpty() && Character.isDigit(part.literal().charAt(0)))
                    errors.add(emitRule(owner, "FORMAT separator must not start with a digit", "FORMAT_SEPARATOR"));
            } else {
                var name = part.attribute().orElseThrow();
                attribute = structure.flatMap(st -> findMember(st, name.text()));
                recordReference(owner, name, attribute.orElse(null), ReferenceUsage.TYPE_USE);
                if (attribute.isEmpty()) errors.add(unresolved(name));
                attribute.flatMap(index::byId).ifPresent(this::attributeType);
                TypeDescriptor value = attribute.map(declaredTypes::get).map(this::effectiveDescriptor).orElse(null);
                if (part.format().isPresent()) {
                    nested = bindFormatName(context, part.format().orElseThrow(), Set.of(SymbolKind.DOMAIN));
                    TypeDescriptor nestedType = nested.map(declaredTypes::get).map(this::effectiveDescriptor).orElse(null);
                    SymbolId selected = value instanceof CollectionType c && !isMany(c.cardinality()) ? structures.target(c) : structures.target(value);
                    if (value instanceof CollectionType c && isMany(c.cardinality())) selected = null;
                    if (!(nestedType instanceof FormattedType f) || f.structure().isEmpty() || selected == null
                            || !targetSpecializes(selected, f.structure().orElseThrow()))
                        errors.add(emitRule(owner, "nested FORMAT requires a compatible singleton structure", "FORMAT_ATTRIBUTE"));
                } else if (!(value instanceof NumericType)) errors.add(emitRule(owner, "FORMAT attribute must be numeric", "FORMAT_ATTRIBUTE"));
                else {
                    NumericRange range = ((NumericType)value).range();
                    if (range.minimum().isPresent() && range.maximum().isPresent()
                            && range.minimum().orElseThrow().precision().fractionalDigits()
                            != range.maximum().orElseThrow().precision().fractionalDigits())
                        errors.add(emitRule(owner, "FORMAT component bounds must have matching precision", "FORMAT_PRECISION"));
                }
                if (part.width().filter(w -> w.signum() <= 0).isPresent()) errors.add(emitRule(owner, "FORMAT width must be positive", "FORMAT_WIDTH"));
            }
            parts.add(new FormattedType.Part(attribute, part.width(), nested, part.literal(), new Origin.DeclaredOrigin(part.range())));
        }
        var result = new FormattedType(id, new Origin.DeclaredOrigin(syntax.range()),
                syntax.base().map(guru.interlis.syntax.NameSyntax::text).orElse("<inherited>"),
                predefined, domain, structure, parts, syntax.minimum(), syntax.maximum());
        if (result.minimum().isPresent() || result.maximum().isPresent()) {
            var min = result.minimum().flatMap(v -> formatValue(result, v));
            var max = result.maximum().flatMap(v -> formatValue(result, v));
            if (min.isEmpty() || max.isEmpty() || FormattedValues.compare(min.orElseThrow(), max.orElseThrow()) > 0)
                errors.add(emitRule(owner, "FORMAT bounds must fit the layout and component ranges", "FORMAT_BOUNDS"));
            else {
                Set<TypeId> seen = new LinkedHashSet<>();
                for (FormattedType ancestor = base; ancestor != null && seen.add(ancestor.id());) {
                    checkWork();
                    var lower = ancestor.minimum().isPresent() ? formatValue(ancestor, ancestor.minimum().orElseThrow()) : Optional.<List<java.math.BigDecimal>>empty();
                    var upper = ancestor.maximum().isPresent() ? formatValue(ancestor, ancestor.maximum().orElseThrow()) : Optional.<List<java.math.BigDecimal>>empty();
                    if (lower.filter(v -> FormattedValues.compare(min.orElseThrow(), v) < 0).isPresent()
                            || upper.filter(v -> FormattedValues.compare(max.orElseThrow(), v) > 0).isPresent())
                        errors.add(emitRule(owner, "FORMAT extension widens base bounds", "FORMAT_BOUNDS"));
                    TypeDescriptor next = ancestor.baseDomain().map(declaredTypes::get).map(this::effectiveDescriptor).orElse(null);
                    ancestor = next instanceof FormattedType f ? f : null;
                }
            }
        }
        return errors.isEmpty()?result:new ErrorType(id,new Origin.DeclaredOrigin(syntax.range()),errors);
    }

    private void validateFormattedUses() {
        for (var entry : declaredTypes.entrySet()) {
            checkWork();
            DeclInfo declaration = index.byId(entry.getKey()).orElse(null);
            if (declaration == null || declaration.kind() != SymbolKind.DOMAIN && declaration.kind() != SymbolKind.ATTRIBUTE)
                continue;
            TypeDescriptor type = effectiveDescriptor(entry.getValue());
            while (type instanceof CollectionType collection) type = effectiveDescriptor(collection.elementType());
            if (type instanceof FormattedType format && formattedValues().isAbstract(format)
                    && !declaration.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))
                emitRule(declaration, "FORMAT with abstract components requires an abstract domain or attribute", "FORMAT_ABSTRACT");
        }
    }

    private FormattedValues formattedValues() {
        return new FormattedValues(id -> effectiveDescriptor(declaredTypes.get(id)), this::checkWork);
    }

    private Optional<List<java.math.BigDecimal>> formatValue(FormattedType type, String value) {
        return formattedValues().parse(type, value, false);
    }

    private TypeId buildType(TypeSyntax syntax, SymbolId context, SourceId source) {
        checkWork();
        Objects.requireNonNull(syntax, "syntax");
        TypeId id = nextTypeId();
        TypeDescriptor descriptor = switch (syntax) {
            case TextTypeSyntax text -> {
                if (text.maxLength().isPresent() && text.maxLength().getAsInt() == 0)
                    yield new ErrorType(id, new Origin.DeclaredOrigin(text.range()), List.of(emitRule(index.byId(context).orElseThrow(),
                            "declared text maximum length must be positive", "TEXT_LENGTH", text.range())));
                yield new TextType(id, new Origin.DeclaredOrigin(text.range()), text.textKind(),
                    text.maxLength().isPresent() ? text.maxLength() : switch (text.textKind()) {
                        case NAME -> java.util.OptionalInt.of(255);
                        case URI -> java.util.OptionalInt.of(1023);
                        default -> java.util.OptionalInt.empty();
                    });
            }
            case BooleanTypeSyntax bool -> new guru.interlis.model.BooleanType(id,
                    new Origin.DeclaredOrigin(bool.range()));
            case TemporalTypeSyntax syntaxTime -> {
                String name = switch (syntaxTime.temporalKind()) {
                    case DATE -> "XMLDate"; case TIMEOFDAY -> "XMLTime"; case DATETIME -> "XMLDateTime";
                };
                var domain = temporal.find(index.byId(context).orElseThrow(), "INTERLIS." + name, Set.of(SymbolKind.DOMAIN)).orElseThrow();
                yield new DomainReferenceType(id, new Origin.DeclaredOrigin(syntaxTime.range()), domain);
            }
            case OidTypeSyntax oid -> {
                Optional<TypeId> carrier = oid.valueType().map(t -> buildType(t, context, source));
                if (oid.oidKind() == guru.interlis.model.OidKind.UUID) {
                    TypeId text = nextTypeId(); types.put(text, new TextType(text, new Origin.DeclaredOrigin(oid.range()), TextKind.TEXT, java.util.OptionalInt.of(36)));
                    carrier = Optional.of(text);
                }
                yield new guru.interlis.model.OidType(id, new Origin.DeclaredOrigin(oid.range()), oid.oidKind(), carrier);
            }
            case BlackboxTypeSyntax blackbox -> new guru.interlis.model.BlackboxType(id,
                    new Origin.DeclaredOrigin(blackbox.range()), blackbox.blackboxKind());
            case AlignmentTypeSyntax alignment -> new guru.interlis.model.AlignmentType(id,
                    new Origin.DeclaredOrigin(alignment.range()), alignment.alignment());
            case EnumTreeValueSyntax enumTree -> {
                List<DiagnosticId> errors = new ArrayList<>();
                Optional<SymbolId> domain = bindTarget(index.byId(context).orElseThrow(), enumTree.target(),
                        Set.of(SymbolKind.DOMAIN), ReferenceUsage.TYPE_USE, errors);
                if (domain.isEmpty()) yield new ErrorType(id, new Origin.DeclaredOrigin(enumTree.range()), errors);
                index.byId(domain.orElseThrow()).ifPresent(this::domainType);
                TypeDescriptor base=effectiveDescriptor(declaredTypes.get(domain.orElseThrow()));
                if(!(base instanceof EnumerationType enumeration) || enumeration.treeValues())
                    yield new ErrorType(id,new Origin.DeclaredOrigin(enumTree.range()),List.of(emitRule(index.byId(context).orElseThrow(),"ALL OF requires an enumeration domain","ENUM_TREE_TARGET",enumTree.range())));
                yield new EnumerationType(id,new Origin.DeclaredOrigin(enumTree.range()),enumeration.values(),enumeration.ordered(),
                        enumeration.circular(),enumeration.finalSubtrees(),Optional.of(enumeration.id()),true);
            }
            case guru.interlis.syntax.EnumArgumentTypeSyntax argument ->
                new guru.interlis.model.EnumArgumentType(id,new Origin.DeclaredOrigin(argument.range()),argument.treeValues());

            case guru.interlis.syntax.StructureValueTypeSyntax structure -> structures.bind(id,structure,index.byId(context).orElseThrow());
            case GenericTypeSyntax generic -> {
                if ("INHERITED".equals(generic.name())) {
                    yield new ErrorType(id, new Origin.DeclaredOrigin(generic.range()), List.of(emitRule(
                            index.byId(context).orElseThrow(), "a type can only be omitted on an inherited attribute",
                            "INHERITED_TYPE_CONTEXT")));
                }
                yield new GenericType(id, new Origin.DeclaredOrigin(generic.range()), generic.name());
            }
            case guru.interlis.syntax.AttributePathTypeSyntax path -> {
                Optional<guru.interlis.model.AttributePathType.Owner> owner = Optional.empty();
                if (path.argument().isPresent()) {
                    DeclInfo declaration = index.byId(context).orElseThrow();
                    String name = path.argument().orElseThrow().text();
                    int position = -1;
                    if (declaration.syntax() instanceof FunctionDeclarationSyntax function) {
                        for (int i = 0; i < function.parameters().size(); i++)
                            if (function.parameters().get(i).name().equals(name)) { position = i; break; }
                    }
                    if (position < 0) {
                        yield new ErrorType(id, new Origin.DeclaredOrigin(path.range()), List.of(emitRule(declaration,
                                "ATTRIBUTE OF @ requires an argument of this function", "ATTRIBUTE_ARGUMENT")));
                    }
                    TypeDescriptor argumentType = effectiveDescriptor(functionArgumentType(declaration, position));
                    if (!(argumentType instanceof guru.interlis.model.ObjectArgumentType || argumentType instanceof StructureValueType
                            || argumentType instanceof ClassValueType || argumentType instanceof guru.interlis.model.AttributePathType || argumentType instanceof ErrorType))
                        emitRule(declaration, "ATTRIBUTE OF @ argument must describe objects, classes or an attribute path", "ATTRIBUTE_ARGUMENT_TYPE");
                    owner = Optional.of(new guru.interlis.model.AttributePathType.Argument(context, position));
                }
                Optional<ExpressionResult> ownerClass = path.ownerClass().map(expression -> buildExpression(expression, index.byId(context).orElseThrow().owner().orElse(context)));
                List<TypeId> restrictions = path.restrictions().stream().map(t -> buildType(t, context, source)).toList();
                if (ownerClass.isPresent() && !(effectiveDescriptor(ownerClass.orElseThrow().typeId()) instanceof ClassValueType))
                    emitRule(index.byId(context).orElseThrow(), "ATTRIBUTE OF requires a class-valued attribute path", "ATTRIBUTE_PATH_TYPE");
                yield new guru.interlis.model.AttributePathType(id, new Origin.DeclaredOrigin(path.range()),
                        ownerClass.isPresent() ? Optional.of(new guru.interlis.model.AttributePathType.ClassAttribute(ownerClass.orElseThrow().id())) : owner, restrictions);
            }
            case ClassValueTypeSyntax classValue -> {
                List<DiagnosticId> errors = new ArrayList<>();
                List<SymbolId> restrictions = new ArrayList<>();
                var kinds = classValue.structureValue() ? Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE)
                        : Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW);
                for (var name : classValue.restrictions()) {
                    bindTarget(index.byId(context).orElseThrow(), name, kinds, ReferenceUsage.TYPE_USE, errors).ifPresent(target -> {
                        SymbolId current = target;
                        Set<SymbolId> visited = new HashSet<>();
                        while (current != null && visited.add(current)) { checkWork(); current = objectInheritanceBase(current).orElse(null); }
                        restrictions.add(target);
                    });
                }
                yield errors.isEmpty() ? new ClassValueType(id, new Origin.DeclaredOrigin(classValue.range()), classValue.structureValue(), restrictions)
                        : new ErrorType(id, new Origin.DeclaredOrigin(classValue.range()), errors);
            }
            case guru.interlis.syntax.ObjectArgumentTypeSyntax object ->
                    buildObjectArgumentType(id, object, context);
            case guru.interlis.syntax.MetaObjectTypeSyntax meta -> {
                var declaration = index.byId(context).orElseThrow();
                var target = meta.target().flatMap(name -> {
                    var predefined = metadata.predefined(declaration, name.text(), Set.of(SymbolKind.CLASS));
                    if (predefined.isPresent()) recordReference(declaration, name, predefined.get(), ReferenceUsage.TYPE_USE);
                    return predefined.isPresent() ? predefined : bindTarget(declaration, name, Set.of(SymbolKind.CLASS), ReferenceUsage.TYPE_USE, new ArrayList<>());
                });
                target.ifPresent(idTarget -> {
                    metadata.ensureBases(idTarget);
                    var root = metadata.predefined(declaration, "INTERLIS.METAOBJECT", Set.of(SymbolKind.CLASS)).orElseThrow();
                    if (!guru.interlis.model.internal.TypeCompatibility.specializes(idTarget, root, baseOf::get))
                        emitRule(declaration, "METAOBJECT OF requires a metadata class", "PARAMETER_METAOBJECT_TARGET");
                });
                yield new guru.interlis.model.MetaObjectType(id, new Origin.DeclaredOrigin(meta.range()), target);
            }
            case LineTypeSyntax line -> lines.buildType(id, line, index.byId(context).orElseThrow());
            case FormattedTypeSyntax formatted -> buildFormattedType(id, formatted, context);
            case guru.interlis.syntax.NumericTypeSyntax numeric -> {
                var invalid = new ArrayList<>(numericErrors(numeric, index.byId(context).orElseThrow()));
                if (!invalid.isEmpty()) yield new ErrorType(id, new Origin.DeclaredOrigin(numeric.range()), invalid);
                var range=metadata.numeric(numeric,index.byId(context).orElseThrow(),invalid);
                validateNumericUnit(range,index.byId(context).orElseThrow(),numeric.range(),invalid);
                yield invalid.isEmpty()?new NumericType(id,new Origin.DeclaredOrigin(numeric.range()),range)
                        :new ErrorType(id,new Origin.DeclaredOrigin(numeric.range()),invalid);
            }
            case EnumerationTypeSyntax enumeration -> {
                DeclInfo declaration = index.byId(context).orElseThrow();
                TypeDescriptor inherited = null;
                if (declaration.kind() == SymbolKind.ATTRIBUTE && declaration.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                    SymbolId owner = declaration.owner().orElse(null);
                    AttributeSymbol baseAttribute = owner == null ? null : findBaseAttribute(baseOf.get(owner), declaration.name());
                    if (baseAttribute != null) inherited = effectiveDescriptor(baseAttribute.declaredType());
                }
                // Domains are merged after their base reference has been bound.
                yield declaration.kind() == SymbolKind.DOMAIN
                        ? new EnumerationType(id, new Origin.DeclaredOrigin(enumeration.range()), List.of(),
                                enumeration.ordered(), enumeration.circular(), enumeration.finalSubtrees(), Optional.empty(), false)
                        : effectiveEnumeration(id, enumeration, inherited, declaration);
            }
            case DomainReferenceSyntax reference -> {
                var declaration=index.byId(context).orElseThrow();var errors=new ArrayList<DiagnosticId>();
                var target=bindTarget(declaration,reference.name(),Set.of(SymbolKind.DOMAIN,SymbolKind.STRUCTURE),ReferenceUsage.TYPE_USE,errors);
                if(target.isEmpty())yield new ErrorType(id,new Origin.DeclaredOrigin(reference.range()),errors);
                SymbolId selected=target.orElseThrow();
                var sourceDeclaration=index.byId(selected);
                SymbolKind kind=sourceDeclaration.map(DeclInfo::kind).orElseGet(()->symbols.get(selected).kind());
                if(kind==SymbolKind.STRUCTURE)yield new StructureValueType(id,new Origin.DeclaredOrigin(reference.range()),Optional.of(selected),List.of());
                sourceDeclaration.ifPresent(this::domainType);
                yield new DomainReferenceType(id,new Origin.DeclaredOrigin(reference.range()),selected);
            }
            case ReferenceTypeSyntax reference -> {
                DeclInfo declaration = index.byId(context).orElseThrow();
                Optional<SymbolId> target = reference.target().text().equals("ANYCLASS") ? Optional.empty()
                        : Optional.of(bindObjectTarget(declaration, reference.target(), ReferenceUsage.TYPE_USE));
                List<SymbolId> restrictions = reference.restrictions().stream()
                        .map(name -> bindObjectTarget(declaration, name, ReferenceUsage.TYPE_USE)).toList();
                for (SymbolId restriction : restrictions) {
                    checkWork();
                    if (target.isPresent() && !targetSpecializes(restriction, target.orElseThrow()))
                        emitRule(declaration, "reference restriction must specialize its primary target", "REFERENCE_TARGET_RESTRICTION");
                }
                yield new ReferenceType(id, new Origin.DeclaredOrigin(reference.range()), target, restrictions, reference.external());
            }
            case CollectionTypeSyntax collection -> {
                TypeId element = buildType(collection.elementType(), context, source);
                if (index.byId(context).orElseThrow().language() == guru.interlis.model.LanguageVersion.ILI23
                        && !(effectiveDescriptor(element) instanceof StructureValueType)
                        && !(effectiveDescriptor(element) instanceof ErrorType))
                    emitRule(index.byId(context).orElseThrow(), "INTERLIS 2.3 collections require structure elements", "COLLECTION_ELEMENT_VERSION");
                yield new CollectionType(id, new Origin.DeclaredOrigin(collection.range()),
                        collection.collectionKind(), collection.cardinality(), element);
            }
            case CoordinateTypeSyntax coordinate -> {
                List<DiagnosticId> invalid = new ArrayList<>();
                for (var axis : coordinate.axes()) invalid.addAll(numericErrors(axis, index.byId(context).orElseThrow()));
                if (!invalid.isEmpty()) yield new ErrorType(id, new Origin.DeclaredOrigin(coordinate.range()), invalid);
                var axes=new ArrayList<NumericRange>();
                for(var axis:coordinate.axes()) {
                    var range=metadata.numeric(axis,index.byId(context).orElseThrow(),invalid);
                    validateNumericUnit(range,index.byId(context).orElseThrow(),axis.range(),invalid);axes.add(range);
                }
                yield invalid.isEmpty()?new guru.interlis.model.CoordinateType(id,
                    new Origin.DeclaredOrigin(coordinate.range()),coordinate.dimensions(),axes,coordinate.multiCoordinate(),coordinate.referenceSystem(),coordinate.rotation())
                        :new ErrorType(id,new Origin.DeclaredOrigin(coordinate.range()),invalid);
            }
            case guru.interlis.syntax.UnsupportedTypeSyntax unsupported -> {
                if (unsupported.featureId().equals("MISSING_ATTRIBUTE_TYPE") || unsupported.featureId().equals("MISSING_FUNCTION_RETURN_TYPE")) {
                    DiagnosticId cause = null;
                    int distance = Integer.MAX_VALUE;
                    for (var diagnostic : syntaxErrors) {
                        checkWork();
                        var range = diagnostic.primaryRange().orElse(null);
                        if (range == null || !range.source().equals(unsupported.range().source())
                                || !range.revision().equals(unsupported.range().revision())) continue;
                        int candidate = Math.abs(range.startOffset() - unsupported.range().endOffset());
                        if (candidate < distance) { cause = diagnostic.id(); distance = candidate; }
                    }
                    if (cause == null) cause = emitRule(index.byId(context).orElseThrow(), "attribute type is missing", "SOURCE_MISSING_TYPE", unsupported.range());
                    yield new ErrorType(id, new Origin.DeclaredOrigin(unsupported.range()), List.of(cause));
                }
                DiagnosticId diagnostic = emitUnsupported(source, unsupported.range(),
                        unsupported.featureId(), unsupported.construct());
                yield new ErrorType(id, new Origin.DeclaredOrigin(unsupported.range()),
                        List.of(diagnostic));
            }
        };
        types.put(id, descriptor);
        return id;
    }

    private TypeDescriptor buildObjectArgumentType(TypeId id,
            guru.interlis.syntax.ObjectArgumentTypeSyntax syntax, SymbolId context) {
        DeclInfo owner = index.byId(context).orElseThrow();
        List<DiagnosticId> errors = new ArrayList<>();
        List<SymbolId> restrictions = new ArrayList<>();
        Optional<SymbolId> base = syntax.base().flatMap(name ->
                bindObjectTarget(owner, name, errors));
        for (var name : syntax.restrictions()) {
            checkWork();
            Optional<SymbolId> restriction = bindObjectTarget(owner, name, errors);
            restriction.ifPresent(restrictions::add);
            if (base.isPresent() && restriction.isPresent()
                    && !guru.interlis.model.internal.TypeCompatibility.specializes(
                            restriction.get(), base.get(), baseOf::get)) {
                errors.add(collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0005"),
                                Severity.ERROR, Phase.TYPE_CHECKING, "diagnostic.semantics.type")
                        .at(name.range())
                        .argument(new DiagnosticArgument.TypeNameArgument("actual", name.text()))
                        .argument(new DiagnosticArgument.TypeNameArgument("expected",
                                "specialization of " + syntax.base().orElseThrow().text()))));
            }
        }
        Origin origin = new Origin.DeclaredOrigin(syntax.range());
        return errors.isEmpty()
                ? new guru.interlis.model.ObjectArgumentType(id, origin, base, restrictions, syntax.objects())
                : new ErrorType(id, origin, errors);
    }

    private Optional<SymbolId> bindObjectTarget(DeclInfo owner,
            guru.interlis.syntax.NameSyntax name, List<DiagnosticId> errors) {
        return bindTarget(owner, name, Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW), ReferenceUsage.TYPE_USE, errors);
    }

    /** One source-name selection for user declarations and canonical predefined targets. */
    private NameResolver.Resolution resolveName(DeclInfo owner, guru.interlis.syntax.NameSyntax name,
            Set<SymbolKind> kinds, boolean relativeQualifiers) {
        checkWork();
        if(name.text().startsWith("INTERLIS.")) {
            var predefined=predefinedTarget(owner,name.text(),kinds);
            if(predefined.isPresent())return new NameResolver.Resolution(NameResolver.Status.BOUND,predefined,List.of());
            return resolver.resolve(owner.id(),name,kinds,relativeQualifiers);
        }
        if(name.segments().size()!=1)return resolver.resolve(owner.id(),name,kinds,relativeQualifiers);
        var local=resolver.resolveLocal(owner.id(),name,kinds);
        if(local.status()!=NameResolver.Status.UNRESOLVED)return local;
        var normal=resolver.resolve(owner.id(),name,kinds,relativeQualifiers);
        boolean visible=index.modelOfSymbol(owner.id()).flatMap(index::byId).filter(d->d.syntax() instanceof guru.interlis.syntax.ModelSyntax)
                .map(d->((guru.interlis.syntax.ModelSyntax)d.syntax()).imports().stream().anyMatch(i->i.modelName().equals("INTERLIS") && i.unqualified())).orElse(false);
        // Preserve the existing priority of actual imported declarations. In particular,
        // ambiguity is a result, never a reason to substitute a builtin function.
        if(!visible || normal.status()!=NameResolver.Status.UNRESOLVED)return normal;
        var predefined=predefinedTarget(owner,"INTERLIS."+name.text(),kinds);
        return predefined.isPresent()
                ? new NameResolver.Resolution(NameResolver.Status.BOUND,predefined,List.of()) : normal;
    }

    private Optional<SymbolId> predefinedTarget(DeclInfo owner,String qualified,Set<SymbolKind> kinds) {
        if(!qualified.startsWith("INTERLIS."))return Optional.empty();
        Optional<SymbolId> target=kinds.contains(SymbolKind.DOMAIN)?builtinOidDomain(owner.id(),qualified):Optional.empty();
        if(target.isEmpty() && kinds.contains(SymbolKind.DOMAIN))target=builtinSimpleDomain(owner,qualified);
        if(target.isEmpty())target=temporal.find(owner,qualified,kinds);
        if(target.isEmpty())target=metadata.predefined(owner,qualified,kinds);
        if(target.isEmpty())target=lines.predefined(owner,qualified,kinds);
        if(target.isEmpty() && kinds.contains(SymbolKind.UNIT))target=builtinUnit(owner.id(),qualified);
        if(target.isEmpty() && kinds.contains(SymbolKind.FUNCTION) && qualified.indexOf('.',9)<0) {
            var signature=BuiltinFunctions.find(qualified.substring(9)).filter(f->f.since()!=guru.interlis.model.LanguageVersion.ILI24 || owner.language()==guru.interlis.model.LanguageVersion.ILI24);
            if(signature.isPresent())target=Optional.of(builtinDeclaration(signature.orElseThrow(),owner.language()).id());
        }
        return target;
    }

    Optional<SymbolId> bindTarget(DeclInfo owner, guru.interlis.syntax.NameSyntax name,
            Set<SymbolKind> kinds, ReferenceUsage usage, List<DiagnosticId> errors) {
        return bindTarget(owner, name, kinds, usage, errors, true);
    }

    Optional<SymbolId> bindTarget(DeclInfo owner, guru.interlis.syntax.NameSyntax name,
            Set<SymbolKind> kinds, ReferenceUsage usage, List<DiagnosticId> errors, boolean relativeQualifiers) {
        var resolution = resolveName(owner, name, kinds, relativeQualifiers);
        Optional<SymbolId> target = resolution.target();
        recordReference(owner, name, target.orElse(null), usage);
        if (resolution.status() == NameResolver.Status.AMBIGUOUS) {
            errors.add(collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0002"),
                            Severity.ERROR, Phase.BINDING, "diagnostic.semantics.ambiguous")
                    .at(name.range())
                    .argument(new DiagnosticArgument.IdentifierArgument("name", name.text()))
                    .argument(new DiagnosticArgument.IdentifierListArgument("candidates",
                            resolution.candidates().stream().map(SymbolId::value).toList()))));
        } else if (target.isEmpty()) {
            errors.add(unresolved(name));
        }
        return target;
    }

    private Optional<SymbolId> tryResolve(DeclInfo context,
                                          guru.interlis.syntax.NameSyntax name,
                                          Set<SymbolKind> expected) {
        NameResolver.Resolution resolution = resolver.resolve(context.id(), name, expected);
        return resolution.status() == NameResolver.Status.BOUND
                ? resolution.target() : Optional.empty();
    }

    DiagnosticId unresolved(guru.interlis.syntax.NameSyntax name) {
        return collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0001"),
                        Severity.ERROR, Phase.BINDING, "diagnostic.semantics.unresolved")
                .at(name.range())
                .argument(new DiagnosticArgument.IdentifierArgument("name", name.text())));
    }

    private TypeId errorType(SourceId source, SourceRange range, String feature) {
        TypeId id = nextTypeId();
        DiagnosticId diagnostic = emitUnsupported(source, range, feature, feature);
        types.put(id, new ErrorType(id, new Origin.SyntheticOrigin(
                guru.interlis.model.SyntheticReason.MISSING_TYPE, Optional.of(range)),
                List.of(diagnostic)));
        return id;
    }

    DiagnosticId emitUnsupported(SourceId source, SourceRange range, String feature,
                                         String construct) {
        DiagnosticDraft draft = DiagnosticDraft.of(new DiagnosticCode("ILI-IMPL-0001"),
                        Severity.ERROR, Phase.SEMANTIC_CHECKING,
                        "diagnostic.implementation.unsupported")
                .argument(new DiagnosticArgument.TextArgument("feature", feature))
                .argument(new DiagnosticArgument.TextArgument("construct",
                        construct == null ? feature : construct));
        if (range != null) {
            draft.at(range);
        }
        unsupportedBySource.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(feature);
        return collector.emit(draft);
    }

    private TypeId builtinBoolean(guru.interlis.model.LanguageVersion language) {
        return booleanTypes.computeIfAbsent(language,version->{
            TypeId id=nextTypeId();types.put(id,new guru.interlis.model.BooleanType(id,new Origin.PredefinedOrigin(version,"BOOLEAN")));return id;
        });
    }

    TypeId nextTypeId() {
        return new TypeId("type-" + (++typeCounter));
    }

    // ---- expressions -----------------------------------------------------------

    ExpressionResult buildExpression(ExpressionSyntax syntax, SymbolId owner) {
        ExpressionId id = new ExpressionId("expr-" + (++expressionCounter));
        return switch (syntax) {
            case LiteralExpressionSyntax literal -> literalExpression(id, literal, owner);
            case PathExpressionSyntax path -> pathExpression(id, path, owner);
            case BinaryExpressionSyntax binary -> binaryExpression(id, binary, owner);
            case UnaryExpressionSyntax unary -> unaryExpression(id, unary, owner);
            case DefinedExpressionSyntax defined -> definedExpression(id, defined, owner);
            case ConstantExpressionSyntax constant -> constantExpression(id, constant, owner);
            case UndefinedExpressionSyntax undefined -> undefinedExpression(id, undefined, owner);
            case ParenthesizedExpressionSyntax parenthesized ->
                    buildExpression(parenthesized.inner(), owner);
            case FunctionCallExpressionSyntax call -> functionCallExpression(id, call, owner);
            case BuiltinFunctionExpressionSyntax builtin -> builtinFunctionExpression(id, builtin, owner);
            case EnumerationConstantExpressionSyntax constant -> enumerationConstantExpression(id, constant, owner);
            case ClassConstantExpressionSyntax constant -> classConstantExpression(id, constant, owner);
            case guru.interlis.syntax.ObjectSetExpressionSyntax set -> objectSetExpression(id, set, owner);
            case guru.interlis.syntax.InspectionExpressionSyntax inspection -> views.factor(id, inspection, owner);
            case GenericConstantExpressionSyntax constant -> genericConstantExpression(id, constant, owner);
            case guru.interlis.syntax.ParameterExpressionSyntax parameter -> parameterExpression(id, parameter, owner);
            case guru.interlis.syntax.AttributeConstantExpressionSyntax constant -> attributeConstantExpression(id, constant, owner);
            case UnsupportedExpressionSyntax unsupported -> {
                DiagnosticId diagnostic = emitUnsupported(ownerSource(owner), unsupported.range(),
                        unsupported.featureId(), unsupported.construct());
                TypeId error = errorType(ownerSource(owner), unsupported.range(),
                        unsupported.featureId());
                ErrorExpression expression = new ErrorExpression(id, error,
                        guru.interlis.model.ExpressionTypeState.ERROR, unsupported.range(),
                        List.of(), List.of(diagnostic));
                expressions.put(id, expression);
                yield new ExpressionResult(id, error, true);
            }
        };
    }

    private ExpressionResult objectSetExpression(ExpressionId id,
            guru.interlis.syntax.ObjectSetExpressionSyntax syntax, SymbolId owner) {
        TypeId type;
        if (syntax.selection().isPresent()) {
            type = buildType(syntax.selection().orElseThrow(), owner, ownerSource(owner));
        } else {
            SymbolId context = expressionContext(owner);
            var kind = index.byId(context).map(DeclInfo::kind).orElse(null);
            type = nextTypeId();
            if (kind == SymbolKind.CLASS || kind == SymbolKind.ASSOCIATION || kind == SymbolKind.VIEW) {
                types.put(type, new guru.interlis.model.ObjectArgumentType(type,
                        new Origin.DeclaredOrigin(syntax.range()), Optional.of(context), List.of(), true));
            } else {
                type = errorType(ownerSource(owner), syntax.range(), "ALL requires an object context");
            }
        }
        boolean error = effectiveDescriptor(type) instanceof ErrorType;
        expressions.put(id, new guru.interlis.model.ObjectSetExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range()));
        return new ExpressionResult(id, type, error);
    }

    private SymbolId expressionContext(SymbolId owner) {
        SymbolId context = owner;
        while (index.byId(context).map(DeclInfo::kind).orElse(null) == SymbolKind.CONSTRAINT) {
            checkWork();
            context = externalConstraintTargets.getOrDefault(context,
                    index.byId(context).orElseThrow().owner().orElseThrow());
        }
        return graphics.context(context);
    }

    private ExpressionResult genericConstantExpression(ExpressionId id,
                                                       GenericConstantExpressionSyntax syntax,
                                                       SymbolId owner) {
        TypeId type = nextTypeId();
        types.put(type, new GenericType(type, new Origin.DeclaredOrigin(syntax.range()), syntax.name()));
        expressions.put(id, new guru.interlis.model.ConstantExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.name()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult classConstantExpression(ExpressionId id,
                                                     ClassConstantExpressionSyntax syntax,
                                                     SymbolId owner) {
        List<DiagnosticId> errors = new ArrayList<>();
        var declaration = index.byId(owner).orElseThrow();
        var kinds = Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE, SymbolKind.ASSOCIATION, SymbolKind.VIEW);
        var target = lines.predefined(declaration, syntax.target().text(), kinds);
        if (target.isEmpty()) target = metadata.predefined(declaration, syntax.target().text(), kinds);
        if (target.isEmpty()) target = bindTarget(declaration, syntax.target(), kinds, ReferenceUsage.EXPRESSION, errors);
        else recordReference(declaration, syntax.target(), target.orElseThrow(), ReferenceUsage.EXPRESSION);
        if (target.isEmpty()) return failedValueExpression(id, syntax.range(), errors);
        TypeId type = nextTypeId();
        boolean structure = symbols.get(target.orElseThrow()) instanceof StructureSymbol
                || index.byId(target.orElseThrow()).map(d -> d.kind() == SymbolKind.STRUCTURE).orElse(false);
        types.put(type, new ClassValueType(type, new Origin.DeclaredOrigin(syntax.range()), structure, target.stream().toList()));
        expressions.put(id, new guru.interlis.model.SymbolValueExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), target.orElseThrow()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult attributeConstantExpression(ExpressionId id, guru.interlis.syntax.AttributeConstantExpressionSyntax syntax, SymbolId owner) {
        var declaration = index.byId(owner).orElseThrow();
        List<DiagnosticId> errors = new ArrayList<>();
        Optional<SymbolId> viewable = syntax.viewable().isPresent()
                ? bindTarget(declaration, syntax.viewable().orElseThrow(), Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE, SymbolKind.ASSOCIATION, SymbolKind.VIEW), ReferenceUsage.EXPRESSION, errors)
                : Optional.of(expressionContext(owner));
        Optional<SymbolId> attribute = viewable.flatMap(target -> findMember(target, syntax.attribute().text()))
                .filter(target -> index.byId(target).map(DeclInfo::kind).orElse(null) == SymbolKind.ATTRIBUTE || symbols.get(target) instanceof AttributeSymbol);
        recordReference(declaration, syntax.attribute(), attribute.orElse(null), ReferenceUsage.EXPRESSION);
        if (attribute.isEmpty()) {
            errors.add(unresolved(syntax.attribute()));
            return failedValueExpression(id, syntax.range(), errors);
        }
        TypeId attributeType = index.byId(attribute.orElseThrow()).map(this::attributeType)
                .orElseGet(() -> ((AttributeSymbol) symbols.get(attribute.orElseThrow())).declaredType());
        TypeId type = nextTypeId();
        types.put(type, new guru.interlis.model.AttributePathType(type, new Origin.DeclaredOrigin(syntax.range()),
                Optional.of(new guru.interlis.model.AttributePathType.Viewable(viewable.orElseThrow())), List.of(attributeType)));
        expressions.put(id, new guru.interlis.model.SymbolValueExpression(id, type, guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), attribute.orElseThrow()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult parameterExpression(ExpressionId id, guru.interlis.syntax.ParameterExpressionSyntax syntax, SymbolId owner) {
        List<DiagnosticId> errors = new ArrayList<>();
        var target = bindTarget(index.byId(owner).orElseThrow(), syntax.parameter(), Set.of(SymbolKind.PARAMETER), ReferenceUsage.EXPRESSION, errors);
        if (target.isEmpty()) return failedValueExpression(id, syntax.range(), errors);
        var parameter = (guru.interlis.model.ParameterSymbol) buildSymbol(index.byId(target.orElseThrow()).orElseThrow());
        boolean error = effectiveDescriptor(parameter.typeId()) instanceof ErrorType;
        expressions.put(id, new guru.interlis.model.SymbolValueExpression(id, parameter.typeId(),
                error ? guru.interlis.model.ExpressionTypeState.ERROR : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), parameter.id()));
        return new ExpressionResult(id, parameter.typeId(), error);
    }

    private ExpressionResult failedValueExpression(ExpressionId id, SourceRange range, List<DiagnosticId> errors) {
        TypeId type = nextTypeId();
        types.put(type, new ErrorType(type, new Origin.DeclaredOrigin(range), errors));
        expressions.put(id, new guru.interlis.model.ErrorExpression(id, type,
                guru.interlis.model.ExpressionTypeState.ERROR, range, List.of(), errors));
        return new ExpressionResult(id, type, true);
    }

    private ExpressionResult enumerationConstantExpression(ExpressionId id,
                                                           EnumerationConstantExpressionSyntax syntax,
                                                           SymbolId owner) {
        String text = syntax.text().toLowerCase(java.util.Locale.ROOT);
        if (text.equals("#true") || text.equals("#false")) {
            TypeId type = builtinBoolean(index.byId(owner).orElseThrow().language());
            expressions.put(id, new guru.interlis.model.LiteralExpression(id, type,
                    guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                    guru.interlis.model.LiteralKind.BOOLEAN, syntax.text(), Optional.empty(),
                    Optional.empty()));
            return new ExpressionResult(id, type, false);
        }
        TypeId type = nextTypeId();
        types.put(type,new guru.interlis.model.EnumArgumentType(type,new Origin.DeclaredOrigin(syntax.range()),false));
        expressions.put(id, new guru.interlis.model.ConstantExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.text()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult builtinFunctionExpression(ExpressionId id,
                                                       BuiltinFunctionExpressionSyntax syntax,
                                                       SymbolId owner) {
        List<ExpressionResult> arguments = new ArrayList<>();
        for (var argument : syntax.arguments()) {
            checkWork();
            arguments.add(buildExpression(argument, owner));
        }
        var signature = BuiltinFunctions.find(syntax.functionName().segments().getLast().text()).orElse(null);
        var declaration = index.byId(owner).orElseThrow();
        boolean error = arguments.stream().anyMatch(ExpressionResult::error);
        boolean available=signature!=null && (signature.since()!=guru.interlis.model.LanguageVersion.ILI24 || declaration.language()==guru.interlis.model.LanguageVersion.ILI24);
        TypeId type;
        if (signature == null || signature.since() == guru.interlis.model.LanguageVersion.ILI24
                && declaration.language() != guru.interlis.model.LanguageVersion.ILI24) {
            DiagnosticId cause = emitRule(declaration, "Unknown standard function INTERLIS." + syntax.functionName().segments().getLast().text(),
                    "BUILTIN_NAME");
            type = nextTypeId();
            types.put(type, new ErrorType(type, new Origin.DeclaredOrigin(syntax.range()), List.of(cause)));
            error = true;

        } else {
            type = builtinResultType(signature.result(), declaration.language());
            if (arguments.size() != signature.arguments().size()) {
                emitTypeMismatch(ownerSource(owner), syntax.range(),
                        arguments.size() + " arguments", signature.arguments().size() + " arguments");
                error = true;
            }
            for (int i = 0; i < Math.min(arguments.size(), signature.arguments().size()); i++) {
                checkWork();
                var actual = arguments.get(i);
                var expected = signature.arguments().get(i);
                if (!actual.error() && expected!=BuiltinFunctions.Value.ATTRIBUTE
                        && !(i==1 && (signature.rule()==BuiltinFunctions.Rule.AREAS_ATTRIBUTE || signature.rule()==BuiltinFunctions.Rule.AREAS_TEXT)
                            && expressions.get(actual.id()) instanceof guru.interlis.model.UndefinedExpression)
                        && !builtinArgumentMatches(actual.typeId(), expected, declaration.language())) {
                    emitTypeMismatch(ownerSource(owner), syntax.arguments().get(i).range(),
                            typeName(actual.typeId()), expected.name());
                    error = true;
                }
            }
        }
        if(!error && (signature.name().equals("isEnumSubVal") || signature.name().equals("inEnumRange")))
            error=!enumArgumentsValid(arguments,signature,declaration,syntax.range());
        if (!error && syntax.functionName().segments().getLast().text().equals("myClass")) {
            TypeDescriptor argument = effectiveDescriptor(arguments.getFirst().typeId());
            List<SymbolId> targets = argument instanceof ReferenceType r ? r.targetRoots()
                    : argument instanceof guru.interlis.model.ObjectArgumentType o ? (o.restrictions().isEmpty() ? o.base().stream().toList() : o.restrictions())
                    : argument instanceof StructureValueType structure ? structure.targetRoots() : List.of();
            types.put(type, new ClassValueType(type, new Origin.DeclaredOrigin(syntax.range()),
                    argument instanceof StructureValueType || guru.interlis.model.internal.TypeCompatibility.isUndefined(argument), targets));
        }
        if (!error && signature.rule()==BuiltinFunctions.Rule.CONVERT_UNIT) {
            var from=effectiveDescriptor(arguments.getFirst().typeId());
            type=nextTypeId();
            types.put(type,new NumericType(type,new Origin.DeclaredOrigin(syntax.range()),new NumericRange(Optional.empty(),Optional.empty(),false,
                    guru.interlis.model.NumericDirection.NONE,from instanceof NumericType n?n.range().unit():Optional.empty(),Optional.empty())));
        }
        FunctionBindings.Result bound = FunctionBindings.Result.empty();
        if (!error) {
            bound = functions.bind(builtinDeclaration(signature,declaration.language()),arguments,declaration,syntax.range(),signature.rule());
            error = bound.error();
        }
        if(available)recordReference(declaration,syntax.functionName(),
                builtinDeclaration(signature,declaration.language()).id(),ReferenceUsage.EXPRESSION);
        expressions.put(id, new guru.interlis.model.FunctionCallExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), arguments.stream().map(ExpressionResult::id).toList(),
                "INTERLIS." + syntax.functionName().segments().getLast().text(), Optional.ofNullable(available?builtinDeclaration(signature,declaration.language()).id():null),bound.paths()));
        return new ExpressionResult(id, type, error);
    }

    private boolean enumArgumentsValid(List<ExpressionResult> arguments, BuiltinFunctions.Signature signature, DeclInfo owner, SourceRange range) {
        var anchor=arguments.stream().filter(a->!isEnumConstant(a)).map(a->effectiveDescriptor(a.typeId()))
                .filter(a->a instanceof EnumerationType || a instanceof guru.interlis.model.BooleanType || a instanceof guru.interlis.model.AlignmentType).findFirst().orElse(null);
        if(anchor==null)return true; // Polymorphic literal-only calls have no declared enum identity.
        for(int i=0;i<arguments.size();i++) {
            var argument=arguments.get(i);var actual=effectiveDescriptor(argument.typeId());
            boolean valid;
            if(isEnumConstant(argument)) {
                var path=((guru.interlis.model.ConstantExpression)expressions.get(argument.id())).constantName().substring(1);
                var values=enumerationPaths(anchor);
                valid=values.contains(path) && (signature.arguments().get(i)==BuiltinFunctions.Value.ENUM_TREE
                        || values.stream().noneMatch(v->v.startsWith(path+".")));
            } else valid=anchor instanceof EnumerationType a && actual instanceof EnumerationType b
                        && guru.interlis.model.internal.TypeCompatibility.relatedEnumerations(a,b,this::effectiveDescriptor)
                    || anchor instanceof guru.interlis.model.BooleanType && actual instanceof guru.interlis.model.BooleanType
                    || anchor instanceof guru.interlis.model.AlignmentType a && actual instanceof guru.interlis.model.AlignmentType b && a.alignment()==b.alignment();
            if(guru.interlis.model.internal.TypeCompatibility.isUndefined(actual))continue;
            if(!valid) {emitRule(owner,"enumeration function arguments must refer to compatible values of the same enumeration","ENUM_ARGUMENT",range);return false;}
            if(isEnumConstant(argument)) {
                TypeId context=anchor.id();
                if(anchor instanceof EnumerationType enumeration && !enumeration.treeValues()
                        && signature.arguments().get(i)==BuiltinFunctions.Value.ENUM_TREE) {
                    context=nextTypeId();types.put(context,new EnumerationType(context,enumeration.origin(),enumeration.values(),enumeration.ordered(),
                            enumeration.circular(),enumeration.finalSubtrees(),Optional.of(enumeration.id()),true));
                }
                arguments.set(i,contextualEnumConstant(argument,context,owner.id()));
            }

        }
        return true;
    }

    private List<String> enumerationPaths(TypeDescriptor descriptor) {
        if(descriptor instanceof EnumerationType enumeration)return enumeration.values().stream().map(EnumerationValue::path).toList();
        if(descriptor instanceof guru.interlis.model.BooleanType)return List.of("false","true");
        if(descriptor instanceof guru.interlis.model.AlignmentType alignment)return alignment.alignment()==guru.interlis.model.AlignmentKind.HORIZONTAL
                ?List.of("Left","Center","Right"):List.of("Top","Cap","Half","Base","Bottom");
        return List.of();
    }

    FunctionSymbol builtinDeclaration(BuiltinFunctions.Signature signature, guru.interlis.model.LanguageVersion language) {
        var id=new SymbolId("predefined:"+language+":function:INTERLIS."+signature.name());
        if(symbols.get(id) instanceof FunctionSymbol function)return function;
        var parameters=new ArrayList<guru.interlis.model.FunctionParameter>();
        var names=BuiltinFunctions.parameterNames(signature.name());
        for(int i=0;i<signature.arguments().size();i++) {
            TypeId parameter;
            if(signature.arguments().get(i)==BuiltinFunctions.Value.ATTRIBUTE) {
                parameter=nextTypeId();
                TypeId restriction=builtinResultType(i==1?BuiltinFunctions.Value.BAG:BuiltinFunctions.Value.SURFACE,language);
                types.put(parameter,new guru.interlis.model.AttributePathType(parameter,new Origin.PredefinedOrigin(language,"INTERLIS."+signature.name()),
                        Optional.of(new guru.interlis.model.AttributePathType.Argument(id,i-1)),List.of(restriction)));
            } else parameter=builtinResultType(signature.arguments().get(i),language);
            parameters.add(new guru.interlis.model.FunctionParameter(names.get(i),parameter,false));
        }
        var header=new SymbolHeader(id,SymbolKind.FUNCTION,DeclaredName.synthetic("INTERLIS."+signature.name()),Optional.empty(),0,
                new Origin.PredefinedOrigin(language,"INTERLIS."+signature.name()),Optional.empty(),language,Documentation.empty(),MetaAttributeSet.empty(),Set.of(),List.of());
        var function=new FunctionSymbol(header,parameters,builtinResultType(signature.result(),language),false,Documentation.empty());symbols.put(id,function);return function;
    }

    private TypeId builtinResultType(BuiltinFunctions.Value value,
                                    guru.interlis.model.LanguageVersion language) {
        if (value == BuiltinFunctions.Value.BOOLEAN) return builtinBoolean(language);
        TypeId id = nextTypeId();
        var origin = new Origin.PredefinedOrigin(language, value.name());
        TypeDescriptor descriptor = switch (value) {
            case NUMERIC -> new NumericType(id, origin, NumericRange.unbounded());
            case CLASS_VALUE -> new ClassValueType(id, origin, true, List.of());
            case POLYLINE, MULTIPOLYLINE, SURFACE, MULTISURFACE -> new GeometryType(id,origin,
                    guru.interlis.model.GeometryKind.valueOf(value.name()),List.of(),Optional.empty(),Optional.empty(),false,false,Optional.empty());
            case ENUM, ENUM_TREE -> new guru.interlis.model.EnumArgumentType(id,origin,value==BuiltinFunctions.Value.ENUM_TREE);
            case OBJECT -> new guru.interlis.model.ObjectArgumentType(id,origin,Optional.empty(),List.of(),false);
            case OBJECTS -> new guru.interlis.model.ObjectArgumentType(id, origin, Optional.empty(), List.of(), true);
            case ANYSTRUCTURE -> new StructureValueType(id,origin,Optional.empty(),List.of());
            case BAG -> new CollectionType(id,origin,guru.interlis.model.CollectionKind.BAG,Cardinality.unboundedFrom(0),
                    builtinResultType(BuiltinFunctions.Value.ANYSTRUCTURE,language));
            case TEXT, MTEXT -> new TextType(id, origin,
                    value == BuiltinFunctions.Value.TEXT ? TextKind.TEXT : TextKind.MTEXT,
                    java.util.OptionalInt.empty());
            default -> throw new IllegalArgumentException("unsupported builtin result " + value);
        };
        types.put(id, descriptor);
        return id;
    }

    private boolean builtinArgumentMatches(TypeId actual, BuiltinFunctions.Value expected, guru.interlis.model.LanguageVersion language) {
        return guru.interlis.model.internal.TypeCompatibility.assign(effectiveDescriptor(actual),
                effectiveDescriptor(builtinResultType(expected,language)),this::effectiveDescriptor,baseOf::get,
                domain->effectiveDescriptor(declaredTypes.get(domain)),guru.interlis.model.AssignmentContext.FUNCTION_ARGUMENT)
                .decision()==guru.interlis.model.RelationDecision.ALLOWED;
    }

    private ExpressionResult functionCallExpression(ExpressionId id,
                                                    FunctionCallExpressionSyntax syntax,
                                                    SymbolId owner) {
        var selected=resolveName(index.byId(owner).orElseThrow(),syntax.functionName(),Set.of(SymbolKind.FUNCTION),true);
        if(selected.target().map(symbols::get).filter(f->f instanceof FunctionSymbol && f.origin() instanceof Origin.PredefinedOrigin).isPresent())
            return builtinFunctionExpression(id,new BuiltinFunctionExpressionSyntax(syntax.id(),syntax.range(),syntax.functionName(),syntax.arguments()),owner);
        List<ExpressionResult> arguments = new ArrayList<>();
        for (var argument : syntax.arguments()) arguments.add(buildExpression(argument, owner));
        List<ExpressionId> children = arguments.stream().map(ExpressionResult::id).toList();
        List<DiagnosticId> bindingErrors = new ArrayList<>();
        Optional<SymbolId> target = bindTarget(index.byId(owner).orElseThrow(), syntax.functionName(),
                Set.of(SymbolKind.FUNCTION), ReferenceUsage.EXPRESSION, bindingErrors);
        FunctionSymbol function = target.flatMap(index::byId).map(this::buildSymbol)
                .filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).orElse(null);
        boolean error = arguments.stream().anyMatch(ExpressionResult::error);
        TypeId type;
        if (function == null) {
            checkWork();
            type = nextTypeId();
            types.put(type, new ErrorType(type, new Origin.DeclaredOrigin(syntax.range()),
                    bindingErrors));
            error = true;
        } else {
            type = function.returnType();
            if (arguments.size() != function.parameters().size()) {
                emitTypeMismatch(ownerSource(owner), syntax.range(),
                        arguments.size() + " arguments", function.parameters().size() + " arguments");
                error = true;
            }
            for (int i = 0; i < Math.min(arguments.size(), function.parameters().size()); i++) {
                checkWork();
                var actual = arguments.get(i);
                TypeId expected = function.parameters().get(i).typeId();
                actual=contextualValue(actual,expected,owner);arguments.set(i,actual);
                if (!actual.error() && !(effectiveDescriptor(expected) instanceof guru.interlis.model.AttributePathType) && guru.interlis.model.internal.TypeCompatibility.assign(
                        effectiveDescriptor(actual.typeId()), effectiveDescriptor(expected),
                        this::effectiveDescriptor, baseOf::get, domain -> effectiveDescriptor(declaredTypes.get(domain)),guru.interlis.model.AssignmentContext.FUNCTION_ARGUMENT).decision()
                        != guru.interlis.model.RelationDecision.ALLOWED) {
                    emitTypeMismatch(ownerSource(owner), syntax.arguments().get(i).range(),
                            typeName(actual.typeId()), typeName(expected));
                    error = true;
                }
            }
        }
        if (!error) error = functions.bind(function,arguments,index.byId(owner).orElseThrow(),syntax.range(),BuiltinFunctions.Rule.SIMPLE).error();
        expressions.put(id, new guru.interlis.model.FunctionCallExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), children, syntax.functionName().text(), target,Map.of()));
        return new ExpressionResult(id, type, error);
    }

    private ExpressionResult literalExpression(ExpressionId id, LiteralExpressionSyntax syntax, SymbolId owner) {
        TypeId type;
        TypedExpression expression;
        if (syntax.literalKind() == guru.interlis.model.LiteralKind.TEXT) {
            String value = unquote(syntax.lexeme());
            type = nextTypeId();
            types.put(type, new TextType(type, new Origin.DeclaredOrigin(syntax.range()),
                    value.indexOf('\r')>=0 || value.indexOf('\n')>=0 || value.indexOf('\t')>=0 ? TextKind.MTEXT : TextKind.TEXT,
                    java.util.OptionalInt.of(TextLengths.canonicalLength(value))));
            expression = new guru.interlis.model.LiteralExpression(id, type,
                    guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                    syntax.literalKind(), syntax.lexeme(), Optional.empty(),
                    Optional.of(value));
        } else {
            type = numericLiteralType(syntax.decimal(), syntax.unit(), syntax.range(), owner);
            expression = new guru.interlis.model.LiteralExpression(id, type,
                    effectiveDescriptor(type) instanceof ErrorType ? guru.interlis.model.ExpressionTypeState.ERROR : guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                    syntax.literalKind(), syntax.lexeme(), syntax.decimal(), Optional.empty());
        }
        expressions.put(id, expression);
        return new ExpressionResult(id, type, effectiveDescriptor(type) instanceof ErrorType);
    }

    private TypeId numericLiteralType(Optional<DecimalLiteral> decimal, Optional<guru.interlis.syntax.NameSyntax> unitName, SourceRange sourceRange, SymbolId owner) {
        TypeId type = nextTypeId();
        Optional<SymbolId> unit = unitName.flatMap(name -> bindUnit(index.byId(owner).orElseThrow(), name));
        if (unitName.isPresent() && unit.isEmpty()) {
            var diagnostic = emitRule(index.byId(owner).orElseThrow(), "Numeric constant requires a known unit", "CONSTANT_UNIT", unitName.orElseThrow().range());
            types.put(type, new ErrorType(type, new Origin.DeclaredOrigin(sourceRange), List.of(diagnostic)));
            return type;
        }
        if (unit.isPresent() && extensionUnit(unit.orElseThrow()).abstractUnit()) {
            var diagnostic = emitRule(index.byId(owner).orElseThrow(), "Numeric constant requires a concrete unit", "CONSTANT_UNIT", unitName.orElseThrow().range());
            types.put(type, new ErrorType(type, new Origin.DeclaredOrigin(sourceRange), List.of(diagnostic)));
            return type;
        }
        NumericRange range = new NumericRange(decimal, decimal, false,
                guru.interlis.model.NumericDirection.NONE, unit, Optional.empty());
        types.put(type, new NumericType(type, new Origin.DeclaredOrigin(sourceRange), range));
        return type;
    }

    private ExpressionResult pathExpression(ExpressionId id, PathExpressionSyntax syntax,
                                            SymbolId owner) {
        return pathExpression(id, syntax, owner, expressionContext(owner), false);
    }

    private ExpressionResult pathExpression(ExpressionId id, PathExpressionSyntax syntax,
            SymbolId owner, SymbolId start, boolean localPrefix) {
        return pathExpression(id, syntax, owner, start, localPrefix, PathContext.VALUE);
    }

    guru.interlis.syntax.FragmentSnapshot parsePathLiteral(SourceRange range, guru.interlis.model.LanguageVersion language) {
        var fragment = new guru.interlis.compiler.internal.antlr.AntlrSyntaxService().parsePathLiteral(
                sourceSet.sources().get(range.source()), range, language, cancellation);
        collector.importAll(fragment.diagnostics());
        return fragment;
    }

    ExpressionResult valuePath(PathExpressionSyntax syntax, SymbolId owner, SymbolId start) {
        return pathExpression(new ExpressionId("expr-" + (++expressionCounter)), syntax, owner, start, false, PathContext.FUNCTION_PATH);
    }

    ExpressionResult inspectionPath(PathExpressionSyntax syntax, SymbolId owner, SymbolId start) {
        return pathExpression(new ExpressionId("expr-" + (++expressionCounter)), syntax, owner, start, false, PathContext.INSPECTION);
    }

    private enum PathContext { VALUE, INSPECTION, FUNCTION_PATH }

    private ExpressionResult pathExpression(ExpressionId id, PathExpressionSyntax syntax,
            SymbolId owner, SymbolId start, boolean localPrefix, PathContext pathContext) {
        boolean inspection=pathContext==PathContext.INSPECTION;
        List<guru.interlis.model.PathStep> steps = new ArrayList<>();
        SymbolId currentScope = start;
        SymbolId contextTopic = scopeRoot(graphics.context(owner));
        currentScope = contextualObject(currentScope, contextTopic);
        TypeId currentType = null;
        boolean error = false;
        for (int i = 0; i < syntax.segments().size(); i++) {
            checkWork();
            var segment = syntax.segments().get(i);
            guru.interlis.syntax.NameSyntax name = segment.name();
            String text = name.text();
            if (Set.of("PARENT", "THISAREA", "THATAREA").contains(text)) {
                var inspected = views.inspection(expressionContext(owner));
                boolean valid = inspected != null && (text.equals("PARENT") != inspected.area())
                        && segment.index().isEmpty() && !segment.associationAccess() && segment.associationQualifier().isEmpty()
                        && (i == 0 || java.util.Objects.equals(currentScope, inspected.element())
                            || java.util.Objects.equals(currentScope, expressionContext(owner)));
                if (!valid) {
                    var diagnostic = emitRule(index.byId(owner).orElseThrow(), "invalid inspection path context", "VIEW_INSPECTION_CONTEXT");
                    currentType = nextTypeId();
                    types.put(currentType, new ErrorType(currentType, new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                    steps.add(new guru.interlis.model.PathStep.UnresolvedStep(text, name.range()));
                    error = true; break;
                }
                currentScope = inspected.parent(); currentType = objectPathType(currentScope, name.range());
                steps.add(new guru.interlis.model.PathStep.InspectionStep(guru.interlis.model.PathStep.InspectionKind.valueOf(text),
                        expressionContext(owner), currentScope, name.range()));
                continue;
            }
            if ("THIS".equals(text) && segment.index().isEmpty() && !segment.associationAccess() && (currentScope != null || currentType != null
                    && effectiveDescriptor(currentType) instanceof guru.interlis.model.ObjectArgumentType)) {
                steps.add(new guru.interlis.model.PathStep.ThisStep(name.range()));
                currentType = i == 0 ? declaredTypes.get(currentScope) : currentType;
                if (currentType == null) {
                    currentType = objectPathType(currentScope, name.range());
                }
                TypeDescriptor descriptor = effectiveDescriptor(currentType);
                if (!(descriptor instanceof ReferenceType)
                        && !(descriptor instanceof StructureValueType)
                        && !(descriptor instanceof guru.interlis.model.ObjectArgumentType)) {
                    currentScope = null;
                }
                continue;
            }
            boolean incomingMany = currentType != null && effectiveDescriptor(currentType)
                    instanceof guru.interlis.model.ObjectArgumentType set && set.objects();
            Optional<SymbolId> member = currentScope == null || segment.associationAccess() || segment.associationQualifier().isPresent()
                    ? Optional.empty() : findMember(currentScope, text);
            // Projections retain role access to their source object, including
            // roles which ALL OF does not copy as transfer attributes.
            if (i == 0 && member.isEmpty()) {
                var projection = views.projectionBase(currentScope);
                if (projection != null) {
                    currentScope = projection.target();
                    steps.add(new guru.interlis.model.PathStep.ViewBaseStep(projection.id(), currentScope, name.range()));
                    if (!segment.associationAccess() && segment.associationQualifier().isEmpty())
                        member = findMember(currentScope, text).filter(candidate ->
                                index.byId(candidate).map(d -> d.kind() == SymbolKind.ROLE).orElse(false));
                }
            }
            List<SymbolId> objectScopes = currentScope == null ? List.of() : List.of(currentScope);
            if (currentType != null && effectiveDescriptor(currentType) instanceof guru.interlis.model.ObjectArgumentType objects) {
                if (!objects.restrictions().isEmpty()) objectScopes = objects.restrictions();
                else if (objects.base().isPresent()) objectScopes = List.of(objects.base().orElseThrow());
            }
            if (!localPrefix && segment.index().isEmpty() && member.isEmpty() && !objectScopes.isEmpty()) {
                boolean outgoing = segment.associationQualifier().isPresent();
                List<RoleAccess> accesses = associationAccesses(objectScopes, contextTopic, name, i == 0, outgoing);
                if (!outgoing && !segment.associationAccess() && accesses.isEmpty()) {
                    accesses = associationAccesses(objectScopes, contextTopic, name, i == 0, true);
                }
                if (segment.associationQualifier().isPresent()) {
                    var qualifier = segment.associationQualifier().orElseThrow();
                    var target = resolver.resolve(owner, qualifier, Set.of(SymbolKind.ASSOCIATION)).target();
                    if (target.isEmpty()) target = baseTopicMember(owner, qualifier.text(), Set.of(SymbolKind.ASSOCIATION));
                    recordReference(index.byId(owner).orElseThrow(), qualifier, target.orElse(null), ReferenceUsage.EXPRESSION);
                    if (target.isEmpty()) unresolved(qualifier);
                    SymbolId selected = target.orElse(null);
                    accesses = accesses.stream().filter(a -> a.association().equals(selected)).toList();
                }
                if (accesses.size() == 1) {
                    RoleAccess access = accesses.getFirst();
                    boolean associationObject = segment.associationAccess() || access.target().isEmpty();
                    currentScope = associationObject ? access.association()
                            : commonRoleScope(access.target().orElseThrow(), contextTopic);
                    Cardinality cardinality = accessCardinality(access, associationObject);
                    boolean many = incomingMany || isMany(cardinality);
                    currentType = nextTypeId();
                    types.put(currentType, associationObject
                            ? new guru.interlis.model.ObjectArgumentType(currentType, new Origin.DeclaredOrigin(segment.range()),
                                    Optional.of(currentScope), List.of(), many)
                            : rolePathType(currentType, access.target().orElseThrow(), contextTopic, segment.range(), many));
                    if (access.target().isEmpty()) {
                        steps.add(new guru.interlis.model.PathStep.AssociationSourceStep(text, access.association(),
                                access.source().id(), cardinality, currentType, segment.range()));
                    } else {
                        steps.add(new guru.interlis.model.PathStep.AssociationAccessStep(text,
                                access.association(), access.source().id(), access.target().orElseThrow().id(),
                                associationObject, cardinality, currentType, segment.range()));
                    }
                    recordReference(index.byId(owner).orElseThrow(), name,
                            access.target().orElse(access.source()).id(), ReferenceUsage.EXPRESSION);
                    continue;
                } else if (accesses.size() > 1) {
                    var diagnostic = collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0002"),
                                    Severity.ERROR, Phase.BINDING, "diagnostic.semantics.ambiguous")
                            .at(name.range()).argument(new DiagnosticArgument.IdentifierArgument("name", text))
                            .argument(new DiagnosticArgument.IdentifierListArgument("candidates",
                                    accesses.stream().map(a -> a.association().value() + ":" + a.source().id().value()).toList())));
                    currentType = nextTypeId();
                    types.put(currentType, new ErrorType(currentType, new Origin.DeclaredOrigin(segment.range()), List.of(diagnostic)));
                    steps.add(new guru.interlis.model.PathStep.UnresolvedStep(text, segment.range()));
                    error = true;
                    break;
                }
            }
            if (member.isEmpty()) {
                DiagnosticId diagnostic = unresolved(name);
                steps.add(new guru.interlis.model.PathStep.UnresolvedStep(text, name.range()));
                TypeId errorType = nextTypeId();
                types.put(errorType, new ErrorType(errorType,
                        new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                currentType = errorType;
                error = true;
                break;
            }
            var referenceTarget = symbols.get(member.orElseThrow());
            recordReference(index.byId(owner).orElseThrow(), name,
                    referenceTarget instanceof ViewSymbol.Base basis && basis.nameRange().isEmpty() ? basis.target() : member.orElseThrow(),
                    ReferenceUsage.EXPRESSION);
            SymbolKind kind = index.byId(member.get()).map(DeclInfo::kind).orElse(SymbolKind.ATTRIBUTE);
            if ((localPrefix || segment.index().isPresent()) && kind != SymbolKind.ATTRIBUTE) {
                DiagnosticId diagnostic = emitTypeMismatch(ownerSource(owner), name.range(), "role", "structure attribute");
                currentType = nextTypeId();
                types.put(currentType, new ErrorType(currentType, new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                error = true;
                break;
            }
            if (kind == SymbolKind.VIEW_BASE) {
                ViewSymbol.Base base = (ViewSymbol.Base) buildSymbol(index.byId(member.get()).orElseThrow());
                steps.add(new guru.interlis.model.PathStep.ViewBaseStep(base.id(), views.basisTarget(base), name.range()));
                currentScope = views.basisTarget(base); currentType = objectPathType(currentScope, name.range());
            } else if (kind == SymbolKind.ROLE) {
                steps.add(new guru.interlis.model.PathStep.RoleStep(text, member.get(),
                        name.range()));
                // A role declared on this association selects one participant of
                // the association instance. Its relationship cardinality does
                // not turn that participant into an object set.
                RoleSymbol role = (RoleSymbol) buildSymbol(index.byId(member.get()).orElseThrow());
                currentScope = commonRoleScope(role, contextTopic);
                currentType = nextTypeId();
                types.put(currentType, rolePathType(currentType, role, contextTopic, name.range(), incomingMany));
            } else {
                steps.add(new guru.interlis.model.PathStep.AttributeStep(text, member.get(),
                        name.range()));
                currentType = declaredTypes.get(member.get());
                if (currentType == null) currentType = attributeType(index.byId(member.get()).orElseThrow());
                TypeDescriptor descriptor = currentType == null ? null
                        : effectiveDescriptor(currentType);
                currentScope = null;
                if (segment.index().isPresent()) {
                    var selection = segment.index().orElseThrow();
                    TypeId selected = incomingMany ? null : indexedType(descriptor, selection, segment.range());
                    if (selected == null) {
                        DiagnosticId diagnostic = emitTypeMismatch(ownerSource(owner), segment.range(),
                                typeName(currentType), "valid LIST position or coordinate axis");
                        currentType = nextTypeId();
                        types.put(currentType, new ErrorType(currentType, new Origin.DeclaredOrigin(segment.range()), List.of(diagnostic)));
                        error = true;
                        break;
                    }
                    currentType = selected;
                    descriptor = effectiveDescriptor(currentType);
                    steps.add(new guru.interlis.model.PathStep.IndexStep(selection, currentType, segment.range()));
                }
                if (localPrefix) {
                    currentScope = structures.target(descriptor);
                    if (currentScope == null) {
                        DiagnosticId diagnostic = emitTypeMismatch(ownerSource(owner), name.range(),
                                typeName(currentType), "structure attribute");
                        currentType = nextTypeId();
                        types.put(currentType, new ErrorType(currentType, new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                        error = true;
                        break;
                    }
                } else if (descriptor instanceof CollectionType collection && !incomingMany
                        && (pathContext!=PathContext.VALUE || !isMany(collection.cardinality())) && i + 1 < syntax.segments().size()) {
                    currentScope = structures.target(collection);
                } else if (descriptor instanceof ReferenceType reference) {
                    List<SymbolId> roots = reference.targetRoots().stream().map(t -> contextualObject(t, contextTopic)).toList();
                    currentScope = commonObjectScope(roots);
                    currentType = nextTypeId();
                    if (incomingMany) {
                        types.put(currentType, new guru.interlis.model.ObjectArgumentType(currentType,
                                new Origin.DeclaredOrigin(name.range()), roots.size() == 1 ? Optional.of(roots.getFirst()) : Optional.empty(),
                                roots.size() <= 1 ? List.of() : roots, true));
                    } else {
                        types.put(currentType, new ReferenceType(currentType, new Origin.DeclaredOrigin(name.range()),
                                reference.target().map(t -> contextualObject(t, contextTopic)),
                                reference.restrictions().stream().map(t -> contextualObject(t, contextTopic)).toList(), reference.external()));
                    }
                } else if (inspection && descriptor instanceof GeometryType geometry && i + 1 < syntax.segments().size()) {
                    currentScope = views.geometryElement(index.byId(owner).orElseThrow(), geometry, false);
                } else if (!incomingMany && descriptor instanceof StructureValueType structure) {
                    currentScope = structures.target(structure);
                } else if (incomingMany) {
                    DiagnosticId diagnostic = emitTypeMismatch(ownerSource(owner), name.range(),
                            "multiple attribute values", "single attribute value");
                    currentType = nextTypeId();
                    types.put(currentType, new ErrorType(currentType, new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                    error = true;
                    break;
                }
            }
        }
        if (currentType == null) {
            currentType = errorType(ownerSource(owner), syntax.range(), "PATH");
            error = true;
        }
        guru.interlis.model.ObjectPath path = new guru.interlis.model.ObjectPath(steps.isEmpty()
                ? List.of(new guru.interlis.model.PathStep.ThisStep(syntax.range())) : steps);
        TypedExpression expression = new guru.interlis.model.PathExpression(id, currentType,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(), path);
        expressions.put(id, expression);
        return new ExpressionResult(id, currentType, error);
    }

    private TypeId indexedType(TypeDescriptor type, guru.interlis.model.PathIndex index,
            guru.interlis.source.SourceRange range) {
        var position = index.position();
        if (position.filter(p -> p.signum() <= 0).isPresent()) return null;
        if (type instanceof CollectionType collection && collection.collectionKind() == guru.interlis.model.CollectionKind.LIST) {
            if (collection.cardinality().upper() instanceof guru.interlis.model.CardinalityUpper.Finite maximum
                    && (maximum.value().signum() == 0 || position.filter(p -> p.compareTo(maximum.value()) > 0).isPresent()))
                return null;
            return collection.elementType();
        }
        if (type instanceof CoordinateType coordinate && !coordinate.multiCoordinate() && position.isPresent()
                && position.orElseThrow().compareTo(java.math.BigInteger.valueOf(coordinate.dimensions())) <= 0) {
            TypeId result = nextTypeId();
            types.put(result, new NumericType(result, new Origin.DeclaredOrigin(range),
                    coordinate.axes().get(position.orElseThrow().intValueExact() - 1)));
            return result;
        }
        return null;
    }

    private boolean isMany(Cardinality cardinality) {
        return !(cardinality.upper() instanceof guru.interlis.model.CardinalityUpper.Finite finite)
                || finite.value().compareTo(java.math.BigInteger.ONE) > 0;
    }

    private Cardinality accessCardinality(RoleAccess access, boolean associationObject) {
        var roles = associationRoles(access.association());
        // A role bound only for one participant does not constrain the remaining
        // combinations in an n-ary relation. Never infer a singleton from it.
        if (roles.size() != 2) return Cardinality.unboundedFrom(0);
        RoleSymbol target = access.target().orElseGet(() -> roles.stream()
                .filter(r -> !r.id().equals(access.source().id())).findFirst().orElseThrow());
        Cardinality cardinality = target.cardinality();
        if (associationObject) {
            Cardinality links = Cardinality.exactly(1);
            for (SymbolId base : baseChain(access.association())) {
                var syntax = (AssociationDeclarationSyntax) index.byId(base).orElseThrow().syntax();
                if (syntax.cardinality().isPresent()) { links = syntax.cardinality().orElseThrow(); break; }
            }
            cardinality = multiplyCardinality(cardinality, links);
        }
        return cardinality;
    }

    private Cardinality multiplyCardinality(Cardinality left, Cardinality right) {
        var upper = left.upper() instanceof guru.interlis.model.CardinalityUpper.Finite l
                && right.upper() instanceof guru.interlis.model.CardinalityUpper.Finite r
                ? new guru.interlis.model.CardinalityUpper.Finite(l.value().multiply(r.value()))
                : guru.interlis.model.CardinalityUpper.unbounded();
        return new Cardinality(left.lower().multiply(right.lower()), upper);
    }

    private record RoleAccess(SymbolId association, RoleSymbol source, Optional<RoleSymbol> target) { }

    private List<RoleAccess> associationAccesses(List<SymbolId> fromTargets, SymbolId contextTopic,
            guru.interlis.syntax.NameSyntax name, boolean first, boolean outgoing) {
        List<RoleAccess> result = new ArrayList<>();
        for (DeclInfo association : index.all()) {
            checkWork();
            if (association.kind() != SymbolKind.ASSOCIATION) continue;
            SymbolId topic = scopeRoot(association.id());
            if (!topicAvailable(contextTopic, topic)) continue;
            // A declaration appearing later cannot retroactively add an access to a constraint.
            if (association.sourceId().equals(name.range().source())
                    && association.syntax().range().startOffset() > name.range().startOffset()) continue;
            List<RoleSymbol> roles = associationRoles(association.id());
            for (RoleSymbol target : roles) {
                checkWork();
                if (!outgoing && !target.name().value().equals(name.text())) continue;
                for (RoleSymbol source : roles) {
                    checkWork();
                    if (outgoing ? !source.id().equals(target.id()) : source.id().equals(target.id())) continue;
                    if (outgoing && !source.name().value().equals(name.text())) continue;
                    if (!fromTargets.stream().allMatch(from -> roleAccepts(source, from))) continue;
                    if (first) {
                        if (source.external()) continue;
                        if (!fromTargets.stream().allMatch(from -> {
                            boolean originalAccess = source.targetRoots().stream()
                                    .anyMatch(t -> targetSpecializes(from, t) && Objects.equals(scopeRoot(source.id()), scopeRoot(t)));
                            boolean extendedHere = Objects.equals(topic, scopeRoot(from))
                                    && index.byId(from).map(d -> d.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)).orElse(false);
                            return originalAccess || extendedHere;
                        })) continue;
                    }
                    result.add(new RoleAccess(association.id(), source, outgoing ? Optional.empty() : Optional.of(target)));
                }
            }
        }
        List<RoleAccess> candidates = List.copyOf(result);
        result.removeIf(candidate -> candidates.stream().anyMatch(other -> {
            if (candidate == other) return false;
            boolean derived = baseChain(other.association()).contains(candidate.association());
            boolean base = baseChain(candidate.association()).contains(other.association());
            boolean localExtension = index.byId(other.association()).orElseThrow().modifiers()
                    .contains(guru.interlis.model.Modifier.EXTENDED);
            boolean candidateExtension = index.byId(candidate.association()).orElseThrow().modifiers()
                    .contains(guru.interlis.model.Modifier.EXTENDED);
            return derived && (other.source().hiding() || localExtension)
                    || base && !candidate.source().hiding() && !candidateExtension;
        }));
        return result;
    }

    private boolean topicAvailable(SymbolId context, SymbolId candidate) {
        Set<SymbolId> seen = new HashSet<>();
        for (SymbolId current = context; current != null && seen.add(current);) {
            checkWork();
            if (current.equals(candidate)) return true;
            DeclInfo declaration = index.byId(current).orElse(null);
            if (declaration == null || !(declaration.syntax() instanceof TopicSyntax topic)
                    || topic.baseTopic().isEmpty()) return false;
            current = resolver.resolve(current, topic.baseTopic().orElseThrow(), Set.of(SymbolKind.TOPIC))
                    .target().orElse(null);
        }
        return false;
    }

    private SymbolId contextualObject(SymbolId target, SymbolId topic) {
        if (target == null) return null;
        Set<SymbolId> seen = new HashSet<>();
        for (SymbolId context = topic; context != null && seen.add(context); context = baseOf.get(context)) {
            for (DeclInfo candidate : index.children(context)) {
                checkWork();
                if ((candidate.kind() == SymbolKind.CLASS || candidate.kind() == SymbolKind.ASSOCIATION)
                        && candidate.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)
                        && guru.interlis.model.internal.TypeCompatibility.specializes(candidate.id(), target, baseOf::get)) {
                    return candidate.id();
                }
            }
        }
        return target;
    }

    private SymbolId commonRoleScope(RoleSymbol role, SymbolId topic) {
        List<SymbolId> roots = role.targetRoots().stream().map(t -> contextualObject(t, topic)).toList();
        return commonObjectScope(roots);
    }

    SymbolId commonObjectScope(List<SymbolId> roots) {
        if (roots.isEmpty()) return null;
        // Only a common hierarchy admits a subsequent statically bound member.
        for (SymbolId candidate : baseChain(roots.getFirst())) {
            if (roots.stream().allMatch(t -> targetSpecializes(t, candidate))) return candidate;
        }
        return null;
    }

    private guru.interlis.model.ObjectArgumentType rolePathType(TypeId id, RoleSymbol role,
            SymbolId topic, SourceRange range, boolean many) {
        List<SymbolId> roots = role.targetRoots().stream().map(t -> contextualObject(t, topic)).toList();
        boolean any = roots.stream().anyMatch(t -> objectBase(t).isEmpty());
        return new guru.interlis.model.ObjectArgumentType(id, new Origin.DeclaredOrigin(range),
                !any && roots.size() == 1 ? Optional.of(roots.getFirst()) : Optional.empty(),
                any || roots.size() == 1 ? List.of() : roots, many);
    }

    TypeId objectPathType(SymbolId target, SourceRange range) {
        TypeId type = nextTypeId();
        if (objectBase(target).isEmpty()) {
            types.put(type, new guru.interlis.model.ObjectArgumentType(type, new Origin.DeclaredOrigin(range),
                    Optional.empty(), List.of(), false));
        } else if (index.byId(target).map(DeclInfo::kind).orElse(null) == SymbolKind.STRUCTURE) {
            types.put(type, new StructureValueType(type, new Origin.DeclaredOrigin(range), Optional.of(target), List.of()));
        } else {
            types.put(type, new ReferenceType(type, new Origin.DeclaredOrigin(range), Optional.of(target), List.of(), false));
        }
        return type;
    }

    Optional<SymbolId> findMember(SymbolId owner, String name) {
        SymbolId current = owner;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            checkWork();
            for (DeclInfo child : index.children(current)) {
                checkWork();
                if ((child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE || child.kind() == SymbolKind.VIEW_BASE)
                        && child.name().equals(name)) {
                    return Optional.of(child.id());
                }
            }
            Symbol predefinedOwner = symbols.get(current);
            if (predefinedOwner != null && predefinedOwner.origin() instanceof Origin.PredefinedOrigin) {
                var members = predefinedOwner instanceof StructureSymbol s ? s.members() : predefinedOwner instanceof ClassSymbol c ? c.members() : List.<SymbolId>of();
                for (SymbolId member : members) if (symbols.get(member).kind() == SymbolKind.ATTRIBUTE && symbols.get(member).name().value().equals(name)) return Optional.of(member);
            }
            var viewMember = views.member(current, name);
            if (viewMember.isPresent()) return viewMember;
            current = objectInheritanceBase(current).orElse(null);
        }
        return Optional.empty();
    }

    private ExpressionResult binaryExpression(ExpressionId id, BinaryExpressionSyntax syntax,
                                              SymbolId owner) {
        ExpressionResult left = buildExpression(syntax.left(), owner);
        ExpressionResult right = buildExpression(syntax.right(), owner);
        TypeId result = builtinBoolean(index.byId(owner).orElseThrow().language());
        boolean error = left.error() || right.error();
        switch (syntax.operator()) {
            case AND, OR, IMPLIES -> {
                if (!isBoolean(left.typeId()) || !isBoolean(right.typeId())) {
                    emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeName(left.typeId()) + "," + typeName(right.typeId()), "BOOLEAN");
                    error = true;
                }
            }
            case EQ, NEQ, LT, LTE, GT, GTE -> {
                var comparison=syntax.operator()==guru.interlis.model.BinaryOperator.EQ || syntax.operator()==guru.interlis.model.BinaryOperator.NEQ
                        ?guru.interlis.model.ComparisonOperator.EQUALITY:guru.interlis.model.ComparisonOperator.ORDERING;
                left=contextualNumericConstant(left,right.typeId());
                right=contextualNumericConstant(right,left.typeId());
                left=contextualValue(left,right.typeId(),owner);
                right=contextualValue(right,left.typeId(),owner);
                error |= left.error() || right.error();
                if(!comparisonAllowed(left,right,comparison)) {
                    emitTypeMismatch(ownerSource(owner),syntax.range(),typeName(left.typeId())+","+typeName(right.typeId()),"COMPARABLE "+comparison);
                    error=true;
                }
            }
            case PLUS, MINUS, MUL, DIV, POW -> {
                result = left.typeId();
                if (!isNumeric(left.typeId()) || !isNumeric(right.typeId())) {
                    emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeName(left.typeId()) + "," + typeName(right.typeId()), "NUMERIC");
                    error = true;
                }
            }
        }
        TypedExpression expression = new guru.interlis.model.BinaryExpression(id, result,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(left.id(), right.id()), syntax.operator(),
                left.id(), right.id());
        expressions.put(id, expression);
        return new ExpressionResult(id, result, error);
    }

    private ExpressionResult unaryExpression(ExpressionId id, UnaryExpressionSyntax syntax,
                                             SymbolId owner) {
        ExpressionResult operand = buildExpression(syntax.operand(), owner);
        TypeId type = builtinBoolean(index.byId(owner).orElseThrow().language());
        boolean error = operand.error();
        if (syntax.operator() == guru.interlis.model.UnaryOperator.NOT && !isBoolean(operand.typeId())) {
            emitTypeMismatch(ownerSource(owner), syntax.range(), typeName(operand.typeId()),
                    "BOOLEAN");
            error = true;
        }
        TypedExpression expression = new guru.interlis.model.UnaryExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(operand.id()), syntax.operator(), operand.id());
        expressions.put(id, expression);
        return new ExpressionResult(id, type, error);
    }

    private ExpressionResult definedExpression(ExpressionId id, DefinedExpressionSyntax syntax,
                                               SymbolId owner) {
        ExpressionResult operand = buildExpression(syntax.operand(), owner);
        TypeId type = builtinBoolean(index.byId(owner).orElseThrow().language());
        TypedExpression expression = new guru.interlis.model.DefinedExpression(id, type,
                operand.error() ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(operand.id()), operand.id());
        expressions.put(id, expression);
        return new ExpressionResult(id, type, operand.error());
    }

    private ExpressionResult constantExpression(ExpressionId id, ConstantExpressionSyntax syntax, SymbolId owner) {
        TypeId type = numericLiteralType(Optional.empty(), syntax.unit(), syntax.range(), owner);
        TypedExpression expression = new guru.interlis.model.ConstantExpression(id, type,
                effectiveDescriptor(type) instanceof ErrorType ? guru.interlis.model.ExpressionTypeState.ERROR : guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.constantName());
        expressions.put(id, expression);
        return new ExpressionResult(id, type, effectiveDescriptor(type) instanceof ErrorType);
    }

    private ExpressionResult undefinedExpression(ExpressionId id, UndefinedExpressionSyntax syntax, SymbolId owner) {
        TypeId type = nextTypeId();
        types.put(type, new guru.interlis.model.GenericType(type,
                new Origin.PredefinedOrigin(index.byId(owner).orElseThrow().language(), "UNDEFINED"),
                "UNDEFINED"));
        TypedExpression expression = new guru.interlis.model.UndefinedExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of());
        expressions.put(id, expression);
        return new ExpressionResult(id, type, false);
    }

    /** A missing parsed condition is a source recovery error, never an implementation gap. */
    private ExpressionResult missingConstraintExpression(DeclInfo owner,SourceRange range) {
        var causes=syntaxErrors.stream().filter(d->d.primaryRange().filter(r->r.source().equals(range.source())
                && r.revision().equals(range.revision()) && r.startOffset()<=range.endOffset() && r.endOffset()>=range.startOffset()).isPresent())
                .map(guru.interlis.diagnostics.Diagnostic::id).toList();
        if(causes.isEmpty())causes=List.of(emitRule(owner,"constraint syntax is incomplete","SOURCE_MISSING_CONSTRAINT",range));
        return failedValueExpression(new ExpressionId("expr-"+(++expressionCounter)),range,causes);
    }

    private ExpressionResult errorExpression(SourceId source, SourceRange range, String feature) {
        ExpressionId id = new ExpressionId("expr-" + (++expressionCounter));
        TypeId type = errorType(source, range, feature);
        DiagnosticId diagnostic = emitUnsupported(source, range, feature, feature);
        TypedExpression expression = new ErrorExpression(id, type,
                guru.interlis.model.ExpressionTypeState.ERROR, range, List.of(),
                List.of(diagnostic));
        expressions.put(id, expression);
        return new ExpressionResult(id, type, true);
    }

    private boolean formattedTextValid(ExpressionResult left, ExpressionResult right) {
        TypeDescriptor leftDescriptor = effectiveDescriptor(left.typeId());
        TypeDescriptor rightDescriptor = effectiveDescriptor(right.typeId());
        guru.interlis.model.FormattedType formatted;
        ExpressionId textId;
        if (leftDescriptor instanceof guru.interlis.model.FormattedType f) {
            formatted = f;
            textId = right.id();
        } else if (rightDescriptor instanceof guru.interlis.model.FormattedType f) {
            formatted = f;
            textId = left.id();
        } else {
            return true;
        }
        TypedExpression expression = expressions.get(textId);
        if (!(expression instanceof guru.interlis.model.LiteralExpression literal)
                || literal.textValue().isEmpty()) {
            return true;
        }
        String value = literal.textValue().orElseThrow();
        return formattedValues().parse(formatted, value, true).isPresent();
    }

    private boolean isEnumTreeType(TypeId id) {
        return effectiveDescriptor(id) instanceof EnumerationType enumeration && enumeration.treeValues();
    }

    private boolean isEnumConstant(ExpressionResult result) {
        TypedExpression expression = expressions.get(result.id());
        return expression instanceof guru.interlis.model.ConstantExpression constant
                && constant.constantName().startsWith("#");
    }

    private boolean enumConstantValid(ExpressionResult left, ExpressionResult right) {
        ExpressionResult enumSide = null;
        ExpressionResult constSide = null;
        if (effectiveDescriptor(left.typeId()) instanceof EnumerationType) {
            enumSide = left;
            constSide = right;
        } else if (effectiveDescriptor(right.typeId()) instanceof EnumerationType) {
            enumSide = right;
            constSide = left;
        }
        if (enumSide == null || constSide == null) {
            return true;
        }
        TypedExpression constant = expressions.get(constSide.id());
        if (!(constant instanceof guru.interlis.model.ConstantExpression value)
                || !value.constantName().startsWith("#")) {
            return true;
        }
        String path = value.constantName().substring(1);
        EnumerationType enumeration = (EnumerationType) effectiveDescriptor(enumSide.typeId());
        boolean exact = enumeration.values().stream().anyMatch(v -> v.path().equals(path));
        boolean hasChildren = enumeration.values().stream().anyMatch(v -> v.path().startsWith(path + "."));
        return exact && (!hasChildren || isEnumTreeType(enumSide.typeId()));
    }

    /** Bind constants to their expected value type; shared inherited expressions are only checked by valueAssignable. */
    ExpressionResult contextualValue(ExpressionResult value, TypeId expected, SymbolId owner) {
        if(value.error())return value;
        value=contextualNumericConstant(value,expected);
        if (expressions.get(value.id()) instanceof guru.interlis.model.FunctionCallExpression call && isUnitConversion(call)
                && effectiveDescriptor(expected) instanceof NumericType numeric) {
            // Unrestricted NUMERIC is a wildcard formal, not a new unitless destination.
            if(numeric.range().unit().isEmpty() && numeric.range().minimum().isEmpty() && numeric.range().maximum().isEmpty())return value;
            TypeId type=expected;boolean error=!conversionCompatible(call,numeric);
            if(error) {
                var diagnostic=emitRule(index.byId(owner).orElseThrow(),"convertUnit source and destination need a common conversion unit","CONVERT_UNIT",call.range());
                type=nextTypeId();types.put(type,new ErrorType(type,new Origin.DeclaredOrigin(call.range()),List.of(diagnostic)));
            }
            expressions.put(call.id(),new guru.interlis.model.FunctionCallExpression(call.id(),type,
                    error?guru.interlis.model.ExpressionTypeState.ERROR:call.typeState(),call.range(),call.children(),call.functionName(),call.targetFunction(),call.argumentPaths()));
            return new ExpressionResult(call.id(),type,error);
        }
        var result=contextualEnumConstant(value,expected,owner);
        if(expressions.get(result.id()) instanceof guru.interlis.model.LiteralExpression literal && literal.textValue().isPresent()
                && effectiveDescriptor(expected) instanceof FormattedType formatted
                && formattedValues().parse(formatted,literal.textValue().orElseThrow(),true).isPresent()) {
            expressions.put(result.id(),new guru.interlis.model.LiteralExpression(literal.id(),expected,literal.typeState(),literal.range(),literal.children(),literal.literalKind(),literal.lexeme(),literal.decimal(),literal.textValue()));
            return new ExpressionResult(result.id(),expected,false);
        }
        return result;
    }

    private ExpressionResult contextualNumericConstant(ExpressionResult value, TypeId expected) {
        if(value.error() || !(effectiveDescriptor(expected) instanceof NumericType target) || target.range().unit().isEmpty()
                || !(effectiveDescriptor(value.typeId()) instanceof NumericType source) || source.range().unit().isPresent())return value;
        var expression=expressions.get(value.id());
        if(!(expression instanceof guru.interlis.model.LiteralExpression literal && literal.literalKind()==guru.interlis.model.LiteralKind.NUMBER)
                && !(expression instanceof guru.interlis.model.ConstantExpression constant && (constant.constantName().equals("PI") || constant.constantName().equals("LNBASE"))))return value;
        TypeId type=nextTypeId();var range=source.range();
        types.put(type,new NumericType(type,source.origin(),new NumericRange(range.minimum(),range.maximum(),range.circular(),range.direction(),target.range().unit(),range.referenceSystem())));
        if(expression instanceof guru.interlis.model.LiteralExpression literal)
            expressions.put(value.id(),new guru.interlis.model.LiteralExpression(literal.id(),type,literal.typeState(),literal.range(),literal.children(),literal.literalKind(),literal.lexeme(),literal.decimal(),literal.textValue()));
        else {var constant=(guru.interlis.model.ConstantExpression)expression;
            expressions.put(value.id(),new guru.interlis.model.ConstantExpression(constant.id(),type,constant.typeState(),constant.range(),constant.children(),constant.constantName()));}
        return new ExpressionResult(value.id(),type,false);
    }

    private boolean isUnitConversion(guru.interlis.model.FunctionCallExpression call) {
        return call.targetFunction().flatMap(id->Optional.ofNullable(symbols.get(id))).filter(FunctionSymbol.class::isInstance)
                .map(Symbol::origin).filter(Origin.PredefinedOrigin.class::isInstance).map(Origin.PredefinedOrigin.class::cast)
                .map(origin->origin.key().equals("INTERLIS.convertUnit")).orElse(false);
    }

    private boolean conversionCompatible(guru.interlis.model.FunctionCallExpression call,NumericType target) {
        var descriptor=effectiveDescriptor(expressions.get(call.children().getFirst()).typeId());
        if(guru.interlis.model.internal.TypeCompatibility.isUndefined(descriptor))return true;
        var from=(NumericType)descriptor;
        var sourceUnit=from.range().unit();var targetUnit=target.range().unit();
        if(sourceUnit.isEmpty() || targetUnit.isEmpty())return sourceUnit.isEmpty() && targetUnit.isEmpty();
        return guru.interlis.model.internal.TypeCompatibility.unitRelation(sourceUnit.orElseThrow(),targetUnit.orElseThrow(),this::extensionUnit)
                .map(relation->relation.decision()==guru.interlis.model.RelationDecision.ALLOWED).orElse(false);
    }

    boolean valueAssignable(TypedExpression value, TypeId expected) {
        var descriptor=effectiveDescriptor(expected);
        if(value instanceof guru.interlis.model.FunctionCallExpression call && isUnitConversion(call) && descriptor instanceof NumericType numeric)
            return conversionCompatible(call,numeric);
        if(value instanceof guru.interlis.model.ConstantExpression constant && constant.constantName().startsWith("#")) {
            var paths=enumerationPaths(descriptor);String path=constant.constantName().substring(1);
            if(!paths.isEmpty())return paths.contains(path) && (!(descriptor instanceof EnumerationType enumeration) || enumeration.treeValues()
                    || paths.stream().noneMatch(p->p.startsWith(path+".")));
        }
        if(value instanceof guru.interlis.model.LiteralExpression literal && literal.textValue().isPresent() && descriptor instanceof FormattedType formatted)
            return formattedValues().parse(formatted,literal.textValue().orElseThrow(),true).isPresent();
        return assignable(value.typeId(),expected);
    }

    private ExpressionResult contextualEnumConstant(ExpressionResult value, TypeId context, SymbolId owner) {
        if(!isEnumConstant(value))return value;
        var actual=(guru.interlis.model.ConstantExpression)expressions.get(value.id());
        TypeDescriptor expected=effectiveDescriptor(context);
        String path=actual.constantName().substring(1);
        var paths=enumerationPaths(expected);
        boolean valid=paths.contains(path) && (!(expected instanceof EnumerationType e) || e.treeValues()
                || paths.stream().noneMatch(p->p.startsWith(path+".")));
        if(!valid)return value;
        expressions.put(value.id(),new guru.interlis.model.ConstantExpression(actual.id(),context,actual.typeState(),actual.range(),actual.children(),actual.constantName()));
        if(expected instanceof EnumerationType enumeration) {
            var segments=new ArrayList<ReferenceSegment>();int start=actual.range().startOffset()+1;String prefix="";
            for(String part:path.split("\\.")) {
                prefix=prefix.isEmpty()?part:prefix+"."+part;
                var target=enumMember(enumeration,prefix);
                var range=new SourceRange(actual.range().source(),actual.range().revision(),start,start+part.length());
                segments.add(new ReferenceSegment(part,range,Optional.ofNullable(target)));start+=part.length()+1;
            }
            SymbolId target=segments.getLast().target().orElse(null);
            var range=new SourceRange(actual.range().source(),actual.range().revision(),actual.range().startOffset()+1,actual.range().endOffset());
            if(target!=null && recordedReferences.add(new ReferenceSite(owner,range,ReferenceUsage.EXPRESSION,Optional.of(target)))) {
                var id=new ReferenceId("ref-"+(++referenceCounter));
                references.put(id,new NameReference(id,new ScopeId(owner.value()),ReferenceUsage.EXPRESSION,segments,range,new BindingResolution.BoundTarget(target)));
            }
        }
        return new ExpressionResult(value.id(),context,value.error());
    }

    private SymbolId enumMember(EnumerationType type,String path) {
        Set<TypeId> seen=new HashSet<>();
        for(var current=type;current!=null && seen.add(current.id());) {
            for(var entry:declaredTypes.entrySet()) {
                checkWork();TypeDescriptor candidate=effectiveDescriptor(entry.getValue());
                while(candidate instanceof CollectionType collection)candidate=effectiveDescriptor(collection.elementType());
                if(candidate==null || !candidate.id().equals(current.id()))continue;
                for(var child:index.children(entry.getKey()))if(child.kind()==SymbolKind.ENUM_ELEMENT && child.name().equals(path))return child.id();
            }
            var base=current.baseType().map(this::effectiveDescriptor).orElse(null);current=base instanceof EnumerationType e?e:null;
        }
        return null;
    }

    private boolean comparisonAllowed(ExpressionResult left, ExpressionResult right, guru.interlis.model.ComparisonOperator operator) {
        if(!enumConstantValid(left,right) || !formattedTextValid(left,right))return false;
        TypeDescriptor a=effectiveDescriptor(left.typeId()),b=effectiveDescriptor(right.typeId());
        if(isEnumConstant(left) && b instanceof EnumerationType)a=b;
        if(isEnumConstant(right) && a instanceof EnumerationType)b=a;
        if(a instanceof FormattedType && expressions.get(right.id()) instanceof guru.interlis.model.LiteralExpression literal
                && literal.literalKind()==guru.interlis.model.LiteralKind.TEXT)b=a;
        if(b instanceof FormattedType && expressions.get(left.id()) instanceof guru.interlis.model.LiteralExpression literal
                && literal.literalKind()==guru.interlis.model.LiteralKind.TEXT)a=b;
        return guru.interlis.model.internal.TypeCompatibility.compare(a,b,operator,this::effectiveDescriptor,id->effectiveDescriptor(declaredTypes.get(id))).decision()
                ==guru.interlis.model.RelationDecision.ALLOWED;
    }

    boolean isBoolean(TypeId typeId) {
        var type=effectiveDescriptor(typeId);
        return type instanceof guru.interlis.model.BooleanType || guru.interlis.model.internal.TypeCompatibility.isUndefined(type);
    }

    private boolean isNumeric(TypeId typeId) {
        var type=effectiveDescriptor(typeId);
        return type instanceof NumericType || guru.interlis.model.internal.TypeCompatibility.isUndefined(type);
    }

    TypeDescriptor effectiveDescriptor(TypeId typeId) {
        TypeDescriptor descriptor = types.get(typeId);
        Set<TypeId> seen = new HashSet<>();
        while (descriptor instanceof DomainReferenceType reference && seen.add(descriptor.id())) {
            checkWork();
            TypeId target = declaredTypes.get(reference.targetDomain());
            if (target == null) break;
            descriptor = types.get(target);
        }
        return descriptor;
    }

    String typeName(TypeId id) {
        TypeDescriptor descriptor = effectiveDescriptor(id);
        if(descriptor instanceof guru.interlis.model.EnumArgumentType argument)return argument.treeValues()?"ENUMTREEVAL":"ENUMVAL";
        if (descriptor instanceof TextType text) {
            return text.textKind().name() + (text.maxLength().isPresent() ? "*" + text.maxLength().getAsInt() : "");
        }
        return descriptor == null ? "UNKNOWN" : descriptor.kind().name();
    }

    DiagnosticId emitTypeMismatch(SourceId source, SourceRange range, String actual, String expected) {
        return collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0005"), Severity.ERROR,
                        Phase.TYPE_CHECKING, "diagnostic.semantics.type")
                .at(range)
                .argument(new DiagnosticArgument.TypeNameArgument("actual", actual))
                .argument(new DiagnosticArgument.TypeNameArgument("expected", expected)));
    }

    private Optional<DecimalLiteral> decimal(String lexeme) {
        try {
            return Optional.of(DecimalLiteral.parse(lexeme));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private SourceId ownerSource(SymbolId owner) {
        return index.modelOfSymbol(owner).flatMap(index::byId)
                .map(DeclInfo::sourceId).orElseGet(() -> sourceSet.sources().keySet().iterator().next());
    }

    // ---- overrides, effective members and types --------------------------------

    private final Set<SymbolId> effectiveProcessed = new HashSet<>();

    private void resolveOverridesAndEffectiveMembers() {
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            checkWork();
            for (DeclInfo child : index.children(entry.id())) {
                checkWork();
                collectEffective(child);
            }
        }
    }

    void collectEffective(DeclInfo decl) {
        if (!effectiveProcessed.add(decl.id())) {
            return;
        }
        if (decl.kind() == SymbolKind.CLASS || decl.kind() == SymbolKind.STRUCTURE
                || decl.kind() == SymbolKind.ASSOCIATION) {
            List<SymbolId> chain = baseChain(decl.id());
            if (hasCycle(chain)) {
                emitCycle(decl);
                effectiveMembers.put(decl.id(), localMembers(decl));
                return;
            }
            List<EffectiveMember> result = new ArrayList<>();
            Map<String, Integer> positions = new HashMap<>();
            for (int depth = chain.size() - 1; depth >= 0; depth--) {
                checkWork();
                SymbolId current = chain.get(depth);
                if (symbols.get(current) != null && symbols.get(current).origin() instanceof Origin.PredefinedOrigin) {
                    var predefined = symbols.get(current);
                    var members = predefined instanceof StructureSymbol s ? s.members() : predefined instanceof ClassSymbol c ? c.members() : List.<SymbolId>of();
                    for (SymbolId member : members) {
                        if (symbols.get(member).kind() != SymbolKind.ATTRIBUTE) continue;
                        String name = symbols.get(member).name().value();
                        Integer existing = positions.get(name);
                        int position = existing == null ? result.size() : existing;
                        var effective = new EffectiveMember(member, decl.id(), pathTo(chain, depth, decl.id()), position,
                                EffectiveMemberOrigin.INHERITED, existing != null);
                        if (existing == null) { positions.put(name, position); result.add(effective); }
                        else result.set(position, effective);
                    }
                }
                for (DeclInfo attribute : index.children(current)) {
                    checkWork();
                    if (attribute.kind() != SymbolKind.ATTRIBUTE && attribute.kind() != SymbolKind.ROLE) {
                        continue;
                    }
                    Integer existing = positions.get(attribute.name());
                    if (existing != null) {
                        if (!attribute.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                            emitRule(attribute, "override requires EXTENDED", "OVERRIDE");
                        }
                        EffectiveMember old = result.get(existing);
                        result.set(existing, new EffectiveMember(attribute.id(), decl.id(),
                                pathTo(chain, depth, decl.id()), existing,
                                EffectiveMemberOrigin.OVERRIDDEN, true));
                        AttributeSymbol override = attribute.kind() == SymbolKind.ATTRIBUTE
                                ? overrideAttribute(attribute.id(), old.declaredSymbol()) : null;
                        if (override != null) {
                            symbols.put(attribute.id(), override);
                        }
                    } else {
                        int position = result.size();
                        positions.put(attribute.name(), position);
                        result.add(new EffectiveMember(attribute.id(), decl.id(),
                                pathTo(chain, depth, decl.id()), position,
                                depth == 0 ? EffectiveMemberOrigin.LOCAL
                                        : EffectiveMemberOrigin.INHERITED, false));
                    }
                }
            }
            effectiveMembers.put(decl.id(), List.copyOf(result));
        }
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            collectEffective(child);
        }
    }

    DiagnosticId emitRule(DeclInfo decl, String rule, String reason) {
        return emitRule(decl, rule, reason, decl.origin() instanceof Origin.DeclaredOrigin declared
                ? declared.range() : decl.nameRange().orElse(null));
    }

    DiagnosticId emitRule(DeclInfo decl, String rule, String reason, SourceRange range) {
        return collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0010"),
                        Severity.ERROR, Phase.SEMANTIC_CHECKING,
                        "diagnostic.semantics.inheritance")
                .at(range)
                .argument(new DiagnosticArgument.IdentifierArgument("element", decl.name()))
                .argument(new DiagnosticArgument.TextArgument("rule", rule + "/" + reason)));
    }

    SymbolId scopeRoot(SymbolId symbol) {
        SymbolId current = symbol;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            checkWork();
            DeclInfo info = index.byId(current).orElse(null);
            if (info == null) {
                return null;
            }
            if (info.kind() == SymbolKind.TOPIC || info.kind() == SymbolKind.MODEL) {
                return current;
            }
            current = info.owner().orElse(null);
        }
        return null;
    }

    private AttributeSymbol overrideAttribute(SymbolId id, SymbolId baseId) {
        if (symbols.get(id) instanceof AttributeSymbol attribute) {
            return new AttributeSymbol(attribute.header(), attribute.declaredType(),
                    attribute.mandatory(), attribute.transientAttribute(), attribute.continuous(),
                    attribute.subdivision(), Optional.of(baseId), attribute.derivations());
        }
        return null;
    }

    List<SymbolId> baseChain(SymbolId id) {
        List<SymbolId> chain = new ArrayList<>();
        SymbolId current = id;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null) {
            checkWork();
            chain.add(current);
            if (!seen.add(current)) break;
            current = baseOf.get(current);
        }
        return chain;
    }

    private boolean hasCycle(List<SymbolId> chain) {
        return chain.size() != new HashSet<>(chain).size();
    }

    private void emitCycle(DeclInfo decl) {
        collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0006"), Severity.ERROR,
                        Phase.SEMANTIC_CHECKING, "diagnostic.semantics.cycle")
                .at(decl.origin() instanceof Origin.DeclaredOrigin d ? d.range() : decl.nameRange().orElse(null))
                .argument(new DiagnosticArgument.TextArgument("relationship", "INHERITANCE"))
                .argument(new DiagnosticArgument.IdentifierListArgument("cycle",
                        baseChain(decl.id()).stream().map(SymbolId::value).toList())));
    }

    private List<EffectiveMember> localMembers(DeclInfo decl) {
        List<EffectiveMember> result = new ArrayList<>();
        int position = 0;
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            if (child.kind() == SymbolKind.ATTRIBUTE) {
                result.add(new EffectiveMember(child.id(), decl.id(), List.of(decl.id()), position++,
                        EffectiveMemberOrigin.LOCAL, false));
            }
        }
        return List.copyOf(result);
    }

    private List<SymbolId> pathTo(List<SymbolId> chain, int depth, SymbolId derived) {
        List<SymbolId> result = new ArrayList<>();
        for (int i = depth; i < chain.size(); i++) {
            checkWork();
            result.add(chain.get(i));
        }
        result.add(derived);
        return List.copyOf(result);
    }

    private void buildEffectiveTypes() {
        for (Map.Entry<SymbolId, TypeId> entry : declaredTypes.entrySet()) {
            checkWork();
            effectiveTypes.put(entry.getKey(), effectiveType(entry.getValue()));
        }
    }

    private EffectiveType effectiveType(TypeId declared) {
        List<SymbolId> aliasPath = new ArrayList<>();
        TypeId current = declared;
        Set<TypeId> seen = new HashSet<>();
        while (true) {
            checkWork();
            TypeDescriptor descriptor = types.get(current);
            if (!(descriptor instanceof DomainReferenceType reference)) {
                break;
            }
            if (!seen.add(current)) {
                break;
            }
            aliasPath.add(reference.targetDomain());
            Symbol symbol = symbols.get(reference.targetDomain());
            if (symbol instanceof DomainSymbol domain) {
                current = domain.typeId();
            } else {
                break;
            }
        }
        return new EffectiveType(declared, current, aliasPath);
    }

    // ---- references and coverage ----------------------------------------------

    private void buildReferences() {
        // Walk the complete declaration tree: attributes and roles also carry
        // navigable type/role references, not only model-level declarations.
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            checkWork();
            if (symbols.get(entry.id()) instanceof ModelSymbol model) {
                for (var imported : model.imports()) {
                    var name = new guru.interlis.syntax.NameSyntax(
                            new guru.interlis.syntax.SyntaxNodeId("import-" + referenceCounter),
                            imported.range(), List.of(new guru.interlis.syntax.NameSyntax.NameSegmentSyntax(
                                    imported.importedModel(), imported.range())));
                    recordReference(entry.declaration(), name, imported.targetModel().orElse(null), ReferenceUsage.IMPORT);
                }
            }
            referenceTree(entry.id());
        }
    }

    private void referenceTree(SymbolId owner) {
        for (DeclInfo child : index.children(owner)) {
            checkWork();
            referenceFor(child);
            referenceTree(child.id());
        }
    }

    private void referenceFor(DeclInfo decl) {
        if (decl.syntax() instanceof ClassDeclarationSyntax clazz && clazz.baseType().isPresent()) {
            recordReference(decl, clazz.baseType().get(), baseOf.get(decl.id()), ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof StructureDeclarationSyntax structure
                && structure.baseType().isPresent()) {
            recordReference(decl, structure.baseType().get(), baseOf.get(decl.id()),
                    ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof TopicSyntax topic && topic.baseTopic().isPresent()) {
            recordReference(decl, topic.baseTopic().get(), baseOf.get(decl.id()),
                    ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof DomainDeclarationSyntax domain
                && domain.baseDomain().isPresent()) {
            recordReference(decl, domain.baseDomain().get(), baseOf.get(decl.id()),
                    ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof AssociationDeclarationSyntax association
                && association.baseAssociation().isPresent()) {
            recordReference(decl, association.baseAssociation().orElseThrow(), baseOf.get(decl.id()), ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof AttributeDeclarationSyntax attribute) {
            referenceForType(decl, attribute.type());
        }
    }

    private void referenceForType(DeclInfo decl, TypeSyntax syntax) {
        if (syntax instanceof DomainReferenceSyntax reference) {
            TypeDescriptor descriptor = types.get(declaredTypes.get(decl.id()));
            if (descriptor instanceof DomainReferenceType domain) {
                recordReference(decl, reference.name(), domain.targetDomain(),
                        ReferenceUsage.TYPE_USE);
            }
        } else if (syntax instanceof CollectionTypeSyntax collection) {
            referenceForType(decl, collection.elementType());
        }
    }

    private record ReferenceSite(SymbolId owner, SourceRange range, ReferenceUsage usage, Optional<SymbolId> target) { }

    void recordReference(DeclInfo owner, guru.interlis.syntax.NameSyntax name,
                                 SymbolId target, ReferenceUsage usage) {
        if (!recordedReferences.add(new ReferenceSite(owner.id(), name.range(), usage, Optional.ofNullable(target)))) return;
        ReferenceId id = new ReferenceId("ref-" + (++referenceCounter));
        List<ReferenceSegment> segments = new ArrayList<>();
        SymbolId current = target;
        for (int i = name.segments().size() - 1; i >= 0; i--) {
            checkWork();
            var segment = name.segments().get(i);
            if (i < name.segments().size() - 1) {
                current = referenceOwner(current);
                while (current != null && !referenceName(current).equals(segment.text())) {
                    checkWork();
                    current = referenceOwner(current);
                }
            }
            segments.addFirst(new ReferenceSegment(segment.text(), segment.range(), Optional.ofNullable(current)));
        }
        BindingResolution resolution = target == null
                ? new BindingResolution.UnresolvedTarget(guru.interlis.model.UnresolvedReason.NAME_NOT_FOUND,
                List.of())
                : new BindingResolution.BoundTarget(target);
        references.put(id, new NameReference(id, new ScopeId(owner.id().value()), usage,
                segments, name.range(), resolution));
    }

    private SymbolId referenceOwner(SymbolId symbol) {
        if (symbol == null) return null;
        var declaration = index.byId(symbol);
        if (declaration.isPresent()) return declaration.orElseThrow().owner().orElse(null);
        var value = symbols.get(symbol);
        if (value == null) return null;
        if (value.owner().isPresent()) return value.owner().orElseThrow();
        return value.origin() instanceof Origin.PredefinedOrigin predefined && predefined.key().startsWith("INTERLIS.")
                ? PredefinedModels.ensure(this, predefined.language()) : null;
    }

    private String referenceName(SymbolId symbol) {
        return index.byId(symbol).map(DeclInfo::name).orElseGet(() -> {
            String name = symbols.get(symbol).name().value(); return name.substring(name.lastIndexOf('.') + 1);
        });
    }

    private AnalysisCoverage buildCoverage() {
        List<AnalysisCoverage.CoverageEntry> entries = new ArrayList<>();
        List<guru.interlis.diagnostics.Diagnostic> diagnostics = collector.snapshot().stream()
                .filter(d -> d.phase() == Phase.LEXING || d.phase() == Phase.PARSING
                        || d.primaryRange().map(closure::contains).orElse(true)).toList();
        for (SourceId source : closure.sources()) {
            checkWork();
            boolean lexing = diagnostics.stream().noneMatch(d -> d.primaryRange()
                    .filter(r -> r.source().equals(source)).isPresent()
                    && d.code().value().equals("ILI-SYN-0001"));
            boolean parsing = diagnostics.stream().noneMatch(d -> d.primaryRange()
                    .filter(r -> r.source().equals(source)).isPresent()
                    && d.code().value().matches("ILI-SYN-000[2-6]"));
            boolean indexing = diagnostics.stream().noneMatch(d -> d.code().value().equals("ILI-SEM-0003")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean binding = diagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-000[124]")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean typeChecking = diagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-000[578]")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean semantic = diagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-0006|ILI-SEM-0010")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            entries.add(entry(Phase.LEXING, source, lexing ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.PARSING, source, parsing ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.DECLARATION_INDEXING, source,
                    indexing ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.BINDING, source,
                    binding ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.TYPE_CHECKING, source,
                    typeChecking ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.SEMANTIC_CHECKING, source,
                    semantic ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
        }
        for (var diagnostic : diagnostics) {
            if (diagnostic.code().value().equals("ILI-IMPL-0001")) {
                var feature = diagnostic.arguments().stream()
                        .filter(a -> a instanceof DiagnosticArgument.TextArgument)
                        .map(a -> ((DiagnosticArgument.TextArgument) a).value()).findFirst();
                entries.add(new AnalysisCoverage.CoverageEntry(Phase.SEMANTIC_CHECKING,
                        diagnostic.primaryRange().map(SourceRange::source), CoverageStatus.NOT_IMPLEMENTED,
                        feature, Optional.of(diagnostic.id())));
            }
        }
        return new AnalysisCoverage(entries);
    }

    private AnalysisCoverage.CoverageEntry entry(Phase phase, SourceId source,
                                                 CoverageStatus status) {
        return new AnalysisCoverage.CoverageEntry(phase, Optional.of(source), status,
                Optional.empty(), Optional.empty());
    }

    private Optional<SymbolId> findModelId(String name, int offset, SourceId source) {
        return index.models().stream()
                .filter(e -> e.model().sourceId().equals(source))
                .filter(e -> e.model().name().equals(name))
                .filter(e -> e.model().declarationOffset() == offset)
                .map(IndexedDeclarations.ModelEntry::id)
                .findFirst();
    }

    private static String unquote(String text) {
        return InterlisStrings.decode(text).text();
    }

    record GraphResult(SemanticGraph graph, AnalysisCoverage coverage, List<SymbolId> roots,
                       List<SymbolId> models, Map<SourceId, Set<String>> unsupportedBySource) {
        GraphResult {
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(coverage, "coverage");
            roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
            models = List.copyOf(Objects.requireNonNull(models, "models"));
            unsupportedBySource = Map.copyOf(Objects.requireNonNull(unsupportedBySource,
                    "unsupportedBySource"));
        }
    }

    record ExpressionResult(ExpressionId id, TypeId typeId, boolean error) {
    }

}
