package guru.interlis.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure, locale-independent DE/EN presentation. No IO, language checking or mutable locale state. */
public final class DiagnosticMessageFormatter {
    private record Text(String en, String de) { }
    private static Map.Entry<String, Text> text(String key, String en, String de) {
        return Map.entry("diagnostic." + key, new Text(en, de));
    }
    private static final Map<String, Text> TEXTS = Map.ofEntries(
            text("syntax.lexical", "The input contains an invalid character or token '{token}'.", "Die Eingabe enthält ein ungültiges Zeichen oder Token '{token}'."),
            text("syntax.unexpected", "Unexpected token '{actual}'; expected {expected}.", "Unerwartetes Token '{actual}'; erwartet wird {expected}."),
            text("syntax.missing", "A required token is missing; expected {expected}.", "Ein erforderliches Token fehlt; erwartet wird {expected}."),
            text("syntax.trailing", "Unexpected content '{actual}' follows the end of the model input.", "Nach dem Ende der Modelleingabe folgt unerwarteter Inhalt '{actual}'."),
            text("syntax.version", "Language version '{actual}' is not supported. Supported versions: {supported}.", "Die Sprachversion '{actual}' wird nicht unterstützt. Unterstützte Versionen: {supported}."),
            text("syntax.versionConstruct", "The construct '{construct}' is not allowed in INTERLIS {language}.", "Das Konstrukt '{construct}' ist in INTERLIS {language} nicht erlaubt."),
            text("syntax.redundantProperty", "The property '{property}' is repeated unnecessarily.", "Die Eigenschaft '{property}' ist unnötig mehrfach angegeben."),
            text("resolution.missing", "The requested or imported model '{model}' could not be found.", "Das angeforderte oder importierte Modell '{model}' wurde nicht gefunden."),
            text("resolution.ambiguous", "The model '{model}' is ambiguous. Candidates: {candidates}.", "Das Modell '{model}' ist mehrdeutig. Kandidaten: {candidates}."),
            text("resolution.source", "The source '{source}' could not be provided: {reason}.", "Die Quelle '{source}' konnte nicht bereitgestellt werden: {reason}."),
            text("resolution.identity", "The source was expected to contain model '{expected}', but contains {actual}.", "In der Quelle wurde das Modell '{expected}' erwartet; enthalten ist {actual}."),
            text("resolution.overlay.missing", "The translation original '{model}' required by overlay '{overlay}' could not be found.", "Das vom Overlay '{overlay}' benötigte Übersetzungsoriginal '{model}' wurde nicht gefunden."),
            text("resolution.overlay.ambiguous", "The translation original '{model}' for overlay '{overlay}' is ambiguous. Candidates: {candidates}.", "Das Übersetzungsoriginal '{model}' für das Overlay '{overlay}' ist mehrdeutig. Kandidaten: {candidates}."),
            text("resolution.translation.identity", "The selected translation source has an inconsistent model identity: {reason}.", "Die ausgewählte Übersetzungsquelle hat eine widersprüchliche Modellidentität: {reason}."),
            text("resolution.lookup", "Model lookup: {model} @ {repository}", "Modellsuche: {model} @ {repository}"),
            text("resolution.selected", "Model selected: {model} {detail}", "Modellauswahl: {model} {detail}"),
            text("resolution.verification", "Completion check: checking resolved sources for changes.", "Abschlussprüfung: verwendete Quellen auf Änderungen prüfen."),
            text("resolution.cache", "Cache access for source '{source}': {event}.", "Cachezugriff für Quelle '{source}': {event}."),
            text("resolution.adjustment", "Repository '{repository}', model '{model}': {reason}.", "Repository '{repository}', Modell '{model}': {reason}."),
            text("resolution.rejected", "Model '{model}' could not be provided: {reason}.", "Das Modell '{model}' konnte nicht bereitgestellt werden: {reason}."),
            text("semantics.unresolved", "The name '{name}' cannot be resolved at this location.", "Der Name '{name}' kann an dieser Stelle nicht aufgelöst werden."),
            text("semantics.ambiguous", "The name '{name}' is ambiguous at this location. Candidates: {candidates}.", "Der Name '{name}' ist an dieser Stelle mehrdeutig. Kandidaten: {candidates}."),
            text("semantics.duplicate", "The name '{name}' is already declared in this namespace.", "Der Name '{name}' ist in diesem Namensraum bereits deklariert."),
            text("semantics.visibility", "The name '{name}' is not accessible here: {rule}.", "Der Name '{name}' ist hier nicht zugänglich: {rule}."),
            text("semantics.type", "Type '{actual}' is not compatible with the required type '{expected}'.", "Der Typ '{actual}' ist mit dem erforderlichen Typ '{expected}' nicht verträglich."),
            text("semantics.cycle", "The relationship '{relationship}' contains a prohibited cycle: {cycle}.", "Die Beziehung '{relationship}' enthält einen unzulässigen Zyklus: {cycle}."),
            text("semantics.numeric", "The numeric range or precision is invalid: {reason}.", "Der numerische Wertebereich oder seine Genauigkeit ist ungültig: {reason}."),
            text("semantics.path", "The object-path segment '{segment}' is invalid: {reason}.", "Der Objektpfadschritt '{segment}' ist ungültig: {reason}."),
            text("semantics.inheritance", "The definition of '{element}' violates a required condition: {rule}.", "Die Definition von '{element}' verletzt eine erforderliche Bedingung: {rule}."),
            text("semantics.overlay", "Overlay '{overlay}' cannot be applied to '{target}': {reason}.", "Das Overlay '{overlay}' kann nicht auf '{target}' angewendet werden: {reason}."),
            text("implementation.unsupported", "The required language feature '{feature}' is not implemented for '{construct}'; the model has not been fully checked.", "Die erforderliche Sprachfunktion '{feature}' ist für '{construct}' nicht implementiert; das Modell ist nicht vollständig geprüft."),
            text("projection.unsupported", "The consumer '{consumer}' does not support the valid model feature '{feature}'.", "Der Verbraucher '{consumer}' unterstützt die gültige Modellfunktion '{feature}' nicht."),
            text("internal", "An internal error prevented completion. Reference: {failureId}.", "Ein interner Fehler hat den Abschluss verhindert. Referenz: {failureId}."),
            text("limit", "The resource limit for '{resource}' was reached ({limit}); checking is incomplete.", "Die Ressourcengrenze für '{resource}' wurde erreicht ({limit}); die Prüfung ist unvollständig.")
    );

    /**
     * Returns safe text/hint without modifying canonical values. Unknown external
     * codes/keys and missing arguments produce a neutral fallback, never a key dump.
     * Null inputs are caller errors. Exact numeric values are never rounded.
     */
    public DiagnosticMessage format(Diagnostic diagnostic, DiagnosticLanguage language) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        Objects.requireNonNull(language, "language");
        var definition = DiagnosticCatalog.initial().find(diagnostic.code());
        var template = TEXTS.get(diagnostic.messageKey());
        if (template == null || definition.isEmpty()
                || !definition.orElseThrow().messageKey().equals(diagnostic.messageKey()))
            return new DiagnosticMessage(language == DiagnosticLanguage.DE
                    ? "Für diese Diagnose sind keine weiteren Angaben verfügbar."
                    : "No further explanation is available for this diagnostic.", Optional.empty());
        var values = arguments(diagnostic.arguments());
        if (diagnostic.code().value().equals("ILI-RES-0010") && values.containsKey("event"))
            values.put("event", cacheEvent(values.get("event"), language));
        if (diagnostic.code().value().startsWith("ILI-RES-") && values.containsKey("reason")) values.put("reason", resolutionReason(values.get("reason"), language));
        String rule = diagnostic.arguments().stream().filter(a -> a.name().equals("rule"))
                .filter(DiagnosticArgument.TextArgument.class::isInstance)
                .map(DiagnosticArgument.TextArgument.class::cast).map(DiagnosticArgument.TextArgument::value)
                .findFirst().orElse("");
        if (diagnostic.code().value().equals("ILI-SEM-0010")) {
            if (rule.endsWith("/SCOPE_OUTER_TYPE_NAME")) return new DiagnosticMessage(
                    replace(language == DiagnosticLanguage.DE
                            ? "Der lokale Typname '{element}' kollidiert mit einem Namen im umschliessenden Namensraum."
                            : "The local type name '{element}' conflicts with a name in the enclosing namespace.", values),
                    Optional.of(language == DiagnosticLanguage.DE
                            ? "Verwende unterschiedliche Namen für die kollidierenden Deklarationen."
                            : "Use distinct names for the conflicting declarations."));
            if (rule.endsWith("/SCOPE_TYPE_NAME_UNIQUE")) return new DiagnosticMessage(
                    replace(language == DiagnosticLanguage.DE ? "Der Typname '{element}' ist in diesem Namensraum bereits deklariert."
                            : "The type name '{element}' is already declared in this namespace.", values), Optional.empty());
            if (rule.endsWith("/SCOPE_INHERITED_TYPE_NAME")) return new DiagnosticMessage(
                    replace(language == DiagnosticLanguage.DE ? "Der geerbte Typname '{element}' benötigt eine explizite EXTENDED-Deklaration."
                            : "The inherited type name '{element}' requires an explicit EXTENDED declaration.", values), Optional.empty());
        }
        return new DiagnosticMessage(replace(language == DiagnosticLanguage.DE ? template.de() : template.en(), values),
                Optional.ofNullable(values.get("detail")).filter(v -> !v.isEmpty()));
    }

    private static String cacheEvent(String event, DiagnosticLanguage language) {
        boolean de = language == DiagnosticLanguage.DE;
        return switch (event) {
            case "FRESH_HIT" -> de ? "lokalen Cache innerhalb der Gültigkeitsfrist verwendet" : "content used from fresh cache";
            case "NOT_MODIFIED" -> de ? "Inhalt aus Cache verwendet (HTTP 304)" : "content used from cache (HTTP 304)";
            case "OFFLINE_HIT" -> de ? "Inhalt offline aus Cache verwendet" : "content used from cache offline";
            case "DOWNLOAD" -> de ? "Quelle heruntergeladen" : "source downloaded";
            case "CACHE_MISS" -> de ? "Cache konnte nicht verwendet werden" : "cache could not be used";
            case "OFFLINE_MISS" -> de ? "Quelle im Offlinecache nicht verfügbar" : "source unavailable in offline cache";
            default -> event;
        };
    }

    private static String resolutionReason(String reason, DiagnosticLanguage language) {
        boolean de = language == DiagnosticLanguage.DE;
        return switch (reason) {
            case "DUPLICATE_ROOT" -> de ? "mehrere Startversionen; die erste im Index wird verwendet" : "multiple initial versions; using the first in index order";
            case "DUPLICATE_SUCCESSOR" -> de ? "mehrere Nachfolgeversionen; die erste im Index wird verwendet" : "multiple successors; using the first in index order";
            case "BROKEN_CHAIN" -> de ? "unterbrochene Versionskette; die letzte erreichbare Version wird verwendet" : "broken version chain; using the last reachable version";
            case "NO_ROOT" -> de ? "keine Version ohne Vorgänger; Modell wird ignoriert" : "no version without a predecessor; ignoring model";
            case "REPOSITORY_UNAVAILABLE" -> de ? "Modellindex nicht verfügbar; Suche wird fortgesetzt" : "model index unavailable; continuing search";
            case "SITE_UNAVAILABLE" -> de ? "Repositoryverknüpfungen nicht verfügbar; Suche wird fortgesetzt" : "repository links unavailable; continuing search";
            case "NOT_FOUND" -> de ? "Quelle nicht gefunden" : "source not found";
            case "OFFLINE" -> de ? "verifizierter Inhalt ist offline nicht verfügbar" : "verified content is unavailable offline";
            case "ACCESS_DENIED" -> de ? "Zugriff verweigert" : "access denied";
            case "TIMEOUT" -> de ? "Zeitüberschreitung beim Laden" : "loading timed out";
            case "IO_FAILURE" -> de ? "Ein-/Ausgabefehler beim Laden" : "input/output failure while loading";
            case "INVALID_ENCODING" -> de ? "Quelle enthält ungültiges UTF-8" : "source contains invalid UTF-8";
            case "TOO_LARGE" -> de ? "Quelle überschreitet die Grössenbegrenzung" : "source exceeds the size limit";
            case "REDIRECT_LIMIT" -> de ? "zu viele HTTP-Weiterleitungen" : "too many HTTP redirects";
            case "VERSION_MISMATCH" -> de ? "angeforderte Modellversion stimmt nicht mit den verfügbaren Quellen überein" : "requested model version does not match available sources";
            case "INVALID_SOURCE" -> de ? "Modellkopf oder Quellinhalt ist ungültig" : "model header or source content is invalid";
            case "INDEX_FAILURE", "INVALID_INDEX" -> de ? "Repositorymetadaten sind ungültig" : "repository metadata is invalid";
            case "PIN_MISMATCH" -> de ? "Quelle stimmt nicht mit dem expliziten Pin überein" : "source does not match the explicit pin";
            case "LOCK_MISMATCH" -> de ? "Quelle oder Auswahl stimmt nicht mit dem Lock überein" : "source or selection does not match the lock";
            case "CHANGED_SOURCE" -> de ? "Quelle hat sich während der Beschaffung geändert" : "source changed during resolution";
            default -> reason;
        };
    }

    /** Formats a related-location explanation; its position is rendered by the host without IO. */
    public String formatRelated(RelatedLocation location, DiagnosticLanguage language) {
        Objects.requireNonNull(location, "location"); Objects.requireNonNull(language, "language");
        if (location.messageKey().equals("diagnostic.related.conflictingDeclaration"))
            return replace(language == DiagnosticLanguage.DE ? "Kollidierende Deklaration von '{name}'."
                    : "Conflicting declaration of '{name}'.", arguments(location.arguments()));
        return language == DiagnosticLanguage.DE ? "Verwandte Quellstelle." : "Related source location.";
    }

    private static Map<String, String> arguments(java.util.List<DiagnosticArgument> arguments) {
        var values = new LinkedHashMap<String, String>();
        for (var argument : arguments) {
            String value = switch (argument) {
                case DiagnosticArgument.TextArgument a -> a.value();
                case DiagnosticArgument.IdentifierArgument a -> a.value();
                case DiagnosticArgument.TokenArgument a -> a.value();
                case DiagnosticArgument.IntegerArgument a -> a.value().toString();
                case DiagnosticArgument.DecimalArgument a -> a.value().toString();
                case DiagnosticArgument.TypeNameArgument a -> a.value();
                case DiagnosticArgument.IdentifierListArgument a -> String.join(", ", a.values());
            };
            if (argument.name().equals("rule")) value = value.replaceFirst("/[A-Z][A-Z0-9_]*$", "");
            values.putIfAbsent(argument.name(), safeText(value));
        }
        return values;
    }

    private static String replace(String template, Map<String, String> values) {
        var result = new StringBuilder();
        for (int i = 0; i < template.length();) {
            int start = template.indexOf('{', i);
            if (start < 0) { result.append(template, i, template.length()); break; }
            result.append(template, i, start);
            int end = template.indexOf('}', start);
            result.append(values.getOrDefault(template.substring(start + 1, end), "?"));
            i = end + 1;
        }
        return result.toString();
    }

    /** Escapes terminal controls and line/bidi separators; preserves printable Unicode and exact text. */
    public static String safeText(String value) {
        Objects.requireNonNull(value, "value");
        var out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029'
                    || c == '\u200e' || c == '\u200f' || c == '\u061c'
                    || c >= '\u202a' && c <= '\u202e' || c >= '\u2066' && c <= '\u2069') {
                String hex = Integer.toHexString(c);
                out.append("\\u");
                out.append("0".repeat(4 - hex.length())).append(hex);
            } else out.append(c);
        }
        return out.toString();
    }
}
