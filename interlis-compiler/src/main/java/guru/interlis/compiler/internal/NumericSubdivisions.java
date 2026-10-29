package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import java.math.BigDecimal;
import java.util.*;

/** Contextual subdivision rules over the final, alias-resolved effective attribute order. */
final class NumericSubdivisions {
    private final GraphBuilder graph;
    private final ExactUnitFactors factors;
    NumericSubdivisions(GraphBuilder graph) { this.graph=graph;this.factors=new ExactUnitFactors(graph); }

    void validate() {
        Map<SymbolId,Optional<SymbolId>> originalPredecessors=new HashMap<>();
        for(var owner:graph.effectiveMembers.entrySet()) {
            Optional<SymbolId> previous=Optional.empty();
            for(var member:owner.getValue()) {
                graph.checkWork();
                if(!(graph.symbols.get(member.declaredSymbol()) instanceof AttributeSymbol attribute))continue;
                if(attribute.owner().filter(owner.getKey()::equals).isPresent())originalPredecessors.put(attribute.id(),previous);
                previous=Optional.of(attribute.id());
            }
        }
        record Relation(SymbolId attribute,Optional<SymbolId> previous) { }
        Set<Relation> checked=new HashSet<>();
        for(var owner:graph.effectiveMembers.entrySet()) {
            graph.checkWork();AttributeSymbol previous=null;
            for(var member:owner.getValue()) {
                graph.checkWork();
                if(!(graph.symbols.get(member.declaredSymbol()) instanceof AttributeSymbol attribute))continue;
                if(attribute.subdivision()) {
                    var preceding=previous==null?Optional.<SymbolId>empty():Optional.of(previous.id());
                    var relation=new Relation(attribute.id(),preceding);
                    boolean unchanged=preceding.equals(originalPredecessors.get(attribute.id()));
                    var declaration=unchanged?graph.index.byId(attribute.id()).orElse(null)
                            :previous==null?null:previous.owner().flatMap(graph.index::byId).orElse(null);
                    if(declaration==null)declaration=graph.index.byId(owner.getKey()).orElse(null);
                    // Predefined declarations have no user source. Their inherited
                    // uses are checked in the context of each declared extension.
                    if(declaration!=null&&checked.add(relation))validate(attribute,previous,declaration);
                }
                previous=attribute;
            }
        }
    }
    private void validate(AttributeSymbol attribute,AttributeSymbol previous,DeclInfo declaration) {
        if(previous==null) { error(declaration,"subdivision needs a preceding attribute","SUBDIVISION_PREDECESSOR");return; }
        var own=graph.effectiveDescriptor(attribute.declaredType());
        var predecessor=graph.effectiveDescriptor(previous.declaredType());
        if(unknown(own)||unknown(predecessor))return;
        if(!(own instanceof NumericType number)) {error(declaration,"subdivision must have a scalar numeric type","SUBDIVISION_NUMERIC");return;}
        if(!(predecessor instanceof NumericType parent)) {error(declaration,"subdivision predecessor must have a scalar numeric type","SUBDIVISION_PREDECESSOR_NUMERIC");return;}
        if(parent.range().minimum().filter(v->!integerPrecision(v)).isPresent()
                ||parent.range().maximum().filter(v->!integerPrecision(v)).isPresent()) {
            error(declaration,"subdivision predecessor must be integer-valued","SUBDIVISION_PREDECESSOR_INTEGER");return;
        }
        if(number.range().minimum().filter(v->v.exactValue().signum()<0).isPresent()
                ||number.range().maximum().filter(v->v.exactValue().signum()<0).isPresent()) {
            error(declaration,"subdivision range must be nonnegative","SUBDIVISION_NONNEGATIVE");return;
        }
        if(!attribute.continuous())return;
        if(!number.range().circular()) {error(declaration,"continuous subdivision must be CIRCULAR","SUBDIVISION_CIRCULAR");return;}
        if(number.range().minimum().isEmpty()||number.range().maximum().isEmpty()
                ||parent.range().unit().isEmpty()||number.range().unit().isEmpty())return;
        var parentUnit=graph.symbols.get(parent.range().unit().orElseThrow());
        if(parentUnit instanceof UnitSymbol unit && unit.abstractUnit()) {
            var specialization=guru.interlis.model.internal.TypeCompatibility.numericUnitExtension(number,parent,id->{
                graph.checkWork();return graph.symbols.get(id) instanceof UnitSymbol value?value:null;
            });
            if(specialization.decision()==RelationDecision.DISALLOWED)
                error(declaration,"subdivision unit cannot specialize the abstract predecessor unit","SUBDIVISION_FACTOR");
            return; // The concrete predecessor unit, and hence its factor, is still deferred.
        }
        graph.checkWork();var maximum=number.range().maximum().orElseThrow();
        var quantum=BigDecimal.ONE.scaleByPowerOfTen(maximum.precision().exponent()-maximum.precision().fractionalDigits());
        var minimum=number.range().minimum().orElseThrow().exactValue();
        chargeAlignment(minimum,maximum.exactValue(),quantum);
        var period=maximum.exactValue().subtract(minimum).add(quantum);
        if(factors.compare(parent.range().unit().orElseThrow(),number.range().unit().orElseThrow(),period)==ExactUnitFactors.Result.MISMATCH)
            error(declaration,"continuous subdivision period differs from the unit conversion factor","SUBDIVISION_FACTOR");
    }
    /** Decimal alignment may allocate a power of ten far larger than either
     * source literal when a caller increases the lexical exponent budget. */
    private void chargeAlignment(BigDecimal minimum,BigDecimal maximum,BigDecimal quantum) {
        long scale=Math.max(Math.max(minimum.scale(),maximum.scale()),quantum.scale());
        long digits=Math.max(Math.max((long)minimum.precision()+scale-minimum.scale(),
                (long)maximum.precision()+scale-maximum.scale()),1L+scale-quantum.scale());
        long words=1+digits/16,work=1+words*words;
        for(long i=0;i<work;i++)graph.checkWork();
    }
    private static boolean unknown(TypeDescriptor type) {return type==null||type instanceof ErrorType||type instanceof PendingType;}
    private static boolean integerPrecision(DecimalLiteral value) {return value.precision().exponent()>=value.precision().fractionalDigits();}
    private void error(DeclInfo declaration,String message,String reason) {graph.emitRule(declaration,message,reason);}
}
