package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.math.BigInteger;
import java.util.*;

/** Structured FORMAT declaration. All values are immutable and null-free;
 * base names and element names retain their own source/revision ranges. */
public record FormattedTypeSyntax(SyntaxNodeId id, SourceRange range, Optional<NameSyntax> base,
        boolean basedOnStructure, boolean inheritance, List<Part> parts,
        Optional<String> minimum, Optional<String> maximum) implements TypeSyntax {
    public FormattedTypeSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(base);
        parts = List.copyOf(parts); Objects.requireNonNull(minimum); Objects.requireNonNull(maximum);
    }
    /** Literal parts have no attribute, width or format; attribute parts have an empty literal. */
    public record Part(Optional<NameSyntax> attribute, Optional<BigInteger> width,
            Optional<NameSyntax> format, String literal, SourceRange range) {
        public Part { Objects.requireNonNull(attribute); Objects.requireNonNull(width);
            Objects.requireNonNull(format); Objects.requireNonNull(literal); Objects.requireNonNull(range); }
    }
    @Override public SyntaxKind kind() { return SyntaxKind.TYPE_FORMATTED; }
    @Override public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>(); base.ifPresent(result::add);
        for (Part part : parts) { part.attribute().ifPresent(result::add); part.format().ifPresent(result::add); }
        return List.copyOf(result);
    }
}
