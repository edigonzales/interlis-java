package guru.interlis.compiler.internal;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SemanticWorkBudgetTest {
    @Test
    void remainingBudgetIncludesOverlayWorkAndAllowsTheLastUnit() {
        var work = new SemanticWork(CancellationToken.none(), 4, 2);
        work.checkpoint();
        work.checkpoint();
        var exceeded = assertThrows(AnalysisBudgetExceededException.class, work::checkpoint);
        assertEquals("maxSemanticWorkUnits", exceeded.resource());
        assertEquals(4, exceeded.limit());
    }

    @Test
    void cancellationIsCheckedBeforeChargingTheNextUnit() {
        var cancelled = new AtomicBoolean(true);
        var work = new SemanticWork(cancelled::get, 2, 1);
        var aborted = assertThrows(OperationCancelledException.class, work::checkpoint);
        assertEquals(CancellationReason.USER_REQUESTED, aborted.reason());
        cancelled.set(false);
        work.checkpoint();
        assertThrows(AnalysisBudgetExceededException.class, work::checkpoint);

        var exhausted = new SemanticWork(CancellationToken.requested(CancellationReason.USER_REQUESTED), 1, 1);
        assertThrows(OperationCancelledException.class, exhausted::checkpoint);
    }
}
