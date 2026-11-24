package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.SmartFixtures.assertGeometryStorage;
import static guru.interlis.db.SmartFixtures.assertStructureStorage;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SmartPayloadInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new SmartFixtures(work, version);
        var reference = new ReferenceTool(work, postgres != null);
        String structures = "SmartStructures" + version;
        String geometry = "SmartGeometry" + version;
        Path structuresIli = fixture.fixture(structures + ".ili");
        Path geometryIli = fixture.fixture(geometry + ".ili");
        Path structuresXtf = fixture.fixture(structures + ".xtf");
        Path geometryXtf = fixture.fixture(geometry + ".xtf");
        Path legacyInput = delivery(version, structures, geometry, structuresXtf, geometryXtf, "legacy");
        Path nativeInput = delivery(version, structures, geometry, structuresXtf, geometryXtf, "native");

        for (String layout : List.of("smart1", "smart2")) {
            var old = new TestDatabase(work, postgres, layout + "_payload_reference", "v" + version + "_");
            var own = old.peer(layout + "_payload_native");
            schema(reference, old, work, structures + ";" + geometry, layout);
            assertSchemaWithIdentityColumns(own, structures, structuresIli,
                    "--model", geometry, "--model-file", geometryIli.toString(), "--inheritance", layout);
            try (var ref = old.connect(); var sql = own.connect()) {
                DbSchemaCatalog.read(ref, old.postgis(), old.schema()).assertSameAs(DbSchemaCatalog.read(sql, own.postgis(), own.schema()));
            }
            for (var db : List.of(old, own)) {
                importDataset(reference, db, "legacy", legacyInput);
                Path output = work.resolve(db.schema() + "-native.xtf");
                var exported = cli(db, "export", structures, "legacy", output, CancellationToken.none(), "--model", geometry);
                assertEquals(0, exported.code(), exported::diagnostics);
                assertDelivery(fixture, version, structures, geometry, output, "legacy");

                var imported = cli(db, "import", structures, "native", nativeInput, CancellationToken.none(), "--model", geometry);
                assertEquals(0, imported.code(), imported::diagnostics);
                output = work.resolve(db.schema() + "-reference.xtf");
                exportDataset(reference, db, "native", output);
                assertDelivery(fixture, version, structures, geometry, output, "native");
                try (var sql = db.connect()) {
                    assertStructureStorage(db, sql, layout, 2);
                    assertGeometryStorage(db, sql, layout, 2);
                }
            }
        }
    }

    private Path delivery(int version, String structures, String geometry, Path structuresXtf, Path geometryXtf, String prefix)
            throws Exception {
        String noteBasket = basketXml(structuresXtf, structures, version, prefix + "-structure");
        String geometryBasket = basketXml(geometryXtf, geometry, version, prefix + "-geometry");
        String transfer = version == 23 ? """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="%1$s" VERSION="1" URI="https://example.org/smart"/>
                    <MODEL NAME="%2$s" VERSION="1" URI="https://example.org/smart"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION>%3$s%4$s</DATASECTION>
                </TRANSFER>
                """ : """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <i:headersection><i:models><i:model>%1$s</i:model><i:model>%2$s</i:model></i:models></i:headersection>
                  <i:datasection>%3$s%4$s</i:datasection>
                </i:transfer>
                """;
        return Files.writeString(work.resolve(prefix + "-payload.xtf"),
                transfer.formatted(structures, geometry, noteBasket, geometryBasket));
    }

    private static String basketXml(Path path, String model, int version, String prefix) throws Exception {
        String input = Files.readString(path);
        String section = version == 23 ? "DATASECTION" : "i:datasection";
        String start = "<" + section + ">";
        String end = "</" + section + ">";
        String basket = input.substring(input.indexOf(start) + start.length(), input.indexOf(end))
                .replace("fixture-", prefix + "-");
        if (version == 24) {
            basket = basket.replace("<Data ", "<Data xmlns=\"http://www.interlis.ch/xtf/2.4/" + model + "\" ");
        }
        return basket;
    }

    private static void assertDelivery(SmartFixtures fixture, int version, String structures, String geometry,
                                       Path output, String prefix) throws Exception {
        var data = readDataSection(output, version);
        var header = child((Element) data.getParentNode(), version == 23 ? "HEADERSECTION" : "headersection");
        var models = children(child(header, version == 23 ? "MODELS" : "models"));
        assertEquals(Set.of(structures, geometry), models.stream()
                .map(model -> version == 23 ? model.getAttribute("NAME") : model.getTextContent())
                .collect(Collectors.toSet()));
        assertEquals(2, children(data).size());
        fixture.assertStructures(basket(data, version, structures, prefix + "-structure-b"), structures, prefix + "-structure");
        fixture.assertGeometries(basket(data, version, geometry, prefix + "-geometry-b"), geometry, prefix + "-geometry");
    }

    private static Element basket(Element data, int version, String model, String bid) {
        var basket = children(data).stream().filter(child -> bid.equals(id(child, version, "bid"))).findFirst().orElseThrow();
        assertEquals(version == 23 ? model + ".Data" : "Data", basket.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + model, basket.getNamespaceURI());
        return basket;
    }
}
