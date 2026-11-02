package guru.interlis.lsp;
import java.util.*;
/** Bounded artifact from a checked schema. Metadata identifies the actual captured source state. */
public record ExportArtifact(String mediaType,String encoding,String content,String fileNameSuggestion,
                             SourceSnapshot sourceSnapshot,List<String> limitations) {
    public ExportArtifact {limitations=List.copyOf(limitations);}
    public record SourceSnapshot(String serverInstanceId,String snapshotId,String rootUri,int documentVersion,
                                 String workspaceRevision,Map<String,String> sourceRevisions) {
        public SourceSnapshot {sourceRevisions=Map.copyOf(sourceRevisions);}
    }
}
