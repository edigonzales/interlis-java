package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DbTestSupport {
    private static final String XTF24_NS = "http://www.interlis.ch/xtf/2.4/INTERLIS";

    private DbTestSupport() { }

    static PostgreSQLContainer postgisContainer() {
        var image = DockerImageName.parse("sogis/postgis:16-3.4").asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer(image)
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,size=256m"))
                .withPassword("db_interop_password");
    }

    static class Target {
        final PostgreSQLContainer postgres;
        final boolean postgis;
        final Path file;
        final String schema;
        final String user;
        final String password;
        final String url;

        Target(Path file, PostgreSQLContainer postgres, String schema) {
            this.file = file;
            this.postgres = postgres;
            this.schema = schema;
            postgis = postgres != null;
            url = postgis ? postgres.getJdbcUrl() : "jdbc:sqlite:" + file;
            user = postgis ? postgres.getUsername() : "";
            password = postgis ? postgres.getPassword() : "";
        }

        Connection connect() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        List<String> nativeOptions() {
            return postgis ? List.of("--pg-url", url, "--schema", schema, "--user", user)
                    : List.of("--gpkg", file.toString());
        }

        boolean postgis() { return postgis; }

        String schema() { return schema; }

        String table(String name) { return postgis ? schema + "." + name : name; }
    }

    static String referenceDatasetFixture(String model, int version, String bid, String objects) {
        if (version == 23) return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="interop"><MODELS><MODEL NAME="%1$s" VERSION="1" URI="https://example.org/interlis/db"/></MODELS></HEADERSECTION>
                  <DATASECTION><%1$s.Data BID="%2$s">%3$s</%1$s.Data></DATASECTION>
                </TRANSFER>
                """.formatted(model, bid, objects);
        return """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="http://www.interlis.ch/xtf/2.4/%1$s">
                  <i:headersection><i:models><i:model>%1$s</i:model></i:models><i:sender>interop</i:sender></i:headersection>
                  <i:datasection><Data i:bid="%2$s">%3$s</Data></i:datasection>
                </i:transfer>
                """.formatted(model, bid, objects);
    }

    static Element readDataSection(Path output, int version) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(output.toFile());
        var data = child(document.getDocumentElement(), version == 24 ? "datasection" : "DATASECTION");
        assertNotNull(data);
        return data;
    }

    static List<Element> children(Element parent) {
        var elements = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) elements.add(child);
        }
        return elements;
    }

    static Element child(Element parent, String name) {
        return children(parent).stream().filter(element -> element.getLocalName().equals(name)).findFirst().orElse(null);
    }

    static String id(Element element, int version, String name) {
        return version == 24 ? element.getAttributeNS(XTF24_NS, name) : element.getAttribute(name.toUpperCase(Locale.ROOT));
    }

    static String scalar(Connection sql, String query) throws SQLException {
        try (var statement = sql.createStatement(); var result = statement.executeQuery(query)) {
            if (!result.next()) return null;
            String value = result.getString(1);
            assertFalse(result.next(), query);
            return value;
        }
    }

    static void execute(Connection sql, String query) throws SQLException {
        try (var statement = sql.createStatement()) {
            statement.executeUpdate(query);
        }
    }

    static void assertCliSuccess(Target db, String operation, String model, String dataset, Path path) {
        var result = cli(db, operation, model, dataset, path, CancellationToken.none());
        assertEquals(0, result.code(), result::diagnostics);
    }

    static void assertCliFailure(Target db, String operation, String model, String dataset, Path path,
                                 String diagnostic, String... options) {
        var result = cli(db, operation, model, dataset, path, CancellationToken.none(), options);
        assertEquals(1, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains(diagnostic), result::diagnostics);
    }

    static void assertSchemaWithIdentityColumns(Target db, String model, Path path) {
        var result = schemaWithIdentityColumns(db, model, path, CancellationToken.none());
        assertEquals(0, result.code(), result::diagnostics);
    }

    static CliResult schemaWithIdentityColumns(Target db, String model, Path path,
                                                CancellationToken token, String... options) {
        var flags = new ArrayList<>(List.of(options));
        if (!flags.contains("--add-models")) flags.addAll(List.of("--create-basket-col", "--create-tid-col"));
        return cli(db, "schemaimport", model, null, path, token, flags.toArray(String[]::new));
    }

    static CliResult cli(Target db, String operation, String model, String dataset, Path path,
                         CancellationToken token, String... options) {
        var diagnostics = new StringWriter();
        var print = new PrintWriter(diagnostics);
        var args = new ArrayList<>(List.of(operation, "--model", model));
        if (dataset != null) args.addAll(List.of("--dataset", dataset));
        args.addAll(db.nativeOptions());
        args.addAll(List.of(options));
        if (path != null) args.add(path.toString());
        int code = Main.run(args.toArray(String[]::new), print, print, db.password, token);
        return new CliResult(code, diagnostics.toString());
    }

    record CliResult(int code, String diagnostics) {}
}
