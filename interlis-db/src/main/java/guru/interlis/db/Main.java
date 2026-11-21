package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.transfer.TransferProtocolException;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferWriter;
import guru.interlis.xtf23.Xtf23Exception;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf23.Xtf23Writer;
import guru.interlis.xtf24.Xtf24Exception;
import guru.interlis.xtf24.Xtf24Reader;
import guru.interlis.xtf24.Xtf24Writer;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class Main {
    private static final String USAGE = """
            Usage: interlis-db <export|import|replace|update> --model NAME --dataset NAME
                    (--gpkg FILE | --pg-url JDBC_URL --schema NAME --user NAME) FILE.xtf
                   interlis-db delete --model NAME --dataset NAME
                    (--gpkg FILE | --pg-url JDBC_URL --schema NAME --user NAME)
                   interlis-db schemaimport --model NAME [--default-epsg CODE] [--create-geom-index] [--one-geom-per-table | --gpkg-multi-geom-per-table]
                    (--gpkg FILE | --pg-url JDBC_URL --schema NAME --user NAME) MODEL.ili
            Set INTERLIS_DB_PASSWORD for password authentication with PostgreSQL.
            """;

    private Main() { }

    public static void main(String[] args) {
        System.exit(run(args, new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                new PrintWriter(System.err, true, StandardCharsets.UTF_8),
                System.getenv("INTERLIS_DB_PASSWORD"), Thread.currentThread()::isInterrupted));
    }

    static int run(String[] args, PrintWriter out, PrintWriter err, String password, CancellationToken cancellation) {
        if (args.length == 1 && args[0].equals("--help")) {
            out.print(USAGE);
            out.flush();
            return 0;
        }
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException | DbException failure) {
            err.println(failure.getMessage());
            err.print(USAGE);
            err.flush();
            return 2;
        }
        try {
            cancellation.throwIfCancellationRequested();
            options.checkPaths();
            switch (options.command()) {
                case IMPORT, REPLACE, UPDATE -> importData(options, password, cancellation);
                case DELETE -> deleteData(options, password, cancellation);
                case EXPORT -> exportData(options, password, cancellation);
                case SCHEMAIMPORT -> createSchema(options, password, cancellation);
            }
            return 0;
        } catch (OperationCancelledException failure) {
            err.println(failure.getMessage());
            return 130;
        } catch (DbException | TransferProtocolException | Xtf23Exception | Xtf24Exception | IOException failure) {
            err.println(failure.getMessage());
            return 1;
        } catch (SQLException failure) {
            // Drivers can include connection properties and input data in their error messages.
            String code = failure.getSQLState() == null ? Integer.toString(failure.getErrorCode()) : failure.getSQLState();
            err.println("Database operation failed (SQL code " + code + ")");
            return 1;
        } finally {
            err.flush();
        }
    }

    private static void createSchema(Options options, String password, CancellationToken cancellation)
            throws IOException, SQLException {
        var plan = SchemaPlan.read(options.model(), options.file(), options.gpkg() != null,
                options.splitGeometries(), options.defaultEpsg(), cancellation);
        cancellation.throwIfCancellationRequested();
        if (options.gpkg() != null) {
            GeoPackageSchema.create(options.gpkg(), plan, options.createGeometryIndexes(), cancellation);
        } else {
            try (var database = options.open(password)) {
                cancellation.throwIfCancellationRequested();
                SchemaSql.createPostgres(database, plan, options.createGeometryIndexes(), cancellation);
                cancellation.throwIfCancellationRequested();
                database.commit();
            }
        }
    }

    private static void importData(Options options, String password, CancellationToken cancellation)
            throws IOException, SQLException {
        try (var database = options.open(password)) {
            var mapping = DbSchemaReader.read(database, options.model(), cancellation);
            try (var input = Files.newInputStream(options.file());
                 TransferReader reader = switch (mapping.version()) {
                     case ILI23 -> new Xtf23Reader(mapping.plan(), input);
                     case ILI24 -> new Xtf24Reader(mapping.plan(), input);
                 }) {
                var mode = switch (options.command()) {
                    case REPLACE -> DbTransfer.WriteMode.REPLACE;
                    case UPDATE -> DbTransfer.WriteMode.UPDATE;
                    default -> DbTransfer.WriteMode.IMPORT;
                };
                DbTransfer.importData(database, mapping, options.dataset(), reader, mode, cancellation);
            }
            cancellation.throwIfCancellationRequested();
            database.commit();
        }
    }

    private static void deleteData(Options options, String password, CancellationToken cancellation) throws SQLException {
        try (var database = options.open(password)) {
            var mapping = DbSchemaReader.read(database, options.model(), cancellation);
            DbDatasetWrite.deleteDataset(database, mapping, options.dataset(), cancellation);
            cancellation.throwIfCancellationRequested();
            database.commit();
        }
    }

    private static void exportData(Options options, String password, CancellationToken cancellation)
            throws IOException, SQLException {
        Path temporary = Files.createTempFile(options.file().getParent(), ".interlis-db-", ".xtf");
        try {
            try (var database = options.open(password)) {
                var mapping = DbSchemaReader.read(database, options.model(), cancellation);
                try (var output = Files.newOutputStream(temporary);
                     TransferWriter writer = switch (mapping.version()) {
                         case ILI23 -> new Xtf23Writer(mapping.plan(), output);
                         case ILI24 -> new Xtf24Writer(mapping.plan(), output);
                     }) {
                    DbTransfer.exportData(database, mapping, options.dataset(), writer, cancellation);
                }
                database.commit();
            }
            cancellation.throwIfCancellationRequested();
            Files.move(temporary, options.file(), ATOMIC_MOVE, REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private enum Command { IMPORT, REPLACE, UPDATE, DELETE, EXPORT, SCHEMAIMPORT }

    private record Options(Command command, String model, String dataset, Path file, Path gpkg,
                           String pgUrl, String schema, String user, boolean createGeometryIndexes,
                           boolean splitGeometries, int defaultEpsg) {
        private static final Set<String> NAMES = Set.of("--model", "--dataset", "--gpkg", "--pg-url", "--schema", "--user",
                "--default-epsg");

        static Options parse(String[] args) {
            Command command = switch (args.length == 0 ? "" : args[0]) {
                case "import" -> Command.IMPORT;
                case "replace" -> Command.REPLACE;
                case "update" -> Command.UPDATE;
                case "delete" -> Command.DELETE;
                case "export" -> Command.EXPORT;
                case "schemaimport" -> Command.SCHEMAIMPORT;
                default -> throw new IllegalArgumentException("Expected import, replace, update, delete, export or schemaimport");
            };
            var flags = new HashMap<String, String>();
            Path file = null;
            var schemaFlags = new HashSet<String>();
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--create-geom-index") || arg.equals("--one-geom-per-table")
                        || arg.equals("--gpkg-multi-geom-per-table")) {
                    if (command != Command.SCHEMAIMPORT) {
                        throw new IllegalArgumentException(arg + " is only valid with schemaimport");
                    }
                    if (!schemaFlags.add(arg)) throw new IllegalArgumentException("Repeated option " + arg);
                } else if (arg.startsWith("--")) {
                    if (!NAMES.contains(arg)) {
                        throw new IllegalArgumentException("Unknown option " + arg);
                    }
                    if (++i == args.length || args[i].isBlank() || args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for " + arg);
                    }
                    if (flags.putIfAbsent(arg, args[i]) != null) {
                        throw new IllegalArgumentException("Repeated option " + arg);
                    }
                } else {
                    if (file != null || arg.isBlank()) {
                        throw new IllegalArgumentException("Expected exactly one source or destination file");
                    }
                    file = Path.of(arg).toAbsolutePath().normalize();
                }
            }
            String model = flags.get("--model");
            String dataset = flags.get("--dataset");
            if (command == Command.SCHEMAIMPORT) {
                if (file == null || file.getParent() == null || model == null) {
                    throw new IllegalArgumentException("An .ili path and --model are required");
                }
                if (dataset != null) throw new IllegalArgumentException("--dataset is not valid with schemaimport");
            } else if (command == Command.DELETE) {
                if (file != null || model == null || dataset == null) {
                    throw new IllegalArgumentException("delete requires --model and --dataset without a file");
                }
            } else if (file == null || file.getParent() == null || model == null || dataset == null) {
                throw new IllegalArgumentException("An XTF path, --model and --dataset are required");
            }
            String gpkgPath = flags.get("--gpkg");
            String pgUrl = flags.get("--pg-url");
            String schema = flags.get("--schema");
            String user = flags.get("--user");
            Path gpkg = gpkgPath == null ? null : Path.of(gpkgPath).toAbsolutePath().normalize();
            if (gpkg != null) {
                if (pgUrl != null || schema != null || user != null) {
                    throw new IllegalArgumentException("Choose a single database with --gpkg or --pg-url");
                }
            } else if (pgUrl == null || !pgUrl.startsWith("jdbc:postgresql:") || schema == null || user == null) {
                throw new IllegalArgumentException("PostgreSQL needs a JDBC PostgreSQL URL, --schema and --user");
            }
            String epsg = flags.get("--default-epsg");
            if (epsg != null && command != Command.SCHEMAIMPORT) {
                throw new IllegalArgumentException("--default-epsg is only valid with schemaimport");
            }
            int defaultEpsg = epsg == null ? Ili2dbLayout.LV95 : Ili2dbLayout.parseEpsgCode(epsg, "--default-epsg");
            boolean oneGeometryPerTable = schemaFlags.contains("--one-geom-per-table");
            if (oneGeometryPerTable && gpkg != null) {
                throw new IllegalArgumentException("--one-geom-per-table is only valid with PostgreSQL");
            }
            boolean multipleGeoPackageGeometries = schemaFlags.contains("--gpkg-multi-geom-per-table");
            if (multipleGeoPackageGeometries && gpkg == null) {
                throw new IllegalArgumentException("--gpkg-multi-geom-per-table is only valid with GeoPackage");
            }
            return new Options(command, model, dataset, file, gpkg, pgUrl, schema, user,
                    schemaFlags.contains("--create-geom-index"),
                    oneGeometryPerTable || (gpkg != null && !multipleGeoPackageGeometries), defaultEpsg);
        }

        void checkPaths() throws IOException {
            if (gpkg != null && command != Command.SCHEMAIMPORT) {
                if (!Files.isRegularFile(gpkg)) {
                    throw new DbException("Expected an existing GeoPackage file");
                }
                if (file != null && (file.equals(gpkg) || (Files.exists(file) && Files.isSameFile(file, gpkg)))) {
                    throw new DbException("The XTF and GeoPackage paths must identify different files");
                }
            }
        }

        Database open(String password) throws SQLException {
            boolean writable = command != Command.EXPORT;
            int isolation = switch (command) {
                case REPLACE, UPDATE, DELETE -> Connection.TRANSACTION_READ_COMMITTED;
                default -> Connection.TRANSACTION_REPEATABLE_READ;
            };
            return gpkg == null ? Database.openPostgres(pgUrl, schema, user, password, writable, isolation)
                    : Database.openGeoPackage(gpkg, writable);
        }
    }
}
