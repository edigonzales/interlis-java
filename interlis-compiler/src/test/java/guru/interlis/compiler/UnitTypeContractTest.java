package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.NumericType;
import guru.interlis.model.UnitSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class UnitTypeContractTest {
    @Test
    void unitDeclarationBindsNumericRange() {
        SourceText source = new SourceText(new SourceId("memory:/units.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL Units AT "https://example.interlis.ch" VERSION "1.0" =
                  UNIT m;
                  TOPIC T =
                    CLASS C =
                      length : 0.000 .. 10.000 [m];
                    END C;
                  END T;
                END Units.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("Units",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        UnitSymbol unit = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(UnitSymbol.class::isInstance).map(UnitSymbol.class::cast)
                .findFirst().orElseThrow();
        ClassSymbol clazz = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                .findFirst().orElseThrow();
        AttributeSymbol attribute = assertInstanceOf(AttributeSymbol.class,
                succeeded.analysis().semanticGraph().symbol(clazz.members().getFirst()).orElseThrow());
        NumericType numeric = assertInstanceOf(NumericType.class,
                succeeded.analysis().semanticGraph().type(attribute.declaredType()).orElseThrow());
        assertEquals(Optional.of(unit.id()), numeric.range().unit());
    }
}
