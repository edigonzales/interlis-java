package guru.interlis.transfer;

import org.junit.jupiter.api.Test;

import static guru.interlis.transfer.TransferTestSchemas.compileInline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TransferPlannerValueTypesTest {
    @Test
    void plansBlackboxesOidPathsAndGeometryWithLineAttributesFromInterlis23() {
        var schema = compileInline("FullValues", """
                INTERLIS 2.3;
                MODEL FullValues (en) AT "https://example.org" VERSION "1" =
                  DOMAIN
                    Position = COORD 0.000 .. 200.000 [INTERLIS.m], 0.000 .. 200.000 [INTERLIS.m];
                    TextId = OID TEXT*24;
                    NumberId = OID 0..1000;
                    OpenId = OID ANY;
                  TOPIC Data =
                    STRUCTURE Marks =
                      label : MANDATORY TEXT*20;
                    END Marks;
                    CLASS Entry =
                      document : BLACKBOX XML;
                      payload : BLACKBOX BINARY;
                      textId : TextId;
                      numberId : NumberId;
                      openId : OpenId;
                      uuid : INTERLIS.UUIDOID;
                      className : CLASS;
                      structureName : STRUCTURE;
                      attributeName : ATTRIBUTE;
                      trace : POLYLINE WITH (STRAIGHTS) VERTEX Position;
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Position WITHOUT OVERLAPS > 0.001 LINE ATTRIBUTES Marks;
                      parcel : AREA WITH (STRAIGHTS) VERTEX Position WITHOUT OVERLAPS > 0.001 LINE ATTRIBUTES Marks;
                    END Entry;
                  END Data;
                END FullValues.
                """);
        var fingerprint = schema.fingerprint();
        assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema));
        assertEquals(fingerprint, schema.fingerprint());
    }
}
