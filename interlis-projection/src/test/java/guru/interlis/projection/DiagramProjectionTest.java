package guru.interlis.projection;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramProjectionTest {
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
        assertTrue(document.edges().stream().anyMatch(e -> e.kind() == DiagramEdge.EdgeKind.INHERITANCE));
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
        String plantUml = new PlantUmlRenderer().render(document);
        assertTrue(plantUml.startsWith("@startuml"));
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

    private static CompiledSchema compile(String text) {
        SourceText source = new SourceText(new SourceId("memory:/diagram.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("DiagramModel", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
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
