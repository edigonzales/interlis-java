package guru.interlis.explorer;

import guru.interlis.model.AlignmentType;
import guru.interlis.model.BooleanType;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.FormattedType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.NumericDirection;
import guru.interlis.model.NumericType;
import guru.interlis.model.OidType;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TemporalKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferSlot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.explorer.IqlModel.shortName;

final class IqlBinder {
    private sealed interface Binding permits Row, Value, Position, Computed {}

    private record Row(ObjectDescriptor type, String fields, String typeName, String position,
                       String valuePath, String associationId, String identity, String objectTable,
                       boolean inspectable) implements Binding {}

    private record Value(TypeDescriptor type, String raw, String position, String path, IqlSyntax.Token token,
                         IqlModel.Role role, Row owner) implements Binding {}

    private record Position(String sql) implements Binding {}
    private record Computed(Kind kind, String sql) implements Binding {}
    private record CollectionRows(String from, Binding element, Position position) {}
    private record RoleRows(String from, String restriction, Row target) {}
    private record ExpandedScope(Scope scope, String order, Occurrence occurrence) {}
    private record Column(String key, IqlSyntax.Token alias, Kind kind) {}

    private enum Kind { TEXT, NUMBER, BOOLEAN, ENUMERATION, DATE }

    private record Scalar(Kind kind, String sql) {}
    private record Occurrence(String position, String path) {}
    private record ScalarKey(Occurrence occurrence, Kind kind, int scale) {}
    private record Guard(Scope scope, String sql, List<Object> parameters) {}
    private static final String LV95 = "EPSG:2056";

    private record SpatialCheck(Scope scope, Value value, IqlSyntax.Path path, String requiredCrs, List<Guard> guards) {}

    private static final class Scope {
        private final Scope parent;
        private final String from;
        private final String restriction;
        private final Row implicit;
        private final Map<String, Binding> bindings = new HashMap<>();
        private final List<String> joins = new ArrayList<>();
        private final List<String> pathJoins = new ArrayList<>();
        private final Map<Occurrence, String> projectedScalars = new HashMap<>();
        private final Map<ScalarKey, Scalar> scalarExpressions = new HashMap<>();
        private final Map<String, String> identityObjects = new HashMap<>();
        private final Map<String, String> identityBaskets = new HashMap<>();

        Scope(Scope parent, String from, String restriction, Row implicit, String alias, Binding binding) {
            this.parent = parent;
            this.from = from;
            this.restriction = restriction;
            this.implicit = implicit;
            bindings.put(alias, binding);
        }

        Binding alias(String name) {
            var binding = bindings.get(name);
            return binding != null || parent == null ? binding : parent.alias(name);
        }

        String sourceSql() { return from + String.join("", joins); }
        String preflightSourceSql(boolean guarded) { return guarded ? sourceSql() : from + String.join("", pathJoins); }

        void pathJoin(String join) {
            joins.add(join);
            pathJoins.add(join);
        }
        List<Scope> lineage() {
            var result = new ArrayList<Scope>();
            for (Scope scope = this; scope != null; scope = scope.parent) result.add(scope);
            return result.reversed();
        }
    }

    private final IqlModel model;
    private final List<Object> parameters = new ArrayList<>();
    private final List<SpatialCheck> checks = new ArrayList<>();
    private int aliasNumber;

    IqlBinder(IqlModel model) { this.model = model; }

    Iql.Compiled compile(IqlSyntax.Query query, int previewLimit) {
        var matches = model.matchingSources(query.source().name());
        if (matches.isEmpty()) throw query.source().first().error("Unknown class or association " + query.source().name());
        if (matches.size() > 1) {
            var names = matches.stream().map(IqlBinder::name).sorted().collect(Collectors.joining(", "));
            throw query.source().first().error("Ambiguous class or association " + query.source().name() + ": " + names);
        }
        var source = matches.getFirst();
        boolean association = source.kind() == SymbolKind.ASSOCIATION;
        String table = alias();
        var row = association ? associationRow(source, table) : objectRow(source, table);
        var rootAlias = query.alias() == null ? query.source().last() : query.alias();
        var scope = new Scope(null, (association ? "association " : "object ") + table,
                table + ".type_name IN " + typeNames(model.descendants(source)), row, rootAlias.text(), row);
        var occurrence = association ? null : new Occurrence(row.position(), row.valuePath());
        var expansionOrder = new ArrayList<String>();
        IqlSyntax.Token previousAlias = null;
        for (var expansion : query.expansions()) {
            if (previousAlias != null && !expansion.path().first().text().equals(previousAlias.text())) {
                throw expansion.path().first().error("A following EXPAND must start at " + previousAlias.text());
            }
            var expanded = expand(scope, expansion);
            scope = expanded.scope();
            occurrence = expanded.occurrence();
            expansionOrder.add(expanded.order());
            previousAlias = expansion.alias();
        }

        boolean allColumns = query.selections().isEmpty();
        var selections = allColumns ? star(source, query.source().last(), rootAlias) : query.selections();
        boolean grouped = !query.group().isEmpty();
        if (grouped && allColumns) throw query.source().last().error("GROUP BY requires explicit SELECT columns");
        boolean aggregate = grouped || selections.stream().anyMatch(IqlSyntax.Selection::count);
        if (aggregate && !grouped && selections.size() != 1) {
            throw query.source().last().error("Selecting other columns with COUNT(*) requires GROUP BY");
        }
        var columns = new ArrayList<String>();
        var selected = new ArrayList<Column>();
        var geometryColumns = new ArrayList<Integer>();
        var geometryMetadata = new ArrayList<String>();
        for (var selection : selections) {
            if (selection.count()) {
                columns.add("count(*) AS " + identifier(selection.alias() == null ? "count" : selection.alias().text()));
                selected.add(new Column(null, selection.alias(), Kind.NUMBER));
                continue;
            }
            var expression = selection.expression();
            var token = anchor(expression);
            Binding binding = resolve(scope, expression);
            var kind = kind(binding);
            String label = selection.alias() == null ? token.text() : selection.alias().text();
            boolean nativeGeometry = selection.geometry() || !allColumns && binding instanceof Value field && geometry(field);
            String projection;
            if (grouped) {
                if (nativeGeometry || kind == null) throw token.error("GROUP BY requires scalar attributes or functions");
                projection = scalar(scope, binding, scale(type(binding)), token).sql();
            } else if (nativeGeometry) {
                geometryColumns.add(columns.size());
                projection = geometryProjection(scope, binding, token, geometryMetadata);
            } else {
                projection = project(scope, binding);
            }
            columns.add(projection + " AS " + identifier(label));
            selected.add(new Column(expressionKey(scope, expression), selection.alias(), kind));
        }
        String where = query.filter() == null ? "" : " AND (" + predicate(scope, query.filter()) + ")";
        var groups = grouping(scope, query, selected);

        var ordering = new ArrayList<String>();
        if (aggregate && !grouped && !query.order().isEmpty()) {
            throw anchor(query.order().getFirst().expression()).error("ORDER BY is unavailable with COUNT(*)");
        }
        for (var order : query.order()) {
            ordering.add(orderSql(scope, order.expression(), selected, groups)
                    + (order.descending() ? " DESC NULLS LAST" : " ASC NULLS LAST"));
        }
        if (!aggregate) {
            ordering.add(association ? row.associationId() : row.position());
            ordering.addAll(expansionOrder);
        } else {
            groups.values().forEach(index -> ordering.add(index + " ASC NULLS LAST"));
        }
        var lineage = scope.lineage();
        String from = lineage.stream().map(Scope::sourceSql).collect(Collectors.joining(" CROSS JOIN "));
        String restriction = lineage.stream().map(layer -> layer.restriction).collect(Collectors.joining(" AND "));
        String select = "SELECT " + String.join(", ", columns);
        String group = groups.isEmpty() ? "" : " GROUP BY " + String.join(", ", groups.values());
        String queryBody = " FROM " + from
                + " WHERE " + restriction + where + group
                + (ordering.isEmpty() ? "" : " ORDER BY " + String.join(", ", ordering))
                + " LIMIT " + Math.min(query.limit(), previewLimit + 1L);
        boolean navigable = !aggregate && occurrence != null;
        if (navigable) {
            geometryMetadata.add(occurrence.position());
            geometryMetadata.add(occurrence.path());
        }
        String metadata = geometryMetadata.isEmpty() ? "" : ", " + String.join(", ", geometryMetadata);
        return new Iql.Compiled(select + queryBody, parameters, checks.stream().map(this::spatialCheck).toList(),
                select + metadata + queryBody, geometryColumns, navigable);
    }

    private Map<String, String> grouping(Scope scope, IqlSyntax.Query query, List<Column> columns) {
        var groups = new LinkedHashMap<String, String>();
        for (var expression : query.group()) {
            var key = expressionKey(scope, expression);
            if (groups.containsKey(key)) continue;
            for (int index = 0; index < columns.size(); index++) {
                if (key.equals(columns.get(index).key())) {
                    groups.put(key, Integer.toString(index + 1));
                    break;
                }
            }
            if (!groups.containsKey(key)) {
                var binding = resolve(scope, expression);
                var token = anchor(expression);
                if (kind(binding) == null) throw token.error("GROUP BY requires scalar attributes or functions");
                groups.put(key, scalar(scope, binding, scale(type(binding)), token).sql());
            }
        }
        if (!groups.isEmpty()) {
            for (var selection : query.selections()) {
                if (!selection.count() && !groups.containsKey(expressionKey(scope, selection.expression()))) {
                    throw anchor(selection.expression()).error("Selected values must appear in GROUP BY");
                }
            }
        }
        return groups;
    }

    private String orderSql(Scope scope, IqlSyntax.Expression expression, List<Column> columns, Map<String, String> groups) {
        var token = anchor(expression);
        if (expression instanceof IqlSyntax.Path path && path.segments().size() == 1) {
            int match = -1;
            for (int index = 0; index < columns.size(); index++) {
                var alias = columns.get(index).alias();
                if (alias == null || !alias.text().equals(path.name())) continue;
                if (match >= 0) throw token.error("Ambiguous SELECT alias " + path.name());
                match = index;
            }
            if (match >= 0) {
                if (groups.isEmpty()) requireOrderable(columns.get(match).kind(), token);
                return Integer.toString(match + 1);
            }
        }
        if (!groups.isEmpty()) {
            var key = groups.get(expressionKey(scope, expression));
            if (key == null) throw token.error("ORDER BY requires a grouping key or SELECT alias");
            return key;
        }
        var binding = resolve(scope, expression);
        var scalar = scalar(scope, binding, scale(type(binding)), token);
        requireOrderable(scalar.kind(), token);
        return scalar.sql();
    }

    private static void requireOrderable(Kind kind, IqlSyntax.Token token) {
        if (kind == null) throw token.error("ORDER BY requires a scalar value");
        if (kind == Kind.ENUMERATION) throw token.error("Enumeration ordering is not supported");
    }

    private static String expressionKey(Scope scope, IqlSyntax.Expression expression) {
        if (expression instanceof IqlSyntax.Function function) {
            return function.name().text().toUpperCase(Locale.ROOT) + "(" + expressionKey(scope, function.path()) + ")";
        }
        var path = (IqlSyntax.Path) expression;
        var parts = path.segments();
        var alias = scope.alias(path.first().text());
        if (parts.size() > 1 && alias == scope.implicit) {
            parts = parts.subList(1, parts.size());
        }
        return (alias == null || alias == scope.implicit ? "root:" : "alias:")
                + parts.stream().map(IqlSyntax.Token::text).collect(Collectors.joining("."));
    }

    private static IqlSyntax.Token anchor(IqlSyntax.Expression expression) {
        return switch (expression) {
            case IqlSyntax.Path path -> path.last();
            case IqlSyntax.Function function -> function.name();
            case IqlSyntax.Literal literal -> literal.token();
            default -> throw new IllegalArgumentException("Expected a scalar expression");
        };
    }

    private List<IqlSyntax.Selection> star(ObjectDescriptor owner, IqlSyntax.Token anchor, IqlSyntax.Token rootAlias) {
        var columns = owner.slots().stream().filter(slot -> !(slot instanceof AttributeSlot attr)
                || !attr.attribute().transientAttribute()).map(slot -> {
                    var field = new IqlSyntax.Token(IqlSyntax.Kind.WORD, shortName(slot), anchor.start(), anchor.end());
                    return new IqlSyntax.Selection(new IqlSyntax.Path(List.of(rootAlias, field)), null, false, false);
                }).toList();
        if (columns.isEmpty()) throw anchor.error("This source has no selectable fields");
        return columns;
    }

    private Binding resolve(Scope scope, IqlSyntax.Expression expression) {
        return switch (expression) {
            case IqlSyntax.Path path -> resolve(scope, path);
            case IqlSyntax.Function function -> function(scope, function);
            default -> throw new IllegalArgumentException("Expected a bound expression");
        };
    }

    private Binding function(Scope scope, IqlSyntax.Function function) {
        if (function.name().is("ELEMENT_COUNT")) {
            var path = function.path();
            var field = value(resolve(scope, path), path.last());
            if (!(field.type() instanceof CollectionType)) throw path.last().error("ELEMENT_COUNT requires a LIST or BAG");
            return new Computed(Kind.NUMBER, "(CASE WHEN " + field.raw() + " IS NULL THEN 0 WHEN "
                    + tag(field.raw()) + " = 'repeated' THEN json_array_length(" + field.raw() + ", '$[2]') END)");
        }
        var path = function.path();
        var binding = path.segments().size() == 1 ? scope.alias(path.name()) : null;
        if (!(binding instanceof Row row) || row.type().kind() != SymbolKind.CLASS && row.type().kind() != SymbolKind.ASSOCIATION) {
            throw path.last().error(function.name().text() + " requires an object or association alias");
        }
        String object = row.objectTable();
        if (object == null) {
            object = scope.identityObjects.computeIfAbsent(row.position(), position -> {
                String table = alias();
                scope.joins.add(" LEFT JOIN object " + table + " ON " + table + ".position = " + position);
                return table;
            });
        }
        if (function.name().is("BID")) {
            var objectTable = object;
            String basket = scope.identityBaskets.computeIfAbsent(objectTable, source -> {
                String table = alias();
                scope.joins.add(" LEFT JOIN basket " + table + " ON " + table + ".position = " + source + ".basket_position");
                return table;
            });
            return new Computed(Kind.TEXT, basket + ".bid");
        }
        String id = object + (function.name().is("TID") ? ".tid" : ".oid");
        if (row.type().kind() == SymbolKind.ASSOCIATION) {
            id = "(CASE WHEN " + row.valuePath() + " = '' AND " + object + ".type_name = " + row.typeName()
                    + " THEN " + id + " END)";
        }
        return new Computed(Kind.TEXT, id);
    }

    private Binding resolve(Scope scope, IqlSyntax.Path path) {
        Binding binding = scope.alias(path.first().text());
        var names = path.segments();
        if (binding != null) names = names.subList(1, names.size());
        else binding = scope.implicit;
        for (var name : names) {
            Row row = switch (binding) {
                case Row object -> object;
                case Value field -> traverse(scope, field);
                case Position ignored -> throw name.error("A LIST position has no nested members");
                case Computed ignored -> throw name.error("A scalar value has no nested members");
            };
            binding = member(row, name);
        }
        return binding;
    }

    private Value member(Row row, IqlSyntax.Token member) {
        var attribute = model.attribute(row.type(), member.text());
        var roles = model.roles(row.type()).stream().filter(role -> shortName(role.target()).equals(member.text())).toList();
        if (roles.size() > 1 || attribute.isPresent() && !roles.isEmpty()) {
            throw member.error("Ambiguous member " + member.text());
        }
        if (!roles.isEmpty()) return new Value(null, null, row.position(), null, member, roles.getFirst(), row);
        var slot = attribute.orElseThrow(() -> member.error("Unknown member " + member.text() + " on " + name(row.type())));
        String qualified = effectiveSlot(row.type(), row.typeName(), slot,
                actual -> model.attribute(actual, member.text()).orElse(null));
        return new Value(model.type(slot), extract(row.fields(), "'$.\"' || " + qualified + " || '\"'"),
                row.position(), row.valuePath() + " || '/' || " + qualified, member, null, row);
    }

    private String effectiveSlot(ObjectDescriptor owner, String typeName, TransferSlot declared,
                                 Function<ObjectDescriptor, TransferSlot> lookup) {
        String base = quote(declared.name().declared().toString());
        var alternatives = new ArrayList<String>();
        for (var actual : model.descendants(owner)) {
            var effective = lookup.apply(actual);
            if (effective != null && !effective.name().declared().equals(declared.name().declared())) {
                alternatives.add(" WHEN " + quote(name(actual)) + " THEN " + quote(effective.name().declared().toString()));
            }
        }
        return alternatives.isEmpty() ? base : "(CASE " + typeName + String.join("", alternatives) + " ELSE " + base + " END)";
    }

    private Row traverse(Scope scope, Value value) {
        if (value.role() != null) {
            if (value.role().multiple()) throw value.token().error("Use EXISTS to navigate a multi-valued role");
            return roleTarget(scope, value);
        }
        return switch (value.type()) {
            case StructureValueType structure -> new Row(requiredTarget(structure, value), extract(value.raw(), "'$[2]'"),
                    text(value.raw(), "$[1]"), value.position(), value.path(), null, "NULL", null, inspectable(value));
            case ReferenceType reference -> referenceTarget(scope, value, reference);
            case CollectionType ignored -> throw value.token().error("Use EXISTS or EXPAND to navigate a LIST or BAG");
            default -> throw value.token().error("This value has no nested members");
        };
    }

    private ObjectDescriptor requiredTarget(TypeDescriptor type, Value value) {
        return model.target(type).orElseThrow(() -> value.token().error("No common model type is known for this path"));
    }

    private Row referenceTarget(Scope scope, Value value, ReferenceType type) {
        String reference = alias();
        scope.pathJoin(" LEFT JOIN reference " + reference + " ON " + reference + ".source_position = " + value.position()
                + " AND " + reference + ".value_path = " + value.path());
        return joinTarget(scope, requiredTarget(type, value), model.allowedTargets(type.targetRoots()), reference + ".target_position");
    }

    private Row roleTarget(Scope scope, Value value) {
        var roots = value.role().target().role().targetRoots();
        var type = model.commonTarget(roots).orElseThrow(() -> value.token().error("No common target class for this role"));
        String only = alias();
        String targets = alias();
        scope.pathJoin(" LEFT JOIN LATERAL (SELECT CASE WHEN count(*) = 1 THEN min(target_position) END AS target_position"
                + " FROM (" + roles(value) + ") " + targets + ") " + only + " ON TRUE");
        return joinTarget(scope, type, model.allowedTargets(roots), only + ".target_position");
    }

    private Row joinTarget(Scope scope, ObjectDescriptor type, List<ObjectDescriptor> allowed, String position) {
        String joined = alias();
        scope.pathJoin(" LEFT JOIN object " + joined + " ON " + joined + ".position = " + position
                + " AND " + joined + ".type_name IN " + typeNames(allowed));
        return targetRow(scope, type, joined);
    }

    private Row targetRow(Scope scope, ObjectDescriptor type, String joined) {
        Row object = objectRow(type, joined);
        if (type.kind() != SymbolKind.ASSOCIATION) return object;
        String association = alias();
        scope.pathJoin(" LEFT JOIN association " + association + " ON " + association + ".source_position = " + object.position()
                + " AND " + association + ".value_path = '' AND " + association + ".type_name = " + object.typeName());
        return new Row(type, object.fields(), object.typeName(), object.position(), "''", association + ".id",
                object.identity(), joined, false);
    }

    private String roles(Value value) {
        var role = value.role();
        Row owner = value.owner();
        String end = alias();
        if (role.source() == null) {
            return "SELECT " + end + ".target_position FROM association_role " + end
                    + " WHERE " + end + ".association_id = " + owner.associationId() + " AND " + end + ".slot = "
                    + roleSlot(role.association(), owner.typeName(), role.target());
        }
        String record = alias();
        String source = alias();
        return "SELECT " + end + ".target_position FROM association " + record
                + " JOIN association_role " + source + " ON " + source + ".association_id = " + record + ".id"
                + " JOIN association_role " + end + " ON " + end + ".association_id = " + record + ".id"
                + " WHERE " + source + ".target_position = " + owner.position()
                + " AND " + record + ".type_name IN " + typeNames(model.descendants(role.association()))
                + " AND " + source + ".slot = " + roleSlot(role.association(), record + ".type_name", role.source())
                + " AND " + end + ".slot = " + roleSlot(role.association(), record + ".type_name", role.target());
    }

    private String roleSlot(ObjectDescriptor type, String actual, RoleSlot slot) {
        return effectiveSlot(type, actual, slot, association -> model.roleSlots(association).stream()
                .filter(member -> shortName(member).equals(shortName(slot))).findFirst().orElse(null));
    }

    private String project(Scope scope, Binding binding) {
        if (binding instanceof Row row) return row.identity();
        if (binding instanceof Position position) return position.sql();
        if (binding instanceof Computed computed) return computed.sql();
        var value = (Value) binding;
        if (value.role() != null) {
            if (value.role().multiple()) return "(SELECT count(*) FROM (" + roles(value) + ") " + alias() + ")";
            return roleTarget(scope, value).identity();
        }
        return switch (value.type()) {
            case ReferenceType ignored -> text(value.raw(), "$[1]");
            case GeometryType ignored -> kindSummary(value.raw(), "Geometry");
            case CoordinateType ignored -> kindSummary(value.raw(), "Geometry");
            case StructureValueType ignored -> "CASE WHEN " + tag(value.raw()) + " = 'structure' THEN " + text(value.raw(), "$[1]") + " END";
            case CollectionType ignored -> "CASE WHEN " + tag(value.raw()) + " = 'repeated' THEN "
                    + text(value.raw(), "$[1]") + " || ' (' || json_array_length(" + value.raw() + ", '$[2]') || ')' END";
            default -> projection(scope, value);
        };
    }

    private String projection(Scope scope, Value value) {
        if (kind(value.type()) == null) return kindSummary(value.raw(), "Value");
        return scalar(scope, value, scale(value.type())).sql();
    }

    private String geometryProjection(Scope scope, Binding binding, IqlSyntax.Token token, List<String> metadata) {
        Value field = value(binding, token);
        if (!geometry(field)) throw token.error("GEOMETRY requires a geometry attribute");
        String table = alias();
        scope.joins.add(" LEFT JOIN geometry " + table + " ON " + geometryKey(table, field));
        metadata.add(table + ".source_position");
        metadata.add(table + ".value_path");
        metadata.add(table + ".crs");
        metadata.add(table + ".projection_error");
        return table + ".geom";
    }

    private String predicate(Scope scope, IqlSyntax.Expression expression) {
        return predicate(scope, expression, false);
    }

    private String predicate(Scope scope, IqlSyntax.Expression expression, boolean distinguishUnknown) {
        return switch (expression) {
            case IqlSyntax.Junction junction -> junction(scope, junction, distinguishUnknown);
            case IqlSyntax.Not not -> "(NOT " + predicate(scope, not.operand(), true) + ")";
            case IqlSyntax.Test test -> test(scope, test);
            case IqlSyntax.Comparison comparison -> compare(scope, comparison);
            case IqlSyntax.In in -> in(scope, in);
            case IqlSyntax.Between between -> between(scope, between);
            case IqlSyntax.TextSearch search -> textSearch(scope, search);
            case IqlSyntax.Exists exists -> exists(scope, exists);
            case IqlSyntax.Intersects intersects -> intersects(scope, intersects, distinguishUnknown);
            case IqlSyntax.WithinDistance distance -> withinDistance(scope, distance, distinguishUnknown);
            case IqlSyntax.Function function -> throw function.name().error("Expected a Boolean expression");
            case IqlSyntax.Path path -> {
                var field = value(resolve(scope, path), path.last());
                if (kind(field.type()) != Kind.BOOLEAN) throw path.last().error("Expected a Boolean expression");
                yield scalar(scope, field, 0).sql();
            }
            case IqlSyntax.Literal literal -> {
                if (literal.kind() != IqlSyntax.LiteralKind.BOOLEAN) throw literal.token().error("Expected a Boolean expression");
                yield parameter(literal.value());
            }
        };
    }

    private String junction(Scope scope, IqlSyntax.Junction junction, boolean distinguishUnknown) {
        int firstParameter = parameters.size();
        int firstCheck = checks.size();
        String left = predicate(scope, junction.left(), distinguishUnknown);
        int middleParameter = parameters.size();
        int middleCheck = checks.size();
        String right = predicate(scope, junction.right(), distinguishUnknown);
        boolean leftSpatial = firstCheck != middleCheck;
        boolean rightSpatial = middleCheck != checks.size();
        if (leftSpatial != rightSpatial) {
            // A spatial sibling is unknown until execution. A non-spatial sibling
            // can prove the spatial branch cannot affect the filter.
            String condition = junction.operator().equals("OR") ? " IS NOT TRUE"
                    : distinguishUnknown ? " IS NOT FALSE" : " IS TRUE";
            String sibling = leftSpatial ? right : left;
            int siblingParameter = leftSpatial ? middleParameter : firstParameter;
            int endParameter = leftSpatial ? parameters.size() : middleParameter;
            var guard = new Guard(scope, "(" + sibling + ")" + condition,
                    List.copyOf(parameters.subList(siblingParameter, endParameter)));
            for (int check = firstCheck; check < checks.size(); check++) {
                checks.get(check).guards().add(guard);
            }
        }
        return "(" + left + " " + junction.operator() + " " + right + ")";
    }

    private String test(Scope scope, IqlSyntax.Test test) {
        Value value = value(resolve(scope, test.path()), test.path().last());
        String result = switch (test.state()) {
            case MISSING -> value.role() == null ? value.raw() + " IS NULL" : "NOT EXISTS (" + roles(value) + ")";
            case UNDEFINED -> value.role() == null ? "COALESCE(" + tag(value.raw()) + " = 'undefined', FALSE)" : "FALSE";
            case UNRESOLVED -> {
                if (value.role() != null && !value.role().multiple()) {
                    yield "EXISTS (" + roles(value) + ") AND " + roleTarget(scope, value).position() + " IS NULL";
                }
                if (value.type() instanceof ReferenceType reference) {
                    yield "COALESCE(" + tag(value.raw()) + " = 'reference', FALSE) AND "
                            + referenceTarget(scope, value, reference).position() + " IS NULL";
                }
                throw value.token().error("IS UNRESOLVED requires a single-valued reference or role");
            }
        };
        if (test.negated()) result = "NOT (" + result + ")";
        return value.owner() == null ? "(" + result + ")"
                : "(CASE WHEN " + value.owner().fields() + " IS NOT NULL THEN " + result + " END)";
    }

    private String compare(Scope scope, IqlSyntax.Comparison comparison) {
        var left = comparison.left() instanceof IqlSyntax.Literal ? null : resolve(scope, comparison.left());
        var right = comparison.right() instanceof IqlSyntax.Literal ? null : resolve(scope, comparison.right());
        int scale = Math.max(operandScale(comparison.left(), left), operandScale(comparison.right(), right));
        if (scale > 38) throw comparison.operator().error("Exact numeric comparison exceeds DuckDB decimal precision");
        var leftKind = left == null ? kind((IqlSyntax.Literal) comparison.left(), type(right)) : kind(left);
        var rightKind = right == null ? kind((IqlSyntax.Literal) comparison.right(), type(left)) : kind(right);
        if (leftKind == null || leftKind != rightKind) throw comparison.operator().error("Values have incompatible comparison types");
        String operator = comparison.operator().text();
        if ((leftKind == Kind.ENUMERATION || leftKind == Kind.BOOLEAN) && !List.of("=", "!=", "<>").contains(operator)) {
            throw comparison.operator().error("This value type supports equality comparisons only");
        }
        if (left != null && right != null) validateComparison(left, right, comparison.operator());
        String leftSql = left == null ? literal((IqlSyntax.Literal) comparison.left(), leftKind, scale, type(right))
                : scalar(scope, left, scale, comparison.operator()).sql();
        String rightSql = right == null ? literal((IqlSyntax.Literal) comparison.right(), rightKind, scale, type(left))
                : scalar(scope, right, scale, comparison.operator()).sql();
        return "(" + leftSql + " " + operator + " " + rightSql + ")";
    }

    private String in(Scope scope, IqlSyntax.In in) {
        var token = anchor(in.value());
        var binding = filterValue(scope, in.value(), "IN");
        var kind = kind(binding);
        int scale = literalScale(binding, in.options(), token);
        var valueSql = scalar(scope, binding, scale, token).sql();
        var options = in.options().stream()
                .map(option -> typedLiteral(option, binding, kind, scale)).collect(Collectors.joining(", "));
        return "(" + valueSql + (in.negated() ? " NOT IN (" : " IN (") + options + "))";
    }

    private String between(Scope scope, IqlSyntax.Between between) {
        var token = anchor(between.value());
        var binding = filterValue(scope, between.value(), "BETWEEN");
        var kind = kind(binding);
        if (kind != Kind.NUMBER && kind != Kind.DATE) throw token.error("BETWEEN requires a number or ISO date");
        int scale = literalScale(binding, List.of(between.lower(), between.upper()), token);
        var valueSql = scalar(scope, binding, scale, token).sql();
        var lower = typedLiteral(between.lower(), binding, kind, scale);
        var upper = typedLiteral(between.upper(), binding, kind, scale);
        return "(" + valueSql + (between.negated() ? " NOT BETWEEN " : " BETWEEN ") + lower + " AND " + upper + ")";
    }

    private Binding filterValue(Scope scope, IqlSyntax.Expression expression, String operator) {
        if (expression instanceof IqlSyntax.Literal) throw anchor(expression).error(operator + " requires a scalar attribute or function");
        var binding = resolve(scope, expression);
        if (kind(binding) == null) throw anchor(expression).error(operator + " requires a scalar attribute or function");
        return binding;
    }

    private int literalScale(Binding binding, List<IqlSyntax.Literal> literals, IqlSyntax.Token token) {
        int scale = scale(type(binding));
        for (var value : literals) {
            scale = Math.max(scale, operandScale(value, null));
        }
        if (scale > 38) throw token.error("Exact numeric comparison exceeds DuckDB decimal precision");
        return scale;
    }

    private String typedLiteral(IqlSyntax.Literal value, Binding binding, Kind expected, int scale) {
        var type = type(binding);
        if (kind(value, type) != expected) throw value.token().error("Literal has an incompatible type");
        return literal(value, expected, scale, type);
    }

    private String textSearch(Scope scope, IqlSyntax.TextSearch search) {
        var token = search.path().last();
        var field = value(resolve(scope, search.path()), token);
        if (!(field.type() instanceof TextType)) throw token.error("Text search requires a text attribute");
        String fn = search.prefix() ? "starts_with" : "contains";
        return fn + "(" + scalar(scope, field, 0).sql() + ", " + parameter(search.text().value()) + ")";
    }

    private void validateComparison(Binding leftBinding, Binding rightBinding, IqlSyntax.Token operator) {
        TypeDescriptor left = type(leftBinding);
        TypeDescriptor right = type(rightBinding);
        if (leftBinding instanceof Position || rightBinding instanceof Position
                || leftBinding instanceof Computed || rightBinding instanceof Computed) {
            TypeDescriptor other = leftBinding instanceof Position || leftBinding instanceof Computed ? right : left;
            if (other instanceof NumericType numeric && (numeric.range().unit().isPresent()
                    || numeric.range().referenceSystem().isPresent() || numeric.range().direction() != NumericDirection.NONE
                    || numeric.range().circular())) {
                throw operator.error("A LIST position or element count can only be compared with a unitless number");
            }
        }
        if (left instanceof NumericType a && right instanceof NumericType b) {
            var leftUnit = a.range().unit();
            var rightUnit = b.range().unit();
            if (!leftUnit.equals(rightUnit)) {
                if (leftUnit.isPresent() && rightUnit.isPresent()
                        && model.hasDeclaredUnitConversion(leftUnit.orElseThrow(), rightUnit.orElseThrow())) {
                    throw operator.error("Comparing these attributes requires unit conversion. IQL comparisons require the same unit");
                }
                throw operator.error("Comparing these attributes requires matching units");
            }
            if (!a.range().referenceSystem().equals(b.range().referenceSystem())) {
                throw operator.error("Comparing these attributes requires matching reference systems and axes");
            }
            if (a.range().direction() != b.range().direction() || a.range().circular() != b.range().circular()) {
                throw operator.error("Comparing these attributes requires matching numeric directions and circularity");
            }
        } else if (left instanceof EnumerationType a && right instanceof EnumerationType b
                && !model.relatedEnumerations(a, b)) {
            throw operator.error("Comparing these attributes requires related enumeration types");
        }
    }

    private String literal(IqlSyntax.Literal literal, Kind kind, int scale, TypeDescriptor other) {
        return switch (kind) {
            case NUMBER -> {
                var decimal = normalize(literal);
                if (decimal.signum() != 0 && (decimal.scale() > scale || (long) decimal.precision() - decimal.scale() > 38L - scale)) {
                    throw literal.token().error("Exact numeric literal exceeds DuckDB decimal precision");
                }
                yield "CAST(" + parameter(decimal.toPlainString()) + " AS DECIMAL(38," + scale + "))";
            }
            case DATE -> {
                try {
                    java.time.LocalDate.parse(literal.value().toString());
                } catch (java.time.format.DateTimeParseException ex) {
                    throw literal.token().error("Expected an ISO date such as '2026-01-15'");
                }
                yield "CAST(" + parameter(literal.value()) + " AS DATE)";
            }
            case ENUMERATION -> {
                if (other instanceof EnumerationType type
                        && !IqlModel.validEnumerationValue(type, literal.value().toString())) {
                    throw literal.token().error("Unknown enumeration value #" + literal.value());
                }
                yield parameter(literal.value());
            }
            default -> parameter(literal.value());
        };
    }

    private int operandScale(IqlSyntax.Expression expression, Binding binding) {
        if (binding != null) return scale(type(binding));
        if (expression instanceof IqlSyntax.Literal literal && literal.kind() == IqlSyntax.LiteralKind.NUMBER) {
            return Math.max(0, normalize(literal).scale());
        }
        return 0;
    }

    private static int scale(TypeDescriptor type) {
        if (!(type instanceof NumericType numeric)) return 0;
        return Math.max(numeric.range().minimum().map(IqlBinder::scale).orElse(0),
                numeric.range().maximum().map(IqlBinder::scale).orElse(0));
    }

    private static int scale(DecimalLiteral value) { return Math.max(0, value.exactValue().scale()); }

    private static BigDecimal normalize(IqlSyntax.Literal value) {
        try {
            return ((BigDecimal) value.value()).stripTrailingZeros();
        } catch (ArithmeticException ex) {
            throw value.token().error("Exact numeric literal exceeds DuckDB decimal precision");
        }
    }

    private static Kind kind(IqlSyntax.Literal literal, TypeDescriptor other) {
        return switch (literal.kind()) {
            case TEXT -> kind(other) == Kind.DATE ? Kind.DATE : Kind.TEXT;
            case NUMBER -> Kind.NUMBER;
            case BOOLEAN -> Kind.BOOLEAN;
            case ENUMERATION -> Kind.ENUMERATION;
        };
    }

    private static Kind kind(Binding binding) {
        return switch (binding) {
            case Position ignored -> Kind.NUMBER;
            case Computed computed -> computed.kind();
            case null, default -> kind(type(binding));
        };
    }

    private static TypeDescriptor type(Binding binding) {
        return binding instanceof Value field ? field.type() : null;
    }

    private static Kind kind(TypeDescriptor type) {
        return switch (type) {
            case TextType ignored -> Kind.TEXT;
            case OidType ignored -> Kind.TEXT;
            case AlignmentType ignored -> Kind.TEXT;
            case FormattedType format -> format.predefined().orElse(null) == TemporalKind.DATE ? Kind.DATE : Kind.TEXT;
            case BooleanType ignored -> Kind.BOOLEAN;
            case EnumerationType ignored -> Kind.ENUMERATION;
            case NumericType ignored -> Kind.NUMBER;
            case null, default -> null;
        };
    }

    private Scalar scalar(Scope scope, Binding binding, int scale, IqlSyntax.Token token) {
        if (binding instanceof Position position) {
            return ordinal(position.sql(), scale);
        }
        if (binding instanceof Computed computed) {
            return computed.kind() == Kind.NUMBER ? ordinal(computed.sql(), scale)
                    : new Scalar(computed.kind(), computed.sql());
        }
        return scalar(scope, value(binding, token), scale);
    }

    private static Scalar ordinal(String sql, int scale) {
        return new Scalar(Kind.NUMBER, scale == 0 ? sql : "CAST(" + sql + " AS DECIMAL(38," + scale + "))");
    }

    private Scalar scalar(Scope scope, Value value, int scale) {
        Kind kind = kind(value.type());
        if (kind == null) throw value.token().error("Expected a scalar attribute");
        if (scale > 38) throw value.token().error("Exact value exceeds DuckDB decimal precision");
        var occurrence = new Occurrence(value.position(), value.path());
        return scope.scalarExpressions.computeIfAbsent(new ScalarKey(occurrence, kind, scale), key -> {
            String projected = scope.projectedScalars.computeIfAbsent(occurrence, ignored -> {
                String table = alias();
                scope.joins.add(" LEFT JOIN scalar " + table + " ON " + table + ".source_position = " + occurrence.position()
                        + " AND " + table + ".value_path = " + occurrence.path());
                return table;
            });
            String sql = switch (kind) {
                case NUMBER -> exactNumber(scope, projected, scale);
                case BOOLEAN -> projected + ".boolean_value";
                case DATE -> projected + ".date_value";
                case ENUMERATION, TEXT -> projected + ".text_value";
            };
            return new Scalar(kind, sql);
        });
    }

    private String exactNumber(Scope scope, String scalar, int scale) {
        // BigDecimal's stored spelling can use an exponent. Test its precision using digits,
        // and construct a bounded plain decimal when the exact typed projection cannot be used.
        String cast = "DECIMAL(38," + scale + ")";
        String projected = alias();
        String integral = "TRY_CAST(" + scalar + ".integer_value AS " + cast + ")";
        String decimalValue = scalar + ".decimal_value";
        String nativeDecimal = "TRY_CAST(" + decimalValue + " AS " + cast + ")";
        if (scale < 18) nativeDecimal = "CASE WHEN trunc(" + decimalValue + ", " + scale + ") = " + decimalValue
                + " THEN " + nativeDecimal + " END";
        scope.joins.add(" LEFT JOIN LATERAL (SELECT COALESCE(" + integral + ", " + nativeDecimal
                + ") AS exact_value) " + projected + " ON TRUE");
        String input = alias();
        String significand = alias();
        String decimal = alias();
        String raw = "(CASE WHEN " + projected + ".exact_value IS NULL THEN upper(" + scalar + ".number_text) END)";
        scope.joins.add(" LEFT JOIN LATERAL (SELECT split_part(" + raw + ", 'E', 1) AS mantissa,"
                + " COALESCE(TRY_CAST(NULLIF(split_part(" + raw + ", 'E', 2), '') AS BIGINT), 0) AS exponent) "
                + input + " ON TRUE");
        scope.joins.add(" LEFT JOIN LATERAL (SELECT regexp_replace(" + input + ".mantissa, '[+.-]', '', 'g') AS digits,"
                + " length(split_part(" + input + ".mantissa, '.', 2)) AS fraction) " + significand + " ON TRUE");
        String digits = "ltrim(rtrim(" + significand + ".digits, '0'), '0')";
        String exponent = input + ".exponent - " + significand + ".fraction + length(" + significand + ".digits)"
                + " - length(rtrim(" + significand + ".digits, '0'))";
        scope.joins.add(" LEFT JOIN LATERAL (SELECT " + digits + " AS digits, " + exponent + " AS exponent) "
                + decimal + " ON TRUE");
        String exponentSql = decimal + ".exponent";
        String digitsSql = decimal + ".digits";
        String point = "(length(" + digitsSql + ") + " + exponentSql + ")";
        String plain = "CASE WHEN " + exponentSql + " >= 0 THEN " + digitsSql + " || repeat('0', " + exponentSql + ")"
                + " WHEN " + point + " <= 0 THEN '0.' || repeat('0', -" + point + ") || " + digitsSql
                + " ELSE left(" + digitsSql + ", " + point + ") || '.' || right(" + digitsSql + ", -" + exponentSql + ") END";
        return "(CASE WHEN " + projected + ".exact_value IS NOT NULL THEN " + projected + ".exact_value"
                + " WHEN " + input + ".mantissa IS NULL THEN NULL WHEN " + digitsSql + " = '' THEN CAST(0 AS " + cast + ")"
                + " WHEN " + exponentSql + " < " + -scale + " OR " + point + " > " + (38 - scale)
                + " THEN error('INTERLIS numeric value exceeds exact IQL decimal precision')"
                + " ELSE CAST((CASE WHEN starts_with(" + input + ".mantissa, '-') THEN '-' ELSE '' END) || (" + plain + ") AS " + cast + ") END)";
    }

    private String exists(Scope parent, IqlSyntax.Exists exists) {
        var value = value(resolve(parent, exists.path()), exists.path().last());
        Scope scope = value.role() == null ? collectionScope(parent, value, exists.alias())
                : roleScope(parent, value, exists.alias());
        String condition = predicate(scope, exists.condition());
        return "EXISTS (SELECT 1 FROM " + scope.sourceSql() + " WHERE " + scope.restriction + " AND (" + condition + "))";
    }

    private ExpandedScope expand(Scope parent, IqlSyntax.Expansion expansion) {
        Binding binding = resolve(parent, expansion.path());
        if (!(binding instanceof Value field)) {
            throw expansion.path().last().error("EXPAND requires a LIST, BAG or multi-valued role");
        }
        var name = expansion.alias();
        if (parent.alias(name.text()) != null) {
            throw name.error("The EXPAND alias must differ from other aliases");
        }
        var positionName = expansion.position();
        if (field.role() != null && field.role().multiple()) {
            if (positionName != null) throw positionName.error("AT requires a LIST");
            var rows = roleRows(field);
            var target = rows.target();
            var scope = new Scope(parent, rows.from(), rows.restriction(), parent.implicit, name.text(), target);
            var occurrence = target.inspectable() ? new Occurrence(target.position(), target.valuePath()) : null;
            return new ExpandedScope(scope, target.position(), occurrence);
        }
        if (!(field.type() instanceof CollectionType type)) {
            throw expansion.path().last().error("EXPAND requires a LIST, BAG or multi-valued role");
        }
        if (positionName != null) {
            if (type.collectionKind() != CollectionKind.LIST) throw positionName.error("AT requires a LIST");
            if (name.text().equals(positionName.text()) || parent.alias(positionName.text()) != null) {
                throw positionName.error("The AT alias must differ from other aliases");
            }
        }
        var rows = collectionRows(field, type, name);
        var scope = new Scope(parent, rows.from(), "TRUE", parent.implicit, name.text(), rows.element());
        if (positionName != null) scope.bindings.put(positionName.text(), rows.position());
        var path = switch (rows.element()) {
            case Row row -> row.valuePath();
            case Value element -> element.path();
            default -> throw new IllegalStateException("Expected a collection element");
        };
        var occurrence = inspectable(field) ? new Occurrence(field.position(), path) : null;
        return new ExpandedScope(scope, rows.position().sql(), occurrence);
    }

    private static boolean inspectable(Value value) {
        return value.owner() != null && value.owner().inspectable();
    }

    private Scope collectionScope(Scope parent, Value value, IqlSyntax.Token name) {
        if (!(value.type() instanceof CollectionType type)) throw value.token().error("EXISTS requires a LIST, BAG or multi-valued role");
        var rows = collectionRows(value, type, name);
        Row implicit = rows.element() instanceof Row row ? row : parent.implicit;
        return new Scope(parent, rows.from(), "TRUE", implicit, name.text(), rows.element());
    }

    private CollectionRows collectionRows(Value value, CollectionType type, IqlSyntax.Token name) {
        String item = alias();
        var element = new Value(model.type(type.elementType()), item + ".value", value.position(),
                value.path() + " || '/' || " + item + ".key", name, null, null);
        String from = "LATERAL json_each(" + value.raw() + ", '$[2]') " + item;
        var position = new Position("(CAST(" + item + ".key AS BIGINT) + 1)");
        if (element.type() instanceof StructureValueType structure) {
            String typeName = text(element.raw(), "$[1]");
            var row = new Row(requiredTarget(structure, element), extract(element.raw(), "'$[2]'"),
                    typeName, element.position(), element.path(), null, typeName, null, inspectable(value));
            return new CollectionRows(from, row, position);
        }
        return new CollectionRows(from, element, position);
    }

    private Scope roleScope(Scope parent, Value value, IqlSyntax.Token name) {
        if (!value.role().multiple()) throw value.token().error("EXISTS requires a multi-valued role");
        var rows = roleRows(value);
        return new Scope(parent, rows.from(), rows.restriction(), rows.target(), name.text(), rows.target());
    }

    private RoleRows roleRows(Value value) {
        var roots = value.role().target().role().targetRoots();
        var type = model.commonTarget(roots).orElseThrow(() -> value.token().error("No common target class for this role"));
        String role = alias();
        String target = alias();
        String from = "LATERAL (" + roles(value) + ") " + role + " JOIN object " + target
                + " ON " + target + ".position = " + role + ".target_position";
        var row = objectRow(type, target);
        return new RoleRows(from, target + ".type_name IN " + typeNames(model.allowedTargets(roots)), row);
    }

    private String intersects(Scope scope, IqlSyntax.Intersects predicate, boolean distinguishUnknown) {
        var value = geometryOperand(scope, predicate.path(), "INTERSECTS");
        var box = predicate.box().stream().map(this::parameter).toList();
        String condition = "ST_Intersects(geom, ST_MakeEnvelope(" + String.join(", ", box) + "))";
        return spatialMatch(scope, value, predicate.path(), condition, null, distinguishUnknown);
    }

    private String withinDistance(Scope scope, IqlSyntax.WithinDistance predicate, boolean distinguishUnknown) {
        var value = geometryOperand(scope, predicate.path(), "WITHIN_DISTANCE");
        String point = "ST_Point(" + parameter(predicate.x()) + ", " + parameter(predicate.y()) + ")";
        String condition = "ST_DWithin(geom, " + point + ", " + parameter(predicate.radius()) + ")";
        return spatialMatch(scope, value, predicate.path(), condition, LV95, distinguishUnknown);
    }

    private Value geometryOperand(Scope scope, IqlSyntax.Path path, String operation) {
        var value = value(resolve(scope, path), path.last());
        if (!geometry(value)) {
            throw value.token().error(operation + " requires a geometry attribute");
        }
        return value;
    }

    private String spatialMatch(Scope scope, Value value, IqlSyntax.Path path, String condition,
                                String requiredCrs, boolean distinguishUnknown) {
        checks.add(new SpatialCheck(scope, value, path, requiredCrs, new ArrayList<>()));
        String match = alias();
        String crsFilter = requiredCrs == null ? "" : "crs = " + quote(requiredCrs) + " AND ";
        // Keep the indexed column and its constant search arguments visible to DuckDB's optimizer.
        String matches = "EXISTS (SELECT 1 FROM (SELECT source_position, value_path FROM geometry"
                + " WHERE " + crsFilter + condition + ") " + match
                + " WHERE " + geometryKey(match, value) + ")";
        if (!distinguishUnknown) return matches;
        String present = alias();
        return "(CASE WHEN EXISTS (SELECT 1 FROM geometry " + present + " WHERE " + geometryKey(present, value)
                + ") THEN " + matches + " END)";
    }

    private Iql.Check spatialCheck(SpatialCheck check) {
        Value value = check.value();
        var lineage = check.scope().lineage();
        var guardedScopes = check.guards().stream().map(Guard::scope).collect(Collectors.toSet());
        String scope = lineage.stream().map(layer -> layer.preflightSourceSql(guardedScopes.contains(layer)))
                .collect(Collectors.joining(" CROSS JOIN "));
        String where = lineage.stream().map(layer -> layer.restriction).collect(Collectors.joining(" AND "));
        var checkParameters = new ArrayList<Object>();
        var guards = new StringBuilder();
        for (var guard : check.guards()) {
            guards.append(" AND (").append(guard.sql()).append(')');
            checkParameters.addAll(guard.parameters());
        }
        String requiredCrs = check.requiredCrs() == null ? ""
                : " WHEN count(*) FILTER (WHERE g.crs <> " + quote(check.requiredCrs()) + ") > 0"
                + " THEN " + quote("WITHIN_DISTANCE requires LV95 (" + check.requiredCrs() + ") on this path");
        String sql = "SELECT CASE WHEN count(*) FILTER (WHERE g.projection_error IS NOT NULL) > 0"
                + " THEN 'A geometry on this path has no native projection'"
                + " WHEN count(*) FILTER (WHERE g.crs IS NULL) > 0 THEN 'The coordinate reference system is unknown on this path'"
                + " WHEN count(DISTINCT g.crs) > 1 THEN 'This path has more than one coordinate reference system'"
                + requiredCrs + " END"
                + " FROM " + scope + " JOIN geometry g ON " + geometryKey("g", value) + " WHERE " + where + guards;
        return new Iql.Check(sql, checkParameters, check.path().first().start(), check.path().last().end());
    }

    private static Row objectRow(ObjectDescriptor type, String alias) {
        return new Row(type, alias + ".fields", alias + ".type_name", alias + ".position", "''", null,
                "COALESCE(" + alias + ".tid, " + alias + ".oid, CAST(" + alias + ".position AS VARCHAR))",
                alias, type.kind() == SymbolKind.CLASS);
    }

    private static Row associationRow(ObjectDescriptor type, String alias) {
        return new Row(type, alias + ".fields", alias + ".type_name", alias + ".source_position", alias + ".value_path",
                alias + ".id", "CAST(" + alias + ".id AS VARCHAR)", null, false);
    }

    private static Value value(Binding binding, IqlSyntax.Token token) {
        if (!(binding instanceof Value value)) throw token.error("Expected an attribute or role path");
        return value;
    }

    private static String name(ObjectDescriptor type) { return type.name().declared().toString(); }
    private static String typeNames(List<ObjectDescriptor> types) {
        if (types.isEmpty()) return "(NULL)";
        return types.stream().map(type -> quote(name(type))).collect(Collectors.joining(", ", "(", ")"));
    }

    private static String kindSummary(String raw, String summary) {
        return "CASE WHEN " + tag(raw) + " <> 'undefined' THEN " + quote(summary) + " END";
    }

    private static String tag(String raw) { return text(raw, "$[0]"); }

    private static boolean geometry(Value value) {
        return value.type() instanceof GeometryType || value.type() instanceof CoordinateType;
    }

    private static String geometryKey(String table, Value value) {
        return table + ".source_position = " + value.position() + " AND " + table + ".value_path = " + value.path();
    }
    private static String text(String raw, String path) { return "json_extract_string(" + raw + ", " + quote(path) + ")"; }
    private static String extract(String raw, String path) { return "json_extract(" + raw + ", " + path + ")"; }
    private static String quote(String value) { return "'" + value.replace("'", "''") + "'"; }
    private static String identifier(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }

    private String parameter(Object value) {
        parameters.add(value);
        return "?";
    }

    private String alias() { return "iq" + ++aliasNumber; }
}
