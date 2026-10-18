package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;

/** Closed typed expression hierarchy; original ranges stay available. */
public sealed interface TypedExpression permits LiteralExpression, PathExpression,
        BinaryExpression, UnaryExpression, DefinedExpression, FunctionCallExpression,
        ConstantExpression, UndefinedExpression, ErrorExpression {

    /**
     * Returns the {@code id} value.
     *
     * @return the value
     */
    ExpressionId id();

    /**
     * Returns the {@code typeId} value.
     *
     * @return the value
     */
    TypeId typeId();

    /**
     * Returns the {@code typeState} value.
     *
     * @return the value
     */
    ExpressionTypeState typeState();

    /**
     * Returns the {@code range} value.
     *
     * @return the value
     */
    SourceRange range();

    /**
     * Returns the {@code children} value.
     *
     * @return the value
     */
    List<ExpressionId> children();
}
