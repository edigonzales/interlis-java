package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
import guru.interlis.xtf24.Xtf24Reader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class DatasetRuleTestSupport {
    private DatasetRuleTestSupport() { }

    static ValidationPlan plan(String declarations) {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    %s
                  END Data;
                END Checks.
                """.formatted(declarations));
        return assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(schema, transfer(schema), ValidationProfile.DATASET)).plan();
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

    static ValidationReport validate(ValidationPlan plan, List<String> completeTypes, Input... inputs) {
        var claims = new ArrayList<ExtentDeclaration>();
        var ids = List.of(inputs).stream().map(input -> input.descriptor().id()).collect(Collectors.toSet());
        for (var name : completeTypes) {
            var selector = new ExtentSelector(type(plan, name), ids, Optional.empty());
            claims.add(new ExtentDeclaration(selector, ExtentAspect.OBJECT_MEMBERSHIP, "test extent"));
            claims.add(new ExtentDeclaration(selector, ExtentAspect.ATTRIBUTE_VALUES, "test extent"));
        }
        var options = new DatasetOptions(ValidationOptions.defaults(), SubjectSelection.fromRoots(plan),
                new ExtentDeclarations(claims), DatasetLimits.defaults());
        var token = CancellationToken.none();
        try (var storage = new InMemoryValidationStorage(10_000, 10_000_000);
             var run = DatasetValidator.session(plan, options, storage)) {
            for (var input : inputs) {
                String xtf = """
                        <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                                    xmlns="http://www.interlis.ch/xtf/2.4/Checks"
                                    xmlns:g="http://www.interlis.ch/geometry/1.0">
                          <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                          <i:datasection>%s</i:datasection>
                        </i:transfer>
                        """.formatted(input.baskets());
                try (var reader = new Xtf24Reader(plan.transferPlan(),
                        new ByteArrayInputStream(xtf.getBytes(StandardCharsets.UTF_8)))) {
                    run.readInput(input.descriptor(), reader, token);
                }
            }
            return run.finish(token);
        }
    }

    record Input(InputDescriptor descriptor, String baskets) { }
}
