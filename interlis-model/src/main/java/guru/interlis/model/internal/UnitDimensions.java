package guru.interlis.model.internal;

import guru.interlis.model.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

/** Dimension mismatch proofs, distinct from declared conversion permission or a numerical factor. */
final class UnitDimensions {
    private UnitDimensions() { }
    private sealed interface Atom {
        record Declared(SymbolId id) implements Atom { }
        record Predefined(String key) implements Atom { }
        record Opaque(SymbolId id) implements Atom { }
    }
    static boolean definitelyDifferent(SymbolId left,SymbolId right,Function<SymbolId,UnitSymbol> lookup,Runnable step) {
        record Visit(SymbolId id,boolean exit) { }
        var dimensions=new HashMap<SymbolId,Map<Atom,BigInteger>>();
        var pending=new ArrayDeque<Visit>();var active=new HashSet<SymbolId>();
        pending.push(new Visit(left,false));pending.push(new Visit(right,false));
        while(!pending.isEmpty()) {
            step.run();var visit=pending.pop();if(dimensions.containsKey(visit.id()))continue;
            var unit=lookup.apply(visit.id());if(unit==null)return false;
            List<SymbolId> dependencies;
            if(!unit.components().isEmpty())dependencies=unit.components().stream().map(UnitSymbol.Component::unit).toList();
            else if(unit.derivedFrom().isPresent())dependencies=List.of(unit.derivedFrom().orElseThrow());
            else if(unit.base().isPresent()) {
                var base=lookup.apply(unit.base().orElseThrow());if(base==null)return false;
                dependencies=isPredefined(base,"ANYUNIT")?List.of():List.of(base.id());
            } else dependencies=List.of();
            if(!visit.exit()) {
                if(!active.add(visit.id()))return false;
                pending.push(new Visit(visit.id(),true));
                for(var dependency:dependencies)if(!dimensions.containsKey(dependency))pending.push(new Visit(dependency,false));
                continue;
            }
            if(unit.components().isEmpty()&&!dependencies.isEmpty()) {
                dimensions.put(visit.id(),dimensions.get(dependencies.getFirst()));
                active.remove(visit.id());continue;
            }
            Map<Atom,BigInteger> result=new HashMap<>();
            if(dependencies.isEmpty()) {
                if(!isPredefined(unit,"DIMENSIONLESS")) {
                    Atom atom=unit.abstractUnit()&&!isPredefined(unit,"ANYUNIT")
                            ?unit.origin() instanceof Origin.PredefinedOrigin p?new Atom.Predefined(p.key()):new Atom.Declared(unit.id())
                            :new Atom.Opaque(unit.id());
                    result.put(atom,BigInteger.ONE);
                }
            } else if(!unit.components().isEmpty()) {
                for(var component:unit.components())merge(result,dimensions.get(component.unit()),
                        component.operator()==UnitSymbol.Operator.DIVIDE,step);
            }
            dimensions.put(visit.id(),Map.copyOf(result));active.remove(visit.id());
        }
        var difference=new HashMap<>(dimensions.get(left));merge(difference,dimensions.get(right),true,step);
        return !difference.isEmpty()&&difference.keySet().stream().noneMatch(Atom.Opaque.class::isInstance);
    }
    private static boolean isPredefined(UnitSymbol unit,String name) {
        return unit.origin() instanceof Origin.PredefinedOrigin p&&p.key().equals("INTERLIS."+name);
    }
    private static void merge(Map<Atom,BigInteger> result,Map<Atom,BigInteger> source,boolean inverse,Runnable step) {
        for(var entry:source.entrySet()) {
            step.run();var previous=result.getOrDefault(entry.getKey(),BigInteger.ZERO);
            var value=inverse?entry.getValue().negate():entry.getValue();
            long work=1+((long)previous.bitLength()+value.bitLength())/64;
            for(long i=0;i<work;i++)step.run();
            var total=previous.add(value);if(total.signum()==0)result.remove(entry.getKey());else result.put(entry.getKey(),total);
        }
    }
}
