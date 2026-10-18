package guru.interlis.workspace.resolution;

import guru.interlis.execution.CancellationToken;

/** Pure model resolution over an already frozen inventory. */
public interface ModelResolutionService {
    ResolutionOutcome prepare(ResolutionRequest request, SourceProvider provider,
                              CancellationToken cancellation);
}
