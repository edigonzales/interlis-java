package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.Origin;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;
import guru.interlis.model.TypeKind;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Planned lookup rows. The transfer continues to use the stored attribute value. */
record DbEnumLookup(String table, Optional<String> mappedName, boolean create, List<Row> rows) {
    static final String BOOLEAN_DEFINITION = "INTERLIS.BOOLEAN";
    DbEnumLookup {
        rows = List.copyOf(rows);
    }

    record Row(String definition, String base, String path, int itfCode, Integer sequence, String label, String documentation) { }

    record Definition(Symbol declaration, EnumerationType type) { }

    static List<DbEnumLookup> plan(DbMapping mapping, SqlNames names, DbEnumOptions options, CancellationToken token) {
        if (options.lookup() == DbEnumOptions.Lookup.NONE) return List.of();
        var plan = mapping.plan();
        var objects = mapping.inheritance().declarations().stream().map(DbInheritance.Declaration::object).toList();
        var definitions = definitions(plan, DbMapping.storageRoots(plan, mapping.crsPair()), objects, token);
        if (options.lookup() == DbEnumOptions.Lookup.SINGLE) {
            var rows = new ArrayList<Row>();
            for (var definition : definitions) rows.addAll(rows(plan, definition, options.beautify(), token));
            return List.of(new DbEnumLookup(Ili2dbLayout.ENUM, Optional.empty(), !names.hasTable(Ili2dbLayout.ENUM), rows));
        }
        var lookups = new ArrayList<DbEnumLookup>();
        if (options.lookup() == DbEnumOptions.Lookup.PER_DEFINITION && hasBoolean(plan, objects)) {
            String table = names.mappedTable(BOOLEAN_DEFINITION, "INTERLIS", null, "BOOLEAN");
            lookups.add(new DbEnumLookup(table, Optional.of(BOOLEAN_DEFINITION), !names.hasClass(BOOLEAN_DEFINITION),
                    List.of(new Row(BOOLEAN_DEFINITION, null, "false", 0, null, "false", null),
                            new Row(BOOLEAN_DEFINITION, null, "true", 1, null, "true", null))));
        }
        for (var definition : definitions) {
            if (options.lookup() == DbEnumOptions.Lookup.WITH_ID && DbEnumeration.base(plan, definition.declaration()) != null) {
                throw DbMapping.unsupported("creation of enumeration ID tables for extended definitions");
            }
            String path = name(plan, definition.declaration());
            String table = table(plan, names, definition.declaration(), path);
            lookups.add(new DbEnumLookup(table, Optional.of(path), !names.hasClass(path),
                    rows(plan, definition, options.beautify(), token)));
        }
        return List.copyOf(lookups);
    }

    static boolean hasBoolean(TransferPlan plan, List<ObjectDescriptor> objects) {
        return objects.stream().flatMap(object -> DbMapping.attributes(object).stream())
                .anyMatch(slot -> plan.type(slot.effectiveType()).known().orElseThrow().kind() == TypeKind.BOOLEAN);
    }

    private static List<Row> rows(TransferPlan plan, Definition definition, boolean beautify, CancellationToken token) {
        var symbol = definition.declaration();
        var base = DbEnumeration.base(plan, symbol);
        var elements = DbEnumeration.elements(plan, symbol);
        String name = name(plan, symbol);
        String parent = base == null ? null : name(plan, base);
        String language = DbEnumText.language(plan, symbol);
        var rows = new ArrayList<Row>();
        int code = 0;
        for (var value : DbEnumeration.values(definition.type())) {
            token.throwIfCancellationRequested();
            var element = elements.get(value.path());
            String label = element == null ? null : DbEnumText.displayName(element, language);
            String doc = element == null || element.header().documentation().isEmpty()
                    ? null : element.header().documentation().text();
            rows.add(new Row(name, parent, value.path(), code, definition.type().ordered() ? code : null,
                    label == null ? DbEnumText.fallback(value.path(), beautify) : label, doc));
            code++;
        }
        return rows;
    }

    static List<Definition> definitions(TransferPlan plan, List<SymbolRef> models, List<ObjectDescriptor> objects,
                                        CancellationToken token) {
        var roots = models.stream().map(SymbolRef::symbol).collect(Collectors.toSet());
        var definitions = new LinkedHashMap<SymbolId, Definition>();
        for (var symbol : plan.schema().semanticGraph().symbols()) {
            if (!(symbol instanceof DomainSymbol domain) || !(domain.header().origin() instanceof Origin.DeclaredOrigin)) continue;
            token.throwIfCancellationRequested();
            if (roots.contains(model(plan, domain).id())
                    && plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow() instanceof EnumerationType type) {
                definitions.put(domain.id(), new Definition(domain, type));
            }
        }
        for (var object : objects) {
            for (var slot : DbMapping.attributes(object)) {
                var effective = plan.type(slot.effectiveType()).known().orElseThrow();
                if (!(effective instanceof EnumerationType)) continue;
                token.throwIfCancellationRequested();
                var declaration = DbEnumeration.definition(plan, slot);
                var type = declaration instanceof DomainSymbol domain
                        ? plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow()
                        : effective;
                if (type instanceof EnumerationType enumeration) {
                    definitions.putIfAbsent(declaration.id(), new Definition(declaration, enumeration));
                }
            }
        }
        return List.copyOf(definitions.values());
    }

    private static String table(TransferPlan plan, SqlNames names, Symbol declaration, String path) {
        if (names.hasClass(path)) return names.storedClass(path);
        String model = model(plan, declaration).name().value();
        var parent = plan.symbol(new SymbolRef(plan.snapshotId(), declaration.owner().orElseThrow())).known().orElseThrow();
        String local = declaration.name().value();
        String table;
        if (declaration instanceof DomainSymbol) {
            table = names.table(model, parent instanceof ModelSymbol ? null : parent.name().value(), local);
        } else {
            var object = plan.object(new SymbolRef(plan.snapshotId(), parent.id())).orElseThrow();
            String topic = object.topic().map(id -> plan.topic(id).orElseThrow().name().declared().last()).orElse(null);
            table = names.geometryTable(model, topic, parent.name().value(), local);
        }
        if (Ili2dbLayout.reservedTable(table, false) || Ili2dbLayout.reservedTable(table, true)) {
            throw DbMapping.unsupported("enumeration " + path + " maps to technical table " + table);
        }
        return table;
    }

    private static ModelSymbol model(TransferPlan plan, Symbol symbol) {
        while (!(symbol instanceof ModelSymbol)) {
            symbol = plan.symbol(new SymbolRef(plan.snapshotId(), symbol.owner().orElseThrow())).known().orElseThrow();
        }
        return (ModelSymbol) symbol;
    }

    static String name(TransferPlan plan, Symbol symbol) {
        return symbol.owner().map(id -> name(plan, plan.symbol(new SymbolRef(plan.snapshotId(), id)).known().orElseThrow())
                + "." + symbol.name().value()).orElseGet(() -> symbol.name().value());
    }
}
