package guru.interlis.xtf23;

import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static guru.interlis.transfer.TransferIdentity.*;
import static guru.interlis.xtf23.Xtf23Exception.Reason.DUPLICATE_ID;
import static guru.interlis.xtf23.Xtf23Exception.Reason.INVALID_WIRE;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

final class Xtf23IdentityScopeTest {
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() throws IOException {
        plan = referencePlan();
    }

    @Test
    void detectsBothCollisionOrdersAcrossBaskets() {
        for (String baskets : List.of(
                """
                <Reference23.Records BID="same"/>
                <Reference23.Records BID="other"><Reference23.Records.Parent TID="same"/></Reference23.Records>
                """,
                """
                <Reference23.Records BID="other"><Reference23.Records.Parent TID="same"/></Reference23.Records>
                <Reference23.Records BID="same"/>
                """,
                """
                <Reference23.Records BID="same"><Reference23.Records.Parent TID="same"/></Reference23.Records>
                """)) {
            try (var reader = new Xtf23Reader(plan, input(document(MODELS, baskets)))) {
                assertEquals(DUPLICATE_ID, assertThrows(Xtf23Exception.class, () -> readAll(reader)).reason());
            }
        }
    }

    @Test
    void detectsDuplicateObjectAndBasketIdsAtTheirOpeningElements() {
        String repeatedObject = document(MODELS, """
                <Reference23.Records BID="first">
                  <Reference23.Records.Parent TID="same"/>
                </Reference23.Records>
                <Reference23.Records BID="second">
                  <Reference23.Records.Parent TID="same">
                    <unknown/>
                  </Reference23.Records.Parent>
                </Reference23.Records>
                """);
        assertDuplicateAt(repeatedObject, "<Reference23.Records.Parent TID=\"same\">");
        try (var reader = new Xtf23Reader(plan, input(repeatedObject.replace("TID=\"same\">", "TID=\"fresh\">")))) {
            assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class, () -> readAll(reader)).reason());
        }

        String repeatedBasket = document(MODELS, """
                <Reference23.Records BID="same"/>
                <Reference23.Records BID="same">
                  <Reference23.Records.Parent TID="fresh"/>
                </Reference23.Records>
                """);
        assertDuplicateAt(repeatedBasket, "<Reference23.Records BID=\"same\">");
    }

    private static void assertDuplicateAt(String xml, String openingElement) {
        long line = xml.lines().takeWhile(text -> !text.contains(openingElement)).count() + 1;
        try (var reader = new Xtf23Reader(plan, input(xml))) {
            var failure = assertThrows(Xtf23Exception.class, () -> readAll(reader));
            assertEquals(DUPLICATE_ID, failure.reason());
            assertEquals(line, failure.line());
        }
    }

    @Test
    void preservesDistinctLifetimesAndChecksTheSpellingIndependentlyOfOrigin() {
        for (var basketOrigin : IdOrigin.values()) {
            for (var tidOrigin : IdOrigin.values()) {
                var basket = new BasketId("same", basketOrigin);
                var object = new TransferId("same", tidOrigin);
                var bidFirst = new Xtf23IdentityScope();
                assertTrue(bidFirst.claim(basket));
                assertFalse(bidFirst.claim(object));
                var tidFirst = new Xtf23IdentityScope();
                assertTrue(tidFirst.claim(object));
                assertFalse(tidFirst.claim(basket));
            }
        }
        String document = document(MODELS, """
                <Reference23.Records BID="one">
                  <Reference23.Records.Parent TID="two" BID="one"/>
                </Reference23.Records>
                """);
        for (int i = 0; i < 2; i++) {
            try (var reader = new Xtf23Reader(plan, input(document))) {
                assertInstanceOf(TransferEvent.EndTransfer.class, readAll(reader).getLast());
            }
        }
    }
}
