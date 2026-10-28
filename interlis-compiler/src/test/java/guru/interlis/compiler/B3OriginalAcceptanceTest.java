package guru.interlis.compiler;

import guru.interlis.model.*;
import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static guru.interlis.compiler.OriginalViewAcceptanceSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public semantic/query proofs for the six unchanged B3 suite sources. */
class B3OriginalAcceptanceTest {
    @Test void renamedInspectionBindsItsAliasToTheInspectedElement() throws Exception {
        var result = original("ili23.View23Test.view_AcceptBasenameOfRenamedInspectionDef");
        var inspection = structureInspection(result, "Test.Base.VB", "Test.Base.B", "Test.Base.A",
                "Test.Base.B.Attr2", CollectionKind.BAG);
        var basis = (ViewSymbol.Base) result.schema().semanticGraph().symbol(inspection.base()).orElseThrow();
        assertEquals("C", basis.name().value());
        navigation(result, basis.nameRange().orElseThrow(), basis.id());
        var derived = attribute(result, "Test.Base.VB.Attr1");
        textLength(result, derived.declaredType(), 20);
        var expression = path(result, derived.derivations().getFirst());
        inspectedAttribute(result, expression, inspection.base(), "Test.Base.A", "Test.Base.A.Attr1");
        assertEquals(Optional.empty(), derived.overrides());
    }

    @Test void unrenamedInspectionUsesItsSourceNameAndInspectedMemberNamespace() throws Exception {
        var result = original("ili23.View23Test.view_AcceptBasenameOfUnrenamedInspectionDef");
        var inspection = structureInspection(result, "Test.Base.VB", "Test.Base.B", "Test.Base.A",
                "Test.Base.B.Attr2", CollectionKind.BAG);
        var basis = (ViewSymbol.Base) result.schema().semanticGraph().symbol(inspection.base()).orElseThrow();
        assertEquals("B", basis.name().value());
        assertTrue(basis.nameRange().isEmpty());
        var derived = attribute(result, "Test.Base.VB.Attr1");
        textLength(result, derived.declaredType(), 20);
        inspectedAttribute(result, path(result, derived.derivations().getFirst()), inspection.base(),
                "Test.Base.A", "Test.Base.A.Attr1");
    }

    @Test void inspectionAllOfCopiesTheStructureAttributeAndRetainsItsOrigin() throws Exception {
        var result = original("ili23.View23Test.view_AcceptBasicInspectionDef");
        var graph = result.schema().semanticGraph();
        var inspected = view(result, "Test.Inspection.VB");
        var parent = assertInstanceOf(ClassSymbol.class, named(result, "Test.Base.B"));
        assertEquals(1, parent.constraints().size());
        assertInstanceOf(UniquenessConstraint.class,
                assertInstanceOf(ConstraintSymbol.class, graph.symbol(parent.constraints().getFirst()).orElseThrow()).definition());
        assertTrue(inspected.constraints().isEmpty());
        var inspection = structureInspection(result, "Test.Inspection.VB", "Test.Base.B", "Test.Base.A",
                "Test.Base.B.Attr2", CollectionKind.BAG);
        assertEquals(List.of("Attr1"), names(result, inspected.members()));
        var copy = (AttributeSymbol) graph.symbol(inspected.members().getFirst()).orElseThrow();
        var original = attribute(result, "Test.Base.A.Attr1");
        var parentAttribute = attribute(result, "Test.Base.B.Attr1");
        assertNotEquals(original.id(), copy.id());
        assertEquals(original.declaredType(), copy.declaredType());
        assertEquals(new Origin.DerivedOrigin(original.id(), DerivationKind.ALIAS), copy.origin());
        assertEquals(Optional.of(inspected.id()), copy.owner());
        assertFalse(copy.mandatory());
        assertEquals(Optional.empty(), copy.overrides());
        textLength(result, copy.declaredType(), 20);
        var derivation = path(result, copy.derivations().getFirst());
        var last = assertInstanceOf(PathStep.AttributeStep.class, derivation.path().steps().getLast());
        assertEquals(original.id(), last.target());
        assertNotEquals(parentAttribute.id(), last.target());
        var first = assertInstanceOf(PathStep.ViewBaseStep.class, derivation.path().steps().getFirst());
        assertEquals(inspection.base(), first.base());
        assertEquals(inspection.element(), first.target());
        var member = result.analysis().queries().effectiveMembers(new SymbolRef(result.analysis().snapshotId(), inspected.id()))
                .known().orElseThrow().getFirst();
        assertEquals(copy.id(), member.declaredSymbol());
        assertEquals(0, member.position());
        assertEquals(EffectiveMemberOrigin.LOCAL, member.origin());
        var topic = assertInstanceOf(TopicSymbol.class, named(result, "Test.Inspection"));
        assertEquals(List.of(named(result, "Test.Base").id()), topic.dependencies());
    }

    @Test void translatedInspectionUsesTranslatedStructureParentAndCollectionPath() throws Exception {
        var result = original("ili23.ViewDef23Test.viewInspectionComplete");
        var graph = result.schema().semanticGraph();
        var a = structureInspection(result, "ModelA.TopicA1.ViewA0", "ModelA.TopicA1.ClassA0",
                "ModelA.TopicA1.StructA0", "ModelA.TopicA1.ClassA0.a0", CollectionKind.LIST);
        var b = structureInspection(result, "ModelB.TopicB1.ViewB0", "ModelB.TopicB1.ClassB0",
                "ModelB.TopicB1.StructB0", "ModelB.TopicB1.ClassB0.b0", CollectionKind.LIST);
        assertNotEquals(a.parent(), b.parent());
        assertNotEquals(a.element(), b.element());
        assertNotEquals(a.path(), b.path());
        assertEquals("A0", graph.symbol(a.base()).orElseThrow().name().value());
        assertEquals("B0", graph.symbol(b.base()).orElseThrow().name().value());
        assertTrue(view(result, "ModelA.TopicA1.ViewA0").members().isEmpty());
        assertTrue(view(result, "ModelB.TopicB1.ViewB0").members().isEmpty());
        var translated = assertInstanceOf(ModelSymbol.class, named(result, "ModelB"));
        assertEquals("ModelA", translated.translation().orElseThrow().sourceModel());
        assertEquals(Optional.of("1"), translated.translation().orElseThrow().sourceVersion());
        for (var inspection : List.of(a, b)) {
            var attribute = assertInstanceOf(PathStep.AttributeStep.class, path(result, inspection.path()).path().steps().getFirst());
            navigation(result, attribute.range(), attribute.target());
            assertFalse(attribute.target().equals(named(result, inspection == a ? "ModelA.TopicA1.ClassA0.a1" : "ModelB.TopicB1.ClassB0.b1").id()));
        }
    }

    @Test void derivedAssociationRetainsViewBasisRoleTargetsAndJoinSelection() throws Exception {
        var result = original("ili23.Association23Test.association_DerivedAssociationDef");
        var graph = result.schema().semanticGraph();
        var join = view(result, "model.topic.ABIntersection");
        var formation = assertInstanceOf(ViewSymbol.Join.class, join.formation().orElseThrow());
        assertEquals(List.of("A", "B"), names(result, formation.bases()));
        var association = assertInstanceOf(AssociationSymbol.class, named(result, "model.topic.IntersectedAB"));
        var derived = assertInstanceOf(ViewSymbol.Base.class, graph.symbol(association.derivedFrom().orElseThrow()).orElseThrow());
        assertEquals(join.id(), derived.target());
        assertEquals(Optional.of(association.id()), derived.owner());
        assertEquals(List.of("ARole", "BRole"), names(result, association.roles()));
        for (int i = 0; i < 2; i++) {
            String className = i == 0 ? "A" : "B";
            var role = assertInstanceOf(RoleSymbol.class, graph.symbol(association.roles().get(i)).orElseThrow());
            var target = named(result, "model.topic." + className);
            assertEquals(List.of(target.id()), role.targetRoots());
            var derivation = path(result, role.derivation().orElseThrow());
            assertEquals(2, derivation.path().steps().size());
            var from = assertInstanceOf(PathStep.ViewBaseStep.class, derivation.path().steps().getFirst());
            var participant = assertInstanceOf(PathStep.ViewBaseStep.class, derivation.path().steps().getLast());
            assertEquals(derived.id(), from.base());
            assertEquals(join.id(), from.target());
            assertEquals(formation.bases().get(i), participant.base());
            assertEquals(target.id(), participant.target());
            assertEquals(target.id(), assertInstanceOf(ReferenceType.class, effective(result, derivation.typeId())).target().orElseThrow());
            navigation(result, from.range(), join.id());
            navigation(result, participant.range(), target.id());
        }
        var selection = assertInstanceOf(FunctionCallExpression.class, graph.expression(join.selections().getFirst()).orElseThrow());
        assertEquals(named(result, "model.Intersect").id(), selection.targetFunction().orElseThrow());
        assertInstanceOf(BooleanType.class, effective(result, selection.typeId()));
        assertEquals(2, selection.children().size());
        for (int i = 0; i < 2; i++) {
            var argument = path(result, selection.children().get(i));
            var first = assertInstanceOf(PathStep.ViewBaseStep.class, argument.path().steps().getFirst());
            assertEquals(formation.bases().get(i), first.base());
            var last = assertInstanceOf(PathStep.AttributeStep.class, argument.path().steps().getLast());
            assertEquals(attribute(result, i == 0 ? "model.topic.A.a1" : "model.topic.B.b1").id(), last.target());
            assertEquals(GeometryKind.SURFACE, assertInstanceOf(GeometryType.class, effective(result, argument.typeId())).geometryKind());
            navigation(result, last.range(), last.target());
        }
    }

    @Test void projectedRolePathsDistinguishParticipantsSingletonAccessAndObjectSets() throws Exception {
        var result = original("ili23.View23Test.view_ObjectPathWithRolename");
        var graph = result.schema().semanticGraph();
        var manyToMany = view(result, "ModelA.Topic.View_n2n");
        assertEquals(named(result, "ModelA.Topic.n2n").id(), ((ViewSymbol.Base) graph.symbol(manyToMany.bases().getFirst()).orElseThrow()).target());
        assertTrue(manyToMany.members().isEmpty(), "ALL OF an association does not turn roles into attributes");
        var comparison = condition(result, manyToMany);
        assertEquals(BinaryOperator.NEQ,assertInstanceOf(BinaryExpression.class,comparison).operator());
        assertEquals(2, comparison.children().size());
        for (int i = 0; i < 2; i++) {
            String letter = i == 0 ? "K" : "L";
            var participant = path(result, comparison.children().get(i));
            assertEquals(3, participant.path().steps().size());
            var step = assertInstanceOf(PathStep.RoleStep.class, participant.path().steps().get(1));
            assertEquals(named(result, "ModelA.Topic.n2n.role" + letter + "_n2n").id(), step.target());
            var member = assertInstanceOf(PathStep.AttributeStep.class, participant.path().steps().getLast());
            assertEquals(attribute(result, "ModelA.Topic.Class" + letter + ".attr" + letter).id(), member.target());
            textLength(result, participant.typeId(), 10);
            referenceAt(result, step.range(), step.target());
            navigation(result, member.range(), member.target());
        }
        var singletonView = view(result, "ModelA.Topic.View_n21");
        assertEquals(BinaryOperator.NEQ,assertInstanceOf(BinaryExpression.class,condition(result,singletonView)).operator());
        projectedCopy(result, singletonView, "ModelA.Topic.ClassL.attrL");
        var singleton = path(result, condition(result, singletonView).children().getFirst());
        var one = assertInstanceOf(PathStep.AssociationAccessStep.class, singleton.path().steps().get(1));
        assertEquals(named(result, "ModelA.Topic.n21").id(), one.association());
        assertEquals(named(result, "ModelA.Topic.n21.roleL_n21").id(), one.sourceRole());
        assertEquals(named(result, "ModelA.Topic.n21.roleK_n21").id(), one.targetRole());
        assertEquals(Cardinality.of(0, 1), one.cardinality());
        assertFalse(one.associationObject());
        assertFalse(assertInstanceOf(ObjectArgumentType.class, effective(result, one.resultType())).objects());
        referenceAt(result, one.range(), one.targetRole());
        textLength(result, singleton.typeId(), 10);
        var objectSetView = view(result, "ModelA.Topic.View_class_n2n");
        projectedCopy(result, objectSetView, "ModelA.Topic.ClassK.attrK");
        var count = assertInstanceOf(FunctionCallExpression.class, graph.expression(condition(result, objectSetView).children().getFirst()).orElseThrow());
        assertEquals("INTERLIS.objectCount", count.functionName());
        var set = path(result, count.children().getFirst());
        var many = assertInstanceOf(PathStep.AssociationAccessStep.class, set.path().steps().getLast());
        assertEquals(named(result, "ModelA.Topic.n2n").id(), many.association());
        assertEquals(named(result, "ModelA.Topic.n2n.roleK_n2n").id(), many.sourceRole());
        assertEquals(named(result, "ModelA.Topic.n2n.roleL_n2n").id(), many.targetRole());
        assertEquals(Cardinality.unboundedFrom(0), many.cardinality());
        var objects = assertInstanceOf(ObjectArgumentType.class, effective(result, set.typeId()));
        assertTrue(objects.objects());
        assertEquals(named(result, "ModelA.Topic.ClassL").id(), objects.base().orElseThrow());
        referenceAt(result, many.range(), many.targetRole());
    }

    private static ViewSymbol.Inspection structureInspection(CompilationOutcome.Succeeded result, String viewName,
            String parentName, String elementName, String collectionName, CollectionKind kind) {
        var graph = result.schema().semanticGraph();
        var view = view(result, viewName);
        var inspection = assertInstanceOf(ViewSymbol.Inspection.class, view.formation().orElseThrow());
        assertFalse(inspection.area());
        assertEquals(named(result, parentName).id(), inspection.parent());
        assertEquals(named(result, elementName).id(), inspection.element());
        assertEquals(List.of(inspection.base()), view.bases());
        var basis = assertInstanceOf(ViewSymbol.Base.class, graph.symbol(inspection.base()).orElseThrow());
        assertEquals(inspection.parent(), basis.target());
        var decomposition = path(result, inspection.path());
        assertEquals(ExpressionTypeState.RESOLVED, decomposition.typeState());
        assertEquals(1, decomposition.path().steps().size());
        var step = assertInstanceOf(PathStep.AttributeStep.class, decomposition.path().steps().getFirst());
        assertEquals(attribute(result, collectionName).id(), step.target());
        var collection = assertInstanceOf(CollectionType.class, effective(result, decomposition.typeId()));
        assertEquals(kind, collection.collectionKind());
        assertEquals(inspection.element(), assertInstanceOf(StructureValueType.class, effective(result, collection.elementType())).target().orElseThrow());
        navigation(result, step.range(), step.target());
        return inspection;
    }

    private static void inspectedAttribute(CompilationOutcome.Succeeded result, PathExpression expression,
            SymbolId base, String elementName, String attributeName) {
        assertEquals(2, expression.path().steps().size());
        var from = assertInstanceOf(PathStep.ViewBaseStep.class, expression.path().steps().getFirst());
        assertEquals(base, from.base());
        assertEquals(named(result, elementName).id(), from.target());
        var member = assertInstanceOf(PathStep.AttributeStep.class, expression.path().steps().getLast());
        assertEquals(attribute(result, attributeName).id(), member.target());
        var basis = (ViewSymbol.Base) result.schema().semanticGraph().symbol(base).orElseThrow();
        navigation(result, from.range(), basis.nameRange().isPresent() ? basis.id() : basis.target());
        navigation(result, member.range(), member.target());
        textLength(result, expression.typeId(), 20);
    }

    private static void projectedCopy(CompilationOutcome.Succeeded result, ViewSymbol view, String sourceAttribute) {
        assertEquals(1, view.members().size());
        var original = attribute(result, sourceAttribute);
        var copy = (AttributeSymbol) result.schema().semanticGraph().symbol(view.members().getFirst()).orElseThrow();
        assertNotEquals(original.id(), copy.id());
        assertEquals(original.name(), copy.name());
        assertEquals(original.declaredType(), copy.declaredType());
        assertEquals(new Origin.DerivedOrigin(original.id(), DerivationKind.ALIAS), copy.origin());
        var derivation = path(result, copy.derivations().getFirst());
        assertEquals(original.id(), assertInstanceOf(PathStep.AttributeStep.class, derivation.path().steps().getLast()).target());
        textLength(result, copy.declaredType(), 10);
    }

    private static TypedExpression condition(CompilationOutcome.Succeeded result, ViewSymbol view) {
        var constraint = (ConstraintSymbol) result.schema().semanticGraph().symbol(view.constraints().getFirst()).orElseThrow();
        var id = assertInstanceOf(MandatoryConstraint.class, constraint.definition()).condition();
        return result.analysis().queries().expression(new ExpressionRef(result.analysis().snapshotId(), id)).known().orElseThrow();
    }

    private static void referenceAt(CompilationOutcome.Succeeded result, SourceRange range, SymbolId target) {
        navigation(result, range, target);
        var references = result.analysis().queries().referencesTo(new SymbolRef(result.analysis().snapshotId(), target));
        assertTrue(references.stream().flatMap(r -> r.segments().stream()).anyMatch(s -> s.range().equals(range)
                && s.target().equals(Optional.of(target))), () -> "Missing reference query result at " + range);
    }

    private static void textLength(CompilationOutcome.Succeeded result, TypeId type, int length) {
        assertEquals(length, assertInstanceOf(TextType.class, effective(result, type)).maxLength().orElseThrow());
    }
}
