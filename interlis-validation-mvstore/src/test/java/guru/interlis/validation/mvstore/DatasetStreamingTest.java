package guru.interlis.validation.mvstore;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;
import guru.interlis.validation.DatasetLimits;
import guru.interlis.validation.DatasetOptions;
import guru.interlis.validation.DatasetRuleCoverage;
import guru.interlis.validation.DatasetValidator;
import guru.interlis.validation.InputDescriptor;
import guru.interlis.validation.InputId;
import guru.interlis.validation.InputRole;
import guru.interlis.validation.StorageArea;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.ValidationLimitException;
import guru.interlis.validation.ValidationStreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.mvstore.StorageParityTest.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetStreamingTest {
    private static final int FAN_OUT_LIMIT = 100;
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final InputDescriptor PRIMARY = new InputDescriptor(new InputId("generated"), InputRole.PRIMARY);
    @TempDir Path root;

    @Test
    void countsAHighFanOutAndNavigatesItsSingleReverseLink() throws IOException {
        int rows = Integer.getInteger("interlis.validation.streaming.objects", 256);
        assertTrue(rows > 0 && rows <= 250_000);
        var fixture = fixture(MODEL.replace("    END Link;", """
                    END Link;
                CONSTRAINTS OF Owner =
                  MANDATORY CONSTRAINT INTERLIS.objectCount(items) == %d;
                END;
                CONSTRAINTS OF Item =
                  MANDATORY CONSTRAINT DEFINED(owner);
                END;
                """.formatted(rows)));
        var data = new StringBuilder("<Data i:bid='fanout'><Owner i:tid='owner'/>");
        for (int i = 0; i < rows; i++) {
            data.append("<Item i:tid='item").append(i).append("'><code>k").append(i)
                    .append("</code><owner i:ref='owner'/></Item>");
        }
        data.append("</Data>");
        long start = System.nanoTime();
        try (var storage = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(512L * 1024 * 1024))) {
            var report = validate(fixture, storage, data.toString());
            assertEquals(rows + 1L, report.inspectedObjects());
            assertTrue(report.examples().isEmpty(), report.examples().toString());
            var checks = report.coverage().constraints();
            assertTrue(checks.stream().allMatch(check ->
                    check.status() == guru.interlis.validation.ValidationCoverage.ConstraintStatus.CHECKED));
            assertEquals(rows + 1L, checks.stream().mapToLong(
                    guru.interlis.validation.ValidationCoverage.ConstraintCount::occurrences).sum());
            long heap = Runtime.getRuntime().maxMemory();
            if (Boolean.getBoolean("interlis.validation.streaming.require2g")) {
                assertEquals(2L << 30, heap);
            }
            System.out.printf("objects=%d links=%d heapBytes=%d elapsedMillis=%d peakTempBytes=%d%n",
                    rows + 1L, rows, heap, (System.nanoTime() - start) / 1_000_000, storage.peakDiskBytes());
        }
        assertClean();
    }

    @Test
    void streamsLateTargetsAndHighFanOutWithBothDuplicateAndDistinctKeys() throws IOException {
        int rows = Integer.getInteger("interlis.validation.streaming.objects", 256);
        assertTrue(rows > FAN_OUT_LIMIT + 1 && rows <= 250_000);
        var options = new MvStoreOptions(4_000_000, 2L << 30, 2L << 30, 64, 1_024);
        var fixture = fixture(MODEL.replace("{0..*} Item", "{0.." + FAN_OUT_LIMIT + "} Item")
                .replace("  END Data;", """
                          VIEW Selected PROJECTION OF item~Item;
                            WHERE item->code == "duplicate";
                            =
                              MANDATORY CONSTRAINT INTERLIS.len(item->code) == 9;
                          END Selected;
                        END Data;
                        """));
        try (var storage = MvStoreValidationStorage.open(root, options);
             var run = DatasetValidator.session(fixture.plan(), DatasetOptions.defaults(), storage);
             var input = generated(fixture, rows)) {
            run.readInput(PRIMARY, input, TOKEN);
            var report = run.finish(TOKEN);
            assertEquals(rows + 1L, report.inspectedObjects());
            assertEquals(List.of(ValidationCode.REFERENCE_TARGET_MISSING, ValidationCode.CARDINALITY_VIOLATION,
                            ValidationCode.DATASET_UNIQUE_DUPLICATE, ValidationCode.DATASET_UNIQUE_DUPLICATE),
                    report.examples().stream().map(d -> d.code()).toList(), report.examples().toString());
            var overflow = report.examples().get(1);
            assertEquals(List.of(Integer.toString(rows - 1), "0", Integer.toString(FAN_OUT_LIMIT)),
                    overflow.arguments());
            assertEquals("late-owner", overflow.data().tid().orElseThrow().value());
            assertEquals("item-" + (rows - 1), report.examples().getFirst().data().tid().orElseThrow().value());
            assertTrue(report.examples().subList(2, 4).stream().allMatch(
                    finding -> finding.arguments().getLast().equals("2")));
            assertEquals(4, report.detectedViolations());
            var viewRows = report.coverage().constraints().stream()
                    .mapToLong(guru.interlis.validation.ValidationCoverage.ConstraintCount::occurrences).sum();
            assertEquals(2, viewRows);
            long logicalBytes = 0;
            for (var area : StorageArea.values()) {
                long bytes = 0;
                long entries = 0;
                try (var cursor = storage.table(area).scan(new byte[0], Optional.empty())) {
                    for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                        var entry = next.orElseThrow();
                        bytes += (long) entry.key().length + entry.value().length;
                        entries++;
                    }
                }
                logicalBytes += bytes;
                System.out.printf("area=%s entries=%d bytes=%d%n", area, entries, bytes);
            }
            assertEquals(storage.usage().bytes(), logicalBytes);
            long peak = storage.peakDiskBytes();
            assertTrue(storage.diskBytes() > 0 && peak >= storage.diskBytes() && peak <= options.maxDiskBytes());
            long heap = Runtime.getRuntime().maxMemory();
            if (Boolean.getBoolean("interlis.validation.streaming.require2g")) {
                assertEquals(2L << 30, heap, "Effective test JVM maximum heap");
            }
            System.out.printf("rows=%d heapBytes=%d logicalBytes=%d fileBytes=%d peakTempBytes=%d cacheMiB=%d bufferKiB=%d%n",
                    rows, heap, logicalBytes, storage.diskBytes(), peak, options.cacheMiB(), options.writeBufferKiB());
        }
        assertClean();
    }

    @Test
    void aggregatesManyDeliveredBasketScopesIntoABoundedReport() throws IOException {
        var fixture = fixture();
        int baskets = 400;
        var data = new StringBuilder();
        for (int i = 0; i < baskets; i++) {
            data.append("<Data i:bid='b").append(i).append("'/>");
        }
        try (var storage = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(64 * 1024 * 1024))) {
            var report = validate(fixture, storage, data.toString());
            assertEquals(baskets, report.inspectedBaskets());
            assertEquals(0, report.inspectedObjects());
            assertEquals(0, report.detectedViolations());
            var basketCoverage = report.coverage().datasetRules().stream()
                    .filter(scope -> scope.scopeKind() == DatasetRuleCoverage.ScopeKind.BASKET).toList();
            assertEquals(2, basketCoverage.size(), basketCoverage.toString());
            assertTrue(basketCoverage.stream().allMatch(scope -> scope.scopes() == baskets
                    && scope.candidates() == 0 && scope.outcome() == DatasetRuleCoverage.Outcome.SATISFIED));
            assertEquals(baskets, basketCoverage.stream().mapToLong(DatasetRuleCoverage::evaluations).sum());
        }
        assertClean();
    }

    @Test
    void stopsTheGeneratedRelationRunAtItsWorkBudgetAndCleansUp() throws IOException {
        var fixture = fixture();
        var defaults = DatasetOptions.defaults();
        var limits = new DatasetLimits(1, 1, 1_000, 100_000, 10);
        var options = new DatasetOptions(defaults.localOptions(), limits, defaults.scope());
        try (var storage = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(64 * 1024 * 1024));
             var run = DatasetValidator.session(fixture.plan(), options, storage);
             var input = generated(fixture, 128)) {
            run.readInput(PRIMARY, input, TOKEN);
            var exhausted = assertThrows(ValidationLimitException.class, () -> run.finish(TOKEN));
            assertEquals("Dataset relationship work limit exceeded", exhausted.getMessage());
            assertThrows(ValidationStreamException.class, () -> run.finish(TOKEN));
        }
        assertClean();
    }

    private void assertClean() throws IOException {
        try (var children = Files.list(root)) {
            assertEquals(0, children.count());
        }
        System.out.println("runDirectoryRemaining=0");
    }

    private static TransferReader generated(Fixture fixture, int rows) {
        var plan = fixture.transfer();
        var model = plan.models().getFirst();
        var topic = model.topics().getFirst();
        var item = plan.objects().stream().filter(t -> t.name().declared().last().equals("Item"))
                .findFirst().orElseThrow();
        var owner = plan.objects().stream().filter(t -> t.name().declared().last().equals("Owner"))
                .findFirst().orElseThrow();
        var link = plan.objects().stream().filter(t -> t.name().declared().last().equals("Link"))
                .findFirst().orElseThrow();
        var code = item.slots().getFirst().symbol();
        var role = link.slots().getFirst().symbol();
        var bid = new TransferIdentity.BasketId("generated-basket", TransferIdentity.IdOrigin.SOURCE);
        return new TransferReader(plan) {
            private int position;

            @Override
            protected Optional<TransferEvent> readEvent(CancellationToken cancellation) {
                cancellation.throwIfCancellationRequested();
                int step = position++;
                if (step == 0) {
                    return Optional.of(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(),
                            List.of(new TransferEvent.ModelDeclaration(model.symbol(), model.languageVersion(),
                                    Optional.empty(), Optional.empty()))));
                }
                if (step == 1) {
                    return Optional.of(new TransferEvent.StartBasket(bid, topic, List.of(), TransferEvent.BasketKind.FULL,
                            TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of()));
                }
                int row = step - 2;
                if (row < rows) {
                    var identity = identity("item-" + row);
                    var target = new TransferIdentity.TransferId(row == rows - 1 ? "missing-owner" : "late-owner",
                            TransferIdentity.IdOrigin.SOURCE);
                    var ref = new TransferValue.Reference(target, Optional.empty(), false, Optional.empty());
                    var value = new TransferValue.Structured(item.symbol(), Map.of(
                            code, new TransferValue.Text(row < 2 ? "duplicate" : "k" + row), role, ref));
                    return Optional.of(object(identity, value));
                }
                if (row == rows) {
                    return Optional.of(object(identity("late-owner"), new TransferValue.Structured(owner.symbol(), Map.of())));
                }
                if (row == rows + 1) {
                    return Optional.of(new TransferEvent.EndBasket(bid));
                }
                return row == rows + 2 ? Optional.of(new TransferEvent.EndTransfer()) : Optional.empty();
            }

            private TransferIdentity identity(String id) {
                return new TransferIdentity(bid, Optional.empty(),
                        Optional.of(new TransferIdentity.TransferId(id, TransferIdentity.IdOrigin.SOURCE)),
                        Optional.empty(), Optional.empty());
            }

            private TransferEvent.ObjectEvent object(TransferIdentity id, TransferValue.Structured value) {
                return new TransferEvent.ObjectEvent(new TransferObject(id, value), Optional.empty(),
                        TransferEvent.Consistency.COMPLETE);
            }

            @Override
            protected void release() { }
        };
    }
}
