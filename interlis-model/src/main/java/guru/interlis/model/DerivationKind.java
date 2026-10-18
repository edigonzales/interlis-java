package guru.interlis.model;

/** How a derived model element came into existence. */
public enum DerivationKind {
    /** The {@code INHERITANCE} constant. */
    INHERITANCE,
    /** The {@code OVERRIDE} constant. */
    OVERRIDE,
    /** The {@code ALIAS} constant. */
    ALIAS,
    /** The {@code EFFECTIVE_MEMBER} constant. */
    EFFECTIVE_MEMBER,
    /** The {@code IMPLICIT_ROLE} constant. */
    IMPLICIT_ROLE
}
