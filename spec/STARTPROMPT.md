# Startauftrag für einen LLM-Coding-Agenten

Den folgenden Auftrag zusammen mit diesem Spezifikationspaket im vorgesehenen Repository verwenden. Bei Übernahme liegen die Kapitel unter `docs/spec/` und die Referenzevidenz unter `docs/evidence/`; die beiden Arbeitsregeldateien liegen als `AGENTS.md` im Root und unter `docs/spec/`.

**Aktivierung:** Der Text unter der Trennlinie ist eine Vorlage für einen ausdrücklichen Implementierungsauftrag. Das Lesen oder Überarbeiten dieser Datei allein startet P0/P1 nicht. Bei einem anderen aktuellen Nutzerauftrag bleibt dessen Umfang massgeblich.

---

Implementiere den ersten Entwicklungsabschnitt des Projekts **interlis-java** nach der Spezifikation unter `docs/spec/`. Das Gesamtziel ist ein moderner Java-INTERLIS-Stack mit den Fähigkeiten von ili2c, iox-ili, ilivalidator und ili2db. Der aktuelle Implementierungsauftrag umfasst **P0 und P1 vollständig**. Die Zielanforderungen von P2 bis P5 bleiben erhalten und dürfen nicht aus der Spezifikation entfernt oder durch unbelegte Erfolgsbehauptungen als erfüllt markiert werden.

Lies zuerst vorhandene Repository-Arbeitsregeln, dann `docs/spec/README.md`, `00-auftrag-und-agentenregeln.md`, `01-zielbild-und-anforderungen.md`, `02-architektur-und-modulgrenzen.md`, `03-build-und-repository.md`, `16-api-register-und-vertragstests.md` und den P0/P1-Teil von `14-implementierungsplan-und-arbeitspakete.md`. Lies danach für jeden konkreten Schritt die betroffenen Fachkapitel. Halte den aktuellen Stand im Repository fest, damit ein anderer Agent ohne Chatkontext fortsetzen kann.

Verbindliche Entscheidungen:

- Java 25 ohne Preview, Paketbasis `guru.interlis`.
- Gradle-Multiprojektbuild mit Groovy-DSL und gepinntem Wrapper. Kein Maven-Zweitbuild. Ant/Ivy wird nicht zusätzlich eingerichtet.
- Nur INTERLIS 2.3 und 2.4.
- ANTLR 4 intern; eigene öffentliche Syntax-/Metamodelltypen.
- Gemeinsame Engine für Batch, IDE und portable Ausführung.
- Unveränderliche veröffentlichte Snapshots, explizite Revisionen und Gültigkeitsgate.
- Strukturierte Diagnosen und Laufkontexte ab dem ersten öffentlichen Lauf.
- TeaVM und echter Web-Image-Untersuchungsauftrag gemäss Kapitel 10.
- LSP-/Diagramm-/Browserfähigkeit entsteht bereits im vertikalen P1-Abschnitt.

Beginne mit einer Bestandsaufnahme und einem kurzen ausführbaren Arbeitsplan. Implementiere anschliessend WP-00 bis WP-03 und den P1-Abschnitt WP-10 bis WP-17 in den angegebenen Abhängigkeiten. Kleine private Hilfsklassen darfst du selbst sinnvoll zuschneiden. Halte gemeinsame API-Namen und Verträge konsistent; lege keine leeren Zukunftsmodule und kein eigenes Framework ohne aktuellen Bedarf an.

Die erste semantische Strecke muss mehrere Modelldateien, Import, Domänen, Klassenvererbung, Attribute/Rollen und mindestens einen typisierten Constraint abdecken. Validierte Referenzfixtures und gezielte Gegenbeispiele sind Pflicht. Unbekannte oder nicht implementierte Sprachkonstrukte dürfen niemals still übersprungen werden. Aus bloss erfolgreichem Parsen darf kein gültiges CompiledSchema entstehen. ErrorType, noch nicht analysierte Zustände, UNDEFINED und legal aufgeschobene 2.4-Typbindungen bleiben getrennt.

Verwende die beigefügte Syntax-WASM-Probe als reproduzierbare Ausgangsevidenz. Überführe ihren aktiven Ablauf in Gradle. Behaupte keine vollständige Semantik, Browserkompatibilität oder Graal-Erfolge aufgrund dieser kleinen Probe. Führe die in Kapitel 10 vorgeschriebenen tatsächlichen Zielplattform- und Browserprüfungen aus. Ein dokumentierter technischer Web-Image-Blocker wird konkret untersucht und berichtet; fehlende Pflichtprüfungen halten ihr Gate offen.

Für jedes Arbeitspaket:

1. Benenne die bearbeiteten Anforderungs-IDs und die erwarteten Beobachtungen.
2. Implementiere einen vollständigen fachlichen Abschnitt einschliesslich Fehlerpfad, öffentlichem Zugriff und relevanten Adaptern.
3. Führe die zugehörigen aussagekräftigen Prüfungen aus und protokolliere genaue Kommandos, Versionen, Umfang und Ergebnisse.
4. Prüfe die Konsistenz mit Metamodell, Diagnostik, Workspace, WASM und späteren Verbraucheranforderungen.
5. Aktualisiere API-Register, Status und Nachweise, soweit betroffen.
6. Fahre mit dem nächsten vollständigen Abschnitt fort, solange der Auftrag nicht abgeschlossen oder konkret blockiert ist.

Keine Testbaseline wird nur deshalb geändert, weil die neue Implementierung abweicht. Untersuche Normregel, Referenzverhalten und ursprüngliche Testabsicht. Halte bekannte Abweichungen explizit fest. Vergleiche nach Editierfolgen die wiederverwendende mit einer frischen Analyse. Unterscheide Compilerfehler, Quellfehler, fehlende Implementierung, Resolverprobleme, Warnungsablehnung und Abbruch bis zu CLI/Logs/Browser.

Bei einer routinemässigen Implementierungsentscheidung arbeite mit der einfachsten vertragskonformen Lösung weiter. Bereite echte Scope-/Kompatibilitätsentscheidungen mit konkreten Auswirkungen und Empfehlung vor und erledige unabhängige Arbeit weiter. Veröffentliche keine Releases und führe keine Produktionsmigration durch, sofern das nicht zusätzlich beauftragt ist.

Am Ende des P0/P1-Auftrags liefere einen nachvollziehbaren Status: erfüllte Anforderungen, Build-/Startanleitung, Test-/Plattformmatrix, verwendete Pins, tatsächlich gemessene Werte, offene Gates und den nächsten P2-Abschnitt. Ein grüner Parserlauf, eine Demo mit fest verdrahteter Ausgabe oder angelegte Klassen allein schliessen diesen Auftrag nicht ab.
