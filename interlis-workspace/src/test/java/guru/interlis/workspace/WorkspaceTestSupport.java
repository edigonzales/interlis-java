package guru.interlis.workspace;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;

final class WorkspaceTestSupport {
    private WorkspaceTestSupport() {
    }

    static Workspace workspace() {
        return Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults());
    }

    static SourceText add(Workspace workspace, String id, String text) {
        SourceId sourceId = new SourceId(id);
        workspace.update(new SourceUpdate(sourceId, new SourceExpectation.Absent(),
                new SourceChange.Replace(text)));
        return workspace.source(sourceId).orElseThrow();
    }

    static void replace(Workspace workspace, SourceId id, String text) {
        SourceText current = workspace.source(id).orElseThrow();
        workspace.update(new SourceUpdate(id, new SourceExpectation.At(current.revision()),
                new SourceChange.Replace(text)));
    }
}
