package guru.interlis.validator.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SymbolRef;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.DatasetScope;
import guru.interlis.validation.DatasetValidationSession;
import guru.interlis.validation.GeometryEvidence;
import guru.interlis.validation.GeometryWitness;
import guru.interlis.validation.ValidationDiagnostic;
import guru.interlis.validation.ValidationOptions;
import guru.interlis.validation.ValidationReport;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ValidationOutput {
    private static final String TOOL = "interlis-validator";
    private final boolean json;
    private final PrintWriter out;
    private final PrintWriter err;
    final List<ValidationInput> inputs = new ArrayList<>();
    DatasetScope scope;
    String phase = "options";
    Path currentInput;
    SemanticGraph graph;
    DatasetValidationSession.Findings provisional;
    int maxDiagnostics = ValidationOptions.defaults().maxDiagnosticExamples();

    ValidationOutput(boolean json, PrintWriter out, PrintWriter err) {
        this.json = json;
        this.out = out;
        this.err = err;
    }

    int version() {
        var result = base(0, "version", false);
        if (!json) err.println(TOOL + " " + versionString());
        return write(0, result);
    }

    int failure(int code, String phase, List<String> messages) {
        return failure(code, phase, messages, null);
    }

    int failure(int code, String phase, List<String> messages, String diagnosticCode) {
        long violations = provisional == null ? 0 : provisional.detectedViolations();
        int resultCode = code == 5 && violations > 0 ? 1 : code;
        var result = base(resultCode, phase, false);
        var errors = new JsonArray();
        for (var message : messages.stream().limit(maxDiagnostics).toList()) {
            var error = new JsonObject();
            if (diagnosticCode != null) error.addProperty("code", diagnosticCode);
            error.addProperty("message", message);
            if (currentInput != null) error.addProperty("path", currentInput.toString());
            errors.add(error);
            if (!json) err.println(phase + ": " + (diagnosticCode == null ? "" : diagnosticCode + " ")
                    + message + (currentInput == null ? "" : " | " + currentInput));
        }
        long total = messages.size() + violations;
        int shown = errors.size();
        if (provisional != null) {
            result.addProperty("violations", violations);
            var examples = provisional.examples().stream().limit(maxDiagnostics - shown).toList();
            for (var diagnostic : examples) {
                if (json) errors.add(jsonDiagnostic(diagnostic, graph));
                else render(diagnostic, graph);
            }
            shown += examples.size();
            if (!json && violations > 0) err.println("Violations observed before interruption: " + violations);
        }
        result.add("diagnostics", errors);
        result.addProperty("diagnosticsTruncated", total > shown);
        if (!json && total > shown) err.println((total - shown) + " further diagnostic examples omitted.");
        return write(resultCode, result);
    }

    int report(ValidationReport report, List<String> gaps, SemanticGraph graph) {
        int code = report.detectedViolations() > 0 ? 1 : gaps.isEmpty() ? 0 : 5;
        var result = base(code, "validation", true);
        var diagnostics = new JsonArray();
        for (var diagnostic : report.examples()) {
            if (json) diagnostics.add(jsonDiagnostic(diagnostic, graph));
            else render(diagnostic, graph);
        }
        var unchecked = new JsonArray();
        gaps.forEach(unchecked::add);
        result.add("diagnostics", diagnostics);
        result.add("unchecked", unchecked);
        result.addProperty("objects", report.inspectedObjects());
        result.addProperty("violations", report.detectedViolations());
        result.addProperty("diagnosticsTruncated", report.detectedViolations() > report.examples().size());
        if (!json) {
            gaps.forEach(gap -> err.println("Unchecked: " + gap));
            err.println("Objects checked: " + report.inspectedObjects() + ", violations: " + report.detectedViolations()
                    + ", diagnostic examples: " + report.examples().size());
            String delivery = scope == DatasetScope.PARTIAL_DATASET ? "partial dataset" : "supplied dataset";
            switch (code) {
                case 1 -> err.println("Rejected " + delivery + (gaps.isEmpty() ? "." : " with incomplete checks."));
                case 5 -> err.println("Validation is incomplete for the " + delivery + ".");
                default -> err.println("No violations found in the " + delivery + ".");
            }
        }
        return write(code, result);
    }

    private JsonObject base(int code, String phase, boolean completed) {
        var result = new JsonObject();
        result.addProperty("tool", TOOL);
        result.addProperty("version", versionString());
        result.addProperty("exitCode", code);
        result.addProperty("status", switch (code) {
            case 0 -> completed ? "passed" : "ok";
            case 1 -> "rejected";
            case 2 -> "usage-error";
            case 3 -> "operational-error";
            case 4 -> "cancelled";
            case 5 -> "incomplete";
            default -> throw new IllegalArgumentException("Unknown exit code " + code);
        });
        if (scope != null) {
            result.addProperty("scope", scope.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        result.addProperty("completed", completed);
        result.addProperty("phase", phase);
        var sources = new JsonArray();
        for (var input : inputs) {
            var source = new JsonObject();
            source.addProperty("path", input.path().toString());
            source.addProperty("role", input.role().name().toLowerCase(Locale.ROOT));
            source.addProperty("format", input.format());
            sources.add(source);
        }
        result.add("inputs", sources);
        return result;
    }

    private int write(int code, JsonObject result) {
        if (json && !err.checkError()) out.println(result);
        return flush(code);
    }

    int flush(int code) {
        if (out.checkError() || err.checkError()) {
            err.println("Output failure.");
            err.flush();
            return 3;
        }
        return code;
    }

    private static String versionString() {
        var module = Main.class.getModule().getDescriptor();
        return module == null ? "development" : module.rawVersion().orElse("development");
    }

    private static JsonObject jsonDiagnostic(ValidationDiagnostic diagnostic, SemanticGraph graph) {
        var result = new JsonObject();
        result.addProperty("code", diagnostic.code().name());
        result.addProperty("message", diagnostic.message());
        var arguments = new JsonArray();
        diagnostic.arguments().forEach(arguments::add);
        result.add("arguments", arguments);
        result.add("location", jsonLocation(diagnostic.data(), graph));
        var rule = new JsonObject();
        rule.addProperty("declaration", name(diagnostic.rule().declaration(), graph));
        diagnostic.rule().range().ifPresent(range -> {
            rule.addProperty("source", range.source().value());
            rule.addProperty("startOffset", range.startOffset());
            rule.addProperty("endOffset", range.endOffset());
        });
        result.add("ruleLocation", rule);
        result.add("relatedLocations", jsonRelatedLocations(diagnostic.relatedData().stream().toList(), graph));
        diagnostic.geometryEvidence().ifPresent(evidence -> result.add("geometryEvidence", jsonGeometry(evidence, graph)));
        return result;
    }

    private static JsonObject jsonGeometry(GeometryEvidence evidence, SemanticGraph graph) {
        var result = new JsonObject();
        result.addProperty("code", evidence.code().name());
        result.addProperty("detail", evidence.detail());
        result.add("location", jsonLocation(evidence.location(), graph));
        result.add("relatedLocations", jsonRelatedLocations(evidence.relatedLocations(), graph));
        var witness = new JsonObject();
        witness.addProperty("kind", witnessKind(evidence.witness()));
        witness.addProperty("x", evidence.witness().x().toPlainString());
        witness.addProperty("y", evidence.witness().y().toPlainString());
        result.add("witness", witness);
        return result;
    }

    private static JsonArray jsonRelatedLocations(List<DataLocation> locations, SemanticGraph graph) {
        var related = new JsonArray();
        locations.forEach(value -> related.add(jsonLocation(value, graph)));
        return related;
    }

    private static JsonObject jsonLocation(DataLocation location, SemanticGraph graph) {
        var result = new JsonObject();
        location.source().ifPresent(source -> result.addProperty("source", source.value()));
        location.basket().ifPresent(basket -> result.addProperty("bid", basket.value()));
        location.tid().ifPresent(tid -> result.addProperty("tid", tid.value()));
        location.oid().ifPresent(oid -> result.addProperty("oid", oid.value()));
        location.objectType().ifPresent(type -> result.addProperty("objectType", name(type, graph)));
        result.addProperty("path", path(location, graph));
        return result;
    }

    private void render(ValidationDiagnostic diagnostic, SemanticGraph graph) {
        var rule = diagnostic.rule();
        err.println(diagnostic.code() + " " + diagnostic.message() + location(diagnostic.data(), graph)
                + " | model rule " + name(rule.declaration(), graph)
                + rule.range().map(range -> " at " + range.source().value() + ":" + range.startOffset()).orElse("")
                + diagnostic.relatedData().map(other -> " | related:" + location(other, graph)).orElse("")
                + diagnostic.geometryEvidence().map(evidence -> geometryText(diagnostic, evidence, graph)).orElse(""));
    }

    private static String geometryText(ValidationDiagnostic diagnostic, GeometryEvidence evidence, SemanticGraph graph) {
        var point = evidence.witness();
        var display = new StringBuilder(" | ");
        if (evidence.code() != diagnostic.code()) {
            display.append(evidence.code()).append(' ');
        }
        display.append(witnessKind(point)).append(" XY=(").append(point.x().toPlainString()).append(", ")
                .append(point.y().toPlainString()).append(") ").append(evidence.detail());
        if (!evidence.location().equals(diagnostic.data())) {
            display.append(location(evidence.location(), graph));
        }
        for (var other : evidence.relatedLocations()) {
            if (diagnostic.relatedData().filter(other::equals).isEmpty()) {
                display.append(" | related:").append(location(other, graph));
            }
        }
        return display.toString();
    }

    private static String witnessKind(GeometryWitness witness) {
        return witness.kind().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String location(DataLocation location, SemanticGraph graph) {
        var value = new StringBuilder();
        location.source().ifPresent(source -> value.append(" ").append(source.value()));
        location.basket().ifPresent(basket -> value.append(" BID=").append(basket.value()));
        location.tid().ifPresent(tid -> value.append(" TID=").append(tid.value()));
        location.oid().ifPresent(oid -> value.append(" OID=").append(oid.value()));
        location.objectType().ifPresent(type -> value.append(" ").append(name(type, graph)));
        return value.append(path(location, graph)).toString();
    }

    private static String path(DataLocation location, SemanticGraph graph) {
        var value = new StringBuilder();
        for (var step : location.path()) {
            switch (step) {
                case DataLocation.Slot slot -> value.append("/").append(name(slot.symbol(), graph));
                case DataLocation.Index index -> value.append("[").append(index.position()).append("]");
                case DataLocation.GeometryComponent geometry ->
                        value.append("/").append(geometry.kind()).append("[").append(geometry.position()).append("]");
            }
        }
        return value.toString();
    }

    private static String name(SymbolRef ref, SemanticGraph graph) {
        return graph.symbol(ref.symbol()).map(symbol -> symbol.name().value()).orElse(ref.symbol().value());
    }
}
