package guru.interlis.model;
import java.util.Objects;
import java.util.Optional;
/** Signature metadata argument. Empty target means the signature class at the use site (METAOBJECT without OF).
 * Immutable and graph-bound; it is not a transfer reference and imposes no OID requirement. */
public record MetaObjectType(TypeId id, Origin origin, Optional<SymbolId> target) implements TypeDescriptor {
    public MetaObjectType { Objects.requireNonNull(id); Objects.requireNonNull(origin); Objects.requireNonNull(target); }
    @Override public TypeKind kind() { return TypeKind.METAOBJECT; }
}
