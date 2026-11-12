package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentityIndexTest {
    @Test
    void separatesInputsAndKeepsStableCollisionsAmbiguous() {
        try (var storage = new InMemoryValidationStorage(20, 1_000)) {
            var index = new IdentityIndex(storage);
            var one = new ObjectHandle(1);
            var two = new ObjectHandle(2);
            var three = new ObjectHandle(3);
            index.addTemporary(1, "same", one);
            index.addTemporary(2, "same", two);
            index.addStable("space", "A", three);
            index.addStable("space", "A", one);
            index.addStable("space", "A\uffff", two);
            index.addStable("other", "A", two);

            assertEquals(new IdentityIndex.Found(one), index.temporaryTarget(1, "same"));
            assertEquals(new IdentityIndex.Found(two), index.temporaryTarget(2, "same"));
            assertEquals(IdentityIndex.Missing.INSTANCE, index.temporaryTarget(3, "same"));
            assertEquals(new IdentityIndex.Ambiguous(one, three), index.stableTarget("space", "A"));
            assertEquals(new IdentityIndex.Found(two), index.stableTarget("space", "A\uffff"));
            assertEquals(new IdentityIndex.Found(two), index.stableTarget("other", "A"));
        }
    }
}
