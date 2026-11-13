package guru.interlis.xtf24;

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
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24ResourceLimitTest.document;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24SecurityAndLifecycleTest {
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() {
        plan = plan("""
                INTERLIS 2.4;
                MODEL M AT "https://example.org" VERSION "1" =
                  XMLNS "urn:m";
                  TOPIC T = CLASS C = x: TEXT; y: LIST OF TEXT; z: BLACKBOX XML; END C; END T;
                END M.
                """, "M");
    }

    @Test
    void rejectsDoctypeWithoutContactingTheCanaryAndIgnoresSchemaHints() throws Exception {
        try (var server = new ServerSocket(0, 1, java.net.InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
            server.setSoTimeout(250);
            var contacts = new AtomicInteger();
            var canary = new Thread(() -> {
                try (var socket = server.accept()) {
                    contacts.incrementAndGet();
                } catch (SocketTimeoutException expected) {
                    // No attempted external XML access.
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                }
            });
            canary.start();
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/entity";
            for (String doctype : java.util.List.of(
                    "<!DOCTYPE transfer SYSTEM \"" + url + "\">",
                    "<!DOCTYPE transfer [<!ENTITY e SYSTEM \"" + url + "\">]>",
                    "<!DOCTYPE transfer [<!ENTITY a \"x\"><!ENTITY b \"&a;&a;&a;\">]>")) {
                try (var reader = reader(doctype + document("<x>&amp;</x>"))) {
                    assertEquals(Xtf24Exception.Reason.UNSAFE_XML,
                            assertThrows(Xtf24Exception.class, () -> reader.readNext(CancellationToken.none())).reason());
                    assertEquals(TransferProtocolException.Reason.FAILED,
                            assertThrows(TransferProtocolException.class, () -> reader.readNext(CancellationToken.none())).reason());
                }
            }
            String hint = document("").replace("<i:transfer ", "<i:transfer xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                    + " xsi:schemaLocation=\"urn:m " + url + "\" ");
            assertEquals(5, read(plan, hint).size());
            canary.join(1500);
            assertFalse(canary.isAlive());
            assertEquals(0, contacts.get());
        }
    }

    @Test
    void preservesIoCausesAndMakesFailedStreamsTerminal() {
        var readFailure = new IOException("read-canary");
        var input = new java.io.InputStream() {
            @Override public int read() throws IOException { throw readFailure; }
        };
        try (var reader = new Xtf24Reader(plan, input)) {
            var failure = assertThrows(Xtf24Exception.class, () -> reader.readNext(CancellationToken.none()));
            assertEquals(Xtf24Exception.Reason.IO, failure.reason());
            assertSame(readFailure, rootCause(failure));
            assertEquals(TransferProtocolException.Reason.FAILED,
                    assertThrows(TransferProtocolException.class, () -> reader.readNext(CancellationToken.none())).reason());
        }
        for (boolean flushOnly : new boolean[]{false, true}) {
            var writeFailure = new IOException(flushOnly ? "flush-canary" : "write-canary");
            var closes = new AtomicInteger();
            var output = new java.io.OutputStream() {
                @Override public void write(int value) throws IOException {
                    if (!flushOnly) throw writeFailure;
                }
                @Override public void flush() throws IOException {
                    if (flushOnly) throw writeFailure;
                }
                @Override public void close() { closes.incrementAndGet(); }
            };
            try (var writer = new Xtf24Writer(plan, output)) {
                var failure = assertThrows(Xtf24Exception.class,
                        () -> read(plan, document("")).forEach(writer::write));
                assertEquals(Xtf24Exception.Reason.IO, failure.reason());
                assertSame(writeFailure, rootCause(failure));
                assertEquals(TransferProtocolException.Reason.FAILED,
                        assertThrows(TransferProtocolException.class, () -> writer.write(new TransferEvent.EndTransfer())).reason());
            }
            assertEquals(0, closes.get());
        }
    }

    @Test
    void preservesMidOutputFailureAndHonorsWriterPreCancellation() {
        var events = read(plan, document("<y>payload</y>".repeat(2000)));
        var bytes = new ByteArrayOutputStream();
        var marker = new IOException("mid-output-canary");
        var output = new java.io.OutputStream() {
            @Override public void write(int value) throws IOException {
                if (bytes.size() == 1500) {
                    throw marker;
                }
                bytes.write(value);
            }
        };
        try (var writer = new Xtf24Writer(plan, output)) {
            assertThrows(OperationCancelledException.class, () -> writer.write(events.getFirst(), () -> true));
            assertEquals(0, bytes.size());
            var failure = assertThrows(Xtf24Exception.class, () -> events.forEach(writer::write));
            assertEquals(Xtf24Exception.Reason.IO, failure.reason());
            assertSame(marker, rootCause(failure));
            assertEquals(1500, bytes.size());
        }
    }

    @Test
    void cancelsDuringFragmentCopyAndRejectsFurtherWrites() {
        var events = read(plan, document("<z><payload>" + "text".repeat(3000) + "</payload></z>"));
        var calls = new AtomicInteger();
        try (var writer = new Xtf24Writer(plan, new ByteArrayOutputStream())) {
            writer.write(events.get(0));
            writer.write(events.get(1));
            assertThrows(OperationCancelledException.class,
                    () -> writer.write(events.get(2), () -> calls.incrementAndGet() > 12));
            assertEquals(TransferProtocolException.Reason.FAILED,
                    assertThrows(TransferProtocolException.class, () -> writer.write(events.get(2))).reason());
        }
    }

    @Test
    void observesCancellationDuringCollectionsAndLeavesCallerStreamsOpen() {
        var input = new ByteArrayInputStream(document("<y>value</y>".repeat(5000)).getBytes(StandardCharsets.UTF_8));
        var calls = new AtomicInteger();
        try (var reader = new Xtf24Reader(plan, input)) {
            reader.readNext(CancellationToken.none());
            reader.readNext(CancellationToken.none());
            assertThrows(OperationCancelledException.class, () -> reader.readNext(() -> calls.incrementAndGet() > 50));
            assertEquals(TransferProtocolException.Reason.FAILED,
                    assertThrows(TransferProtocolException.class, () -> reader.readNext(CancellationToken.none())).reason());
        }
        assertTrue(input.available() > 0);
    }

    @Test
    void boundsReadAheadAndKeepsErrorsLocatedAndMessagesBounded() {
        byte[] large = document("<x>" + "a".repeat(200_000) + "</x>").getBytes(StandardCharsets.UTF_8);
        var input = new ByteArrayInputStream(large);
        try (var reader = new Xtf24Reader(plan, input)) {
            assertEquals(large.length, input.available());
            assertInstanceOf(TransferEvent.StartTransfer.class, reader.readNext(CancellationToken.none()).orElseThrow());
            assertTrue(large.length - input.available() < 16_384);
        }
        String broken = document("<unknown xmlns=\"" + "u".repeat(10_000) + "\"/>");
        try (var reader = reader(broken)) {
            reader.readNext(CancellationToken.none());
            reader.readNext(CancellationToken.none());
            var failure = assertThrows(Xtf24Exception.class, () -> reader.readNext(CancellationToken.none()));
            assertEquals(Xtf24Exception.Reason.INVALID_WIRE, failure.reason());
            assertTrue(failure.line() > 0);
            assertTrue(failure.column() > 0);
            assertTrue(failure.getMessage().length() < 600);
        }
    }

    @Test
    void rejectsTruncatedRootsBeforePublishingEndTransferAndRejectsInvalidUtf8() {
        String truncated = document("").replace("</i:transfer>", "");
        var delivered = new java.util.ArrayList<TransferEvent>();
        try (var reader = reader(truncated)) {
            assertEquals(Xtf24Exception.Reason.MALFORMED_XML, assertThrows(Xtf24Exception.class, () -> {
                while (true) {
                    var event = reader.readNext(CancellationToken.none());
                    if (event.isEmpty()) break;
                    delivered.add(event.orElseThrow());
                }
            }).reason());
        }
        assertFalse(delivered.stream().anyMatch(TransferEvent.EndTransfer.class::isInstance));
        byte[] invalid = document("<x>?</x>").getBytes(StandardCharsets.UTF_8);
        int marker = document("<x>?</x>").indexOf('?');
        invalid[marker] = (byte) 0xff;
        try (var reader = new Xtf24Reader(plan, new ByteArrayInputStream(invalid))) {
            assertEquals(Xtf24Exception.Reason.MALFORMED_XML, assertThrows(Xtf24Exception.class, () -> {
                while (reader.readNext(CancellationToken.none()).isPresent()) {}
            }).reason());
        }
    }

    @Test
    void closesAbandonedAdaptersIdempotentlyWithoutClosingCallerStreams() {
        var input = new ByteArrayInputStream(document("").getBytes(StandardCharsets.UTF_8)) {
            int closes;
            @Override public void close() { closes++; }
        };
        var reader = new Xtf24Reader(plan, input);
        reader.readNext(CancellationToken.none());
        reader.close();
        reader.close();
        assertEquals(0, input.closes);
        assertEquals(TransferProtocolException.Reason.CLOSED,
                assertThrows(TransferProtocolException.class, () -> reader.readNext(CancellationToken.none())).reason());
        var output = new ByteArrayOutputStream() {
            int closes;
            @Override public void close() { closes++; }
        };
        var writer = new Xtf24Writer(plan, output);
        writer.write(read(plan, document("")).getFirst());
        assertEquals(TransferProtocolException.Reason.INCOMPLETE_TRANSFER,
                assertThrows(TransferProtocolException.class, writer::close).reason());
        writer.close();
        assertEquals(0, output.closes);
        assertFalse(output.toString(StandardCharsets.UTF_8).contains("</ili:transfer>"));
    }

    @Test
    void completesOnlyAtEndTransferAndClosesIdempotentlyWithoutClosingTheCaller() {
        var output = new ByteArrayOutputStream() {
            int flushes;
            int closes;
            @Override public void flush() { flushes++; }
            @Override public void close() { closes++; }
        };
        var writer = new Xtf24Writer(plan, output);
        assertEquals(0, output.size());
        var events = read(plan, document(""));
        events.subList(0, events.size() - 1).forEach(writer::write);
        assertEquals(0, output.flushes);
        writer.write(events.getLast());
        assertTrue(output.flushes > 0);
        writer.close();
        writer.close();
        assertEquals(0, output.closes);
    }

    private static Xtf24Reader reader(String xml) {
        return new Xtf24Reader(plan, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Throwable rootCause(Throwable failure) {
        while (failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }
}
