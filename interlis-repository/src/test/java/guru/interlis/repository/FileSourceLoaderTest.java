package guru.interlis.repository;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSourceLoaderTest {
    @TempDir
    Path directory;

    @Test
    void loadsUtf8WithByteProvenance() throws Exception {
        Path file = directory.resolve("model.ili");
        Files.writeString(file, "INTERLIS 2.4;", StandardCharsets.UTF_8);
        FileSourceLoader loader = new FileSourceLoader(directory);
        LoadedSource loaded = loader.load(new SourceLocator("model.ili"),
                FetchPolicy.defaults(), CancellationToken.none());
        assertEquals("INTERLIS 2.4;", loaded.text());
        assertEquals(64, loaded.byteSha256().length());
        assertTrue(loaded.sourceId().value().startsWith("file:"));
    }

    @Test
    void rejectsMissingInvalidAndOversizedSources() throws Exception {
        FileSourceLoader loader = new FileSourceLoader(directory);
        SourceLoadException missing = assertThrows(SourceLoadException.class, () -> loader.load(
                new SourceLocator("absent.ili"), FetchPolicy.defaults(),
                CancellationToken.none()));
        assertEquals(SourceLoadException.Reason.NOT_FOUND, missing.reason());

        Path invalid = directory.resolve("invalid.ili");
        Files.write(invalid, new byte[]{(byte) 0xC3, 0x28});
        SourceLoadException encoding = assertThrows(SourceLoadException.class, () -> loader.load(
                new SourceLocator("invalid.ili"), FetchPolicy.defaults(),
                CancellationToken.none()));
        assertEquals(SourceLoadException.Reason.INVALID_ENCODING, encoding.reason());

        Path large = directory.resolve("large.ili");
        Files.writeString(large, "0123456789", StandardCharsets.UTF_8);
        SourceLoadException oversized = assertThrows(SourceLoadException.class, () -> loader.load(
                new SourceLocator("large.ili"),
                new FetchPolicy(false, 4, Duration.ofSeconds(1)), CancellationToken.none()));
        assertEquals(SourceLoadException.Reason.TOO_LARGE, oversized.reason());
    }

    @Test
    void rejectsOfflineCancelledAndInvalidPath() {
        FileSourceLoader loader = new FileSourceLoader(directory);
        SourceLoadException offline = assertThrows(SourceLoadException.class, () -> loader.load(
                new SourceLocator("model.ili"), new FetchPolicy(true, 100, Duration.ofSeconds(1)),
                CancellationToken.none()));
        assertEquals(SourceLoadException.Reason.OFFLINE, offline.reason());
        assertThrows(OperationCancelledException.class, () -> loader.load(
                new SourceLocator("model.ili"), FetchPolicy.defaults(),
                CancellationToken.requested(CancellationReason.USER_REQUESTED)));
        SourceLoadException invalid = assertThrows(SourceLoadException.class, () -> loader.load(
                new SourceLocator("\u0000"), FetchPolicy.defaults(), CancellationToken.none()));
        assertEquals(SourceLoadException.Reason.IO_FAILURE, invalid.reason());
    }
}
