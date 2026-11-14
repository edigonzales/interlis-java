package guru.interlis.conformance;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

public final class SuiteJUnit {
    public static void main(String[] args) throws Exception {
        var filter = Pattern.compile(args[1]);
        PostDiscoveryFilter selection = descriptor -> {
            if (descriptor.getSource().orElse(null) instanceof MethodSource method) {
                return FilterResult.includedIf(filter.matcher(method.getClassName() + "." + method.getMethodName()).matches());
            }
            return FilterResult.included("Test container");
        };
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClasspathRoots(Set.of(Path.of(args[0]))))
                .filters(EngineFilter.includeEngines("junit-vintage"), selection)
                .build();
        var summary = new SummaryGeneratingListener();
        var output = Path.of(args[2]);
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(summary,
                new LegacyXmlReportGeneratingListener(output, new PrintWriter(System.err, true)));
        launcher.execute(request);
        var results = summary.getSummary();
        try (var writer = new PrintWriter(Files.newBufferedWriter(output.resolve("summary.txt")))) {
            results.printTo(writer);
            results.printFailuresTo(writer, 5);
        }
        if (!Files.isRegularFile(output.resolve("TEST-junit-vintage.xml"))
                || results.getTestsFoundCount() == 0 || results.getTestsAbortedCount() != 0
                || results.getTotalFailureCount() != 0) {
            System.exit(1);
        }
    }
}
