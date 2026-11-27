package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class Smart2LinkExtensionFixtures {
    record Destination(String tid, String bid) { }

    static final Destination ORIGINAL = new Destination("original-entry", "original-basket");

    final SmartModelExtensionFixtures catalog;
    final String survey;
    final Path surveySource;
    private final ProfileFixtures transfers;
    private final Path work;
    private final int version;

    Smart2LinkExtensionFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        transfers = new ProfileFixtures(work, version);
        catalog = new SmartModelExtensionFixtures(work, version, false, false);
        survey = "LinkedSurvey" + version;
        surveySource = Files.writeString(work.resolve(survey + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  IMPORTS %s;
                  TOPIC Data =
                    DEPENDS ON %s.Data;
                    CLASS Visit = END Visit;
                    ASSOCIATION Assignment =
                      Visits -- {0..*} Visit;
                      Sites (EXTERNAL) -- {0..*} %s.Data.BaseAsset;
                    END Assignment;
                  END Data;
                END %s.
                """.formatted(version - 20, survey, catalog.original, catalog.original, catalog.original, survey));
    }

    String associationPath() { return survey + ".Data.Assignment"; }

    Path linkInput(String prefix, Destination... destinations) throws Exception {
        String tag = version == 23 ? associationPath() : "Assignment";
        String visits = transfers.reference("Visits", prefix + "-visit");
        String links = Arrays.stream(destinations).map(destination -> "<" + tag + ">" + visits
                + destinationReference(destination) + "</" + tag + ">").collect(Collectors.joining());
        return transfers.data(survey, prefix, transfers.object(survey, "Visit", prefix + "-visit", "") + links);
    }

    Path combined(String prefix) throws Exception {
        return ModelFixtures.mergeDeliveries(work.resolve(prefix + "-combined.xtf"), version,
                catalog.specializedInput(prefix + "-catalog"), linkInput(prefix, ORIGINAL, specialized(prefix + "-catalog")));
    }

    static Destination specialized(String prefix) {
        return new Destination(prefix + "-specialized", prefix + "-basket");
    }

    private String destinationReference(Destination destination) {
        String site = transfers.reference("Sites", destination.tid());
        return version == 23 ? site.replace("/>", " BID=\"" + destination.bid() + "\"/>") : site;
    }

    void assertLinks(Path output, String prefix, boolean nativeExport, Destination... expected) throws Exception {
        var basket = ModelFixtures.basket(DbTestSupport.readDataSection(output, version), version, survey + ".Data", prefix + "-basket");
        var rows = children(basket);
        assertEquals(expected.length + 1, rows.size());
        var visit = rows.stream().filter(row -> id(row, version, "tid").equals(prefix + "-visit")).findFirst().orElseThrow();
        ModelFixtures.assertName(visit, version, survey + ".Data.Visit");
        var destinations = rows.stream().filter(row -> row != visit).map(link -> {
            ModelFixtures.assertName(link, version, associationPath());
            assertEquals("", id(link, version, "tid"));
            assertEquals(prefix + "-visit", id(child(link, "Visits"), version, "ref"));
            var site = child(link, "Sites");
            return new Destination(id(site, version, "ref"), version == 23 ? id(site, version, "bid") : "");
        }).collect(Collectors.toSet());
        var expectedDestinations = version == 23 && nativeExport ? Set.of(expected)
                : Arrays.stream(expected).map(site -> new Destination(site.tid(), "")).collect(Collectors.toSet());
        assertEquals(expectedDestinations, destinations);
    }
}
