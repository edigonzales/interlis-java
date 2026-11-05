package guru.interlis.gui;

import guru.interlis.diagnostics.DiagnosticMessageFormatter;
import guru.interlis.execution.RunEvent;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.repository.RepositorySearchStep;

final class RunLog {
    private static final int MAX_REPOSITORY_STEPS = 128;

    private RunLog() {
    }

    static String text(CompileResult result) {
        if (result == null) {
            return "No compiler run.";
        }
        var text = new StringBuilder("Run ").append(safe(result.request().context().runId().value())).append('\n');
        if (result.events().isEmpty()) {
            text.append("No compiler events were captured.\n");
        }
        for (RunEvent event : result.events()) {
            text.append(event.header().sequence()).append(" · ").append(describe(event)).append('\n');
        }
        var steps = result.repositorySteps();
        int start = Math.max(0, steps.size() - MAX_REPOSITORY_STEPS);
        if (!steps.isEmpty()) {
            text.append("\nRepository decisions\n");
            if (start != 0) {
                text.append(start).append(" earlier decisions omitted.\n");
            }
            for (int i = start; i < steps.size(); i++) {
                var step = steps.get(i);
                text.append(step.kind()).append(" · ").append(safe(step.modelName())).append(" · ")
                        .append(safe(step.repository().toString())).append('\n');
            }
        }
        return text.toString();
    }

    private static String describe(RunEvent event) {
        return switch (event) {
            case RunEvent.Started(var header, var metadata) -> "Started · " + safe(metadata.operation());
            case RunEvent.PhaseStarted(var header, var phase, var metadata) -> phase + " · started";
            case RunEvent.PhaseFinished(var header, var phase, var status, var duration, var units) ->
                    phase + " · " + status + " · " + units + " units";
            case RunEvent.CacheLookup(var header, var cache, var hit, var reason) -> safe(cache) + " · " + (hit ? "hit" : "miss");
            case RunEvent.WorkspaceInvalidated(var header, var revision, var reason, var units) -> "Workspace invalidated · revision " + revision;
            case RunEvent.SourceResolved(var header, var source, var strategy, var hit, var duration) ->
                    "Source resolved · " + safe(RepositorySearchStep.safeUri(source));
            case RunEvent.CancelRequested(var header, var reason, var adapter) -> "Cancellation requested · " + reason;
            case RunEvent.InternalFailure(var header, var id, var exception, var detail) -> "Internal failure · " + safe(id.value());
            case RunEvent.Finished ignored -> "Finished";
            case RunEvent.ObserverDegraded(var header, var sink, var failure, var discarded) ->
                    "Observer degraded · " + discarded + " discarded";
        };
    }

    private static String safe(String value) {
        int limit = Math.min(value.length(), 400);
        if (limit < value.length() && Character.isLowSurrogate(value.charAt(limit))) {
            limit--;
        }
        return DiagnosticMessageFormatter.safeText(value.substring(0, limit)) + (limit < value.length() ? "…" : "");
    }
}
