package guru.interlis.explorer.gui;

import guru.interlis.explorer.ExplorerQueries.Suggestion;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

final class QueryAutoCompletion extends AutoCompletion {
    QueryAutoCompletion(BiFunction<String, Integer, List<Suggestion>> suggestions) {
        super(new Provider(suggestions));
    }

    @Override
    protected void insertCompletion(Completion completion, boolean typedParamListStartChar) {
        if (!(completion instanceof Choice choice)) {
            super.insertCompletion(completion, typedParamListStartChar);
            return;
        }
        hidePopupWindow();
        var editor = (RSyntaxTextArea) getTextComponent();
        if (editor.getCaretPosition() != choice.caret || !editor.getText().equals(choice.query)) {
            return;
        }
        var suggestion = choice.suggestion;
        editor.replaceRange(suggestion.text(), suggestion.start(), suggestion.end());
        editor.setCaretPosition(suggestion.start() + suggestion.text().length());
    }

    private static final class Provider extends DefaultCompletionProvider {
        private final BiFunction<String, Integer, List<Suggestion>> suggestions;

        private Provider(BiFunction<String, Integer, List<Suggestion>> suggestions) {
            this.suggestions = suggestions;
            setAutoActivationRules(true, ".");
        }

        @Override
        public String getAlreadyEnteredText(JTextComponent component) {
            var text = component.getText();
            int caret = component.getCaretPosition();
            var items = suggestions.apply(text, caret);
            return items.isEmpty() ? "" : text.substring(items.getFirst().start(), caret);
        }

        @Override
        protected List<Completion> getCompletionsImpl(JTextComponent component) {
            var text = component.getText();
            int caret = component.getCaretPosition();
            var choices = new ArrayList<Completion>();
            for (var item : suggestions.apply(text, caret)) {
                choices.add(new Choice(this, item, text, caret));
            }
            return choices;
        }
    }

    private static final class Choice extends BasicCompletion {
        private final Suggestion suggestion;
        private final String query;
        private final int caret;

        private Choice(CompletionProvider provider, Suggestion suggestion, String query, int caret) {
            super(provider, suggestion.text(), suggestion.description());
            this.suggestion = suggestion;
            this.query = query;
            this.caret = caret;
        }
    }
}
