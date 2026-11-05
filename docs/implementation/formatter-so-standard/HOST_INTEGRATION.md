# CLI, LSP, zukünftiges GUI und modulare Verbraucher

## 1. Eine gemeinsame API, keine zweite Formatierung

Der Kern bleibt `ModelFormatters.create().format(source, options, token)`. Hosts decodieren ihre Optionen, stellen Quelle und Cancellation bereit, behandeln Ergebnis/Notice und schreiben oder liefern Edits. Sie implementieren keine Kommentarnormalisierung, Klammerpolicy oder Nachformatierung.

Eine neue `maxLineWidth` ist additive Steuerung, kein neuer Stil. Vergleichstests sprechen von identischen **effektiven** Optionen, nicht von verschieden konfigurierten Benutzereinstellungen. CLI und GUI dürfen eigene UI-Defaults zeigen, müssen den tatsächlichen Optionswert aber offenlegen.

## 2. CLI-Verträge

Vorhandene `format`, `--check`, `--write`, `--indent`, `--line-ending`, `--diagnostic-language`, `--verbose` und Diagnose-/stdoutregeln erhalten. `CommandSpec.standard("format")` und den tatsächlichen `parseFormat`-Pfad um eine Werteoption erweitern:

```sh
interlis-compiler format --max-line-width 100 Model.ili
interlis-compiler format --check --max-line-width 120 Model.ili
interlis-compiler format --write --indent 2 --line-ending lf Model.ili
```

Dies ist das geplante neue Verhalten. `--max-line-width` ist nicht als schon vorhandene Option zu behaupten. Ganze Zahl40..240; fehlender, mehrfacher, negativer, nichtnumerischer oder zu grosser Wert liefert den bestehenden Usagefehler vor Lesen/Schreiben. `--check` und `--write` bleiben unvereinbar. Hilfe/Capabilities/Formatversionsquelle konsistent aktualisieren, keine neue allgemeine Konfigurationssprache.

`format` bleibt lokal und akzeptiert keine Repositoryauflösung. Fehlende importierte Modelle hindern die Syntaxformatierung nicht. Syntax-/lexikalische Fehler dagegen keine Erfolgsausgabe. stdout enthält bei normalem Formatieren nur Quelltext; Notices/Diagnosen auf stderr. ANSI nicht in Quelltext, JSON oder Datei einschleusen.

`--check` ändert nie Dateien, signalisiert echten Änderungsbedarf über die bestehende FormatCheck-/Exitzuordnung. Keinen neuen Exitwert erfinden. Nach erfolgreichem `--write` muss ein `--check` mit identischen Optionen bestehen. Geschützte Teile mit erklärter Notice sind nicht als Syntaxfehler zu deklarieren.

### Schreibsicherheit

Bestehenden `CliOutputWriter` und dessen erwarteten Originalinhalt weiterverwenden. Alle Eingaben wie bisher zunächst formatieren, bevor Writephase beginnt. Pro Datei atomarer Ersatz, Quellinhalt unmittelbar vor Ersatz prüfen. Bei mehreren Dateien keine atomare Gesamttransaktion behaupten: bereits geschriebene Artefakte korrekt melden, nächste fehlerhafte Datei unverändert lassen. Kein blindes Überschreiben inzwischen geänderter Dateien.

BOM-/Encodingvertrag am vorhandenen Loader prüfen. Wenn der Loader nur Text liefert, muss der Schreibadapter die tatsächlichen Originalbytes/Encodinginformationen behalten, statt einen BOM-verlustigen Textvergleich als Dateiintegrität zu behandeln. Minimale notwendige Hostkorrektur erlaubt; kein allgemeiner Encodingumbau.

CLI-Testfälle: einzelner und mehrere Inputs, Duplikate, Pfad mit Leerzeichen/Unicode, führender Bindestrich nach `--`, ungültige Quelle, fehlendes File, `--check` vorher/nachher, Writekonflikt, Cancellation, stabiler Noticekanal, unveränderte Originaldatei nach Fehler. Defekter Formatterinvariantentest muss den internen Fehlerpfad erreichen, nicht SOURCE_REJECTED vortäuschen.

## 3. LSP-Standardformatierung

`textDocument/formatting` bleibt der einzige vollständige LSP-Formatierzugang. Bestehenden Scheduler, Capture/HostView, Cancellation, readonly- und Versionsschutz verwenden; nicht parallel an deren Lifecycle vorbei schreiben.

Effektive Optionen:

- tabSize1..16 aus dem konkreten DocumentFormattingParams; keine globale letzte Editorbreite.
- Spaces sind der feste Hausstil. `insertSpaces=false` nicht still in Tabs umsetzen: klarer InvalidParams-Hinweis auf den Spaces-Stil; negative/positive Tests. Keine globale Änderung der Editorbenutzereinstellungen.
- EOL=PRESERVE und finalNewline=true entsprechend bisherigem Profil.
- maxLineWidth=120; optionales zusätzliches LSP-FormattingOptions-Feld `interlis.maxLineWidth` darf als typisierter Integer40..240 gelesen werden. Kein coercion von Strings/Floats oder ungeprüften JSONwerten. Ein Client ohne Zusatzfeld funktioniert unverändert.

Eine kleine `LspFormattingOptions`-Hilfsklasse ist zulässig:

```java
static FormatOptions decode(FormattingOptions options)
```

Die Standardoptionsdecodierung und Fehlerfälle zentral behandeln. Kein neues per-Workspace-Konfigurationssystem nur für die Breite. Eine spätere Extensioneinstellung kann dasselbe zusätzliche Feld setzen; sie ist kein Pflichtumbau dieses Auftrags.

### Edits und Fehlermeldungen

Unveränderter Text liefert leere Editliste. Vollständiger Dokumentersatz ist vorerst erlaubt; ein minimaler TextEdit-Diff ist kein neues Pflichtfeature. Range bleibt UTF-16-korrekt auch mit Emoji/CRLF. Ein Request auf einem inzwischen geänderten Dokument darf nicht unbesehen auf den neuen Stand angewendet werden; vorhandenen Request-/Generationsschutz prüfen und gezielt testen. LSP FormattingResponse trägt selbst keine Version wie WorkspaceEdit; deshalb nicht bloss auf ein versioniertes TextEdit hoffen.

Readonly-/Repositorytexte bleiben schreibgeschützt. Abbruch ist RequestCancelled, Syntaxablehnung ein sinnvoller Requestfehler, interne Formatterinvariantenfehler interner Fehler. Es wird nie eine Teil-Editliste als Erfolg geliefert. Notices über vorhandenen sicheren Log-/Diagnosekanal oder einen begrenzten Hinweis vermitteln, nicht als Prosa auf LSP-stdout. Keine Flut identischer Popups.

## 4. Bewusste Bereinigung: redundante Format-Code-Action

Im Ausgangsstand erzeugt `codeActionNow` unter `source.format` direkt einen vollständigen Edit mit `FormatOptions.defaults()`. Das umgeht die tatsächlichen Dokumentformatieroptionen. Da CodeActionParams die entsprechenden Editorpräferenzen nicht mitliefert, wird dieser redundante Editpfad entfernt.

„Format Document“ und der registrierte Standardformatierprovider bleiben. Andere Source-Actions, Quickfixes, CodeActionfilter, versionierte WorkspaceEdits und onTypeFormatting bleiben unverändert. Keine automatische Umleitung auf einen VS-Code-spezifischen Command im sprachneutralen LSP. Kein interner zweiter Formatter und kein globales Merken des zuletzt verwendeten tabSize als vermeintlicher Fix.

Aktive Dokumentation/Tests, die diese zusätzliche Action versprechen, präzise aktualisieren. Im Abschluss diese kleine UX-Änderung ausdrücklich nennen. Falls ein vorhandener Extensiontest die spezifische Action voraussetzt, das als gezielt betroffenen Verbraucher behandeln; keine pauschale Extensionmigration. Normalerweise genügt ein LSP-Prozesssmoke plus Standardproviderbeleg.

## 5. API-/GUI-Kompatibilität und Parallelbranche

Das parallele GUI ist nicht Teil des Auftrags. Dreierkonstruktor von FormatOptions und bisherige Formatteraufrufe bleiben; vierter Breitenwert kann später verwendet werden. Keine bestehenden GUIquellen kopieren, mergen oder an neue Ergebnisvarianten zwingen. Keine Swing-, JavaFX-, FlatLaf-, HTML-Diff- oder Webserver-Dependency hinzufügen.

FormatOutcome.Formatted enthält weiterhin Text und Notices, Rejected/Cancelled behalten ihren Vertrag. Ein GUI kann Vorher-/Nachher-Diff rein als Verbraucher darstellen. Das Formattermodul kennt keine Dateidialoge, Vorschauen oder UIzustände.

## 6. Jenesis und JPMS

Aktive Kommandos des geprüften Ausgangsstands:

```sh
java build/InterlisJvmBuild.java build --module interlis-compiler --tests '.*Formatter.*Test'
java build/InterlisJvmBuild.java build --module interlis-compiler-cli --tests '<passender vorhandener/neuer Regex>'
java build/InterlisJvmBuild.java build --module interlis-lsp --tests '<passender vorhandener/neuer Regex>'
java build/InterlisJvmBuild.java artifacts
```

Patterns vor Ausführung an realen Testklassen verifizieren; null entdeckte Tests sind kein PASS. Bestehende White-Box-Ausführung ist testseitig erlaubt, aber zusätzlich eine kleine benannte Consumeranwendung gegen unveränderte Produkt-JARs auf dem Module-Path ausführen. Sie muss nur `guru.interlis.format` und nötige öffentliche Quell-/Cancellationtypen verwenden, ohne `--add-exports` auf Interna oder `--patch-module` im Produktlauf.

Neuer Formattercode liegt im existierenden Modul. Keine neuen `opens`, kein Test-/Node-/Gui-Leak in der Runtimeclosure, keine zweite Classpathproduktvariante. Angepasste Fixtureinputs präzise in bestehende Fingerprints eintragen. Ein isolierter nichtfälliger jpackage-/Runtimebau ist keine Voraussetzung für reine Formatterlogik.

## 7. Portable Verbraucher und echte Erreichbarkeit

In F0 sämtliche FormatOptions-/ModelFormatter-Aufrufe in Core, CLI, LSP, Bridge, TeaVM, Web Image, Dokumentation und Tests inventarisieren. Nicht aus dem blossen Vorhandensein eines compiler.jar im Backendclasspath eine komplette Wasm-Testpflicht ableiten; umgekehrt einen tatsächlich erreichbaren Formatter nicht ignorieren.

Ist Formatter/API in einer portablen Laufstrecke wirklich erreichbar: bestehende Snapshotübergabe aus B verwenden, frische betroffene Produkt-JARs exportieren und eine kleine echte Probe (Kommentar/Constraint/Unicode/Idempotenz) in der betroffenen Runtime ausführen. Bei indirekter Quell-/APIänderung nur Compile-/Linkrisiko zuerst schmal prüfen; Laufpflicht nach tatsächlichem Vertrag. JS-Erfolg belegt kein WasmGC und WasmGC kein Web Image. Keine API-/Klassennamen per Reflection neu einführen, die diese Verbraucher unnötig belasten.

Ist der Formatter dort nicht erreichbar und keine gemeinsame genutzte API-/Syntaxänderung erfolgt: mit konkretem Aufruf-/Abhängigkeitsbefund `NOT_AFFECTED` dokumentieren. Keine vollständige Browser-/Safari-/Performance-/Releasekampagne. Aktive bestehende portable Pflichten nicht dauerhaft löschen; hier lediglich den notwendigen Änderungsumfang abgrenzen.

## 8. Echte Abschlussintegration

Mindestens: öffentliche SDKprobe, realer CLI-Prozess über aktuellen modularen Launcher und echter LSP-Prozess mit initialize/didOpen/formatting sowie einer Korrektur-/Abbruchgegenprobe. Kein voller VSIX-/GUI-Neubau, sofern nur Serververhalten und keine Extension-/Bundleverträge geändert werden. Bei tatsächlich notwendigen Extensionänderungen exakten zweiten Commit und Bundlebindung in Review einbeziehen; nicht alte Extensiontests als Beweis neuer Bytes ausgeben.
