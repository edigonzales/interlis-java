package guru.interlis.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticCatalogTest {
    @Test
    void initialCatalogIsStableAndUnique() {
        DiagnosticCatalog catalog = DiagnosticCatalog.initial();
        assertFalse(catalog.definitions().isEmpty());
        assertEquals(catalog.definitions().size(),
                catalog.definitions().stream().map(DiagnosticDefinition::code).distinct().count());
        assertTrue(catalog.find(new DiagnosticCode("ILI-SYN-0001")).isPresent());
        assertTrue(catalog.find(new DiagnosticCode("ILI-SYN-9999")).isEmpty());
    }

    @Test
    void duplicateCodeIsRejected() {
        DiagnosticDefinition definition = new DiagnosticDefinition(
                new DiagnosticCode("ILI-TEST-0001"), "test", Severity.ERROR,
                Set.of(Phase.LEXING), "test", List.of(),
                DiagnosticDefinition.PositionRule.OPTIONAL, "test");
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosticCatalog(List.of(definition, definition)));
    }

    @Test
    void validationRejectsWrongPhaseAndUnknownArgument() {
        DiagnosticCatalog catalog = DiagnosticCatalog.initial();
        DiagnosticDraft wrongPhase = DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0001"),
                        Severity.ERROR, Phase.BINDING, "diagnostic.syntax.lexical")
                .argument(new DiagnosticArgument.TokenArgument("token", "x"));
        assertThrows(IllegalStateException.class, () -> catalog.validate(wrongPhase));
        DiagnosticDraft unknownArgument = DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0001"),
                        Severity.ERROR, Phase.LEXING, "diagnostic.syntax.lexical")
                .argument(new DiagnosticArgument.TextArgument("unknown", "x"));
        assertThrows(IllegalStateException.class, () -> catalog.validate(unknownArgument));
    }
}
