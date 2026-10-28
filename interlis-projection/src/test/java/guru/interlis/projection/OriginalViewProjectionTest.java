package guru.interlis.projection;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The unchanged upstream B1–B3 cases remain useful through the diagram consumer. */
class OriginalViewProjectionTest {
    private static final Path DIRECTORY = Path.of("../testdata/conformance/b1-b3-acceptance");
    private static final CancellationToken TOKEN = CancellationToken.none();

    @Test void allOriginalViewsRetainMembersAndSupportedRelationsInTheDiagram() throws Exception {
        var pins = new Properties();
        try (var reader = Files.newBufferedReader(DIRECTORY.resolve("sources.properties"), StandardCharsets.UTF_8)) {
            pins.load(reader);
        }
        assertEquals(19, pins.size(), "Every catalogued B1–B3 original must participate");
        for (String testId : pins.stringPropertyNames().stream().sorted().toList()) {
            var entry = pins.getProperty(testId).split("\\|", 3);
            var bytes = Files.readAllBytes(DIRECTORY.resolve(entry[0]));
            assertEquals(entry[1], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), testId + " original source pin");
            var source = new SourceText(new SourceId("memory:/original-projection/" + entry[0]),
                    new SourceRevision(1), new String(bytes, StandardCharsets.UTF_8));
            var syntax = SyntaxServices.create().parse(source, ParseOptions.defaults(ParseMode.EDITOR), TOKEN);
            var selectors = syntax.root().models().stream().map(model -> (ModelSelector) new ModelSelector.ByName(
                    model.name(), Optional.empty(), Optional.empty())).toList();
            var request = new CompilationRequest(selectors, SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
            var outcome = CompilerEngines.create().compile(request, TOKEN);
            var succeeded = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                    () -> testId + ": " + (outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics() : outcome));
            var schema = succeeded.schema();
            var graph = schema.semanticGraph();
            var document = assertInstanceOf(DiagramProjectionOutcome.Produced.class,
                    DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),
                            DiagramOptions.defaults(new DiagramSelection(schema.rootModels())), TOKEN), testId).document();
            assertEquals(ProjectionCompleteness.COMPLETE, document.completeness(), testId);
            assertEquals(schema.snapshotId(), document.snapshotId(), testId);
            assertEquals(schema.sourceRevisions(), document.sourceRevisions(), testId);
            assertTrue(document.notices().isEmpty(), () -> testId + ": " + document.notices());
            assertTrue(new DiagramValidator().validate(document).isEmpty(), testId);
            int expectedDerivations = 0;
            for (var symbol : graph.symbols()) {
                if (symbol instanceof ViewSymbol view) {
                    var node = node(document, view.id(), testId);
                    assertEquals(DiagramNodeKind.VIEW, node.kind(), testId);
                    assertEquals(DiagramNodeStatus.RESOLVED, node.status(), testId);
                    assertEquals(view.name().value(), node.label(), testId);
                    assertEquals(view.nameRange().orElseThrow(), node.anchor().orElseThrow().nameRange(), testId);
                    assertEquals(schema.snapshotId(), node.anchor().orElseThrow().snapshotId(), testId);
                    var attributes = node.compartments().stream()
                            .filter(c -> c.kind() == DiagramCompartment.CompartmentKind.ATTRIBUTES)
                            .flatMap(c -> c.members().stream()).toList();
                    assertEquals(view.members(), attributes.stream().map(DiagramMember::declaredSymbol).toList(), testId + " attribute identity/order: " + view.name());
                    var effective = schema.queries().effectiveMembers(new SymbolRef(schema.snapshotId(), view.id())).known().orElseThrow();
                    for (int i = 0; i < attributes.size(); i++) {
                        var member = attributes.get(i);
                        var attribute = (AttributeSymbol) graph.symbol(view.members().get(i)).orElseThrow();
                        assertEquals(DiagramMember.MemberKind.ATTRIBUTE, member.kind(), testId);
                        assertEquals(attribute.name().value(), member.label(), testId);
                        assertTrue(member.typeLabel().isPresent(), testId + " type of " + attribute.name());
                        assertEquals(effective.get(i).origin() != EffectiveMemberOrigin.LOCAL, member.inherited(), testId);
                        assertEquals(effective.get(i).overridden(), member.overridden(), testId);
                    }
                    var constraints = node.compartments().stream()
                            .filter(c -> c.kind() == DiagramCompartment.CompartmentKind.CONSTRAINTS)
                            .flatMap(c -> c.members().stream()).toList();
                    assertEquals(view.constraints(), constraints.stream().map(DiagramMember::declaredSymbol).toList(), testId + " constraint identity/order");
                    if (view.baseView().isPresent()) {
                        relation(document, node, node(document, view.baseView().orElseThrow(), testId),
                                DiagramEdge.EdgeKind.INHERITANCE, Optional.empty(), testId);
                    }
                    for (var basis : view.bases()) {
                        derivation(document, graph, node, basis, testId);
                        expectedDerivations++;
                    }
                    if (view.formation().orElse(null) instanceof ViewSymbol.Inspection inspection) {
                        // The diagram contract depicts the decomposition's source basis. The inspected
                        // element and parent remain distinct public semantic identities, not invented edges.
                        var basis = (ViewSymbol.Base) graph.symbol(inspection.base()).orElseThrow();
                        relation(document, node, node(document, basis.target(), testId),
                                DiagramEdge.EdgeKind.VIEW_DERIVATION, Optional.of(basis.id()), testId);
                        assertTrue(graph.symbol(inspection.element()).isPresent(), testId);
                        assertTrue(graph.symbol(inspection.parent()).isPresent(), testId);
                    }
                } else if (symbol instanceof AssociationSymbol association && association.derivedFrom().isPresent()) {
                    derivation(document, graph, node(document, association.id(), testId), association.derivedFrom().orElseThrow(), testId);
                    expectedDerivations++;
                }
            }
            assertEquals(expectedDerivations, document.edges().stream().filter(e -> e.kind() == DiagramEdge.EdgeKind.VIEW_DERIVATION).count(), testId + " derivation count");
        }
    }

    private static DiagramNode node(DiagramDocument document, SymbolId id, String testId) {
        return document.nodes().stream().filter(n -> n.symbol().map(SymbolRef::symbol).filter(id::equals).isPresent())
                .findFirst().orElseThrow(() -> new AssertionError(testId + ": missing diagram node " + id));
    }

    private static void derivation(DiagramDocument document, SemanticGraph graph, DiagramNode source,
            SymbolId basisId, String testId) {
        var basis = assertInstanceOf(ViewSymbol.Base.class, graph.symbol(basisId).orElseThrow());
        var edge = relation(document, source, node(document, basis.target(), testId),
                DiagramEdge.EdgeKind.VIEW_DERIVATION, Optional.of(basisId), testId);
        assertEquals(basis.name().value(), edge.label(), testId);
        assertEquals(Optional.of(new SymbolRef(document.snapshotId(), basisId)), edge.origin(), testId);
    }

    private static DiagramEdge relation(DiagramDocument document, DiagramNode source, DiagramNode target,
            DiagramEdge.EdgeKind kind, Optional<SymbolId> origin, String testId) {
        return document.edges().stream().filter(e -> e.kind() == kind && e.source().elementId().equals(source.id())
                        && e.target().elementId().equals(target.id()) && e.origin().map(SymbolRef::symbol).equals(origin))
                .findFirst().orElseThrow(() -> new AssertionError(testId + ": missing " + kind + " from " + source.label() + " to " + target.label()));
    }
}
