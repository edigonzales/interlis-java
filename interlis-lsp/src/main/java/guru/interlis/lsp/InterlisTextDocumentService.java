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
    private final LanguageQueries queries = LanguageQueries.create();
    private final AtomicReference<DiagnosticSink> sink = new AtomicReference<>();

    InterlisTextDocumentService(Workspace workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    void sink(DiagnosticSink sink) {
        this.sink.set(sink);
    }

    @Override
    public synchronized void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();
        SourceId sourceId = new SourceId(uri);
        var existing = workspace.source(sourceId);
        if (existing.isEmpty()) {
            workspace.update(new SourceUpdate(sourceId, new SourceExpectation.Absent(),
                    new SourceChange.Replace(text)));
        } else {
            workspace.update(new SourceUpdate(sourceId,
                    new SourceExpectation.At(existing.get().revision()),
                    new SourceChange.Replace(text)));
        }
        analyze(uri, params.getTextDocument().getVersion());
    }

    @Override
    public synchronized void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        DocumentState previous = documents.get(uri);
        if (previous == null || params.getTextDocument().getVersion() <= previous.lspVersion) {
            return;
        }
        SourceId sourceId = new SourceId(uri);
        SourceText current = workspace.source(sourceId).orElseThrow(
                () -> new IllegalStateException("document is not open: " + uri));
        String text = materialize(current, params.getContentChanges());
        workspace.update(new SourceUpdate(sourceId,
                new SourceExpectation.At(current.revision()), new SourceChange.Replace(text)));
        analyze(uri, params.getTextDocument().getVersion());
    }

    @Override
    public synchronized void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.remove(uri);
        SourceId id = new SourceId(uri);
        if (uri.startsWith("file:")) {
            reloadExternal(uri);
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
    public synchronized void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        DocumentState state = documents.get(uri);
        if (state != null) {
            analyze(uri, state.lspVersion);
        }
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        DocumentState state = requireState(params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<HoverInfo> result = queries.hover(state.analysis, cursor,
                CancellationToken.none());
        if (result instanceof QueryResult.Known<HoverInfo> known) {
            HoverInfo info = known.value();
            MarkupContent content = new MarkupContent(MarkupKind.MARKDOWN,
                    "```interlis\n" + info.signature() + "\n```"
                            + (info.documentation().isBlank() ? "" : "\n\n" + info.documentation()));
            return CompletableFuture.completedFuture(new Hover(content));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>>>
            definition(DefinitionParams params) {
        DocumentState state = requireState(params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<DefinitionTarget> result = queries.definition(state.analysis, cursor,
                CancellationToken.none());
        if (result instanceof QueryResult.Known<DefinitionTarget> known) {
            DefinitionTarget target = known.value();
            SourceText targetSource = workspace.source(target.nameRange().source()).orElse(null);
            if (targetSource != null) {
                return CompletableFuture.completedFuture(Either.forLeft(List.of(
                        new Location(target.nameRange().source().value(),
                                toLspRange(targetSource, target.nameRange())))));
            }
        }
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            CompletionParams params) {
        DocumentState state = requireState(params.getPosition(), params.getTextDocument());
        SourceText source = workspace.source(state.sourceId).orElseThrow();
        int offset = offsetAt(source, params.getPosition());
        String prefix = prefixAt(source.text(), offset);
        CompletionResult result = queries.complete(state.analysis,
                new CompletionQuery(cursor(state, params.getPosition()),
                        new NamePrefix(prefix), 100), CancellationToken.none());
        List<CompletionItem> items = new ArrayList<>();
        for (CompletionCandidate candidate : result.candidates()) {
            CompletionItem item = new CompletionItem(candidate.label());
            item.setKind(completionKind(candidate));
            item.setDetail(candidate.detail());
            items.add(item);
        }
        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    @Override
    public CompletableFuture<List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>>>
            documentSymbol(DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        DocumentState state = documents.get(uri);
        if (state == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
        for (Symbol symbol : state.analysis.semanticGraph().declarationsInOrder()) {
            if (symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                    && declared.range().source().value().equals(uri)
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
        DocumentState state = requireState(params.getPosition(), params.getTextDocument());
        SourceRange cursor = cursor(state, params.getPosition());
        QueryResult<SymbolRef> selected = queries.symbolAt(state.analysis, cursor,
                CancellationToken.none());
        if (!(selected instanceof QueryResult.Known<SymbolRef> known)) {
            return CompletableFuture.completedFuture(List.of());
        }
        boolean includeDeclaration = params.getContext() == null
                || params.getContext().isIncludeDeclaration();
        ReferenceResult result = queries.references(state.analysis, known.value(),
                new ReferenceOptions(includeDeclaration), CancellationToken.none());
        List<Location> locations = new ArrayList<>();
        for (var occurrence : result.references()) {
            SourceText source = workspace.source(occurrence.range().source()).orElse(null);
            if (source != null && source.revision().equals(occurrence.range().revision())) {
                locations.add(new Location(occurrence.range().source().value(),
                        toLspRange(source, occurrence.range())));
            }
        }
        return CompletableFuture.completedFuture(locations);
    }

    @Override
    public CompletableFuture<Either3<Range, org.eclipse.lsp4j.PrepareRenameResult,
            PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
        DocumentState state = requireState(params.getPosition(), params.getTextDocument());
        RenamePreparation preparation = queries.prepareRename(state.analysis,
                cursor(state, params.getPosition()), CancellationToken.none());
        if (!preparation.allowed()) {
            return failed(ResponseErrorCode.RequestFailed,
                    preparation.reason().orElse("rename is not allowed"));
        }
        SourceRange range = preparation.range().orElseThrow();
        if (!documents.containsKey(range.source().value())) {
            return failed(ResponseErrorCode.RequestFailed,
                    "rename target is not an editable open source");
        }
        return CompletableFuture.completedFuture(Either3.forFirst(
                toLspRange(state.source, range)));
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        DocumentState state = requireState(params.getPosition(), params.getTextDocument());
        QueryResult<SymbolRef> selected = queries.symbolAt(state.analysis,
                cursor(state, params.getPosition()), CancellationToken.none());
        if (!(selected instanceof QueryResult.Known<SymbolRef> known)) {
            return failed(ResponseErrorCode.RequestFailed, "no editable symbol at cursor");
        }
        RenamePlan plan;
        try {
            plan = queries.planRename(state.analysis, new RenameRequest(known.value(),
                    params.getNewName(), state.analysis.sourceRevisions()),
                    CancellationToken.none());
        } catch (IllegalArgumentException invalidName) {
            return failed(ResponseErrorCode.InvalidParams, invalidName.getMessage());
        }
        if (!plan.complete()) {
            ResponseErrorCode code = plan.reason().orElse("").equals("CONTENT_MODIFIED")
                    ? ResponseErrorCode.ContentModified : ResponseErrorCode.RequestFailed;
            return failed(code, plan.reason().orElse("rename could not be planned"));
        }
        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        for (var document : plan.edits()) {
            if (!documents.containsKey(document.source().value())) {
                return failed(ResponseErrorCode.RequestFailed,
                        "rename would modify a non-open/read-only source: "
                                + document.source().value());
            }
            SourceText source = workspace.source(document.source()).orElse(null);
            if (source == null || !source.revision().equals(document.expected())) {
                return failed(ResponseErrorCode.ContentModified, "CONTENT_MODIFIED");
            }
            List<TextEdit> edits = document.edits().stream()
                    .map(edit -> new TextEdit(toLspRange(source, edit.range()),
                            edit.replacement()))
                    .toList();
            changes.put(document.source().value(), edits);
        }
        WorkspaceEdit edit = new WorkspaceEdit();
        edit.setChanges(changes);
        return CompletableFuture.completedFuture(edit);
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        DocumentState state = requireState(new Position(0, 0), params.getTextDocument());
        FormatOutcome outcome = ModelFormatters.create().format(state.source,
                FormatOptions.defaults(), CancellationToken.none());
        if (!(outcome instanceof FormatOutcome.Formatted formatted)
                || formatted.text().equals(state.source.text())) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(List.of(new TextEdit(
                toLspRange(state.source, state.source.range(0, state.source.length())),
                formatted.text())));
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        DocumentState state = requireState(params.getRange().getStart(), params.getTextDocument());
        FormatOutcome outcome = ModelFormatters.create().format(state.source,
                FormatOptions.defaults(), CancellationToken.none());
        if (!(outcome instanceof FormatOutcome.Formatted formatted)
                || formatted.text().equals(state.source.text())) {
            return CompletableFuture.completedFuture(List.of());
        }
        CodeAction action = new CodeAction("Format INTERLIS source");
        action.setKind("source.format");
        WorkspaceEdit edit = new WorkspaceEdit();
        edit.setChanges(Map.of(state.sourceId.value(), List.of(new TextEdit(
                toLspRange(state.source, state.source.range(0, state.source.length())),
                formatted.text()))));
        action.setEdit(edit);
        return CompletableFuture.completedFuture(List.of(Either.forRight(action)));
    }

    synchronized void configurationChanged() {
        reanalyzeOpen();
    }

    synchronized void reloadExternal(String uri) {
        if (documents.containsKey(uri) || !uri.startsWith("file:")) {
            return;
        }
        try {
            java.net.URI fileUri = java.net.URI.create(uri);
            if (!java.nio.file.Files.exists(java.nio.file.Path.of(fileUri))) {
                deleteExternal(uri);
                return;
            }
            guru.interlis.repository.FileSourceLoader loader =
                    new guru.interlis.repository.FileSourceLoader(java.nio.file.Path.of("."));
            var loaded = loader.load(new guru.interlis.repository.SourceLocator(
                            java.nio.file.Path.of(fileUri).toString()),
                    guru.interlis.repository.FetchPolicy.defaults(), CancellationToken.none());
            SourceId sourceId = new SourceId(uri);
            var existing = workspace.source(sourceId);
            if (existing.isPresent() && existing.get().text().equals(loaded.text())) {
                return;
            }
            SourceExpectation expectation = existing
                    .<SourceExpectation>map(value -> new SourceExpectation.At(value.revision()))
                    .orElseGet(SourceExpectation.Absent::new);
            workspace.update(new SourceUpdate(sourceId, expectation,
                    new SourceChange.Replace(loaded.text())));
        } catch (RuntimeException ignored) {
            // A watched file may disappear between notification and read.
        }
    }

    synchronized void deleteExternal(String uri) {
        if (documents.containsKey(uri)) return;
        SourceId id = new SourceId(uri);
        workspace.source(id).ifPresent(text -> workspace.remove(id, text.revision()));
    }

    synchronized void reanalyzeOpen() {
        for (String uri : List.copyOf(documents.keySet())) {
            DocumentState state = documents.get(uri);
            if (state != null) {
                analyze(uri, state.lspVersion);
            }
        }
    }

    private static <T> CompletableFuture<T> failed(ResponseErrorCode code, String message) {
        return CompletableFuture.failedFuture(new ResponseErrorException(
                new ResponseError(code, message, null)));
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        DocumentState state = requireState(new Position(0, 0), params.getTextDocument());
        List<Integer> data = semanticTokens(state);
        return CompletableFuture.completedFuture(new SemanticTokens(data));
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        DocumentState state = requireState(new Position(0, 0), params.getTextDocument());
        List<FoldingRange> ranges = new ArrayList<>();
        for (Symbol symbol : state.analysis.semanticGraph().declarationsInOrder()) {
            if (symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                    && declared.range().source().equals(state.sourceId)) {
                var range = toLspRange(state.source, declared.range());
                if (range.getEnd().getLine() > range.getStart().getLine()) {
                    ranges.add(new FoldingRange(range.getStart().getLine(),
                            range.getEnd().getLine()));
                }
            }
        }
        ranges.sort(Comparator.comparingInt(FoldingRange::getStartLine)
                .thenComparingInt(FoldingRange::getEndLine));
        return CompletableFuture.completedFuture(ranges);
    }

    private static List<Integer> semanticTokens(DocumentState state) {
        List<SyntaxToken> tokens = new ArrayList<>();
        for (SyntaxToken token : state.analysis.syntaxSnapshot(state.sourceId)
                .map(snapshot -> snapshot.tokens()).orElse(List.of())) {
            if (token.kind() != guru.interlis.syntax.TokenKind.EOF
                    && token.kind() != guru.interlis.syntax.TokenKind.PUNCTUATION) {
                tokens.add(token);
            }
        }
        tokens.sort(Comparator.comparingInt(token -> token.range().startOffset()));
        List<Integer> data = new ArrayList<>();
        int lastLine = 0;
        int lastCharacter = 0;
        for (SyntaxToken token : tokens) {
            var start = state.source.lineMap().positionAt(token.range().startOffset());
            int type = switch (token.kind()) {
                case KEYWORD -> 0;
                case NUMBER -> 1;
                case STRING -> 2;
                case IDENTIFIER -> 5;
                default -> 6;
            };
            int deltaLine = start.line() - lastLine;
            int deltaCharacter = deltaLine == 0 ? start.character() - lastCharacter
                    : start.character();
            int length = Math.max(1, token.range().length());
            data.add(deltaLine);
            data.add(deltaCharacter);
            data.add(length);
            data.add(type);
            data.add(0);
            lastLine = start.line();
            lastCharacter = start.character();
        }
        return data;
    }

    CompletableFuture<String> diagram(DiagramParams params) {
        DocumentState state = documents.get(params.uri());
        if (state == null) {
            return CompletableFuture.failedFuture(new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                    new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                            org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
                            "document is not open: " + params.uri(), null)));
        }
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
                CancellationToken.none());
        if (outcome instanceof DiagramProjectionOutcome.Rejected rejected) {
            return CompletableFuture.failedFuture(new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                    new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                            org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.RequestFailed,
                            rejected.reasons().toString(), null)));
        }
        DiagramDocument document = ((DiagramProjectionOutcome.Produced) outcome).document();
        return CompletableFuture.completedFuture(new DiagramJsonCodec().encode(document));
    }

    private DocumentState requireState(Position position, TextDocumentIdentifier document) {
        String uri = document.getUri();
        DocumentState state = documents.get(uri);
        if (state == null) {
            throw new IllegalStateException("document is not open: " + uri);
        }
        return state;
    }

    private void analyze(String uri, int lspVersion) {
        SourceId sourceId = new SourceId(uri);
        SourceText source = workspace.source(sourceId).orElseThrow();
        AnalysisSnapshot analysis = workspace.analyze(new AnalysisRequest(
                workspace.currentRevision(),
                List.of(new guru.interlis.compiler.ModelSelector.BySource(sourceId))),
                CancellationToken.none());
        DocumentState state = new DocumentState(sourceId, lspVersion, source, analysis);
        documents.put(uri, state);
        publish(state);
    }

    private void publish(DocumentState state) {
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
        current.publish(new PublishDiagnosticsParams(state.sourceId.value(), diagnostics));
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
        String text = base.text();
        for (TextDocumentContentChangeEvent change : changes) {
            if (change.getRange() == null) {
                text = change.getText();
            } else {
                // Each delta refers to the intermediate state produced by the
                // previous delta, not to the original document.
                SourceText intermediate = new SourceText(base.id(), base.revision(), text);
                int start = offsetAt(intermediate, change.getRange().getStart());
                int end = offsetAt(intermediate, change.getRange().getEnd());
                start = Math.min(start, text.length());
                end = Math.min(Math.max(end, start), text.length());
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
        int line = Math.max(0, Math.min(position.getLine(), source.lineMap().lineCount() - 1));
        int start = source.lineMap().lineStart(line);
        int end = source.lineMap().lineContentEnd(line);
        int character = Math.max(0, position.getCharacter());
        int offset = start + Math.min(character, end - start);
        return Math.min(offset, source.length());
    }

    private static Range toLspRange(SourceText source, SourceRange range) {
        var start = source.lineMap().positionAt(range.startOffset());
        var end = source.lineMap().positionAt(range.endOffset());
        return new Range(new Position(start.line(), start.character()),
                new Position(end.line(), end.character()));
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
            case CLASS, STRUCTURE, ASSOCIATION -> CompletionItemKind.Class;
            case DOMAIN, LINE_FORM -> CompletionItemKind.TypeParameter;
            case ATTRIBUTE, ROLE -> CompletionItemKind.Field;
            case PARAMETER, METAOBJECT -> CompletionItemKind.Variable;
            case METADATA_BASKET, GRAPHIC -> CompletionItemKind.Module;
            case DRAWING_RULE -> CompletionItemKind.Method;
            case TOPIC, MODEL -> CompletionItemKind.Module;
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
