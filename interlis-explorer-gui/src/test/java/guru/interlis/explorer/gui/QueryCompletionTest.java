package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.explorer.ExplorerQueries;
import guru.interlis.explorer.ExplorerQueries.SqlCatalog;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryCompletionTest {
    @Test
    void acceptsIqlAtTheStartMiddleAndEndOfAnIdentifier() throws Exception {
        var fixtures = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");
        try (var dataset = ExplorerDataset.open(List.of(fixtures.resolve("RelationsDemo.ili")),
                fixtures.resolve("relations-demo.xtf"), CancellationToken.none())) {
            var queries = new ExplorerQueries(dataset.store());
            SwingUtilities.invokeAndWait(() -> {
                var editor = new RSyntaxTextArea();
                var completion = new QueryAutoCompletion(queries::completeIql);
                completion.install(editor);
                try {
                    var suffix = ", i.title FROM RelationsDemo.Data.Item AS i;";
                    var expected = "SELECT i.title" + suffix;
                    accept(editor, completion, "SELECT i.|titleOld" + suffix, "title", expected);
                    accept(editor, completion, "SELECT i.ti|tleOld" + suffix, "title", expected);
                    accept(editor, completion, "SELECT i.ti|" + suffix, "title", expected);
                } finally {
                    completion.uninstall();
                }
            });
        }
    }

    @Test
    void acceptsSqlInTheMiddleOfAQuotedIdentifierAndAtTheEndOfAWord() throws Exception {
        var catalog = new SqlCatalog(Map.of("object", List.of("tid", "type_name")), List.of("count"));
        SwingUtilities.invokeAndWait(() -> {
            var editor = new RSyntaxTextArea();
            var completion = new QueryAutoCompletion(catalog::complete);
            completion.install(editor);
            try {
                var suffix = ", o.type_name FROM object AS o;";
                accept(editor, completion, "SELECT o.\"ti|dOld\"" + suffix, "\"tid\"", "SELECT o.\"tid\"" + suffix);
                accept(editor, completion, "SELECT o.ti|" + suffix, "tid", "SELECT o.tid" + suffix);
            } finally {
                completion.uninstall();
            }
        });
    }

    @Test
    void discardsACompletionWhenTheTextHasChanged() throws Exception {
        var catalog = new SqlCatalog(Map.of("object", List.of("tid")), List.of());
        SwingUtilities.invokeAndWait(() -> {
            var editor = new RSyntaxTextArea();
            var completion = new QueryAutoCompletion(catalog::complete);
            completion.install(editor);
            try {
                String text = "SELECT o.ti FROM object AS o";
                int caret = text.indexOf(" FROM");
                editor.setText(text);
                editor.setCaretPosition(caret);
                var item = choice(editor, completion, "tid");
                var updated = text + " WHERE o.tid = 'example'";
                editor.append(updated.substring(text.length()));
                editor.setCaretPosition(caret);
                completion.insertCompletion(item, false);
                assertEquals(updated, editor.getText());
            } finally {
                completion.uninstall();
            }
        });
    }

    private static void accept(RSyntaxTextArea editor, QueryAutoCompletion completion,
                               String marked, String replacement, String expected) {
        int caret = marked.indexOf('|');
        String input = marked.substring(0, caret) + marked.substring(caret + 1);
        editor.setText(input);
        editor.setCaretPosition(caret);
        editor.discardAllEdits();

        completion.insertCompletion(choice(editor, completion, replacement), false);

        assertEquals(expected, editor.getText());
        assertEquals(expected.indexOf(replacement) + replacement.length(), editor.getCaretPosition());
        editor.undoLastAction();
        assertEquals(input, editor.getText());
    }

    private static Completion choice(RSyntaxTextArea editor, QueryAutoCompletion completion, String replacement) {
        return completion.getCompletionProvider().getCompletions(editor).stream()
                .filter(item -> item.getReplacementText().equals(replacement)).findFirst().orElseThrow();
    }
}
