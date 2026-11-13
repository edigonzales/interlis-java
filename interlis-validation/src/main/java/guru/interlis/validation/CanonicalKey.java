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
        int shared = Math.min(bytes.length, other.bytes.length);
        for (int i = 0; i < shared; i++) {
            int comparison = Integer.compare(Byte.toUnsignedInt(bytes[i]), Byte.toUnsignedInt(other.bytes[i]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(bytes.length, other.bytes.length);
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
