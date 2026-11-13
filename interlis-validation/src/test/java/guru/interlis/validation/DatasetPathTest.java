package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetPathTest {
    @Test
    void checksDeferredConstraintsOnEachStructureOccurrence() {
        var plan = plan("""
                CLASS Target = amount: 0..100; END Target;
                STRUCTURE Pointer =
                  link: REFERENCE TO Target;
                  MANDATORY CONSTRAINT link->amount == 5;
                END Pointer;
                CLASS Owner = pointers: LIST {0..*} OF Pointer; END Owner;
                """);
        var owner = """
                <Owner i:tid="owner">
                  <pointers><Pointer><link i:ref="good"/></Pointer></pointers>
                  <pointers><Pointer><link i:ref="bad"/></Pointer></pointers>
                </Owner>
                """;
        var targets = """
                <Target i:tid="good"><amount>5</amount></Target>
                <Target i:tid="bad"><amount>7</amount></Target>
                """;
        for (var objects : List.of(owner + targets, targets + owner)) {
            var report = validate(plan, List.of("Owner", "Target"),
                    primary("p", basket("basket", objects)));
            assertEquals(1, report.examples().size(), report.examples().toString());
            var diagnostic = report.examples().getFirst();
            assertEquals(ValidationCode.MANDATORY_CONSTRAINT_FALSE, diagnostic.code());
            assertEquals(new DataLocation.Index(2), diagnostic.data().path().getLast());
            assertEquals(2, report.coverage().constraints().getFirst().occurrences());
        }
    }

    @Test
    void checksEmbeddedPayloadWithBothAssociationEnds() {
        var plan = plan("""
                CLASS Parent = amount: 0..100; END Parent;
                CLASS Item = amount: 0..100; END Item;
                ASSOCIATION Note =
                  parent -- {0..1} Parent;
                  item -- {0..*} Item;
                  MANDATORY CONSTRAINT parent->amount == item->amount;
                END Note;
                """);
        var report = validate(plan, List.of("Parent", "Item"), primary("p", basket("basket", """
                <Item i:tid="item"><amount>7</amount><parent i:ref="target"><Note/></parent></Item>
                <Parent i:tid="target"><amount>5</amount></Parent>
                """)));
        assertEquals(1, report.examples().size(), report.examples().toString());
        var diagnostic = report.examples().getFirst();
        assertEquals(ValidationCode.MANDATORY_CONSTRAINT_FALSE, diagnostic.code());
        assertEquals("item", diagnostic.data().tid().orElseThrow().value());
        assertEquals(1, diagnostic.data().path().size());
        assertInstanceOf(DataLocation.Slot.class, diagnostic.data().path().getFirst());
        assertEquals(1, report.coverage().constraints().getFirst().occurrences());
    }

    @Test
    void followsTwoCrossBasketReferenceHopsIncludingACycle() {
        var plan = plan("""
                CLASS Node =
                  amount: 0..100;
                  pointer: Pointer;
                  MANDATORY CONSTRAINT pointer->link->pointer->link->amount == amount;
                END Node;
                STRUCTURE Pointer =
                  link: REFERENCE TO (EXTERNAL) Node;
                END Pointer;
                """);
        var first = basket("first", """
                <Node i:tid="a"><amount>5</amount><pointer><Pointer><link i:ref="b"/></Pointer></pointer></Node>
                """);
        var second = basket("second", """
                <Node i:tid="b"><amount>7</amount><pointer><Pointer><link i:ref="a"/></Pointer></pointer></Node>
                """);
        var report = validate(plan, List.of("Node"), primary("p", first + second));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(1, report.coverage().constraints().size());
        var checked = report.coverage().constraints().getFirst();
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, checked.status());
        assertEquals(2, checked.occurrences());
    }
    @Test
    void followsForwardAndInverseAssociationEndsWithoutSelectingAnArbitraryTarget() {
        var plan = plan("""
                CLASS Parent = END Parent;
                CLASS Item = END Item;
                ASSOCIATION Link =
                  parent -- {1} Parent;
                  items -- {0..*} Item;
                END Link;
                CONSTRAINTS OF Parent =
                  MANDATORY CONSTRAINT INTERLIS.objectCount(items) == 2;
                END;
                CONSTRAINTS OF Item =
                  MANDATORY CONSTRAINT DEFINED(parent);
                END;
                """);
        var report = validate(plan, List.of("Parent", "Item"), primary("p", basket("basket", """
                <Parent i:tid="p1"/>
                <Item i:tid="i1"><parent i:ref="p1"/></Item>
                <Item i:tid="i2"><parent i:ref="p1"/></Item>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(2, report.coverage().constraints().size());
        assertTrue(report.coverage().constraints().stream()
                .allMatch(rule -> rule.status() == ValidationCoverage.ConstraintStatus.CHECKED));
    }
    @Test
    void evaluatesBoundParentAgainstTheSuppliedStructureStack() {
        var schema = ValidationPlannerTest.compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    STRUCTURE Child = value: TEXT; END Child;
                    CLASS Parent = label: TEXT; children: BAG OF Child; END Parent;
                    VIEW Expanded INSPECTION OF child~Parent->children; =
                      parentLabel := PARENT->label;
                    END Expanded;
                  END Data;
                END Checks.
                """);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class, ValidationPlanner.plan(schema,
                ValidationPlannerTest.transfer(schema), ValidationProfile.DATASET)).plan();
        var declaration = schema.semanticGraph().symbols().stream()
                .filter(guru.interlis.model.AttributeSymbol.class::isInstance)
                .map(guru.interlis.model.AttributeSymbol.class::cast)
                .filter(attribute -> attribute.name().value().equals("parentLabel")).findFirst().orElseThrow();
        var parentType = type(plan, "Parent");
        var label = plan.object(parentType).fields().getFirst().slot().symbol();
        var parent = new guru.interlis.transfer.TransferValue.Structured(parentType,
                java.util.Map.of(label, new guru.interlis.transfer.TransferValue.Text("parent")));
        var child = new guru.interlis.transfer.TransferValue.Structured(type(plan, "Child"), java.util.Map.of());
        var frame = new EvaluationFrame(plan, parent, false, () -> { }).descend(parent, child);
        var graph = schema.semanticGraph();
        var program = ExpressionCompiler.compile(graph, new guru.interlis.model.query.TypeRelations(graph),
                declaration.derivations().getFirst()).orElseThrow();
        assertEquals(new EvaluationResult.Known(new guru.interlis.transfer.TransferValue.Text("parent")),
                program.evaluate(frame));
    }
    @Test
    void readsExactPositionsForTheOrderedRelationshipIndex() {
        try (var storage = new InMemoryValidationStorage(100, 100_000)) {
            var index = new RelationPaths(storage.table(StorageArea.TEMPORARY));
            var from = new ObjectHandle(1);
            var source = new guru.interlis.model.SymbolId("source");
            var target = new guru.interlis.model.SymbolId("target");
            index.observe(from, source, target, new ObjectHandle(2), new ObjectHandle(1),
                    java.util.Optional.of(new java.math.BigInteger("18446744073709551619")));
            index.observe(from, source, target, new ObjectHandle(3), new ObjectHandle(2),
                    java.util.Optional.of(java.math.BigInteger.ONE));
            assertEquals(List.of(new ObjectHandle(3), new ObjectHandle(2)),
                    index.targets(from, source, target, () -> { }));
        }
    }
    @Test
    void switchesGenericBindingsWithTheTargetOccurrence() {
        var schema = ValidationPlannerTest.compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                    A = COORD 0.0..100.0, 0.0..100.0;
                    B = COORD 0.00..100.00, 0.00..100.00;
                  CONTEXT Choice = G = A OR B;
                  TOPIC Data =
                    DEFERRED GENERICS G;
                    CLASS Node = point: G; END Node;
                  END Data;
                END Checks.
                """);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class, ValidationPlanner.plan(schema,
                ValidationPlannerTest.transfer(schema), ValidationProfile.DATASET)).plan();
        var root = type(plan, "Node");
        var topic = plan.transferPlan().object(root).orElseThrow().topic().orElseThrow();
        var generic = schema.queries().findQualifiedName(guru.interlis.model.QualifiedName.of("Checks", "G")).getFirst();
        var first = schema.queries().findQualifiedName(guru.interlis.model.QualifiedName.of("Checks", "A")).getFirst();
        var second = schema.queries().findQualifiedName(guru.interlis.model.QualifiedName.of("Checks", "B")).getFirst();
        var basketA = new guru.interlis.transfer.TransferEvent.StartBasket(
                new guru.interlis.transfer.TransferIdentity.BasketId("first", guru.interlis.transfer.TransferIdentity.IdOrigin.SOURCE),
                topic, List.of(), guru.interlis.transfer.TransferEvent.BasketKind.FULL,
                guru.interlis.transfer.TransferEvent.Consistency.COMPLETE, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(), List.of(new guru.interlis.transfer.TransferEvent.DomainBinding(generic, first)));
        var basketB = new guru.interlis.transfer.TransferEvent.StartBasket(
                new guru.interlis.transfer.TransferIdentity.BasketId("second", guru.interlis.transfer.TransferIdentity.IdOrigin.SOURCE),
                topic, List.of(), guru.interlis.transfer.TransferEvent.BasketKind.FULL,
                guru.interlis.transfer.TransferEvent.Consistency.COMPLETE, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(), List.of(new guru.interlis.transfer.TransferEvent.DomainBinding(generic, second)));
        assertEquals(first.symbol(), ValidationSession.BasketContext.of(basketA, plan).domains().get(generic.symbol()));
        assertEquals(second.symbol(), ValidationSession.BasketContext.of(basketB, plan).domains().get(generic.symbol()));
    }
}
