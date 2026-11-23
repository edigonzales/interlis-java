package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ExternalReferenceFixtures.assertExternalRows;
import static guru.interlis.db.ExternalReferenceFixtures.assertExternalTopicRows;
import static guru.interlis.db.ExternalReferenceFixtures.assertExternalTopicXtf;
import static guru.interlis.db.ExternalReferenceFixtures.assertExternalXtf;
import static guru.interlis.db.ExternalReferenceFixtures.assertReferenceDatasetRows;
import static guru.interlis.db.ExternalReferenceFixtures.assertReferenceDatasetXtf;
import static guru.interlis.db.ExternalReferenceFixtures.assertSplitTopicLinks;
import static guru.interlis.db.ExternalReferenceFixtures.assertSplitTopicLinksXtf;
import static guru.interlis.db.ExternalReferenceFixtures.catalogInput;
import static guru.interlis.db.ExternalReferenceFixtures.modelText;
import static guru.interlis.db.ExternalReferenceFixtures.splitInput;
import static guru.interlis.db.TransferCatalogAssertions.assertAssociationCatalog;
import static guru.interlis.db.TransferCatalogAssertions.assertQualifiedAssociationCatalog;
import static guru.interlis.db.TransferCatalogAssertions.assertQualifiedReferenceMapping;
import static guru.interlis.db.TransferCatalogAssertions.assertReferenceMapping;
import static guru.interlis.db.TransferCatalogAssertions.assertTypeKind;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CoreExternalCases {
    private final Path work;

    CoreExternalCases(Path work) {
        this.work = work;
    }

    void exchangeExternal(int version, TestDatabase referenceDb, ReferenceTool reference) throws Exception {
        String model = "DbExternal" + version;
        Path ili = Files.writeString(work.resolve(model + ".ili"), modelText(model + ".ili"));
        String fixture = modelText(model + ".xtf");
        reference.run(referenceDb, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                ili.toString());
        var nativeDb = referenceDb.peer("external_native");
        assertSchemaWithIdentityColumns(nativeDb, model, ili);
        try (var referenceSql = referenceDb.connect(); var nativeSql = nativeDb.connect()) {
            DbSchemaCatalog.read(referenceSql, referenceDb.postgis, referenceDb.schema)
                    .assertSameAs(DbSchemaCatalog.read(nativeSql, nativeDb.postgis, nativeDb.schema));
            assertReferenceMapping(referenceDb, referenceSql, model, "Detail.TargetRef", "detail", "targetref", "targetbranch");
            if (version == 24) {
                assertReferenceMapping(referenceDb, referenceSql, model, "Report.DirectRef", "report", "directref", "targetbranch");
            }
            assertReferenceMapping(referenceDb, referenceSql, model, "ReportPlace.BaseSite", "report", "basesite", "targetbranch");
            assertReferenceMapping(referenceDb, referenceSql, model, "ReportPairing.PairedSite", "report", "pairedsite", "targetbranch");
            for (String association : List.of("ReportPlace", "ReportPairing")) {
                assertEquals("embedded", scalar(referenceSql, "select setting from " + referenceDb.table("t_ili2db_trafo")
                        + " where iliname='" + model + ".Data." + association + "' and tag='ch.ehi.ili2db.inheritance'"));
            }
            assertAssociationCatalog(referenceDb, referenceSql, model, "ExternalPair",
                    Map.of("PairObject", "report", "PairTarget", "targetbranch"));
            assertAssociationCatalog(referenceDb, referenceSql, model, "ExternalReview",
                    Map.of("ReviewOwner", "report", "ReviewSite", "targetbranch"));
            assertTypeKind(referenceDb, referenceSql, "externalreview", "reviewmemo", "TEXT");
            exchangeExternalDatasets(reference, referenceDb, referenceSql, model, version, fixture);
            exchangeExternalDatasets(reference, nativeDb, nativeSql, model, version, fixture);
            exchangeReferenceDataset(reference, referenceDb, referenceSql, model, version, false);
            exchangeReferenceDataset(reference, nativeDb, nativeSql, model, version, true);
        }
    }

    private void exchangeExternalDatasets(ReferenceTool reference, TestDatabase db, Connection sql, String model,
                                          int version, String fixture) throws Exception {
        Path referenceInput = Files.writeString(work.resolve("external-input.xtf"), fixture);
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "external",
                referenceInput.toString());
        assertExternalRows(db, sql, version, "external");
        Path ownOutput = work.resolve("external-own-output.xtf");
        assertCliSuccess(db, "export", model, "external", ownOutput);
        assertExternalXtf(ownOutput, model, version, "external", true);

        Path ownInput = Files.writeString(work.resolve("external-own-input.xtf"), fixture.replace("external-", "own-"));
        assertCliSuccess(db, "import", model, "own", ownInput);
        assertExternalRows(db, sql, version, "own");
        Path referenceOutput = work.resolve("external-reference-output.xtf");
        reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "own", referenceOutput.toString());
        assertExternalXtf(referenceOutput, model, version, "own", false);

        Path replay = Files.writeString(work.resolve("external-replay.xtf"),
                Files.readString(referenceOutput).replace("own-", "replay-"));
        assertCliSuccess(db, "import", model, "replay", replay);
        assertExternalRows(db, sql, version, "replay");
    }

    private void exchangeReferenceDataset(ReferenceTool reference, TestDatabase db, Connection sql, String model,
                                          int version, boolean followUp) throws Exception {
        var inputs = catalogInput(work, model, version);
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "catalog", inputs.target().toString());
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "catalog-source", inputs.source().toString());
        assertReferenceDatasetRows(db, sql, version, "report-id");
        Path output = work.resolve("catalog-native-output.xtf");
        assertCliSuccess(db, "export", model, "catalog-source", output);
        assertReferenceDatasetXtf(output, version, true);

        Path replay = Files.writeString(work.resolve("catalog-replay.xtf"),
                Files.readString(output).replace("report-id", "replay-report").replace("catalog-source", "replay-basket"));
        assertCliSuccess(db, "import", model, "catalog-replay", replay);
        assertReferenceDatasetRows(db, sql, version, "replay-report");
        Path referenceOutput = work.resolve("catalog-reference-output.xtf");
        reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "catalog-replay", referenceOutput.toString());
        assertReferenceDatasetXtf(referenceOutput, version, false);
        Path referenceReplay = Files.writeString(work.resolve("catalog-reference-replay.xtf"),
                Files.readString(referenceOutput).replace("replay-report", "reference-report").replace("replay-basket", "reference-basket"));
        assertCliSuccess(db, "import", model, "catalog-reference-replay", referenceReplay);
        assertReferenceDatasetRows(db, sql, version, "reference-report");

        if (followUp) {
            reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "catalog-tail", inputs.tail().toString());
            assertEquals("1", scalar(sql, "select count(*) from " + db.table("targetroot") + " where t_ili_tid='tail-catalog-leaf'"));
        }
    }

    void exchangeExternalTopics(int version, TestDatabase referenceDb, ReferenceTool reference) throws Exception {
        String model = "DbExternalTopics" + version;
        Path ili = Files.writeString(work.resolve(model + ".ili"), modelText(model + ".ili"));
        String fixture = modelText(model + ".xtf");
        Path referenceInput = Files.writeString(work.resolve("topics-input.xtf"), fixture);
        Path ownInput = Files.writeString(work.resolve("topics-own-input.xtf"), fixture.replace("fixture-", "own-"));
        Path tailInput = Files.writeString(work.resolve("topics-tail-input.xtf"), fixture.replace("fixture-", "tail-"));
        Path ownOutput = work.resolve("topics-own-output.xtf");
        Path referenceOutput = work.resolve("topics-reference-output.xtf");
        reference.run(referenceDb, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", ili.toString());
        var nativeDb = referenceDb.peer("topics_native");
        assertSchemaWithIdentityColumns(nativeDb, model, ili);
        try (var referenceSql = referenceDb.connect(); var nativeSql = nativeDb.connect()) {
            DbSchemaCatalog.read(referenceSql, referenceDb.postgis, referenceDb.schema)
                    .assertSameAs(DbSchemaCatalog.read(nativeSql, nativeDb.postgis, nativeDb.schema));
            assertQualifiedReferenceMapping(referenceDb, referenceSql, model + ".Surveys.SiteAnnotation.AnnotationSite",
                    "siteannotation", "annotationsite", "registeredsite");
            assertQualifiedReferenceMapping(referenceDb, referenceSql, model + ".Surveys.SiteAnnotation.FlatSite",
                    "siteannotation", "flatsite", "landmark");
            if (version == 24) {
                assertQualifiedReferenceMapping(referenceDb, referenceSql, model + ".Surveys.SurveyRecord.DirectSite",
                        "surveyrecord", "directsite", "registeredsite");
            }
            assertQualifiedReferenceMapping(referenceDb, referenceSql, model + ".Surveys.SurveyArea.MarkedSite",
                    "surveyrecord", "markedsite", "registeredsite");
            assertEquals("embedded", scalar(referenceSql, "select setting from " + referenceDb.table("t_ili2db_trafo")
                    + " where iliname='" + model + ".Surveys.SurveyArea' and tag='ch.ehi.ili2db.inheritance'"));
            assertQualifiedAssociationCatalog(referenceDb, referenceSql, model + ".Surveys", "SurveyReview",
                    Map.of("Reviews", "surveyrecord", "ReviewedAt", "registeredsite"));
            assertQualifiedAssociationCatalog(referenceDb, referenceSql, model + ".Surveys", "SurveyMatch",
                    Map.of("MatchedRecord", "surveyrecord", "Surveyed", "registeredsite"));
            assertQualifiedAssociationCatalog(referenceDb, referenceSql, model + ".Surveys", "SitePermit",
                    Map.of("IssuedPermit", "permitrecord", "CoveredSite", "registeredsite"));
        }
        for (var db : List.of(referenceDb, nativeDb)) {
            try (var sql = db.connect()) {
                reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "fixture",
                        referenceInput.toString());
                assertExternalTopicRows(db, sql, model, version, "fixture");
                assertCliSuccess(db, "export", model, "fixture", ownOutput);
                assertExternalTopicXtf(ownOutput, model, version, "fixture", true);
                assertCliSuccess(db, "import", model, "own", ownInput);
                assertExternalTopicRows(db, sql, model, version, "own");
                reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "own", referenceOutput.toString());
                assertExternalTopicXtf(referenceOutput, model, version, "own", false);
                exchangeExternalTopicDatasets(reference, db, sql, model, version, fixture);
                if (db == nativeDb) {
                    reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "tail",
                            tailInput.toString());
                    assertExternalTopicRows(db, sql, model, version, "tail");
                }
            }
        }
    }

    private void exchangeExternalTopicDatasets(ReferenceTool reference, TestDatabase db, Connection sql, String model,
                                               int version, String fixture) throws Exception {
        var inputs = splitInput(work, model, version, fixture);
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "split-catalog",
                inputs.target().toString());
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "split",
                inputs.source().toString());
        assertSplitTopicLinks(db, sql, "split");
        Path ownOutput = work.resolve("topics-split-own-output.xtf");
        assertCliSuccess(db, "export", model, "split", ownOutput);
        assertSplitTopicLinksXtf(ownOutput, model, version, "split", true);
        assertCliSuccess(db, "import", model, "split-own", inputs.ownSource());
        assertSplitTopicLinks(db, sql, "split-own");
        Path referenceOutput = work.resolve("topics-split-reference-output.xtf");
        reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "split-own", referenceOutput.toString());
        assertSplitTopicLinksXtf(referenceOutput, model, version, "split-own", false);
    }
}
