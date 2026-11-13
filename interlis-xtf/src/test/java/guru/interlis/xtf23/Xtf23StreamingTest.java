package guru.interlis.xtf23;

import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Enumeration;

import static guru.interlis.xtf23.Xtf23Exception.Reason.LIMIT_EXCEEDED;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

final class Xtf23StreamingTest {
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() throws IOException {
        plan = referencePlan();
    }

    @Test
    void deliversObjectsWhileMostOfTheTransferHasNotBeenGenerated() throws IOException {
        String open = document(MODELS, "").split("</DATASECTION>", 2)[0] + "<Reference23.Records BID=\"basket\">";
        final int objectCount = 8000;
        class Pieces implements Enumeration<InputStream> {
            int piece;

            @Override
            public boolean hasMoreElements() {
                return piece <= objectCount + 1;
            }

            @Override
            public ByteArrayInputStream nextElement() {
                int index = piece++;
                if (index == 0) {
                    return input(open);
                }
                if (index <= objectCount) {
                    return input("<Reference23.Records.Parent TID=\"p" + index + "\"/>");
                }
                return input("</Reference23.Records></DATASECTION></TRANSFER>");
            }
        }
        var pieces = new Pieces();
        try (var source = new SequenceInputStream(pieces); var reader = new Xtf23Reader(plan, source)) {
            assertInstanceOf(TransferEvent.StartTransfer.class, reader.readNext(TOKEN).orElseThrow());
            assertInstanceOf(TransferEvent.StartBasket.class, reader.readNext(TOKEN).orElseThrow());
            for (int i = 1; i <= objectCount; i++) {
                var object = assertInstanceOf(TransferEvent.ObjectEvent.class, reader.readNext(TOKEN).orElseThrow());
                assertEquals("p" + i, object.object().identity().tid().orElseThrow().value());
                if (i == 1 || i == objectCount / 2) {
                    assertTrue(pieces.piece < i + 500, "Reader generated too many objects before delivery");
                }
            }
            assertInstanceOf(TransferEvent.EndBasket.class, reader.readNext(TOKEN).orElseThrow());
            assertInstanceOf(TransferEvent.EndTransfer.class, reader.readNext(TOKEN).orElseThrow());
            assertTrue(reader.readNext(TOKEN).isEmpty());
        }
    }

    @Test
    void limitsTheSumOfTextInOneObjectEvenWhenEachValueFits() {
        String description = "<Reference23.Records.Detail><description>" + "x".repeat(Xtf23Input.MAX_TEXT)
                + "</description></Reference23.Records.Detail>";
        String xml = document(MODELS, """
                <Reference23.Records BID="basket"><Reference23.Records.Item TID="item">
                  <details>%s</details>
                </Reference23.Records.Item></Reference23.Records>
                """.formatted(description.repeat(Xtf23Input.MAX_OBJECT_UNITS / Xtf23Input.MAX_TEXT + 1)));
        try (var reader = new Xtf23Reader(plan, input(xml))) {
            reader.readNext(TOKEN);
            reader.readNext(TOKEN);
            var failure = assertThrows(Xtf23Exception.class, () -> reader.readNext(TOKEN));
            assertEquals(LIMIT_EXCEEDED, failure.reason());
            assertTrue(failure.getMessage().contains("object exceeds"), failure::getMessage);
        }
    }
}
