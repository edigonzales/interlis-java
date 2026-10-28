package guru.interlis.syntax;
import guru.interlis.model.Modifier;
import guru.interlis.source.SourceRange;
import java.util.*;
/** Structured graphics and drawing clauses in source order, with original UTF-16 ranges. Immutable and null-free. */
public record GraphicDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name, Optional<SourceRange> nameRange,
        Set<Modifier> modifiers, Optional<NameSyntax> baseGraphic, Optional<NameSyntax> basis,
        List<ExpressionSyntax> selections, List<Rule> rules, Optional<String> endName) implements DeclarationSyntax {
    public GraphicDeclarationSyntax { Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(name); Objects.requireNonNull(nameRange);
        modifiers=Set.copyOf(modifiers); Objects.requireNonNull(baseGraphic); Objects.requireNonNull(basis); selections=List.copyOf(selections); rules=List.copyOf(rules); Objects.requireNonNull(endName); }
    @Override public SyntaxKind kind() { return SyntaxKind.GRAPHIC; }
    @Override public List<SyntaxNode> children() { List<SyntaxNode> nodes=new ArrayList<>(); baseGraphic.ifPresent(nodes::add); basis.ifPresent(nodes::add); nodes.addAll(selections); nodes.addAll(rules); return List.copyOf(nodes); }
    public record Rule(SyntaxNodeId id,SourceRange range,String name,Optional<SourceRange> nameRange,Set<Modifier> modifiers,Optional<NameSyntax> signatureClass,List<Clause> clauses) implements DeclarationSyntax {
        public Rule { Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(name); Objects.requireNonNull(nameRange); modifiers=Set.copyOf(modifiers); Objects.requireNonNull(signatureClass); clauses=List.copyOf(clauses); }
        @Override public SyntaxKind kind() { return SyntaxKind.DRAWING_RULE; }
        @Override public List<SyntaxNode> children() { List<SyntaxNode> nodes=new ArrayList<>(); signatureClass.ifPresent(nodes::add); for(var c:clauses) { c.condition().ifPresent(nodes::add); for(var a:c.assignments()) { nodes.add(a.parameter()); addValue(a.value(),nodes); } } return List.copyOf(nodes); }
    }
    public record Clause(SourceRange range,Optional<ExpressionSyntax> condition,List<Assignment> assignments) { public Clause { Objects.requireNonNull(range); Objects.requireNonNull(condition); assignments=List.copyOf(assignments); } }
    public record Assignment(SourceRange range,NameSyntax parameter,Value value) { public Assignment { Objects.requireNonNull(range); Objects.requireNonNull(parameter); Objects.requireNonNull(value); } }
    public sealed interface Value permits ExpressionValue,MetaObjectValue,AccordingValue {}
    public record ExpressionValue(ExpressionSyntax expression) implements Value { public ExpressionValue { Objects.requireNonNull(expression); } }
    public record MetaObjectValue(NameSyntax object) implements Value { public MetaObjectValue { Objects.requireNonNull(object); } }
    public record AccordingValue(ExpressionSyntax selector,List<EnumAssignment> cases) implements Value { public AccordingValue { Objects.requireNonNull(selector); cases=List.copyOf(cases); } }
    public record EnumAssignment(SourceRange range,String first,Optional<String> last,Value value) { public EnumAssignment { Objects.requireNonNull(range); Objects.requireNonNull(first); Objects.requireNonNull(last); Objects.requireNonNull(value); } }
    private static void addValue(Value value,List<SyntaxNode> nodes) {
        if(value instanceof ExpressionValue e) nodes.add(e.expression());
        if(value instanceof MetaObjectValue m) nodes.add(m.object());
        if(value instanceof AccordingValue a) { nodes.add(a.selector()); a.cases().forEach(c->addValue(c.value(),nodes)); }
    }
}
