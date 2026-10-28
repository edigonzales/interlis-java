package guru.interlis.compiler.internal;

import java.util.Arrays;
import java.util.Objects;

/** One decoder for lexer-validated INTERLIS strings, including original boundaries.
 * Recovery/display text without quotes is retained unchanged. No normalization. */
public final class InterlisStrings {
    private InterlisStrings() { }

    public static Decoded decode(String lexeme) {
        Objects.requireNonNull(lexeme,"lexeme");
        boolean quoted=lexeme.length()>=2 && lexeme.charAt(0)=='"' && lexeme.charAt(lexeme.length()-1)=='"';
        int start=quoted?1:0,end=quoted?lexeme.length()-1:lexeme.length();
        StringBuilder text=new StringBuilder(end-start);int[] offsets=new int[end-start+1];int count=0;
        offsets[0]=start;
        for(int i=start;i<end;) {
            char value=lexeme.charAt(i++);
            if(quoted && value=='\\' && i<end) {
                value=lexeme.charAt(i++);
                if(value=='u' && i+4<=end) { value=(char)Integer.parseInt(lexeme.substring(i,i+4),16);i+=4; }
            }
            text.append(value);offsets[++count]=i;
        }
        return new Decoded(text.toString(),Arrays.copyOf(offsets,count+1));
    }

    /** Immutable decoded text; rawOffset maps a decoded UTF-16 boundary into the
     * original lexeme, including complete escape sequences. Arrays stay private. */
    public static final class Decoded {
        private final String text;
        private final int[] offsets;
        private Decoded(String text,int[] offsets) { this.text=text;this.offsets=offsets; }
        public String text() { return text; }
        public int rawOffset(int boundary) { return offsets[boundary]; }
    }
}
