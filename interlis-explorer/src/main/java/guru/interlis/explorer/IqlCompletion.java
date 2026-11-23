package guru.interlis.explorer;

import guru.interlis.model.CollectionType;
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
    private record Source(ObjectDescriptor type, String alias) {}

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
        while (position < tokens.size() && tokens.get(position).end() < caret) position++;
        IqlSyntax.Token current = position < tokens.size() ? tokens.get(position) : null;
        inString = current != null && current.kind() == IqlSyntax.Kind.STRING
                && current.start() < caret && caret <= current.end();
        if (current != null && current.kind() == IqlSyntax.Kind.WORD && current.start() <= caret) {
            start = current.start();
            end = current.end();
        } else {
            if (current != null && current.end() <= caret) position++;
            start = end = caret;
        }
        cursor = position;
        word = text.substring(start, caret);
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

        Source root = source();
        if (root != null) {
            var visible = scopes(root);
            var effective = visible.getLast().type();
            if (prefix.qualifiers().isEmpty()) {
                for (var scope : visible) suggest(scope.alias(), "Alias of " + scope.type().name().declared());
            } else {
                effective = follow(visible, prefix.qualifiers(), false);
            }
            if (effective != null) {
                for (var slot : effective.slots()) {
                    if (slot instanceof AttributeSlot attr && !attr.attribute().transientAttribute()) {
                        suggest(shortName(attr), model.type(attr).kind().toString());
                    }
                }
                for (var role : model.roles(effective)) suggest(shortName(role.target()), role.multiple() ? "Role · multi-valued" : "Role");
            }
        }
        if (prefix.qualifiers().isEmpty()) KEYWORDS.forEach(keyword -> suggest(keyword, "IQL keyword"));
        return List.copyOf(matches.values());
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

    private Source source() {
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
            if (stop < tokens.size() && tokens.get(stop).is("AS")) stop++;
            String alias = stop < tokens.size() && tokens.get(stop).kind() == IqlSyntax.Kind.WORD
                    && !IqlSyntax.clause(tokens.get(stop)) ? tokens.get(stop).text() : path.getLast();
            return new Source(type.orElseThrow(), alias);
        }
        return null;
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

    private List<Source> scopes(Source root) {
        var visible = new ArrayList<Source>();
        var stack = new ArrayDeque<Boolean>();
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
