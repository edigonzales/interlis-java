package guru.interlis.transfer;

import guru.interlis.model.QualifiedName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.transfer.TransferTestSchemas.compileInline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferPlannerUnsupportedFeatureTest {
    @Test
    void rejectsAValidSchemaWithABlackboxSlotAtItsQualifiedPath() {
        var schema = compileInline("Payload", """
                INTERLIS 2.4;
                MODEL Payload AT "https://example.org" VERSION "1" =
                  DOMAIN Raw = BLACKBOX XML;
                  TOPIC Data =
                    CLASS Entry =
                      label : TEXT*20;
                      body : Raw;
                    END Entry;
                  END Data;
                END Payload.
                """);
        var fingerprint = schema.fingerprint();
        var result = assertInstanceOf(TransferPlanningResult.Unsupported.class, TransferPlanner.plan(schema));
        assertEquals(List.of(new TransferPlanningResult.Issue(QualifiedName.of("Payload", "Data", "Entry", "body"),
                TransferPlanningResult.Feature.VALUE_TYPE)), result.issues());
        assertThrows(UnsupportedOperationException.class, () -> result.issues().clear());
        assertEquals(fingerprint, schema.fingerprint());
    }
}
