package guru.interlis.repository;

import guru.interlis.compiler.ImportSite;
import guru.interlis.diagnostics.*;
import java.util.*;

/** Shared, pure projection of host resolution facts to the canonical diagnostic vocabulary. */
public final class RepositoryDiagnostics {
    private RepositoryDiagnostics() { }
    /** Global warnings remain revision-independent when a prepared batch is installed atomically. */
    public static List<Diagnostic> warnings(List<RepositorySearchStep> steps) {
        var result=new LinkedHashMap<DiagnosticId,Diagnostic>();
        for(var step:steps)if(step.warning()) {
            var d=diagnostic("ILI-RES-0008","diagnostic.resolution.adjustment",Severity.WARNING,Optional.empty(),List.of(
                    new DiagnosticArgument.IdentifierArgument("model",step.modelName()),
                    new DiagnosticArgument.TextArgument("repository",step.repository().toString()),
                    new DiagnosticArgument.TextArgument("reason",step.kind().name()),new DiagnosticArgument.TextArgument("detail",step.detail())));
            result.putIfAbsent(d.id(),d);
        }
        return List.copyOf(result.values());
    }
    public static List<Diagnostic> rejection(RepositoryProblem problem,List<ImportSite> sites,List<RepositorySearchStep> steps) {
        String detail=String.join("; ",steps.stream().map(s->s.kind()+" "+s.repository()+" "+s.detail()).toList());
        if (problem.source().isPresent()) detail += "; " + RepositorySearchStep.safeUri(problem.source().orElseThrow().value());
        if (!problem.candidateModels().isEmpty()) detail += "; " + String.join("; ",problem.candidateModels().stream()
                .map(m -> m.version()+" "+RepositorySearchStep.safeUri(m.location().toString())).toList());
        var ranges=sites.stream().map(ImportSite::nameRange).distinct().map(Optional::of).toList();
        if(ranges.isEmpty())ranges=List.of(Optional.empty());
        var result=new ArrayList<>(warnings(steps));
        for(var range:ranges) {
            var args=new ArrayList<DiagnosticArgument>();String code,key;
            args.add(new DiagnosticArgument.IdentifierArgument("model",problem.modelName()));
            switch(problem.reason()) {
                case NOT_FOUND -> {code="ILI-RES-0001";key="diagnostic.resolution.missing";}
                case AMBIGUOUS -> {
                    code="ILI-RES-0002";key="diagnostic.resolution.ambiguous";
                    args.add(new DiagnosticArgument.IdentifierListArgument("candidates",problem.candidates().stream().map(id->RepositorySearchStep.safeUri(id.value())).toList()));
                }
                case LOAD_FAILURE,INDEX_FAILURE -> {
                    code="ILI-RES-0003";key="diagnostic.resolution.source";args.clear();
                    args.add(new DiagnosticArgument.TextArgument("source",problem.source().map(id->RepositorySearchStep.safeUri(id.value())).orElse(problem.modelName())));
                    args.add(new DiagnosticArgument.TextArgument("reason",problem.loadReason().map(Enum::name).orElse(problem.reason().name())));
                }
                default -> {code="ILI-RES-0009";key="diagnostic.resolution.rejected";args.add(new DiagnosticArgument.TextArgument("reason",problem.reason().name()));}
            }
            args.add(new DiagnosticArgument.TextArgument("detail",detail));
            result.add(diagnostic(code,key,Severity.ERROR,range,args));
        }
        return List.copyOf(result);
    }
    private static Diagnostic diagnostic(String code,String key,Severity severity,Optional<guru.interlis.source.SourceRange> range,List<DiagnosticArgument> args) {
        var id=DiagnosticIds.create(new DiagnosticCode(code),Phase.SOURCE_RESOLUTION,range,key,args);
        return new Diagnostic(id,new DiagnosticCode(code),severity,Phase.SOURCE_RESOLUTION,key,args,range,List.of(),Optional.empty(),List.of(),Optional.empty(),Optional.empty(),List.of());
    }
}
