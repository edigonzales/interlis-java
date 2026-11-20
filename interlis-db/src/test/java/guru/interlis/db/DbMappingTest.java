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
                        () -> DbMapping.planNew("Ownership", source, false, CancellationToken.none()));
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
                    () -> DbMapping.planNew("Tree", source, false, CancellationToken.none()));
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
            var mapping = DbMapping.planNew("Routes", source, false, CancellationToken.none());
            var geometryTypes = mapping.tables().stream()
                    .map(table -> table.columns().getFirst().kind().geometryType()).toList();
            assertEquals(List.of("COMPOUNDCURVE", "COMPOUNDCURVE", "COMPOUNDCURVE"), geometryTypes,
                    "INTERLIS " + version);
        }
    }
}
