package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.BindingResolution;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.UnresolvedReason;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The type namespace includes structures, functions and units, even when never referenced. */
class ScopeNamespaceContractTest {
    private static CompilationRequest request(String version,String body) {
        var source=CompilerTestSupport.source("memory:/namespaces.ili","INTERLIS "+version+";CONTRACTED MODEL F AT \"x\" VERSION \"1\"="+body+"END F.");
        return CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    @Test void duplicateTypeNamesAreRejectedInEveryDeclaredScopeEvenWithoutAnyUse() {
        for(String version:List.of("2.3","2.4"))for(String declarations:List.of(
                "STRUCTURE S=END S;STRUCTURE S=END S;",
                "STRUCTURE S=END S;DOMAIN S=TEXT;",
                "CLASS S(ABSTRACT)=END S;STRUCTURE S=END S;",
                "UNIT S;DOMAIN S=TEXT;",
                "FUNCTION S(a:TEXT):TEXT;DOMAIN S=TEXT;"))for(String body:List.of(declarations,"TOPIC T="+declarations+"END T;")) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,body),CancellationToken.none()),version+body);
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0010")&&d.arguments().toString().contains("SCOPE_TYPE_NAME_UNIQUE")),out.analysis().diagnostics().toString());
        }
    }

    @Test
    void publishedIlisMeta16KeepsBasketOidDomainAndAssociationDistinct() throws Exception {
        var model = Path.of("../testdata/conformance/metamodel/IlisMeta16.ili");
        var source = CompilerTestSupport.source(model.toAbsolutePath().normalize().toUri().toString(), Files.readString(model));
        var request = CompilerTestSupport.request(List.of("IlisMeta16"), Map.of(source.id(), source),
                List.of(), CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, CancellationToken.none());
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().diagnostics().toString() : outcome.toString()).schema();
        var queries = schema.queries();
        var domain = queries.findQualifiedName(QualifiedName.of("IlisMeta16", "BasketOID")).getFirst();
        assertInstanceOf(DomainSymbol.class, queries.symbol(domain).known().orElseThrow());
        var topicRef = queries.findQualifiedName(QualifiedName.of("IlisMeta16", "ModelData")).getFirst();
        var topic = assertInstanceOf(TopicSymbol.class, queries.symbol(topicRef).known().orElseThrow());
        var associationRef = queries.findQualifiedName(QualifiedName.of("IlisMeta16", "ModelData", "BasketOID")).getFirst();
        var association = assertInstanceOf(AssociationSymbol.class, queries.symbol(associationRef).known().orElseThrow());
        assertEquals(List.of("ForDataUnit", "Oid"), association.roles().stream()
                .map(id -> schema.semanticGraph().symbol(id).orElseThrow().name().value()).toList());
        var basketRule = topic.header().oidRules().stream().filter(rule -> rule.basket()).findFirst().orElseThrow();
        assertEquals(Optional.of(domain.symbol()), basketRule.domain());
    }

    @Test
    void basketOidCompatibilityDoesNotAllowSameScopeCollisionsOrChangeInterlis23() {
        String topic = """
                TOPIC T =
                  %s
                  CLASS A = END A;
                  CLASS B = END B;
                  ASSOCIATION Id = a -- A; b -- B; END Id;
                END T;
                """;
        var engine = CompilerEngines.create();
        var oldVersion = assertInstanceOf(CompilationOutcome.Rejected.class, engine.compile(
                request("2.3", "DOMAIN Id = OID TEXT;" + topic.formatted("")), CancellationToken.none()));
        var sameScope = assertInstanceOf(CompilationOutcome.Rejected.class, engine.compile(
                request("2.4", topic.formatted("DOMAIN Id = OID TEXT;")), CancellationToken.none()));
        assertTrue(oldVersion.analysis().requestDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.arguments().toString().contains("SCOPE_OUTER_TYPE_NAME")));
        assertTrue(sameScope.analysis().requestDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.arguments().toString().contains("SCOPE_TYPE_NAME_UNIQUE")));
    }
    @Test void typeAndRuntimeParameterNamesRemainDifferentNamespaces() {
        for(String version:List.of("2.3","2.4"))assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request(version,"DOMAIN Value=TEXT;PARAMETER Value:TEXT;TOPIC T=CLASS C=Value:TEXT;END C;END T;"),CancellationToken.none()));
    }
    @Test void ambiguousBaseKeepsOneReferenceAfterTheFinalReferenceWalk() {
        for (String version : List.of("2.3", "2.4")) {
            var result = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(
                    request(version, "TOPIC T=CLASS A=END A;CLASS A=END A;CLASS B EXTENDS A=END B;END T;"),
                    CancellationToken.none()));
            var graph = result.analysis().semanticGraph();
            var uses = graph.references().stream().filter(ref -> ref.usage() == ReferenceUsage.BASE_TYPE).toList();
            assertEquals(1, uses.size());
            var resolution = assertInstanceOf(BindingResolution.UnresolvedTarget.class, uses.getFirst().resolution());
            assertEquals(UnresolvedReason.AMBIGUOUS_NAME, resolution.reason());
            assertEquals(graph.symbols().stream().filter(symbol -> symbol.name().value().equals("A"))
                    .map(symbol -> symbol.id()).sorted(Comparator.comparing(SymbolId::value)).toList(),
                    resolution.candidates());
        }
    }
    @Test void anonymousAssociationsDoNotInventCollidingUserTypeNames() {
        for(String version:List.of("2.3","2.4")) {
            var out=CompilerEngines.create().compile(request(version,
                "TOPIC Base=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=END B;ASSOCIATION=a--A;b--B;END;ASSOCIATION=c--A;d--B;END;END Base;"
                +"TOPIC Child EXTENDS Base=ASSOCIATION=e--A;f--B;END;END Child;"),CancellationToken.none());
            assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected rejected?rejected.analysis().diagnostics().toString():out.toString());
        }
    }
    @Test void localTypesCannotShadowOuterTypesOrUnmarkedInheritedTypes() {
        for(String version:List.of("2.3","2.4"))for(String body:List.of(
                "DOMAIN X=TEXT;TOPIC T=CLASS X=END X;END T;",
                "TOPIC T=CLASS U=END U;END T;TOPIC U=END U;",
                "UNIT X;TOPIC T=DOMAIN X=TEXT;END T;",
                "TOPIC Base=STRUCTURE S=END S;END Base;TOPIC Child EXTENDS Base=STRUCTURE S=END S;END Child;")) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,body),CancellationToken.none()),version+body);
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0010")&&(d.arguments().toString().contains("SCOPE_OUTER_TYPE_NAME")||d.arguments().toString().contains("SCOPE_INHERITED_TYPE_NAME"))),out.analysis().diagnostics().toString());
        }
    }
    @Test
    void duplicateClassesAndTopicsStillConflictInTheirDeclaringScope() {
        for (var version : List.of("2.3", "2.4")) {
            for (var body : List.of(
                    "TOPIC T = CLASS T = END T; CLASS T = END T; END T;",
                    "TOPIC T = END T; TOPIC T = END T;")) {
                var result = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create()
                        .compile(request(version, body), CancellationToken.none()));
                assertTrue(result.analysis().requestDiagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code().value().equals("ILI-SEM-0010")
                                && diagnostic.arguments().toString().contains("SCOPE_TYPE_NAME_UNIQUE")));
            }
        }
    }

    @Test
    void classMayShareItsOwnTopicName() {
        for (var version : List.of("2.3", "2.4")) {
            var result = CompilerEngines.create().compile(request(version, """
                    TOPIC Building =
                      CLASS Building = END Building;
                      CLASS Special EXTENDS Building = END Special;
                    END Building;
                    TOPIC Other =
                      DEPENDS ON Building;
                    END Other;
                    """), CancellationToken.none());
            var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                    () -> result instanceof CompilationOutcome.Rejected rejected
                            ? rejected.analysis().diagnostics().toString() : result.toString()).schema();
            var queries = schema.queries();
            var topic = queries.findQualifiedName(guru.interlis.model.QualifiedName.of("F", "Building")).getFirst();
            var building = queries.findQualifiedName(guru.interlis.model.QualifiedName.of("F", "Building", "Building")).getFirst();
            var special = queries.findQualifiedName(guru.interlis.model.QualifiedName.of("F", "Building", "Special")).getFirst();
            var other = queries.findQualifiedName(guru.interlis.model.QualifiedName.of("F", "Other")).getFirst();
            assertEquals(Optional.of(building.symbol()),
                    assertInstanceOf(guru.interlis.model.ClassSymbol.class,
                            queries.symbol(special).known().orElseThrow()).baseType());
            assertEquals(List.of(topic.symbol()),
                    assertInstanceOf(guru.interlis.model.TopicSymbol.class,
                            queries.symbol(other).known().orElseThrow()).dependencies());
        }
    }

    @Test void qualifiedOuterReferencesAndExplicitInheritedExtensionsKeepTheirActualTargets() {
        for(String version:List.of("2.3","2.4")) {
            var out=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request(version,"DOMAIN X=TEXT;TOPIC Base=STRUCTURE S=value:F.X;END S;END Base;TOPIC Child EXTENDS Base=STRUCTURE S(EXTENDED)=other:BOOLEAN;END S;END Child;"),CancellationToken.none()));
            var graph=out.schema().semanticGraph();var domain=out.schema().queries().findQualifiedName(guru.interlis.model.QualifiedName.of("F","X")).getFirst();
            var attribute=(guru.interlis.model.AttributeSymbol)out.schema().queries().symbol(out.schema().queries().findQualifiedName(guru.interlis.model.QualifiedName.of("F","Base","S","value")).getFirst()).known().orElseThrow();
            assertEquals(domain.symbol(),((guru.interlis.model.DomainReferenceType)graph.type(attribute.declaredType()).orElseThrow()).targetDomain());
            var base=out.schema().queries().findQualifiedName(guru.interlis.model.QualifiedName.of("F","Base","S")).getFirst();
            var child=(guru.interlis.model.StructureSymbol)out.schema().queries().symbol(out.schema().queries().findQualifiedName(guru.interlis.model.QualifiedName.of("F","Child","S")).getFirst()).known().orElseThrow();
            assertEquals(Optional.of(base.symbol()),child.baseType());
            assertEquals(List.of("value","other"),graph.effectiveMembers(child.id()).stream().map(m->graph.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
        }
    }
}
