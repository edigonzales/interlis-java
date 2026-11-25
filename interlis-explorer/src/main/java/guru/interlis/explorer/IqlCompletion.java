package guru.interlis.explorer;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static guru.interlis.explorer.IqlModel.shortName;

final class IqlCompletion {
    private record Prefix(List<String> qualifiers, int left) {}
    private record Source(ObjectDescriptor type, TypeDescriptor value, String alias, String description) {
        Source(ObjectDescriptor type, String alias) {
            this(type, null, alias, "Alias of " + type.name().declared());
        }
    }
    private record Root(Source source, int from, int next) {}
    private record CollectionTarget(ObjectDescriptor type, TypeDescriptor value, String description, boolean ordered) {}
    private record Expansion(List<Source> visible, Source previous, int keyword, int pathEnd, CollectionTarget target,
                             Source element, int aliasEnd, Source position, boolean hasPosition, int next) {}
    private enum FieldKind { ANY, COLLECTION, ATTRIBUTE_COLLECTION, GEOMETRY, OBJECT_ALIAS, TEXT }

    private static final List<String> KEYWORDS = List.of("SELECT", "FROM", "AS", "WHERE", "AND", "OR", "NOT", "IS",
            "EXPAND", "IN", "BETWEEN", "MISSING", "UNDEFINED", "UNRESOLVED", "TRUE", "FALSE", "EXISTS", "INTERSECTS",
            "WITHIN_DISTANCE", "CONTAINS", "STARTS_WITH", "COUNT", "GROUP", "ORDER", "BY", "ASC", "DESC", "LIMIT");

    private final IqlModel model;
    private final List<IqlSyntax.Token> tokens;
    private final Map<String, Iql.Completion> matches = new LinkedHashMap<>();
    private final int start;
    private final int end;
    private final int cursor;
    private final String word;
    private final boolean inString;

    IqlCompletion(IqlModel model, String text, int caret) {
        this.model = model;
        var scanned = IqlSyntax.scan(text, false);
        tokens = scanned.subList(0, scanned.size() - 1);
        int position = 0;
        while (position < tokens.size() && precedesCaret(tokens.get(position), caret)) position++;
        IqlSyntax.Token current = position < tokens.size() ? tokens.get(position) : null;
        inString = current != null && current.kind() == IqlSyntax.Kind.STRING
                && current.start() < caret && caret <= current.end();
        if (current != null && current.kind() == IqlSyntax.Kind.WORD && current.start() <= caret) {
            start = current.start();
            end = current.end();
        } else {
            start = end = caret;
        }
        cursor = position;
        word = text.substring(start, caret);
    }

    private static boolean precedesCaret(IqlSyntax.Token token, int caret) {
        // At a boundary, keep a completed word or advance past punctuation to the next word.
        return token.end() < caret || token.end() == caret
                && token.kind() != IqlSyntax.Kind.WORD && token.kind() != IqlSyntax.Kind.STRING;
    }

    List<Iql.Completion> suggestions() {
        if (inString) return List.of();
        Prefix prefix = prefix();
        if (at(prefix.left() - 1, "FROM")) {
            sourceSuggestions(prefix);
            return List.copyOf(matches.values());
        }

        if (prefix.qualifiers().isEmpty() && (at(cursor - 1, "GROUP") || at(cursor - 1, "ORDER"))) {
            suggest("BY", "IQL keyword");
            return List.copyOf(matches.values());
        }
        Root root = source();
        if (root != null) {
            var expansions = expansions(root);
            for (var expansion : expansions) {
                if (prefix.left() == expansion.keyword() + 1) {
                    expansionPathSuggestions(prefix, expansion);
                    return List.copyOf(matches.values());
                }
                if (completeExpansionDeclaration(expansion, prefix)) return List.copyOf(matches.values());
            }
            if (prefix.qualifiers().isEmpty() && cursor == root.next() && expansions.isEmpty()) {
                nextClauseSuggestions();
            }
            var visible = scopes(root.source(), expansions);
            if (enumerationSuggestions(prefix, visible) || completeSpatialAnchor(prefix)) {
                return List.copyOf(matches.values());
            }
            var kind = fieldKind(prefix);
            if (kind == FieldKind.ANY && prefix.qualifiers().isEmpty()) orderAliasSuggestions(root);
            pathSuggestions(prefix, visible, kind);
            if (kind != FieldKind.ANY) return List.copyOf(matches.values());
        }
        if (prefix.qualifiers().isEmpty()) {
            if (root == null || cursor < root.from()) suggest("GEOMETRY", "IQL projection");
            IqlSyntax.VALUE_FUNCTIONS.forEach(name -> suggest(name, "IQL function"));
            KEYWORDS.forEach(keyword -> suggest(keyword, "IQL keyword"));
        }
        return List.copyOf(matches.values());
    }

    private void sourceSuggestions(Prefix prefix) {
        var sources = model.sources();
        int length = prefix.qualifiers().size();
        for (var type : sources) {
            List<String> parts = type.name().declared().segments();
            if (length < parts.size() && parts.subList(0, length).equals(prefix.qualifiers())) {
                String description = length == 0 ? "Model" : length == parts.size() - 1
                        ? type.kind().toString() : "Topic";
                suggest(parts.get(length), description);
            }
        }
        if (length == 0 && !at(cursor + 1, ".")) {
            var byShortName = new LinkedHashMap<String, List<ObjectDescriptor>>();
            for (var type : sources) {
                String shortName = type.name().declared().segments().getLast();
                byShortName.computeIfAbsent(shortName, ignored -> new ArrayList<>()).add(type);
            }
            for (var entry : byShortName.entrySet()) {
                String name = entry.getKey();
                var candidates = entry.getValue();
                if (candidates.size() == 1) suggest(name, candidates.getFirst().kind().toString());
                else if (!word.isEmpty()) {
                    for (var type : candidates) {
                        suggestMatching(name, type.name().declared().toString(), type.kind().toString());
                    }
                }
            }
        }
    }

    private FieldKind fieldKind(Prefix prefix) {
        if (at(prefix.left() - 1, "(")) {
            int function = prefix.left() - 2;
            if (at(function, "GEOMETRY") || at(function, "INTERSECTS") || at(function, "WITHIN_DISTANCE")) {
                return FieldKind.GEOMETRY;
            }
            if (at(function, "EXISTS")) return FieldKind.COLLECTION;
            if (at(function, "ELEMENT_COUNT")) return FieldKind.ATTRIBUTE_COLLECTION;
            if (at(function, "CONTAINS") || at(function, "STARTS_WITH")) return FieldKind.TEXT;
            if (at(function, "TID") || at(function, "OID") || at(function, "BID")) return FieldKind.OBJECT_ALIAS;
        }
        return FieldKind.ANY;
    }

    private void expansionPathSuggestions(Prefix prefix, Expansion expansion) {
        var previous = expansion.previous();
        if (previous != null && prefix.qualifiers().isEmpty()) {
            suggest(previous.alias(), previous.description());
        } else if (previous == null || prefix.qualifiers().getFirst().equals(previous.alias())) {
            pathSuggestions(prefix, expansion.visible(), FieldKind.COLLECTION);
        }
    }

    private void orderAliasSuggestions(Root root) {
        boolean order = false;
        int depth = 0;
        for (int i = root.next(); i < cursor; i++) {
            if (at(i, "(")) depth++;
            else if (at(i, ")")) depth--;
            else if (depth == 0 && at(i, "ORDER") && at(i + 1, "BY") && i + 1 < cursor) order = true;
            else if (depth == 0 && at(i, "LIMIT")) order = false;
        }
        if (!order || depth != 0) return;
        for (int i = 1; i < root.from(); i++) {
            if (at(i, "(")) depth++;
            else if (at(i, ")")) depth--;
            else if (depth == 0 && at(i, "AS") && i + 1 < root.from() && wordAt(i + 1)) {
                suggest(tokens.get(i + 1).text(), "SELECT alias");
            }
        }
    }

    private void pathSuggestions(Prefix prefix, List<Source> visible, FieldKind kind) {
        ObjectDescriptor effective;
        if (prefix.qualifiers().isEmpty()) {
            for (var scope : visible.reversed()) {
                if (kind == FieldKind.OBJECT_ALIAS) {
                    if (scope.type() != null && (scope.type().kind() == SymbolKind.CLASS
                            || scope.type().kind() == SymbolKind.ASSOCIATION)) suggest(scope.alias(), scope.description());
                } else if (kind == FieldKind.ANY || scope.type() != null
                        || scope.value() != null && suggestAttribute(scope.value(), kind)) {
                    suggest(scope.alias(), scope.description());
                }
            }
            effective = visible.getLast().type();
        } else {
            effective = follow(visible, prefix.qualifiers());
        }
        if (effective == null || kind == FieldKind.OBJECT_ALIAS) return;
        for (var slot : effective.slots()) {
            if (!(slot instanceof AttributeSlot attr) || attr.attribute().transientAttribute()) continue;
            var type = model.type(attr);
            if (suggestAttribute(type, kind)) {
                String description = type instanceof CollectionType collection ? collection.collectionKind().name()
                        : type.kind().toString();
                suggest(shortName(attr), description);
            }
        }
        var roles = model.roles(effective);
        for (var role : roles) {
            String name = shortName(role.target());
            boolean navigable = model.commonTarget(role.target().role().targetRoots()).isPresent()
                    && model.attribute(effective, name).isEmpty()
                    && roles.stream().filter(other -> shortName(other.target()).equals(name)).count() == 1;
            if (kind == FieldKind.ANY || navigable && (!role.multiple() || kind == FieldKind.COLLECTION)) {
                suggest(name, role.multiple() ? "Role · multi-valued" : "Role");
            }
        }
    }

    private boolean suggestAttribute(TypeDescriptor type, FieldKind kind) {
        if (kind == FieldKind.ANY || model.target(type).isPresent()) return true;
        return switch (kind) {
            case COLLECTION, ATTRIBUTE_COLLECTION -> type instanceof CollectionType;
            case GEOMETRY -> type instanceof GeometryType || type instanceof CoordinateType;
            case TEXT -> type instanceof TextType;
            default -> false;
        };
    }

    /** Recognizes gaps for AS and optional AT within each leading EXPAND declaration. */
    private boolean completeExpansionDeclaration(Expansion expansion, Prefix prefix) {
        if (prefix.left() <= expansion.keyword() || prefix.left() > expansion.next()) return false;
        if (cursor == expansion.pathEnd() && expansion.target() != null) suggest("AS", "IQL keyword");
        if (cursor == expansion.aliasEnd() && expansion.element() != null && expansion.target().ordered()) {
            suggest("AT", "IQL keyword");
        }
        if (cursor == expansion.next() && expansion.element() != null
                && (!expansion.hasPosition() || expansion.position() != null)) {
            nextClauseSuggestions();
        }
        return true;
    }

    private void nextClauseSuggestions() {
        for (String clause : List.of("EXPAND", "WHERE", "GROUP", "ORDER", "LIMIT")) {
            suggest(clause, "IQL keyword");
        }
    }

    private Prefix prefix() {
        var qualifiers = new ArrayList<String>();
        int left = cursor;
        while (left >= 2 && tokens.get(left - 1).is(".") && tokens.get(left - 2).kind() == IqlSyntax.Kind.WORD) {
            qualifiers.addFirst(tokens.get(left - 2).text());
            left -= 2;
        }
        return new Prefix(List.copyOf(qualifiers), left);
    }

    private Root source() {
        int depth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.is("(")) depth++;
            if (token.is(")")) depth--;
            if (depth != 0 || !token.is("FROM")) continue;
            var path = new ArrayList<String>();
            int stop = path(i + 1, path);
            if (path.isEmpty()) return null;
            var name = String.join(".", path);
            var sources = model.matchingSources(name);
            if (sources.size() != 1) return null;
            boolean explicit = at(stop, "AS");
            if (explicit) stop++;
            String alias = path.getLast();
            if (wordAt(stop) && (explicit || !IqlSyntax.clause(tokens.get(stop)) && !isExpansionStart(stop))) {
                alias = tokens.get(stop).text();
                if (explicit || cursor != stop) stop++;
            }
            return new Root(new Source(sources.getFirst(), alias), i, stop);
        }
        return null;
    }

    private boolean isExpansionStart(int index) {
        return at(index, "EXPAND") && (cursor == index + 1 || IqlSyntax.startsExpansion(tokens, index));
    }

    private List<Expansion> expansions(Root root) {
        var expanded = new ArrayList<Expansion>();
        var visible = new ArrayList<>(List.of(root.source()));
        int next = root.next();
        while (at(next, "EXPAND")) {
            Source previous = expanded.isEmpty() ? null : expanded.getLast().element();
            var expansion = expansion(visible, previous, next);
            expanded.add(expansion);
            if (expansion.element() == null || expansion.hasPosition() && expansion.position() == null) break;
            visible.addFirst(expansion.element());
            if (expansion.position() != null) visible.addFirst(expansion.position());
            next = expansion.next();
        }
        return List.copyOf(expanded);
    }

    private Expansion expansion(List<Source> visible, Source previous, int keyword) {
        var names = new ArrayList<String>();
        int pathEnd = path(keyword + 1, names);
        CollectionTarget target = previous == null || !names.isEmpty() && names.getFirst().equals(previous.alias())
                ? collection(visible, names) : null;
        boolean hasAlias = at(pathEnd, "AS");
        int aliasIndex = hasAlias ? pathEnd + 1 : pathEnd;
        int aliasEnd = hasAlias && wordAt(aliasIndex) ? aliasIndex + 1 : aliasIndex;
        String alias = aliasEnd > aliasIndex ? tokens.get(aliasIndex).text() : null;
        Source element = target != null && alias != null && alias(visible, alias) == null
                ? new Source(target.type(), target.value(), alias, target.description()) : null;
        boolean hasPosition = at(aliasEnd, "AT");
        int next = hasPosition ? aliasEnd + 1 : aliasEnd;
        Source position = null;
        if (hasPosition && wordAt(next) && !IqlSyntax.clause(tokens.get(next)) && !isExpansionStart(next)) {
            String name = tokens.get(next++).text();
            if (element != null && target.ordered() && !name.equals(alias) && alias(visible, name) == null) {
                position = new Source(null, null, name, "LIST position");
            }
        }
        return new Expansion(List.copyOf(visible), previous, keyword, pathEnd, target, element, aliasEnd, position,
                hasPosition, next);
    }

    private CollectionTarget collection(List<Source> visible, List<String> names) {
        if (names.isEmpty() || names.size() == 1 && alias(visible, names.getFirst()) != null) return null;
        var owner = names.size() == 1 ? visible.getLast().type() : follow(visible, names.subList(0, names.size() - 1));
        if (owner == null) return null;
        String name = names.getLast();
        var roles = model.roles(owner).stream().filter(role -> shortName(role.target()).equals(name)).toList();
        TypeDescriptor type = model.attribute(owner, name).map(model::type).orElse(null);
        if (type instanceof CollectionType collection && roles.isEmpty()) {
            TypeDescriptor element = model.type(collection.elementType());
            return new CollectionTarget(model.target(element).orElse(null), element,
                    collection.collectionKind().name() + " element", collection.collectionKind() == CollectionKind.LIST);
        }
        if (type == null && roles.size() == 1 && roles.getFirst().multiple()) {
            return model.commonTarget(roles.getFirst().target().role().targetRoots())
                    .map(target -> new CollectionTarget(target, null, "Role target", false)).orElse(null);
        }
        return null;
    }

    private boolean at(int index, String text) {
        return index >= 0 && index < tokens.size() && tokens.get(index).is(text);
    }

    private boolean wordAt(int index) {
        return index >= 0 && index < tokens.size() && tokens.get(index).kind() == IqlSyntax.Kind.WORD;
    }

    private int path(int start, List<String> path) {
        int next = start;
        while (next < tokens.size() && tokens.get(next).kind() == IqlSyntax.Kind.WORD) {
            path.add(tokens.get(next++).text());
            if (next >= tokens.size() || !tokens.get(next).is(".")) break;
            next++;
        }
        return next;
    }

    private List<Source> scopes(Source root, List<Expansion> expansions) {
        var visible = new ArrayList<>(List.of(root));
        var stack = new ArrayDeque<Boolean>();
        for (var expansion : expansions) {
            if (expansion.element() != null) visible.addFirst(expansion.element());
            if (expansion.position() != null) visible.addFirst(expansion.position());
        }
        for (int i = 0; i < cursor; i++) {
            if (tokens.get(i).is("(")) {
                Source binding = i > 0 && tokens.get(i - 1).is("EXISTS") ? existsScope(visible, i + 1) : null;
                stack.push(binding != null);
                if (binding != null) visible.add(binding);
            } else if (tokens.get(i).is(")") && !stack.isEmpty() && stack.pop()) {
                visible.removeLast();
            }
        }
        return visible;
    }

    private Source existsScope(List<Source> visible, int start) {
        var names = new ArrayList<String>();
        int next = path(start, names);
        if (names.isEmpty() || next + 2 >= cursor || !tokens.get(next).is("AS") || !tokens.get(next + 2).is("WHERE")) return null;
        var collection = collection(visible, names);
        return collection == null ? null : new Source(collection.type(), collection.value(),
                tokens.get(next + 1).text(), collection.description());
    }

    private static Source alias(List<Source> visible, String name) {
        for (var source : visible.reversed()) {
            if (source.alias().equals(name)) return source;
        }
        return null;
    }

    private ObjectDescriptor follow(List<Source> visible, List<String> path) {
        Source binding = alias(visible, path.getFirst());
        var type = (binding == null ? visible.getLast() : binding).type();
        for (int i = binding == null ? 0 : 1; i < path.size() && type != null; i++) {
            String field = path.get(i);
            var attribute = model.attribute(type, field);
            var roles = model.roles(type).stream().filter(role -> shortName(role.target()).equals(field)).toList();
            if (attribute.isPresent() && roles.isEmpty()) {
                type = model.target(model.type(attribute.orElseThrow())).orElse(null);
            } else if (roles.size() == 1 && attribute.isEmpty() && !roles.getFirst().multiple()) {
                type = model.commonTarget(roles.getFirst().target().role().targetRoots()).orElse(null);
            } else return null;
        }
        return type;
    }

    private boolean completeSpatialAnchor(Prefix prefix) {
        if (!prefix.qualifiers().isEmpty() || !at(cursor - 1, ",")) return false;
        int argument = cursor - 2;
        while (at(argument - 1, ".") && wordAt(argument - 2)) argument -= 2;
        if (!wordAt(argument) || !at(argument - 1, "(")) return false;
        if (at(argument - 2, "WITHIN_DISTANCE")) suggest("POINT", "IQL spatial anchor");
        else if (at(argument - 2, "INTERSECTS")) suggest("BOX", "IQL spatial anchor");
        else return false;
        return true;
    }

    private boolean enumerationSuggestions(Prefix prefix, List<Source> visible) {
        boolean hash = at(prefix.left() - 1, "#");
        if (!hash && !prefix.qualifiers().isEmpty()) return false;
        int right = prefix.left() - (hash ? 1 : 0);
        int operator = right - 1;
        if (at(operator, "(") || at(operator, ",")) {
            operator = listOperator(operator);
        } else if (!at(operator, "=") && !at(operator, "!=") && !at(operator, "<>")) {
            return hash;
        }
        if (operator < 1) return hash;
        int pathEnd = at(operator - 1, "NOT") ? operator - 1 : operator;
        int pathStart = pathEnd - 1;
        if (!wordAt(pathStart)) return hash;
        while (at(pathStart - 1, ".") && wordAt(pathStart - 2)) pathStart -= 2;
        var names = new ArrayList<String>();
        path(pathStart, names);
        var enumeration = enumerationType(visible, names);
        if (enumeration == null) return hash;
        int length = prefix.qualifiers().size();
        for (var option : enumeration.values()) {
            List<String> segments = List.of(option.path().split("[.]"));
            if (length >= segments.size() || !segments.subList(0, length).equals(prefix.qualifiers())) continue;
            String name = segments.get(length);
            String insert = hash ? name : "#" + name;
            if (!startsWithWord(name) || matches.containsKey(insert)) continue;
            String qualified = String.join(".", segments.subList(0, length + 1));
            String description = IqlModel.validEnumerationValue(enumeration, qualified)
                    ? "Enumeration value" : "Enumeration branch";
            matches.put(insert, new Iql.Completion(insert, description, start, end));
        }
        return hash || at(operator, "IN");
    }

    private int listOperator(int index) {
        int nested = 0;
        for (int i = index; i >= 0; i--) {
            if (at(i, ")")) nested++;
            if (at(i, "(")) {
                if (nested == 0) return at(i - 1, "IN") ? i - 1 : -1;
                nested--;
            }
        }
        return -1;
    }

    private EnumerationType enumerationType(List<Source> visible, List<String> path) {
        Source binding = alias(visible, path.getFirst());
        if (path.size() == 1 && binding != null) {
            return binding.value() instanceof EnumerationType enumeration ? enumeration : null;
        }
        var owner = path.size() == 1 ? visible.getLast().type() : follow(visible, path.subList(0, path.size() - 1));
        if (owner == null || model.roles(owner).stream().anyMatch(role -> shortName(role.target()).equals(path.getLast()))) {
            return null;
        }
        var type = model.attribute(owner, path.getLast()).map(model::type).orElse(null);
        return type instanceof EnumerationType enumeration ? enumeration : null;
    }

    private void suggest(String name, String description) {
        suggestMatching(name, name, description);
    }

    private void suggestMatching(String name, String insertion, String description) {
        if (startsWithWord(name)) {
            matches.putIfAbsent(insertion, new Iql.Completion(insertion, description, start, end));
        }
    }

    private boolean startsWithWord(String name) {
        return name.regionMatches(true, 0, word, 0, word.length());
    }
}
