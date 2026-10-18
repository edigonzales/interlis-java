# Arbeitsregeln für die INTERLIS-Spezifikation

Diese Datei gehört nach `docs/spec/AGENTS.md` und ergänzt die `AGENTS.md` im Repository-Root für Änderungen in diesem Verzeichnis und seinen Unterverzeichnissen. Die fachlichen Produktregeln gelten weiterhin gemäss Spezifikation. Diese Datei regelt deren Pflege; sie erteilt keinen Implementierungsauftrag und aktiviert insbesondere nicht automatisch den enthaltenen `STARTPROMPT.md`.

## 1. Vertragsquellen und Umfang

Lies vor einer Änderung die betroffenen Kapitel und ihre im Text verknüpften Verträge. Das Gesamtziel bleibt ein Java-Stack für ili2c, iox-ili, ilivalidator und ili2db. Der frühere Lieferzeitpunkt des Compilers hebt spätere Verbraucher- und Datenbankanforderungen nicht auf.

| Dokument | Zuständigkeit |
|---|---|
| `00-auftrag-und-agentenregeln.md` | Normative Sprache, Arbeitszyklus und Nachweisregeln |
| `01-zielbild-und-anforderungen.md` | Produktumfang und übergeordnete Ziele |
| `02-architektur-und-modulgrenzen.md` | Modulgrenzen, Abhängigkeiten und Zustandsbesitz |
| `03-build-und-repository.md` | Gradle/Groovy, Toolchain und Build-/Testaufgaben |
| `04` bis `12` und `18` | Ausführliche fachliche Daten-, Klassen- und Methodenverträge |
| `13-tests-kompatibilitaet-und-qualitaet.md` | Referenzprofile, Fallmetadaten und Qualitätsnachweise |
| `14-implementierungsplan-und-arbeitspakete.md` | Arbeitspakete, Voraussetzungen und Phasengates |
| `15-entscheidungen-risiken-und-quellen.md` | Entscheidungen, Quellenstände und offene Nachweise |
| `16-api-register-und-vertragstests.md` | Gemeinsame Typnamen, Besitzer und API-Einstiege |
| `17-abdeckungsmatrix-und-review.md` | Zuordnung der Nutzerziele und abgeleiteter Anforderungsindex |

Aktuelle Nutzeranweisungen und übergeordnete Anweisungen der Ausführungsumgebung gehen vor. Ein Widerspruch zwischen Register und Fachkapitel ist ein zu klärender Dokumentfehler. Erlaube nicht durch selektives Lesen zwei unterschiedliche Verträge für denselben Typ.

## 2. Änderungen an Anforderungen

- Erhalte vorhandene IDs und ihre Bedeutung. Eine neue Anforderung erhält eine neue freie ID des passenden Bereichs. Nummeriere bestehende Anforderungen nicht zur optischen Bereinigung um und verwende eine alte ID nicht erneut.
- Jede normative Anforderung besitzt einen klaren Text, ihre Phase und eine überprüfbare Abnahme. Verwende MUSS, DARF NICHT, SOLL und KANN gemäss Kapitel 00.
- Ändere den normativen Text zuerst im zuständigen Fachkapitel und synchronisiere anschliessend Kapitel 17. Dessen Index ist keine zweite unabhängige Definition und kein Implementierungsstatus.
- Behalte bei mehrphasigen Anforderungen die noch ausstehenden Verpflichtungen. Eine funktionierende P1-Demo erledigt keine vollständige P2-Sprachunterstützung oder P5-Datenbankkompatibilität.
- Verschiebe oder streiche Anforderungen, Pflichtprüfungen und Gates nur im Rahmen eines entsprechenden Nutzerauftrags beziehungsweise einer bereits getroffenen Projektentscheidung. Dokumentiere Grund, betroffene IDs und Folgen. Eine technische Schwierigkeit oder ein roter Test rechtfertigt für sich allein keine Abschwächung.

## 3. Klassen- und Methodenverträge konsistent halten

Beschreibe für eine neue oder geänderte API mindestens: kanonischen Namen und Modul/Package, Eingaben und Ergebnisvarianten, Nullfreiheit und Mutabilität, Vor-/Nachbedingungen, fachliche Reihenfolge, Quell-/Snapshotbezug, Zustandsbesitz und Lebensdauer, Abbruch-/Fehlerverhalten sowie passende positive und negative Fälle. Bei einer reinen redaktionellen Änderung ist keine zusätzliche API zu erfinden.

Prüfe gemeinsam die betroffenen Fachkapitel, Kapitel 16, Verbraucher und Beispiele. Besonders häufig betreffen Änderungen diese Übergänge:

- Quelle und Syntax: Revisionen, Halbintervalle in UTF-16, Originaltext und Fingerprints;
- Semantik und Modell: Identitäten, Typvarianten, legale späte Bindung und geprüfte Schemaauswahl;
- Resolver und Workspace: vorbereitete Closure, negative Abhängigkeiten und Wiederverwendung;
- Hintergrundanalyse: Capture, bedingte Annahme und Veröffentlichung;
- Compiler und Adapter: Ergebnisvarianten, RejectionSummary und genau ein Lifecycle pro Lauf;
- Diagramme und spätere Pläne: explizite Auswahl, Fähigkeiten, Overlays und Reihenfolgen;
- JVM und Browser: portable Kernverträge, Wire-Zahlen, Sessions und Abbruch.

Ändert sich die Abnahme, aktualisiere zusätzlich Kapitel 13 und 14. Bei einer Architekturentscheidung pflege Kapitel 15. Passe README und STARTPROMPT an, wenn Einstieg, Scope oder Meilensteinauftrag betroffen sind. Kopiere vollständige Signaturkataloge nicht in beide AGENTS-Dateien; die Fachkapitel und Kapitel 16 bleiben zuständig.

## 4. Belege, Beispiele und tatsächlicher Status

Unterscheide klar zwischen bestehendem Referenzverhalten, neuer Entwurfsentscheidung, geplanter Implementierung und tatsächlich ausgeführtem Nachweis. Belege Aussagen über Upstreamprojekte, Standards und Toolchains mit Primärquellen und bei reproduzierbaren Referenzen mit dem konkreten Stand. Erfinde keine Normabschnitte, Commit-Hashes, Prüfsummen oder Messergebnisse.

Eine beobachtete Abweichung zur Referenz muss gegen Norm und Verbraucherwirkung bewertet werden. Ändere die Anforderung oder Testbaseline nicht allein deshalb, weil die neue Implementierung anders reagiert. Offene Fragen erhalten eine konkrete Fälligkeit beziehungsweise ein zuständiges Arbeitspaket in Kapitel 15 oder 14; führe kein unspezifisches TODO als scheinbar vollständigen Vertrag ein.

Markiere Java-/Groovy-Signaturbeispiele mit ausgelassenen Bodies oder Imports als solche. Vollständige JSON-Beispiele müssen parsebar sein. Kennzeichne Kommando- und Versionsbeispiele, die erst im Implementierungsprojekt verifiziert werden. Behaupte aus einer Dokumentprüfung weder einen erfolgreichen Compilerbuild noch Browser-, Konformitäts- oder Datenbankkompatibilität.

Die historische Evidenz unter `docs/evidence/` behält ihren tatsächlichen Versuchsstand. Schreibe deren Maven-/Node-/Toolchainnachweise nicht nachträglich um, damit sie wie ein neuer Gradle-, Browser- oder Web-Image-Lauf erscheinen. Ergänze neue Versuche mit eigener Provenienz und aktualisiere die fachliche Einordnung.

## 5. Form und angemessene Prüfung

Schreibe Deutsch mit Schweizer Schreibweise; API-Namen und Produktionscode bleiben Englisch. Verwende UTF-8, aussagekräftige Überschriften, relative Kapitelverweise, lesbare Tabellen und Codeblöcke mit Sprachangabe. Halte die Dokumente konkret und vermeide doppelte Regelkopien. Private Implementierungsdetails werden nur festgelegt, wenn sie einen fachlichen Vertrag erklären oder absichern.

Prüfe nach einer Dokumentänderung ihren tatsächlichen Umfang:

1. Sind IDs eindeutig, referenzierte IDs vorhanden und Phase/Abnahme konsistent?
2. Stimmen Register, Fachvertrag, Beispiele, Arbeitspakete und abgeleiteter Index an den betroffenen Stellen überein?
3. Lösen Dateiverweise auf, sind Codeblöcke geschlossen und vollständige JSON-Beispiele parsebar?
4. Stimmen Umfangsangaben und Inhaltsverzeichnisse, wenn Dateien oder Anforderungen hinzugekommen sind?
5. Bleiben Aussagen über implementierte oder geprüfte Fähigkeiten durch tatsächliche Nachweise gedeckt?

Verwende vorhandene Dokumentwerkzeuge, wenn sie im Repository existieren. Die Erstellungshelfer eines heruntergeladenen Spezifikationspakets sind keine automatisch vorhandenen Projekttasks. Richte für eine kleine redaktionelle Änderung kein neues Lint-/Buildframework ein. Führe Java-/Gradle- oder Plattformprüfungen gezielt aus, wenn die Änderung ausführbaren Code oder dessen zugesagtes Verhalten betrifft.

Die Einzelkapitel sind die pflegbare Quelle. Eine mitgelieferte Gesamtfassung oder ein ZIP wird daraus abgeleitet. Aktualisiere solche Ausgaben bei beauftragter Paketpflege; ändere sie nicht isoliert und verlange ihre Neuerzeugung nicht als Voraussetzung für jede lokale Textkorrektur.

## 6. Änderungsbericht

Nenne die geänderten Verträge beziehungsweise IDs, ihre Auswirkungen, synchronisierte Kapitel und die tatsächlich ausgeführten Prüfungen. Weise verbleibende Widersprüche konkret aus. Bei einer reinen Spezifikationsänderung wird kein Implementierungsfortschritt behauptet.
