package guru.interlis.lsp;
import java.util.*;
/** Typed immutable wire report. Empty strings/lists denote unavailable data; no exception objects or schema certificates. */
public record CompileReport(String outcome,List<ModelChoice> modelChoices,Summary summary) {
    public CompileReport { Objects.requireNonNull(outcome);modelChoices=List.copyOf(modelChoices);Objects.requireNonNull(summary); }
    public record ModelChoice(String modelName,String modelVersion) {
        public ModelChoice { Objects.requireNonNull(modelName);Objects.requireNonNull(modelVersion); }
    }
    public record Summary(String snapshotId,int modelCount,List<String> reasons,List<DiagnosticEntry> diagnostics,
                          String cancellationReason,String failureId) {
        public Summary { if(modelCount<0)throw new IllegalArgumentException("negative model count");Objects.requireNonNull(snapshotId);reasons=List.copyOf(reasons);diagnostics=List.copyOf(diagnostics);Objects.requireNonNull(cancellationReason);Objects.requireNonNull(failureId); }
        public static Summary empty() { return new Summary("",0,List.of(),List.of(),"",""); }
    }
    /** Range is two UTF-16 coordinates, or empty for a global diagnostic. URI is empty for globals. */
    public record DiagnosticEntry(String code,String severity,String message,String uri,List<Coordinate> range) {
        public DiagnosticEntry { Objects.requireNonNull(code);Objects.requireNonNull(severity);Objects.requireNonNull(message);Objects.requireNonNull(uri);range=List.copyOf(range);if(range.size()!=0 && range.size()!=2)throw new IllegalArgumentException("diagnostic range"); }
    }
    public record Coordinate(int line,int character) {
        public Coordinate { if(line<0 || character<0)throw new IllegalArgumentException("negative coordinate"); }
    }
}
