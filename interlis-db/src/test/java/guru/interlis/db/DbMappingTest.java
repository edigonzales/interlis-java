package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DbMappingTest {
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
