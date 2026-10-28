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
 * @param returnMandatory explicit result MANDATORY; domain requirements are bound later
 * @param explanation optional interpreted explanation and its original range
 */
public record FunctionDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                        Optional<SourceRange> nameRange,
                                        List<FunctionParameterSyntax> parameters,
                                        TypeSyntax returnType, boolean returnMandatory,Optional<SourceMetadata.Text> explanation) implements DeclarationSyntax {
    /** Creates a validated instance. */
    public FunctionDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(explanation,"explanation");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.FUNCTION;
    }

    @Override
    public List<SyntaxNode> children() {
        var children = new java.util.ArrayList<SyntaxNode>();
        parameters.forEach(p -> children.add(p.type()));
        children.add(returnType);
        return List.copyOf(children);
    }

    /**
     * Declared parameter.
     *
     * @param name parameter name
     * @param type source-level type with exact source range
     * @param mandatory explicit MANDATORY, before resolving domain requirements
     */
    public record FunctionParameterSyntax(String name, TypeSyntax type, boolean mandatory) {
        /** Creates a validated instance. */
        public FunctionParameterSyntax {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }
}
