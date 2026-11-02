package guru.interlis.lsp;
import java.util.Map;
/** Lightweight accepted-analysis notification; it does not calculate any diagram. */
public record AnalysisReady(int protocolVersion,String serverInstanceId,String workspaceRevision,String snapshotId,Map<String,Integer> documentVersions) {
    public AnalysisReady {documentVersions=Map.copyOf(documentVersions);}
}
