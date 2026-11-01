package guru.interlis.lsp;
import java.util.*;
/** SelectionRequired performs no compile; other outcomes come from the one compiler gate. */
public record CompileReport(String outcome,List<ModelChoice> modelChoices,Map<String,Object> summary) {
    public CompileReport { modelChoices=List.copyOf(modelChoices);summary=Map.copyOf(summary); }
    public record ModelChoice(String modelName,String modelVersion) { }
}
