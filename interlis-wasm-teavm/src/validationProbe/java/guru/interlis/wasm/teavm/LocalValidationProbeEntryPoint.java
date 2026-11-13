package guru.interlis.wasm.teavm;

import guru.interlis.bridge.Json;
import guru.interlis.bridge.JsonValue;
import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.*;
import guru.interlis.transfer.*;
import guru.interlis.validation.*;
import org.teavm.jso.JSExport;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test-only entry point for local neutral-transfer validation. */
public final class LocalValidationProbeEntryPoint {
    private LocalValidationProbeEntryPoint() { }

    public static void main(String[] args) { }

    @JSExport
    public static String validateExample(String modelText, String amountLexeme, String label, boolean includeLabel) {
        var source = new SourceText(new SourceId("memory:/Probe.ili"), new SourceRevision(1), modelText);
        var request = new CompilationRequest(List.of(new ModelSelector.ByName("Probe", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        var token = CancellationToken.none();
        var compiled = CompilerEngines.create().compile(request, token);
        if (!(compiled instanceof CompilationOutcome.Succeeded success)) {
            if (compiled instanceof CompilationOutcome.Rejected rejected) {
                return Json.write(new JsonValue.JsonObject(Map.of(
                        "result", text("COMPILATION_REJECTED"),
                        "diagnostics", new JsonValue.JsonArray(rejected.analysis().diagnostics().stream()
                                .<JsonValue>map(diagnostic -> text(diagnostic.code().value())).toList()))));
            }
            throw new IllegalStateException("Unexpected compiler outcome: " + compiled);
        }
        var transfer = ((TransferPlanningResult.Ready) TransferPlanner.plan(success.schema())).plan();
        var validation = ((ValidationPlanningResult.Ready) ValidationPlanner.plan(transfer)).plan();
        var type = transfer.objects().stream().filter(object -> object.name().declared().last().equals("Item"))
                .findFirst().orElseThrow();
        var model = transfer.rootModels().getFirst();
        var topic = type.topic().orElseThrow();
        var basket = new TransferIdentity.BasketId("probe", TransferIdentity.IdOrigin.SOURCE);
        var tid = new TransferIdentity.TransferId("item", TransferIdentity.IdOrigin.SOURCE);
        var values = new LinkedHashMap<guru.interlis.model.SymbolRef, TransferValue>();
        for (var slot : type.slots()) {
            switch (slot.name().declared().last()) {
                case "amount" -> values.put(slot.symbol(),
                        new TransferValue.NumberValue(amountLexeme, new BigDecimal(amountLexeme)));
                case "label" -> {
                    if (includeLabel) {
                        values.put(slot.symbol(), new TransferValue.Text(label));
                    }
                }
                default -> throw new IllegalArgumentException("Unexpected example slot");
            }
        }
        var id = new TransferIdentity(basket, Optional.empty(), Optional.of(tid), Optional.empty(), Optional.empty());
        var object = new TransferObject(id, new TransferValue.Structured(type.symbol(), values));
        try (var session = LocalValidator.session(validation, ValidationOptions.defaults())) {
            session.accept(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of(
                    new TransferEvent.ModelDeclaration(model, LanguageVersion.ILI24, Optional.empty(), Optional.empty()))), token);
            session.accept(new TransferEvent.StartBasket(basket, topic, List.of(), TransferEvent.BasketKind.FULL,
                    TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of()), token);
            session.accept(new TransferEvent.ObjectEvent(object, Optional.empty(), TransferEvent.Consistency.COMPLETE), token);
            session.accept(new TransferEvent.EndBasket(basket), token);
            session.accept(new TransferEvent.EndTransfer(), token);
            var report = session.finish();
            return Json.write(new JsonValue.JsonObject(Map.of(
                    "result", text(report.result().name()),
                    "violations", new JsonValue.JsonNumber(BigDecimal.valueOf(report.detectedViolations()), Long.toString(report.detectedViolations())),
                    "diagnostics", new JsonValue.JsonArray(report.examples().stream().<JsonValue>map(diagnostic ->
                            new JsonValue.JsonObject(Map.of(
                                    "code", text(diagnostic.code().name()),
                                    "path", new JsonValue.JsonArray(diagnostic.data().path().stream().<JsonValue>map(step -> {
                                        var slot = (DataLocation.Slot) step;
                                        return text(transfer.symbol(slot.symbol()).known().orElseThrow().name().value());
                                    }).toList())))).toList()))));
        }
    }

    private static JsonValue text(String value) {
        return new JsonValue.JsonString(value);
    }
}
