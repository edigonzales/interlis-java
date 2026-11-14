package guru.interlis.conformance;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
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
        var deviations = new LinkedHashMap<String, Deviation>();
        for (var line : Files.readAllLines(Path.of(args[3]))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            var fields = line.split("\t", -1);
            if (fields.length != 4 || List.of(fields).stream().anyMatch(String::isBlank)
                    || deviations.putIfAbsent(fields[0], new Deviation(fields[1],
                    fields[2].equals("-") ? List.of() : List.of(fields[2].split(",")), fields[3])) != null) {
                throw new IllegalArgumentException("Invalid validator deviation entry: " + line);
            }
        }
        var found = new ArrayList<String>();
        var problems = new ArrayList<String>();
        var evidence = new ArrayList<SuiteValidation.Observation>();
        var qualification = new TestExecutionListener() {
            @Override
            public void executionStarted(TestIdentifier test) {
                if (test.isTest()) {
                    evidence.clear();
                    SuiteValidation.observe(evidence);
                }
            }

            @Override
            public void executionFinished(TestIdentifier test, TestExecutionResult result) {
                if (!test.isTest()) return;
                SuiteValidation.observe(null);
                if (!(test.getSource().orElse(null) instanceof MethodSource method)) return;
                var name = method.getClassName() + "." + method.getMethodName();
                var expected = deviations.remove(name);
                if (expected == null) return;
                var failure = result.getThrowable().orElse(null);
                if (evidence.size() == 1 && expected.matches(evidence.getFirst(), failure)) {
                    found.add(name + " — " + expected.reason());
                } else {
                    problems.add(name + " — expected " + expected + ", observed " + result.getStatus()
                            + " " + evidence);
                }
            }
        };
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(summary, qualification,
                new LegacyXmlReportGeneratingListener(output, new PrintWriter(System.err, true)));
        launcher.execute(request);
        var results = summary.getSummary();
        try (var writer = new PrintWriter(Files.newBufferedWriter(output.resolve("summary.txt")))) {
            results.printTo(writer);
            writer.println("Known deviations: " + found.size());
            found.forEach(writer::println);
            deviations.forEach((test, deviation) -> {
                if (filter.matcher(test).matches()) problems.add("Selected deviation absent: " + test);
            });
            problems.forEach(writer::println);
            results.printFailuresTo(writer, 5);
        }
        if (!Files.isRegularFile(output.resolve("TEST-junit-vintage.xml"))
                || results.getTestsStartedCount() == 0 || results.getTestsAbortedCount() != 0
                || results.getTestsSkippedCount() != 0 || results.getContainersSkippedCount() != 0
                || results.getContainersAbortedCount() != 0
                || !problems.isEmpty() || results.getTotalFailureCount() != found.size()) {
            System.exit(1);
        }
    }
    record Deviation(String fixture, List<String> evidence, String reason) {
        boolean matches(SuiteValidation.Observation actual, Throwable failure) {
            boolean matchingFailure = actual.incomplete()
                    ? failure instanceof SuiteValidation.UnfinishedValidation
                    : failure instanceof AssertionError;
            return matchingFailure && fixture.equals(actual.fixture()) && evidence.equals(actual.evidence());
        }
    }
}
