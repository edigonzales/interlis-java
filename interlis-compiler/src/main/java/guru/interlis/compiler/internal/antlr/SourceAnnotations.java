package guru.interlis.compiler.internal.antlr;

import guru.interlis.compiler.internal.DiagnosticCollector;
import guru.interlis.compiler.internal.InterlisStrings;
import guru.interlis.diagnostics.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import guru.interlis.syntax.SourceMetadata;
import org.antlr.v4.runtime.*;
import java.util.*;

/** Parses lexical annotation comments once, without scanning unrelated source text. */
final class SourceAnnotations {
    static Map<SourceRange,SourceMetadata> read(SourceText source,CommonTokenStream tokens,
            AntlrOffsetMap offsets,DiagnosticCollector diagnostics,CancellationToken cancellation) {
        var result=new LinkedHashMap<SourceRange,SourceMetadata>();
        var attributes=new ArrayList<SourceMetadata.Attribute>();var documentation=new ArrayList<SourceMetadata.Text>();
        for(var token:tokens.getTokens()) {
            cancellation.throwIfCancellationRequested();String text=token.getText();
            if(token.getType()==InterlisLexer.SingleLineComment && text.startsWith("!!@"))
                attributes.addAll(new Values(source,offsets.tokenRange(token),text,diagnostics,cancellation).read());
            else if(token.getType()==InterlisLexer.BlockComment && text.startsWith("/**")) {
                String content=(text.length()<5?"":text.substring(3,text.length()-2)).replaceAll("(?m)^\\s*\\* ?","").strip();
                documentation.add(new SourceMetadata.Text(content,offsets.tokenRange(token)));
            } else if(token.getChannel()==Token.DEFAULT_CHANNEL && (!attributes.isEmpty() || !documentation.isEmpty())
                    && token.getType()!=InterlisLexer.DOMAIN && token.getType()!=InterlisLexer.UNIT
                    && token.getType()!=InterlisLexer.ATTRIBUTE && token.getType()!=InterlisLexer.PARAMETER) {
                result.put(offsets.tokenRange(token),new SourceMetadata(attributes,documentation));attributes.clear();documentation.clear();
            }
        }
        return Map.copyOf(result);
    }

    private static final class Values {
        private final SourceText source;private final SourceRange range;private final String text;
        private final DiagnosticCollector diagnostics;private final CancellationToken cancellation;private int at=3;
        Values(SourceText source,SourceRange range,String text,DiagnosticCollector diagnostics,CancellationToken cancellation) {
            this.source=source;this.range=range;this.text=text;this.diagnostics=diagnostics;this.cancellation=cancellation;
        }
        private boolean bare(char ch) { return !Character.isWhitespace(ch) && ch!='=' && ch!=';' && ch!=',' && ch!='"'; }
        private void whitespace() {while(at<text.length() && Character.isWhitespace(text.charAt(at)))advance();}
        private void advance() {if((++at&255)==0)cancellation.throwIfCancellationRequested();}
        List<SourceMetadata.Attribute> read() {
            var result=new ArrayList<SourceMetadata.Attribute>();
            while(at<text.length()) {
                whitespace();if(at==text.length())break;
                if(text.charAt(at)==';') {advance();continue;}
                int start=at;while(at<text.length() && bare(text.charAt(at)))advance();
                if(start==at) {error();break;}
                String key=text.substring(start,at);whitespace();
                if(at==text.length() || text.charAt(at)!='=') {error();break;}advance();whitespace();
                int valueStart=at;String value;
                if(at<text.length() && text.charAt(at)=='"') {
                    advance();boolean closed=false,valid=true;
                    while(at<text.length()) {
                        char ch=text.charAt(at);advance();
                        if(ch=='"') {closed=true;break;}
                        if(ch=='\\') {
                            if(at==text.length()) {valid=false;break;}
                            char escape=text.charAt(at);advance();
                            if(escape=='u')for(int i=0;i<4;i++) {
                                if(at==text.length() || "0123456789abcdefABCDEF".indexOf(text.charAt(at))<0) {valid=false;break;}
                                advance();
                            } else if(escape!='"' && escape!='\\')valid=false;
                            if(!valid)break;
                        }
                    }
                    if(!closed || !valid) {error();break;}
                    value=InterlisStrings.decode(text.substring(valueStart,at)).text();
                } else {
                    while(at<text.length() && bare(text.charAt(at)))advance();
                    if(valueStart==at) {error();break;}value=text.substring(valueStart,at);
                }
                int end=at;whitespace();
                if(at<text.length() && text.charAt(at)!=';') {error();break;}
                result.add(new SourceMetadata.Attribute(key,value,source.range(range.startOffset()+start,range.startOffset()+end)));
                if(at<text.length())advance();
            }
            return List.copyOf(result);
        }
        private void error() {
            diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0002"),Severity.ERROR,Phase.PARSING,"diagnostic.syntax.unexpected")
                    .at(source.range(range.startOffset()+at,range.startOffset()+Math.min(text.length(),at+1)))
                    .argument(new DiagnosticArgument.TokenArgument("actual",at==text.length()?"<end-of-annotation>":String.valueOf(text.charAt(at))))
                    .argument(new DiagnosticArgument.IdentifierListArgument("expected",List.of("metadata key=value"))));
        }
    }
}
