package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.OrderedRoleFixtures.assertEmbeddedOrderOwner;
import static guru.interlis.db.OrderedRoleFixtures.assertOrderColumn;
import static guru.interlis.db.OrderedRoleFixtures.assertOrderedTransfer;
import static guru.interlis.db.OrderedRoleFixtures.assertStoredOrderedPositions;
import static guru.interlis.db.OrderedRoleFixtures.copyOrderedFixture;
import static guru.interlis.db.TransferCatalogAssertions.assertAssociationCatalog;
import static guru.interlis.db.TransferCatalogAssertions.assertTypeKind;

final class CoreOrderedCases {
    private static final String STORED_MODELS = "%ILI_FROM_DB";
    private final Path work;

    CoreOrderedCases(Path work) {
        this.work = work;
    }

    void exchangeOrdered(ReferenceTool reference, TestDatabase referenceSchema, int version) throws Exception {
        String model = "OrderedInterop" + version;
        Path ili = copyOrderedFixture(work, model + ".ili");
        Path input = copyOrderedFixture(work, model + ".xtf");
        String fixture = Files.readString(input);
        Path nativeInput = Files.writeString(work.resolve("ordered-native.xtf"), fixture.replace("ordered-", "native-"));
        var nativeSchema = referenceSchema.peer("ordered_native");

        reference.run(referenceSchema, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                ili.toString());
        assertSchemaWithIdentityColumns(nativeSchema, model, ili);
        try (var original = referenceSchema.connect(); var own = nativeSchema.connect()) {
            DbSchemaCatalog.read(original, referenceSchema.postgis, referenceSchema.schema)
                    .assertSameAs(DbSchemaCatalog.read(own, nativeSchema.postgis, nativeSchema.schema));
        }
        for (var db : List.of(referenceSchema, nativeSchema)) {
            reference.run(db, STORED_MODELS, "--import", "--importTid", "--importBid", "--dataset", "ordered", input.toString());
            Path nativeOutput = work.resolve(db.schema + "-native-export.xtf");
            Path referenceOutput = work.resolve(db.schema + "-reference-export.xtf");
            try (var sql = db.connect()) {
                assertOrderColumn(db, sql, "membership", "amember");
                assertOrderColumn(db, sql, "membership", "team");
                assertOrderColumn(db, sql, "entry", "runner");
                assertOrderColumn(db, sql, "document", "writtenin");
                assertOrderColumn(db, sql, "placement", "operatives");
                assertEmbeddedOrderOwner(db, sql, model);
                assertAssociationCatalog(db, sql, model, "Placement", Map.of("Site", "dutyarea", "Operatives", "operative"));
                assertTypeKind(db, sql, "placement", "memo", "TEXT");
                assertStoredOrderedPositions(db, sql, "ordered", false);
                assertCliSuccess(db, "export", model, "ordered", nativeOutput);
                assertOrderedTransfer(nativeOutput, model, version, "ordered", false);

                assertCliSuccess(db, "import", model, "native", nativeInput);
                assertStoredOrderedPositions(db, sql, "native", true);
                reference.run(db, STORED_MODELS, "--export", "--exportTid", "--dataset", "native", referenceOutput.toString());
                assertOrderedTransfer(referenceOutput, model, version, "native", false);

            }
        }
    }
}
