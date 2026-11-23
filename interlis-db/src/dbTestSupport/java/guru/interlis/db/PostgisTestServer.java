package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.UUID;

import static guru.interlis.db.DbTestSupport.postgisContainer;

final class PostgisTestServer implements AutoCloseable {
    enum Backend { GEOPACKAGE, POSTGIS }

    private PostgreSQLContainer postgres;

    Target target(Path file, Backend backend) {
        return backend == Backend.POSTGIS ? target(file) : new Target(file, null, "geo_package");
    }

    Target target(Path file) {
        String schema = "db_test_" + UUID.randomUUID().toString().replace("-", "");
        // Drop schemas with the container so other tests can safely read the shared PostGIS catalog.
        return new Target(file, container(), schema);
    }

    synchronized PostgreSQLContainer container() {
        if (postgres == null) {
            var container = postgisContainer();
            try {
                container.start();
            } catch (RuntimeException | Error failure) {
                try {
                    container.close();
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
            postgres = container;
        }
        return postgres;
    }

    @Override
    public synchronized void close() {
        if (postgres != null) postgres.close();
    }

}
