package guru.interlis.compiler.query;

/** Native editor indentation preference, without an editor dependency. */
public record EditorIndentOptions(int tabSize,boolean insertSpaces) {
    public EditorIndentOptions {if(tabSize<1||tabSize>16)throw new IllegalArgumentException("tab size must be 1..16");}
    public String unit(){return insertSpaces?" ".repeat(tabSize):"\t";}
}
