package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.compiler.query.RenameRequest;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QualifiedAssociationPathContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String text(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL F AT \"x\" VERSION \"1\" = " + body + " END F.";
    }
    private static CompilationOutcome compile(String version, String body) {
        var source = CompilerTestSupport.source("memory:/qualified.ili", text(version, body));
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"), Map.of(source.id(), source),
                List.of(), CompilerOptions.defaults()), TOKEN);
    }
    private static CompilationOutcome.Succeeded succeeds(String version, String body) {
        var result = compile(version, body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> body + "\n" + (result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics() : result));
    }
    private static void rejects(String version, String body) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, compile(version, body), body);
        assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT), result.toString());
    }
    private static String model(boolean nary, String cardinality, String expression) {
        return """
                TOPIC T = OID AS INTERLIS.UUIDOID;
                  CLASS A = flag: BOOLEAN; END A; CLASS B = END B;
                  CLASS Other = END Other; CLASS Owner = END Owner;
                  FUNCTION one(v: OBJECT OF A): BOOLEAN;
                  FUNCTION many(v: OBJECTS OF A): BOOLEAN;
                  FUNCTION manyB(v: OBJECTS OF B): BOOLEAN;
                  ASSOCIATION Link = a -- {%s} A; owner -- {1} Owner; %s END Link;
                  ASSOCIATION Chain = sender -- {1} A; b -- {1} B; END Chain;
                  CONSTRAINTS OF Owner = MANDATORY CONSTRAINT %s; END;
                END T;
                """.formatted(cardinality, nary ? "other -- {1} Other;" : "", expression);
    }
    private static List<PathStep> steps(SemanticGraph graph) {
        return graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast)
                .flatMap(p -> p.path().steps().stream()).toList();
    }
    private static Symbol named(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(s -> s.name().value().equals(name)).findFirst().orElseThrow();
    }

    @Test void naryAccessDoesNotInferSingletonFromOneRoleCardinality() {
        for (String version : List.of("2.3", "2.4")) {
            var graph = succeeds(version, model(true, "1", "many(a)")).schema().semanticGraph();
            var access = (PathStep.AssociationAccessStep) steps(graph).getFirst();
            assertEquals(Cardinality.unboundedFrom(0), access.cardinality());
            assertTrue(((ObjectArgumentType) graph.type(access.resultType()).orElseThrow()).objects());
            assertEquals(named(graph, "owner").id(), access.sourceRole());
            rejects(version, model(true, "1", "one(a)"));
            rejects(version, model(true, "1", "a->flag"));
            succeeds(version, model(true, "1", "INTERLIS.objectCount(\\a) >= 0"));
        }
    }

    @Test void outgoingQualificationBindsAssociationAndSourceRoleWithExactRanges() {
        for (String version : List.of("2.3", "2.4")) {
            String body = model(true, "1", "many(owner[Link]->a)");
            var result = succeeds(version, body);
            var graph = result.schema().semanticGraph();
            var access = (PathStep.AssociationSourceStep) steps(graph).getFirst();
            assertEquals(named(graph, "Link").id(), access.association());
            assertEquals(named(graph, "owner").id(), access.sourceRole());
            assertTrue(graph.type(access.resultType()).isPresent());
            var source = CompilerTestSupport.source("memory:/qualified.ili", text(version, body));
            var query = LanguageQueries.create();
            int offset = source.text().indexOf("owner[Link]");
            var atRole = query.symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().orElseThrow();
            var atAssociation = query.symbolAt(result.analysis(), source.range(offset + 6, offset + 6), TOKEN).known().orElseThrow();
            assertEquals(access.sourceRole(), atRole.symbol());
            assertEquals(access.association(), atAssociation.symbol());
            var rename = query.planRename(result.analysis(), new RenameRequest(atAssociation, "Relation", result.analysis().sourceRevisions()), TOKEN);
            assertTrue(rename.complete(), rename.toString());
            assertTrue(rename.edits().stream().flatMap(e -> e.edits().stream()).anyMatch(e -> e.range().equals(source.range(offset + 6, offset + 10))));
            assertEquals("owner[Link]", source.text().substring(access.range().startOffset(), access.range().endOffset()));
            for (String invalid : List.of("many(a[Link])", "many(owner[Missing]->a)", "many(owner[Owner]->a)", "many(b[Link])")) {
                rejects(version, model(true, "1", invalid));
            }
            succeeds(version, model(false, "1", "one(owner[Link]->a)"));
            succeeds(version, model(true, "1", "many(owner->a)"));
        }
    }

    @Test void objectSetsRemainSetsAcrossRolesAssociationInstancesAndReferenceAttributes() {
        for (String version : List.of("2.3", "2.4")) {
            for (boolean nary : List.of(false, true)) {
                for (String expression : List.of("manyB(a->b)", "manyB(\\a->a->b)", "many(owner[Link]->a)", "many(a->THIS)")) {
                    succeeds(version, model(nary, "*", expression));
                }
                rejects(version, model(nary, "*", "one(owner[Link]->a)"));
                rejects(version, model(nary, "*", "DEFINED(a->flag)"));
                rejects(version, model(nary, "*", "a->flag == #true"));
            }
        }
        for (String version : List.of("2.3", "2.4")) {
            String union = model(false, "*", "manyB(a->b)")
                    .replace("a -- {*} A", "a -- {*} A OR Other")
                    .replace("sender -- {1} A", "sender -- {1} A OR Other");
            succeeds(version, union);
            rejects(version, union.replace("sender -- {1} A OR Other", "sender -- {1} A"));
        }
        String reference = model(false, "*", "manyB(a->ref)").replace("flag: BOOLEAN;", "flag: BOOLEAN; ref: REFERENCE TO B;");
        var graph = succeeds("2.4", reference).schema().semanticGraph();
        var path = graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast).findFirst().orElseThrow();
        assertTrue(((ObjectArgumentType) graph.type(path.typeId()).orElseThrow()).objects());
        var function = (FunctionSymbol) named(graph, "manyB");
        assertEquals(RelationDecision.ALLOWED, new TypeRelations(graph).canAssign(new TypeRef(graph.snapshotId(), path.typeId()),
                new TypeRef(graph.snapshotId(), function.parameters().getFirst().typeId()), AssignmentContext.NORMAL).known().orElseThrow().decision());
    }

    @Test void qualifiedAccessCannotBypassHidingOrTopicVisibility() {
        for (String version : List.of("2.3", "2.4")) {
            String body = model(false, "1", "many(owner[Narrow]->a)").replace("CONSTRAINTS OF Owner", """
                    CLASS Child EXTENDS Owner = END Child;
                    ASSOCIATION Narrow EXTENDS Link = owner (EXTENDED,HIDING) -- {1} Child; END Narrow;
                    CONSTRAINTS OF Child""");
            succeeds(version, body);
            rejects(version, body.replace("owner[Narrow]", "owner[Link]"));
            rejects(version, model(true, "1", "many(owner[Link]->a)").replace("owner --", "owner (EXTERNAL) --"));
            rejects(version, model(true, "1", "many(owner[Chain]->a)"));
        }
    }

    @Test void ambiguousParticipantsRequireTheActualOutgoingRole() {
        for (String version : List.of("2.3", "2.4")) {
            String body = model(true, "1", "many(owner[Link]->a)").replace("other -- {1} Other", "other -- {1} Owner");
            succeeds(version, body);
            rejects(version, body.replace("owner[Link]->a", "a"));
            succeeds(version, body.replace("owner[Link]->a", "other[Link]->a"));
        }
    }
}
