package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.compiler.query.RenameRequest;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.syntax.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintPathBindingContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String text(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL M AT \"https://example.org\" VERSION \"1\" = " + body + " END M.";
    }
    private static CompilationOutcome compile(String version, String body) {
        var source = CompilerTestSupport.source("memory:/constraints.ili", text(version, body));
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source),
                List.of(), CompilerOptions.defaults()), TOKEN);
    }
    private static CompilationOutcome.Succeeded ok(String version, String body) {
        var result = compile(version, body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> body + "\n" + (result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics() : result));
    }
    private static CompilationOutcome.Rejected bad(String version, String body) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, compile(version, body), body);
        return result;
    }
    private static List<ConstraintDefinition> constraints(SemanticGraph graph) {
        return graph.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast)
                .map(ConstraintSymbol::definition).toList();
    }
    private static PathExpression path(SemanticGraph graph, ExpressionId id) {
        return assertInstanceOf(PathExpression.class, graph.expression(id).orElseThrow());
    }
    private static Stream<SyntaxNode> nodes(SyntaxNode node) {
        return Stream.concat(Stream.of(node), node.children().stream().flatMap(ConstraintPathBindingContractTest::nodes));
    }
    private static String simple(String rule) {
        return "TOPIC T = STRUCTURE S = code: 0..100; other: TEXT*20; END S; "
                + "CLASS A = detail: S; code: TEXT*20; " + rule + " END A; END T;";
    }

    @Test void globalKeysAndWhereAreBoundInOrderWithSourceReferences() {
        for (String version : List.of("2.3", "2.4")) {
            String body = simple("UNIQUE WHERE detail->code > 0: detail->code, code;");
            var result = ok(version, body); var graph = result.schema().semanticGraph();
            var unique = (UniquenessConstraint) constraints(graph).getFirst();
            assertEquals(2, unique.keys().size()); assertTrue(unique.where().isPresent());
            assertEquals(2, path(graph, unique.keys().getFirst()).path().steps().size());
            assertInstanceOf(NumericType.class, graph.type(path(graph, unique.keys().getFirst()).typeId()).orElseThrow());
            var source = CompilerTestSupport.source("memory:/constraints.ili", text(version, body));
            int offset = source.text().lastIndexOf("detail->code") + 8;
            var query = LanguageQueries.create();
            var handle = query.symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().orElseThrow();
            var attribute = (AttributeSymbol) graph.symbol(handle.symbol()).orElseThrow();
            assertEquals("S", graph.symbol(attribute.owner().orElseThrow()).orElseThrow().name().value());
            var edits = query.planRename(result.analysis(), new RenameRequest(handle, "number", result.analysis().sourceRevisions()), TOKEN);
            assertTrue(edits.complete());
            assertTrue(edits.edits().stream().flatMap(e -> e.edits().stream()).anyMatch(e -> e.range().equals(source.range(offset, offset + 4))));
            var syntax = result.analysis().syntaxSnapshots().values().iterator().next();
            var declaration = nodes(syntax.root()).filter(ConstraintDeclarationSyntax.class::isInstance)
                    .map(ConstraintDeclarationSyntax.class::cast).findFirst().orElseThrow();
            assertEquals(3, declaration.children().size());
            bad(version, simple("UNIQUE detail->missing;"));
            bad(version, simple("UNIQUE WHERE code: detail->code;"));
            bad(version, simple("UNIQUE detail;"));
            ok(version, simple("UNIQUE code;").replace("CLASS A", "CLASS Unrelated = code: BAG OF S; END Unrelated; CLASS A"));
        }
    }

    @Test void localPrefixAndFieldsAreFullyBoundWithoutEmptySuccess() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = STRUCTURE Item = code: 0..10; name: TEXT*10; END Item; "
                    + "STRUCTURE Group = entries: BAG OF Item; END Group; "
                    + "CLASS A = groups: LIST OF Group; active: BOOLEAN; "
                    + "UNIQUE WHERE active: (LOCAL) groups->entries: code, name; END A; END T;";
            var graph = ok(version, body).schema().semanticGraph();
            var unique = (UniquenessConstraint) constraints(graph).getFirst();
            assertFalse(unique.basket()); assertTrue(unique.keys().isEmpty()); assertTrue(unique.where().isPresent());
            var local = unique.localKeys().getFirst();
            assertEquals(2, path(graph, local.prefix()).path().steps().size());
            assertEquals(2, local.fields().size());
            var field = (PathStep.AttributeStep) path(graph, local.fields().getFirst()).path().steps().getFirst();
            assertEquals("Item", graph.symbol(graph.symbol(field.target()).orElseThrow().owner().orElseThrow()).orElseThrow().name().value());
            for (String invalid : List.of(body.replace("groups->entries:", "groups->missing:"),
                    body.replace("code, name;", "unknown;"), body.replace("groups->entries:", "active:"),
                    body.replace("groups->entries: code, name", "groups->entries"))) bad(version, invalid);
        }
        var basket = (UniquenessConstraint) constraints(ok("2.4", simple("UNIQUE (BASKET) Key: code;")).schema().semanticGraph()).getFirst();
        assertTrue(basket.basket()); assertEquals("Key", basket.name().orElseThrow());
        bad("2.3", simple("UNIQUE (BASKET) code;"));
        bad("2.3", simple("UNIQUE Key: code;"));
        bad("2.4", simple("UNIQUE (BASKET) (LOCAL) detail: code;"));
    }

    @Test void uniquenessChecksEveryAccessAndKeepsQualifiedSteps() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = code: TEXT*10; END A; CLASS Owner = END Owner; "
                    + "ASSOCIATION Link = a -- {1} A; owner -- {1} Owner; END Link; "
                    + "CONSTRAINTS OF Owner = UNIQUE owner[Link]->a->code; END; END T;";
            var graph = ok(version, body).schema().semanticGraph();
            var unique = (UniquenessConstraint) constraints(graph).getFirst();
            assertInstanceOf(PathStep.AssociationSourceStep.class, path(graph, unique.keys().getFirst()).path().steps().getFirst());
            bad(version, body.replace("a -- {1}", "a -- {*}"));
            bad(version, body.replace("[Link]", "[Missing]"));
            ok(version, simple("UNIQUE detail->code;").replace("detail: S", "detail: BAG {1} OF S"));
            bad(version, simple("UNIQUE detail->code;").replace("detail: S", "detail: BAG OF S"));
        }
    }

    @Test void existenceRetainsEveryAlternativeAndUsesItsOwnObjectScope() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = STRUCTURE S = code: TEXT*20; END S; CLASS A = detail: S; END A; "
                    + "CLASS B = short: TEXT*10; EXISTENCE CONSTRAINT short REQUIRED IN A:detail->code OR B:short; END B; END T;";
            var result = ok(version, body); var graph = result.schema().semanticGraph();
            var existence = (ExistenceConstraint) constraints(graph).getFirst();
            assertEquals(2, existence.targets().size());
            assertEquals(2, path(graph, existence.targets().getFirst().path()).path().steps().size());
            assertInstanceOf(TextType.class, graph.type(path(graph, existence.source()).typeId()).orElseThrow());
            assertEquals("A", graph.symbol(existence.targets().getFirst().viewable()).orElseThrow().name().value());
            for (String invalid : List.of(body.replace("OR B:short", "OR B:missing"), body.replace("A:detail->code", "Wrong.A:detail->code"))) bad(version, invalid);
            var syntax = result.analysis().syntaxSnapshots().values().iterator().next();
            var payload = nodes(syntax.root()).filter(ExistenceConstraintSyntax.class::isInstance)
                    .map(ExistenceConstraintSyntax.class::cast).findFirst().orElseThrow();
            assertEquals(5, payload.children().size());
        }
    }

    @Test void existenceTopicDependenciesUseBoundIdentitiesAndAcceptSameTopicQualification() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC Other = CLASS A = code: 0..100; END A; END Other; "
                    + "TOPIC T = DEPENDS ON Other; CLASS B = code: 0..10; EXISTENCE CONSTRAINT code REQUIRED IN M.Other.A:code; END B; END T;";
            ok(version, body); bad(version, body.replace("DEPENDS ON Other;", ""));
            bad(version, body.replace("M.Other.A", "Other.A"));
            ok(version, "TOPIC T = CLASS A = code: 0..100; END A; CLASS B = code: 0..10; EXISTENCE CONSTRAINT code REQUIRED IN M.T.A:code; END B; END T;");
            ok(version, body.replace("code: 0..10; EXISTENCE", "code: 0..200; EXISTENCE"));
        }
    }

    @Test void existenceChecksStructureIdentityAndGeometryCoordinateDirection() {
        for (String version : List.of("2.3", "2.4")) {
            String structures = "TOPIC T = STRUCTURE S = code: TEXT; END S; STRUCTURE R EXTENDS S = END R; "
                    + "STRUCTURE Wrong = code: TEXT; END Wrong; CLASS A = value: S; END A; "
                    + "CLASS B = value: R; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;";
            ok(version, structures); bad(version, structures.replace("value: R; EXISTENCE", "value: Wrong; EXISTENCE"));
            String geometry = "TOPIC T = DOMAIN C = COORD 0..100, 0..100; D = COORD 0..100, 0..100, 0..100; "
                    + "CLASS A = coord: C; END A; CLASS B = line: POLYLINE WITH (STRAIGHTS) VERTEX C; "
                    + "EXISTENCE CONSTRAINT line REQUIRED IN A:coord; END B; END T;";
            ok(version, geometry); bad(version, geometry.replace("coord: C", "coord: D"));
            bad(version, geometry.replace("EXISTENCE CONSTRAINT line REQUIRED IN A:coord;", "")
                    .replace("END B; END T;", "END B; CONSTRAINTS OF A = EXISTENCE CONSTRAINT coord REQUIRED IN B:line; END; END T;"));
        }
    }

    @Test void incompatibleResolvedExistenceEnumerationsAreSourceErrors() {
        for (String version : List.of("2.3", "2.4")) {
            for (String type : List.of("(one, two)")) {
                var result = bad(version, "TOPIC T = STRUCTURE S = code: TEXT; END S; CLASS A = value: " + type
                        + "; END A; CLASS B = value: (one, three)"
                        + "; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;");
                assertEquals(java.util.Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
                assertTrue(result.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0005")));
            }
        }
    }

    @Test void invalidIndicesAndScalarContinuationsNeverProduceValidSchema() {
        for (String version : List.of("2.3", "2.4")) {
            for (String rule : List.of("UNIQUE detail[FIRST]->code;", "EXISTENCE CONSTRAINT detail[FIRST]->code REQUIRED IN A:code;")) {
                var rejected = bad(version, simple(rule));
                assertFalse(rejected.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
            }
            bad(version, simple("UNIQUE code->code;"));
            bad(version, simple("EXISTENCE CONSTRAINT THIS REQUIRED IN A:code;"));
        }
    }
    @Test void existenceComparesValuesWithoutWeakeningAssignmentBounds() {
        for (String version : List.of("2.3", "2.4")) {
            for (String type : List.of("0..999", "TEXT*30", "COORD 0..999, 0..999")) {
                String target = type.replace("999", "10").replace("30", "10");
                var graph = ok(version, "TOPIC T = CLASS A = value: " + target + "; END A; CLASS B = value: " + type
                        + "; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;").schema().semanticGraph();
                var existence = (ExistenceConstraint) constraints(graph).getFirst();
                var from = graph.type(path(graph, existence.source()).typeId()).orElseThrow();
                var to = graph.type(path(graph, existence.targets().getFirst().path()).typeId()).orElseThrow();
                assertEquals(RelationDecision.DISALLOWED, guru.interlis.model.internal.TypeCompatibility
                        .assign(from, to, id -> graph.type(id).orElseThrow()).decision());
            }
            String units = "UNIT metre [m]; second [s]; TOPIC T = CLASS A = value: 0..10 [m]; END A; "
                    + "CLASS B = value: 0..999 [s]; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;";
            bad(version, units);
        }
    }

    @Test void existenceDistinguishesAlignmentBlackboxTemporalAndEnumerationValues() {
        for (String version : List.of("2.3", "2.4")) {
            for (String type : List.of("HALIGNMENT", "VALIGNMENT", "BLACKBOX XML", "BLACKBOX BINARY", "(one, two(sub, other))")) {
                String body = "TOPIC T = CLASS A = value: " + type + "; END A; CLASS B = value: " + type
                        + "; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;";
                ok(version, body);
            }
            for (var pair : List.of(List.of("HALIGNMENT", "VALIGNMENT"), List.of("BLACKBOX XML", "BLACKBOX BINARY"))) {
                var rejected = bad(version, "TOPIC T = CLASS A = value: " + pair.getFirst()
                        + "; END A; CLASS B = value: " + pair.getLast()
                        + "; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;");
                assertFalse(rejected.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
            }
        }
        for (String type : List.of("DATE", "TIMEOFDAY", "DATETIME")) {
            String body = "TOPIC T = CLASS A = value: " + type + "; END A; CLASS B = value: " + type
                    + "; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;";
            ok("2.4", body);
            bad("2.4", body.replace("CLASS B = value: " + type, "CLASS B = value: " + (type.equals("DATE") ? "TIMEOFDAY" : "DATE")));
        }
    }

    @Test void listSelectionPreservesTypesReferencesAndNestedContinuation() {
        for (String version : List.of("2.3", "2.4")) {
            for (String selection : List.of("FIRST", "LAST", "1", "3")) {
                String body = "TOPIC T = STRUCTURE Item = code: TEXT*20; END Item; "
                        + "STRUCTURE Group = items: LIST {0..3} OF Item; END Group; "
                        + "CLASS A = value: TEXT*10; END A; CLASS B = groups: LIST OF Group; "
                        + "UNIQUE groups[FIRST]->items[" + selection + "]->code; "
                        + "EXISTENCE CONSTRAINT groups[LAST]->items[" + selection + "]->code REQUIRED IN A:value; END B; END T;";
                var result = ok(version, body); var graph = result.schema().semanticGraph();
                var unique = (UniquenessConstraint) constraints(graph).getFirst();
                var steps = path(graph, unique.keys().getFirst()).path().steps();
                assertEquals(5, steps.size());
                var index = assertInstanceOf(PathStep.IndexStep.class, steps.get(3));
                assertEquals(selection.equals("FIRST") ? PathIndex.Kind.FIRST : selection.equals("LAST") ? PathIndex.Kind.LAST : PathIndex.Kind.POSITION,
                        index.index().kind());
                assertInstanceOf(StructureValueType.class, graph.type(index.resultType()).orElseThrow());
                var source = CompilerTestSupport.source("memory:/constraints.ili", text(version, body));
                int offset = source.text().indexOf("items[" + selection);
                var queries = LanguageQueries.create();
                var handle = queries.symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().orElseThrow();
                assertEquals("items", graph.symbol(handle.symbol()).orElseThrow().name().value());
                var rename = queries.planRename(result.analysis(), new RenameRequest(handle, "entries", result.analysis().sourceRevisions()), TOKEN);
                assertTrue(rename.complete());
                assertTrue(rename.edits().stream().flatMap(e -> e.edits().stream())
                        .anyMatch(e -> e.range().equals(source.range(offset, offset + 5))));
            }
            String base = "TOPIC T = STRUCTURE S = code: TEXT; END S; CLASS A = values: LIST {0..3} OF S; UNIQUE values[%s]->code; END A; END T;";
            for (String index : List.of("0", "4", "999999999999999999999999999999999999")) bad(version, base.formatted(index));
            bad(version, base.formatted("FIRST").replace("LIST", "BAG"));
            bad(version, base.formatted("FIRST").replace("0..3", "0..0"));
            ok(version, base.formatted("999999999999999999999999999999999999").replace("{0..3}", "{*}"));
        }
    }

    @Test void coordinateIndicesRetainExactAxisAndCannotBeUsedAsStructures() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = CLASS A = value: 0..10; END A; "
                    + "CLASS B = point: COORD 0..999, 5..20; UNIQUE point[1], point[2]; "
                    + "EXISTENCE CONSTRAINT point[2] REQUIRED IN A:value; END B; END T;";
            var graph = ok(version, body).schema().semanticGraph();
            var unique = (UniquenessConstraint) constraints(graph).getFirst();
            var axis = assertInstanceOf(NumericType.class, graph.type(path(graph, unique.keys().getLast()).typeId()).orElseThrow());
            assertEquals(new java.math.BigDecimal("5"), axis.range().minimum().orElseThrow().exactValue());
            assertEquals(new java.math.BigDecimal("20"), axis.range().maximum().orElseThrow().exactValue());
            for (String selection : List.of("0", "3", "FIRST", "LAST", "999999999999999999999999999999999"))
                bad(version, body.replace("point[2]", "point[" + selection + "]"));
            bad(version, body.replace("point[2] REQUIRED", "point[2]->value REQUIRED"));
            bad(version, body.replace("point[2] REQUIRED", "point[2]->THIS REQUIRED"));
        }
    }

    @Test void singletonStructureCollectionsAndSelectedElementsKeepTheirIdentity() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = STRUCTURE S = code: TEXT; END S; STRUCTURE Wrong = code: TEXT; END Wrong; "
                    + "CLASS A = value: S; END A; CLASS B = value: LIST {0..2} OF S; "
                    + "EXISTENCE CONSTRAINT value[LAST] REQUIRED IN A:value; END B; END T;";
            ok(version, body);
            bad(version, body.replace("CLASS A = value: S", "CLASS A = value: Wrong"));
            ok(version, body.replace("value[LAST]", "value").replace("LIST {0..2}", "BAG {0..1}"));
            bad(version, body.replace("EXISTENCE CONSTRAINT value[LAST] REQUIRED IN A:value", "UNIQUE value[LAST]"));
        }
    }

}
