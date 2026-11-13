package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.GeometryType;
import guru.interlis.model.SymbolId;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

final class GeometryScopeRunner {
    private final DatasetRuleContext dataset;

    GeometryScopeRunner(DatasetRuleContext dataset) {
        this.dataset = dataset;
    }

    List<DatasetRuleCoverage> check(ValidationSession report) {
        var rules = new LinkedHashMap<SymbolId, AttributeSymbol>();
        for (var object : dataset.plan.transferPlan().objects()) {
            dataset.visit();
            if (!object.transferable() || object.topic().filter(dataset.plan.subjects()::selectsTopic).isEmpty()) {
                continue;
            }
            for (var field : dataset.plan.object(object.symbol()).fields()) {
                if (area(field.values().getFirst().type())) {
                    var attribute = declaration(field.slot().attribute());
                    rules.putIfAbsent(attribute.id(), attribute);
                }
            }
        }
        var results = new ArrayList<DatasetRuleCoverage>();
        for (var attribute : rules.values()) {
            var symbol = dataset.plan.ref(attribute.id());
            var key = new RuleKey(symbol, dataset.plan.ref(attribute.header().owner().orElseThrow()), Optional.of(symbol));
            long[] candidates = {0};
            long[] violations = {0};
            boolean[] missing = {false};
            var engine = dataset.plan.bindings().geometry();
            var source = new GeometryEngine.Source() {
                @Override public GeometryEngine.Cursor open() {
                    candidates[0] = 0;
                    return dataset.geometryCursor(occurrence -> select(attribute, occurrence, missing).map(input -> {
                        candidates[0]++;
                        return input;
                    }));
                }

                @Override public boolean complete() {
                    return dataset.geometryPopulationClosed() && !missing[0];
                }
            };
            var check = engine.map(value -> value.checkCoverage(source, GeometryEngine.CoveragePurpose.AREA,
                    new GeometryEngine.Control(dataset::visit, finding -> {
                        violations[0]++;
                        report.geometryFinding(attribute, finding);
                    }))).orElse(GeometryEngine.Check.UNSUPPORTED);
            var outcome = switch (check) {
                case VALID -> DatasetRuleCoverage.Outcome.SATISFIED;
                case INVALID -> DatasetRuleCoverage.Outcome.VIOLATED;
                case MISSING_PARAMETERS -> source.complete() ? DatasetRuleCoverage.Outcome.UNDETERMINED
                        : DatasetRuleCoverage.Outcome.NO_VIOLATION_IN_AVAILABLE_SCOPE;
                case UNSUPPORTED -> DatasetRuleCoverage.Outcome.UNSUPPORTED;
                case NUMERICALLY_UNDETERMINED -> DatasetRuleCoverage.Outcome.UNDETERMINED;
            };
            boolean decided = check == GeometryEngine.Check.VALID || check == GeometryEngine.Check.INVALID;
            results.add(new DatasetRuleCoverage(key, DatasetRuleCoverage.ScopeKind.DATASET, outcome, 1,
                    candidates[0], decided ? candidates[0] : 0, violations[0], decided ? 0 : candidates[0]));
        }
        return results;
    }

    private Optional<GeometryEngine.Input> select(AttributeSymbol declaration, DatasetRuleContext.Occurrence occurrence,
                                                  boolean[] missing) {
        if (!occurrence.selected() || occurrence.input().descriptor().role() != InputRole.PRIMARY) {
            return Optional.empty();
        }
        var object = dataset.plan.object(occurrence.object().type());
        var field = object.field(declaration.id());
        if (field == null || !area(field.values().getFirst().type())) {
            return Optional.empty();
        }
        var slot = field.slot();
        var type = (GeometryType) field.values().getFirst().type();
        var basket = ValidationSession.BasketContext.of(occurrence.basket(), dataset.plan);
        var value = occurrence.object().data().value(slot.symbol());
        if (value.isEmpty() && field.derived()) {
            var derived = dataset.geometryDerivation(occurrence, slot.attribute());
            if (derived instanceof EvaluationResult.Known known) {
                value = Optional.of(known.value());
            } else if (!(derived instanceof EvaluationResult.UndefinedByData)) {
                missing[0] = true;
            }
        }
        if (!(value.orElse(null) instanceof TransferGeometry geometry)) {
            if (value.filter(supplied -> supplied != TransferValue.Undefined.INSTANCE).isPresent()
                    || field.values().getFirst().required()) {
                missing[0] = true;
            }
            return Optional.empty();
        }
        var vertex = type.vertexDomain().map(id -> GeometryParameters.coordinate(dataset.plan, basket, id));
        return Optional.of(new GeometryEngine.Input(geometry, type,
                occurrence.location(List.of(new DataLocation.Slot(slot.symbol()))), slot.attribute().languageVersion(),
                GeometryParameters.quantum(vertex.orElse(null)),
                GeometryParameters.overlap(type, id -> GeometryParameters.coordinate(dataset.plan, basket, id)),
                occurrence.partial(), vertex.map(guru.interlis.model.CoordinateType::id)));
    }

    private AttributeSymbol declaration(AttributeSymbol attribute) {
        while (attribute.overrides().isPresent()) {
            var inherited = (AttributeSymbol) dataset.plan.symbol(attribute.overrides().orElseThrow()).orElseThrow();
            if (!dataset.plan.effectiveType(inherited.declaredType()).filter(GeometryScopeRunner::area).isPresent()) {
                break;
            }
            attribute = inherited;
        }
        return attribute;
    }

    private static boolean area(guru.interlis.model.TypeDescriptor type) {
        return type instanceof GeometryType geometry && switch (geometry.geometryKind()) {
            case AREA, MULTIAREA -> true;
            default -> false;
        };
    }
}
