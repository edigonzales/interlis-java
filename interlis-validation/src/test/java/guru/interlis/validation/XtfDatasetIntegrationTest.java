package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferReader;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static guru.interlis.validation.ValidationCode.DATASET_UNIQUE_DUPLICATE;
import static guru.interlis.validation.ValidationCode.REFERENCE_TARGET_MISSING;
import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class XtfDatasetIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"23", "24"})
    void distinguishesForwardReferenceDuplicateAndMissingTarget(String version) throws IOException {
        var fixture = fixture(version);
        var valid = fixture.validate("valid");
        assertEquals(0, valid.detectedViolations(), valid.examples().toString());
        assertEquals(2, valid.inspectedObjects());
        assertTrue(valid.coverage().checked().stream()
                .anyMatch(rule -> rule.family() == ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY));

        var duplicate = fixture.validate("duplicate");
        assertEquals(1, duplicate.detectedViolations());
        var group = duplicate.examples().getFirst();
        assertEquals(DATASET_UNIQUE_DUPLICATE, group.code());
        assertEquals(List.of("duplicate", "second-basket", "second", "2"), group.arguments());
        assertEquals("first-basket", group.data().basket().orElseThrow().value());
        assertTrue(group.rule().range().isPresent());

        var missing = fixture.validate("missing-target");
        assertEquals(1, missing.detectedViolations());
        var target = missing.examples().getFirst();
        assertEquals(REFERENCE_TARGET_MISSING, target.code());
        assertEquals("dangling", target.data().tid().orElseThrow().value());
        assertInstanceOf(DataLocation.Slot.class, target.data().path().getFirst());
        assertTrue(target.rule().range().isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"23", "24"})
    void varyingInputOrderPreservesRuleCountsAndSemantics(String version) throws IOException {
        var fixture = fixture(version);
        var first = fixture.validate("duplicate", "missing-target");
        var second = fixture.validate("missing-target", "duplicate");
        assertEquals(first.detectedViolations(), second.detectedViolations());
        assertEquals(first.examples(), second.examples());
        assertEquals(first.coverage().datasetRules(), second.coverage().datasetRules());
        assertEquals(2, first.detectedViolations());
    }

    private static Fixture fixture(String version) throws IOException {
        var schema = compile(new String(bytes(version, "Checks.ili"), StandardCharsets.UTF_8));
        var transfer = transfer(schema);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(schema, transfer, ValidationProfile.DATASET)).plan();
        return new Fixture(version, transfer, plan);
    }

    private static byte[] bytes(String version, String name) throws IOException {
        try (var input = XtfDatasetIntegrationTest.class.getResourceAsStream("/dataset/" + version + "/" + name)) {
            assertNotNull(input, name);
            return input.readAllBytes();
        }
    }

    private record Fixture(String version, TransferPlan transfer, ValidationPlan plan) {
        ValidationReport validate(String... samples) throws IOException {
            var token = CancellationToken.none();
            try (var storage = new InMemoryValidationStorage(10_000, 2_000_000);
                 var session = DatasetValidator.session(plan, DatasetOptions.defaults(plan), storage)) {
                for (var sample : samples) {
                    var source = new ByteArrayInputStream(bytes(version, sample + ".xtf"));
                    try (TransferReader reader = version.equals("23")
                            ? new Xtf23Reader(transfer, source) : new Xtf24Reader(transfer, source)) {
                        session.readInput(new InputDescriptor(new InputId(sample), InputRole.PRIMARY), reader, token);
                    }
                }
                return session.finish(token);
            }
        }
    }
}
