package guru.interlis.db;

import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

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
}
