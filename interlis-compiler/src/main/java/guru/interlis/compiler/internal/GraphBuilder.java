package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.Phase;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AnalysisCoverage;
import guru.interlis.model.CoverageStatus;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Coordinates the ordered construction and publication of one semantic graph. */
final class GraphBuilder {
    private final IndexedDeclarations index;
    private final SourceSet sourceSet;
    private final Closure closure;
    private final SnapshotId snapshotId;
    private final SemanticBuildState state = new SemanticBuildState();
    private final SemanticWork work;
    private final SemanticDiagnostics diagnostics;
    private final OverlayBindings overlays;
    private final Map<SourceId, Set<String>> syntaxUnsupportedBySource;
    private final DeclarationBindings declarations;
    private final InheritanceBindings inheritance;
    private final ConstraintBindings constraints;
    private final TopicBindings topics;
    private final ExpressionBindings expressions;
    private final ObjectPathBindings paths;
    private final TypeBindings types;
    private final UnitBindings units;
    private final OidBindings oid;
    private final FormattedTypeBindings formats;
    private final GenericBindings generics;
    private final LineForms lines;
    private final GraphicBuilder graphics;
    private final MetadataBindings metadata;
    private final StructureBindings structures;
    private final PredefinedTemporal temporal;
    private final ViewBuilder views;
    private final ReferenceBindings references;
    private final TranslationBindings translations;

    GraphBuilder(IndexedDeclarations index, SourceSet sourceSet, Closure closure,
                 DiagnosticCollector collector, SnapshotId snapshotId,
                 Map<SourceId, Set<String>> syntaxUnsupportedBySource,
                 CancellationToken cancellation, AnalysisBudget budget, OverlayBindings overlays) {
        this.overlays = overlays;
        this.work = new SemanticWork(cancellation,
                Objects.requireNonNull(budget, "budget").maxSemanticWorkUnits(), overlays.workUnits());
        this.index = Objects.requireNonNull(index, "index");
        this.sourceSet = Objects.requireNonNull(sourceSet, "sourceSet");
        this.closure = Objects.requireNonNull(closure, "closure");
        this.diagnostics = new SemanticDiagnostics(collector, this.closure);
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.syntaxUnsupportedBySource = Map.copyOf(Objects.requireNonNull(syntaxUnsupportedBySource, "syntaxUnsupportedBySource"));
        var resolver = new NameResolver(index, closure);
        this.references = new ReferenceBindings(index, state, work, state::storeReference,
                language -> PredefinedModels.ensure(state, language));
        var names = new NameBindings(index, resolver, work, diagnostics, references,
                (owner, name, kinds) -> temporal().find(owner, name, kinds),
                (owner, name, kinds) -> metadata().predefined(owner, name, kinds),
                (owner, name, kinds) -> lines().predefined(owner, name, kinds),
                (owner, name) -> oid().builtinOidDomain(owner, name),
                (owner, name) -> types().builtinSimpleDomain(owner, name),
                (owner, name) -> units().builtinUnit(owner, name),
                (signature, language) -> expressions().builtinDeclaration(signature, language));
        Function<DeclInfo, Symbol> ensureSymbol = declaration -> declarations().buildSymbol(declaration);
        Function<DeclInfo, SymbolHeader> symbolHeader = declaration -> declarations().header(declaration);
        Function<TypeId, TypeDescriptor> effectiveDescriptor = id -> types().effectiveDescriptor(id);
        Function<SymbolId, SymbolId> scopeRoot = id -> paths().scopeRoot(id);

        this.units = new UnitBindings(state, work, index, diagnostics, names, this::temporal,
                state::defineSymbol, symbolHeader, ensureSymbol, effectiveDescriptor);
        this.oid = new OidBindings(state, work, index, diagnostics, names, this::types, this::inheritance,
                state::defineSymbol, state::replaceSymbol, scopeRoot);
        this.topics = new TopicBindings(state, work, index, diagnostics, this::inheritance, this::paths, oid,
                effectiveDescriptor);
        this.inheritance = new InheritanceBindings(state, work, index, diagnostics, names, this::types, topics,
                this::metadata, state::replaceSymbol, scopeRoot, ensureSymbol);
        this.generics = new GenericBindings(state, work, index, diagnostics, names, inheritance, this::metadata,
                state::replaceSymbol, symbolHeader, effectiveDescriptor, scopeRoot);
        this.structures = new StructureBindings(state, work, index, diagnostics, names, inheritance,
                this::paths, generics, effectiveDescriptor);
        this.formats = new FormattedTypeBindings(state, work, index, diagnostics, names, this::types,
                inheritance, this::paths, references, structures);
        this.lines = new LineForms(state, work, index, diagnostics, names, inheritance, references, this::types,
                units, this::expressions, symbolHeader, ensureSymbol);
        this.constraints = new ConstraintBindings(state, work, index, diagnostics, resolver, this::paths,
                inheritance, references, this::expressions, this::types, structures, symbolHeader);
        this.paths = new ObjectPathBindings(state, work, index, diagnostics, resolver, sourceSet, cancellation,
                this::expressions, this::views, this::types, inheritance, references, structures,
                owner -> graphics().context(owner), ensureSymbol);
        this.metadata = new MetadataBindings(state, work, index, diagnostics, resolver, names, references,
                inheritance, units, this::types, paths, structures, this::expressions, symbolHeader,
                ensureSymbol);
        this.temporal = new PredefinedTemporal(state, units, this::types, metadata, inheritance, paths);
        this.types = new TypeBindings(state, work, index, diagnostics, units, names, inheritance, lines, paths,
                this::expressions, temporal, structures, metadata, references, formats, this::declarations,
                state::defineSymbol);
        this.graphics = new GraphicBuilder(state, work, index, diagnostics, names, metadata, inheritance,
                this::expressions, types, references, topics, symbolHeader, ensureSymbol, scopeRoot);
        var functions = new FunctionBindings(state, work, index, diagnostics, this::expressions, types, paths,
                (left, right) -> translations().sameBoundExpression(left, right));
        this.expressions = new ExpressionBindings(state, work, index, diagnostics, sourceSet, paths,
                this::views, types, lines, metadata, names, references, functions, units, formats,
                state::defineSymbol,
                (constraint, fallback) -> constraints().externalConstraintTarget(constraint, fallback),
                owner -> graphics().context(owner), ensureSymbol);
        this.views = new ViewBuilder(state, work, index, diagnostics, resolver, references, inheritance,
                expressions, types, paths, structures, topics, lines, ensureSymbol, symbolHeader);
        this.declarations = new DeclarationBindings(state, work, index, diagnostics, resolver, closure,
                overlays, units, lines, metadata, generics, constraints, graphics, views, names, inheritance,
                references, types, expressions);
        this.translations = new TranslationBindings(index, state, work, diagnostics, types::effectiveDescriptor,
                paths::findMember, views, state::replaceSymbol);
        for (var _ : index.models()) {
            work.checkpoint();
        }
    }

    // Recursive collaborators are invoked only after construction.
    private ConstraintBindings constraints() { return constraints; }
    private DeclarationBindings declarations() { return declarations; }
    private ExpressionBindings expressions() { return expressions; }
    private GraphicBuilder graphics() { return graphics; }
    private InheritanceBindings inheritance() { return inheritance; }
    private LineForms lines() { return lines; }
    private MetadataBindings metadata() { return metadata; }
    private OidBindings oid() { return oid; }
    private ObjectPathBindings paths() { return paths; }
    private PredefinedTemporal temporal() { return temporal; }
    private TranslationBindings translations() { return translations; }
    private TypeBindings types() { return types; }
    private UnitBindings units() { return units; }
    private ViewBuilder views() { return views; }

    GraphResult build() {
        syntaxUnsupportedBySource.forEach((source, features) -> {
            diagnostics.registerUnsupportedSource(source);
            for (String feature : features) {
                work.checkpoint();
                if (diagnostics.recordUnsupported(source, feature)) {
                    SourceText text = sourceSet.sources().get(source);
                    SourceRange range = text == null ? null : text.range(0, 0);
                    diagnostics.emitUnsupported(source, range, feature, feature);
                }
            }
        });
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            work.checkpoint();
            for (DeclInfo child : index.children(entry.id())) {
                work.checkpoint();
                declarations.buildSymbol(child);
            }
        }
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            work.checkpoint();
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
        new NumericSubdivisions(state, work, index, diagnostics, types::effectiveDescriptor).validate();
        references.complete();
        inheritance.validateAssociationRules();
        inheritance.validateAttributeOverrides();
        inheritance.validateClassExtensions();
        declarations.validateTypeNamespaces();
        constraints.validateConstraintNames();
        overlays.validateCrs(state, work, types);
        translations.complete();
        declarations.bindTransferNamespaces();
        Set<SymbolId> modelIds = closure.models().stream()
                .map(m -> index.findModelId(m.name(), m.declarationOffset(), m.sourceId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<SymbolId> rootIds = closure.roots().stream()
                .map(m -> index.findModelId(m.name(), m.declarationOffset(), m.sourceId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        Set<guru.interlis.model.LanguageVersion> languages = new LinkedHashSet<>();
        for (ModelSymbol model : state.symbolsInBuildOrder().stream()
                .filter(s -> s instanceof ModelSymbol)
                .map(s -> (ModelSymbol) s)
                .filter(s -> modelIds.contains(s.id()))
                .toList()) {
            work.checkpoint();
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

    private AnalysisCoverage buildCoverage() {
        List<AnalysisCoverage.CoverageEntry> entries = new ArrayList<>();
        List<guru.interlis.diagnostics.Diagnostic> relevantDiagnostics = diagnostics.snapshot().stream()
                .filter(d -> d.phase() == Phase.LEXING || d.phase() == Phase.PARSING
                        || d.primaryRange().map(closure::contains).orElse(true)).toList();
        for (SourceId source : closure.sources()) {
            work.checkpoint();
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
