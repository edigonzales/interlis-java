package guru.interlis.lsp;

import guru.interlis.diagnostics.*;
import guru.interlis.source.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import java.util.*;
import java.util.function.Function;

/** Pure presentation over exact source revisions; never invents a location for global diagnostics. */
final class LspDiagnosticMapper {
    private final DiagnosticMessageFormatter formatter = new DiagnosticMessageFormatter();

    org.eclipse.lsp4j.Diagnostic toLsp(guru.interlis.diagnostics.Diagnostic diagnostic,
            Map<SourceId,SourceText> sources, DiagnosticLanguage language, Function<SourceId,String> uri) {
        var range=diagnostic.primaryRange().orElseThrow();
        var source=matching(sources,range).orElseThrow(() -> PositionMapper.invalid());
        var message=formatter.format(diagnostic,language);
        var result=new org.eclipse.lsp4j.Diagnostic(PositionMapper.toLspRange(source,range),
                message.text()+message.hint().map(h -> " " + h).orElse(""),switch(diagnostic.severity()) {
                    case ERROR -> DiagnosticSeverity.Error; case WARNING -> DiagnosticSeverity.Warning;
                    case INFO -> DiagnosticSeverity.Information; case HINT -> DiagnosticSeverity.Hint;
                },"interlis");
        result.setCode(Either.forLeft(diagnostic.code().value()));
        var related=new ArrayList<DiagnosticRelatedInformation>();
        for(var location:diagnostic.relatedLocations()) matching(sources,location.range()).ifPresent(text ->
                related.add(new DiagnosticRelatedInformation(new Location(uri.apply(text.id()),
                        PositionMapper.toLspRange(text,location.range())),formatter.formatRelated(location,language))));
        result.setRelatedInformation(related);
        var tags=new ArrayList<org.eclipse.lsp4j.DiagnosticTag>();
        if(diagnostic.tags().contains(guru.interlis.diagnostics.DiagnosticTag.DEPRECATED)) tags.add(org.eclipse.lsp4j.DiagnosticTag.Deprecated);
        if(diagnostic.tags().contains(guru.interlis.diagnostics.DiagnosticTag.UNNECESSARY)) tags.add(org.eclipse.lsp4j.DiagnosticTag.Unnecessary);
        result.setTags(tags);
        return result;
    }

    String global(guru.interlis.diagnostics.Diagnostic diagnostic,DiagnosticLanguage language) {
        var message=formatter.format(diagnostic,language);
        return diagnostic.code().value()+": "+message.text()+message.hint().map(h -> " "+h).orElse("");
    }

    private static Optional<SourceText> matching(Map<SourceId,SourceText> sources,SourceRange range) {
        return Optional.ofNullable(sources.get(range.source())).filter(s -> s.revision().equals(range.revision()));
    }
}
