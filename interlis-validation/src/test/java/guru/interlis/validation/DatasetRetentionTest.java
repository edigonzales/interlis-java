package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetRetentionTest {
    private static final CancellationToken TOKEN = CancellationToken.none();

    @Test
    void projectsOnlyTheNumericKeyAndKeepsItsCrossInputWitness() {
        var plan = plan("""
                CLASS Item = precision: 0.0..20.0; label: MANDATORY TEXT*50; UNIQUE precision; END Item;
                CLASS Plain = label: MANDATORY TEXT*50; END Plain;
                CLASS Other = label: MANDATORY TEXT*50; UNIQUE label; END Other;
                """);
        var reference = reference("reference", basket("refBasket",
                "<Item i:tid='ref'><precision>01.00</precision><label>reference payload</label></Item>"));
        var primary = primary("primary", basket("dataBasket", """
                <Item i:tid='item'><precision>1.04</precision><label>primary payload</label></Item>
                <Plain i:tid='plain'><label>head only</label></Plain>
                <Other i:tid='other'><label>full</label></Other>
                """));
        var key = plan.object(type(plan, "Item")).fields().getFirst().slot().symbol();

        try (var storage = new InMemoryValidationStorage(10_000, 10_000_000);
             var run = DatasetValidator.session(plan, DatasetOptions.defaults(), storage)) {
            ingest(plan, run, reference, primary);
            var codec = new StoredValueCodec(plan.snapshotId(), DatasetLimits.defaults().maxRecordBytes());
            try (var cursor = storage.table(StorageArea.OBJECTS).scan(new byte[0], Optional.empty())) {
                for (var tid : List.of("ref", "item")) {
                    var bytes = cursor.next().orElseThrow().value();
                    var header = codec.decodeObjectHeader(bytes);
                    assertTrue(header.projected());
                    assertEquals(tid, header.identity().tid().orElseThrow().value());
                    assertThrows(ValidationStreamException.class, () -> codec.decodeObject(bytes));
                    var values = codec.decodeRetainedObject(bytes).event().object().data().values();
                    assertEquals(Set.of(key), values.keySet());
                    var number = assertInstanceOf(TransferValue.NumberValue.class, values.get(key));
                    assertEquals(0, new BigDecimal("1").compareTo(number.exactValue()));
                }
                var plain = cursor.next().orElseThrow().value();
                assertTrue(codec.decodeObjectHeader(plain).projected());
                assertTrue(codec.decodeRetainedObject(plain).event().object().data().values().isEmpty());
                var other = cursor.next().orElseThrow().value();
                assertFalse(codec.decodeObjectHeader(other).projected());
                assertFalse(codec.decodeObject(other).event().object().data().values().isEmpty());
                assertTrue(cursor.next().isEmpty());
            }
            var report = run.finish(TOKEN);
            assertEquals(3, report.inspectedObjects());
            assertEquals(1, report.detectedViolations());
            var duplicate = report.examples().getFirst();
            assertEquals(ValidationCode.DATASET_UNIQUE_DUPLICATE, duplicate.code());
            assertEquals("item", duplicate.data().tid().orElseThrow().value());
            assertEquals(List.of("reference", "refBasket", "ref", "2"), duplicate.arguments());
            var unique = report.coverage().datasetRules().getFirst();
            assertEquals(DatasetRuleCoverage.Outcome.VIOLATED, unique.outcome());
            assertEquals(2, unique.evaluations());
        }
    }

    @Test
    void keepsNestedReferencesAndAreaPayloadsForDatasetChecks() {
        var plan = plan("""
                DOMAIN Point = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                CLASS Peer = END Peer;
                STRUCTURE Link = peer: REFERENCE TO Peer; END Link;
                CLASS Container = link: Link; END Container;
                CLASS Parcel = shape: AREA WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.002; END Parcel;
                """);
        assertNull(plan.retention().slots(type(plan, "Parcel")));
        var primary = primary("primary", basket("dataBasket", """
                <Container i:tid='source'><link><Link><peer i:ref='missing'/></Link></link></Container>
                """));
        try (var storage = new InMemoryValidationStorage(10_000, 10_000_000);
             var run = DatasetValidator.session(plan, DatasetOptions.defaults(), storage)) {
            ingest(plan, run, primary);
            var codec = new StoredValueCodec(plan.snapshotId(), DatasetLimits.defaults().maxRecordBytes());
            try (var cursor = storage.table(StorageArea.OBJECTS).scan(new byte[0], Optional.empty())) {
                assertFalse(codec.decodeObjectHeader(cursor.next().orElseThrow().value()).projected());
            }
            var report = run.finish(TOKEN);
            assertTrue(report.examples().stream().anyMatch(diagnostic ->
                    diagnostic.code() == ValidationCode.REFERENCE_TARGET_MISSING
                    && diagnostic.data().tid().orElseThrow().value().equals("source")));
        }
    }

    @Test
    void groupsInheritedNumericKeysWithMixedFullAndProjectedPayloads() {
        var plan = plan("""
                CLASS Base (ABSTRACT) = number: 0..100; UNIQUE number; END Base;
                CLASS Small EXTENDS Base = END Small;
                CLASS Full EXTENDS Base = label: TEXT*10; UNIQUE label; END Full;
                """);
        assertNotNull(plan.retention().slots(type(plan, "Small")));
        assertNull(plan.retention().slots(type(plan, "Full")));
        for (boolean fullFirst : List.of(false, true)) {
            String small = "<Small i:tid='small'><number>8</number></Small>";
            String full = "<Full i:tid='full'><number>8</number><label>full</label></Full>";
            var report = validate(plan, DatasetScope.SUPPLIED_DATASET,
                    primary("p", basket("data", fullFirst ? full + small : small + full)));
            assertEquals(1, report.detectedViolations());
            var duplicate = report.examples().getFirst();
            assertEquals(ValidationCode.DATASET_UNIQUE_DUPLICATE, duplicate.code());
            assertEquals(fullFirst ? "full" : "small", duplicate.data().tid().orElseThrow().value());
            assertEquals(List.of("p", "data", fullFirst ? "small" : "full", "2"), duplicate.arguments());
        }
    }

    @Test
    void retainsExistenceSourcesAndTargetsAlongsideUnrelatedHeaders() {
        var plan = plan("""
                CLASS Target = code: TEXT*10; END Target;
                CLASS Source = code: TEXT*10; EXISTENCE CONSTRAINT code REQUIRED IN Target:code; END Source;
                CLASS Unrelated = payload: TEXT*10; END Unrelated;
                """);
        assertNull(plan.retention().slots(type(plan, "Source")));
        assertNull(plan.retention().slots(type(plan, "Target")));
        assertEquals(Set.of(), plan.retention().slots(type(plan, "Unrelated")));
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("data", """
                <Source i:tid='source'><code>match</code></Source>
                <Unrelated i:tid='unrelated'><payload>unused</payload></Unrelated>
                <Target i:tid='target'><code>match</code></Target>
                """)));
        assertEquals(3, report.inspectedObjects());
        assertEquals(0, report.detectedViolations());
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, report.coverage().datasetRules().getFirst().outcome());
    }

    private static void ingest(ValidationPlan plan, DatasetValidationSession run, Input... inputs) {
        for (var input : inputs) {
            try (var reader = reader(plan, input.baskets())) {
                run.readInput(input.descriptor(), reader, TOKEN);
            }
        }
    }
}
