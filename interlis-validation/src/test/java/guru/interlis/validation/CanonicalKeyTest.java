package guru.interlis.validation;

import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.plan;
import static guru.interlis.validation.DatasetRuleTestSupport.type;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalKeyTest {
    private static final ValidationPlan PLAN = plan("""
            DOMAIN FirstEnum = (a(b, bc)); SecondEnum = (a(b, bc));
            CLASS Keys =
              text: TEXT*20;
              number: -10.0..10.0;
              first: FirstEnum;
              second: SecondEnum;
            END Keys;
            """);
    private static final ValueSemantics VALUES = ValueSemantics.local(PLAN, false, () -> { });

    @Test
    void normalizesExactNumbersWithoutConvertingThemToMachineNumbers() {
        assertEquals(key("number", number("1")), key("number", number("1.00")));
        assertEquals(key("number", number("-2.0")), key("number", number("-2.000")));
        assertEquals(key("number", number("0.00")), key("number", number("-0.0")));
        assertNotEquals(key("number", number("1000000000000000000001")),
                key("number", number("1000000000000000000000")));
        assertEquals(key("number", number("1E+999")), key("number", number("10E+998")));
    }

    @Test
    void preservesComponentBoundariesEnumDomainsAndExactText() {
        var ab = key("text", new TransferValue.Text("ab"));
        var c = key("text", new TransferValue.Text("c"));
        var a = key("text", new TransferValue.Text("a"));
        var bc = key("text", new TransferValue.Text("bc"));
        assertNotEquals(VALUES.tuple(List.of(ab, c)), VALUES.tuple(List.of(a, bc)));
        assertNotEquals(key("text", new TransferValue.Text("é")), key("text", new TransferValue.Text("é")));
        var first = new TransferValue.Enumeration(List.of("a", "b"));
        var other = new TransferValue.Enumeration(List.of("a", "bc"));
        assertNotEquals(key("first", first), key("first", other));
        assertNotEquals(key("first", first), key("second", first));
    }

    @Test
    void comparesAllBytesEvenWhenHashesMatchAndOwnsItsArray() {
        byte[] input = {0, 31};
        var left = new CanonicalKey(input);
        input[1] = 0;
        var right = new CanonicalKey(new byte[]{1, 0});
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, right);
        byte[] exported = left.bytes();
        exported[1] = 0;
        assertArrayEquals(new byte[]{0, 31}, left.bytes());
    }

    @Test
    void rejectsExcessiveKeysBeforeAppendingComponents() {
        var limited = new ValueSemantics(PLAN, 12, false, guru.interlis.model.LanguageVersion.ILI24,
                () -> { }, (reference, type) -> java.util.Optional.empty(), value -> new StableIdentity("", value));
        var component = new CanonicalKey(new byte[8]);
        assertThrows(ValidationLimitException.class, () -> limited.tuple(List.of(component)));
    }

    private static CanonicalKey key(String name, TransferValue value) {
        var owner = PLAN.transferPlan().object(type(PLAN, "Keys")).orElseThrow();
        var slot = (guru.interlis.transfer.AttributeSlot) owner.slots().stream()
                .filter(candidate -> candidate.name().transfer().segments().getLast().equals(name)).findFirst().orElseThrow();
        var descriptor = PLAN.effectiveType(slot.effectiveType().type()).orElseThrow();
        return assertInstanceOf(ValueSemantics.Known.class,
                VALUES.key(new LocalExpressions.Known(value), descriptor)).key();
    }

    private static TransferValue.NumberValue number(String lexeme) {
        return new TransferValue.NumberValue(lexeme, new BigDecimal(lexeme));
    }
}
