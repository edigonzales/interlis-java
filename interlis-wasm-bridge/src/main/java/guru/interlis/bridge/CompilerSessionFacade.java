package guru.interlis.bridge;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngine;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SemanticProfileId;
import guru.interlis.compiler.SemanticOverlays;
import guru.interlis.compiler.WarningPolicy;
import guru.interlis.compiler.query.CompletionCandidate;
import guru.interlis.compiler.query.CompletionQuery;
import guru.interlis.compiler.query.CompletionResult;
import guru.interlis.compiler.query.DefinitionTarget;
import guru.interlis.compiler.query.HoverInfo;
import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.NamePrefix;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.query.QueryResult;
import guru.interlis.projection.DiagramDocument;
import guru.interlis.projection.DiagramInput;
import guru.interlis.projection.DiagramJsonCodec;
import guru.interlis.projection.DiagramOptions;
import guru.interlis.projection.DiagramProjectionOutcome;
import guru.interlis.projection.DiagramProjectors;
import guru.interlis.projection.DiagramSelection;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.source.TextEdit;
import guru.interlis.workspace.AnalysisAcceptance;
import guru.interlis.workspace.AnalysisRequest;
import guru.interlis.workspace.SourceChange;
import guru.interlis.workspace.SourceExpectation;
import guru.interlis.workspace.SourceUpdate;
import guru.interlis.workspace.UpdateReceipt;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceCapture;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.WorkspaceCompileRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Domain operations of one protocol session. Serialized by the bridge. */
final class CompilerSessionFacade {
    private final CompilerEngine engine;
    private final Workspace workspace;
    private final Map<String, AnalysisSnapshot> snapshots = new LinkedHashMap<>();
    private final Map<String, CompiledSchema> schemas = new LinkedHashMap<>();
    private final LanguageQueries queries = LanguageQueries.create();
    private int schemaCounter;

    CompilerSessionFacade(CompilerEngine engine, Workspace workspace) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    JsonValue.JsonObject updateSource(JsonValue.JsonObject params) {
        SourceUpdate update = update(params);
        UpdateReceipt receipt = workspace.update(update);
        return receipt(receipt);
    }

    JsonValue.JsonObject updateSources(JsonValue.JsonObject params) {
        long expected = BridgeCodec.longValue(params.members().get("expectedWorkspaceRevision"),
                "expectedWorkspaceRevision");
        List<SourceUpdate> updates = new ArrayList<>();
        for (JsonValue value : BridgeCodec.array(params, "updates").values()) {
            updates.add(update((JsonValue.JsonObject) value));
        }
        var receipt = workspace.applyBatch(updates,
                new guru.interlis.workspace.WorkspaceRevision(expected));
        Map<String, JsonValue> result = new LinkedHashMap<>();
        result.put("previousWorkspaceRevision", JsonValues.string(
                Long.toString(receipt.previousWorkspaceRevision().value())));
        result.put("workspaceRevision", JsonValues.string(
                Long.toString(receipt.workspaceRevision().value())));
        result.put("updates", JsonValues.array(receipt.updates().stream()
                .map(this::receipt).map(v -> (JsonValue) v).toList()));
        return new JsonValue.JsonObject(result);
    }

    JsonValue.JsonObject removeSource(JsonValue.JsonObject params) {
        SourceId sourceId = new SourceId(BridgeCodec.string(params, "sourceId"));
        SourceRevision expected = new SourceRevision(BridgeCodec.longValue(
                params.members().get("expectedRevision"), "expectedRevision"));
        return receipt(workspace.remove(sourceId, expected));
    }

    JsonValue.JsonObject setConfiguration(JsonValue.JsonObject params) {
        long expected = BridgeCodec.longValue(params.members().get("expectedWorkspaceRevision"),
                "expectedWorkspaceRevision");
        WorkspaceConfiguration current = WorkspaceConfiguration.defaults();
        CompilerOptions options = OverlayCodec.options(params,current.compilerOptions());
        guru.interlis.workspace.CacheLimits limits = current.cacheLimits();
        if (params.members().get("cacheLimits") instanceof JsonValue.JsonObject cache) {
            limits = new guru.interlis.workspace.CacheLimits(
                    intValue(cache, "maxSyntaxEntries", limits.maxSyntaxEntries()),
                    intValue(cache, "maxAnalysisEntries", limits.maxAnalysisEntries()),
                    intValue(cache, "maxQueryEntries", limits.maxQueryEntries()),
                    longValue(cache, "maxRetainedSourceUtf16Units",
                            limits.maxRetainedSourceUtf16Units()));
        }
        WorkspaceConfiguration configuration = new WorkspaceConfiguration(options,
                current.resolutionPolicy(), current.resolverCatalog(), limits);
        var receipt = workspace.configure(configuration,
                new guru.interlis.workspace.WorkspaceRevision(expected));
        return JsonValues.object(
                "previousWorkspaceRevision", JsonValues.string(
                        Long.toString(receipt.previousWorkspaceRevision().value())),
                "workspaceRevision", JsonValues.string(
                        Long.toString(receipt.workspaceRevision().value())),
                "changed", JsonValues.bool(receipt.changed()),
                "configurationFingerprint", JsonValues.string(
                        receipt.configurationFingerprint()));
    }

    JsonValue.JsonObject analyze(JsonValue.JsonObject params) {
        long expected = BridgeCodec.longValue(params.members().get("expectedWorkspaceRevision"),
                "expectedWorkspaceRevision");
        List<ModelSelector> roots = roots(params);
        CancellationToken token = CancellationToken.none();
        WorkspaceCapture capture = workspace.capture(new AnalysisRequest(
                new guru.interlis.workspace.WorkspaceRevision(expected), roots), token);
        AnalysisSnapshot analysis = engine.analyze(capture.request(), capture.reuse(), token);
        workspace.acceptAnalysis(capture, analysis);
        snapshots.put(analysis.snapshotId().value(), analysis);
        Map<String, JsonValue> result = analysisResult(analysis);
        List<JsonValue> missing = new ArrayList<>();
        for (var pending : capture.resolution().pendingFetches()) {
            missing.add(JsonValues.object("modelName", JsonValues.string(pending.modelName()),
                    "modelVersion", pending.modelVersion()
                            .map(v -> (JsonValue) JsonValues.string(v))
                            .orElse(JsonValue.NULL),
                    "reason", JsonValues.string(pending.reason())));
        }
        result.put("missingModels", JsonValues.array(missing));
        return new JsonValue.JsonObject(result);
    }

    JsonValue.JsonObject compile(JsonValue.JsonObject params) {
        long expected = BridgeCodec.longValue(params.members().get("expectedWorkspaceRevision"),
                "expectedWorkspaceRevision");
        List<ModelSelector> roots = roots(params);
        CompilationOutcome outcome = workspace.compile(new WorkspaceCompileRequest(
                new guru.interlis.workspace.WorkspaceRevision(expected), roots),
                CancellationToken.none());
        Map<String, JsonValue> result = new LinkedHashMap<>();
        switch (outcome) {
            case CompilationOutcome.Succeeded succeeded -> {
                snapshots.put(succeeded.analysis().snapshotId().value(), succeeded.analysis());
                String schemaId = "schema-" + (++schemaCounter);
                schemas.put(schemaId, succeeded.schema());
                result.put("outcome", JsonValues.string("succeeded"));
                result.put("schemaId", JsonValues.string(schemaId));
                result.put("snapshotId", JsonValues.string(
                        succeeded.analysis().snapshotId().value()));
                result.put("diagnostics", diagnostics(succeeded.analysis()));
            }
            case CompilationOutcome.Rejected rejected -> {
                snapshots.put(rejected.analysis().snapshotId().value(), rejected.analysis());
                result.put("outcome", JsonValues.string("rejected"));
                result.put("snapshotId", JsonValues.string(
                        rejected.analysis().snapshotId().value()));
                result.put("diagnostics", diagnostics(rejected.analysis()));
                result.put("rejection", rejection(rejected));
            }
            case CompilationOutcome.Cancelled cancelled -> {
                result.put("outcome", JsonValues.string("cancelled"));
                result.put("cancellation", JsonValues.object("reason",
                        JsonValues.string(cancelled.reason().name())));
            }
            case CompilationOutcome.Failed failed -> {
                result.put("outcome", JsonValues.string("failed"));
                result.put("failureId", JsonValues.string(failed.failureId().value()));
                result.put("diagnostic", new BridgeCodec().diagnostic(failed.error()));
            }
        }
        return new JsonValue.JsonObject(result);
    }

    JsonValue.JsonObject query(JsonValue.JsonObject params) {
        String snapshotId = BridgeCodec.string(params, "snapshotId");
        AnalysisSnapshot snapshot = requireSnapshot(snapshotId);
        JsonValue.JsonObject query = (JsonValue.JsonObject) params.members().get("query");
        String kind = BridgeCodec.string(query, "kind");
        CancellationToken token = CancellationToken.none();
        return switch (kind) {
            case "symbolAt" -> encodeQueryResult(queries.symbolAt(snapshot, cursor(snapshot, query),
                    token), snapshotId);
            case "definition" -> definition(queries.definition(snapshot,
                    cursor(snapshot, query), token), snapshotId);
            case "semanticProperties" -> semanticProperties(snapshot,query,token);
            case "hover" -> hover(queries.hover(snapshot, cursor(snapshot, query), token));
            case "completion" -> completion(snapshot, query, token);
            default -> throw new BridgeException("UNSUPPORTED_OPERATION",
                    "unsupported query kind " + kind);
        };
    }

    JsonValue.JsonObject diagram(JsonValue.JsonObject params) {
        DiagramInput input;
        String snapshotId = BridgeCodec.optionalString(params, "snapshotId").orElse(null);
        String schemaId = BridgeCodec.optionalString(params, "schemaId").orElse(null);
        List<SymbolRef> selection;
        if (snapshotId != null) {
            AnalysisSnapshot snapshot = requireSnapshot(snapshotId);
            input = new DiagramInput.FromAnalysis(snapshot);
            selection = selection(params, snapshot.snapshotId(),
                    snapshot.semanticGraph().rootModels());
        } else if (schemaId != null) {
            CompiledSchema schema = schemas.get(schemaId);
            if (schema == null) {
                throw new BridgeException("SNAPSHOT_EXPIRED", "unknown schema");
            }
            input = new DiagramInput.FromSchema(schema);
            selection = selection(params, schema.snapshotId(),
                    schema.semanticGraph().rootModels());
        } else {
            throw new BridgeException("INVALID_REQUEST", "snapshotId or schemaId required");
        }
        DiagramOptions options = DiagramOptions.defaults(new DiagramSelection(selection));
        var outcome = DiagramProjectors.create().project(input, options,
                CancellationToken.none());
        if (outcome instanceof DiagramProjectionOutcome.Rejected rejected) {
            throw new BridgeException("INVALID_REQUEST", "diagram rejected",
                    JsonValues.object("reasons", JsonValues.strings(
                            rejected.reasons().stream().map(Object::toString).toList())));
        }
        DiagramDocument document = ((DiagramProjectionOutcome.Produced) outcome).document();
        return (JsonValue.JsonObject) Json.parse(new DiagramJsonCodec().encode(document));
    }

    void releaseSnapshot(String snapshotId) {
        snapshots.remove(snapshotId);
        schemas.entrySet().removeIf(entry -> entry.getValue().snapshotId().value()
                .equals(snapshotId));
    }

    /**
     * Returns the {@code close} value.
     *
     * @return the value
     */
    void close() {
        workspace.close();
        snapshots.clear();
        schemas.clear();
    }

    private AnalysisSnapshot requireSnapshot(String id) {
        AnalysisSnapshot snapshot = snapshots.get(id);
        if (snapshot == null) {
            throw new BridgeException("SNAPSHOT_EXPIRED", "unknown snapshot");
        }
        return snapshot;
    }

    private List<ModelSelector> roots(JsonValue.JsonObject params) {
        List<ModelSelector> roots = new ArrayList<>();
        for (JsonValue value : BridgeCodec.array(params, "roots").values()) {
            JsonValue.JsonObject root = (JsonValue.JsonObject) value;
            String kind = BridgeCodec.string(root, "kind");
            if ("byName".equals(kind)) {
                roots.add(new ModelSelector.ByName(BridgeCodec.string(root, "modelName"),
                        BridgeCodec.optionalString(root, "modelVersion"),
                        BridgeCodec.optionalString(root, "sourceHint").map(SourceId::new)));
            } else if ("bySource".equals(kind)) {
                roots.add(new ModelSelector.BySource(
                        new SourceId(BridgeCodec.string(root, "source"))));
            } else {
                throw new BridgeException("INVALID_REQUEST", "unknown root kind " + kind);
            }
        }
        if (roots.isEmpty()) {
            throw new BridgeException("INVALID_REQUEST", "at least one root required");
        }
        return roots;
    }

    private SourceUpdate update(JsonValue.JsonObject params) {
        SourceId sourceId = new SourceId(BridgeCodec.string(params, "sourceId"));
        JsonValue.JsonObject expectation = (JsonValue.JsonObject) params.members().get("expectation");
        SourceExpectation sourceExpectation = switch (BridgeCodec.string(expectation, "kind")) {
            case "absent" -> new SourceExpectation.Absent();
            case "at" -> new SourceExpectation.At(new SourceRevision(BridgeCodec.longValue(
                    expectation.members().get("revision"), "revision")));
            default -> throw new BridgeException("INVALID_REQUEST", "unknown expectation");
        };
        JsonValue.JsonObject change = (JsonValue.JsonObject) params.members().get("change");
        SourceChange sourceChange = switch (BridgeCodec.string(change, "kind")) {
            case "replace" -> new SourceChange.Replace(BridgeCodec.string(change, "text"));
            case "edits" -> {
                SourceText current = workspace.source(sourceId).orElseThrow(
                        () -> new BridgeException("VERSION_CONFLICT", "source is missing"));
                List<TextEdit> edits = new ArrayList<>();
                for (JsonValue value : BridgeCodec.array(change, "edits").values()) {
                    JsonValue.JsonObject edit = (JsonValue.JsonObject) value;
                    int start = (int) BridgeCodec.longValue(edit.members().get("startOffset"),
                            "startOffset");
                    int end = (int) BridgeCodec.longValue(edit.members().get("endOffset"),
                            "endOffset");
                    edits.add(new TextEdit(current.range(start, end),
                            BridgeCodec.string(edit, "text")));
                }
                yield new SourceChange.ApplyEdits(edits);
            }
            default -> throw new BridgeException("INVALID_REQUEST", "unknown change kind");
        };
        return new SourceUpdate(sourceId, sourceExpectation, sourceChange);
    }

    private JsonValue.JsonObject receipt(UpdateReceipt receipt) {
        return JsonValues.object(
                "sourceId", JsonValues.string(receipt.source().value()),
                "previousSourceRevision", receipt.previousSourceRevision()
                        .map(r -> (JsonValue) JsonValues.string(Long.toString(r.value())))
                        .orElse(JsonValue.NULL),
                "sourceRevision", receipt.sourceRevision()
                        .map(r -> (JsonValue) JsonValues.string(Long.toString(r.value())))
                        .orElse(JsonValue.NULL),
                "previousWorkspaceRevision", JsonValues.string(
                        Long.toString(receipt.previousWorkspaceRevision().value())),
                "workspaceRevision", JsonValues.string(
                        Long.toString(receipt.workspaceRevision().value())),
                "changed", JsonValues.bool(receipt.changed()),
                "reasons", JsonValues.strings(receipt.reasons().stream()
                        .map(Enum::name).toList()));
    }

    private Map<String, JsonValue> analysisResult(AnalysisSnapshot analysis) {
        Map<String, JsonValue> result = new LinkedHashMap<>();
        result.put("workspaceRevision", JsonValues.string(Long.toString(
                workspace.currentRevision().value())));
        result.put("snapshotId", JsonValues.string(analysis.snapshotId().value()));
        result.put("analysisState", JsonValues.string(
                analysis.coverage().incompleteEntries().isEmpty() ? "complete" : "partial"));
        List<JsonValue> sources = analysis.sourceRevisions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(SourceId::value)))
                .map(entry -> (JsonValue) JsonValues.object("sourceId", JsonValues.string(
                                entry.getKey().value()),
                        "revision", JsonValues.string(Long.toString(entry.getValue().value()))))
                .toList();
        result.put("sources", JsonValues.array(sources));
        result.put("diagnostics", diagnostics(analysis));
        result.put("coverage", JsonValues.object("requestedRootsChecked", JsonValues.bool(false),
                "incomplete", JsonValues.strings(analysis.coverage().incompleteEntries().stream()
                        .map(e -> e.phase().name() + ":" + e.status()).toList())));
        return result;
    }

    private JsonValue.JsonArray diagnostics(AnalysisSnapshot analysis) {
        List<JsonValue> diagnostics = new ArrayList<>();
        BridgeCodec codec = new BridgeCodec();
        for (Diagnostic diagnostic : analysis.diagnostics()) {
            diagnostics.add(codec.diagnostic(diagnostic));
        }
        return JsonValues.array(diagnostics);
    }

    private JsonValue.JsonObject rejection(CompilationOutcome.Rejected rejected) {
        var summary = rejected.rejection();
        return JsonValues.object(
                "reasons", JsonValues.strings(summary.reasons().stream().map(Enum::name).toList()),
                "diagnosticIds", JsonValues.strings(summary.diagnosticIds().stream()
                        .map(id -> id.value()).toList()),
                "requestedRoots", JsonValues.strings(summary.requestedRoots().stream()
                        .map(Object::toString).toList()),
                "unsupportedFeatureIds", JsonValues.strings(summary.unsupportedFeatureIds()));
    }

    private SourceRange cursor(AnalysisSnapshot snapshot, JsonValue.JsonObject query) {
        SourceId sourceId = new SourceId(BridgeCodec.string(query, "sourceId"));
        SourceRevision revision = new SourceRevision(BridgeCodec.longValue(
                query.members().get("revision"), "revision"));
        if (!revision.equals(snapshot.sourceRevisions().get(sourceId))) {
            throw new BridgeException("SNAPSHOT_MISMATCH", "cursor revision is stale");
        }
        int start = (int) BridgeCodec.longValue(query.members().get("startOffset"), "startOffset");
        int end = (int) BridgeCodec.longValue(query.members().get("endOffset"), "endOffset");
        if (start < 0 || end < start) {
            throw new BridgeException("INVALID_REQUEST", "invalid cursor");
        }
        return new SourceRange(sourceId, revision, start, end);
    }

    private JsonValue.JsonObject encodeQueryResult(QueryResult<SymbolRef> result,
                                                   String snapshotId) {
        return switch (result) {
            case QueryResult.Known<SymbolRef> known -> JsonValues.object(
                    "kind", JsonValues.string("known"),
                    "snapshotId", JsonValues.string(snapshotId),
                    "symbolId", JsonValues.string(known.value().symbol().value()));
            case QueryResult.Unresolved<SymbolRef> unresolved -> JsonValues.object(
                    "kind", JsonValues.string("unresolved"),
                    "reason", JsonValues.string(unresolved.reason().name()),
                    "candidates", JsonValues.strings(unresolved.candidates().stream()
                            .map(r -> r.symbol().value()).toList()));
            case QueryResult.Invalid<SymbolRef> invalid -> JsonValues.object(
                    "kind", JsonValues.string("invalid"),
                    "diagnosticIds", JsonValues.strings(invalid.diagnosticIds().stream()
                            .map(Object::toString).toList()));
            case QueryResult.NotAnalyzed<SymbolRef> not -> JsonValues.object(
                    "kind", JsonValues.string("notAnalyzed"),
                    "reason", JsonValues.string(not.reason().name()));
        };
    }

    private JsonValue.JsonObject definition(QueryResult<DefinitionTarget> result,
                                            String snapshotId) {
        if (result instanceof QueryResult.Known<DefinitionTarget> known) {
            DefinitionTarget target = known.value();
            return JsonValues.object("kind", JsonValues.string("definition"),
                    "snapshotId", JsonValues.string(snapshotId),
                    "symbolId", JsonValues.string(target.symbol().symbol().value()),
                    "nameRange", range(target.nameRange()),
                    "declarationRange", range(target.declarationRange()));
        }
        return JsonValues.object("kind", JsonValues.string("unresolved"));
    }

    private JsonValue.JsonObject hover(QueryResult<HoverInfo> result) {
        if (result instanceof QueryResult.Known<HoverInfo> known) {
            HoverInfo info = known.value();
            return JsonValues.object("kind", JsonValues.string("hover"),
                    "signature", JsonValues.string(info.signature()),
                    "documentation", JsonValues.string(info.documentation()),
                    "range", range(info.range()));
        }
        return JsonValues.object("kind", JsonValues.string("unresolved"));
    }

    private JsonValue.JsonObject completion(AnalysisSnapshot snapshot, JsonValue.JsonObject query,
                                            CancellationToken token) {
        SourceRange cursor = cursor(snapshot, query);
        String prefix = BridgeCodec.optionalString(query, "prefix").orElse("");
        int limit = (int) BridgeCodec.longValue(query.members().getOrDefault("limit",
                JsonValues.number(100)), "limit");
        CompletionResult result = queries.complete(snapshot,
                new CompletionQuery(cursor, new NamePrefix(prefix), limit), token);
        List<JsonValue> candidates = new ArrayList<>();
        for (CompletionCandidate candidate : result.candidates()) {
            candidates.add(JsonValues.object("label", JsonValues.string(candidate.label()),
                    "insertText", JsonValues.string(candidate.insertText()),
                    "kind", JsonValues.string(candidate.kind().name()),
                    "detail", JsonValues.string(candidate.detail())));
        }
        return JsonValues.object("kind", JsonValues.string("completion"),
                "candidates", JsonValues.array(candidates),
                "replacementRange", range(result.replacementRange()),
                "incomplete", JsonValues.bool(result.incomplete()));
    }

    private List<SymbolRef> selection(JsonValue.JsonObject params,
                                      guru.interlis.model.SnapshotId snapshotId,
                                      List<guru.interlis.model.SymbolId> defaults) {
        if (params.members().get("selection") instanceof JsonValue.JsonArray array
                && !array.values().isEmpty()) {
            List<SymbolRef> result = new ArrayList<>();
            for (JsonValue value : array.values()) {
                JsonValue.JsonObject ref = (JsonValue.JsonObject) value;
                result.add(new SymbolRef(snapshotId,
                        new guru.interlis.model.SymbolId(BridgeCodec.string(ref, "symbolId"))));
            }
            return result;
        }
        return defaults.stream().map(id -> new SymbolRef(snapshotId, id)).toList();
    }

    private JsonValue.JsonObject semanticProperties(AnalysisSnapshot snapshot,JsonValue.JsonObject query,CancellationToken token) {
        var reference=queries.symbolAt(snapshot,cursor(snapshot,query),token).known();
        if(reference.isEmpty())return JsonValues.object("kind",JsonValues.string("unresolved"));
        var symbol=snapshot.queries().symbol(reference.orElseThrow()).known().orElseThrow();
        var values=new ArrayList<JsonValue>();
        for(var entry:symbol.header().metaAttributes().entries())values.add(JsonValues.object(
                "key",JsonValues.string(entry.key()),"text",JsonValues.string(entry.value().text()),"origin",origin(entry.value().origin())));
        var result=new LinkedHashMap<String,JsonValue>();result.put("kind",JsonValues.string("semanticProperties"));
        result.put("symbolId",JsonValues.string(symbol.id().value()));result.put("metadata",JsonValues.array(values));
        if(symbol instanceof guru.interlis.model.ModelSymbol model)model.translation().ifPresent(t->{
            var translated=new LinkedHashMap<String,JsonValue>();translated.put("sourceModel",JsonValues.string(t.sourceModel()));
            t.sourceTarget().ifPresent(id->translated.put("sourceTarget",JsonValues.string(id.value())));
            t.origin().ifPresent(o->translated.put("origin",origin(o)));
            translated.put("correspondences",JsonValues.array(t.correspondences().stream().map(link->(JsonValue)JsonValues.object(
                    "original",JsonValues.string(link.original().value()),"translated",JsonValues.string(link.translated().value()))).toList()));
            result.put("translation",new JsonValue.JsonObject(translated));
        });
        return new JsonValue.JsonObject(result);
    }
    private JsonValue.JsonObject origin(guru.interlis.model.Origin origin) {
        return switch(origin) {
            case guru.interlis.model.Origin.OverlayOrigin o->{
                var fields=new LinkedHashMap<String,JsonValue>();fields.put("kind",JsonValues.string("overlay"));
                fields.put("overlayId",JsonValues.string(o.overlayId()));fields.put("priority",JsonValues.number(o.priority()));
                o.range().ifPresent(r->fields.put("range",range(r)));yield new JsonValue.JsonObject(fields);
            }
            case guru.interlis.model.Origin.DeclaredOrigin d->JsonValues.object("kind",JsonValues.string("declared"),"range",range(d.range()));
            case guru.interlis.model.Origin.DerivedOrigin d->JsonValues.object("kind",JsonValues.string("derived"),"declaredAt",JsonValues.string(d.declaredAt().value()),"derivation",JsonValues.string(d.kind().name()));
            case guru.interlis.model.Origin.PredefinedOrigin d->JsonValues.object("kind",JsonValues.string("predefined"),"key",JsonValues.string(d.key()));
            case guru.interlis.model.Origin.SyntheticOrigin d->JsonValues.object("kind",JsonValues.string("synthetic"),"reason",JsonValues.string(d.reason().name()));
        };
    }

    private JsonValue.JsonObject range(SourceRange range) {
        return JsonValues.object("source", JsonValues.string(range.source().value()),
                "revision", JsonValues.string(Long.toString(range.revision().value())),
                "startOffset", JsonValues.number(range.startOffset()),
                "endOffset", JsonValues.number(range.endOffset()));
    }

    private static int intValue(JsonValue.JsonObject object, String name, int fallback) {
        return object.members().containsKey(name)
                ? (int) BridgeCodec.longValue(object.members().get(name), name) : fallback;
    }

    private static long longValue(JsonValue.JsonObject object, String name, long fallback) {
        return object.members().containsKey(name)
                ? BridgeCodec.longValue(object.members().get(name), name) : fallback;
    }
}
