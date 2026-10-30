package guru.interlis.compiler.internal;

import guru.interlis.compiler.*;
import guru.interlis.compiler.SemanticOverlays.*;
import guru.interlis.compiler.SemanticOverlays.SemanticOverlay.*;
import guru.interlis.diagnostics.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;

/** Run-local binding of explicit configuration to indexed declaration identities.
 * No lexical aliases, host IO, global state or mutation of prior graphs. */
final class OverlayBindings {
    record Translation(SymbolId original,OverlaySource source) {}
    private record Values(OverlaySource source,List<String> values,long order) {}
    private final IndexedDeclarations index;
    private final DiagnosticCollector diagnostics;
    private final CancellationToken token;
    private final long limit;
    private long work;
    private long order;
    private final Map<ModelDecl,SymbolId> modelIds=new HashMap<>();
    private final Map<SymbolId,Map<Integer,SymbolId>> translationChoices=new HashMap<>();
    private final Map<SymbolId,Translation> translations=new LinkedHashMap<>();
    private final Map<SymbolId,Map<String,List<Values>>> metadata=new LinkedHashMap<>();
    private final Map<SymbolId,ModelDecl> models=new LinkedHashMap<>();

    OverlayBindings(IndexedDeclarations index,SemanticOverlays input,DiagnosticCollector diagnostics,
            CancellationToken token,AnalysisBudget budget) {
        this.index=index;this.diagnostics=diagnostics;this.token=token;limit=budget.maxSemanticWorkUnits();
        index.models().forEach(e->{models.put(e.id(),e.model());modelIds.put(e.model(),e.id());});
        for(var overlay:input.overlays()) {
            charge();var target=resolve(overlay.target(),overlay.source());if(target==null)continue;
            switch(overlay) {
                case TranslationOverlay t->translation(target,t);
                case CrsOverlay c->metadata(target,"CRS",List.of(c.crs()),c.source());
                case MetaAttributeOverlay m->m.attributes().forEach((key,values)->metadata(target,key,values,m.source()));
            }
        }
    }
    long workUnits(){return work;}
    private void charge(){token.throwIfCancellationRequested();if(++work>limit)throw new AnalysisBudgetExceededException("maxSemanticWorkUnits",limit);}
    private DeclInfo resolve(Target target,OverlaySource source) {return resolve(target,source,false);}
    private DeclInfo resolve(Target target,OverlaySource source,boolean originalDependency) {
        var candidates=new ArrayList<DeclInfo>();
        for(var declaration:index.byQualifiedName(target.name().toString())) {
            charge();var model=index.modelOfSymbol(declaration.id()).map(models::get).orElse(null);
            if(model==null)continue;
            if(target.modelVersion().filter(v->!v.equals(model.version())).isPresent())continue;
            if(target.sourceHint().filter(v->!v.equals(declaration.sourceId())).isPresent())continue;
            candidates.add(declaration);
        }
        if(candidates.size()!=1) {
            if(originalDependency && target.name().segments().size()==1) {
                var draft=DiagnosticDraft.of(new DiagnosticCode(candidates.isEmpty()?"ILI-RES-0005":"ILI-RES-0006"),Severity.ERROR,Phase.SOURCE_RESOLUTION,
                        candidates.isEmpty()?"diagnostic.resolution.overlay.missing":"diagnostic.resolution.overlay.ambiguous")
                        .argument(new DiagnosticArgument.TextArgument("overlay",source.id()))
                        .argument(new DiagnosticArgument.IdentifierArgument("model",target.name().toString()));
                if(!candidates.isEmpty())draft=draft.argument(new DiagnosticArgument.IdentifierListArgument("candidates",candidates.stream().map(d->d.sourceId().value()+":"+d.syntax().range().startOffset()).sorted().toList()));
                diagnostics.emit(draft);
            } else error(source,target.name().toString(),candidates.isEmpty()?"target not found":"target is ambiguous");
            return null;
        }
        return candidates.getFirst();
    }
    private void translation(DeclInfo target,TranslationOverlay overlay) {
        var original=resolve(overlay.original(),overlay.source(),true);if(original==null)return;
        if(target.kind()!=SymbolKind.MODEL||original.kind()!=SymbolKind.MODEL){error(overlay.source(),target.qualifiedName(),"translation targets must be models");return;}
        var syntax=models.get(target.id()).syntax();var originalModel=models.get(original.id());
        if(target.language()!=original.language()){error(overlay.source(),target.qualifiedName(),"translation language versions differ");return;}
        if(syntax.translationSource().isPresent() && (!syntax.translationSource().orElseThrow().text().equals(original.name())
                || syntax.translationVersion().filter(v->!v.equals(originalModel.version())).isPresent())) {
            error(overlay.source(),target.qualifiedName(),"overlay contradicts TRANSLATION OF");return;
        }
        var samePriority=translationChoices.computeIfAbsent(target.id(),ignored->new HashMap<>()).putIfAbsent(overlay.source().priority(),original.id());
        if(samePriority!=null&&!samePriority.equals(original.id()))error(overlay.source(),target.qualifiedName(),"conflicting translations at equal priority");
        var prior=translations.get(target.id());
        if(prior==null||prior.source().priority()<overlay.source().priority())translations.put(target.id(),new Translation(original.id(),overlay.source()));
    }
    private void metadata(DeclInfo target,String key,List<String> values,OverlaySource source) {
        for(var ignored:values)charge();
        if(key.equals("CRS")&&(values.size()!=1||values.getFirst().isBlank())){error(source,target.qualifiedName(),"CRS needs one nonblank identifier");return;}
        var groups=metadata.computeIfAbsent(target.id(),ignored->new LinkedHashMap<>()).computeIfAbsent(key,ignored->new ArrayList<>());
        for(var group:groups){charge();if(group.source().priority()==source.priority()&&!group.values().equals(values))error(source,target.qualifiedName(),"conflicting metadata at equal priority: "+key);}
        groups.add(new Values(source,values,order++));
    }
    void validateCrs(GraphBuilder g) {
        metadata.forEach((id,keys)->{if(keys.containsKey("CRS")){g.checkWork();if(!(g.effectiveDescriptor(g.declaredTypes.get(id)) instanceof CoordinateType))
            error(keys.get("CRS").getFirst().source(),index.byId(id).orElseThrow().qualifiedName(),"CRS target is not a coordinate declaration");}});
    }
    List<MetaAttributeSet.Entry> metadata(SymbolId id) {
        var result=new ArrayList<MetaAttributeSet.Entry>();
        var ordered=new ArrayList<Map.Entry<String,Values>>();
        metadata.getOrDefault(id,Map.of()).forEach((key,groups)->groups.forEach(v->ordered.add(Map.entry(key,v))));
        ordered.sort(Comparator.comparingInt((Map.Entry<String,Values> e)->e.getValue().source().priority()).reversed().thenComparingLong(e->e.getValue().order()));
        ordered.forEach(e->e.getValue().values().forEach(text->result.add(new MetaAttributeSet.Entry(e.getKey(),new MetaAttributeSet.Value(text,e.getValue().source().origin())))));
        return List.copyOf(result);
    }
    Optional<ModelDecl> original(ModelDecl model) {
        return Optional.ofNullable(translations.get(modelIds.get(model))).map(t->models.get(t.original()));
    }
    Optional<ModelTranslation> translation(SymbolId target,Optional<ModelTranslation> declared) {
        var bound=translations.get(target);if(bound==null)return declared;
        var original=models.get(bound.original());return Optional.of(new ModelTranslation(original.name(),Optional.of(original.version()),
                declared.flatMap(ModelTranslation::language),Optional.of(bound.original()),List.of(),Optional.of(bound.source().origin())));
    }
    private void error(OverlaySource source,String target,String reason) {
        diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0011"),Severity.ERROR,Phase.BINDING,"diagnostic.semantics.overlay")
                .argument(new DiagnosticArgument.TextArgument("overlay",source.id())).argument(new DiagnosticArgument.TextArgument("target",target))
                .argument(new DiagnosticArgument.TextArgument("reason",reason)));
    }
}
