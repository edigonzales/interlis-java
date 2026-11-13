package guru.interlis.validation;

import guru.interlis.model.AlignmentType;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.FormattedType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.OidType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeKind;
import guru.interlis.model.SymbolId;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferValue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

final class ValueSemantics {
    private static final int MAX_DEPTH = 128;
    private final ValidationPlan plan;
    private final int maxBytes;
    private final boolean partial;
    private final LanguageVersion language;
    private final Runnable visit;
    private final BiFunction<TransferValue.Reference, TypeDescriptor, Optional<StableIdentity>> reference;
    private final Function<String, StableIdentity> oid;

    ValueSemantics(ValidationPlan plan, int maxBytes, boolean partial, LanguageVersion language, Runnable visit,
                   BiFunction<TransferValue.Reference, TypeDescriptor, Optional<StableIdentity>> reference,
                   Function<String, StableIdentity> oid) {
        this.plan = plan;
        this.maxBytes = maxBytes;
        this.partial = partial;
        this.language = language;
        this.visit = visit;
        this.reference = reference;
        this.oid = oid;
    }

    static ValueSemantics local(ValidationPlan plan, boolean partial, Runnable visit) {
        return new ValueSemantics(plan, 8 * 1024 * 1024, partial, LanguageVersion.ILI24, visit, (ignored, type) -> Optional.empty(),
                value -> new StableIdentity("", value));
    }

    sealed interface Result permits Known, Gap { }

    record Known(CanonicalKey key) implements Result { }

    enum Gap implements Result { UNDEFINED, MISSING_CONTEXT, INVALID_VALUE, UNSUPPORTED }

    Result key(EvaluationResult result, TypeDescriptor type) {
        return switch (result) {
            case EvaluationResult.UndefinedByData ignored -> Gap.UNDEFINED;
            case EvaluationResult.BlockedByViolation ignored -> Gap.INVALID_VALUE;
            case EvaluationResult.MissingContext ignored -> Gap.MISSING_CONTEXT;
            case EvaluationResult.KnownNumber number -> number.value().decimal()
                    .<Result>map(value -> encode(new TransferValue.NumberValue(value.toString(), value), type, 0))
                    .orElse(Gap.UNSUPPORTED);
            case EvaluationResult.Unsupported _, EvaluationResult.KnownObjects _ -> Gap.UNSUPPORTED;
            case EvaluationResult.Known known -> encode(known.value(), type, 0);
        };
    }

    Result existenceKey(EvaluationResult result, TypeDescriptor type) {
        if (type instanceof EnumerationType && result instanceof EvaluationResult.Known known
                && known.value() instanceof TransferValue.Enumeration enumeration) {
            // The bound source and target have already passed compiler compatibility checks.
            visit.run();
            var out = new Output(maxBytes);
            var issue = writeEnumeration(out, type, enumeration, false);
            return issue == null ? new Known(out.finish()) : issue;
        }
        return key(result, type);
    }

    CanonicalKey tuple(List<CanonicalKey> keys) {
        var out = new Output(maxBytes);
        out.number(keys.size());
        keys.forEach(out::key);
        return out.finish();
    }

    CanonicalKey coordinate(TransferGeometry.Coordinate coordinate) {
        var out = new Output(maxBytes);
        writeCoordinate(out, coordinate);
        return out.finish();
    }

    private Result encode(TransferValue value, TypeDescriptor type, int depth) {
        visit.run();
        if (depth > MAX_DEPTH) {
            throw new ValidationLimitException("Canonical value nesting limit exceeded");
        }
        if (value == TransferValue.Undefined.INSTANCE) {
            return Gap.UNDEFINED;
        }
        if (type == null) {
            return Gap.UNSUPPORTED;
        }
        var out = new Output(maxBytes);
        var gap = switch (type.kind()) {
            case BOOLEAN -> value instanceof TransferValue.BooleanValue bool
                    ? out.primitive(1, bool.value() ? 1 : 0) : Gap.INVALID_VALUE;
            case TEXT -> value instanceof TransferValue.Text text
                    ? out.taggedText(2, text.value()) : Gap.INVALID_VALUE;
            case NUMERIC -> value instanceof TransferValue.NumberValue number
                    ? writeNumber(out, number.exactValue()) : Gap.INVALID_VALUE;
            case ENUMERATION, ALIGNMENT -> value instanceof TransferValue.Enumeration enumeration
                    ? writeEnumeration(out, type, enumeration, true) : Gap.INVALID_VALUE;
            case COORDINATE -> writeCoordinateValue(out, (CoordinateType) type, value);
            case STRUCTURE_VALUE -> value instanceof TransferValue.Structured structure
                    ? writeStructure(out, (StructureValueType) type, structure, depth) : Gap.INVALID_VALUE;
            case COLLECTION -> value instanceof TransferValue.Repeated collection
                    ? writeCollection(out, (CollectionType) type, collection, depth) : Gap.INVALID_VALUE;
            case REFERENCE, OBJECT_ARGUMENT -> value instanceof TransferValue.Reference ref
                    ? writeReference(out, ref, type) : Gap.UNSUPPORTED;
            case FORMATTED -> value instanceof TransferValue.Text text
                    ? writeFormatted(out, (FormattedType) type, text.value()) : Gap.INVALID_VALUE;
            case OID -> value instanceof TransferValue.Oid id ? writeOid(out, (OidType) type, id) : Gap.INVALID_VALUE;
            case CLASS_VALUE -> value instanceof TransferValue.ClassName name
                    ? out.taggedText(11, name.name().toString()) : Gap.INVALID_VALUE;
            case ATTRIBUTE_PATH -> value instanceof TransferValue.AttributePath path
                    ? out.taggedText(12, path.name().toString()) : Gap.INVALID_VALUE;
            default -> Gap.UNSUPPORTED;
        };
        return gap == null ? new Known(out.finish()) : gap;
    }

    private static Gap writeNumber(Output out, BigDecimal number) {
        ScalarRules.requireNumericBudget(number);
        var normalized = number.stripTrailingZeros();
        out.tag(3);
        out.number(normalized.scale());
        out.bytes(normalized.unscaledValue().toByteArray());
        return null;
    }

    private Gap writeEnumeration(Output out, TypeDescriptor type, TransferValue.Enumeration value,
                                 boolean includeDomain) {
        out.tag(4);
        var path = value.path();
        if (type instanceof EnumerationType enumeration) {
            var spelling = String.join(".", path);
            var member = enumeration.values().stream().filter(item -> item.path().equals(spelling)).findFirst();
            if (member.isEmpty()) {
                return Gap.INVALID_VALUE;
            }
            var canonical = plan.canonicalEnumeration(enumeration);
            if (!canonical.id().equals(enumeration.id())) {
                var order = member.orElseThrow().order();
                var equivalent = canonical.values().stream().filter(item -> item.order() == order).findFirst();
                if (equivalent.isEmpty()) {
                    return Gap.UNSUPPORTED;
                }
                path = List.of(equivalent.orElseThrow().path().split("\\."));
            }
            while (canonical.baseType().isPresent()) {
                visit.run();
                var base = plan.effectiveType(canonical.baseType().orElseThrow()).orElse(null);
                if (!(base instanceof EnumerationType parent)) {
                    return Gap.UNSUPPORTED;
                }
                canonical = plan.canonicalEnumeration(parent);
            }
            out.text(includeDomain ? canonical.id().value() : "");
        } else {
            out.text(((AlignmentType) type).alignment().name());
        }
        out.number(path.size());
        path.forEach(out::text);
        return null;
    }

    private Gap writeFormatted(Output out, FormattedType type, String value) {
        if (type.predefined().isEmpty()) {
            var parsed = FormatRules.parse(plan, type, value, visit);
            if (parsed.isEmpty()) {
                return Gap.INVALID_VALUE;
            }
            out.tag(15);
            var parts = parsed.orElseThrow();
            out.number(parts.size());
            parts.forEach(part -> out.text(part.stripTrailingZeros().toString()));
            return null;
        }
        var kind = type.predefined().orElseThrow();
        if (type.parts().size() != (kind == guru.interlis.model.TemporalKind.DATETIME ? 11 : 5)) {
            return Gap.UNSUPPORTED;
        }
        try {
            var parsed = TemporalRules.parse(value, kind, language);
            out.tag(13);
            out.text(kind.name());
            out.text(parsed.toString());
            return null;
        } catch (java.time.DateTimeException invalid) {
            return Gap.INVALID_VALUE;
        }
    }

    private Gap writeCoordinateValue(Output out, CoordinateType type, TransferValue value) {
        if (!type.multiCoordinate()) {
            return value instanceof TransferGeometry.Coordinate coordinate
                    && type.dimensions() == coordinate.ordinates().size()
                    ? writeCoordinate(out, coordinate) : Gap.INVALID_VALUE;
        }
        if (!(value instanceof TransferGeometry.MultiCoordinate multi)) {
            return Gap.INVALID_VALUE;
        }
        var positions = new TreeSet<CanonicalKey>();
        long pending = 1 + Integer.BYTES;
        for (var coordinate : multi.coordinates()) {
            visit.run();
            if (type.dimensions() != coordinate.ordinates().size()) {
                return Gap.INVALID_VALUE;
            }
            var key = coordinate(coordinate);
            if (positions.add(key)) {
                pending += (long) key.size() + Integer.BYTES;
                if (pending > maxBytes) {
                    throw new ValidationLimitException("Canonical key byte budget exceeded");
                }
            }
        }
        out.tag(14);
        out.number(positions.size());
        positions.forEach(out::key);
        return null;
    }

    private Gap writeCoordinate(Output out, TransferGeometry.Coordinate coordinate) {
        out.tag(5);
        out.number(coordinate.ordinates().size());
        for (var ordinate : coordinate.ordinates()) {
            visit.run();
            writeNumber(out, ordinate.exactValue());
        }
        return null;
    }

    private Gap writeStructure(Output out, StructureValueType type, TransferValue.Structured structure, int depth) {
        var descriptor = plan.transferPlan().object(structure.type()).orElse(null);
        var rules = plan.object(structure.type());
        if (descriptor == null || rules == null) {
            return Gap.MISSING_CONTEXT;
        }
        if (!plan.assignable(type, rules)) {
            return Gap.INVALID_VALUE;
        }
        out.tag(6);
        var base = descriptor;
        while (base.baseType().isPresent()) {
            visit.run();
            base = plan.transferPlan().object(base.baseType().orElseThrow()).orElseThrow();
        }
        out.text(base.name().canonical().symbol().value());
        var fields = descriptor.slots().stream().filter(AttributeSlot.class::isInstance)
                .map(AttributeSlot.class::cast).filter(slot -> !slot.attribute().transientAttribute()).toList();
        out.number(fields.size());
        for (var field : fields) {
            out.text(canonicalSlot(field.attribute()).value());
            var context = new EvaluationFrame(plan, structure, partial, visit);
            var result = ExpressionEvaluator.attribute(context, structure, field.symbol().symbol());
            if (result instanceof EvaluationResult.UndefinedByData) {
                out.tag(0);
                continue;
            }
            if (!(result instanceof EvaluationResult.Known known)) {
                return result instanceof EvaluationResult.MissingContext ? Gap.MISSING_CONTEXT : Gap.UNSUPPORTED;
            }
            var fieldType = plan.effectiveType(field.effectiveType().type()).orElse(null);
            switch (encode(known.value(), fieldType, depth + 1)) {
                case Known component -> {
                    out.tag(1);
                    out.key(component.key());
                }
                case Gap gap -> { return gap; }
            }
        }
        return null;
    }

    private SymbolId canonicalSlot(AttributeSymbol attribute) {
        while (attribute.overrides().isPresent()) {
            visit.run();
            attribute = (AttributeSymbol) plan.symbol(attribute.overrides().orElseThrow()).orElseThrow();
        }
        return plan.canonical(attribute.id());
    }

    private Gap writeCollection(Output out, CollectionType type, TransferValue.Repeated values, int depth) {
        if (values.kind() != type.collectionKind()) {
            return Gap.INVALID_VALUE;
        }
        boolean bag = values.kind() == CollectionKind.BAG;
        out.tag(bag ? 7 : 8);
        out.number(values.values().size());
        var element = plan.effectiveType(type.elementType()).orElse(null);
        var sorted = new ArrayList<CanonicalKey>();
        long pending = 0;
        for (var value : values.values()) {
            var component = encode(value, element, depth + 1);
            if (component instanceof Gap gap && gap != Gap.UNDEFINED) {
                return gap;
            }
            var key = component instanceof Known known ? known.key() : new CanonicalKey(new byte[]{0});
            if (bag) {
                pending += (long) key.size() + Integer.BYTES;
                if (pending > maxBytes) {
                    throw new ValidationLimitException("Canonical key byte budget exceeded");
                }
                sorted.add(key);
            } else {
                out.key(key);
            }
        }
        if (bag) {
            sorted.sort(CanonicalKey::compareTo);
            sorted.forEach(out::key);
        }
        return null;
    }

    private Gap writeReference(Output out, TransferValue.Reference value, TypeDescriptor type) {
        var target = reference.apply(value, type);
        if (target.isEmpty()) {
            return Gap.MISSING_CONTEXT;
        }
        out.tag(9);
        out.text(target.orElseThrow().namespace());
        out.text(target.orElseThrow().value());
        return null;
    }

    private Gap writeOid(Output out, OidType type, TransferValue.Oid id) {
        final StableIdentity identity;
        try {
            identity = oid.apply(id.lexeme());
        } catch (ValidationStreamException invalidNamespace) {
            return Gap.INVALID_VALUE;
        }
        if (id.lexeme().indexOf(':') >= 0 && identity.namespace().isEmpty()) {
            return Gap.MISSING_CONTEXT;
        }
        var validity = new ArrayList<ValidationCode>(1);
        boolean supported = OidRules.check(plan, type, id.lexeme(), (code, args) -> validity.add(code));
        if (!validity.isEmpty()) {
            return Gap.INVALID_VALUE;
        }
        if (!supported) {
            return Gap.UNSUPPORTED;
        }
        out.tag(10);
        out.text(identity.namespace());
        out.text(type.oidKind().name());
        if (type.oidKind() == guru.interlis.model.OidKind.NUMERIC) {
            return writeNumber(out, new BigDecimal(identity.value()));
        }
        out.text(identity.value());
        return null;
    }

    private static final class Output {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream stream = new DataOutputStream(bytes);

        private Output(int limit) {
            this.limit = limit;
        }

        private void require(long length) {
            if (length > limit - bytes.size()) {
                throw new ValidationLimitException("Canonical key byte budget exceeded");
            }
        }

        private void tag(int value) {
            require(1);
            bytes.write(value);
        }

        private Gap primitive(int tag, int value) {
            tag(tag);
            tag(value);
            return null;
        }

        private Gap taggedText(int tag, String value) {
            tag(tag);
            text(value);
            return null;
        }

        private void number(int value) {
            require(Integer.BYTES);
            try {
                stream.writeInt(value);
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
        }


        private void text(String value) {
            require(Integer.BYTES + (long) Character.BYTES * value.length());
            number(value.length());
            try {
                stream.writeChars(value);
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
        }

        private void bytes(byte[] value) {
            require(Integer.BYTES + (long) value.length);
            number(value.length);
            bytes.writeBytes(value);
        }

        private void key(CanonicalKey value) {
            require(Integer.BYTES + (long) value.size());
            bytes(value.bytes());
        }

        private CanonicalKey finish() {
            return new CanonicalKey(bytes.toByteArray());
        }
    }
}
