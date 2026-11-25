package guru.interlis.explorer;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IqlSyntaxCompletionTest {
    @Test
    void parsesContextualFunctionsGroupedKeysAndLiteralPredicates() {
        var query = IqlSyntax.parse("""
                SELECT TID(u) AS id, COUNT(*) AS total
                FROM Unique u
                EXPAND u.entries AS e AT position
                EXPAND e.children AS c
                WHERE OID(u) IN ('A', 'O''Neil') AND ELEMENT_COUNT(e.children) NOT BETWEEN 1 AND 3
                   OR NOT CONTAINS(c.label, '%_')
                GROUP BY TID(u) ORDER BY total DESC LIMIT 9
                """);
        var identity = assertInstanceOf(IqlSyntax.Function.class, query.selections().getFirst().expression());
        assertEquals("TID", identity.name().text());
        assertEquals("u", identity.path().name());
        assertTrue(query.selections().getLast().count());
        assertEquals("total", query.selections().getLast().alias().text());
        assertEquals(List.of("u.entries", "e.children"), query.expansions().stream()
                .map(expansion -> expansion.path().name()).toList());
        assertEquals("position", query.expansions().getFirst().position().text());
        var either = assertInstanceOf(IqlSyntax.Junction.class, query.filter());
        assertEquals("OR", either.operator());
        var both = assertInstanceOf(IqlSyntax.Junction.class, either.left());
        assertEquals("AND", both.operator());
        var in = assertInstanceOf(IqlSyntax.In.class, both.left());
        assertEquals(List.of("A", "O'Neil"), in.options().stream().map(IqlSyntax.Literal::value).toList());
        var between = assertInstanceOf(IqlSyntax.Between.class, both.right());
        assertTrue(between.negated());
        assertEquals("1", between.lower().token().text());
        assertEquals("3", between.upper().token().text());
        var negation = assertInstanceOf(IqlSyntax.Not.class, either.right());
        var text = assertInstanceOf(IqlSyntax.TextSearch.class, negation.operand());
        assertEquals("%_", text.text().value());
        assertFalse(text.prefix());
        assertInstanceOf(IqlSyntax.Function.class, query.group().getFirst());
        assertTrue(query.order().getFirst().descending());
        assertEquals(9, query.limit());

        var identifiers = IqlSyntax.parse("""
                SELECT OID, starts_with, oid.oid FROM Unique AS oid WHERE oid.CONTAINS = TRUE
                """);
        assertTrue(identifiers.selections().stream().allMatch(item -> item.expression() instanceof IqlSyntax.Path));
        assertInstanceOf(IqlSyntax.Comparison.class, identifiers.filter());
    }

    @Test
    void rejectsMalformedLiteralPredicatesAndInvalidDistanceWithExactRanges() {
        String select = "SELECT label FROM Unique u WHERE ";
        String invalid = select + "label IN (u.label)";
        var nonliteral = assertThrows(Iql.QueryException.class, () -> IqlSyntax.parse(invalid));
        assertEquals("u", invalid.substring(nonliteral.start(), nonliteral.end()));
        assertThrows(Iql.QueryException.class, () -> IqlSyntax.parse(select + "label IN ()"));
        assertThrows(Iql.QueryException.class, () -> IqlSyntax.parse(select + "count BETWEEN 2 OR 3"));
        assertThrows(Iql.QueryException.class, () -> IqlSyntax.parse(select + "STARTS_WITH(label, 2)"));
        assertThrows(Iql.QueryException.class, () -> IqlSyntax.parse("SELECT COUNT(*), COUNT(*) FROM Unique"));
        var distance = assertInstanceOf(IqlSyntax.WithinDistance.class,
                IqlSyntax.parse(select + "WITHIN_DISTANCE(u.point, POINT(1, 2.5), 3)").filter());
        assertEquals("POINT", distance.anchor().text());
        assertEquals(1.0, distance.x());
        assertEquals(2.5, distance.y());
        assertEquals(3.0, distance.radius());
        for (String radius : List.of("-1e-400", "1e400")) {
            String input = select + "WITHIN_DISTANCE(u.point, POINT(1, 2), " + radius + ")";
            var failure = assertThrows(Iql.QueryException.class, () -> IqlSyntax.parse(input));
            assertEquals(radius, input.substring(failure.start(), failure.end()));
        }
    }

    @Test
    void completesUniqueSourcesAndReplacesWholeEnumerationSegments() {
        var plan = plan();
        assertEquals(List.of("Unique"), texts(plan, "SELECT * FROM Uni|que"));
        assertEquals(Set.of("QueryCompletion.First.Shared", "QueryCompletion.Second.Shared"),
                matches(plan, "SELECT * FROM Sha|red").stream().map(Iql.Completion::text).collect(Collectors.toSet()));
        assertEquals(List.of("Shared"), texts(plan, "SELECT * FROM QueryCompletion.First.Sha|red"));

        String query = "SELECT * FROM Unique u EXPAND u.entries AS e EXPAND e.children AS c";
        String filter = query + " WHERE c.state NOT IN (#planned, #active.to|morrow)";
        var completion = matches(plan, filter).getFirst();
        assertEquals("today", completion.text());
        String text = filter.replace("|", "");
        assertEquals("tomorrow", text.substring(completion.start(), completion.end()));
        assertEquals(query + " WHERE c.state NOT IN (#planned, #active.today)",
                text.substring(0, completion.start()) + completion.text() + text.substring(completion.end()));
        assertEquals(List.of("active"), texts(plan, query + " WHERE c.state = #ac|tive.today"));
        assertTrue(texts(plan, query + " WHERE c.state IN (pla|nned)").contains("#planned"));
        assertTrue(texts(plan, query + " WHERE c.state = #other.|").isEmpty());
        assertEquals(List.of("today"), texts(plan, query + " WHERE EXISTS(e.children AS x WHERE state = #active.to|)"));
    }

    @Test
    void completesValidTypesAndImmediateAliasesInSequentialExpansion() {
        var plan = plan();
        String query = "SELECT * FROM Unique u";
        String first = query + " EXPAND u.entries AS e AT position";
        assertTrue(texts(plan, query + " EXPAND u.me|mbers AS m").contains("members"));
        assertEquals(List.of("e"), texts(plan, first + " EXPAND |"));
        assertEquals(List.of("children"), texts(plan, first + " EXPAND e.chi|ldren AS c"));
        assertTrue(texts(plan, first + " EXPAND u.|").isEmpty());
        assertTrue(texts(plan, query + " WHERE ELEMENT_COUNT(u.me|mbers) > 0").isEmpty());
        assertEquals(List.of("u"), texts(plan, "SELECT TID(|) FROM Unique u EXPAND u.entries AS e"));
        assertEquals(List.of("total"), texts(plan, "SELECT u.label, COUNT(*) AS total FROM Unique u "
                + "GROUP BY u.label ORDER BY to|tal DESC"));
        assertEquals(List.of("POINT"), texts(plan, query + " WHERE WITHIN_DISTANCE(u.point, POI|NT(1, 2), 3)"));
    }

    private static List<String> texts(TransferPlan plan, String marked) {
        return matches(plan, marked).stream().map(Iql.Completion::text).toList();
    }

    private static List<Iql.Completion> matches(TransferPlan plan, String marked) {
        return Iql.complete(plan, marked.replace("|", ""), marked.indexOf('|'));
    }

    private static TransferPlan plan() {
        String name = "QueryCompletion";
        var source = new SourceText(new SourceId("fixture:/QueryCompletion.ili"), new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL QueryCompletion AT "https://example.org" VERSION "1" =
                  TOPIC First =
                    STRUCTURE Child =
                      label : TEXT;
                      state : (planned, active(today, later));
                    END Child;
                    STRUCTURE Entry =
                      children : BAG {0..*} OF Child;
                    END Entry;
                    CLASS Unique =
                      label : TEXT;
                      entries : LIST {0..*} OF Entry;
                    END Unique;
                    CLASS Shared =
                      label : TEXT;
                    END Shared;
                    ASSOCIATION Grouping =
                      owner -- {0..1} Unique;
                      members -- {0..*} Shared;
                    END Grouping;
                  END First;
                  TOPIC Second =
                    CLASS Shared =
                      label : TEXT;
                    END Shared;
                  END Second;
                END QueryCompletion.
                """);
        var request = new CompilationRequest(List.of(new ModelSelector.ByName(name, Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        var compiled = assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        return assertInstanceOf(TransferPlanningResult.Ready.class,
                TransferPlanner.plan(compiled.schema(), TransferPlanner.Names.DECLARED)).plan();
    }
}
