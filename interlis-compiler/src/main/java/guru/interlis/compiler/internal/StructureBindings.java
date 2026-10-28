package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.*;
import guru.interlis.syntax.StructureValueTypeSyntax;
import java.util.*;

/** Structure binding and availability within one graph build; owns no parallel state. */
final class StructureBindings {
    private final GraphBuilder g;
    StructureBindings(GraphBuilder graph) { g=graph; }

    TypeDescriptor bind(TypeId id, StructureValueTypeSyntax syntax, DeclInfo declaration) {
        List<DiagnosticId> errors=new ArrayList<>();
        Optional<SymbolId> target=syntax.target().flatMap(name -> g.bindTarget(declaration,name,
                Set.of(SymbolKind.STRUCTURE),ReferenceUsage.TYPE_USE,errors));
        List<SymbolId> restrictions=new ArrayList<>();
        for(var name:syntax.restrictions()) {
            g.checkWork();g.bindTarget(declaration,name,Set.of(SymbolKind.STRUCTURE),ReferenceUsage.TYPE_USE,errors).ifPresent(restrictions::add);
        }
        if(!errors.isEmpty())return new ErrorType(id,new Origin.DeclaredOrigin(syntax.range()),errors);
        target.ifPresent(g::objectInheritanceBase);
        for(SymbolId restriction:restrictions) {
            g.objectInheritanceBase(restriction);
            if(target.isPresent()&&!g.targetSpecializes(restriction,target.orElseThrow()))
                errors.add(g.emitRule(declaration,"structure restriction must specialize the primary structure","STRUCTURE_RESTRICTION",syntax.range()));
        }
        return errors.isEmpty()?new StructureValueType(id,new Origin.DeclaredOrigin(syntax.range()),target,restrictions)
                :new ErrorType(id,new Origin.DeclaredOrigin(syntax.range()),errors);
    }

    SymbolId target(TypeDescriptor type) {
        if(type instanceof CollectionType collection)type=g.effectiveDescriptor(collection.elementType());
        return type instanceof StructureValueType structure?g.commonObjectScope(structure.targetRoots()):null;
    }

    void validate() {
        for(Symbol symbol:g.symbols.values()) {
            g.checkWork();
            if(!(symbol instanceof AttributeSymbol attribute))continue;
            DeclInfo declaration=g.index.byId(attribute.id()).orElse(null);
            if(declaration==null)continue;
            TypeDescriptor type=g.effectiveDescriptor(attribute.declaredType());
            boolean required=attribute.mandatory();
            if(type instanceof CollectionType collection) {
                required=collection.cardinality().lower().signum()>0;type=g.effectiveDescriptor(collection.elementType());
            }
            if(required && type instanceof StructureValueType structure && !attribute.header().has(Modifier.ABSTRACT)
                    && !hasConcreteStructure(structure,declaration))
                g.emitRule(declaration,"required structure attribute needs a concrete allowed structure or ABSTRACT","STRUCTURE_ABSTRACT_REQUIRED");
        }
        for(Symbol owner:g.symbols.values()) {
            g.checkWork();
            if(!(owner instanceof ClassSymbol || owner instanceof StructureSymbol) || owner.header().has(Modifier.ABSTRACT))continue;
            DeclInfo declaration=g.index.byId(owner.id()).orElse(null);
            if(declaration==null)continue;
            for(var member:g.effectiveMembers.getOrDefault(owner.id(),List.of()))
                if(g.symbols.get(member.declaredSymbol()) instanceof AttributeSymbol attribute && attribute.header().has(Modifier.ABSTRACT))
                    g.emitRule(declaration,"concrete class/structure retains an abstract attribute","ABSTRACT_MEMBER");
        }
    }

    private boolean hasConcreteStructure(StructureValueType type, DeclInfo declaration) {
        if(type.targetRoots().isEmpty())return false;
        for(SymbolId root:type.targetRoots())
            if(g.symbols.get(root) instanceof StructureSymbol structure && !structure.header().has(Modifier.ABSTRACT))return true;
        var visible=g.importedModels(g.index.modelOfSymbol(declaration.id()).orElseThrow());
        SymbolId topic=g.scopeRoot(declaration.id());
        for(Symbol symbol:g.symbols.values()) {
            g.checkWork();
            if(!(symbol instanceof StructureSymbol structure) || structure.header().has(Modifier.ABSTRACT))continue;
            if(!(structure.origin() instanceof Origin.PredefinedOrigin)
                    && !visible.contains(g.index.modelOfSymbol(structure.id()).orElse(null)))continue;
            SymbolId candidateTopic=g.scopeRoot(structure.id());
            boolean available=!(g.symbols.get(candidateTopic) instanceof TopicSymbol) || Objects.equals(candidateTopic,topic)
                    || type.targetRoots().stream().anyMatch(root -> Objects.equals(g.scopeRoot(root),candidateTopic));
            if(available && type.targetRoots().stream().anyMatch(root -> g.targetSpecializes(structure.id(),root)))return true;
        }
        return false;
    }
}
