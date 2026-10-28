package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Import translation correspondences use selected identities, not available names. */
class ImportedTranslationContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String declaration(String name, String original, String body) {
        return "MODEL " + name + " AT \"https://example.org\" VERSION \"1\" "
                + (original == null ? "" : "TRANSLATION OF " + original + " [\"1\"] ") + "= " + body + " END " + name + ".";
    }
    private static String imported(boolean translated) {
        return translated ? "DOMAIN DD = 0..10; EE = 0..10; TOPIC TT = CLASS AA = xx: DD; END AA; CLASS BB = xx: DD; END BB; END TT;"
                : "DOMAIN D = 0..10; E = 0..10; TOPIC T = CLASS A = x: D; END A; CLASS B = x: D; END B; END T;";
    }
    private static String consumer(boolean translated) {
        String m = translated ? "Translated" : "Original", t = translated ? "TT" : "T", c = translated ? "AA" : "A", a = translated ? "xx" : "x", d = translated ? "DD" : "D";
        return "IMPORTS " + m + "; TOPIC U = DEPENDS ON " + m + "." + t + "; CLASS C EXTENDS " + m + "." + t + "." + c
                + " = value: " + m + "." + d + "; MANDATORY CONSTRAINT value==1; END C; VIEW V PROJECTION OF a~" + m + "." + t + "." + c
                + "; WHERE a->" + a + "==1; = ALL OF a; END V; END U;";
    }
    private static List<String> models() {
        return List.of(declaration("Original", null, imported(false)), declaration("Translated", "Original", imported(true)),
                declaration("Base", null, consumer(false)), declaration("F", "Base", consumer(true)));
    }
    private static CompilationRequest request(String version, List<String> models, boolean reverse, int revision) {
        Map<SourceId, SourceText> sources = new LinkedHashMap<>();
        var order = new ArrayList<Integer>(); for (int i=0;i<models.size();i++) order.add(i);
        if (reverse) Collections.reverse(order);
        for (int i : order) {
            var source = new SourceText(new SourceId("memory:/" + (reverse ? models.size()-i : i) + ".ili"), new SourceRevision(revision), models.get(i).startsWith("INTERLIS ") ? models.get(i) : "INTERLIS " + version + "; " + models.get(i));
            sources.put(source.id(),source);
        }
        var syntax = sources.values().stream().map(CompilerTestSupport::parse).toList();
        List<ImportBinding> imports = new ArrayList<>();
        for (var s : syntax) for (var model : s.root().models()) for (var imported : model.imports()) {
            var target = syntax.stream().flatMap(x -> x.root().models().stream().map(m -> Map.entry(x,m)))
                    .filter(x -> x.getValue().name().equals(imported.modelName())).findFirst().orElseThrow();
            imports.add(new ImportBinding(new ImportSite(imported.range(),model.name(),imported.modelName()),new ImportResolution.Resolved(
                    new ModelLocation(target.getKey().source().id(),target.getKey().source().revision(),target.getValue().name(),
                            Optional.of(target.getValue().modelVersion()),target.getValue().range().startOffset()))));
        }
        return CompilerTestSupport.request(List.of("F"),sources,imports,CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded success(CompilationRequest request) {
        var result=CompilerEngines.create().compile(request,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result, () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
    }
    private static Symbol symbol(SemanticGraph graph,String name) {
        return graph.symbol(graph.qualifiedNames().getOrDefault(new QualifiedName(List.of(name.split("\\."))),List.of()).getFirst()).orElseThrow();
    }

    @Test void pinnedOriginalFindingRetainsTheImportedViewAndAttributeTargets() throws Exception {
        var pins=Map.of("2.3","39a8f3f6bbd5287f0608d3208fc53a799f188d43bac43c5f65ec9ba3e59e0629",
                "2.4","d5705603281688cf11b88f3b18c2dc1b9e0692f436bccc0fe53e443c86556477");
        for (var version:List.of("2.3","2.4")) {
            var path=java.nio.file.Path.of("../testdata/conformance/g2-translations/translated-import-"+version+".ili");
            var bytes=java.nio.file.Files.readAllBytes(path);
            assertEquals(pins.get(version),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            var result=success(request(version,List.of(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)),false,1));
            var graph=result.schema().semanticGraph();var t=((ModelSymbol)symbol(graph,"F")).translation().orElseThrow();
            assertTrue(t.correspondences().contains(new TranslationLink(symbol(graph,"Original.T.A.x").id(),symbol(graph,"Translated.T.A.x").id())));
            var view=assertInstanceOf(ViewSymbol.class,symbol(graph,"F.U.V"));
            assertEquals(symbol(graph,"Translated.T.A").id(),((ViewSymbol.Base)graph.symbol(view.bases().getFirst()).orElseThrow()).target());
            var attribute=(AttributeSymbol)graph.symbol(view.members().getFirst()).orElseThrow();
            assertEquals(symbol(graph,"Translated.T.A.x").id(),((PathStep.AttributeStep)((PathExpression)graph.expression(attribute.derivations().getFirst()).orElseThrow()).path().steps().getLast()).target());
        }
    }

    @Test void translationCyclesHaveNoCanonicalIdentity() {
        for (var version:List.of("2.3","2.4")) {
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,
                    List.of(declaration("Base","F",""),declaration("F","Base","")),false,1),TOKEN));
            assertTrue(result.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains("TRANSLATION_CYCLE")));
        }
    }

    @Test void theDeclaredOriginalVersionSelectsItsIdentityWithoutALatestFallback() {
        for (var version:List.of("2.3","2.4")) for (boolean reversed:List.of(false,true)) {
            var ms=List.of(declaration("Original",null,"DOMAIN N=0..10;"),
                    declaration("Original",null,"DOMAIN N=0..20;").replace("VERSION \"1\"","VERSION \"2\""),
                    declaration("F","Original","DOMAIN Num=0..20;").replace("[\"1\"]","[\"2\"]"));
            var result=success(request(version,ms,reversed,1));var graph=result.schema().semanticGraph();
            var translation=((ModelSymbol)symbol(graph,"F")).translation().orElseThrow();
            var original=(ModelSymbol)graph.symbol(translation.sourceTarget().orElseThrow()).orElseThrow();
            assertEquals("2",original.modelVersion());
            assertEquals(List.of(original.id()),graph.qualifiedNames().get(new QualifiedName(List.of("Original"))));
            var missing=new ArrayList<>(ms);missing.set(2,ms.get(2).replace("[\"2\"]","[\"missing\"]"));
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,missing,reversed,1),TOKEN));
            assertTrue(rejected.rejection().reasons().contains(RejectionReason.MISSING_INPUT));
        }
    }

    @Test void pinnedEnumOriginalsExposeEveryDeclaredEnumCorrespondence() throws Exception {
        var pins=Map.of("mandCstrtEnumTreeConstant","d005ed759227bb3823a7e68ae576846120e17c0ccf90ab53f033c2bb5cc7f924",
                "mandCstrtEnumerationConstant","bbe28fac1295bb07930e51c9fc991d4477377a25db951832dbd540066ecdebd6",
                "enumComplete","6ec704d575fefe5e94b7aec3ececb2fd0326b60704e85b7d5831dbfce7b901db");
        for (var entry:pins.entrySet()) {
            var bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../testdata/conformance/g2-translations/enums/"+entry.getKey()+".ili"));
            assertEquals(entry.getValue(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            var input=request("2.3",List.of(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)),false,1);
            var result=success(new CompilationRequest(List.of(new ModelSelector.ByName("ModelB",Optional.empty(),Optional.empty())),input.sources(),input.options()));
            var graph=result.schema().semanticGraph();var t=((ModelSymbol)symbol(graph,"ModelB")).translation().orElseThrow();
            int enums=0;
            for (var link:t.correspondences()) if (graph.symbol(link.original()).orElseThrow() instanceof EnumElementSymbol original) {
                var translated=assertInstanceOf(EnumElementSymbol.class,graph.symbol(link.translated()).orElseThrow());
                assertEquals(original.ordinal(),translated.ordinal());assertEquals(original.path().size(),translated.path().size());
                var owner=new SymbolRef(result.analysis().snapshotId(),original.owner().orElseThrow());
                var tree=assertInstanceOf(EnumerationType.class,result.analysis().queries().declaredType(owner).known().orElseThrow());
                assertEquals(tree.values().stream().map(EnumerationValue::path).toList().indexOf(String.join(".",original.path())),original.ordinal());
                assertTrue(original.nameRange().isPresent());assertTrue(translated.nameRange().isPresent());enums++;
            }
            assertTrue(enums>0);assertEquals(2*enums,graph.symbols().stream().filter(EnumElementSymbol.class::isInstance).count());
        }
    }

    @Test void renamedImportsExposeTheBoundCorrespondencesAndViewTargets() {
        for (var version:List.of("2.3","2.4")) for (boolean reversed:List.of(false,true)) {
            var result=success(request(version,models(),reversed,1));var graph=result.schema().semanticGraph();
            var f=assertInstanceOf(ModelSymbol.class,symbol(graph,"F"));var translation=f.translation().orElseThrow();
            assertEquals(symbol(graph,"Base").id(),translation.sourceTarget().orElseThrow());
            assertTrue(translation.correspondences().contains(new TranslationLink(symbol(graph,"Original.T.A").id(),symbol(graph,"Translated.TT.AA").id())));
            assertTrue(translation.correspondences().contains(new TranslationLink(symbol(graph,"Original.D").id(),symbol(graph,"Translated.DD").id())));
            assertTrue(translation.correspondences().contains(new TranslationLink(symbol(graph,"Original.T.A.x").id(),symbol(graph,"Translated.TT.AA.xx").id())));
            var view=assertInstanceOf(ViewSymbol.class,symbol(graph,"F.U.V"));
            assertEquals(symbol(graph,"Translated.TT.AA").id(),assertInstanceOf(ViewSymbol.Base.class,graph.symbol(view.bases().getFirst()).orElseThrow()).target());
            var inherited=assertInstanceOf(ClassSymbol.class,symbol(graph,"F.U.C"));
            assertEquals(symbol(graph,"Translated.TT.AA").id(),inherited.baseType().orElseThrow());
            assertEquals("Original.T.A",graph.symbol(translation.correspondences().stream().filter(l -> l.translated().equals(inherited.baseType().orElseThrow())).findFirst().orElseThrow().original())
                    .map(s -> "Original.T."+s.name().value()).orElseThrow());
            assertNotEquals(symbol(graph,"Original.T.A").id(),inherited.baseType().orElseThrow());
            var query=result.analysis().queries().symbol(new SymbolRef(result.analysis().snapshotId(),f.id())).known().orElseThrow();
            assertEquals(translation,((ModelSymbol)query).translation().orElseThrow());
        }
    }

    @Test void unusedLanguageVariantsDoNotChangeUntranslatedImports() {
        for (var version:List.of("2.3","2.4")) for (boolean reversed:List.of(false,true)) {
            var ms=new ArrayList<>(models());ms.set(3,declaration("F","Base",consumer(false)));
            ms.add(declaration("OtherLanguage","Original",imported(false)));
            var result=success(request(version,ms,reversed,1));var graph=result.schema().semanticGraph();
            var t=((ModelSymbol)symbol(graph,"F")).translation().orElseThrow();
            assertTrue(t.correspondences().contains(new TranslationLink(symbol(graph,"Original.T.A").id(),symbol(graph,"Original.T.A").id())));
            assertTrue(graph.qualifiedNames().getOrDefault(new QualifiedName(List.of("OtherLanguage")),List.of()).isEmpty());
        }
    }

    @Test void equalTypedWrongImportedTargetsRemainErrors() {
        for (var version:List.of("2.3","2.4")) for (String wrong:List.of(consumer(true).replace("Translated.DD","Translated.EE"),consumer(true).replace("a~Translated.TT.AA","a~Translated.TT.BB"))) {
            var ms=new ArrayList<>(models());ms.set(3,declaration("F","Base",wrong));
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,ms,false,1),TOKEN));
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains("TRANSLATION_REFERENCE")),rejected.analysis().diagnostics().toString());
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
        }
    }

    @Test void transitiveImportsRetainTheirOwnCorrespondences() {
        for (var version:List.of("2.3","2.4")) {
            var ms=new ArrayList<>(models());
            ms.set(0,declaration("Original",null,"IMPORTS Leaf; "+imported(false).replace("x: D","x: Leaf.N")));
            ms.set(1,declaration("Translated","Original","IMPORTS LeafFR; "+imported(true).replace("xx: DD","xx: LeafFR.Num")));
            ms.add(declaration("Leaf",null,"DOMAIN N=0..10;"));ms.add(declaration("LeafFR","Leaf","DOMAIN Num=0..10;"));
            var graph=success(request(version,ms,true,1)).schema().semanticGraph();
            var t=((ModelSymbol)symbol(graph,"F")).translation().orElseThrow();
            assertTrue(t.correspondences().contains(new TranslationLink(symbol(graph,"Leaf.N").id(),symbol(graph,"LeafFR.Num").id())));
        }
    }

    @Test void conflictingTransitiveChoicesAreRejectedInsteadOfOverwritingAMapping() {
        for (var version:List.of("2.3","2.4")) {
            var ms=List.of(declaration("Shared",null,"DOMAIN N=0..10;"),declaration("SharedFR","Shared","DOMAIN N=0..10;"),
                    declaration("SharedDE","Shared","DOMAIN N=0..10;"),declaration("Left",null,"IMPORTS Shared;"),
                    declaration("Right",null,"IMPORTS Shared;"),declaration("LeftFR","Left","IMPORTS SharedFR;"),
                    declaration("RightFR","Right","IMPORTS SharedDE;"),declaration("Base",null,"IMPORTS Left, Right;"),
                    declaration("F","Base","IMPORTS LeftFR, RightFR;"));
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,ms,false,1),TOKEN));
            assertTrue(result.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains("TRANSLATION_STRUCTURE")));
            assertTrue(((ModelSymbol)symbol(result.analysis().semanticGraph(),"F")).translation().orElseThrow().correspondences().isEmpty());
        }
    }

    @Test void importPairingPreservesQualificationWithoutDependingOnDeclarationOrder() {
        for (var version:List.of("2.3","2.4")) {
            var ms=List.of(declaration("One",null,"DOMAIN D=0..1;"),declaration("Two",null,"DOMAIN N=0..1;"),
                    declaration("Base",null,"IMPORTS UNQUALIFIED One, Two;"),declaration("F","Base","IMPORTS Two, UNQUALIFIED One;"));
            success(request(version,ms,false,1));
            var bad=new ArrayList<>(ms);bad.set(3,declaration("F","Base","IMPORTS One, UNQUALIFIED Two;"));
            assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,bad,false,1),TOKEN));
        }
    }

    @Test void theSameTranslatedNameInDifferentImportsKeepsItsBoundMeaning() {
        for (var version:List.of("2.3","2.4")) {
            var ms=List.of(declaration("One",null,"DOMAIN NumericValue=0..10;"),declaration("OneFR","One","DOMAIN Value=0..10;"),
                    declaration("Two",null,"DOMAIN TextValue=TEXT*10;"),declaration("TwoFR","Two","DOMAIN Value=TEXT*10;"),
                    declaration("Base",null,"IMPORTS One, Two; CLASS C (ABSTRACT) = number: One.NumericValue; text: Two.TextValue; END C;"),
                    declaration("F","Base","IMPORTS OneFR, TwoFR; CLASS D (ABSTRACT) = nombre: OneFR.Value; texte: TwoFR.Value; END D;"));
            var graph=success(request(version,ms,true,1)).schema().semanticGraph();
            var a=(AttributeSymbol)symbol(graph,"F.D.nombre");var b=(AttributeSymbol)symbol(graph,"F.D.texte");
            assertEquals(symbol(graph,"OneFR.Value").id(),((DomainReferenceType)graph.type(a.declaredType()).orElseThrow()).targetDomain());
            assertEquals(symbol(graph,"TwoFR.Value").id(),((DomainReferenceType)graph.type(b.declaredType()).orElseThrow()).targetDomain());
        }
    }

    @Test void editsRebuildImportMappingsAndMatchFreshResults() {
        for (var version:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;
            for (int step=1;step<=3;step++) {
                var ms=new ArrayList<>(models());if(step==2)ms.set(3,declaration("F","Base",consumer(true).replace("Translated.DD","Translated.EE")));
                var input=request(version,ms,false,step);
                var reused=engine.analyze(input,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                var fresh=CompilerEngines.create().analyze(input,TOKEN);assertEquals(fresh.diagnostics(),reused.diagnostics());
                assertEquals(step!=2,reused.diagnostics().isEmpty(),reused.diagnostics().toString());
                var expected=((ModelSymbol)symbol(fresh.semanticGraph(),"F")).translation().orElseThrow();
                var actual=((ModelSymbol)symbol(reused.semanticGraph(),"F")).translation().orElseThrow();
                assertEquals(expected.correspondences().stream().map(l -> List.of(fresh.semanticGraph().symbol(l.original()).orElseThrow().name(),fresh.semanticGraph().symbol(l.translated()).orElseThrow().name())).toList(),
                        actual.correspondences().stream().map(l -> List.of(reused.semanticGraph().symbol(l.original()).orElseThrow().name(),reused.semanticGraph().symbol(l.translated()).orElseThrow().name())).toList());
                previous=reused;
            }
        }
    }
}
