package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.Optional;

/** Closed semantic symbol hierarchy. */
public sealed interface Symbol permits ModelSymbol, TopicSymbol, ClassSymbol,
        StructureSymbol, AssociationSymbol, AttributeSymbol, RoleSymbol, DomainSymbol,
        ConstraintSymbol, EnumElementSymbol, ViewSymbol, UnsupportedSymbol {

    /**
     * Returns the {@code header} value.
     *
     * @return the value
     */
    SymbolHeader header();

    /**
     * Returns the {@code id} value.
     *
     * @return the value
     */
    default SymbolId id() {
        return header().id();
    }

    /**
     * Returns the {@code kind} value.
     *
     * @return the value
     */
    default SymbolKind kind() {
        return header().kind();
    }

    /**
     * Returns the {@code name} value.
     *
     * @return the value
     */
    default DeclaredName name() {
        return header().name();
    }

    /**
     * Returns the {@code owner} value.
     *
     * @return the value
     */
    default Optional<SymbolId> owner() {
        return header().owner();
    }

    /**
     * Returns the {@code declarationOrder} value.
     *
     * @return the value
     */
    default int declarationOrder() {
        return header().declarationOrder();
    }

    /**
     * Returns the {@code origin} value.
     *
     * @return the value
     */
    default Origin origin() {
        return header().origin();
    }

    /**
     * Returns the {@code nameRange} value.
     *
     * @return the value
     */
    default Optional<SourceRange> nameRange() {
        return header().nameRange();
    }

    /**
     * Returns the {@code languageVersion} value.
     *
     * @return the value
     */
    default LanguageVersion languageVersion() {
        return header().languageVersion();
    }
}
