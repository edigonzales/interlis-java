package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import guru.interlis.syntax.*;
import java.util.*;

/** Graphics within one compiler run, using its shared name, expression and type binders. */
final class GraphicBuilder {
    private final GraphBuilder g;
    private final Set<SymbolId> building = new HashSet<>();
    private final Map<SymbolId, SymbolId> contexts = new HashMap<>();
    GraphicBuilder(GraphBuilder graph) { g=graph; }
    SymbolId context(SymbolId owner) { return contexts.getOrDefault(owner,owner); }

    GraphicSymbol build(DeclInfo decl) {
        var syntax=(GraphicDeclarationSyntax)decl.syntax();
        if (!building.add(decl.id())) {
            g.emitRule(decl,"cyclic graphic inheritance","GRAPHIC_CYCLE");
            return new GraphicSymbol(g.header(decl),Optional.empty(),Optional.empty(),List.of(),List.of(),List.of());
        }
        var base=syntax.baseGraphic().flatMap(n->g.bindTarget(decl,n,Set.of(SymbolKind.GRAPHIC),ReferenceUsage.BASE_TYPE,new ArrayList<>(),false));
        GraphicSymbol parent=base.map(id->(GraphicSymbol)g.buildSymbol(g.index.byId(id).orElseThrow())).orElse(null);
        var basis=syntax.basis().flatMap(n->g.bindTarget(decl,n,Set.of(SymbolKind.CLASS,SymbolKind.VIEW),ReferenceUsage.TYPE_USE,new ArrayList<>(),false));
        if(basis.isEmpty() && parent!=null) basis=parent.basis();
        if(basis.isEmpty()) g.emitRule(decl,"graphic requires a bound class or view basis","GRAPHIC_BASIS");
        if(parent!=null) {
            if(parent.header().has(Modifier.FINAL)) g.emitRule(decl,"cannot extend FINAL graphic","GRAPHIC_FINAL");
            if(basis.isPresent() && parent.basis().isPresent()) {
                g.metadata.ensureBases(basis.get());
                if(!guru.interlis.model.internal.TypeCompatibility.specializes(basis.get(),parent.basis().get(),g.baseOf::get))
                    g.emitRule(decl,"graphic basis must specialize inherited basis","GRAPHIC_BASIS");
            }
        }
        if(syntax.endName().filter(n->!n.equals(decl.name())).isPresent()) g.emitRule(decl,"graphic END name mismatch","GRAPHIC_END_NAME");
        basis.ifPresent(id->{ g.index.byId(id).ifPresent(g::buildSymbol); contexts.put(decl.id(),id); });
        List<ExpressionId> selections=new ArrayList<>(parent==null?List.of():parent.selections());
        for(var selection:syntax.selections()) selections.add(condition(decl,selection));
        LinkedHashMap<String,SymbolId> effective=new LinkedHashMap<>();
        if(parent!=null) for(var id:parent.effectiveRules()) effective.put(g.symbols.get(id).name().value(),id);
        List<SymbolId> local=new ArrayList<>(); Set<String> names=new HashSet<>();
        for(var child:g.index.children(decl.id())) {
            g.checkWork();
            basis.ifPresent(id->contexts.put(child.id(),id));
            if(!names.add(child.name())) g.emitRule(child,"duplicate drawing rule","GRAPHIC_RULE_NAME");
            var rule=rule(child,Optional.ofNullable(effective.get(child.name())));
            g.symbols.put(rule.id(),rule); local.add(rule.id()); effective.put(child.name(),rule.id());
        }
        if(!decl.modifiers().contains(Modifier.ABSTRACT)) for(var id:effective.values()) {
            var rule=(DrawingRuleSymbol)g.symbols.get(id);
            if(rule.header().has(Modifier.ABSTRACT)) g.emitRule(decl,"concrete graphic retains abstract drawing rule","GRAPHIC_ABSTRACT_RULE");
        }
        var result=new GraphicSymbol(g.header(decl),base,basis,selections,local,List.copyOf(effective.values()));
        base.ifPresent(id->g.baseOf.put(decl.id(),id)); building.remove(decl.id()); return result;
    }

    private ExpressionId condition(DeclInfo decl,ExpressionSyntax syntax) {
        var result=g.buildExpression(syntax,decl.id());
        if(!result.error() && !g.isBoolean(result.typeId())) g.emitRule(decl,"graphic condition must be Boolean","GRAPHIC_CONDITION");
        return result.id();
    }

    private DrawingRuleSymbol rule(DeclInfo decl,Optional<SymbolId> inherited) {
        var syntax=(GraphicDeclarationSyntax.Rule)decl.syntax();
        var parent=inherited.map(id->(DrawingRuleSymbol)g.symbols.get(id)).orElse(null);
        if(inherited.isPresent()!=decl.modifiers().contains(Modifier.EXTENDED))
            g.emitRule(decl,"drawing-rule override requires inherited rule and EXTENDED","GRAPHIC_RULE_OVERRIDE");
        if(parent!=null && parent.header().has(Modifier.FINAL)) g.emitRule(decl,"cannot override FINAL rule","GRAPHIC_RULE_FINAL");
        var signature=syntax.signatureClass().flatMap(n->g.bindTarget(decl,n,Set.of(SymbolKind.CLASS),ReferenceUsage.TYPE_USE,new ArrayList<>(),false));
        if(signature.isEmpty() && parent!=null) signature=parent.signatureClass();
        if(signature.isEmpty() && !decl.modifiers().contains(Modifier.ABSTRACT)) g.emitRule(decl,"concrete drawing rule requires signature class","GRAPHIC_SIGNATURE");
        signature.ifPresent(id->{
            ensureBuilt(id);
            var sign=g.metadata.predefined(decl,"INTERLIS.SIGN",Set.of(SymbolKind.CLASS)).orElseThrow();
            if(!guru.interlis.model.internal.TypeCompatibility.specializes(id,sign,g.baseOf::get)) g.emitRule(decl,"drawing rule requires a SIGN class","GRAPHIC_SIGNATURE");
            if(parent!=null && parent.signatureClass().isPresent() && !guru.interlis.model.internal.TypeCompatibility.specializes(id,parent.signatureClass().get(),g.baseOf::get))
                g.emitRule(decl,"drawing signature must specialize base signature","GRAPHIC_SIGNATURE");
        });
        List<DrawingRuleSymbol.Clause> clauses=new ArrayList<>();
        for(var clause:syntax.clauses()) {
            g.checkWork();
            var condition=clause.condition().map(e->condition(decl,e));
            List<DrawingRuleSymbol.Assignment> assignments=new ArrayList<>();
            for(var assignment:clause.assignments()) {
                var parameter=signature.flatMap(id->g.metadata.parameter(id,assignment.parameter().text()));
                g.recordReference(decl,assignment.parameter(),parameter.orElse(null),ReferenceUsage.EXPRESSION);
                if(parameter.isEmpty()) g.emitRule(decl,"unknown signature parameter: "+assignment.parameter().text(),"GRAPHIC_PARAMETER");
                assignments.add(new DrawingRuleSymbol.Assignment(assignment.range(),parameter,value(decl,parameter,assignment.value())));
            }
            clauses.add(new DrawingRuleSymbol.Clause(clause.range(),condition,assignments));
        }
        var result=new DrawingRuleSymbol(g.header(decl),signature,inherited,clauses);
        return result;
    }

    private void ensureBuilt(SymbolId id) {
        Set<SymbolId> seen=new HashSet<>();
        while(id!=null && seen.add(id)) { g.checkWork(); g.index.byId(id).ifPresent(g::buildSymbol); id=g.baseOf.get(id); }
    }
    private List<SymbolId> parameters(SymbolId owner) {
        LinkedHashMap<String,SymbolId> result=new LinkedHashMap<>(); Set<SymbolId> seen=new HashSet<>();
        while(owner!=null && seen.add(owner)) {
            g.checkWork(); var s=g.symbols.get(owner);
            if(s instanceof ClassSymbol c) for(var id:c.members()) if(g.symbols.get(id) instanceof ParameterSymbol p) result.putIfAbsent(p.name().value(),id);
            owner=g.baseOf.get(owner);
        }
        return List.copyOf(result.values());
    }
    private void clauses(DrawingRuleSymbol rule, List<DrawingRuleSymbol.Clause> result, Set<SymbolId> seen) {
        if (!seen.add(rule.id())) return;
        g.checkWork();
        rule.overrides().ifPresent(id -> clauses((DrawingRuleSymbol)g.symbols.get(id), result, seen));
        result.addAll(rule.clauses());
    }

    /** Static assignment contexts, not truth assignments for conditions. Common
     * unconditional values are combined with each conditional clause. A partial
     * conditional extension also retains each earlier conditional Sign selection.
     * No Boolean/enum predicate is evaluated and no exponential path expansion is
     * performed. Every context applies assignments in their original source order. */
    private Set<List<DrawingRuleSymbol.Assignment>> assignmentContexts(DrawingRuleSymbol rule) {
        List<DrawingRuleSymbol.Clause> clauses = new ArrayList<>(); clauses(rule, clauses, new HashSet<>());
        Set<List<DrawingRuleSymbol.Assignment>> result = new LinkedHashSet<>();
        result.add(assignmentContext(clauses, -1, -1));
        for (int i = 0; i < clauses.size(); i++) {
            g.checkWork(); if (clauses.get(i).condition().isEmpty()) continue;
            result.add(assignmentContext(clauses, i, -1));
            if (hasSign(clauses.get(i))) continue;
            for (int j = 0; j < i; j++) {
                g.checkWork();
                if (clauses.get(j).condition().isPresent() && hasSign(clauses.get(j)))
                    result.add(assignmentContext(clauses, j, i));
            }
        }
        return result;
    }

    private boolean hasSign(DrawingRuleSymbol.Clause clause) {
        for (var assignment : clause.assignments()) {
            g.checkWork();
            if (assignment.parameter().map(g.symbols::get).filter(p -> p.name().value().equals("Sign")).isPresent()) return true;
        }
        return false;
    }

    private List<DrawingRuleSymbol.Assignment> assignmentContext(List<DrawingRuleSymbol.Clause> clauses, int first, int second) {
        Map<String, DrawingRuleSymbol.Assignment> values = new LinkedHashMap<>();
        for (int i = 0; i < clauses.size(); i++) {
            g.checkWork(); var clause = clauses.get(i);
            if (clause.condition().isPresent() && i != first && i != second) continue;
            for (var assignment : clause.assignments()) {
                g.checkWork(); assignment.parameter().ifPresent(id -> values.put(g.symbols.get(id).name().value(), assignment));
            }
        }
        return List.copyOf(values.values());
    }

    private void objects(DrawingRuleSymbol.Value value, Set<SymbolId> result) {
        g.checkWork();
        if (value instanceof DrawingRuleSymbol.MetaObjectValue object) object.object().ifPresent(result::add);
        else if (value instanceof DrawingRuleSymbol.AccordingValue according)
            for (var entry : according.cases()) objects(entry.value(), result);
    }

    /** Check each bound Sign alternative. This is static parameter presence/type
     * checking; conditions and ACCORDING values remain unevaluated in the model. */
    private void responsibility(DeclInfo decl, DrawingRuleSymbol rule) {
        if (rule.signatureClass().isEmpty() || rule.header().has(Modifier.ABSTRACT)) return;
        SymbolId declared = rule.signatureClass().orElseThrow();
        var contexts = assignmentContexts(rule);
        Set<String> assigned = new HashSet<>();
        for (var context : contexts) for (var assignment : context) {
            g.checkWork(); assigned.add(g.symbols.get(assignment.parameter().orElseThrow()).name().value());
        }
        if (!assigned.contains("Sign")) g.emitRule(decl, "missing signature parameter: Sign", "GRAPHIC_PARAMETER_MISSING");
        for (var used : contexts) checkContext(decl, declared, used, assigned);
    }

    private void checkContext(DeclInfo decl, SymbolId declared, List<DrawingRuleSymbol.Assignment> used, Set<String> assigned) {
        Set<SymbolId> signs = new LinkedHashSet<>(); boolean signExpression = false;
        for (var assignment : used) {
            g.checkWork();
            var original = g.symbols.get(assignment.parameter().orElseThrow());
            // Explicit METAOBJECT OF restrictions belong to the declared parameter
            // contract, even when its value was inherited and responsibility stays
            // at a base class. They must never be weakened by contextual promotion.
            g.metadata.parameter(declared, original.name().value()).filter(id -> !id.equals(original.id()))
                    .map(g.symbols::get).map(ParameterSymbol.class::cast)
                    .filter(p -> g.effectiveDescriptor(p.typeId()) instanceof MetaObjectType)
                    .ifPresent(p -> checkValue(decl, p, assignment.value()));
            if (original.name().value().equals("Sign")) {
                objects(assignment.value(), signs); signExpression |= assignment.value() instanceof DrawingRuleSymbol.ExpressionValue;
            }
        }
        Set<SymbolId> responsibleClasses = new LinkedHashSet<>();
        for (var object : signs) {
            g.checkWork();
            var actual = ((MetaObjectSymbol)g.symbols.get(object)).objectClass().orElse(null);
            if (actual == null) continue;
            if (!specializes(actual, declared) && !specializes(declared, actual)) {
                g.emitRule(decl, "metadata object class does not match signature parameter", "GRAPHIC_METAOBJECT_TYPE");
                continue;
            }
            SymbolId responsible = actual;
            for (var assignment : used) {
                g.checkWork(); var owner = g.symbols.get(assignment.parameter().orElseThrow()).owner().orElseThrow();
                if (specializes(responsible, owner)) continue;
                if (specializes(owner, responsible) && specializes(declared, owner)) responsible = owner;
                else g.emitRule(decl, "signature parameter owner cannot determine a compatible responsible class", "GRAPHIC_SIGNATURE_RESPONSIBILITY");
            }
            responsibleClasses.add(responsible);
        }
        // A non-object expression (e.g. UNDEFINED) retains the existing static
        // declared-signature checks; no runtime sign value is invented.
        if (signs.isEmpty() && signExpression) responsibleClasses.add(declared);
        for (SymbolId responsible : responsibleClasses) {
            g.checkWork();
            for (var id : parameters(responsible)) {
                var parameter = (ParameterSymbol)g.symbols.get(id);
                if ((parameter.mandatory() || parameter.name().value().equals("Sign")) && !assigned.contains(parameter.name().value()))
                    g.emitRule(decl, "missing signature parameter: " + parameter.name().value(), "GRAPHIC_PARAMETER_MISSING");
            }
            for (var assignment : used) {
                g.checkWork(); var original = g.symbols.get(assignment.parameter().orElseThrow());
                var effective = g.metadata.parameter(responsible, original.name().value()).map(g.symbols::get)
                        .map(ParameterSymbol.class::cast).orElse((ParameterSymbol)original);
                if (!effective.id().equals(original.id())) checkValue(decl, effective, assignment.value());
                if (!effective.name().value().equals("Sign")
                        && g.effectiveDescriptor(effective.typeId()) instanceof MetaObjectType meta && meta.target().isEmpty()) {
                    Set<SymbolId> targets = new LinkedHashSet<>(); objects(assignment.value(), targets);
                    for (SymbolId object : targets) {
                        g.checkWork(); var actual = ((MetaObjectSymbol)g.symbols.get(object)).objectClass();
                        if (actual.isPresent() && !specializes(actual.orElseThrow(), responsible))
                            g.emitRule(decl, "metadata object class does not match signature parameter", "GRAPHIC_METAOBJECT_TYPE");
                    }
                }
            }
        }
    }

    private boolean specializes(SymbolId actual, SymbolId base) {
        return guru.interlis.model.internal.TypeCompatibility.specializes(actual, base, g.baseOf::get);
    }

    /** An inherited value may now target an overridden parameter with a narrower
     * type. Check the already bound value without re-binding syntax/references. */
    private void checkValue(DeclInfo decl, ParameterSymbol parameter, DrawingRuleSymbol.Value value) {
        g.checkWork(); var expected = g.effectiveDescriptor(parameter.typeId());
        if (value instanceof DrawingRuleSymbol.ExpressionValue expression) {
            var actual = g.expressions.get(expression.expression());
            checkMandatoryValue(decl,parameter,actual);
            if (actual.typeState() != ExpressionTypeState.ERROR && !g.valueAssignable(actual, expected.id()))
                g.emitRule(decl, "incompatible signature parameter assignment", "GRAPHIC_PARAMETER_TYPE");
        } else if (value instanceof DrawingRuleSymbol.MetaObjectValue object && expected instanceof MetaObjectType type) {
            var actual = object.object().map(g.symbols::get).filter(MetaObjectSymbol.class::isInstance)
                    .map(MetaObjectSymbol.class::cast).flatMap(MetaObjectSymbol::objectClass);
            if (actual.isPresent() && type.target().isPresent() && !specializes(actual.orElseThrow(), type.target().orElseThrow()))
                g.emitRule(decl, "metadata object class does not match signature parameter", "GRAPHIC_METAOBJECT_TYPE");
        } else if (value instanceof DrawingRuleSymbol.AccordingValue according)
            for (var entry : according.cases()) checkValue(decl, parameter, entry.value());
    }

    private void checkMandatoryValue(DeclInfo declaration,ParameterSymbol parameter,TypedExpression value) {
        if(parameter.mandatory() && value.typeState()!=ExpressionTypeState.ERROR
                && guru.interlis.model.internal.TypeCompatibility.isUndefined(g.effectiveDescriptor(value.typeId())))
            g.emitRule(declaration,"MANDATORY signature parameter excludes undefined","GRAPHIC_PARAMETER_MANDATORY",value.range());
    }

    private DrawingRuleSymbol.Value value(DeclInfo decl,Optional<SymbolId> parameter,GraphicDeclarationSyntax.Value syntax) {
        TypeDescriptor expected=parameter.map(g.symbols::get).map(ParameterSymbol.class::cast).map(p->g.effectiveDescriptor(p.typeId())).orElse(null);
        if(syntax instanceof GraphicDeclarationSyntax.MetaObjectValue m) {
            var target=g.metadata.objectReference(decl,m.object());
            if(expected!=null && !(expected instanceof MetaObjectType)) g.emitRule(decl,"metadata object assigned to an ordinary parameter","GRAPHIC_PARAMETER_TYPE");
            if(expected instanceof MetaObjectType type) {
                var required=type.target();
                var actual=target.map(g.symbols::get).filter(MetaObjectSymbol.class::isInstance).map(MetaObjectSymbol.class::cast).flatMap(MetaObjectSymbol::objectClass);
                if(actual.isPresent() && required.isPresent() && !guru.interlis.model.internal.TypeCompatibility.specializes(actual.get(),required.get(),g.baseOf::get))
                    g.emitRule(decl,"metadata object class does not match signature parameter","GRAPHIC_METAOBJECT_TYPE");
            }
            return new DrawingRuleSymbol.MetaObjectValue(target);
        }
        if(syntax instanceof GraphicDeclarationSyntax.ExpressionValue e) {
            var expression=g.buildExpression(e.expression(),decl.id());
            if(expected!=null)expression=g.contextualValue(expression,expected.id(),decl.id());
            if(!expression.error() && parameter.isPresent())checkMandatoryValue(decl,(ParameterSymbol)g.symbols.get(parameter.orElseThrow()),g.expressions.get(expression.id()));
            if(expected!=null && !expression.error() && !g.assignable(expression.typeId(),expected.id()))
                g.emitRule(decl,"incompatible signature parameter assignment","GRAPHIC_PARAMETER_TYPE");
            return new DrawingRuleSymbol.ExpressionValue(expression.id());
        }
        var according=(GraphicDeclarationSyntax.AccordingValue)syntax;
        var selector=g.buildExpression(according.selector(),decl.id());
        var type=g.effectiveDescriptor(selector.typeId());
        if(!(type instanceof EnumerationType)) g.emitRule(decl,"ACCORDING requires an enumeration attribute","GRAPHIC_ENUM_SELECTOR");
        List<DrawingRuleSymbol.EnumAssignment> cases=new ArrayList<>();
        for(var entry:according.cases()) {
            if(type instanceof EnumerationType enumeration) {
                List<String> paths=enumeration.values().stream().map(EnumerationValue::path).toList();
                boolean valid=paths.contains(entry.first());
                if(entry.last().isPresent()) {
                    var last=entry.last().get();
                    valid &= enumeration.ordered() && paths.contains(last) && parent(entry.first()).equals(parent(last)) && paths.indexOf(entry.first())<=paths.indexOf(last);
                }
                if(!valid) g.emitRule(decl,"invalid enumeration node or ordered sibling range","GRAPHIC_ENUM_RANGE");
            }
            cases.add(new DrawingRuleSymbol.EnumAssignment(entry.range(),entry.first(),entry.last(),value(decl,parameter,entry.value())));
        }
        return new DrawingRuleSymbol.AccordingValue(selector.id(),cases);
    }
    private String parent(String path) { int dot=path.lastIndexOf('.'); return dot<0?"":path.substring(0,dot); }

    void validateDependencies() {
        for(var symbol:g.symbols.values()) if(symbol instanceof GraphicSymbol graphic && graphic.basis().isPresent()) {
            var decl=g.index.byId(graphic.id()).orElseThrow();
            for (var id : graphic.rules()) responsibility(g.index.byId(id).orElseThrow(), (DrawingRuleSymbol)g.symbols.get(id));
            var source=g.scopeRoot(graphic.id()); var target=g.scopeRoot(graphic.basis().get());
            if(g.symbols.get(source) instanceof TopicSymbol && g.symbols.get(target) instanceof TopicSymbol
                    && !g.isTopicSubtype(source,target) && !g.topicDependencies(source).contains(target))
                g.emitRule(decl,"graphic basis topic requires DEPENDS ON","GRAPHIC_TOPIC_DEPENDENCY");
        }
    }
}
