package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class Main {
    private static final String USAGE = """
            Usage: interlis-db export --model NAME [--model NAME]... [--export-model NAME]
                    [--dataset NAME | --basket BID [--basket BID]... | --topic MODEL.TOPIC [--topic MODEL.TOPIC]...]
                    (--gpkg FILE | --pg-url JDBC_URL --schema NAME --user NAME) FILE.xtf
                   interlis-db import --model NAME [--model NAME]... [--dataset NAME] [--raw-scalar-text]
                    (--gpkg FILE | --pg-url JDBC_URL --schema NAME --user NAME) FILE.xtf
                   interlis-db <replace|update> --model NAME [--model NAME]... --dataset NAME
                    (--gpkg FILE | --pg-url JDBC_URL --schema NAME --user NAME) FILE.xtf
                   interlis-db delete --model NAME [--model NAME]... --dataset NAME
                    (--gpkg FILE | --pg-url JDBC_URL --schema NAME --user NAME)
                   interlis-db schemaimport --model NAME [--model NAME]...
                    [--add-models] [--create-basket-col] [--create-tid-col]
                    [--default-epsg CODE] [--multi-srs [--alt-srs-model ORIGINAL=ALTERNATE]]
                    [--model-file IMPORT.ili]...
                    [--inheritance smart1|smart2] [--coalesce-catalogue-ref] [--expand-multilingual] [--expand-localised]
                    [--coalesce-multi-point] [--coalesce-multi-line] [--coalesce-multi-surface] [--expand-struct] [--coalesce-json] [--coalesce-array]
                    [--create-enum-col-as-itf-code] [--create-enum-txt-col] [--beautify-enum-disp-name]
                    [--create-single-enum-tab | --create-enum-tabs | --create-enum-tabs-with-id]
                    [--create-fk] [--create-fk-idx] [--sql-ext-ref-cols] [--sql-cols-as-text]
                    [--create-type-discriminator] [--create-type-constraint] [--create-dataset-col] [--create-std-cols]
                    [--create-unique] [--create-num-checks] [--create-text-checks] [--create-date-time-checks]
                    [--sql-not-null] [--create-mandatory-checks] [--create-meta-info]
                    [--stroke-arcs] [--create-geom-index] [--one-geom-per-table | --gpkg-multi-geom-per-table]
                    (--gpkg FILE | --pg-url JDBC_URL --schema NAME --user NAME) MODEL.ili
            Use --dataset for transfers against schemas with T_basket. Export also accepts --basket or --topic.
            --create-tid-col preserves ordinary transfer IDs. Concrete object OIDs are always stored.
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
        if (options.addModels()) {
            try (var database = options.open(password)) {
                appendModels(database, options, cancellation);
                cancellation.throwIfCancellationRequested();
                database.commit();
            }
            return;
        }
        var plan = SchemaPlan.read(options.models(), options.file(), options.modelFiles(), options.gpkg() != null,
                options.splitGeometries(), options.defaultEpsg(), options.multiSrs(), options.srsModels(), new SqlNames(),
                options.inheritance(), options.transformations(), options.strokeArcs(), options.enumOptions(),
                options.schemaOptions(), options.sqlNotNull(), cancellation);
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

    private static void appendModels(Database db, Options options, CancellationToken cancellation)
            throws IOException, SQLException {
        if (!db.gpkg()) {
            String tables = Ili2dbLayout.METADATA_TABLES.stream().map(db::technicalTable).sorted().collect(Collectors.joining(", "));
            try (var statement = db.connection().createStatement()) {
                statement.execute("LOCK TABLE " + tables + " IN SHARE ROW EXCLUSIVE MODE NOWAIT");
            }
        }
        var settings = DbSchemaReader.readSettings(db, options.models());
        boolean smart2 = settings.inheritance() == DbInheritance.Profile.SMART2 && settings.sqlEnableNull();
        if ((!smart2 && settings.inheritance() != DbInheritance.Profile.NEW_CLASS) || settings.defaultEpsg() == 0
                || (options.explicitEpsg() && options.defaultEpsg() != settings.defaultEpsg())
                || (options.explicitLayout() && options.splitGeometries() != settings.splitGeometries())
                || (options.multiSrs() && !settings.multiSrs())
                || (options.createGeometryIndexes() && !settings.createGeometryIndexes())
                || !settings.schemaOptions().containsAll(options.schemaOptions())) {
            throw new DbException("Schema options conflict with the existing ili2db SETTINGS");
        }
        var names = DbSchemaReader.namesForExtension(db, options.models());
        var plan = SchemaPlan.read(options.models(), options.file(), options.modelFiles(), db.gpkg(),
                settings.splitGeometries(), settings.defaultEpsg(), settings.multiSrs(), Optional.empty(), names,
                settings.inheritance(), settings.structureTransformations(), settings.strokeArcs(), settings.enumOptions(),
                settings.schemaOptions(), !settings.sqlEnableNull(), cancellation);
        var newFiles = DbSchemaReader.newModelFiles(db, plan.modelFiles(), cancellation);
        if (smart2) {
            for (var declaration : plan.mapping().inheritance().declarations()) {
                String path = declaration.object().name().declared().toString();
                if (names.hasClass(path)) {
                    throw new DbException("Smart2 --add-models cannot yet reuse the stored mapping for " + path);
                }
            }
        }
        plan = plan.withModelFiles(newFiles);
        if (plan.addedTables().isEmpty() && plan.modelFiles().isEmpty()) {
            throw new DbException("No new model tables or sources to add");
        }
        if (db.gpkg()) GeoPackageSchema.addModels(db, plan, settings.createGeometryIndexes(), cancellation);
        else SchemaSql.addTables(db, plan, settings.createGeometryIndexes(), cancellation);

        var transfer = plan.mapping().plan();
        var byVersion = transfer.rootModels().stream().map(model -> transfer.model(model).orElseThrow())
                .collect(Collectors.groupingBy(model -> model.languageVersion(),
                        Collectors.mapping(model -> model.name().declared().last(), Collectors.toList())));
        for (var models : byVersion.values()) DbSchemaReader.read(db, models, cancellation);
    }

    private static void importData(Options options, String password, CancellationToken cancellation)
            throws IOException, SQLException {
        try (var database = options.open(password)) {
            var mapping = DbSchemaReader.read(database, options.models(), cancellation);
            checkTransferOptions(mapping, options);
            var rawSlots = options.rawScalarText() ? DbScalarText.rawSlots(mapping) : Set.<SymbolRef>of();
            try (var input = Files.newInputStream(options.file());
                 TransferReader reader = switch (mapping.version()) {
                     case ILI23 -> Xtf23Reader.forIli2db(mapping.plan(), input, rawSlots);
                     case ILI24 -> Xtf24Reader.forIli2db(mapping.plan(), input, rawSlots);
                 }) {
                var mode = switch (options.command()) {
                    case REPLACE -> DbTransfer.WriteMode.REPLACE;
                    case UPDATE -> DbTransfer.WriteMode.UPDATE;
                    default -> DbTransfer.WriteMode.IMPORT;
                };
                DbTransfer.importData(database, mapping, options.dataset(), reader, mode, options.rawScalarText(), cancellation);
            }
            cancellation.throwIfCancellationRequested();
            database.commit();
        }
    }

    private static void deleteData(Options options, String password, CancellationToken cancellation) throws SQLException {
        try (var database = options.open(password)) {
            var mapping = DbSchemaReader.read(database, options.models(), cancellation);
            checkTransferOptions(mapping, options);
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
                var mapping = DbSchemaReader.read(database, options.models(), options.exportModel(), cancellation);
                checkTransferOptions(mapping, options);
                try (var output = Files.newOutputStream(temporary);
                     TransferWriter writer = switch (mapping.version()) {
                         case ILI23 -> Xtf23Writer.forIli2db(mapping.plan(), output);
                         case ILI24 -> new Xtf24Writer(mapping.plan(), output);
                     }) {
                    DbTransfer.exportData(database, mapping, options.dataset(), options.baskets(), options.topics(),
                            options.exportModel(), writer, cancellation);
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

    private static void checkTransferOptions(DbMapping mapping, Options options) {
        if (!mapping.hasBasketColumn() && options.dataset() != null) {
            throw new DbException("Dataset operations require a schema created with --create-basket-col");
        }
        boolean filtered = !options.baskets().isEmpty() || !options.topics().isEmpty();
        if (filtered && !mapping.hasBasketColumn()) {
            throw new DbException("Basket or topic export requires a schema created with --create-basket-col");
        }
        if (mapping.hasBasketColumn() && options.dataset() == null && !filtered) {
            if (options.command() == Command.EXPORT) throw new DbException("Choose --dataset, --basket or --topic for export with T_basket");
            throw new DbException("--dataset is required for schemas with T_basket");
        }
        if (options.command() == Command.UPDATE && !mapping.storesTransferTids()) {
            throw new DbException("update requires a schema created with --create-tid-col");
        }
    }

    private record Options(Command command, List<String> models, String dataset, Path file, Path gpkg,
                           String pgUrl, String schema, String user, boolean createGeometryIndexes,
                           boolean splitGeometries, int defaultEpsg, boolean multiSrs, boolean addModels,
                           boolean explicitEpsg, boolean explicitLayout, boolean strokeArcs, DbEnumOptions enumOptions,
                           Optional<DbCrsPair.Models> srsModels, List<Path> modelFiles, Optional<String> exportModel,
                           List<String> baskets, List<String> topics,
                           DbInheritance.Profile inheritance, Set<Ili2dbLayout.AttributeTransformation> transformations,
                           Set<Ili2dbLayout.SchemaOption> schemaOptions, boolean rawScalarText, boolean sqlNotNull) {
        private static final Set<String> NAMES = Set.of("--dataset", "--gpkg", "--pg-url", "--schema", "--user",
                "--default-epsg", "--alt-srs-model", "--export-model", "--inheritance");

        private static final Set<String> ENUM_OPTIONS = Set.of("--create-enum-col-as-itf-code",
                "--create-enum-txt-col", "--beautify-enum-disp-name", "--create-single-enum-tab", "--create-enum-tabs", "--create-enum-tabs-with-id");

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
            var transformations = EnumSet.noneOf(Ili2dbLayout.AttributeTransformation.class);
            var schemaOptions = EnumSet.noneOf(Ili2dbLayout.SchemaOption.class);
            var modelFiles = new ArrayList<Path>();
            var models = new ArrayList<String>();
            var baskets = new ArrayList<String>();
            var topics = new ArrayList<String>();
            boolean rawScalarText = false;
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                var transformation = Ili2dbLayout.AttributeTransformation.fromOption(arg);
                var schemaOption = Ili2dbLayout.SchemaOption.fromOption(arg);
                if (arg.equals("--raw-scalar-text")) {
                    if (command != Command.IMPORT) throw new IllegalArgumentException(arg + " is only valid with import");
                    if (rawScalarText) throw new IllegalArgumentException("Repeated option " + arg);
                    rawScalarText = true;
                } else if (arg.equals("--stroke-arcs") || arg.equals("--create-geom-index") || arg.equals("--one-geom-per-table")
                        || arg.equals("--gpkg-multi-geom-per-table") || arg.equals("--multi-srs") || arg.equals("--add-models")
                        || arg.equals("--sql-not-null") || ENUM_OPTIONS.contains(arg) || transformation != null || schemaOption != null) {
                    if (command != Command.SCHEMAIMPORT) {
                        throw new IllegalArgumentException(arg + " is only valid with schemaimport");
                    }
                    if (!schemaFlags.add(arg)) throw new IllegalArgumentException("Repeated option " + arg);
                    if (transformation != null) transformations.add(transformation);
                    if (schemaOption != null) schemaOptions.add(schemaOption);
                } else if (arg.equals("--model")) {
                    if (++i == args.length || args[i].isBlank() || args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for " + arg);
                    }
                    if (models.contains(args[i])) throw new IllegalArgumentException("Repeated model " + args[i]);
                    models.add(args[i]);
                } else if (arg.equals("--model-file")) {
                    if (command != Command.SCHEMAIMPORT) {
                        throw new IllegalArgumentException(arg + " is only valid with schemaimport");
                    }
                    if (++i == args.length || args[i].isBlank() || args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for " + arg);
                    }
                    var source = Path.of(args[i]).toAbsolutePath().normalize();
                    if (modelFiles.contains(source)) throw new IllegalArgumentException("Repeated model file " + source);
                    modelFiles.add(source);
                } else if (arg.equals("--basket") || arg.equals("--topic")) {
                    if (command != Command.EXPORT) throw new IllegalArgumentException(arg + " is only valid with export");
                    if (++i == args.length || args[i].isBlank() || args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for " + arg);
                    }
                    var selection = arg.equals("--basket") ? baskets : topics;
                    if (selection.contains(args[i])) throw new IllegalArgumentException("Repeated " + arg + " value " + args[i]);
                    selection.add(args[i]);
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
            String dataset = flags.get("--dataset");
            if ((dataset != null && (!baskets.isEmpty() || !topics.isEmpty())) || (!baskets.isEmpty() && !topics.isEmpty())) {
                throw new IllegalArgumentException("Choose one of --dataset, --basket or --topic");
            }
            var exportModel = Optional.ofNullable(flags.get("--export-model"));
            if (exportModel.isPresent() && command != Command.EXPORT) {
                throw new IllegalArgumentException("--export-model is only valid with export");
            }
            if (command == Command.SCHEMAIMPORT) {
                if (file == null || file.getParent() == null || models.isEmpty()) {
                    throw new IllegalArgumentException("An .ili path and --model are required");
                }
                if (dataset != null) throw new IllegalArgumentException("--dataset is not valid with schemaimport");
                if (modelFiles.contains(file)) throw new IllegalArgumentException("Repeated model file " + file);
            } else if (command == Command.DELETE) {
                if (file != null || models.isEmpty() || dataset == null) {
                    throw new IllegalArgumentException("delete requires --model and --dataset without a file");
                }
            } else if (file == null || file.getParent() == null || models.isEmpty()) {
                throw new IllegalArgumentException("An XTF path and --model are required");
            } else if ((command == Command.UPDATE || command == Command.REPLACE) && dataset == null) {
                throw new IllegalArgumentException("update and replace require --dataset");
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
            boolean multiSrs = schemaFlags.contains("--multi-srs");
            String alternateSrsModel = flags.get("--alt-srs-model");
            if (alternateSrsModel != null && (command != Command.SCHEMAIMPORT || !multiSrs)) {
                throw new IllegalArgumentException("--alt-srs-model requires schemaimport --multi-srs");
            }
            if (alternateSrsModel != null && models.size() != 1) {
                throw new IllegalArgumentException("--alt-srs-model requires one --model");
            }
            boolean addModels = schemaFlags.contains("--add-models");
            if (addModels && alternateSrsModel != null) {
                throw new IllegalArgumentException("--add-models uses the existing schema's CRS settings");
            }
            String inheritanceOption = flags.get("--inheritance");
            if (inheritanceOption != null && (command != Command.SCHEMAIMPORT || addModels)) {
                throw new IllegalArgumentException("--inheritance requires creation of a new schema");
            }
            var inheritance = switch (inheritanceOption) {
                case null -> DbInheritance.Profile.NEW_CLASS;
                case "smart1" -> DbInheritance.Profile.SMART1;
                case "smart2" -> DbInheritance.Profile.SMART2;
                default -> throw new IllegalArgumentException("Expected --inheritance smart1 or smart2");
            };
            boolean sqlNotNull = schemaFlags.contains("--sql-not-null");
            if (sqlNotNull && (addModels || inheritance != DbInheritance.Profile.NEW_CLASS)) {
                throw new IllegalArgumentException("--sql-not-null requires a new schema with newClass inheritance");
            }
            if (sqlNotNull && schemaOptions.contains(Ili2dbLayout.SchemaOption.MANDATORY_CHECKS)) {
                throw new IllegalArgumentException("--sql-not-null cannot be combined with --create-mandatory-checks");
            }
            if (!transformations.isEmpty() && (addModels || inheritance == DbInheritance.Profile.SMART1)) {
                throw new IllegalArgumentException(transformations.iterator().next().option
                        + " requires a new schema with newClass or Smart2 inheritance");
            }
            if (transformations.contains(Ili2dbLayout.AttributeTransformation.JSON_STRUCTURE)
                    && inheritance != DbInheritance.Profile.NEW_CLASS) {
                throw new IllegalArgumentException("--coalesce-json requires a new schema with newClass inheritance");
            }
            boolean strokeArcs = schemaFlags.contains("--stroke-arcs");
            if (strokeArcs && (addModels || inheritance == DbInheritance.Profile.SMART1)) {
                throw new IllegalArgumentException("--stroke-arcs requires a new schema with newClass or Smart2 inheritance");
            }
            if (addModels && schemaFlags.stream().anyMatch(ENUM_OPTIONS::contains)) {
                throw new IllegalArgumentException("--add-models uses the existing schema's enumeration settings");
            }
            var enumLookup = DbEnumOptions.Lookup.NONE;
            for (var mode : DbEnumOptions.Lookup.values()) {
                if (mode.option == null || !schemaFlags.contains(mode.option)) continue;
                if (enumLookup != DbEnumOptions.Lookup.NONE) {
                    throw new IllegalArgumentException("Choose one enumeration lookup table option");
                }
                enumLookup = mode;
            }
            var enumOptions = new DbEnumOptions(schemaFlags.contains("--create-enum-col-as-itf-code"),
                    schemaFlags.contains("--create-enum-txt-col"), schemaFlags.contains("--beautify-enum-disp-name"),
                    enumLookup);
            var srsModels = Optional.ofNullable(alternateSrsModel).map(value -> DbCrsPair.Models.parse(value, models.getFirst()));
            return new Options(command, List.copyOf(models), dataset, file, gpkg, pgUrl, schema, user,
                    schemaFlags.contains("--create-geom-index"),
                    oneGeometryPerTable || (gpkg != null && !multipleGeoPackageGeometries), defaultEpsg, multiSrs, addModels,
                    epsg != null, oneGeometryPerTable || multipleGeoPackageGeometries, strokeArcs, enumOptions, srsModels,
                    List.copyOf(modelFiles), exportModel, List.copyOf(baskets), List.copyOf(topics), inheritance, Set.copyOf(transformations),
                    Set.copyOf(schemaOptions), rawScalarText, sqlNotNull);
        }

        void checkPaths() throws IOException {
            if (gpkg != null && (command != Command.SCHEMAIMPORT || addModels)) {
                if (!Files.isRegularFile(gpkg)) {
                    throw new DbException("Expected an existing GeoPackage file");
                }
                if (command != Command.SCHEMAIMPORT && file != null
                        && (file.equals(gpkg) || (Files.exists(file) && Files.isSameFile(file, gpkg)))) {
                    throw new DbException("The XTF and GeoPackage paths must identify different files");
                }
            }
        }

        Database open(String password) throws SQLException {
            boolean writable = command != Command.EXPORT;
            int isolation = switch (command) {
                case REPLACE, UPDATE, DELETE -> Connection.TRANSACTION_READ_COMMITTED;
                default -> addModels ? Connection.TRANSACTION_READ_COMMITTED : Connection.TRANSACTION_REPEATABLE_READ;
            };
            return gpkg == null ? Database.openPostgres(pgUrl, schema, user, password, writable, isolation)
                    : Database.openGeoPackage(gpkg, writable);
        }
    }
}
