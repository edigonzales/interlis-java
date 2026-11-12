package guru.interlis.validation;

import guru.interlis.model.AlignmentKind;
import guru.interlis.model.AlignmentType;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.FormattedType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextLengths;
import guru.interlis.model.TextType;
import guru.interlis.transfer.TransferValue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static guru.interlis.validation.ValidationCode.*;

final class ScalarRules {
    private static final Set<String> HORIZONTAL = Set.of("Left", "Center", "Right");
    private static final Set<String> VERTICAL = Set.of("Top", "Cap", "Half", "Base", "Bottom");

    private ScalarRules() { }

    static int textLength(String text) {
        if (text.length() > 1_000_000) {
            throw new ValidationLimitException("Text exceeds one million UTF-16 code units");
        }
        return TextLengths.canonicalLength(text);
    }

    static Set<List<String>> enumerationValues(EnumerationType type) {
        var inner = new HashSet<String>();
        if (!type.treeValues()) {
            for (var value : type.values()) {
                int separator = value.path().lastIndexOf('.');
                if (separator >= 0) {
                    inner.add(value.path().substring(0, separator));
                }
            }
        }
        return type.values().stream().filter(value -> !inner.contains(value.path()))
                .map(value -> List.of(value.path().split("\\."))).collect(Collectors.toUnmodifiableSet());
    }

    enum Coverage { CHECKED, UNCHECKED, NOT_APPLICABLE }

    static Coverage check(ObjectValidationPlan.ValueRules rules, TransferValue value, int length,
                          LanguageVersion language, Optional<String> charset,
                          BiConsumer<ValidationCode, List<String>> report) {
        switch (rules.type()) {
            case TextType type -> {
                return text(type, ((TransferValue.Text) value).value(), length, language, charset, report);
            }
            case NumericType type -> {
                checkNumber(type.range(), ((TransferValue.NumberValue) value).exactValue(), report);
                return Coverage.CHECKED;
            }
            case EnumerationType _ -> {
                if (!rules.enumerationValues().contains(((TransferValue.Enumeration) value).path())) {
                    report.accept(ENUM_VALUE_NOT_ALLOWED, List.of());
                }
                return Coverage.CHECKED;
            }
            case AlignmentType alignment -> {
                var path = ((TransferValue.Enumeration) value).path();
                var allowed = alignment.alignment() == AlignmentKind.HORIZONTAL ? HORIZONTAL : VERTICAL;
                if (path.size() != 1 || !allowed.contains(path.getFirst())) {
                    report.accept(ENUM_VALUE_NOT_ALLOWED, List.of(alignment.alignment().name()));
                }
                return Coverage.CHECKED;
            }
            case FormattedType type -> {
                return TemporalRules.check(type, ((TransferValue.Text) value).value(), language, report);
            }
            default -> {
                return Coverage.NOT_APPLICABLE;
            }
        }
    }

    private static Coverage text(TextType type, String value, int length, LanguageVersion language,
                                 Optional<String> charset, BiConsumer<ValidationCode, List<String>> report) {
        var kind = type.textKind();
        if (kind == TextKind.NAME && !TextRules.validName(value, language)) {
            report.accept(INVALID_NAME, List.of());
        }
        if (kind == TextKind.URI && !TextRules.validUri(value)) {
            report.accept(INVALID_URI, List.of());
        }
        if (type.maxLength().isPresent() && length > type.maxLength().getAsInt()) {
            report.accept(TEXT_TOO_LONG, List.of(Integer.toString(length), Integer.toString(type.maxLength().getAsInt())));
        }
        return switch (TextRules.repertoire(value, kind, language, charset)) {
            case ACCEPTED -> Coverage.CHECKED;
            case UNKNOWN -> Coverage.UNCHECKED;
            case REJECTED -> {
                report.accept(TEXT_CHARACTER_NOT_ALLOWED, List.of());
                yield Coverage.CHECKED;
            }
        };
    }

    static void checkNumber(NumericRange range, BigDecimal actual,
                            BiConsumer<ValidationCode, List<String>> report) {
        requireNumericBudget(actual);
        var minimum = range.minimum().map(DecimalLiteral::exactValue);
        var maximum = range.maximum().map(DecimalLiteral::exactValue);
        minimum.ifPresent(ScalarRules::requireNumericBudget);
        maximum.ifPresent(ScalarRules::requireNumericBudget);
        // INTERLIS 2.3 §3.3.11.4 and 2.4 §4.3.11.4 permit higher transfer
        // precision as long as the exact value stays within its range.
        if (minimum.filter(bound -> actual.compareTo(bound) < 0).isPresent()
                || maximum.filter(bound -> actual.compareTo(bound) > 0).isPresent()) {
            report.accept(VALUE_OUT_OF_RANGE, List.of(example(actual), minimum.map(ScalarRules::example).orElse("*"),
                    maximum.map(ScalarRules::example).orElse("*")));
        }
    }

    private static void requireNumericBudget(BigDecimal value) {
        if (value.unscaledValue().bitLength() > 16_384 || Math.abs((long) value.scale()) > 10_000) {
            throw new ValidationLimitException("Numeric magnitude or scale exceeds the local comparison budget");
        }
    }

    private static String example(BigDecimal value) {
        var display = value.toString();
        return display.length() <= 120 ? display : display.substring(0, 120) + "…";
    }
}
