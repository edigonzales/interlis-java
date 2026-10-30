package guru.interlis.diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable catalog of diagnostic codes. Unknown internally generated codes
 * are programming errors; external protocol codes are validated by the
 * adapter before they enter the compiler.
 */
public final class DiagnosticCatalog {
    public static final String VERSION = "interlis-diagnostics/v1";
    private final Map<DiagnosticCode, DiagnosticDefinition> definitions;

    /**
     * Creates an immutable catalog; duplicate codes are rejected.
     *
     * @param definitions catalog entries
     */
    public DiagnosticCatalog(List<DiagnosticDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<DiagnosticCode, DiagnosticDefinition> map = new LinkedHashMap<>();
        for (DiagnosticDefinition definition : definitions) {
            if (map.put(definition.code(), definition) != null) {
                throw new IllegalArgumentException("duplicate diagnostic code: " + definition.code());
            }
        }
        this.definitions = Map.copyOf(map);
    }

    /**
     * Returns the definition for a code or throws for an unknown code.
     *
     * @param code catalog code
     * @return definition
     */
    public DiagnosticDefinition require(DiagnosticCode code) {
        DiagnosticDefinition definition = definitions.get(code);
        if (definition == null) {
            throw new IllegalStateException("unknown compiler diagnostic code: " + code);
        }
        return definition;
    }

    /** Returns the immutable definitions in catalog order.
     *
     * @return catalog definitions
     */
    public List<DiagnosticDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    /** Finds a definition without throwing.
     *
     * @param code catalog code
     * @return definition, or empty
     */
    public Optional<DiagnosticDefinition> find(DiagnosticCode code) {
        return Optional.ofNullable(definitions.get(code));
    }

    /** Validates phase, position rule and argument names/kinds of a draft.
     *
     * @param draft draft to validate
     */
    public void validate(DiagnosticDraft draft) {
        DiagnosticDefinition definition = require(draft.code());
        if (!definition.allowedPhases().contains(draft.phase())) {
            throw new IllegalStateException("phase " + draft.phase() + " not allowed for " + draft.code());
        }
        if (definition.positionRule() == DiagnosticDefinition.PositionRule.REQUIRED
                && draft.primaryRange().isEmpty()) {
            throw new IllegalStateException("diagnostic " + draft.code() + " requires a source range");
        }
        if (definition.positionRule() == DiagnosticDefinition.PositionRule.FORBIDDEN
                && draft.primaryRange().isPresent()) {
            throw new IllegalStateException("diagnostic " + draft.code() + " forbids a source range");
        }
        for (DiagnosticArgument argument : draft.arguments()) {
            ArgumentDefinition expected = definition.argument(argument.name())
                    .orElseThrow(() -> new IllegalStateException(
                            "unknown argument " + argument.name() + " for " + draft.code()));
            ArgumentKind actual = switch (argument) {
                case DiagnosticArgument.TextArgument ignored -> ArgumentKind.TEXT;
                case DiagnosticArgument.IdentifierArgument ignored -> ArgumentKind.IDENTIFIER;
                case DiagnosticArgument.TokenArgument ignored -> ArgumentKind.TOKEN;
                case DiagnosticArgument.IntegerArgument ignored -> ArgumentKind.INTEGER;
                case DiagnosticArgument.DecimalArgument ignored -> ArgumentKind.DECIMAL;
                case DiagnosticArgument.TypeNameArgument ignored -> ArgumentKind.TYPE_NAME;
                case DiagnosticArgument.IdentifierListArgument ignored -> ArgumentKind.IDENTIFIER_LIST;
            };
            if (expected.kind() != actual) {
                throw new IllegalStateException("argument " + argument.name() + " of "
                        + draft.code() + " has kind " + actual + ", expected " + expected.kind());
            }
        }
    }

    private static DiagnosticCatalog initial;

    /** The versioned initial catalog. The returned instance is immutable. */
    public static synchronized DiagnosticCatalog initial() {
        if (initial == null) {
            initial = buildInitial();
        }
        return initial;
    }

    private static DiagnosticCatalog buildInitial() {
        List<DiagnosticDefinition> list = new ArrayList<>();
        list.add(def("ILI-SYN-0001", "lexically invalid input", Severity.ERROR,
                Set.of(Phase.LEXING), "diagnostic.syntax.lexical",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("token", ArgumentKind.TOKEN)));
        list.add(def("ILI-SYN-0002", "unexpected token", Severity.ERROR,
                Set.of(Phase.PARSING), "diagnostic.syntax.unexpected",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("actual", ArgumentKind.TOKEN), arg("expected", ArgumentKind.IDENTIFIER_LIST)));
        list.add(def("ILI-SYN-0003", "expected token missing", Severity.ERROR,
                Set.of(Phase.PARSING), "diagnostic.syntax.missing",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("expected", ArgumentKind.IDENTIFIER_LIST)));
        list.add(def("ILI-SYN-0004", "content after the complete start input", Severity.ERROR,
                Set.of(Phase.PARSING), "diagnostic.syntax.trailing",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("actual", ArgumentKind.TOKEN)));
        list.add(def("ILI-SYN-0005", "unsupported language version", Severity.ERROR,
                Set.of(Phase.PARSING), "diagnostic.syntax.version",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("actual", ArgumentKind.TEXT), arg("supported", ArgumentKind.IDENTIFIER_LIST)));
        list.add(def("ILI-SYN-0006", "construct not allowed in the chosen language version", Severity.ERROR,
                Set.of(Phase.PARSING, Phase.SEMANTIC_CHECKING), "diagnostic.syntax.versionConstruct",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("construct", ArgumentKind.TEXT), arg("language", ArgumentKind.TEXT)));
        list.add(def("ILI-SYN-0007", "redundant repeated property", Severity.WARNING,
                Set.of(Phase.PARSING), "diagnostic.syntax.redundantProperty",
                DiagnosticDefinition.PositionRule.REQUIRED, arg("property", ArgumentKind.TOKEN)));
        list.add(def("ILI-RES-0001", "requested or imported model is missing", Severity.ERROR,
                Set.of(Phase.SOURCE_RESOLUTION), "diagnostic.resolution.missing",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("model", ArgumentKind.IDENTIFIER)));
        list.add(def("ILI-RES-0002", "ambiguous model resolution", Severity.ERROR,
                Set.of(Phase.SOURCE_RESOLUTION), "diagnostic.resolution.ambiguous",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("model", ArgumentKind.IDENTIFIER), arg("candidates", ArgumentKind.IDENTIFIER_LIST)));
        list.add(def("ILI-RES-0003", "source could not be provided", Severity.ERROR,
                Set.of(Phase.SOURCE_RESOLUTION), "diagnostic.resolution.source",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("source", ArgumentKind.TEXT), arg("reason", ArgumentKind.TEXT)));
        list.add(def("ILI-RES-0004", "resolved model identity does not match the source content", Severity.ERROR,
                Set.of(Phase.SOURCE_RESOLUTION, Phase.BINDING), "diagnostic.resolution.identity",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("expected", ArgumentKind.IDENTIFIER), arg("actual", ArgumentKind.IDENTIFIER_LIST)));
        list.add(def("ILI-SEM-0001", "name unresolved in the required scope", Severity.ERROR,
                Set.of(Phase.BINDING, Phase.TYPE_CHECKING), "diagnostic.semantics.unresolved",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("name", ArgumentKind.IDENTIFIER)));
        list.add(def("ILI-SEM-0002", "name is ambiguous at this location", Severity.ERROR,
                Set.of(Phase.BINDING), "diagnostic.semantics.ambiguous",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("name", ArgumentKind.IDENTIFIER), arg("candidates", ArgumentKind.IDENTIFIER_LIST)));
        list.add(def("ILI-SEM-0003", "illegal duplicate declaration", Severity.ERROR,
                Set.of(Phase.DECLARATION_INDEXING), "diagnostic.semantics.duplicate",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("name", ArgumentKind.IDENTIFIER)));
        list.add(def("ILI-SEM-0004", "reference violates visibility or ordering rules", Severity.ERROR,
                Set.of(Phase.BINDING), "diagnostic.semantics.visibility",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("name", ArgumentKind.IDENTIFIER), arg("rule", ArgumentKind.TEXT)));
        list.add(def("ILI-SEM-0005", "type incompatible with the checked operation", Severity.ERROR,
                Set.of(Phase.TYPE_CHECKING), "diagnostic.semantics.type",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("actual", ArgumentKind.TYPE_NAME), arg("expected", ArgumentKind.TYPE_NAME)));
        list.add(def("ILI-SEM-0006", "illegal cycle in this relationship", Severity.ERROR,
                Set.of(Phase.SEMANTIC_CHECKING), "diagnostic.semantics.cycle",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("relationship", ArgumentKind.TEXT), arg("cycle", ArgumentKind.IDENTIFIER_LIST)));
        list.add(def("ILI-SEM-0007", "invalid numeric range or declared precision", Severity.ERROR,
                Set.of(Phase.TYPE_CHECKING), "diagnostic.semantics.numeric",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("reason", ArgumentKind.TEXT)));
        list.add(def("ILI-SEM-0008", "invalid object path step", Severity.ERROR,
                Set.of(Phase.TYPE_CHECKING), "diagnostic.semantics.path",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("segment", ArgumentKind.IDENTIFIER), arg("reason", ArgumentKind.TEXT)));
        list.add(def("ILI-SEM-0010", "inheritance or override violates a checked rule", Severity.ERROR,
                Set.of(Phase.SEMANTIC_CHECKING), "diagnostic.semantics.inheritance",
                DiagnosticDefinition.PositionRule.REQUIRED,
                arg("element", ArgumentKind.IDENTIFIER), arg("rule", ArgumentKind.TEXT)));
        list.add(def("ILI-RES-0005", "configured translation original is missing", Severity.ERROR,
                Set.of(Phase.SOURCE_RESOLUTION), "diagnostic.resolution.overlay.missing", DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("overlay", ArgumentKind.TEXT), arg("model", ArgumentKind.IDENTIFIER)));
        list.add(def("ILI-RES-0006", "configured translation original is ambiguous", Severity.ERROR,
                Set.of(Phase.SOURCE_RESOLUTION), "diagnostic.resolution.overlay.ambiguous", DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("overlay", ArgumentKind.TEXT), arg("model", ArgumentKind.IDENTIFIER), arg("candidates", ArgumentKind.IDENTIFIER_LIST)));
        list.add(def("ILI-RES-0007", "translation resolver selection has an invalid identity", Severity.ERROR,
                Set.of(Phase.SOURCE_RESOLUTION), "diagnostic.resolution.translation.identity", DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("reason", ArgumentKind.TEXT)));
        list.add(def("ILI-SEM-0011", "invalid semantic overlay configuration", Severity.ERROR,
                Set.of(Phase.BINDING), "diagnostic.semantics.overlay",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("overlay", ArgumentKind.TEXT), arg("target", ArgumentKind.TEXT), arg("reason", ArgumentKind.TEXT)));
        list.add(def("ILI-IMPL-0001", "required language feature is not implemented", Severity.ERROR,
                Set.of(Phase.PARSING, Phase.DECLARATION_INDEXING, Phase.TYPE_CHECKING,
                        Phase.SEMANTIC_CHECKING), "diagnostic.implementation.unsupported",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("feature", ArgumentKind.TEXT), arg("construct", ArgumentKind.TEXT)));
        list.add(def("ILI-PROJ-0001", "consumer does not support a valid model feature", Severity.ERROR,
                Set.of(Phase.PROJECTION), "diagnostic.projection.unsupported",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("consumer", ArgumentKind.TEXT), arg("feature", ArgumentKind.TEXT)));
        list.add(def("ILI-INF-0001", "internal error", Severity.ERROR,
                Set.of(Phase.INFRASTRUCTURE), "diagnostic.internal",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("failureId", ArgumentKind.TEXT)));
        list.add(def("ILI-INF-0002", "explicit resource limit reached", Severity.ERROR,
                Set.of(Phase.INFRASTRUCTURE), "diagnostic.limit",
                DiagnosticDefinition.PositionRule.OPTIONAL,
                arg("resource", ArgumentKind.TEXT), arg("limit", ArgumentKind.INTEGER)));
        return new DiagnosticCatalog(list);
    }

    private static DiagnosticDefinition def(String code, String meaning, Severity severity,
                                            Set<Phase> phases, String messageKey,
                                            DiagnosticDefinition.PositionRule positionRule,
                                            ArgumentDefinition... arguments) {
        return new DiagnosticDefinition(new DiagnosticCode(code), meaning, severity, phases,
                messageKey, List.of(arguments), positionRule, VERSION);
    }

    private static ArgumentDefinition arg(String name, ArgumentKind kind) {
        return new ArgumentDefinition(name, kind);
    }
}
