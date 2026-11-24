package guru.interlis.explorer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class IqlSyntax {
    enum Kind { WORD, STRING, NUMBER, SYMBOL, END }

    record Token(Kind kind, String text, int start, int end) {
        boolean is(String value) {
            return kind != Kind.STRING && text.equalsIgnoreCase(value);
        }

        Iql.QueryException error(String message) {
            return new Iql.QueryException(message, start, end);
        }
    }

    sealed interface Expression permits Path, Literal, Test, Not, Comparison, Junction, Exists, Intersects {}

    enum LiteralKind { TEXT, NUMBER, BOOLEAN, ENUMERATION }
    enum State { MISSING, UNDEFINED, UNRESOLVED }

    record Path(List<Token> segments) implements Expression {
        Path { segments = List.copyOf(segments); }
        Token first() { return segments.getFirst(); }
        Token last() { return segments.getLast(); }
        String name() { return String.join(".", segments.stream().map(Token::text).toList()); }
    }

    record Literal(Token token, LiteralKind kind, Object value) implements Expression {}
    record Test(Path path, State state, boolean negated) implements Expression {}
    record Not(Expression operand) implements Expression {}
    record Comparison(Expression left, Token operator, Expression right) implements Expression {}
    record Junction(Expression left, String operator, Expression right) implements Expression {}
    record Exists(Path path, Token alias, Expression condition) implements Expression {}
    record Intersects(Path path, List<Double> box) implements Expression {}
    record Selection(Path path, Token alias, boolean count, boolean geometry) {}
    record Expansion(Path path, Token alias, Token position) {}
    record Order(Path path, boolean descending) {}
    record Query(List<Selection> selections, Path source, Token alias, Expansion expansion, Expression filter,
                 List<Order> order, int limit) {}

    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final int MAX_TOKENS = 1024;
    private static final int MAX_DEPTH = 16;
    private final List<Token> tokens;
    private int index;
    private int depth;

    private IqlSyntax(String input) {
        tokens = scan(input, true);
    }

    static Query parse(String input) {
        return new IqlSyntax(input).query();
    }

    static List<Token> scan(String input, boolean strict) {
        var tokens = new ArrayList<Token>();
        var matcher = NUMBER.matcher(input);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            int start = i;
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (input.startsWith("--", i)) {
                int newline = input.indexOf('\n', i + 2);
                i = newline < 0 ? input.length() : newline + 1;
                continue;
            }
            if (input.startsWith("/*", i)) {
                int end = input.indexOf("*/", i + 2);
                if (end < 0 && strict) throw new Iql.QueryException("Unterminated comment", start, input.length());
                i = end < 0 ? input.length() : end + 2;
                continue;
            }
            if (tokens.size() == MAX_TOKENS) {
                if (strict) throw new Iql.QueryException("Query contains too many tokens", start, input.length());
                break;
            }
            if (c == '\'') {
                var text = new StringBuilder();
                boolean closed = false;
                i++;
                while (i < input.length()) {
                    char value = input.charAt(i++);
                    if (value == '\'') {
                        if (i == input.length() || input.charAt(i) != '\'') {
                            closed = true;
                            break;
                        }
                        i++;
                    }
                    text.append(value);
                }
                if (!closed && strict) throw new Iql.QueryException("Unterminated string", start, i);
                tokens.add(new Token(Kind.STRING, text.toString(), start, i));
            } else if (Character.isLetter(c) || c == '_') {
                do { i++; } while (i < input.length()
                        && (Character.isLetterOrDigit(input.charAt(i)) || input.charAt(i) == '_'));
                tokens.add(new Token(Kind.WORD, input.substring(start, i), start, i));
            } else if ((Character.isDigit(c) || "+-.".indexOf(c) >= 0)
                    && matcher.region(i, input.length()).lookingAt()) {
                i = matcher.end();
                tokens.add(new Token(Kind.NUMBER, input.substring(start, i), start, i));
            } else {
                i++;
                if (i < input.length() && ("<>=!".indexOf(c) >= 0 && input.charAt(i) == '='
                        || c == '<' && input.charAt(i) == '>')) i++;
                tokens.add(new Token(Kind.SYMBOL, input.substring(start, i), start, i));
            }
        }
        tokens.add(new Token(Kind.END, "", input.length(), input.length()));
        return List.copyOf(tokens);
    }

    private Query query() {
        require("SELECT");
        var selections = new ArrayList<Selection>();
        if (peek().is("COUNT") && tokens.get(index + 1).is("(")) {
            index++;
            require("(");
            require("*");
            require(")");
            selections.add(new Selection(null, take("AS") ? word() : null, true, false));
        } else if (!take("*")) {
            do {
                boolean geometry = peek().is("GEOMETRY") && tokens.get(index + 1).is("(");
                if (geometry) index += 2;
                Path path = path();
                if (geometry) require(")");
                selections.add(new Selection(path, take("AS") ? word() : null, false, geometry));
            } while (take(","));
        }
        require("FROM");
        Path source = path();
        Token alias = take("AS") || hasImplicitSourceAlias() ? word() : null;
        Expansion expansion = take("EXPAND") ? expansion() : null;
        Expression filter = take("WHERE") ? condition() : null;
        var ordering = new ArrayList<Order>();
        if (take("ORDER")) {
            require("BY");
            do {
                Path path = path();
                boolean descending = take("DESC");
                if (!descending) take("ASC");
                ordering.add(new Order(path, descending));
            } while (take(","));
        }
        int limit = Integer.MAX_VALUE;
        if (take("LIMIT")) {
            Token token = peek();
            try {
                limit = Integer.parseInt(token.text());
                if (token.kind() != Kind.NUMBER || limit < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                throw token.error("Expected a non-negative whole number for LIMIT");
            }
            index++;
        }
        take(";");
        if (peek().kind() != Kind.END) throw peek().error("Expected the end of the query");
        return new Query(List.copyOf(selections), source, alias, expansion, filter, List.copyOf(ordering), limit);
    }

    private boolean hasImplicitSourceAlias() {
        Token current = peek();
        if (current.kind() != Kind.WORD || clause(current)) return false;
        if (!current.is("EXPAND")) return true;
        Token next = tokens.get(index + 1);
        return next.kind() != Kind.WORD || clause(next);
    }

    private Expansion expansion() {
        Path path = path();
        require("AS");
        Token alias = word();
        return new Expansion(path, alias, take("AT") ? word() : null);
    }

    static boolean clause(Token token) {
        return token.is("WHERE") || token.is("ORDER") || token.is("LIMIT");
    }

    private Expression condition() {
        Expression value = conjunction();
        while (take("OR")) value = new Junction(value, "OR", conjunction());
        return value;
    }

    private Expression conjunction() {
        Expression value = predicate();
        while (take("AND")) value = new Junction(value, "AND", predicate());
        return value;
    }

    private Expression predicate() {
        if (++depth > MAX_DEPTH) throw peek().error("Query is nested too deeply");
        try {
            if (take("NOT")) return new Not(predicate());
            if (take("(")) {
                Expression result = condition();
                require(")");
                return result;
            }
            if (take("EXISTS")) return exists();
            if (take("INTERSECTS")) return intersects();
            Expression left = operand();
            if (take("IS")) return stateTest(left);
            Token operator = peek();
            if (operator.kind() == Kind.SYMBOL && List.of("=", "!=", "<>", "<", "<=", ">", ">=").contains(operator.text())) {
                index++;
                return new Comparison(left, operator, operand());
            }
            return left;
        } finally {
            depth--;
        }
    }

    private Test stateTest(Expression left) {
        if (!(left instanceof Path path)) throw peek().error("IS requires an attribute or role path");
        boolean negate = take("NOT");
        if (take("MISSING")) return new Test(path, State.MISSING, negate);
        if (take("UNDEFINED")) return new Test(path, State.UNDEFINED, negate);
        if (take("UNRESOLVED")) return new Test(path, State.UNRESOLVED, negate);
        throw peek().error("Expected MISSING, UNDEFINED or UNRESOLVED");
    }

    private Exists exists() {
        require("(");
        Path path = path();
        require("AS");
        Token alias = word();
        require("WHERE");
        Expression filter = condition();
        require(")");
        return new Exists(path, alias, filter);
    }

    private Intersects intersects() {
        require("(");
        Path path = path();
        require(",");
        require("BOX");
        require("(");
        var coordinates = new ArrayList<Double>(4);
        Token start = peek();
        for (int i = 0; i < 4; i++) {
            if (i > 0) require(",");
            Token token = peek();
            double coordinate = number(token).doubleValue();
            if (!Double.isFinite(coordinate)) throw token.error("BOX coordinates must be finite");
            coordinates.add(coordinate);
            index++;
        }
        if (coordinates.get(0) >= coordinates.get(2) || coordinates.get(1) >= coordinates.get(3)) {
            throw start.error("BOX minimums must be smaller than its maximums");
        }
        require(")");
        require(")");
        return new Intersects(path, List.copyOf(coordinates));
    }

    private Expression operand() {
        Token token = peek();
        if (token.kind() == Kind.STRING) {
            index++;
            return new Literal(token, LiteralKind.TEXT, token.text());
        }
        if (token.kind() == Kind.NUMBER) {
            index++;
            return new Literal(token, LiteralKind.NUMBER, number(token));
        }
        if (take("TRUE")) return new Literal(token, LiteralKind.BOOLEAN, true);
        if (take("FALSE")) return new Literal(token, LiteralKind.BOOLEAN, false);
        if (take("#")) return new Literal(token, LiteralKind.ENUMERATION, path().name());
        return path();
    }

    private static BigDecimal number(Token token) {
        if (token.kind() != Kind.NUMBER) throw token.error("Expected a numeric literal");
        try {
            return new BigDecimal(token.text());
        } catch (NumberFormatException ex) {
            throw token.error("Numeric literal cannot be represented");
        }
    }

    private Path path() {
        var segments = new ArrayList<Token>();
        segments.add(word());
        while (take(".")) {
            if (segments.size() == MAX_DEPTH) throw peek().error("Path is too long");
            segments.add(word());
        }
        return new Path(segments);
    }

    private Token word() {
        Token token = peek();
        if (token.kind() != Kind.WORD) throw token.error("Expected an INTERLIS name");
        index++;
        return token;
    }

    private boolean take(String text) {
        if (!peek().is(text)) return false;
        index++;
        return true;
    }

    private void require(String text) {
        if (!take(text)) throw peek().error("Expected " + text);
    }

    private Token peek() { return tokens.get(index); }
}
