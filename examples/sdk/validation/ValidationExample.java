package example.validation;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.documentation.DocumentationProjector;
import guru.interlis.documentation.DocumentationOptions;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.validation.DatasetOptions;
import guru.interlis.validation.DatasetScope;
import guru.interlis.validation.DatasetValidator;
import guru.interlis.validation.InMemoryValidationStorage;
import guru.interlis.validation.InputDescriptor;
import guru.interlis.validation.InputId;
import guru.interlis.validation.InputRole;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.ValidationPlanner;
import guru.interlis.validation.ValidationPlanningResult;
import guru.interlis.validation.ValidationProfile;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf24.Xtf24Reader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Compiles and validates two deliveries using the public SDK artifacts. */
public final class ValidationExample {
    private ValidationExample() { }

    public static void main(String[] args) throws Exception {
        boolean named = args.length == 0;
        if (ValidationExample.class.getModule().isNamed() != named
                || Xtf23Reader.class.getModule() != Xtf24Reader.class.getModule()
                || named && (!"guru.interlis.xtf".equals(Xtf24Reader.class.getModule().getName())
                    || !"guru.interlis.projection".equals(DocumentationProjector.class.getModule().getName()))) {
            throw new AssertionError("Unexpected SDK module layout");
        }
        if (named && CompilerEngines.class.getModule().isExported(
                "guru.interlis.compiler.internal", ValidationExample.class.getModule())) {
            throw new AssertionError("Compiler internals are accessible");
        }
        if (ModuleLayer.boot().findModule("com.h2database.mvstore").isPresent()
                || ModuleLayer.boot().findModule("guru.interlis.compiler.gui").isPresent()) {
            throw new AssertionError("Unexpected dependency in the standard validator");
        }
        check("2.3", """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="example"><MODELS>
                    <MODEL NAME="Delivery" VERSION="1" URI="https://example.org"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION><Delivery.Data BID="b">
                    <Delivery.Data.Item TID="bad"><label>toolong</label></Delivery.Data.Item>
                  </Delivery.Data></DATASECTION>
                </TRANSFER>
                """);
        check("2.4", """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                            xmlns="http://www.interlis.ch/xtf/2.4/Delivery">
                  <i:headersection><i:models><i:model>Delivery</i:model></i:models></i:headersection>
                  <i:datasection><Data i:bid="b">
                    <Item i:tid="bad"><label>toolong</label></Item>
                  </Data></i:datasection>
                </i:transfer>
                """);
    }

    private static void check(String version, String xml) throws Exception {
        var source = new SourceText(new SourceId("memory:/Delivery.ili"), new SourceRevision(1), """
                INTERLIS %s;
                MODEL Delivery AT "https://example.org" VERSION "1" =
                  TOPIC Data = CLASS Item = label: TEXT*3; END Item; END Data;
                END Delivery.
                """.formatted(version));
        var request = new CompilationRequest(List.of(new ModelSelector.ByName("Delivery", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        var token = CancellationToken.none();
        if (!(CompilerEngines.create().compile(request, token) instanceof CompilationOutcome.Succeeded compilation)
                || !(TransferPlanner.plan(compilation.schema()) instanceof TransferPlanningResult.Ready transfer)
                || !(ValidationPlanner.plan(transfer.plan(), ValidationProfile.DATASET) instanceof ValidationPlanningResult.Ready validation)) {
            throw new AssertionError("Could not plan delivery " + version);
        }
        var documentation = new DocumentationProjector().project(compilation.schema(),
                DocumentationOptions.defaults(), token);
        if (documentation.sections().stream().noneMatch(section -> section.name().equals("Item"))) {
            throw new AssertionError("Missing Item in the documentation projection");
        }
        var input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        try (var storage = new InMemoryValidationStorage(1_000, 100_000);
             var session = DatasetValidator.session(validation.plan(), DatasetOptions.defaults(), storage);
             var reader = version.equals("2.3") ? new Xtf23Reader(transfer.plan(), input)
                     : new Xtf24Reader(transfer.plan(), input)) {
            session.readInput(new InputDescriptor(new InputId("example"), InputRole.PRIMARY), reader, token);
            var report = session.finish(token);
            if (report.inspectedObjects() != 1 || report.detectedViolations() != 1
                    || !report.datasetScope().equals(Optional.of(DatasetScope.SUPPLIED_DATASET))
                    || report.examples().getFirst().code() != ValidationCode.TEXT_TOO_LONG
                    || !report.examples().getFirst().data().tid().orElseThrow().value().equals("bad")) {
                throw new AssertionError("Unexpected delivery result for " + version + ": " + report);
            }
            System.out.println("XTF " + version + ": " + report.examples().getFirst().code());
        }
    }
}
