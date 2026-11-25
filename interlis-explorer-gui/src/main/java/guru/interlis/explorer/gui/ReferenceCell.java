package guru.interlis.explorer.gui;

import guru.interlis.explorer.gui.ExplorerDataset.Link;
import guru.interlis.explorer.gui.ExplorerDataset.LinkedText;

import javax.swing.JLabel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;
import java.util.Optional;

/** Uses the same text layout for drawing links and finding them under the pointer. */
final class ReferenceCell extends JLabel {
    private LinkedText value;
    private TextLayout layout;

    JLabel render(JLabel style, LinkedText text) {
        value = text;
        if (text.links().isEmpty()) {
            layout = null;
            return style;
        }
        setText(text.text());
        setFont(style.getFont());
        setForeground(style.getForeground());
        setBackground(style.getBackground());
        setBorder(style.getBorder());
        setToolTipText(style.getToolTipText());

        var attributed = new AttributedString(text.text());
        attributed.addAttribute(TextAttribute.FONT, getFont());
        for (var link : text.links()) {
            attributed.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, link.start(), link.end());
        }
        layout = new TextLayout(attributed.getIterator(), getFontMetrics(getFont()).getFontRenderContext());
        return this;
    }

    Optional<Link> linkAt(Point point, int height) {
        if (layout == null) {
            return Optional.empty();
        }
        float x = point.x - getInsets().left;
        float y = point.y - baseline(height);
        return value.links().stream()
                .filter(link -> layout.getLogicalHighlightShape(link.start(), link.end()).contains(x, y))
                .findFirst();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        var ink = (Graphics2D) graphics.create();
        try {
            ink.setColor(getBackground());
            ink.fillRect(0, 0, getWidth(), getHeight());
            ink.setColor(getForeground());
            layout.draw(ink, getInsets().left, baseline(getHeight()));
        } finally {
            ink.dispose();
        }
    }

    private float baseline(int height) {
        var insets = getInsets();
        float lineHeight = layout.getAscent() + layout.getDescent() + layout.getLeading();
        return insets.top + (height - insets.top - insets.bottom - lineHeight) / 2 + layout.getAscent();
    }
}
