package guru.interlis.workspace;

/** The workspace was already closed. */
public final class WorkspaceClosedException extends IllegalStateException {
    /** Creates the exception. */
    public WorkspaceClosedException() {
        super("workspace is closed");
    }
}
