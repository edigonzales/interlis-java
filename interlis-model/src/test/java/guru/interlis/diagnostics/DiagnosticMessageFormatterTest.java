package guru.interlis.diagnostics;

import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticMessageFormatterTest {
    private final DiagnosticMessageFormatter formatter = new DiagnosticMessageFormatter();
    private static Diagnostic diagnostic(DiagnosticDefinition definition, List<DiagnosticArgument> arguments) {
        return new Diagnostic(new DiagnosticId("formatter"), definition.code(), definition.defaultSeverity(),
                definition.allowedPhases().iterator().next(), definition.messageKey(), arguments, Optional.empty(),
                List.of(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(), List.of());
    }

    @Test void everyActualCatalogTemplateUsesEveryTypedParameterInBothLanguages() {
        for (var definition : DiagnosticCatalog.initial().definitions()) {
            var arguments = definition.arguments().stream().map(a -> switch (a.kind()) {
                case TEXT -> (DiagnosticArgument) new DiagnosticArgument.TextArgument(a.name(), "value_" + a.name());
                case IDENTIFIER -> new DiagnosticArgument.IdentifierArgument(a.name(), "value_" + a.name());
                case TOKEN -> new DiagnosticArgument.TokenArgument(a.name(), "value_" + a.name());
                case INTEGER -> new DiagnosticArgument.IntegerArgument(a.name(), new BigInteger("123456789012345678901234567890"));
                case DECIMAL -> new DiagnosticArgument.DecimalArgument(a.name(), new BigDecimal("1234567890.12345678901234567890"), Optional.empty());
                case TYPE_NAME -> new DiagnosticArgument.TypeNameArgument(a.name(), "value_" + a.name());
                case IDENTIFIER_LIST -> new DiagnosticArgument.IdentifierListArgument(a.name(), List.of("value_" + a.name(), "🦊"));
            }).toList();
            for (var language : DiagnosticLanguage.values()) {
                var text = formatter.format(diagnostic(definition, arguments), language).text();
                assertFalse(text.contains("?"), definition.code() + text);
                assertFalse(text.contains("diagnostic."), text); assertFalse(text.contains("{"), text);
                assertFalse(text.contains("No further explanation") || text.contains("keine weiteren Angaben"), definition.code().toString());
                for (var a : definition.arguments()) assertTrue(text.contains(a.kind() == ArgumentKind.INTEGER
                        ? "123456789012345678901234567890" : "value_" + a.name()), definition.code() + " " + a.name() + " " + text);
            }
        }
    }

    @Test void missingExternalArgumentsAndUnknownKeysCannotCrashOrDumpRecords() {
        for (var definition : DiagnosticCatalog.initial().definitions())
            for (var language : DiagnosticLanguage.values()) assertFalse(formatter.format(diagnostic(definition, List.of()), language).text().isBlank());
        var unknown = new Diagnostic(new DiagnosticId("external"), new DiagnosticCode("ILI-EXT-0001"), Severity.ERROR,
                Phase.INFRASTRUCTURE, "external.secret.key", List.of(), Optional.empty(), List.of(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(), List.of());
        assertFalse(formatter.format(unknown, DiagnosticLanguage.EN).text().contains("secret"));
    }

    @Test void terminalArgumentsAreEscapedOnceAndExactNumbersAreNotRounded() {
        var unresolved = DiagnosticCatalog.initial().require(new DiagnosticCode("ILI-SEM-0001"));
        String raw = "🦊\n\r\t\u001b[31m\u202e{name}";
        var text = formatter.format(diagnostic(unresolved, List.of(new DiagnosticArgument.IdentifierArgument("name", raw))), DiagnosticLanguage.EN).text();
        assertTrue(text.contains("🦊\\u000a\\u000d\\u0009\\u001b[31m\\u202e{name}"), text);
        assertTrue(text.chars().noneMatch(Character::isISOControl));
        var limit = DiagnosticCatalog.initial().require(new DiagnosticCode("ILI-INF-0002"));
        var value = new BigInteger("1234567890123456789012345678901234567890");
        assertTrue(formatter.format(diagnostic(limit, List.of(new DiagnosticArgument.TextArgument("resource", "memory"),
                new DiagnosticArgument.IntegerArgument("limit", value))), DiagnosticLanguage.DE).text().contains(value.toString()));
        // External typed data may carry a precise decimal even where the current catalog expects text.
        var decimal = new BigDecimal("1234567890.00000000000000000000000000012300");
        assertTrue(formatter.format(diagnostic(unresolved, List.of(new DiagnosticArgument.DecimalArgument("name", decimal, Optional.empty()))), DiagnosticLanguage.EN).text().contains(decimal.toString()));
    }

    @Test void namespaceConflictExplainsTheCauseAndHintWithoutChangingCanonicalArguments() {
        var definition = DiagnosticCatalog.initial().require(new DiagnosticCode("ILI-SEM-0010"));
        var d = diagnostic(definition, List.of(new DiagnosticArgument.IdentifierArgument("element", "Office"),
                new DiagnosticArgument.TextArgument("rule", "local type names must not shadow an enclosing type name/SCOPE_OUTER_TYPE_NAME")));
        assertTrue(formatter.format(d, DiagnosticLanguage.DE).text().contains("kollidiert"));
        assertTrue(formatter.format(d, DiagnosticLanguage.EN).text().contains("conflicts"));
        assertTrue(formatter.format(d, DiagnosticLanguage.DE).hint().isPresent());
        assertTrue(d.arguments().toString().contains("SCOPE_OUTER_TYPE_NAME"));
    }
}
