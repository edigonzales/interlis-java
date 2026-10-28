package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
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

/** E1: versioned empty function lists and the complete predefined translation class. */
class E1FunctionMetadataContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.";
    }
    private static CompilationRequest input(String text, int revision) {
        var s = new SourceText(new SourceId("memory:/e1.ili"), new SourceRevision(revision), text);
        return request(List.of("F"), Map.of(s.id(), s), List.of(), CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String text) {
        var result = CompilerEngines.create().compile(input(text, 1), TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
    }
    private static CompilationOutcome.Rejected bad(String text, String cause) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(input(text, 1), TOKEN));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), result.rejection().reasons());
        assertTrue(result.analysis().diagnostics().toString().contains(cause), result.analysis().diagnostics()::toString);
        return result;
    }
    private static ClassSymbol translationClass(CompilationOutcome.Succeeded result) {
        var ref = result.analysis().queries().findQualifiedName(new QualifiedName(List.of("INTERLIS", "METAOBJECT_TRANSLATION"))).getFirst();
        return assertInstanceOf(ClassSymbol.class, result.analysis().queries().symbol(ref).known().orElseThrow());
    }
    private static void axisRelation(AnalysisSnapshot analysis, RelationDecision expected) {
        var g = analysis.semanticGraph();
        var c = analysis.queries().findQualifiedName(new QualifiedName(List.of("F", "T", "C"))).getFirst();
        var owner = (ClassSymbol)g.symbol(c.symbol()).orElseThrow();
        var derived = (AttributeSymbol)g.symbol(owner.members().getFirst()).orElseThrow();
        var base = (AttributeSymbol)g.symbol(((ClassSymbol)g.symbol(owner.baseType().orElseThrow()).orElseThrow()).members().getFirst()).orElseThrow();
        assertEquals(expected, new guru.interlis.model.query.TypeRelations(g).canExtend(new TypeRef(g.snapshotId(), derived.declaredType()),
                new TypeRef(g.snapshotId(), base.declaredType()), ExtensionContext.DOMAIN_EXTENSION,g.languageVersions().iterator().next()).known().orElseThrow().decision());
    }

    @Test void exactOriginalsArePinnedAndReachTheirNormativeOutcomes() throws Exception {
        var pins = Map.of("2.3-metaobject-translation.ili", "5cbff7ba805fd8ad2e3120bb1cf473bbbed0b85f6b47b369e095ea46aeea8009",
                "2.4-metaobject-translation.ili", "7b8366b7511186d199a240dbb8d807400a1bd328a6f2e6875519a0002c69f3bc",
                "2.3-empty-function.ili", "b07822adfb24cd65f8108d01e7575f15fb18a1937a083d946d6159802fe0c1ce",
                "2.4-empty-function.ili", "c596cc74cea5509ce04b25defb308a2315cc1bcb9d4e66edf822d893be705eb7");
        for (var pin : pins.entrySet()) {
            var bytes = Files.readAllBytes(Path.of("../testdata/conformance/g2-e1-functions-metadata", pin.getKey()));
            assertEquals(pin.getValue(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            var text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (pin.getKey().equals("2.3-empty-function.ili")) bad(text, "ILI-SYN-0006"); else ok(text);
        }
    }

    @Test void declarationProfileRetainsEmptySignatureAndFollowingDeclarationsInBothModes() {
        for (var version : List.of("2.3", "2.4")) for (var mode : ParseMode.values()) {
            var text = model(version, "!! Grüsse\r\n FUNCTION f( /* empty */ ):BOOLEAN; FUNCTION next(v:BOOLEAN):BOOLEAN; TOPIC T=CLASS C=END C; END T;");
            var s = source("memory:/profile.ili", text);
            var syntax = SyntaxServices.create().parse(s, ParseOptions.defaults(mode), TOKEN);
            var functions = syntax.root().models().getFirst().members().stream().filter(FunctionDeclarationSyntax.class::isInstance).map(FunctionDeclarationSyntax.class::cast).toList();
            assertEquals(2, functions.size()); assertTrue(functions.getFirst().parameters().isEmpty());
            assertEquals("next", functions.getLast().name()); assertEquals(1, functions.getLast().parameters().size());
            if (version.equals("2.4")) assertTrue(syntax.diagnostics().isEmpty(), syntax.diagnostics()::toString);
            else {
                assertEquals(1, syntax.diagnostics().size()); var diagnostic = syntax.diagnostics().getFirst();
                assertEquals("ILI-SYN-0006", diagnostic.code().value());
                assertEquals(text.indexOf("( /* empty */ )"), diagnostic.primaryRange().orElseThrow().startOffset());
                assertEquals(text.indexOf("( /* empty */ )") + "( /* empty */ )".length(), diagnostic.primaryRange().orElseThrow().endOffset());
            }
        }
    }

    @Test void emptyCallsUseTheSameProfileInDocumentsAndExpressionFragments() {
        for (var version : List.of("2.3", "2.4")) for (var call : List.of("f()", "F.f()", "INTERLIS.len()")) {
            var text = model(version, "FUNCTION f(v:BOOLEAN):BOOLEAN; TOPIC T=CLASS C=MANDATORY CONSTRAINT " + call + "; END C; END T;");
            var s = source("memory:/profile.ili", text);
            for (var mode : ParseMode.values()) {
                var syntax = SyntaxServices.create().parse(s, ParseOptions.defaults(mode), TOKEN);
                assertEquals(version.equals("2.3"), syntax.diagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SYN-0006")), syntax.diagnostics()::toString);
            }
            var full = source("memory:/fragment.ili", "prefix " + call + " suffix");
            var fragment = SyntaxServices.create().parseFragment(full, full.range(7, 7 + call.length()), FragmentKind.EXPRESSION,
                    version.equals("2.3") ? LanguageVersion.ILI23 : LanguageVersion.ILI24, TOKEN);
            assertTrue(fragment.consumedEntireFragment());
            assertEquals(version.equals("2.3"), fragment.diagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SYN-0006")), fragment.diagnostics()::toString);
            if (version.equals("2.3")) {
                var range = fragment.diagnostics().getFirst().primaryRange().orElseThrow();
                assertEquals(7 + call.indexOf('('), range.startOffset()); assertEquals(7 + call.length(), range.endOffset());
            }
        }
    }

    @Test void incompleteFunctionListsRemainRecoverableSourceErrors() {
        for (var version : List.of("2.3", "2.4")) for (var tail : List.of("FUNCTION f(",
                "FUNCTION f(v:BOOLEAN):BOOLEAN; TOPIC T=CLASS C=MANDATORY CONSTRAINT f(",
                "TOPIC T=CLASS C=MANDATORY CONSTRAINT INTERLIS.len(")) {
            var text = "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = " + tail;
            for (var mode : ParseMode.values()) {
                var syntax = assertDoesNotThrow(() -> SyntaxServices.create().parse(source("memory:/incomplete.ili", text), ParseOptions.defaults(mode), TOKEN));
                assertFalse(syntax.diagnostics().isEmpty()); assertEquals("F", syntax.root().models().getFirst().name());
            }
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(input(text, 1), TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejected.rejection().reasons());
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d -> d.code().value().startsWith("ILI-SYN-")));
            assertTrue(rejected.analysis().diagnostics().stream().noneMatch(d -> d.code().value().startsWith("ILI-IMPL-")));
        }
    }

    @Test void zeroArgumentCallsRetainTypedTargetsAndArityChecksIn24() {
        var text = model("2.4", "FUNCTION f():BOOLEAN; TOPIC T=CLASS C=MANDATORY CONSTRAINT F.f(); END C; END T;");
        var result = ok(text); var graph = result.schema().semanticGraph();
        var function = graph.symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).findFirst().orElseThrow();
        var call = graph.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
        assertTrue(function.parameters().isEmpty()); assertTrue(call.children().isEmpty());
        assertEquals(function.id(), call.targetFunction().orElseThrow()); assertEquals(function.returnType(), call.typeId());
        assertInstanceOf(BooleanType.class, graph.type(call.typeId()).orElseThrow());
        bad(text.replace("F.f()", "F.f(#true)"), "ILI-SEM-0005");
        bad(text.replace("FUNCTION f()", "FUNCTION f(v:BOOLEAN)"), "ILI-SEM-0005");
        for (var v : List.of("2.3", "2.4")) ok(model(v, "FUNCTION f(v:BOOLEAN):BOOLEAN; TOPIC T=CLASS C=MANDATORY CONSTRAINT F.f(#true); END C; END T;"));
    }

    @Test void predefinedClassHasTwoMandatoryNamesAndTwoIndependentBoundUniqueKeys() {
        for (var version : List.of("2.3", "2.4")) {
            var result = ok(model(version, "TOPIC T=CLASS C EXTENDS INTERLIS.METAOBJECT_TRANSLATION=END C; END T;"));
            var g = result.schema().semanticGraph(); var root = translationClass(result);
            assertTrue(root.baseType().isEmpty()); assertFalse(root.header().has(Modifier.ABSTRACT));
            assertEquals(version.equals("2.3") ? LanguageVersion.ILI23 : LanguageVersion.ILI24, root.header().languageVersion());
            assertInstanceOf(Origin.PredefinedOrigin.class, root.origin()); assertTrue(root.header().nameRange().isEmpty());
            assertEquals(List.of("Name", "NameInBaseLanguage"), root.members().stream().map(id -> g.symbol(id).orElseThrow().name().value()).toList());
            assertEquals(2, root.constraints().size());
            for (int i = 0; i < 2; i++) {
                var a = assertInstanceOf(AttributeSymbol.class, g.symbol(root.members().get(i)).orElseThrow());
                assertTrue(a.mandatory()); assertEquals(root.id(), a.owner().orElseThrow()); assertEquals(i, a.declarationOrder());
                var type = assertInstanceOf(TextType.class, result.analysis().queries().declaredType(new SymbolRef(g.snapshotId(), a.id())).known().orElseThrow());
                assertEquals(TextKind.NAME, type.textKind()); assertEquals(255, type.maxLength().orElseThrow());
                var constraint = assertInstanceOf(ConstraintSymbol.class, g.symbol(root.constraints().get(i)).orElseThrow());
                assertEquals(i + 2, constraint.declarationOrder()); assertEquals(root.id(), constraint.owner().orElseThrow());
                var unique = assertInstanceOf(UniquenessConstraint.class, constraint.definition());
                assertTrue(unique.name().isEmpty()); assertFalse(unique.basket()); assertTrue(unique.where().isEmpty()); assertTrue(unique.localKeys().isEmpty()); assertEquals(1, unique.keys().size());
                var path = assertInstanceOf(PathExpression.class, result.analysis().queries().expression(new ExpressionRef(g.snapshotId(), unique.keys().getFirst())).known().orElseThrow());
                assertEquals(a.declaredType(), path.typeId()); assertEquals(a.id(), ((PathStep.AttributeStep)path.path().steps().getFirst()).target());
                assertEquals("predefined:/INTERLIS-" + root.header().languageVersion(), path.range().source().value());
                assertEquals(0, path.range().startOffset()); assertEquals(0, path.range().length());
            }
            var child = result.analysis().queries().findQualifiedName(new QualifiedName(List.of("F", "T", "C"))).getFirst();
            var effective = result.analysis().queries().effectiveMembers(child).known().orElseThrow();
            assertEquals(root.members(), effective.stream().map(EffectiveMember::declaredSymbol).toList());
            assertTrue(effective.stream().allMatch(m -> m.origin() == EffectiveMemberOrigin.INHERITED));
            var s = result.analysis().syntaxSnapshots().values().iterator().next().source(); int pos = s.text().indexOf("METAOBJECT_TRANSLATION");
            assertEquals(root.id(), LanguageQueries.create().symbolAt(result.analysis(), s.range(pos, pos), TOKEN).known().orElseThrow().symbol());
            assertThrows(UnsupportedOperationException.class, () -> root.members().clear());
        }
    }

    @Test void inheritedPathsOverridesAndWrongTargetKindsUseCommonRules() {
        for (var version : List.of("2.3", "2.4")) {
            var body = "TOPIC T=CLASS C EXTENDS INTERLIS.METAOBJECT_TRANSLATION=MANDATORY CONSTRAINT INTERLIS.len(NameInBaseLanguage)>0; END C; "
                    + "CLASS D EXTENDS C=NameInBaseLanguage(EXTENDED):MANDATORY NAME; END D; END T;";
            var result = ok(model(version, body));
            var source = result.analysis().syntaxSnapshots().values().iterator().next().source();
            int offset = source.text().indexOf("len(NameInBaseLanguage)") + 4;
            assertEquals(translationClass(result).members().get(1), LanguageQueries.create()
                    .symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().orElseThrow().symbol());
            bad(model(version, body.replace("NameInBaseLanguage(EXTENDED):MANDATORY NAME", "NameInBaseLanguage(EXTENDED):NAME")), "ATTRIBUTE_OVERRIDE_MANDATORY");
            bad(model(version, body.replace("NameInBaseLanguage(EXTENDED):MANDATORY NAME", "NameInBaseLanguage(EXTENDED):MANDATORY 0..10")), "ATTRIBUTE_OVERRIDE_TYPE");
            bad(model(version, "STRUCTURE S EXTENDS INTERLIS.METAOBJECT_TRANSLATION=END S;"), "ILI-SEM-0001");
            bad(model(version, body.replace("len(NameInBaseLanguage)", "len(NoSuchName)")), "ILI-SEM-0001");
        }
    }

    @Test void importedMetadataAndEmptySignaturesBindIndependentlyOfSourceOrder() {
        for (var version : List.of("2.3", "2.4")) for (boolean reverse : List.of(false, true)) {
            var base = source(reverse ? "memory:/z-base.ili" : "memory:/a-base.ili", model(version,
                    "TOPIC T=CLASS C EXTENDS INTERLIS.METAOBJECT_TRANSLATION=END C; END T;" + (version.equals("2.4") ? "FUNCTION f():BOOLEAN;" : "FUNCTION f(v:BOOLEAN):BOOLEAN;"))
                    .replace("MODEL F", "MODEL Base").replace("END F.", "END Base."));
            var use = source(reverse ? "memory:/a-use.ili" : "memory:/z-use.ili", model(version,
                    "IMPORTS Base; TOPIC U=DEPENDS ON Base.T; CLASS D EXTENDS Base.T.C=MANDATORY CONSTRAINT INTERLIS.len(NameInBaseLanguage)>0 AND Base.f(" + (version.equals("2.4") ? "" : "#true") + "); END D; END U;"));
            var request = request(List.of("F"), Map.of(base.id(), base, use.id(), use), List.of(binding(parse(use), "Base", base, parse(base))), CompilerOptions.defaults());
            var result = assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create().compile(request, TOKEN));
            var root = translationClass(result); var g = result.schema().semanticGraph();
            var d = result.analysis().queries().findQualifiedName(new QualifiedName(List.of("F", "U", "D"))).getFirst();
            assertEquals(root.members(), g.effectiveMembers(d.symbol()).stream().map(EffectiveMember::declaredSymbol).toList());
        }
    }

    @Test void predefinedCollectionOverridesUseBoundElementsAndCardinalities() {
        for (var version : List.of("2.3", "2.4")) {
            var body = "TOPIC T=CLASS C EXTENDS INTERLIS.COORDSYSTEM=Axis(EXTENDED):LIST {2} OF INTERLIS.AXIS; END C; END T;";
            axisRelation(ok(model(version, body)).analysis(), RelationDecision.ALLOWED);
            for (var invalid : List.of(body.replace("{2}", "{4}"), body.replace("LIST", "BAG"),
                    body.replace("OF INTERLIS.AXIS", "OF INTERLIS.GregorianDate")))
                axisRelation(bad(model(version, invalid), "ATTRIBUTE_OVERRIDE_TYPE").analysis(), RelationDecision.DISALLOWED);
        }
    }

    @Test void nameAndUriKeywordsKeepTheirIntrinsicBoundsInPublicTypes() {
        for (var version : List.of("2.3", "2.4")) {
            var result = ok(model(version, "TOPIC T=CLASS C=n:NAME; u:URI; text:TEXT; END C; END T;"));
            for (var attribute : result.schema().semanticGraph().symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).toList()) {
                var type = assertInstanceOf(TextType.class, result.analysis().queries().declaredType(new SymbolRef(result.analysis().snapshotId(), attribute.id())).known().orElseThrow());
                if (attribute.name().value().equals("n")) assertEquals(255, type.maxLength().orElseThrow());
                else if (attribute.name().value().equals("u")) assertEquals(1023, type.maxLength().orElseThrow());
                else assertTrue(type.maxLength().isEmpty());
            }
        }
    }

    @Test void translationsKeepPredefinedIdentityAndCompareBoundMembers() {
        for (var version : List.of("2.3", "2.4")) {
            var body = "TOPIC T=CLASS C EXTENDS INTERLIS.METAOBJECT_TRANSLATION=MANDATORY CONSTRAINT INTERLIS.len(NameInBaseLanguage)>0; END C; END T;";
            var original = model(version, body).replace("MODEL F", "MODEL Original").replace("END F.", "END Original.");
            var translated = model(version, body).replace("INTERLIS " + version + "; ", "").replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Original [\"1\"] =");
            var result = ok(original + translated); assertEquals(2, translationClass(result).constraints().size());
            bad(original + translated.replace("len(NameInBaseLanguage)", "len(Name)"), "TRANSLATION");
        }
    }

    @Test void predefinedClassTargetsBindInFunctionObjectAndClassArguments() {
        for (var version : List.of("2.3", "2.4")) {
            var body = "FUNCTION f(v:OBJECT OF INTERLIS.METAOBJECT_TRANSLATION):BOOLEAN; "
                    + "FUNCTION g(v:CLASS RESTRICTION(INTERLIS.METAOBJECT_TRANSLATION)):BOOLEAN; "
                    + "TOPIC T=CLASS C EXTENDS INTERLIS.METAOBJECT_TRANSLATION=MANDATORY CONSTRAINT f(THIS) AND g(>F.T.C); END C; "
                    + "CLASS Other=END Other; END T;";
            var result = ok(model(version, body)); var graph = result.schema().semanticGraph();
            var functions = graph.symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).toList();
            var root = translationClass(result);
            assertEquals(root.id(), ((ObjectArgumentType)graph.type(functions.getFirst().parameters().getFirst().typeId()).orElseThrow()).base().orElseThrow());
            assertEquals(List.of(root.id()), ((ClassValueType)graph.type(functions.getLast().parameters().getFirst().typeId()).orElseThrow()).restrictions());
            bad(model(version, body.replace("g(>F.T.C)", "g(>F.T.Other)")), "ILI-SEM-0005");
            bad(model(version, body.replace("OBJECT OF INTERLIS.METAOBJECT_TRANSLATION", "OBJECT OF INTERLIS.UnknownTranslation")), "ILI-SEM-0001");
        }
    }

    @Test void validInvalidValidEditsMatchFreshPublicModelsAndDiagnostics() {
        var engine = CompilerEngines.create();
        for (var version : List.of("2.3", "2.4")) for (boolean function : List.of(false, true)) {
            AnalysisSnapshot previous = null;
            for (int step = 1; step <= 3; step++) {
                var body = function ? "FUNCTION f(" + (step == 2 ? "" : "v:BOOLEAN") + "):BOOLEAN;" : "TOPIC T=CLASS C EXTENDS INTERLIS." + (step == 2 ? "UnknownTranslation" : "METAOBJECT_TRANSLATION") + "=END C; END T;";
                var request = input(model(version, body), step);
                var reused = engine.analyze(request, previous == null ? AnalysisReuse.none() : new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), TOKEN);
                var fresh = CompilerEngines.create().analyze(request, TOKEN);
                assertEquals(fresh.diagnostics(), reused.diagnostics());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(), fresh.semanticGraph()), OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(), reused.semanticGraph()));
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(), fresh.semanticGraph()), OriginalViewReuseTest.semantic(reused.semanticGraph().expressions(), reused.semanticGraph()));
                assertEquals(List.copyOf(fresh.semanticGraph().types()), List.copyOf(reused.semanticGraph().types()));
                assertEquals(step != 2 || function && version.equals("2.4"), reused.diagnostics().isEmpty()); previous = reused;
            }
        }
    }
}
