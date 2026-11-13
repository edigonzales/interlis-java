package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.model.ViewSymbol;
import guru.interlis.transfer.TransferEvent;

import java.util.Set;
import java.util.stream.Collectors;

final class SubjectSelection {
    private final ValidationPlan plan;
    private final Set<SymbolRef> topics;

    SubjectSelection(ValidationPlan plan) {
        this.plan = plan;
        this.topics = plan.rootModels().stream()
                .flatMap(model -> plan.transferPlan().model(model).orElseThrow().topics().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean selects(InputRole role, TransferEvent.StartBasket basket) {
        return role == InputRole.PRIMARY && (selectsTopic(basket.topic())
                || basket.additionalTopics().stream().anyMatch(this::selectsTopic));
    }

    boolean selectsView(ViewSymbol view) {
        return view.owner().map(plan::ref).filter(this::selectsTopic).isPresent();
    }

    boolean selectsTopic(SymbolRef topic) {
        var current = plan.transferPlan().topic(topic);
        while (current.isPresent()) {
            var descriptor = current.orElseThrow();
            if (topics.contains(descriptor.symbol())) {
                return true;
            }
            current = descriptor.baseTopic().flatMap(plan.transferPlan()::topic);
        }
        return false;
    }
}
