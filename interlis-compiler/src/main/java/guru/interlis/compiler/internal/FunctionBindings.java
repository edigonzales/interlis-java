package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.*;
import java.util.*;

/** Dependent function arguments within one compiler run. Uses the common type
 * and path binders; owns no second graph, source parser or name semantics. */
final class FunctionBindings {
    private final GraphBuilder g;
    FunctionBindings(GraphBuilder graph) { g=graph; }

    record Result(boolean error, Map<Integer,FunctionCallExpression.ArgumentPath> paths) {
        Result { paths=Map.copyOf(paths); }
        static Result empty() { return new Result(false,Map.of()); }
    }

    /** Bind actuals in dependency order while preserving their source positions.
     * A formal ATTRIBUTE OF may refer to a later formal; traversal is iterative
     * and memoized, with no current-call state outside this invocation. */
    List<GraphBuilder.ExpressionResult> arguments(FunctionSymbol function,List<ExpressionSyntax> syntax,
            DeclInfo owner,BuiltinFunctions.Rule rule) {
        if(function==null)return syntax.stream().map(s->g.buildExpression(s,owner.id())).toList();
        var values=new GraphBuilder.ExpressionResult[syntax.size()];
        var arguments=Arrays.asList(values);
        for(int i=0;i<values.length;i++) {
            g.checkWork();var chain=new ArrayList<Integer>();var active=new HashSet<Integer>();int current=i;
            while(values[current]==null) {
                g.checkWork();
                if(!active.add(current)) {
                    var cause=g.emitRule(owner,"cyclic actual attribute argument dependency","FUNCTION_ARGUMENT_CYCLE",syntax.get(current).range());
                    values[current]=g.failedFunctionArgument(syntax.get(current).range(),List.of(cause));break;
                }
                chain.add(current);int dependency=dependency(function,current);
                if(dependency<0||dependency>=values.length||values[dependency]!=null)break;
                current=dependency;
            }
            for(int n=chain.size()-1;n>=0;n--) {
                g.checkWork();int position=chain.get(n);if(values[position]!=null)continue;
                var formal=position<function.parameters().size()?g.effectiveDescriptor(function.parameters().get(position).typeId()):null;
                int dependency=dependency(function,position);
                if(dependency>=0&&dependency<values.length&&values[dependency]!=null&&values[dependency].error()) {
                    values[position]=g.blockedFunctionArgument(syntax.get(position).range(),values[dependency],owner);continue;
                }
                var expression=syntax.get(position);
                while(expression instanceof ParenthesizedExpressionSyntax parenthesized) {g.checkWork();expression=parenthesized.inner();}
                var roots=formal instanceof AttributePathType attribute?argumentRoots(attribute,position,function,arguments,rule):List.<SymbolId>of();
                values[position]=expression instanceof AttributeConstantExpressionSyntax constant&&constant.viewable().isEmpty()&&roots.size()==1
                        ?g.contextualAttributeConstant(constant,owner.id(),roots.getFirst()):g.buildExpression(syntax.get(position),owner.id());
            }
        }
        return List.copyOf(arguments);
    }

    private int dependency(FunctionSymbol function,int position) {
        if(position>=function.parameters().size())return -1;
        var formal=g.effectiveDescriptor(function.parameters().get(position).typeId());
        return formal instanceof AttributePathType attribute&&attribute.owner().orElse(null) instanceof AttributePathType.Argument argument
                &&argument.function().equals(function.id())?argument.index():-1;
    }

    /** Instantiate a bindable ATTRIBUTE result's OF dependency for this call
     * only. Undefined or unrestricted object roots retain their formal symbolic
     * dependency, with the actual still reachable through the call's children.
     * The declaration stays immutable; no external function is evaluated. */
    TypeId returnType(FunctionSymbol function,List<GraphBuilder.ExpressionResult> arguments,SourceRange range) {
        var descriptor=g.effectiveDescriptor(function.returnType());
        if(!(descriptor instanceof AttributePathType attribute)
                ||!(attribute.owner().orElse(null) instanceof AttributePathType.Argument dependency)
                ||!dependency.function().equals(function.id())||dependency.index()>=arguments.size())return function.returnType();
        var actual=arguments.get(dependency.index());
        AttributePathType.Owner owner;
        if(g.effectiveDescriptor(actual.typeId()) instanceof ClassValueType) {
            var value=g.expressions.get(actual.id());
            owner=value instanceof SymbolValueExpression symbol&&Set.of(SymbolKind.CLASS,SymbolKind.STRUCTURE,SymbolKind.ASSOCIATION,SymbolKind.VIEW)
                    .contains(g.index.byId(symbol.target()).map(DeclInfo::kind).orElseGet(()->g.symbols.get(symbol.target()).kind()))
                    ?new AttributePathType.Viewable(symbol.target()):new AttributePathType.ClassAttribute(actual.id());
        } else {
            var roots=roots(actual.typeId(),new HashSet<>());
            if(roots.size()!=1)return function.returnType();
            owner=new AttributePathType.Viewable(roots.getFirst());
        }
        var id=g.nextTypeId();
        g.types.put(id,new AttributePathType(id,new Origin.DeclaredOrigin(range),Optional.of(owner),attribute.restrictions()));
        return id;
    }

    private List<SymbolId> argumentRoots(AttributePathType attribute,int position,FunctionSymbol function,
            List<GraphBuilder.ExpressionResult> arguments,BuiltinFunctions.Rule rule) {
        if(rule==BuiltinFunctions.Rule.AREAS_ATTRIBUTE&&position==2&&arguments.size()>1&&arguments.get(1)!=null&&undefined(arguments.get(1)))
            return roots(arguments.getFirst().typeId(),new HashSet<>());
        return ownerRoots(attribute.owner(),function,arguments,new HashSet<>());
    }

    Result bind(FunctionSymbol function,List<GraphBuilder.ExpressionResult> arguments,DeclInfo owner,
            SourceRange range,BuiltinFunctions.Rule rule) {
        boolean error=false;
        var paths=new LinkedHashMap<Integer,FunctionCallExpression.ArgumentPath>();
        for(int i=0;i<Math.min(function.parameters().size(),arguments.size());i++) {
            g.checkWork();
            var formal=g.effectiveDescriptor(function.parameters().get(i).typeId());
            var actual=arguments.get(i);
            if(actual.error())continue;
            boolean undefinedValue=guru.interlis.model.internal.TypeCompatibility.isUndefined(g.effectiveDescriptor(actual.typeId()));
            if(undefinedValue && function.parameters().get(i).mandatory()) {
                error=true;g.emitRule(owner,"MANDATORY function parameter excludes undefined","FUNCTION_ARGUMENT_MANDATORY",g.expressions.get(actual.id()).range());continue;
            }
            boolean areas=rule==BuiltinFunctions.Rule.AREAS_ATTRIBUTE || rule==BuiltinFunctions.Rule.AREAS_TEXT;
            if(areas && i==1 && undefined(actual))continue;
            if(formal instanceof AttributePathType attribute) {
                if(undefinedValue)continue;
                var roots=argumentRoots(attribute,i,function,arguments,rule);
                boolean valid=attributeMatches(actual.typeId(),attribute,roots,function,arguments);
                if(!valid) { error=true;g.emitRule(owner,"attribute argument has an incompatible owner or value type","FUNCTION_ATTRIBUTE",g.expressions.get(actual.id()).range()); }
            } else if(rule==BuiltinFunctions.Rule.AREAS_TEXT && i>0) {
                int root=i==1 || undefined(arguments.get(1))?0:1;
                var value=g.expressions.get(actual.id());
                if(!(value instanceof LiteralExpression literal) || literal.textValue().isEmpty()) {
                    error=true;g.emitRule(owner,"area path argument requires a constant TEXT path","FUNCTION_PATH_CONSTANT",value.range());continue;
                }
                var fragment=g.parsePathLiteral(literal.range(),owner.language());
                if(!fragment.consumedEntireFragment() || fragment.diagnostics().stream().anyMatch(d->d.severity()==guru.interlis.diagnostics.Severity.ERROR)
                        || !(fragment.root() instanceof PathExpressionSyntax path)) { error=true;continue; }
                var dependency=paths.get(root);
                List<SymbolId> roots=dependency==null?roots(arguments.get(root).typeId(),new HashSet<>())
                        :dependency.binding().map(g.expressions::get).map(e->roots(e.typeId(),new HashSet<>())).orElseGet(List::of);
                TypeId expected=areaPathType(i,owner.language());
                Optional<ExpressionId> binding=Optional.empty();
                if(!roots.isEmpty()) {
                    SymbolId scope=g.commonObjectScope(roots);
                    if(scope==null) {error=true;g.emitRule(owner,"path roots have no common object context","FUNCTION_PATH_OWNER",literal.range());continue;}
                    var bound=g.valuePath(path,owner.id(),scope);binding=Optional.of(bound.id());
                    if(bound.error())error=true;
                    else if(!assign(bound.typeId(),expected)) {error=true;g.emitRule(owner,"area path has an incompatible value type","FUNCTION_PATH_TYPE",literal.range());}
                }
                var steps=path.segments().stream().map(s->new FunctionCallExpression.ArgumentPathStep(s.name().text(),s.range(),s.associationAccess(),
                        s.associationQualifier().map(n->new QualifiedName(n.segments().stream().map(s2->s2.text()).toList())),s.index())).toList();
                paths.put(i,new FunctionCallExpression.ArgumentPath(steps,root,expected,binding));
            }
        }
        return new Result(error,paths);
    }

    private TypeId areaPathType(int index,LanguageVersion language) {
        // Reuse the ATTRIBUTE signatures of the same operation, not a second type family.
        var signature=BuiltinFunctions.find("areAreas").orElseThrow();
        var formal=g.builtinDeclaration(signature,language).parameters().get(index).typeId();
        return ((AttributePathType)g.effectiveDescriptor(formal)).restrictions().getFirst();
    }

    private boolean undefined(GraphBuilder.ExpressionResult argument) { return guru.interlis.model.internal.TypeCompatibility.isUndefined(g.effectiveDescriptor(argument.typeId())); }

    private List<SymbolId> ownerRoots(Optional<AttributePathType.Owner> owner,FunctionSymbol function,
            List<GraphBuilder.ExpressionResult> arguments,Set<TypeId> visited) {
        if(owner.isEmpty())return List.of();
        return switch(owner.orElseThrow()) {
            case AttributePathType.Viewable v -> List.of(v.target());
            case AttributePathType.ClassAttribute a -> roots(g.expressions.get(a.path()).typeId(),visited);
            case AttributePathType.Argument a -> a.function().equals(function.id()) && a.index()<arguments.size()
                    ? roots(arguments.get(a.index()).typeId(),visited):List.of();
        };
    }

    private boolean attributeMatches(TypeId actual,AttributePathType formal,List<SymbolId> owners,
            FunctionSymbol function,List<GraphBuilder.ExpressionResult> arguments) {
        if(!(g.effectiveDescriptor(actual) instanceof AttributePathType attribute))return false;
        boolean sameDependentValue=attribute.owner().orElse(null) instanceof AttributePathType.ClassAttribute path
                &&formal.owner().orElse(null) instanceof AttributePathType.Argument argument
                &&argument.function().equals(function.id())&&argument.index()<arguments.size()
                &&g.sameBoundExpression(path.path(),arguments.get(argument.index()).id());
        if(!sameDependentValue&&!owners.isEmpty() && (!(attribute.owner().orElse(null) instanceof AttributePathType.Viewable value)
                || owners.stream().anyMatch(owner->!owner.equals(value.target()))))return false;
        return formal.restrictions().isEmpty() || !attribute.restrictions().isEmpty() && attribute.restrictions().stream()
                .allMatch(source->formal.restrictions().stream().anyMatch(target->assign(source,target)));
    }

    private boolean assign(TypeId source,TypeId target) {
        return guru.interlis.model.internal.TypeCompatibility.assign(g.effectiveDescriptor(source),g.effectiveDescriptor(target),
                g::effectiveDescriptor,g.baseOf::get,domain->g.effectiveDescriptor(g.declaredTypes.get(domain)),AssignmentContext.FUNCTION_ARGUMENT)
                .decision()==RelationDecision.ALLOWED;
    }

    private List<SymbolId> roots(TypeId id,Set<TypeId> visited) {
        g.checkWork();if(!visited.add(id))return List.of();
        var type=g.effectiveDescriptor(id);
        if(type instanceof ObjectArgumentType object)return object.restrictions().isEmpty()?object.base().stream().toList():object.restrictions();
        if(type instanceof ReferenceType reference)return reference.targetRoots();
        if(type instanceof StructureValueType structure)return structure.targetRoots();
        if(type instanceof ClassValueType value)return value.restrictions();
        if(type instanceof CollectionType collection)return roots(collection.elementType(),visited);
        if(type instanceof AttributePathType attribute)return attribute.restrictions().stream().flatMap(t->roots(t,new HashSet<>(visited)).stream()).distinct().toList();
        return List.of();
    }
}
