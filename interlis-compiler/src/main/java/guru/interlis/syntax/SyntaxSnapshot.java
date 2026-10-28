package guru.interlis.syntax;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.source.SourceText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable syntax result for one source revision. It retains the original
 * source, own nodes/tokens/trivia and canonical syntax diagnostics.
 */
public final class SyntaxSnapshot {
    private final SourceText source;
    private final ParseOptions options;
    private final LanguageDetection language;
    private final DocumentSyntax root;
    private final List<SyntaxToken> tokens;
    private final List<TriviaSpan> trivia;
    private final List<Diagnostic> diagnostics;
    private final SyntaxCoverage coverage;
    private final SyntaxFingerprint fingerprint;
    private final java.util.Map<guru.interlis.source.SourceRange,SourceMetadata> metadata;

    public SyntaxSnapshot(SourceText source, ParseOptions options, LanguageDetection language,
                          DocumentSyntax root, List<SyntaxToken> tokens,
                          List<TriviaSpan> trivia, List<Diagnostic> diagnostics,
                          SyntaxCoverage coverage, SyntaxFingerprint fingerprint,
                          java.util.Map<guru.interlis.source.SourceRange,SourceMetadata> metadata) {
        this.source = Objects.requireNonNull(source, "source");
        this.options = Objects.requireNonNull(options, "options");
        this.language = Objects.requireNonNull(language, "language");
        this.root = Objects.requireNonNull(root, "root");
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        this.trivia = List.copyOf(Objects.requireNonNull(trivia, "trivia"));
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.metadata=java.util.Map.copyOf(metadata);
        for(var entry:this.metadata.entrySet()) {
            checkMetadataRange(entry.getKey());
            entry.getValue().attributes().forEach(value->checkMetadataRange(value.range()));
            entry.getValue().documentation().forEach(value->checkMetadataRange(value.range()));
        }
    }

    /** Annotation groups keyed by the following token's original range. */
    public java.util.Map<guru.interlis.source.SourceRange,SourceMetadata> metadata() { return metadata; }

    private void checkMetadataRange(guru.interlis.source.SourceRange range) {
        if(!range.source().equals(source.id()) || !range.revision().equals(source.revision()) || range.endOffset()>source.length())
            throw new IllegalArgumentException("metadata belongs to another source revision");
    }

    /**
     * Executes {@code source}.
     *
     * @return the result
     */
    public SourceText source() {
        return source;
    }

    /**
     * Executes {@code mode}.
     *
     * @return the result
     */
    public ParseMode mode() {
        return options.mode();
    }

    /** Exact parse profile, including resource limits; part of syntax reuse identity. */
    public ParseOptions options() {
        return options;
    }

    /**
     * Executes {@code language}.
     *
     * @return the result
     */
    public LanguageDetection language() {
        return language;
    }

    /**
     * Executes {@code root}.
     *
     * @return the result
     */
    public DocumentSyntax root() {
        return root;
    }

    /**
     * Executes {@code tokens}.
     *
     * @return the result
     */
    public List<SyntaxToken> tokens() {
        return tokens;
    }

    /**
     * Executes {@code trivia}.
     *
     * @return the result
     */
    public List<TriviaSpan> trivia() {
        return trivia;
    }

    /**
     * Executes {@code diagnostics}.
     *
     * @return the result
     */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * Executes {@code coverage}.
     *
     * @return the result
     */
    public SyntaxCoverage coverage() {
        return coverage;
    }

    /**
     * Executes {@code consumedEntireInput}.
     *
     * @return the result
     */
    public boolean consumedEntireInput() {
        return coverage.consumedEntireInput();
    }

    /**
     * Executes {@code fingerprint}.
     *
     * @return the result
     */
    public SyntaxFingerprint fingerprint() {
        return fingerprint;
    }

    /** Returns the deterministic smallest or largest node around an offset. */
    public Optional<SyntaxNode> nodeAt(int offset, NodeSelection selection) {
        Objects.requireNonNull(selection, "selection");
        if (offset < 0 || offset > source.length()) {
            throw new IllegalArgumentException("offset outside source");
        }
        List<SyntaxNode> candidates = new ArrayList<>();
        collect(root, offset, candidates);
        Comparator<SyntaxNode> comparator = Comparator
                .comparingInt(SyntaxNode::width)
                .thenComparing(n -> n.id().value());
        if (selection == NodeSelection.LARGEST) {
            comparator = comparator.reversed();
        }
        return candidates.stream().min(comparator);
    }

    private static void collect(SyntaxNode node, int offset, List<SyntaxNode> target) {
        if (node.range().contains(offset)
                || (node.range().isEmpty() && node.range().startOffset() == offset)) {
            target.add(node);
            for (SyntaxNode child : node.children()) {
                collect(child, offset, target);
            }
        }
    }

    /** Lossless reconstruction of the original input. */
    public String reconstruct() {
        return source.text();
    }
}
