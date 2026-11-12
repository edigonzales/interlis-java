package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static guru.interlis.validation.DatasetRuleCoverage.Completeness.*;
import static guru.interlis.validation.DatasetRuleCoverage.ScopeKind.*;

final class DatasetUnique {
    private static final byte TAG = 4;
    private final DatasetRuleContext context;
    private final ValidationSession report;
    private final DatasetRulePlan rule;
    private final Map<SymbolRef, List<Binding>> byType;
    private final boolean basket;
    private final int ordinal;
    private final LinkedHashMap<Long, Scope> scopes = new LinkedHashMap<>();

    DatasetUnique(DatasetRuleContext context, ValidationSession report, List<DatasetRulePlan> equivalents,
                  int ordinal) {
        this.context = context;
        this.report = report;
        this.rule = equivalents.getFirst();
        this.basket = ((UniquenessConstraint) rule.declaration().definition()).basket();
        this.ordinal = ordinal;
        var bindings = new LinkedHashMap<SymbolRef, List<Binding>>();
        for (var equivalent : equivalents) {
            var definition = (UniquenessConstraint) equivalent.declaration().definition();
            var where = definition.where().flatMap(context::program);
            var fields = definition.keys().stream().flatMap(id -> context.program(id).stream()).toList();
            boolean supported = definition.where().isEmpty() == where.isEmpty() && !fields.isEmpty()
                    && fields.size() == definition.keys().size();
            var binding = new Binding(equivalent, where, fields, supported);
            for (var type : equivalent.candidateTypes()) {
                bindings.computeIfAbsent(type, ignored -> new ArrayList<>()).add(binding);
            }
        }
        this.byType = bindings;
    }

    private Optional<Binding> binding(DatasetRuleContext.Occurrence occurrence) {
        return byType.getOrDefault(occurrence.object().type(), List.of()).stream()
                .filter(binding -> context.candidate(binding.rule(), occurrence)).findFirst();
    }

    List<DatasetRuleCoverage> check() {
        boolean globalComplete = !basket && context.declaredComplete(rule.key().applicationRoot());
        context.forObjects(occurrence -> {
            var found = binding(occurrence);
            if (found.isEmpty()) {
                return;
            }
            var binding = found.orElseThrow();
            long scopeId = basket ? occurrence.stored().basket() : 0;
            var scope = scopes.computeIfAbsent(scopeId, ignored -> new Scope(
                    basket ? occurrence.basket().consistency() == TransferEvent.Consistency.COMPLETE
                            ? CLOSED_BY_TRANSFER : OPEN : globalComplete ? DECLARED_COMPLETE : OPEN,
                    new DatasetScopeCounts()));
            var counts = scope.counts();
            counts.candidates++;
            counts.selected |= occurrence.selected();
            if (!binding.supported()) {
                counts.unsupported++;
                counts.undecided++;
            } else {
                observe(occurrence, scopeId, counts, binding.where(), binding.fields());
            }
        });
        reportDuplicates();
        if (scopes.isEmpty()) {
            return List.of(new DatasetScopeCounts().coverage(rule.key(), basket ? BASKET : DATASET, OPEN));
        }
        var aggregated = new LinkedHashMap<CoverageKind, DatasetRuleCoverage>();
        for (var scope : scopes.values()) {
            var coverage = scope.counts().coverage(rule.key(), basket ? BASKET : DATASET, scope.completeness());
            aggregated.merge(new CoverageKind(coverage.completeness(), coverage.outcome()), coverage,
                    DatasetUnique::merge);
        }
        return List.copyOf(aggregated.values());
    }

    private void observe(DatasetRuleContext.Occurrence occurrence, long scope, DatasetScopeCounts counts,
                         Optional<LocalExpressions.Program> where, List<LocalExpressions.Program> fields) {
        if (where.isPresent()) {
            var filter = context.evaluate(where.orElseThrow(), occurrence);
            switch (filter) {
                case LocalExpressions.UndefinedByData ignored -> { counts.evaluations++; return; }
                case LocalExpressions.Known known when known.value() instanceof TransferValue.BooleanValue bool -> {
                    if (!bool.value()) {
                        counts.evaluations++;
                        return;
                    }
                }
                default -> {
                    counts.undecided++;
                    if (filter == LocalExpressions.Unsupported.INSTANCE) {
                        counts.unsupported++;
                    }
                    return;
                }
            }
        }
        var semantics = context.semantics(occurrence);
        var parts = new ArrayList<CanonicalKey>();
        ValueSemantics.Gap issue = null;
        for (var field : fields) {
            var type = context.valueType(field, occurrence);
            switch (semantics.key(context.evaluate(field, occurrence), type)) {
                case ValueSemantics.Gap.UNDEFINED -> { counts.evaluations++; return; }
                case ValueSemantics.Gap gap -> issue = gap;
                case ValueSemantics.Known known -> parts.add(known.key());
            }
        }
        if (issue != null) {
            counts.undecided++;
            if (issue == ValueSemantics.Gap.UNSUPPORTED) {
                counts.unsupportedValues++;
            }
            return;
        }
        counts.evaluations++;
        var key = context.indexKey(TAG, ordinal, scope, semantics.tuple(parts));
        long handle = occurrence.handle().ordinal();
        var value = context.keys.get(key).map(ByteBuffer::wrap);
        var group = value.map(Group::read).orElse(new Group(0, handle, 0, 0));
        var updated = new Group(group.size() + 1, group.first(), group.size() == 1 ? handle : group.second(),
                group.selected() == 0 && occurrence.selected() ? handle : group.selected());
        context.keys.put(key, updated.bytes());
    }

    private void reportDuplicates() {
        byte[] lower = ByteBuffer.allocate(1 + Integer.BYTES).put(TAG).putInt(ordinal).array();
        byte[] upper = ByteBuffer.allocate(1 + Integer.BYTES).put(TAG).putInt(Math.incrementExact(ordinal)).array();
        try (var cursor = context.keys.scan(lower, Optional.of(upper))) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                context.visit();
                var entry = next.orElseThrow();
                var group = Group.read(ByteBuffer.wrap(entry.value()));
                if (group.selected() == 0 || group.size() < 2) {
                    continue;
                }
                long scope = ByteBuffer.wrap(entry.key()).getLong(1 + Integer.BYTES);
                var selected = context.occurrence(new ObjectHandle(group.selected()));
                var witness = context.occurrence(new ObjectHandle(group.selected() == group.first()
                        ? group.second() : group.first()));
                var arguments = new ArrayList<>(witness.witness());
                arguments.add(Long.toString(group.size()));
                scopes.get(scope).counts().violations++;
                report.datasetViolation(binding(selected).orElseThrow().rule().declaration(), ValidationCode.DATASET_UNIQUE_DUPLICATE,
                        selected.location(List.of()), arguments);
            }
        }
    }

    private static DatasetRuleCoverage merge(DatasetRuleCoverage a, DatasetRuleCoverage b) {
        return new DatasetRuleCoverage(a.rule(), a.scopeKind(), a.completeness(), a.outcome(),
                Math.addExact(a.scopes(), b.scopes()), Math.addExact(a.candidates(), b.candidates()),
                Math.addExact(a.evaluations(), b.evaluations()), Math.addExact(a.violations(), b.violations()),
                Math.addExact(a.undecidedCandidates(), b.undecidedCandidates()));
    }

    private record Binding(DatasetRulePlan rule, Optional<LocalExpressions.Program> where,
                           List<LocalExpressions.Program> fields, boolean supported) { }

    private record CoverageKind(DatasetRuleCoverage.Completeness completeness, DatasetRuleCoverage.Outcome outcome) { }
    private record Scope(DatasetRuleCoverage.Completeness completeness, DatasetScopeCounts counts) { }

    private record Group(long size, long first, long second, long selected) {
        static Group read(ByteBuffer value) {
            return new Group(value.getLong(), value.getLong(), value.getLong(), value.getLong());
        }

        byte[] bytes() {
            return ByteBuffer.allocate(4 * Long.BYTES).putLong(size).putLong(first).putLong(second).putLong(selected).array();
        }
    }
}
