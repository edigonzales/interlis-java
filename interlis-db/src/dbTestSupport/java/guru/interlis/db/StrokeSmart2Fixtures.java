package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StrokeSmart2Fixtures {
    final String model;
    private final StrokeFixtures xml;
    private final Path source;

    StrokeSmart2Fixtures(Path work, int version) throws Exception {
        xml = new StrokeFixtures(work, version);
        model = "StrokeSmart" + version;
        source = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN Vertex = COORD 400000.000 .. 900000.000, 0.000 .. 400000.000;
                  TOPIC Data =
                    CLASS AssetBase (ABSTRACT) =
                      Anchor : Vertex;
                      Shape : POLYLINE WITH (STRAIGHTS, ARCS) VERTEX Vertex;
                    END AssetBase;
                    CLASS SurveyAsset EXTENDS AssetBase =
                      Marker : TEXT*20;
                    END SurveyAsset;
                  END Data;
                END %s.
                """.formatted(version - 20, model, model));
    }

    void schema(Target db) {
        var options = new ArrayList<>(List.of("--inheritance", "smart2", "--stroke-arcs", "--create-geom-index"));
        if (db.postgis()) options.add("--one-geom-per-table");
        assertSchemaWithIdentityColumns(db, model, source, options.toArray(String[]::new));
    }

    Path delivery(String filePrefix, String idPrefix, int x, String marker) throws Exception {
        var line = xml.polyline(xml.coord(x, 200000) + xml.arc(x + 5, 199995, x + 10, 200000));
        return xml.transfer(model, filePrefix, idPrefix + "-b", xml.object(model, "SurveyAsset", idPrefix + "-value",
                "<Anchor>" + xml.coord(x, 200000) + "</Anchor><Shape>" + line + "</Shape><Marker>" + marker + "</Marker>"));
    }

    void assertAsset(Path output, String prefix, int x, String marker) throws Exception {
        var objects = xml.objects(output, prefix);
        assertEquals(1, objects.size());
        var asset = objects.get(prefix + "-value");
        assertEquals(marker, child(asset, "Marker").getTextContent());
        assertEquals(List.of((double) x, 200000.0), xml.position(children(child(asset, "Anchor")).getFirst()));
        var line = xml.linePositions(asset);
        assertEquals(81, line.size());
        assertEquals(List.of((double) x, 200000.0), line.getFirst());
        assertEquals(List.of((double) x + 5, 199995.0), line.get(40));
        assertEquals(List.of((double) x + 10, 200000.0), line.getLast());
    }
}
