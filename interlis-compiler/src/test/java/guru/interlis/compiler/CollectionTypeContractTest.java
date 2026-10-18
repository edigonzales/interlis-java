package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.TextType;
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

/** INTERLIS 2.4 LIST/BAG over scalar types must not crash the syntax lowering. */
class CollectionTypeContractTest {
    @Test
    void listOfTextBuildsExactCollectionDescriptor() {
        SourceText source = new SourceText(new SourceId("memory:/collection.ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL Collections AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
                    CLASS Item =
                      values : LIST {0..*} OF TEXT*20;
                    END Item;
                  END Data;
                END Collections.
                """);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Collections", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        ClassSymbol item = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                .findFirst().orElseThrow();
        AttributeSymbol attribute = (AttributeSymbol) succeeded.analysis().semanticGraph()
                .symbol(item.members().getFirst()).orElseThrow();
        CollectionType collection = assertInstanceOf(CollectionType.class,
                succeeded.analysis().semanticGraph().type(attribute.declaredType()).orElseThrow());
        assertEquals(CollectionKind.LIST, collection.collectionKind());
        assertEquals(0, collection.cardinality().lower().intValueExact());
        assertInstanceOf(CardinalityUpper.Unbounded.class, collection.cardinality().upper());
        TextType element = assertInstanceOf(TextType.class,
                succeeded.analysis().semanticGraph().type(collection.elementType()).orElseThrow());
        assertTrue(element.maxLength().isPresent());
        assertEquals(20, element.maxLength().getAsInt());
    }
}
