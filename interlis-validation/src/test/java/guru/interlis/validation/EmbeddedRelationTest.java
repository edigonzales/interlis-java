package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddedRelationTest {
    @Test
    void storesEmbeddedAndStandaloneLinksWithBothEndpoints() {
        for (boolean embedded : List.of(true, false)) {
            var schema = compile("""
                    INTERLIS 2.4;
                    MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                      TOPIC Data =
                        CLASS Parent = END Parent;
                        CLASS Item = END Item;
                        ASSOCIATION Link =
                          parent -- {0..%s} Parent;
                          items -- {0..*} Item;
                          note : TEXT*3;
                        END Link;
                      END Data;
                    END Checks.
                    """.formatted(embedded ? "1" : "*"));
            var transfer = transfer(schema);
            var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                    ValidationPlanner.plan(transfer, ValidationProfile.DATASET)).plan();
            var payload = embedded
                    ? """
                      <Item i:tid="i1"><parent i:ref="p1"><Link><note>over</note></Link></parent></Item>
                      """
                    : """
                      <Item i:tid="i1"/>
                      <Link><parent i:ref="p1"/><items i:ref="i1"/><note>over</note></Link>
                      """;
            var xml = """
                    <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                                xmlns="http://www.interlis.ch/xtf/2.4/Checks">
                      <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                      <i:datasection><Data i:bid="b1"><Parent i:tid="p1"/>%s</Data></i:datasection>
                    </i:transfer>
                    """.formatted(payload);
            try (var store = new InMemoryValidationStorage(100, 100_000);
                 var run = DatasetValidator.session(plan, DatasetOptions.defaults(plan), store);
                 var reader = new Xtf24Reader(transfer, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
                var token = CancellationToken.none();
                run.readInput(new InputDescriptor(new InputId("primary"), InputRole.PRIMARY), reader, token);
                var report = run.finish(token);
                assertEquals(List.of(ValidationCode.TEXT_TOO_LONG),
                        report.examples().stream().map(ValidationDiagnostic::code).toList());
                try (var links = store.table(StorageArea.LINKS).scan(new byte[0], Optional.empty())) {
                    var codec = new StoredValueCodec(plan.snapshotId(), 100_000);
                    var link = codec.decodeLink(links.next().orElseThrow().value());
                    assertEquals(embedded, link.embeddedField().isPresent());
                    assertEquals(2, link.endpoints().size());
                    assertEquals("p1", link.endpoints().getFirst().reference().orElseThrow().target().value());
                    if (embedded) {
                        assertEquals(Optional.of(link.source()), link.endpoints().getLast().implicitTarget());
                    } else {
                        assertEquals("i1", link.endpoints().getLast().reference().orElseThrow().target().value());
                    }
                    assertTrue(links.next().isEmpty());
                }
            }
        }
    }
}
