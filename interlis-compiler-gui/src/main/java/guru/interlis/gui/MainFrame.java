package guru.interlis.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

final class MainFrame extends JFrame {
    private final JTabbedPane views = new JTabbedPane();

    MainFrame() {
        super("INTERLIS Compiler");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("The compiler window must be created on the event dispatch thread");
        }
        setName("compiler-window");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 700));
        setSize(1280, 860);
        setLocationRelativeTo(null);

        var add = unavailable("Add files…");
        var compile = unavailable("Compile");
        var cancel = unavailable("Cancel");
        var settings = unavailable("Settings…");
        setJMenuBar(menuBar(add, compile, cancel, settings));

        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        toolbar.setName("session-actions");
        for (var action : new Action[]{add, compile, cancel, unavailable("Offline"), settings}) {
            toolbar.add(new JButton(action));
        }
        var inputs = new JLabel("Inputs: 0 files · 0 explicit roots · 0 enabled repositories");
        inputs.setName("session-inputs");
        inputs.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        var header = new JPanel(new BorderLayout());
        header.add(toolbar, BorderLayout.NORTH);
        header.add(inputs, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        views.setName("work-areas");
        addView("Check", "No files have been added.");
        addView("Dependencies", "Dependencies will appear after a compiler run.");
        addView("Repositories", "No repositories are configured.");
        add(views, BorderLayout.CENTER);

        var status = new JLabel("Last run: None");
        status.setName("session-status");
        status.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        var footer = new JPanel(new BorderLayout());
        footer.add(status, BorderLayout.CENTER);
        footer.add(new JButton(unavailable("Show log")), BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private void addView(String title, String message) {
        var panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.add(new JLabel(message), BorderLayout.NORTH);
        views.addTab(title, panel);
    }

    private JMenuBar menuBar(Action add, Action compile, Action cancel, Action settings) {
        var menus = new JMenuBar();
        var file = new JMenu("File");
        file.add(add);
        file.add(compile);
        file.add(cancel);
        file.addSeparator();
        file.add(new AbstractAction("Close") {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        menus.add(file);
        var view = new JMenu("View");
        for (var title : new String[]{"Check", "Dependencies", "Repositories"}) {
            view.add(new AbstractAction(title) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    views.setSelectedIndex(views.indexOfTab(title));
                }
            });
        }
        menus.add(view);
        var preferences = new JMenu("Settings");
        preferences.add(settings);
        menus.add(preferences);
        var help = new JMenu("Help");
        help.add(new AbstractAction("About INTERLIS Compiler") {
            @Override
            public void actionPerformed(ActionEvent event) {
                JOptionPane.showMessageDialog(MainFrame.this, "INTERLIS Compiler", "About",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        menus.add(help);
        return menus;
    }

    private static Action unavailable(String name) {
        var action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
                throw new IllegalStateException("The disabled action was invoked: " + name);
            }
        };
        action.setEnabled(false);
        return action;
    }
}
