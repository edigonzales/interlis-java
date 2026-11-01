package guru.interlis.lsp;

import guru.interlis.compiler.*;
import guru.interlis.diagnostics.*;
import guru.interlis.source.SourceId;
import java.util.*;
import java.util.function.Function;

/** Root report presentation reads the gate's requestDiagnostics, never the full IDE diagnostic list. */
final class LspCompileMapper {
    private final DiagnosticMessageFormatter formatter=new DiagnosticMessageFormatter();
    CompileReport map(CompilationOutcome outcome,DiagnosticLanguage language,Function<SourceId,String> uri) {
        AnalysisSnapshot analysis=null;String status,snapshot="",cancel="",failure="";int count=0;List<String> reasons=List.of();
        if(outcome instanceof CompilationOutcome.Succeeded success) {
            status="succeeded";analysis=success.analysis();snapshot=success.schema().snapshotId().value();count=success.schema().models().size();
        } else if(outcome instanceof CompilationOutcome.Rejected rejected) {
            status="rejected";analysis=rejected.analysis();snapshot=analysis.snapshotId().value();reasons=rejected.rejection().reasons().stream().map(Enum::name).toList();
        } else if(outcome instanceof CompilationOutcome.Cancelled cancelled) {
            status="cancelled";cancel=cancelled.reason().name();
        } else { status="failed";failure=((CompilationOutcome.Failed)outcome).failureId().value(); }
        var diagnostics=new ArrayList<CompileReport.DiagnosticEntry>();
        if(analysis!=null) for(var diagnostic:analysis.requestDiagnostics()) {
            var message=formatter.format(diagnostic,language);String address="";List<CompileReport.Coordinate> positions=List.of();
            if(diagnostic.primaryRange().isPresent()) {
                var range=diagnostic.primaryRange().orElseThrow();address=uri.apply(range.source());
                var source=analysis.syntaxSnapshot(range.source()).map(s -> s.source()).orElseThrow();
                var mapped=PositionMapper.toLspRange(source,range);
                positions=List.of(new CompileReport.Coordinate(mapped.getStart().getLine(),mapped.getStart().getCharacter()),
                        new CompileReport.Coordinate(mapped.getEnd().getLine(),mapped.getEnd().getCharacter()));
            }
            diagnostics.add(new CompileReport.DiagnosticEntry(diagnostic.code().value(),diagnostic.severity().name(),
                    message.text()+message.hint().map(h -> " "+h).orElse(""),address,positions));
        }
        return new CompileReport(status,List.of(),new CompileReport.Summary(snapshot,count,reasons,diagnostics,cancel,failure));
    }
}
