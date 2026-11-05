package guru.interlis.gui;

import guru.interlis.gui.presentation.DiagnosticPresenter;
import guru.interlis.gui.presentation.SourcePreviewModel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

final class SourcePreviewPanel extends JPanel {
    private static final DefaultHighlighter.DefaultHighlightPainter SOURCE_MARK =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(0xe9bd62)) {
                @Override
                public Color getColor() {
                    Color selection = UIManager.getColor("TextArea.selectionBackground");
                    return selection == null ? super.getColor() : selection;
                }
            };
    private final PreviewText text = new PreviewText();
    private final JTextArea gutter = new JTextArea();
    private final JLabel location = new JLabel();
    private final JScrollPane scroll = new JScrollPane(text);

    SourcePreviewPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Source preview (read-only)"));
        setMinimumSize(new java.awt.Dimension(160, 100));
        location.setName("source-location");
        location.putClientProperty("html.disable", true);
        text.setName("source-preview");
        text.setEditable(false);
        var font = new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize());
        text.setFont(font);
        gutter.setFont(font);
        gutter.setName("source-line-numbers");
        gutter.setEditable(false);
        gutter.setFocusable(false);
        gutter.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 8));
        gutter.setBackground(getBackground());
        scroll.setRowHeaderView(gutter);
        add(location, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        clear("Select a source location.");
    }

    void show(SourcePreviewModel model) {
        String span = model.highlight().map(highlight -> " · UTF-16 " + highlight.original().startOffset()
                + "–" + highlight.original().endOffset() + (highlight.clipped() ? " (partially shown)" : "")).orElse("");
        String description = " · revision " + model.source().revision().value() + span + (model.clipped() ? " · excerpt" : "");
        location.setText(DiagnosticPresenter.shortSource(model.source().id()) + description);
        location.setToolTipText(DiagnosticPresenter.displaySource(model.source().id()) + description);
        text.setText(model.displayText());
        text.getHighlighter().removeAllHighlights();
        text.marker = -1;
        int lineCount = text.getLineCount();
        int last = model.firstLine() + lineCount - 1;
        int digits = Integer.toString(last).length();
        var numbers = new StringBuilder();
        for (int line = model.firstLine(); line <= last; line++) {
            String value = Integer.toString(line);
            if (!numbers.isEmpty()) {
                numbers.append('\n');
            }
            numbers.append(" ".repeat(digits - value.length())).append(value);
        }
        gutter.setText(numbers.toString());
        text.setCaretPosition(0);
        model.highlight().ifPresent(highlight -> {
            try {
                if (highlight.start() == highlight.end()) {
                    text.marker = highlight.start();
                } else {
                    text.getHighlighter().addHighlight(highlight.start(), highlight.end(), SOURCE_MARK);
                }
                text.setCaretPosition(highlight.start());
                var shape = text.modelToView2D(highlight.start());
                if (shape != null) {
                    text.scrollRectToVisible(shape.getBounds());
                }
            } catch (BadLocationException failure) {
                throw new IllegalStateException("Invalid preview coordinates", failure);
            }
        });
        text.repaint();
    }

    void clear(String reason) {
        location.setText(reason);
        location.setToolTipText(null);
        text.setText("");
        text.getHighlighter().removeAllHighlights();
        text.marker = -1;
        gutter.setText("");
        text.repaint();
    }

    private static final class PreviewText extends JTextArea {
        private int marker = -1;

        @Override
        public void updateUI() {
            var previous = getHighlighter();
            int start = 0;
            int end = 0;
            if (previous != null) {
                for (var highlight : previous.getHighlights()) {
                    if (highlight.getPainter() == SOURCE_MARK) {
                        start = highlight.getStartOffset();
                        end = highlight.getEndOffset();
                        break;
                    }
                }
            }
            super.updateUI();
            if (start < end) {
                try {
                    getHighlighter().addHighlight(start, end, SOURCE_MARK);
                } catch (BadLocationException failure) {
                    throw new IllegalStateException("Invalid preview coordinates", failure);
                }
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (marker >= 0) {
                try {
                    var shape = modelToView2D(marker);
                    if (shape != null) {
                        var area = shape.getBounds();
                        Color accent = UIManager.getColor("Component.focusColor");
                        graphics.setColor(accent == null ? new Color(0xc45516) : accent);
                        graphics.fillRect(area.x, area.y, 2, area.height);
                    }
                } catch (BadLocationException failure) {
                    throw new IllegalStateException("Invalid preview marker", failure);
                }
            }
        }
    }
}
