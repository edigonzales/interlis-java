package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.RelationCardinalityTest.validateModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedRelationTest {
    private static final String MODEL = """
            CLASS Owner = END Owner;
            CLASS Item = END Item;
            ASSOCIATION Links =
              owner -- {0..*} Owner;
              items (ORDERED) -- {0..*} Item;
            END Links;
            """;

    @Test
    void keepsIndependentOwnerListsAndExactLargePositions() {
        var objects = "<Owner i:tid='o1'/><Owner i:tid='o2'/><Item i:tid='i1'/><Item i:tid='i2'/>";
        var valid = objects + link("o1", "i1", "1") + link("o2", "i2", "1") + link("o1", "i2", "2");
        assertTrue(validateModel(MODEL, "COMPLETE", valid).examples().isEmpty());
        var large = "18446744073709551619";
        var invalid = objects + link("o1", "i1", large) + link("o1", "i2", large) + link("o2", "i1", "1");
        var complete = validateModel(MODEL, "COMPLETE", invalid);
        assertEquals(List.of(ValidationCode.ORDER_POSITION_GAP, ValidationCode.ORDER_POSITION_DUPLICATE),
                complete.examples().stream().map(ValidationDiagnostic::code).toList());
        assertEquals(List.of("1", large), complete.examples().getFirst().arguments());
        var incomplete = validateModel(MODEL, "INCOMPLETE", invalid);
        assertEquals(List.of(ValidationCode.ORDER_POSITION_DUPLICATE),
                incomplete.examples().stream().map(ValidationDiagnostic::code).toList());
    }

    @Test
    void checksPositionsOfTheImplicitOrderedRoleOfAnEmbeddedLink() {
        String embedded = MODEL.replace("owner -- {0..*}", "owner -- {0..1}");
        String objects = """
                <Owner i:tid="o1"/><Owner i:tid="o2"/>
                <Item i:tid="i1"><owner i:ref="o1" i:order_pos="%s"/></Item>
                <Item i:tid="i2"><owner i:ref="o2" i:order_pos="1"/></Item>
                <Item i:tid="i3"><owner i:ref="o1" i:order_pos="1"/></Item>
                """;
        assertTrue(validateModel(embedded, "COMPLETE", objects.formatted("2")).examples().isEmpty());
        var duplicate = validateModel(embedded, "COMPLETE", objects.formatted("1"));
        assertEquals(List.of(ValidationCode.ORDER_POSITION_DUPLICATE),
                duplicate.examples().stream().map(ValidationDiagnostic::code).toList());
        var gap = validateModel(embedded, "COMPLETE", objects.formatted("3"));
        assertEquals(List.of(ValidationCode.ORDER_POSITION_GAP),
                gap.examples().stream().map(ValidationDiagnostic::code).toList());
        assertEquals(List.of("2", "3"), gap.examples().getFirst().arguments());
    }

    private static String link(String owner, String item, String position) {
        return "<Links><owner i:ref='%s'/><items i:ref='%s' i:order_pos='%s'/></Links>"
                .formatted(owner, item, position);
    }
}
