package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;

/**
 * Bound function call. Children retain the ordered source arguments. Additional
 * parsed string paths are keyed by zero-based argument index, in increasing
 * order; their IDs and targets belong to the same immutable snapshot.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param functionName the {@code functionName} component
 * @param targetFunction the {@code targetFunction} component
 */
public record FunctionCallExpression(ExpressionId id, TypeId typeId,
                                     ExpressionTypeState typeState, SourceRange range,
                                     List<ExpressionId> children, String functionName,
                                     Optional<SymbolId> targetFunction,
                                     Map<Integer,ArgumentPath> argumentPaths)
        implements TypedExpression {
    /** Creates a validated instance. */
    public FunctionCallExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(functionName, "functionName");
        targetFunction = Objects.requireNonNull(targetFunction, "targetFunction");
        Objects.requireNonNull(argumentPaths,"argumentPaths");
        var ordered=new java.util.TreeMap<Integer,ArgumentPath>();
        for(var entry:argumentPaths.entrySet()) {
            if(entry.getKey()<0 || entry.getKey()>=children.size())throw new IllegalArgumentException("argument path index outside arguments");
            var path=Objects.requireNonNull(entry.getValue());
            if(path.rootArgument()>=entry.getKey())throw new IllegalArgumentException("path root must precede its string argument");
            ordered.put(entry.getKey(),path);
        }
        argumentPaths=ordered.isEmpty()?Map.of():java.util.Collections.unmodifiableMap(ordered);
    }
    /** Parsed constant path with its actual root argument and required value type.
     * Empty binding denotes a legal value-dependent object type; syntax is still
     * parsed and retained. A present binding is a PathExpression in this snapshot. */
    public record ArgumentPath(List<ArgumentPathStep> steps,int rootArgument,TypeId expectedType,Optional<ExpressionId> binding) {
        public ArgumentPath {
            steps=List.copyOf(steps);Objects.requireNonNull(expectedType);Objects.requireNonNull(binding);
            if(steps.isEmpty() || rootArgument<0)throw new IllegalArgumentException("invalid argument path");
        }
    }
    /** Original order, qualified association choice and collection index are
     * retained even when the object's concrete type is only known at runtime. */
    public record ArgumentPathStep(String name,SourceRange range,boolean associationAccess,
            Optional<QualifiedName> associationQualifier,Optional<PathIndex> index) {
        public ArgumentPathStep { Objects.requireNonNull(name);Objects.requireNonNull(range);Objects.requireNonNull(associationQualifier);Objects.requireNonNull(index); }
    }

}
