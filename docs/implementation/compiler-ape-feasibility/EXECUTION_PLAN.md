# Ausführungsplan — F0 bis F5, danach unabhängiger Review

## Allgemeine Phasenregel

Codex implementiert und prüft jede Phase. Erst bei erfolgreichem **jeweiligem Gate** beginnt die nächste implementierende Phase. Keine fachlichen unabhängigen Reviews nach jeder Phase. Technischer OpenCode-/Modellpreflight darf früh erfolgen; der unabhängige Gesamt-Review folgt erst auf den committeten Abschlusskandidaten.

Die normale erfolgreiche Route ist F0 → F1 → F2 → F3 → F4 → F5 → R. Der negative terminale Weg ist in Abschnitt 7 definiert. Er darf nicht genutzt werden, um einen gewöhnlichen behebbaren Testfehler oder notwendige Programmierarbeit zu umgehen.

## F0 — Baseline, Artefaktschnittstelle, wirklicher Umfang

**Kriterien:** AP-01–04, AP-20–23, AP-27–28, AP-31.

### Arbeit

1. Geltende AGENTS, diesen Auftrag und reale A-Implementierung lesen. `BASE_SHA`, Tree, lokale Fremdänderungen und Produktversionen erfassen.
2. A-Abschlussbelege suchen, soweit lokal erreichbar; eingecheckten Status und externe Reviewbelege unterscheiden. Keine Wiederholung aller A-Tests. Relevante offene Defekte transparent notieren.
3. Compilerclosure über vorhandene Jenesis-Ergebnisse untersuchen. Fehlende/veraltete Artefakte durch den vorhandenen Erzeugungspfad neu herstellen; keine Fallbacks auf Gradle.
4. Hauptklasse, Versions-/Ressourcen-/Servicepfade, Hostzugriffe, vorhandene CLI-/Repositoryfixtures und Launcher prüfen.
5. Tatsächliche Linux-x64-Builder- und Zielplattformmöglichkeiten inventarisieren. Keine gewünschte Plattform als vorhanden voraussetzen. Quotas, Sandbox und benötigte Downloads benennen.
6. OpenCode2, vorhandene Anmeldung und Modellzuordnung technisch prüfen, noch kein fachlicher Review.
7. Den optionalen `ape`-Namensraum und sichere Quell-/Ausgabeorte minimal einführen. Keine Änderungen an Defaultprofilen.

### Gezieltes Gate

- `inspect` liest nur und meldet tatsächliche Daten; unbekannte/fehlende Argumente scheitern.
- Runtimeclosure vollständig, geordnet, gehasht; Tool-/Test-/LSP-Leaks werden in einer Fixturegegenprobe zurückgewiesen.
- Ein realer JVM-Referenzlauf mit `version --json`, gültigem kleinem Modell und einem ungültigen Modell über genau diese JARs.
- Eine gefälschte/veraltete Eingabeliste oder veränderte JAR wird abgelehnt.
- Bestehender Buildeinstieg weiter benutzbar; kleine Parser-/Inputprobe, kein gesamtes `check`.

**Outputs:** `BASELINE.md`, erste `STATUS.md`, Runtime-/Inputinventar und Originalreferenzlogs. Fehlende externe Zielrunner sind ein offener F4-Nachweis, kein Grund, F1/F2 nicht auf vorhandener Umgebung zu versuchen.

## F1 — Gepinnte Toolchain und minimale Java-APE-Probe

**Kriterien:** AP-05–10, AP-16, AP-18, AP-21–22, AP-25, AP-28.

### Arbeit

1. Referenzpins anhand der Versionquellen bestätigen, konkrete `toolchain.lock.json` erzeugen.
2. Explizit freigegebene Archive isoliert beziehen, Hash/Archivinhalt/Version prüfen. Keine globalen Installer ausführen.
3. Linux-x64-Builder einrichten oder bereits vorhandenen nutzen; Emulation dokumentieren.
4. `ToolchainProbe.java` erstellen: kleine Java25-Konstrukte, UTF-8-Text, eine eingebettete Ressource, Datei lesen/schreiben, echter Kindthread und kontrollierte Ausnahme. Keine INTERLIS-Produktklassen in die Probe hineinziehen.
5. Minimale CosmoGVM-Aufrufe für x86-64 und ARM64 testen. Rohargumente und tatsächlich benötigte Flags festhalten.
6. Wenn beide Bilder erzeugbar sind, bereits an der Probe `fixupobj`/`apelink` erproben. Auf dem verfügbaren Host wirklich starten; nicht verfügbare Ziele nicht behaupten.

### Gezieltes Gate

- Positiver verifizierter Toolchainaufbau; Hashmanipulation und fehlende Werkzeuge fail-closed.
- Mindestens auf der verfügbaren tatsächlichen Laufzeitumgebung eine real ausgeführte Probe mit überprüfter Ressource/UTF-8/Datei-/Threadausgabe.
- Beide Architekturpläne sind real verarbeitet, oder ein konkret reproduzierbarer Technikblocker ist minimiert. Ein erfolgreiches x86-64-Probeprogramm alleine ist kein vollständiger positiver F1-Abschluss für den Universalweg.
- Probe startet ohne Java-/GraalVM-Fallback; fehlendes Hostziel bleibt separat offen.

**Outputs:** `TOOLCHAIN_REPORT.md`, Lock, Probequellen und gehashte Rohbelege. Bei technischem Scheitern nach begrenzter Untersuchung terminaler F5-Zweig; kein Toolchainfork als Ausweg.

## F2 — Echter Compiler als Cosmo-Native-Image und kombiniertes APE

**Kriterien:** AP-02–04, AP-08–13, AP-16, AP-19, AP-21, AP-27, AP-32.

### Arbeit

1. Compilerinputs einmal unveränderlich vorbereiten; JVM-Referenz aus exakt denselben Bytes festhalten.
2. Compiler zunächst mit minimaler belegter Konfiguration auf x86-64 bauen. Fehlende Ressourcen/Reflection/Services gezielt nachweisen und konfigurieren.
3. Keine Madura-javac-Metadaten kopieren; kein LSP-/Testclasspath. Notwendige Anwendungskorrektur nur nach enger Regel in der Spezifikation.
4. Den kleinen Kernsmoke K01–K05 und F01–F03 gegen das tatsächlich erzeugte Zwischenbild ausführen, soweit dessen Host ausführbar ist.
5. Dasselbe Inputinventar für ARM64 verwenden; nur architekturspezifische Toolpfade/Optionen ändern.
6. Auf Kopien fixup ausführen, beide Bilder fusionieren, finalen Kandidaten einfrieren. Den Kernsmoke auf dem verfügbaren Host nochmals auf **der fusionierten Datei** ausführen.

### Gezieltes Gate

- Zwei echte Compilerbilder und deren Fusion ohne heimlichen JVM-Fallback.
- Tatsächliche Ausgaben von `version`, `capabilities`, `check` gültig/ungültig und `format` stimmen in den definierten Beobachtungen mit der JVM überein.
- Keine private Buildumgebung/Secrets im Runtimeverhalten; notwendige Metadaten erklärt.
- Fremdes oder geändertes Architekturinput wird vor Fusion abgelehnt; endgültige Binary nach Test unverändert.

**Outputs:** `compiler-inputs.json`, Buildpläne, Native-Logs, beide Slice- und Universalhashes, Kandidatenmanifest. Noch kein vollständiger Portabilitätsclaim.

## F3 — Funktionale CLI-, Netzwerk- und Dateivertragsparität

**Kriterien:** AP-09–16, AP-19, AP-23–24, AP-32.

### Arbeit

1. Feste kleine Fälle nach `TEST_MATRIX.md` mit erwarteten Resultaten inventarisieren; bekannte JVM-Baseline zuerst prüfen.
2. Reale CLI-Parität für alle vorhandenen Hauptbefehle und kritischen Optionen auf einem verfügbaren nativen Host prüfen.
3. `format` stdout/check/write, Optionen, Abbruch-/Fehler-/Originaldateischutz in den ausgewählten Fällen testen. Kein Stilumbau.
4. Kontrolliertes Repository mit lokalem HTTP-/HTTPS-Server, Importclosure, Proxy und verzögerten Antworten verwenden. Serverzugriffe tatsächlich zählen.
5. Standardtrustquelle plus isolierten Testtruststore unterscheiden. Öffentlichen HTTPS-Smoke separat protokollieren; keine Live-Corpusprüfung daraus machen.
6. Relokation, anderer CWD, HOME/Temp und Runtime-JDK-Sperre testen. Nicht nur Umgebungsvariablen löschen.
7. Kleine Messungen am bereits getesteten Artefakt durchführen, ohne Optimierungsiteration.

### Gezieltes Gate

- K01–K09, F01–F06, N01–N09 und die lokalen H-Fälle tatsächlich abgeschlossen; erwartete Fehler laufen durch den Harness als überprüfte Negativfälle.
- Keine fachlich relevanten Unterschiede wegnormalisiert; grosse Dezimalwerte/Unicode bleiben exakt.
- Positive TLS-Kontrolle und negative Trust-/Hostnamekontrollen funktionieren; Test-CA nicht im finalen Standardkandidaten.
- Offlinezugriff verursacht null Netzaktivität; Timeout-/Proxyfehler sind kontrolliert und kein Hänger.
- Fehlende Cases, manipulierte Reports oder falsche Binaryhashes werden abgelehnt.

**Outputs:** selektierter Casekatalog, Rohlogs, Laufzeit-/Fixture-/Harnessmanifest, Paritätsbericht und kleine Messung. Standardkandidat und eventueller Testkandidat sind ausdrücklich getrennt.

## F4 — Identische Datei auf drei nativen Zielplattformen

**Kriterien:** AP-13–19, AP-24–26, AP-28, AP-32.

### Arbeit

1. Den finalen unveränderten Kandidaten und eine kleine Fixture-/Harnesskopie bereitstellen, nicht den Builderbaum.
2. P01–P09 auf Linux x64, Windows x64 und macOS arm64 ausführen. Identität vor/nach jedem Ziel prüfen.
3. Alle Startarten und jede Loader-/Cache-/Temppfadvoraussetzung aufzeichnen. Direkter Kindprozessstart getrennt vom Shellstart.
4. Zusätzlich je Ziel die kleinen Netzwerkfälle N01/N02, N03/N04, N06/N07/N08 ausführen: Die gesamte F3-Variantenkombinatorik nicht auf jedem Ziel wiederholen, OS-abhängige Netzwerkgrundfunktionen aber wirklich prüfen.
5. Erforderliche Gegenprobe ohne JDK/Buildbaum durchführen. Eine vorhandene JVM für den Testtreiber darf für den Kandidaten nicht erreichbar sein.
6. Nur bei wirklicher Verfügbarkeit weitere Ziele ergänzen. WSL/Rosetta/QEMU nicht als native Pflichtzielbestätigung ausgeben.

### Gezieltes Gate

- Für GO: alle Pflichtzielprofile erfolgreich, derselbe finale APE-Hash und erlaubte vollständige Runtimebeigaben.
- Behebbarer Fehler: nur dessen Bereich untersuchen, korrigieren und gemäss Änderungswirkung erneut testen.
- Bei neuem finalem Binaryhash: Plattformbelege für die neue Datei erneuern; unveränderte Comparator-/Unitbelege nicht pauschal neu laufen lassen.
- Fehlender Zielrunner: `INCONCLUSIVE` für den vollständigen Anspruch, nicht fingierte Plattformdateien.

**Outputs:** echte Plattformmatrix, Startvertrag, Bundlemanifest und Transport-/Extraktionsprüfung. Dies ist keine notarized Produktionsfreigabe.

## F5 — Bereinigung, Entscheidung, geprüfter Kandidatencommit

**Kriterien:** AP-19–28, AP-31–32; Vorbereitung AP-29–30.

### Arbeit

1. Alle wirksamen Konfigurationen und benötigten Helfer dokumentieren; unbenutzte Flags/Versuche/Fallbacks aus aktiven Pfaden entfernen. Fehler-Rohlogs bleiben erhalten.
2. Source-/Artefakt-/Toolchain-/Caseinventare gegeneinander prüfen. Keine LSP-/Extensionänderungen und keine native Pflicht in bisherigen Builds.
3. Nur notwendige aktuelle Nachprüfungen: z.B. Argumentparser nach Adapterfix, Manifestvalidierung nach Schemafix. Keine pauschale abschliessende Vollsuite.
4. `FEASIBILITY_REPORT.md` mit Entscheidung, Begründung, wirklicher Plattformabdeckung, Startarten, Runtimebeigaben, Ressourcen, Einschränkungen und getrennten Folgearbeiten erstellen.
5. Gezielte Original-/Cache-/Clean-/Leak-Gegenproben abschliessen, `git diff --check` und Staging prüfen.
6. Lokaler Commit ausschliesslich dieses Auftrags. `CANDIDATE_SHA`, Tree und kumulativen Diff gegen `BASE_SHA` extern erfassen.

Mögliche Commitmessage: `build: add isolated compiler APE feasibility prototype` oder bei belegtem negativem Abschluss eine entsprechend ehrliche Message. Kein „universal compiler ready“, wenn die Pflichtmatrix fehlt.

### Gate

Die Belege tragen die erklärte Entscheidung. Es gibt keine offenen **behebbaren** Implementierungsfehler, die mit einem negativen Produkturteil verdeckt werden. Notwendige externe Blocker sind konkret dokumentiert. Die Abschlusszusammenfassung unterscheidet lokale Machbarkeit, Plattformumfang und ausstehende Releasearbeit.

## 7. Terminaler Abschluss bei Blockern

Bei einem frühen unvermeidbaren technischen Blocker:

1. Originalfehlversuch sichern.
2. Mit kleinem Reproducer zwischen Produkt, Toolchain und Umgebung unterscheiden.
3. Mindestens eine sinnvolle Kontrollhypothese prüfen; kein ritualisiertes mehrfaches identisches Neucompilieren.
4. Dokumentieren, warum der nächste Eingriff den Scope verlassen würde oder warum die Umgebung fehlt.
5. Abhängige Phasen als `NOT_RUN_BLOCKED` markieren.
6. In F5 den tatsächlich erreichten, bereinigten Stand und die negative/offene Entscheidung abschliessen.
7. Danach genauso committen und den unabhängigen Reviewer die Diagnose, die eingehaltenen Grenzen und die Aussagekraft der Proben prüfen lassen.

NO_GO kann technische Nichterfüllung unter den definierten Grenzen belegen. INCONCLUSIVE benennt fehlende Erkenntnis. Beides erlaubt keine positive Portabilitätsbehauptung und keinen automatischen Ersatz durch einen Nicht-APE-Build.

## R — autonomer unabhängiger Gesamt-Review

Erst jetzt `OPENCODE_REVIEW_PROMPT.md` ausführen. Der Reviewer prüft alle 32 Kriterien am gesamten kumulativen Diff und die begründete Entscheidung, nicht nur die letzten Änderungen. Er ergänzt eigene gezielte Gegenproben und muss keine bereits gültige Native-Build-/Plattformkampagne unbesehen wiederholen.

Bei Findings: Codex korrigiert → notwendige Testmenge bestimmen → gezielt ausführen → neuer Commit → frische unabhängige Reviewrunde. Binärnachweise und Quellnachweise getrennt behandeln. Unveränderte Belege behalten ihre ursprünglichen SHA-/Dirty-/Runangaben.

**Abschluss:** tatsächlicher finaler Commit, Machbarkeitsentscheidung, unabhängiges Reviewurteil und externe `review.md`-/`verdict.json`-Pfade. Kein nachträglicher ungeprüfter Abschlusscommit.
