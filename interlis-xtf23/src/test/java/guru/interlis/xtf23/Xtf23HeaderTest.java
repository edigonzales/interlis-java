package guru.interlis.xtf23;

import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferProtocolException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

final class Xtf23HeaderTest {
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() throws IOException {
        plan = referencePlan();
    }

    @Test
    void readsHeaderAndFirstObjectFromPinnedIoxFixture() throws IOException {
        var source = new SourceId("memory:/identity.xtf");
        try (var input = Files.newInputStream(fixture("identity.xtf"));
             var reader = new Xtf23Reader(plan, input, Optional.of(source))) {
            var start = assertInstanceOf(TransferEvent.StartTransfer.class, reader.readNext(TOKEN).orElseThrow());
            assertEquals(Optional.of("iox-reference"), start.sender());
            assertEquals(Optional.of(source), start.source());
            assertEquals(1, start.models().size());
            var model = start.models().getFirst();
            assertEquals("Reference23", plan.model(model.model()).orElseThrow().name().declared().toString());
            assertEquals(Optional.of("2026-11-06"), model.modelVersion());
            assertEquals(Optional.of("https://example.invalid/iox-reference"), model.issuer());
            var basket = assertInstanceOf(TransferEvent.StartBasket.class, reader.readNext(TOKEN).orElseThrow());
            assertEquals("records-1", basket.id().value());
            var parent = assertInstanceOf(TransferEvent.ObjectEvent.class, reader.readNext(TOKEN).orElseThrow());
            assertEquals("parent-1", parent.object().identity().tid().orElseThrow().value());
            assertEquals(Optional.of(source), parent.object().identity().source());
            assertEquals(basket.id(), parent.object().identity().currentBasket());
        }
    }

    @Test
    void readsEmptyAndNonemptyBasketsWithNamespacedTagsAndHeaderMetadata() {
        String xml = document(MODELS + """
                <ALIAS/>
                <OIDSPACES><OIDSPACE NAME="stable" OIDDOMAIN="INTERLIS.UUIDOID"/></OIDSPACES>
                <COMMENT>one &amp; two&#13;</COMMENT>
                """, """
                <Reference23.Records BID="1"/>
                <Reference23.Records BID="stable:2" KIND="FULL" CONSISTENCY="ADAPTED">
                  <Reference23.Records.Parent TID="stable:3" BID="1" CONSISTENCY="INCOMPLETE"/>
                </Reference23.Records>
                """).replace("<TRANSFER xmlns=", "<i:TRANSFER xmlns:i=")
                .replace("</TRANSFER>", "</i:TRANSFER>")
                .replace("<HEADERSECTION", "<HEADERSECTION xmlns=\"http://www.interlis.ch/INTERLIS2.3\"")
                .replace("<DATASECTION", "<DATASECTION xmlns=\"http://www.interlis.ch/INTERLIS2.3\"");
        try (var reader = new Xtf23Reader(plan, input(xml))) {
            assertTrue(reader.oidSpaces().isEmpty());
            var events = readAll(reader);
            assertEquals(List.of(TransferEvent.StartTransfer.class, TransferEvent.StartBasket.class,
                    TransferEvent.EndBasket.class, TransferEvent.StartBasket.class, TransferEvent.ObjectEvent.class,
                    TransferEvent.EndBasket.class, TransferEvent.EndTransfer.class),
                    events.stream().map(Object::getClass).toList());
            var transfer = (TransferEvent.StartTransfer) events.getFirst();
            assertEquals(Optional.of("test & sender"), transfer.sender());
            assertEquals(Optional.of("one & two\r"), transfer.comment());
            assertEquals(List.of(new Xtf23OidSpace("stable", "INTERLIS.UUIDOID")), reader.oidSpaces());
            var emptyBasket = (TransferEvent.StartBasket) events.get(1);
            assertEquals(TransferEvent.Consistency.COMPLETE, emptyBasket.consistency());
            var secondBasket = (TransferEvent.StartBasket) events.get(3);
            assertEquals(Optional.of("stable:2"), secondBasket.stableOid());
            assertEquals(TransferEvent.Consistency.ADAPTED, secondBasket.consistency());
            var parent = (TransferEvent.ObjectEvent) events.get(4);
            assertEquals(TransferEvent.Consistency.INCOMPLETE, parent.consistency());
            assertEquals(emptyBasket.id(), parent.object().identity().originalBasket().orElseThrow());
            assertEquals("stable:3", parent.object().identity().oid().orElseThrow().value());
        }
    }

    @Test
    void requiresVersionSenderModelsAndConsistentDeclarations() {
        String valid = document(MODELS, "");
        for (String xml : List.of(
                valid.replace("INTERLIS2.3", "INTERLIS2.4"),
                valid.replace("VERSION=\"2.3\"", "VERSION=\"2.4\""),
                valid.replace(" SENDER=\"test &amp; sender\"", ""),
                valid.replace(MODELS, ""),
                document("<MODELS/>", ""),
                valid.replace("Reference23", "Unknown"),
                valid.replace("2026-11-06", "2026-10-06"),
                valid.replace("https://example.invalid/iox-reference", "https://example.invalid/other"),
                valid.replace("</MODELS>", MODELS.substring(MODELS.indexOf("<MODEL "), MODELS.indexOf("</MODELS>"))
                        + "</MODELS>"),
                valid.replace("<DATASECTION>", "<UNKNOWN>"), valid.substring(PROLOG.length()))) {
            assertFailure(xml, INVALID_WIRE);
        }
    }

    @Test
    void rejectsUnsupportedHeaderAndIncrementalBasket() {
        assertFailure(document(MODELS + "<ALIAS><ENTRIES FOR=\"Reference23\"/></ALIAS>", ""), UNSUPPORTED);
        assertFailure(document(MODELS, "<Reference23.Records BID=\"1\" KIND=\"UPDATE\"/>"), UNSUPPORTED);
        assertFailure(document(MODELS + "<FUTURE_HEADER/>", ""), INVALID_WIRE);
    }

    @Test
    void rejectsUnknownTopicsAndInconsistentBasketContext() {
        assertFailure(document(MODELS, "<Reference23.Unknown BID=\"1\"/>"), INVALID_WIRE);
        assertFailure(document(MODELS, "<Reference23.Records BID=\"x:1\"/>"), INVALID_WIRE);
        try (var reader = new Xtf23Reader(plan, input(document(MODELS,
                "<Reference23.Records BID=\"1\" TOPICS=\"Reference23.Records\"/>")))) {
            var failure = assertThrows(TransferProtocolException.class, () -> readAll(reader));
            assertEquals(TransferProtocolException.Reason.CONTEXT_MISMATCH, failure.reason());
        }
    }

    private static void assertFailure(String xml, Xtf23Exception.Reason expected) {
        try (var reader = new Xtf23Reader(plan, input(xml))) {
            var failure = assertThrows(Xtf23Exception.class, () -> readAll(reader));
            assertEquals(expected, failure.reason(), failure::getMessage);
        }
    }
}
