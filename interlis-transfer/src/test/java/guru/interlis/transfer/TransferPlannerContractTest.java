package guru.interlis.transfer;

import guru.interlis.model.Cardinality;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveMemberOrigin;
import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.NumericType;
import guru.interlis.model.OidRule;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeRef;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static guru.interlis.transfer.TransferTestSchemas.compile;
import static guru.interlis.transfer.TransferTestSchemas.compileInline;
import static guru.interlis.transfer.TransferTestSchemas.fixture;
import static guru.interlis.transfer.TransferTestSchemas.ref;
import static guru.interlis.transfer.TransferTestSchemas.survey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferPlannerContractTest {
    @Test
    void projectsEffectiveMemberOrderAndOriginAcrossTheImportClosure() throws IOException {
        var schema = survey();
        var snapshot = schema.snapshotId();
        var fingerprint = schema.fingerprint();
        var graph = schema.semanticGraph();
        var plan = ready(schema);

        var survey = ref(schema, "Survey");
        var base = ref(schema, "Base");
        var data = ref(schema, "Survey", "Data");
        var common = ref(schema, "Base", "Common");
        var building = ref(schema, "Survey", "Data", "Building");
        var baseBuilding = ref(schema, "Base", "Common", "BaseBuilding");
        var ownerLink = ref(schema, "Survey", "Data", "OwnerLink");

        assertEquals(List.of(survey), plan.rootModels());
        assertEquals(List.of(data), plan.model(survey).orElseThrow().topics());
        assertEquals(List.of(common), plan.model(base).orElseThrow().topics());
        var model = plan.model(survey).orElseThrow();
        assertEquals(LanguageVersion.ILI24, model.languageVersion());
        assertEquals("1.0", model.modelVersion());
        assertEquals("http://www.interlis.ch/xtf/2.4/Survey", model.xmlNamespace());
        var topic = plan.topic(data).orElseThrow();
        assertEquals(survey, topic.model());
        assertEquals(List.of(common), topic.dependencies());
        assertEquals(List.of(building, ownerLink), topic.objectTypes());

        var object = plan.object(building).orElseThrow();
        assertEquals(SymbolKind.CLASS, object.kind());
        assertEquals(Optional.of(baseBuilding), object.baseType());
        assertEquals(Optional.of(data), object.topic());
        assertEquals(List.of("height", "label", "ownerName"), slotNames(object));
        var members = object.slots().stream().map(TransferSlot::member).toList();
        assertEquals(List.of(0, 1, 2), members.stream().map(EffectiveMember::position).toList());
        assertEquals(List.of(EffectiveMemberOrigin.OVERRIDDEN, EffectiveMemberOrigin.INHERITED,
                EffectiveMemberOrigin.LOCAL), members.stream().map(EffectiveMember::origin).toList());
        assertEquals(List.of(true, false, false), members.stream().map(EffectiveMember::overridden).toList());
        assertEquals(List.of(building.symbol()), members.getFirst().inheritancePath());
        assertEquals(List.of(baseBuilding.symbol(), building.symbol()), members.get(1).inheritancePath());
        var height = assertInstanceOf(AttributeSlot.class, object.slots().getFirst());
        assertEquals(Optional.of(ref(schema, "Base", "Common", "BaseBuilding", "height").symbol()),
                height.attribute().overrides());
        assertEquals(Cardinality.of(0, 1), height.cardinality());
        assertInstanceOf(NumericType.class, plan.type(height.effectiveType()).known().orElseThrow());

        var uuid = ref(schema, "INTERLIS", "UUIDOID");
        assertEquals(plan.topic(common).orElseThrow().oidRules(), object.oidRules());
        assertEquals(Optional.of(uuid.symbol()), object.oidRules().getFirst().domain());
        assertTrue(plan.symbol(uuid).known().isPresent());
        assertTrue(plan.model(ref(schema, "INTERLIS")).isEmpty());
        var association = plan.object(ownerLink).orElseThrow();
        assertEquals(SymbolKind.ASSOCIATION, association.kind());
        assertEquals(List.of("building", "ownerRole"), slotNames(association));
        var ownerRole = assertInstanceOf(RoleSlot.class, association.slots().get(1)).role();
        assertEquals(List.of(baseBuilding.symbol()), ownerRole.targetRoots());
        assertEquals(Cardinality.exactly(1), ownerRole.cardinality());
        assertTrue(ownerRole.external());

        assertThrows(UnsupportedOperationException.class, () -> plan.rootModels().clear());
        assertThrows(UnsupportedOperationException.class, () -> topic.objectTypes().clear());
        assertThrows(UnsupportedOperationException.class, () -> object.slots().clear());
        assertSame(graph, schema.semanticGraph());
        assertEquals(snapshot, schema.snapshotId());
        assertEquals(snapshot, plan.snapshotId());
        assertEquals(fingerprint, schema.fingerprint());
        assertEquals(fingerprint, plan.schemaFingerprint());
    }

    @Test
    void retainsStructureListsAssociationRolesAndExactGeometryFromTheReferenceModel() throws IOException {
        var schema = compile("Reference23", fixture("reference/iox/ili23/Reference23.ili"));
        var plan = ready(schema);
        var records = ref(schema, "Reference23", "Records");
        var detail = ref(schema, "Reference23", "Records", "Detail");
        var parent = ref(schema, "Reference23", "Records", "Parent");
        var item = ref(schema, "Reference23", "Records", "Item");
        var link = ref(schema, "Reference23", "Records", "ParentLink");
        var parcel = ref(schema, "Reference23", "Records", "Parcel");
        var model = plan.model(ref(schema, "Reference23")).orElseThrow();
        assertEquals(LanguageVersion.ILI23, model.languageVersion());
        assertEquals("2026-11-06", model.modelVersion());
        assertEquals("http://www.interlis.ch/INTERLIS2.3", model.xmlNamespace());
        assertEquals(List.of(parent, item, link, parcel), plan.topic(records).orElseThrow().objectTypes());

        var structure = plan.object(detail).orElseThrow();
        assertEquals(SymbolKind.STRUCTURE, structure.kind());
        assertFalse(structure.transferable());
        assertEquals(List.of("description"), slotNames(structure));
        var itemObject = plan.object(item).orElseThrow();
        assertEquals(List.of("code", "details"), slotNames(itemObject));
        var details = assertInstanceOf(AttributeSlot.class, itemObject.slots().get(1));
        var list = assertInstanceOf(CollectionType.class, plan.type(details.effectiveType()).known().orElseThrow());
        assertEquals(CollectionKind.LIST, list.collectionKind());
        assertEquals(Cardinality.unboundedFrom(0), details.cardinality());
        var element = assertInstanceOf(StructureValueType.class,
                plan.type(new TypeRef(plan.snapshotId(), list.elementType())).known().orElseThrow());
        assertEquals(List.of(detail.symbol()), element.targetRoots());

        var association = plan.object(link).orElseThrow();
        assertEquals(List.of("parent", "children"), slotNames(association));
        var children = assertInstanceOf(RoleSlot.class, association.slots().get(1)).role();
        assertEquals(List.of(item.symbol()), children.targetRoots());
        assertEquals(Cardinality.unboundedFrom(0), children.cardinality());

        var shape = assertInstanceOf(AttributeSlot.class, plan.object(parcel).orElseThrow().slots().getFirst());
        var area = assertInstanceOf(GeometryType.class, plan.type(shape.effectiveType()).known().orElseThrow());
        assertEquals(GeometryKind.AREA, area.geometryKind());
        assertEquals(List.of(ref(schema, "INTERLIS", "STRAIGHTS").symbol(), ref(schema, "INTERLIS", "ARCS").symbol()),
                area.lineForms());
        assertTrue(area.withoutOverlaps());
        var tolerance = assertInstanceOf(GeometryType.Explicit.class, area.overlap().orElseThrow()).value();
        assertEquals("0.01", tolerance.lexeme());
        assertEquals(new BigDecimal("0.01"), tolerance.exactValue());
    }

    @Test
    void keepsDeclaredTransferAndCanonicalNamesSeparateForTranslations() {
        var schema = compileInline("Target", """
                INTERLIS 2.3;
                MODEL Source AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T = CLASS A = a : TEXT*10; END A; END T;
                END Source.
                MODEL Target (de) AT "https://example.interlis.ch" VERSION "1.0" TRANSLATION OF Source ["1.0"] =
                  TOPIC U = CLASS B = b : TEXT*10; END B; END U;
                END Target.
                """);
        var originalPlan = ready(schema);
        var declaredPlan = assertInstanceOf(TransferPlanningResult.Ready.class,
                TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED)).plan();
        var translatedRef = ref(schema, "Target", "U", "B");
        var originalRef = ref(schema, "Source", "T", "A");
        var original = originalPlan.object(translatedRef).orElseThrow();
        var declared = declaredPlan.object(translatedRef).orElseThrow();
        assertNotEquals(originalRef, translatedRef);
        assertEquals(translatedRef, original.symbol());
        assertEquals(new TransferName(QualifiedName.of("Target", "U", "B"), QualifiedName.of("Source", "T", "A"),
                originalRef), original.name());
        assertEquals(new TransferName(QualifiedName.of("Target", "U", "B"), QualifiedName.of("Target", "U", "B"),
                originalRef), declared.name());
        assertEquals(new TransferName(QualifiedName.of("Target", "U", "B", "b"),
                QualifiedName.of("Source", "T", "A", "a"), ref(schema, "Source", "T", "A", "a")),
                original.slots().getFirst().name());
        assertEquals(QualifiedName.of("Target", "U", "B", "b"), declared.slots().getFirst().name().transfer());
        assertEquals(originalPlan.schemaFingerprint(), declaredPlan.schemaFingerprint());
    }

    @Test
    void retainsSeparateBasketOidRulesAndDeferredDomainAlternatives() {
        var schema = compileInline("GenericExample", """
                INTERLIS 2.4;
                MODEL GenericExample AT "https://example.org" VERSION "1" =
                  DOMAIN BasketId = OID TEXT*20; ObjectId = OID TEXT*20;
                         G (GENERIC) = COORD NUMERIC, NUMERIC;
                         A = COORD 0..100,0..100; B = COORD 200..300,200..300;
                  CONTEXT Default = G = A OR B;
                  TOPIC T =
                    BASKET OID AS BasketId; OID AS ObjectId;
                    DEFERRED GENERICS G;
                    CLASS C = NO OID; p: G; END C;
                  END T;
                  TOPIC U = CLASS D = END D; END U;
                END GenericExample.
                """);
        var plan = ready(schema);
        var topic = plan.topic(ref(schema, "GenericExample", "T")).orElseThrow();
        assertEquals(List.of(true, false), topic.oidRules().stream().map(OidRule::basket).toList());
        assertEquals(List.of(ref(schema, "GenericExample", "BasketId").symbol(),
                ref(schema, "GenericExample", "ObjectId").symbol()), topic.oidRules().stream()
                .map(rule -> rule.domain().orElseThrow()).toList());
        var rules = plan.object(ref(schema, "GenericExample", "T", "C")).orElseThrow().oidRules();
        assertEquals(1, rules.size());
        assertFalse(rules.getFirst().basket());
        assertEquals(Optional.empty(), rules.getFirst().domain());
        assertEquals(List.of(), plan.object(ref(schema, "GenericExample", "U", "D")).orElseThrow().oidRules());

        assertEquals(List.of(new TopicSymbol.GenericBinding(ref(schema, "GenericExample", "G").symbol(),
                List.of(ref(schema, "GenericExample", "A").symbol(), ref(schema, "GenericExample", "B").symbol()),
                List.of(ref(schema, "GenericExample", "Default").symbol()), TopicSymbol.BindingState.DEFERRED)),
                topic.genericBindings());
    }

    @Test
    void rejectsReferencesFromAnotherRevisionBeforeLookingUpTheId() throws IOException {
        var source = fixture("models/legacy23.ili");
        var schema = compile("Legacy", source);
        var foreignSchema = compile("Legacy", new SourceText(source.id(), new SourceRevision(2), source.text()));
        var plan = ready(schema);
        var foreignPlan = ready(foreignSchema);
        var item = ref(schema, "Legacy", "Data", "Item");
        var foreignItem = ref(foreignSchema, "Legacy", "Data", "Item");
        assertNotEquals(plan.snapshotId(), foreignPlan.snapshotId());
        assertEquals(List.of("label", "count"), slotNames(plan.object(item).orElseThrow()));
        assertThrows(SnapshotMismatchException.class, () -> plan.object(foreignItem));
        assertThrows(SnapshotMismatchException.class,
                () -> plan.object(new SymbolRef(foreignPlan.snapshotId(), item.symbol())));
        assertThrows(SnapshotMismatchException.class, () -> plan.symbol(foreignItem));
        assertThrows(SnapshotMismatchException.class, () -> plan.topic(ref(foreignSchema, "Legacy", "Data")));
        assertThrows(SnapshotMismatchException.class, () -> plan.model(ref(foreignSchema, "Legacy")));
        var foreignCount = assertInstanceOf(AttributeSlot.class, foreignPlan.object(foreignItem).orElseThrow()
                .slots().get(1));
        assertThrows(SnapshotMismatchException.class, () -> plan.type(foreignCount.effectiveType()));
    }

    private static TransferPlan ready(CompiledSchema schema) {
        return assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema)).plan();
    }

    private static List<String> slotNames(ObjectDescriptor object) {
        return object.slots().stream().map(slot -> slot.name().declared().last()).toList();
    }
}
