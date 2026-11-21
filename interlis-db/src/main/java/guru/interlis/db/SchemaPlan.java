package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DomainReferenceType;
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
import guru.interlis.transfer.AttributeSlot;

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
                  List<ColumnProperty> columnProperties, Map<String, List<GeometryColumn>> geometryColumns,
                  boolean splitGeometries, int defaultEpsg, boolean multiSrs) {
    record ColumnProperty(String table, String subtype, String column, String key, String value) { }

    /** A mapped geometry and its SQL catalog bounds, with null for an absent bound. */
    record GeometryColumn(DbMapping.Column column, Double minX, Double minY, Double maxX, Double maxY) { }

    SchemaPlan {
        columnProperties = List.copyOf(columnProperties);
        geometryColumns = geometryColumns.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    static SchemaPlan read(String modelName, Path file, boolean gpkg, boolean splitGeometries,
                           int defaultEpsg, CancellationToken token) throws IOException {
        return read(modelName, file, gpkg, splitGeometries, defaultEpsg, false, token);
    }

    static SchemaPlan read(String modelName, Path file, boolean gpkg, boolean splitGeometries,
                           int defaultEpsg, boolean multiSrs, CancellationToken token) throws IOException {
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
        var mapping = DbMapping.planNew(modelName, source, gpkg, splitGeometries, defaultEpsg, multiSrs, token);
        var model = (ModelSymbol) symbol(mapping, mapping.plan().rootModels().getFirst());
        var imports = model.imports().stream().map(ModelImport::importedModel).distinct().sorted().toList();
        String modelNames = imports.isEmpty() ? modelName : modelName + "{ " + String.join(" ", imports) + "}";
        var properties = new ArrayList<ColumnProperty>();
        var geometries = new HashMap<String, List<GeometryColumn>>();
        for (var table : mapping.tables()) {
            token.throwIfCancellationRequested();
            if (table.hasTidColumn()) {
                table.oidDomain().ifPresent(domain -> properties.add(new ColumnProperty(table.name(), null,
                        Ili2dbLayout.TID_COLUMN, Ili2dbLayout.OID_DOMAIN_PROPERTY, domain)));
            }
            for (var column : table.columns()) {
                addProperties(properties, geometries, mapping, table.name(), column);
            }
            for (var secondary : table.secondaryGeometries()) {
                addProperties(properties, geometries, mapping, secondary.table(), secondary.column());
                properties.add(new ColumnProperty(secondary.table(), null, Ili2dbLayout.ID_COLUMN,
                        Ili2dbLayout.PREFIX + "foreignKey", table.name()));
            }
        }
        for (var composition : mapping.compositions()) {
            token.throwIfCancellationRequested();
            var child = mapping.table(composition.childRoot());
            var parent = mapping.table(composition.parent());
            properties.add(new ColumnProperty(child.name(), null, composition.ownerColumn(),
                    Ili2dbLayout.PREFIX + "foreignKey", parent.name()));
        }
        return new SchemaPlan(mapping, filename, modelNames, content, properties, geometries, splitGeometries, defaultEpsg, multiSrs);
    }

    List<Integer> geometryEpsgCodes() {
        return geometryColumns.values().stream().flatMap(List::stream)
                .map(geometry -> geometry.column().epsgCode()).distinct().sorted().toList();
    }

    private static TypeDescriptor type(DbMapping mapping, TypeRef ref) {
        return mapping.plan().type(ref).known().orElseThrow();
    }

    private static TypeDescriptor declaredType(DbMapping mapping, DbMapping.Column column) {
        var slot = (AttributeSlot) column.slot();
        return slot.declaredType().equals(slot.effectiveType())
                ? column.effectiveType().orElseThrow() : type(mapping, slot.declaredType());
    }

    private static Symbol symbol(DbMapping mapping, SymbolRef ref) {
        return mapping.plan().symbol(ref).known().orElseThrow();
    }

    private static String qualifiedName(DbMapping mapping, Symbol symbol) {
        String name = symbol.name().value();
        return symbol.owner().map(id -> qualifiedName(mapping, symbol(mapping,
                new SymbolRef(mapping.plan().snapshotId(), id))) + "." + name).orElse(name);
    }

    private static void addProperties(List<ColumnProperty> properties, Map<String, List<GeometryColumn>> geometries,
                                      DbMapping mapping, String table, DbMapping.Column column) {
        column.target().ifPresent(target ->
                property(properties, table, null, column, "foreignKey", mapping.table(target).name()));
        if (!(column.slot() instanceof AttributeSlot attribute)) return;

        var effective = column.effectiveType().orElseThrow();
        String typeKind = switch (column.kind()) {
            case TEXT -> ((TextType) effective).textKind().name();
            case BOOLEAN -> "BOOLEAN";
            case INTEGER -> "NUMERIC";
            case ENUMERATION -> "ENUM";
            case POINT -> "COORD";
            case MULTIPOINT -> "MULTICOORD";
            case POLYLINE, SURFACE, MULTICURVE, MULTISURFACE -> ((GeometryType) effective).geometryKind().name();
            case REFERENCE -> null;
        };
        if (typeKind != null) property(properties, table, null, column, "typeKind", typeKind);
        if (column.kind() == DbMapping.Kind.BOOLEAN || column.kind() == DbMapping.Kind.ENUMERATION) {
            if (declaredType(mapping, column) instanceof DomainReferenceType alias) {
                var ref = new SymbolRef(mapping.plan().snapshotId(), alias.targetDomain());
                String domain = qualifiedName(mapping, symbol(mapping, ref));
                property(properties, table, table, column, "enumDomain", domain);
            } else if (column.kind() == DbMapping.Kind.BOOLEAN) {
                property(properties, table, table, column, "enumDomain", "INTERLIS.BOOLEAN");
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
        if (column.kind().geometry()) {
            geometries.computeIfAbsent(table, ignored -> new ArrayList<>())
                    .add(geometryColumn(properties, mapping, table, column));
        }
        String caption = metaText(attribute.attribute(), "ili2db.dispName");
        if (caption != null) property(properties, table, null, column, "dispName", caption);
    }

    String displayName(DbMapping.Table table) {
        return metaText(symbol(mapping, table.object().symbol()), "ili2db.dispName");
    }

    private static GeometryColumn geometryColumn(List<ColumnProperty> properties, DbMapping mapping,
                                                 String table, DbMapping.Column column) {
        CoordinateType coordinates;
        var effective = column.effectiveType().orElseThrow();
        if (effective instanceof CoordinateType coordinateType) {
            coordinates = coordinateType;
        } else {
            var domain = column.domainBinding().map(binding -> DbDomains.domain(mapping.plan(), binding.concrete().symbol()))
                    .orElseGet(() -> DbMapping.vertexDomain(mapping.plan(), (GeometryType) effective));
            coordinates = (CoordinateType) type(mapping, new TypeRef(mapping.plan().snapshotId(), domain.typeId()));
        }
        property(properties, table, null, column, "geomType", column.kind().geometryType());
        property(properties, table, null, column, Ili2dbLayout.SRID_PROPERTY, Integer.toString(column.epsgCode()));
        property(properties, table, null, column, "coordDimension", Integer.toString(column.dimensions()));
        var x = coordinates.axes().get(0);
        var y = coordinates.axes().get(1);
        Double minX = bound(properties, table, column, "c1Min", x.minimum());
        Double maxX = bound(properties, table, column, "c1Max", x.maximum());
        Double minY = bound(properties, table, column, "c2Min", y.minimum());
        Double maxY = bound(properties, table, column, "c2Max", y.maximum());
        if (column.dimensions() == 3) {
            var z = coordinates.axes().get(2);
            bound(properties, table, column, "c3Min", z.minimum());
            bound(properties, table, column, "c3Max", z.maximum());
        }
        return new GeometryColumn(column, minX, minY, maxX, maxY);
    }

    private static Double bound(List<ColumnProperty> properties, String table, DbMapping.Column column,
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

    private static void property(List<ColumnProperty> properties, String table, String subtype,
                                 DbMapping.Column column, String key, String value) {
        properties.add(new ColumnProperty(table, subtype, column.name(), Ili2dbLayout.PREFIX + key, value));
    }
}
