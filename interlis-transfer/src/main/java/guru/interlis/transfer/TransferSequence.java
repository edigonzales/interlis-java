package guru.interlis.transfer;

import guru.interlis.model.DomainSymbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent.BasketKind;
import guru.interlis.transfer.TransferEvent.EndBasket;
import guru.interlis.transfer.TransferEvent.EndTransfer;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.transfer.TransferEvent.StartTransfer;
import guru.interlis.transfer.TransferIdentity.BasketId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static guru.interlis.transfer.TransferProtocolException.Reason.CLOSED;
import static guru.interlis.transfer.TransferProtocolException.Reason.CONTEXT_MISMATCH;
import static guru.interlis.transfer.TransferProtocolException.Reason.DUPLICATE_ID;
import static guru.interlis.transfer.TransferProtocolException.Reason.FAILED;
import static guru.interlis.transfer.TransferProtocolException.Reason.FOREIGN_SNAPSHOT;
import static guru.interlis.transfer.TransferProtocolException.Reason.UNEXPECTED_EVENT;
import static guru.interlis.transfer.TransferProtocolException.Reason.UNKNOWN_MODEL_ELEMENT;

/** Shared sequence checks with the active basket context and transfer-scoped identity sets. */
final class TransferSequence {
    private final TransferPlan plan;
    private final boolean includeModelImports;
    private State state = State.BEFORE_TRANSFER;
    private Set<SymbolRef> declaredModels = Set.of();
    private Set<String> basketIds = new HashSet<>();
    private Set<String> fullObjectIds = new HashSet<>();
    private ActiveBasket basket;
    private boolean closed;
    private boolean failed;

    TransferPlan plan() {
        return plan;
    }

    TransferSequence(TransferPlan plan, boolean includeModelImports) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.includeModelImports = includeModelImports;
    }

    void requireOpen() {
        if (closed) {
            throw new TransferProtocolException(CLOSED, "Transfer stream is closed");
        }
        if (failed) {
            throw new TransferProtocolException(FAILED, "Transfer stream has failed");
        }
    }

    boolean beginClose() {
        if (closed) {
            return false;
        }
        closed = true;
        releaseState();
        return true;
    }

    boolean failed() {
        return failed;
    }

    void fail() {
        failed = true;
        releaseState();
    }

    boolean ended() {
        return state == State.ENDED;
    }

    void accept(TransferEvent event) {
        requireOpen();
        switch (event) {
            case StartTransfer start -> {
                requireState(State.BEFORE_TRANSFER, event);
                declaredModels = validateModels(start);
                state = State.BETWEEN_BASKETS;
            }
            case StartBasket start -> {
                requireState(State.BETWEEN_BASKETS, event);
                var next = validateBasket(start);
                requireNewId(basketIds, start.id().value(), "basket");
                basket = next;
                state = State.IN_BASKET;
            }
            case ObjectEvent occurrence -> {
                requireState(State.IN_BASKET, event);
                validateObject(occurrence.object());
            }
            case EndBasket end -> {
                requireState(State.IN_BASKET, event);
                requireBasketId(end.id());
                basket = null;
                state = State.BETWEEN_BASKETS;
            }
            case EndTransfer ignored -> {
                requireState(State.BETWEEN_BASKETS, event);
                state = State.ENDED;
                releaseState();
            }
        }
    }

    private Set<SymbolRef> validateModels(StartTransfer start) {
        var declared = new HashSet<SymbolRef>();
        for (var declaration : start.models()) {
            SymbolRef ref = declaration.model();
            requireSnapshot(ref);
            var model = plan.model(ref).orElseThrow(() -> unknown("model", ref));
            if (model.languageVersion() != declaration.languageVersion()
                    || declaration.modelVersion().filter(version -> !version.equals(model.modelVersion())).isPresent()) {
                throw new TransferProtocolException(CONTEXT_MISMATCH,
                        "Declared version differs from the transfer model " + model.name().declared());
            }
            declared.add(ref);
        }
        return includeModelImports ? plan.modelImportClosure(declared) : declared;
    }

    private ActiveBasket validateBasket(StartBasket start) {
        TopicDescriptor primary = concreteTopic(start.topic());
        requireDeclaredModel(primary.model());
        Set<SymbolRef> types = new HashSet<>(primary.objectTypes());
        var topics = new ArrayList<TopicDescriptor>();
        topics.add(primary);
        for (SymbolRef ref : start.additionalTopics()) {
            TopicDescriptor extension = concreteTopic(ref);
            requireDeclaredModel(extension.model());
            if (!extendsTopic(extension, primary.symbol())) {
                throw new TransferProtocolException(CONTEXT_MISMATCH,
                        extension.name().declared() + " does not extend " + primary.name().declared());
            }
            types.addAll(extension.objectTypes());
            topics.add(extension);
        }
        validateDomains(start.domainBindings(), topics);
        return new ActiveBasket(start.id(), start.kind(), types);
    }

    private void validateObject(TransferObject object) {
        requireBasketId(object.identity().currentBasket());
        SymbolRef ref = object.type();
        requireSnapshot(ref);
        var type = plan.object(ref).orElseThrow(() -> unknown("object type", ref));
        if (!type.transferable() || !basket.objectTypes().contains(ref)) {
            throw new TransferProtocolException(CONTEXT_MISMATCH,
                    type.name().declared() + " cannot occur in basket " + basket.id().value());
        }
        requireDeclaredModel(type.model());
        if (basket.kind() == BasketKind.FULL) {
            object.identity().tid().ifPresent(tid -> requireNewId(fullObjectIds, tid.value(), "object"));
        }
    }

    private void requireBasketId(BasketId id) {
        if (!basket.id().equals(id)) {
            throw new TransferProtocolException(CONTEXT_MISMATCH, "Basket ID differs from the active basket");
        }
    }

    private TopicDescriptor topic(SymbolRef ref) {
        requireSnapshot(ref);
        return plan.topic(ref).orElseThrow(() -> unknown("topic", ref));
    }

    private TopicDescriptor concreteTopic(SymbolRef ref) {
        TopicDescriptor topic = topic(ref);
        if (topic.abstractTopic()) {
            throw new TransferProtocolException(CONTEXT_MISMATCH,
                    "Basket topic " + topic.name().declared() + " is abstract");
        }
        return topic;
    }

    private boolean extendsTopic(TopicDescriptor extension, SymbolRef ancestor) {
        while (extension.baseTopic().isPresent()) {
            SymbolRef base = extension.baseTopic().orElseThrow();
            if (base.equals(ancestor)) {
                return true;
            }
            extension = topic(base);
        }
        return false;
    }

    private void validateDomains(List<TransferEvent.DomainBinding> bindings, List<TopicDescriptor> topics) {
        var selected = new HashMap<SymbolId, SymbolId>();
        for (var binding : bindings) {
            var generic = requireDomain(binding.generic());
            var concrete = requireDomain(binding.concrete());
            if (!generic.generic() || concrete.generic()
                    || selected.putIfAbsent(generic.id(), concrete.id()) != null) {
                throw new TransferProtocolException(CONTEXT_MISMATCH,
                        "Expected one generic-to-concrete assignment for " + generic.name().value());
            }
        }
        var effective = new HashMap<SymbolId, SymbolId>();
        for (var topic : topics) {
            for (var binding : topic.genericBindings()) {
                SymbolId genericId = binding.genericDomain();
                SymbolId supplied = selected.get(genericId);
                SymbolId concrete = switch (binding.state()) {
                    case CONCRETE -> supplied == null ? binding.alternatives().getFirst() : supplied;
                    case DEFERRED -> supplied;
                    case ABSTRACT -> null;
                };
                if (concrete == null || !binding.alternatives().contains(concrete)) {
                    var generic = requireDomain(new SymbolRef(plan.snapshotId(), genericId));
                    throw new TransferProtocolException(CONTEXT_MISMATCH,
                            "Basket for " + topic.name().declared() + " needs a permitted assignment for "
                                    + generic.name().value());
                }
                SymbolId previous = effective.putIfAbsent(genericId, concrete);
                if (previous != null && !previous.equals(concrete)) {
                    var generic = requireDomain(new SymbolRef(plan.snapshotId(), genericId));
                    throw new TransferProtocolException(CONTEXT_MISMATCH,
                            "Basket topics resolve " + generic.name().value() + " to different domains");
                }
            }
        }
        if (!effective.keySet().containsAll(selected.keySet())) {
            throw new TransferProtocolException(CONTEXT_MISMATCH, "Basket assigns an unused generic domain");
        }
    }

    private DomainSymbol requireDomain(SymbolRef ref) {
        requireSnapshot(ref);
        if (plan.symbol(ref).known().orElse(null) instanceof DomainSymbol domain) {
            return domain;
        }
        throw unknown("domain", ref);
    }

    private void requireDeclaredModel(SymbolRef model) {
        if (!declaredModels.contains(model)) {
            throw new TransferProtocolException(CONTEXT_MISMATCH,
                    "Transfer header must declare " + plan.model(model).orElseThrow().name().declared());
        }
    }

    private static void requireNewId(Set<String> ids, String id, String kind) {
        if (!ids.add(id)) {
            throw new TransferProtocolException(DUPLICATE_ID, "Duplicate " + kind + " ID " + id);
        }
    }

    private void releaseState() {
        basket = null;
        declaredModels = Set.of();
        basketIds = Set.of();
        fullObjectIds = Set.of();
    }

    private void requireSnapshot(SymbolRef ref) {
        if (!plan.snapshotId().equals(ref.snapshot())) {
            throw new TransferProtocolException(FOREIGN_SNAPSHOT, "Model reference belongs to another snapshot");
        }
    }

    private void requireState(State expected, TransferEvent event) {
        if (state != expected) {
            throw new TransferProtocolException(UNEXPECTED_EVENT,
                    event.getClass().getSimpleName() + " cannot occur in state " + state);
        }
    }

    private static TransferProtocolException unknown(String kind, SymbolRef ref) {
        return new TransferProtocolException(UNKNOWN_MODEL_ELEMENT, "Unknown " + kind + " " + ref.symbol().value());
    }

    private enum State {
        BEFORE_TRANSFER, BETWEEN_BASKETS, IN_BASKET, ENDED
    }

    private record ActiveBasket(BasketId id, BasketKind kind, Set<SymbolRef> objectTypes) { }
}
