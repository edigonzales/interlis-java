package guru.interlis.db;

import guru.interlis.db.Ili2dbLayout.AttributeTransformation;
import guru.interlis.db.Ili2dbLayout.SchemaOption;
import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.ExpressionRef;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Modifier;
import guru.interlis.model.ModelImport;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.ModelTranslation;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathStep;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.model.UniquenessConstraint;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static guru.interlis.db.DbMapping.metaText;

/** Immutable SQL definitions and metadata for the tables to create. */
record SchemaPlan(DbMapping mapping, List<ModelFile> modelFiles,
                  List<DbMapping.Table> addedTables, List<DbMapping.EmbeddedAssociation> addedAssociations,
                  List<DbMapping.Composition> addedCompositions,
                  List<ColumnProperty> columnProperties, Map<String, List<GeometryColumn>> geometryColumns,
                  boolean splitGeometries, int defaultEpsg, boolean multiSrs, boolean strokeArcs, DbEnumOptions enumOptions,
                  Set<AttributeTransformation> transformations, List<DbEnumLookup> enumLookups,
                  Map<String, List<List<String>>> uniqueKeys) {
    record ModelFile(String filename, LanguageVersion version, String modelNames, String content) { }

    record ColumnProperty(String table, String subtype, String column, String key, String value) { }

    record ForeignKey(String table, String column, String target, boolean index) {
        ForeignKey(String table, String column, String target) { this(table, column, target, true); }
    }

    /** A mapped geometry and its SQL catalog bounds, with null for an absent bound. */
    record GeometryColumn(DbMapping.Column column, Double minX, Double minY, Double maxX, Double maxY) { }

    SchemaPlan {
        modelFiles = List.copyOf(modelFiles);
        addedTables = List.copyOf(addedTables);
        addedAssociations = List.copyOf(addedAssociations);
        addedCompositions = List.copyOf(addedCompositions);
        columnProperties = List.copyOf(columnProperties);
        transformations = Set.copyOf(transformations);
        enumLookups = List.copyOf(enumLookups);
        geometryColumns = geometryColumns.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    static SchemaPlan read(String modelName, Path file, boolean gpkg, boolean splitGeometries,
                           int defaultEpsg, CancellationToken token) throws IOException {
        return read(List.of(modelName), file, List.of(), gpkg, splitGeometries, defaultEpsg, false, Optional.empty(),
                new SqlNames(), DbInheritance.Profile.NEW_CLASS, Set.of(), false, DbEnumOptions.DEFAULT, Set.of(), token);
    }

    static SchemaPlan read(List<String> models, Path file, List<Path> imports, boolean gpkg, boolean splitGeometries,
                           int defaultEpsg, boolean multiSrs, Optional<DbCrsPair.Models> srsModels, SqlNames names,
                           DbInheritance.Profile inheritance, Set<AttributeTransformation> transformations, boolean strokeArcs,
                           DbEnumOptions enumOptions, Set<SchemaOption> schemaOptions, CancellationToken token)
            throws IOException {
        var files = new ArrayList<Path>();
        files.add(file);
        files.addAll(imports);
        var sources = new LinkedHashMap<SourceId, SourceText>();
        var filenames = new HashMap<SourceId, String>();
        long totalLength = 0;
        for (var path : files) {
            token.throwIfCancellationRequested();
            var source = readSource(path);
            totalLength += source.text().length();
            if (totalLength > AnalysisBudget.defaults().maxTotalSourceUtf16Length()) {
                throw new DbException("Model file closure exceeds the size limit");
            }
            sources.put(source.id(), source);
            filenames.put(source.id(), path.getFileName().toString());
        }
        var inputModels = srsModels.map(DbCrsPair.Models::names).orElse(models);
        var compiled = DbModelSources.compile(inputModels, sources.values(), "model files", token);
        var mapping = DbMapping.planNew(compiled.plan(), gpkg, splitGeometries, defaultEpsg, multiSrs,
                srsModels, names, inheritance, transformations, strokeArcs, enumOptions, schemaOptions, token);
        var declarations = mapping.plan().models().stream()
                .map(model -> (ModelSymbol) symbol(mapping, model.symbol()))
                .collect(Collectors.groupingBy(model -> origin(model).range().source()));
        var modelFiles = new ArrayList<ModelFile>();
        for (var source : sources.values()) {
            var fileModels = declarations.get(source.id());
            if (fileModels == null) continue;
            String modelEntries = fileModels.stream().sorted(Comparator.comparingInt(model -> origin(model).range().startOffset()))
                    .map(SchemaPlan::modelEntry).collect(Collectors.joining(" "));
            modelFiles.add(new ModelFile(filenames.get(source.id()), fileModels.getFirst().languageVersion(),
                    modelEntries, source.text()));
        }
        var addedTables = mapping.tables().stream()
                .filter(table -> !names.hasClass(table.object().name().declared().toString())).toList();
        var addedAssociations = mapping.associations().stream().filter(association -> !association.linkTable()
                && !names.hasClass(association.object().name().declared().toString())).toList();
        var addedCompositions = mapping.compositions().stream()
                .filter(composition -> !names.hasAttribute(composition.slot().name().declared().toString())).toList();
        var properties = new ArrayList<ColumnProperty>();
        var geometries = new HashMap<String, List<GeometryColumn>>();
        var uniqueKeys = new HashMap<String, List<List<String>>>();
        Map<SymbolId, List<ConstraintSymbol>> constraints = schemaOptions.contains(SchemaOption.UNIQUE)
                ? uniqueConstraints(mapping) : Map.of();
        for (var table : addedTables) {
            token.throwIfCancellationRequested();
            if (table.hasTidColumn()) {
                table.oidDomain().ifPresent(domain -> properties.add(new ColumnProperty(table.name(), null,
                        Ili2dbLayout.TID_COLUMN, Ili2dbLayout.OID_DOMAIN_PROPERTY, domain)));
            }
            for (var column : table.columns()) {
                addProperties(properties, geometries, mapping, table, table.name(), column);
            }
            for (var secondary : table.secondaryGeometries()) {
                addProperties(properties, geometries, mapping, table, secondary.table(), secondary.column());
                properties.add(new ColumnProperty(secondary.table(), null, Ili2dbLayout.ID_COLUMN,
                        Ili2dbLayout.PREFIX + "foreignKey", table.name()));
            }
            var rules = constraints.get(table.object().symbol().symbol());
            if (rules != null) uniqueKeys.put(table.name(), uniqueKeys(mapping, table, rules));
        }
        for (var composition : addedCompositions) {
            token.throwIfCancellationRequested();
            var child = mapping.table(composition.childRoot());
            var parent = mapping.table(composition.parent());
            properties.add(new ColumnProperty(child.name(), null, composition.ownerColumn(),
                    Ili2dbLayout.PREFIX + "foreignKey", parent.name()));
        }
        return new SchemaPlan(mapping, modelFiles, addedTables, addedAssociations, addedCompositions, properties, geometries,
                splitGeometries, defaultEpsg, multiSrs, strokeArcs, enumOptions, transformations,
                DbEnumLookup.plan(mapping, names, enumOptions, compiled.syntax(), token), Map.copyOf(uniqueKeys));
    }

    List<String> addedStorageTables() { return DbMapping.storageTables(addedTables); }

    Set<SchemaOption> schemaOptions() { return mapping.schemaOptions(); }

    private static Map<SymbolId, List<ConstraintSymbol>> uniqueConstraints(DbMapping mapping) {
        var constraints = new HashMap<SymbolId, List<ConstraintSymbol>>();
        for (var symbol : mapping.plan().schema().semanticGraph().declarationsInOrder()) {
            if (symbol instanceof ConstraintSymbol constraint && constraint.definition() instanceof UniquenessConstraint) {
                constraint.target().or(constraint.header()::owner).ifPresent(owner ->
                        constraints.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(constraint));
            }
        }
        return constraints;
    }

    private static List<List<String>> uniqueKeys(DbMapping mapping, DbMapping.Table table, List<ConstraintSymbol> constraints) {
        if (table.object().kind() == SymbolKind.STRUCTURE) return List.of();
        var keys = new ArrayList<List<String>>();
        for (var constraint : constraints) {
            var rule = (UniquenessConstraint) constraint.definition();
            if (rule.where().isPresent() || rule.keys().isEmpty()) continue;
            var columns = rule.keys().stream().map(expression -> uniqueColumn(mapping, table, expression)).toList();
            if (columns.stream().anyMatch(java.util.Objects::isNull)) continue;
            var key = new ArrayList<>(columns);
            if (rule.basket()) key.add(Ili2dbLayout.T_BASKET);
            keys.add(List.copyOf(key));
        }
        return List.copyOf(keys);
    }

    private static String uniqueColumn(DbMapping mapping, DbMapping.Table table, ExpressionId id) {
        var expression = mapping.plan().schema().queries().expression(new ExpressionRef(mapping.plan().snapshotId(), id))
                .known().orElseThrow();
        if (!(expression instanceof PathExpression path) || path.path().steps().size() != 1) return null;
        var target = switch (path.path().steps().getFirst()) {
            case PathStep.AttributeStep attribute -> attribute.target();
            case PathStep.RoleStep role -> role.target();
            default -> null;
        };
        if (target == null) return null;
        var columns = table.columns().stream().filter(column -> column.slot().symbol().symbol().equals(target)).toList();
        if (columns.size() != 1) return null;
        var column = columns.getFirst();
        if (column.array().isPresent() || column.jsonStructure().isPresent() || column.expandedStructure().isPresent()
                || column.textExpansion().isPresent() || column.kind().geometry() || column.kind() == DbMapping.Kind.XML) return null;
        return column.name();
    }

    List<String> discriminatorTypes(DbMapping.Table table) {
        return mapping.objectStorage().stream()
                .filter(type -> type.root().equals(table) && !type.object().modifiers().contains(Modifier.ABSTRACT))
                .map(DbMapping.ObjectStorage::discriminator).sorted().toList();
    }

    List<ForeignKey> foreignKeys(boolean newSchema) {
        var keys = new ArrayList<ForeignKey>();
        if (newSchema) keys.add(new ForeignKey(Ili2dbLayout.BASKET, "dataset", Ili2dbLayout.DATASET));
        for (var table : addedTables) {
            table.baseTable().ifPresent(base -> keys.add(new ForeignKey(table.name(), Ili2dbLayout.ID_COLUMN,
                    mapping.table(base).name(), false)));
            keys.add(new ForeignKey(table.name(), Ili2dbLayout.BASKET_COLUMN, Ili2dbLayout.BASKET));
            for (var column : table.columns()) {
                if (column.target().isPresent()) {
                    keys.add(new ForeignKey(table.name(), column.name(), mapping.table(column.targetRelation()).name()));
                }
            }
            for (var geometry : table.secondaryGeometries()) {
                keys.add(new ForeignKey(geometry.table(), Ili2dbLayout.ID_COLUMN, table.name(), false));
                keys.add(new ForeignKey(geometry.table(), Ili2dbLayout.BASKET_COLUMN, Ili2dbLayout.BASKET));
            }
        }
        for (var composition : addedCompositions) {
            keys.add(new ForeignKey(mapping.table(composition.childRoot()).name(), composition.ownerColumn(),
                    mapping.table(composition.parent()).name()));
        }
        return List.copyOf(keys);
    }

    List<String> linkOwnerColumns(DbMapping.Table table) {
        return mapping.associations().stream().filter(link -> link.linkTable() && link.object().symbol().equals(table.object().symbol()))
                .map(link -> table.column(link.ownerRole().slot().symbol()).name()).toList();
    }

    String tableKind(DbMapping.Table table) {
        return DbCatalogueReference.isCatalogueItem(mapping.plan(), table.object())
                ? "CATALOGUE" : table.object().kind().name();
    }

    SchemaPlan withModelFiles(List<ModelFile> files) {
        return new SchemaPlan(mapping, files, addedTables, addedAssociations, addedCompositions, columnProperties,
                geometryColumns, splitGeometries, defaultEpsg, multiSrs, strokeArcs, enumOptions, transformations, enumLookups,
                uniqueKeys);
    }

    private static Origin.DeclaredOrigin origin(ModelSymbol model) {
        return (Origin.DeclaredOrigin) model.header().origin();
    }

    private static String modelEntry(ModelSymbol model) {
        String name = model.name().value();
        var imports = Stream.concat(model.imports().stream().map(ModelImport::importedModel),
                model.translation().stream().map(ModelTranslation::sourceModel)).distinct().sorted().toList();
        return imports.isEmpty() ? name : name + "{ " + String.join(" ", imports) + "}";
    }

    private static SourceText readSource(Path file) throws IOException {
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
        return new SourceText(new SourceId(file.toUri().toString()), new SourceRevision(1), content);
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
                                      DbMapping mapping, DbMapping.Table owner, String table, DbMapping.Column column) {
        addProperties(properties, geometries, mapping, owner, table, column, false);
    }

    private static void addProperties(List<ColumnProperty> properties, Map<String, List<GeometryColumn>> geometries,
                                      DbMapping mapping, DbMapping.Table owner, String table, DbMapping.Column column,
                                      boolean expandedField) {
        if (column.expandedStructure().isPresent()) {
            var expansion = column.expandedStructure().orElseThrow();
            for (var group : expansion.groups()) {
                properties.add(new ColumnProperty(table, null, group.typeColumn(), Ili2dbLayout.PREFIX + "types",
                        "[\"" + expansion.typeName() + "\"]"));
                for (var field : group.fields()) addProperties(properties, geometries, mapping, owner, table, field, true);
            }
            return;
        }
        if (column.target().isPresent()) {
            property(properties, table, null, column, "foreignKey", mapping.table(column.targetRelation()).name());
        }
        if (!(column.slot() instanceof AttributeSlot attribute) || column.textExpansion().isPresent()) return;

        var declaringType = new SymbolRef(mapping.plan().snapshotId(), attribute.attribute().owner().orElseThrow());
        String subtype = !declaringType.equals(owner.object().symbol())
                && mapping.isAssignableTo(declaringType, owner.object().symbol())
                ? mapping.storage(declaringType).discriminator() : null;
        var effective = column.effectiveType().orElseThrow();
        if (column.array().isPresent()) {
            var binding = column.array().orElseThrow();
            binding.target().ifPresent(target ->
                    property(properties, table, null, column, "foreignKey", mapping.table(target).name()));
            if (binding.kind() == DbMapping.Kind.ENUMERATION || binding.kind() == DbMapping.Kind.BOOLEAN) {
                String domain = null;
                if (binding.declaredElement(mapping.plan(), attribute) instanceof DomainReferenceType alias) {
                    domain = qualifiedName(mapping, symbol(mapping, new SymbolRef(mapping.plan().snapshotId(), alias.targetDomain())));
                } else if (binding.kind() == DbMapping.Kind.BOOLEAN) {
                    domain = "INTERLIS.BOOLEAN";
                }
                if (domain != null) {
                    property(properties, table, null, column, "enumDomain", domain);
                    if (binding.wrapper().isEmpty()) {
                        property(properties, table, table, column, "enumDomain", domain);
                    }
                }
            }
            if (binding.type() instanceof TextType text && text.textKind() == TextKind.MTEXT) {
                property(properties, table, subtype, column, "textKind", "MTEXT");
            }
            return;
        }
        String typeKind = column.jsonStructure().isPresent() ? "STRUCTURE" : switch (column.kind()) {
            case TEXT -> ((TextType) effective).textKind().name();
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case TIME -> "TIMEOFDAY";
            case DATETIME -> "DATETIME";
            case FORMATTED -> "FORMATTED";
            case UUID -> "OID";
            case BINARY -> "BINARY";
            case XML -> "XML";
            case INTEGER, BIGINT, DECIMAL -> "NUMERIC";
            case ENUMERATION -> "ENUM";
            case POINT -> "COORD";
            case MULTIPOINT -> "MULTICOORD";
            case POLYLINE, SURFACE -> ((GeometryType) effective).geometryKind().name();
            case MULTICURVE -> "MULTIPOLYLINE";
            case MULTISURFACE -> column.wrappedGeometry().isPresent() ? "MULTISURFACE" : ((GeometryType) effective).geometryKind().name();
            case REFERENCE -> null;
        };
        if (typeKind != null) property(properties, table, subtype, column, "typeKind", typeKind);
        if (!expandedField && (column.kind() == DbMapping.Kind.BOOLEAN || column.kind() == DbMapping.Kind.ENUMERATION)) {
            String domain = null;
            if (declaredType(mapping, column) instanceof DomainReferenceType alias) {
                domain = qualifiedName(mapping, symbol(mapping, new SymbolRef(mapping.plan().snapshotId(), alias.targetDomain())));
            } else if (column.kind() == DbMapping.Kind.BOOLEAN) {
                domain = "INTERLIS.BOOLEAN";
            }
            if (domain != null) {
                if (mapping.profile() == DbInheritance.Profile.NEW_CLASS && !DbTextExpansion.isLocalisationBase(owner.object())) {
                    property(properties, table, table, column, "enumDomain", domain);
                } else {
                    for (var type : mapping.objectStorage()) {
                        if (type.rows().contains(owner) && !type.object().modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                                && mapping.applies(column, type.object().symbol())) {
                            property(properties, table, type.discriminator(), column, "enumDomain", domain);
                        }
                    }
                }
            }
        }
        if (effective instanceof TextType text && text.textKind() == TextKind.MTEXT) {
            property(properties, table, subtype, column, "textKind", "MTEXT");
        }
        if (effective instanceof NumericType number) {
            number.range().unit().ifPresent(id -> {
                var unit = symbol(mapping, new SymbolRef(mapping.plan().snapshotId(), id));
                String name = unit.name().value();
                property(properties, table, subtype, column, "unit", name.substring(name.lastIndexOf('.') + 1));
            });
        }
        if (column.kind().geometry()) {
            geometries.computeIfAbsent(table, ignored -> new ArrayList<>())
                    .add(geometryColumn(properties, mapping, table, subtype, column));
        }
        String caption = metaText(attribute.attribute(), "ili2db.dispName");
        if (caption != null) property(properties, table, subtype, column, "dispName", caption);
    }

    String displayName(DbMapping.Table table) {
        return metaText(symbol(mapping, table.object().symbol()), "ili2db.dispName");
    }

    private static GeometryColumn geometryColumn(List<ColumnProperty> properties, DbMapping mapping,
                                                 String table, String subtype, DbMapping.Column column) {
        var coordinates = column.vertexCoordinates(mapping.plan());
        property(properties, table, subtype, column, Ili2dbLayout.GEOMETRY_TYPE_PROPERTY, column.geometryType());
        property(properties, table, subtype, column, Ili2dbLayout.SRID_PROPERTY, Integer.toString(column.epsgCode()));
        property(properties, table, subtype, column, "coordDimension", Integer.toString(column.dimensions()));
        var x = coordinates.axes().get(0);
        var y = coordinates.axes().get(1);
        Double minX = bound(properties, table, subtype, column, "c1Min", x.minimum());
        Double maxX = bound(properties, table, subtype, column, "c1Max", x.maximum());
        Double minY = bound(properties, table, subtype, column, "c2Min", y.minimum());
        Double maxY = bound(properties, table, subtype, column, "c2Max", y.maximum());
        if (column.dimensions() == 3) {
            var z = coordinates.axes().get(2);
            bound(properties, table, subtype, column, "c3Min", z.minimum());
            bound(properties, table, subtype, column, "c3Max", z.maximum());
        }
        return new GeometryColumn(column, minX, minY, maxX, maxY);
    }

    private static Double bound(List<ColumnProperty> properties, String table, String subtype, DbMapping.Column column,
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
        property(properties, table, subtype, column, key, spelling);
        return extent;
    }

    private static void property(List<ColumnProperty> properties, String table, String subtype,
                                 DbMapping.Column column, String key, String value) {
        properties.add(new ColumnProperty(table, subtype, column.name(), Ili2dbLayout.PREFIX + key, value));
    }
}
