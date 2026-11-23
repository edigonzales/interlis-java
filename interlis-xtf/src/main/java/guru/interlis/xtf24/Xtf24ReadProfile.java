package guru.interlis.xtf24;

/** Selects which XTF 2.4 encodings a reader accepts. */
public enum Xtf24ReadProfile {
    /** The encodings defined in eCH-0031 and its schema. */
    STANDARD,
    /** Also accepts the arc coordinate order, grouped structure collections and explicit topic prefixes in the DMAV 1.1 transfer. */
    DMAV_1_1
}
