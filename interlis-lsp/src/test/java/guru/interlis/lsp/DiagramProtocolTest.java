package guru.interlis.lsp;

import com.google.gson.*;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DiagramProtocolTest {
    @TempDir Path directory;
    private static final String URI="untitled:Diagram.ili";
    private static final String SOURCE="!! Grüezi 😀\r\nINTERLIS 2.4;\r\nMODEL Diagram AT \"x\" VERSION \"1\" =\r\nDOMAIN Number=0..10;\r\nTOPIC Data=\r\nCLASS Building=\r\n count:Number;\r\nEND Building;\r\nEND Data;END Diagram.\r\n";
    private InterlisLanguageServer server() throws Exception {
        var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
        var init=new InitializeParams();init.setInitializationOptions(Map.of("repositories",List.of()));server.initialize(init).get(5,TimeUnit.SECONDS);return server;
    }
    private void open(InterlisLanguageServer server,String uri,int version,String text) {server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri,"interlis",version,text)));}
    private DiagramResponse diagram(InterlisLanguageServer server,String uri,int version,String options) throws Exception {
        return server.diagram(new DiagramRequest(1,"test-request",new VersionedTextDocumentIdentifier(uri,version),List.of(),options==null?null:JsonParser.parseString(options).getAsJsonObject())).get(10,TimeUnit.SECONDS);
    }
    private DiagramNavigationParams navigation(DiagramResponse response,int version,String id) {
        return new DiagramNavigationParams(1,response.serverInstanceId(),response.diagramId(),response.snapshotId(),new VersionedTextDocumentIdentifier(response.rootUri(),version),id);
    }
    private void failure(ResponseErrorCode expected,Callable<?> action) {
        var failed=assertThrows(ExecutionException.class,()->action.call());assertEquals(expected.getValue(),assertInstanceOf(ResponseErrorException.class,failed.getCause()).getResponseError().getCode());
    }
    @Test void sourceSelectionUsesJavaSymbolsForDeclarationsUsesAndCurrentVersions() throws Exception {
        var server=server();try {
            open(server,URI,3,SOURCE);var response=diagram(server,URI,3,null);
            var building=response.document().nodes().stream().filter(n->n.label().equals("Building")).findFirst().orElseThrow();
            var count=building.compartments().stream().flatMap(c->c.members().stream()).filter(m->m.label().equals("count")).findFirst().orElseThrow();
            var root=new VersionedTextDocumentIdentifier(URI,3);
            var member=server.diagramSelect(new DiagramSelectionParams(1,response.serverInstanceId(),response.diagramId(),response.snapshotId(),root,root,new Position(6,2))).get();
            assertTrue(member.elementIds().contains(count.id()));assertEquals(List.of(count.declaredSymbol().value()),member.symbolIds());
            var number=response.document().nodes().stream().filter(n->n.label().equals("Number")).findFirst().orElseThrow();
            var use=server.diagramSelect(new DiagramSelectionParams(1,response.serverInstanceId(),response.diagramId(),response.snapshotId(),root,root,new Position(6,8))).get();
            assertTrue(use.elementIds().contains(number.id()));assertEquals(List.of(number.symbol().orElseThrow().symbol().value()),use.symbolIds());
            var empty=server.diagramSelect(new DiagramSelectionParams(1,response.serverInstanceId(),response.diagramId(),response.snapshotId(),root,root,new Position(0,2))).get();assertTrue(empty.elementIds().isEmpty());assertTrue(empty.symbolIds().isEmpty());
            failure(ResponseErrorCode.ContentModified,()->server.diagramSelect(new DiagramSelectionParams(1,response.serverInstanceId(),response.diagramId(),response.snapshotId(),root,new VersionedTextDocumentIdentifier(URI,2),new Position(6,2))).get());
            failure(ResponseErrorCode.ContentModified,()->server.diagramSelect(new DiagramSelectionParams(1,"old-server",response.diagramId(),response.snapshotId(),root,root,new Position(6,2))).get());
            failure(ResponseErrorCode.InvalidParams,()->server.diagramSelect(new DiagramSelectionParams(1,response.serverInstanceId(),response.diagramId(),response.snapshotId(),root,root,new Position(0,11))).get());
        }finally{server.shutdown().get(5,TimeUnit.SECONDS);}
    }
    @Test void typedDiagramUsesOnlyExplicitRootAndNavigatesMembersWithCrlfRevisionGuards() throws Exception {
        var server=server();try {
            open(server,"untitled:Other.ili",1,SOURCE.replace("Diagram","Other").replace("Building","Foreign"));open(server,URI,3,SOURCE);
            var response=diagram(server,URI,3,null);assertEquals(1,response.protocolVersion());assertEquals(3,response.documentVersion());assertEquals(3,response.documentVersions().get(URI));
            assertTrue(response.document().nodes().stream().noneMatch(node->node.label().equals("Foreign")));
            var building=response.document().nodes().stream().filter(node->node.label().equals("Building")).findFirst().orElseThrow();
            var location=server.diagramNavigate(navigation(response,3,building.id())).get(5,TimeUnit.SECONDS);
            assertEquals(URI,location.uri());assertEquals(new Position(5,6),location.range().getStart());assertEquals(new Position(5,14),location.range().getEnd());assertFalse(location.readonly());
            var member=building.compartments().stream().flatMap(c->c.members().stream()).filter(m->m.label().equals("count")).findFirst().orElseThrow();
            assertEquals(new Position(6,1),server.diagramNavigate(navigation(response,3,member.id())).get().range().getStart());
            var wire=new Gson().toJsonTree(response).getAsJsonObject();var document=wire.getAsJsonObject("document");
            assertEquals("1.0",document.get("schemaVersion").getAsString());assertEquals(response.snapshotId(),document.get("snapshotId").getAsString());assertTrue(document.getAsJsonObject("sourceRevisions").get(URI).isJsonPrimitive());
            server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI,4),List.of(new TextDocumentContentChangeEvent("\r\n"+SOURCE))));
            failure(ResponseErrorCode.ContentModified,()->server.diagramNavigate(navigation(response,3,member.id())).get(5,TimeUnit.SECONDS));
            failure(ResponseErrorCode.ContentModified,()->server.diagramNavigate(navigation(response,4,member.id())).get(5,TimeUnit.SECONDS));
            var fresh=diagram(server,URI,4,null);assertNotEquals(response.diagramId(),fresh.diagramId());assertNotEquals(response.snapshotId(),fresh.snapshotId());
        } finally {server.shutdown().get(5,TimeUnit.SECONDS);}
    }
    @Test void compactSelfEdgesUseTheCanonicalAssociationAnchorAndBothRoleEnds() throws Exception {
        var server=server();String text="INTERLIS 2.4;MODEL Diagram AT \"x\" VERSION \"1\"=TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;ASSOCIATION Self=parent--{0..1}A;children--{*}A;END Self;END T;END Diagram.";
        try {
            open(server,URI,1,text);var response=diagram(server,URI,1,"{\"schemaVersion\":\"1.0\",\"associationPresentation\":\"COMPACT_BINARY\"}");
            var self=response.document().edges().stream().filter(edge->edge.kind()==guru.interlis.projection.DiagramEdge.EdgeKind.ASSOCIATION).findFirst().orElseThrow();
            assertEquals(self.source(),self.target());assertEquals("parent",self.sourceEnd().roleName().orElseThrow());assertEquals("children",self.targetEnd().roleName().orElseThrow());
            var target=server.diagramNavigate(navigation(response,1,self.id())).get(5,TimeUnit.SECONDS);int column=text.indexOf("ASSOCIATION Self")+"ASSOCIATION ".length();assertEquals(new Range(new Position(0,column),new Position(0,column+4)),target.range());assertEquals(URI,target.uri());
            assertEquals(guru.interlis.projection.ProjectionCompleteness.COMPLETE,response.document().completeness());
        }finally{server.shutdown().get(5,TimeUnit.SECONDS);}
    }
    @Test void strictOptionsAndServerIdentityFailClosedWhilePartialAnalysisIsHonest() throws Exception {
        var server=server();try {
            open(server,URI,1,SOURCE);var response=diagram(server,URI,1,null);
            failure(ResponseErrorCode.InvalidParams,()->diagram(server,URI,1,"{\"schemaVersion\":\"1.0\",\"zoom\":2}"));
            failure(ResponseErrorCode.InvalidParams,()->server.diagram(new DiagramRequest(1,"blank-symbol",new VersionedTextDocumentIdentifier(URI,1),List.of(" "),null)).get());
            failure(ResponseErrorCode.InvalidParams,()->diagram(server,URI,1,"{\"schemaVersion\":\"1.0\",\"translationModel\":{\"snapshot\":\"\",\"symbol\":\"\"}}"));
            failure(ResponseErrorCode.InvalidParams,()->diagram(server,URI,1,"{\"schemaVersion\":\"1.0\",\"budget\":{\"maxNodes\":501}}"));
            failure(ResponseErrorCode.InvalidParams,()->diagram(server,URI,1,"{\"schemaVersion\":\"1.0\",\"neighbourhoodDepth\":1.5}"));
            failure(ResponseErrorCode.ContentModified,()->server.diagramNavigate(new DiagramNavigationParams(1,"different-server",response.diagramId(),response.snapshotId(),new VersionedTextDocumentIdentifier(URI,1),response.document().nodes().getFirst().id())).get());
            failure(ResponseErrorCode.RequestFailed,()->server.diagramNavigate(navigation(response,1,"https://example.invalid/arbitrary.ili")).get());
            server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI,2),List.of(new TextDocumentContentChangeEvent(SOURCE.replace("count:Number","count:Missing")))));
            var partial=diagram(server,URI,2,null);assertEquals(guru.interlis.projection.ProjectionCompleteness.PARTIAL,partial.document().completeness());assertFalse(partial.document().nodes().isEmpty());
        } finally {server.shutdown().get(5,TimeUnit.SECONDS);}
    }
    @Test void transitiveDiagramAnchorsNavigateReadonlyEvenAfterExternalDidOpen() throws Exception {
        var fixture=new RepositoryEditorTest();fixture.directory=directory;String base=fixture.serve();var server=fixture.server(base,false,directory.resolve("cache"));
        try {
            open(server,URI,1,RepositoryEditorTest.ROOT);var response=diagram(server,URI,1,"{\"schemaVersion\":\"1.0\",\"scope\":\"WITH_DEPENDENCIES\"}");
            var imported=response.document().nodes().stream().filter(node->node.canonicalName().equals("Dep.Value")).findFirst().orElseThrow();
            var target=server.diagramNavigate(navigation(response,1,imported.id())).get(5,TimeUnit.SECONDS);assertTrue(target.readonly());assertTrue(target.uri().startsWith("interlis-source:/"));
            var revision=java.net.URI.create(target.uri()).getQuery();var text=server.sourceContent(new SourceContentParams(target.uri(),revision)).get();assertEquals(RepositoryEditorTest.DEP,text.text());
            open(server,target.uri(),1,text.text());
            // Opening a source can accept a new IDE snapshot. Navigate using its current diagram identity.
            var current=diagram(server,URI,1,"{\"schemaVersion\":\"1.0\",\"scope\":\"WITH_DEPENDENCIES\"}");
            var currentImported=current.document().nodes().stream().filter(node->node.canonicalName().equals("Dep.Value")).findFirst().orElseThrow();
            var opened=server.diagramNavigate(navigation(current,1,currentImported.id())).get();assertTrue(opened.readonly());assertEquals(target.uri(),opened.uri());
            var selected=server.diagramSelect(new DiagramSelectionParams(1,current.serverInstanceId(),current.diagramId(),current.snapshotId(),new VersionedTextDocumentIdentifier(URI,1),new VersionedTextDocumentIdentifier(opened.uri(),1),opened.range().getStart())).get();
            assertTrue(selected.elementIds().contains(currentImported.id()));assertEquals(opened.uri(),selected.sourceUri());
        } finally {server.shutdown().get(5,TimeUnit.SECONDS);fixture.stopHttp();}
    }
}
