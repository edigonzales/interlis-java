package guru.interlis.gui;

import guru.interlis.compiler.WarningPolicy;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.gui.application.SessionOptions;
import guru.interlis.gui.infrastructure.GuiSettings;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositoryProxyConfiguration;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

final class SettingsDialog {
    record Choice(GuiSettings.Theme theme, SessionOptions options) {
    }

    private enum ProxyMode {
        SYSTEM("System"), DIRECT("Direct"), HTTP("Explicit HTTP proxy");
        private final String label;
        ProxyMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private SettingsDialog() {
    }

    static Optional<Choice> edit(Component parent, GuiSettings.Theme theme, SessionOptions options, Path suggestedCache) {
        var themes = new JComboBox<>(GuiSettings.Theme.values());
        themes.setSelectedItem(theme);
        themes.setName("settings-theme");
        var languages = new JComboBox<>(new String[]{"English", "German"});
        languages.setSelectedIndex(options.diagnosticLanguage() == DiagnosticLanguage.EN ? 0 : 1);
        var warnings = new JCheckBox("Reject warnings", options.warnings() == WarningPolicy.REJECT_WARNINGS);
        var policy = options.repositories().fetchPolicy();
        var offline = new JCheckBox("Offline", policy.offline());
        var cache = new JCheckBox("Use persistent cache", options.cache().directory().isPresent());
        var cachePath = new JTextField(options.cache().directory().orElse(suggestedCache).toString(), 32);
        cachePath.setEnabled(cache.isSelected());
        cache.addActionListener(event -> cachePath.setEnabled(cache.isSelected()));
        var proxies = new JComboBox<>(ProxyMode.values());
        var proxyHost = new JTextField(24);
        var proxyPort = new JTextField(8);
        switch (options.proxy()) {
            case RepositoryProxyConfiguration.SystemDefault ignored -> proxies.setSelectedItem(ProxyMode.SYSTEM);
            case RepositoryProxyConfiguration.Direct ignored -> proxies.setSelectedItem(ProxyMode.DIRECT);
            case RepositoryProxyConfiguration.HttpProxy(var host, var port) -> {
                proxies.setSelectedItem(ProxyMode.HTTP);
                proxyHost.setText(host);
                proxyPort.setText(Integer.toString(port));
            }
        }
        Runnable enableProxy = () -> {
            boolean explicit = proxies.getSelectedItem() == ProxyMode.HTTP;
            proxyHost.setEnabled(explicit);
            proxyPort.setEnabled(explicit);
        };
        proxies.addActionListener(event -> enableProxy.run());
        enableProxy.run();
        var connect = new JTextField(Long.toString(policy.connectTimeout().toMillis()), 8);
        var read = new JTextField(Long.toString(policy.readTimeout().toMillis()), 8);
        var request = new JTextField(policy.requestTimeout().map(value -> Long.toString(value.toMillis())).orElse(""), 8);

        var panel = new JPanel(new GridLayout(0, 2, 10, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 8, 8, 8));
        field(panel, "Theme", themes);
        field(panel, "Diagnostic language", languages);
        field(panel, "Warnings", warnings);
        field(panel, "Network", offline);
        field(panel, "Cache", cache);
        field(panel, "Cache directory", cachePath);
        field(panel, "Proxy", proxies);
        field(panel, "Proxy host", proxyHost);
        field(panel, "Proxy port", proxyPort);
        field(panel, "Connect timeout (ms)", connect);
        field(panel, "Read timeout (ms)", read);
        field(panel, "Request timeout (ms, blank for off)", request);
        var problem = new JLabel();
        problem.putClientProperty("html.disable", true);
        field(panel, "", problem);

        while (JOptionPane.showConfirmDialog(parent, panel, "Compiler settings",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                var newPolicy = new FetchPolicy(offline.isSelected(), policy.maxBytes(), duration(connect), duration(read),
                        request.getText().isBlank() ? Optional.empty() : Optional.of(duration(request)));
                var proxy = switch ((ProxyMode) proxies.getSelectedItem()) {
                    case SYSTEM -> new RepositoryProxyConfiguration.SystemDefault();
                    case DIRECT -> new RepositoryProxyConfiguration.Direct();
                    case HTTP -> new RepositoryProxyConfiguration.HttpProxy(proxyHost.getText().strip(),
                            Integer.parseInt(proxyPort.getText().strip()));
                };
                var store = cache.isSelected() ? RepositoryCacheConfiguration.at(absoluteCache(cachePath.getText()))
                        : RepositoryCacheConfiguration.disabled();
                var session = new SessionOptions(new RepositoryConfig(options.repositories().sources(), newPolicy), store, proxy,
                        warnings.isSelected() ? WarningPolicy.REJECT_WARNINGS : WarningPolicy.ALLOW_WARNINGS,
                        languages.getSelectedIndex() == 0 ? DiagnosticLanguage.EN : DiagnosticLanguage.DE);
                return Optional.of(new Choice((GuiSettings.Theme) themes.getSelectedItem(), session));
            } catch (IllegalArgumentException invalid) {
                problem.setText("Check the cache path, proxy and positive timeout values.");
            }
        }
        return Optional.empty();
    }

    private static Path absoluteCache(String value) {
        Path path = Path.of(value.strip());
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Cache directory must be absolute");
        }
        return path;
    }

    private static Duration duration(JTextField field) {
        return Duration.ofMillis(Long.parseLong(field.getText().strip()));
    }

    private static void field(JPanel panel, String name, Component control) {
        var label = new JLabel(name);
        label.setLabelFor(control);
        panel.add(label);
        panel.add(control);
    }
}
