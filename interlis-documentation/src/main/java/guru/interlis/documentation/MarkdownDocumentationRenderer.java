package guru.interlis.documentation;

import java.util.*;
import java.util.stream.Collectors;

/** Escaped Markdown catalogue with stable local anchors and provenance. */
public final class MarkdownDocumentationRenderer {
    public String render(DocumentationDocument d){var b=new StringBuilder("# "+DocumentationText.markdown(DocumentationLabels.get(d.options(),"catalog"))+"\n\nSnapshot: `"+DocumentationText.markdown(d.snapshotId())+"`\n\n");
        for(var s:d.sections()){
            b.append("<a id=\"").append(DocumentationText.anchor(s.id())).append("\"></a>\n\n## ").append(DocumentationText.markdown(s.name())).append(s.abstractType()?" «ABSTRACT»":"").append("\n\n");
            b.append(DocumentationText.markdown(s.kind()+" "+s.type())).append("\n\n");if(!s.base().isEmpty())b.append(DocumentationText.markdown(DocumentationLabels.get(d.options(),"base")+": "+s.base())).append("\n\n");
            b.append(DocumentationText.markdown(s.description())).append("\n\n");table(b,d,s.members());
            if(!s.inherited().isEmpty()){b.append("### ").append(DocumentationText.markdown(DocumentationLabels.get(d.options(),"inherited"))).append("\n\n");table(b,d,s.inherited());}
            if(!s.constraints().isEmpty()){b.append("### ").append(DocumentationText.markdown(DocumentationLabels.get(d.options(),"constraints"))).append("\n\n");for(var c:s.constraints())b.append("- ").append(DocumentationText.markdown(c)).append('\n');b.append('\n');}
            DocumentationText.checkSize(b.length());
        }
        return b.toString();
    }
    private static void table(StringBuilder b,DocumentationDocument d,List<DocumentationDocument.Member> members){if(members.isEmpty())return;b.append(row(DocumentationText.headers(d.options()))).append("\n|---|---|---|---|---|---|\n");for(var m:members)b.append(row(DocumentationText.cells(m))).append('\n');b.append('\n');}
    private static String row(List<String> cells){return "| "+cells.stream().map(DocumentationText::markdown).collect(Collectors.joining(" | "))+" |";}
}
