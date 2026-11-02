package guru.interlis.compiler;

import static org.junit.jupiter.api.Assertions.*;

import guru.interlis.compiler.query.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.NamePrefix;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Pattern;

class EditorTemplateContractTest {
    private static String materialize(String template) {
        var values = new HashMap<String, String>();
        var pattern = Pattern.compile("\\$\\{(\\d+)(?::([^}]*))?\\}|\\$(\\d+)");
        var matcher = pattern.matcher(template);
        var out = new StringBuilder();
        while (matcher.find()) {
            String id = matcher.group(1) == null ? matcher.group(3) : matcher.group(1),
                    value = matcher.group(2);
            if (value != null) values.put(id, value);
            matcher.appendReplacement(
                    out, java.util.regex.Matcher.quoteReplacement(values.getOrDefault(id, "")));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    @Test
    void allAdvertisedTemplatesMaterializeAsValidInterlisInBothVersions() {
        for (String version : List.of("2.3", "2.4"))
            for (String kind :
                    List.of(
                            "MODEL",
                            "TOPIC",
                            "VIEW TOPIC",
                            "CLASS",
                            "STRUCTURE",
                            "ASSOCIATION",
                            "VIEW",
                            "GRAPHIC",
                            "DOMAIN",
                            "UNIT",
                            "Role")) {
                String prefix = "INTERLIS " + version + ";\nMODEL M AT \"x\" VERSION \"1\"=\n",
                        suffix = "END M.",
                        partial = kind.substring(0, Math.min(2, kind.length()));
                if (kind.equals("MODEL")) {
                    prefix = "INTERLIS " + version + ";\n";
                    suffix = "";
                } else if (kind.equals("VIEW TOPIC")) prefix += "TOPIC BaseTopic=END BaseTopic;\n";
                else if (Set.of("ASSOCIATION", "VIEW", "GRAPHIC", "Role").contains(kind)) {
                    prefix +=
                            "TOPIC T=OID AS INTERLIS.UUIDOID;CLASS FirstClass=END FirstClass;CLASS"
                                + " SecondClass=END SecondClass;CLASS BaseClass=END BaseClass;CLASS"
                                + " Target=END Target;\n";
                    suffix = "END T;END M.";
                    if (kind.equals("Role")) {
                        prefix += "ASSOCIATION A=other--{0..*}Target;\n";
                        suffix = "END A;" + suffix;
                    }
                }
                String original = prefix + partial + "\n" + suffix;
                var source = EditorAssistanceTest.source(original);
                var analysis = EditorAssistanceTest.analyze(source);
                int at = prefix.length() + partial.length();
                var result =
                        new EditorAssistanceService()
                                .complete(
                                        analysis,
                                        new CompletionQuery(
                                                source.range(at, at), new NamePrefix(partial), 100),
                                        CancellationToken.none());
                var candidate =
                        result.candidates().stream()
                                .filter(c -> c.label().equals(kind + " block"))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new AssertionError(
                                                        kind
                                                                + " "
                                                                + result
                                                                + "\n"
                                                                + original
                                                                + "\n"
                                                                + nodes(
                                                                        analysis.syntaxSnapshot(
                                                                                        source.id())
                                                                                .orElseThrow()
                                                                                .root(),
                                                                        at)));
                String complete =
                        original.substring(0, result.replacementRange().startOffset())
                                + materialize(candidate.insertText())
                                + "\n"
                                + original.substring(result.replacementRange().endOffset());
                var after = EditorAssistanceTest.analyze(EditorAssistanceTest.source(complete));
                assertTrue(
                        after.diagnostics().stream()
                                .noneMatch(
                                        d ->
                                                d.severity()
                                                        == guru.interlis.diagnostics.Severity
                                                                .ERROR),
                        () -> version + " " + kind + "\n" + complete + "\n" + after.diagnostics());
            }
    }

    private static String nodes(guru.interlis.syntax.SyntaxNode n, int at) {
        return (n.range().startOffset() <= at && at <= n.range().endOffset()
                        ? n.kind()
                                + " "
                                + n.range().startOffset()
                                + ".."
                                + n.range().endOffset()
                                + "\n"
                        : "")
                + n.children().stream()
                        .map(c -> nodes(c, at))
                        .collect(java.util.stream.Collectors.joining());
    }

    @Test
    void metaAttributeNamesAndValuesAreBoundToTheFollowingRealDeclaration() {
        String head = "INTERLIS 2.4;MODEL M AT \"x\" VERSION \"1\"=\n";
        var structure =
                EditorCompletionTest.labels(
                        EditorCompletionTest.complete(
                                head + "!!@ ili2db.│\nSTRUCTURE S=END S;END M."));
        assertTrue(structure.contains("ili2db.mapping"));
        assertFalse(structure.contains("ili2db.oid"));
        var cls =
                EditorCompletionTest.labels(
                        EditorCompletionTest.complete(head + "!!@ ili2db.│\nCLASS C=END C;END M."));
        assertTrue(cls.contains("ili2db.oid"));
        assertFalse(cls.contains("ili2db.mapping"));
        var values =
                EditorCompletionTest.labels(
                        EditorCompletionTest.complete(
                                head + "CLASS C=\n!!@ ilivalid.type=│\nvalue:TEXT;END C;END M."));
        assertEquals(Set.of("on", "warning", "off"), values);
        var role =
                EditorCompletionTest.labels(
                        EditorCompletionTest.complete(
                                head
                                        + "TOPIC T=CLASS C=END C;ASSOCIATION A=\n"
                                        + "!!@ │\n"
                                        + "fromRole--C;toRole--C;END A;END T;END M."));
        assertTrue(role.contains("ilivalid.target"));
        assertFalse(role.contains("ili2db.dispName"));
    }
}
