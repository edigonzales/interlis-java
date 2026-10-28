package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.*;

/** Ordered lexical comment annotations attached to the following source token.
 * Values are immutable, null-free and retain original UTF-16/revision ranges. */
public record SourceMetadata(List<Attribute> attributes,List<Text> documentation) {
    public SourceMetadata { attributes=List.copyOf(attributes);documentation=List.copyOf(documentation); }
    /** One assignment; duplicates remain visible until semantic conflict checking. */
    public record Attribute(String key,String text,SourceRange range) {
        public Attribute { Objects.requireNonNull(key);Objects.requireNonNull(text);Objects.requireNonNull(range);if(key.isBlank())throw new IllegalArgumentException("empty metadata key"); }
    }
    /** A comment or explanation's interpreted text and exact original token range. */
    public record Text(String text,SourceRange range) {
        public Text { Objects.requireNonNull(text);Objects.requireNonNull(range); }
    }
    public static SourceMetadata empty() { return new SourceMetadata(List.of(),List.of()); }
}
