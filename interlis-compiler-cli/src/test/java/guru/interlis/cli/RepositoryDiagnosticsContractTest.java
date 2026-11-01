package guru.interlis.cli;

import com.google.gson.JsonParser;
import guru.interlis.diagnostics.*;
import guru.interlis.execution.CancellationToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryDiagnosticsContractTest {
    @TempDir Path directory;
    record Run(CliExecutionResult result,String out,String err) { }
    Run run(String...args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        var result=new DefaultCompilerCli().run(List.of(args),new CliEnvironment(directory,InputStream.nullInputStream(),new PrintStream(out),new PrintStream(err),Map.of()),CancellationToken.none());
        return new Run(result,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    static String model(String name,String body) {return "INTERLIS 2.4; MODEL "+name+" AT \"x\" VERSION \"1\" = "+body+" END "+name+".";}
    static String entry(String name,String version,String file) {return "<ModelMetadata><Name>"+name+"</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>"+version+"</Version><File>"+file+"</File></ModelMetadata>";}
    Path repository(String entries) throws Exception {
        var repo=Files.createDirectory(directory.resolve("repo"));Files.writeString(repo.resolve("ilimodels.xml"),"<TRANSFER>"+entries+"</TRANSFER>");return repo;
    }
    @Test void allCommandsPresentMissingImportsAtTheirActualSourceWithLocalizedVerboseAndJsonDetails() throws Exception {
        Path root=directory.resolve("root.ili");Files.writeString(root,model("Root","IMPORTS Missing; STRUCTURE P=p:Missing.Point; END P;"));
        var repo=repository(entry("Other","1","other.ili"));
        for(String command:List.of("check","compile","analyze","diagram")) {
            var compact=run(command,"--repository",repo.toString(),root.toString());
            assertEquals(1,compact.result.exitCode());assertEquals(CliTermination.INPUT_UNRESOLVED,compact.result.termination());
            assertTrue(compact.err.contains("ILI-RES-0001"));assertTrue(compact.err.contains("Missing"));assertTrue(compact.err.contains("root.ili:1:"));
            assertFalse(compact.err.contains("resolution rejected:"));assertFalse(compact.err.contains("Hint:"));assertTrue(compact.result.artifacts().isEmpty());
            var verbose=run(command,"--repository",repo.toString(),"--verbose","--diagnostic-language","de",root.toString());
            assertTrue(verbose.err.contains("ERROR [ILI-RES-0001]"));assertTrue(verbose.err.contains("Hinweis:"));assertTrue(verbose.err.contains("LOOKUP"));assertTrue(verbose.err.contains("IMPORTS Missing"));
            if (command.equals("diagram")) continue; // --format json selects diagram artifacts, not diagnostic JSON.
            var json=run(command,"--repository",repo.toString(),"--json",root.toString());
            var payload=JsonParser.parseString(json.out).getAsJsonObject();assertFalse(payload.getAsJsonArray("diagnostics").isEmpty());
            assertFalse(payload.getAsJsonObject("resolution").getAsJsonArray("steps").isEmpty());assertTrue(json.err.isEmpty());
            assertFalse(json.result.repositorySteps().isEmpty());assertEquals(compact.result.diagnostics(),json.result.diagnostics());
        }
    }
    @Test void downloadedImportFailureShowsItsTextAndUtf16Position() throws Exception {
        var repo=repository(entry("Base","1","Base.ili"));
        String text="INTERLIS 2.4;\n!! 😀 ä\nMODEL Base AT \"x\" VERSION \"1\" = IMPORTS Missing; END Base.";
        Files.writeString(repo.resolve("Base.ili"),text);Files.writeString(directory.resolve("root.ili"),model("Root","IMPORTS Base;"));
        var out=run("check","--repository",repo.toString(),"--verbose","root.ili");
        var diagnostic=out.result.diagnostics().stream().filter(d->d.code().value().equals("ILI-RES-0001")).findFirst().orElseThrow();
        assertEquals(text.indexOf("Missing"),diagnostic.primaryRange().orElseThrow().startOffset());
        assertTrue(out.err.contains("Base.ili:3:"));assertTrue(out.err.contains("IMPORTS Missing"));
    }
    @Test void missingImportDependentsAreHiddenOnlyInTextAndIndependentNamesRemainVisible() throws Exception {
        Files.writeString(directory.resolve("root.ili"),model("Root","IMPORTS Missing; STRUCTURE P=p:Missing.Point; q:Other; END P;"));
        var out=run("check","root.ili");
        var dependent=out.result.diagnostics().stream().filter(d->d.code().value().equals("ILI-SEM-0001")&&d.arguments().toString().contains("Missing.Point")).findFirst().orElseThrow();
        assertTrue(dependent.causeId().isPresent());assertTrue(dependent.dependsOn().contains(dependent.causeId().orElseThrow()));
        assertFalse(out.err.contains("'Missing.Point'"));assertTrue(out.err.contains("'Other'"));
        var json=run("check","--json","root.ili");assertTrue(json.out.contains("Missing.Point"));assertTrue(json.out.contains("causeId"));
    }
    @Test void duplicateStartWarningsReachTheGateWhileAnalyzeRetainsItsAnalysisContract() throws Exception {
        var repo=repository(entry("Dep","1","Dep.ili")+entry("Dep","2","Other.ili"));Files.writeString(repo.resolve("Dep.ili"),model("Dep","DOMAIN D=TEXT;"));
        for(String command:List.of("check","compile","diagram")) {
            var rejected=run(command,"--repository",repo.toString(),"--root","Dep","--warnings","reject");
            assertEquals(CliTermination.WARNINGS_REJECTED,rejected.result.termination(),rejected.err);
            assertTrue(rejected.result.rejectionSummary().orElseThrow().reasons().contains(guru.interlis.compiler.RejectionReason.WARNINGS_REJECTED));
        }
        var analysis=run("analyze","--repository",repo.toString(),"--root","Dep","--warnings","reject");assertEquals(0,analysis.result.exitCode());assertTrue(analysis.err.contains("WARNING [ILI-RES-0008]"));
        var selected=run("check","--repository",repo.toString(),"--root","Dep","--json");
        assertEquals("1",JsonParser.parseString(selected.out).getAsJsonObject().getAsJsonObject("resolution").getAsJsonArray("models").get(0).getAsJsonObject().get("version").getAsString());
        assertEquals(2,selected.result.resolutionLock().orElseThrow().formatVersion());
    }
    @Test void lockCannotOverwriteDiscoveredSiteMetadata() throws Exception {
        var repo=repository(entry("Dep","1","Dep.ili"));Files.writeString(repo.resolve("Dep.ili"),model("Dep",""));
        var mother=Files.createDirectory(directory.resolve("mother"));Files.writeString(mother.resolve("ilimodels.xml"),"<TRANSFER>"+entry("Other","1","other.ili")+"</TRANSFER>");
        String site="<TRANSFER><IliSite09.SiteMetadata.Site><subsidiarySite><RepositoryLocation_><value>"+repo.toUri()+"</value></RepositoryLocation_></subsidiarySite></IliSite09.SiteMetadata.Site></TRANSFER>";
        Files.writeString(mother.resolve("ilisite.xml"),site);
        var out=run("check","--repository",mother.toString(),"--root","Dep","--write-lock",mother.resolve("ilisite.xml").toString());
        assertEquals(CliTermination.USAGE_ERROR,out.result.termination());assertEquals(site,Files.readString(mother.resolve("ilisite.xml")));
    }
    @Test void malformedDownloadedSourcesExposeParserDiagnosticsAndTheirOwnText() throws Exception {
        var repo=repository(entry("Base","1","Base.ili"));
        Files.writeString(repo.resolve("Base.ili"),"INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = STRUCTURE P= p:TEXT END Base.");
        Files.writeString(directory.resolve("root.ili"),model("Root","IMPORTS Base;"));
        var out=run("check","--repository",repo.toString(),"--verbose","root.ili");
        assertEquals(1,out.result.exitCode());assertTrue(out.err.contains("Base.ili:1:"));
        assertTrue(out.result.diagnostics().stream().anyMatch(d -> d.code().value().startsWith("ILI-SYN-")),out.err);
        assertTrue(out.result.artifacts().isEmpty());
    }

}
