package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SmartReferenceExtensionFixtures {
    final SmartModelExtensionFixtures catalog;
    final String survey;
    final Path surveySource;
    private final ProfileFixtures transfers;
    private final Path work;
    private final int version;
    private final boolean attribute;

    SmartReferenceExtensionFixtures(Path work, int version) throws Exception {
        this(work, version, false);
    }

    SmartReferenceExtensionFixtures(Path work, int version, boolean attribute) throws Exception {
        this.work = work;
        this.version = version;
        this.attribute = attribute;
        transfers = new ProfileFixtures(work, version);
        catalog = new SmartModelExtensionFixtures(work, version, false, false);
        survey = "StoredSurvey" + version;
        String definition = "Site : REFERENCE TO (EXTERNAL) " + catalog.original + ".Data.BaseAsset;";
        String declarations = attribute ? version == 23 ? "STRUCTURE SiteNote = " + definition + " END SiteNote;\n"
                + "    CLASS SurveyRecord = LocationInfo : SiteNote; END SurveyRecord;"
                : "CLASS SurveyRecord = " + definition + " END SurveyRecord;" : """
                    CLASS SurveyRecord = END SurveyRecord;
                    ASSOCIATION Location =
                      Records -- {0..*} SurveyRecord;
                      Site (EXTERNAL) -- {0..1} %s.Data.BaseAsset;
                    END Location;
                """.formatted(catalog.original).strip();
        surveySource = Files.writeString(work.resolve(survey + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  IMPORTS %s;
                  TOPIC Data =
                    DEPENDS ON %s.Data;
                    %s
                  END Data;
                END %s.
                """.formatted(version - 20, survey, catalog.original, catalog.original, declarations, survey));
    }

    String referencePath() { return survey + ".Data." + (attribute ? version == 23 ? "SiteNote" : "SurveyRecord" : "Location") + ".Site"; }

    String ownerTable() { return attribute && version == 23 ? "sitenote" : "surveyrecord"; }

    Path existingSurvey(String prefix, String targetTid, String targetBid) throws Exception {
        return survey(prefix, targetTid, targetBid);
    }

    Path combined(String prefix) throws Exception {
        return ModelFixtures.mergeDeliveries(work.resolve(prefix + "-combined.xtf"), version,
                catalog.specializedInput(prefix + "-catalog"), survey(prefix, prefix + "-catalog-specialized", prefix + "-catalog-basket"));
    }

    private Path survey(String prefix, String targetTid, String targetBid) throws Exception {
        String reference = targetTid == null ? "" : transfers.reference("Site", targetTid);
        if (version == 23 && targetTid != null) {
            reference = reference.replace("/>", " BID=\"" + targetBid + "\"/>");
            if (attribute) {
                String tag = survey + ".Data.SiteNote";
                reference = "<LocationInfo><" + tag + ">" + reference + "</" + tag + "></LocationInfo>";
            }
        }
        return transfers.data(survey, prefix, transfers.object(survey, "SurveyRecord", prefix + "-record", reference));
    }

    void assertSurvey(Path output, String prefix, String targetTid) throws Exception {
        var basket = ModelFixtures.basket(readDataSection(output, version), version, survey + ".Data", prefix + "-basket");
        assertEquals(1, children(basket).size());
        var row = children(basket).getFirst();
        ModelFixtures.assertName(row, version, survey + ".Data.SurveyRecord");
        assertEquals(prefix + "-record", id(row, version, "tid"));
        var owner = attribute && version == 23 ? child(row, "LocationInfo") : row;
        var site = owner == null ? null : child(attribute && version == 23 ? children(owner).getFirst() : owner, "Site");
        if (targetTid == null) assertNull(site);
        else assertEquals(targetTid, id(site, version, "ref"));
    }
}
