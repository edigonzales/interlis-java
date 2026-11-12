package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.RelationCardinalityTest.validateModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionTest {
    private static final String MODEL = """
            CLASS Node = END Node;
            ASSOCIATION First =
              parent -<#> {0..1} Node;
              parts -- {0..*} Node;
            END First;
            ASSOCIATION Second =
              otherParent -<#> {0..1} Node;
              otherParts -- {0..*} Node;
            END Second;
            """;

    @Test
    void detectsCyclesOnlyAlongCompositionEdges() {
        var data = "<Node i:tid='a'><parent i:ref='b'/></Node>"
                + "<Node i:tid='b'><parent i:ref='a'/></Node>";
        assertEquals(List.of(ValidationCode.COMPOSITION_CYCLE), codes(MODEL, data));
        assertTrue(codes(MODEL.replace("-<#>", "--"), data).isEmpty());
    }

    @Test
    void detectsDifferentOwnersAcrossTwoDeclarations() {
        var owners = "<Node i:tid='a'/><Node i:tid='c'/>";
        var independent = owners + "<Node i:tid='b'><parent i:ref='a'/></Node>"
                + "<Node i:tid='d'><otherParent i:ref='c'/></Node>";
        assertTrue(codes(MODEL, independent).isEmpty());
        var conflict = owners + "<Node i:tid='b'><otherParent i:ref='c'/><parent i:ref='a'/></Node>";
        assertEquals(List.of(ValidationCode.COMPOSITION_MULTIPLE_OWNERS), codes(MODEL, conflict));
    }

    private static List<ValidationCode> codes(String model, String data) {
        return validateModel(model, "COMPLETE", data).examples().stream().map(ValidationDiagnostic::code).toList();
    }

}
