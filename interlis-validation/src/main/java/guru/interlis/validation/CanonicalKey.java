package guru.interlis.validation;

import java.util.Arrays;

final class CanonicalKey implements Comparable<CanonicalKey> {
    private final byte[] bytes;

    CanonicalKey(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    int size() {
        return bytes.length;
    }

    byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public int compareTo(CanonicalKey other) {
        return Arrays.compareUnsigned(bytes, other.bytes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CanonicalKey key && Arrays.equals(bytes, key.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
