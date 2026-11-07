package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.syntax.NameSyntax;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/** Applies source-level predefined and visibility rules to indexed name resolution. */
final class NameBindings {
    @FunctionalInterface
    interface PredefinedLookup {
        Optional<SymbolId> lookup(DeclInfo owner, String name, Set<SymbolKind> kinds);
    }

    private final IndexedDeclarations index;
    private final NameResolver resolver;
    private final SemanticWork work;
    private final SemanticDiagnostics diagnostics;
    private final ReferenceBindings references;
    private final PredefinedLookup temporal;
    private final PredefinedLookup metadata;
    private final PredefinedLookup lines;
    private final BiFunction<SymbolId, String, Optional<SymbolId>> predefinedOid;
    private final BiFunction<DeclInfo, String, Optional<SymbolId>> predefinedDomain;
    private final BiFunction<SymbolId, String, Optional<SymbolId>> predefinedUnit;
    private final BiFunction<BuiltinFunctions.Signature, LanguageVersion,
            FunctionSymbol> predefinedFunction;

    NameBindings(IndexedDeclarations index, NameResolver resolver, SemanticWork work, SemanticDiagnostics diagnostics,
                 ReferenceBindings references, PredefinedLookup temporal, PredefinedLookup metadata, PredefinedLookup lines,
                 BiFunction<SymbolId, String, Optional<SymbolId>> predefinedOid,
                 BiFunction<DeclInfo, String, Optional<SymbolId>> predefinedDomain,
                 BiFunction<SymbolId, String, Optional<SymbolId>> predefinedUnit,
                 BiFunction<BuiltinFunctions.Signature, LanguageVersion,
                         FunctionSymbol> predefinedFunction) {
        this.index = index;
        this.resolver = resolver;
        this.work = work;
        this.diagnostics = diagnostics;
        this.references = references;
        this.temporal = temporal;
        this.metadata = metadata;
        this.lines = lines;
        this.predefinedOid = predefinedOid;
        this.predefinedDomain = predefinedDomain;
        this.predefinedUnit = predefinedUnit;
        this.predefinedFunction = predefinedFunction;
    }

    /** One source-name selection for user declarations and canonical predefined targets. */
    NameResolver.Resolution resolveName(DeclInfo owner, NameSyntax name,
            Set<SymbolKind> kinds, boolean relativeQualifiers) {
        work.checkpoint();
        if(name.text().startsWith("INTERLIS.")) {
            var predefined=predefinedTarget(owner,name.text(),kinds);
            if(predefined.isPresent())return new NameResolver.Resolution(NameResolver.Status.BOUND,predefined,List.of());
            return resolver.resolve(owner.id(),name,kinds,relativeQualifiers);
        }
        if(name.segments().size()!=1)return resolver.resolve(owner.id(),name,kinds,relativeQualifiers);
        var local=resolver.resolveLocal(owner.id(),name,kinds);
        if(local.status()!=NameResolver.Status.UNRESOLVED)return local;
        var normal=resolver.resolve(owner.id(),name,kinds,relativeQualifiers);
        boolean visible=index.modelOfSymbol(owner.id()).flatMap(index::byId).filter(d->d.syntax() instanceof guru.interlis.syntax.ModelSyntax)
                .map(d->((guru.interlis.syntax.ModelSyntax)d.syntax()).imports().stream().anyMatch(i->i.modelName().equals("INTERLIS") && i.unqualified())).orElse(false);
        // Preserve the existing priority of actual imported declarations. In particular,
        // ambiguity is a result, never a reason to substitute a builtin function.
        if(!visible || normal.status()!=NameResolver.Status.UNRESOLVED)return normal;
        var predefined=predefinedTarget(owner,"INTERLIS."+name.text(),kinds);
        return predefined.isPresent()
                ? new NameResolver.Resolution(NameResolver.Status.BOUND,predefined,List.of()) : normal;
    }

    private Optional<SymbolId> predefinedTarget(DeclInfo owner,String qualified,Set<SymbolKind> kinds) {
        if(!qualified.startsWith("INTERLIS."))return Optional.empty();
        Optional<SymbolId> target=kinds.contains(SymbolKind.DOMAIN)?predefinedOid.apply(owner.id(),qualified):Optional.empty();
        if(target.isEmpty() && kinds.contains(SymbolKind.DOMAIN))target=predefinedDomain.apply(owner,qualified);
        if(target.isEmpty())target=temporal.lookup(owner,qualified,kinds);
        if(target.isEmpty())target=metadata.lookup(owner,qualified,kinds);
        if(target.isEmpty())target=lines.lookup(owner,qualified,kinds);
        if(target.isEmpty() && kinds.contains(SymbolKind.UNIT))target=predefinedUnit.apply(owner.id(),qualified);
        if(target.isEmpty() && kinds.contains(SymbolKind.FUNCTION) && qualified.indexOf('.',9)<0) {
            var signature=BuiltinFunctions.find(qualified.substring(9)).filter(f->f.since()!=LanguageVersion.ILI24 || owner.language()==LanguageVersion.ILI24);
            if(signature.isPresent())target=Optional.of(predefinedFunction.apply(signature.orElseThrow(),owner.language()).id());
        }
        return target;
    }

    Optional<SymbolId> bindTarget(DeclInfo owner, NameSyntax name,
            Set<SymbolKind> kinds, ReferenceUsage usage, List<DiagnosticId> errors) {
        return bindTarget(owner, name, kinds, usage, errors, true);
    }

    Optional<SymbolId> bindTarget(DeclInfo owner, NameSyntax name,
            Set<SymbolKind> kinds, ReferenceUsage usage, List<DiagnosticId> errors, boolean relativeQualifiers) {
        var resolution = resolveName(owner, name, kinds, relativeQualifiers);
        Optional<SymbolId> target = resolution.target();
        references.recordResolution(owner, name, resolution, usage);
        if (resolution.status() == NameResolver.Status.AMBIGUOUS) {
            errors.add(reportAmbiguity(name, resolution));
        } else if (target.isEmpty()) {
            errors.add(diagnostics.unresolved(name));
        }
        return target;
    }

    DiagnosticId reportAmbiguity(NameSyntax name, NameResolver.Resolution resolution) {
        return diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0002"),
                        Severity.ERROR, Phase.BINDING, "diagnostic.semantics.ambiguous")
                .at(name.range())
                .argument(new DiagnosticArgument.IdentifierArgument("name", name.text()))
                .argument(new DiagnosticArgument.IdentifierListArgument("candidates",
                        resolution.candidates().stream().map(SymbolId::value).toList())));
    }

    Optional<SymbolId> tryResolve(DeclInfo context,
                                          NameSyntax name,
                                          Set<SymbolKind> expected) {
        NameResolver.Resolution resolution = resolver.resolve(context.id(), name, expected);
        return resolution.status() == NameResolver.Status.BOUND
                ? resolution.target() : Optional.empty();
    }
}
