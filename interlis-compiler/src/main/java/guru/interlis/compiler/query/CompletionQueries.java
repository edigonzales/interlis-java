package guru.interlis.compiler.query;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.SyntaxToken;
import java.util.*;

/** Pure completion over captured syntax and published semantic visibility facts. */
final class CompletionQueries {
    private CompletionQueries() { }

    static CompletionResult complete(AnalysisSnapshot snapshot, CompletionQuery query,
                                     CancellationToken token) {
        Objects.requireNonNull(snapshot, "snapshot");
        token.throwIfCancellationRequested();
        var syntax = snapshot.syntaxSnapshot(query.cursor().source());
        SourceRange replacement = syntax.stream().flatMap(value -> value.tokens().stream())
                .filter(value -> value.kind() == guru.interlis.syntax.TokenKind.IDENTIFIER)
                .map(SyntaxToken::range).filter(range -> range.startOffset() <= query.cursor().startOffset()
                        && query.cursor().startOffset() <= range.endOffset()).findFirst().orElse(query.cursor());
        Symbol scope = completionScope(snapshot, query.cursor());
        var qualifier = completionQualifier(snapshot, query.cursor(), replacement, scope, token);
        var candidates = new ArrayList<CompletionCandidate>();
        if (scope != null) {
            var visible = qualifier.qualified() ? qualifier.members()
                    : snapshot.queries().visibleSymbols(new guru.interlis.model.ScopeRef(snapshot.snapshotId(),
                    guru.interlis.model.ScopeId.forOwner(scope.id())), query.prefix()).known().orElse(List.of());
            for (var reference : visible) {
                token.throwIfCancellationRequested();
                Symbol symbol = snapshot.semanticGraph().symbol(reference.symbol()).orElseThrow();
                if (symbol.kind() == guru.interlis.model.SymbolKind.UNSUPPORTED || !symbol.name().value().startsWith(query.prefix().value())) continue;
                candidates.add(new CompletionCandidate(symbol.name().value(), symbol.name().value(), kind(symbol), symbol.kind().name()));
            }
        }
        var language = scope == null ? syntax.flatMap(value -> value.language().version())
                : Optional.of(scope.languageVersion());
        if (!qualifier.qualified()) language.ifPresent(version -> guru.interlis.compiler.internal.antlr.LanguageProfile.keywords(version).stream()
                .filter(word -> word.startsWith(query.prefix().value())).sorted().forEach(word -> {
                    token.throwIfCancellationRequested();
                    candidates.add(new CompletionCandidate(word, word, CompletionKind.KEYWORD, "syntax/" + version.literal()));
                }));
        boolean incomplete = candidates.size() > query.limit() || scope == null
                || !qualifier.complete()
                || !snapshot.coverage().incompleteEntries().isEmpty()
                || syntax.stream().flatMap(value -> value.diagnostics().stream())
                        .anyMatch(diagnostic -> diagnostic.severity() == guru.interlis.diagnostics.Severity.ERROR);
        return new CompletionResult(candidates.stream().limit(query.limit()).toList(), replacement, incomplete,
                snapshot.snapshotId());
    }

    private record CompletionQualifier(boolean qualified, List<SymbolRef> members, boolean complete) {
        CompletionQualifier { members = List.copyOf(members); }
    }

    private static CompletionQualifier completionQualifier(AnalysisSnapshot snapshot, SourceRange cursor,
            SourceRange replacement, Symbol scope, CancellationToken token) {
        var syntax = snapshot.syntaxSnapshot(cursor.source());
        if (syntax.isEmpty()) return new CompletionQualifier(false, List.of(), true);
        var trivia = syntax.get().trivia().stream().map(guru.interlis.syntax.TriviaSpan::range).collect(java.util.stream.Collectors.toSet());
        var before = syntax.get().tokens().stream().filter(value -> value.kind()!=guru.interlis.syntax.TokenKind.EOF
                && value.range().endOffset()<=replacement.startOffset() && !trivia.contains(value.range())).toList();
        if (before.isEmpty() || !before.getLast().text().equals(".")) return new CompletionQualifier(false,List.of(),true);
        // Prefer the prefix target already established by the canonical binder.
        for (var reference : snapshot.semanticGraph().references()) {
            if (!reference.range().source().equals(cursor.source())) continue;
            for (int i=1;i<reference.segments().size();i++) {
                var segment = reference.segments().get(i);
                if (segment.range().startOffset()==replacement.startOffset()
                        && reference.segments().get(i-1).target().isPresent()) {
                    var target = new SymbolRef(snapshot.snapshotId(),reference.segments().get(i-1).target().orElseThrow());
                    return new CompletionQualifier(true,completionMembers(snapshot,target,token),true);
                }
            }
        }
        // Recovery uses actual syntax tokens and published visibility/member facts.
        var parts = new ArrayList<String>();
        for (int i=before.size()-2;i>=0;i-=2) {
            var segment = before.get(i);
            if (segment.kind()!=guru.interlis.syntax.TokenKind.IDENTIFIER && !segment.text().equals("INTERLIS")) break;
            parts.addFirst(segment.text());
            if (i<2 || !before.get(i-1).text().equals(".")) break;
        }
        if (parts.isEmpty() || scope==null) return new CompletionQualifier(true,List.of(),false);
        List<SymbolRef> choices;
        if (parts.getFirst().equals("INTERLIS")) {
            choices = snapshot.semanticGraph().symbols().stream()
                    .filter(symbol -> symbol.origin() instanceof guru.interlis.model.Origin.PredefinedOrigin
                            && symbol.owner().isEmpty() && symbol.languageVersion()==scope.languageVersion())
                    .map(symbol -> new SymbolRef(snapshot.snapshotId(),symbol.id())).toList();
            if (parts.size()==1) return new CompletionQualifier(true,choices,true);
            choices = exactCompletionName(snapshot,choices,parts.get(1));
            parts = new ArrayList<>(parts.subList(1,parts.size()));
        } else choices = exactCompletionName(snapshot,snapshot.queries().visibleSymbols(
                new guru.interlis.model.ScopeRef(snapshot.snapshotId(),guru.interlis.model.ScopeId.forOwner(scope.id())),
                new NamePrefix(parts.getFirst())).known().orElse(List.of()),parts.getFirst());
        for (int i=1;i<parts.size();i++) {
            if (choices.size()!=1) return new CompletionQualifier(true,List.of(),false);
            choices = exactCompletionName(snapshot,completionMembers(snapshot,choices.getFirst(),token),parts.get(i));
        }
        return choices.size()==1 ? new CompletionQualifier(true,completionMembers(snapshot,choices.getFirst(),token),true)
                : new CompletionQualifier(true,List.of(),false);
    }

    private static List<SymbolRef> exactCompletionName(AnalysisSnapshot snapshot,List<SymbolRef> choices,String name) {
        return choices.stream().filter(ref -> snapshot.semanticGraph().symbol(ref.symbol()).orElseThrow().name().value().equals(name))
                .filter(ref -> switch(snapshot.semanticGraph().symbol(ref.symbol()).orElseThrow().kind()) {
                    case MODEL, TOPIC, CLASS, STRUCTURE, ASSOCIATION, VIEW, DOMAIN, ATTRIBUTE, ROLE, VIEW_BASE -> true;
                    default -> false;
                }).toList();
    }

    private static List<SymbolRef> completionMembers(AnalysisSnapshot snapshot,SymbolRef target,CancellationToken token) {
        token.throwIfCancellationRequested();
        var direct = snapshot.queries().membersOf(target).known().orElse(List.of());
        Symbol symbol = snapshot.semanticGraph().symbol(target.symbol()).orElseThrow();
        if (!(symbol instanceof guru.interlis.model.AttributeSymbol) && !(symbol instanceof guru.interlis.model.RoleSymbol)) return direct;
        if (!direct.isEmpty()) return direct;
        List<SymbolId> roots = symbol instanceof guru.interlis.model.RoleSymbol role ? role.targetRoots()
                : snapshot.queries().effectiveType(target).known().map(type -> completionTypeRoots(snapshot,type.effectiveType(),new HashSet<>())).orElse(List.of());
        List<SymbolRef> common = null;
        for (var root : roots) {
            token.throwIfCancellationRequested();
            var members = snapshot.queries().membersOf(new SymbolRef(snapshot.snapshotId(),root)).known().orElse(List.of()).stream()
                    .filter(ref -> Set.of(guru.interlis.model.SymbolKind.ATTRIBUTE,guru.interlis.model.SymbolKind.ROLE,
                            guru.interlis.model.SymbolKind.ENUM_ELEMENT).contains(snapshot.semanticGraph().symbol(ref.symbol()).orElseThrow().kind())).toList();
            if (common==null) common=members;
            else {
                var names = members.stream().map(ref -> snapshot.semanticGraph().symbol(ref.symbol()).orElseThrow().name().value()).collect(java.util.stream.Collectors.toSet());
                common=common.stream().filter(ref -> names.contains(snapshot.semanticGraph().symbol(ref.symbol()).orElseThrow().name().value())).toList();
            }
        }
        return common==null ? List.of() : common;
    }

    private static List<SymbolId> completionTypeRoots(AnalysisSnapshot snapshot,guru.interlis.model.TypeId id,
                                                     Set<guru.interlis.model.TypeId> seen) {
        if (!seen.add(id)) return List.of();
        var type = snapshot.semanticGraph().type(id).orElse(null);
        return switch(type) {
            case guru.interlis.model.StructureValueType value -> value.targetRoots();
            case guru.interlis.model.ReferenceType value -> value.targetRoots();
            case guru.interlis.model.CollectionType value -> completionTypeRoots(snapshot,value.elementType(),seen);
            case guru.interlis.model.DomainReferenceType value -> snapshot.semanticGraph().declaredType(value.targetDomain())
                    .map(next -> completionTypeRoots(snapshot,next,seen)).orElse(List.of());
            case null, default -> List.of();
        };
    }

    private static Symbol completionScope(AnalysisSnapshot snapshot, SourceRange cursor) {
        return snapshot.semanticGraph().symbols().stream()
                .filter(symbol -> switch (symbol.kind()) {
                    case MODEL, TOPIC, CLASS, STRUCTURE, ASSOCIATION, VIEW, GRAPHIC, FUNCTION, DOMAIN -> true;
                    default -> false;
                }).filter(symbol -> symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                        && declared.range().source().equals(cursor.source())
                        && declared.range().startOffset() <= cursor.startOffset()
                        && cursor.endOffset() <= declared.range().endOffset())
                .min(Comparator.comparingInt(symbol -> ((guru.interlis.model.Origin.DeclaredOrigin) symbol.origin()).range().length()))
                .orElse(null);
    }

    private static CompletionKind kind(Symbol symbol) {
        return switch (symbol.kind()) {
            case MODEL -> CompletionKind.MODEL;
            case TOPIC -> CompletionKind.TOPIC;
            case CLASS -> CompletionKind.CLASS;
            case STRUCTURE -> CompletionKind.STRUCTURE;
            case ASSOCIATION -> CompletionKind.ASSOCIATION;
            case DOMAIN -> CompletionKind.DOMAIN;
            case ATTRIBUTE -> CompletionKind.ATTRIBUTE;
            case ROLE -> CompletionKind.ROLE;
            case PARAMETER -> CompletionKind.PARAMETER;
            case GRAPHIC -> CompletionKind.GRAPHIC;
            case DRAWING_RULE -> CompletionKind.DRAWING_RULE;
            case LINE_FORM -> CompletionKind.LINE_FORM;
            case METADATA_BASKET -> CompletionKind.METADATA_BASKET;
            case METAOBJECT -> CompletionKind.METAOBJECT;
            case CONSTRAINT -> CompletionKind.CONSTRAINT;
            case FUNCTION -> CompletionKind.FUNCTION;
            case UNIT -> CompletionKind.UNIT;
            case VIEW -> CompletionKind.VIEW;
            case VIEW_BASE -> CompletionKind.VIEW_BASE;
            case CONTEXT -> CompletionKind.CONTEXT;
            case ENUM_ELEMENT -> CompletionKind.ENUM_ELEMENT;
            case UNSUPPORTED -> throw new IllegalArgumentException("unsupported declarations have no semantic completion kind");
        };
    }
}
