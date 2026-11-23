package guru.interlis.db;

import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.postgisContainer;

final class TestDatabase extends DbTestSupport.Target {
    private final Path work;
    private final String scope;

    TestDatabase(Path work, PostgreSQLContainer postgres, String name) {
        this(work, postgres, name, "");
    }

    TestDatabase(Path work, PostgreSQLContainer postgres, String name, String scope) {
        super(work.resolve(name + ".gpkg"), postgres, "db_interop_" + scope + name);
        this.work = work;
        this.scope = scope;
    }

    List<String> referenceOptions() {
        if (!postgis) return List.of("--dbfile", file.toString());
        return List.of("--dbhost", postgres.getHost(), "--dbport", postgres.getFirstMappedPort().toString(),
                "--dbdatabase", postgres.getDatabaseName(), "--dbschema", schema, "--dbusr", user, "--dbpwd", password);
    }

    TestDatabase peer(String name) { return new TestDatabase(work, postgres, name, scope); }

    @FunctionalInterface
    interface PostgisCase {
        void run(int version, PostgreSQLContainer postgres) throws Exception;
    }

    static void exchangeWithPostgis(int version, PostgisCase test) throws Exception {
        try (var postgres = postgisContainer()) {
            postgres.start();
            test.run(version, postgres);
        }
    }
}
