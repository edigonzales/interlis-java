package guru.interlis.explorer;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static guru.interlis.explorer.IqlModel.shortName;

final class IqlCompletion {
    private record Prefix(List<String> qualifiers, int left) {}
    private record Source(ObjectDescriptor type, String alias, String description) {
        Source(ObjectDescriptor type, String alias) {
            this(type, alias, "Alias of " + type.name().declared());
        }
    }
    private record Root(Source source, int from, int next) {}
    private record Expansion(CollectionType type, Source element, Source position, int pathEnd, int aliasEnd) {}
    private enum FieldKind { ANY, COLLECTION, GEOMETRY }

    private static final List<String> KEYWORDS = List.of("SELECT", "FROM", "AS", "WHERE", "AND", "OR", "NOT", "IS",
            "MISSING", "UNDEFINED", "UNRESOLVED", "TRUE", "FALSE", "EXISTS", "INTERSECTS", "BOX",
            "COUNT", "ORDER", "BY", "ASC", "DESC", "LIMIT");

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
        if (prefix.left() > 0 && tokens.get(prefix.left() - 1).is("FROM")) {
            for (var type : model.sources()) {
                List<String> parts = type.name().declared().segments();
                int length = prefix.qualifiers().size();
                if (length < parts.size() && parts.subList(0, length).equals(prefix.qualifiers())) {
                    String description = length == 0 ? "Model" : length == parts.size() - 1
                            ? type.kind().toString() : "Topic";
                    suggest(parts.get(length), description);
                }
            }
            return List.copyOf(matches.values());
        }

        Root root = source();
        if (root != null) {
            var expansion = expansion(root);
            if (expansion != null && prefix.left() == root.next() + 1) {
                pathSuggestions(prefix, List.of(root.source()), FieldKind.COLLECTION);
                return List.copyOf(matches.values());
            }
            if (expansion != null && completeExpansionDeclaration(root, expansion, prefix)) {
                return List.copyOf(matches.values());
            }
            if (prefix.qualifiers().isEmpty() && cursor == root.next() && expansion == null) {
                suggest("EXPAND", "IQL keyword");
            }
            var fieldKind = prefix.left() < root.from() && at(prefix.left() - 2, "GEOMETRY")
                    && at(prefix.left() - 1, "(") ? FieldKind.GEOMETRY : FieldKind.ANY;
            var visible = scopes(root.source(), expansion);
            pathSuggestions(prefix, visible, fieldKind);
            if (fieldKind == FieldKind.GEOMETRY) return List.copyOf(matches.values());
        }
        if (prefix.qualifiers().isEmpty()) {
            if (root == null || cursor < root.from()) suggest("GEOMETRY", "IQL projection");
            KEYWORDS.forEach(keyword -> suggest(keyword, "IQL keyword"));
        }
        return List.copyOf(matches.values());
    }

    private void pathSuggestions(Prefix prefix, List<Source> visible, FieldKind kind) {
        ObjectDescriptor effective;
        if (prefix.qualifiers().isEmpty()) {
            for (var scope : visible.reversed()) suggest(scope.alias(), scope.description());
            effective = visible.getLast().type();
        } else {
            effective = follow(visible, prefix.qualifiers(), false);
        }
        if (effective == null) return;
        for (var slot : effective.slots()) {
            if (!(slot instanceof AttributeSlot attr) || attr.attribute().transientAttribute()) continue;
            var type = model.type(attr);
            if (type instanceof CollectionType collection) {
                if (kind != FieldKind.GEOMETRY) suggest(shortName(attr), collection.collectionKind().name());
            } else if (kind == FieldKind.ANY || kind == FieldKind.GEOMETRY
                    && (type instanceof GeometryType || type instanceof CoordinateType) || model.target(type).isPresent()) {
                suggest(shortName(attr), type.kind().toString());
            }
        }
        for (var role : model.roles(effective)) {
            if (kind == FieldKind.ANY || !role.multiple()) {
                suggest(shortName(role.target()), role.multiple() ? "Role · multi-valued" : "Role");
            }
        }
    }

    /** Recognizes the gaps for keywords and aliases in the leading EXPAND declaration. */
    private boolean completeExpansionDeclaration(Root root, Expansion expansion, Prefix prefix) {
        int last = expansion.aliasEnd();
        boolean hasPosition = at(last, "AT");
        if (hasPosition) last += 2;
        if (prefix.left() <= root.next() || prefix.left() > last) return false;
        if (cursor == expansion.pathEnd() && expansion.type() != null) suggest("AS", "IQL keyword");
        if (cursor == expansion.aliasEnd() && expansion.element() != null
                && expansion.type().collectionKind() == CollectionKind.LIST) {
            suggest("AT", "IQL keyword");
        }
        if (cursor == last && expansion.element() != null && (!hasPosition || expansion.position() != null)) {
            suggest("WHERE", "IQL keyword");
            suggest("ORDER", "IQL keyword");
            suggest("LIMIT", "IQL keyword");
        }
        return true;
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
            var type = model.sources().stream().filter(source -> source.name().declared().toString().equals(name)).findFirst();
            if (type.isEmpty()) return null;
            boolean explicit = at(stop, "AS");
            if (explicit) stop++;
            String alias = path.getLast();
            if (wordAt(stop) && (explicit || !IqlSyntax.clause(tokens.get(stop)) && !isExpansionStart(stop))) {
                alias = tokens.get(stop).text();
                if (explicit || cursor != stop) stop++;
            }
            return new Root(new Source(type.orElseThrow(), alias), i, stop);
        }
        return null;
    }

    private boolean isExpansionStart(int index) {
        return at(index, "EXPAND") && (cursor == index + 1 || wordAt(index + 1)
                && !IqlSyntax.clause(tokens.get(index + 1)));
    }

    private Expansion expansion(Root root) {
        if (!at(root.next(), "EXPAND")) return null;
        int start = root.next() + 1;
        var names = new ArrayList<String>();
        int pathEnd = path(start, names);
        var type = collection(root.source(), names);
        if (type == null || !at(pathEnd, "AS") || !wordAt(pathEnd + 1)) {
            return new Expansion(type, null, null, pathEnd, pathEnd + 1);
        }
        var alias = tokens.get(pathEnd + 1).text();
        int aliasEnd = pathEnd + 2;
        if (alias.equals(root.source().alias())) return new Expansion(type, null, null, pathEnd, aliasEnd);
        var element = new Source(model.target(model.type(type.elementType())).orElse(null), alias,
                type.collectionKind().name() + " element");
        Source position = null;
        if (type.collectionKind() == CollectionKind.LIST && at(aliasEnd, "AT") && wordAt(aliasEnd + 1)
                && !IqlSyntax.clause(tokens.get(aliasEnd + 1))) {
            var name = tokens.get(aliasEnd + 1).text();
            if (!name.equals(alias) && !name.equals(root.source().alias())) position = new Source(null, name, "LIST position");
        }
        return new Expansion(type, element, position, pathEnd, aliasEnd);
    }

    private CollectionType collection(Source root, List<String> names) {
        if (names.isEmpty() || names.size() == 1 && names.getFirst().equals(root.alias())) return null;
        var owner = names.size() == 1 ? root.type() : follow(List.of(root), names.subList(0, names.size() - 1), false);
        if (owner == null || model.roles(owner).stream().anyMatch(role -> shortName(role.target()).equals(names.getLast()))) {
            return null;
        }
        var type = model.attribute(owner, names.getLast()).map(model::type).orElse(null);
        return type instanceof CollectionType collection ? collection : null;
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

    private List<Source> scopes(Source root, Expansion expansion) {
        var visible = new ArrayList<Source>();
        var stack = new ArrayDeque<Boolean>();
        if (expansion != null) {
            if (expansion.element() != null) visible.add(expansion.element());
            if (expansion.position() != null) visible.add(expansion.position());
        }
        visible.add(root);
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
        var type = follow(visible, names, true);
        return type == null ? null : new Source(type, tokens.get(next + 1).text());
    }

    private ObjectDescriptor follow(List<Source> visible, List<String> path, boolean collectionEnd) {
        var type = visible.getLast().type();
        int first = 0;
        for (Source source : visible.reversed()) {
            if (source.alias().equals(path.getFirst())) {
                type = source.type();
                first = 1;
                break;
            }
        }
        for (int i = first; i < path.size() && type != null; i++) {
            String field = path.get(i);
            boolean collection = collectionEnd && i == path.size() - 1;
            var attribute = model.attribute(type, field);
            var roles = model.roles(type).stream().filter(role -> shortName(role.target()).equals(field)).toList();
            if (attribute.isPresent() && roles.isEmpty()) {
                var attributeType = model.type(attribute.orElseThrow());
                if (collection && attributeType instanceof CollectionType repeated) attributeType = model.type(repeated.elementType());
                else if (collection) return null;
                type = model.target(attributeType).orElse(null);
            } else if (roles.size() == 1 && attribute.isEmpty() && roles.getFirst().multiple() == collection) {
                type = model.commonTarget(roles.getFirst().target().role().targetRoots()).orElse(null);
            } else return null;
        }
        return type;
    }

    private void suggest(String name, String description) {
        if (name.toUpperCase(Locale.ROOT).startsWith(word.toUpperCase(Locale.ROOT))) {
            matches.putIfAbsent(name, new Iql.Completion(name, description, start, end));
        }
    }
}
