package guru.interlis.compiler;

import guru.interlis.compiler.query.*;
import guru.interlis.compiler.internal.antlr.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class LexicalProfileContractTest {
    private static CompilationRequest input(String version,String body) {
        var s=source("memory:/words.ili","INTERLIS "+version+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String version,String body) {
        var r=CompilerEngines.create().compile(input(version,body),CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString());
    }
    private static List<String> words(String v) throws Exception {
        var path=Path.of("../testdata/conformance/g2-lexical/reserved-"+v+".txt");
        assertEquals(v.equals("23")?"cddfa17d2232cf109fe0096a99b6e7b814c9fdd5a18467e07ef9f4cc4f55e7d3":"25c1d536c990f547a3cbad2f314d47ad731b8616843f5de38db9b1a7467b838e",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        return Files.readAllLines(path);
    }
    @Test void completeReservedTablesRejectIdentifiersAndPermitMixedCaseNames() throws Exception {
        for(String v:List.of("23","24")) {
            var words=words(v);assertEquals(v.equals("23")?154:168,words.size());
            var language=v.equals("23")?LanguageVersion.ILI23:LanguageVersion.ILI24;
            for(String word:words) {
                assertFalse(LanguageProfile.isIdentifier(word,language),word);
                var input=input(language.literal(),"TOPIC T=CLASS "+word+"=END "+word+";END T;");
                var parsed=parse(input.sources().sources().values().iterator().next());
                assertTrue(parsed.diagnostics().stream().anyMatch(d->d.severity()==guru.interlis.diagnostics.Severity.ERROR),v+":"+word);
                assertTrue(LanguageProfile.isIdentifier(word.toLowerCase(java.util.Locale.ROOT),language),word);
            }
        }
    }
    @Test void new24WordsAreOrdinary23NamesAndRemainReservedIn24() throws Exception {
        var old=new HashSet<>(words("23"));
        for(String word:words("24"))if(!old.contains(word)) {
            var result=ok("2.3","DOMAIN "+word+"=BOOLEAN;TOPIC T=CLASS C=value:"+word+";MANDATORY CONSTRAINT value;END C;END T;");
            assertTrue(result.schema().semanticGraph().symbols().stream().anyMatch(s->s.name().value().equals(word)));
            assertTrue(LanguageProfile.isIdentifier(word,LanguageVersion.ILI23));assertFalse(LanguageProfile.isIdentifier(word,LanguageVersion.ILI24));
        }
        for(String word:List.of("XML","UUIDOID","ANYOID","INTERLIS1","E","PAGE"))for(String v:List.of("2.3","2.4"))
            ok(v,"TOPIC T=CLASS "+word+"=END "+word+";END T;");
    }
    @Test void contextualBlackboxXmlAndVersionedRefsysKeepTheirOwnMeaning() {
        for(String v:List.of("2.3","2.4"))ok(v,"TOPIC T=CLASS XML=box:BLACKBOX XML;END XML;END T;");
        var input=input("2.3","TOPIC T=CLASS C=point:COORD 0..10,0..10 REFSYS \"EPSG:2056\";END C;END T;");
        var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input,CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());assertTrue(rejected.analysis().diagnostics().toString().contains("REFSYS"));
        ok("2.4","TOPIC T=CLASS C=point:COORD 0..10,0..10 REFSYS \"EPSG:2056\";END C;END T;");
    }
    @Test void fragmentAndRenameUseTheSameVersionedIdentifierRules() {
        for(String v:List.of("2.3","2.4")) {
            var result=ok(v,"TOPIC T=CLASS C=value:BOOLEAN;MANDATORY CONSTRAINT value;END C;END T;");var a=result.analysis();
            var c=a.semanticGraph().symbols().stream().filter(ClassSymbol.class::isInstance).findFirst().orElseThrow();
            for(String word:List.of("DATETIME","XML","BLANK","CODE","GENERIC","DATE","UUIDOID")) {
                var plan=LanguageQueries.create().planRename(a,new RenameRequest(new SymbolRef(a.snapshotId(),c.id()),word,a.sourceRevisions()),CancellationToken.none());
                boolean valid=LanguageProfile.isIdentifier(word,c.header().languageVersion());assertEquals(valid,plan.complete(),v+":"+word);
                var source=new SourceText(new SourceId("memory:/fragment.ili"),new SourceRevision(7),"😀\r\n"+word+" suffix");
                var fragment=SyntaxServices.create().parseFragment(source,source.range(4,4+word.length()),FragmentKind.OBJECT_PATH,c.header().languageVersion(),CancellationToken.none());
                assertEquals(valid,fragment.diagnostics().isEmpty(),v+":"+word+":"+fragment.diagnostics());
                if(valid)assertEquals(word,assertInstanceOf(PathExpressionSyntax.class,fragment.root()).segments().getFirst().name().text());
            }
        }
    }
    @Test void nameLengthAndInvalidCharactersAgreeForSyntaxAndRename() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,"TOPIC T=CLASS "+"A".repeat(255)+"=END "+"A".repeat(255)+";END T;");
            var syntax=parse(input(v,"TOPIC T=CLASS "+"A".repeat(256)+"=END "+"A".repeat(256)+";END T;").sources().sources().values().iterator().next());
            assertFalse(syntax.diagnostics().isEmpty());
            for(String invalid:List.of("A".repeat(256),"Ä","_Name","2Name","A$"," A","A ",""))assertFalse(LanguageProfile.isIdentifier(invalid,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24),invalid);
        }
    }
    @Test void nameAlphabetAndLengthBoundariesKeepExactSourceIdentityAndRename() {
        for(String v:List.of("2.3","2.4")) {
            var result=ok(v,"TOPIC T=CLASS A_a09=END A_a09;END T;");
            var graph=result.schema().semanticGraph();
            var symbol=graph.symbols().stream().filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                    .filter(s->s.name().value().equals("A_a09")).findFirst().orElseThrow();
            var source=result.analysis().syntaxSnapshots().values().iterator().next().source();
            var nameRange=symbol.nameRange().orElseThrow();
            assertEquals("A_a09",source.slice(nameRange));
            assertTrue(LanguageQueries.create().symbolAt(result.analysis(),
                    source.range(nameRange.startOffset(),nameRange.startOffset()),CancellationToken.none()).known().isPresent());

            for(String invalid:List.of("_A","0A","Aä","")) {
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,
                        CompilerEngines.create().compile(input(v,"TOPIC T=CLASS "+invalid+"=END "+invalid+";END T;"),CancellationToken.none()));
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons(),v+":"+invalid);
            }
            var commented=ok(v,"!! äöü 😀\nTOPIC T=CLASS C=END C;END T;");
            var commentSource=commented.analysis().syntaxSnapshots().values().iterator().next();
            assertTrue(commentSource.trivia().stream().anyMatch(t->t.text().contains("äöü 😀")));

            var longName="A".repeat(255);
            var longResult=ok(v,"TOPIC T=CLASS "+longName+"=END "+longName+";END T;");
            var longGraph=longResult.schema().semanticGraph();
            var longSymbol=longGraph.symbols().stream().filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                    .filter(s->s.name().value().equals(longName)).findFirst().orElseThrow();
            var longSource=longResult.analysis().syntaxSnapshots().values().iterator().next().source();
            var longRange=longSymbol.nameRange().orElseThrow();
            assertEquals(255,longSymbol.name().value().length());
            assertEquals(255,longRange.length());
            assertEquals(longName,longSource.slice(longRange));
            var renameHandle=LanguageQueries.create().symbolAt(longResult.analysis(),
                    longSource.range(longRange.startOffset(),longRange.startOffset()),CancellationToken.none()).known().orElseThrow();
            var complete=LanguageQueries.create().planRename(longResult.analysis(),
                    new RenameRequest(renameHandle,"B".repeat(255),longResult.analysis().sourceRevisions()),CancellationToken.none());
            assertTrue(complete.complete(),()->complete.reason().orElse("incomplete"));
            assertEquals(255,complete.edits().stream().flatMap(e->e.edits().stream())
                    .filter(e->e.range().equals(longRange)).findFirst().orElseThrow().replacement().length());
            var incomplete=LanguageQueries.create().planRename(longResult.analysis(),
                    new RenameRequest(renameHandle,"B".repeat(256),longResult.analysis().sourceRevisions()),CancellationToken.none());
            assertFalse(incomplete.complete());
            assertTrue(incomplete.reason().isPresent());

            var tooLong=assertInstanceOf(CompilationOutcome.Rejected.class,
                    CompilerEngines.create().compile(input(v,"TOPIC T=CLASS "+"A".repeat(256)+"=END "+"A".repeat(256)+";END T;"),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),tooLong.rejection().reasons());
        }
    }
    @Test void regeneratedLexerAndParserShareTheirTokenVocabulary() {
        assertEquals(InterlisLexer.INTERLIS,InterlisParser.INTERLIS);assertEquals(InterlisLexer.Name,InterlisParser.Name);assertEquals(InterlisLexer.RESERVED_WORD,InterlisParser.RESERVED_WORD);
        for(String v:List.of("2.3","2.4"))ok(v,"TOPIC T=CLASS C=value:BOOLEAN;MANDATORY CONSTRAINT value;END C;END T;");
    }
    @Test void booleanOriginsFollowEachOwnerInMixedLanguageClosuresAndReuse() {
        for(boolean reverse:List.of(false,true)) {
            var a=source(reverse?"memory:/z-a.ili":"memory:/a-a.ili","INTERLIS 2.3;MODEL A AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS C=value:BOOLEAN;MANDATORY CONSTRAINT #true AND NOT(value) AND DEFINED(value) AND INTERLIS.isSubClass(>C,>C);END C;END T;END A.");
            var b=source(reverse?"memory:/a-b.ili":"memory:/z-b.ili",a.text().replace("2.3","2.4").replace("MODEL A","MODEL B").replace("END A.","END B."));
            var input=request(List.of("A","B"),Map.of(a.id(),a,b.id(),b),List.of(),CompilerOptions.defaults());var engine=CompilerEngines.create();
            var fresh=engine.analyze(input,CancellationToken.none());assertTrue(fresh.diagnostics().isEmpty(),fresh.diagnostics().toString());
            var reused=engine.analyze(input,new AnalysisReuse(Optional.of(fresh),fresh.syntaxSnapshots()),CancellationToken.none());assertEquals(fresh.diagnostics(),reused.diagnostics());
            for(var graph:List.of(fresh.semanticGraph(),reused.semanticGraph()))for(var e:graph.expressions()) {
                var type=graph.type(e.typeId()).orElseThrow();if(type instanceof BooleanType && type.origin() instanceof Origin.PredefinedOrigin predefined)
                    assertEquals(e.range().source().equals(a.id())?LanguageVersion.ILI23:LanguageVersion.ILI24,predefined.language());
            }
        }
    }
}
