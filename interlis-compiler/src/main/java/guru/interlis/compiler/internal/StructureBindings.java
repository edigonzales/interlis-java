package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.*;
import guru.interlis.syntax.StructureValueTypeSyntax;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Structure binding and availability within one graph build; owns no parallel state. */
final class StructureBindings {
    private final SemanticGraphRead state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final NameBindings names;
    private final InheritanceBindings inheritance;
    private final Supplier<ObjectPathBindings> paths;
    private final GenericBindings generics;
    private final Function<TypeId, TypeDescriptor> effectiveDescriptor;

    StructureBindings(SemanticGraphRead state,
                      SemanticWork work,
                      IndexedDeclarations index,
                      SemanticDiagnostics diagnostics,
                      NameBindings names,
                      InheritanceBindings inheritance,
                      Supplier<ObjectPathBindings> paths,
                      GenericBindings generics,
                      Function<TypeId, TypeDescriptor> effectiveDescriptor) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.names = names;
        this.inheritance = inheritance;
        this.paths = paths;
        this.generics = generics;
        this.effectiveDescriptor = effectiveDescriptor;
    }

    TypeDescriptor bind(TypeId id, StructureValueTypeSyntax syntax, DeclInfo declaration) {
        List<DiagnosticId> errors=new ArrayList<>();
        Optional<SymbolId> target=syntax.target().flatMap(name -> names.bindTarget(declaration,name,
                Set.of(SymbolKind.STRUCTURE),ReferenceUsage.TYPE_USE,errors));
        List<SymbolId> restrictions=new ArrayList<>();
        for(var name:syntax.restrictions()) {
            work.checkpoint();names.bindTarget(declaration,name,Set.of(SymbolKind.STRUCTURE),ReferenceUsage.TYPE_USE,errors).ifPresent(restrictions::add);
        }
        if(!errors.isEmpty())return new ErrorType(id,new Origin.DeclaredOrigin(syntax.range()),errors);
        target.ifPresent(inheritance::objectInheritanceBase);
        for(SymbolId restriction:restrictions) {
            inheritance.objectInheritanceBase(restriction);
            if(target.isPresent()&&!inheritance.targetSpecializes(restriction,target.orElseThrow()))
                errors.add(diagnostics.emitRule(declaration,"structure restriction must specialize the primary structure","STRUCTURE_RESTRICTION",syntax.range()));
        }
        return errors.isEmpty()?new StructureValueType(id,new Origin.DeclaredOrigin(syntax.range()),target,restrictions)
                :new ErrorType(id,new Origin.DeclaredOrigin(syntax.range()),errors);
    }

    SymbolId target(TypeDescriptor type) {
        if(type instanceof CollectionType collection)type=effectiveDescriptor.apply(collection.elementType());
        return type instanceof StructureValueType structure?paths.get().commonObjectScope(structure.targetRoots()):null;
    }

    void validate() {
        for(Symbol symbol:state.symbolsInBuildOrder()) {
            work.checkpoint();
            if(!(symbol instanceof AttributeSymbol attribute))continue;
            DeclInfo declaration=index.byId(attribute.id()).orElse(null);
            if(declaration==null)continue;
            TypeDescriptor type=effectiveDescriptor.apply(attribute.declaredType());
            boolean required=attribute.mandatory();
            if(type instanceof CollectionType collection) {
                required=collection.cardinality().lower().signum()>0;type=effectiveDescriptor.apply(collection.elementType());
            }
            if(required && type instanceof StructureValueType structure && !attribute.header().has(Modifier.ABSTRACT)
                    && !hasConcreteStructure(structure,declaration))
                diagnostics.emitRule(declaration,"required structure attribute needs a concrete allowed structure or ABSTRACT","STRUCTURE_ABSTRACT_REQUIRED");
        }
        for(Symbol owner:state.symbolsInBuildOrder()) {
            work.checkpoint();
            if(!(owner instanceof ClassSymbol || owner instanceof StructureSymbol || owner instanceof AssociationSymbol) || owner.header().has(Modifier.ABSTRACT))continue;
            DeclInfo declaration=index.byId(owner.id()).orElse(null);
            if(declaration==null)continue;
            for(var member:state.effectiveMembers(owner.id()))
                if(state.symbol(member.declaredSymbol()) instanceof AttributeSymbol attribute && attribute.header().has(Modifier.ABSTRACT))
                    diagnostics.emitRule(declaration,"concrete viewable retains an abstract attribute","ABSTRACT_MEMBER");
        }
    }

    private boolean hasConcreteStructure(StructureValueType type, DeclInfo declaration) {
        if(type.targetRoots().isEmpty())return false;
        for(SymbolId root:type.targetRoots())
            if(state.symbol(root) instanceof StructureSymbol structure && !structure.header().has(Modifier.ABSTRACT))return true;
        var visible=generics.importedModels(index.modelOfSymbol(declaration.id()).orElseThrow());
        SymbolId topic=paths.get().scopeRoot(declaration.id());
        for(Symbol symbol:state.symbolsInBuildOrder()) {
            work.checkpoint();
            if(!(symbol instanceof StructureSymbol structure) || structure.header().has(Modifier.ABSTRACT))continue;
            if(!(structure.origin() instanceof Origin.PredefinedOrigin)
                    && !visible.contains(index.modelOfSymbol(structure.id()).orElse(null)))continue;
            SymbolId candidateTopic=paths.get().scopeRoot(structure.id());
            boolean available=!(state.symbol(candidateTopic) instanceof TopicSymbol) || Objects.equals(candidateTopic,topic)
                    || type.targetRoots().stream().anyMatch(root -> Objects.equals(paths.get().scopeRoot(root),candidateTopic));
            if(available && type.targetRoots().stream().anyMatch(root -> inheritance.targetSpecializes(structure.id(),root)))return true;
        }
        return false;
    }
}
