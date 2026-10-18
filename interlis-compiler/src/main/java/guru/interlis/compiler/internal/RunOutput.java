package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisSnapshot;

/** Internal analysis output plus closure/gate inputs. */
record RunOutput(AnalysisSnapshot analysis, Closure closure, RootSelection roots,
                 GraphBuilder.GraphResult graphResult) {
}
