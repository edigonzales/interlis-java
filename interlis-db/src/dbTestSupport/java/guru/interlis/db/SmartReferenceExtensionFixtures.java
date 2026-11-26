package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SmartReferenceExtensionFixtures {
    final SmartModelExtensionFixtures catalog;
    final String survey;
    final Path surveySource;
    private final ProfileFixtures transfers;
    private final Path work;
    private final int version;

    SmartReferenceExtensionFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        transfers = new ProfileFixtures(work, version);
        catalog = new SmartModelExtensionFixtures(work, version, false, false);
        survey = "StoredSurvey" + version;
        surveySource = Files.writeString(work.resolve(survey + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  IMPORTS %s;
                  TOPIC Data =
                    DEPENDS ON %s.Data;
                    CLASS SurveyRecord = END SurveyRecord;
                    ASSOCIATION Location =
                      Records -- {0..*} SurveyRecord;
                      Site (EXTERNAL) -- {0..1} %s.Data.BaseAsset;
                    END Location;
                  END Data;
                END %s.
                """.formatted(version - 20, survey, catalog.original, catalog.original, catalog.original, survey));
    }

    String referencePath() { return survey + ".Data.Location.Site"; }

    Path existingSurvey(String prefix, String targetTid, String targetBid) throws Exception {
        return survey(prefix, targetTid, targetBid);
    }

    Path combined(String prefix) throws Exception {
        return ModelFixtures.mergeDeliveries(work.resolve(prefix + "-combined.xtf"), version,
                catalog.specializedInput(prefix + "-catalog"), survey(prefix, prefix + "-catalog-specialized", prefix + "-catalog-basket"));
    }

    private Path survey(String prefix, String targetTid, String targetBid) throws Exception {
        String reference = transfers.reference("Site", targetTid);
        if (version == 23) reference = reference.replace("/>", " BID=\"" + targetBid + "\"/>");
        return transfers.data(survey, prefix, transfers.object(survey, "SurveyRecord", prefix + "-record", reference));
    }

    void assertSurvey(Path output, String prefix, String targetTid) throws Exception {
        var basket = ModelFixtures.basket(readDataSection(output, version), version, survey + ".Data", prefix + "-basket");
        assertEquals(1, children(basket).size());
        var row = children(basket).getFirst();
        ModelFixtures.assertName(row, version, survey + ".Data.SurveyRecord");
        assertEquals(prefix + "-record", id(row, version, "tid"));
        assertEquals(targetTid, id(child(row, "Site"), version, "ref"));
    }
}
