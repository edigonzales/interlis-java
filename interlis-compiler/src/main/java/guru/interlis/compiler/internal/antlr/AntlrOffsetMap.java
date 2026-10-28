package guru.interlis.compiler.internal.antlr;

import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;
import org.antlr.v4.runtime.Token;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Converts ANTLR input code-point boundaries to original UTF-16 source offsets.
 * Maps are immutable and local to one parse, including fragments and decoded literals. */
public final class AntlrOffsetMap {
    private final SourceText source;
    private final int[] codePointToUtf16;
    private final int[] lineStarts;

    public AntlrOffsetMap(SourceText source) {
        this(source,source.text(),IntUnaryOperator.identity());
    }

    /** Map each UTF-16 input boundary to the owning original source revision.
     * The mapping is evaluated once and must be monotonic within the source.
     * EOF denotes the input's mapped end, including for a fragment. */
    public AntlrOffsetMap(SourceText source,String input,IntUnaryOperator originalOffset) {
        this.source=Objects.requireNonNull(source,"source");
        Objects.requireNonNull(input,"input");Objects.requireNonNull(originalOffset,"originalOffset");
        int lines=1;
        for(int i=0;i<input.length();i++)if(input.charAt(i)=='\n')lines++;
        lineStarts=new int[lines];
        codePointToUtf16=new int[input.codePointCount(0,input.length())+1];
        int cp=0,utf16=0,line=0,previous=-1;
        while(true) {
            int mapped=originalOffset.applyAsInt(utf16);
            if(mapped<previous || mapped<0 || mapped>source.length())throw new IllegalArgumentException("invalid original offset mapping");
            codePointToUtf16[cp]=mapped;previous=mapped;
            if(utf16==input.length())break;
            int value=input.codePointAt(utf16);utf16+=Character.charCount(value);cp++;
            // ANTLR's lexer increments its line on LF, rather than on a bare CR.
            if(value=='\n')lineStarts[++line]=cp;
        }
    }

    public SourceText source() { return source; }
    public int codePointCount() { return codePointToUtf16.length-1; }

    public int utf16Offset(int codePointBoundary) {
        if(codePointBoundary<0 || codePointBoundary>=codePointToUtf16.length)
            throw new IllegalArgumentException("code-point boundary outside input: "+codePointBoundary);
        return codePointToUtf16[codePointBoundary];
    }

    public SourceRange tokenRange(Token token) {
        Objects.requireNonNull(token,"token");
        if(token.getType()==Token.EOF || token.getStartIndex()<0 || token.getStopIndex()<token.getStartIndex())return eofRange();
        return range(token.getStartIndex(),token.getStopIndex());
    }

    public SourceRange range(int startCodePointInclusive,int stopCodePointInclusive) {
        return source.range(utf16Offset(startCodePointInclusive),utf16Offset(stopCodePointInclusive+1));
    }

    public SourceRange eofRange() {
        int end=codePointToUtf16[codePointToUtf16.length-1];return source.range(end,end);
    }

    /** One-based lexer line and code-point column, relative to this parse's input. */
    public int offsetAtLineColumn(int line,int column) {
        if(line<1 || column<0)return codePointToUtf16[0];
        int index=Math.min(line-1,lineStarts.length-1),start=lineStarts[index];
        int end=index+1<lineStarts.length?lineStarts[index+1]-1:codePointCount();
        return utf16Offset(start+Math.min(column,end-start));
    }
}
