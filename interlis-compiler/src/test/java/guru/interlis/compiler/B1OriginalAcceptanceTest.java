package guru.interlis.compiler;

import guru.interlis.model.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;
import static guru.interlis.compiler.OriginalViewAcceptanceSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** B1 exact-source acceptance: META-008/014, SEM-008/010/014, TEST-011/012. */
class B1OriginalAcceptanceTest {
    @Test void expressions_BasenamePathElement() throws Exception {
        assertBasename("expressions_BasenamePathElement", "BaseName", true);
    }

    @Test void expressions_BasenameToUnrenamedViewable() throws Exception {
        assertBasename("expressions_BasenameToUnrenamedViewable", "B", false);
    }

    private void assertBasename(String test, String alias, boolean explicit) throws Exception {
        var result = original("ili23.Expressions23Test." + test);
        var view = view(result, "Test.Base.VB");
        var base = projection(result, view, alias, "Test.Base.B");
        assertEquals(explicit, base.nameRange().isPresent());
        assertEquals(List.of("a"), names(result, view.members()));
        var a = attribute(result, "Test.Base.VB.a");
        text(result, a, 20);
        assertEquals(1, a.derivations().size());
        sourcePath(result, a.derivations().getFirst(), base, "Test.Base.B.Attr1", true);
    }

    @Test void abstractView_AbstractBaseInConcretViewDef() throws Exception {
        var result = original("ili23.View23Test.abstractView_AbstractBaseInConcretViewDef");
        var view = view(result, "Test.Join.BC");
        assertFalse(view.header().has(Modifier.ABSTRACT));
        assertInstanceOf(ViewSymbol.Join.class, view.formation().orElseThrow());
        assertEquals(List.of("B", "C"), names(result, view.bases()));
        assertTrue(named(result, "Test.Base.B").header().has(Modifier.ABSTRACT));
        assertEquals(List.of("Attr2", "Attr3"), names(result, view.members()));
        var attr2 = attribute(result, "Test.Join.BC.Attr2");
        var attr3 = attribute(result, "Test.Join.BC.Attr3");
        text(result, attr2, 10); text(result, attr3, 30);
        assertFalse(attr2.header().has(Modifier.ABSTRACT)); assertFalse(attr3.header().has(Modifier.ABSTRACT));
        sourcePath(result, attr2.derivations().getFirst(), basis(result, view.bases().getFirst()), "Test.Base.B.Attr2", true);
        allOf(result, attr3, "Test.Base.C.Attr3");
        assertEquals(named(result, "Test.Base.B.Attr1").id(), attribute(result, "Test.Base.B2.Attr1").overrides().orElseThrow());
        var topic = assertInstanceOf(TopicSymbol.class, named(result, "Test.Join"));
        assertEquals(List.of(named(result, "Test.Base").id()), topic.dependencies());
    }

    @Test void view_AcceptBasicSelectionViewDef() throws Exception {
        var result = original("ili23.View23Test.view_AcceptBasicSelectionViewDef");
        var view = view(result, "Test.View.VB");
        var base = projection(result, view, "B", "Test.Base.B");
        assertEquals(List.of("Attr1", "Attr2"), names(result, view.members()));
        for (int i = 1; i <= 2; i++) {
            var a = attribute(result, "Test.View.VB.Attr" + i);
            allOf(result, a, "Test.Base.B.Attr" + i);
            text(result, a, i == 1 ? 20 : 10);
            sourcePath(result, a.derivations().getFirst(), base, "Test.Base.B.Attr" + i, false);
        }
        assertTrue(view.selections().isEmpty());
        assertEquals(List.of(named(result, "Test.Base").id()),
                assertInstanceOf(TopicSymbol.class, named(result, "Test.View")).dependencies());
    }

    @Test void view_AcceptSimpleBaseExtentionInView() throws Exception {
        var result = original("ili23.View23Test.view_AcceptSimpleBaseExtentionInView");
        var view = view(result, "Test.SelectionView.VB");
        var base = projection(result, view, "B", "Test.Base.B");
        assertEquals(1, view.baseExtensions().size());
        var extension = view.baseExtensions().getFirst();
        assertEquals(base.id(), extension.base());
        assertEquals(1, extension.extensions().size());
        var c = basis(result, extension.extensions().getFirst());
        assertEquals(List.of(base.id(), c.id()), view.bases());
        assertEquals("C", c.name().value());
        assertEquals(named(result, "Test.Base.C").id(), c.target());
        assertEquals(base.target(), assertInstanceOf(ClassSymbol.class, named(result, "Test.Base.C")).baseType().orElseThrow());
        assertEquals(List.of("a"), names(result, view.members()));
        var a = attribute(result, "Test.SelectionView.VB.a");
        text(result, a, 30);
        sourcePath(result, a.derivations().getFirst(), c, "Test.Base.C.Attr2", true);
    }

    @Test void viewProjectionComplete() throws Exception {
        var result = original("ili23.ViewDef23Test.viewProjectionComplete");
        assertEquals(10, result.schema().semanticGraph().symbols().stream().filter(ViewSymbol.class::isInstance).count());
        var translated = assertInstanceOf(ModelSymbol.class, named(result, "ModelB"));
        assertEquals("ModelA", translated.translation().orElseThrow().sourceModel());
        assertEquals("1", translated.translation().orElseThrow().sourceVersion().orElseThrow());
        for (String letter : List.of("A", "B")) {
            var prefix = "Model" + letter + ".Topic" + letter;
            var first = prefix + "1.";
            var second = prefix + "2.";
            var className = "Class" + letter + "0";
            var member = letter.equals("A") ? "a" : "b";
            var v0 = view(result, first + "View" + letter + "0");
            var v1 = view(result, first + "View" + letter + "1");
            var v2 = view(result, first + "View" + letter + "2");
            var v3 = view(result, first + "View" + letter + "3");
            var extended = view(result, second + "View" + letter + "0");
            var base = projection(result, v0, letter + "0", first + className);
            assertTrue(v0.header().has(Modifier.ABSTRACT));
            assertTrue(v1.header().has(Modifier.TRANSIENT));
            assertTrue(v3.header().has(Modifier.FINAL));
            assertTrue(extended.header().has(Modifier.EXTENDED));
            assertEquals(v2.id(), v3.baseView().orElseThrow());
            assertEquals(v0.id(), extended.baseView().orElseThrow());
            assertEquals(List.of(member + "1"), names(result, v0.members()));
            var output = attribute(result, first + "View" + letter + "0." + member + "1");
            assertInstanceOf(NumericType.class, effective(result, output.declaredType()));
            sourcePath(result, output.derivations().getFirst(), base, first + className + "." + member + "1", true);
            assertEquals(1, v0.selections().size());
            var selection = binary(result, v0.selections().getFirst());
            assertEquals(BinaryOperator.EQ, selection.operator());
            sourcePath(result, selection.left(), base, first + className + "." + member + "0", true);
            assertEquals(1, v0.constraints().size());
            var constraint = assertInstanceOf(ConstraintSymbol.class, result.schema().semanticGraph().symbol(v0.constraints().getFirst()).orElseThrow());
            var condition = binary(result, assertInstanceOf(MandatoryConstraint.class, constraint.definition()).condition());
            assertEquals(BinaryOperator.EQ, condition.operator());
            var constraintTarget = assertInstanceOf(PathStep.AttributeStep.class, path(result, condition.left()).path().steps().getLast());
            assertEquals(output.id(), constraintTarget.target()); navigation(result, constraintTarget.range(), output.id());
            assertEquals(List.of(member + "0", member + "1"), names(result, v1.members()));
            for (int i = 0; i < 2; i++) allOf(result,
                    attribute(result, first + "View" + letter + "1." + member + i), first + className + "." + member + i);
            assertTrue(v2.members().isEmpty()); assertTrue(v3.members().isEmpty());
            assertEquals(v0.members(), extended.members());
            assertEquals(v0.selections(), extended.selections());
            var effective = result.schema().queries().effectiveMembers(new SymbolRef(result.schema().snapshotId(), extended.id())).known().orElseThrow();
            assertEquals(EffectiveMemberOrigin.INHERITED, effective.getFirst().origin());
            assertEquals(List.of(v0.id(), extended.id()), effective.getFirst().inheritancePath());
            assertEquals(extended.id(), effective.getFirst().effectiveOwner());
            var ext = extended.baseExtensions().getFirst();
            assertEquals(base.id(), ext.base());
            var extAlias = basis(result, ext.extensions().getFirst());
            assertEquals(letter + "0p", extAlias.name().value());
            assertEquals(named(result, second + className).id(), extAlias.target());
            navigation(result, extAlias.nameRange().orElseThrow(), extAlias.id());
        }
        var source = source("ili23.ViewDef23Test.viewProjectionComplete");
        // b0 and b1 have identical types. Translation must still preserve the selected identity.
        var changed = source.text().replace("WHERE B0->b0==1", "WHERE B0->b1==1");
        assertNotEquals(source.text(), changed);
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(
                request(new SourceText(source.id(), source.revision(), changed)), CancellationToken.none()));
        assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains("TRANSLATION_REFERENCE")),
                rejected.analysis().diagnostics()::toString);
    }

    private static ViewSymbol.Base projection(CompilationOutcome.Succeeded result, ViewSymbol view, String alias, String target) {
        var formation = assertInstanceOf(ViewSymbol.Projection.class, view.formation().orElseThrow());
        assertEquals(formation.base(), view.bases().getFirst());
        if (view.baseExtensions().isEmpty()) assertEquals(1, view.bases().size());
        var base = basis(result, formation.base());
        assertEquals(alias, base.name().value()); assertEquals(named(result, target).id(), base.target());
        assertFalse(base.orNull());
        base.nameRange().ifPresent(range -> navigation(result, range, base.id()));
        return base;
    }
    private static ViewSymbol.Base basis(CompilationOutcome.Succeeded result, SymbolId id) {
        return assertInstanceOf(ViewSymbol.Base.class, result.schema().queries().symbol(new SymbolRef(result.schema().snapshotId(), id)).known().orElseThrow());
    }
    private static void text(CompilationOutcome.Succeeded result, AttributeSymbol attribute, int length) {
        assertEquals(length, assertInstanceOf(TextType.class, effective(result, attribute.declaredType())).maxLength().orElseThrow());
    }
    private static void allOf(CompilationOutcome.Succeeded result, AttributeSymbol output, String input) {
        var origin = assertInstanceOf(Origin.DerivedOrigin.class, output.origin());
        assertEquals(DerivationKind.ALIAS, origin.kind());
        assertEquals(named(result, input).id(), origin.declaredAt());
        assertEquals(attribute(result, input).declaredType(), output.declaredType());
    }
    private static void sourcePath(CompilationOutcome.Succeeded result, ExpressionId expression, ViewSymbol.Base base, String target, boolean spelled) {
        var path = path(result, expression);
        assertEquals(2, path.path().steps().size());
        var first = assertInstanceOf(PathStep.ViewBaseStep.class, path.path().steps().getFirst());
        var last = assertInstanceOf(PathStep.AttributeStep.class, path.path().steps().getLast());
        assertEquals(base.id(), first.base()); assertEquals(base.target(), first.target());
        assertEquals(named(result, target).id(), last.target());
        assertEquals(attribute(result, target).declaredType(), path.typeId());
        if (spelled) {
            navigation(result, first.range(), base.nameRange().isPresent() ? base.id() : base.target());
            navigation(result, last.range(), last.target());
        }
    }
    private static BinaryExpression binary(CompilationOutcome.Succeeded result, ExpressionId id) {
        var expression = assertInstanceOf(BinaryExpression.class, result.schema().queries().expression(new ExpressionRef(result.schema().snapshotId(), id)).known().orElseThrow());
        assertInstanceOf(BooleanType.class, effective(result, expression.typeId()));
        return expression;
    }
}
