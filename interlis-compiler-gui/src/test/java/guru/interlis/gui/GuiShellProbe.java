package guru.interlis.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

public final class GuiShellProbe {
    public static void main(String[] args) throws Exception {
        GuiMain.main(new String[0]);
        var frame = new AtomicReference<MainFrame>();
        var capture = new AtomicReference<BufferedImage>();
        try {
            EventQueue.invokeAndWait(() -> {
                var window = Arrays.stream(Window.getWindows()).filter(MainFrame.class::isInstance)
                        .map(MainFrame.class::cast).filter(Window::isShowing).findFirst().orElseThrow();
                frame.set(window);
                require(UIManager.getLookAndFeel().getClass().getName().equals("com.formdev.flatlaf.FlatLightLaf"), "FlatLaf");
                require(window.getName().equals("compiler-window"), "window identity");
                var contents = window.getContentPane().getComponents();
                var tabs = Arrays.stream(contents).filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast)
                        .findFirst().orElseThrow();
                var expected = new String[]{"Check", "Dependencies", "Repositories"};
                require(tabs.getTabCount() == expected.length, "tab count");
                var menu = window.getJMenuBar().getMenu(1);
                for (int i = 0; i < expected.length; i++) {
                    require(tabs.getTitleAt(i).equals(expected[i]), "tab label " + i);
                    menu.getItem(i).doClick();
                    require(tabs.getSelectedIndex() == i, "tab selection " + i);
                }
                tabs.setSelectedIndex(0);
                require(components(window.getContentPane()).anyMatch(component -> component instanceof JLabel label
                        && "session-status".equals(label.getName()) && label.getText().equals("Last run: None")), "session status");
                window.setSize(1000, 700);
                window.validate();
                require(window.getSize().equals(window.getMinimumSize()), "minimum-size layout");
                window.toFront();

            });
            var robot = new Robot();
            robot.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                var pane = frame.get().getRootPane();
                var screenshot = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = screenshot.createGraphics();
                try {
                    pane.printAll(graphics);
                } finally {
                    graphics.dispose();
                }
                capture.set(screenshot);
            });
            require(ImageIO.write(capture.get(), "png", new File(args[0])), "PNG writer");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame.get() != null) {
                    frame.get().dispatchEvent(new WindowEvent(frame.get(), WindowEvent.WINDOW_CLOSING));
                    require(!frame.get().isDisplayable(), "window close");
                }
            });
        }
        System.out.println("GUI_SHELL_PASS tabs=3 menuNavigation=true resize=1000x700 close=disposed java.home="
                + System.getProperty("java.home"));
    }

    private static Stream<Component> components(Container parent) {
        return Arrays.stream(parent.getComponents()).flatMap(component -> component instanceof Container container
                ? Stream.concat(Stream.of(component), components(container)) : Stream.of(component));
    }

    private static void require(boolean condition, String check) {
        if (!condition) {
            throw new AssertionError(check);
        }
    }
}
