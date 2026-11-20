package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.GeometryType;
import guru.interlis.model.ModelImport;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.NumericType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.db.DbMapping.metaText;

/** Read and plan a new schema before opening a writable database. */
record SchemaPlan(DbMapping mapping, String filename, String modelNames, String content,
                  List<ColumnProperty> columnProperties, Map<String, GeometryColumn> geometryColumns) {
    record ColumnProperty(String table, String subtype, String column, String key, String value) { }

    /** A mapped geometry and its SQL catalog bounds, with null for an absent bound. */
    record GeometryColumn(DbMapping.Column column, Double minX, Double minY, Double maxX, Double maxY) { }

    SchemaPlan {
        columnProperties = List.copyOf(columnProperties);
        geometryColumns = Map.copyOf(geometryColumns);
    }

    static SchemaPlan read(String modelName, Path file, boolean gpkg, CancellationToken token) throws IOException {
        token.throwIfCancellationRequested();
        String filename = file.getFileName().toString();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".ili") || filename.codePointCount(0, filename.length()) > 250) {
            throw new DbException("Expected an .ili model filename of at most 250 characters");
        }
        if (!Files.isRegularFile(file)) {
            throw new DbException("Expected a regular .ili model file: " + filename);
        }
        int maxBytes = 4 * Ili2dbLayout.MAX_MODEL_CHARACTERS;
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(maxBytes + 1);
        }
        if (bytes.length > maxBytes) throw new DbException("Model file exceeds the size limit");
        String content = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        if (content.codePointCount(0, content.length()) > Ili2dbLayout.MAX_MODEL_CHARACTERS) {
            throw new DbException("Model file exceeds the size limit");
        }
        var source = new SourceText(new SourceId(file.toUri().toString()), new SourceRevision(1), content);
        var mapping = DbMapping.planNew(modelName, source, gpkg, token);
        var model = (ModelSymbol) symbol(mapping, mapping.plan().rootModels().getFirst());
        var imports = model.imports().stream().map(ModelImport::importedModel).distinct().sorted().toList();
        String modelNames = imports.isEmpty() ? modelName : modelName + "{ " + String.join(" ", imports) + "}";
        var properties = new ArrayList<ColumnProperty>();
        var geometries = new HashMap<String, GeometryColumn>();
        for (var table : mapping.tables()) {
            token.throwIfCancellationRequested();
            for (var column : table.columns()) {
                addProperties(properties, geometries, mapping, table, column);
            }
        }
        return new SchemaPlan(mapping, filename, modelNames, content, properties, geometries);
    }

    private static TypeDescriptor type(DbMapping mapping, TypeRef ref) {
        return mapping.plan().type(ref).known().orElseThrow();
    }

    private static TypeDescriptor declaredType(DbMapping mapping, DbMapping.Column column) {
        var slot = column.slot();
        return slot.declaredType().equals(slot.effectiveType())
                ? column.effectiveType() : type(mapping, slot.declaredType());
    }

    private static Symbol symbol(DbMapping mapping, SymbolRef ref) {
        return mapping.plan().symbol(ref).known().orElseThrow();
    }

    private static String qualifiedName(DbMapping mapping, Symbol symbol) {
        String name = symbol.name().value();
        return symbol.owner().map(id -> qualifiedName(mapping, symbol(mapping,
                new SymbolRef(mapping.plan().snapshotId(), id))) + "." + name).orElse(name);
    }

    private static void addProperties(List<ColumnProperty> properties, Map<String, GeometryColumn> geometries,
                                      DbMapping mapping, DbMapping.Table table, DbMapping.Column column) {
        var effective = column.effectiveType();
        String typeKind = switch (column.kind()) {
            case TEXT -> ((TextType) effective).textKind().name();
            case BOOLEAN -> "BOOLEAN";
            case INTEGER -> "NUMERIC";
            case ENUMERATION -> "ENUM";
            case POINT -> "COORD";
            case POLYLINE, SURFACE, MULTISURFACE -> ((GeometryType) effective).geometryKind().name();
            case REFERENCE -> null;
        };
        if (typeKind != null) property(properties, table, null, column, "typeKind", typeKind);
        if (column.kind() == DbMapping.Kind.BOOLEAN || column.kind() == DbMapping.Kind.ENUMERATION) {
            if (declaredType(mapping, column) instanceof DomainReferenceType alias) {
                var ref = new SymbolRef(mapping.plan().snapshotId(), alias.targetDomain());
                String domain = qualifiedName(mapping, symbol(mapping, ref));
                property(properties, table, table.name(), column, "enumDomain", domain);
            } else if (column.kind() == DbMapping.Kind.BOOLEAN) {
                property(properties, table, table.name(), column, "enumDomain", "INTERLIS.BOOLEAN");
            }
        }
        if (effective instanceof TextType text && text.textKind() == TextKind.MTEXT) {
            property(properties, table, null, column, "textKind", "MTEXT");
        }
        if (effective instanceof NumericType number) {
            number.range().unit().ifPresent(id -> {
                var unit = symbol(mapping, new SymbolRef(mapping.plan().snapshotId(), id));
                property(properties, table, null, column, "unit", unit.header().name().value());
            });
        }
        column.target().ifPresent(target ->
                property(properties, table, null, column, "foreignKey", mapping.table(target).name()));
        if (column.kind().geometry()) {
            geometries.put(table.name(), geometryColumn(properties, mapping, table, column));
        }
        String caption = metaText(column.slot().attribute(), "ili2db.dispName");
        if (caption != null) property(properties, table, null, column, "dispName", caption);
    }

    String displayName(DbMapping.Table table) {
        return metaText(symbol(mapping, table.object().symbol()), "ili2db.dispName");
    }

    private static GeometryColumn geometryColumn(List<ColumnProperty> properties, DbMapping mapping,
                                                 DbMapping.Table table, DbMapping.Column column) {
        CoordinateType coordinates;
        if (column.effectiveType() instanceof CoordinateType point) {
            validatePointCrs(mapping, column, point);
            coordinates = point;
        } else {
            var domain = DbMapping.vertexDomain(mapping.plan(), (GeometryType) column.effectiveType());
            coordinates = (CoordinateType) type(mapping, new TypeRef(mapping.plan().snapshotId(), domain.typeId()));
        }
        property(properties, table, null, column, "geomType", column.kind().geometryType());
        property(properties, table, null, column, "srid", Integer.toString(column.srsId()));
        property(properties, table, null, column, "coordDimension", "2");
        var x = coordinates.axes().get(0);
        var y = coordinates.axes().get(1);
        Double minX = bound(properties, table, column, "c1Min", x.minimum());
        Double maxX = bound(properties, table, column, "c1Max", x.maximum());
        Double minY = bound(properties, table, column, "c2Min", y.minimum());
        Double maxY = bound(properties, table, column, "c2Max", y.maximum());
        return new GeometryColumn(column, minX, minY, maxX, maxY);
    }

    private static void validatePointCrs(DbMapping mapping, DbMapping.Column column, CoordinateType point) {
        Symbol declaration = column.slot().attribute();
        if (declaredType(mapping, column) instanceof DomainReferenceType alias) {
            var domain = (DomainSymbol) symbol(mapping, new SymbolRef(mapping.plan().snapshotId(), alias.targetDomain()));
            // The reference uses the default CRS when the immediate domain is another alias.
            if (type(mapping, new TypeRef(mapping.plan().snapshotId(), domain.typeId())) instanceof DomainReferenceType) {
                return;
            }
            declaration = domain;
        }
        DbMapping.validateGeometryCrs(mapping.plan(), point, declaration, "point", column.slot().name().declared().toString());
    }

    private static Double bound(List<ColumnProperty> properties, DbMapping.Table table, DbMapping.Column column,
                                String key, Optional<DecimalLiteral> limit) {
        if (limit.isEmpty()) return null;
        var value = limit.get();
        double extent = value.exactValue().doubleValue();
        if (!Double.isFinite(extent)) {
            throw new DbException("Coordinate bound exceeds DOUBLE storage at " + column.slot().name().declared());
        }
        int exponent = value.precision().exponent();
        String spelling = value.exactValue().scaleByPowerOfTen(-exponent).toPlainString();
        if (exponent != 0) spelling += "e" + exponent;
        property(properties, table, null, column, key, spelling);
        return extent;
    }

    private static void property(List<ColumnProperty> properties, DbMapping.Table table, String subtype,
                                 DbMapping.Column column, String key, String value) {
        properties.add(new ColumnProperty(table.name(), subtype, column.name(), Ili2dbLayout.PREFIX + key, value));
    }
}
