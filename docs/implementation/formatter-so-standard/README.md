# INTERLIS-Formatter — Solothurner Stil und Constraint-Layout

**Ein Auftrag; zwei fachliche Ausbaustufen; ein unabhängiger Gesamt-Review.**

Stand der Spezifikation: 5. November 2026. Primäragent: Codex. Reviewer: OpenCode 2.x mit DeepSeek Flash 1017 über die direkte DeepSeek-API. Die Ausführung erfolgt autonom, phasenweise und mit dem kleinsten ausreichenden Testumfang.

## Einstieg

[CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md) ist der ausführbare Auftrag. Er beauftragt F0, F1a–F1c, F2a–F2b und F3 sowie anschliessend R. Nach erfolgreichen Tests lokal committen, dann den exakten Kandidaten unabhängig prüfen. Findings korrigieren, betroffene Verträge nachtesten, neu committen und frisch reviewen. Kein Push, Merge oder Release.

| Dokument | Verbindlicher Gegenstand |
|---|---|
| [SPECIFICATION.md](SPECIFICATION.md) | Scope, Architekturentscheidungen, APIs, 40 Abnahmekriterien |
| [STYLE_GUIDE.md](STYLE_GUIDE.md) | Einrückung, Kommentare, Metadaten, Leerzeilen, Tokenabstände, EOL |
| [CONSTRAINT_LAYOUT.md](CONSTRAINT_LAYOUT.md) | Alle fünf Constraint-Familien, Ausdrücke, Klammern und Umbruchalgorithmus |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Klassen-/Methodenverträge, Quelltreue, Sicherheitsprüfung, Laufzeitgrenzen |
| [HOST_INTEGRATION.md](HOST_INTEGRATION.md) | CLI, LSP, zukünftiges GUI, JPMS und tatsächlich betroffene portable Verbraucher |
| [CORPUS_AND_TESTS.md](CORPUS_AND_TESTS.md) | Gepinnter Korpus, Goldens, unabhängige Orakel, Testmatrix und Diffbericht |
| [EXECUTION_PLAN.md](EXECUTION_PLAN.md) | Phasen, Mindestgates, Zwischenstände, Abschluss |
| [TEST_AND_EVIDENCE_POLICY.md](TEST_AND_EVIDENCE_POLICY.md) | Gezielte Tests, Herkunft, Wiederverwendung nach Korrekturen |
| [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) | Autonomer Reviewer, Prozesskontrolle, Urteil, Korrekturschleife |
| [SOURCES.md](SOURCES.md) | Gelesene Primärquellen und Grenzen der Voranalyse |

## Referenzstände, keine Reset-Anweisung

- Core: `edigonzales/interlis-java`, `origin/main`, geprüft bei `c02c9112f93cd69c113faf52d6267f601433e0fd`.
- Stilreferenz: `sogis/sogis-interlis-repository`, geprüft bei `630e3550a93288a99dd9eb4d2d6dc804f9027107`.
- Auftrag B ist die aktive Buildgrundlage: Jenesis/JDK 25, explizite eigene JPMS-Module. Gradle baut nur die bestehenden Wasm-Backends.
- Der Formatter liegt weiterhin im Compiler-Modul. `guru.interlis.format` ist bereits exportiert. Keine neue Formatter-Dependency, kein neuer Build und kein neues Produktmodul.

Codex erfasst vor Änderungen den tatsächlichen BASE_SHA, Arbeitsbaum und Versionsstand. Neuere Änderungen erhalten; keine Rückkehr zum Referenzcommit. Bei geändertem Ausgangscode die Verträge sinnvoll auf den aktuellen Stand übertragen und wesentliche Unterschiede dokumentieren.

## Getroffene Entscheidungen

1. Ein zusammenhängender SO-orientierter Standardstil mit dokumentierter Formatversion; keine parallele alte/neue Formatterengine und kein Profil-/Pluginframework. `SO_STANDARD` ist der fachliche Name, kein Zwang zu einem öffentlichen Enum.
2. F1 liefert Kommentare, Metadaten, Abstände und Deklarationsstruktur. F2 ergänzt vollständiges Constraint-Layout auf derselben Architektur. Die beiden Abschnitte haben eigene Testgates und Zwischencommits.
3. Blockkommentare werden nur gemeinsam verschoben; Text, relative Innenstruktur und Tabellen bleiben erhalten. Werttragende Metadaten und Erklärungen werden nicht umgeschrieben.
4. Fachliche Tokens, ursprüngliche Klammern und Reihenfolgen bleiben erhalten. Formatierung ist kein Refactoring und benötigt keine aufgelösten Imports.
5. Standard: zwei Leerzeichen, 120 Zeichen weiche Zeilenbreite, vorhandene Zeilenenden soweit zuordenbar erhalten, genau ein abschliessender Zeilenumbruch. Die bisherigen drei FormatOptions-Konstruktorargumente bleiben verwendbar.
6. CLI und LSP verwenden denselben Kern. Der redundante LSP-`source.format`-Edit mit eigenen Defaultoptionen wird entfernt; Standard-`textDocument/formatting`/„Format Document“ bleibt erhalten. Kein zweiter Formatierweg mit abweichender Einrückung.
7. Das parallele Swing-/FlatLaf-GUI wird hier weder gebaut noch verändert. Es kann die kompatible öffentliche Formatter-API später verwenden. APE/Uhrpatch bleiben pausiert.
8. Standardstiländerungen werden an echten Dateien und Sollbeispielen begründet; eine hohe Diffzahl ist nicht automatisch ein Defekt, unklassifizierte oder inhaltliche Änderungen dagegen schon.

## Ergebnis und Ehrlichkeit

Dieses Paket ist eine Spezifikation, kein bereits ausgeführter Build. Referenzbeispiele sind Regeln bzw. Quelldaten, keine behaupteten bestandenen Tests. Der Implementierer muss die vollständigen Fixtures parsen und, wo erforderlich, semantisch prüfen. Keine neue Norminterpretation aus einem hübsch aussehenden Codeschnipsel ableiten.

Arbeitsbelege unter dem neuen Auftragsverzeichnis schlank halten (`STATUS.md`, `BASELINE.md`, `TEST_LEDGER.md`, `ACCEPTANCE.md`). Grosse Rohbelege und finale Reviewurteile ausserhalb des versionierten Produktbaums aufbewahren und über tatsächliche Hashes referenzieren. Die historischen B-, APE- und sonstigen Nachweise bleiben unverändert.
