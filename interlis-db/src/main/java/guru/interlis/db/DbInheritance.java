package guru.interlis.db;

import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves the per-declaration ili2db strategies into physical relations. */
final class DbInheritance {
    enum Profile { NEW_CLASS, SMART1, SMART2 }
    enum Strategy {
        NEW_CLASS("newClass"), SUPER_CLASS("superClass"), SUB_CLASS("subClass"), NEW_AND_SUB_CLASS("newAndSubClass");

        final String value;
        Strategy(String value) { this.value = value; }

        boolean createsTable() { return this == NEW_CLASS || this == NEW_AND_SUB_CLASS; }
        boolean copiesFields() { return this == SUB_CLASS || this == NEW_AND_SUB_CLASS; }
    }

    record Declaration(ObjectDescriptor object, String sqlName, Strategy strategy) { }
    record Relation(Declaration declaration, Optional<SymbolRef> base, List<ObjectDescriptor> contributors,
                    boolean discriminator) {
        Relation {
            contributors = List.copyOf(contributors);
        }
        ObjectDescriptor object() { return declaration.object(); }
    }

    private final List<Declaration> declarations;
    private final List<Relation> relations;
    private final Map<SymbolRef, SymbolRef> owners;

    DbInheritance(TransferPlan plan, List<Declaration> declarations, Profile profile) {
        this.declarations = List.copyOf(declarations);
        var strategies = new LinkedHashMap<SymbolRef, Strategy>();
        for (var declaration : declarations) strategies.put(declaration.object().symbol(), declaration.strategy());
        var placements = new LinkedHashMap<SymbolRef, SymbolRef>();
        for (var declaration : declarations) {
            var object = declaration.object();
            if (declaration.strategy() == Strategy.SUB_CLASS) {
                if (!object.modifiers().contains(Modifier.ABSTRACT)) throw invalid(object);
                continue;
            }
            SymbolRef owner = object.symbol();
            if (declaration.strategy() == Strategy.SUPER_CLASS) {
                owner = nearestTable(plan, object, strategies, true).orElseThrow(() -> invalid(object));
            }
            placements.put(object.symbol(), owner);
        }
        owners = Map.copyOf(placements);
        var bases = new LinkedHashMap<SymbolRef, Optional<SymbolRef>>();
        for (var declaration : declarations) {
            if (declaration.strategy().createsTable()) {
                bases.put(declaration.object().symbol(), nearestTable(plan, declaration.object(), strategies, false));
            }
        }
        var typesByRoot = new LinkedHashMap<SymbolRef, List<ObjectDescriptor>>();
        for (var declaration : declarations) {
            var object = declaration.object();
            var owner = owners.get(object.symbol());
            if (owner == null) continue;
            while (bases.get(owner).isPresent()) owner = bases.get(owner).orElseThrow();
            typesByRoot.computeIfAbsent(owner, unused -> new ArrayList<>()).add(object);
        }
        var relations = new ArrayList<Relation>();
        for (var declaration : declarations) {
            if (!declaration.strategy().createsTable()) continue;
            var object = declaration.object();
            var ref = object.symbol();
            var contributors = new ArrayList<ObjectDescriptor>();
            contributors.add(object);
            for (var parent = base(plan, object); parent != null && strategies.get(parent.symbol()).copiesFields();
                 parent = base(plan, parent)) {
                contributors.add(parent);
            }
            for (var extension : declarations) {
                if (extension.strategy() == Strategy.SUPER_CLASS && ref.equals(owners.get(extension.object().symbol()))) {
                    contributors.add(extension.object());
                }
            }
            var physicalBase = bases.get(ref);
            boolean discriminator = physicalBase.isEmpty() && typesByRoot.get(ref).stream().anyMatch(type ->
                    !ref.equals(type.symbol()) && (profile == Profile.NEW_CLASS || !type.modifiers().contains(Modifier.ABSTRACT)));
            relations.add(new Relation(declaration, physicalBase, contributors, discriminator));
        }
        this.relations = List.copyOf(relations);
    }

    List<Relation> relations() { return relations; }

    List<DbMapping.StorageAlias> aliases() {
        return declarations.stream().filter(entry -> entry.strategy() == Strategy.SUPER_CLASS)
                .map(entry -> new DbMapping.StorageAlias(entry.object(), entry.sqlName(), owners.get(entry.object().symbol())))
                .toList();
    }

    private static Optional<SymbolRef> nearestTable(TransferPlan plan, ObjectDescriptor object,
                                                     Map<SymbolRef, Strategy> strategies, boolean includeFullTables) {
        for (var parent = base(plan, object); parent != null; parent = base(plan, parent)) {
            var strategy = strategies.get(parent.symbol());
            if (strategy == null) throw invalid(object);
            if (strategy == Strategy.NEW_CLASS || (includeFullTables && strategy.createsTable())) {
                return Optional.of(parent.symbol());
            }
        }
        return Optional.empty();
    }

    private static ObjectDescriptor base(TransferPlan plan, ObjectDescriptor object) {
        return object.baseType().flatMap(plan::object).orElse(null);
    }

    static DbException invalid(ObjectDescriptor object) {
        return DbMapping.unsupported("inheritance TRAFO for " + object.name().declared());
    }
}
