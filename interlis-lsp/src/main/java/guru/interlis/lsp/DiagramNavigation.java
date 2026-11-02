package guru.interlis.lsp;
import org.eclipse.lsp4j.Range;
/** Host-resolved location, including readonly handles. No client-side offset or name inference. */
public record DiagramNavigation(String serverInstanceId,String snapshotId,String uri,Range range,boolean readonly) { }
