package guru.interlis.projection;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.ImportSite;
import guru.interlis.compiler.ModelLocation;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.ResolutionFingerprint;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.Origin;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramProjectionTest {
    @Test void inlineEnumerationValuesHaveOriginalAnchorsAndInheritedOwners() {
        var schema=compile("INTERLIS 2.4;MODEL DiagramModel AT \"x\" VERSION \"1\"=TOPIC T=CLASS A=flag:(first,group(child));END A;CLASS B EXTENDS A=END B;END T;END DiagramModel.");
        var document=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),DiagramOptions.defaults(new DiagramSelection(schema.rootModels())),CancellationToken.none())).document();
        var a=document.nodes().stream().filter(n->n.label().equals("A")).findFirst().orElseThrow();
        var b=document.nodes().stream().filter(n->n.label().equals("B")).findFirst().orElseThrow();
        var values=a.compartments().stream().filter(c->c.kind()==DiagramCompartment.CompartmentKind.ENUM_VALUES).findFirst().orElseThrow().members();
        assertEquals(List.of("flag.first","flag.group","flag.group.child"),values.stream().map(DiagramMember::label).toList());
        var inherited=b.compartments().stream().filter(c->c.kind()==DiagramCompartment.CompartmentKind.ENUM_VALUES).findFirst().orElseThrow().members();
        for(int i=0;i<values.size();i++){assertTrue(values.get(i).anchor().isPresent());assertTrue(inherited.get(i).inherited());assertFalse(values.get(i).inherited());assertEquals(values.get(i).declaredSymbol(),inherited.get(i).declaredSymbol());assertFalse(values.get(i).id().equals(inherited.get(i).id()));assertEquals(values.get(i).anchor(),inherited.get(i).anchor());}
        assertTrue(new DiagramValidator().validate(document).isEmpty());
    }
    @Test void enumDomainPathsAndSourceAnchorsSurviveProjectionWithoutRendererParsing() {
        var schema=compile("INTERLIS 2.4;MODEL DiagramModel AT \"x\" VERSION \"1\"=DOMAIN State=(first,group(child));DOMAIN CopyState EXTENDS State=(third);END DiagramModel.");
        var document=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),DiagramOptions.defaults(new DiagramSelection(schema.rootModels())),CancellationToken.none())).document();
        for(String name:List.of("State","CopyState")) {
            var node=document.nodes().stream().filter(value->value.label().equals(name)).findFirst().orElseThrow();
            var values=node.compartments().stream().filter(value->value.kind()==DiagramCompartment.CompartmentKind.ENUM_VALUES).findFirst().orElseThrow().members();
            assertEquals(name.equals("State")?List.of("first","group","group.child"):List.of("first","group","group.child","third"),values.stream().map(DiagramMember::label).toList());
            for(var value:values){assertEquals(DiagramMember.MemberKind.ENUM_VALUE,value.kind());assertTrue(value.anchor().isPresent());var declared=schema.semanticGraph().symbol(value.declaredSymbol()).orElseThrow();assertInstanceOf(guru.interlis.model.EnumElementSymbol.class,declared);assertEquals(declared.nameRange(),value.anchor().map(SourceAnchor::nameRange));}
        }
        assertTrue(new DiagramValidator().validate(document).isEmpty());
        var defaults=DiagramOptions.defaults(new DiagramSelection(schema.rootModels()));
        var hidden=new DiagramOptions(defaults.selection(),defaults.scope(),defaults.visibleKinds(),InheritedMembers.HIDE,true,true,true,true,defaults.labelPolicy(),defaults.associationPresentation(),defaults.budget());
        var own=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),hidden,CancellationToken.none())).document();
        var copy=own.nodes().stream().filter(node->node.label().equals("CopyState")).findFirst().orElseThrow();
        assertEquals(List.of("third"),copy.compartments().stream().filter(c->c.kind()==DiagramCompartment.CompartmentKind.ENUM_VALUES).flatMap(c->c.members().stream()).map(DiagramMember::label).toList());
        var bounded=new DiagramOptions(defaults.selection(),defaults.scope(),defaults.visibleKinds(),defaults.inheritedMembers(),true,true,true,true,defaults.labelPolicy(),defaults.associationPresentation(),new ProjectionBudget(500,1000,1));
        var limited=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),bounded,CancellationToken.none())).document();
        assertEquals(ProjectionCompleteness.LIMITED,limited.completeness());assertTrue(limited.nodes().stream().allMatch(node->node.compartments().stream().mapToInt(c->c.members().size()).sum()<=1));

    }
    @Test void declarationModifiersRemainAvailableToTypedRendererAdapters() {
        var schema=compile("INTERLIS 2.4;MODEL DiagramModel AT \"x\" VERSION \"1\"=TOPIC T(ABSTRACT)=CLASS A(ABSTRACT)=value:TEXT;END A;CLASS B(FINAL) EXTENDS A=END B;END T;END DiagramModel.");
        var document=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),DiagramOptions.defaults(new DiagramSelection(schema.rootModels())),CancellationToken.none())).document();
        var a=document.nodes().stream().filter(node -> node.label().equals("A")).findFirst().orElseThrow();
        var b=document.nodes().stream().filter(node -> node.label().equals("B")).findFirst().orElseThrow();
        assertTrue(a.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT));
        assertFalse(b.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT));
        assertTrue(b.modifiers().contains(guru.interlis.model.Modifier.FINAL));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,() -> a.modifiers().clear());
        assertTrue(new DiagramJsonCodec().encode(document).contains("\"modifiers\":[\"ABSTRACT\"]"));
    }
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL DiagramModel AT "https://example.interlis.ch" VERSION "1.0" =
              DOMAIN Code = 0..99;
              TOPIC Data = OID AS INTERLIS.UUIDOID;
                CLASS Base =
                  label : TEXT*20;
                END Base;
                CLASS Derived EXTENDS Base =
                  value : Code;
                END Derived;
                ASSOCIATION Link =
                  fromRole -- {0..1} Derived;
                  toRole -- {1} Base;
                END Link;
              END Data;
            END DiagramModel.
            """;

    @Test void projectsGraphicBasisRuleSignatureAndInheritance() {
        var schema=compile("INTERLIS 2.4; MODEL DiagramModel AT \"https://example.org\" VERSION \"1\" = TOPIC T = CLASS S EXTENDS INTERLIS.SIGN = END S; CLASS C = n: TEXT; END C; END T; SIGN BASKET B ~ T OBJECTS OF S: Dot; TOPIC Maps = DEPENDS ON T; GRAPHIC G BASED ON DiagramModel.T.C = D OF DiagramModel.T.S: (Sign := {Dot}); END G; GRAPHIC H EXTENDS G = D (EXTENDED): (Sign := {Dot}); END H; END Maps; END DiagramModel.");
        var document=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),DiagramOptions.defaults(new DiagramSelection(schema.rootModels())),CancellationToken.none())).document();
        assertTrue(new DiagramValidator().validate(document).isEmpty());
        assertEquals(2,document.nodes().stream().filter(n->n.kind()==DiagramNodeKind.GRAPHIC).count());
        assertEquals(2,document.nodes().stream().filter(n->n.kind()==DiagramNodeKind.DRAWING_RULE).count());
        for(String label:List.of("basis","signature","graphic")) assertTrue(document.edges().stream().anyMatch(e->e.label().equals(label)));
        assertTrue(document.edges().stream().filter(e->e.kind()==DiagramEdge.EdgeKind.INHERITANCE).count()>=2);
    }

    @Test
    void retainsClassToStructureInheritanceAndMandatoryMember() {
        var schema = compile("INTERLIS 2.4; MODEL DiagramModel AT \"https://example.org\" VERSION \"1\" = TOPIC T = STRUCTURE S = flag: BOOLEAN; END S; CLASS C EXTENDS S = flag (EXTENDED): MANDATORY; MANDATORY CONSTRAINT flag; END C; END T; END DiagramModel.");
        var outcome = DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),
                DiagramOptions.defaults(new DiagramSelection(schema.rootModels())), CancellationToken.none());
        var document = assertInstanceOf(DiagramProjectionOutcome.Produced.class, outcome).document();
        assertTrue(new DiagramValidator().validate(document).isEmpty());
        assertTrue(document.nodes().stream().anyMatch(n -> n.label().equals("S")));
        assertTrue(document.nodes().stream().anyMatch(n -> n.label().equals("C")));
        assertEquals(1, document.edges().stream().filter(e -> e.kind() == DiagramEdge.EdgeKind.INHERITANCE).count());
    }

    @Test
    void projectsLineFormTargetAndSegmentInheritance() {
        var schema = compile("INTERLIS 2.4; MODEL DiagramModel AT \"https://example.org\" VERSION \"1\" = STRUCTURE S EXTENDS INTERLIS.LineSegment = END S; LINE FORM Curve: S; END DiagramModel.");
        var document = assertInstanceOf(DiagramProjectionOutcome.Produced.class,
                DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),
                        DiagramOptions.defaults(new DiagramSelection(schema.rootModels())), CancellationToken.none())).document();
        assertTrue(new DiagramValidator().validate(document).isEmpty());
        assertTrue(document.nodes().stream().anyMatch(n -> n.label().equals("Curve") && n.kind() == DiagramNodeKind.LINE_FORM));
        assertTrue(document.edges().stream().anyMatch(e -> e.kind() == DiagramEdge.EdgeKind.REFERENCE && e.label().equals("segment")));
        var base = document.nodes().stream().filter(n -> n.label().equals("INTERLIS.LineSegment"))
                .findFirst().orElseThrow();
        assertEquals(DiagramNodeKind.EXTERNAL_REFERENCE, base.kind());
        assertTrue(document.edges().stream().anyMatch(e -> e.kind() == DiagramEdge.EdgeKind.INHERITANCE
                && e.target().elementId().equals(base.id())));
    }

    @Test
    void projectsMetadataObjectsWithBasketTopicAndClassBindings() {
        var schema = compile("INTERLIS 2.4; MODEL DiagramModel AT \"https://example.org\" VERSION \"1\" = TOPIC T = CLASS CS EXTENDS INTERLIS.SCALSYSTEM = PARAMETER Unit (EXTENDED): NUMERIC [INTERLIS.LENGTH]; END CS; END T; REFSYSTEM BASKET B ~ T OBJECTS OF CS: CH; END DiagramModel.");
        var document = assertInstanceOf(DiagramProjectionOutcome.Produced.class,
                DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),
                        DiagramOptions.defaults(new DiagramSelection(schema.rootModels())), CancellationToken.none())).document();
        assertTrue(new DiagramValidator().validate(document).isEmpty());
        assertTrue(document.nodes().stream().anyMatch(n -> n.label().equals("B") && n.kind() == DiagramNodeKind.METADATA_BASKET));
        assertTrue(document.nodes().stream().anyMatch(n -> n.label().equals("CH") && n.kind() == DiagramNodeKind.METAOBJECT));
        for (String label : List.of("topic", "class", "basket")) assertTrue(document.edges().stream().anyMatch(e -> e.label().equals(label)));
        var node = document.nodes().stream().filter(n -> n.label().equals("CS")).findFirst().orElseThrow();
        assertTrue(node.compartments().stream().filter(c -> c.kind() == DiagramCompartment.CompartmentKind.PARAMETERS)
                .flatMap(c -> c.members().stream()).anyMatch(m -> m.kind() == DiagramMember.MemberKind.PARAMETER && m.overridden() && m.label().equals("Unit")));
    }

    @Test
    void projectsViewBasesInheritanceAndDerivedMembers() {
        var schema = compile("INTERLIS 2.4; MODEL DiagramModel AT \"x\" VERSION \"1\" = TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF a~A; = ALL OF a; END V; VIEW W EXTENDS V = y := a->x; END W; END T; END DiagramModel.");
        var document = assertInstanceOf(DiagramProjectionOutcome.Produced.class,
                DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),
                        DiagramOptions.defaults(new DiagramSelection(schema.rootModels())), CancellationToken.none())).document();
        assertTrue(new DiagramValidator().validate(document).isEmpty());
        assertEquals(2, document.edges().stream().filter(e -> e.kind() == DiagramEdge.EdgeKind.VIEW_DERIVATION).count());
        assertEquals(1, document.edges().stream().filter(e -> e.kind() == DiagramEdge.EdgeKind.INHERITANCE).count());
        var view = document.nodes().stream().filter(n -> n.label().equals("W")).findFirst().orElseThrow();
        assertEquals(2, view.compartments().stream().flatMap(c -> c.members().stream()).count());
    }

    @Test
    void projectsInheritanceAssociationAndMembers() {
        CompiledSchema schema = compile(MODEL);
        SymbolRef model = schema.rootModels().getFirst();
        DiagramOptions options = DiagramOptions.defaults(new DiagramSelection(List.of(model)));
        DiagramProjectionOutcome outcome = DiagramProjectors.create()
                .project(new DiagramInput.FromSchema(schema), options, CancellationToken.none());
        DiagramProjectionOutcome.Produced produced = assertInstanceOf(
                DiagramProjectionOutcome.Produced.class, outcome);
        DiagramDocument document = produced.document();
        assertTrue(new DiagramValidator().validate(document).isEmpty());
        assertTrue(document.nodes().stream().anyMatch(n -> n.label().equals("Derived")));
        assertTrue(document.nodes().stream().anyMatch(n -> n.label().equals("Link")));
        assertEquals(1, document.edges().stream()
                .filter(e -> e.kind() == DiagramEdge.EdgeKind.INHERITANCE).count());
        assertEquals(2, document.edges().stream()
                .filter(e -> e.kind() == DiagramEdge.EdgeKind.ASSOCIATION_ROLE).count());
        DiagramNode derived = document.nodes().stream()
                .filter(n -> n.label().equals("Derived")).findFirst().orElseThrow();
        assertTrue(derived.compartments().stream()
                .flatMap(c -> c.members().stream())
                .anyMatch(m -> m.label().equals("label") && m.inherited()));
        var inherited=derived.compartments().stream().flatMap(c -> c.members().stream()).filter(m -> m.label().equals("label")).findFirst().orElseThrow();
        assertEquals(derived.symbol().orElseThrow().symbol(),inherited.effectiveOwner());
        var memberIds=document.nodes().stream().flatMap(n -> n.compartments().stream()).flatMap(c -> c.members().stream()).map(DiagramMember::id).toList();
        assertEquals(memberIds.size(),new java.util.HashSet<>(memberIds).size());
        assertTrue(document.nodes().stream()
                .anyMatch(n -> n.kind() == DiagramNodeKind.EXTERNAL_REFERENCE
                        || n.label().equals("Base")));
    }


    @Test
    void projectsEveryTargetAndInheritedAssociationRole() {
        var schema = compile(MODEL.replace("fromRole -- {0..1} Derived;", "fromRole -- {0..1} ANYCLASS RESTRICTION (Derived; Base);")
                .replace("END Link;", "END Link; ASSOCIATION Next EXTENDS Link = END Next;"));
        var produced = assertInstanceOf(DiagramProjectionOutcome.Produced.class,
                DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),
                        DiagramOptions.defaults(new DiagramSelection(List.of(schema.rootModels().getFirst()))), CancellationToken.none()));
        var document = produced.document();
        assertTrue(new DiagramValidator().validate(document).isEmpty());
        assertEquals(6, document.edges().stream().filter(e -> e.kind() == DiagramEdge.EdgeKind.ASSOCIATION_ROLE).count());
        assertEquals(2, document.edges().stream().filter(e -> e.kind() == DiagramEdge.EdgeKind.INHERITANCE).count());
        var next = document.nodes().stream().filter(n -> n.label().equals("Next")).findFirst().orElseThrow();
        assertEquals(2, next.compartments().stream().flatMap(c -> c.members().stream()).filter(DiagramMember::inherited).count());
    }

    @Test
    void budgetLimitIsVisibleAsLimitedCompleteness() {
        CompiledSchema schema = compile(MODEL);
        DiagramOptions base = DiagramOptions.defaults(
                new DiagramSelection(List.of(schema.rootModels().getFirst())));
        DiagramOptions limited = new DiagramOptions(base.selection(), base.scope(),
                base.visibleKinds(), base.inheritedMembers(), base.showTypes(),
                base.showRoleCardinalities(), base.showConstraints(), base.showImports(),
                base.labelPolicy(), base.associationPresentation(),
                new ProjectionBudget(1, 100, 100));
        DiagramProjectionOutcome.Produced produced = assertInstanceOf(
                DiagramProjectionOutcome.Produced.class,
                DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),
                        limited, CancellationToken.none()));
        assertEquals(ProjectionCompleteness.LIMITED, produced.document().completeness());
        assertFalse(produced.document().notices().isEmpty());
    }

    @Test
    void mermaidAndPlantUmlAreDeterministicAndEscaped() {
        CompiledSchema schema = compile(MODEL.replace("TEXT*20", "TEXT*20"));
        DiagramOptions options = DiagramOptions.defaults(
                new DiagramSelection(List.of(schema.rootModels().getFirst())));
        DiagramProjectionOutcome outcome = DiagramProjectors.create()
                .project(new DiagramInput.FromSchema(schema), options, CancellationToken.none());
        DiagramDocument document = assertInstanceOf(
                DiagramProjectionOutcome.Produced.class, outcome).document();
        String mermaid = new MermaidRenderer().render(document);
        assertEquals(mermaid, new MermaidRenderer().render(document));
        assertTrue(mermaid.startsWith("classDiagram"));
        assertTrue(mermaid.contains("Derived"));
        assertTrue(mermaid.contains("label : TEXT*20 #123;inherited#125;"), mermaid);
        assertTrue(mermaid.contains("fromRole #123;0..1#125;"), mermaid);
        String plantUml = new PlantUmlRenderer().render(document);
        assertTrue(plantUml.startsWith("@startuml"));
        Map<String, String> names = GraphTextFormatter.safeNames(document);
        String base = names.get(document.nodes().stream().filter(n -> n.label().equals("Base")).findFirst().orElseThrow().id());
        String derived = names.get(document.nodes().stream().filter(n -> n.label().equals("Derived")).findFirst().orElseThrow().id());
        assertTrue(mermaid.contains(derived + " --|> " + base) || mermaid.contains(base + " <|-- " + derived), mermaid);
        assertFalse(mermaid.contains(derived + " <|-- " + base), "Mermaid must not make the superclass derive from its child");
        assertTrue(plantUml.contains(base + " <|-- " + derived), plantUml);
        String json = new DiagramJsonCodec().encode(document);
        assertTrue(json.contains("\"schemaVersion\":\"1.0\""));
        assertTrue(json.contains("\"snapshotId\":"));
        assertTrue(json.contains("\"nodes\":["));
    }

    @Test
    void budgetYieldsLimitedDocument() {
        CompiledSchema schema = compile(MODEL);
        DiagramOptions base = DiagramOptions.defaults(
                new DiagramSelection(List.of(schema.rootModels().getFirst())));
        DiagramOptions limited = new DiagramOptions(base.selection(), base.scope(),
                base.visibleKinds(), base.inheritedMembers(), base.showTypes(),
                base.showRoleCardinalities(), base.showConstraints(), base.showImports(),
                base.labelPolicy(), base.associationPresentation(),
                new ProjectionBudget(1, 1, 1));
        DiagramProjectionOutcome.Produced produced = assertInstanceOf(
                DiagramProjectionOutcome.Produced.class, DiagramProjectors.create()
                        .project(new DiagramInput.FromSchema(schema), limited,
                                CancellationToken.none()));
        assertEquals(ProjectionCompleteness.LIMITED, produced.document().completeness());
        assertTrue(produced.notices().stream()
                .anyMatch(n -> n.code() == ProjectionNoticeCode.PROJECTION_LIMIT_REACHED));
    }


    @Test void sharedBoundTypesProduceDistinctDomainReferenceAndCompositionEdges() {
        var schema=compile("INTERLIS 2.4;MODEL DiagramModel AT \"x\" VERSION \"1\"=DOMAIN Code=TEXT;TOPIC T=OID AS INTERLIS.UUIDOID;STRUCTURE Detail=label:TEXT;END Detail;CLASS Target=END Target;CLASS Owner=code:Code;ref:REFERENCE TO Target;parts:BAG OF Detail;END Owner;END T;END DiagramModel.");
        var document=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),DiagramOptions.defaults(new DiagramSelection(schema.rootModels())),CancellationToken.none())).document();
        for(var kind:List.of(DiagramEdge.EdgeKind.DOMAIN_USE,DiagramEdge.EdgeKind.REFERENCE,DiagramEdge.EdgeKind.STRUCTURE_COMPOSITION))
            assertEquals(1,document.edges().stream().filter(edge->edge.kind()==kind).count(),kind.toString());
        assertTrue(new DiagramValidator().validate(document).isEmpty());
    }

    @Test
    void predefinedDomainsKeepShortTypeLabelsWithoutAutomaticNodes() {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL DiagramModel AT "x" VERSION "1" =
                  IMPORTS INTERLIS;
                  DOMAIN Identifier = TEXT*20; XMLDate = TEXT*10;
                  TOPIC T =
                    CLASS Person =
                      identifier : Identifier;
                      homepage : INTERLIS.URI;
                      created : INTERLIS.XMLDate;
                      active : INTERLIS.BOOLEAN;
                      localDate : XMLDate;
                    END Person;
                  END T;
                END DiagramModel.
                """);
        var selection = new DiagramSelection(schema.rootModels());
        var base = DiagramOptions.defaults(selection);
        var graph = schema.semanticGraph();
        var predefinedDomains = graph.symbols().stream()
                .filter(s -> s instanceof DomainSymbol && s.origin() instanceof Origin.PredefinedOrigin)
                .map(s -> s.id()).toList();
        var expectedLabels = Map.of("identifier", "Identifier", "homepage", "URI", "created", "XMLDate",
                "active", "BOOLEAN", "localDate", "XMLDate");
        for (var scope : DiagramScope.values()) {
            var options = new DiagramOptions(selection, scope, base.visibleKinds(), base.inheritedMembers(),
                    base.showTypes(), base.showRoleCardinalities(), base.showConstraints(), base.showImports(),
                    base.labelPolicy(), base.associationPresentation(), base.budget(), Optional.empty(),
                    scope == DiagramScope.NEIGHBOURHOOD ? OptionalInt.of(1) : OptionalInt.empty());
            var document = assertInstanceOf(DiagramProjectionOutcome.Produced.class, DiagramProjectors.create()
                    .project(new DiagramInput.FromSchema(schema), options, CancellationToken.none())).document();
            assertEquals(ProjectionCompleteness.COMPLETE, document.completeness(), scope.toString());
            assertEquals(List.of(), document.notices(), scope.toString());
            assertTrue(new DiagramValidator().validate(document).isEmpty());
            assertFalse(document.nodes().stream().flatMap(n -> n.symbol().stream())
                    .anyMatch(ref -> predefinedDomains.contains(ref.symbol())), scope.toString());
            var externalNodes = document.nodes().stream()
                    .filter(n -> n.kind() == DiagramNodeKind.EXTERNAL_REFERENCE).toList();
            if (scope == DiagramScope.SELECTED) {
                assertEquals(List.of(), externalNodes);
            }
            assertEquals(!externalNodes.isEmpty(),
                    document.containers().stream().anyMatch(c -> c.id().equals("container-external")), scope.toString());
            assertTrue(document.edges().stream().anyMatch(e -> e.kind() == DiagramEdge.EdgeKind.MODEL_IMPORT));
            var person = document.nodes().stream().filter(n -> n.label().equals("Person")).findFirst().orElseThrow();
            var attributes = person.compartments().stream().filter(c -> c.kind() == DiagramCompartment.CompartmentKind.ATTRIBUTES)
                    .findFirst().orElseThrow().members();
            assertEquals(expectedLabels, attributes.stream().collect(java.util.stream.Collectors.toMap(
                    DiagramMember::label, m -> m.typeLabel().orElseThrow())));
            for (var attribute : attributes) {
                var type = assertInstanceOf(DomainReferenceType.class,
                        graph.declaredType(attribute.declaredSymbol()).flatMap(graph::type).orElseThrow());
                var target = assertInstanceOf(DomainSymbol.class, graph.symbol(type.targetDomain()).orElseThrow());
                var declared = attribute.label().equals("identifier") || attribute.label().equals("localDate");
                if (declared) {
                    assertInstanceOf(Origin.DeclaredOrigin.class, target.origin());
                    var node = document.nodes().stream().filter(n -> n.symbol().orElseThrow().symbol().equals(target.id()))
                            .findFirst().orElseThrow();
                    assertEquals(DiagramNodeKind.DOMAIN, node.kind());
                    assertEquals(target.nameRange().orElseThrow(), node.anchor().orElseThrow().nameRange());
                    var edge = document.edges().stream().filter(e -> e.kind() == DiagramEdge.EdgeKind.DOMAIN_USE
                            && e.label().equals(attribute.label())).findFirst().orElseThrow();
                    assertEquals(person.id(), edge.source().elementId());
                    assertEquals(node.id(), edge.target().elementId());
                    assertEquals(attribute.anchor(), edge.anchor());
                } else {
                    var origin = assertInstanceOf(Origin.PredefinedOrigin.class, target.origin());
                    assertEquals("INTERLIS." + expectedLabels.get(attribute.label()), origin.key());
                    assertFalse(document.edges().stream().anyMatch(e -> e.kind() == DiagramEdge.EdgeKind.DOMAIN_USE
                            && e.label().equals(attribute.label())));
                }
            }
        }
        var predefinedModel = graph.symbols().stream().filter(s -> s.name().value().equals("INTERLIS"))
                .findFirst().orElseThrow();
        var explicit = DiagramOptions.defaults(new DiagramSelection(List.of(
                schema.rootModels().getFirst(), new SymbolRef(schema.snapshotId(), predefinedModel.id()))));
        var document = assertInstanceOf(DiagramProjectionOutcome.Produced.class, DiagramProjectors.create()
                .project(new DiagramInput.FromSchema(schema), explicit, CancellationToken.none())).document();
        assertTrue(document.nodes().stream().anyMatch(n -> n.kind() == DiagramNodeKind.DOMAIN
                && n.label().equals("INTERLIS.URI")));
        assertFalse(document.edges().stream().anyMatch(e -> e.kind() == DiagramEdge.EdgeKind.DOMAIN_USE
                && e.label().equals("homepage")));
        assertTrue(new DiagramValidator().validate(document).isEmpty());
    }

    @Test
    void retainsExternalClassForInheritance() {
        String text = """
                INTERLIS 2.4;
                MODEL Base AT "x" VERSION "1" =
                  TOPIC Common = CLASS Parent = END Parent; END Common;
                END Base.
                MODEL DiagramModel AT "x" VERSION "1" =
                  IMPORTS Base;
                  TOPIC Data =
                    DEPENDS ON Base.Common;
                    CLASS Child EXTENDS Base.Common.Parent = END Child;
                  END Data;
                END DiagramModel.
                """;
        var source = new SourceText(new SourceId("memory:/diagram.ili"), new SourceRevision(1), text);
        int importOffset = text.indexOf("Base;");
        var site = new ImportSite(new SourceRange(source.id(), source.revision(), importOffset, importOffset + 4),
                "DiagramModel", "Base");
        var target = new ModelLocation(source.id(), source.revision(), "Base", Optional.of("1"), text.indexOf("MODEL Base"));
        var binding = new ImportBinding(site, new ImportResolution.Resolved(target));
        var schema = compile(SourceSet.of(Map.of(source.id(), source), List.of(binding), List.of(), ResolutionFingerprint.none()));
        var document = assertInstanceOf(DiagramProjectionOutcome.Produced.class, DiagramProjectors.create()
                .project(new DiagramInput.FromSchema(schema),
                        DiagramOptions.defaults(new DiagramSelection(schema.rootModels())), CancellationToken.none())).document();
        var parent = document.nodes().stream().filter(n -> n.canonicalName().equals("Base.Common.Parent"))
                .findFirst().orElseThrow();
        assertEquals(DiagramNodeKind.EXTERNAL_REFERENCE, parent.kind());
        var child = document.nodes().stream().filter(n -> n.label().equals("Child")).findFirst().orElseThrow();
        assertTrue(document.edges().stream().anyMatch(e -> e.kind() == DiagramEdge.EdgeKind.INHERITANCE
                && e.source().elementId().equals(child.id()) && e.target().elementId().equals(parent.id())));
        assertTrue(new DiagramValidator().validate(document).isEmpty());
    }

    private static CompiledSchema compile(String text) {
        SourceText source = new SourceText(new SourceId("memory:/diagram.ili"),
                new SourceRevision(1), text);
        return compile(SourceSet.of(Map.of(source.id(), source)));
    }

    private static CompiledSchema compile(SourceSet sources) {
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("DiagramModel", Optional.empty(),
                        Optional.empty())),
                sources, CompilerOptions.defaults());
        CompilationOutcome outcome = CompilerEngines.create().compile(request,
                CancellationToken.none());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class, outcome, () -> switch (outcome) {
                    case CompilationOutcome.Rejected rejected -> rejected.rejection()
                            + " " + rejected.analysis().diagnostics();
                    case CompilationOutcome.Failed failed -> failed.error().toString();
                    case CompilationOutcome.Cancelled cancelled -> cancelled.reason().name();
                    case CompilationOutcome.Succeeded ignored -> "succeeded";
                });
        return succeeded.schema();
    }
}
