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
    final DeclarationBindings declarations;
    final InheritanceBindings inheritance;
    final ConstraintBindings constraints;
    final TopicBindings topics;
    final ExpressionBindings expressions;
    final ObjectPathBindings paths;
    final TypeBindings types;
    final UnitBindings units;
    final OidBindings oid;
    final FormattedTypeBindings formats;
    final GenericBindings generics;
    private final SemanticDiagnostics diagnostics;
    private final Map<SourceId, Set<String>> syntaxUnsupportedBySource;

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
        this.declarations = new DeclarationBindings(this, state, work, index, diagnostics, closure, overlays, views);
        this.inheritance = new InheritanceBindings(this, state, work, index, diagnostics, views);
        this.constraints = new ConstraintBindings(this, state, work, index, diagnostics);
        this.topics = new TopicBindings(this, state, work, index, diagnostics);
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
        for (var _ : index.models()) {
            checkWork();
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
                declarations.buildSymbol(child);
            }
        }
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            checkWork();
            declarations.buildModel(entry);
        }
        inheritance.validateTopicInheritanceCycles();
        units.validateUnits();
        inheritance.resolveOverridesAndEffectiveMembers();
        structures.validate();
        lines.validateConcreteMembers();
        metadata.validate();
        generics.validateGenericSystem();
        oid.bindOidRules();
        topics.validateTopicDependencies();
        views.validateDependencies();
        graphics.validateDependencies();
        topics.validateReferenceTargets();
        types.buildEffectiveTypes();
        formats.validateFormattedUses();
        units.validateNumericUses();
        new NumericSubdivisions(this, state).validate();
        references.complete();
        inheritance.validateAssociationRules();
        inheritance.validateAttributeOverrides();
        inheritance.validateClassExtensions();
        declarations.validateTypeNamespaces();
        constraints.validateConstraintNames();
        overlays.validateCrs(this, state);
        translations.complete();
        declarations.bindTransferNamespaces();
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

    // EXISTENCE compares values, it does not assign the complete source domain.
    // Numeric/text bounds are data restrictions, not an assignment precondition.

    private final ViewBuilder views = new ViewBuilder(this, state);

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

    Optional<SymbolId> findModelId(String name, int offset, SourceId source) {
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
