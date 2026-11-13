package guru.interlis.validation.mvstore;

import guru.interlis.validation.InMemoryValidationStorage;
import guru.interlis.validation.ValidationCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.validation.mvstore.StorageParityTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeStorageParityTest {
    @TempDir Path directory;

    @Test
    void preservesGroupedViewAndReferenceResultsAcrossBackends() throws Exception {
        var model = fixture("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    CLASS Target = amount: 0..10; END Target;
                    CLASS Item =
                      key: TEXT;
                      peer: REFERENCE TO Target;
                      mirrored (TRANSIENT): 0..10 := peer->amount;
                      MANDATORY CONSTRAINT mirrored > 1;
                    END Item;
                    VIEW Grouped AGGREGATION OF item~Item EQUAL (item->key); =
                      size := INTERLIS.elementCount(AGGREGATES);
                      MANDATORY CONSTRAINT size >= 2;
                    END Grouped;
                  END Data;
                END Checks.
                """);
        var data = """
                <Data i:bid='basket'>
                  <Item i:tid='a'><key>group</key><peer i:ref='late'/></Item>
                  <Item i:tid='b'><key>group</key><peer i:ref='late'/></Item>
                  <Item i:tid='c'><key>other</key><peer i:ref='late'/></Item>
                  <Target i:tid='late'><amount>3</amount></Target>
                </Data>
                """;
        try (var memory = new InMemoryValidationStorage(10_000, 10_000_000);
             var disk = MvStoreValidationStorage.open(directory, MvStoreOptions.defaults(64 * 1024 * 1024))) {
            var expected = validate(model, memory, data);
            var actual = validate(model, disk, data);
            assertEquals(expected, actual);
            assertEquals(memory.usage(), disk.usage());
            assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE),
                    actual.examples().stream().map(example -> example.code()).toList());
            assertEquals("c", actual.examples().getFirst().data().tid().orElseThrow().value());
        }
        try (var children = Files.list(directory)) {
            assertEquals(0, children.count());
        }
    }
}
