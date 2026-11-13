package guru.interlis.xtf23;

import guru.interlis.model.OidType;
import guru.interlis.transfer.TransferValue.NumberValue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Lexical rules for XTF values, independent of model constraints. */
final class Xtf23Lexemes {
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern STRUCTURED_NUMBER = Pattern.compile("[+-]?[0-9]++(?::[0-9]++)++(?:\\.[0-9]++)?");
    static final int MAX_NUMERIC_LENGTH = 4096;
    static final int MAX_MODEL_PATH_LENGTH = 4096;
    static final Pattern POSITION = Pattern.compile("[0-9]+");

    private Xtf23Lexemes() { }

    static boolean singleLine(String value) {
        return value.indexOf('\r') < 0 && value.indexOf('\n') < 0 && value.indexOf('\t') < 0;
    }

    static boolean enumeration(String value) {
        return value.length() <= 4096 && Arrays.stream(value.split("\\.", -1))
                .allMatch(part -> !part.equals("OTHERS") && NAME.matcher(part).matches());
    }

    static boolean decimal(String value) {
        return DECIMAL.matcher(value).matches();
    }

    static boolean oidValue(OidType type, String identifier) {
        int separator = identifier.indexOf(':');
        String local = identifier.substring(separator + 1);
        return switch (type.oidKind()) {
            case ANY -> separator >= 0;
            case NUMERIC -> decimal(local);
            case TEXT -> local.charAt(0) < '0' || local.charAt(0) > '9';
            case UUID -> true;
        };
    }

    static boolean modelPath(List<String> segments, boolean attribute) {
        int minimum = attribute ? 3 : 2;
        return segments.size() >= minimum && segments.size() <= minimum + 1
                && segments.stream().allMatch(part -> NAME.matcher(part).matches());
    }

    static NumberValue number(String value) {
        return new NumberValue(value, new BigDecimal(value));
    }

    static boolean nonDecimalNumber(String value) {
        return value.equals("PI") || value.equals("LNBASE")
                || (value.indexOf(':') >= 0 && STRUCTURED_NUMBER.matcher(value).matches());
    }
}
