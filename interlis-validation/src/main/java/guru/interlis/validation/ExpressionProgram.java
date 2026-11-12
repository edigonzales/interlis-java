package guru.interlis.validation;

import guru.interlis.model.ExpressionId;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.TypedExpression;

import java.util.Map;

record ExpressionProgram(SnapshotId snapshot, ExpressionId root, Map<ExpressionId, TypedExpression> nodes,
                         Map<ExpressionId, EvaluationResult> constants,
                         Map<ExpressionId, ExpressionEvaluator.Comparison> comparisons,
                         ExpressionRequirements requirements) {
    ExpressionProgram {
        nodes = Map.copyOf(nodes);
        constants = Map.copyOf(constants);
        comparisons = Map.copyOf(comparisons);
    }

    TypedExpression expression() {
        return nodes.get(root);
    }

    EvaluationResult evaluate(EvaluationFrame frame) {
        return new ExpressionEvaluator(this).evaluate(frame);
    }
}
