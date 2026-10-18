package spike;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.*;
import org.teavm.jso.JSExport;
import spike.grammar.InterlisLexer;
import spike.grammar.InterlisParser;

/** Feasibility probe, not a semantically complete INTERLIS compiler. */
public final class CompilerSpike {
    sealed interface Node permits ModelNode, MissingNode {}
    record ModelNode(String name, int topics) implements Node {}
    record MissingNode(String reason) implements Node {}
    record Diagnostic(int line, int codePointColumn, String message) {}
    record Snapshot(List<Node> models, List<Diagnostic> diagnostics, boolean eof) {}

    static String nameOf(Node node) {
        return switch (node) {
            case ModelNode(var name, var topics) -> name + ":" + topics;
            case MissingNode(var reason) -> "missing:" + reason;
        };
    }

    static Snapshot parse(String source) {
        var diagnostics = new ArrayList<Diagnostic>();
        var errors = new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?, ?> recognizer, Object token, int line,
                int charPositionInLine, String message, RecognitionException cause) {
                diagnostics.add(new Diagnostic(line, charPositionInLine, message));
            }
        };
        var lexer = new InterlisLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners(); lexer.addErrorListener(errors);
        var tokens = new CommonTokenStream(lexer);
        var parser = new InterlisParser(tokens);
        parser.removeErrorListeners(); parser.addErrorListener(errors);
        var tree = parser.interlis2def();
        boolean eof = tokens.LA(1) == Token.EOF;
        if (!eof) diagnostics.add(new Diagnostic(tokens.LT(1).getLine(),
            tokens.LT(1).getCharPositionInLine(), "Unconsumed trailing input"));
        var models = new ArrayList<Node>();
        for (var model : tree.modeldef()) {
            models.add(model.Name().isEmpty() ? new MissingNode("model name")
                : new ModelNode(model.Name(0).getText(), model.topicDef().size()));
        }
        return new Snapshot(List.copyOf(models), List.copyOf(diagnostics), eof);
    }

    /** Simple adapter boundary: text in, stable JSON out; no filesystem or network. */
    @JSExport public static String compile(String source) {
        var snapshot = parse(source);
        var names = new StringBuilder();
        for (var node : snapshot.models()) {
            if (!names.isEmpty()) names.append(',');
            names.append('"').append(nameOf(node)).append('"');
        }
        return "{\"models\":[" + names + "],\"errors\":" + snapshot.diagnostics().size()
            + ",\"eof\":" + snapshot.eof() + "}";
    }

    static final String MODEL = """
        INTERLIS 2.3;
        !! Grüezi, 😀. Unicode comments and literals.
        MODEL Sample AT "https://interlis.guru/Grüezi😀" VERSION "2026-10-18" =
          DOMAIN
            Count = 0..999;
            Decimal = -12.300..45.670;
          TOPIC Data =
            CLASS Item =
              label : TEXT*100;
              count : Count;
            END Item;
          END Data;
        END Sample.
        """;

    public static void main(String[] args) {
        var common23 = parse(MODEL);
        check(common23.diagnostics().isEmpty(), "2.3 syntax", common23.diagnostics());
        check(common23.eof(), "2.3 EOF", common23);
        String source24 = MODEL.replace("INTERLIS 2.3", "INTERLIS 2.4")
            .replace("Count = 0..999;", "Count = 0..999;\nPoint = MULTICOORD 0.000..1000.000, 0.000..1000.000;");
        var extended24 = parse(source24);
        check(extended24.diagnostics().isEmpty(), "2.4 MULTICOORD syntax", extended24.diagnostics());
        var broken = parse(MODEL.replace("label : TEXT*100;", "label : TEXT*100"));
        check(!broken.diagnostics().isEmpty(), "recovery diagnostic", broken);
        check(broken.models().size() == 1 && broken.eof(), "recovery snapshot", broken);
        check(!parse(MODEL + "GARBAGE").diagnostics().isEmpty(), "trailing input", "EOF guard");

        Node record = new ModelNode("Sample", 1);
        check(record.equals(new ModelNode("Sample", 1)), "record equals", record);
        check(record.hashCode() == new ModelNode("Sample", 1).hashCode(), "record hashCode", record);
        check(record.toString().contains("Sample"), "record toString", record);
        check(nameOf(record).equals("Sample:1"), "record pattern switch", record);
        check(nameOf(new MissingNode("x")).equals("missing:x"), "sealed exhaustive switch", record);

        var decimal = new BigDecimal("9007199254740993.0001").add(new BigDecimal("0.0009"));
        check(decimal.toPlainString().equals("9007199254740993.0010"), "decimal precision", decimal);
        var integer = new BigInteger("9007199254740993").multiply(new BigInteger("7"));
        check(integer.toString().equals("63050394783186951"), "BigInteger precision", integer);
        check(Pattern.compile("\\p{L}+").matcher("Grüezi").matches(), "Unicode regex", "Grüezi");

        var lexer = new InterlisLexer(CharStreams.fromString(MODEL));
        var strings = lexer.getAllTokens().stream().filter(t -> t.getType() == InterlisLexer.STRING).toList();
        var version = strings.get(1);
        int utf16Offset = MODEL.offsetByCodePoints(0, version.getStartIndex());
        check(MODEL.substring(utf16Offset).startsWith("\"2026-10-18\""), "code point to UTF-16 offset", utf16Offset);
        check(utf16Offset > version.getStartIndex(), "supplementary Unicode conversion", utf16Offset);

        System.out.println("CHECKS PASS: parser23 parser24 recovery eof records sealed record-pattern-switch BigDecimal BigInteger Unicode-regex Unicode-offsets");
        System.out.println("23 " + compile(MODEL));
        System.out.println("24 " + compile(source24));
        System.out.println("broken " + compile(MODEL.replace("label : TEXT*100;", "label : TEXT*100")));
        System.out.println("decimal " + decimal.toPlainString());
    }

    private static void check(boolean condition, String check, Object observed) {
        if (!condition) throw new AssertionError(check + ": " + observed);
    }
}
