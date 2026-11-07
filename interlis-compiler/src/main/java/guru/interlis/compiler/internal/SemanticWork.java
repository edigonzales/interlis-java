package guru.interlis.compiler.internal;

import guru.interlis.execution.CancellationToken;

import java.util.Objects;

/** A compilation run's semantic work budget, including work already charged by overlays. */
final class SemanticWork {
    private final CancellationToken cancellation;
    private final long maximumWork;
    private long work;

    SemanticWork(CancellationToken cancellation, long maximumWork, long overlayWork) {
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.maximumWork = maximumWork;
        this.work = overlayWork;
    }

    void checkpoint() {
        cancellation.throwIfCancellationRequested();
        if (++work > maximumWork) {
            throw new AnalysisBudgetExceededException("maxSemanticWorkUnits", maximumWork);
        }
    }
}
