package guru.interlis.xtf23;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferProtocolException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

final class Xtf23StreamLifecycleTest {
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() throws IOException {
        plan = referencePlan();
    }

    @Test
    void confirmsCompleteXmlBeforeEndTransferAndLeavesInputOpen() {
        var source = new ObservedInput(document(MODELS, "") + "<!-- well-formed tail -->");
        try (var reader = new Xtf23Reader(plan, source)) {
            assertEquals(source.originalLength, source.available());
            assertInstanceOf(TransferEvent.StartTransfer.class, reader.readNext(TOKEN).orElseThrow());
            assertInstanceOf(TransferEvent.EndTransfer.class, reader.readNext(TOKEN).orElseThrow());
            assertTrue(reader.readNext(TOKEN).isEmpty());
            assertTrue(reader.readNext(TOKEN).isEmpty());
            assertEquals(0, source.available());
        }
        assertEquals(0, source.closes);
    }

    @Test
    void rejectsPrematureEofMisnestingAndTrailingContent() {
        String valid = document(MODELS, "");
        for (String broken : new String[] {
                valid.replace("</TRANSFER>", ""),
                valid.replace("</DATASECTION>", "</HEADERSECTION>"),
                valid + "<another/>",
                valid + "unexpected"
        }) {
            try (var reader = new Xtf23Reader(plan, input(broken))) {
                assertInstanceOf(TransferEvent.StartTransfer.class, reader.readNext(TOKEN).orElseThrow());
                assertEquals(MALFORMED_XML, assertThrows(Xtf23Exception.class,
                        () -> reader.readNext(TOKEN)).reason());
                assertEquals(TransferProtocolException.Reason.FAILED,
                        assertThrows(TransferProtocolException.class, () -> reader.readNext(TOKEN)).reason());
            }
        }
    }

    @Test
    void rejectsDoctypeAndDoesNotRequireResolvingSchemaHints() {
        String valid = document(MODELS, "");
        for (String declaration : new String[] {
                "<!DOCTYPE TRANSFER SYSTEM \"file:///does-not-exist\">",
                "<!DOCTYPE TRANSFER [<!ENTITY x SYSTEM \"http://127.0.0.1:1/never\">]>"
        }) {
            try (var reader = new Xtf23Reader(plan, input(valid.replace(PROLOG, PROLOG + declaration)))) {
                assertEquals(UNSAFE_XML, assertThrows(Xtf23Exception.class, () -> reader.readNext(TOKEN)).reason());
            }
        }
        try (var reader = new Xtf23Reader(plan, input(valid.replace("<TRANSFER ",
                "<TRANSFER xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"http://www.interlis.ch/INTERLIS2.3 http://127.0.0.1:1/never\" ")))) {
            assertInstanceOf(TransferEvent.EndTransfer.class, readAll(reader).getLast());
        }
    }

    @Test
    void supportsPreCancellationAndAbandoningABorrowedStream() {
        var source = new ObservedInput(document(MODELS, ""));
        var reader = new Xtf23Reader(plan, source);
        try {
            var cancelled = CancellationToken.requested(CancellationReason.USER_REQUESTED);
            assertThrows(OperationCancelledException.class, () -> reader.readNext(cancelled));
            assertEquals(source.originalLength, source.available());
            assertInstanceOf(TransferEvent.StartTransfer.class, reader.readNext(TOKEN).orElseThrow());
        } finally {
            reader.close();
            reader.close();
        }
        assertEquals(0, source.closes);
        assertEquals(TransferProtocolException.Reason.CLOSED,
                assertThrows(TransferProtocolException.class, () -> reader.readNext(TOKEN)).reason());
    }

    @Test
    void treatsCancellationDuringInputAsTerminal() {
        var source = new ObservedInput(document(MODELS, ""));
        try (var reader = new Xtf23Reader(plan, source)) {
            assertThrows(OperationCancelledException.class,
                    () -> reader.readNext(() -> source.available() < source.originalLength));
            assertEquals(TransferProtocolException.Reason.FAILED,
                    assertThrows(TransferProtocolException.class, () -> reader.readNext(TOKEN)).reason());
        }
        assertEquals(0, source.closes);
    }

    @Test
    void distinguishesIoFailuresAndTextLimitsFromXmlErrors() {
        try (var reader = new Xtf23Reader(plan, new InputStream() {
            @Override public int read() throws IOException { throw new IOException("source failed"); }
        })) {
            assertEquals(IO, assertThrows(Xtf23Exception.class, () -> reader.readNext(TOKEN)).reason());
        }
        try (var reader = new Xtf23Reader(plan, input(document(MODELS
                + "<COMMENT>" + "x".repeat(Xtf23Input.MAX_TEXT + 1) + "</COMMENT>", "")))) {
            assertEquals(LIMIT_EXCEEDED, assertThrows(Xtf23Exception.class, () -> reader.readNext(TOKEN)).reason());
        }
        try (var reader = new Xtf23Reader(plan, input(document(MODELS, "").replace(PROLOG,
                PROLOG + "<!--" + "x".repeat(Xtf23Input.MAX_TOKEN_BYTES + 1) + "-->")))) {
            assertEquals(LIMIT_EXCEEDED, assertThrows(Xtf23Exception.class, () -> reader.readNext(TOKEN)).reason());
        }
    }

    @Test
    void requiresUtf8AndRejectsInvalidUtf8AsMalformedXml() {
        String valid = document(MODELS, "");
        var utf16 = valid.replace("UTF-8", "UTF-16").getBytes(StandardCharsets.UTF_16);
        try (var reader = new Xtf23Reader(plan, new ByteArrayInputStream(utf16))) {
            assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class, () -> reader.readNext(TOKEN)).reason());
        }
        byte[] invalid = valid.replace("test &amp; sender", "~").getBytes(StandardCharsets.UTF_8);
        invalid[valid.replace("test &amp; sender", "~").indexOf('~')] = (byte) 0xff;
        try (var reader = new Xtf23Reader(plan, new ByteArrayInputStream(invalid))) {
            assertEquals(MALFORMED_XML, assertThrows(Xtf23Exception.class, () -> reader.readNext(TOKEN)).reason());
        }
    }

    private static final class ObservedInput extends ByteArrayInputStream {
        private final int originalLength;
        private int closes;

        private ObservedInput(String xml) {
            super(xml.getBytes(StandardCharsets.UTF_8));
            originalLength = available();
        }

        @Override
        public void close() {
            closes++;
        }
    }
}
