package guru.interlis.documentation;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.projection.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationTest {
    private static final String MODEL="""
        INTERLIS 2.4;
        MODEL Catalog AT "https://example.org" VERSION "1" =
          DOMAIN Code = TEXT*17;
          TOPIC T = OID AS INTERLIS.UUIDOID;
            STRUCTURE Detail = note: TEXT; END Detail;
            CLASS Base (ABSTRACT) =
              /** Keep <script>alert(1)</script> & | [link](https://evil.invalid) */
              flag: BOOLEAN;
            END Base;
            CLASS Child EXTENDS Base =
              code: Code;
              items: BAG {0..3} OF Detail;
              state: (red, group(blue));
              MANDATORY CONSTRAINT flag;
            END Child;
            CLASS Other = END Other;
            ASSOCIATION Link =
              left -- {1} Child;
              right -- {0..*} Other;
            END Link;
          END T;
        END Catalog.
        """;
    static CompiledSchema compile(String text){var s=new SourceText(new SourceId("memory:/catalog.ili"),new SourceRevision(1),text);var r=new CompilationRequest(List.of(new ModelSelector.ByName("Catalog",Optional.empty(),Optional.empty())),SourceSet.of(Map.of(s.id(),s)),CompilerOptions.defaults());var o=CompilerEngines.create().compile(r,CancellationToken.none());return assertInstanceOf(CompilationOutcome.Succeeded.class,o,()->o instanceof CompilationOutcome.Rejected r2?r2.analysis().diagnostics().toString():o.toString()).schema();}
    static DocumentationDocument document(CompiledSchema s,DocumentationOptions.Language l,DocumentationOptions.Inheritance i){return new DocumentationProjector().project(s,new DocumentationOptions(List.of(),l,i,Map.of("name","CUSTOM <Name>"),10_000),CancellationToken.none());}
    @Test void checkedProjectionPreservesTypesOriginsAndInheritanceChoices(){var schema=compile(MODEL);for(var i:DocumentationOptions.Inheritance.values()){var d=document(schema,DocumentationOptions.Language.EN,i);var c=d.sections().stream().filter(s->s.name().equals("Catalog.T.Child")).findFirst().orElseThrow();assertEquals("Catalog.T.Base",c.base());assertEquals(i==DocumentationOptions.Inheritance.SEPARATE, !c.inherited().isEmpty());var members=new ArrayList<>(c.members());members.addAll(c.inherited());assertEquals(4,members.size());assertTrue(members.stream().anyMatch(m->m.name().equals("items")&&m.type().equals("BAG {0..3} OF Catalog.T.Detail")&&m.references().contains("Catalog.T.Detail")));assertTrue(members.stream().anyMatch(m->m.name().equals("flag")&&m.origin().equals("Catalog.T.Base")&&m.description().contains("<script>")));assertEquals(List.of("MANDATORY CONSTRAINT flag"),c.constraints());assertTrue(members.stream().anyMatch(m->m.type().contains("group.blue")));assertTrue(d.sections().stream().anyMatch(s->s.abstractType()&&s.name().equals("Catalog.T.Base")));}}
    @Test void allFourLanguagesAndOverridesEscapeUntrustedModelText(){var schema=compile(MODEL);for(var l:DocumentationOptions.Language.values()){var d=document(schema,l,DocumentationOptions.Inheritance.INLINE);var md=new MarkdownDocumentationRenderer().render(d);var html=new HtmlDocumentationRenderer().render(d);assertTrue(md.contains("Catalog.T.Child"));assertTrue(html.contains("CUSTOM &lt;Name&gt;"));assertFalse(html.contains("<script>alert"));assertTrue(html.contains("&lt;script&gt;"));assertTrue(html.contains("default-src 'none'"));assertFalse(html.contains("<a href=\"https://evil"));assertTrue(md.contains("\\|"));assertTrue(md.contains("\\[link\\]"));assertFalse(md.contains("<script>"));assertTrue(html.contains(switch(l){case EN->"Object catalogue";case DE->"Objektkatalog";case FR->"Catalogue des objets";case IT->"Catalogo degli oggetti";}));}}
    @Test void realOoxmlPackageHasRelationshipsStylesHeadingsTablesAndContent() throws Exception {var d=document(compile(MODEL),DocumentationOptions.Language.FR,DocumentationOptions.Inheritance.SEPARATE);var bytes=new DocxDocumentationRenderer().render(d);var entries=new LinkedHashMap<String,String>();try(var z=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry()){assertFalse(e.getName().contains(".."));String xml=new String(z.readAllBytes(),StandardCharsets.UTF_8);parse(xml);entries.put(e.getName(),xml);}}assertEquals(Set.of("[Content_Types].xml","_rels/.rels","word/document.xml","word/styles.xml","word/_rels/document.xml.rels"),entries.keySet());String xml=entries.get("word/document.xml");assertTrue(xml.contains("<w:tbl>"));assertTrue(xml.contains("Heading1"));assertTrue(xml.contains("Catalog.T.Child"));assertTrue(xml.contains("TEXT*17"));assertTrue(xml.contains("MANDATORY CONSTRAINT flag"));assertTrue(xml.contains("Catalogue des objets"));assertFalse(xml.contains("<script>"));assertTrue(entries.get("_rels/.rels").contains("Target=\"word/document.xml\""));assertTrue(entries.get("word/_rels/document.xml.rels").contains("Target=\"styles.xml\""));}
    @Test void graphMlKeepsContainersAndNaryEndpoints() throws Exception {var schema=compile(MODEL.replace("right -- {0..*} Other;","right -- {0..*} Other; third -- {0..1} Child;"));var diagram=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),DiagramOptions.defaults(new DiagramSelection(schema.rootModels())),CancellationToken.none())).document();var xml=new GraphMlRenderer().render(diagram);var dom=parse(xml);assertEquals(diagram.nodes().size()+diagram.containers().size(),dom.getElementsByTagName("node").getLength());assertEquals(diagram.edges().size(),dom.getElementsByTagName("edge").getLength());assertTrue(xml.contains("sourceCardinality"));assertTrue(xml.contains("third"));assertTrue(xml.contains("0..*"));var ids=new HashSet<String>();var nodes=dom.getElementsByTagName("node");for(int j=0;j<nodes.getLength();j++)assertTrue(ids.add(((org.w3c.dom.Element)nodes.item(j)).getAttribute("id")));var edges=dom.getElementsByTagName("edge");for(int j=0;j<edges.getLength();j++){var e=(org.w3c.dom.Element)edges.item(j);assertTrue(ids.contains(e.getAttribute("source")));assertTrue(ids.contains(e.getAttribute("target")));}}
    @Test void recursiveStructuresStayFiniteAndBudgetsRejectInsteadOfPretendingComplete(){var s=compile(MODEL.replace("note: TEXT;","note: TEXT; children: BAG {0..*} OF Detail;"));var d=document(s,DocumentationOptions.Language.EN,DocumentationOptions.Inheritance.INLINE);assertTrue(new MarkdownDocumentationRenderer().render(d).length()<20_000);assertTrue(d.sections().stream().flatMap(x->x.members().stream()).anyMatch(m->m.references().contains("Catalog.T.Detail")));assertThrows(IllegalArgumentException.class,()->new DocumentationProjector().project(s,new DocumentationOptions(List.of(),DocumentationOptions.Language.EN,DocumentationOptions.Inheritance.INLINE,Map.of(),1),CancellationToken.none()));assertThrows(IllegalArgumentException.class,()->new DocumentationProjector().project(s,new DocumentationOptions(List.of(new SymbolId("missing")),DocumentationOptions.Language.EN,DocumentationOptions.Inheritance.INLINE,Map.of(),100),CancellationToken.none()));}
    private static org.w3c.dom.Document parse(String xml) throws Exception {var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));}
}
