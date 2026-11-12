package guru.interlis.validation;

import guru.interlis.model.FormattedType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.TemporalKind;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static guru.interlis.validation.ScalarRules.Coverage.*;
import static guru.interlis.validation.ValidationCode.*;

final class TemporalRules {
    private static final Pattern DATE_23 = Pattern.compile("([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})");
    private static final Pattern TIME_23 = Pattern.compile("([0-9]{2}):([0-9]{1,2}):([0-9]{1,2})(?:\\.([0-9]{1,3}))?");
    private static final Pattern DATE_24 = Pattern.compile("([0-9]{4})-([0-9]{2})-([0-9]{2})");
    private static final Pattern TIME_24 = Pattern.compile("([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\\.([0-9]{1,3}))?");

    private TemporalRules() { }

    static ScalarRules.Coverage check(FormattedType type, String value, LanguageVersion language,
                                      BiConsumer<ValidationCode, List<String>> report) {
        if (type.predefined().isEmpty()) {
            return UNCHECKED;
        }
        var kind = type.predefined().orElseThrow();
        if (type.parts().size() != (kind == TemporalKind.DATETIME ? 11 : 5)) {
            return UNCHECKED;
        }
        final LocalDateTime lower;
        final LocalDateTime upper;
        try {
            lower = type.minimum().map(text -> parse(text, kind, language)).orElse(null);
            upper = type.maximum().map(text -> parse(text, kind, language)).orElse(null);
        } catch (DateTimeException invalidBound) {
            return UNCHECKED;
        }
        try {
            var parsed = parse(value, kind, language);
            if (lower != null && parsed.isBefore(lower) || upper != null && parsed.isAfter(upper)) {
                report.accept(VALUE_OUT_OF_RANGE, List.of(kind.name()));
            }
        } catch (DateTimeException invalid) {
            report.accept(INVALID_TEMPORAL_VALUE, List.of(kind.name()));
        }
        return CHECKED;
    }

    static LocalDateTime parse(String text, TemporalKind kind, LanguageVersion language) {
        if (text.length() > 23) {
            throw new DateTimeException("Temporal value exceeds the standard layout");
        }
        boolean version24 = language == LanguageVersion.ILI24;
        return switch (kind) {
            case DATE -> date(text, version24).atStartOfDay();
            case TIMEOFDAY -> LocalDate.of(2000, 1, 1).atTime(time(text, version24));
            case DATETIME -> {
                int separator = text.indexOf('T');
                if (separator < 0) {
                    throw new DateTimeException("Missing date/time separator");
                }
                yield date(text.substring(0, separator), version24)
                        .atTime(time(text.substring(separator + 1), version24));
            }
        };
    }

    private static LocalDate date(String text, boolean version24) {
        var match = (version24 ? DATE_24 : DATE_23).matcher(text);
        if (!match.matches()) {
            throw new DateTimeException("Invalid date layout");
        }
        int year = Integer.parseInt(match.group(1));
        if (year < 1582 || year > 2999) {
            throw new DateTimeException("Year is outside the INTERLIS Gregorian domain");
        }
        return LocalDate.of(year, Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)));
    }

    private static LocalTime time(String text, boolean version24) {
        var match = (version24 ? TIME_24 : TIME_23).matcher(text);
        if (!match.matches()) {
            throw new DateTimeException("Invalid time layout");
        }
        var fraction = match.group(4);
        int nanos = fraction == null ? 0 : Integer.parseInt((fraction + "000").substring(0, 3)) * 1_000_000;
        return LocalTime.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)), nanos);
    }
}
