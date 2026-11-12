package guru.interlis.validation.mvstore;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.compiler.SourceSet;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.validation.DatasetOptions;
import guru.interlis.validation.DatasetValidator;
import guru.interlis.validation.InMemoryValidationStorage;
import guru.interlis.validation.InputDescriptor;
import guru.interlis.validation.InputId;
import guru.interlis.validation.InputRole;
import guru.interlis.validation.StorageArea;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.ValidationPlan;
import guru.interlis.validation.ValidationPlanner;
import guru.interlis.validation.ValidationPlanningResult;
import guru.interlis.validation.ValidationProfile;
import guru.interlis.validation.ValidationReport;
import guru.interlis.validation.ValidationStorage;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StorageParityTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
              TOPIC Data =
                CLASS Owner = END Owner;
                CLASS Item =
                  code: TEXT*10;
                  UNIQUE code;
                  UNIQUE (BASKET) code;
                  SET CONSTRAINT (BASKET) INTERLIS.objectCount(ALL) >= 0;
                END Item;
                ASSOCIATION Link =
                  owner -- {1} Owner;
                  items -- {0..*} Item;
                END Link;
              END Data;
            END Checks.
            """;
    private static final String DATA = """
            <Data i:bid="b1">
              <Owner i:tid="o1"/>
              <Item i:tid="i1"><code>same</code><owner i:ref="o1"/></Item>
            </Data>
            <Data i:bid="b2">
              <Item i:tid="i2"><code>same</code><owner i:ref="absent"/></Item>
            </Data>
            """;

    @TempDir Path directory;

    @Test
    void producesTheSameReportForValidAndInvalidTransfers() {
        var fixture = fixture();
        var valid = """
                <Data i:bid="b1">
                  <Owner i:tid="o1"/>
                  <Item i:tid="i1"><code>first</code><owner i:ref="o1"/></Item>
                </Data>
                """;
        for (var content : List.of(valid, DATA)) {
            try (var memory = new InMemoryValidationStorage(10_000, 1_000_000);
                 var disk = MvStoreValidationStorage.open(directory, new MvStoreOptions(10_000, 1_000_000,
                         4_000_000, 1, 1))) {
                var memoryReport = validate(fixture, memory, content);
                var diskReport = validate(fixture, disk, content);
                assertEquals(memoryReport, diskReport);
                assertEquals(memory.usage(), disk.usage());
                assertTrue(disk.diskBytes() > 0);
                if (content.equals(DATA)) {
                    assertEquals(List.of(ValidationCode.REFERENCE_TARGET_MISSING, ValidationCode.DATASET_UNIQUE_DUPLICATE),
                            diskReport.examples().stream().map(example -> example.code()).toList());
                } else {
                    assertEquals(0, diskReport.detectedViolations());
                }
            }
        }
    }

    @Test
    void preservesUnsignedOrderingAndOwnsArraysAcrossACheckpointWithACursor() {
        try (var disk = MvStoreValidationStorage.open(directory, new MvStoreOptions(50, 100_000, 1_000_000, 1, 1))) {
            var table = disk.table(StorageArea.OBJECTS);
            byte[] key = {(byte) 0xff};
            byte[] value = {10};
            assertTrue(table.putIfAbsent(key, value).isEmpty());
            key[0] = 1;
            value[0] = 0;
            table.put(new byte[]{0}, new byte[]{1});
            table.put(new byte[]{(byte) 0x80}, new byte[]{2});
            disk.checkpoint();
            try (var cursor = table.scan(new byte[]{0}, Optional.of(new byte[]{(byte) 0xff}))) {
                assertArrayEquals(new byte[]{0}, cursor.next().orElseThrow().key());
                assertThrows(IllegalStateException.class, () -> table.put(new byte[]{1}, new byte[]{5}));
                disk.table(StorageArea.TEMPORARY).put(new byte[]{1}, new byte[20_000]);
                disk.checkpoint();
                assertArrayEquals(new byte[]{(byte) 0x80}, cursor.next().orElseThrow().key());
                assertTrue(cursor.next().isEmpty());
            }
            var prior = table.putIfAbsent(new byte[]{(byte) 0xff}, new byte[]{99}).orElseThrow();
            assertArrayEquals(new byte[]{10}, prior);
            prior[0] = 0;
            assertArrayEquals(new byte[]{10}, table.get(new byte[]{(byte) 0xff}).orElseThrow());
        }
    }

    static Fixture fixture() {
        var id = new SourceId("memory:/Checks.ili");
        var source = new SourceText(id, new SourceRevision(1), MODEL);
        var request = new CompilationRequest(List.of(new ModelSelector.ByName("Checks", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(id, source)), CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, TOKEN);
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, outcome.toString()).schema();
        var transfer = assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema)).plan();
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(schema, transfer, ValidationProfile.DATASET)).plan();
        return new Fixture(plan, transfer);
    }

    static Xtf24Reader reader(Fixture fixture, String data) {
        var xtf = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                            xmlns="http://www.interlis.ch/xtf/2.4/Checks">
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection>%s</i:datasection>
                </i:transfer>
                """.formatted(data);
        return new Xtf24Reader(fixture.transfer(), new ByteArrayInputStream(xtf.getBytes(StandardCharsets.UTF_8)));
    }

    static ValidationReport validate(Fixture fixture, ValidationStorage storage, String data) {
        var plan = fixture.plan();
        try (var run = DatasetValidator.session(plan, DatasetOptions.defaults(plan), storage);
             var reader = reader(fixture, data)) {
            run.readInput(new InputDescriptor(new InputId("primary"), InputRole.PRIMARY), reader, TOKEN);
            return run.finish(TOKEN);
        }
    }

    record Fixture(ValidationPlan plan, TransferPlan transfer) { }
}
