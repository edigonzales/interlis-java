package guru.interlis.syntax;

import guru.interlis.model.Modifier;
import guru.interlis.source.SourceRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Structured view syntax; source ranges and ordered contents are retained without reparsing text. */
public record ViewDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
        Optional<SourceRange> nameRange, Set<Modifier> modifiers, Optional<NameSyntax> baseView,
        Optional<Formation> formation, List<BaseExtension> baseExtensions,
        List<ExpressionSyntax> selections, List<Member> members,
        List<ConstraintDeclarationSyntax> constraints, Optional<String> endName) implements DeclarationSyntax {
    public ViewDeclarationSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(name);
        Objects.requireNonNull(nameRange); modifiers = Set.copyOf(modifiers);
        Objects.requireNonNull(baseView); Objects.requireNonNull(formation);
        baseExtensions = List.copyOf(baseExtensions); selections = List.copyOf(selections);
        members = List.copyOf(members); constraints = List.copyOf(constraints); Objects.requireNonNull(endName);
    }
    public enum FormationKind { PROJECTION, JOIN, UNION, AGGREGATION, INSPECTION, AREA_INSPECTION }
    public record Base(NameSyntax alias, NameSyntax target, boolean renamed, boolean orNull) {}
    public record Formation(FormationKind kind, List<Base> bases, List<PathExpressionSyntax> paths) {
        public Formation { Objects.requireNonNull(kind); bases = List.copyOf(bases); paths = List.copyOf(paths); }
    }
    public record BaseExtension(NameSyntax base, List<Base> extensions) {
        public BaseExtension { Objects.requireNonNull(base); extensions = List.copyOf(extensions); }
    }
    public sealed interface Member permits AllOf, Attribute {}
    public record AllOf(NameSyntax base) implements Member {}
    public record Attribute(AttributeDeclarationSyntax declaration) implements Member {}
    @Override public SyntaxKind kind() { return SyntaxKind.VIEW; }
    @Override public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>(); baseView.ifPresent(result::add);
        formation.ifPresent(f -> { f.bases().forEach(b -> { result.add(b.alias()); result.add(b.target()); }); result.addAll(f.paths()); });
        baseExtensions.forEach(e -> { result.add(e.base()); e.extensions().forEach(b -> { result.add(b.alias()); result.add(b.target()); }); });
        result.addAll(selections);
        members.forEach(m -> { if (m instanceof AllOf a) result.add(a.base()); else result.add(((Attribute)m).declaration()); });
        result.addAll(constraints); return List.copyOf(result);
    }
}
