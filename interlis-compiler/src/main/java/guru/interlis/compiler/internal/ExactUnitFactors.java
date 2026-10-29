package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import guru.interlis.model.internal.TypeCompatibility;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Exact factor comparisons, without evaluating FUNCTION explanations or rounding named constants. */
final class ExactUnitFactors {
    enum Result { MATCH, MISMATCH, UNKNOWN }
    private final GraphBuilder graph;
    private final Map<SymbolId,Integer> ranks=new HashMap<>();
    ExactUnitFactors(GraphBuilder graph) {this.graph=graph;}

    Result compare(SymbolId coarse,SymbolId fine,BigDecimal period) {
        var relation=TypeCompatibility.unitRelation(coarse,fine,this::unit);
        if(relation.isEmpty())return Result.UNKNOWN; // Existing unit diagnostics own invalid dependencies.
        if(relation.orElseThrow().decision()==RelationDecision.DISALLOWED)
            return TypeCompatibility.definitelyDifferentUnitDimensions(coarse,fine,this::unit,graph::checkWork)?Result.MISMATCH:Result.UNKNOWN;
        if(coarse.equals(fine))return period.compareTo(BigDecimal.ONE)==0?Result.MATCH:Result.MISMATCH;
        if(!rank(coarse)||!rank(fine))return Result.UNKNOWN;
        var queue=new PriorityQueue<SymbolId>(Comparator.<SymbolId>comparingInt(ranks::get).reversed().thenComparing(SymbolId::value));
        Map<SymbolId,BigInteger> terms=new HashMap<>();
        Map<SymbolId,BigInteger> roots=new HashMap<>();
        var numbers=new TreeMap<BigDecimal,BigInteger>();
        var constants=new EnumMap<UnitSymbol.ConstantKind,BigInteger>(UnitSymbol.ConstantKind.class);
        enqueue(terms,queue,coarse,BigInteger.ONE);enqueue(terms,queue,fine,BigInteger.ONE.negate());
        while(!queue.isEmpty()) {
            graph.checkWork();var id=queue.remove();var power=terms.remove(id);if(power==null)continue;
            var unit=unit(id);if(unit==null)return Result.UNKNOWN;
            if(unit.conversionFunction().isPresent()||unit.derivedFrom().isEmpty()&&unit.components().isEmpty()) {
                add(roots,id,power);continue;
            }
            if(unit.derivedFrom().isPresent()) {
                for(var factor:unit.conversionFactors()) {
                    graph.checkWork();var signed=factor.operator()==UnitSymbol.Operator.MULTIPLY?power:power.negate();
                    if(factor.kind()==UnitSymbol.ConstantKind.DECIMAL) {
                        var number=factor.decimal().orElseThrow().exactValue();
                        if(number.signum()==0)return Result.UNKNOWN;
                        add(numbers,number,signed);
                    } else add(constants,factor.kind(),signed);
                }
                enqueue(terms,queue,unit.derivedFrom().orElseThrow(),power);
            } else for(var component:unit.components()) {
                graph.checkWork();enqueue(terms,queue,component.unit(),component.operator()==UnitSymbol.Operator.MULTIPLY?power:power.negate());
            }
        }
        // A common opaque base cancels before its definition is inspected.
        // A remaining FUNCTION root does not establish a constant numeric ratio.
        if(!roots.isEmpty())return Result.UNKNOWN;
        if(!constants.isEmpty()) {
            // A nonzero integral power of either transcendental alone remains
            // transcendental, and cannot equal a nonzero finite decimal period.
            // No algebraic independence of PI and LNBASE is assumed.
            return constants.size()==1?Result.MISMATCH:Result.UNKNOWN;
        }
        add(numbers,period,BigInteger.ONE.negate());
        Fraction result=new Fraction(BigInteger.ONE,BigInteger.ONE);
        for(var number:numbers.entrySet()) {
            graph.checkWork();result=result.multiply(decimal(number.getKey()).power(number.getValue()));
        }
        return result.numerator.equals(result.denominator)?Result.MATCH:Result.MISMATCH;
    }

    private UnitSymbol unit(SymbolId id) {graph.checkWork();return graph.symbols.get(id) instanceof UnitSymbol unit?unit:null;}
    private static List<SymbolId> dependencies(UnitSymbol unit) {
        if(unit.conversionFunction().isPresent())return List.of();
        if(unit.derivedFrom().isPresent())return List.of(unit.derivedFrom().orElseThrow());
        return unit.components().stream().map(UnitSymbol.Component::unit).toList();
    }
    private boolean rank(SymbolId root) {
        record Visit(SymbolId id,boolean exit) { }
        var pending=new ArrayDeque<Visit>();var active=new HashSet<SymbolId>();pending.push(new Visit(root,false));
        while(!pending.isEmpty()) {
            graph.checkWork();var visit=pending.pop();if(ranks.containsKey(visit.id()))continue;
            var unit=unit(visit.id());if(unit==null)return false;var dependencies=dependencies(unit);
            if(!visit.exit()) {
                if(!active.add(visit.id()))return false;
                pending.push(new Visit(visit.id(),true));
                for(var dependency:dependencies)if(!ranks.containsKey(dependency))pending.push(new Visit(dependency,false));
            } else {
                int rank=0;for(var dependency:dependencies) {graph.checkWork();rank=Math.max(rank,ranks.get(dependency)+1);}
                ranks.put(visit.id(),rank);active.remove(visit.id());
            }
        }
        return true;
    }
    private static <K> void add(Map<K,BigInteger> terms,K key,BigInteger power) {
        var total=terms.getOrDefault(key,BigInteger.ZERO).add(power);
        if(total.signum()==0)terms.remove(key);else terms.put(key,total);
    }
    private static void enqueue(Map<SymbolId,BigInteger> terms,PriorityQueue<SymbolId> queue,SymbolId id,BigInteger power) {
        add(terms,id,power);if(terms.containsKey(id))queue.add(id);
    }
    private Fraction decimal(BigDecimal value) {
        graph.checkWork();int scale=value.scale();
        chargeBits(4L*Math.abs((long)scale));
        var ten=BigInteger.TEN.pow(Math.abs(scale));
        return scale>=0?new Fraction(value.unscaledValue(),ten):new Fraction(product(value.unscaledValue(),ten),BigInteger.ONE);
    }
    private BigInteger gcd(BigInteger left,BigInteger right) {
        graph.checkWork();if(left.abs().equals(right.abs()))return left.abs();
        if(left.abs().equals(BigInteger.ONE)||right.abs().equals(BigInteger.ONE))return BigInteger.ONE;
        charge(left.bitLength(),right.bitLength());return left.gcd(right);
    }
    private BigInteger product(BigInteger left,BigInteger right) {
        graph.checkWork();charge(left.bitLength(),right.bitLength());return left.multiply(right);
    }
    private void charge(long leftBits,long rightBits) {
        long work=1+leftBits*rightBits/4096;
        for(long i=0;i<work;i++)graph.checkWork();
    }
    private void chargeBits(long bits) {for(long i=0;i<1+bits/64;i++)graph.checkWork();}
    private final class Fraction {
        final BigInteger numerator,denominator;
        Fraction(BigInteger numerator,BigInteger denominator) {
            if(denominator.signum()==0)throw new IllegalArgumentException("zero denominator");
            if(denominator.signum()<0) {numerator=numerator.negate();denominator=denominator.negate();}
            var common=gcd(numerator,denominator);this.numerator=numerator.divide(common);this.denominator=denominator.divide(common);
        }
        Fraction multiply(Fraction other) {
            var first=gcd(numerator,other.denominator);var second=gcd(other.numerator,denominator);
            return new Fraction(product(numerator.divide(first),other.numerator.divide(second)),
                    product(denominator.divide(second),other.denominator.divide(first)));
        }
        Fraction power(BigInteger power) {
            graph.checkWork();Fraction base=this;
            if(power.signum()<0) {base=new Fraction(denominator,numerator);power=power.negate();}
            if(base.numerator.equals(base.denominator))return base;
            Fraction result=new Fraction(BigInteger.ONE,BigInteger.ONE);
            while(power.signum()!=0) {
                graph.checkWork();if(power.testBit(0))result=result.multiply(base);
                power=power.shiftRight(1);if(power.signum()!=0)base=base.multiply(base);
            }
            return result;
        }
    }
}
