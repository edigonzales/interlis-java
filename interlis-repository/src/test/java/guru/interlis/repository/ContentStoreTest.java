package guru.interlis.repository;

import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentStoreTest {
    private static final byte[] BYTES = "MODEL".getBytes(StandardCharsets.UTF_8);

    @Test
    void storesAndLooksUpVerifiedContent() {
        ContentStore store = new InMemoryContentStore();
        ContentDigest digest = ContentDigest.sha256(BYTES);
        store.put(new VerifiedContent(new SourceId("memory:/a.ili"), digest, BYTES));
        assertTrue(store.lookup(digest).isPresent());
        assertFalse(store.lookup(ContentDigest.sha256("other".getBytes(
                StandardCharsets.UTF_8))).isPresent());
    }

    @Test
    void rejectsTamperedContent() {
        ContentDigest digest = ContentDigest.sha256(BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                new VerifiedContent(new SourceId("memory:/a.ili"), digest,
                        "tampered".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () ->
                new VerifiedContent(new SourceId("memory:/a.ili"),
                        new ContentDigest("sha256", "0".repeat(64)), BYTES));
    }

    @Test
    void defensiveCopiesBytes() {
        byte[] input = BYTES.clone();
        VerifiedContent content = new VerifiedContent(new SourceId("memory:/a.ili"),
                ContentDigest.sha256(input), input);
        input[0] = 'X';
        assertEquals("MODEL", new String(content.bytes(), StandardCharsets.UTF_8));
    }
}
