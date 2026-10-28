package guru.interlis.model;

import java.math.BigInteger;
import java.util.*;

/** Bound FORMAT layout and lexical bounds. All IDs belong to the owning snapshot.
 * Parts are immutable in effective formatting order, including inherited parts.
 * Inherited attribute IDs refer to the effective member of the specialized structure;
 * each part retains its original declaration provenance. Abstract numeric components
 * remain represented by their unbounded NumericType, including through nested formats.
 * formatName is display text only; predefined, structure and baseDomain carry identity. */
public record FormattedType(TypeId id, Origin origin, String formatName,
        Optional<TemporalKind> predefined, Optional<SymbolId> baseDomain, Optional<SymbolId> structure,
        List<Part> parts, Optional<String> minimum, Optional<String> maximum) implements TypeDescriptor {
    public FormattedType {
        Objects.requireNonNull(id); Objects.requireNonNull(origin); Objects.requireNonNull(formatName);
        Objects.requireNonNull(predefined); Objects.requireNonNull(baseDomain); Objects.requireNonNull(structure);
        parts = List.copyOf(parts); Objects.requireNonNull(minimum); Objects.requireNonNull(maximum);
    }
    /** An attribute reference with optional decimal padding or nested format domain,
     * or a literal when attribute is empty. Declaration provenance is retained. */
    public record Part(Optional<SymbolId> attribute, Optional<BigInteger> width,
            Optional<SymbolId> format, String literal, Origin origin) {
        public Part { Objects.requireNonNull(attribute); Objects.requireNonNull(width);
            Objects.requireNonNull(format); Objects.requireNonNull(literal); Objects.requireNonNull(origin); }
    }
    @Override public TypeKind kind() { return TypeKind.FORMATTED; }
}
