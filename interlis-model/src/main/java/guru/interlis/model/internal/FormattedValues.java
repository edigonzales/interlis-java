package guru.interlis.model.internal;

import guru.interlis.model.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Interprets bound FORMAT parts. No source scanning or persistent cache. */
public final class FormattedValues {
    private static final String NUMBER = "[+-]?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?";
    private final Function<SymbolId, TypeDescriptor> typeOf;
    private final Runnable checkWork;

    public FormattedValues(Function<SymbolId, TypeDescriptor> typeOf, Runnable checkWork) {
        this.typeOf = Objects.requireNonNull(typeOf); this.checkWork = Objects.requireNonNull(checkWork);
    }

    /** Effective lexicographic component interval, including nested and inherited
     * bounds. An absent endpoint is unbounded; an absent interval is invalid or
     * cyclic. All lists are immutable and use effective component order. */
    public record Interval(Optional<List<BigDecimal>> lower, Optional<List<BigDecimal>> upper, int components) {
        public Interval {
            lower=lower.map(List::copyOf); upper=upper.map(List::copyOf);
            if(components<1)throw new IllegalArgumentException("format needs numeric components");
        }
    }

    public Optional<Interval> interval(FormattedType type) { return interval(type,new HashSet<>()); }

    private Optional<Interval> interval(FormattedType type,Set<TypeId> active) {
        checkWork.run();if(!active.add(type.id()))return Optional.empty();
        try {
            Optional<List<BigDecimal>> lower=Optional.of(List.of()),upper=Optional.of(List.of());int components=0;
            for(var part:type.parts()) {
                checkWork.run();if(part.attribute().isEmpty())continue;
                if(part.format().isPresent()) {
                    if(!(typeOf.apply(part.format().orElseThrow()) instanceof FormattedType nested))return Optional.empty();
                    var child=interval(nested,active);if(child.isEmpty())return Optional.empty();
                    lower=append(lower,child.orElseThrow().lower());upper=append(upper,child.orElseThrow().upper());components+=child.orElseThrow().components();
                } else {
                    if(!(typeOf.apply(part.attribute().orElseThrow()) instanceof NumericType number))return Optional.empty();
                    lower=append(lower,number.range().minimum().map(d->List.of(d.exactValue())));
                    upper=append(upper,number.range().maximum().map(d->List.of(d.exactValue())));components++;
                }
            }
            if(components==0)return Optional.empty();
            var componentLower=lower;var componentUpper=upper;
            var seen=new HashSet<TypeId>();
            for(FormattedType current=type;current!=null;) {
                checkWork.run();if(!seen.add(current.id()))return Optional.empty();
                if(current.minimum().isPresent()) {
                    var parsed=parse(current,current.minimum().orElseThrow(),false);
                    if(parsed.isEmpty())return Optional.empty();
                    var bound=complete(parsed.orElseThrow(),components,componentLower);
                    if(bound.isPresent() && (lower.isEmpty() || compare(bound.orElseThrow(),lower.orElseThrow())>0))lower=bound;
                }
                if(current.maximum().isPresent()) {
                    var parsed=parse(current,current.maximum().orElseThrow(),false);
                    if(parsed.isEmpty())return Optional.empty();
                    var bound=complete(parsed.orElseThrow(),components,componentUpper);
                    if(bound.isPresent() && (upper.isEmpty() || compare(bound.orElseThrow(),upper.orElseThrow())<0))upper=bound;
                }
                var parent=current.baseDomain().map(typeOf).orElse(null);
                if(parent!=null && !(parent instanceof FormattedType))return Optional.empty();
                current=parent instanceof FormattedType f?f:null;
            }
            if(lower.isPresent() && upper.isPresent() && compare(lower.orElseThrow(),upper.orElseThrow())>0)return Optional.empty();
            return Optional.of(new Interval(lower,upper,components));
        } finally {active.remove(type.id());}
    }

    private static Optional<List<BigDecimal>> append(Optional<List<BigDecimal>> prefix,Optional<List<BigDecimal>> suffix) {
        if(prefix.isEmpty() || suffix.isEmpty())return Optional.empty();
        var values=new ArrayList<>(prefix.orElseThrow());values.addAll(suffix.orElseThrow());return Optional.of(List.copyOf(values));
    }

    private static Optional<List<BigDecimal>> complete(List<BigDecimal> prefix,int components,Optional<List<BigDecimal>> defaults) {
        if(prefix.size()==components)return Optional.of(prefix);
        if(prefix.size()>components || defaults.isEmpty())return Optional.empty();
        var values=new ArrayList<>(prefix);values.addAll(defaults.orElseThrow().subList(prefix.size(),components));return Optional.of(List.copyOf(values));
    }

    public boolean isAbstract(FormattedType type) { return isAbstract(type, new HashSet<>()); }

    private boolean isAbstract(FormattedType type, Set<TypeId> active) {
        checkWork.run();
        if (!active.add(type.id())) return true;
        try {
            for (var part : type.parts()) {
                checkWork.run();
                if (part.attribute().isEmpty()) continue;
                if (part.format().isPresent()) {
                    if (typeOf.apply(part.format().orElseThrow()) instanceof FormattedType nested
                            && isAbstract(nested, active)) return true;
                } else if (typeOf.apply(part.attribute().orElseThrow()) instanceof NumericType number
                        && (number.range().minimum().isEmpty() || number.range().maximum().isEmpty())) return true;
            }
            return false;
        } finally { active.remove(type.id()); }
    }

    public Optional<List<BigDecimal>> parse(FormattedType type, String text, boolean bounds) {
        return parse(type, text, bounds, new HashSet<>());
    }

    private Optional<List<BigDecimal>> parse(FormattedType type, String text, boolean bounds, Set<TypeId> active) {
        checkWork.run();
        if (!active.add(type.id())) return Optional.empty();
        try {
            Optional<List<BigDecimal>> parsed;
            {
                String pattern = pattern(type, new HashSet<>() , true);
                if (pattern == null) return Optional.empty();
                var match = Pattern.compile(pattern).matcher(text);
                if (!match.matches()) return Optional.empty();
                List<BigDecimal> values = new ArrayList<>(); int group = 0;
                for (var part : type.parts()) {
                    checkWork.run();
                    if (part.attribute().isEmpty()) continue;
                    String field = match.group(++group);
                    if (part.format().isPresent()) {
                        var nested = typeOf.apply(part.format().orElseThrow());
                        if (!(nested instanceof FormattedType f)) return Optional.empty();
                        var child = parse(f, field, true, active);
                        if (child.isEmpty()) return Optional.empty();
                        values.addAll(child.orElseThrow());
                    } else {
                        var attribute = typeOf.apply(part.attribute().orElseThrow());
                        if (!(attribute instanceof NumericType n)) return Optional.empty();
                        var value = numeric(n.range(), part.width(), field);
                        if (value.isEmpty()) return Optional.empty();
                        values.add(value.orElseThrow());
                    }
                }
                parsed = values.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(values));
            }
            if (parsed.isEmpty() || !bounds) return parsed;
            // The same type may be used to parse its bounds; nested cycles still use active.
            active.remove(type.id());
            return withinBounds(type, parsed.orElseThrow(), active) ? parsed : Optional.empty();
        } finally { active.remove(type.id()); }
    }

    private String pattern(FormattedType type, Set<TypeId> active, boolean capture) {
        checkWork.run();
        if (!active.add(type.id())) return null;
        StringBuilder result = new StringBuilder();
        try {
            for (var part : type.parts()) {
                checkWork.run();
                if (part.attribute().isEmpty()) result.append(Pattern.quote(part.literal()));
                else {
                    String field = NUMBER;
                    if (part.format().isPresent()) {
                        var nested = typeOf.apply(part.format().orElseThrow());
                        if (!(nested instanceof FormattedType f)) return null;
                        field = pattern(f, active, false);
                        if (field == null) return null;
                    }
                    result.append(capture ? "(" : "(?:").append(field).append(")");
                }
            }
            return result.toString();
        } finally { active.remove(type.id()); }
    }

    private Optional<BigDecimal> numeric(NumericRange range, Optional<BigInteger> width, String text) {
        checkWork.run();
        int exponentAt = Math.max(text.indexOf('e'), text.indexOf('E'));
        String mantissa = exponentAt < 0 ? text : text.substring(0, exponentAt);
        int dot = mantissa.indexOf('.'); int sign = mantissa.startsWith("-") || mantissa.startsWith("+") ? 1 : 0;
        int integerDigits = (dot < 0 ? mantissa.length() : dot) - sign;
        int fraction = dot < 0 ? 0 : mantissa.length() - dot - 1;
        if (width.filter(w -> w.compareTo(BigInteger.valueOf(integerDigits)) > 0).isPresent()) return Optional.empty();
        // Abstract components have no precision yet. Their lexical bounds still
        // constrain the mathematical value; specialization supplies precision.
        if (range.minimum().isEmpty() || range.maximum().isEmpty()) {
            try { return Optional.of(new BigDecimal(text)); }
            catch (NumberFormatException e) { return Optional.empty(); }
        }
        var lo = range.minimum().orElseThrow(); var hi = range.maximum().orElseThrow();
        boolean floating = lo.lexeme().matches(".*[eE].*") || hi.lexeme().matches(".*[eE].*");
        if (floating != (exponentAt >= 0)) return Optional.empty();
        int precision = lo.precision().fractionalDigits();
        if (precision != hi.precision().fractionalDigits() || fraction != precision) return Optional.empty();
        try {
            BigDecimal value = new BigDecimal(text);
            if (floating) {
                BigDecimal m = new BigDecimal(mantissa).abs();
                if (m.compareTo(new BigDecimal("0.1")) < 0 || m.compareTo(BigDecimal.ONE) >= 0) return Optional.empty();
            }
            return value.compareTo(lo.exactValue()) < 0 || value.compareTo(hi.exactValue()) > 0
                    ? Optional.empty() : Optional.of(value);
        } catch (NumberFormatException e) { return Optional.empty(); }
    }

    private boolean withinBounds(FormattedType type, List<BigDecimal> values, Set<TypeId> active) {
        Set<TypeId> seen = new HashSet<>();
        for (FormattedType current = type; current != null && seen.add(current.id());) {
            checkWork.run();
            if (current.minimum().isPresent()) {
                var lower = parse(current, current.minimum().orElseThrow(), false, active);
                if (lower.isEmpty() || compare(values, lower.orElseThrow()) < 0) return false;
            }
            if (current.maximum().isPresent()) {
                var upper = parse(current, current.maximum().orElseThrow(), false, active);
                if (upper.isEmpty() || compare(values, upper.orElseThrow()) > 0) return false;
            }
            var base = current.baseDomain().map(typeOf).orElse(null);
            current = base instanceof FormattedType f ? f : null;
        }
        return true;
    }

    public static int compare(List<BigDecimal> left, List<BigDecimal> right) {
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int c = left.get(i).compareTo(right.get(i)); if (c != 0) return c;
        }
        return 0;
    }

}
