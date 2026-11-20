package guru.interlis.execution;

import guru.interlis.diagnostics.Phase;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable tagged operational event. */
public sealed interface RunEvent permits RunEvent.Started, RunEvent.PhaseStarted,
        RunEvent.PhaseFinished, RunEvent.CacheLookup, RunEvent.Finished {

    RunEventHeader header();

    record RunEventHeader(RunId runId, Optional<RunId> parentRunId, long sequence,
                          int schemaVersion, long wallTimeEpochMillis,
                          long monotonicNanos) {
        /** Creates a validated instance. */
        public RunEventHeader {
            Objects.requireNonNull(runId, "runId");
            parentRunId = Objects.requireNonNull(parentRunId, "parentRunId");
            if (sequence < 1 || schemaVersion < 1) {
                throw new IllegalArgumentException("sequence and schemaVersion must be positive");
            }
        }
    }

    record RunMetadata(String operation, List<String> languageProfiles,
                       int rootCount, String optionsFingerprint) {
        /** Creates a validated instance. */
        public RunMetadata {
            Objects.requireNonNull(operation, "operation");
            languageProfiles = List.copyOf(Objects.requireNonNull(languageProfiles, "languageProfiles"));
            if (rootCount < 0) {
                throw new IllegalArgumentException("rootCount must be non-negative");
            }
            Objects.requireNonNull(optionsFingerprint, "optionsFingerprint");
        }
    }

    record PhaseMetadata(int unitCount) {
        /** Creates a validated instance. */
        public PhaseMetadata {
            if (unitCount < 0) {
                throw new IllegalArgumentException("unitCount must be non-negative");
            }
        }
    }

    record Started(RunEventHeader header, RunMetadata metadata) implements RunEvent {
        /** Creates a validated instance. */
        public Started {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(metadata, "metadata");
        }
    }

    record PhaseStarted(RunEventHeader header, Phase phase, PhaseMetadata metadata)
            implements RunEvent {
        /** Creates a validated instance. */
        public PhaseStarted {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(metadata, "metadata");
        }
    }

    record PhaseFinished(RunEventHeader header, Phase phase, PhaseStatus status,
                         long durationNanos, int unitCount) implements RunEvent {
        /** Creates a validated instance. */
        public PhaseFinished {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(status, "status");
            if (durationNanos < 0 || unitCount < 0) {
                throw new IllegalArgumentException("duration and unitCount must be non-negative");
            }
        }
    }

    record CacheLookup(RunEventHeader header, String cache, boolean hit, String reason)
            implements RunEvent {
        /** Creates a validated instance. */
        public CacheLookup {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(cache, "cache");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Finished(RunEventHeader header, RunCompletion completion) implements RunEvent {
        /** Creates a validated instance. */
        public Finished {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(completion, "completion");
        }
    }
}
