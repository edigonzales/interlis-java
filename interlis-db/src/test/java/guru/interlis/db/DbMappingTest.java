package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DbMappingTest {
    @Test
    void rejectsIntegerOverrideWithDifferentStorageKind() {
        for (String version : List.of("2.3", "2.4")) {
            var source = new SourceText(new SourceId("test:/Readings.ili"), new SourceRevision(1), """
                    INTERLIS %s;
                    MODEL Readings (en) AT "https://example.org" VERSION "2026-11-25" =
                      TOPIC Data =
                        CLASS Reading =
                          Value : 0 .. 5000000000;
                        END Reading;
                        CLASS LocalReading EXTENDS Reading =
                          Value (EXTENDED) : 0 .. 49;
                        END LocalReading;
                      END Data;
                    END Readings.
                    """.formatted(version));
            var rejected = assertThrows(DbException.class,
                    () -> DbMapping.planNew("Readings", source, false, false, 2056, CancellationToken.none()));
            assertTrue(rejected.getMessage().contains("extended attribute in inheritance at Readings.Data.LocalReading.Value"),
                    rejected::getMessage);
        }
    }

    @Test
    void rejectsDecimalOverrideWithDifferentScale() {
        var source = new SourceText(new SourceId("test:/Readings.ili"), new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL Readings (en) AT "https://example.org" VERSION "2026-11-25" =
                  TOPIC Data =
                    CLASS Reading =
                      Value : -90.00 .. 90.00;
                    END Reading;
                    CLASS LocalReading EXTENDS Reading =
                      Value (EXTENDED) : -9.999 .. 9.999;
                    END LocalReading;
                  END Data;
                END Readings.
                """);
        var rejected = assertThrows(DbException.class,
                () -> DbMapping.planNew("Readings", source, false, false, 2056, CancellationToken.none()));
        assertTrue(rejected.getMessage().contains("extended attribute in inheritance at Readings.Data.LocalReading.Value"),
                rejected::getMessage);
    }

    @Test
    void rejectsOneToOneAssociationsBetweenTopics() {
        for (String version : List.of("2.3", "2.4")) {
            var source = new SourceText(new SourceId("test:/Location.ili"), new SourceRevision(1), """
                    INTERLIS %s;
                    MODEL Location (en) AT "https://example.org" VERSION "2026-11-21" =
                      TOPIC Catalog = CLASS Site = END Site; END Catalog;
                      TOPIC Surveys =
                        DEPENDS ON Location.Catalog;
                        CLASS Report = END Report;
                        ASSOCIATION SurveySite =
                          SiteRole (EXTERNAL) -- {0..1} Location.Catalog.Site;
                          ReportRole -- {0..1} Report;
                        END SurveySite;
                      END Surveys;
                    END Location.
                    """.formatted(version));
            var rejected = assertThrows(DbException.class,
                    () -> DbMapping.planNew("Location", source, false, false, 2056, CancellationToken.none()));
            assertTrue(rejected.getMessage().contains("one-to-one associations between topics at Location.Surveys.SurveySite"),
                    rejected::getMessage);
        }
    }

    @Test
    void rejectsOwnAttributesOnAggregationsAndCompositions() {
        for (String version : List.of("2.3", "2.4")) {
            for (String strength : List.of("-<>", "-<#>")) {
                var source = new SourceText(new SourceId("test:/Ownership.ili"), new SourceRevision(1), """
                        INTERLIS %s;
                        MODEL Ownership (en) AT "https://example.org" VERSION "2026-11-20" =
                          TOPIC Data =
                            CLASS Parent = END Parent;
                            CLASS Child = END Child;
                            ASSOCIATION OwnershipLink =
                              Whole %s {0..1} Parent;
                              Part -- {0..*} Child;
                              Note : TEXT*40;
                            END OwnershipLink;
                          END Data;
                        END Ownership.
                        """.formatted(version, strength));
                var rejected = assertThrows(DbException.class,
                        () -> DbMapping.planNew("Ownership", source, false, false, 2056, CancellationToken.none()));
                assertTrue(rejected.getMessage().contains("attributes on an aggregation or composition at Ownership.Data.OwnershipLink"),
                        rejected::getMessage);
            }
        }
    }

    @Test
    void rejectsCyclicStructureCompositionDuringPlanning() {
        for (String version : List.of("2.3", "2.4")) {
            var source = new SourceText(new SourceId("test:/Tree.ili"), new SourceRevision(1), """
                    INTERLIS %s;
                    MODEL Tree (en) AT "https://example.org" VERSION "2026-11-20" =
                      TOPIC Data =
                        STRUCTURE Node =
                          Children : LIST {0..*} OF Node;
                        END Node;
                        CLASS Root =
                          Entry : Node;
                        END Root;
                      END Data;
                    END Tree.
                    """.formatted(version));
            var rejected = assertThrows(DbException.class,
                    () -> DbMapping.planNew("Tree", source, false, false, 2056, CancellationToken.none()));
            assertTrue(rejected.getMessage().contains("cyclic structure composition at Tree.Data.Node.Children"),
                    rejected::getMessage);
        }
    }

    @Test
    void mapsEachPredefinedPolylineFormToCompoundCurve() {
        for (String version : List.of("2.3", "2.4")) {
            var source = new SourceText(new SourceId("test:/Routes.ili"), new SourceRevision(1), """
                    INTERLIS %s;
                    MODEL Routes (en) AT "https://example.org" VERSION "2026-11-20" =
                      DOMAIN Position = COORD 2460000 .. 2870000, 1045000 .. 1310000;
                      TOPIC Data =
                        CLASS StraightRoute =
                          Trace : POLYLINE WITH (STRAIGHTS) VERTEX Position;
                        END StraightRoute;
                        CLASS ArcRoute =
                          Trace : POLYLINE WITH (ARCS) VERTEX Position;
                        END ArcRoute;
                        CLASS MixedRoute =
                          Trace : POLYLINE WITH (STRAIGHTS, ARCS) VERTEX Position;
                        END MixedRoute;
                      END Data;
                    END Routes.
                    """.formatted(version));
            var mapping = DbMapping.planNew("Routes", source, false, false, 2056, CancellationToken.none());
            var geometryTypes = mapping.tables().stream()
                    .map(table -> table.columns().getFirst().kind().geometryType()).toList();
            assertEquals(List.of("COMPOUNDCURVE", "COMPOUNDCURVE", "COMPOUNDCURVE"), geometryTypes,
                    "INTERLIS " + version);
        }
    }
}
