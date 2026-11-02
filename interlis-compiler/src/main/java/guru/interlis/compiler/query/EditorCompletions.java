package guru.interlis.compiler.query;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.syntax.*;
import java.util.*;
import java.util.stream.Collectors;

/** Contextual editor presentation of canonical completion facts and genuine recovery syntax. */
final class EditorCompletions {
    private EditorCompletions(){}
    static CompletionResult complete(AnalysisSnapshot snapshot,CompletionQuery query,CancellationToken token){
        var value=completeNow(snapshot,query,token);String prefix=snapshot.syntaxSnapshot(query.cursor().source()).orElseThrow().source().text().substring(value.replacementRange().startOffset(),query.cursor().startOffset());var candidates=value.candidates().stream().filter(c->c.label().startsWith(prefix)).toList();return new CompletionResult(candidates.stream().limit(query.limit()).toList(),value.replacementRange(),value.incomplete()||candidates.size()>query.limit(),value.snapshotId());
    }
    private static CompletionResult completeNow(AnalysisSnapshot snapshot,CompletionQuery query,CancellationToken token){
        var plain=LanguageQueries.create().complete(snapshot,query,token);var syntax=snapshot.syntaxSnapshot(query.cursor().source()).orElseThrow();int at=query.cursor().startOffset();
        var replacement=query.prefix().value().isEmpty()?plain.replacementRange():syntax.tokens().stream().filter(t->(t.kind()==TokenKind.IDENTIFIER||t.kind()==TokenKind.KEYWORD)&&t.range().startOffset()<at&&at<=t.range().endOffset()&&t.text().startsWith(query.prefix().value())).map(SyntaxToken::range).findFirst().orElse(plain.replacementRange());
        var base=new CompletionResult(plain.candidates(),replacement,plain.incomplete(),plain.snapshotId());int start=base.replacementRange().startOffset();
        var meta=meta(snapshot,syntax,query,base);if(meta.isPresent())return meta.orElseThrow();
        if(EditorSyntax.inert(syntax,at))return result(base,List.of(),false);
        var before=EditorSyntax.tokens(syntax).stream().filter(t->t.range().endOffset()<=start).toList();
        var block=EditorSyntax.nodes(syntax).stream().flatMap(n->EditorSyntax.block(n).stream()).map(b->EditorSyntax.withActualEnd(syntax,b)).filter(b->b.node().range().startOffset()<start&&(at<=b.node().range().endOffset()||b.endName().isEmpty()&&b.node().range().endOffset()<=start&&syntax.source().text().substring(b.node().range().endOffset(),start).isBlank())).min(Comparator.comparingInt(b->b.node().width())).orElse(null);String last=before.isEmpty()?"":before.getLast().text();
        int boundary=-1;for(int i=before.size()-1;i>=0;i--)if(Set.of(";","=").contains(before.get(i).text())){boundary=i;break;}
        var tail=before.subList(boundary+1,before.size());var words=tail.stream().map(SyntaxToken::text).toList();
        if(last.equals("END"))return result(base,block==null?List.of():List.of(word(block.name())),false);
        if(words.contains("IMPORTS")){
            var existing=EditorSyntax.nodes(syntax).stream().filter(ModelSyntax.class::isInstance).map(ModelSyntax.class::cast).filter(m->m.range().startOffset()<=at&&at<=m.range().endOffset()).findFirst();
            var imported=existing.stream().flatMap(m->m.imports().stream()).map(ImportSyntax::modelName).collect(Collectors.toSet());
            return result(base,snapshot.semanticGraph().symbols().stream().filter(s->s instanceof ModelSymbol&&s.origin() instanceof Origin.DeclaredOrigin&&syntax.language().version().filter(v->v==s.languageVersion()).isPresent()&&!imported.contains(s.name().value())&&existing.map(m->!m.name().equals(s.name().value())).orElse(true)).map(s->new CompletionCandidate(s.name().value(),s.name().value(),CompletionKind.MODEL,"model/"+s.languageVersion())).toList(),base.incomplete());
        }
        if(words.contains("EXTENDS")){
            var kind=headerKind(tail);var allowed=switch(kind){case "CLASS"->Set.of(CompletionKind.CLASS,CompletionKind.STRUCTURE,CompletionKind.MODEL,CompletionKind.TOPIC);case "STRUCTURE"->Set.of(CompletionKind.STRUCTURE,CompletionKind.MODEL,CompletionKind.TOPIC);case "TOPIC"->Set.of(CompletionKind.TOPIC,CompletionKind.MODEL);case "DOMAIN"->Set.of(CompletionKind.DOMAIN,CompletionKind.MODEL,CompletionKind.TOPIC);case "UNIT"->Set.of(CompletionKind.UNIT,CompletionKind.MODEL);default->Set.of(CompletionKind.CLASS,CompletionKind.STRUCTURE,CompletionKind.ASSOCIATION,CompletionKind.VIEW,CompletionKind.GRAPHIC,CompletionKind.MODEL,CompletionKind.TOPIC);};
            return result(base,base.candidates().stream().filter(c->allowed.contains(c.kind())).toList(),base.incomplete());
        }
        String header=headerKind(tail);if(Set.of("CLASS","STRUCTURE","TOPIC","DOMAIN","UNIT").contains(header)&&words.size()>=2&&!words.contains(":")){
            var items=new ArrayList<CompletionCandidate>();for(String k:List.of("(ABSTRACT)","(FINAL)","EXTENDS","="))items.add(word(k));if(Set.of("CLASS","STRUCTURE","TOPIC").contains(header))items.add(word("(EXTENDED)"));return result(base,items,false);
        }
        var value=EditorSyntax.nodes(syntax).stream().filter(n->(n instanceof DomainDeclarationSyntax||n instanceof UnitDeclarationSyntax||n instanceof AttributeDeclarationSyntax)&&n.range().startOffset()<=at&&at<=n.range().endOffset()).min(Comparator.comparingInt(SyntaxNode::width)).orElse(null);
        boolean type=words.contains(":")||last.equals("=")&&value instanceof DomainDeclarationSyntax||words.contains("OF")||words.contains("FORMAT");
        if(value instanceof UnitDeclarationSyntax&&last.equals("=")||header.equals("UNIT")&&last.equals("="))return result(base,units(base),base.incomplete());
        if(type){
            if(last.equals("LIST")||last.equals("BAG"))return result(base,List.of(word("OF"),new CompletionCandidate("cardinality OF","{${1:0}..${2:*}} OF ${0}",CompletionKind.KEYWORD,"collection",true)),false);
            if(words.contains("FORMAT"))return result(base,base.candidates().stream().filter(c->c.kind()==CompletionKind.MODEL||c.kind()==CompletionKind.TOPIC||c.kind()==CompletionKind.DOMAIN&&formatted(snapshot,c.label())).toList(),base.incomplete());
            boolean collection23=words.contains("OF")&&syntax.language().version().filter(v->v.literal().equals("2.3")).isPresent();
            var items=new ArrayList<>(base.candidates().stream().filter(c->c.kind()==CompletionKind.DOMAIN&&!collection23||c.kind()==CompletionKind.STRUCTURE||c.kind()==CompletionKind.MODEL||c.kind()==CompletionKind.TOPIC).toList());
            if(!collection23){for(String k:List.of("TEXT","MTEXT","BOOLEAN","NUMERIC","COORD","POLYLINE","SURFACE","AREA","MANDATORY","REFERENCE TO","BAG","LIST","FORMAT","INTERLIS"))items.add(word(k));if(syntax.language().version().filter(v->v.literal().equals("2.4")).isPresent())for(String k:List.of("DATE","TIMEOFDAY","DATETIME","MULTICOORD","MULTIPOLYLINE","MULTISURFACE","MULTIAREA"))items.add(word(k));items.add(new CompletionCandidate("enumeration","(${1:first}, ${2:second})$0",CompletionKind.KEYWORD,"type",true));items.add(new CompletionCandidate("numeric range","${1:0.0}..${2:100.0}$0",CompletionKind.KEYWORD,"type",true));}return result(base,items,base.incomplete());
        }
        // The compiler's parsed container determines declaration slots; qualified completion remains semantic.
        if(last.equals("."))return result(base,base.candidates().stream().filter(c->c.kind()!=CompletionKind.KEYWORD).toList(),base.incomplete());
        boolean body=tail.size()<=1&&(tail.isEmpty()||tail.getFirst().kind()==TokenKind.IDENTIFIER||tail.getFirst().kind()==TokenKind.KEYWORD);
        if(body){String kind=block==null?"ROOT":block.node().kind().name();var items=new ArrayList<CompletionCandidate>();for(String k:declarations(kind)){items.add(word(k));String snippet=template(k);if(snippet!=null)items.add(new CompletionCandidate(k+" block",snippet,CompletionKind.KEYWORD,"INTERLIS template",true));}if(!Set.of("ROOT","MODEL","TOPIC").contains(kind))items.addAll(base.candidates().stream().filter(c->c.kind()!=CompletionKind.KEYWORD).toList());return result(base,items,base.incomplete());}
        return result(base,base.candidates().stream().filter(c->c.kind()!=CompletionKind.KEYWORD||Set.of("TRUE","FALSE","UNDEFINED","DEFINED","THIS","AND","OR","NOT").contains(c.label())).toList(),base.incomplete());
    }
    private static boolean formatted(AnalysisSnapshot s,String name){return s.semanticGraph().symbols().stream().filter(v->v instanceof DomainSymbol&&v.name().value().equals(name)).map(DomainSymbol.class::cast).anyMatch(d->s.semanticGraph().effectiveType(d.id()).map(t->s.semanticGraph().type(t.effectiveType()).orElse(null) instanceof FormattedType).orElse(false));}
    private static List<CompletionCandidate> units(CompletionResult b){var items=new ArrayList<>(b.candidates().stream().filter(c->Set.of(CompletionKind.UNIT,CompletionKind.MODEL).contains(c.kind())).toList());items.add(new CompletionCandidate("base unit","[${1:m}]$0",CompletionKind.KEYWORD,"unit",true));items.add(new CompletionCandidate("unit product","(${1:1} [${2:INTERLIS.m}] * ${3:1} [${4:INTERLIS.m}])$0",CompletionKind.KEYWORD,"unit",true));return items;}
    private static String headerKind(List<SyntaxToken> ts){for(int i=ts.size()-1;i>=0;i--)if(Set.of("MODEL","TOPIC","CLASS","STRUCTURE","ASSOCIATION","VIEW","GRAPHIC","DOMAIN","UNIT").contains(ts.get(i).text()))return ts.get(i).text();return "";}
    private static CompletionCandidate word(String s){return new CompletionCandidate(s,s,CompletionKind.KEYWORD,"INTERLIS syntax");}
    private static CompletionResult result(CompletionResult base,List<CompletionCandidate> values,boolean incomplete){var seen=new HashSet<String>();return new CompletionResult(values.stream().filter(c->seen.add(c.label())).toList(),base.replacementRange(),incomplete,base.snapshotId());}
    private static List<String> declarations(String kind){return switch(kind){case "ROOT"->List.of("MODEL");case "MODEL"->List.of("TOPIC","VIEW TOPIC","CLASS","STRUCTURE","DOMAIN","UNIT","FUNCTION","CONTEXT","LINE FORM");case "TOPIC"->List.of("CLASS","STRUCTURE","ASSOCIATION","VIEW","GRAPHIC","DOMAIN","UNIT","FUNCTION","CONTEXT","CONSTRAINTS");case "ASSOCIATION"->List.of("Role");default->List.of();};}
    static String template(String k){return switch(k){
        case "MODEL"->"MODEL ${1:ModelName} (${2:de})\n  AT \"${3:https://example.org}\"\n  VERSION \"${4:2026-01-01}\"\n  =\n  $0\nEND $1.";
        case "CLASS","STRUCTURE","TOPIC"->k+" ${1:Name} ${2}=\n  $0\nEND $1;";
        case "ASSOCIATION"->"ASSOCIATION ${1:Name} ${2}=\n  ${3:first} -- {${4:0..*}} ${5:FirstClass};\n  ${6:second} -- {${7:0..*}} ${8:SecondClass};\n  $0\nEND $1;";
        case "DOMAIN"->"DOMAIN ${1:Name} ${2}= ${0:TEXT};";case "UNIT"->"UNIT ${1:Name} ${2}= ${0:1 [INTERLIS.m]};";
        case "VIEW TOPIC"->"VIEW TOPIC ${1:Name} ${2}=\n  DEPENDS ON ${3:BaseTopic};\n  $0\nEND $1;";
        case "VIEW"->"VIEW ${1:Name} PROJECTION OF ${2:base}~${3:BaseClass};\n=\n  ALL OF $2;\n  $0\nEND $1;";
        case "GRAPHIC"->"GRAPHIC ${1:Name} BASED ON ${2:BaseClass} =\n  $0\nEND $1;";
        case "Role"->"${1:role} -- {${2:0..*}} ${3:Target};$0";default->null;};}
    private static Optional<CompletionResult> meta(AnalysisSnapshot snapshot,SyntaxSnapshot s,CompletionQuery q,CompletionResult base){
        int at=q.cursor().startOffset();var span=s.trivia().stream().filter(t->t.kind()!=TriviaKind.WHITESPACE_GAP&&t.range().startOffset()<=at&&at<=t.range().endOffset()).findFirst().orElse(null);if(span==null)return Optional.empty();
        String fragment=s.source().text().substring(span.range().startOffset(),at);int marker=fragment.lastIndexOf("!!@");if(marker<0)return Optional.empty();String part=fragment.substring(marker+3);if(part.contains("\n"))return Optional.of(result(base,List.of(),false));
        int equals=part.indexOf('=');int from=span.range().startOffset()+marker+3+(equals<0?0:equals+1);String prefix=s.source().text().substring(from,at);int leading=prefix.length()-prefix.stripLeading().length();from+=leading;prefix=prefix.stripLeading();
        var values=equals<0?MetaAttributeCatalog.names(snapshot,s,span.range().endOffset()):MetaAttributeCatalog.values(snapshot,s,span.range().endOffset(),part.substring(0,equals).strip());String filter=prefix;
        var range=s.source().range(from,at);return Optional.of(new CompletionResult(values.stream().filter(c->c.label().startsWith(filter)).toList(),range,false,base.snapshotId()));
    }
}
