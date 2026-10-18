package guru.interlis.model;

/** How an effective member relates to its declaration. */
public enum EffectiveMemberOrigin {
    /** The {@code LOCAL} constant. */
    LOCAL,
    /** The {@code INHERITED} constant. */
    INHERITED,
    /** The {@code OVERRIDDEN} constant. */
    OVERRIDDEN
}
