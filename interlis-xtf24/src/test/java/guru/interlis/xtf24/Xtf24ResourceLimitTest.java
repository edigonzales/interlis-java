package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24ResourceLimitTest {
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
    void enforcesScalarBoundaryAcrossParserTextChunksInBothDirections() {
        var limits = new Xtf24Limits(128, 64, 4096, 1_000_000, 16_777_216, 8_388_608, 1_000_000);
        String exact = document("<x>" + "r".repeat(64) + "</x>");
        readLimited(limits, exact);
        writeLimited(limits, exact);
        String tooLong = document("<x>" + "r".repeat(65) + "</x>");
        exceeds(() -> readLimited(limits, tooLong));
        exceeds(() -> writeLimited(limits, tooLong));
        String chunks = document("<x>" + "<![CDATA[".concat("r".repeat(20)).concat("]]>").repeat(4) + "</x>");
        exceeds(() -> readLimited(limits, chunks));
    }

    @Test
    void enforcesDepthEventAndAggregateObjectLimits() {
        var depth = new Xtf24Limits(5, 1024, 4096, 1_000_000, 16_777_216, 8_388_608, 1_000_000);
        readLimited(depth, document("<x>ok</x>"));
        exceeds(() -> readLimited(depth, document("<z><nested/></z>")));
        exceeds(() -> writeLimited(depth, document("<z><nested/></z>")));

        var events = new Xtf24Limits(128, 1024, 4096, 20, 16_777_216, 8_388_608, 1_000_000);
        readLimited(events, document("<y>a</y>".repeat(3)));
        exceeds(() -> readLimited(events, document("<y>a</y>".repeat(12))));
        exceeds(() -> writeLimited(events, document("<y>a</y>".repeat(12))));

        var volume = new Xtf24Limits(128, 1024, 4096, 1_000_000, 1000, 8_388_608, 1_000_000);
        readLimited(volume, document(("<y>" + "r".repeat(100) + "</y>").repeat(3)));
        exceeds(() -> readLimited(volume, document(("<y>" + "r".repeat(100) + "</y>").repeat(12))));
        exceeds(() -> writeLimited(volume, document(("<y>" + "r".repeat(100) + "</y>").repeat(12))));
    }

    @Test
    void enforcesIdentityIdentifierAndParserAdvanceLimits() {
        var identities = new Xtf24Limits(128, 1024, 4096, 1_000_000, 16_777_216, 8_388_608, 1);
        exceeds(() -> readLimited(identities, document("")));
        exceeds(() -> writeLimited(identities, document("")));

        var identifier = new Xtf24Limits(128, 1024, 16, 1_000_000, 16_777_216, 8_388_608, 1_000_000);
        String atLimit = document("").replace("i:tid=\"one\"", "i:tid=\"" + "i".repeat(16) + "\"");
        readLimited(identifier, atLimit);
        writeLimited(identifier, atLimit);
        String above = atLimit.replace("i".repeat(16), "i".repeat(17));
        exceeds(() -> readLimited(identifier, above));
        exceeds(() -> writeLimited(identifier, above));

        var advance = new Xtf24Limits(128, 4096, 4096, 1_000_000, 16_777_216, 512, 1_000_000);
        String largeComment = document("").replace("<T ", "<!--" + "x".repeat(2000) + "--><T ");
        exceeds(() -> readLimited(advance, largeComment));
        byte[] bytes = largeComment.getBytes(StandardCharsets.UTF_8);
        var singleByte = new ByteArrayInputStream(bytes) {
            @Override public synchronized int read(byte[] target, int offset, int length) {
                return super.read(target, offset, Math.min(1, length));
            }
        };
        try (var reader = new Xtf24Reader(plan, singleByte, Optional.empty(), advance)) {
            exceeds(() -> {
                while (reader.readNext(CancellationToken.none()).isPresent()) {}
            });
        }
    }

    @Test
    void enforcesHeaderAndNamespaceBudgetsBeforeObjectsExist() {
        var limits = new Xtf24Limits(128, 4096, 4096, 1_000_000, 1000, 8_388_608, 1_000_000);
        String header = document("").replace("<i:models>", "<!--" + "c".repeat(1100) + "--><i:models>");
        exceeds(() -> readLimited(limits, header));
        var namespaces = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            namespaces.append(" xmlns:p").append(i).append("=\"urn:").append(i).append("\"");
        }
        exceeds(() -> readLimited(Xtf24Limits.defaults(), document("").replace("<i:transfer ", "<i:transfer" + namespaces + " ")));
    }

    static String document(String values) {
        return """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:m">
                  <i:headersection><i:models><i:model>M</i:model></i:models></i:headersection>
                  <i:datasection><T i:bid="b"><C i:tid="one">%s</C></T></i:datasection>
                </i:transfer>
                """.formatted(values);
    }

    private static void readLimited(Xtf24Limits limits, String xml) {
        try (var reader = new Xtf24Reader(plan, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                Optional.empty(), limits)) {
            while (reader.readNext(CancellationToken.none()).isPresent()) {}
        }
    }

    private static void writeLimited(Xtf24Limits limits, String xml) {
        var events = read(plan, xml);
        try (var writer = new Xtf24Writer(plan, new ByteArrayOutputStream(), limits)) {
            events.forEach(writer::write);
        }
    }

    private static void exceeds(org.junit.jupiter.api.function.Executable operation) {
        assertEquals(Xtf24Exception.Reason.LIMIT_EXCEEDED,
                assertThrows(Xtf24Exception.class, operation).reason());
    }
}
