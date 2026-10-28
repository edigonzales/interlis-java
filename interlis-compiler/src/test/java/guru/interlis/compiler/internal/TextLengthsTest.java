package guru.interlis.compiler.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextLengthsTest {
    @Test void canonicalLengthCountsStartersAndPreservesCompatibilityCharacters() {
        assertEquals(1,TextLengths.canonicalLength("é"));
        assertEquals(1,TextLengths.canonicalLength("e\u0301"));
        assertEquals(1,TextLengths.canonicalLength("C\u0302\u0327"));
        assertEquals(1,TextLengths.canonicalLength("\uFB03")); // NFD, never NFKD.
        assertEquals(1,TextLengths.canonicalLength("\u034F")); // CCC=0 even though Mn.
        assertEquals(0,TextLengths.canonicalLength("\u0301"));
        assertEquals(1,TextLengths.canonicalLength("🥳"));
        assertEquals(2,TextLengths.canonicalLength("가"));
        assertEquals(3,TextLengths.canonicalLength("각"));
        assertEquals(3,TextLengths.canonicalLength("\u1100\u1161\u11A8"));
        assertEquals(2,TextLengths.canonicalLength("\u09CB"));
        assertEquals(0,TextLengths.canonicalLength(""));
    }
}
