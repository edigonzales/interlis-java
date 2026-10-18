package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.RoleSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssociationRulesContractTest {
    @Test
    void roleWithoutCardinalityUsesUnboundedDefault() {
        SourceText source = source("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = END A;
                    CLASS B = END B;
                    ASSOCIATION a2b =
                      a -- A;
                      b -- B;
                    END a2b;
                  END T;
                END M.
                """);
        CompilationOutcome.Succeeded succeeded = compile(source);
        AssociationSymbol association = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(AssociationSymbol.class::isInstance).map(AssociationSymbol.class::cast)
                .findFirst().orElseThrow();
        for (var roleId : association.roles()) {
            RoleSymbol role = assertInstanceOf(RoleSymbol.class,
                    succeeded.analysis().semanticGraph().symbol(roleId).orElseThrow());
            assertEquals(0, role.cardinality().lower().intValueExact());
            assertInstanceOf(CardinalityUpper.Unbounded.class, role.cardinality().upper());
            assertEquals(RoleAggregation.NONE, role.aggregation());
        }
    }

    @Test
    void associationWithoutRolesIsRejected() {
        SourceText source = source("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = END A;
                    ASSOCIATION a2a =
                    END a2a;
                  END T;
                END M.
                """);
        CompilationOutcome rejected = CompilerEngines.create().compile(request(source),
                CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Rejected.class, rejected);
    }

    private static SourceText source(String text) {
        return new SourceText(new SourceId("memory:/association.ili"), new SourceRevision(1), text);
    }

    private static CompilationRequest request(SourceText source) {
        return new CompilationRequest(List.of(new ModelSelector.ByName("M",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
    }

    private static CompilationOutcome.Succeeded compile(SourceText source) {
        return assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request(source), CancellationToken.none()));
    }
}
