package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class NumericConstantBindingContractTest {
    private static CompilationOutcome compile(String version,String body) {
        var s=source("memory:/constants.ili","INTERLIS "+version+"; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = "+body+" END F.");
        return CompilerEngines.create().compile(request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults()),CancellationToken.none());
    }
    @Test void explicitUnitsHaveActualTargetsAndExactNumbersInBothVersions() {
        for(String version:List.of("2.3","2.4")) {
            var result=compile(version,"UNIT m; TOPIC T=CLASS C=n:-100000.00000..100000.00000[m]; MANDATORY CONSTRAINT n>0.00001[m]; END C; END T;");
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result.toString()).schema().semanticGraph();
            var literal=graph.expressions().stream().filter(LiteralExpression.class::isInstance).map(LiteralExpression.class::cast).filter(e->e.decimal().isPresent()).findFirst().orElseThrow();
            var type=assertInstanceOf(NumericType.class,graph.type(literal.typeId()).orElseThrow());
            assertEquals(literal.decimal(),type.range().minimum());assertEquals(type.range().minimum(),type.range().maximum());
            var unit=type.range().unit().orElseThrow();assertEquals("m",graph.symbol(unit).orElseThrow().name().value());
            assertInstanceOf(Origin.DeclaredOrigin.class,type.origin());
            assertTrue(graph.references().stream().anyMatch(r->r.resolution().equals(new BindingResolution.BoundTarget(unit))));
        }
    }
    @Test void leadingZerosAndSignedIntegersKeepTheirValueAndOriginalLexeme() {
        for(String version:List.of("2.3","2.4")) {
            var result=compile(version,"TOPIC T=CLASS C=n:-1000..1000; MANDATORY CONSTRAINT n==007 AND n==+7 AND n==-7; END C; END T;");
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result,
                    ()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString()).schema().semanticGraph();
            var literals=graph.expressions().stream().filter(LiteralExpression.class::isInstance).map(LiteralExpression.class::cast)
                    .filter(e->e.decimal().isPresent()).toList();
            assertEquals(Set.of("007","+7","-7"),literals.stream().map(LiteralExpression::lexeme).collect(java.util.stream.Collectors.toSet()));
            for(var literal:literals) {
                var expected=literal.lexeme().equals("-7")?new java.math.BigDecimal("-7"):new java.math.BigDecimal("7");
                assertEquals(0,expected.compareTo(literal.decimal().orElseThrow().exactValue()),literal.lexeme());
            }
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,
                    compile(version,"TOPIC T=CLASS C=n:-1000..1000; MANDATORY CONSTRAINT n==+; END C; END T;"));
            assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
        }
    }
    @Test void transcendentalConstantsKeepSymbolicValuesAndUnitsWithoutFakeExactDecimals() {
        for(String version:List.of("2.3","2.4"))for(String name:List.of("PI","LNBASE")) {
            var result=compile(version,"UNIT m; TOPIC T=CLASS C=n:-100000.00000..100000.00000[m]; MANDATORY CONSTRAINT n>"+name+"[m]; END C; END T;");
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result.toString()).schema().semanticGraph();
            var value=graph.expressions().stream().filter(ConstantExpression.class::isInstance).map(ConstantExpression.class::cast).filter(e->e.constantName().equals(name)).findFirst().orElseThrow();
            var type=assertInstanceOf(NumericType.class,graph.type(value.typeId()).orElseThrow());
            assertTrue(type.range().minimum().isEmpty());assertTrue(type.range().maximum().isEmpty());
            assertEquals("m",graph.symbol(type.range().unit().orElseThrow()).orElseThrow().name().value());
            assertInstanceOf(Origin.DeclaredOrigin.class,type.origin());
        }
    }
    @Test void unknownWrongKindAndAbstractConstantUnitsAreSourceErrors() {
        for(String version:List.of("2.3","2.4"))for(String unit:List.of("Missing","N","Abstract")) {
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,compile(version,"UNIT Abstract (ABSTRACT); DOMAIN N=0..10; TOPIC T=CLASS C=n:-100000..100000; MANDATORY CONSTRAINT n>1["+unit+"]; END C; END T;"));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
            assertTrue(result.analysis().diagnostics().toString().contains("CONSTANT_UNIT"));
        }
    }
}
