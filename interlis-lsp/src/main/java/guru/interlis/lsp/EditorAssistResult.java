package guru.interlis.lsp;
import java.util.*;
import org.eclipse.lsp4j.*;
/** Java-planned edits and the resulting native caret; null response means no assistance. */
public record EditorAssistResult(int protocolVersion,String serverInstanceId,String snapshotId,
                                  String uri,int documentVersion,List<TextEdit> edits,Position caret) {
    public EditorAssistResult {edits=List.copyOf(edits);}
}
