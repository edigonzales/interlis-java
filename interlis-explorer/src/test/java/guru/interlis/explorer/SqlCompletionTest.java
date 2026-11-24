package guru.interlis.explorer;

import guru.interlis.explorer.ExplorerQueries.SqlCatalog;
import guru.interlis.explorer.ExplorerQueries.SqlCatalog.Table;
import guru.interlis.explorer.ExplorerQueries.Suggestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlCompletionTest {
    private static final SqlCatalog CATALOG = new SqlCatalog(Map.of("object", List.of("tid", "type_name")),
            List.of("count"), List.of(
                    new Table("transfer", "main", "object", List.of("tid", "type_name")),
                    new Table("transfer", "main", "basket", List.of("bid", "position")),
                    new Table("readonly db", "model data", "table.with.dot", List.of("object id", "say\"what")),
                    new Table("transfer", "main", "Ämter", List.of("Amtsname")),
                    new Table("transfer", "main", "ämter", List.of("Unteramt"))));

    @Test
    void resolvesQualifiedCatalogAndTableNamesWithFullTokenReplacement() {
        assertEquals("SELECT ti FROM transfer.main.object", insert("SELECT ti FROM transfer.main.ob|x", "object"));
        assertEquals("SELECT * FROM \"readonly db\".\"model data\".\"table.with.dot\"",
                insert("SELECT * FROM \"readonly db\".\"mo|del typo\".\"table.with.dot\"", "\"model data\""));
        assertEquals("SELECT * FROM \"readonly db\".\"model data\".\"table.with.dot\"",
                insert("SELECT * FROM \"readonly db\".\"model data\".tab|letypo", "\"table.with.dot\""));
        assertEquals("SELECT transfer.main.object.tid FROM transfer.main.object",
                insert("SELECT transfer.main.object.ti|xd FROM transfer.main.object", "tid"));
        assertEquals("SELECT transfer.object.tid FROM transfer.object",
                insert("SELECT transfer.object.ti| FROM transfer.object", "tid"));
    }

    @Test
    void resolvesQuotedAliasesAndKeepsOrAddsIdentifierQuoting() {
        assertEquals("SELECT \"o x\".\"object id\" FROM \"readonly db\".\"model data\".\"table.with.dot\" \"O X\"",
                insert("SELECT \"o x\".ob|xy FROM \"readonly db\".\"model data\".\"table.with.dot\" \"O X\"",
                        "\"object id\""));
        assertEquals("SELECT b.\"say\"\"what\" FROM object o JOIN \"readonly db\".\"model data\".\"table.with.dot\" AS b ON true",
                insert("SELECT b.\"say\"\"w|rong\" FROM object o JOIN \"readonly db\".\"model data\".\"table.with.dot\" AS b ON true",
                        "\"say\"\"what\""));
        assertEquals(List.of("Amtsname"), suggestions("SELECT \"Ämter\".| FROM \"Ämter\""));
    }

    @Test
    void usesExplicitAndInferredCteColumnsInScope() {
        var sql = """
                WITH stage("row id") AS (SELECT tid FROM object),
                shape AS (SELECT s.*, b.bid AS basket_id, count(*) total
                          FROM stage s CROSS JOIN basket b GROUP BY ALL)
                SELECT result.| FROM shape AS result
                """;
        assertEquals(List.of("\"row id\"", "basket_id", "total"), suggestions(sql));
        assertEquals(List.of("tid", "type_name"), suggestions("WITH items AS (SELECT * FROM object) SELECT items.| FROM items"));
        assertEquals(List.of("tid"), suggestions("WITH items AS (SELECT o.tid FROM object o) SELECT items.| FROM items"));
        assertEquals(List.of("my_column"), suggestions("WITH object AS (SELECT 1 AS my_column) SELECT object.| FROM object"));
        assertEquals(List.of("tid", "type_name"),
                suggestions("WITH object AS (SELECT 1 AS my_column) SELECT main.object.| FROM main.object"));
        assertTrue(suggestions("WITH stage AS (SELECT 1 AS x) SELECT * FROM st|ge").contains("stage"));
    }

    @Test
    void skipsCommentsAndLiteralsWithoutInventingBindings() {
        assertTrue(suggestions("SELECT tid -- artificial.|\nFROM object").isEmpty());
        assertTrue(suggestions("SELECT /* outer /* nested */ b.| */ tid FROM object").isEmpty());
        assertTrue(suggestions("SELECT 'JOIN basket o.|' FROM object o").isEmpty());
        assertTrue(suggestions("SELECT $$FROM basket o.|$$ FROM object o").isEmpty());
        assertTrue(suggestions("SELECT $test$JOIN basket o.|$test$ FROM object o").isEmpty());
        assertTrue(suggestions("SELECT E'\\'JOIN basket o.|' FROM object o").isEmpty());
        assertEquals(List.of("tid", "type_name"),
                suggestions("SELECT o.|, 'JOIN basket o' FROM object o /* JOIN basket o */"));
        assertEquals(List.of("tid", "type_name"), suggestions("SELECT o.| FROM object -- alias follows\n o"));
    }

    @Test
    void keepsNestedBindingsAndStatementsLocal() {
        assertEquals(List.of("bid", "position"),
                suggestions("SELECT (SELECT nested.| FROM basket nested) FROM object outer_object"));
        assertEquals(List.of("tid", "type_name"),
                suggestions("SELECT a.tid FROM basket a; SELECT a.| FROM object a"));
        assertEquals(List.of("tid", "type_name"), suggestions("WITH q AS (SELECT a.| FROM object a) SELECT * FROM q"));
    }

    private static List<String> suggestions(String marked) {
        var caret = marked.indexOf('|');
        return CATALOG.complete(marked.replace("|", ""), caret).stream().map(Suggestion::text).toList();
    }

    private static String insert(String marked, String candidate) {
        int caret = marked.indexOf('|');
        String source = marked.replace("|", "");
        var suggestion = CATALOG.complete(source, caret).stream().filter(choice -> choice.text().equals(candidate))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + candidate + " in " + suggestions(marked)));
        return source.substring(0, suggestion.start()) + suggestion.text() + source.substring(suggestion.end());
    }
}
