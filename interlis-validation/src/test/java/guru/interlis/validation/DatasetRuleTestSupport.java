package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf24.Xtf24Reader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class DatasetRuleTestSupport {
    private DatasetRuleTestSupport() { }

    static ValidationPlan plan(String declarations) {
        return plan("2.4", declarations);
    }

    static ValidationPlan plan(String version, String declarations) {
        var schema = compile("""
                INTERLIS %s;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    %s
                  END Data;
                END Checks.
                """.formatted(version, declarations));
        return assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer(schema), ValidationProfile.DATASET)).plan();
    }

    static SymbolRef type(ValidationPlan plan, String name) {
        return plan.transferPlan().objects().stream()
                .filter(object -> object.name().transfer().segments().getLast().equals(name))
                .findFirst().orElseThrow().symbol();
    }

    static String basket(String id, String data) {
        return "<Data i:bid=\"%s\">%s</Data>".formatted(id, data);
    }

    static Input primary(String id, String baskets) {
        return new Input(new InputDescriptor(new InputId(id), InputRole.PRIMARY), baskets);
    }

    static Input reference(String id, String baskets) {
        return new Input(new InputDescriptor(new InputId(id), InputRole.REFERENCE), baskets);
    }

    static ValidationReport validate(ValidationPlan plan, DatasetScope scope, Input... inputs) {
        var options = new DatasetOptions(ValidationOptions.defaults(), DatasetLimits.defaults(), scope);
        var token = CancellationToken.none();
        try (var storage = new InMemoryValidationStorage(10_000, 10_000_000);
             var run = DatasetValidator.session(plan, options, storage)) {
            for (var input : inputs) {
                try (var reader = reader(plan, input.baskets())) {
                    run.readInput(input.descriptor(), reader, token);
                }
            }
            return run.finish(token);
        }
    }

    static TransferReader reader(ValidationPlan plan, String baskets) {
        boolean ili23 = plan.transferPlan().model(plan.rootModels().getFirst()).orElseThrow().languageVersion()
                == LanguageVersion.ILI23;
        var xtf = (ili23 ? """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="Checks" VERSION="1" URI="https://example.org/checks"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION>%s</DATASECTION>
                </TRANSFER>
                """ : """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                            xmlns="http://www.interlis.ch/xtf/2.4/Checks"
                            xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection>%s</i:datasection>
                </i:transfer>
                """).formatted(baskets);
        var bytes = new ByteArrayInputStream(xtf.getBytes(StandardCharsets.UTF_8));
        return ili23 ? new Xtf23Reader(plan.transferPlan(), bytes) : new Xtf24Reader(plan.transferPlan(), bytes);
    }

    static TransferReader translatedReader(ValidationPlan plan, String modelName,
                                             String objectName, String id, String label) {
        var model = plan.transferPlan().models().stream().filter(m -> m.name().declared().last().equals(modelName))
                .findFirst().orElseThrow();
        var topic = model.topics().getFirst();
        var type = plan.transferPlan().objects().stream()
                .filter(o -> o.model().equals(model.symbol()) && o.name().declared().last().equals(objectName))
                .findFirst().orElseThrow();
        var field = type.slots().getFirst().symbol();
        var basket = new TransferIdentity.BasketId("basket",
                TransferIdentity.IdOrigin.SOURCE);
        var identity = new TransferIdentity(basket, Optional.empty(),
                Optional.of(new TransferIdentity.TransferId(id,
                        TransferIdentity.IdOrigin.SOURCE)), Optional.empty(),
                Optional.empty());
        var value = new TransferValue.Structured(type.symbol(),
                Map.of(field, new TransferValue.Enumeration(List.of(label))));
        var events = List.<TransferEvent>of(
                new TransferEvent.StartTransfer(Optional.empty(),
                        Optional.empty(), Optional.empty(), List.of(
                        new TransferEvent.ModelDeclaration(model.symbol(),
                                model.languageVersion(), Optional.empty(), Optional.empty()))),
                new TransferEvent.StartBasket(basket, topic, List.of(),
                        TransferEvent.BasketKind.FULL,
                        TransferEvent.Consistency.COMPLETE, Optional.empty(),
                        Optional.empty(), Optional.empty(), List.of()),
                new TransferEvent.ObjectEvent(new TransferObject(identity, value),
                        Optional.empty(), TransferEvent.Consistency.COMPLETE),
                new TransferEvent.EndBasket(basket),
                new TransferEvent.EndTransfer()).iterator();
        return new TransferReader(plan.transferPlan()) {
            @Override
            protected Optional<TransferEvent> readEvent(
                    CancellationToken cancellation) {
                return events.hasNext() ? Optional.of(events.next()) : Optional.empty();
            }

            @Override
            protected void release() { }
        };
    }


    record Input(InputDescriptor descriptor, String baskets) { }
}
