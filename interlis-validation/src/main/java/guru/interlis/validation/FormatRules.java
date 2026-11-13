package guru.interlis.validation;

import guru.interlis.model.FormattedType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.internal.FormattedValues;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;

final class FormatRules {
    private static final int MAX_TEXT_LENGTH = 4_096;

    private FormatRules() { }

    static ScalarRules.Coverage check(ValidationPlan plan, FormattedType type, String text, Runnable visit,
                                      BiConsumer<ValidationCode, List<String>> report) {
        if (parse(plan, type, text, visit).isEmpty()) {
            report.accept(ValidationCode.INVALID_FORMATTED_VALUE, List.of(type.formatName()));
        }
        return ScalarRules.Coverage.CHECKED;
    }

    static OptionalInt compare(ValidationPlan plan, FormattedType leftType, FormattedType rightType,
                               String left, String right, Runnable visit) {
        if (leftType.predefined().isPresent() && leftType.predefined().equals(rightType.predefined())) {
            var kind = leftType.predefined().orElseThrow();
            try {
                var lhs = TemporalRules.parse(left, kind, LanguageVersion.ILI23);
                var rhs = TemporalRules.parse(right, kind, LanguageVersion.ILI23);
                return OptionalInt.of(lhs.compareTo(rhs));
            } catch (java.time.DateTimeException invalid) {
                return OptionalInt.empty();
            }
        }
        var lhs = parse(plan, leftType, left, visit);
        var rhs = parse(plan, rightType, right, visit);
        return lhs.isPresent() && rhs.isPresent()
                ? OptionalInt.of(FormattedValues.compare(lhs.orElseThrow(), rhs.orElseThrow())) : OptionalInt.empty();
    }

    static Optional<List<BigDecimal>> parse(ValidationPlan plan, FormattedType type, String text, Runnable visit) {
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ValidationLimitException("Formatted value exceeds the validation length limit");
        }
        var values = new FormattedValues(symbol -> plan.valueType(symbol).orElse(null), visit, true);
        try {
            var parsed = values.parse(type, text, true);
            parsed.ifPresent(parts -> parts.forEach(ScalarRules::requireNumericBudget));
            return parsed;
        } catch (StackOverflowError exceeded) {
            throw new ValidationLimitException("Formatted value exceeds the validation stack limit");
        }
    }
}
