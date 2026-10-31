package guru.interlis.cli.verification;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticComparisonTest {
    @Test void actualGraphProjectionDetectsLossWrongEqualNamedTargetOrderAndFalseValidity() throws Exception {
        Path root=Path.of(System.getProperty("repo.root"));
        for(String version:List.of("2.3","2.4")) {
            var good=SemanticComparison.project(root.resolve("testdata/g2/a3/ordered-targets-"+version+".ili"));
            var bad=SemanticComparison.project(root.resolve("testdata/g2/a3/invalid-"+version+".ili"));
            assertEquals("succeeded",good.get("outcome").getAsString());assertEquals("rejected",bad.get("outcome").getAsString());
            assertEquals(List.of("first","second","third"),HostChecks.rows(good.getAsJsonObject("elements"),"F.Child").stream().map(m->m.get("name").getAsString()).toList());
            for(String topic:List.of("T","U")) {
                var members=HostChecks.rows(good.getAsJsonObject("elements"),"F."+topic+".Link");
                assertEquals(List.of("a","b"),members.stream().map(m->m.get("name").getAsString()).toList());
                for(int i=0;i<2;i++)assertEquals(List.of("F."+topic+"."+(i==0?"A":"B")),HostChecks.strings(members.get(i).get("targets")));
            }
            assertEquals(4,SemanticComparison.counterchecks(good,bad));
        }
    }
}
