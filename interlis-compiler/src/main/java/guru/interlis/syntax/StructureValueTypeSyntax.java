package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.*;

/** Source structure type with explicit primary/restriction names and original
 * UTF-16 ranges. Empty target denotes the ANYSTRUCTURE keyword. */
public record StructureValueTypeSyntax(SyntaxNodeId id, SourceRange range,
                                      Optional<NameSyntax> target, List<NameSyntax> restrictions) implements TypeSyntax {
    public StructureValueTypeSyntax {
        Objects.requireNonNull(id,"id");Objects.requireNonNull(range,"range");
        target=Objects.requireNonNull(target,"target");restrictions=List.copyOf(restrictions);
    }
    @Override public SyntaxKind kind() { return SyntaxKind.TYPE_STRUCTURE_VALUE; }
    @Override public List<SyntaxNode> children() {
        var result=new ArrayList<SyntaxNode>();target.ifPresent(result::add);result.addAll(restrictions);return List.copyOf(result);
    }
}
