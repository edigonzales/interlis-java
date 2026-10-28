package guru.interlis.compiler.query;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.NamePrefix;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.query.QueryResult;
import guru.interlis.syntax.SyntaxToken;
import guru.interlis.model.UnresolvedReason;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.TextEdit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/** Default read-only language query implementation. */
final class LanguageQueryService implements LanguageQueries {
    @Override
    public QueryResult<SymbolRef> symbolAt(AnalysisSnapshot snapshot, SourceRange cursor,
                                          CancellationToken token) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateCursor(snapshot, cursor);
        token.throwIfCancellationRequested();
        Symbol match = null;
        for (Symbol symbol : snapshot.semanticGraph().symbols()) {
            if (symbol.nameRange().filter(r -> contains(r, cursor))
                    .filter(r -> r.length() == symbol.name().value().length()).isPresent()) {
                if (match == null || width(symbol) < width(match)) {
                    match = symbol;
                }
            }
        }
        if (match != null) {
            return new QueryResult.Known<>(new SymbolRef(snapshot.snapshotId(), match.id()));
        }
        for (var reference : snapshot.semanticGraph().references()) {
            for (var segment : reference.segments()) {
                if (!contains(segment.range(), cursor)) continue;
                if (segment.target().isPresent()) {
                    return new QueryResult.Known<>(new SymbolRef(snapshot.snapshotId(),
                            segment.target().orElseThrow()));
                }
                return switch (reference.resolution()) {
                    case guru.interlis.model.BindingResolution.UnresolvedTarget unresolved ->
                            new QueryResult.Unresolved<>(unresolved.reason(), unresolved.candidates()
                                    .stream().map(id -> new SymbolRef(snapshot.snapshotId(), id)).toList());
                    case guru.interlis.model.BindingResolution.InvalidTarget invalid ->
                            new QueryResult.Invalid<>(invalid.diagnosticIds());
                    case guru.interlis.model.BindingResolution.NotAnalyzedTarget not ->
                            new QueryResult.NotAnalyzed<>(not.reason());
                    case guru.interlis.model.BindingResolution.BoundTarget ignored ->
                            new QueryResult.Unresolved<>(UnresolvedReason.NAME_NOT_FOUND, List.of());
                };
            }
        }
        for (Symbol symbol : snapshot.semanticGraph().symbols()) {
            if (closingName(snapshot, symbol).filter(r -> contains(r, cursor)).isPresent()) {
                return new QueryResult.Known<>(new SymbolRef(snapshot.snapshotId(), symbol.id()));
            }
        }
        return new QueryResult.Unresolved<>(UnresolvedReason.NAME_NOT_FOUND, List.of());
    }

    @Override
    public QueryResult<DefinitionTarget> definition(AnalysisSnapshot snapshot, SourceRange cursor,
                                                    CancellationToken token) {
        QueryResult<SymbolRef> symbol = symbolAt(snapshot, cursor, token);
        if (!(symbol instanceof QueryResult.Known<SymbolRef> known)) {
            return symbol.map(ref -> new DefinitionTarget(ref, cursor, cursor,
                    new guru.interlis.model.Origin.SyntheticOrigin(
                            guru.interlis.model.SyntheticReason.MISSING_REFERENCE,
                            Optional.of(cursor))));
        }
        Symbol resolved = snapshot.semanticGraph().symbol(known.value().symbol()).orElse(null);
        if (resolved == null) {
            return new QueryResult.Unresolved<>(UnresolvedReason.UNKNOWN_ID, List.of());
        }
        SourceRange nameRange = resolved.nameRange().orElse(cursor);
        SourceRange declarationRange = resolved.origin()
                instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                ? declared.range() : nameRange;
        return new QueryResult.Known<>(new DefinitionTarget(known.value(), declarationRange,
                nameRange, resolved.origin()));
    }

    @Override
    public QueryResult<HoverInfo> hover(AnalysisSnapshot snapshot, SourceRange cursor,
                                        CancellationToken token) {
        QueryResult<SymbolRef> symbol = symbolAt(snapshot, cursor, token);
        if (symbol instanceof QueryResult.Known<SymbolRef> known) {
            Symbol resolved = snapshot.semanticGraph().symbol(known.value().symbol()).orElseThrow();
            String type = snapshot.queries().declaredType(known.value())
                    .known().map(t -> t.kind().name()).orElse(resolved.kind().name());
            String signature = resolved.kind() + " " + resolved.name().value() + " : " + type;
            return new QueryResult.Known<>(new HoverInfo(known.value(), signature, hoverDocumentation(resolved),
                    resolved.nameRange().orElse(cursor)));
        }
        return switch (symbol) {
            case QueryResult.Unresolved<SymbolRef> unresolved ->
                    new QueryResult.Unresolved<>(unresolved.reason(), unresolved.candidates());
            case QueryResult.Invalid<SymbolRef> invalid -> new QueryResult.Invalid<>(
                    invalid.diagnosticIds());
            case QueryResult.NotAnalyzed<SymbolRef> not -> new QueryResult.NotAnalyzed<>(
                    not.reason());
            case QueryResult.Known<SymbolRef> ignored -> throw new IllegalStateException();
        };
    }

    @Override
    public CompletionResult complete(AnalysisSnapshot snapshot, CompletionQuery query,
                                     CancellationToken token) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateCursor(snapshot, query.cursor());
        token.throwIfCancellationRequested();
        List<CompletionCandidate> candidates = snapshot.semanticGraph().symbols().stream()
                .filter(s -> s.name().value().startsWith(query.prefix().value()))
                .filter(s -> !s.name().synthetic())
                .sorted(Comparator.comparing((Symbol s) -> s.name().value())
                        .thenComparing(s -> s.id().value()))
                .limit(query.limit())
                .map(s -> new CompletionCandidate(s.name().value(), s.name().value(),
                        kind(s), s.kind().name()))
                .toList();
        boolean incomplete = candidates.size() >= query.limit();
        return new CompletionResult(candidates, query.cursor(), incomplete,
                snapshot.snapshotId());
    }

    @Override
    public ReferenceResult references(AnalysisSnapshot snapshot, SymbolRef symbol,
                                      ReferenceOptions options, CancellationToken token) {
        Objects.requireNonNull(snapshot, "snapshot");
        token.throwIfCancellationRequested();
        List<ReferenceOccurrence> occurrences = new ArrayList<>();
        snapshot.queries().symbol(symbol); // Validate snapshot ownership.
        for (var reference : snapshot.semanticGraph().references()) {
            token.throwIfCancellationRequested();
            for (var segment : reference.segments()) {
                if (segment.target().filter(symbol.symbol()::equals).isPresent()) {
                    occurrences.add(new ReferenceOccurrence(segment.range(), reference.id()));
                }
            }
        }
        if (options.includeDeclaration()) {
            snapshot.semanticGraph().symbol(symbol.symbol()).flatMap(Symbol::nameRange)
                    .ifPresent(range -> occurrences.add(new ReferenceOccurrence(range,
                            new ReferenceId("declaration-" + symbol.symbol().value()))));
        }
        return new ReferenceResult(occurrences,
                snapshot.coverage().incompleteEntries().isEmpty(), snapshot.snapshotId());
    }

    private static String hoverDocumentation(Symbol symbol) {
        String documentation=symbol.header().documentation().text();
        if(symbol instanceof guru.interlis.model.FunctionSymbol function && !function.explanation().isEmpty())
            return documentation.isEmpty()?function.explanation().text():documentation+"\n\n"+function.explanation().text();
        return documentation;
    }

    @Override
    public RenamePreparation prepareRename(AnalysisSnapshot snapshot, SourceRange cursor,
                                           CancellationToken token) {
        QueryResult<SymbolRef> symbol = symbolAt(snapshot, cursor, token);
        if (!(symbol instanceof QueryResult.Known<SymbolRef> known)) {
            return RenamePreparation.reject("no editable symbol at cursor");
        }
        if (!snapshot.coverage().incompleteEntries().isEmpty()) {
            return RenamePreparation.reject("analysis is incomplete");
        }
        Symbol resolved = snapshot.semanticGraph().symbol(known.value().symbol()).orElse(null);
        if (resolved == null || resolved.name().synthetic() || resolved.nameRange().isEmpty()) {
            return RenamePreparation.reject("symbol has no editable declaration name");
        }
        return editableOccurrence(snapshot, resolved, cursor)
                .map(RenamePreparation::allow)
                .orElseGet(() -> RenamePreparation.reject("no editable name at cursor"));
    }

    @Override
    public RenamePlan planRename(AnalysisSnapshot snapshot, RenameRequest request,
                                 CancellationToken token) {
        Objects.requireNonNull(snapshot, "snapshot");
        token.throwIfCancellationRequested();
        snapshot.queries().symbol(request.symbol()); // Enforce snapshot ownership before planning.
        Symbol resolved = snapshot.semanticGraph().symbol(request.symbol().symbol()).orElse(null);
        if (resolved == null || resolved.name().synthetic() || resolved.nameRange().isEmpty()) {
            return new RenamePlan(List.of(),List.of(request.symbol()),false,Optional.of("symbol is not editable"));
        }
        if (!guru.interlis.compiler.internal.antlr.LanguageProfile.isIdentifier(request.newName(),resolved.header().languageVersion())) {
            return new RenamePlan(List.of(),List.of(request.symbol()),false,Optional.of("name is not an INTERLIS identifier"));
        }
        if (!snapshot.coverage().incompleteEntries().isEmpty()) {
            return new RenamePlan(List.of(),List.of(request.symbol()),false,Optional.of("analysis is incomplete"));
        }
        boolean collision = snapshot.semanticGraph().symbols().stream()
                .filter(symbol -> !symbol.id().equals(resolved.id()))
                .filter(symbol -> symbol.owner().equals(resolved.owner()))
                .filter(symbol -> (symbol.kind() == guru.interlis.model.SymbolKind.PARAMETER)
                        == (resolved.kind() == guru.interlis.model.SymbolKind.PARAMETER))
                .anyMatch(symbol -> symbol.name().value().equals(request.newName()));
        if (collision) {
            return new RenamePlan(List.of(), List.of(request.symbol()), false,
                    Optional.of("name collision"));
        }
        Map<SourceId, List<TextEdit>> edits = new LinkedHashMap<>();
        Map<SourceId, Set<SourceRange>> seen = new LinkedHashMap<>();
        SourceRange declaration = resolved.nameRange().get();
        addEdit(edits, seen, declaration, request.newName());
        for (var reference : snapshot.semanticGraph().references()) {
            token.throwIfCancellationRequested();
            for (var segment : reference.segments()) {
                if (segment.target().filter(request.symbol().symbol()::equals).isPresent()
                        && segment.range().length() == resolved.name().value().length()) {
                    addEdit(edits, seen, segment.range(), request.newName());
                }
            }
        }
        closingName(snapshot, resolved).ifPresent(range ->
                addEdit(edits, seen, range, request.newName()));
        List<DocumentEdit> documents = new ArrayList<>();
        for (Map.Entry<SourceId, List<TextEdit>> entry : edits.entrySet()) {
            SourceRevision expected = snapshot.sourceRevisions().get(entry.getKey());
            if (expected == null) {
                return new RenamePlan(List.of(), List.of(request.symbol()), false,
                        Optional.of("source revision is missing"));
            }
            if (!request.expectedRevisions().getOrDefault(entry.getKey(), expected)
                    .equals(expected)) {
                return new RenamePlan(List.of(), List.of(request.symbol()), false,
                        Optional.of("CONTENT_MODIFIED"));
            }
            List<TextEdit> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparingInt((TextEdit e) -> e.range().startOffset())
                            .reversed())
                    .toList();
            int lastStart = Integer.MAX_VALUE;
            for (TextEdit edit : sorted) {
                if (edit.range().endOffset() > lastStart) {
                    return new RenamePlan(List.of(), List.of(request.symbol()), false,
                            Optional.of("overlapping rename edits"));
                }
                lastStart = edit.range().startOffset();
            }
            documents.add(new DocumentEdit(entry.getKey(), expected, sorted));
        }
        return new RenamePlan(documents, List.of(request.symbol()), true, Optional.empty());
    }

    private static void addEdit(Map<SourceId, List<TextEdit>> edits,
                                Map<SourceId, Set<SourceRange>> seen,
                                SourceRange range, String replacement) {
        Set<SourceRange> ranges = seen.computeIfAbsent(range.source(), ignored -> new HashSet<>());
        boolean sameStart = ranges.stream()
                .anyMatch(existing -> existing.startOffset() == range.startOffset());
        if (!sameStart && ranges.add(range)) {
            edits.computeIfAbsent(range.source(), ignored -> new ArrayList<>())
                    .add(new TextEdit(range, replacement));
        }
    }

    private static void validateCursor(AnalysisSnapshot snapshot, SourceRange cursor) {
        Objects.requireNonNull(cursor, "cursor");
        if (!cursor.isEmpty() || snapshot.syntaxSnapshot(cursor.source())
                .map(s -> cursor.endOffset() > s.source().length()).orElse(true)) {
            throw new IllegalArgumentException("cursor must be an in-bounds empty range");
        }
        SourceRevision revision = snapshot.sourceRevisions().get(cursor.source());
        if (revision == null || !revision.equals(cursor.revision())) {
            throw new IllegalArgumentException("cursor refers to an unknown source revision");
        }
    }

    private static boolean contains(SourceRange range, SourceRange cursor) {
        return range.source().equals(cursor.source()) && range.revision().equals(cursor.revision())
                && cursor.startOffset() >= range.startOffset()
                && cursor.startOffset() <= range.endOffset();
    }

    private static Optional<SourceRange> editableOccurrence(AnalysisSnapshot snapshot,
                                                            Symbol symbol, SourceRange cursor) {
        var declaration = symbol.nameRange().filter(r -> contains(r, cursor));
        if (declaration.isPresent()) return declaration;
        for (var reference : snapshot.semanticGraph().references()) {
            for (var segment : reference.segments()) {
                if (segment.target().filter(symbol.id()::equals).isPresent()
                        && contains(segment.range(), cursor)) return Optional.of(segment.range());
            }
        }
        return closingName(snapshot, symbol).filter(r -> contains(r, cursor));
    }

    private static Optional<SourceRange> closingName(AnalysisSnapshot snapshot, Symbol symbol) {
        if (!(symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin origin)) {
            return Optional.empty();
        }
        return snapshot.syntaxSnapshot(origin.range().source()).flatMap(syntax -> {
            var comments = syntax.trivia().stream().map(t -> t.range().startOffset())
                    .collect(java.util.stream.Collectors.toSet());
            List<SyntaxToken> tokens = syntax.tokens().stream()
                    .filter(t -> t.range().startOffset() >= origin.range().startOffset()
                            && t.range().endOffset() <= origin.range().endOffset())
                    .filter(t -> t.kind() != guru.interlis.syntax.TokenKind.EOF
                            && !comments.contains(t.range().startOffset())).toList();
            int size = tokens.size();
            if (size >= 3 && "END".equals(tokens.get(size - 3).text())
                    && symbol.name().value().equals(tokens.get(size - 2).text())) {
                return Optional.of(tokens.get(size - 2).range());
            }
            return Optional.empty();
        });
    }

    private static int width(Symbol symbol) {
        return symbol.nameRange().map(SourceRange::length).orElse(Integer.MAX_VALUE);
    }

    private static CompletionKind kind(Symbol symbol) {
        return switch (symbol.kind()) {
            case MODEL -> CompletionKind.MODEL;
            case TOPIC -> CompletionKind.TOPIC;
            case CLASS -> CompletionKind.CLASS;
            case STRUCTURE -> CompletionKind.STRUCTURE;
            case ASSOCIATION -> CompletionKind.ASSOCIATION;
            case DOMAIN -> CompletionKind.DOMAIN;
            case ATTRIBUTE -> CompletionKind.ATTRIBUTE;
            case ROLE -> CompletionKind.ROLE;
            case PARAMETER -> CompletionKind.PARAMETER;
            case GRAPHIC -> CompletionKind.GRAPHIC;
            case DRAWING_RULE -> CompletionKind.DRAWING_RULE;
            case LINE_FORM -> CompletionKind.LINE_FORM;
            case METADATA_BASKET -> CompletionKind.METADATA_BASKET;
            case METAOBJECT -> CompletionKind.METAOBJECT;
            case CONSTRAINT -> CompletionKind.CONSTRAINT;
            default -> CompletionKind.KEYWORD;
        };
    }
}
