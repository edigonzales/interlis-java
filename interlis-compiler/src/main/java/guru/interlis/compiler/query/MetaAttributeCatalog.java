package guru.interlis.compiler.query;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.model.*;
import guru.interlis.syntax.*;
import java.util.*;

/** Declarative editor hints, not semantic validation. Reference behaviour:
 * interlis-language-tools f27801bf, completion-meta.test.ts (profiles/values),
 * interpreted using this compiler's actual following syntax and published types. */
final class MetaAttributeCatalog {
    private record Entry(String name,List<String> values) {Entry(String name,String... values){this(name,List.of(values));}}
    static List<Entry> entries(AnalysisSnapshot snapshot,SyntaxSnapshot syntax,int commentEnd){
        var next=EditorSyntax.tokens(syntax).stream().filter(t->t.range().startOffset()>=commentEnd).findFirst().orElse(null);if(next==null)return List.of();
        var nodes=EditorSyntax.nodes(syntax);var n=nodes.stream().filter(v->v.range().startOffset()==next.range().startOffset()).filter(v->v instanceof DeclarationSyntax||v instanceof TopicSyntax||v instanceof ModelSyntax).min(Comparator.comparingInt(SyntaxNode::width)).orElse(null);var entries=new ArrayList<Entry>();
        boolean enumeration=nodes.stream().filter(EnumerationTypeSyntax.class::isInstance).map(EnumerationTypeSyntax.class::cast).flatMap(e->e.values().stream()).anyMatch(v->v.range().startOffset()==next.range().startOffset());
        if(n instanceof ClassDeclarationSyntax){entries.add(text("ili2db.dispName"));entries.add(new Entry("ili2db.oid","INTERLIS.UUIDOID"));entries.add(text("ilivalid.keymsg"));entries.add(text("ilivalid.keymsg_de"));}
        if(n instanceof StructureDeclarationSyntax){entries.add(text("ili2db.dispName"));entries.add(new Entry("ili2db.mapping","MultiSurface","MultiLine","MultiPoint","Multilingual","Localised"));}
        if(n instanceof AttributeDeclarationSyntax a){entries.add(text("ili2db.dispName"));entries.add(severity("ilivalid.type"));entries.add(severity("ilivalid.multiplicity"));var symbol=snapshot.semanticGraph().symbols().stream().filter(s->s instanceof AttributeSymbol&&s.nameRange().equals(a.nameRange())).findFirst().orElse(null);var type=symbol==null?null:snapshot.semanticGraph().effectiveType(symbol.id()).flatMap(t->snapshot.semanticGraph().type(t.effectiveType())).orElse(null);if(type instanceof CollectionType c&&isStructure(snapshot,c.elementType(),new HashSet<>())){entries.add(new Entry("ili2db.mapping","ARRAY","JSON","EXPAND"));entries.add(new Entry("ilivalid.requiredIn","bid1"));}else if(type instanceof ReferenceType||a.type() instanceof ReferenceTypeSyntax)entries.add(new Entry("ilivalid.requiredIn","bid1"));}
        if(n instanceof RoleDeclarationSyntax){entries.add(severity("ilivalid.target"));entries.add(severity("ilivalid.multiplicity"));entries.add(new Entry("ilivalid.requiredIn","bid1"));}
        if(n instanceof ConstraintDeclarationSyntax){entries.add(severity("ilivalid.check"));entries.add(new Entry("category","category"));entries.add(text("ilivalid.msg"));entries.add(text("ilivalid.msg_de"));entries.add(text("message"));entries.add(text("message_de"));entries.add(new Entry("name","c1023"));}
        if(enumeration)entries.add(text("ili2db.dispName"));return List.copyOf(entries);
    }
    private static boolean isStructure(AnalysisSnapshot s,TypeId id,Set<TypeId> seen){if(!seen.add(id))return false;return switch(s.semanticGraph().type(id).orElse(null)){case StructureValueType v->true;case DomainReferenceType d->s.semanticGraph().declaredType(d.targetDomain()).map(t->isStructure(s,t,seen)).orElse(false);case null,default->false;};}
    private static Entry text(String name){return new Entry(name,"\"...\"");}
    private static Entry severity(String name){return new Entry(name,"on","warning","off");}
    static List<CompletionCandidate> names(AnalysisSnapshot snapshot,SyntaxSnapshot syntax,int commentEnd){return entries(snapshot,syntax,commentEnd).stream().map(e->new CompletionCandidate(e.name(),e.name(),CompletionKind.KEYWORD,"metadata hint")).toList();}
    static List<CompletionCandidate> values(AnalysisSnapshot snapshot,SyntaxSnapshot syntax,int commentEnd,String name){return entries(snapshot,syntax,commentEnd).stream().filter(e->e.name().equals(name)).flatMap(e->e.values().stream()).map(v->new CompletionCandidate(v,v,CompletionKind.KEYWORD,"metadata value")).toList();}
}
