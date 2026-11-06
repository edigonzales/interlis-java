package guru.interlis.lsp;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.query.CompletionCandidate;
import guru.interlis.compiler.query.CompletionQuery;
import guru.interlis.compiler.query.CompletionResult;
import guru.interlis.compiler.query.DefinitionTarget;
import guru.interlis.compiler.query.HoverInfo;
import guru.interlis.compiler.query.ReferenceOptions;
import guru.interlis.compiler.query.ReferenceResult;
import guru.interlis.compiler.query.RenamePlan;
import guru.interlis.compiler.query.RenamePreparation;
import guru.interlis.compiler.query.RenameRequest;
import guru.interlis.format.FormatOptions;
import guru.interlis.format.FormatOutcome;
import guru.interlis.format.ModelFormatters;
import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.NamePrefix;
import guru.interlis.model.Symbol;
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
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxToken;
import guru.interlis.workspace.AnalysisRequest;
import guru.interlis.workspace.SourceChange;
import guru.interlis.workspace.SourceExpectation;
import guru.interlis.workspace.SourceUpdate;
import guru.interlis.workspace.Workspace;
import guru.interlis.lsp.LspDocumentRegistry.Synchronized;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Thin LSP adapter over Workspace and LanguageQueries. */
final class InterlisTextDocumentService implements TextDocumentService {
    private final Workspace workspace;
    private final ExternalSourceRegistry externalSources=new ExternalSourceRegistry();
    private guru.interlis.workspace.WorkspaceConfiguration userConfiguration;
    private RepositorySettings repositorySettings=RepositorySettings.defaults();
    private guru.interlis.diagnostics.DiagnosticLanguage localeLanguage=guru.interlis.diagnostics.DiagnosticLanguage.EN;
    private guru.interlis.repository.RepositoryResolutionOutcome.Rejected repositoryFailure;
    private final java.util.Set<String> publishedExternal=new java.util.HashSet<>();
    private final LspDiagnosticMapper diagnosticMapper=new LspDiagnosticMapper();
    private guru.interlis.diagnostics.DiagnosticLanguage diagnosticLanguage=guru.interlis.diagnostics.DiagnosticLanguage.EN;
    private final LspDocumentRegistry documents = new LspDocumentRegistry();
    private volatile java.util.function.Consumer<String> operations = ignored -> { };
    private final AnalysisScheduler scheduler;
    private final LspInputReadiness readiness;
    private final LspFileIdentityRegistry fileIdentities = new LspFileIdentityRegistry();
    private java.util.function.BiFunction<String,Map<SourceId,String>,CompletableFuture<ProjectSourceLoader.IdentityMatch>> identifyFile=
            (uri,known) -> CompletableFuture.completedFuture(new ProjectSourceLoader.IdentityMatch(Optional.empty(),Optional.empty()));
    private volatile java.util.function.Consumer<String> externalReload;
    private java.util.function.BiFunction<List<ProjectSourceLoader.FileContent>,CancellationToken,CompletableFuture<Void>> verifyFiles =
            (files,token) -> failed(ResponseErrorCode.RequestFailed,"project file service is not initialized");
    private boolean versionedEdits;
    private final LanguageQueries queries = LanguageQueries.create();
    private final LspDiagramService diagrams=new LspDiagramService();
    private volatile java.util.function.Consumer<AnalysisReady> analysisReady=ignored -> { };
    void analysisReadySink(java.util.function.Consumer<AnalysisReady> sink) {analysisReady=Objects.requireNonNull(sink);}
    String serverInstanceId() {return diagrams.serverInstanceId();}
    private final AtomicReference<DiagnosticSink> sink = new AtomicReference<>();
    private Runnable requestGuard=() -> { };
    void requestGuard(Runnable guard) { requestGuard=Objects.requireNonNull(guard); }

    InterlisTextDocumentService(Workspace workspace, guru.interlis.compiler.CompilerEngine engine,
                                guru.interlis.compiler.CompilerServices services) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.userConfiguration=workspace.configuration();
        scheduler = new AnalysisScheduler(workspace, engine, services, this::accepted,
                message -> operations.accept(message));
        scheduler.repositoryResult(this::repositoryResolved,id -> !externalSources.contains(id));
        readiness = new LspInputReadiness(documents::generation, scheduler::completeOnOwner, this::operation);
    }

    void sink(DiagnosticSink sink) {
        this.sink.set(sink);
    }

    void operationSink(java.util.function.Consumer<String> sink) {
        operations = Objects.requireNonNull(sink, "sink");
    }

    void close() { scheduler.close(() -> {
        readiness.cancelPendingOnClose();
        externalSources.close(); publishedExternal.clear();
        diagrams.close();analysisReady=ignored -> { };
        fileIdentities.clear(); documents.clear();
    }); }

    CompletableFuture<guru.interlis.compiler.CompilationOutcome> compileWorkspace() {
        try { requestGuard.run(); } catch(ResponseErrorException invalid) { return CompletableFuture.failedFuture(invalid); }
        return afterFiles(() -> {
            if (documents.needsResync()) return failed(ResponseErrorCode.ContentModified, "full document resynchronization is required");
            long generation = documents.generation();
            return scheduler.compileRoots(null, () -> requireInputGeneration(generation));
        });
    }

    CompletableFuture<CompileReport> compileModel(CompileModelParams params) {
        if(params==null || params.mode()==null || !java.util.Set.of("current","workspace").contains(params.mode())) return failed(ResponseErrorCode.InvalidParams,"compile mode must be current or workspace");
        if("workspace".equals(params.mode())) {
            if(params.textDocument()!=null || params.modelName()!=null || params.modelVersion()!=null) return failed(ResponseErrorCode.InvalidParams,"workspace compile accepts no document selection");
            return LspFutures.map(compileWorkspace(),this::compileReport,operations);
        }
        if(params.textDocument()==null || params.textDocument().getVersion()==null) return failed(ResponseErrorCode.InvalidParams,"current compile requires a versioned document");
        var captured=copyParams(params,CompileModelParams.class);
        var choosing=requestReady((view,token) -> {
            var state=requireState(view,new Position(0,0),captured.textDocument());
            if(state.lspVersion()!=captured.textDocument().getVersion()) return failed(ResponseErrorCode.ContentModified,"compile document version changed");
            if(!editable(view,state.sourceId())) return failed(ResponseErrorCode.RequestFailed,"compile current requires a local editable source");
            var models=state.analysis().semanticGraph().declarationsInOrder().stream()
                    .filter(guru.interlis.model.ModelSymbol.class::isInstance).map(guru.interlis.model.ModelSymbol.class::cast)
                    .filter(m -> m.nameRange().map(r -> r.source().equals(state.sourceId())).orElse(false))
                    .map(m -> new CompileReport.ModelChoice(m.name().value(),m.modelVersion())).toList();
            if(captured.modelName()==null && models.size()>1) return CompletableFuture.completedFuture(new CompileSelection(List.of(),models,view.documentGeneration()));
            List<guru.interlis.compiler.ModelSelector> roots=List.of(new guru.interlis.compiler.ModelSelector.BySource(state.sourceId()));
            if(captured.modelName()!=null) {
                var match=models.stream().filter(m -> m.modelName().equals(captured.modelName())
                        && (captured.modelVersion()==null || m.modelVersion().equals(captured.modelVersion()))).toList();
                if(match.size()!=1) return failed(ResponseErrorCode.InvalidParams,"model selection is missing or ambiguous in current document");
                roots=List.of(new guru.interlis.compiler.ModelSelector.ByName(match.getFirst().modelName(),Optional.of(match.getFirst().modelVersion()),Optional.of(state.sourceId())));
            }
            return CompletableFuture.completedFuture(new CompileSelection(roots,List.of(),view.documentGeneration()));
        });
        return LspFutures.compose(choosing,selection -> selection.roots().isEmpty()?CompletableFuture.completedFuture(
                new CompileReport("selectionRequired",selection.choices(),CompileReport.Summary.empty())):
                LspFutures.map(afterFiles(() -> scheduler.compileRoots(selection.roots(), () ->
                        requireSelectedDocument(captured.textDocument(), selection.documentGeneration(), "compile document version changed"))),
                        this::compileReport,operations),operations);
    }
    CompletableFuture<ExportArtifact> exportArtifact(ExportArtifactParams params) {
        if(params==null||params.protocolVersion()!=1||params.textDocument()==null||params.textDocument().getVersion()==null||!LspExportService.FORMATS.contains(params.format()))return failed(ResponseErrorCode.InvalidParams,"export requires protocol 1, a format and an exact document version");
        var p=copyParams(params,ExportArtifactParams.class);
        try {LspExportService.options(p);}catch(RuntimeException invalid){return failed(ResponseErrorCode.InvalidParams,"invalid documentation options");}
        var selection=requestReady((view,token)->{
            var state=requireState(view,new Position(0,0),p.textDocument());if(state.lspVersion()!=p.textDocument().getVersion())return failed(ResponseErrorCode.ContentModified,"export document version changed");
            var models=state.analysis().semanticGraph().symbols().stream().filter(guru.interlis.model.ModelSymbol.class::isInstance).map(guru.interlis.model.ModelSymbol.class::cast).filter(m->m.nameRange().map(r->r.source().equals(state.sourceId())).orElse(false)).toList();
            if(models.size()>1&&p.modelName()==null)return failed(ResponseErrorCode.InvalidParams,"choose an explicit model for this source");
            List<guru.interlis.compiler.ModelSelector> roots=List.of(new guru.interlis.compiler.ModelSelector.BySource(state.sourceId()));
            if(p.modelName()!=null){var matching=models.stream().filter(m->m.name().value().equals(p.modelName())&&(p.modelVersion()==null||m.modelVersion().equals(p.modelVersion()))).toList();if(matching.size()!=1)return failed(ResponseErrorCode.InvalidParams,"model selection is unavailable or ambiguous");roots=List.of(new guru.interlis.compiler.ModelSelector.ByName(p.modelName(),Optional.of(matching.getFirst().modelVersion()),Optional.of(state.sourceId())));}
            return CompletableFuture.completedFuture(new CompileSelection(roots,List.of(),view.documentGeneration()));
        });
        return LspFutures.compose(selection, chosen -> LspFutures.compose(afterFiles(() -> scheduler.compileRoots(chosen.roots(), () ->
                requireSelectedDocument(p.textDocument(), chosen.documentGeneration(), "export source changed"))), outcome -> {
            if(!(outcome instanceof guru.interlis.compiler.CompilationOutcome.Succeeded success))return failed(outcome instanceof guru.interlis.compiler.CompilationOutcome.Cancelled?ResponseErrorCode.RequestCancelled:ResponseErrorCode.RequestFailed,"Documentation export requires a complete valid current model");
            return requestReady((view,token) -> {
                var state=requireState(view,new Position(0,0),p.textDocument());
                if (state.lspVersion() != p.textDocument().getVersion() || view.documentGeneration() != chosen.documentGeneration())
                    return failed(ResponseErrorCode.ContentModified,"export document version changed");
                var revisions=new LinkedHashMap<String,String>();var closed=new ArrayList<ProjectSourceLoader.FileContent>();
                for(var e:success.schema().sourceRevisions().entrySet()){var source=view.source(e.getKey()).orElse(null);if(source==null||!source.revision().equals(e.getValue()))return failed(ResponseErrorCode.ContentModified,"export dependency changed");revisions.put(view.uri(e.getKey()),Long.toString(e.getValue().value()));var file=view.closedFiles().get(e.getKey().value());if(file!=null&&view.open(e.getKey())==null)closed.add(file);}
                var provenance=new ExportArtifact.SourceSnapshot(diagrams.serverInstanceId(),success.schema().snapshotId().value(),p.textDocument().getUri(),state.lspVersion(),view.workspaceRevision(),revisions);
                var artifact=new LspExportService().render(success.schema(),p,provenance,token);
                return closed.isEmpty()?CompletableFuture.completedFuture(artifact):LspFutures.map(verifyFiles.apply(List.copyOf(closed),token), ignored -> artifact, operations);
            });
        },operations),operations);
    }

    private CompileReport compileReport(guru.interlis.compiler.CompilationOutcome outcome) {
        return scheduler.onOwner(() -> new LspCompileMapper().map(outcome,diagnosticLanguage,captureView()::uri));
    }

    private void requireSelectedDocument(org.eclipse.lsp4j.VersionedTextDocumentIdentifier document,
                                         long generation, String message) {
        var open = documents.synchronizedDocument(document.getUri());
        if (open == null || open.lspVersion() != document.getVersion()
                || documents.generation() != generation || documents.needsResync()) {
            throw LspFailures.error(ResponseErrorCode.ContentModified, message);
        }
    }

    private record CompileSelection(List<guru.interlis.compiler.ModelSelector> roots,
                                    List<CompileReport.ModelChoice> choices, long documentGeneration) { }

    private boolean editorAssist;
    void editorAssist(boolean enabled){editorAssist=enabled;}
    CompletableFuture<EditorAssistResult> editorAssist(EditorAssistParams params){
        if(!editorAssist)return failed(ResponseErrorCode.InvalidRequest,"editorAssist was not negotiated");
        if(params==null||params.protocolVersion()!=1||params.textDocument()==null||params.textDocument().getVersion()==null||params.position()==null||params.options()==null)return failed(ResponseErrorCode.InvalidParams,"editorAssist requires an exact version, position and indentation options");
        var p=copyParams(params,EditorAssistParams.class);
        return requestReady((view,token)->{var state=requireState(view,p.position(),p.textDocument());
            if(state.lspVersion()!=p.textDocument().getVersion())return failed(ResponseErrorCode.ContentModified,"editor assistance document version changed");
            if(!editable(view,state.sourceId()))return failed(ResponseErrorCode.RequestFailed,"readonly source cannot be edited");
            var plan=new guru.interlis.compiler.query.EditorAssistanceService().planBlockClose(state.analysis(),cursor(state,p.position()),new guru.interlis.compiler.query.EditorIndentOptions(p.options().getTabSize(),p.options().isInsertSpaces()),token);
            if(plan.isEmpty())return CompletableFuture.completedFuture(null);var value=plan.orElseThrow();String text=state.source().text();
            for(var edit:value.edits().stream().sorted(java.util.Comparator.comparingInt((guru.interlis.source.TextEdit e)->e.range().startOffset()).reversed()).toList())text=text.substring(0,edit.range().startOffset())+edit.replacement()+text.substring(edit.range().endOffset());
            var resultSource=new SourceText(state.sourceId(),state.source().revision(),text);var caret=resultSource.lineMap().positionAt(value.caretOffset());
            return CompletableFuture.completedFuture(new EditorAssistResult(1,diagrams.serverInstanceId(),state.analysis().snapshotId().value(),p.textDocument().getUri(),state.lspVersion(),value.edits().stream().map(e->new TextEdit(toLspRange(state.source(),e.range()),e.replacement())).toList(),new Position(caret.line(),caret.character())));
        });
    }

    private <T> CompletableFuture<T> requestReady(java.util.function.BiFunction<HostView,CancellationToken,CompletableFuture<T>> action) {
        try { requestGuard.run(); } catch(ResponseErrorException invalid) { return CompletableFuture.failedFuture(invalid); }
        return afterFiles(() -> {
            if (documents.needsResync()) return failed(ResponseErrorCode.ContentModified, "full document resynchronization is required");
            long generation = documents.generation();
            return scheduler.requestNow(token -> {
                requireInputGeneration(generation);
                HostView view = captureView();
                return scheduler.detached(token, () -> action.apply(view, token));
            });
        });
    }

    private void requireInputGeneration(long generation) {
        if (documents.generation() != generation) {
            throw LspFailures.error(ResponseErrorCode.ContentModified, "CONTENT_MODIFIED: workspace inputs changed");
        }
    }

    private record HostView(Map<String,Synchronized> documents,Map<SourceId,SourceText> sources,
                            Map<String,ProjectSourceLoader.FileContent> closedFiles, boolean versionedEdits,
                            Map<SourceId,String> externalUris,String workspaceRevision,long documentGeneration) {
        Optional<SourceText> source(SourceId id) { return Optional.ofNullable(sources.get(id)); }
        Synchronized open(SourceId id) { return documents.values().stream().filter(state -> state.sourceId().equals(id)).findFirst().orElse(null); }
        String uri(SourceId id) { if(externalUris.containsKey(id)) return externalUris.get(id); return documents.entrySet().stream().filter(entry -> entry.getValue().sourceId().equals(id)).map(Map.Entry::getKey).findFirst()
                .orElseGet(() -> closedFiles.containsKey(id.value())?closedFiles.get(id.value()).path().toUri().toString():id.value()); }
    }

    private HostView captureView() {
        var sources=new LinkedHashMap<SourceId,SourceText>();
        for(var id:workspace.state().sourceRevisions().keySet()) workspace.source(id).ifPresent(source -> sources.put(id,source));
        return new HostView(documents.synchronizedDocuments(),Map.copyOf(sources),fileIdentities.installedClosedFiles(),versionedEdits,
                externalSources.uris(),Long.toString(workspace.currentRevision().value()),documents.generation());
    }

    private <T> CompletableFuture<T> afterFiles(java.util.function.Supplier<CompletableFuture<T>> start) {
        try {
            return scheduler.onOwner(() -> readiness.awaitReady(start));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(LspFailures.response(failure, operations));
        }
    }

    private Map<SourceId, String> fileSources() {
        var result = new LinkedHashMap<SourceId, String>();
        fileIdentities.addClosedSourceUris(result);
        documents.forEachClientSource(result::put);
        return Map.copyOf(result);
    }

    record FileReload(String canonicalUri, Map<SourceId, String> knownSources,
                      Map<String, ProjectSourceLoader.FileContent> closedFiles, List<String> aliases) { }

    FileReload beginFileReload(String requestedUri, long request, java.util.function.Predicate<String> register) {
        if (requestedUri == null) return null;
        var address = LspFileIdentityRegistry.path(requestedUri);
        if (address.isEmpty()) return null;
        return scheduler.onOwner(() -> {
            if (documents.anyClientSource((id, client) -> LspFileIdentityRegistry.path(client).equals(address))) return null;
            String canonical = canonicalUriNow(requestedUri);
            if (!register.test(canonical)) return null;
            var capture = new FileReload(canonical, fileSources(), fileIdentities.installedClosedFiles(),
                    fileIdentities.aliasesOf(canonical));
            beginExternal(canonical, request);
            return capture;
        });
    }

    private boolean openSource(SourceId id) { return documents.hasClientAuthority(id); }

    private String canonicalUriNow(String uri) {
        var open = documents.find(uri);
        if (open != null && open.hasClientAuthority()) return open.sourceId().value();
        return externalSources.lookup(uri).map(e -> e.source().id().value())
                .orElseGet(() -> fileIdentities.canonicalUri(uri));
    }

    void beginExternal(String uri, long request) {
        scheduler.onOwner(() -> {
            readiness.beginExternal(uri, request);
            invalidateInputs();
            return null;
        });
    }

    private void invalidateInputs() {
        documents.advanceGeneration();
        scheduler.invalidate();
    }

    void externalFailed(String uri, long request, Throwable failure) {
        scheduler.completeOnOwner(() -> readiness.completeExternal(uri, request, failure, this::reanalyzeOpenNow));
    }

    void fileServices(java.util.function.Consumer<String> reload,
            java.util.function.BiFunction<List<ProjectSourceLoader.FileContent>,CancellationToken,CompletableFuture<Void>> verify,
            java.util.function.BiFunction<String,Map<SourceId,String>,CompletableFuture<ProjectSourceLoader.IdentityMatch>> identify) {
        externalReload=Objects.requireNonNull(reload);
        scheduler.onOwner(() -> { verifyFiles=Objects.requireNonNull(verify); identifyFile=Objects.requireNonNull(identify); return null; });
    }

    void versionedEdits(boolean supported) { scheduler.onOwner(() -> { versionedEdits=supported; return null; }); }

    void installProject(ProjectSourceLoader.Project project, java.util.function.Predicate<String> current) {
        scheduler.onOwner(() -> {
            var admitted = project.files().stream().filter(file -> current.test(file.loaded().sourceId().value())
                    && current.test(project.primaryPaths().get(file.path()).toUri().toString())).toList();
            fileIdentities.checkCapacity(admitted.stream().map(ProjectSourceLoader.FileContent::identity).toList());
            var updates = new ArrayList<SourceUpdate>();
            var primaries = new LinkedHashMap<java.nio.file.Path, String>();
            var aliases = new LinkedHashMap<ProjectSourceLoader.FileIdentity, String>();
            var acceptedFiles = new LinkedHashMap<String, ProjectSourceLoader.FileContent>();
            for (var file : admitted) {
                var primary = project.primaryPaths().get(file.path());
                String uri = primaries.computeIfAbsent(primary, address -> fileIdentities.sourceForKey(file.identity())
                        .orElseGet(() -> canonicalUriNow(address.toUri().toString())));
                if (!current.test(uri) || wouldRebindOpenFile(file.identity(), uri)) continue;
                aliases.put(file.identity(), uri);
                var id = new SourceId(uri);
                if (acceptedFiles.putIfAbsent(uri, file) != null || openSource(id)) continue;
                var previous = workspace.source(id);
                updates.add(new SourceUpdate(id, previous.<SourceExpectation>map(value -> new SourceExpectation.At(value.revision()))
                        .orElseGet(SourceExpectation.Absent::new), new SourceChange.Replace(file.loaded().text())));
            }
            workspace.applyBatch(updates, workspace.currentRevision());
            aliases.forEach(fileIdentities::rememberPhysicalIdentity);
            acceptedFiles.forEach(fileIdentities::installClosed);
            reanalyzeOpenNow();
            return null;
        });
    }

    void applyExternal(String uri, String requestedUri, long request, Optional<ProjectSourceLoader.FileContent> file,
                       Optional<SourceId> verifiedSource, boolean requestedMissing, java.util.function.Predicate<String> claimCurrent) {
        scheduler.onOwner(() -> {
            if (!readiness.isCurrentExternal(uri, request)) return null;
            // Closing the last client can leave only the catalog's physical aliases.
            var established = file.flatMap(value -> verifiedSource.map(SourceId::value)
                    .or(() -> fileIdentities.sourceForKey(value.identity()))
                    .or(() -> requestedMissing && fileIdentities.isRegisteredKeylessAlias(value.identity(), uri)
                            ? Optional.of(uri) : Optional.empty()));
            String canonical = file.map(value -> established.orElseGet(() -> value.path().toUri().toString()))
                    .orElseGet(() -> canonicalUriNow(requestedUri));
            if (!claimCurrent.test(canonical)) {
                readiness.completeExternal(uri, request, null, this::reanalyzeOpenNow);
                return null;
            }
            var id = new SourceId(canonical);
            if (file.isPresent()) {
                var loaded = file.orElseThrow();
                if (requestedMissing && !uri.equals(canonical)) {
                    throw LspFailures.error(ResponseErrorCode.ContentModified, "surviving alias belongs to a different source");
                }
                if (wouldRebindOpenFile(loaded.identity(), canonical)) {
                    readiness.completeExternal(uri, request, null, this::reanalyzeOpenNow);
                    return null;
                }
                var existing = fileIdentities.closedFile(canonical);
                if (established.isEmpty() && existing != null && !loaded.path().equals(existing.path())) {
                    throw LspFailures.error(ResponseErrorCode.ContentModified, "file identity changed before refresh");
                }
                fileIdentities.checkCapacity(List.of(loaded.identity()));
                boolean open = openSource(id);
                if (!open) replaceSource(id, loaded.loaded().text());
                if (requestedMissing) forgetMissingFileAlias(requestedUri);
                fileIdentities.rememberPhysicalIdentity(loaded.identity(), canonical);
                if (!open) fileIdentities.installClosed(canonical, loaded);
            } else {
                if (openSource(id)) {
                    forgetMissingFileAlias(requestedUri);
                } else {
                    workspace.source(id).ifPresent(source -> workspace.remove(id, source.revision()));
                    fileIdentities.removeClosed(canonical);
                }
            }
            readiness.completeExternal(uri, request, null, this::reanalyzeOpenNow);
            return null;
        });
    }

    private boolean wouldRebindOpenFile(ProjectSourceLoader.FileIdentity identity, String canonical) {
        return documents.anyClientSource((id, uri) -> !id.value().equals(canonical)
                && LspFileIdentityRegistry.path(uri).filter(path ->
                        path.equals(identity.path()) || path.equals(identity.realPath())).isPresent());
    }

    private void forgetMissingFileAlias(String uri) {
        var address = LspFileIdentityRegistry.path(uri);
        if (address.isPresent() && !documents.anyClientSource((id, client) -> LspFileIdentityRegistry.path(client).equals(address))) {
            fileIdentities.forgetPath(uri);
        }
    }

    void operation(String message) { try { operations.accept(message); } catch(RuntimeException unavailable) { /* disconnected client */ } }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        if(!notificationReady()) return;
        var item=copyParams(params,DidOpenTextDocumentParams.class).getTextDocument();
        scheduler.onOwner(() -> {
            readiness.orderDocumentOperation(() -> openWithIdentity(item.getUri(), item.getVersion(), item.getText()));
            return null;
        });
    }

    private record OpenInput(String uri, int version, String text) { }

    private void openWithIdentity(String uri, int version, String text) {
        var previous = documents.find(uri);
        if (previous != null) {
            if (version <= previous.lspVersion()) return;
            if (!previous.hasClientAuthority()) documents.observeRejectedVersion(uri, version);
        }
        var known = fileSources();
        if (LspFileIdentityRegistry.path(uri).isEmpty() || known.isEmpty()) {
            didOpenNow(uri, version, text);
        } else {
            resolveOpenIdentity(new OpenInput(uri, version, text), known, 0, readiness.beginOpen());
        }
    }

    private void resolveOpenIdentity(OpenInput input, Map<SourceId, String> known, int retries, long request) {
        long capturedIdentities = fileIdentities.identityRevision();
        try {
            invalidateInputs();
            var lookup = Objects.requireNonNull(identifyFile.apply(input.uri(), known));
            readiness.trackOpen(request, lookup);
            lookup.whenComplete((match, failure) -> scheduler.completeOnOwner(() ->
                    completeOpenIdentity(input, known, capturedIdentities, retries, request, match, failure)));
        } catch (RuntimeException failure) {
            completeOpenIdentity(input, known, capturedIdentities, retries, request, null, failure);
        }
    }

    private void completeOpenIdentity(OpenInput input, Map<SourceId, String> known, long capturedIdentities,
                                      int retries, long request, ProjectSourceLoader.IdentityMatch match, Throwable failure) {
        if (!readiness.isCurrentOpen(request)) return;
        Throwable problem = failure;
        try {
            if (failure != null) {
                operation("Document identity lookup failed; reopen required");
                return;
            }
            var current = fileSources();
            if (capturedIdentities != fileIdentities.identityRevision() || !known.equals(current)) {
                // A watcher can admit a new physical alias while the lookup runs.
                if (retries >= 3) {
                    var previous = documents.find(input.uri());
                    var id = previous == null ? new SourceId(input.uri()) : previous.sourceId();
                    if (!documents.hasSynchronizedAlias(input.uri(), id)) {
                        documents.advanceGeneration();
                        documents.requireResync(input.uri(), id, input.version()).ifPresent(this::clearDocumentDiagnostics);
                    }
                    operation("Document identities kept changing; reopen required");
                } else if (current.isEmpty()) {
                    didOpenNow(input.uri(), input.version(), input.text());
                } else {
                    resolveOpenIdentity(input, current, retries + 1, readiness.retryOpen(request));
                }
                return;
            }
            match.identity().ifPresent(identity -> {
                var verifiedSource = fileIdentities.sourceForKey(identity).or(() -> match.source().map(SourceId::value));
                String canonical = verifiedSource.orElse(input.uri());
                if (wouldRebindOpenFile(identity, canonical)) {
                    throw LspFailures.error(ResponseErrorCode.ContentModified, "file identity changed before open");
                }
                var existing = fileIdentities.closedFile(canonical);
                if (verifiedSource.isEmpty() && existing != null && !identity.path().equals(existing.path())) {
                    throw LspFailures.error(ResponseErrorCode.ContentModified, "file identity changed before open");
                }
                fileIdentities.rememberPhysicalIdentity(identity, canonical);
            });
            didOpenNow(input.uri(), input.version(), input.text());
        } catch (RuntimeException rejected) {
            problem = rejected;
            operation("Document identity admission failed; reopen required");
        } finally {
            readiness.completeOpen(request, problem, this::reanalyzeOpenNow);
        }
    }

    private void didOpenNow(String uri, int version, String text) {
        documents.advanceGeneration();
        SourceId id = new SourceId(canonicalUriNow(uri));
        if (documents.hasSynchronizedAlias(uri, id)) {
            if (documents.find(uri) instanceof LspDocumentRegistry.ResyncRequired resync && !resync.hasClientAuthority()) {
                documents.remove(uri);
                reanalyzeOpenNow();
                clearDocumentDiagnostics(uri);
            }
            operation("Document is already open under another URI");
            return;
        }
        if (uri.startsWith("interlis-source:") && externalSources.lookup(uri).isEmpty()) {
            operation("Unknown or expired readonly source handle; navigate again.");
            return;
        }
        if (externalSources.contains(id)) {
            var source = workspace.source(id).orElseThrow();
            if (externalSources.lookup(uri).filter(entry -> !entry.source().revision().equals(source.revision())).isPresent()
                    || !source.text().equals(text)) {
                operation("Readonly source content differs; reopen through definition.");
                return;
            }
            analyze(uri, id, version);
            return;
        }
        try {
            replaceSource(id, text);
        } catch (guru.interlis.workspace.UpdateRejectedException rejected) {
            rejectDocument(uri, id, version, rejected.code().name());
            return;
        }
        analyze(uri, id, version);
    }

    private void clearDocumentDiagnostics(String uri) {
        var current = sink.get();
        if (current != null) current.publish(new PublishDiagnosticsParams(uri, List.of()));
    }

    private void replaceSource(SourceId id,String text) {
        var existing=workspace.source(id);
        workspace.update(new SourceUpdate(id,existing.<SourceExpectation>map(source -> new SourceExpectation.At(source.revision()))
                .orElseGet(SourceExpectation.Absent::new),new SourceChange.Replace(text)));
    }

    private void rejectDocument(String uri, SourceId id, int version, String code) {
        var retiredAlias = documents.reject(uri, id, version);
        reportRejectedDocument(code);
        retiredAlias.ifPresent(this::clearDocumentDiagnostics);
    }

    private void reportRejectedDocument(String code) {
        scheduler.invalidate();
        operation("Document update rejected: "+code+"; full document resynchronization is required (full replacement or reopen).");
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        if(!notificationReady()) return;
        var captured=copyParams(params,DidChangeTextDocumentParams.class);
        scheduler.onOwner(() -> { readiness.orderDocumentOperation(() -> didChangeNow(captured)); return null; });
    }

    private void didChangeNow(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        var previous = documents.find(uri);
        if (externalSources.lookup(uri).isPresent() || (previous != null && externalSources.contains(previous.sourceId()))) {
            operation("Readonly repository sources cannot be changed.");
            return;
        }
        int version = params.getTextDocument().getVersion();
        if (previous == null || version <= previous.lspVersion()) return;
        documents.advanceGeneration();
        SourceId id = previous.sourceId();
        var changes = params.getContentChanges();
        if (!(previous instanceof Synchronized)
                && (changes == null || changes.isEmpty() || changes.getFirst() == null || changes.getFirst().getRange() != null)) {
            documents.observeRejectedVersion(uri, version);
            return;
        }
        // A full replacement supplies its own text before any following deltas.
        var base = workspace.source(id).orElseGet(() -> new SourceText(id, guru.interlis.source.SourceRevision.initial(), ""));
        String text;
        try {
            text = materialize(base, changes);
        } catch (ResponseErrorException invalid) {
            if (previous.hasClientAuthority()) rejectDocument(uri, id, version, "INVALID_EDIT");
            else {
                documents.observeRejectedVersion(uri, version);
                reportRejectedDocument("INVALID_EDIT");
            }
            return;
        }
        if (!previous.hasClientAuthority()) {
            openWithIdentity(uri, version, text);
            return;
        }
        try {
            replaceSource(id, text);
        } catch (guru.interlis.workspace.UpdateRejectedException invalid) {
            rejectDocument(uri, id, version, invalid.code().name());
            return;
        }
        analyze(uri, id, version);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        if(!notificationReady()) return;
        var captured=copyParams(params,DidCloseTextDocumentParams.class);
        scheduler.onOwner(() -> { readiness.orderDocumentOperation(() -> didCloseNow(captured)); return null; });
    }

    private void didCloseNow(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        var closed = documents.find(uri);
        if (closed == null) return;
        SourceId id = closed.sourceId();
        boolean local = !externalSources.contains(id);
        boolean reload = local && closed.hasClientAuthority() && LspFileIdentityRegistry.path(uri).isPresent();
        if (local && !reload) {
            closed.acceptedSource().ifPresent(accepted -> workspace.source(id)
                    .filter(current -> current.revision().equals(accepted.revision()))
                    .ifPresent(current -> workspace.remove(id, current.revision())));
        }
        documents.remove(uri);
        documents.advanceGeneration();
        if (reload) reloadExternal(id.value());
        reanalyzeOpenNow();
        clearDocumentDiagnostics(uri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        if(!notificationReady()) return;
        var captured=copyParams(params,DidSaveTextDocumentParams.class);
        scheduler.onOwner(() -> { readiness.orderDocumentOperation(() -> didSaveNow(captured)); return null; });
    }

    boolean notificationReady() {
        try { requestGuard.run(); return true; }
        catch(ResponseErrorException unavailable) { operation(unavailable.getResponseError().getMessage()); return false; }
    }

    private void didSaveNow(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        var state = documents.synchronizedDocument(uri);
        if (state != null) analyze(uri, state.sourceId(), state.lspVersion());
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        var captured = copyParams(params, HoverParams.class);
        return requestReady((view, token) -> hoverNow(view, captured, token));
    }

    private CompletableFuture<Hover> hoverNow(HostView view, HoverParams params, CancellationToken token) {
        Synchronized state = requireState(view, params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<HoverInfo> result = queries.hover(state.analysis(), cursor,
                token);
        if (result instanceof QueryResult.Known<HoverInfo> known) {
            HoverInfo info = known.value();
            MarkupContent content = new MarkupContent(MarkupKind.PLAINTEXT,
                    info.signature()
                            + (info.documentation().isBlank() ? "" : "\n\n" + info.documentation()));
            return CompletableFuture.completedFuture(new Hover(content));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>>> definition(DefinitionParams params) {
        var captured = copyParams(params, DefinitionParams.class);
        return requestReady((view, token) -> definitionNow(view, captured, token));
    }

    private CompletableFuture<Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>>> definitionNow(HostView view, DefinitionParams params, CancellationToken token) {
        Synchronized state = requireState(view, params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<DefinitionTarget> result = queries.definition(state.analysis(), cursor,
                token);
        if (result instanceof QueryResult.Known<DefinitionTarget> known) {
            DefinitionTarget target = known.value();
            SourceText targetSource = view.source(target.nameRange().source()).orElse(null);
            if (targetSource != null) {
                return CompletableFuture.completedFuture(Either.forLeft(List.of(
                        new Location(view.uri(target.nameRange().source()),
                                toLspRange(targetSource, target.nameRange())))));
            }
        }
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        var captured = copyParams(params, CompletionParams.class);
        return requestReady((view, token) -> completionNow(view, captured, token));
    }

    private CompletableFuture<Either<List<CompletionItem>, CompletionList>> completionNow(HostView view, CompletionParams params, CancellationToken token) {
        Synchronized state = requireState(view, params.getPosition(), params.getTextDocument());
        SourceText source = view.source(state.sourceId()).orElseThrow();
        int offset = offsetAt(source, params.getPosition());
        String prefix = prefixAt(source.text(), offset);
        CompletionResult result = new guru.interlis.compiler.query.EditorAssistanceService().complete(state.analysis(),
                new CompletionQuery(cursor(state, params.getPosition()),
                        new NamePrefix(prefix), 100), token);
        List<CompletionItem> items = new ArrayList<>();
        int rank = 0;
        for (CompletionCandidate candidate : result.candidates()) {
            CompletionItem item = new CompletionItem(candidate.label());
            item.setKind(completionKind(candidate));
            item.setDetail(candidate.detail());
            item.setInsertText(candidate.insertText());
            item.setInsertTextFormat(candidate.snippet()?org.eclipse.lsp4j.InsertTextFormat.Snippet:org.eclipse.lsp4j.InsertTextFormat.PlainText);
            item.setTextEdit(Either.forLeft(new TextEdit(toLspRange(state.source(), result.replacementRange()), candidate.insertText())));
            if(candidate.snippet()) {
                String template=candidate.label().replace(" block","");int stops=switch(template){case "MODEL"->4;case "CLASS","STRUCTURE","TOPIC"->2;case "DOMAIN","UNIT"->2;case "VIEW TOPIC"->3;default->0;};
                item.setCommand(new Command("INTERLIS snippet interaction","interlisGuru.snippet.accept",List.of(Map.of("uri",params.getTextDocument().getUri(),"baseVersion",state.lspVersion(),"headerStops",stops,"suggestAtBody",java.util.Set.of("DOMAIN","UNIT").contains(template),"suppressHeader",template.equals("MODEL")))));
            }
            item.setSortText(String.format(java.util.Locale.ROOT, "%06d", rank++));
            items.add(item);
        }
        return CompletableFuture.completedFuture(Either.forRight(new CompletionList(result.incomplete(), items)));
    }

    @Override
    public CompletableFuture<List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        var captured = copyParams(params, DocumentSymbolParams.class);
        return requestReady((view, token) -> documentSymbolNow(view, captured, token));
    }

    private CompletableFuture<List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>>> documentSymbolNow(HostView view, DocumentSymbolParams params, CancellationToken token) {
        String uri = params.getTextDocument().getUri();
        Synchronized state = requireState(view, new Position(0, 0), params.getTextDocument());
        List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
        var byId=new LinkedHashMap<guru.interlis.model.SymbolId,DocumentSymbol>();
        for (Symbol symbol : state.analysis().semanticGraph().declarationsInOrder()) {
            if (symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                    && declared.range().source().equals(state.sourceId())
                    && symbol.nameRange().isPresent()) {
                DocumentSymbol documentSymbol = new DocumentSymbol(symbol.name().value(),
                        symbolKind(symbol),
                        toLspRange(state.source(), declared.range()),
                        toLspRange(state.source(), symbol.nameRange().get()));
                documentSymbol.setChildren(new ArrayList<>());
                byId.put(symbol.id(),documentSymbol);
            }
        }
        for(Symbol symbol:state.analysis().semanticGraph().declarationsInOrder()) {
            var item=byId.get(symbol.id()); if(item==null) continue;
            var parent=symbol.owner().map(byId::get).orElse(null);
            if(parent==null) result.add(Either.forRight(item)); else parent.getChildren().add(item);
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        var captured = copyParams(params, ReferenceParams.class);
        return requestReady((view, token) -> referencesNow(view, captured, token));
    }

    private CompletableFuture<List<? extends Location>> referencesNow(HostView view, ReferenceParams params, CancellationToken token) {
        Synchronized state = requireState(view, params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<SymbolRef> selected = queries.symbolAt(state.analysis(), cursor,
                token);
        if (!(selected instanceof QueryResult.Known<SymbolRef> known)) {
            return CompletableFuture.completedFuture(List.of());
        }
        boolean includeDeclaration = params.getContext() == null
                || params.getContext().isIncludeDeclaration();
        ReferenceResult result = queries.references(state.analysis(), known.value(),
                new ReferenceOptions(includeDeclaration), token);
        if(!result.complete()) return failed(ResponseErrorCode.RequestFailed,"workspace reference search is incomplete");
        List<Location> locations = new ArrayList<>();
        for (var occurrence : result.references()) {
            SourceText source = view.source(occurrence.range().source()).orElse(null);
            if(source==null || !source.revision().equals(occurrence.range().revision()))
                return failed(ResponseErrorCode.ContentModified,"CONTENT_MODIFIED: reference source changed");
            locations.add(new Location(view.uri(occurrence.range().source()),toLspRange(source,occurrence.range())));
        }
        return CompletableFuture.completedFuture(locations);
    }

    @Override
    public CompletableFuture<Either3<Range, org.eclipse.lsp4j.PrepareRenameResult,
            PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
        var captured = copyParams(params, PrepareRenameParams.class);
        return requestReady((view, token) -> prepareRenameNow(view, captured, token));
    }

    private CompletableFuture<Either3<Range, org.eclipse.lsp4j.PrepareRenameResult,
            PrepareRenameDefaultBehavior>> prepareRenameNow(HostView view, PrepareRenameParams params, CancellationToken token) {
        Synchronized state = requireState(view, params.getPosition(), params.getTextDocument());
        RenamePreparation preparation = queries.prepareRename(state.analysis(),
                cursor(state, params.getPosition()), token);
        if (!preparation.allowed()) {
            return failed(ResponseErrorCode.RequestFailed,
                    preparation.reason().orElse("rename is not allowed"));
        }
        SourceRange range = preparation.range().orElseThrow();
        if (!editable(view,range.source())) {
            return failed(ResponseErrorCode.RequestFailed,
                    "rename target is not an editable workspace source");
        }
        SourceText targetSource=view.source(range.source()).orElseThrow();
        return CompletableFuture.completedFuture(Either3.forFirst(
                toLspRange(targetSource, range)));
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        var captured = copyParams(params, RenameParams.class);
        return requestReady((view, token) -> renameNow(view, captured, token));
    }

    private CompletableFuture<WorkspaceEdit> renameNow(HostView view, RenameParams params, CancellationToken token) {
        Synchronized state = requireState(view, params.getPosition(), params.getTextDocument());
        if(params.getNewName()==null) return failed(ResponseErrorCode.InvalidParams,"newName is required");
        QueryResult<SymbolRef> selected = queries.symbolAt(state.analysis(),
                cursor(state, params.getPosition()), token);
        if (!(selected instanceof QueryResult.Known<SymbolRef> known)) {
            return failed(ResponseErrorCode.RequestFailed, "no editable symbol at cursor");
        }
        RenamePlan plan;
        try {
            plan = queries.planRename(state.analysis(), new RenameRequest(known.value(),
                    params.getNewName(), state.analysis().sourceRevisions()),
                    token);
        } catch (IllegalArgumentException invalidName) {
            return failed(ResponseErrorCode.InvalidParams, invalidName.getMessage());
        }
        if (!plan.complete()) {
            ResponseErrorCode code = plan.reason().orElse("").equals("CONTENT_MODIFIED")
                    ? ResponseErrorCode.ContentModified : ResponseErrorCode.RequestFailed;
            return failed(code, plan.reason().orElse("rename could not be planned"));
        }
        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        var versioned=new ArrayList<Either<TextDocumentEdit,org.eclipse.lsp4j.ResourceOperation>>();
        var closed=new ArrayList<ProjectSourceLoader.FileContent>();
        for (var document : plan.edits()) {
            if (!editable(view,document.source())) {
                return failed(ResponseErrorCode.RequestFailed,
                        "rename would modify an unavailable/read-only source: "
                                + document.source().value());
            }
            SourceText source = view.source(document.source()).orElse(null);
            if (source == null || !source.revision().equals(document.expected())) {
                return failed(ResponseErrorCode.ContentModified, "CONTENT_MODIFIED");
            }
            List<TextEdit> edits = document.edits().stream()
                    .map(edit -> new TextEdit(toLspRange(source, edit.range()),
                            edit.replacement()))
                    .toList();
            String uri=view.uri(document.source()); changes.put(uri, edits);
            var open=view.open(document.source());
            versioned.add(Either.forLeft(new TextDocumentEdit(new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(
                    uri,open==null?null:open.lspVersion()),edits)));
            if(open==null) closed.add(view.closedFiles().get(document.source().value()));
        }
        WorkspaceEdit edit = new WorkspaceEdit();
        if(view.versionedEdits()) { edit.setChanges(null); edit.setDocumentChanges(versioned); } else edit.setChanges(changes);
        return closed.isEmpty() ? CompletableFuture.completedFuture(edit)
                : LspFutures.map(verifyFiles.apply(List.copyOf(closed),token), ignored -> edit, operations);
    }

    private static boolean editable(HostView view,SourceId id) {
        if(view.externalUris().containsKey(id)) return false;
        var known=view.closedFiles().get(id.value());
        return view.open(id)!=null ? known==null || known.editable() : known!=null && known.editable();
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        var captured = copyParams(params, DocumentFormattingParams.class);
        return requestReady((view, token) -> formattingNow(view, captured, token));
    }

    private CompletableFuture<List<? extends TextEdit>> formattingNow(HostView view, DocumentFormattingParams params, CancellationToken token) {
        Synchronized state = requireState(view, new Position(0, 0), params.getTextDocument());
        if(!editable(view,state.sourceId())) return failed(ResponseErrorCode.RequestFailed,"readonly source cannot be formatted");
        FormatOptions options = LspFormattingOptions.decode(params.getOptions());
        FormatOutcome outcome = ModelFormatters.create().format(state.source(), options, token);
        if(outcome instanceof FormatOutcome.Cancelled cancelled) return failed(ResponseErrorCode.RequestCancelled,"formatting cancelled: "+cancelled.reason());
        if(outcome instanceof FormatOutcome.Rejected) return failed(ResponseErrorCode.RequestFailed,"source cannot be formatted without changing its meaning");
        var formatted = (FormatOutcome.Formatted) outcome;
        formatted.notices().stream().map(notice -> notice.code() + ": " + notice.message())
                .distinct().limit(3).forEach(this::operation);
        if (formatted.text().equals(state.source().text())) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(List.of(new TextEdit(
                toLspRange(state.source(), state.source().range(0, state.source().length())),
                formatted.text())));
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        var captured = copyParams(params, CodeActionParams.class);
        return requestReady((view, token) -> codeActionNow(view, captured, token));
    }

    private CompletableFuture<List<Either<Command, CodeAction>>> codeActionNow(HostView view, CodeActionParams params, CancellationToken token) {
        Synchronized state = requireState(view, params.getRange().getStart(), params.getTextDocument());
        if(!editable(view,state.sourceId())) return CompletableFuture.completedFuture(List.of());
        var result=new ArrayList<Either<Command,CodeAction>>();
        if(view.versionedEdits())for(var plan:new guru.interlis.compiler.query.EditorAssistanceService().quickFixes(state.analysis(),state.source().range(offsetAt(state.source(),params.getRange().getStart()),offsetAt(state.source(),params.getRange().getEnd())),token)) {
            var action=new CodeAction(plan.title());action.setKind("quickfix");action.setIsPreferred(true);
            var edits=plan.edits().stream().map(e->new TextEdit(toLspRange(state.source(),e.range()),e.replacement())).toList();
            var edit=new WorkspaceEdit();edit.setChanges(null);edit.setDocumentChanges(List.of(Either.forLeft(new TextDocumentEdit(new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(view.uri(state.sourceId()),state.lspVersion()),edits))));action.setEdit(edit);result.add(Either.forRight(action));
        }
        var only=params.getContext()==null?null:params.getContext().getOnly();
        if(only!=null&&!only.isEmpty())return CompletableFuture.completedFuture(result.stream().filter(action->action.isRight()&&only.stream().anyMatch(kind->kind.equals(action.getRight().getKind())||action.getRight().getKind().startsWith(kind+"."))).toList());
        return CompletableFuture.completedFuture(List.copyOf(result));
    }

    @Override public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(org.eclipse.lsp4j.DocumentOnTypeFormattingParams params) {
        var p=copyParams(params,org.eclipse.lsp4j.DocumentOnTypeFormattingParams.class);
        if(editorAssist||!"\n".equals(p.getCh()))return CompletableFuture.completedFuture(List.of());
        return requestReady((view,token)->{var state=requireState(view,p.getPosition(),p.getTextDocument());if(!editable(view,state.sourceId()))return CompletableFuture.completedFuture(List.of());
            var plan=new guru.interlis.compiler.query.EditorAssistanceService().planBlockClose(state.analysis(),cursor(state,p.getPosition()),new guru.interlis.compiler.query.EditorIndentOptions(p.getOptions().getTabSize(),p.getOptions().isInsertSpaces()),token);
            return CompletableFuture.completedFuture(plan.map(value->value.edits().stream().map(e->new TextEdit(toLspRange(state.source(),e.range()),e.replacement())).toList()).orElse(List.of()));});
    }

    void configurationChanged(Object settings) {
        scheduler.onOwner(() -> {
            guru.interlis.workspace.WorkspaceConfiguration configuration;
            RepositorySettings host;
            try { configuration=LspConfiguration.read(settings,userConfiguration); host=RepositorySettings.read(settings,repositorySettings); }
            catch(IllegalArgumentException | ArithmeticException invalid) {
                operation("Invalid INTERLIS configuration; previous configuration retained"); return null;
            }
            boolean hostChanged=!host.equals(repositorySettings);
            boolean repositoryChanged=!host.options().equals(repositorySettings.options()) || !host.cache().equals(repositorySettings.cache());
            boolean compilerChanged=!configuration.equals(userConfiguration);
            var current=workspace.configuration();
            List<String> expiredDocuments = List.of();
            if(repositoryChanged) {
                // An expired virtual editor must not reserve its old SourceId as
                // an active open alias when fresh navigation opens a new handle.
                for(var id:externalSources.uris().keySet()) workspace.source(id).ifPresent(source -> workspace.remove(id,source.revision()));
                expiredDocuments = documents.removeSources(externalSources::contains);
                externalSources.close();
                current=configuration;
            } else if(compilerChanged) {
                var ranks=new LinkedHashMap<>(current.resolutionPolicy().sourcePriorities());ranks.putAll(configuration.resolutionPolicy().sourcePriorities());
                current=new guru.interlis.workspace.WorkspaceConfiguration(configuration.compilerOptions(),
                        new guru.interlis.workspace.resolution.ResolutionPolicy(configuration.resolutionPolicy().preferWorkspaceSource(),configuration.resolutionPolicy().modelSelections(),ranks),
                        current.resolverCatalog(),configuration.cacheLimits(),current.resolutionDiagnostics());
            }
            var receipt=workspace.configure(current,workspace.state().revision());
            userConfiguration=configuration; applyHostSettings(host);
            if(receipt.changed() || hostChanged) { documents.advanceGeneration(); reanalyzeOpenNow(); }
            expiredDocuments.forEach(this::clearDocumentDiagnostics);
            return null;
        });
    }

    void initializeConfiguration(Object settings) {
        scheduler.onOwner(() -> {
            guru.interlis.workspace.WorkspaceConfiguration configuration;
            RepositorySettings host;
            try { configuration=LspConfiguration.read(settings,userConfiguration); host=RepositorySettings.read(settings,repositorySettings); }
            catch(IllegalArgumentException | ArithmeticException invalid) {
                throw LspFailures.error(ResponseErrorCode.InvalidParams,"invalid initial INTERLIS configuration");
            }
            workspace.configure(configuration,workspace.currentRevision()); userConfiguration=configuration; applyHostSettings(host); return null;
        });
    }

    void reloadExternal(String uri) { Objects.requireNonNull(externalReload,"project file service").accept(uri); }

    void reanalyzeOpen() {
        scheduler.onOwner(() -> { reanalyzeOpenNow(); return null; });
    }

    private void reanalyzeOpenNow() {
        documents.invalidateAnalysis();
        scheduleOpenAnalysis();
    }

    private void scheduleOpenAnalysis() {
        if (readiness.isReady() && !documents.needsResync()) scheduler.schedule();
        else scheduler.invalidate();
    }

    private static <T> T copyParams(T value, Class<T> type) {
        if(value==null) throw LspFailures.error(ResponseErrorCode.InvalidParams,"parameters are required");
        var gson = new com.google.gson.Gson();
        return gson.fromJson(gson.toJsonTree(value), type);
    }

    private static <T> CompletableFuture<T> failed(ResponseErrorCode code, String message) {
        return CompletableFuture.failedFuture(new ResponseErrorException(
                new ResponseError(code, message, null)));
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        var captured = copyParams(params, SemanticTokensParams.class);
        return requestReady((view, token) -> semanticTokensFullNow(view, captured, token));
    }

    private CompletableFuture<SemanticTokens> semanticTokensFullNow(HostView view, SemanticTokensParams params, CancellationToken token) {
        Synchronized state = requireState(view, new Position(0, 0), params.getTextDocument());
        List<Integer> data = semanticTokens(state,token);
        return CompletableFuture.completedFuture(new SemanticTokens(data));
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        var captured = copyParams(params, FoldingRangeRequestParams.class);
        return requestReady((view, token) -> foldingRangeNow(view, captured, token));
    }

    private CompletableFuture<List<FoldingRange>> foldingRangeNow(HostView view, FoldingRangeRequestParams params, CancellationToken token) {
        Synchronized state = requireState(view, new Position(0, 0), params.getTextDocument());
        List<FoldingRange> ranges = new ArrayList<>();
        for (Symbol symbol : state.analysis().semanticGraph().declarationsInOrder()) {
            token.throwIfCancellationRequested();
            if (symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                    && declared.range().source().equals(state.sourceId())) {
                var range = toLspRange(state.source(), declared.range());
                if (range.getEnd().getLine() > range.getStart().getLine()) {
                    ranges.add(new FoldingRange(range.getStart().getLine(),
                            range.getEnd().getLine()));
                }
            }
        }
        state.analysis().syntaxSnapshot(state.sourceId()).ifPresent(syntax -> {
            for(var trivia:syntax.trivia()) {
                token.throwIfCancellationRequested();
                if(trivia.kind()==guru.interlis.syntax.TriviaKind.WHITESPACE_GAP) continue;
                var range=toLspRange(state.source(),trivia.range());
                if(range.getEnd().getLine()>range.getStart().getLine()) {
                    var fold=new FoldingRange(range.getStart().getLine(),range.getEnd().getLine());
                    fold.setKind("comment"); ranges.add(fold);
                }
            }
        });
        ranges.sort(Comparator.comparingInt(FoldingRange::getStartLine)
                .thenComparingInt(FoldingRange::getEndLine));
        return CompletableFuture.completedFuture(ranges);
    }

    static final List<String> TOKEN_TYPES=List.of("keyword","number","string","enumMember","function","variable","property","namespace","class","type","comment");

    private List<Integer> semanticTokens(Synchronized state,CancellationToken cancellation) {
        record Span(SourceRange range,int type) { }
        var spans=new ArrayList<Span>();
        state.analysis().syntaxSnapshot(state.sourceId()).ifPresent(syntax -> {
            for(var token:syntax.tokens()) {
                cancellation.throwIfCancellationRequested();
                int type=switch(token.kind()) {
                    case KEYWORD -> 0; case NUMBER -> 1; case STRING -> 2;
                    case IDENTIFIER -> {
                        var result=queries.symbolAt(state.analysis(),state.source().range(token.range().startOffset(),token.range().startOffset()),cancellation);
                        if(result instanceof QueryResult.Known<SymbolRef> known) {
                            var symbol=state.analysis().semanticGraph().symbol(known.value().symbol()).orElseThrow();
                            yield switch(symbol.kind()) {
                                case MODEL,TOPIC,CONTEXT,METADATA_BASKET -> 7;
                                case CLASS,STRUCTURE,ASSOCIATION,VIEW -> 8;
                                case DOMAIN,UNIT,LINE_FORM -> 9;
                                case FUNCTION -> 4; case ENUM_ELEMENT -> 3;
                                case ATTRIBUTE,ROLE -> 6; default -> 5;
                            };
                        }
                        yield 5;
                    }
                    default -> -1;
                };
                if(type>=0) spans.add(new Span(token.range(),type));
            }
            for(var trivia:syntax.trivia()) if(trivia.kind()!=guru.interlis.syntax.TriviaKind.WHITESPACE_GAP) spans.add(new Span(trivia.range(),10));
        });
        spans.sort(Comparator.comparingInt(span -> span.range().startOffset()));
        List<Integer> data = new ArrayList<>();
        int lastLine = 0;
        int lastCharacter = 0;
        var lines=state.source().lineMap();
        for(var span:spans) {
            int first=lines.positionAt(span.range().startOffset()).line();
            int last=lines.positionAt(span.range().endOffset()).line();
            for(int line=first;line<=last;line++) {
                cancellation.throwIfCancellationRequested();
                int start=Math.max(span.range().startOffset(),lines.lineStart(line));
                int end=Math.min(span.range().endOffset(),lines.lineContentEnd(line));
                if(end<=start) continue;
                int character=start-lines.lineStart(line),deltaLine=line-lastLine;
                data.add(deltaLine); data.add(deltaLine==0?character-lastCharacter:character);
                data.add(end-start); data.add(span.type()); data.add(0);
                lastLine=line; lastCharacter=character;
            }
        }
        return data;
    }

    CompletableFuture<String> diagram(DiagramParams params) {
        return requestReady((view, token) -> diagramNow(view, params, token));
    }

    private CompletableFuture<String> diagramNow(HostView view, DiagramParams params, CancellationToken token) {
        Synchronized state = requireState(view, new Position(0, 0), new TextDocumentIdentifier(params.uri()));
        if (params.snapshotId() != null
                && !params.snapshotId().equals(state.analysis().snapshotId().value())) {
            return CompletableFuture.failedFuture(new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                    new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                            org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
                            "snapshotId is stale for the requested document", null)));
        }
        var request=new DiagramRequest(1,"legacy-command",new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(params.uri(),state.lspVersion()),params.symbolIds(),null);
        var response=diagrams.project(request,diagramContext(view,state,params.uri()),token);
        return CompletableFuture.completedFuture(new DiagramJsonCodec().encode(response.document()));
    }

    CompletableFuture<DiagramResponse> diagramRequest(DiagramRequest params) {
        if(params==null||params.textDocument()==null||params.textDocument().getVersion()==null)return failed(ResponseErrorCode.InvalidParams,"diagram requires a versioned document");
        var captured=copyParams(params,DiagramRequest.class);
        return requestReady((view,token)-> {
            var state=requireState(view,new Position(0,0),captured.textDocument());
            if(state.lspVersion()!=captured.textDocument().getVersion())return failed(ResponseErrorCode.ContentModified,"diagram document version changed");
            return CompletableFuture.completedFuture(diagrams.project(captured,diagramContext(view,state,captured.textDocument().getUri()),token));
        });
    }

    CompletableFuture<DiagramNavigation> diagramNavigate(DiagramNavigationParams params) {
        if(params==null||params.textDocument()==null||params.textDocument().getVersion()==null)return failed(ResponseErrorCode.InvalidParams,"diagram navigation requires a versioned root document");
        var captured=copyParams(params,DiagramNavigationParams.class);
        return requestReady((view,token)-> {
            var state=requireState(view,new Position(0,0),captured.textDocument());
            if(state.lspVersion()!=captured.textDocument().getVersion())return failed(ResponseErrorCode.ContentModified,"diagram document version changed");
            var anchor=diagrams.navigation(captured,diagramContext(view,state,captured.textDocument().getUri()));
            var range=anchor.nameRange();var source=view.source(range.source()).orElse(null);
            if(source==null||!source.revision().equals(range.revision()))return failed(ResponseErrorCode.ContentModified,"diagram source revision changed");
            var result=new DiagramNavigation(diagrams.serverInstanceId(),anchor.snapshotId().value(),view.uri(range.source()),toLspRange(source,range),!editable(view,range.source()));
            var closed=view.closedFiles().get(range.source().value());
            return closed!=null&&view.open(range.source())==null?LspFutures.map(verifyFiles.apply(List.of(closed),token), ignored -> result, operations):CompletableFuture.completedFuture(result);
        });
    }

    CompletableFuture<DiagramSelectionResult> diagramSelect(DiagramSelectionParams params) {
        if(params==null||params.textDocument()==null||params.textDocument().getVersion()==null
                ||params.sourceDocument()==null||params.sourceDocument().getVersion()==null||params.position()==null)
            return failed(ResponseErrorCode.InvalidParams,"diagram selection requires exact root/source versions and a position");
        var captured=copyParams(params,DiagramSelectionParams.class);
        return requestReady((view,token)-> {
            var root=requireState(view,new Position(0,0),captured.textDocument());
            var source=requireState(view,captured.position(),captured.sourceDocument());
            if(root.lspVersion()!=captured.textDocument().getVersion()||source.lspVersion()!=captured.sourceDocument().getVersion())
                return failed(ResponseErrorCode.ContentModified,"source selection document version changed");
            var selected=queries.symbolAt(source.analysis(),cursor(source,captured.position()),token);
            SymbolRef symbol=selected instanceof QueryResult.Known<SymbolRef> known?known.value():null;
            token.throwIfCancellationRequested();
            return CompletableFuture.completedFuture(diagrams.selection(captured,diagramContext(view,root,captured.textDocument().getUri()),symbol));
        });
    }

    private static Map<String,Integer> documentVersions(HostView view) {
        var result=new LinkedHashMap<String,Integer>();view.documents().forEach((uri,state)->result.put(uri,state.lspVersion()));return Map.copyOf(result);
    }
    private static LspDiagramService.Context diagramContext(HostView view,Synchronized state,String uri) {
        return new LspDiagramService.Context(state.analysis(),state.sourceId(),uri,state.lspVersion(),view.workspaceRevision(),documentVersions(view));
    }

    private static Synchronized requireState(HostView view,Position position, TextDocumentIdentifier document) {
        if(document==null || document.getUri()==null || document.getUri().isBlank())
            throw LspFailures.error(ResponseErrorCode.InvalidParams,"document URI is required");
        String uri = document.getUri();
        var direct=view.documents().get(uri);
        Synchronized state = direct!=null?direct:view.open(new SourceId(uri));
        if (state == null) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams,
                    "document is not available", null));
        }
        if(uri.startsWith("interlis-source:") && !uri.equals(view.externalUris().get(state.sourceId())))
            throw LspFailures.error(ResponseErrorCode.ContentModified,"readonly source handle expired; navigate again");
        if (view.source(state.sourceId()).filter(source -> source.revision().equals(state.source().revision())).isEmpty()
                || state.currentAnalysis().isEmpty()) throw new ResponseErrorException(new ResponseError(
                ResponseErrorCode.ContentModified, "CONTENT_MODIFIED: document revision changed", null));
        PositionMapper.toOffset(state.source(), position);
        return state;
    }

    private void analyze(String uri, SourceId sourceId, int version) {
        SourceText source = workspace.source(sourceId).orElseThrow();
        var retiredAlias = documents.synchronize(uri, version, source);
        scheduleOpenAnalysis();
        retiredAlias.ifPresent(this::clearDocumentDiagnostics);
    }

    private void applyHostSettings(RepositorySettings settings) {
        repositorySettings=settings; scheduler.repositorySettings(settings);
        diagnosticLanguage=switch(settings.diagnosticLanguage()) {
            case "de" -> guru.interlis.diagnostics.DiagnosticLanguage.DE;
            case "en" -> guru.interlis.diagnostics.DiagnosticLanguage.EN;
            default -> localeLanguage;
        };
    }

    private void repositoryResolved(guru.interlis.repository.RepositoryResolutionOutcome result) {
        repositoryFailure=result instanceof guru.interlis.repository.RepositoryResolutionOutcome.Rejected rejected?rejected:null;
        if(result instanceof guru.interlis.repository.RepositoryResolutionOutcome.Prepared prepared) {
            for(var update:prepared.update().sources()) workspace.source(update.source()).ifPresent(source ->
                    externalSources.register(source,workspace.configuration().compilerOptions().budget().maxTotalSourceUtf16Length()));
        }
    }

    CompletableFuture<SourceContent> sourceContent(SourceContentParams params) {
        try { requestGuard.run();
            return CompletableFuture.completedFuture(scheduler.onOwner(() -> {
                if(params==null || params.handle()==null || params.expectedRevision()==null) throw PositionMapper.invalid();
                var entry=externalSources.read(params.handle(),params.expectedRevision());
                return new SourceContent(entry.uri(),Long.toString(entry.source().revision().value()),entry.source().text(),true,
                        "repository",entry.digest(),"SHA-256/UTF-16BE");
            }));
        } catch(RuntimeException invalid) { return CompletableFuture.failedFuture(LspFailures.response(invalid,operations)); }
    }

    private void accepted(AnalysisSnapshot analysis) {
        if (!readiness.isReady() || documents.needsResync()) return;
        long generation = documents.generation();
        publishClosedDiagnostics(analysis);
        for(var diagnostic:analysis.diagnostics()) if(diagnostic.primaryRange().isEmpty())
            operation(diagnosticMapper.global(diagnostic,diagnosticLanguage));
        for (String uri : documents.synchronizedDocuments().keySet()) {
            if (documents.generation() != generation) return;
            var previous = documents.synchronizedDocument(uri);
            var source = workspace.source(previous.sourceId());
            if (source.isEmpty() || !source.orElseThrow().revision().equals(previous.source().revision())
                    || !source.orElseThrow().revision().equals(analysis.sourceRevisions().get(previous.sourceId()))) continue;
            publish(uri, documents.acceptAnalysis(uri, previous, analysis));
        }
        if (documents.generation() != generation) return;
        var view=captureView();
        analysisReady.accept(new AnalysisReady(1,diagrams.serverInstanceId(),view.workspaceRevision(),analysis.snapshotId().value(),documentVersions(view)));
    }

    private void publishClosedDiagnostics(AnalysisSnapshot analysis) {
        var current=sink.get();if(current==null) return;
        var view=captureView();var sources=new LinkedHashMap<>(view.sources());
        var diagnostics=new ArrayList<>(analysis.diagnostics());
        if(repositoryFailure!=null) {
            repositoryFailure.sources().forEach((id,source) -> {
                sources.putIfAbsent(id,source);
                if(!view.sources().containsKey(id)) externalSources.register(source,workspace.configuration().compilerOptions().budget().maxTotalSourceUtf16Length());
            });
            diagnostics.addAll(repositoryFailure.diagnostics());
        }
        var externalUris=externalSources.uris();
        java.util.function.Function<SourceId,String> uri=id -> externalUris.getOrDefault(id,view.uri(id));
        var byUri=new LinkedHashMap<String,List<org.eclipse.lsp4j.Diagnostic>>();
        for(var d:diagnostics) {
            if(d.primaryRange().isEmpty()) { if(repositoryFailure!=null && repositoryFailure.diagnostics().contains(d)) operation(diagnosticMapper.global(d,diagnosticLanguage));continue; }
            var range=d.primaryRange().orElseThrow();
            if(view.open(range.source())!=null) continue;
            var source=sources.get(range.source());
            if(source==null || !source.revision().equals(range.revision())) continue;
            byUri.computeIfAbsent(uri.apply(range.source()),ignored -> new ArrayList<>()).add(diagnosticMapper.toLsp(d,sources,diagnosticLanguage,uri));
        }
        for(String old:publishedExternal) if(!byUri.containsKey(old)) current.publish(new PublishDiagnosticsParams(old,List.of()));
        byUri.forEach((address,items) -> current.publish(new PublishDiagnosticsParams(address,items)));
        publishedExternal.clear();publishedExternal.addAll(byUri.keySet());
    }

    private void publish(String uri,Synchronized state) {
        DiagnosticSink current = sink.get();
        if (current == null) {
            return;
        }
        List<org.eclipse.lsp4j.Diagnostic> diagnostics = new ArrayList<>();
        var displayed=new ArrayList<>(state.analysis().diagnostics());
        if(repositoryFailure!=null) displayed.addAll(repositoryFailure.diagnostics());
        for (Diagnostic diagnostic : displayed) {
            if (diagnostic.primaryRange().isEmpty()) {
                continue;
            }
            SourceRange range = diagnostic.primaryRange().get();
            if (!range.source().equals(state.sourceId())) {
                continue;
            }
            var view=captureView();
            org.eclipse.lsp4j.Diagnostic lsp=diagnosticMapper.toLsp(diagnostic,view.sources(),diagnosticLanguage,view::uri);
            diagnostics.add(lsp);
        }
        diagnostics.sort(Comparator.comparingInt((org.eclipse.lsp4j.Diagnostic d) ->
                        d.getRange().getStart().getLine())
                .thenComparingInt(d -> d.getRange().getStart().getCharacter()));
        var publication = new PublishDiagnosticsParams(uri, diagnostics);
        publication.setVersion(state.lspVersion());
        current.publish(publication);
    }

    void locale(String locale) {
        scheduler.onOwner(() -> { localeLanguage=locale!=null && locale.toLowerCase(java.util.Locale.ROOT).startsWith("de")
                ? guru.interlis.diagnostics.DiagnosticLanguage.DE : guru.interlis.diagnostics.DiagnosticLanguage.EN;
            applyHostSettings(repositorySettings); return null; });
    }

    private static String materialize(SourceText base, List<TextDocumentContentChangeEvent> changes) {
        if(changes==null) throw PositionMapper.invalid();
        String text = base.text();
        for (TextDocumentContentChangeEvent change : changes) {
            if(change==null || change.getText()==null) throw PositionMapper.invalid();
            if (change.getRange() == null) {
                text = change.getText();
            } else {
                // Each delta refers to the intermediate state produced by the
                // previous delta, not to the original document.
                SourceText intermediate = new SourceText(base.id(), base.revision(), text);
                int start = offsetAt(intermediate, change.getRange().getStart());
                int end = offsetAt(intermediate, change.getRange().getEnd());
                if (start > end || (change.getRangeLength() != null
                        && change.getRangeLength() != end - start)) throw PositionMapper.invalid();
                text = text.substring(0, start) + change.getText() + text.substring(end);
            }
        }
        return text;
    }

    private static SourceRange cursor(Synchronized state, Position position) {
        int offset = offsetAt(state.source(), position);
        return state.source().range(offset, offset);
    }

    private static int offsetAt(SourceText source, Position position) {
        return PositionMapper.toOffset(source, position);
    }

    private static Range toLspRange(SourceText source, SourceRange range) {
        return PositionMapper.toLspRange(source, range);
    }

    private static String prefixAt(String text, int offset) {
        int start = offset;
        while (start > 0 && (Character.isLetterOrDigit(text.charAt(start - 1))
                || text.charAt(start - 1) == '_')) {
            start--;
        }
        return text.substring(start, Math.min(offset, text.length()));
    }

    private static CompletionItemKind completionKind(CompletionCandidate candidate) {
        return switch (candidate.kind()) {
            case CLASS, STRUCTURE, ASSOCIATION, VIEW -> CompletionItemKind.Class;
            case DOMAIN, LINE_FORM -> CompletionItemKind.TypeParameter;
            case ATTRIBUTE, ROLE -> CompletionItemKind.Field;
            case PARAMETER, METAOBJECT, VIEW_BASE -> CompletionItemKind.Variable;
            case METADATA_BASKET, GRAPHIC -> CompletionItemKind.Module;
            case DRAWING_RULE -> CompletionItemKind.Method;
            case TOPIC, MODEL, CONTEXT -> CompletionItemKind.Module;
            case FUNCTION -> CompletionItemKind.Function;
            case UNIT -> CompletionItemKind.Unit;
            case ENUM_ELEMENT -> CompletionItemKind.EnumMember;
            case CONSTRAINT -> CompletionItemKind.Keyword;
            case KEYWORD -> CompletionItemKind.Keyword;
        };
    }

    private static SymbolKind symbolKind(Symbol symbol) {
        return switch (symbol.kind()) {
            case MODEL -> SymbolKind.Module;
            case TOPIC -> SymbolKind.Namespace;
            case CLASS -> SymbolKind.Class;
            case STRUCTURE -> SymbolKind.Struct;
            case ASSOCIATION -> SymbolKind.Interface;
            case DOMAIN -> SymbolKind.TypeParameter;
            case ATTRIBUTE -> SymbolKind.Field;
            case PARAMETER -> SymbolKind.Variable;
            case ROLE -> SymbolKind.Field;
            case CONSTRAINT -> SymbolKind.Key;
            default -> SymbolKind.Object;
        };
    }

}
