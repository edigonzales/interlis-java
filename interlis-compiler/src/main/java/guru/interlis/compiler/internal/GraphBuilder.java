package guru.interlis.compiler.internal;

import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.SourceSet;
import guru.interlis.compiler.internal.ExpressionBindings.ExpressionResult;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.AnalysisCoverage;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
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
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.PendingReason;
import guru.interlis.model.PendingType;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.ReferenceSegment;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.RoleSymbol;
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
    final NameBindings names;
    final ReferenceBindings references;
    final TranslationBindings translations;
    final ExpressionBindings expressions;
    final ObjectPathBindings paths;
    final TypeBindings types;
    final UnitBindings units;
    final OidBindings oid;
    final FormattedTypeBindings formats;
    final GenericBindings generics;
    private final SemanticDiagnostics diagnostics;
    private final Map<SourceId, Set<String>> syntaxUnsupportedBySource;
    private final Map<SymbolId, SymbolId> externalConstraintTargets = new LinkedHashMap<>();
    private final Set<SymbolId> resolvedObjectBases = new HashSet<>();


    private final Set<SymbolId> buildingAssociations = new HashSet<>();
    private final Map<SymbolId, SymbolId> modelIdByDecl = new LinkedHashMap<>();
    private final Map<String, DeclInfo> modelNames = new LinkedHashMap<>();
    private final guru.interlis.execution.CancellationToken cancellation;
    private final SemanticBuildState state = new SemanticBuildState();
    private final SemanticWork work;
    private final OverlayBindings overlays;


    GraphBuilder(IndexedDeclarations index, SourceSet sourceSet, Closure closure,
                 DiagnosticCollector collector, guru.interlis.model.SnapshotId snapshotId,
                 Map<SourceId, Set<String>> syntaxUnsupportedBySource,
                 guru.interlis.execution.CancellationToken cancellation,
                 guru.interlis.compiler.AnalysisBudget budget, OverlayBindings overlays) {
        this.overlays = overlays;
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.work = new SemanticWork(this.cancellation,
                Objects.requireNonNull(budget, "budget").maxSemanticWorkUnits(), overlays.workUnits());
        this.index = Objects.requireNonNull(index, "index");
        this.sourceSet = Objects.requireNonNull(sourceSet, "sourceSet");
        this.closure = Objects.requireNonNull(closure, "closure");
        this.diagnostics = new SemanticDiagnostics(collector, this.closure);
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.syntaxUnsupportedBySource = Map.copyOf(
                Objects.requireNonNull(syntaxUnsupportedBySource, "syntaxUnsupportedBySource"));
        this.resolver = new NameResolver(index, closure);
        this.expressions = new ExpressionBindings(this, state, work, index, diagnostics, sourceSet, views);
        this.paths = new ObjectPathBindings(this, state, work, index, diagnostics, sourceSet, cancellation, views, structures);
        this.types = new TypeBindings(this, state, work, index, diagnostics);
        this.units = new UnitBindings(this, state, work, index, diagnostics);
        this.oid = new OidBindings(this, state, work, index, diagnostics);
        this.formats = new FormattedTypeBindings(this, state, work, index, diagnostics);
        this.generics = new GenericBindings(this, state, work, index, diagnostics);
        this.references = new ReferenceBindings(index, state, work, state::storeReference,
                language -> PredefinedModels.ensure(state, language));
        this.names = new NameBindings(index, resolver, work, diagnostics, references, temporal, metadata, lines,
                oid::builtinOidDomain, types::builtinSimpleDomain, units::builtinUnit, expressions::builtinDeclaration);
        this.translations = new TranslationBindings(index, state, work, diagnostics,
                types::effectiveDescriptor, paths::findMember, views, state::replaceSymbol);
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            checkWork();
            modelIdByDecl.put(entry.declaration().id(), entry.id());
        }
    }

    void checkWork() {
        work.checkpoint();
    }

    DiagnosticId unresolved(guru.interlis.syntax.NameSyntax name) {
        return diagnostics.unresolved(name);
    }

    DiagnosticId emitUnsupported(SourceId source, SourceRange range, String feature, String construct) {
        return diagnostics.emitUnsupported(source, range, feature, construct);
    }

    DiagnosticId emitTypeMismatch(SourceId source, SourceRange range, String actual, String expected) {
        return diagnostics.emitTypeMismatch(range, actual, expected);
    }

    DiagnosticId emitRule(DeclInfo decl, String rule, String reason) {
        return diagnostics.emitRule(decl, rule, reason);
    }

    DiagnosticId emitRule(DeclInfo decl, String rule, String reason, SourceRange range) {
        return diagnostics.emitRule(decl, rule, reason, range);
    }

    final LineForms lines = new LineForms(this, state);
    final GraphicBuilder graphics = new GraphicBuilder(this, state);
    final MetadataBindings metadata = new MetadataBindings(this, state);
    final StructureBindings structures = new StructureBindings(this, state);
    final FunctionBindings functions = new FunctionBindings(this, state);
    final PredefinedTemporal temporal = new PredefinedTemporal(this, state);

    GraphResult build() {
        syntaxUnsupportedBySource.forEach((source, features) -> {
            diagnostics.registerUnsupportedSource(source);
            for (String feature : features) {
                checkWork();
                if (diagnostics.recordUnsupported(source, feature)) {
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
        validateTopicInheritanceCycles();
        units.validateUnits();
        resolveOverridesAndEffectiveMembers();
        structures.validate();
        lines.validateConcreteMembers();
        metadata.validate();
        generics.validateGenericSystem();
        oid.bindOidRules();
        validateTopicDependencies();
        views.validateDependencies();
        graphics.validateDependencies();
        validateReferenceTargets();
        types.buildEffectiveTypes();
        formats.validateFormattedUses();
        units.validateNumericUses();
        new NumericSubdivisions(this, state).validate();
        references.complete();
        validateAssociationRules();
        validateAttributeOverrides();
        validateClassExtensions();
        validateTypeNamespaces();
        validateConstraintNames();
        overlays.validateCrs(this, state);
        translations.complete();
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
        for (ModelSymbol model : state.symbolsInBuildOrder().stream()
                .filter(s -> s instanceof ModelSymbol)
                .map(s -> (ModelSymbol) s)
                .filter(s -> modelIds.contains(s.id()))
                .toList()) {
            checkWork();
            languages.add(model.header().languageVersion());
        }
        PredefinedModels.finish(work, state, languages);
        SemanticGraph graph = state.freeze(
                snapshotId, rootIds, new ArrayList<>(modelIds), languages,
                sourceSet.sources().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().revision())));
        AnalysisCoverage coverage = buildCoverage();
        return new GraphResult(graph, coverage, rootIds, new ArrayList<>(modelIds),
                diagnostics.unsupportedBySource());
    }

    private void validateTypeNamespaces() {
        var typeKinds = DeclarationNamespaces.TYPE_KINDS;
        var names = new LinkedHashMap<SymbolId, Map<String, DeclInfo>>();
        var declarations = index.all().stream().sorted(Comparator.comparingInt(DeclInfo::order)).toList();
        for (var declaration : declarations) {
            checkWork();
            if (declaration.owner().isPresent() && declaration.nameRange().isPresent()
                    && typeKinds.contains(declaration.kind())) {
                var scope = names.computeIfAbsent(declaration.owner().orElseThrow(), ignored -> new LinkedHashMap<>());
                var prior = scope.putIfAbsent(declaration.name(), declaration);
                if (prior != null) emitNamespaceRule(declaration, prior,
                        "type names must be unique in their declaring scope", "SCOPE_TYPE_NAME_UNIQUE");
            }
        }
        for (var declaration : declarations) {
            checkWork();
            if (declaration.owner().isEmpty() || declaration.nameRange().isEmpty()
                    || !typeKinds.contains(declaration.kind())) continue;
            var owner = declaration.owner().orElseThrow();
            var outer = index.byId(owner).flatMap(DeclInfo::owner);
            while (outer.isPresent()) {
                checkWork();
                var prior = names.getOrDefault(outer.orElseThrow(), Map.of()).get(declaration.name());
                if (prior != null) {
                    emitNamespaceRule(declaration, prior,
                            "local type names must not shadow an enclosing type name", "SCOPE_OUTER_TYPE_NAME");
                    break;
                }
                outer = index.byId(outer.orElseThrow()).flatMap(DeclInfo::owner);
            }
            if (declaration.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) continue;
            var seen = new HashSet<SymbolId>();
            for (var inherited = state.baseOf(owner); inherited != null && seen.add(inherited); inherited = state.baseOf(inherited)) {
                checkWork();
                var prior = names.getOrDefault(inherited, Map.of()).get(declaration.name());
                if (prior != null) {
                    emitNamespaceRule(declaration, prior,
                            "inherited type names require an explicit EXTENDED declaration", "SCOPE_INHERITED_TYPE_NAME");
                    break;
                }
            }
        }
    }

    private void emitNamespaceRule(DeclInfo declaration, DeclInfo prior, String rule, String reason) {
        var related = new guru.interlis.diagnostics.RelatedLocation(prior.nameRange().orElseThrow(),
                "diagnostic.related.conflictingDeclaration",
                List.of(new DiagnosticArgument.IdentifierArgument("name", prior.name())));
        diagnostics.emitRule(declaration, rule, reason, List.of(related));
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
            var symbol = state.symbol(target);
            if (!(symbol instanceof ClassSymbol || symbol instanceof StructureSymbol
                    || symbol instanceof AssociationSymbol || symbol instanceof ViewSymbol)) continue;
            var scope = new ConstraintScope(target, paths.scopeRoot(declaration.id()));
            if (!names.computeIfAbsent(scope, ignored -> new LinkedHashSet<>()).add(syntax.name().orElseThrow()))
                emitRule(declaration, "constraint names must be unique within their declaring class",
                        "CONSTRAINT_NAME", declaration.nameRange().orElse(syntax.range()));
        }
    }

    Optional<SymbolId> baseTopicMember(SymbolId context, String name,
                                               Set<SymbolKind> expected) {
        SymbolId current = paths.scopeRoot(context);
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            checkWork();
            DeclInfo topicDecl = index.byId(current).orElse(null);
            if (topicDecl == null || !(topicDecl.syntax() instanceof guru.interlis.syntax.TopicSyntax syntax)
                    || syntax.baseTopic().isEmpty()) {
                return Optional.empty();
            }
            Optional<SymbolId> base = names.tryResolve(topicDecl, syntax.baseTopic().orElseThrow(),
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
    private void validateClassExtensions() {
        Set<SymbolId> extendedBases = new LinkedHashSet<>();
        Map<SymbolId, List<SymbolId>> subclasses = new LinkedHashMap<>();
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            checkWork();
            if (!(symbol instanceof ClassSymbol clazz) || clazz.baseType().isEmpty()) {
                continue;
            }
            SymbolId base = clazz.baseType().orElseThrow();
            subclasses.computeIfAbsent(base, ignored -> new ArrayList<>()).add(clazz.id());
            Symbol baseSymbol = state.symbol(base);
            if (baseSymbol != null && baseSymbol.header().modifiers()
                    .contains(guru.interlis.model.Modifier.EXTENDED)) {
                extendedBases.add(base);
            }
        }
        Map<String, SymbolId> extendedByTopic = new LinkedHashMap<>();
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            checkWork();
            if (symbol instanceof ClassSymbol clazz
                    && clazz.header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                SymbolId topic = paths.scopeRoot(clazz.id());
                if (topic != null) {
                    extendedByTopic.put(topic + ":" + clazz.name().value(), clazz.id());
                }
            }
        }
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            checkWork();
            if (!(symbol instanceof ClassSymbol clazz) || clazz.baseType().isEmpty()) {
                continue;
            }
            SymbolId base = clazz.baseType().orElseThrow();
            Symbol baseSymbol = state.symbol(base);
            SymbolId topic = paths.scopeRoot(clazz.id());
            if (baseSymbol != null && topic != null) {
                SymbolId localExtended = extendedByTopic.get(topic + ":" + baseSymbol.name().value());
                if (localExtended != null && !localExtended.equals(base) && !localExtended.equals(clazz.id())) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            emitRule(decl, "subclass must extend the local EXTENDED class",
                                    "EXTENDED_BASE_CLASS"));
                } else if (localExtended != null
                        && state.symbol(topic) instanceof TopicSymbol topicSymbol
                        && topicSymbol.baseTopic().isEmpty()) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            emitRule(decl, "EXTENDED class may only be extended in a derived topic",
                                    "EXTENDED_BASE_CLASS"));
                }
            }
        }
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            checkWork();
            if (!(symbol instanceof ClassSymbol clazz)
                    || !clazz.header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                continue;
            }
            SymbolId topic = paths.scopeRoot(clazz.id());
            if (!(state.symbol(topic) instanceof TopicSymbol topicSymbol)
                    || topicSymbol.baseTopic().isEmpty()) {
                continue;
            }
            SymbolId baseTopic = topicSymbol.baseTopic().orElseThrow();
            for (DeclInfo baseChild : index.children(baseTopic)) {
                checkWork();
                boolean specialized = index.children(baseTopic).stream()
                        .filter(child -> child.kind() == SymbolKind.CLASS)
                        .anyMatch(child -> state.baseOf(child.id()) != null
                                && state.baseOf(child.id()).equals(baseChild.id()));
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
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            checkWork();
            if (!(symbol instanceof ClassSymbol) && !(symbol instanceof StructureSymbol) && !(symbol instanceof AssociationSymbol)) {
                continue;
            }
            SymbolId base = state.baseOf(symbol.id());
            if (base == null) {
                continue;
            }
            List<SymbolId> members = symbol instanceof ClassSymbol classSymbol
                    ? classSymbol.members() : symbol instanceof StructureSymbol structure ? structure.members()
                    : ((AssociationSymbol) symbol).attributes();
            for (SymbolId member : members) {
                checkWork();
                Symbol memberSymbol = state.symbol(member);
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
                TypeDescriptor baseType = types.effectiveDescriptor(baseAttribute.declaredType());
                TypeDescriptor derivedType = types.effectiveDescriptor(attribute.declaredType());
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
        if (state.type(baseAttribute.declaredType()) instanceof DomainReferenceType baseDomainRef
                && state.type(attribute.declaredType()) instanceof DomainReferenceType derivedDomainRef
                && !domainExtends(derivedDomainRef.targetDomain(), baseDomainRef.targetDomain())) {
            emitRule(attributeDecl, "extended attribute changes domain",
                    "ATTRIBUTE_OVERRIDE_TYPE");
        }
    }

    AttributeSymbol findBaseAttribute(SymbolId base, String name) {
        SymbolId current = base;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            checkWork();
            Symbol symbol = state.symbol(current);
            List<SymbolId> members = symbol instanceof ClassSymbol classSymbol
                    ? classSymbol.members()
                    : symbol instanceof StructureSymbol structure ? structure.members()
                    : symbol instanceof AssociationSymbol association ? association.attributes() : List.of();
            for (SymbolId member : members) {
                checkWork();
                Symbol memberSymbol = state.symbol(member);
                if (memberSymbol instanceof AttributeSymbol attribute && attribute.name().value().equals(name)) {
                    return attribute;
                }
            }
            current = state.baseOf(current);
        }
        return null;
    }

    boolean domainExtends(SymbolId derived, SymbolId base) {
        SymbolId current = derived;
        Set<SymbolId> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            checkWork();
            if (current.equals(base)) {
                return true;
            }
            current = state.baseOf(current);
        }
        return false;
    }

    private String overrideProblem(TypeDescriptor base, TypeDescriptor derived, guru.interlis.model.LanguageVersion language) {
        var relation=types.extension(derived,base,language);
        return relation.decision()==guru.interlis.model.RelationDecision.ALLOWED?null:relation.reason();
    }


    private void validateAssociationRules() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            checkWork();
            if (symbol instanceof AssociationSymbol association) {
                Set<String> roleNames = new LinkedHashSet<>();
                for (SymbolId roleId : association.roles()) {
                    checkWork();
                    Symbol role = state.symbol(roleId);
                    if (role != null) {
                        roleNames.add(role.name().value());
                    }
                }
                for (SymbolId memberId : association.attributes()) {
                    checkWork();
                    Symbol member = state.symbol(memberId);
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
        Map<SymbolId, Map<String, DeclInfo>> roleNamesByTarget = new LinkedHashMap<>();
        for (AssociationSymbol association : state.symbolsInBuildOrder().stream()
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
                for (Symbol other : state.symbolsInBuildOrder()) {
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
            long aggregations = effectiveRoles.stream()
                    .filter(role -> role.aggregation() != RoleAggregation.NONE).count();
            if (aggregations > 1) {
                emitRule(decl, "only one aggregation role is allowed", "MULTIPLE_AGGREGATION");
            }
            if (effectiveRoles.size()!=2 && effectiveRoles.stream().anyMatch(role->role.aggregation()==RoleAggregation.AGGREGATION)) {
                emitRule(decl, "aggregation requires exactly two effective roles", "AGGREGATION_ARITY");
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
                    .map(state::symbol).filter(RoleSymbol.class::isInstance)
                    .map(RoleSymbol.class::cast).toList();
            if (roles.size() == 2) {
                roles.get(0).targetRoots().forEach(t -> addRoleNamespace(roleNamesByTarget, t, roles.get(1)));
                roles.get(1).targetRoots().forEach(t -> addRoleNamespace(roleNamesByTarget, t, roles.get(0)));
            }
        }
        for (ClassSymbol classSymbol : state.symbolsInBuildOrder().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast).toList()) {
            checkWork();
            Set<SymbolId> chain = new LinkedHashSet<>();
            SymbolId current = classSymbol.id();
            while (current != null && chain.add(current)) {
                checkWork();
                current = state.baseOf(current);
            }
            Map<String, DeclInfo> attributes = new LinkedHashMap<>();
            for (SymbolId ancestor : chain) for (DeclInfo member : index.children(ancestor)) {
                checkWork();
                if (member.kind() == SymbolKind.ATTRIBUTE) attributes.putIfAbsent(member.name(), member);
            }
            for (SymbolId classId : chain) {
                checkWork();
                Map<String, DeclInfo> names = roleNamesByTarget.get(classId);
                if (names == null) {
                    continue;
                }
                for (DeclInfo memberDecl : attributes.values()) {
                    checkWork();
                    DeclInfo role = names.get(memberDecl.name());
                    if (role == null) continue;
                    DeclInfo association = role.owner().flatMap(index::byId).orElse(null);
                    DeclInfo attributeOwner = memberDecl.owner().flatMap(index::byId).orElse(null);
                    // A later association cannot retroactively add an inherited name to
                    // an already declared subclass. The direct target still owns its
                    // own namespace and is checked regardless of declaration order.
                    if (!memberDecl.owner().filter(classId::equals).isPresent()
                            && association != null && attributeOwner != null
                            && association.sourceId().equals(attributeOwner.sourceId())
                            && association.syntax().range().startOffset() > attributeOwner.syntax().range().startOffset()) continue;
                    {
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
            SymbolId firstTopic = paths.scopeRoot(role.id());
            SymbolId secondTopic = paths.scopeRoot(previous.id());
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


    private void buildModel(IndexedDeclarations.ModelEntry entry) {
        DeclInfo decl = entry.declaration();
        guru.interlis.syntax.ModelSyntax modelSyntax = entry.model().syntax();
        try {
            String issuer = modelSyntax.issuer();
            if (issuer.length() > 1023 || issuer.chars().anyMatch(c -> c > 127))
                throw new java.net.URISyntaxException(issuer, "issuer outside URI character/length profile");
            new java.net.URI(issuer);
        } catch (java.net.URISyntaxException invalid) {
            emitRule(decl, "model issuer must be an ASCII URI reference of at most 1023 characters", "MODEL_ISSUER");
        }
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
                if (Set.of(SymbolKind.TOPIC, SymbolKind.PARAMETER).contains(child.kind())
                        || child.kind() == SymbolKind.METADATA_BASKET
                        && (!(child.syntax() instanceof guru.interlis.syntax.MetadataBasketDeclarationSyntax basket)
                            || basket.basketKind()!=guru.interlis.model.MetadataBasketSymbol.Kind.REFSYSTEM)
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
            if (site.modelName().equals("INTERLIS")) target = PredefinedModels.ensure(state, entry.declaration().language());
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
        translation=overlays.translation(decl.id(),translation);
        state.defineSymbol(new ModelSymbol(header(decl), syntax.modelKind(),
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
        for (var symbol : List.copyOf(state.symbolsInBuildOrder())) {
            if (!(symbol instanceof ModelSymbol model) || model.translation().isEmpty()) continue;
            Set<SymbolId> seen = new HashSet<>();
            ModelSymbol original = model;
            while (original.translation().flatMap(guru.interlis.model.ModelTranslation::sourceTarget).isPresent()
                    && seen.add(original.id())) {
                checkWork();
                var next = state.symbol(original.translation().orElseThrow().sourceTarget().orElseThrow());
                if (!(next instanceof ModelSymbol base)) break;
                original = base;
            }
            String namespace = original.transferNamespace();
            if (model.options().xmlNamespace().filter(value -> !value.value().equals(namespace)).isPresent())
                emitRule(index.byId(model.id()).orElseThrow(), "translated XMLNS conflicts with the original transfer namespace", "TRANSLATION_XMLNS");
            state.replaceSymbol(new ModelSymbol(model.header(), model.modelKind(), model.contracted(), model.modelVersion(),
                    model.issuer(), model.imports(), model.members(), model.translation(), model.options(), namespace));
        }
    }

    Symbol buildSymbol(DeclInfo decl) {
        checkWork();
        Symbol existing = state.symbol(decl.id());
        if (existing != null) {
            return existing;
        }
        Symbol symbol = switch (decl.kind()) {
            case TOPIC -> buildTopic(decl);
            case CLASS -> buildClass(decl);
            case STRUCTURE -> buildStructure(decl);
            case ASSOCIATION -> buildAssociation(decl);
            case DOMAIN -> buildDomain(decl);
            case UNIT -> units.buildUnit(decl);
            case LINE_FORM -> lines.buildForm(decl);
            case METADATA_BASKET -> metadata.basket(decl);
            case METAOBJECT -> metadata.object(decl);
            case FUNCTION -> buildFunction(decl);
            case PARAMETER -> buildParameter(decl);
            case CONTEXT -> generics.buildContext(decl);
            case ATTRIBUTE -> buildAttribute(decl);
            case ROLE -> buildRole(decl);
            case CONSTRAINT -> buildConstraint(decl);
            case ENUM_ELEMENT -> buildEnumElement(decl);
            case UNSUPPORTED -> buildUnsupported(decl);
            case GRAPHIC -> graphics.build(decl);
            case DRAWING_RULE -> {
                buildSymbol(index.byId(decl.owner().orElseThrow()).orElseThrow());
                yield state.symbol(decl.id());
            }
            case VIEW -> buildView(decl);
            case VIEW_BASE -> views.buildBase(decl);
            case MODEL -> throw new IllegalStateException("model symbols are built separately");
        };
        // A specialized binder can publish a provisional or shared declaration during dispatch.
        if (state.hasSymbol(symbol.id())) state.replaceSymbol(symbol);
        else state.defineSymbol(symbol);
        return symbol;
    }

    private void validateTopicInheritanceCycles() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            checkWork();
            if (symbol instanceof TopicSymbol && hasCycle(baseChain(symbol.id())))
                index.byId(symbol.id()).ifPresent(this::emitCycle);
        }
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
            Optional<SymbolId> resolved = names.tryResolve(decl, name, Set.of(SymbolKind.TOPIC));
            if (resolved.isEmpty()) {
                List<SymbolId> matches = state.symbolsInBuildOrder().stream()
                        .filter(TopicSymbol.class::isInstance).map(TopicSymbol.class::cast)
                        .filter(topic -> topic.name().value().equals(name.text()))
                        .map(TopicSymbol::id).toList();
                if (matches.size() == 1) {
                    return Optional.of(matches.getFirst());
                }
            }
            return resolved;
        });
        base.ifPresent(id -> state.bindBase(decl.id(), id));
        if (base.filter(this::isFinalDeclaration).isPresent())
            emitRule(decl, "cannot extend FINAL topic", "TOPIC_FINAL");
        if (base.isPresent() && state.symbol(base.orElseThrow()) instanceof TopicSymbol baseTopic) {
            boolean abstractBase = baseTopic.header().modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT);
            for (SymbolId member : baseTopic.members()) {
                checkWork();
                if (state.symbol(member) instanceof ClassSymbol baseClass
                        && baseClass.header().modifiers()
                        .contains(guru.interlis.model.Modifier.ABSTRACT)) {
                    abstractBase = true;
                }
            }
            Set<String> baseDomains = new LinkedHashSet<>();
            for (SymbolId member : baseTopic.members()) {
                checkWork();
                if (state.symbol(member) instanceof DomainSymbol domain) {
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
                        || !(state.symbol(child.id()) instanceof ClassSymbol derivedClass)
                        || derivedClass.baseType().isEmpty()) {
                    continue;
                }
                for (SymbolId member : baseTopic.members()) {
                    checkWork();
                    if (!(state.symbol(member) instanceof ClassSymbol baseClass)
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
            references.recordReference(decl, dependency, resolved.orElse(null), ReferenceUsage.TYPE_USE);
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
        for (var name : syntax.deferredGenerics()) names.bindTarget(decl, name, Set.of(SymbolKind.DOMAIN),
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
                TypeId typeId = state.declaredType(member);
                if (decl.language() != guru.interlis.model.LanguageVersion.ILI24
                        && typeId != null && state.type(typeId) instanceof ReferenceType) {
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
                || !resolvedObjectBases.add(owner)) return Optional.ofNullable(state.baseOf(owner));
        Optional<guru.interlis.syntax.NameSyntax> name = decl.syntax() instanceof ClassDeclarationSyntax c
                ? c.baseType() : ((StructureDeclarationSyntax) decl.syntax()).baseType();
        if (name.isPresent() && decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED))
            emitRule(decl, "EXTENDED and EXTENDS cannot be combined in one declaration", "EXTENDED_EXPLICIT_BASE");
        Set<SymbolKind> kinds = decl.kind() == SymbolKind.CLASS
                ? Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE) : Set.of(SymbolKind.STRUCTURE);
        Optional<SymbolId> base=name.flatMap(n->names.bindTarget(decl,n,kinds,ReferenceUsage.BASE_TYPE,new ArrayList<>()));

        if (base.isEmpty() && decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
            base = baseTopicMember(owner, decl.name(), Set.of(decl.kind()));
            if (base.isEmpty()) emitRule(decl, "EXTENDED needs a same-named inherited declaration", "EXTENDED_BASE");
        }
        base.ifPresent(id -> {
            state.bindBase(owner, id);
            if (isFinalDeclaration(id))
                emitRule(decl, "cannot extend FINAL object type", "OBJECT_FINAL");
        });
        return base;
    }

    /** Declaration metadata is available before an imported base is built. */
    private boolean isFinalDeclaration(SymbolId id) {
        return index.byId(id).map(info -> info.modifiers().contains(guru.interlis.model.Modifier.FINAL))
                .orElseGet(() -> state.hasSymbol(id) && state.symbol(id).header().has(guru.interlis.model.Modifier.FINAL));
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
                state.bindBase(decl.id(), parent);
                if (state.symbol(parent).header().modifiers().contains(guru.interlis.model.Modifier.FINAL)) {
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
        Cardinality inheritedCardinality = base.map(state::symbol).map(AssociationSymbol.class::cast)
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
        TypeId type = types.buildType(syntax.type(), decl.id(), decl.sourceId());
        if (index.byId(decl.owner().orElseThrow()).orElseThrow().kind() == SymbolKind.MODEL && containsArea(type, new HashSet<>()))
            emitRule(decl, "runtime parameters cannot contain AREA values", "PARAMETER_AREA");
        state.bindDeclaredType(decl.id(), type);
        return new guru.interlis.model.ParameterSymbol(header(decl), type, syntax.mandatory() || types.mandatoryType(type), Optional.empty());
    }

    private boolean containsArea(TypeId type, Set<TypeId> seen) {
        if (!seen.add(type)) return false;
        TypeDescriptor descriptor = types.effectiveDescriptor(type);
        if (descriptor instanceof GeometryType geometry)
            return geometry.geometryKind() == guru.interlis.model.GeometryKind.AREA
                    || geometry.geometryKind() == guru.interlis.model.GeometryKind.MULTIAREA;
        if (descriptor instanceof CollectionType collection) return containsArea(collection.elementType(), seen);
        if (descriptor instanceof StructureValueType structure) {
            for(SymbolId root:structure.targetRoots()) {
                Set<SymbolId> visited=new HashSet<>();
                for(SymbolId current=root;current!=null && visited.add(current);current=objectInheritanceBase(current).orElse(null)) {
                    checkWork();
                    if(state.symbol(current) instanceof StructureSymbol known) {
                        for(SymbolId member:known.members())
                            if(state.symbol(member) instanceof AttributeSymbol attribute && containsArea(attribute.declaredType(),seen))return true;
                    } else for(var child:index.children(current))
                        if(child.kind()==SymbolKind.ATTRIBUTE && containsArea(types.attributeType(child),seen))return true;
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
                .mapToObj(i -> new FunctionParameter(syntax.parameters().get(i).name(), types.functionArgumentType(decl, i), syntax.parameters().get(i).mandatory() || types.mandatoryType(types.functionArgumentType(decl,i)))).toList();
        TypeId result=types.buildType(syntax.returnType(), decl.id(), decl.sourceId());
        return new FunctionSymbol(header(decl), parameters,result,syntax.returnMandatory() || types.mandatoryType(result),syntax.explanation().map(e->new Documentation(e.text(),List.of(e.range()))).orElseGet(Documentation::empty));
    }


    private DomainSymbol buildDomain(DeclInfo decl) {
        TypeId type = types.domainType(decl);
        Set<String> constraintNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            if (child.kind() == SymbolKind.CONSTRAINT && !constraintNames.add(child.name())) {
                emitRule(child, "domain constraint names must be unique", "DOMAIN_CONSTRAINT_NAME");
            }
            buildSymbol(child);
        }
        return new DomainSymbol(header(decl), type, Optional.ofNullable(state.baseOf(decl.id())),
                decl.modifiers().contains(guru.interlis.model.Modifier.GENERIC));
    }


    private AttributeSymbol buildAttribute(DeclInfo decl) {
        AttributeDeclarationSyntax syntax = (AttributeDeclarationSyntax) decl.syntax();
        TypeId type = types.attributeType(decl);
        for(var child:index.children(decl.id()))if(child.kind()==SymbolKind.ENUM_ELEMENT)buildSymbol(child);
        if (syntax.transientAttribute() && !syntax.assigned()
                && !syntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            emitRule(decl, "transient attribute requires an assignment", "TRANSIENT_ASSIGNMENT");
        }
        return new AttributeSymbol(header(decl), type, syntax.mandatory() || types.mandatoryType(type),
                syntax.transientAttribute(), syntax.continuous(), syntax.subdivision(),
                Optional.empty(), bindAttributeDerivations(decl, syntax, type));
    }


    private List<ExpressionId> bindAttributeDerivations(DeclInfo decl, AttributeDeclarationSyntax syntax, TypeId type) {
        List<ExpressionId> inferred = types.inferredDerivations(decl.id());
        if (inferred != null) return inferred;
        List<ExpressionId> result = new ArrayList<>();
        for (var expression : syntax.derivations()) {
            var bound = expressions.contextualValue(expressions.buildExpression(expression, decl.owner().orElseThrow()),type,decl.id());
            result.add(bound.id());
            if(!bound.error() && (syntax.mandatory() || types.mandatoryType(type))
                    && guru.interlis.model.internal.TypeCompatibility.isUndefined(types.effectiveDescriptor(bound.typeId())))
                emitRule(decl,"MANDATORY attribute excludes an explicitly undefined derived value","MANDATORY_VALUE",expression.range());
            if (!bound.error() && !types.assignable(bound.typeId(), type))
                emitTypeMismatch(decl.sourceId(), expression.range(), types.typeName(bound.typeId()), types.typeName(type));
        }
        return List.copyOf(result);
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
        SymbolId associationBase = state.baseOf(decl.owner().orElseThrow());
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
            if (inherited.ordered() && !decl.modifiers().contains(guru.interlis.model.Modifier.ORDERED)) {
                emitRule(decl, "an ordered role cannot become unordered in an extension", "ROLE_ORDER_EXTENSION");
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
            var bound = expressions.buildExpression(expression, decl.owner().orElseThrow());
            var actual = types.effectiveDescriptor(bound.typeId());
            if (!bound.error()) {
                var roots = targets.stream().flatMap(t -> t.allowedRoots().stream()).toList();
                var expected = new guru.interlis.model.ObjectArgumentType(types.nextTypeId(), decl.origin(), Optional.empty(), roots, false);
                state.storeType(expected);
                if (!types.assignable(bound.typeId(), expected.id())) emitTypeMismatch(decl.sourceId(), expression.range(), types.typeName(bound.typeId()), "single role target");
            }
            return bound.id();
        });
    }

    List<RoleSymbol> associationRoles(SymbolId association) {
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
                || guru.interlis.model.internal.TypeCompatibility.specializes(candidate, base, state::baseOf);
    }

    boolean roleAccepts(RoleSymbol role, SymbolId candidate) {
        return role.targetRoots().stream().anyMatch(base -> targetSpecializes(candidate, base));
    }

    SymbolId bindObjectTarget(DeclInfo decl, guru.interlis.syntax.NameSyntax name, ReferenceUsage usage) {
        Optional<SymbolId> target = name.text().equals("ANYCLASS") ? Optional.of(anyClass(decl))
                : resolver.resolve(decl.id(), name, Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION)).target();
        if (target.isEmpty() && name.segments().size() == 1) {
            target = baseTopicMember(decl.id(), name.text(), Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
        }
        if (target.isEmpty()) unresolved(name);
        references.recordReference(decl, name, target.orElse(null), usage);
        return target.orElseGet(() -> syntheticClass(decl, name));
    }

    private SymbolId bindRoleTarget(DeclInfo decl, guru.interlis.syntax.NameSyntax name) {
        var target=bindObjectTarget(decl, name, ReferenceUsage.ROLE_TARGET);
        return target;
    }

    List<SymbolId> topicDependencies(SymbolId topic) {
        Set<SymbolId> result = new LinkedHashSet<>();
        for (SymbolId id : baseChain(topic)) {
            checkWork();
            if (state.symbol(id) instanceof TopicSymbol owner) result.addAll(owner.dependencies());
        }
        return List.copyOf(result);
    }

    private void validateTopicDependencies() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
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
        SymbolId targetTopic = paths.scopeRoot(target);
        if (!(state.symbol(sourceTopic) instanceof TopicSymbol)
                || !(state.symbol(targetTopic) instanceof TopicSymbol)
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
            current = state.symbol(current) instanceof TopicSymbol topic
                    ? topic.baseTopic().orElse(null) : null;
        }
        current = second;
        guard = 0;
        while (current != null && guard++ < 32) {
            checkWork();
            if (firstBases.contains(current)) {
                return true;
            }
            current = state.symbol(current) instanceof TopicSymbol topic
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
            Symbol symbol = state.symbol(current);
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
        if (!state.hasSymbol(id)) {
            state.defineSymbol(new ClassSymbol(new SymbolHeader(id, SymbolKind.CLASS,
                    DeclaredName.declared("ANYCLASS"), Optional.of(model), 0,
                    new Origin.PredefinedOrigin(owner.language(), "INTERLIS.ANYCLASS"), Optional.empty(),
                    owner.language(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of()),
                    Optional.empty(), List.of(), List.of()));
        }
        return id;
    }

    Optional<SymbolId> objectBase(SymbolId target) {
        Symbol symbol = state.symbol(target);
        return symbol != null && symbol.origin() instanceof Origin.PredefinedOrigin predefined
                && predefined.key().equals("INTERLIS.ANYCLASS") ? Optional.empty() : Optional.of(target);
    }

    private SymbolId syntheticClass(DeclInfo owner, guru.interlis.syntax.NameSyntax name) {
        SymbolId id = new SymbolId("sym-unresolved-" + state.nextTypeSequence());
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
        state.defineSymbol(synthetic);
        return id;
    }

    private ConstraintSymbol buildConstraint(DeclInfo decl) {
        ConstraintDeclarationSyntax syntax;
        Optional<SymbolId> externalTarget = Optional.empty();
        if (decl.syntax() instanceof ExternalConstraintSyntax external) {
            syntax = external.constraint();
            externalTarget = names.tryResolve(decl, external.target(),
                    Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
            if (externalTarget.isEmpty() && external.target().segments().size() == 1) {
                externalTarget = baseTopicMember(decl.id(), external.target().text(),
                        Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
            }
            if (externalTarget.isEmpty()) {
                unresolved(external.target());
            } else {
                SymbolId target = externalTarget.orElseThrow();
                references.recordReference(decl, external.target(), target, ReferenceUsage.EXPRESSION);
                boolean inherited = baseTopicMember(decl.id(),
                        index.byId(target).orElseThrow().name(),
                        Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION)).filter(target::equals).isPresent();
                if (!Objects.equals(paths.scopeRoot(decl.id()), paths.scopeRoot(target)) && !inherited) {
                    emitRule(decl, "external constraints must be declared in the target's topic",
                            "CONSTRAINT_TARGET_TOPIC");
                }
            }
        } else {
            syntax = (ConstraintDeclarationSyntax) decl.syntax();
        }
        externalTarget.ifPresent(target -> externalConstraintTargets.put(decl.id(), target));
        SymbolId context = paths.contextualObject(expressions.expressionContext(decl.id()), paths.scopeRoot(decl.id()));
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
                references.recordReference(decl, target.viewable(), resolved.orElse(null), ReferenceUsage.EXPRESSION);
                if (resolved.isEmpty()) { unresolved(target.viewable()); continue; }
                SymbolId targetScope = resolved.orElseThrow();
                SymbolId ownerTopic = paths.scopeRoot(decl.id());
                SymbolId targetTopic = paths.scopeRoot(targetScope);
                boolean depends = ownerTopic == null || targetTopic == null
                        || index.byId(ownerTopic).map(d -> d.kind() != SymbolKind.TOPIC).orElse(true)
                        || index.byId(targetTopic).map(d -> d.kind() != SymbolKind.TOPIC).orElse(true)
                        || paths.topicAvailable(ownerTopic, targetTopic)
                        || baseChain(ownerTopic).stream().map(index::byId).flatMap(Optional::stream)
                        .filter(d -> d.syntax() instanceof TopicSyntax)
                        .anyMatch(d -> ((TopicSyntax) d.syntax()).dependencies().stream()
                                .anyMatch(n -> resolver.resolve(d.id(), n, Set.of(SymbolKind.TOPIC)).target()
                                        .filter(targetTopic::equals).isPresent()));
                if (!depends) emitRule(decl, "EXISTENCE target topic requires DEPENDS ON", "EXISTENCE_DEPENDS");
                ExpressionResult path = constraintPath(target.path(), decl, targetScope, false);
                requireConstraintValue(decl, path, false);
                if (!source.error() && !path.error() && !existenceCompatible(source.typeId(), path.typeId())) {
                    TypeDescriptor from = types.effectiveDescriptor(source.typeId()), to = types.effectiveDescriptor(path.typeId());
                    boolean familyPending = from.kind() == to.kind() && switch (from.kind()) {
                        case GENERIC, DEFERRED, PENDING -> true;
                        default -> false;
                    };
                    if (familyPending) emitUnsupported(decl.sourceId(), target.path().range(),
                            "EXISTENCE_TYPE_COMPATIBILITY", "EXISTENCE compatibility for " + from.kind());
                    else emitTypeMismatch(decl.sourceId(), target.path().range(), types.typeName(source.typeId()), types.typeName(path.typeId()));
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
                ExpressionResult condition = expressions.buildExpression(e, decl.id());
                if (!condition.error() && !types.isBoolean(condition.typeId()))
                    emitTypeMismatch(decl.sourceId(), e.range(), types.typeName(condition.typeId()), "BOOLEAN");
                return condition.id();
            });
            List<ExpressionId> keys = new ArrayList<>();
            List<UniquenessConstraint.LocalUniqueness> localKeys = new ArrayList<>();
            if (syntax.localPrefix().isPresent()) {
                if (syntax.basket()) emitRule(decl, "UNIQUE cannot combine BASKET and LOCAL", "UNIQUE_KEY");
                ExpressionResult prefix = constraintPath(syntax.localPrefix().orElseThrow(), decl, context, true);
                SymbolId structure = structures.target(types.effectiveDescriptor(prefix.typeId()));
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
            var condition=expressions.buildExpression(e,decl.id());
            if(!condition.error() && !types.isBoolean(condition.typeId()))emitTypeMismatch(decl.sourceId(),e.range(),types.typeName(condition.typeId()),"BOOLEAN");
            return condition.id();
        });
        if(syntax.constraintKind()==guru.interlis.syntax.ConstraintKind.PLAUSIBILITY && syntax.plausibility().isEmpty()) {
            var error=missingConstraintExpression(decl,syntax.range());
            return new ConstraintSymbol(header(decl),new MandatoryConstraint(syntax.name(),error.id()),externalTarget);
        }
        SymbolId expressionOwner = decl.id();
        ExpressionResult expression = syntax.expression()
                .map(e -> expressions.buildExpression(e, expressionOwner))
                .orElseGet(() -> missingConstraintExpression(decl,syntax.range()));
        if (!expression.error() && !types.isBoolean(expression.typeId())) {
            emitTypeMismatch(decl.sourceId(), syntax.range(), types.typeName(expression.typeId()),
                    "BOOLEAN");
        }
        if (!expression.error() && types.isBoolean(expression.typeId()) && syntax.constraintKind()==guru.interlis.syntax.ConstraintKind.SET
                && decl.language()==guru.interlis.model.LanguageVersion.ILI24 && !hasObjectSetFunction(expression.id())) {
            var cause=emitTypeMismatch(decl.sourceId(),state.expression(expression.id()).range(),
                    "SET condition without OBJECTS function","SET condition with OBJECTS function");
            expression=expressions.failedFunctionArgument(state.expression(expression.id()).range(),List.of(cause));
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

    /** 2.4 requires an actual call with an OBJECTS formal somewhere in the set
     * condition; an ALL value or such a call only in WHERE does not suffice. */
    private boolean hasObjectSetFunction(ExpressionId root) {
        var pending=new java.util.ArrayDeque<ExpressionId>();var seen=new java.util.HashSet<ExpressionId>();pending.add(root);
        while(!pending.isEmpty()) {
            checkWork();var id=pending.removeFirst();if(!seen.add(id))continue;var expression=state.expression(id);
            if(expression instanceof guru.interlis.model.FunctionCallExpression call
                    && call.targetFunction().map(state::symbol).filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast)
                    .stream().flatMap(f->f.parameters().stream()).map(p->types.effectiveDescriptor(p.typeId()))
                    .anyMatch(t->t instanceof guru.interlis.model.ObjectArgumentType objects&&objects.objects()))return true;
            pending.addAll(expression.children());
        }
        return false;
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
            return paths.pathExpression(state.nextExpressionId(), path, owner.id(), start, localPrefix);
        return expressions.buildExpression(syntax, owner.id()); // Unsupported operators keep their explicit diagnostic.
    }

    private void requireConstraintValue(DeclInfo owner, ExpressionResult result, boolean unique) {
        if (result.error()) return;
        TypedExpression expression = state.expression(result.id());
        TypeDescriptor type = types.effectiveDescriptor(result.typeId());
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
        TypeDescriptor actual = types.effectiveDescriptor(source);
        TypeDescriptor expected = types.effectiveDescriptor(target);
        if (actual instanceof CollectionType a && expected instanceof CollectionType b)
            return a.collectionKind() == b.collectionKind() && existenceCompatible(a.elementType(), b.elementType());
        if (actual instanceof CollectionType a && !paths.isMany(a.cardinality())) actual = types.effectiveDescriptor(a.elementType());
        if (expected instanceof CollectionType b && !paths.isMany(b.cardinality())) expected = types.effectiveDescriptor(b.elementType());
        if (actual == null || expected == null) return false;
        if (actual instanceof guru.interlis.model.OidType a && expected instanceof guru.interlis.model.OidType b)
            return a.oidKind() == guru.interlis.model.OidKind.ANY || b.oidKind() == guru.interlis.model.OidKind.ANY
                    || a.oidKind() == b.oidKind() && (a.valueType().isEmpty() && b.valueType().isEmpty()
                    || a.valueType().isPresent() && b.valueType().isPresent()
                    && existenceCompatible(a.valueType().orElseThrow(), b.valueType().orElseThrow()));
        if (actual.id().equals(expected.id())) return true;
        if (actual instanceof GeometryType geometry && expected instanceof CoordinateType) {
            actual = geometry.vertexDomain().map(state::declaredType).map(types::effectiveDescriptor).orElse(null);
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
            return guru.interlis.model.internal.TypeCompatibility.relatedEnumerations(a, b, types::effectiveDescriptor) || a.ordered() == b.ordered() && a.circular() == b.circular()
                    && a.values().stream().map(EnumerationValue::path).toList()
                    .equals(b.values().stream().map(EnumerationValue::path).toList());
        return guru.interlis.model.internal.TypeCompatibility.assign(actual, expected,
                types::effectiveDescriptor, state::baseOf, domain -> types.effectiveDescriptor(state.declaredType(domain))).decision() == guru.interlis.model.RelationDecision.ALLOWED;
    }

    private boolean numericValueCompatible(NumericRange a, NumericRange b) {
        return a.unit().equals(b.unit()) && a.referenceSystem().equals(b.referenceSystem())
                && a.direction() == b.direction() && a.circular() == b.circular();
    }

    private EnumElementSymbol buildEnumElement(DeclInfo decl) {
        String path = decl.name();
        List<String> segments = List.of(path.split("\\."));
        TypeDescriptor type = decl.owner().map(state::declaredType).map(types::effectiveDescriptor).orElse(null);
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
        diagnostics.recordUnsupported(decl.sourceId(), feature);
        emitUnsupported(decl.sourceId(), decl.origin() instanceof Origin.DeclaredOrigin d
                ? d.range() : null, feature, decl.name());
        return new UnsupportedSymbol(header(decl), feature, decl.name());
    }

    private final ViewBuilder views = new ViewBuilder(this, state);

    private ViewSymbol buildView(DeclInfo decl) { return views.build(decl); }


    private void validateReferenceTargets() {
        for (var entry : state.effectiveMembersInBuildOrder()) {
            checkWork();
            SymbolId sourceOwner = entry.getKey();
            while (sourceOwner != null && index.byId(sourceOwner).isEmpty()) {
                checkWork();
                sourceOwner = state.symbol(sourceOwner).owner().orElse(null);
            }
            if (sourceOwner == null) continue; // Predefined declarations are checked at their source uses.
            DeclInfo owner = index.byId(sourceOwner).orElseThrow();
            SymbolId topic = paths.scopeRoot(owner.id());
            Set<SymbolId> seen = new HashSet<>();
            var pending = new java.util.ArrayDeque<SymbolId>();
            pending.add(entry.getKey());
            while (!pending.isEmpty()) {
                checkWork();
                SymbolId container = pending.removeFirst();
                if (!seen.add(container)) continue;
                for (EffectiveMember member : state.effectiveMembers(container)) {
                    checkWork();
                    Symbol declaration = state.symbol(member.declaredSymbol());
                    List<SymbolId> roots = List.of();
                    boolean external = false;
                    if (declaration instanceof RoleSymbol role) {
                        roots = role.targetRoots(); external = role.external();
                    }
                    else if (declaration instanceof AttributeSymbol attribute) {
                        TypeDescriptor type = types.effectiveDescriptor(attribute.declaredType());
                        while (type instanceof CollectionType collection) {
                            checkWork(); type = types.effectiveDescriptor(collection.elementType());
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
                        Symbol target = state.symbol(paths.contextualObject(root, topic));
                        if (target == null || index.byId(target.id()).isEmpty()) continue; // ANYCLASS/unresolved: no invented OID.
                        validateReferenceScope(site, topic, target.id(), external,
                                declaration instanceof RoleSymbol ? "ROLE_EXTERNAL" : "REFERENCE_EXTERNAL");
                        boolean objectOid = target.header().oidRules().stream().anyMatch(r -> !r.basket() && oid.oidState(r) != 0);
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
        var configured=overlays.metadata(decl.id());
        if(!configured.isEmpty()) {
            var combined=new ArrayList<MetaAttributeSet.Entry>(configured);combined.addAll(entries);entries=combined;values.clear();
            for(var entry:entries)values.putIfAbsent(entry.key(),entry.value());
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
        if (decl.kind() == SymbolKind.DOMAIN && types.mandatoryDomain(decl.id())) modifiers.add(guru.interlis.model.Modifier.MANDATORY);
        return new SymbolHeader(decl.id(), decl.kind(), declaredName, decl.owner(),
                decl.order(), decl.origin(), decl.nameRange(), decl.language(),
                documentation, new MetaAttributeSet(values,entries), modifiers, List.of());
    }

    private static boolean isSyntheticName(String name) {
        return name.startsWith("<") || name.startsWith("constraint@")
                || name.startsWith("unsupported@") || name.startsWith("Association@");
    }

    // ---- types -----------------------------------------------------------------


    Optional<SymbolId> bindObjectTarget(DeclInfo owner,
            guru.interlis.syntax.NameSyntax name, List<DiagnosticId> errors) {
        return names.bindTarget(owner, name, Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW), ReferenceUsage.TYPE_USE, errors);
    }


    // ---- expressions -----------------------------------------------------------




    /** A missing parsed condition is a source recovery error, never an implementation gap. */
    private ExpressionResult missingConstraintExpression(DeclInfo owner,SourceRange range) {
        var causes=diagnostics.syntaxErrors().stream().filter(d->d.primaryRange().filter(r->r.source().equals(range.source())
                && r.revision().equals(range.revision()) && r.startOffset()<=range.endOffset() && r.endOffset()>=range.startOffset()).isPresent())
                .map(guru.interlis.diagnostics.Diagnostic::id).toList();
        if(causes.isEmpty())causes=List.of(emitRule(owner,"constraint syntax is incomplete","SOURCE_MISSING_CONSTRAINT",range));
        return expressions.failedValueExpression(state.nextExpressionId(),range,causes);
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
            if (decl.kind() != SymbolKind.ASSOCIATION) metadata.ensureBases(decl.id());
            List<SymbolId> chain = baseChain(decl.id());
            if (hasCycle(chain)) {
                emitCycle(decl);
                state.setEffectiveMembers(decl.id(), localMembers(decl));
                return;
            }
            List<EffectiveMember> result = new ArrayList<>();
            Map<String, Integer> positions = new HashMap<>();
            for (int depth = chain.size() - 1; depth >= 0; depth--) {
                checkWork();
                SymbolId current = chain.get(depth);
                if (state.symbol(current) != null && state.symbol(current).origin() instanceof Origin.PredefinedOrigin) {
                    var predefined = state.symbol(current);
                    var members = predefined instanceof StructureSymbol s ? s.members() : predefined instanceof ClassSymbol c ? c.members() : List.<SymbolId>of();
                    for (SymbolId member : members) {
                        if (state.symbol(member).kind() != SymbolKind.ATTRIBUTE) continue;
                        String name = state.symbol(member).name().value();
                        Integer existing = positions.get(name);
                        int position = existing == null ? result.size() : existing;
                        var effective = new EffectiveMember(member, decl.id(), pathTo(chain, depth), position,
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
                    buildSymbol(attribute);
                    Integer existing = positions.get(attribute.name());
                    if (existing != null) {
                        if (depth == 0 && !attribute.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                            emitRule(attribute, "override requires EXTENDED", "OVERRIDE");
                        }
                        EffectiveMember old = result.get(existing);
                        if (depth == 0 && attribute.kind() == SymbolKind.ATTRIBUTE && isFinalDeclaration(old.declaredSymbol()))
                            emitRule(attribute, "cannot override FINAL attribute", "ATTRIBUTE_FINAL");
                        result.set(existing, new EffectiveMember(attribute.id(), decl.id(),
                                pathTo(chain, depth), existing,
                                depth == 0 ? EffectiveMemberOrigin.OVERRIDDEN : EffectiveMemberOrigin.INHERITED, true));
                        AttributeSymbol override = attribute.kind() == SymbolKind.ATTRIBUTE
                                ? overrideAttribute(attribute.id(), old.declaredSymbol()) : null;
                        if (override != null) {
                            state.replaceSymbol(override);
                        }
                    } else {
                        if (depth == 0 && attribute.kind() == SymbolKind.ATTRIBUTE
                                && attribute.modifiers().contains(guru.interlis.model.Modifier.EXTENDED))
                            emitRule(attribute, "EXTENDED attribute needs an inherited attribute", "EXTENDED_BASE");
                        int position = result.size();
                        positions.put(attribute.name(), position);
                        result.add(new EffectiveMember(attribute.id(), decl.id(),
                                pathTo(chain, depth), position,
                                depth == 0 ? EffectiveMemberOrigin.LOCAL
                                        : EffectiveMemberOrigin.INHERITED, false));
                    }
                }
            }
            state.setEffectiveMembers(decl.id(), List.copyOf(result));
        }
        for (DeclInfo child : index.children(decl.id())) {
            checkWork();
            collectEffective(child);
        }
    }


    private AttributeSymbol overrideAttribute(SymbolId id, SymbolId baseId) {
        if (state.symbol(id) instanceof AttributeSymbol attribute) {
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
            current = state.baseOf(current);
        }
        return chain;
    }

    private boolean hasCycle(List<SymbolId> chain) {
        return chain.size() != new HashSet<>(chain).size();
    }

    private void emitCycle(DeclInfo decl) {
        diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0006"), Severity.ERROR,
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

    private List<SymbolId> pathTo(List<SymbolId> chain, int depth) {
        List<SymbolId> result = new ArrayList<>();
        for (int i = depth; i >= 0; i--) {
            checkWork();
            result.add(chain.get(i));
        }
        return List.copyOf(result);
    }


    // ---- references and coverage ----------------------------------------------

    SymbolId externalConstraintTarget(SymbolId constraint, SymbolId fallback) {
        return externalConstraintTargets.getOrDefault(constraint, fallback);
    }

    private AnalysisCoverage buildCoverage() {
        List<AnalysisCoverage.CoverageEntry> entries = new ArrayList<>();
        List<guru.interlis.diagnostics.Diagnostic> relevantDiagnostics = diagnostics.snapshot().stream()
                .filter(d -> d.phase() == Phase.LEXING || d.phase() == Phase.PARSING
                        || d.primaryRange().map(closure::contains).orElse(true)).toList();
        for (SourceId source : closure.sources()) {
            checkWork();
            boolean lexing = relevantDiagnostics.stream().noneMatch(d -> d.primaryRange()
                    .filter(r -> r.source().equals(source)).isPresent()
                    && d.code().value().equals("ILI-SYN-0001"));
            boolean parsing = relevantDiagnostics.stream().noneMatch(d -> d.primaryRange()
                    .filter(r -> r.source().equals(source)).isPresent()
                    && d.code().value().matches("ILI-SYN-000[2-6]"));
            boolean indexing = relevantDiagnostics.stream().noneMatch(d -> d.code().value().equals("ILI-SEM-0003")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean binding = relevantDiagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-000[124]")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean typeChecking = relevantDiagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-000[578]")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean semantic = relevantDiagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-0006|ILI-SEM-0010")
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
        for (var diagnostic : relevantDiagnostics) {
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

    static String unquote(String text) {
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


}
