# Ausführungsplan — C0 bis C5, danach R

## Grundregel

Codex implementiert und testet jede Phase. Erst das erfolgreiche jeweils definierte Gate öffnet die nächste implementierende Phase. Der unabhängige fachliche Gesamt-Review erfolgt nur am Ende. Ein früher technischer OpenCode-/Auth-/Modellcheck ist erlaubt und sinnvoll.

Normale Route: **C0 → C1 → C2 → C3 → C4 → C5 → R**. Ein terminaler Blocker darf nur nach Abschnitt 8 behandelt werden. Das ist kein Ersatz für behebbar fehlerhaften Code.

## C0 — Baseline, exaktes Vendorziel und Prüfplan

**Kriterien:** CP-01–03, CP-09–11, CP-30; Reviewpreflight für CP-32.

### Arbeit

1. Arbeitszustand, Branch, BASE_SHA/Tree und fremde Änderungen erfassen. Relevante APE-Berichte und bestehende Quellen lesen. Original-`NO_GO` und der inzwischen gesetzte Poll-Buildparameter sind nicht dasselbe wie ein bereits erfolgreich neu gebauter Compiler.
2. Vendorlock/Archive/Installationsinventar mit der bestehenden Verifikation prüfen. Bestehende lokale Inputs nutzen, keine erneuten Downloads bei korrektem Pin.
3. Tatsächlich verwendetes Builder-JAR und Ziel-Classfile finden. Klasse/Methoden/Annotationen/Module/Deaktivierungsoption aus dem gepinnten Binary untersuchen. Sourcevergleich mit dem öffentlichen Code nur ergänzend.
4. Erwartete Original-Classfile-/JAR-Identität als Ergebnis dieser Inspektion vorbereiten. Noch keinen beliebigen aktuellen Hash automatisch als dauerhafte Policy vertrauen.
5. Original-Clock-/HTTP-Belege auffinden und deren tatsächliche Input-/Binary-/Hostbindung prüfen. Falls nicht belastbar erreichbar, nur die kleinste Original-Clockprobe neu erzeugen/ausführen. Keine volle Compilerwiederholung.
6. Vorhandene Zielhosts inventarisieren. Poll- und TLS-Testfixtures sowie zeitliche Gültigkeit prüfen. Native/emulierte/verfügbare/nicht verfügbare Umgebung trennen.
7. OpenCode 2.x und direkte DeepSeek-1017-Zuordnung im vorgesehenen Reviewkontext technisch prüfen. Authproblem früh lösen, noch keinen Code-Review starten.

### Gate

- Originalinput ist eindeutig gepinnt, Zielmethode im echten Builder nachgewiesen und der bekannte Defekt reproduziert oder mit originalen gebundenen Laufbelegen belastbar bestätigt.
- Der methodengenaue A-Weg ist technisch untersuchbar; bei fehlender Option besteht ein präziser Befund, keine nur vermutete Versionseinschränkung.
- Produktinputs sind die echten unveränderten Jenesis-JARs oder deren ordnungsgemässe Regenerierung ist eingeplant.
- Kleiner Prüfplan benennt die Frage jedes nächsten Builds, relevante Limits und verfügbare Plattformen.

**Outputs:** kurze Baseline in STATUS, Binärinspektion, erwartete Pins, Evidenceindex und erstes Testledger. Bei bereits korrigiertem oder unbekanntem Vendor nicht den alten Patch blind anwenden; Befund klären und Scopegrenze dokumentieren.

## C1 — Genau eine lokale Korrektur und erste native Clock-Abnahme

**Kriterien:** CP-04–10, CP-15–17.

### Arbeit

1. `InspectCosmoClock` und Patchdefinition nach den tatsächlichen APIs umsetzen; Guards zuerst testen.
2. Route A: enges Substitutions-JAR mit gezielter Methodendeaktivierung erstellen. Compile-only Vendorclasspath und tatsächliche Substitutionserkennung erproben. Keine ganzen Builder-JARs in die Produktclosure kopieren.
3. Route A mit einer kleinen nativen `RuntimeProbe` auf der verfügbaren Hauptarchitektur prüfen. Quelle kann bereits Clock/Selector/HTTP/Zertifikatmodi enthalten; diese werden später mit demselben Binary genutzt.
4. Bei einem A-Fehler zuerst dessen Ursache bestimmen. Eng zulässige Import-/API-/Discoverykorrektur durchführen. Ist der Ansatz am tatsächlichen Vendor nicht sicher möglich, Entscheidung dokumentieren und ausschliesslich B implementieren.
5. Route B: isolierte Ableitung, strukturierter Delta- und Inventarvergleich, anschließend dieselbe native Clockprobe. Keine A-Disable-Option zusätzlich verwenden.
6. Gewählten Weg in `patch.json` festlegen; nicht verwendete aktive Alternativquellen entfernen. Fehlversuchslogs erhalten.

### Gate

- Ein tatsächlich ausgeführtes korrigiertes Native-Probeprogramm besteht RT-01–RT-04 auf einem verfügbaren echten Host.
- Die alte Probe scheitert aus der erwarteten Clock-Ursache oder deren unveränderte Originalbelege sind verifiziert; kein Fixturetrick.
- `nanoTime` und übrige Zielklassenmitglieder sind strukturell unverändert; keine Builderprüfung abgeschaltet.
- Guardtests für falschen Pin, bereits korrigierte Eingaben, zu breite Deaktivierung und doppelte Patchanwendung bestehen.
- Genau ein dokumentierter aktiver Weg und vollständige Original-/Erzeugnisidentität.

**Noch nicht behaupten:** vollständige HTTPS-Funktion, beide Architekturen oder Compilerfreigabe.

## C2 — Saubere Einbindung in APE-Build und Artefaktbindung

**Kriterien:** CP-10–15, CP-26–28.

### Arbeit

1. `compat.cjs`/Receiptvalidierung finalisieren; Erzeugung nur nach erfolgreichem Prozess und vollständig geprüftem Output veröffentlichen.
2. `CompilerApe.parse/run` um COMPAT und ausdrückliche `--compat`-Referenz bei BUILD/FUSE erweitern. Lockreihenfolge erhalten. Keine implizite Patchherstellung in normalen Profilen.
3. `compiler-native.cjs` um getrennte Kompatibilitätsinputs, effective vendor bei B und gemeinsame Digests erweitern. `inputs()` und die sieben Produktbibliotheken unverändert lassen.
4. Sliceproduzent, Seal, `readSlice` und Fusion anpassen. Falscher oder vermischter Patch wird vor teurem Link/Build abgelehnt. Bundleherkunft beschreibt lokale Runtimeänderung.
5. Mini-Probe und Compiler verwenden dieselbe verifizierte Compatibilitybeschreibung. Keine duplizierten Listen mit abweichenden Disable-/Pollflags.
6. Bestehende Recorder-/Timeout-/Containerroutinen korrekt nutzen; bei Bedarf nur konkrete Adapterfehler beseitigen.

### Gate

- Betroffene Node-/Java-Argumentparser- und Manifesttests bestehen.
- M01–M07: fehlender/gefälschter/veralteter Patch, erneutes Hashen ohne passende Provenienz, falscher Strategy-/Sliceinput und zusätzliche Produktdateien werden abgelehnt.
- Eine normale kleine JVM-/`ape inspect`-Probe bleibt ohne Native-Toolchainanforderung erreichbar.
- Keine Produkt-/Originalvendoränderung; begrenzte Quell-/Taskgraphprüfung bestätigt optionale Ausführung.
- Kein grosser Native-Compilerbuild für reine Argument-/Digesttests.

Wenn C2 nur Receipt-/Orchestrierungslogik ändert, C1-Runtimebeleg nicht reflexartig neu erzeugen. Nachweisen, ob Patchbytes und effektive Native-Rezeptinputs unverändert sind. Ein geändertes Runtimeartefakt verlangt dagegen neue Proben.

## C3 — Clock, Selector und sicheres HTTP/TLS als kleines Qualifikationsgate

**Kriterien:** CP-15–20, CP-24, CP-27.

### Arbeit

1. Vollständiges kleines Runtimeprofil RT-01–RT-11 aus TEST_MATRIX auf dem vorhandenen korrigierten Probe-Binary durchführen. Nur nach Quellen-/Rezeptänderung neu bauen.
2. Gültiges und falsches Zeitprofil der Leafzertifikate unabhängig prüfen. Fehlende expired/future-Fixtures gezielt ergänzen, nicht die Hostuhr ändern.
3. Echte Providerwahl, HTTP200/Body, TLS positiv, untrusted/hostname/expired/future negativ, anschliessend positive TLS-Kontrolle.
4. Testtrust nur zur Laufzeit. Standardtrust und einen kleinen öffentlichen HTTPS-Zugriff getrennt beurteilen.
5. Zweite Architektur der **gleichen** Probeinputs erzeugen. Auf verfügbarer nativer Plattform prüfen; sonst klar als Emulationsdiagnose. Weitere bereits zugängliche native Zielhosts für das kleine Runtimeprofil nutzen.
6. Neue Fehler minimieren; keine weiteren Runtimepatches ohne Auftrag.

### Gate

- Clock- und lokale positive/negative TLS-Kontrollen bestehen auf dem primären echten Host.
- Beide Probe-Architekturen sind mit derselben Korrektur gebaut; mindestens reale Ausführung je zugänglichem Ausführungsweg. Emulation ist markiert und kein Plattform-PASS.
- Pflichtkontrollen sind nicht nur generische Verbindungsfehler; Ursache und Serverbeobachtung passen.
- Keine Test-CA im Standardimage; öffentliche Netzwerkhindernisse sind getrennt von lokalen TLS-Ergebnissen erfasst.

Erst jetzt grosse Compilerbilder erzeugen. Ein für alle erreichbaren Hosts bewiesener weiter bestehender Runtimefehler blockiert C4. Fehlende zusätzliche native Runner alleine verhindern keine sinnvolle lokale Compilerintegration, aber später ein vollständiges GO.

## C4 — Neue Compilerbilder, Fusion und gezielte Produktabnahme

**Kriterien:** CP-11, CP-13–15, CP-21–27.

### Arbeit

1. Vorhandene Jenesis-Inputmanifeste auf tatsächliche Produktbytes und gültige Quellbindung prüfen. Orchestratoränderungen können das bisherige Inputmanifest ungültig machen; explizit neu vorbereiten statt Guard entfernen. Identische JAR-Bytes dürfen wiederverwendet werden.
2. Genau ein neues erfolgreiches x86-64- und ein ARM64-Compilerbild pro relevanter endgültiger Konfiguration bauen. Keine Optimierungsexperimente. Abbrüche korrekt behandeln und nicht als gültige Bilder verwenden.
3. Unveränderte Originalslice-Dateien auf Kopien fixup/fusionieren. Patchdigest, Runtimeclosure, Vendor-/CAP-/Trustquelle und Rezept identisch.
4. Auf endgültiger APE-Datei die bestehende kleine CLI-/Formattermatrix und N-Netzwerkmatrix prüfen. Zusätzliche Zeit-TLS-Fälle aufnehmen, soweit schon im Minimalharness abgenommen; keine Produktdiagnosen umbauen.
5. Gleiche Datei auf allen legitim verfügbaren nativen Pflichtzielen gezielt prüfen. Nicht verfügbare Ziele mit konkretem Infrastrukturgrund offen lassen. Keine Wiederholung der gesamten Korpus-/Benchmark-/Browsermatrix.
6. Relokation, vorhandenen Loaderstart und Ausführung ohne erreichbare JVM/Builder verifizieren. Keine neue Loadertechnik.
7. Transport-/Extraktionsinventar, Mutations- und gemischte-Slice-Gegenproben auf Scratchkopien, nicht Originaldateien durchführen.

### Gate

- Beide neuen Compilerbilder und finale Fusion sind echt und passend gebunden.
- Primärer echter Host besteht die relevanten kleinen Produkt-/Netzwerkfälle; keine versteckten semantischen Abweichungen.
- Endartefaktidentität, Patchherkunft und tatsächliche Plattformmatrix sind vollständig.
- Fehlende externe Pflichtzielbelege bleiben offen. Das lokale Integrationsgate ist dadurch nicht „universell bestanden“; der spätere Gesamtstatus ist entsprechend INCONCLUSIVE.

Tritt ein neuer Scope-externer Runtimeblocker auf, nicht durch weitere lokale JDK-/Cosmo-Fixes ausweiten; minimieren und terminalen Abschluss nehmen.

## C5 — Bereinigung, Entfernungspfad und geprüfter Commit

**Kriterien:** CP-01, CP-12, CP-24–31; CP-32 vorbereiten.

### Arbeit

1. Genau ein aktiver Patchweg; keine alternativen Disable-/Classpath-/JAR-Overlayversuche, keine toten Flags. Bewusst historische Reproducer/Logs bleiben nachvollziehbar erhalten.
2. Aktuelle README/APE-Aufrufe und Ergebnisstatus anpassen. Alte Feasibility-Rohbelege und ursprüngliche NO_GO-Entscheidung nicht überschreiben. Ein kurzer datierter Hinweis kann auf diesen Folgeauftrag verweisen.
3. Entfernungsvorgehen bei korrigiertem Vendor dokumentieren. „Already fixed“ und neuer Vendorpin werden fail-closed erkannt.
4. STATUS, TEST_LEDGER und RESULT mit getrennten Patch-/Compiler-/Plattformstatus abschliessen. Nur fällige betroffene Nachprüfungen, kein ritueller Full-Check.
5. `git diff --check`, Stagingumfang, Fremdänderungen, Secrets und erzeugte Binaries kontrollieren. Erforderliche Patchquellen/Specs/Tests/enge Adapteränderungen lokal committen.
6. Candidate-SHA, Tree, kumulativen Diff seit BASE_SHA und gehashte Originalbelege in das externe Reviewpaket legen.

Mögliche Commitmessage: `build: isolate CosmoGVM wall-clock compatibility patch for compiler APE`.

### Gate

Keine offenen behebbaren Implementierungsfehler innerhalb des erklärten Ergebnisses. Die echte Beleglage trägt den Status. Candidate ist committet und unverändert. Es wurde nicht gepusht oder veröffentlicht. Keine nachträgliche Erfindung eines sauberen Commitlaufes: vor Commit erstellte Testreports behalten ihre damalige Herkunft, getestete Produktbytes werden nachvollziehbar dem Commit zugeordnet.

## R — unabhängiger Gesamt-Review

Nach OPENCODE_REVIEW_PROMPT ausführen. Der Reviewer prüft gesamten kumulativen Diff, CP-01–32 und Originalbelege. Mindestens eigene Guard-/Mutationsprobe, eigene Clockausführung auf zugänglichem Kandidaten und Prüfung des positiven/negativen TLS-Zusammenhangs. Keine schematische doppelte Compiler-Native-Erzeugung.

Bei Findings: Codex korrigiert, schreibt den Änderungswirkungsplan, führt die nötigen Retests aus, committet den neuen Kandidaten und startet eine neue unabhängige Sitzung. Historische Reviewurteile bleiben erhalten. Nach finalem PASS keine ungeprüften „kleinen Abschlussänderungen“ mehr.

## 8. Terminaler Blockerabschluss

Erlaubt nur für einen belegten technischen Scopeblocker, nicht für einen reparierbaren Adapter-/Testfehler. Voraussetzungen:

- konkrete Reproduktion und tatsächliche Inputs;
- begrenzte sachgerechte Ursachenuntersuchung, kein zufälliges Flagprobieren;
- klare Trennung von Defekt, fehlender Infrastruktur und fehlender Berechtigung;
- keine weitere unbeauftragte Runtimeänderung;
- abhängige nicht ausgeführte Fälle als `NOT_RUN_BLOCKED`;
- geprüftes, ehrliches Ergebnisdokument und Kandidatencommit;
- danach unabhängiger Gesamt-Review des bis dahin tatsächlich Erreichten.

Nicht bestandene Phasen werden nicht rückwirkend zu PASS. Der finale Bericht kann eine saubere, aber negative Untersuchung bescheinigen. Ein lokaler Laufpatcherfolg bei fehlender anderer Zielplattform ist anders einzuordnen als ein weiterhin kaputtes Clock-/TLS-Minimalprogramm.
