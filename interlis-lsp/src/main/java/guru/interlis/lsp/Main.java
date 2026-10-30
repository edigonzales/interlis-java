package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;
import org.eclipse.lsp4j.launch.LSPLauncher;

/** LSP stdio process entry; stdout carries protocol messages only. */
public final class Main {
    private Main() {
    }

    /**
     * Executes {@code main}.
     *
     * @param args the {@code args} argument
     */
    public static void main(String[] args) {
        InterlisLanguageServer server = new InterlisLanguageServer(
                Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults()));
        server.processExit(System::exit);
        var launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        try {
            launcher.startListening().get();
            server.exit();
            System.exit(0);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            System.exit(1);
        } catch (java.util.concurrent.ExecutionException failure) {
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
