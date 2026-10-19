package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * FUNCTION declaration.
 *
 * @param id stable identifier
 * @param range source range
 * @param name function name
 * @param nameRange optional name range
 * @param parameters declared parameters
 * @param returnType declared result type
 */
public record FunctionDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                        Optional<SourceRange> nameRange,
                                        List<FunctionParameterSyntax> parameters,
                                        String returnType) implements DeclarationSyntax {
    /** Creates a validated instance. */
    public FunctionDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(returnType, "returnType");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.FUNCTION;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }

    /**
     * Declared parameter.
     *
     * @param name parameter name
     * @param typeName source-level type name
     */
    public record FunctionParameterSyntax(String name, String typeName) {
        /** Creates a validated instance. */
        public FunctionParameterSyntax {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(typeName, "typeName");
        }
    }
}
