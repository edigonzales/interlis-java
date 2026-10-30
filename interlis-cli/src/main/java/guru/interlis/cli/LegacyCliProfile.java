package guru.interlis.cli;

import java.util.*;

/** Pinned compatibility input for the checking subset of ili2c 5.6.9. */
public record LegacyCliProfile(String id, String referenceVersion, String referenceSha256,
                               CommandSpec commandSpec, Map<String, String> unsupportedOptions) {
    public LegacyCliProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(referenceVersion, "referenceVersion");
        Objects.requireNonNull(referenceSha256, "referenceSha256");
        Objects.requireNonNull(commandSpec, "commandSpec");
        if (id.isBlank() || referenceVersion.isBlank() || !referenceSha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("invalid legacy profile identity or pin");
        var copy = new LinkedHashMap<String, String>();
        Objects.requireNonNull(unsupportedOptions, "unsupportedOptions").forEach((option, reason) ->
                copy.put(Objects.requireNonNull(option), Objects.requireNonNull(reason)));
        unsupportedOptions = Collections.unmodifiableMap(copy);
    }

    /** Ordered checking defaults; placeholders are expanded only by the host planner. */
    public String defaultModelDirectories() { return "%ILI_DIR;http://models.interlis.ch/;%JAR_DIR"; }

    /** This profile reads no implicit configuration file or environment setting. */
    public List<String> configurationPrecedence() { return List.of("CLI", "PROFILE_DEFAULT"); }

    /** Checking has no stdout artifact; all human-readable output uses stderr. */
    public String diagnosticChannel() { return "stderr"; }

    /** Canonical diagnostic text is versioned separately from reference identity. */
    public String diagnosticCatalogVersion() {
        return guru.interlis.model.VersionManifest.current().diagnosticCatalogVersion();
    }

    /** Explicit deviations, also recorded by the fresh process comparison. */
    public List<String> deviations() {
        return List.of("Own tool identity and canonical diagnostic text; no timestamp equivalence",
                "Empty invocation is a headless usage error",
                "Unknown options, missing values and missing sources fail strictly",
                "Bare model names are an explicit extension",
                "Project language/norm profiles remain in force",
                "Generator, repository-administration and unsupported host options fail before IO");
    }

    /** No generator, IlisMeta, GUI or legacy Java-model adapter is enabled. */
    public static LegacyCliProfile ili2c569() {
        var supported = new ArrayList<CommandSpec.Option>();
        for (String flag : List.of("-h", "--help", "-u", "--usage", "-v", "--version", "-o0",
                "--no-auto", "--quiet", "--without-warnings", "--trace"))
            supported.add(new CommandSpec.Option(flag, 0, false));
        supported.add(new CommandSpec.Option("--modeldir", 1, false));
        var unsupported = new LinkedHashMap<String, String>();
        for (String option : List.of("-o1", "-o2", "-oXSD", "-oGML", "-oILIGML2", "-oFMT", "-oIMD",
                "-oIMD16", "-oNLS", "-oUML", "-oIOM", "--nlsxml", "--lang", "--trafoDiff",
                "--trafoFactor", "--trafoEpsg", "--trafoImports", "--trafoNewModel", "--out", "--with-predefined"))
            unsupported.put(option, "model generators and transformation exports are outside the checking profile");
        for (String option : List.of("--check-repo-ilis", "--clone-repos", "--listModels", "--listAllModels",
                "--listModels2", "--listAllModels2", "--listData"))
            unsupported.put(option, "repository administration is outside the checking profile");
        for (String option : List.of("--proxy", "--proxyPort", "--log", "--xtflog", "--translation"))
            unsupported.put(option, "use explicit host services or the new configuration API; this legacy option is not enabled");
        return new LegacyCliProfile("ili2c-check/5.6.9-v1", "5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722",
                "37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e",
                new CommandSpec("ili2c", supported, true), unsupported);
    }
}
