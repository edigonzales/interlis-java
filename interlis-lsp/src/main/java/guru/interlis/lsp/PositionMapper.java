package guru.interlis.lsp;

import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import java.util.Objects;

/** UTF-16 positions in one captured source; LSP columns beyond the line clamp to its end. */
final class PositionMapper {
    private PositionMapper() { }

    static int toOffset(SourceText source, Position position) {
        Objects.requireNonNull(source, "source");
        if (position == null || position.getLine() < 0 || position.getCharacter() < 0
                || position.getLine() >= source.lineMap().lineCount()) throw invalid();
        int start = source.lineMap().lineStart(position.getLine());
        int end = source.lineMap().lineContentEnd(position.getLine());
        int offset = start + Math.min(position.getCharacter(),end-start);
        if (offset > start && offset < end && Character.isHighSurrogate(source.text().charAt(offset - 1))
                && Character.isLowSurrogate(source.text().charAt(offset))) throw invalid();
        return offset;
    }

    static Range toLspRange(SourceText source, SourceRange range) {
        if (!source.id().equals(range.source()) || !source.revision().equals(range.revision()))
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.ContentModified,
                    "source revision changed", null));
        var start = source.lineMap().positionAt(range.startOffset());
        var end = source.lineMap().positionAt(range.endOffset());
        return new Range(new Position(start.line(), start.character()), new Position(end.line(), end.character()));
    }

    static ResponseErrorException invalid() {
        return new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams,
                "invalid UTF-16 document position or range", null));
    }
}
