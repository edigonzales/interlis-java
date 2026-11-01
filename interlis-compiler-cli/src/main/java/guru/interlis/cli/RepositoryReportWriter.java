package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.repository.*;
import java.util.*;

/** Presentation only: serializes already made repository decisions without performing selection or IO. */
final class RepositoryReportWriter {
    String json(ResolutionLock lock,List<RepositorySearchStep> steps) {
        if(lock==null && steps.isEmpty())return "";
        var out=new StringBuilder(",\"resolution\":{\"steps\":[");
        for(int i=0;i<steps.size();i++) {
            if(i>0)out.append(',');var step=steps.get(i);out.append('{');
            field(out,"model",step.modelName());out.append(',');field(out,"repository",step.repository().toString());
            out.append(',');field(out,"kind",step.kind().name());out.append(',');field(out,"detail",step.detail());out.append('}');
        }
        out.append("],\"models\":[");
        if(lock!=null) {
            var pins=lock.models().entrySet().stream().sorted(Map.Entry.comparingByKey()).flatMap(e->e.getValue().stream()).toList();
            for(int i=0;i<pins.size();i++) {
                if(i>0)out.append(',');var pin=pins.get(i);out.append('{');field(out,"name",pin.modelName());
                out.append(',');field(out,"version",pin.modelVersion());out.append(',');field(out,"source",RepositorySearchStep.safeUri(pin.source().value()));
                out.append(',');field(out,"sha256",pin.digest().value());out.append('}');
            }
        }
        return out.append("]}").toString();
    }
    List<String> text(List<RepositorySearchStep> steps,DiagnosticLanguage language) {
        boolean de=language==DiagnosticLanguage.DE;var out=new ArrayList<String>();
        for(var step:steps) {
            if(step.kind()==RepositorySearchStep.Kind.LOOKUP)out.add((de?"Modellsuche: ":"Model lookup: ")+step.modelName()+" @ "+step.repository());
            if(step.kind()==RepositorySearchStep.Kind.SELECTED)out.add((de?"Modellauswahl: ":"Model selected: ")+step.modelName()+" "+step.detail());
        }
        return out.stream().map(DiagnosticMessageFormatter::safeText).toList();
    }
    private static void field(StringBuilder out,String name,String value) {
        out.append('"').append(name).append("\":\"").append(JsonDiagnosticWriter.escape(value)).append('"');
    }
}
