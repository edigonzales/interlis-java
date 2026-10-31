package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** E1: bound predefined domains and exact qualified name resolution, without fallback types. */
class PredefinedBindingContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.";
    }
    private static CompilationRequest input(String text, int revision) {
        var source = new SourceText(new SourceId("memory:/predefined.ili"), new SourceRevision(revision), text);
        return request(List.of("F"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String text) {
        var result = CompilerEngines.create().compile(input(text, 1), TOKEN);
        assertInstanceOf(CompilationOutcome.Succeeded.class, result, result::toString);
        return (CompilationOutcome.Succeeded) result;
    }
    private static CompilationOutcome.Rejected bad(String text, String cause) {
        var result = CompilerEngines.create().compile(input(text, 1), TOKEN);
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, result, result::toString);
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejected.rejection().reasons());
        assertTrue(rejected.analysis().diagnostics().toString().contains(cause), rejected.analysis().diagnostics()::toString);
        assertTrue(rejected.analysis().diagnostics().stream().allMatch(d -> d.code().value().startsWith("ILI-SEM-")));
        return rejected;
    }

    @Test void qualifiedTextAndBooleanDomainsUseCanonicalPredefinedBindingsInBothLanguages() {
        for(var version:List.of("2.3","2.4")) {
            var text=model(version,"TOPIC T=CLASS C=url:INTERLIS.URI;name:INTERLIS.NAME;flag:INTERLIS.BOOLEAN;END C;END T;");
            var compiled=ok(text);var graph=compiled.schema().semanticGraph();
            for(String name:List.of("URI","NAME","BOOLEAN")) {
                var refs=compiled.analysis().queries().findQualifiedName(new QualifiedName(List.of("INTERLIS",name)));
                assertEquals(1,refs.size());
                var domain=assertInstanceOf(DomainSymbol.class,compiled.analysis().queries().symbol(refs.getFirst()).known().orElseThrow());
                assertTrue(domain.header().has(Modifier.FINAL));
                if(name.equals("BOOLEAN"))assertInstanceOf(BooleanType.class,graph.type(domain.typeId()).orElseThrow());
                else {var type=assertInstanceOf(TextType.class,graph.type(domain.typeId()).orElseThrow());assertEquals(name.equals("URI")?TextKind.URI:TextKind.NAME,type.textKind());assertEquals(name.equals("URI")?1023:255,type.maxLength().orElseThrow());}
                var source=compiled.analysis().syntaxSnapshots().values().iterator().next().source();
                int offset=text.indexOf("INTERLIS."+name)+10;
                assertEquals(domain.id(),LanguageQueries.create().symbolAt(compiled.analysis(),source.range(offset,offset),TOKEN).known().orElseThrow().symbol());
                var attribute=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                        .filter(a->a.name().value().equals(name.equals("URI")?"url":name.equals("NAME")?"name":"flag")).findFirst().orElseThrow();
                assertEquals(domain.id(),assertInstanceOf(DomainReferenceType.class,graph.type(attribute.declaredType()).orElseThrow()).targetDomain());
            }
        }
    }

    @Test void predefinedDomainsRetainTypesFinalityOriginAndNavigationTargets() {
        for (var version : List.of("2.3", "2.4")) {
            var text = model(version, "TOPIC T=CLASS C= "
                    + "date:INTERLIS.INTERLIS_1_DATE; h:INTERLIS.HALIGNMENT; v:INTERLIS.VALIGNMENT; "
                    + "again:INTERLIS.INTERLIS_1_DATE; END C; END T;");
            var result = ok(text); var graph = result.schema().semanticGraph();
            for (String name : List.of("INTERLIS_1_DATE", "HALIGNMENT", "VALIGNMENT")) {
                var refs = result.analysis().queries().findQualifiedName(new QualifiedName(List.of("INTERLIS", name)));
                assertEquals(1, refs.size());
                var domain = assertInstanceOf(DomainSymbol.class, result.analysis().queries().symbol(refs.getFirst()).known().orElseThrow());
                assertEquals(new Origin.PredefinedOrigin(version.equals("2.3") ? LanguageVersion.ILI23 : LanguageVersion.ILI24, "INTERLIS." + name), domain.origin());
                assertTrue(domain.header().has(Modifier.FINAL)); assertTrue(domain.header().nameRange().isEmpty());
                var type = graph.type(domain.typeId()).orElseThrow();
                if (name.equals("INTERLIS_1_DATE")) assertEquals(8, assertInstanceOf(TextType.class, type).maxLength().orElseThrow());
                else assertEquals(name.equals("HALIGNMENT") ? AlignmentKind.HORIZONTAL : AlignmentKind.VERTICAL,
                        assertInstanceOf(AlignmentType.class, type).alignment());
                var source = result.analysis().syntaxSnapshots().values().iterator().next().source();
                int offset = text.indexOf("INTERLIS." + name) + 10;
                assertEquals(domain.id(), LanguageQueries.create().symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().orElseThrow().symbol());
            }
            var attributes = graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).toList();
            var dates = attributes.stream().filter(a -> a.name().value().equals("date") || a.name().value().equals("again")).toList();
            assertEquals(((DomainReferenceType)graph.type(dates.getFirst().declaredType()).orElseThrow()).targetDomain(),
                    ((DomainReferenceType)graph.type(dates.getLast().declaredType()).orElseThrow()).targetDomain());
            for (var attribute : attributes) {
                var effective = new TypeRelations(graph).resolveAliases(new TypeRef(graph.snapshotId(), attribute.declaredType())).known().orElseThrow();
                assertEquals(1, effective.aliasPath().size());
                assertEquals(effective.effectiveType(), graph.effectiveType(attribute.id()).orElseThrow().effectiveType());
            }
        }
    }

    @Test void unknownPredefinedNamesAreSourceErrorsAtTheOriginalReference() {
        for (var version : List.of("2.3", "2.4")) for (String name : List.of("INTERLIS.TypoDomain", "INTERLIS.interlis_1_date", "INTERLIS.NoSuchStructure")) {
            var text = model(version, "TOPIC T=CLASS C=value:" + name + "; END C; END T;");
            var rejected = bad(text, name);
            var diagnostic = rejected.analysis().diagnostics().stream().filter(d -> d.code().value().equals("ILI-SEM-0001")).findFirst().orElseThrow();
            assertEquals(text.indexOf(name), diagnostic.primaryRange().orElseThrow().startOffset());
            assertEquals(text.indexOf(name) + name.length(), diagnostic.primaryRange().orElseThrow().endOffset());
            assertTrue(rejected.analysis().semanticGraph().types().stream().anyMatch(ErrorType.class::isInstance));
            assertTrue(rejected.analysis().semanticGraph().symbols().stream().noneMatch(s -> s.name().value().contains("unresolved-domain")));
        }
    }

    @Test void finalDomainsCanBeUsedButCannotBeExtended() {
        for (var version : List.of("2.3", "2.4")) {
            ok(model(version, "DOMAIN D(FINAL)=TEXT*8; TOPIC T=CLASS C=a:D; END C; END T;"));
            bad(model(version, "DOMAIN D(FINAL)=TEXT*8; E EXTENDS D=TEXT*8;"), "DOMAIN_FINAL");
            for (var declaration : List.of("D EXTENDS INTERLIS.INTERLIS_1_DATE=TEXT*8;",
                    "D EXTENDS INTERLIS.HALIGNMENT=HALIGNMENT;", "D EXTENDS INTERLIS.VALIGNMENT=VALIGNMENT;"))
                bad(model(version, "DOMAIN " + declaration), "DOMAIN_FINAL");
            ok(model(version, "DOMAIN D=TEXT*8; E EXTENDS D=TEXT*4;"));
        }
    }

    @Test void falseFunctionAndEnumerationQualifiersDoNotBindLocalSuffixes() {
        for (var version : List.of("2.3", "2.4")) {
            var function = "FUNCTION f(v:NUMERIC):BOOLEAN; TOPIC T=CLASS C=MANDATORY CONSTRAINT %s(1); END C; END T;";
            for (var name : List.of("f", "F.f")) ok(model(version, function.formatted(name)));
            for (var name : List.of("Missing.f", "Missing.F.f")) bad(model(version, function.formatted(name)), name);
            var enumeration = "DOMAIN D=(a,b); TOPIC T=CLASS C=value:ALL OF %s; END C; END T;";
            for (var name : List.of("D", "F.D")) ok(model(version, enumeration.formatted(name)));
            for (var name : List.of("Missing.D", "Missing.F.D", "INTERLIS.NoSuchEnum")) bad(model(version, enumeration.formatted(name)), name);
        }
    }

    @Test void importedTargetsAndTheirReferencesAreIndependentOfSourceOrder() {
        for (var version : List.of("2.3", "2.4")) for (boolean reverse : List.of(false, true)) {
            var base = source(reverse ? "memory:/z-base.ili" : "memory:/a-base.ili", "INTERLIS " + version
                    + "; CONTRACTED MODEL Base AT \"https://example.org\" VERSION \"1\" = DOMAIN D=(a,b); FUNCTION f(v:NUMERIC):BOOLEAN; END Base.");
            var use = source(reverse ? "memory:/a-use.ili" : "memory:/z-use.ili", model(version,
                    "IMPORTS UNQUALIFIED Base; TOPIC T=CLASS C=value:ALL OF Base.D; date:INTERLIS.INTERLIS_1_DATE; MANDATORY CONSTRAINT Base.f(1) AND f(1); END C; END T;"));
            var request = request(List.of("F"), Map.of(base.id(), base, use.id(), use),
                    List.of(binding(parse(use), "Base", base, parse(base))), CompilerOptions.defaults());
            var result = assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create().compile(request, TOKEN));
            var graph = result.schema().semanticGraph();
            var target = result.analysis().queries().findQualifiedName(new QualifiedName(List.of("Base", "D"))).getFirst();
            var reference = graph.references().stream().filter(r -> r.range().source().equals(use.id())
                    && r.range().startOffset() == use.text().indexOf("Base.D")).findFirst().orElseThrow();
            assertEquals(target.symbol(), reference.segments().getLast().target().orElseThrow());
            assertEquals(target.symbol(), LanguageQueries.create().symbolAt(result.analysis(), use.range(use.text().indexOf("Base.D") + 5, use.text().indexOf("Base.D") + 5), TOKEN).known().orElseThrow().symbol());
        }
    }

    @Test void ambiguousImportedFunctionsKeepTheirBindingCauseAndQualifiedControls() {
        for (var version : List.of("2.3", "2.4")) for (boolean qualified : List.of(false, true)) {
            var base = source("memory:/base.ili", model(version, "FUNCTION f(v:NUMERIC):BOOLEAN;")
                    .replace("MODEL F", "MODEL Base").replace("END F.", "END Base."));
            var other = source("memory:/other.ili", base.text().replace("MODEL Base", "MODEL Other").replace("END Base.", "END Other."));
            var use = source("memory:/use.ili", model(version, "IMPORTS UNQUALIFIED Base, UNQUALIFIED Other; "
                    + "TOPIC T=CLASS C=MANDATORY CONSTRAINT " + (qualified ? "Base.f" : "f") + "(1); END C; END T;"));
            var request = request(List.of("F"), Map.of(base.id(), base, other.id(), other, use.id(), use),
                    List.of(binding(parse(use), "Base", base, parse(base)), binding(parse(use), "Other", other, parse(other))), CompilerOptions.defaults());
            var result = CompilerEngines.create().compile(request, TOKEN);
            if (qualified) assertInstanceOf(CompilationOutcome.Succeeded.class, result);
            else {
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, result);
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejected.rejection().reasons());
                assertEquals(List.of("ILI-SEM-0002"), rejected.analysis().diagnostics().stream().map(d -> d.code().value()).toList());
                var cause = rejected.analysis().diagnostics().getFirst().id();
                assertTrue(rejected.analysis().semanticGraph().types().stream().filter(ErrorType.class::isInstance)
                        .map(ErrorType.class::cast).anyMatch(type -> type.diagnosticIds().contains(cause)));
            }
        }
    }

    @Test void validInvalidValidEditsKeepFreshAndReusedModelsEquivalent() {
        var engine = CompilerEngines.create();
        for (var version : List.of("2.3", "2.4")) {
            AnalysisSnapshot previous = null;
            for (int step = 1; step <= 3; step++) {
                var text = model(version, "TOPIC T=CLASS C=value:INTERLIS." + (step == 2 ? "TypoDomain" : "INTERLIS_1_DATE") + "; END C; END T;");
                var request = input(text, step);
                var reused = engine.analyze(request, previous == null ? AnalysisReuse.none() : new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), TOKEN);
                var fresh = CompilerEngines.create().analyze(request, TOKEN);
                assertEquals(fresh.diagnostics(), reused.diagnostics());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(), fresh.semanticGraph()), OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(), reused.semanticGraph()));
                assertEquals(List.copyOf(fresh.semanticGraph().types()), List.copyOf(reused.semanticGraph().types()));
                assertEquals(step != 2, reused.diagnostics().isEmpty());
                previous = reused;
            }
        }
    }
}
