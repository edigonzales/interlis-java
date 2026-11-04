# Startprompt für Codex

Du arbeitest im lokalen Repository `edigonzales/interlis-java`. Implementiere den folgenden Auftrag vollständig. Eine weitere Spezifikation oder ein unverbindlicher Plan allein ist nicht das Ergebnis.

Lies zuerst diese Auftragsdateien vollständig:

1. `docs/implementation/verification-module/SPECIFICATION.md`
2. `docs/implementation/verification-module/EVIDENCE_DISPOSITION.md`
3. `docs/implementation/verification-module/EXECUTION_PLAN.md`
4. `docs/implementation/verification-module/OPENCODE_REVIEW_PROMPT.md`

Lies die geltenden `AGENTS.md`-Dateien und die tatsächlich betroffenen aktuellen Build-/Qualitätsverträge. Behandle die ausdrücklichen Entscheidungen dieses Nutzerauftrags als verbindlich. Das Lesen anderer alter Pläne löst keinen weiteren Implementierungsauftrag aus.

## Auftrag

Migriere die zusätzlichen CLI-Source-Sets `src/verification`, `src/verificationTest` und `src/evidenceTest` in eine saubere Organisation mit einem internen `interlis-verification`-Modul. Verwende dort nur `src/main` und `src/test`. Gradle bleibt unverändert das Buildsystem. **Keine Jenesis-Migration, keine JPMS-Umstellung, keine Versionsupgrades.**

Verschiebe nicht einfach alle Dateien. Prüfe besonders methodengenau, welche bisherigen Evidenzprüfungen einen aktuellen Schutzvertrag erfüllen, welche besser normale Produkt-/Validator-Tests sind, welche echte Doppelungen darstellen und welche nur einen historischen G2-Arbeitsstand einfrieren. Begründe jede Entfernung und erhalte alle historischen Originalbelege unverändert.

Ordne direkte Compilerregressionen fachlich korrekt zu. Insbesondere gehört `UnicodeWeightsTest` ins Compiler-Testmodul; seine interne Compilerklasse wird nicht öffentlich gemacht. Gemischte Compiler-/CLI-/Prüferverträge bleiben mit ausdrücklich deklarierten Dependencies im neuen Verifikationsmodul.

Erhalte wirksame aktuelle Integritäts-, Coverage-, Normentscheidungs-, Prozess- und Negativprüfungen. Kein Abschwächen von Assertions, kein künstliches Herabsetzen von Mindestzahlen, keine historischen Hash-Neuschreibungen und keine blossen PASS-Platzhalter.

## Ablauf

Arbeite V0 → V1 → V2 → V3 → V4. Jede Phase muss mit den für sie notwendigen gezielten Tests erfolgreich abgeschlossen sein, bevor du die nächste beginnst. Dokumentiere echte Ergebnisse und den begründeten Testumfang.

**Es gibt keinen unabhängigen Review nach jeder Phase.** Erst nachdem alle Implementierungsphasen erfolgreich abgeschlossen und der Kandidat committet ist, startest du selbständig den unabhängigen Gesamt-Review mittels OpenCode 2.x und DeepSeek Flash 1017 über die direkte DeepSeek-API.

Bei berechtigten Reviewerfindings korrigierst du selbst, testest die betroffenen Verträge, committest einen neuen Kandidaten und startest einen frischen unabhängigen Gesamt-Review. Wiederhole bis zum gültigen PASS oder einem konkret belegten nicht selbst behebbaren Blocker. Ein alter PASS gilt nicht für einen neuen Commit.

## Testökonomie und Nachweise

Nur notwendige aussagekräftige Tests ausführen. Das neue Verifikationsmodul, verschobene Produktregressionen, Task-/Reportintegration und kleine echte CLI-/SDK-Prozesse sind relevant. Vollständige Korpus-, Browser-, Web-Image-, Plattform- und Benchmarkläufe sind nicht automatisch nötig.

Codex und OpenCode führen grosse Läufe nicht doppelt aus. Der Reviewer muss Code und Originalbelege selbst untersuchen und eigene gezielte Gegenproben ausführen. Ein Dry-run beweist den Taskgraph, nicht die Laufzeit. Ein Fixturetest beweist den Validator, nicht eine tatsächlich gelaufene Browsermatrix.

Nachweise werden an echte Inputs, Dateien, Versionen und Ausführungen gebunden. Keine vor dem Commit entstandenen Berichte nachträglich auf den neuen SHA umetikettieren. Für identische relevante Produktinhalte Wiederverwendung mit offener Herkunft dokumentieren.

## Git und Autonomie

Erhalte fremde Änderungen; kein Reset, ungezieltes Clean, blindes Staging, Push oder Release. Mache normale technische Entscheidungen selbst. Warte nicht nach jeder Phase auf Benutzerfreigabe.

Prüfe den OpenCode-Zugang frühzeitig, aber starte den fachlichen Review erst nach V4. Verwende vorhandene Authentifizierung. Keine zusätzliche API-Key-Umgebungsvariable verlangen, wenn die vorhandene Anmeldung funktioniert. Prüfe die echte Modellzuordnung; ein generischer Flash-Alias genügt nicht ohne nachvollziehbare Bindung an 1017.

Der Reviewer bekommt einen separaten unveränderlichen Kandidaten, den kumulativen Diff und ursprüngliche Rohbelege. Er darf Produktcode nicht ändern und muss `review.md` sowie `verdict.json` liefern. Codex validiert Prozessabschluss, Modellbezug, Kandidaten-/Tree-SHA, vollständige Kriterienbewertung und eigene Gegenproben. Eine erfolgreiche OpenCode-Prozessausführung allein ist kein Review-PASS.

Halte Reviewartefakte extern, damit ein Abschlussbericht nicht einen neuen ungeprüften Commit erzeugt. Nach PASS keine stillen Änderungen mehr.

## Abschluss

Berichte die wesentlichen Änderungen, die begründeten Evidenzstilllegungen/Ersetzungen, ausgeführte Tests, bewusst nicht fällige Prüfungen, tatsächlichen finalen Commit, OpenCode-/DeepSeek-Modellbezug sowie die externen Pfade des unabhängigen Urteils. Benenne echte Einschränkungen. Erfolg nur bei erfüllten VM-01 bis VM-18.

Beginne mit V0 und führe den Auftrag bis zum geprüften, committeten und unabhängig validierten Ergebnis aus.
