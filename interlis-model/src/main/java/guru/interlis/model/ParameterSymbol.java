package guru.interlis.model;

import java.util.Objects;

/** Immutable parameter declaration with a bound type and optional inherited parameter identity.
 * No runtime value is stored. Model owners denote runtime parameters; class/structure owners denote signature or Unit parameters.
 * Parameter names form a separate namespace from domains and topics; overrides belong to the same graph.
 */
public record ParameterSymbol(SymbolHeader header, TypeId typeId, boolean mandatory, java.util.Optional<SymbolId> overrides) implements Symbol {
    public ParameterSymbol { Objects.requireNonNull(header); Objects.requireNonNull(typeId); Objects.requireNonNull(overrides); }
}
