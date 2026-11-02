package guru.interlis.compiler.query;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;

import java.util.*;

/** Small, pure editor plans derived from captured compiler syntax and complete binding facts. */
public final class EditorAssistanceService {
    public CompletionResult complete(
            AnalysisSnapshot snapshot, CompletionQuery query, CancellationToken token) {
        return EditorCompletions.complete(snapshot, query, token);
    }

    public Optional<EditorEditPlan> planBlockClose(
            AnalysisSnapshot snapshot,
            SourceRange cursor,
            EditorIndentOptions indent,
            CancellationToken token) {
        token.throwIfCancellationRequested();
        var syntax = syntax(snapshot, cursor);
        int offset = cursor.startOffset();
        var source = syntax.source();
        String text = source.text();
        if (EditorSyntax.inert(syntax, offset)) return Optional.empty();
        var before =
                EditorSyntax.tokens(syntax).stream()
                        .filter(t -> t.range().endOffset() <= offset)
                        .toList();
        if (before.isEmpty() || !before.getLast().text().equals("=")) return Optional.empty();
        var equals = before.getLast();
        String gap = text.substring(equals.range().endOffset(), offset);
        if (!gap.contains("\n") || !gap.isBlank()) return Optional.empty();
        var block = EditorSyntax.enclosing(syntax, equals.range().startOffset()).orElse(null);
        if (block == null || block.name().startsWith("<") || block.endName().isPresent())
            return Optional.empty();
        // A nested value assignment has a narrower syntax node than its containing block.
        boolean value =
                EditorSyntax.nodes(syntax).stream()
                        .anyMatch(
                                n ->
                                        (n instanceof AttributeDeclarationSyntax
                                                        || n instanceof DomainDeclarationSyntax
                                                        || n instanceof UnitDeclarationSyntax)
                                                && n.range().startOffset()
                                                        <= equals.range().startOffset()
                                                && equals.range().endOffset()
                                                        <= n.range().endOffset());
        if (value) return Optional.empty();
        if (syntax.diagnostics().stream()
                .anyMatch(
                        d ->
                                d.primaryRange()
                                        .filter(
                                                r ->
                                                        r.startOffset()
                                                                        >= block.node()
                                                                                .range()
                                                                                .startOffset()
                                                                && r.startOffset()
                                                                        < equals.range()
                                                                                .startOffset())
                                        .isPresent())) return Optional.empty();
        var header =
                EditorSyntax.tokens(syntax).stream()
                        .filter(
                                t ->
                                        t.range().startOffset()
                                                        >= block.node().range().startOffset()
                                                && t.range().endOffset()
                                                        <= equals.range().endOffset())
                        .toList();
        if (header.stream().filter(t -> t.text().equals("=")).count() != 1
                || header.stream().anyMatch(t -> t.text().equals(";") || t.text().equals(":")))
            return Optional.empty();
        int lineStart =
                text.lastIndexOf('\n', Math.max(0, block.node().range().startOffset() - 1)) + 1;
        var base = new StringBuilder();
        for (int i = lineStart;
                i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t');
                i++) base.append(text.charAt(i));
        String eol = text.contains("\r\n") ? "\r\n" : "\n",
                body = base + indent.unit(),
                existing =
                        text.substring(text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1, offset);
        if (!existing.isBlank()) return Optional.empty();
        int start = offset - existing.length();
        String first = body + (block.viewTopic() ? "DEPENDS ON " : "");
        String replacement =
                first
                        + eol
                        + (block.viewTopic() ? eol : "")
                        + base
                        + "END "
                        + block.name()
                        + (block.model() ? "." : ";");
        return Optional.of(
                new EditorEditPlan(
                        "block.close",
                        "Complete INTERLIS block",
                        snapshot.snapshotId(),
                        source.id(),
                        source.revision(),
                        List.of(new TextEdit(source.range(start, offset), replacement)),
                        start + first.length()));
    }

    public List<EditorEditPlan> quickFixes(
            AnalysisSnapshot snapshot, SourceRange requested, CancellationToken token) {
        var s = syntax(snapshot, requested);
        var source = s.source();
        var tokens = EditorSyntax.tokens(s);
        var out = new ArrayList<EditorEditPlan>();
        for (var n : EditorSyntax.nodes(s)) {
            token.throwIfCancellationRequested();
            // A missing outer/inner END can make recovery assign an existing closer
            // to the wrong declaration. Only a syntax-complete tree proves a rename.
            if (!s.diagnostics().isEmpty()) break;
            var b = EditorSyntax.block(n).map(v -> EditorSyntax.withActualEnd(s, v)).orElse(null);
            if (b == null
                    || b.endName().isEmpty()
                    || b.name().equals(b.endName().orElseThrow())
                    || b.name().startsWith("<")) continue;
            var within =
                    tokens.stream()
                            .filter(
                                    t ->
                                            t.range().startOffset() >= n.range().startOffset()
                                                    && t.range().endOffset()
                                                            <= n.range().endOffset())
                            .toList();
            for (int i = within.size() - 2; i >= 0; i--)
                if (within.get(i).text().equals("END")) {
                    var name = within.get(i + 1);
                    if (name.kind() == TokenKind.IDENTIFIER
                            && name.text().equals(b.endName().orElseThrow())
                            && near(requested, name.range()))
                        out.add(
                                plan(
                                        snapshot,
                                        source,
                                        "end.name",
                                        "Correct END name to " + b.name(),
                                        name.range(),
                                        b.name()));
                    break;
                }
        }
        // Only the parser's explicit missing-semicolon recovery is eligible. Reparse verifies the
        // repair.
        for (var d : s.diagnostics()) {
            token.throwIfCancellationRequested();
            boolean expectedSemicolon =
                    d.arguments().stream()
                            .anyMatch(
                                    a ->
                                            a
                                                            instanceof
                                                            guru.interlis.diagnostics
                                                                            .DiagnosticArgument
                                                                            .IdentifierListArgument
                                                                    v
                                                    && v.name().equals("expected")
                                                    && v.values().contains("';'")
                                                    && v.values().stream()
                                                            .allMatch(
                                                                    x ->
                                                                            Set.of(
                                                                                            "';'",
                                                                                            "'CONSTRAINTS'")
                                                                                    .contains(x)));
            if (!expectedSemicolon || d.primaryRange().isEmpty()) continue;
            var at = d.primaryRange().orElseThrow();
            if (!near(requested, at)) continue;
            var previous =
                    tokens.stream()
                            .filter(t -> t.range().endOffset() <= at.startOffset())
                            .reduce((a, b) -> b)
                            .orElse(null);
            if (previous == null || previous.text().equals(";")) continue;
            int pos = previous.range().endOffset();
            String changed = source.text().substring(0, pos) + ";" + source.text().substring(pos);
            var parsed =
                    SyntaxServices.create()
                            .parse(
                                    new SourceText(source.id(), source.revision(), changed),
                                    s.options(),
                                    token);
            if (parsed.diagnostics().size() < s.diagnostics().size())
                out.add(
                        plan(
                                snapshot,
                                source,
                                "semicolon",
                                "Insert missing semicolon",
                                source.range(pos, pos),
                                ";"));
        }
        if (s.diagnostics().isEmpty()
                && snapshot.diagnostics().stream()
                        .noneMatch(d -> d.severity() == guru.interlis.diagnostics.Severity.ERROR)
                && snapshot.coverage().incompleteEntries().isEmpty()
                && !snapshot.coverage().entries().isEmpty()
                && snapshot.semanticGraph().references().stream()
                        .allMatch(r -> r.resolution() instanceof BindingResolution.BoundTarget))
            unusedImports(snapshot, s, requested, out, token);
        return List.copyOf(out);
    }

    private static void unusedImports(
            AnalysisSnapshot snapshot,
            SyntaxSnapshot syntax,
            SourceRange requested,
            List<EditorEditPlan> out,
            CancellationToken token) {
        var graph = snapshot.semanticGraph();
        var tokens = EditorSyntax.tokens(syntax);
        for (var n : EditorSyntax.nodes(syntax)) {
            if (!(n instanceof ModelSyntax model)) continue;
            for (var imp : model.imports()) {
                token.throwIfCancellationRequested();
                if (!near(requested, imp.range())) continue;
                var targets =
                        graph.symbols().stream()
                                .filter(
                                        v ->
                                                v instanceof ModelSymbol
                                                        && v.name().value().equals(imp.modelName())
                                                        && v.languageVersion()
                                                                == model.languageVersion())
                                .toList();
                if (targets.size() != 1) continue;
                var target = targets.getFirst();
                boolean used =
                        graph.references().stream()
                                .filter(
                                        r ->
                                                r.range().source().equals(syntax.source().id())
                                                        && r.range().startOffset()
                                                                >= model.range().startOffset()
                                                        && r.range().endOffset()
                                                                <= model.range().endOffset())
                                .filter(
                                        r ->
                                                model.imports().stream()
                                                        .noneMatch(
                                                                i ->
                                                                        i.range().startOffset()
                                                                                        <= r.range()
                                                                                                .startOffset()
                                                                                && r.range()
                                                                                                .endOffset()
                                                                                        <= i.range()
                                                                                                .endOffset()))
                                .anyMatch(
                                        r ->
                                                r.segments().stream()
                                                        .flatMap(seg -> seg.target().stream())
                                                        .anyMatch(
                                                                id ->
                                                                        modelOf(graph, id)
                                                                                .filter(
                                                                                        target.id()
                                                                                                ::equals)
                                                                                .isPresent()));
                if (used) continue;
                var named =
                        tokens.stream()
                                .filter(
                                        t ->
                                                t.text().equals(imp.modelName())
                                                        && t.range().startOffset()
                                                                >= imp.range().startOffset()
                                                        && t.range().endOffset()
                                                                <= imp.range().endOffset())
                                .findFirst()
                                .orElse(null);
                if (named == null) continue;
                int i = tokens.indexOf(named),
                        start = named.range().startOffset(),
                        end = named.range().endOffset();
                int first = i;
                if (i > 0 && tokens.get(i - 1).text().equals("UNQUALIFIED")) {
                    first = i - 1;
                    start = tokens.get(first).range().startOffset();
                }
                if (i + 1 < tokens.size() && tokens.get(i + 1).text().equals(","))
                    end = tokens.get(i + 1).range().endOffset();
                else if (first > 0 && tokens.get(first - 1).text().equals(","))
                    start = tokens.get(first - 1).range().startOffset();
                else {
                    var keyword =
                            tokens.subList(0, i).reversed().stream()
                                    .filter(t -> t.text().equals("IMPORTS"))
                                    .findFirst()
                                    .orElse(null);
                    if (keyword == null
                            || i + 1 >= tokens.size()
                            || !tokens.get(i + 1).text().equals(";")) continue;
                    start = keyword.range().startOffset();
                    end = tokens.get(i + 1).range().endOffset();
                }
                out.add(
                        plan(
                                snapshot,
                                syntax.source(),
                                "import.unused",
                                "Remove unused import " + imp.modelName(),
                                syntax.source().range(start, end),
                                ""));
            }
        }
    }

    private static Optional<SymbolId> modelOf(SemanticGraph g, SymbolId id) {
        var seen = new HashSet<SymbolId>();
        var s = g.symbol(id).orElse(null);
        while (s != null && seen.add(s.id())) {
            if (s instanceof ModelSymbol) return Optional.of(s.id());
            s = s.owner().flatMap(g::symbol).orElse(null);
        }
        return Optional.empty();
    }

    private static boolean near(SourceRange a, SourceRange b) {
        return a.source().equals(b.source())
                && a.startOffset() <= b.endOffset()
                && b.startOffset() <= a.endOffset();
    }

    private static EditorEditPlan plan(
            AnalysisSnapshot s,
            SourceText text,
            String id,
            String title,
            SourceRange range,
            String replacement) {
        return new EditorEditPlan(
                id,
                title,
                s.snapshotId(),
                text.id(),
                text.revision(),
                List.of(new TextEdit(range, replacement)),
                -1);
    }

    private static SyntaxSnapshot syntax(AnalysisSnapshot s, SourceRange cursor) {
        var syntax =
                s.syntaxSnapshot(cursor.source())
                        .orElseThrow(() -> new IllegalArgumentException("source unavailable"));
        if (!syntax.source().revision().equals(cursor.revision())
                || cursor.endOffset() > syntax.source().length())
            throw new IllegalArgumentException("stale editor range");
        return syntax;
    }
}
