package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.Origin;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** Planned lookup rows. The transfer continues to use the stored attribute value. */
record DbEnumLookup(String table, List<Row> rows) {
    DbEnumLookup {
        rows = List.copyOf(rows);
    }

    record Row(String definition, String base, String path, int itfCode, Integer sequence, String label, String documentation) { }

    private record Definition(Symbol declaration, EnumerationType type) { }

    static List<DbEnumLookup> plan(DbMapping mapping, DbEnumOptions options, CancellationToken token) {
        if (options.lookup() == DbEnumOptions.Lookup.NONE) return List.of();
        var plan = mapping.plan();
        var rows = new ArrayList<Row>();
        for (var definition : definitions(mapping, token)) {
            token.throwIfCancellationRequested();
            var symbol = definition.declaration();
            var base = DbEnumeration.base(plan, symbol);
            var elements = DbEnumeration.elements(plan, symbol);
            String name = name(plan, symbol);
            String parent = base == null ? null : name(plan, base);
            String language = DbEnumText.language(plan, symbol);
            int code = 0;
            for (var value : DbEnumeration.values(definition.type())) {
                var element = elements.get(value.path());
                String label = element == null ? null : DbEnumText.displayName(element, language);
                String doc = element == null || element.header().documentation().isEmpty()
                        ? null : element.header().documentation().text();
                rows.add(new Row(name, parent, value.path(), code, definition.type().ordered() ? code : null,
                        label == null ? DbEnumText.fallback(value.path(), options.beautify()) : label, doc));
                code++;
            }
        }
        return List.of(new DbEnumLookup(Ili2dbLayout.ENUM, rows));
    }

    private static List<Definition> definitions(DbMapping mapping, CancellationToken token) {
        var plan = mapping.plan();
        var roots = mapping.models().stream().map(ref -> plan.model(ref).orElseThrow().name().canonical().symbol())
                .collect(Collectors.toSet());
        var definitions = new LinkedHashMap<guru.interlis.model.SymbolId, Definition>();
        for (var symbol : plan.schema().semanticGraph().symbols()) {
            if (!(symbol instanceof DomainSymbol domain) || !(domain.header().origin() instanceof Origin.DeclaredOrigin)) continue;
            token.throwIfCancellationRequested();
            if (roots.contains(model(plan, domain).id())
                    && plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow() instanceof EnumerationType type) {
                definitions.put(domain.id(), new Definition(domain, type));
            }
        }
        for (var table : mapping.tables()) {
            for (var column : table.columns()) {
                if (column.kind() != DbMapping.Kind.ENUMERATION || !(column.slot() instanceof AttributeSlot slot)) continue;
                token.throwIfCancellationRequested();
                var declaration = DbEnumeration.definition(plan, slot);
                var type = declaration instanceof DomainSymbol domain
                        ? plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow()
                        : column.effectiveType().orElseThrow();
                if (type instanceof EnumerationType enumeration) {
                    definitions.putIfAbsent(declaration.id(), new Definition(declaration, enumeration));
                }
            }
        }
        return List.copyOf(definitions.values());
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
