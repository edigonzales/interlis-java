package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.PathExpressionSyntax;
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
                var roots=ownerRoots(attribute.owner(),function,arguments,new HashSet<>());
                if(rule==BuiltinFunctions.Rule.AREAS_ATTRIBUTE && i==2 && undefined(arguments.get(1)))roots=roots(arguments.getFirst().typeId(),new HashSet<>());
                boolean valid=attributeMatches(actual.typeId(),attribute,roots);
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

    private boolean attributeMatches(TypeId actual,AttributePathType formal,List<SymbolId> owners) {
        if(!(g.effectiveDescriptor(actual) instanceof AttributePathType attribute))return false;
        if(!owners.isEmpty() && (!(attribute.owner().orElse(null) instanceof AttributePathType.Viewable value)
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
