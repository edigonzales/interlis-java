package guru.interlis.verification;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static guru.interlis.verification.TestReportInventory.*;
import static org.junit.jupiter.api.Assertions.*;

class JvmBuildParityTest {
    private TestInventory inventory(String builder,String... indices) {
        var receipt=new ExecutionReceipt("interlis-model",builder,"actual-run",Instant.EPOCH,0,"EXECUTED",Map.of(),Map.of());
        return new TestInventory(receipt,Arrays.stream(indices).map(index->new TestCase(new TestIdentity("interlis-model","example.ParameterTest","example(String)",index),"case "+index,"PASSED",Path.of("raw.xml"),"rawhash","native")).toList(),Map.of());
    }
    @Test void rejectsMissingParameterEvenWithOtherPassingCase() {
        var checker=new JvmBuildParity();assertThrows(IllegalArgumentException.class,()->checker.compareExecutedTests(inventory("gradle","1","2"),inventory("jenesis","1","3")));
    }
    @Test void comparesExecutedInvocationIdentitiesAcrossBuilders() {
        assertDoesNotThrow(()->new JvmBuildParity().compareExecutedTests(inventory("gradle","1","2"),inventory("jenesis","2","1")));
    }
}
