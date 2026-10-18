package guru.interlis.source;

/** Lower-case hexadecimal encoding without platform API dependencies. */
public final class Hex {
    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    /** Encodes all bytes as lower-case hexadecimal. */
    public static String encode(byte[] bytes) {
        return encode(bytes, bytes.length);
    }

    /** Encodes the first {@code length} bytes as lower-case hexadecimal. */
    public static String encode(byte[] bytes, int length) {
        StringBuilder out = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 0xff;
            out.append(DIGITS[value >>> 4]).append(DIGITS[value & 0x0f]);
        }
        return out.toString();
    }
}
