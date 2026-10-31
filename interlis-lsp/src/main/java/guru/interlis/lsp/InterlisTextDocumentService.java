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
    private final Map<String, DocumentState> documents = new LinkedHashMap<>();
    private final java.util.Set<String> unsynchronized = new java.util.HashSet<>();
    private record RejectedOpen(SourceId sourceId,int version) { }
    private final Map<String,RejectedOpen> rejectedOpens=new LinkedHashMap<>();
    private volatile java.util.function.Consumer<String> operations = ignored -> { };
    private final AnalysisScheduler scheduler;
    private final Map<String,ProjectSourceLoader.FileContent> closedFiles = new LinkedHashMap<>();
    private final Map<java.nio.file.Path,String> fileIdentities=new LinkedHashMap<>();
    private final Map<Object,String> fileKeys=new LinkedHashMap<>();
    private final java.util.ArrayDeque<Runnable> documentOperations=new java.util.ArrayDeque<>();
    private boolean resolvingOpen;
    private long nextOpen;
    private java.util.function.BiFunction<String,Map<SourceId,String>,CompletableFuture<ProjectSourceLoader.IdentityMatch>> identifyFile=
            (uri,known) -> CompletableFuture.completedFuture(new ProjectSourceLoader.IdentityMatch(Optional.empty(),Optional.empty()));
    private volatile java.util.function.Consumer<String> externalReload;
    private java.util.function.BiFunction<List<ProjectSourceLoader.FileContent>,CancellationToken,CompletableFuture<Void>> verifyFiles =
            (files,token) -> failed(ResponseErrorCode.RequestFailed,"project file service is not initialized");
    private boolean versionedEdits;
    private long documentGeneration;
    private final Map<String,Long> pendingFiles=new LinkedHashMap<>();
    private CompletableFuture<Void> filesReady=CompletableFuture.completedFuture(null);
    private Throwable filesFailure;
    private final java.util.concurrent.atomic.AtomicInteger waitingRequests=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.Set<CompletableFuture<?>> fileWaiters=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final LanguageQueries queries = LanguageQueries.create();
    private final AtomicReference<DiagnosticSink> sink = new AtomicReference<>();
    private Runnable requestGuard=() -> { };
    void requestGuard(Runnable guard) { requestGuard=Objects.requireNonNull(guard); }

    InterlisTextDocumentService(Workspace workspace, guru.interlis.compiler.CompilerEngine engine,
                                guru.interlis.compiler.CompilerServices services) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        scheduler = new AnalysisScheduler(workspace, engine, services, this::accepted,
                message -> operations.accept(message));
    }

    void sink(DiagnosticSink sink) {
        this.sink.set(sink);
    }

    void operationSink(java.util.function.Consumer<String> sink) {
        operations = Objects.requireNonNull(sink, "sink");
    }

    void close() { scheduler.close(() -> {
        fileWaiters.forEach(result -> result.completeExceptionally(LspFailures.error(ResponseErrorCode.RequestCancelled,"server is closing")));
        fileWaiters.clear();
        filesReady.completeExceptionally(LspFailures.error(ResponseErrorCode.RequestCancelled,"server is closing"));
        pendingFiles.clear(); closedFiles.clear(); fileIdentities.clear(); fileKeys.clear(); documentOperations.clear(); documents.clear(); rejectedOpens.clear(); unsynchronized.clear();
    }); }

    CompletableFuture<guru.interlis.compiler.CompilationOutcome> compileWorkspace() {
        try { requestGuard.run(); } catch(ResponseErrorException invalid) { return CompletableFuture.failedFuture(invalid); }
        return afterFiles(() -> unsynchronized.isEmpty()?scheduler.compile():failed(ResponseErrorCode.ContentModified,"full document resynchronization is required"));
    }

    private <T> CompletableFuture<T> requestReady(java.util.function.BiFunction<HostView,CancellationToken,CompletableFuture<T>> action) {
        try { requestGuard.run(); } catch(ResponseErrorException invalid) { return CompletableFuture.failedFuture(invalid); }
        return afterFiles(() -> unsynchronized.isEmpty()?scheduler.requestNow(token -> {
            HostView view=captureView();
            return scheduler.detached(token,() -> action.apply(view,token));
        }):failed(ResponseErrorCode.ContentModified,"full document resynchronization is required"));
    }

    private record HostView(Map<String,DocumentState> documents,Map<SourceId,SourceText> sources,
                            Map<String,ProjectSourceLoader.FileContent> closedFiles,java.util.Set<String> unsynchronized,
                            boolean versionedEdits) {
        Optional<SourceText> source(SourceId id) { return Optional.ofNullable(sources.get(id)); }
        DocumentState open(SourceId id) { return documents.values().stream().filter(state -> state.sourceId.equals(id)).findFirst().orElse(null); }
        String uri(SourceId id) { return documents.entrySet().stream().filter(entry -> entry.getValue().sourceId.equals(id)).map(Map.Entry::getKey).findFirst()
                .orElseGet(() -> closedFiles.containsKey(id.value())?closedFiles.get(id.value()).path().toUri().toString():id.value()); }
    }

    private HostView captureView() {
        var sources=new LinkedHashMap<SourceId,SourceText>();
        for(var id:workspace.state().sourceRevisions().keySet()) workspace.source(id).ifPresent(source -> sources.put(id,source));
        return new HostView(Map.copyOf(documents),Map.copyOf(sources),Map.copyOf(closedFiles),java.util.Set.copyOf(unsynchronized),versionedEdits);
    }

    private <T> CompletableFuture<T> afterFiles(java.util.function.Supplier<CompletableFuture<T>> start) {
        if(waitingRequests.incrementAndGet()>128) {
            waitingRequests.decrementAndGet(); return failed(ResponseErrorCode.RequestFailed,"language request queue is full");
        }
        var active=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<T>>();
        var result=new CompletableFuture<T>() {
            @Override public boolean cancel(boolean interrupt) {
                boolean changed=super.cancel(interrupt); var running=active.get();
                if(changed && running!=null) running.cancel(interrupt); return changed;
            }
        };
        fileWaiters.add(result);
        result.whenComplete((ignored,failure) -> { waitingRequests.decrementAndGet(); fileWaiters.remove(result); });
        try { scheduler.onOwner(() -> { orderedDocumentOperation(() -> {
            long captured=documentGeneration;
            filesReady.whenComplete((ignored,failure) -> {
                if(result.isDone()) return;
                try { scheduler.onOwner(() -> {
                    if(captured!=documentGeneration) result.completeExceptionally(LspFailures.error(ResponseErrorCode.ContentModified,"CONTENT_MODIFIED: workspace inputs changed"));
                    else if(failure!=null) result.completeExceptionally(LspFailures.response(failure,operations));
                    else {
                        var current=start.get(); active.set(current);
                        if(result.isCancelled()) current.cancel(false);
                        current.whenComplete((value,problem) -> {
                            if(problem!=null) result.completeExceptionally(LspFailures.response(problem,operations));
                            else result.complete(value);
                        });
                    }
                    return null;
                }); } catch(RuntimeException closed) { result.completeExceptionally(LspFailures.response(closed,operations)); }
            });
        }); return null;
        }); } catch(RuntimeException failure) { result.completeExceptionally(LspFailures.response(failure,operations)); }
        return result;
    }

    boolean isOpen(String uri) { return scheduler.onOwner(() -> openSource(new SourceId(canonicalUriNow(uri)))); }
    String canonicalUri(String uri) { return scheduler.onOwner(() -> canonicalUriNow(uri)); }
    Map<SourceId,String> fileSources() {
        return scheduler.onOwner(() -> {
            var result=new LinkedHashMap<SourceId,String>();
            closedFiles.forEach((uri,file) -> result.put(new SourceId(uri),file.path().toUri().toString()));
            documents.forEach((uri,state) -> result.put(state.sourceId,uri)); rejectedOpens.forEach((uri,state) -> result.put(state.sourceId,uri)); return Map.copyOf(result);
        });
    }
    List<String> fileAliases(String uri) {
        return scheduler.onOwner(() -> {
            String canonical=canonicalUriNow(uri);
            var paths=new java.util.LinkedHashSet<String>();
            var primary=closedFiles.get(canonical);
            if(primary!=null) paths.add(primary.path().toUri().toString());
            fileIdentities.forEach((path,id) -> { if(id.equals(canonical)) paths.add(path.toUri().toString()); });
            return List.copyOf(paths);
        });
    }
    private boolean openSource(SourceId id) { return documents.values().stream().anyMatch(state -> state.sourceId.equals(id)) || rejectedOpens.values().stream().anyMatch(state -> state.sourceId.equals(id)); }
    private static Optional<java.nio.file.Path> filePath(String uri) {
        try { var value=java.net.URI.create(uri); return "file".equalsIgnoreCase(value.getScheme())
                ? Optional.of(java.nio.file.Path.of(value).toAbsolutePath().normalize()) : Optional.empty(); }
        catch(IllegalArgumentException invalid) { return Optional.empty(); }
    }
    private String canonicalUriNow(String uri) {
        return filePath(uri).map(path -> fileIdentities.getOrDefault(path,uri)).orElse(uri);
    }
    private String rememberFile(ProjectSourceLoader.FileContent file,Optional<SourceId> openAlias) {
        String uri=openAlias.map(SourceId::value).orElseGet(() -> file.fileKey().map(fileKeys::get).orElseGet(() ->
                fileIdentities.getOrDefault(file.realPath(),canonicalUriNow(file.path().toUri().toString()))));
        int added=(fileIdentities.containsKey(file.path())?0:1)
                +(file.path().equals(file.realPath()) || fileIdentities.containsKey(file.realPath())?0:1);
        if(fileIdentities.size()+added>40_000)
            throw LspFailures.error(ResponseErrorCode.RequestFailed,"file identity limit reached");
        fileIdentities.put(file.path(),uri); fileIdentities.put(file.realPath(),uri);
        fileKeys.values().removeIf(uri::equals);
        file.fileKey().ifPresent(key -> fileKeys.put(key,uri)); return uri;
    }

    void beginExternal(String uri,long request) {
        scheduler.onOwner(() -> {
            documentGeneration++;
            if(pendingFiles.isEmpty()) { filesReady=new CompletableFuture<>(); filesFailure=null; }
            pendingFiles.put(uri,request); scheduler.invalidate(); return null;
        });
    }

    private void finishExternal(String uri,long request,Throwable failure) {
        if(!Objects.equals(pendingFiles.get(uri),request)) return;
        if(failure!=null && filesFailure==null) filesFailure=failure;
        pendingFiles.remove(uri);
        if(pendingFiles.isEmpty()) {
            reanalyzeOpenNow();
            var completed=filesReady;
            filesReady=CompletableFuture.completedFuture(null);
            if(filesFailure==null) completed.complete(null); else completed.completeExceptionally(LspFailures.response(filesFailure,operations));
            filesFailure=null;
        }
    }

    void externalFailed(String uri,long request,Throwable failure) {
        scheduler.onOwner(() -> {
            String canonical=canonicalUriNow(uri);
            if(Objects.equals(pendingFiles.get(uri),request) && !openSource(new SourceId(canonical))) {
                closedFiles.remove(canonical); deleteExternalNow(canonical);
            }
            finishExternal(uri,request,failure); return null;
        });
    }

    void fileServices(java.util.function.Consumer<String> reload,
            java.util.function.BiFunction<List<ProjectSourceLoader.FileContent>,CancellationToken,CompletableFuture<Void>> verify,
            java.util.function.BiFunction<String,Map<SourceId,String>,CompletableFuture<ProjectSourceLoader.IdentityMatch>> identify) {
        externalReload=Objects.requireNonNull(reload);
        scheduler.onOwner(() -> { verifyFiles=Objects.requireNonNull(verify); identifyFile=Objects.requireNonNull(identify); return null; });
    }

    void versionedEdits(boolean supported) { scheduler.onOwner(() -> { versionedEdits=supported; return null; }); }

    void installProject(ProjectSourceLoader.Project project,java.util.function.Predicate<String> current) {
        scheduler.onOwner(() -> {
            var updates=new ArrayList<SourceUpdate>();
            var acceptedFiles=new LinkedHashMap<String,ProjectSourceLoader.FileContent>();
            for(var file:project.files()) {
                var source=file.loaded(); if(!current.test(source.sourceId().value())) continue;
                String primary=project.primaryPaths().get(file.path()).toUri().toString();
                String uri=rememberFile(file,Optional.of(new SourceId(canonicalUriNow(primary)))); var id=new SourceId(uri);
                if(acceptedFiles.putIfAbsent(uri,file)!=null || openSource(id)) continue;
                var previous=workspace.source(id);
                updates.add(new SourceUpdate(id,previous.<SourceExpectation>map(value -> new SourceExpectation.At(value.revision()))
                        .orElseGet(SourceExpectation.Absent::new),new SourceChange.Replace(source.text())));
            }
            workspace.applyBatch(updates,workspace.currentRevision());
            closedFiles.putAll(acceptedFiles); reanalyzeOpenNow(); return null;
        });
    }

    void applyExternal(String uri,long request,Optional<ProjectSourceLoader.FileContent> file,Optional<SourceId> openAlias,java.util.function.BooleanSupplier current) {
        scheduler.onOwner(() -> {
            if(!current.getAsBoolean()) return null;
            String canonical=file.map(value -> rememberFile(value,openAlias)).orElseGet(() -> canonicalUriNow(uri));
            if(openSource(new SourceId(canonical))) { finishExternal(uri,request,null); return null; }
            if(file.isPresent()) {
                closedFiles.put(canonical,file.orElseThrow());
            } else closedFiles.remove(canonical);
            if(file.isEmpty()) deleteExternalNow(canonical);
            else {
                var id=new SourceId(canonical); var previous=workspace.source(id);
                workspace.update(new SourceUpdate(id,previous.<SourceExpectation>map(value -> new SourceExpectation.At(value.revision()))
                        .orElseGet(SourceExpectation.Absent::new),new SourceChange.Replace(file.get().loaded().text())));
            }
            finishExternal(uri,request,null); return null;
        });
    }

    void operation(String message) { try { operations.accept(message); } catch(RuntimeException unavailable) { /* disconnected client */ } }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        if(!notificationReady()) return;
        var captured=copyParams(params,DidOpenTextDocumentParams.class);
        scheduler.onOwner(() -> { orderedDocumentOperation(() -> openWithIdentity(captured)); return null; });
    }

    /** Keeps document notifications and request barriers ordered while bounded IO resolves an unknown URI. */
    private void orderedDocumentOperation(Runnable operation) {
        if(!resolvingOpen) { operation.run(); return; }
        if(documentOperations.size()>=256) throw LspFailures.error(ResponseErrorCode.RequestFailed,"document operation queue is full");
        documentOperations.addLast(operation);
    }

    private void openWithIdentity(DidOpenTextDocumentParams params) {
        openWithIdentity(params,0);
    }

    private void openWithIdentity(DidOpenTextDocumentParams params,int retries) {
        String uri=params.getTextDocument().getUri();
        var known=fileSources();
        if(filePath(uri).isEmpty() || !canonicalUriNow(uri).equals(uri) || known.isEmpty()) { didOpenNow(params); return; }
        resolvingOpen=true; long request=++nextOpen; String pending="open-identity:"+request;
        beginExternal(pending,request);
        identifyFile.apply(uri,known).whenComplete((match,failure) -> {
            try { scheduler.onOwner(() -> {
                if(failure==null && !known.equals(fileSources())) {
                    // A watcher may have admitted a hardlink while IO ran, even
                    // on providers without file keys. Re-resolve that new set.
                    var changed=retries<3?null:LspFailures.error(ResponseErrorCode.ContentModified,"file identities kept changing; reopen required");
                    finishExternal(pending,request,changed); resolvingOpen=false;
                    if(retries<3) { openWithIdentity(params,retries+1); return null; }
                    unsynchronized.add(uri); operation("Document identities kept changing; reopen required");
                    while(!resolvingOpen && !documentOperations.isEmpty()) documentOperations.removeFirst().run();
                    return null;
                }
                Throwable problem=failure;
                try {
                    if(problem==null) {
                        var identity=match.identity();
                        String canonical=identity.flatMap(value -> value.fileKey().map(fileKeys::get)
                                .or(() -> Optional.ofNullable(fileIdentities.get(value.realPath()))))
                                .or(() -> match.source().map(SourceId::value)).orElse(uri);
                        identity.ifPresent(value -> {
                            int added=(fileIdentities.containsKey(value.path())?0:1)
                                    +(value.path().equals(value.realPath()) || fileIdentities.containsKey(value.realPath())?0:1);
                            if(fileIdentities.size()+added>40_000)
                                throw LspFailures.error(ResponseErrorCode.RequestFailed,"file identity limit reached");
                            fileIdentities.put(value.path(),canonical); fileIdentities.put(value.realPath(),canonical);
                            value.fileKey().ifPresent(key -> fileKeys.put(key,canonical));
                        });
                        didOpenNow(params);
                    } else operation("Document identity lookup failed; reopen required");
                } catch(RuntimeException rejected) { problem=rejected; operation("Document identity admission failed; reopen required"); }
                finally { finishExternal(pending,request,problem); resolvingOpen=false; }
                while(!resolvingOpen && !documentOperations.isEmpty()) documentOperations.removeFirst().run();
                return null;
            }); } catch(RuntimeException closed) { operation("Document identity operation stopped"); }
        });
    }

    private void didOpenNow(DidOpenTextDocumentParams params) {
        documentGeneration++;
        String uri=params.getTextDocument().getUri();
        SourceId id=new SourceId(canonicalUriNow(uri));
        if(documents.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(uri) && entry.getValue().sourceId.equals(id))) {
            operation("Document is already open under another URI"); return;
        }
        try { replaceSource(id,params.getTextDocument().getText()); }
        catch(guru.interlis.workspace.UpdateRejectedException rejected) {
            rejectDocument(uri,id,params.getTextDocument().getVersion(),rejected.code().name()); return;
        }
        rejectedOpens.remove(uri); unsynchronized.remove(uri);
        analyze(uri,params.getTextDocument().getVersion());
    }

    private void replaceSource(SourceId id,String text) {
        var existing=workspace.source(id);
        workspace.update(new SourceUpdate(id,existing.<SourceExpectation>map(source -> new SourceExpectation.At(source.revision()))
                .orElseGet(SourceExpectation.Absent::new),new SourceChange.Replace(text)));
    }

    private void rejectDocument(String uri,SourceId id,int version,String code) {
        var previous=documents.get(uri);
        if(previous!=null) documents.put(uri,new DocumentState(id,version,previous.source,null));
        else rejectedOpens.put(uri,new RejectedOpen(id,version));
        unsynchronized.add(uri); scheduler.invalidate();
        operation("Document update rejected: "+code+"; full document resynchronization is required (full replacement or reopen).");
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        if(!notificationReady()) return;
        var captured=copyParams(params,DidChangeTextDocumentParams.class);
        scheduler.onOwner(() -> { orderedDocumentOperation(() -> didChangeNow(captured)); return null; });
    }

    private void didChangeNow(DidChangeTextDocumentParams params) {
        String uri=params.getTextDocument().getUri();
        var previous=documents.get(uri); var rejected=rejectedOpens.get(uri);
        int version=params.getTextDocument().getVersion();
        if(previous==null && rejected==null) return;
        if(version<=(previous!=null?previous.lspVersion:rejected.version)) return;
        documentGeneration++;
        SourceId id=previous!=null?previous.sourceId:rejected.sourceId;
        var changes=params.getContentChanges();
        if(unsynchronized.contains(uri) && (changes==null || changes.isEmpty() || changes.getFirst()==null || changes.getFirst().getRange()!=null)) {
            if(previous!=null) documents.put(uri,new DocumentState(id,version,previous.source,null));
            else rejectedOpens.put(uri,new RejectedOpen(id,version));
            return;
        }
        // Only used to interpret a full replacement followed by deltas when an
        // oversized initial open had no accepted Core source. Never published.
        var base=workspace.source(id).orElseGet(() -> new SourceText(id,guru.interlis.source.SourceRevision.initial(),""));
        String text;
        try { text=materialize(base,changes); }
        catch(ResponseErrorException invalid) { rejectDocument(uri,id,version,"INVALID_EDIT"); return; }
        try { replaceSource(id,text); }
        catch(guru.interlis.workspace.UpdateRejectedException invalid) { rejectDocument(uri,id,version,invalid.code().name()); return; }
        rejectedOpens.remove(uri); unsynchronized.remove(uri); analyze(uri,version);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        if(!notificationReady()) return;
        var captured=copyParams(params,DidCloseTextDocumentParams.class);
        scheduler.onOwner(() -> { orderedDocumentOperation(() -> didCloseNow(captured)); return null; });
    }

    private void didCloseNow(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        var closed=documents.remove(uri); var rejected=rejectedOpens.remove(uri); if(closed==null && rejected==null) return;
        documentGeneration++;
        unsynchronized.remove(uri);
        SourceId id = closed!=null?closed.sourceId:rejected.sourceId;
        if (filePath(uri).isPresent()) {
            reloadExternal(id.value());
        } else {
            workspace.source(id).ifPresent(text -> workspace.remove(id, text.revision()));
        }
        reanalyzeOpen();
        DiagnosticSink current = sink.get();
        if (current != null) {
            current.publish(new PublishDiagnosticsParams(params.getTextDocument().getUri(),
                    List.of()));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        if(!notificationReady()) return;
        var captured=copyParams(params,DidSaveTextDocumentParams.class);
        scheduler.onOwner(() -> { orderedDocumentOperation(() -> didSaveNow(captured)); return null; });
    }

    boolean notificationReady() {
        try { requestGuard.run(); return true; }
        catch(ResponseErrorException unavailable) { operation(unavailable.getResponseError().getMessage()); return false; }
    }

    private void didSaveNow(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        DocumentState state = documents.get(uri);
        if (state != null) {
            analyze(uri, state.lspVersion);
        }
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        var captured = copyParams(params, HoverParams.class);
        return requestReady((view, token) -> hoverNow(view, captured, token));
    }

    private CompletableFuture<Hover> hoverNow(HostView view, HoverParams params, CancellationToken token) {
        DocumentState state = requireState(view, params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<HoverInfo> result = queries.hover(state.analysis, cursor,
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
        DocumentState state = requireState(view, params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<DefinitionTarget> result = queries.definition(state.analysis, cursor,
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
        DocumentState state = requireState(view, params.getPosition(), params.getTextDocument());
        SourceText source = view.source(state.sourceId).orElseThrow();
        int offset = offsetAt(source, params.getPosition());
        String prefix = prefixAt(source.text(), offset);
        CompletionResult result = queries.complete(state.analysis,
                new CompletionQuery(cursor(state, params.getPosition()),
                        new NamePrefix(prefix), 100), token);
        List<CompletionItem> items = new ArrayList<>();
        int rank = 0;
        for (CompletionCandidate candidate : result.candidates()) {
            CompletionItem item = new CompletionItem(candidate.label());
            item.setKind(completionKind(candidate));
            item.setDetail(candidate.detail());
            item.setInsertText(candidate.insertText());
            item.setTextEdit(Either.forLeft(new TextEdit(toLspRange(state.source, result.replacementRange()), candidate.insertText())));
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
        DocumentState state = requireState(view, new Position(0, 0), params.getTextDocument());
        List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
        for (Symbol symbol : state.analysis.semanticGraph().declarationsInOrder()) {
            if (symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                    && declared.range().source().equals(state.sourceId)
                    && symbol.nameRange().isPresent()) {
                DocumentSymbol documentSymbol = new DocumentSymbol(symbol.name().value(),
                        symbolKind(symbol),
                        toLspRange(state.source, declared.range()),
                        toLspRange(state.source, symbol.nameRange().get()));
                result.add(Either.forRight(documentSymbol));
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        var captured = copyParams(params, ReferenceParams.class);
        return requestReady((view, token) -> referencesNow(view, captured, token));
    }

    private CompletableFuture<List<? extends Location>> referencesNow(HostView view, ReferenceParams params, CancellationToken token) {
        DocumentState state = requireState(view, params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<SymbolRef> selected = queries.symbolAt(state.analysis, cursor,
                token);
        if (!(selected instanceof QueryResult.Known<SymbolRef> known)) {
            return CompletableFuture.completedFuture(List.of());
        }
        boolean includeDeclaration = params.getContext() == null
                || params.getContext().isIncludeDeclaration();
        ReferenceResult result = queries.references(state.analysis, known.value(),
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
        DocumentState state = requireState(view, params.getPosition(), params.getTextDocument());
        RenamePreparation preparation = queries.prepareRename(state.analysis,
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
        DocumentState state = requireState(view, params.getPosition(), params.getTextDocument());
        if(params.getNewName()==null) return failed(ResponseErrorCode.InvalidParams,"newName is required");
        QueryResult<SymbolRef> selected = queries.symbolAt(state.analysis,
                cursor(state, params.getPosition()), token);
        if (!(selected instanceof QueryResult.Known<SymbolRef> known)) {
            return failed(ResponseErrorCode.RequestFailed, "no editable symbol at cursor");
        }
        RenamePlan plan;
        try {
            plan = queries.planRename(state.analysis, new RenameRequest(known.value(),
                    params.getNewName(), state.analysis.sourceRevisions()),
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
                    uri,open==null?null:open.lspVersion),edits)));
            if(open==null) closed.add(view.closedFiles().get(document.source().value()));
        }
        WorkspaceEdit edit = new WorkspaceEdit();
        if(view.versionedEdits()) { edit.setChanges(null); edit.setDocumentChanges(versioned); } else edit.setChanges(changes);
        return closed.isEmpty() ? CompletableFuture.completedFuture(edit)
                : verifyFiles.apply(List.copyOf(closed),token).thenApply(ignored -> edit);
    }

    private static boolean editable(HostView view,SourceId id) {
        var known=view.closedFiles().get(id.value());
        return view.open(id)!=null ? known==null || known.editable() : known!=null && known.editable();
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        var captured = copyParams(params, DocumentFormattingParams.class);
        return requestReady((view, token) -> formattingNow(view, captured, token));
    }

    private CompletableFuture<List<? extends TextEdit>> formattingNow(HostView view, DocumentFormattingParams params, CancellationToken token) {
        DocumentState state = requireState(view, new Position(0, 0), params.getTextDocument());
        var preferences=params.getOptions();
        if(preferences==null || preferences.getTabSize()<1 || preferences.getTabSize()>16)
            return failed(ResponseErrorCode.InvalidParams,"tabSize must be between 1 and 16");
        FormatOutcome outcome = ModelFormatters.create().format(state.source,
                new FormatOptions(preferences.getTabSize(),FormatOptions.LineEnding.PRESERVE,true), token);
        if(outcome instanceof FormatOutcome.Cancelled cancelled) return failed(ResponseErrorCode.RequestCancelled,"formatting cancelled: "+cancelled.reason());
        if(outcome instanceof FormatOutcome.Rejected) return failed(ResponseErrorCode.RequestFailed,"source cannot be formatted without changing its meaning");
        var formatted=(FormatOutcome.Formatted)outcome;
        if (formatted.text().equals(state.source.text())) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(List.of(new TextEdit(
                toLspRange(state.source, state.source.range(0, state.source.length())),
                formatted.text())));
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        var captured = copyParams(params, CodeActionParams.class);
        return requestReady((view, token) -> codeActionNow(view, captured, token));
    }

    private CompletableFuture<List<Either<Command, CodeAction>>> codeActionNow(HostView view, CodeActionParams params, CancellationToken token) {
        DocumentState state = requireState(view, params.getRange().getStart(), params.getTextDocument());
        FormatOutcome outcome = ModelFormatters.create().format(state.source,
                FormatOptions.defaults(), token);
        if (!(outcome instanceof FormatOutcome.Formatted formatted)
                || formatted.text().equals(state.source.text())) {
            return CompletableFuture.completedFuture(List.of());
        }
        CodeAction action = new CodeAction("Format INTERLIS source");
        action.setKind("source.format");
        WorkspaceEdit edit = new WorkspaceEdit();
        edit.setChanges(Map.of(view.uri(state.sourceId), List.of(new TextEdit(
                toLspRange(state.source, state.source.range(0, state.source.length())),
                formatted.text()))));
        action.setEdit(edit);
        return CompletableFuture.completedFuture(List.of(Either.forRight(action)));
    }

    void configurationChanged(Object settings) {
        scheduler.onOwner(() -> {
            guru.interlis.workspace.WorkspaceConfiguration configuration;
            try { configuration=LspConfiguration.read(settings,workspace.configuration()); }
            catch(IllegalArgumentException | ArithmeticException invalid) {
                operation("Invalid INTERLIS configuration; previous configuration retained"); return null;
            }
            var receipt=workspace.configure(configuration,workspace.state().revision());
            if(receipt.changed()) { documentGeneration++; reanalyzeOpenNow(); }
            return null;
        });
    }

    void initializeConfiguration(Object settings) {
        scheduler.onOwner(() -> {
            guru.interlis.workspace.WorkspaceConfiguration configuration;
            try { configuration=LspConfiguration.read(settings,workspace.configuration()); }
            catch(IllegalArgumentException | ArithmeticException invalid) {
                throw LspFailures.error(ResponseErrorCode.InvalidParams,"invalid initial INTERLIS configuration");
            }
            workspace.configure(configuration,workspace.currentRevision()); return null;
        });
    }

    void reloadExternal(String uri) { Objects.requireNonNull(externalReload,"project file service").accept(uri); }

    void deleteExternal(String uri) {
        scheduler.onOwner(() -> {
            String canonical=canonicalUriNow(uri);
            if(openSource(new SourceId(canonical))) return null;
            documentGeneration++; closedFiles.remove(canonical); deleteExternalNow(canonical);
            var request=pendingFiles.get(uri);
            if(request!=null) finishExternal(uri,request,null); else reanalyzeOpenNow();
            return null;
        });
    }

    private void deleteExternalNow(String uri) {
        if (openSource(new SourceId(canonicalUriNow(uri)))) return;
        SourceId id = new SourceId(canonicalUriNow(uri));
        workspace.source(id).ifPresent(text -> workspace.remove(id, text.revision()));
        fileKeys.values().removeIf(id.value()::equals);
    }

    void reanalyzeOpen() {
        scheduler.onOwner(() -> { reanalyzeOpenNow(); return null; });
    }

    private void reanalyzeOpenNow() {
        for (String uri : List.copyOf(documents.keySet())) {
            DocumentState state = documents.get(uri);
            if (state != null) {
                documents.put(uri,new DocumentState(state.sourceId,state.lspVersion,state.source,null));
            }
        }
        if(pendingFiles.isEmpty() && unsynchronized.isEmpty()) scheduler.schedule(); else scheduler.invalidate();
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
        DocumentState state = requireState(view, new Position(0, 0), params.getTextDocument());
        List<Integer> data = semanticTokens(state,token);
        return CompletableFuture.completedFuture(new SemanticTokens(data));
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        var captured = copyParams(params, FoldingRangeRequestParams.class);
        return requestReady((view, token) -> foldingRangeNow(view, captured, token));
    }

    private CompletableFuture<List<FoldingRange>> foldingRangeNow(HostView view, FoldingRangeRequestParams params, CancellationToken token) {
        DocumentState state = requireState(view, new Position(0, 0), params.getTextDocument());
        List<FoldingRange> ranges = new ArrayList<>();
        for (Symbol symbol : state.analysis.semanticGraph().declarationsInOrder()) {
            token.throwIfCancellationRequested();
            if (symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                    && declared.range().source().equals(state.sourceId)) {
                var range = toLspRange(state.source, declared.range());
                if (range.getEnd().getLine() > range.getStart().getLine()) {
                    ranges.add(new FoldingRange(range.getStart().getLine(),
                            range.getEnd().getLine()));
                }
            }
        }
        state.analysis.syntaxSnapshot(state.sourceId).ifPresent(syntax -> {
            for(var trivia:syntax.trivia()) {
                token.throwIfCancellationRequested();
                if(trivia.kind()==guru.interlis.syntax.TriviaKind.WHITESPACE_GAP) continue;
                var range=toLspRange(state.source,trivia.range());
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

    private List<Integer> semanticTokens(DocumentState state,CancellationToken cancellation) {
        record Span(SourceRange range,int type) { }
        var spans=new ArrayList<Span>();
        state.analysis.syntaxSnapshot(state.sourceId).ifPresent(syntax -> {
            for(var token:syntax.tokens()) {
                cancellation.throwIfCancellationRequested();
                int type=switch(token.kind()) {
                    case KEYWORD -> 0; case NUMBER -> 1; case STRING -> 2;
                    case IDENTIFIER -> {
                        var result=queries.symbolAt(state.analysis,state.source.range(token.range().startOffset(),token.range().startOffset()),cancellation);
                        if(result instanceof QueryResult.Known<SymbolRef> known) {
                            var symbol=state.analysis.semanticGraph().symbol(known.value().symbol()).orElseThrow();
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
        var lines=state.source.lineMap();
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
        DocumentState state = requireState(view, new Position(0, 0), new TextDocumentIdentifier(params.uri()));
        if (params.snapshotId() != null
                && !params.snapshotId().equals(state.analysis.snapshotId().value())) {
            return CompletableFuture.failedFuture(new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                    new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                            org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
                            "snapshotId is stale for the requested document", null)));
        }
        List<SymbolRef> selected = new ArrayList<>();
        if (params.symbolIds().isEmpty()) {
            for (guru.interlis.model.SymbolId id : state.analysis.semanticGraph().rootModels()) {
                selected.add(new SymbolRef(state.analysis.snapshotId(), id));
            }
            if (selected.isEmpty()) {
                for (guru.interlis.model.SymbolId id : state.analysis.semanticGraph().models()) {
                    selected.add(new SymbolRef(state.analysis.snapshotId(), id));
                }
            }
        } else {
            for (String id : params.symbolIds()) {
                selected.add(new SymbolRef(state.analysis.snapshotId(),
                        new guru.interlis.model.SymbolId(id)));
            }
        }
        if (selected.isEmpty()) {
            return CompletableFuture.failedFuture(new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                    new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                            org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
                            "no diagram selection available", null)));
        }
        DiagramOptions options = DiagramOptions.defaults(new DiagramSelection(selected));
        DiagramProjectionOutcome outcome = DiagramProjectors.create().project(
                new DiagramInput.FromAnalysis(state.analysis), options,
                token);
        if (outcome instanceof DiagramProjectionOutcome.Rejected rejected) {
            return CompletableFuture.failedFuture(new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                    new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                            org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.RequestFailed,
                            rejected.reasons().toString(), null)));
        }
        DiagramDocument document = ((DiagramProjectionOutcome.Produced) outcome).document();
        return CompletableFuture.completedFuture(new DiagramJsonCodec().encode(document));
    }

    private static DocumentState requireState(HostView view,Position position, TextDocumentIdentifier document) {
        if(document==null || document.getUri()==null || document.getUri().isBlank())
            throw LspFailures.error(ResponseErrorCode.InvalidParams,"document URI is required");
        String uri = document.getUri();
        var direct=view.documents().get(uri);
        DocumentState state = direct!=null?direct:view.open(new SourceId(uri));
        if (state == null) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams,
                    "document is not available", null));
        }
        if (view.unsynchronized().contains(view.uri(state.sourceId))) throw new ResponseErrorException(new ResponseError(
                ResponseErrorCode.ContentModified, "full document resynchronization is required", null));
        if (view.source(state.sourceId).filter(source -> source.revision().equals(state.source.revision())).isEmpty()
                || state.analysis == null) throw new ResponseErrorException(new ResponseError(
                ResponseErrorCode.ContentModified, "CONTENT_MODIFIED: document revision changed", null));
        PositionMapper.toOffset(state.source, position);
        return state;
    }

    private void analyze(String uri, int lspVersion) {
        SourceId sourceId = new SourceId(canonicalUriNow(uri));
        SourceText source = workspace.source(sourceId).orElseThrow();
        DocumentState state = new DocumentState(sourceId, lspVersion, source, null);
        documents.put(uri, state);
        if(pendingFiles.isEmpty() && unsynchronized.isEmpty()) scheduler.schedule(); else scheduler.invalidate();
    }

    private void accepted(AnalysisSnapshot analysis) {
        if(!unsynchronized.isEmpty()) return;
        for (String uri : List.copyOf(documents.keySet())) {
            if (unsynchronized.contains(uri)) continue;
            var previous = documents.get(uri);
            var source = workspace.source(previous.sourceId);
            if (source.isEmpty() || !source.get().revision().equals(previous.source.revision())
                    || !source.get().revision().equals(analysis.sourceRevisions().get(previous.sourceId))) continue;
            var state = new DocumentState(previous.sourceId, previous.lspVersion, source.orElseThrow(), analysis);
            documents.put(uri, state);
            publish(uri,state);
        }
    }

    private void publish(String uri,DocumentState state) {
        DiagnosticSink current = sink.get();
        if (current == null) {
            return;
        }
        List<org.eclipse.lsp4j.Diagnostic> diagnostics = new ArrayList<>();
        for (Diagnostic diagnostic : state.analysis.diagnostics()) {
            if (diagnostic.primaryRange().isEmpty()) {
                continue;
            }
            SourceRange range = diagnostic.primaryRange().get();
            if (!range.source().equals(state.sourceId)) {
                continue;
            }
            org.eclipse.lsp4j.Diagnostic lsp = new org.eclipse.lsp4j.Diagnostic(
                    toLspRange(state.source, range), message(diagnostic), severity(diagnostic),
                    "interlis");
            lsp.setCode(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(
                    diagnostic.code().value()));
            diagnostics.add(lsp);
        }
        diagnostics.sort(Comparator.comparingInt((org.eclipse.lsp4j.Diagnostic d) ->
                        d.getRange().getStart().getLine())
                .thenComparingInt(d -> d.getRange().getStart().getCharacter()));
        var publication = new PublishDiagnosticsParams(uri, diagnostics);
        publication.setVersion(state.lspVersion);
        current.publish(publication);
    }

    private static String message(Diagnostic diagnostic) {
        StringBuilder text = new StringBuilder(diagnostic.messageKey());
        for (DiagnosticArgument argument : diagnostic.arguments()) {
            text.append(" [").append(argument.name()).append('=').append(argument).append(']');
        }
        return text.toString();
    }

    private static DiagnosticSeverity severity(Diagnostic diagnostic) {
        return switch (diagnostic.severity()) {
            case ERROR -> DiagnosticSeverity.Error;
            case WARNING -> DiagnosticSeverity.Warning;
            case INFO -> DiagnosticSeverity.Information;
            case HINT -> DiagnosticSeverity.Hint;
        };
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

    private static SourceRange cursor(DocumentState state, Position position) {
        int offset = offsetAt(state.source, position);
        return state.source.range(offset, offset);
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

    private record DocumentState(SourceId sourceId, int lspVersion, SourceText source,
                                 AnalysisSnapshot analysis) {
    }
}
