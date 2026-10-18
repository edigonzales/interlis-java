# Zielbild und übergeordneter Anforderungskatalog

## 1. Produktziel

`interlis-java` wird ein moderner Java-Stack für INTERLIS 2.3 und 2.4. Er verbindet Modellkompilierung, Sprachdienste, Modellvisualisierung, Transferverarbeitung, Validierung und Datenbankabbildung über ein gemeinsames semantisches Modell. Die erste Lieferung ist der Compiler. Die Fähigkeiten von ili2c, iox-ili, ilivalidator und ili2db sind das Gesamtziel, keine Behauptung des ersten Entwicklungsstands.

Die Werkzeuge sollen in bestehenden betrieblichen Abläufen möglichst unauffällig austauschbar sein. Die innere Java-Architektur darf sich ändern. Besonders streng ist die Kompatibilität der Datenbankabbildung. Strukturierte Diagnostik und Betriebsereignisse sind Querschnittsanforderungen ab dem ersten Compilerlauf.

## 2. Nutzer und fachliche Abläufe

| Nutzer/Verbraucher | Tätigkeit | Erfolgskriterium |
|---|---|---|
| Modellautor | Modell in VS Code oder Browser bearbeiten | Navigation und verständliche Fehler auch während unvollständiger Eingaben. |
| CLI-/CI-Nutzer | Modelle prüfen oder Modellinformationen erzeugen | Reproduzierbare Ergebnisse, kompatible Aufrufe/Exitcodes und nachvollziehbare Meldungen. |
| Bibliotheksentwickler | Typen, Klassen, Constraints und Transferregeln abfragen | Typisierte stabile API ohne Kenntnis von ANTLR oder internen Buildern. |
| Fachperson | Modell als Diagramm betrachten | Fachlich korrekte Kanten, Filter, Vererbung und Navigation zum Quelltext. |
| Datendienst/ETL | XTF lesen, prüfen, schreiben oder in DB übertragen | Gemeinsame Modellinterpretation, kontrollierter Speicherverbrauch und verständliche Fehler. |
| Betreiber | Fehler in automatisierten Läufen untersuchen | Zusammenhängende strukturierte Ereignisse, Ursachen, Status und Laufzeiten. |
| Bestehendes Java-Ökosystem | Schrittweise auf neuen Kern wechseln | Gezielte Adapter und explizite Kompatibilitätsprofile statt stiller Verhaltensänderungen. |

## 3. Scope und fachliche Grenzen

Unterstützte Modellsprachen sind ausschliesslich INTERLIS 2.3 und 2.4. Modelle für 1.0 oder 2.2 erhalten eine eindeutige Meldung. ITF-Funktionen, die die ausgeschlossene Sprache voraussetzen, sind kein Teil dieses Produktumfangs. Ein zulässiges 2.3-Konstrukt muss in 2.3 korrekt analysiert werden; zusätzliche 2.4-Schlüsselwörter dürfen alte zulässige Bezeichner nicht unbeabsichtigt sperren.

Der vollständige Compilerumfang wird anhand beider Referenzhandbücher und einer versionierten Featurematrix abgegrenzt. Der allgemeine Parser ist nur ein Syntaxwerkzeug. Die Semantik umfasst die in [Metamodell](05-metamodell-und-typensystem.md) und [Semantik](06-kompilierung-und-semantik.md) beschriebenen Konstrukte. Die offizielle Dokumentation stellt die Fassungen getrennt bereit: [INTERLIS 2-Dokumentation](https://www.interlis.ch/dokumentation/interlis-2).

Das neue Metamodell ersetzt nicht alle alten Java-Klassen binär. Eine solche Kompatibilität ist ein eigenständiger Adapterumfang. CLI-/Transfer-/DB-Kompatibilität wird dagegen als Produktvertrag spezifiziert. Nicht unterstützte Verbraucherfunktionen werden explizit gemeldet; der Compiler darf eine normativ gültige Sprache nicht auf den zufälligen ersten Umfang eines Exporters reduzieren.

## 4. Funktionale Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| GOAL-001 | Der Kern MUSS INTERLIS 2.3 und 2.4 mit expliziter Versionsauswahl analysieren; andere Modellsprachen werden eindeutig abgelehnt. | P1/P2 | Versionsmatrix einschliesslich versionsabhängiger Schlüsselwörter; P1 Umfang markiert, P2 vollständig. |
| GOAL-002 | Batch, IDE und Browser MÜSSEN dieselbe semantische Implementierung verwenden. | P1 | Dieselben Eingaben liefern dieselbe normalisierte Analyse und dieselben Diagnosecodes. |
| GOAL-003 | Ein öffentliches typisiertes Metamodell MUSS die Nachfolge der TransferDescription für alle geplanten Verbraucher ermöglichen. | P1/P2 | Verbrauchervertragstests für Typen, Referenzen, Reihenfolgen und Projektionen. |
| GOAL-004 | Unvollständige oder fehlerhafte Quellen MÜSSEN sinnvoll analysierbare Teilinformationen ergeben. | P1 | Editiersequenzen erhalten Nachbarsymbole und präzise fehlende Elemente. |
| GOAL-005 | Der Workspace MUSS Quellen aktualisieren/entfernen und unveränderte Arbeit wiederverwenden können. | P1/P3 | Revisionstests, Cache-Nachweise und Gleichheit zur frischen Analyse. |
| GOAL-006 | Modellvisualisierungen MÜSSEN aus semantischen Projektionen mit Quellverweisen entstehen. | P1/P3 | Diagramm-Goldens und Navigation; Renderer erfindet keine Bindungsregeln. |
| GOAL-007 | Der Compiler MUSS als JVM-Bibliothek und CLI nutzbar sein und einen geprüften Browserpfad erhalten. | P1 | JVM-/WASM-/Browserabnahmen im selben Arbeitspaket. |
| GOAL-008 | Strukturierte Diagnostik und Laufereignisse MÜSSEN ab dem ersten öffentlichen Lauf verfügbar sein. | P1 | Diagnose- und Eventvertragstests, Legacy-/JSON-Darstellungen. |
| GOAL-009 | Die späteren Transfer-/Validator-/DB-Module MÜSSEN dieselbe fachliche Modellinterpretation verwenden. | P4/P5 | Gemeinsame Modellfingerprints und Plan-Goldens. |
| GOAL-010 | Der DB-Ersatz MUSS bestehende Abbildungsinformationen respektieren und gegenseitige Weiterverwendung ermöglichen. | P5 | Alte/neue Werkzeuge lesen und verändern Datenbanken in beiden Richtungen. |
| GOAL-011 | Bekannte CLI-Namen, Optionen, Ausgaben und Exitcodes MÜSSEN pro freigegebenem Legacy-Profil geprüft werden. | P3/P5 | Gepinnte Referenzläufe und genehmigte Abweichungen. |
| GOAL-012 | Die externe Compilersuite MUSS übernommen und mit semantischen, Editor- und Verbrauchertests ergänzt werden. | P0/P2 | Testreports unterscheiden Akzeptanz, Semantik und bekannte Abweichungen. |

## 5. Nichtfunktionale Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| NFR-001 | Quelltextanalyse MUSS bei gleichen fachlichen Inputs deterministisch sein. | P1 | Reihenfolge, Diagnostik und normalisierte Projektion bleiben über Wiederholung/Plattform gleich. |
| NFR-002 | Unveränderliche veröffentlichte Snapshots MÜSSEN gefahrlos parallel lesbar sein. | P1 | Parallele Abfragen verändern weder Resultate noch Cachezustand des Snapshots. |
| NFR-003 | Der portable Kern DARF keine impliziten Netzwerk-, Dateisystem-, DB- oder UI-Zugriffe benötigen. | P0 | Abhängigkeitsprüfung und In-Memory-/WASM-Lauf. |
| NFR-004 | Laufzeit und Speicher MÜSSEN auf einem versionierten Korpus gemessen und gegen Budgets geprüft werden. | P1/P3 | Messverfahren mit Rohresultaten; Ziele und Messungen getrennt. |
| NFR-005 | Abbruch und Ressourcenlimits MÜSSEN definierte Resultate liefern; ein Abbruch darf kein gültiges Teilergebnis ausstellen. | P1 | Phasenabbruchtests und Worker-Verhalten. |
| NFR-006 | Unicode, CRLF, Kommentare und Quellpositionen MÜSSEN verlustfrei und versionsbezogen behandelt werden. | P1 | UTF-16-/Codepoint-/Zeilenmatrix mit Emojis und Umlauten. |
| NFR-007 | Diagnostik und Projektionen MÜSSEN ohne localeabhängige Zahlen-/Sortierunterschiede erzeugt werden. | P1 | Tests mit verschiedenen JVM-Locale-/Timezone-Einstellungen. |
| NFR-008 | Öffentliche Resultate MÜSSEN Nullfreiheit, Reihenfolge und Lebensdauer dokumentieren. | P1 | API-Vertragstests und Register. |
| NFR-009 | Der Build MUSS per Gradle-Wrapper mit Groovy-DSL reproduzierbar ausführbar sein. | P0 | Frischer Checkout, gepinnte Toolchain und dokumentierte Kommandos. |
| NFR-010 | Der Kern MUSS unabhängig von einer laufenden Serverinfrastruktur verwendbar sein. | P0 | Lokale Bibliotheks-/CLI-Ausführung ohne Spring, Datenbank oder Container. |
| NFR-011 | Produktionsgeometrievalidierung MUSS später begrenzte Caches, Streaming und explizite Ressourcenbudgets anbieten. | P4 | Grössenkorpus und definierte Reaktion auf Budgeterschöpfung; 64 GB nicht überschritten im festgelegten Profil. |
| NFR-012 | Abhängigkeiten und technische Neuerungen MÜSSEN einen tatsächlichen aktuellen Nutzen haben. | P0 | Kurzer Zweck je Dependency; kein Framework nur für künftige Eventualitäten. |

Die 64 GB-Grenze ist ein späteres maximales Betriebsbudget des Validators, kein Standardheap des Compilers und keine Garantie für beliebig grosse globale Prüfprobleme. Konkrete Objekt-/Geometriekorpora und passende kleinere Standardbudgets sind in P4 festzulegen. Compilerbudgets werden getrennt behandelt.

## 6. Kompatibilität hat mehrere Dimensionen

Eine Kompatibilitätsaussage MUSS mindestens Werkzeug, Version, Optionen, Sprachversion, Plattform und relevante Ausgabeformate nennen. „Kompatibel zu ili2db“ ohne Profil ist nicht prüfbar. CLI-Kompatibilität umfasst auch Arbeitsverzeichnis, Konfigurationsprioritäten, stdout/stderr und Optionsinteraktionen. Dateikompatibilität umfasst fachlichen Inhalt sowie notwendige Zeichenkodierungs- und Formatdetails. DB-Kompatibilität umfasst Daten und Metadaten, nicht nur gleichnamige Tabellen.

Bei einem Norm-/Legacy-Konflikt wird eine fachliche Entscheidung dokumentiert. Die Standardsemantik folgt der festgelegten Sprachfassung; ausdrücklich verlangtes historisches Verhalten kann in ein versioniertes Kompatibilitätsprofil aufgenommen werden. Ein neues Profil darf keine unsichtbare globale Flag-Sammlung werden.

## 7. Qualitätszustände und Fortschritt

Für jede Fähigkeit werden mindestens `NOT_STARTED`, `IMPLEMENTED`, `VERIFIED` und gegebenenfalls `KNOWN_DIVERGENCE` geführt. `IMPLEMENTED` bedeutet vorhandene Logik; `VERIFIED` bedeutet bestandene bezeichnete Abnahmen auf bezeichneten Plattformen. P1 erhält einen klaren Teilumfang, P2 eine vollständige Sprachmatrix. Der Code darf den Unterschied nicht nur in README behaupten: `AnalysisCoverage` und Verbraucher-Capabilities machen ihn abfragbar.

Gültigkeit ist auf eine angeforderte Wurzelmenge und ihre tatsächlichen Abhängigkeiten bezogen. Unabhängige fehlerhafte Dokumente im Workspace verhindern nicht pauschal die Kompilierung einer anderen gültigen Wurzelmenge. Fehler innerhalb der benötigten Closure müssen das gültige Ergebnis verhindern. Recovery darf keine solche Abhängigkeit verdecken.

## 8. Lieferphasen

| Phase | Ergebnis | Freigabeaussage |
|---|---|---|
| P0 | Gradle-Basis, Pins, Referenzkorpus, reproduzierter und portierter Syntaxspike | Technische Basis, noch kein Compilerersatz. |
| P1 | Durchgängige semantische Strecke, Teilanalyse, Workspace, erste LSP-/Diagramm-/Browserintegration | Belegter vertikaler Teilumfang. |
| P2 | Vollständige Semantik 2.3/2.4, ausgereiftes Metamodell, Konformitäts- und Semantikabnahmen | Fachlich vollständiger Compilerumfang. |
| P3 | Auslieferbare Compiler-CLI, vollständige Integration, Performance-/Kompatibilitätsprofile | Ersatz für explizit freigegebene ili2c-Verwendungen. |
| P4 | Transferverarbeitung und Validator auf gemeinsamen Plänen | Ersatz für freigegebene iox-ili-/ilivalidator-Verwendungen. |
| P5 | Datenbankabbildung und Werkzeuge | Ersatz für geprüfte ili2db-Profile und DB-Backends. |

IDE und WASM werden in P1 begonnen und bei jedem semantischen Ausbau mitgeprüft. P3 ist keine Erlaubnis, diese Anforderungen bis dahin aufzuschieben. Ebenso muss P1 die späteren fachlichen Modellbedürfnisse bereits berücksichtigen, ohne die späteren Engines zu implementieren.
