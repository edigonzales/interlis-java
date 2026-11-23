package guru.interlis.db;

import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.postgisContainer;

final class PostgisTestServer implements AutoCloseable {
    private PostgreSQLContainer postgres;

    Schema target(Path file) {
        String schema = "db_test_" + UUID.randomUUID().toString().replace("-", "");
        return new Schema(file, start(), schema);
    }

    private synchronized PostgreSQLContainer start() {
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

    static final class Schema extends DbTestSupport.Target implements AutoCloseable {
        private Schema(Path file, PostgreSQLContainer postgres, String schema) {
            super(file, postgres, schema);
        }

        @Override
        public void close() throws SQLException {
            try (var sql = connect()) {
                execute(sql, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
