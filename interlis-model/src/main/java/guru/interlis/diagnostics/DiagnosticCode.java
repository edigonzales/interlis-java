package guru.interlis.diagnostics;

import java.util.Objects;
import java.util.regex.Pattern;

/** Published, stable diagnostic classification such as {@code ILI-SEM-0001}. */
public record DiagnosticCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("ILI-[A-Z]+-[0-9]{4}");

    /** Creates a validated instance. */
    public DiagnosticCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid diagnostic code: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
