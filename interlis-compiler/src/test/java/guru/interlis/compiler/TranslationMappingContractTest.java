package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TranslationMappingContractTest {
    private static CompilationOutcome compile(String text, String model) {
        SourceText source = new SourceText(new SourceId("memory:/translation-mapping.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName(model,
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }

    @Test
    void consistentlyRenamedTranslationCompiles() {
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile("""
                INTERLIS 2.3;
                MODEL Source AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A =
                      a : TEXT*10;
                      MANDATORY CONSTRAINT a != "";
                    END A;
                  END T;
                END Source.
                MODEL Target (de) AT "https://example.interlis.ch" VERSION "1.0"
                TRANSLATION OF Source [ "1.0" ] =
                  TOPIC U =
                    CLASS B =
                      b : TEXT*10;
                      MANDATORY CONSTRAINT b != "";
                    END B;
                  END U;
                END Target.
                """, "Target"));
    }

    @Test
    void inconsistentConstraintReferenceIsRejected() {
        assertInstanceOf(CompilationOutcome.Rejected.class, compile("""
                INTERLIS 2.3;
                MODEL Source AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A =
                      a1 : TEXT*10;
                      a2 : TEXT*10;
                      MANDATORY CONSTRAINT a1 != "";
                    END A;
                  END T;
                END Source.
                MODEL Target (de) AT "https://example.interlis.ch" VERSION "1.0"
                TRANSLATION OF Source [ "1.0" ] =
                  TOPIC U =
                    CLASS B =
                      b1 : TEXT*10;
                      b2 : TEXT*10;
                      MANDATORY CONSTRAINT b2 != "";
                    END B;
                  END U;
                END Target.
                """, "Target"));
    }

    @Test
    void inconsistentUnitAliasIsRejected() {
        assertInstanceOf(CompilationOutcome.Rejected.class, compile("""
                INTERLIS 2.3;
                MODEL Source AT "https://example.interlis.ch" VERSION "1.0" =
                  UNIT
                    A1 (ABSTRACT);
                    A2 [A2] EXTENDS A1;
                    A5 = FUNCTION [A2];
                  END;
                END Source.
                MODEL Target (de) AT "https://example.interlis.ch" VERSION "1.0"
                TRANSLATION OF Source [ "1.0" ] =
                  UNIT
                    B1 (ABSTRACT);
                    B2 [B2] EXTENDS B1;
                    B5 = FUNCTION [B1];
                  END;
                END Target.
                """, "Target"));
    }
}
