package guru.interlis.cli.verification;

import com.google.gson.*;
import guru.interlis.compiler.*;
import guru.interlis.diagnostics.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** The original stays byte-pinned; only the separately named control may succeed. */
class CorpusOuterNameContractTest {
    static final Path ROOT=Path.of(System.getProperty("repo.root"));
    static CompilationRequest request(String model,String text,long revision) {
        var source=new SourceText(new SourceId("memory:/corpus.ili"),new SourceRevision(revision),text);
        return new CompilationRequest(List.of(new ModelSelector.ByName(model,Optional.empty(),Optional.empty())),SourceSet.of(Map.of(source.id(),source)),CompilerOptions.defaults());
    }
    @Test void pinnedOriginalHasOneExactSourceErrorAndRenamedControlHasActualInheritedMembers() throws Exception {
        var manifest=read(ROOT.resolve("docs/evidence/p3/geo-corpus.json"));
        for(var expected:CorpusExpectations.load(ROOT,manifest)) {
        String original;
        try(var zip=new ZipFile(ROOT.resolve("docs/evidence/p3/corpus-seed.zip").toFile())) {
            byte[] bytes=zip.getInputStream(zip.getEntry(s(expected,"file").replace("/","__"))).readAllBytes();assertEquals(s(expected,"sha256"),hash(bytes));original=new String(bytes,StandardCharsets.UTF_8);
        }
        var engine=CompilerEngines.create();var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,engine.compile(request(s(expected,"model"),original,1),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());assertEquals(1,rejected.analysis().diagnostics().size());
        var actual=rejected.analysis().diagnostics().getFirst();var wanted=rows(expected,"diagnostics").getFirst();
        assertEquals(s(wanted,"code"),actual.code().value());assertEquals(s(wanted,"severity"),actual.severity().name());assertEquals(s(wanted,"phase"),actual.phase().name());assertEquals(s(wanted,"messageKey"),actual.messageKey());
        var range=wanted.getAsJsonObject("range");assertEquals(new SourceRange(new SourceId("memory:/corpus.ili"),new SourceRevision(1),range.get("startOffset").getAsInt(),range.get("endOffset").getAsInt()),actual.primaryRange().orElseThrow());
        assertEquals(List.of(new DiagnosticArgument.IdentifierArgument("element",s(rows(wanted,"arguments").getFirst(),"value")),new DiagnosticArgument.TextArgument("rule",s(rows(wanted,"arguments").get(1),"value"))),actual.arguments());
        var controlProfile=expected.getAsJsonObject("control");String control=Files.readString(ROOT.resolve(s(controlProfile,"source"))),renamed=original;
        for(var rename:rows(controlProfile,"renames"))renamed=renamed.replace(s(rename,"from"),s(rename,"to"));assertEquals(renamed,control);
        for(var reuse:List.of(AnalysisReuse.none(),new AnalysisReuse(Optional.of(rejected.analysis()),rejected.analysis().syntaxSnapshots()))) {
            var success=assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(request(s(expected,"model"),control,2),reuse,CancellationToken.none()));var schema=success.schema();var graph=schema.semanticGraph();
            var derived=schema.queries().findQualifiedName(new QualifiedName(List.of(s(controlProfile,"className").split("\\.")))).getFirst();
            var actualClass=assertInstanceOf(ClassSymbol.class,graph.symbol(derived.symbol()).orElseThrow());
            if(controlProfile.has("baseStructure")) {
            var base=schema.queries().findQualifiedName(new QualifiedName(List.of(s(controlProfile,"baseStructure").split("\\.")))).getFirst();
            assertInstanceOf(StructureSymbol.class,graph.symbol(base.symbol()).orElseThrow());
            assertEquals(Optional.of(base.symbol()),actualClass.baseType());
            } else assertEquals(Optional.empty(),actualClass.baseType());
            assertEquals(strings(controlProfile.get("members")),graph.effectiveMembers(derived.symbol()).stream().map(m->graph.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
        }
        }
    }
    @Test void wrongSuccessExitDiagnosticSourceRangeAndMultiplicityCannotPassTheNegativeBenchmark() throws Exception {
        var expected=CorpusExpectations.load(ROOT,read(ROOT.resolve("docs/evidence/p3/geo-corpus.json"))).getFirst();Path source=ROOT.resolve("original.ili");
        var diagnostic=rows(expected,"diagnostics").getFirst().deepCopy();diagnostic.addProperty("id","diag-example");var range=diagnostic.getAsJsonObject("range");range.addProperty("source",source.toUri().toString());range.addProperty("revision","1");
        var payload=new JsonObject();payload.addProperty("status","rejected: RejectionSummary[reasons=[SOURCE_ERRORS], unsupportedFeatureIds=[]]");var diagnostics=new JsonArray();diagnostics.add(diagnostic);payload.add("diagnostics",diagnostics);
        var good=new Result(List.of(),1,payload.toString(),"",1);CorpusExpectations.check(good,expected,source);JvmBenchmark.stable(good,good,1);
        for(String mutation:List.of("success","exit","source","range","code","missing","duplicate")) {
            var bad=payload.deepCopy();var list=bad.getAsJsonArray("diagnostics");var item=list.get(0).getAsJsonObject();
            switch(mutation) {
                case "success"->bad.addProperty("status","ok");
                case "source"->item.getAsJsonObject("range").addProperty("source","memory:/wrong.ili");
                case "range"->item.getAsJsonObject("range").addProperty("startOffset",0);
                case "code"->item.addProperty("code","ILI-INF-0001");
                case "missing"->list.remove(0);
                case "duplicate"->list.add(item.deepCopy());
            }
            var result=new Result(List.of(),mutation.equals("exit")?3:1,bad.toString(),"",1);assertThrows(Exception.class,()->CorpusExpectations.check(result,expected,source),mutation);
        }
        assertThrows(Exception.class,()->JvmBenchmark.stable(good,new Result(List.of(),0,good.stdout(),"",1),1));
    }
}
