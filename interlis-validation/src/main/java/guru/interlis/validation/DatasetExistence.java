package guru.interlis.validation;

import guru.interlis.model.CoordinateType;
import guru.interlis.model.ExistenceConstraint;
import guru.interlis.model.GeometryType;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathStep;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.TransferGeometry;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.DatasetScopeStore.Completeness.CLOSED_DELIVERY;
import static guru.interlis.validation.DatasetScopeStore.Completeness.OPEN;
import static guru.interlis.validation.DatasetRuleCoverage.ScopeKind.DATASET;

final class DatasetExistence {
    private static final byte TAG = 5;
    private final DatasetRuleContext context;
    private final ValidationSession report;
    private final DatasetRulePlan rule;
    private final int ordinal;
    private final List<Alternative> alternatives;
    private final Optional<ExpressionProgram> source;
    private final DatasetScopeCounts counts = new DatasetScopeCounts();

    DatasetExistence(DatasetRuleContext context, ValidationSession report, DatasetRulePlan rule,
                     ExistenceConstraint existence, int ordinal) {
        this.context = context;
        this.report = report;
        this.rule = rule;
        this.ordinal = ordinal;
        this.source = context.program(existence.source());
        var targets = new ArrayList<Alternative>();
        for (var target : existence.targets()) {
            targets.add(new Alternative(context.plan.ref(target.viewable()), context.program(target.path()), targets.size()));
        }
        this.alternatives = List.copyOf(targets);
    }

    DatasetRuleCoverage check() {
        if (source.isPresent() && alternatives.stream().anyMatch(alternative -> alternative.program.isPresent())) {
            context.forObjects(this::indexTargets);
        }
        context.forObjects(this::checkSource);
        var completeness = context.declaredComplete(rule.key().applicationRoot()) ? CLOSED_DELIVERY : OPEN;
        return counts.coverage(rule.key(), DATASET, completeness);
    }

    private void indexTargets(DatasetRuleContext.Occurrence occurrence) {
        var semantics = context.semantics(occurrence);
        for (var alternative : alternatives) {
            context.visit();
            if (alternative.program.isEmpty() || !context.target(alternative.root, occurrence)) {
                continue;
            }
            var program = alternative.program.orElseThrow();
            var key = semantics.existenceKey(context.evaluate(program, occurrence), context.valueType(program, occurrence));
            switch (key) {
                case ValueSemantics.Known known -> context.keys.putIfAbsent(indexKey(alternative, known.key()),
                        ByteBuffer.allocate(Long.BYTES).putLong(occurrence.handle().ordinal()).array());
                case ValueSemantics.Gap.UNDEFINED -> alternative.unknown |= occurrence.partial();
                case ValueSemantics.Gap.UNSUPPORTED -> alternative.unsupportedValue = true;
                case ValueSemantics.Gap ignored -> alternative.unknown = true;
            }
        }
    }

    private void checkSource(DatasetRuleContext.Occurrence occurrence) {
        if (!occurrence.selected() || !context.candidate(rule, occurrence)) {
            return;
        }
        counts.selected = true;
        counts.candidates++;
        if (source.isEmpty()) {
            unsupported();
            return;
        }
        var program = source.orElseThrow();
        var result = context.evaluate(program, occurrence);
        var type = context.valueType(program, occurrence);
        var path = sourcePath(program);
        var semantics = context.semantics(occurrence);
        if (type instanceof GeometryType geometry && result instanceof EvaluationResult.Known known
                && known.value() instanceof TransferGeometry value) {
            checkVertices(occurrence, path, semantics, geometry, value);
            return;
        }
        switch (semantics.existenceKey(result, type)) {
            case ValueSemantics.Gap.UNDEFINED -> counts.evaluations++;
            case ValueSemantics.Gap.UNSUPPORTED -> unsupportedValue();
            case ValueSemantics.Gap ignored -> counts.undecided++;
            case ValueSemantics.Known key -> reportResult(occurrence, path, find(key.key(), false));
        }
    }

    private void checkVertices(DatasetRuleContext.Occurrence occurrence, List<DataLocation.Step> path,
                               ValueSemantics semantics, GeometryType type, TransferGeometry geometry) {
        var forms = GeometryRules.forms(context.plan, type);
        if (!GeometryRules.standardForms(forms) || !GeometryRules.accepts(type, geometry, occurrence.partial())
                || alternatives.stream().noneMatch(Alternative::coordinate)) {
            unsupportedValue();
            return;
        }
        var result = new VertexResult();
        GeometryRules.vertices(geometry, forms, context::visit, (coordinate, suffix) -> {
            var witness = find(semantics.coordinate(coordinate), true);
            if (witness == Witness.DEFINITELY_MISSING) {
                if (result.firstMissing == null) {
                    var location = new ArrayList<>(path);
                    location.addAll(suffix);
                    result.firstMissing = List.copyOf(location);
                }
                result.missing++;
            } else if (witness != Witness.FOUND) {
                result.unknown = true;
                result.unsupportedExpression |= witness == Witness.UNSUPPORTED_EXPRESSION;
                result.unsupportedValue |= witness == Witness.UNSUPPORTED_VALUE;
            }
        });
        if (result.missing > 0) {
            counts.evaluations++;
            counts.violations++;
            report.datasetViolation(rule.declaration(), ValidationCode.EXISTENCE_TARGET_MISSING,
                    occurrence.location(result.firstMissing), List.of(Long.toString(result.missing)));
        } else if (result.unsupportedValue) {
            unsupportedValue();
        } else if (result.unsupportedExpression) {
            unsupported();
        } else if (result.unknown || occurrence.partial()) {
            counts.undecided++;
        } else {
            counts.evaluations++;
        }
    }

    private Witness find(CanonicalKey key, boolean geometryVertex) {
        boolean unknown = false;
        boolean unsupportedExpression = false;
        boolean unsupportedValue = false;
        for (var alternative : alternatives) {
            context.visit();
            if (alternative.program.isEmpty()) {
                unsupportedExpression = true;
                continue;
            }
            if (geometryVertex && !alternative.coordinate()) {
                unsupportedValue = true;
                continue;
            }
            if (context.keys.get(indexKey(alternative, key)).isPresent()) {
                return Witness.FOUND;
            }
            unknown |= !alternative.closed || alternative.unknown;
            unsupportedValue |= alternative.unsupportedValue;
        }
        if (unsupportedValue) {
            return Witness.UNSUPPORTED_VALUE;
        }
        if (unsupportedExpression) {
            return Witness.UNSUPPORTED_EXPRESSION;
        }
        return unknown ? Witness.UNKNOWN : Witness.DEFINITELY_MISSING;
    }

    private void reportResult(DatasetRuleContext.Occurrence occurrence, List<DataLocation.Step> path, Witness witness) {
        if (witness == Witness.UNSUPPORTED_VALUE) {
            unsupportedValue();
            return;
        }
        if (witness == Witness.UNSUPPORTED_EXPRESSION) {
            unsupported();
            return;
        }
        if (witness == Witness.UNKNOWN) {
            counts.undecided++;
            return;
        }
        counts.evaluations++;
        if (witness == Witness.DEFINITELY_MISSING) {
            counts.violations++;
            report.datasetViolation(rule.declaration(), ValidationCode.EXISTENCE_TARGET_MISSING,
                    occurrence.location(path), List.of());
        }
    }

    private void unsupported() {
        counts.unsupported++;
        counts.undecided++;
    }

    private void unsupportedValue() {
        counts.unsupportedValues++;
        counts.undecided++;
    }

    private byte[] indexKey(Alternative alternative, CanonicalKey key) {
        return context.indexKey(TAG, ordinal, alternative.ordinal, key);
    }

    private List<DataLocation.Step> sourcePath(ExpressionProgram program) {
        if (!(program.expression() instanceof PathExpression path)) {
            return List.of();
        }
        return path.path().steps().stream().filter(PathStep.AttributeStep.class::isInstance)
                .map(PathStep.AttributeStep.class::cast)
                .map(step -> (DataLocation.Step) new DataLocation.Slot(context.plan.ref(step.target()))).toList();
    }

    private TypeDescriptor effectiveType(ExpressionProgram program) {
        return context.plan.effectiveType(program.expression().typeId()).orElse(null);
    }

    private enum Witness { FOUND, DEFINITELY_MISSING, UNKNOWN, UNSUPPORTED_EXPRESSION, UNSUPPORTED_VALUE }

    private final class Alternative {
        private final SymbolRef root;
        private final Optional<ExpressionProgram> program;
        private final int ordinal;
        private final boolean closed;
        private final boolean coordinate;
        private boolean unknown;
        private boolean unsupportedValue;

        Alternative(SymbolRef root, Optional<ExpressionProgram> program, int ordinal) {
            this.root = root;
            this.program = program;
            this.ordinal = ordinal;
            this.closed = context.declaredComplete(root);
            this.coordinate = program.map(DatasetExistence.this::effectiveType)
                    .filter(CoordinateType.class::isInstance).map(CoordinateType.class::cast)
                    .filter(type -> !type.multiCoordinate()).isPresent();
        }

        boolean coordinate() {
            return coordinate;
        }
    }

    private static final class VertexResult {
        private boolean unsupportedExpression;
        private List<DataLocation.Step> firstMissing;
        private long missing;
        private boolean unknown;
        private boolean unsupportedValue;
    }
}
