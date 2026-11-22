package guru.interlis.explorer.gui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Starts the INTERLIS Object Explorer on the Swing event dispatch thread. */
public final class ExplorerMain {
    private ExplorerMain() {}

    /** Opens the Explorer's main window. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            UIManager.put("SplitPaneDivider.style", "plain");
            new ExplorerFrame().setVisible(true);
        });
    }
}
