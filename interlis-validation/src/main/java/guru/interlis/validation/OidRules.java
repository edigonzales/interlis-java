package guru.interlis.validation;

import guru.interlis.model.NumericType;
import guru.interlis.model.OidType;
import guru.interlis.model.Origin;
import guru.interlis.model.TextType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

final class OidRules {
    private static final Pattern TEXT = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]*");
    private static final Pattern STANDARD = Pattern.compile("[A-Za-z][A-Za-z0-9]{15}");
    private static final Pattern UUID = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    private OidRules() { }

    /** Returns false when no concrete OID domain is available to interpret the identifier. */
    static boolean check(ValidationPlan plan, OidType type, String identifier,
                         BiConsumer<ValidationCode, List<String>> report) {
        if (identifier.length() > 4096) {
            throw new ValidationLimitException("OID exceeds the local identifier budget");
        }
        String value = identifier.substring(identifier.indexOf(':') + 1);
        switch (type.oidKind()) {
            case ANY -> {
                return false;
            }
            case UUID -> {
                if (!UUID.matcher(value).matches()) {
                    invalid(report);
                }
            }
            case TEXT -> {
                if (type.origin() instanceof Origin.PredefinedOrigin predefined
                        && predefined.key().endsWith("STANDARDOID")) {
                    if (!STANDARD.matcher(value).matches()) {
                        invalid(report);
                    }
                } else if (type.valueType().flatMap(plan::effectiveType).orElse(null) instanceof TextType text) {
                    if (!TEXT.matcher(value).matches() || text.maxLength().isPresent()
                            && ScalarRules.textLength(value) > text.maxLength().getAsInt()) {
                        invalid(report);
                    }
                } else {
                    return false;
                }
            }
            case NUMERIC -> {
                if (!(type.valueType().flatMap(plan::effectiveType).orElse(null) instanceof NumericType numeric)) {
                    return false;
                }
                if (!DECIMAL.matcher(value).matches()) {
                    invalid(report);
                } else {
                    BigDecimal number;
                    try {
                        number = new BigDecimal(value);
                    } catch (NumberFormatException exceeded) {
                        throw new ValidationLimitException("OID decimal exponent exceeds the local budget");
                    }
                    ScalarRules.checkNumber(numeric.range(), number, report);
                }
            }
        }
        return true;
    }

    private static void invalid(BiConsumer<ValidationCode, List<String>> report) {
        report.accept(ValidationCode.INVALID_OID, List.of());
    }
}
