package guru.interlis.cli;

/**
 * Pure text diagnostic presentation limits, in visible terminal columns.
 * Verbose presentation includes frames, related locations and hints; compact
 * presentation includes only the diagnostic header. Frames include at most three
 * source lines; published options are immutable and null-free.
 * Limits include the frame gutter. No host or terminal state is consulted.
 */
public record RenderOptions(boolean ansiEnabled, int contextLinesBefore,
                            int contextLinesAfter, int maxVisibleColumns, boolean verbose) {
    /** Retains the detailed presentation of existing library callers. */
    public RenderOptions(boolean ansiEnabled, int contextLinesBefore,
                         int contextLinesAfter, int maxVisibleColumns) {
        this(ansiEnabled, contextLinesBefore, contextLinesAfter, maxVisibleColumns, true);
    }

    /** Rejects negative context, more than three total lines, or widths outside 24..160. */
    public RenderOptions {
        if (contextLinesBefore < 0 || contextLinesAfter < 0
                || contextLinesBefore > 2 || contextLinesAfter > 2
                || contextLinesBefore + contextLinesAfter > 2
                || maxVisibleColumns < 24 || maxVisibleColumns > 160)
            throw new IllegalArgumentException("invalid diagnostic render limits");
    }

    /** Detailed presentation, one line before/after, 160 columns, explicit color choice. */
    public static RenderOptions defaults(boolean ansiEnabled) {
        return new RenderOptions(ansiEnabled, 1, 1, 160);
    }
}
