package guru.interlis.diagnostics;

import guru.interlis.execution.FailureId;
import guru.interlis.model.SymbolId;
import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Mutable, run-scoped draft; it receives its canonical id only in the collector. */
public final class DiagnosticDraft {
    private final DiagnosticCode code;
    private final Severity severity;
    private final Phase phase;
    private final String messageKey;
    private final List<DiagnosticArgument> arguments = new ArrayList<>();
    private SourceRange primaryRange;
    private final List<RelatedLocation> relatedLocations = new ArrayList<>();
    private DiagnosticId causeId;
    private final List<DiagnosticId> dependsOn = new ArrayList<>();
    private SymbolId symbolId;
    private FailureId failureId;
    private final Set<DiagnosticTag> tags = new LinkedHashSet<>();

    private DiagnosticDraft(DiagnosticCode code, Severity severity, Phase phase, String messageKey) {
        this.code = Objects.requireNonNull(code, "code");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.phase = Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(messageKey, "messageKey");
        if (messageKey.isBlank()) {
            throw new IllegalArgumentException("messageKey must not be blank");
        }
        this.messageKey = messageKey;
    }

    /**
     * Starts a draft for one diagnostic code.
     *
     * @param code catalog code
     * @param severity effective severity
     * @param phase reporting phase
     * @param messageKey stable catalog message key
     * @return mutable run-scoped draft
     */
    public static DiagnosticDraft of(DiagnosticCode code, Severity severity, Phase phase,
                                     String messageKey) {
        return new DiagnosticDraft(code, severity, phase, messageKey);
    }

    /**
     * Sets the primary source range and returns this draft.
     *
     * @param range primary source range
     * @return this draft
     */
    public DiagnosticDraft at(SourceRange range) {
        this.primaryRange = Objects.requireNonNull(range, "range");
        return this;
    }

    /**
     * Appends a typed argument and returns this draft.
     *
     * @param argument typed argument
     * @return this draft
     */
    public DiagnosticDraft argument(DiagnosticArgument argument) {
        arguments.add(Objects.requireNonNull(argument, "argument"));
        return this;
    }

    /**
     * Appends a related source location and returns this draft.
     *
     * @param location related location
     * @return this draft
     */
    public DiagnosticDraft related(RelatedLocation location) {
        relatedLocations.add(Objects.requireNonNull(location, "location"));
        return this;
    }

    /**
     * Sets the direct cause diagnostic and returns this draft.
     *
     * @param id cause id
     * @return this draft
     */
    public DiagnosticDraft cause(DiagnosticId id) {
        this.causeId = Objects.requireNonNull(id, "id");
        return this;
    }

    /**
     * Adds a dependency diagnostic and returns this draft.
     *
     * @param id dependency id
     * @return this draft
     */
    public DiagnosticDraft dependsOn(DiagnosticId id) {
        dependsOn.add(Objects.requireNonNull(id, "id"));
        return this;
    }

    /**
     * Associates a semantic symbol and returns this draft.
     *
     * @param id symbol id
     * @return this draft
     */
    public DiagnosticDraft symbol(SymbolId id) {
        this.symbolId = Objects.requireNonNull(id, "id");
        return this;
    }

    /**
     * Associates an internal failure id and returns this draft.
     *
     * @param id failure id
     * @return this draft
     */
    public DiagnosticDraft failure(FailureId id) {
        this.failureId = Objects.requireNonNull(id, "id");
        return this;
    }

    /**
     * Adds a display tag and returns this draft.
     *
     * @param tag display tag
     * @return this draft
     */
    public DiagnosticDraft tag(DiagnosticTag tag) {
        tags.add(Objects.requireNonNull(tag, "tag"));
        return this;
    }

    /** Returns the catalog code.
     *
     * @return catalog code
     */
    public DiagnosticCode code() {
        return code;
    }

    /** Returns the effective severity.
     *
     * @return effective severity
     */
    public Severity severity() {
        return severity;
    }

    /** Returns the reporting phase.
     *
     * @return reporting phase
     */
    public Phase phase() {
        return phase;
    }

    /** Returns the stable message key.
     *
     * @return message key
     */
    public String messageKey() {
        return messageKey;
    }

    /** Returns an immutable snapshot of the arguments.
     *
     * @return immutable arguments
     */
    public List<DiagnosticArgument> arguments() {
        return List.copyOf(arguments);
    }

    /** Returns the optional primary range.
     *
     * @return optional primary range
     */
    public Optional<SourceRange> primaryRange() {
        return Optional.ofNullable(primaryRange);
    }

    /** Returns an immutable snapshot of related locations.
     *
     * @return immutable related locations
     */
    public List<RelatedLocation> relatedLocations() {
        return List.copyOf(relatedLocations);
    }

    /** Returns the optional direct cause id.
     *
     * @return optional cause id
     */
    public Optional<DiagnosticId> causeId() {
        return Optional.ofNullable(causeId);
    }

    /** Returns the immutable dependency ids.
     *
     * @return immutable dependency ids
     */
    public List<DiagnosticId> dependsOn() {
        return List.copyOf(dependsOn);
    }

    /** Returns the optional associated symbol.
     *
     * @return optional symbol id
     */
    public Optional<SymbolId> symbolId() {
        return Optional.ofNullable(symbolId);
    }

    /** Returns the optional internal failure id.
     *
     * @return optional failure id
     */
    public Optional<FailureId> failureId() {
        return Optional.ofNullable(failureId);
    }

    /** Returns the immutable display tags.
     *
     * @return immutable tags
     */
    public List<DiagnosticTag> tags() {
        return List.copyOf(tags);
    }
}
