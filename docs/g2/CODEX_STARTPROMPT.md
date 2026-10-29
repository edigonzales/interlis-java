# Codex-Startprompt – INTERLIS Java G2 fertigstellen und Python entfernen

**Rolle: Agent A (Implementierung).** Dieser Text ist ein ausdrücklicher Umsetzungsauftrag und wird aus dem Root des Repositories [`edigonzales/interlis-java`](https://github.com/edigonzales/interlis-java) gestartet. Der verbindliche Plan liegt unter `docs/g2/EXECUTION_PLAN.md`; die unabhängige Reviewanweisung unter `docs/g2/OPENCODE_REVIEW_PROMPT.md`.

## Auftrag und oberste Regeln

Implementiere den bestehenden **G2-Umfang / WP-20–27 für INTERLIS 2.3 und 2.4** vollständig und fachlich korrekt. **Entferne den gesamten aktiven Python-Code und alle Python-Laufzeitabhängigkeiten aus `interlis-java`.** Verwende das bei `gretl-runtime` bewährte Verfahren:

1. **Einen zusammenhängenden Lieferabschnitt** des Plans implementieren.
2. Reproduzierbare, aussagekräftige Tests und erforderliche Prozess-/Plattformnachweise ausführen.
3. **Code, Tests und Implementierungsbericht lokal committen** und den vollständigen Commit-SHA übergeben.
4. Einen **unabhängigen OpenCode-Review mit DeepSeek Flash 1017 in frischem Kontext** für exakt diesen SHA verlangen. Eine tatsächliche Ausführung darfst du nicht behaupten, wenn du OpenCode nicht aufrufen kannst.
5. **Nur nach `PASS`** den nächsten Lieferabschnitt beginnen. Bei `FAIL` konkret nacharbeiten, neu committen und erneut prüfen; bei `BLOCKED` fehlende Voraussetzungen oder eine Nutzerentscheidung herstellen. **Nie eigenmächtig auf `PASS` setzen.**

**Die einzige Review-Urteilssystematik lautet `PASS` / `FAIL` / `BLOCKED`.** Normfreigaben sind ausschliesslich Sache des Nutzers; eine offene Normentscheidung ist im Reviewergebnis nötigenfalls `BLOCKED (NORM_DECISION)`. Eine reguläre Phase wird durch den **Review der letzten Lieferung inklusive vollständigem kumulativem Phasengate** abgeschlossen; der vorgeschaltete Pilot `L0A` hat ausdrücklich nur seinen engeren Pilot-Gateumfang. Es gibt keinen zusätzlichen rein administrativen Zweitreview für dieselbe Phase.

**Keine Umgehungen:** Tests, Suite-/Normpins, XFAILs, erwartete Diagnosen, Referenzmodelle oder Abnahmekriterien nicht lockern, um grün zu werden; keine Tests überspringen und keinen Erfolg erfinden. Historische Berichte erhalten, aber nicht wieder neue Inventar- oder Evidenzframeworks bauen. `AGENTS.md` und die relevanten fachlichen Spezifikationen bleiben gültig. Der neue Plan ersetzt nur den alten starren A–I-**Arbeitsablauf**. P3-Release/Legacy-CLI, P4 und P5 werden nicht vorgezogen.

## 1. Technischer Einstieg

1. `pwd`, `git status --short`, `git rev-parse HEAD`, verfügbare JDK-/Gradle-/Node-Versionen und die tatsächlich vorhandenen Tasks feststellen. Der untersuchte historische SHA war `29f079d558ac8acca411f9f2975e85d537672b10`; falls das Checkout weiter ist, gilt ausschliesslich der tatsächliche Stand. Fremde Änderungen nicht überschreiben.
2. Lies `AGENTS.md`, `docs/g2/EXECUTION_PLAN.md`, den aktuellen Anfang von `docs/implementation-status.md` und `docs/evidence/stabilization/phase-a-dashboard.md`. Vor einem Fix die **zugehörigen** Kapitel unter `docs/spec/`, Normstellen, vorhandenen Tests und öffentlichen APIs lesen. Nicht alle alten Zwischenberichte in jedem Schritt neu laden.
3. Lege eine **einzige knappe** `docs/g2/STATUS.md` an: aktueller Lieferabschnitt, Status, letzter unabhängiger `PASS`-SHA, fehlende Voraussetzungen und nächster Schritt. Historische A–I-Reviews bleiben unverändert. Eindeutige aktive Verweise auf `docs/g2/EXECUTION_PLAN.md` in den bisherigen Steuerungs-/Einstiegsdateien herstellen, ohne die fachlichen Spezifikationen zu entwerten.
4. Arbeite die Lieferungen in dieser Reihenfolge ab: `L0A`, `L0B-1`, `L0B-2`, `L0B-3`, `L1-1` bis `L1-3`, `L2-1` bis `L2-3`, `L3-1` bis `L3-2`, `L4-1`, `L4-2`. **Beginne jetzt nur mit `L0A`.** Die Kriterien-IDs und fachlichen Gatebedingungen stehen vollständig im Plan.

## 2. Jetzt umsetzen: `L0A` – echter Lexer-Pilotfix

**Ausgangsproblem:** Unter `testdata/g2/a2-unterminated-nested-comment.ili` liegt ein bekanntes `.ili`-Modell mit einem **äusseren, nach abgeschlossenem inneren Block noch offenen Kommentar**. Der bisherige Status beschreibt eine falsche Annahme durch den Compiler. Der bestehende `LexicalRecoveryContractTest` deckte beim untersuchten Stand den unterminierten äusseren Fall nicht vollständig ab.

**Prüfe das aktuelle Verhalten zuerst selbst.** Der Bericht ist keine Ersatztestausführung.

### Fachliche Aufgaben

- Füge zunächst einen Test hinzu, der die reale Falschannahme nachweislich als **RED** reproduziert. Teste **INTERLIS 2.3 und 2.4**.
- Teste einen gültigen, auch mehrstufig verschachtelten Blockkommentar als positive Kontrolle und mindestens zwei negative Varianten: offener innerer sowie offener äusserer Kommentar nach korrekt beendetem inneren Kommentar.
- Prüfe `ParseMode.STRICT` und `ParseMode.EDITOR`, die relevante Syntax-/Lexerdiagnose und den tatsächlichen Compile-Ausgang. Im fehlerhaften Closure darf **kein gültiges `CompiledSchema`** entstehen. Zulässige Editor-Recovery bleibt erhalten.
- Wo für diese Lexikregel relevant: vollständige Quelltextrekonstruktion, Trivia, Original-Source-Ranges, UTF-16/Unicode, LF/CRLF und nachfolgende Deklarationen. Nicht nur `diagnostics().isEmpty()` testen.
- Korrigiere die Ursache **minimal in der passenden Lexer-/Syntaxschicht**. Keine neue Parserarchitektur; keine ungezielte Grossänderung an `GraphBuilder` oder `SyntaxTreeBuilder`.
- Führe gezielte JVM-Vertragstests, die dafür nötigen Conformance-Regressionsfälle und bei breit wirkender Lexeränderung die volle gepinnte T2-Suite aus; berührte T3-Verträge nach Plan. Notiere tatsächliche Exits und nicht ausgeführte fällige Prüfungen.

**Abnahmekriterien für diese Lieferung:** `AC0A-01`, `AC0A-02`, `AC0A-03`, `AC0A-04` in `docs/g2/EXECUTION_PLAN.md` – **alle** müssen vom unabhängigen Agenten als erfüllt geprüft werden. Für `AC0A-04` muss er mindestens einen **eigenen**, nicht aus deinen Tests übernommenen Gegenfall durchführen.

### Übergabe nach Implementierung

1. Schreibe `docs/g2/implementation/L0A.md` mit Kriterien, Basis-SHA, Reproduktion, geänderten öffentlichen Verträgen, konkreten Kommandos/Exitcodes, Testzahlen und Reports sowie offenen Punkten. Kein `head_sha` in eine Datei schreiben, die Teil des eigenen Codecommits ist.
2. Committe **Fix + Tests + Bericht** lokal. Lies danach den **vollen** `CANDIDATE_SHA` aus. Prüfe, dass keine unbeabsichtigten uncommitteten Änderungen in den Kandidaten eingeflossen sind.
3. Übergib OpenCode den Plan, `docs/g2/OPENCODE_REVIEW_PROMPT.md`, den **Basis-SHA und Kandidaten-SHA**, `L0A` und den Implementierungsbericht. Verwende dafür eine separate Nachricht oder eine ignorierte Datei unter `build/review/`; diese ist **kein** Bestandteil des geprüften Codecommits.
4. Ist OpenCode nicht automatisiert erreichbar, **beende hier die Bearbeitung** mit dem direkt kopierbaren Handoff. Status `AWAITING_REVIEW`. Weder unabhängiges `PASS` behaupten noch mit `L0B-1` weiterarbeiten.

## 3. Fortsetzung nur nach unabhängiger Abnahme

Nach einem dokumentierten OpenCode-`PASS` für den aktuellen Kandidatencommit beginnt die nächste Lieferung. Ein `FAIL` wird durch eine neue Revision **derselben** Lieferung behoben und erneut geprüft. Bei `BLOCKED` bleibt sie offen; die Ursache darf nicht durch Abschwächung des Tests oder der Normerwartung kaschiert werden. Unabhängige Arbeiten innerhalb derselben Phase dürfen vorbereitet werden, aber kein abhängiger Liefer- oder Phasenabschluss vorweggenommen werden.

### Phase 0b: Python vollständig entfernen

**Kein 36-facher 1:1-Port nach Java.** Erstelle aus dem **aktuellen** `git ls-files '*.py'` eine kurze vollständige Migrationsmatrix mit `REUSE_EXTERNAL`, `PORT_MINIMAL`, `REPLACE_WITH_TEST`, `FREEZE_ARCHIVE`, `DELETE_REDUNDANT`. Jede Löschung oder Übernahme bekommt einen Verwendungs-, Ersatz- und Negativtestnachweis.

- **`L0B-1`, `AC0B-01` bis `AC0B-03`:** Statt `tools/reference/conformance_check.py` den bereits vorhandenen **gepinnten Java-Runner** `InterlisConformance.java` der externen Suite **direkt** in Gradle starten. Commit-Pin `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` und Archiv-SHA aus dem Plan beibehalten. **Keinen zweiten Conformance-Runner erstellen.** Nur die zusätzlichen projektspezifischen Kontrollen der 18 normativen Referenzabweichungen nach kleinem Java-/JUnit-Vertrag migrieren; gleichen Original-/Kontrollinput, Quellenhash, strukturierte Diagnose, Status/Exit und rohe/Projektbilanz prüfen. Der separate INTERLIS-1-Profilfall bleibt separat. Negative Canarys müssen falsch-grüne Fälle erkennen.
- **`L0B-2`, `AC0B-04` bis `AC0B-05`:** Die **aktiven** JUnit-XML-/Suite-/Klassen-/Methoden-/Hash-/Frische-/Normprüfungen der Python-Evidenzwerkzeuge nach Java übernehmen. Ein gleichnamiger Test aus der falschen Klasse, alter/fehlender/geskippter Bericht oder kaputter Hash muss scheitern. Einmalige historische Inventurgeneratoren dürfen mit nachgewiesener Entbehrlichkeit wegfallen; die geltenden Normbelege und gepinnten Daten müssen erhalten bleiben. Keine neue generische Evidenzplattform.
- **`L0B-3`, `AC0B-06` bis `AC0B-08`:** Verbleibende SDK-/CLI-/Referenz-/Corpus-/Benchmark-Skripte ersetzen oder begründet eliminieren. `pythonFreeCheck` neu implementieren, negative Canary, **keine** `.py` und **keine** Python-/pip-Pflicht mehr im aktiven Gradle-/CI-/Reviewweg. Vollständige relevante Tests **in einer Umgebung ohne Python** selbst ausführen. T1/T2/T3, echtes Safari/Web Image, SDK und alle Phasen-0b-Kriterien sind am **letzten** Kandidaten-SHA unabhängig zu prüfen.

**Phase 0b ist nicht abgeschlossen**, wenn auch nur ein aktiver Pflichtpfad Python braucht oder ein historischer Test-/Normvertrag still verloren ging. Ein nachweislich überflüssiger Generator muss **nicht** durch eine gleich grosse Java-Lösung ersetzt werden.

### Fachphasen 1–4

| Lieferung | Implementierungsumfang und Prüfziel |
|---|---|
| `L1-1` | Predefined-/Language-Profile-/Import-/Syntaxregeln, inklusive `SurfaceEdge`, `SurfaceBoundary`, `LineGeometry` und eindeutiger Querytargets. Kriterien `AC1-01` bis `AC1-02`. |
| `L1-2` | Numerik, Units, Typrelationen, OID, Text/Enum/FORMAT/Time, Geometrie, Collections. `AC1-03` bis `AC1-04`. |
| `L1-3` | Vererbung, Assoziationen, Rollen und effektive Reihenfolge; **volles Phasengate**. `AC1-05` bis `AC1-08`. |
| `L2-1` | Objekt-/Attributpfade, Builtins und Constraints. `AC2-01` bis `AC2-02`. |
| `L2-2` | Views/Inspektionen und tatsächliche effektive Resultatstrukturen. `AC2-03` bis `AC2-04`. |
| `L2-3` | Übersetzungen, GENERIC/DEFERRED, Grafik/Metadaten; **volles Phasengate**. `AC2-05` bis `AC2-07`. |
| `L3-1` | Typisierte Overlaynutzdaten, Zielbindung und inhaltliche Invalidation. `AC3-01` bis `AC3-02`. |
| `L3-2` | Repository-/Offline-/Pin-/Lock-/Closure-Verhalten, reale Modelle; **volles Phasengate**. `AC3-03` bis `AC3-06`. |
| `L4-1` | Vollständige Norm-/Assertion-Nachweise, A3-Differentialprüfung, entscheidungsreife Normfälle. `AC4-01` bis `AC4-03`. |
| `L4-2` | Nutzerfreigaben, vollständige gepinnte Modell-Closures, T1–T4, Pythonfreiheit und **unabhängige G2-Gesamtabnahme**. `AC4-04` bis `AC4-07`. |

Die bisherigen 369 Compilerbedingungen, 80 offenen G2-Anforderungen, 18 Referenzabweichungen und die noch nicht abgenommenen A2/A3-Artefakte bleiben im fachlichen Backlog. Erzeuge **keine** neue riesige PDF-/Testinventur, sondern schliesse mit echten Tests und öffentlichen Semantikassertions die bestehenden Lücken. Verwechsel `CANDIDATE_ONLY` nicht mit bewiesener Semantik. Bei Normunklarheiten früh ein kleines Entscheidungspaket mit Originalnorm und eigener Gegenprobe vorbereiten und keine XFAILs eigenmächtig freigeben.

## 4. Einheitliche Abnahme und Berichte

**Ein Implementierungsbericht und ein unabhängiger Prüfbericht je Lieferung**, keine zusätzlichen parallelen Request-/Manifest-/Patchsysteme. Rohreports in `build/reports/` beziehungsweise reproduzierbaren CI-Artefakten. `docs/g2/STATUS.md` ist die einzige operative Fortschrittsübersicht.

**Review-Handoff (nach dem Commit, nicht in dessen Inhalt):**

```text
Repository: edigonzales/interlis-java
Lieferabschnitt: L0A
Basis-SHA: <vollständiger SHA>
Kandidaten-SHA: <vollständiger SHA>
Kriterien: AC0A-01..AC0A-04
Implementierungsbericht: docs/g2/implementation/L0A.md
Reviewanweisung: docs/g2/OPENCODE_REVIEW_PROMPT.md
Offene oder nicht ausgeführte Pflichtprüfungen: <konkret oder keine>
```

**Reviewauswertung:** Nur `PASS` des **geprüften** aktuellen SHAs gibt frei. Beim letzten Lieferabschnitt einer Phase muss OpenCode die **gesamte bisherige Phase** samt frischen Volltests prüfen. Eine reine Ablage des bereits fertigen Reviewberichts als nachfolgender Dokumentationscommit erfordert keine künstliche Zweitabnahme; eine Änderung an Code, Tests, Build, Abhängigkeiten oder ausführbaren Beispielen dagegen schon.

**Jetzt beginnen:** Starte mit `L0A`. Lies die relevanten Dateien, reproduziere den Bug und implementiere die kleine korrekte Lösung mit Tests. Committe, gib die Handoff-Daten aus und überlasse das Urteil dem unabhängigen OpenCode-Agenten. **Nicht ohne `PASS` zu `L0B-1` springen.**
