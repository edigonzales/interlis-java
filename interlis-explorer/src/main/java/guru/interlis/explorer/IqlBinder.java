package guru.interlis.explorer;

import guru.interlis.model.AlignmentType;
import guru.interlis.model.BooleanType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.FormattedType;
import guru.interlis.model.GeometryType;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.explorer.IqlModel.shortName;

final class IqlBinder {
    private sealed interface Binding permits Row, Value {}

    private record Row(ObjectDescriptor type, String fields, String typeName, String position,
                       String valuePath, String associationId, String identity) implements Binding {}

    private record Value(TypeDescriptor type, String raw, String position, String path, IqlSyntax.Token token,
                         IqlModel.Role role, Row owner) implements Binding {}

    private enum Kind { TEXT, NUMBER, BOOLEAN, ENUMERATION, DATE }

    private record Scalar(Kind kind, String sql) {}
    private record Occurrence(String position, String path) {}
    private record ScalarKey(Occurrence occurrence, Kind kind, int scale) {}
    private record SpatialCheck(Scope scope, Value value, IqlSyntax.Path path) {}

    private static final class Scope {
        private final Scope parent;
        private final String from;
        private final String restriction;
        private final Row implicit;
        private final String alias;
        private final Binding binding;
        private final List<String> joins = new ArrayList<>();
        private final List<String> pathJoins = new ArrayList<>();
        private final Map<Occurrence, String> projectedScalars = new HashMap<>();
        private final Map<ScalarKey, Scalar> scalarExpressions = new HashMap<>();

        Scope(Scope parent, String from, String restriction, Row implicit, String alias, Binding binding) {
            this.parent = parent;
            this.from = from;
            this.restriction = restriction;
            this.implicit = implicit;
            this.alias = alias;
            this.binding = binding;
        }

        Binding alias(String name) {
            if (alias.equals(name)) return binding;
            return parent == null ? null : parent.alias(name);
        }

        String sourceSql() { return from + String.join("", joins); }
        String preflightSourceSql() { return from + String.join("", pathJoins); }

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
        var source = model.sources().stream().filter(type -> name(type).equals(query.source().name())).findFirst()
                .orElseThrow(() -> query.source().first().error("Unknown class or association " + query.source().name()));
        boolean association = source.kind() == SymbolKind.ASSOCIATION;
        String table = alias();
        var row = association ? associationRow(source, table) : objectRow(source, table);
        var scope = new Scope(null, (association ? "association " : "object ") + table,
                table + ".type_name IN " + typeNames(model.descendants(source)), row,
                query.alias() == null ? query.source().last().text() : query.alias().text(), row);

        var selections = query.selections().isEmpty() ? star(source, query.source().last()) : query.selections();
        var columns = new ArrayList<String>();
        for (var selection : selections) {
            if (selection.count()) {
                columns.add("count(*) AS " + identifier(selection.alias() == null ? "count" : selection.alias().text()));
                continue;
            }
            Binding binding = resolve(scope, selection.path());
            String label = selection.alias() == null ? selection.path().last().text() : selection.alias().text();
            columns.add(project(scope, binding) + " AS " + identifier(label));
        }
        String where = query.filter() == null ? "" : " AND (" + predicate(scope, query.filter()) + ")";

        var ordering = new ArrayList<String>();
        boolean aggregate = selections.getFirst().count();
        if (aggregate && !query.order().isEmpty()) {
            throw query.order().getFirst().path().first().error("ORDER BY is unavailable with COUNT(*)");
        }
        for (var order : query.order()) {
            var value = value(resolve(scope, order.path()), order.path().last());
            var scalar = scalar(scope, value, scale(value.type()));
            if (scalar.kind() == Kind.ENUMERATION) throw value.token().error("Enumeration ordering is not supported");
            ordering.add(scalar.sql() + (order.descending() ? " DESC NULLS LAST" : " ASC NULLS LAST"));
        }
        if (!aggregate) ordering.add(association ? row.associationId() : row.position());
        String sql = "SELECT " + String.join(", ", columns) + " FROM " + scope.sourceSql()
                + " WHERE " + scope.restriction + where + (ordering.isEmpty() ? "" : " ORDER BY " + String.join(", ", ordering))
                + " LIMIT " + Math.min(query.limit(), previewLimit + 1L);
        return new Iql.Compiled(sql, parameters, checks.stream().map(this::spatialCheck).toList());
    }

    private List<IqlSyntax.Selection> star(ObjectDescriptor owner, IqlSyntax.Token anchor) {
        var columns = owner.slots().stream().filter(slot -> !(slot instanceof AttributeSlot attr)
                || !attr.attribute().transientAttribute()).map(slot -> {
                    var field = new IqlSyntax.Token(IqlSyntax.Kind.WORD, shortName(slot), anchor.start(), anchor.end());
                    return new IqlSyntax.Selection(new IqlSyntax.Path(List.of(field)), null, false);
                }).toList();
        if (columns.isEmpty()) throw anchor.error("This source has no selectable fields");
        return columns;
    }

    private Binding resolve(Scope scope, IqlSyntax.Path path) {
        Binding binding = scope.alias(path.first().text());
        var names = path.segments();
        if (binding != null) names = names.subList(1, names.size());
        else binding = scope.implicit;
        for (var name : names) {
            Row row = binding instanceof Row object ? object : traverse(scope, (Value) binding);
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
                    text(value.raw(), "$[1]"), value.position(), value.path(), null, "NULL");
            case ReferenceType reference -> referenceTarget(scope, value, reference);
            case CollectionType ignored -> throw value.token().error("Use EXISTS to navigate a LIST or BAG");
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
        return new Row(type, object.fields(), object.typeName(), object.position(), "''", association + ".id", object.identity());
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

    private String predicate(Scope scope, IqlSyntax.Expression expression) {
        return predicate(scope, expression, false);
    }

    private String predicate(Scope scope, IqlSyntax.Expression expression, boolean distinguishUnknown) {
        return switch (expression) {
            case IqlSyntax.Junction junction -> "(" + predicate(scope, junction.left(), distinguishUnknown)
                    + " " + junction.operator() + " " + predicate(scope, junction.right(), distinguishUnknown) + ")";
            case IqlSyntax.Not not -> "(NOT " + predicate(scope, not.operand(), true) + ")";
            case IqlSyntax.Test test -> test(scope, test);
            case IqlSyntax.Comparison comparison -> compare(scope, comparison);
            case IqlSyntax.Exists exists -> exists(scope, exists);
            case IqlSyntax.Intersects intersects -> intersects(scope, intersects, distinguishUnknown);
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
        var left = comparison.left() instanceof IqlSyntax.Path path ? value(resolve(scope, path), path.last()) : null;
        var right = comparison.right() instanceof IqlSyntax.Path path ? value(resolve(scope, path), path.last()) : null;
        int scale = Math.max(operandScale(comparison.left(), left), operandScale(comparison.right(), right));
        if (scale > 38) throw comparison.operator().error("Exact numeric comparison exceeds DuckDB decimal precision");
        var leftKind = left == null ? kind((IqlSyntax.Literal) comparison.left(), right) : kind(left.type());
        var rightKind = right == null ? kind((IqlSyntax.Literal) comparison.right(), left) : kind(right.type());
        if (leftKind == null || leftKind != rightKind) throw comparison.operator().error("Values have incompatible comparison types");
        String operator = comparison.operator().text();
        if ((leftKind == Kind.ENUMERATION || leftKind == Kind.BOOLEAN) && !List.of("=", "!=", "<>").contains(operator)) {
            throw comparison.operator().error("This value type supports equality comparisons only");
        }
        String leftSql = left == null ? literal((IqlSyntax.Literal) comparison.left(), leftKind, scale, right)
                : scalar(scope, left, scale).sql();
        String rightSql = right == null ? literal((IqlSyntax.Literal) comparison.right(), rightKind, scale, left)
                : scalar(scope, right, scale).sql();
        return "(" + leftSql + " " + operator + " " + rightSql + ")";
    }

    private String literal(IqlSyntax.Literal literal, Kind kind, int scale, Value other) {
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
                if (other != null && other.type() instanceof EnumerationType type
                        && (type.values().stream().noneMatch(option -> option.path().equals(literal.value()))
                        || !type.treeValues() && type.values().stream().anyMatch(option -> option.path().startsWith(literal.value() + ".")))) {
                    throw literal.token().error("Unknown enumeration value #" + literal.value());
                }
                yield parameter(literal.value());
            }
            default -> parameter(literal.value());
        };
    }

    private int operandScale(IqlSyntax.Expression expression, Value value) {
        if (value != null) return scale(value.type());
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

    private static Kind kind(IqlSyntax.Literal literal, Value other) {
        return switch (literal.kind()) {
            case TEXT -> other != null && kind(other.type()) == Kind.DATE ? Kind.DATE : Kind.TEXT;
            case NUMBER -> Kind.NUMBER;
            case BOOLEAN -> Kind.BOOLEAN;
            case ENUMERATION -> Kind.ENUMERATION;
        };
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

    private Scope collectionScope(Scope parent, Value value, IqlSyntax.Token name) {
        if (!(value.type() instanceof CollectionType type)) throw value.token().error("EXISTS requires a LIST, BAG or multi-valued role");
        String item = alias();
        var element = new Value(model.type(type.elementType()), item + ".value", value.position(),
                value.path() + " || '/' || " + item + ".key", name, null, null);
        String from = "LATERAL json_each(" + value.raw() + ", '$[2]') " + item;
        if (element.type() instanceof StructureValueType structure) {
            var row = new Row(requiredTarget(structure, element), extract(element.raw(), "'$[2]'"),
                    text(element.raw(), "$[1]"), element.position(), element.path(), null, "NULL");
            return new Scope(parent, from, "TRUE", row, name.text(), row);
        }
        return new Scope(parent, from, "TRUE", parent.implicit, name.text(), element);
    }

    private Scope roleScope(Scope parent, Value value, IqlSyntax.Token name) {
        if (!value.role().multiple()) throw value.token().error("EXISTS requires a multi-valued role");
        var roots = value.role().target().role().targetRoots();
        var type = model.commonTarget(roots).orElseThrow(() -> value.token().error("No common target class for this role"));
        String role = alias();
        String target = alias();
        String from = "LATERAL (" + roles(value) + ") " + role + " JOIN object " + target
                + " ON " + target + ".position = " + role + ".target_position";
        var row = objectRow(type, target);
        return new Scope(parent, from, target + ".type_name IN " + typeNames(model.allowedTargets(roots)), row, name.text(), row);
    }

    private String intersects(Scope scope, IqlSyntax.Intersects predicate, boolean distinguishUnknown) {
        var value = value(resolve(scope, predicate.path()), predicate.path().last());
        if (!(value.type() instanceof GeometryType || value.type() instanceof CoordinateType)) {
            throw value.token().error("INTERSECTS requires a geometry attribute");
        }
        checks.add(new SpatialCheck(scope, value, predicate.path()));
        String match = alias();
        var box = predicate.box().stream().map(this::parameter).toList();
        // Keep the indexed column and the constant search envelope visible to DuckDB's optimizer.
        String matches = "EXISTS (SELECT 1 FROM (SELECT source_position, value_path FROM geometry"
                + " WHERE ST_Intersects(geom, ST_MakeEnvelope(" + String.join(", ", box) + "))) " + match
                + " WHERE " + geometryKey(match, value) + ")";
        if (!distinguishUnknown) return matches;
        String present = alias();
        return "(CASE WHEN EXISTS (SELECT 1 FROM geometry " + present + " WHERE " + geometryKey(present, value)
                + ") THEN " + matches + " END)";
    }

    private Iql.Check spatialCheck(SpatialCheck check) {
        Value value = check.value();
        String scope = check.scope().lineage().stream().map(Scope::preflightSourceSql).collect(Collectors.joining(" CROSS JOIN "));
        String where = check.scope().lineage().stream().map(layer -> layer.restriction).collect(Collectors.joining(" AND "));
        String sql = "SELECT CASE WHEN count(*) FILTER (WHERE g.projection_error IS NOT NULL) > 0"
                + " THEN 'A geometry on this path has no native projection'"
                + " WHEN count(*) FILTER (WHERE g.crs IS NULL) > 0 THEN 'The coordinate reference system is unknown on this path'"
                + " WHEN count(DISTINCT g.crs) > 1 THEN 'This path has more than one coordinate reference system' END"
                + " FROM " + scope + " JOIN geometry g ON " + geometryKey("g", value) + " WHERE " + where;
        return new Iql.Check(sql, check.path().first().start(), check.path().last().end());
    }

    private static Row objectRow(ObjectDescriptor type, String alias) {
        return new Row(type, alias + ".fields", alias + ".type_name", alias + ".position", "''", null,
                "COALESCE(" + alias + ".tid, " + alias + ".oid, CAST(" + alias + ".position AS VARCHAR))");
    }

    private static Row associationRow(ObjectDescriptor type, String alias) {
        return new Row(type, alias + ".fields", alias + ".type_name", alias + ".source_position", alias + ".value_path",
                alias + ".id", "CAST(" + alias + ".id AS VARCHAR)");
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
