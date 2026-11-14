package guru.interlis.conformance;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.validation.*;
import guru.interlis.validation.jts.JtsGeometryEngine;
import guru.interlis.workspace.*;
import guru.interlis.xtf23.Xtf23Exception;
import guru.interlis.xtf23.Xtf23Reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/** Test-side composition of the public compiler, XTF 2.3 reader and dataset validator. */
public final class SuiteValidation {
    private record Plans(TransferPlan transfer, ValidationPlan validation) { }
    private record Closure(Path directory, List<String> roots) { }

    private static final Map<Closure, Plans> PLANS = new HashMap<>();
    private static final Set<String> SUITE_MODELS = Set.of(
            "TestSuite", "TestSuite2", "Roads", "RoadsExt", "areAreas_ext");

    private SuiteValidation() { }

    public static boolean validate(Path input, Path modelDirectory, CancellationToken cancellation) throws IOException {
        cancellation.throwIfCancellationRequested();
        if (!Files.isRegularFile(input)) throw new IOException("Missing suite fixture: " + input);
        var closure = new Closure(modelDirectory, modelRoots(input, cancellation));
        var plans = PLANS.get(closure);
        if (plans == null) {
            plans = prepare(closure, cancellation);
            PLANS.put(closure, plans);
        }
        var id = new SourceId(input.toUri().toString());
        ValidationReport report;
        try (var stream = Files.newInputStream(input);
             var reader = new Xtf23Reader(plans.transfer(), stream, Optional.of(id));
             var storage = new InMemoryValidationStorage(1_000_000, 256L * 1024 * 1024);
             var run = DatasetValidator.session(plans.validation(), DatasetOptions.defaults(), storage)) {
            run.readInput(new InputDescriptor(new InputId(input.getFileName().toString()), InputRole.PRIMARY),
                    reader, cancellation);
            report = run.finish(cancellation);
        } catch (Xtf23Exception e) {
            if (e.getSuppressed().length != 0) throw e;
            return switch (e.reason()) {
                case MALFORMED_XML, UNSAFE_XML, INVALID_WIRE, DUPLICATE_ID -> {
                    System.out.println(input.getFileName() + " WIRE " + e.reason() + " " + e.getMessage());
                    yield false;
                }
                case UNSUPPORTED, LIMIT_EXCEEDED, IO, CONFIGURATION -> throw e;
            };
        }

        String fixture = input.getFileName().toString();
        for (var diagnostic : report.examples()) {
            System.out.println(fixture + " DATA " + diagnostic.code() + " " + diagnostic.data()
                    + " " + diagnostic.arguments());
        }
        var coverage = report.coverage();
        var ruleGaps = coverage.datasetRules().stream().filter(rule -> switch (rule.outcome()) {
            case UNSUPPORTED, UNSUPPORTED_VALUE_SEMANTICS, UNDETERMINED -> rule.candidates() > 0;
            default -> false;
        }).toList();
        var constraintGaps = coverage.constraints().stream().filter(rule -> switch (rule.status()) {
            case CHECKED, UNDEFINED_BY_DATA -> false;
            default -> true;
        }).toList();
        if (!ruleGaps.isEmpty() || !constraintGaps.isEmpty() || !coverage.unsupportedValues().isEmpty()
                || !coverage.missingRuntimeContext().isEmpty() || !coverage.incompleteContext().isEmpty()) {
            var detail = "Uncompleted validation in " + fixture + ": " + ruleGaps + " " + constraintGaps
                    + " " + coverage.unsupportedValues() + " " + coverage.missingRuntimeContext()
                    + " " + coverage.incompleteContext();
            System.out.println(detail);
            if (report.detectedViolations() == 0) throw new IllegalStateException(detail);
        }
        System.out.println(fixture + " EOF objects=" + report.inspectedObjects() + " violations=" + report.detectedViolations());
        return report.detectedViolations() == 0;
    }

    private static List<String> modelRoots(Path input, CancellationToken cancellation) throws IOException {
        var factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        var roots = new LinkedHashSet<String>();
        // Read only the model declarations for plan selection. The product reader validates the input.
        try (var stream = Files.newInputStream(input)) {
            var xml = factory.createXMLStreamReader(stream);
            try {
                for (int count = 0; count < 16_384 && xml.hasNext(); count++) {
                    cancellation.throwIfCancellationRequested();
                    if (xml.next() != XMLStreamConstants.START_ELEMENT) continue;
                    if ("DATASECTION".equals(xml.getLocalName())) break;
                    if ("MODEL".equals(xml.getLocalName())) {
                        String name = xml.getAttributeValue(null, "NAME");
                        if (name != null && SUITE_MODELS.contains(name)) roots.add(name);
                    }
                }
            } finally {
                xml.close();
            }
        } catch (XMLStreamException e) {
            for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof IOException io) throw io;
            }
            // The codec reports XML errors with its own cause and source location.
        }
        if (roots.contains("TestSuite")) roots.remove("TestSuite2");
        if (roots.contains("RoadsExt")) roots.remove("Roads");
        // A missing or broken header still goes through the real codec.
        if (roots.isEmpty()) roots.add("TestSuite2");
        return roots.stream().sorted().toList();
    }

    private static Plans prepare(Closure closure, CancellationToken cancellation) throws IOException {
        try (var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults());
             var files = Files.list(closure.directory())) {
            for (Path file : files.filter(p -> p.toString().endsWith(".ili")).sorted().toList()) {
                workspace.update(new SourceUpdate(new SourceId(file.toUri().toString()),
                        new SourceExpectation.Absent(), new SourceChange.Replace(Files.readString(file))));
            }
            List<ModelSelector> roots = closure.roots().stream().<ModelSelector>map(name ->
                    new ModelSelector.ByName(name, Optional.empty(), Optional.empty())).toList();
            var outcome = workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(), roots), cancellation);
            if (!(outcome instanceof CompilationOutcome.Succeeded compiled)) {
                Object detail = outcome instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().diagnostics() : outcome;
                throw new IllegalStateException("Cannot compile suite models " + closure.roots() + ": " + detail);
            }
            var transfer = TransferPlanner.plan(compiled.schema());
            if (!(transfer instanceof TransferPlanningResult.Ready ready)) {
                throw new IllegalStateException("Cannot plan suite transfer: " + transfer);
            }
            var validation = ValidationPlanner.plan(ready.plan(), ValidationProfile.DATASET,
                    RuntimeBindings.empty().withGeometry(JtsGeometryEngine.standard()));
            if (!(validation instanceof ValidationPlanningResult.Ready rules)) {
                throw new IllegalStateException("Cannot plan suite validation: " + validation);
            }
            return new Plans(ready.plan(), rules.plan());
        }
    }
}
