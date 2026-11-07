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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    @Test
    void attachesTheCurrentXmlPositionToASharedEncodingFailure() throws javax.xml.stream.XMLStreamException {
        var cursor = new Xtf23Input(input(document(MODELS, "")));
        try {
            cursor.open();
            cursor.nextTag();
            cursor.nextTag();
            var original = Xtf23Output.error(UNSUPPORTED, "Unsupported model geometry");
            var situated = cursor.locate(original);
            assertEquals(UNSUPPORTED, situated.reason());
            assertTrue(situated.line() > 0);
            assertTrue(situated.column() > 0);
            assertSame(original, situated.getCause());
            assertSame(situated, cursor.locate(situated));
        } finally {
            cursor.close();
        }
    }

    @Test
    void writerFlushesTheCompletedDocumentAndLeavesItsOutputOpen() {
        var source = new ObservedOutput();
        try (var writer = new Xtf23Writer(plan, source)) {
            assertEquals(0, source.size());
            writer.write(Xtf23WriterRoundTripTest.start(plan, "writer"));
            writer.write(new TransferEvent.EndTransfer());
            assertTrue(source.flushes > 0);
        }
        assertEquals(0, source.closes);
        try (var reader = new Xtf23Reader(plan, new ByteArrayInputStream(source.toByteArray()))) {
            assertInstanceOf(TransferEvent.EndTransfer.class, readAll(reader).getLast());
        }
    }

    @Test
    void writerDoesNotCompleteAnAbandonedOrCancelledDocument() {
        var source = new ObservedOutput();
        var writer = new Xtf23Writer(plan, source);
        var start = Xtf23WriterRoundTripTest.start(plan, "writer");
        assertThrows(OperationCancelledException.class,
                () -> writer.write(start, CancellationToken.requested(CancellationReason.USER_REQUESTED)));
        assertEquals(0, source.size());
        writer.write(start);
        assertEquals(TransferProtocolException.Reason.INCOMPLETE_TRANSFER,
                assertThrows(TransferProtocolException.class, writer::close).reason());
        writer.close();
        assertEquals(0, source.closes);
        assertFalse(source.toString(StandardCharsets.UTF_8).contains("</TRANSFER>"));

        var during = new ObservedOutput();
        try (var cancelling = new Xtf23Writer(plan, during)) {
            assertThrows(OperationCancelledException.class, () -> {
                var token = (CancellationToken) () -> during.size() > 0;
                cancelling.write(start, token);
                cancelling.write(new TransferEvent.EndTransfer(), token);
            });
            assertEquals(TransferProtocolException.Reason.FAILED,
                    assertThrows(TransferProtocolException.class,
                            () -> cancelling.write(new TransferEvent.EndTransfer())).reason());
        }
        assertEquals(0, during.closes);
        assertFalse(during.toString(StandardCharsets.UTF_8).contains("</TRANSFER>"));
    }

    @Test
    void writerMakesAStreamFailureTerminalWithoutClosingTheOutput() {
        var partial = new ObservedOutput();
        var broken = new OutputStream() {
            private boolean failed;

            @Override
            public void write(int value) throws IOException {
                if (partial.size() == 100 && !failed) {
                    failed = true;
                    throw new IOException("output failed");
                }
                partial.write(value);
            }

            @Override
            public void close() {
                partial.close();
            }
        };
        try (var writer = new Xtf23Writer(plan, broken)) {
            assertEquals(IO, assertThrows(Xtf23Exception.class, () -> {
                writer.write(Xtf23WriterRoundTripTest.start(plan, "writer"));
                writer.write(new TransferEvent.EndTransfer());
            }).reason());
            assertEquals(TransferProtocolException.Reason.FAILED,
                    assertThrows(TransferProtocolException.class,
                            () -> writer.write(new TransferEvent.EndTransfer())).reason());
        }
        assertTrue(partial.size() > 0);
        assertEquals(0, partial.closes);
        assertFalse(partial.toString(StandardCharsets.UTF_8).contains("</TRANSFER>"));
    }

    private static final class ObservedOutput extends ByteArrayOutputStream {
        private int closes;
        private int flushes;

        @Override
        public void close() {
            closes++;
        }

        @Override
        public void flush() {
            flushes++;
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
