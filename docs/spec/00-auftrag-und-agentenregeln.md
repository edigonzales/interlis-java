# Auftrag und Arbeitsregeln für den Coding-Agenten

**Status:** normative Spezifikation, Version 1.0, 18. Oktober 2026. **Projekt:** `interlis-java`. **Ziel:** moderner Java-INTERLIS-Stack mit kompatiblen Werkzeugen; zuerst ein wiederverwendbarer Compiler für INTERLIS 2.3 und 2.4. Dieses Paket ist ein Implementierungsauftrag und ein Abnahmevertrag. Es behauptet keine bereits vorhandene Implementierung der beschriebenen Klassen.

## 1. Auftrag und Ergebnis

Entwickle in Java 25 einen neuen Compilerkern, der dieselbe Sprachsemantik für Batch-Kompilierung, IDE-Analyse, Modellvisualisierung und portable Ausführung verwendet. Baue darauf später die Fähigkeiten von ili2c, iox-ili, ilivalidator und ili2db auf. Erhalte vertraute Werkzeugeigenschaften und insbesondere die bestehenden Datenbankverträge. Die öffentlichen neuen Java-Pakete beginnen mit `guru.interlis`.

Der erste Arbeitsauftrag endet nach den vollständigen Abnahmen von P0 und P1 aus [Implementierungsplan](14-implementierungsplan-und-arbeitspakete.md). P1 enthält echte semantische Analyse über mehrere Modelldateien, Fehlerbehandlung im Editor, JVM-/WASM-Vergleich und eine erste Diagrammprojektion. Es ist kein vollständiger Ersatzcompiler. Die Anforderungen von P2 bis P5 bleiben verbindliche Zielanforderungen; sie werden weder gelöscht noch durch Erfolgsmeldungen über einen kleinen Teilumfang als erledigt markiert.

Dieser Implementierungsauftrag wird durch eine entsprechende Nutzeranweisung aktiviert, beispielsweise durch Erteilung des Startprompts. Die Anwesenheit oder das Lesen dieses Dokuments startet keine Implementierung. Bei einer Analyse, Dokumentpflege oder einem gezielten Fix gilt der konkrete aktuelle Auftrag; seine Erledigung verlangt nicht automatisch die Durchführung aller P0/P1-Arbeitspakete.

## 2. Normative Sprache und Rangfolge

- **MUSS / DARF NICHT:** verpflichtender Vertrag; eine Ausnahme benötigt eine nachvollziehbare Entscheidung im Projekt und Anpassung der betroffenen Abnahme.
- **SOLL:** begründeter Standardfall; Abweichungen werden mit Ursache, Konsequenz und Ersatzprüfung dokumentiert.
- **KANN:** erlaubte Erweiterung, keine Voraussetzung für einen Meilenstein.
- **P0 bis P5:** Zeitpunkt, ab dem eine Anforderung für die jeweilige Freigabe erfüllt sein muss. Früher darf sie teilweise implementiert sein, aber nicht als vollständig erfüllt gelten.
- **Beispiel / Skizze:** zeigt einen Vertrag; ist kein behauptetes bereits kompilierendes Repository.

Die aktuellen Nutzeranweisungen haben Vorrang. Innerhalb dieses Pakets regelt [API-Register](16-api-register-und-vertragstests.md) die gemeinsamen Namen und Zuständigkeiten, das jeweils verlinkte Fachkapitel die ausführlichen Vor-/Nachbedingungen. [Zielbild](01-zielbild-und-anforderungen.md) definiert den Scope; [Build](03-build-und-repository.md) entscheidet den Build. Quellen belegen bestehendes Verhalten, ersetzen aber keine explizite Projektentscheidung. Eine erkennbare fachliche Inkonsistenz wird mit betroffenen IDs und einer konkreten Korrektur behandelt, nicht durch stilles Ignorieren eines Kapitels.

## 3. Lesereihenfolge

1. [README](README.md), dieses Dokument und [Zielbild](01-zielbild-und-anforderungen.md).
2. [Architektur](02-architektur-und-modulgrenzen.md), [Build](03-build-und-repository.md), [API-Register](16-api-register-und-vertragstests.md).
3. Das Fachkapitel des nächsten Arbeitspakets und dessen verknüpfte Diagnose-, Test- und WASM-Verträge.
4. [Implementierungsplan](14-implementierungsplan-und-arbeitspakete.md), [Entscheidungen und Quellen](15-entscheidungen-risiken-und-quellen.md), [Abdeckungsmatrix](17-abdeckungsmatrix-und-review.md).

Vor einer Implementierungsänderung benenne die bearbeiteten Anforderungs-IDs, den betroffenen fachlichen Vertrag und die Abnahme. Ein neuer Agent muss aus Repository und Dokumentation ohne frühere Chatnachrichten weiterarbeiten können.

## 4. Verbindlicher Arbeitszyklus

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| AGENT-001 | Der Agent MUSS den aktuellen Repositoryzustand und vorhandene Arbeitsregeln lesen und fremde Änderungen erhalten. | P0 | Arbeitsbericht nennt Ausgangszustand und abgegrenzte Änderungen. |
| AGENT-002 | Jedes Arbeitspaket MUSS Anforderungen, Implementierung, aussagekräftige Prüfungen und Dokumentationsänderungen zusammen liefern. | P0 | Vier Teile sind im Ergebnis nachvollziehbar; keine reine Dateiliste. |
| AGENT-003 | Ein Stub, ein leerer Visitor, eine feste Erfolgsantwort oder ein ignoriertes Konstrukt DARF NICHT als implementierte Sprachunterstützung gelten. | P0 | Negativtest und Capability-/Coverage-Bericht erkennen den fehlenden Teil. |
| AGENT-004 | Der Agent MUSS nicht implementierte fachliche Konstrukte explizit melden und ihre Auswirkungen auf das Kompilierungsergebnis berücksichtigen. | P1 | Unabgedecktes Konstrukt erzeugt kein gültiges CompiledSchema. |
| AGENT-005 | Der Agent MUSS einen fehlgeschlagenen Test am Vertrag analysieren; Erwartungen dürfen nur mit fachlichem Beleg geändert werden. | P0 | Änderung von Goldens/Baselines enthält Referenz und Begründung. |
| AGENT-006 | Die neue Implementierung MUSS die Semantik selbst ausführen; ein heimlicher Aufruf von ili2c/ilic als Produktionscompiler ist unzulässig. | P1 | Referenzwerkzeuge laufen ausschliesslich in Testadaptern oder explizitem Legacy-Adapter. |
| AGENT-007 | Ergebnisse MÜSSEN erreichte Implementierung, ausgeführte Prüfungen, nicht ausgeführte Prüfungen und offene Risiken unterscheiden. | P0 | Abschlussbericht verwendet keine pauschale Aussage „alles grün“ ohne Umfang. |
| AGENT-008 | Wiederaufnahmeinformationen MÜSSEN im Repository stehen; Chatkontext darf keine einzige Quelle für eine Entscheidung sein. | P0 | Neuer Agent findet Anforderungen, Status, Testkommandos und nächsten Arbeitsschritt. |
| AGENT-009 | API-Änderungen MÜSSEN das API-Register, Adapter und Vertragstests im selben Arbeitspaket aktualisieren. | P1 | Keine zweite inkompatible Signatur desselben öffentlichen Typs. |
| AGENT-010 | Der Agent MUSS den kleinsten vollständigen nächsten vertikalen Abschnitt implementieren; ungenutzte Frameworks und leere Zukunftsmodule werden nicht angelegt. | P0 | Jede Produktionsabhängigkeit und jedes Modul besitzt einen aktuellen Verbraucher oder eine konkrete Abnahme. |

Ein geeigneter Arbeitsschritt lautet beispielsweise: „SEM-…: Namensbindung eines importierten Domänentyps; Compilerklasse und Abfrageindex implementieren, gültigen und fehlenden Import prüfen, Diagnose- und WASM-Golden aktualisieren.“ Ein ungeeigneter Arbeitsschritt lautet: „Compiler fertig machen“.

## 5. Umgang mit Unsicherheit

Bei Routineentscheidungen wählt der Agent die kleinste vertragskonforme Lösung und dokumentiert sie. Bei einer unklaren Sprachregel recherchiert er das passende 2.3-/2.4-Referenzhandbuch, liest relevante Referenztests und vergleicht das bisherige Werkzeug. Er erfindet keine INTERLIS-Regel aus einem einzelnen positiven Beispiel. Bei einer echten Produktentscheidung, die Kompatibilität, Scope oder Datenverlust berührt, bereitet er Varianten, Auswirkungen und eine Empfehlung vor. Alle davon unabhängigen Arbeiten werden fortgesetzt.

Eine noch nicht verifizierte Toolchain-Version bleibt ein offener technischer Nachweis und wird nicht als Produktentscheidung ausgegeben. Ein technisch blockierter Web-Image-Pfad wird mit reproduzierbarer Ursache dokumentiert, während die übrigen Artefakte weiter entstehen. Die P1-Abnahme für Web Image verlangt einen tatsächlich ausgeführten Versuch mit Fehleranalyse; eine unerfüllte Pflicht zur Browserausführung oder Semantikgleichheit darf dadurch nicht verschwinden.

## 6. Code- und API-Qualität

Produktionscode und API-Namen sind Englisch, die Spezifikation Deutsch. Javadoc erläutert insbesondere Nullfreiheit, Mutabilität, Reihenfolge, Revisionen, fachliche Einheiten und Fehlerverhalten. Ein öffentlicher Service wird nicht nur durch Methodennamen beschrieben: Eingaben, Resultate, Abbruch, Ausnahmen, deterministische Reihenfolge und Besitzverhältnisse müssen prüfbar sein.

Verwende Records für Werte und Sealed Types für fachlich geschlossene Varianten. Der Compilerkern benötigt keine Dependency-Injection-Laufzeit, keinen Webserver und keine ORM-Schicht. Abhängigkeiten werden über Konstruktoren bzw. kleine Fabriken übergeben. `Optional` beschreibt echte Abwesenheit; ungültiger Quelltext ist nicht generell eine Java-Ausnahme. Unbekannt, ungültig, noch nicht analysiert und legal aufgeschoben müssen unterscheidbar bleiben.

Kein globaler veränderlicher Compilerzustand, keine globalen Log-Listener und keine globalen HTTP-Proxyänderungen. Getrennte Sitzungen dürfen sich nicht über Symbolzähler, Fehlerflags oder Caches beeinflussen. Caches sind Optimierungen und dürfen beobachtbare Sprachsemantik nicht verändern.

## 7. Erwarteter Arbeitsbericht

Jedes abgeschlossene Paket dokumentiert:

1. Problem und implementierten fachlichen Umfang.
2. Erfüllte IDs sowie noch offene abhängige IDs.
3. Geänderte öffentliche Verträge und Migrationsfolgen.
4. Ausgeführte Kommandos, Zielplattformen, Ergebnisse und relevante Laufumgebung.
5. Reproduktionsweg für verbleibende Fehler.
6. Nächsten vollständigen Abschnitt mit konkreter Abnahme.

Berichte über Performance nennen Korpus, Hardware, Laufzeit, Warm-/Kaltzustand und Messverfahren. Schätzungen und Sollwerte werden als solche markiert. Für fachliche Korrektheit gilt kein pauschaler Code-Coverage-Prozentsatz als Ersatz für die Fallmatrix.

## 8. Repository-Fortschreibung

Die Spezifikationsdateien werden unter `docs/spec/` ins spätere Repository übernommen. Die mitgelieferte Root-`AGENTS.md` enthält die allgemeinen Arbeitsregeln; `docs/spec/AGENTS.md` ergänzt Regeln für die Pflege dieser Dokumente. Beide verweisen auf die fachlichen Vertragsquellen und enthalten keine zweite unabhängige API-Spezifikation. Projektstatus steht unter `docs/implementation-status.md`, maschinenlesbare Test-/Capability-Ergebnisse unter dem in Kapitel 13 festgelegten Reportschema. Der Agent darf lokale Implementierung und reversible Verifikation im erteilten Umfang fortsetzen. Repository-Erstellung, Remote-Veröffentlichung und Releases benötigen einen entsprechenden gesonderten Auftrag, sofern sie nicht bereits im Arbeitsauftrag enthalten sind.

## 9. Ende des ersten Auftrags

P0/P1 sind abgeschlossen, wenn die definierten Eingangsmodelle und ihre negativen Varianten auf allen verpflichtenden Plattformen nachvollziehbar laufen, der Editorzwischenstand wirklich analysiert wird, eine Diagrammprojektion aus demselben semantischen Modell entsteht und die Abnahmematrix die Evidenz referenziert. Anschliessend wird der nächste Abschnitt aus P2 gewählt. Eine schicke Demo, ein erfolgreicher Parserlauf oder ein angelegtes Klassengerüst allein erfüllen dieses Ende nicht.
