package guru.interlis.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerContractCatalogTest {
    @TempDir Path root;

    @Test void rejectsDuplicateKeysAndUnknownSchemaBeforeResolvingInputs() throws IOException {
        Path catalog = Files.createDirectories(root.resolve("testdata/verification")).resolve("compiler-contracts.json");
        Files.writeString(catalog, """
                {"schemaVersion":1,"bindings":{"duplicate":{},"duplicate":{}}}
                """);
        var duplicate = assertThrows(IllegalArgumentException.class, () -> CompilerContractCatalog.load(root));
        assertTrue(duplicate.getMessage().contains("Duplicate compiler catalog key"));

        Files.writeString(catalog, "{\"schemaVersion\":2}");
        var version = assertThrows(IllegalArgumentException.class, () -> CompilerContractCatalog.load(root));
        assertTrue(version.getMessage().contains("Unsupported compiler catalog schema"));
    }
}
