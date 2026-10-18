package guru.interlis.compiler.internal;

/** Internal resource-limit signal; mapped to RESOURCE_LIMIT by the engine. */
public final class AnalysisBudgetExceededException extends RuntimeException {
    private final String resource;
    private final long limit;

    public AnalysisBudgetExceededException(String resource, long limit) {
        super("analysis budget exceeded: " + resource + " > " + limit);
        this.resource = resource;
        this.limit = limit;
    }

    public String resource() {
        return resource;
    }

    public long limit() {
        return limit;
    }
}
