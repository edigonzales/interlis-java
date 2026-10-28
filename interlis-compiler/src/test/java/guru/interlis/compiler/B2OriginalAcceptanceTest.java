package guru.interlis.compiler;

import guru.interlis.model.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static guru.interlis.compiler.OriginalViewAcceptanceSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** B2 acceptance of unchanged, pinned upstream inputs: META-008/014, SEM-008/010/014. */
class B2OriginalAcceptanceTest {
    @Test void abstractView_AcceptAbstractBaseInAbstractJoinDef() throws Exception {
        var result = original("ili23.View23Test.abstractView_AcceptAbstractBaseInAbstractJoinDef");
        var view = view(result, "Test.Join.BC");
        assertTrue(view.header().has(Modifier.ABSTRACT));
        assertJoin(result, view, List.of("B", "C"), List.of("Test.Base.B", "Test.Base.C"), false);
        assertTrue(named(result, "Test.Base.B").header().has(Modifier.ABSTRACT));
        assertAllOf(result, view, List.of("Attr1", "Attr2", "Attr3"),
                List.of("Test.Base.B.Attr1", "Test.Base.B.Attr2", "Test.Base.C.Attr3"), List.of(20, 10, 30));
        assertTrue(attribute(result, "Test.Join.BC.Attr1").header().has(Modifier.ABSTRACT));
        assertFalse(attribute(result, "Test.Join.BC.Attr2").header().has(Modifier.ABSTRACT));
        assertTrue(view.selections().isEmpty());
        changedOriginalRejected("ili23.View23Test.abstractView_AcceptAbstractBaseInAbstractJoinDef",
                "VIEW BC (ABSTRACT)", "VIEW BC", "VIEW_ABSTRACT_ATTRIBUTE");
    }

    @Test void view_AcceptBasicJoinDef() throws Exception {
        var result = original("ili23.View23Test.view_AcceptBasicJoinDef");
        var view = view(result, "Test.Join.BC");
        assertJoin(result, view, List.of("B", "C"), List.of("Test.Base.B", "Test.Base.C"), true);
        assertAllOf(result, view, List.of("Attr1", "Attr2", "Attr3"),
                List.of("Test.Base.B.Attr1", "Test.Base.B.Attr2", "Test.Base.C.Attr3"), List.of(20, 10, 30));
        assertEquals(1, view.selections().size());
        var condition = assertInstanceOf(BinaryExpression.class,
                result.schema().semanticGraph().expression(view.selections().getFirst()).orElseThrow());
        assertEquals(BinaryOperator.EQ, condition.operator());
        assertInstanceOf(BooleanType.class, effective(result, condition.typeId()));
        assertSourcePath(result, condition.left(), view.bases().getFirst(), "Test.Base.B.Attr1", true);
        assertSourcePath(result, condition.right(), view.bases().getLast(), "Test.Base.C.Attr3", true);
    }

    @Test void view_AcceptBasicUnionDef() throws Exception {
        var result = original("ili23.View23Test.view_AcceptBasicUnionDef");
        var view = view(result, "Test.Union.CC");
        assertUnion(result, view, List.of("C1", "C2"), List.of("Test.Base.C1", "Test.Base.C2"));
        assertEquals(List.of("Attr1"), names(result, view.members()));
        var output = attribute(result, "Test.Union.CC.Attr1");
        assertEquals(30, assertInstanceOf(TextType.class, effective(result, output.declaredType())).maxLength().orElseThrow());
        assertInstanceOf(Origin.DeclaredOrigin.class, output.origin());
        assertFalse(output.mandatory());
        assertEquals(2, output.derivations().size());
        assertSourcePath(result, output.derivations().getFirst(), view.bases().getFirst(), "Test.Base.C1.Attr1", true);
        assertSourcePath(result, output.derivations().getLast(), view.bases().getLast(), "Test.Base.C2.Attr2", true);
        assertEquals(10, assertInstanceOf(TextType.class,
                effective(result, path(result, output.derivations().getFirst()).typeId())).maxLength().orElseThrow());
        assertEquals(30, assertInstanceOf(TextType.class,
                effective(result, path(result, output.derivations().getLast()).typeId())).maxLength().orElseThrow());
    }

    @Test void view_AcceptJoinDefWithoutBasenames() throws Exception {
        var result = original("ili23.View23Test.view_AcceptJoinDefWithoutBasenames");
        var view = view(result, "Test.Join.BC");
        assertJoin(result, view, List.of("B", "C"), List.of("Test.Base.B", "Test.Base.C"), true);
        assertAllOf(result, view, List.of("Attr1", "Attr2", "Attr3"),
                List.of("Test.Base.B.Attr1", "Test.Base.B.Attr2", "Test.Base.C.Attr3"), List.of(20, 10, 30));
        assertTrue(view.selections().isEmpty());
        // Implicit bases retain local names but the qualified source names navigate to classes.
        var source = source("ili23.View23Test.view_AcceptJoinDefWithoutBasenames");
        int first = source.text().indexOf("JOIN OF Test.Base.B") + "JOIN OF Test.Base.".length();
        int second = source.text().indexOf("Test.Base.C (OR NULL)") + "Test.Base.".length();
        navigation(result, source.range(first, first + 1), named(result, "Test.Base.B").id());
        navigation(result, source.range(second, second + 1), named(result, "Test.Base.C").id());
    }

    @Test void viewAggregationComplete() throws Exception {
        var result = original("ili23.ViewDef23Test.viewAggregationComplete");
        assertTranslatedModel(result);
        var original = view(result, "ModelA.TopicA1.ViewA0");
        var translated = view(result, "ModelB.TopicB1.ViewB0");
        assertNotEquals(original.id(), translated.id());
        assertAggregation(result, original, "A0", "ModelA.TopicA1.ClassA0", "ModelA.TopicA1.ClassA0.a0");
        assertAggregation(result, translated, "B0", "ModelB.TopicB1.ClassB0", "ModelB.TopicB1.ClassB0.b0");
        assertNotEquals(original.bases().getFirst(), translated.bases().getFirst());
        changedOriginalRejected("ili23.ViewDef23Test.viewAggregationComplete", "EQUAL (B0->b0)",
                "EQUAL (B0->b1)", "TRANSLATION_REFERENCE");
    }

    @Test void viewJoinComplete() throws Exception {
        var result = original("ili23.ViewDef23Test.viewJoinComplete");
        assertTranslatedModel(result);
        var original = view(result, "ModelA.TopicA1.ViewA0");
        var translated = view(result, "ModelB.TopicB1.ViewB0");
        assertJoin(result, original, List.of("A0", "A1"),
                List.of("ModelA.TopicA1.ClassA0", "ModelA.TopicA1.ClassA1"), true);
        assertJoin(result, translated, List.of("B0", "B1"),
                List.of("ModelB.TopicB1.ClassB0", "ModelB.TopicB1.ClassB1"), true);
        assertNotEquals(original.id(), translated.id());
        // These originals define no ATTRIBUTE/ALL OF block: the result is intentionally empty.
        assertTrue(original.members().isEmpty());
        assertTrue(translated.members().isEmpty());
        assertTrue(original.selections().isEmpty());
        assertTrue(translated.selections().isEmpty());
        for (int i = 0; i < 2; i++) {
            assertNotEquals(original.bases().get(i), translated.bases().get(i));
            assertNotEquals(basis(result, original.bases().get(i)).target(), basis(result, translated.bases().get(i)).target());
        }
        changedOriginalRejected("ili23.ViewDef23Test.viewJoinComplete", "B1~ClassB1 (OR NULL)",
                "B1~ClassB1", "TRANSLATION_REFERENCE");
    }

    @Test void viewUnionComplete() throws Exception {
        var result = original("ili23.ViewDef23Test.viewUnionComplete");
        assertTranslatedModel(result);
        var original = view(result, "ModelA.TopicA1.ViewA0");
        var translated = view(result, "ModelB.TopicB1.ViewB0");
        assertUnion(result, original, List.of("A0", "A1"),
                List.of("ModelA.TopicA1.ClassA0", "ModelA.TopicA1.ClassA1"));
        assertUnion(result, translated, List.of("B0", "B1"),
                List.of("ModelB.TopicB1.ClassB0", "ModelB.TopicB1.ClassB1"));
        assertEquals(List.of("va1", "va2"), names(result, original.members()));
        assertEquals(List.of("vb1", "vb2"), names(result, translated.members()));
        for (int i = 0; i < 2; i++) {
            assertUnionAttribute(result, original, "ModelA.TopicA1.ViewA0.va" + (i + 1),
                    "ModelA.TopicA1.ClassA0.a" + i, "ModelA.TopicA1.ClassA1.a" + (i + 2));
            assertUnionAttribute(result, translated, "ModelB.TopicB1.ViewB0.vb" + (i + 1),
                    "ModelB.TopicB1.ClassB0.b" + i, "ModelB.TopicB1.ClassB1.b" + (i + 2));
            assertNotEquals(original.members().get(i), translated.members().get(i));
        }
        changedOriginalRejected("ili23.ViewDef23Test.viewUnionComplete", "B0->b0, B1->b2",
                "B0->b0, B1->b3", "TRANSLATION_REFERENCE");
    }

    private static void assertJoin(CompilationOutcome.Succeeded result, ViewSymbol view,
                                   List<String> aliases, List<String> targets, boolean lastOptional) {
        assertEquals(view.bases(), assertInstanceOf(ViewSymbol.Join.class, view.formation().orElseThrow()).bases());
        assertBases(result, view, aliases, targets, lastOptional);
    }

    private static void assertUnion(CompilationOutcome.Succeeded result, ViewSymbol view,
                                    List<String> aliases, List<String> targets) {
        assertEquals(view.bases(), assertInstanceOf(ViewSymbol.Union.class, view.formation().orElseThrow()).bases());
        assertBases(result, view, aliases, targets, false);
    }

    private static void assertBases(CompilationOutcome.Succeeded result, ViewSymbol view,
                                    List<String> aliases, List<String> targets, boolean lastOptional) {
        assertEquals(aliases, names(result, view.bases()));
        assertTrue(view.baseView().isEmpty());
        for (int i = 0; i < aliases.size(); i++) {
            var base = basis(result, view.bases().get(i));
            assertEquals(view.id(), base.owner().orElseThrow());
            assertEquals(named(result, targets.get(i)).id(), base.target());
            assertEquals(lastOptional && i == aliases.size() - 1, base.orNull());
            base.nameRange().ifPresent(range -> navigation(result, range, base.id()));
        }
    }

    private static ViewSymbol.Base basis(CompilationOutcome.Succeeded result, SymbolId id) {
        return assertInstanceOf(ViewSymbol.Base.class, result.schema().semanticGraph().symbol(id).orElseThrow());
    }

    private static void assertAllOf(CompilationOutcome.Succeeded result, ViewSymbol view, List<String> outputNames,
                                    List<String> sourceNames, List<Integer> lengths) {
        assertEquals(outputNames, names(result, view.members()));
        for (int i = 0; i < sourceNames.size(); i++) {
            var output = assertInstanceOf(AttributeSymbol.class, result.schema().semanticGraph().symbol(view.members().get(i)).orElseThrow());
            var input = attribute(result, sourceNames.get(i));
            var origin = assertInstanceOf(Origin.DerivedOrigin.class, output.origin());
            assertEquals(input.id(), origin.declaredAt());
            assertEquals(DerivationKind.ALIAS, origin.kind());
            assertEquals(input.declaredType(), output.declaredType());
            assertEquals(lengths.get(i), assertInstanceOf(TextType.class, effective(result, output.declaredType())).maxLength().orElseThrow());
            assertEquals(view.id(), output.owner().orElseThrow());
            assertEquals(i, output.header().declarationOrder());
            assertFalse(output.mandatory()); // None of these original class attributes is MANDATORY.
            assertEquals(1, output.derivations().size());
            var base = view.bases().get(i < 2 ? 0 : 1);
            assertSourcePath(result, output.derivations().getFirst(), base, sourceNames.get(i), false);
        }
    }

    private static void assertSourcePath(CompilationOutcome.Succeeded result, ExpressionId expression,
                                         SymbolId baseId, String target, boolean sourceSpellsPath) {
        var path = path(result, expression);
        assertEquals(2, path.path().steps().size());
        var first = assertInstanceOf(PathStep.ViewBaseStep.class, path.path().steps().getFirst());
        var last = assertInstanceOf(PathStep.AttributeStep.class, path.path().steps().getLast());
        assertEquals(baseId, first.base());
        assertEquals(basis(result, baseId).target(), first.target());
        var attribute = attribute(result, target);
        assertEquals(attribute.id(), last.target());
        assertEquals(attribute.name().value(), last.name());
        assertEquals(attribute.declaredType(), path.typeId());
        if (sourceSpellsPath) {
            navigation(result, first.range(), first.base());
            navigation(result, last.range(), last.target());
        }
    }

    private static void assertTranslatedModel(CompilationOutcome.Succeeded result) {
        var model = assertInstanceOf(ModelSymbol.class, named(result, "ModelB"));
        assertEquals("ModelA", model.translation().orElseThrow().sourceModel());
        assertEquals("1", model.translation().orElseThrow().sourceVersion().orElseThrow());
        assertEquals("fr", model.translation().orElseThrow().language().orElseThrow());
    }

    private static void assertAggregation(CompilationOutcome.Succeeded result, ViewSymbol view,
                                          String alias, String target, String key) {
        var formation = assertInstanceOf(ViewSymbol.Aggregation.class, view.formation().orElseThrow());
        assertEquals(List.of(formation.base()), view.bases());
        assertBases(result, view, List.of(alias), List.of(target), false);
        assertEquals(1, formation.equal().size());
        assertSourcePath(result, formation.equal().getFirst(), formation.base(), key, true);
        assertNumeric(result, path(result, formation.equal().getFirst()).typeId());
        assertTrue(view.members().isEmpty());
        var aggregate = result.schema().semanticGraph().symbols().stream()
                .filter(s -> s.owner().filter(view.id()::equals).isPresent() && s.name().value().equals("AGGREGATES"))
                .map(s -> assertInstanceOf(AttributeSymbol.class, s)).findFirst().orElseThrow();
        assertTrue(aggregate.name().synthetic());
        assertTrue(aggregate.transientAttribute());
        assertFalse(view.members().contains(aggregate.id()));
        var type = assertInstanceOf(CollectionType.class, result.schema().queries()
                .declaredType(new SymbolRef(result.schema().snapshotId(), aggregate.id())).known().orElseThrow());
        assertEquals(CollectionKind.BAG, type.collectionKind());
        assertEquals(Cardinality.unboundedFrom(0), type.cardinality());
        assertEquals(named(result, target).id(), assertInstanceOf(StructureValueType.class, effective(result, type.elementType())).target().orElseThrow());
        var origin = assertInstanceOf(Origin.DerivedOrigin.class, aggregate.origin());
        assertEquals(formation.base(), origin.declaredAt());
        assertEquals(DerivationKind.ALIAS, origin.kind());
    }

    private static void assertUnionAttribute(CompilationOutcome.Succeeded result, ViewSymbol view,
                                             String qualified, String first, String second) {
        var attribute = attribute(result, qualified);
        assertInstanceOf(Origin.DeclaredOrigin.class, attribute.origin());
        assertFalse(attribute.mandatory());
        assertNumeric(result, attribute.declaredType());
        assertEquals(2, attribute.derivations().size());
        assertSourcePath(result, attribute.derivations().getFirst(), view.bases().getFirst(), first, true);
        assertSourcePath(result, attribute.derivations().getLast(), view.bases().getLast(), second, true);
    }

    private static void assertNumeric(CompilationOutcome.Succeeded result, TypeId id) {
        var type = assertInstanceOf(NumericType.class, effective(result, id));
        assertEquals(0, BigDecimal.ONE.compareTo(type.range().minimum().orElseThrow().exactValue()));
        assertEquals(0, BigDecimal.TEN.compareTo(type.range().maximum().orElseThrow().exactValue()));
        assertTrue(type.range().unit().isEmpty());
    }

    private static void changedOriginalRejected(String testId, String before, String after, String rule) throws Exception {
        var source = source(testId);
        assertTrue(source.text().contains(before), "negative control must modify its intended construct");
        var changed = new SourceText(source.id(), new SourceRevision(2), source.text().replace(before, after));
        var outcome = CompilerEngines.create().compile(request(changed), CancellationToken.none());
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, outcome);
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
        assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains(rule)),
                rejected.analysis().diagnostics()::toString);
    }
}
