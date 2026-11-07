package guru.interlis.xtf23;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class Xtf23TestData {
    static final CancellationToken TOKEN = CancellationToken.none();
    static final String PROLOG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    static final String MODELS = """
            <MODELS><MODEL NAME="Reference23" VERSION="2026-11-06" URI="https://example.invalid/iox-reference"/></MODELS>
            """;

    private Xtf23TestData() { }

    static Path fixture(String name) {
        return Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili23", name);
    }

    static TransferPlan referencePlan() throws IOException {
        Path path = fixture("Reference23.ili");
        return compile("Reference23", new SourceText(new SourceId(path.toUri().toString()),
                new SourceRevision(1), Files.readString(path)));
    }

    static TransferPlan compileInline(String model, String text) {
        return compile(model, new SourceText(new SourceId("memory:/" + model + ".ili"), new SourceRevision(1), text));
    }

    private static TransferPlan compile(String model, SourceText source) {
        var request = new CompilationRequest(List.of(new ModelSelector.ByName(model, Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, TOKEN);
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().requestDiagnostics().toString() : outcome.toString()).schema();
        return assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema)).plan();
    }

    static String document(String headerContent, String baskets) {
        return PROLOG + """
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test &amp; sender">%s</HEADERSECTION>
                  <DATASECTION>%s</DATASECTION>
                </TRANSFER>
                """.formatted(headerContent, baskets);
    }

    static ByteArrayInputStream input(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    static List<TransferEvent> readAll(Xtf23Reader reader) {
        var events = new ArrayList<TransferEvent>();
        for (var event = reader.readNext(TOKEN); event.isPresent(); event = reader.readNext(TOKEN)) {
            events.add(event.orElseThrow());
        }
        return List.copyOf(events);
    }
}
