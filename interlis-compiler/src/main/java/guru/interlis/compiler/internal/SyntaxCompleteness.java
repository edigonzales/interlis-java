package guru.interlis.compiler.internal;

import guru.interlis.syntax.DetectionState;
import guru.interlis.syntax.SyntaxSnapshot;

/** Structural syntax checks shared by root selection and the compilation gate. */
final class SyntaxCompleteness {
    private SyntaxCompleteness() {
    }

    static boolean isComplete(SyntaxSnapshot snapshot) {
        var coverage = snapshot.coverage();
        return snapshot.language().state() == DetectionState.DETECTED
                && coverage.lexicallyComplete()
                && coverage.parseComplete()
                && coverage.consumedEntireInput();
    }
}
