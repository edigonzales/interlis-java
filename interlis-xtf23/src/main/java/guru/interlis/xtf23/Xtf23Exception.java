package guru.interlis.xtf23;

/** An XML, wire format, capability or I/O failure at the XTF 2.3 boundary. */
public final class Xtf23Exception extends IllegalStateException {
    /** Distinguishes format failures from an unsupported feature or host failure. */
    public enum Reason {
        MALFORMED_XML, UNSAFE_XML, INVALID_WIRE, UNSUPPORTED, LIMIT_EXCEEDED, IO, CONFIGURATION
    }

    private final Reason reason;
    private final int line;
    private final int column;

    Xtf23Exception(Reason reason, String message, int line, int column, Throwable cause) {
        super(line > 0 ? message + " (line " + line + ", column " + column + ")" : message, cause);
        this.reason = reason;
        this.line = line;
        this.column = column;
    }

    /** Returns the stable failure category. */
    public Reason reason() {
        return reason;
    }

    /** Returns the one-based XML line, or -1 when unavailable. */
    public int line() {
        return line;
    }

    /** Returns the one-based XML column, or -1 when unavailable. */
    public int column() {
        return column;
    }
}
