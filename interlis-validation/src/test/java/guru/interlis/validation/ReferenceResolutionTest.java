package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceResolutionTest {
    private static final String SOURCE =
            "<Item i:tid=\"item\"><pointer><Pointer><link i:ref=\"target\"/></Pointer></pointer></Item>";
    private static final String TARGET = "<Target i:tid=\"target\"/>";

    @Test
    void resolvesLaterTargetsAndSeparatesTypeAndBasketFailures() {
        var forward = validate(false, basket("one", SOURCE + TARGET));
        var backward = validate(false, basket("one", TARGET + SOURCE));
        assertEquals(forward, backward);
        assertTrue(forward.examples().isEmpty());
        assertEquals(1, forward.coverage().checked().stream()
                .filter(c -> c.family() == ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY)
                .mapToLong(ValidationCoverage.RuleCount::occurrences).sum());

        assertEquals(List.of(REFERENCE_TARGET_TYPE), codes(validate(false,
                basket("one", SOURCE + "<Other i:tid=\"target\"/>"))));
        assertEquals(List.of(REFERENCE_TARGET_BASKET), codes(validate(false,
                basket("one", SOURCE) + basket("two", TARGET))));
        assertEquals(List.of(REFERENCE_TARGET_MISSING), codes(validate(false, basket("one", SOURCE))));
    }

    @Test
    void keepsAnExternalMissingTargetUndetermined() {
        var report = validate(true, basket("one", SOURCE));
        assertTrue(report.examples().isEmpty());
        assertEquals(1, report.coverage().missingRuntimeContext().stream()
                .filter(c -> c.family() == ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY)
                .mapToLong(ValidationCoverage.RuleCount::occurrences).sum());
        assertTrue(validate(true, basket("one", SOURCE) + basket("two", TARGET)).examples().isEmpty());
        assertEquals(List.of(REFERENCE_TARGET_MISSING), codes(validate(true, basket("one", SOURCE), true)));
    }

    @Test
    void resolvesStableIdentitiesAcrossInputsAndReportsAmbiguity() {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    CLASS Target = OID AS INTERLIS.UUIDOID; END Target;
                    STRUCTURE Pointer = link : REFERENCE TO (EXTERNAL) Target; END Pointer;
                    CLASS Item = pointer : Pointer; END Item;
                  END Data;
                END Checks.
                """);
        var transfer = transfer(schema);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET)).plan();
        var oid = "71c7dbf0-3dbc-4b5c-aa11-888cbfb13676";
        var source = basket("subject", SOURCE.replace("target", oid));
        var target = basket("reference", TARGET.replace("target", oid));
        for (boolean collision : List.of(false, true)) {
            try (var store = new InMemoryValidationStorage(100, 100_000);
                 var session = DatasetValidator.session(plan, DatasetOptions.defaults(), store);
                 var primary = reader(transfer, source);
                 var reference = reader(transfer, target);
                 var duplicate = reader(transfer, target)) {
                var token = CancellationToken.none();
                session.readInput(new InputDescriptor(new InputId("primary"), InputRole.PRIMARY), primary, token);
                session.readInput(new InputDescriptor(new InputId("reference"), InputRole.REFERENCE), reference, token);
                if (collision) {
                    session.readInput(new InputDescriptor(new InputId("duplicate"), InputRole.REFERENCE), duplicate, token);
                }
                var report = session.finish(token);
                if (collision) {
                    assertEquals(List.of(AMBIGUOUS_OBJECT_IDENTITY, AMBIGUOUS_OBJECT_IDENTITY), codes(report));
                    assertEquals(List.of("reference", "reference", "duplicate", "reference"),
                            report.examples().getFirst().arguments());
                } else {
                    assertTrue(report.examples().isEmpty());
                    assertTrue(report.coverage().missingRuntimeContext().isEmpty());
                }
            }
        }
    }

    private static ValidationReport validate(boolean external, String data) {
        return validate(external, data, false);
    }

    private static ValidationReport validate(boolean external, String data, boolean closed) {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    CLASS Target = END Target;
                    CLASS Other = END Other;
                    STRUCTURE Pointer = link : REFERENCE TO %sTarget; END Pointer;
                    CLASS Item = pointer : Pointer; END Item;
                  END Data;
                END Checks.
                """.formatted(external ? "(EXTERNAL) " : ""));
        var transfer = transfer(schema);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET)).plan();
        var input = new InputId("primary");
        var options = new DatasetOptions(ValidationOptions.defaults(), DatasetLimits.defaults(),
                closed ? DatasetScope.SUPPLIED_DATASET : DatasetScope.PARTIAL_DATASET);
        try (var store = new InMemoryValidationStorage(100, 100_000);
             var session = DatasetValidator.session(plan, options, store);
             var reader = reader(transfer, data)) {
            session.readInput(new InputDescriptor(new InputId("primary"), InputRole.PRIMARY), reader, CancellationToken.none());
            return session.finish(CancellationToken.none());
        }
    }

    private static Xtf24Reader reader(guru.interlis.transfer.TransferPlan transfer, String data) {
        var xml = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                            xmlns="http://www.interlis.ch/xtf/2.4/Checks">
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection>%s</i:datasection>
                </i:transfer>
                """.formatted(data);
        return new Xtf24Reader(transfer, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String basket(String bid, String contents) {
        return "<Data i:bid=\"" + bid + "\">" + contents + "</Data>";
    }

    private static List<ValidationCode> codes(ValidationReport report) {
        return report.examples().stream().map(ValidationDiagnostic::code).toList();
    }
}
