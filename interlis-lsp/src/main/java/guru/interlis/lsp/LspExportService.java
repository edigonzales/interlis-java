package guru.interlis.lsp;

import guru.interlis.model.*;
import guru.interlis.projection.*;
import guru.interlis.documentation.*;
import guru.interlis.execution.CancellationToken;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

/** Host-independent artifact generation from an already accepted root schema. */
final class LspExportService {
    static final Set<String> FORMATS=Set.of("MARKDOWN","HTML","DOCX","GRAPHML","MERMAID","PLANTUML");
    static DocumentationOptions options(ExportArtifactParams p){var raw=p.options();var language=DocumentationOptions.Language.EN;var inheritance=DocumentationOptions.Inheritance.SEPARATE;var headers=new LinkedHashMap<String,String>();
        if(raw!=null){for(String key:raw.keySet())if(!Set.of("language","inheritance","headers").contains(key))throw new IllegalArgumentException("unknown documentation option");if(raw.has("language"))language=DocumentationOptions.Language.valueOf(raw.get("language").getAsString());if(raw.has("inheritance"))inheritance=DocumentationOptions.Inheritance.valueOf(raw.get("inheritance").getAsString());if(raw.has("headers")){var h=raw.getAsJsonObject("headers");for(String key:h.keySet()){if(!h.get(key).isJsonPrimitive()||!h.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException("documentation headers must be strings");headers.put(key,h.get(key).getAsString());}}}
        return new DocumentationOptions(List.of(),language,inheritance,headers,10_000);
    }
    ExportArtifact render(CompiledSchema schema,ExportArtifactParams request,ExportArtifact.SourceSnapshot provenance,CancellationToken token){
        token.throwIfCancellationRequested();String format=request.format(),content,media,ext,encoding="utf8";var limitations=new ArrayList<String>();
        if(Set.of("MARKDOWN","HTML","DOCX").contains(format)){var document=new DocumentationProjector().project(schema,options(request),token);switch(format){case "MARKDOWN"->{content=new MarkdownDocumentationRenderer().render(document);media="text/markdown";ext="md";}case "HTML"->{content=new HtmlDocumentationRenderer().render(document);media="text/html";ext="html";}default->{content=Base64.getEncoder().encodeToString(new DocxDocumentationRenderer().render(document));media="application/vnd.openxmlformats-officedocument.wordprocessingml.document";ext="docx";encoding="base64";}}}
        else {var projection=DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),DiagramOptions.defaults(new DiagramSelection(schema.rootModels())),token);if(!(projection instanceof DiagramProjectionOutcome.Produced produced)||produced.document().completeness()!=ProjectionCompleteness.COMPLETE)throw LspFailures.error(ResponseErrorCode.RequestFailed,"Graph export requires a complete projection within budget");var d=produced.document();switch(format){case "GRAPHML"->{content=new GraphMlRenderer().render(d);media="application/graphml+xml";ext="graphml";}case "MERMAID"->{content=new MermaidRenderer().render(d);media="text/plain";ext="mmd";limitations.add("Mermaid is a semantic class-diagram text projection; container geometry and manual UI layout are not represented.");}case "PLANTUML"->{content=new PlantUmlRenderer().render(d);media="text/plain";ext="puml";limitations.add("PlantUML is a semantic class-diagram text projection; container geometry and manual UI layout are not represented.");}default->throw new IllegalArgumentException("unsupported export format");}}
        token.throwIfCancellationRequested();if(content.getBytes(StandardCharsets.UTF_8).length>8_000_000)throw LspFailures.error(ResponseErrorCode.RequestFailed,"Export exceeds the 8 MB transport budget");
        String name=schema.rootModels().size()==1?schema.semanticGraph().symbol(schema.rootModels().getFirst().symbol()).orElseThrow().name().value():"catalog";
        return new ExportArtifact(media,encoding,content,name+"."+ext,provenance,limitations);
    }
}
