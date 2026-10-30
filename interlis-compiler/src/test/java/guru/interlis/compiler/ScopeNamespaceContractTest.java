package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
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
    @Test void typeAndRuntimeParameterNamesRemainDifferentNamespaces() {
        for(String version:List.of("2.3","2.4"))assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request(version,"DOMAIN Value=TEXT;PARAMETER Value:TEXT;TOPIC T=CLASS C=Value:TEXT;END C;END T;"),CancellationToken.none()));
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
                "TOPIC T=CLASS T=END T;END T;",
                "UNIT X;TOPIC T=DOMAIN X=TEXT;END T;",
                "TOPIC Base=STRUCTURE S=END S;END Base;TOPIC Child EXTENDS Base=STRUCTURE S=END S;END Child;")) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,body),CancellationToken.none()),version+body);
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0010")&&(d.arguments().toString().contains("SCOPE_OUTER_TYPE_NAME")||d.arguments().toString().contains("SCOPE_INHERITED_TYPE_NAME"))),out.analysis().diagnostics().toString());
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
