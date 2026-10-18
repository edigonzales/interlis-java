package guru.interlis.compiler.query;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.query.QueryResult;
import guru.interlis.source.SourceRange;

/** Stateless IDE queries over an explicit snapshot. */
public interface LanguageQueries {
    QueryResult<SymbolRef> symbolAt(AnalysisSnapshot snapshot, SourceRange cursor,
                                    CancellationToken token);

    QueryResult<DefinitionTarget> definition(AnalysisSnapshot snapshot, SourceRange cursor,
                                             CancellationToken token);

    QueryResult<HoverInfo> hover(AnalysisSnapshot snapshot, SourceRange cursor,
                                 CancellationToken token);

    CompletionResult complete(AnalysisSnapshot snapshot, CompletionQuery query,
                              CancellationToken token);

    ReferenceResult references(AnalysisSnapshot snapshot, SymbolRef symbol,
                               ReferenceOptions options, CancellationToken token);

    RenamePreparation prepareRename(AnalysisSnapshot snapshot, SourceRange cursor,
                                    CancellationToken token);

    RenamePlan planRename(AnalysisSnapshot snapshot, RenameRequest request,
                          CancellationToken token);

    /**
     * Returns the {@code create} value.
     *
     * @return the value
     */
    static LanguageQueries create() {
        /**
         * Returns the {@code LanguageQueryService} value.
         *
         * @return the value
         */
        return new LanguageQueryService();
    }
}
