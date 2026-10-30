package guru.interlis.bridge;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResolutionPolicyProtocolTest {
    @Test void invalidPolicyDataCannotConsumeSessionsOrReachTheCompiler() {
        var bridge=new BridgeDispatcher();
        for(var value:List.of("null","7","{\"preferWorkspaceSource\":7}","{\"sourcePriorities\":[{\"sourceId\":\"memory:/a\",\"priority\":-1}]}",
                "{\"modelSelections\":[{\"modelName\":\"A\"},{\"modelName\":\"A\"}]}","{\"sourcePriorities\":[{\"sourceId\":\"memory:/a\",\"priority\":1.5}]}")) {
            var request="{\"protocol\":\"interlis.compiler\",\"protocolVersion\":\"1.0\",\"requestId\":\"policy\",\"operation\":\"openSession\",\"params\":{\"configuration\":{\"resolutionPolicy\":"+value+"}}}";
            var response=bridge.handle(request);assertTrue(response.contains("INVALID_REQUEST"),response);
        }
        var opened=bridge.handle("{\"protocol\":\"interlis.compiler\",\"protocolVersion\":\"1.0\",\"requestId\":\"ok\",\"operation\":\"openSession\",\"params\":{}}");
        assertTrue(opened.contains("\"sessionId\":\"s-1\""),opened);
    }
}
