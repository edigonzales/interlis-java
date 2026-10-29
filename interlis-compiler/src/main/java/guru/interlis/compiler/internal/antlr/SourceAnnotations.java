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
        var all=tokens.getTokens();
        for(int index=0;index<all.size();index++) {
            var token=all.get(index);
            cancellation.throwIfCancellationRequested();String text=token.getText();
            if(token.getType()==InterlisLexer.SingleLineComment && text.startsWith("!!@")) {
                var annotation=new Annotation(cancellation);var range=offsets.tokenRange(token);
                annotation.append(text,range.startOffset());int end=range.endOffset();
                while(annotation.quoted) {
                    int next=index+1;
                    while(next<all.size() && all.get(next).getText().chars().allMatch(Character::isWhitespace))next++;
                    if(next>=all.size())break;
                    var continuation=all.get(next);String content=continuation.getText();
                    if(continuation.getType()!=InterlisLexer.SingleLineComment || !content.startsWith("!!@"))break;
                    var following=offsets.tokenRange(continuation);
                    String gap=source.text().substring(end,following.startOffset());
                    if(!gap.chars().allMatch(Character::isWhitespace))break;
                    annotation.append(gap,end);annotation.append(content.substring(3),following.startOffset()+3);
                    end=following.endOffset();index=next;
                }
                attributes.addAll(new Values(source,annotation,diagnostics,cancellation).read());
            }
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

    /** Maps logical continuation text back to the untouched UTF-16 source. */
    private static final class Annotation {
        final StringBuilder text=new StringBuilder();final List<Integer> positions=new ArrayList<>();
        final CancellationToken cancellation;boolean quoted,escaped;int end;
        Annotation(CancellationToken cancellation){this.cancellation=cancellation;}
        void append(String content,int start) {
            for(int i=0;i<content.length();i++) {
                if((i&255)==0)cancellation.throwIfCancellationRequested();
                char ch=content.charAt(i);text.append(ch);positions.add(start+i);
                if(escaped)escaped=false;else if(quoted && ch=='\\')escaped=true;else if(ch=='"')quoted=!quoted;
            }
            end=start+content.length();
        }
        int start(int at){return at==positions.size()?end:positions.get(at);}
        int end(int at){return at==0?start(0):positions.get(at-1)+1;}
    }

    private static final class Values {
        private final SourceText source;private final Annotation annotation;private final String text;
        private final DiagnosticCollector diagnostics;private final CancellationToken cancellation;private int at=3;
        Values(SourceText source,Annotation annotation,DiagnosticCollector diagnostics,CancellationToken cancellation) {
            this.source=source;this.annotation=annotation;this.text=annotation.text.toString();this.diagnostics=diagnostics;this.cancellation=cancellation;
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
                result.add(new SourceMetadata.Attribute(key,value,source.range(annotation.start(start),annotation.end(end))));
                if(at<text.length())advance();
            }
            return List.copyOf(result);
        }
        private void error() {
            diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0002"),Severity.ERROR,Phase.PARSING,"diagnostic.syntax.unexpected")
                    .at(source.range(annotation.start(at),at==text.length()?annotation.start(at):annotation.start(at)+1))
                    .argument(new DiagnosticArgument.TokenArgument("actual",at==text.length()?"<end-of-annotation>":String.valueOf(text.charAt(at))))
                    .argument(new DiagnosticArgument.IdentifierListArgument("expected",List.of("metadata key=value"))));
        }
    }
}
