package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.validation.DatasetScopeStore.Scope;
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
    private final List<Binding> bindings;
    private final int ordinal;
    private final DatasetScopeStore scopes;

    DatasetUnique(DatasetRuleContext context, ValidationSession report, List<DatasetRulePlan> equivalents,
                  int ordinal) {
        this.context = context;
        this.report = report;
        this.rule = equivalents.getFirst();
        this.basket = ((UniquenessConstraint) rule.declaration().definition()).basket();
        this.ordinal = ordinal;
        this.scopes = new DatasetScopeStore(context.temporary, ordinal);
        var byType = new LinkedHashMap<SymbolRef, List<Binding>>();
        var bindings = new ArrayList<Binding>();
        for (var equivalent : equivalents) {
            var definition = (UniquenessConstraint) equivalent.declaration().definition();
            var where = definition.where().flatMap(context::program);
            var fields = definition.keys().stream().flatMap(id -> context.program(id).stream()).toList();
            boolean supported = definition.where().isEmpty() == where.isEmpty() && !fields.isEmpty()
                    && fields.size() == definition.keys().size();
            var binding = new Binding(equivalent, where, fields, supported);
            bindings.add(binding);
            for (var type : equivalent.candidateTypes()) {
                byType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(binding);
            }
        }
        this.byType = byType;
        this.bindings = List.copyOf(bindings);
    }

    private Optional<Binding> binding(DatasetRuleContext.Occurrence occurrence) {
        return byType.getOrDefault(occurrence.object().type(), List.of()).stream()
                .filter(binding -> context.candidate(binding.rule(), occurrence)).findFirst();
    }

    List<DatasetRuleCoverage> check() {
        boolean globalComplete = !basket && context.declaredComplete(rule.key().applicationRoot());
        for (var binding : bindings) {
            context.initializeScopes(binding.rule(), basket, true, (ordinal, completeness) -> {
                if (scopes.get(ordinal).isEmpty()) {
                    var counts = new DatasetScopeCounts();
                    counts.selected = true;
                    counts.unsupported = binding.supported() ? 0 : 1;
                    scopes.put(ordinal, new Scope(basket ? completeness : globalComplete ? DECLARED_COMPLETE : OPEN,
                            counts));
                }
            });
        }
        context.forObjects(occurrence -> {
            var found = binding(occurrence);
            if (found.isEmpty()) {
                return;
            }
            var binding = found.orElseThrow();
            long scopeId = basket ? occurrence.stored().basket() : 0;
            var scope = scopes.get(scopeId).orElseGet(() -> new Scope(
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
            scopes.put(scopeId, scope);
        });
        reportDuplicates();
        var coverage = new DatasetScopeCounts.Coverage();
        scopes.forScopes((ordinal, scope) -> {
            context.visit();
            coverage.add(scope.counts().coverage(rule.key(), basket ? BASKET : DATASET, scope.completeness()));
        });
        var reports = coverage.reports();
        return reports.isEmpty() ? List.of(new DatasetRuleCoverage(rule.key(), basket ? BASKET : DATASET, OPEN,
                DatasetRuleCoverage.Outcome.NOT_APPLICABLE, 0, 0, 0, 0, 0)) : reports;
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
                var scopeData = scopes.get(scope).orElseThrow();
                scopeData.counts().violations++;
                scopes.put(scope, scopeData);
                report.datasetViolation(binding(selected).orElseThrow().rule().declaration(), ValidationCode.DATASET_UNIQUE_DUPLICATE,
                        selected.location(List.of()), arguments);
            }
        }
    }

    private record Binding(DatasetRulePlan rule, Optional<LocalExpressions.Program> where,
                           List<LocalExpressions.Program> fields, boolean supported) { }


    private record Group(long size, long first, long second, long selected) {
        static Group read(ByteBuffer value) {
            return new Group(value.getLong(), value.getLong(), value.getLong(), value.getLong());
        }

        byte[] bytes() {
            return ByteBuffer.allocate(4 * Long.BYTES).putLong(size).putLong(first).putLong(second).putLong(selected).array();
        }
    }
}
