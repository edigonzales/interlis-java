package guru.interlis.gui;

import com.formdev.flatlaf.FlatLightLaf;

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;

/** Entry point for the modular Swing compiler application. */
public final class GuiMain {
    private GuiMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("The INTERLIS Compiler GUI does not accept arguments");
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("The INTERLIS Compiler GUI requires a graphical desktop");
        }
        SwingUtilities.invokeAndWait(() -> {
            if (!FlatLightLaf.setup()) {
                throw new IllegalStateException("Could not initialize the FlatLaf look and feel");
            }
            new MainFrame().setVisible(true);
        });
    }
}
