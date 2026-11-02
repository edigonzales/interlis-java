package guru.interlis.lsp;

import com.google.gson.annotations.JsonAdapter;
import guru.interlis.projection.DiagramDocument;
import java.util.List;
import java.util.Map;

/** Immutable response identity vector and actual typed DiagramDocument, encoded once using its authoritative codec. */
public record DiagramResponse(int protocolVersion,String serverInstanceId,String requestId,String diagramId,
        String rootUri,int documentVersion,String workspaceRevision,String snapshotId,
        Map<String,Integer> documentVersions,List<String> selectedSymbolIds,
        @JsonAdapter(DiagramWireAdapter.class) DiagramDocument document) {
    public DiagramResponse {documentVersions=Map.copyOf(documentVersions);selectedSymbolIds=List.copyOf(selectedSymbolIds);}
}
