# Validator-Kommando

Der JVM-Validator prüft XTF 2.3 und 2.4 mit der bestehenden Datensatz- und
JTS-Geometrieprüfung. Er erkennt das Format am XML-Header. Die Dateiendung ist
frei. Die [Transferverträge](transfer.md) beschreiben die unterstützten Daten.

## Installation

Der Paketbau benötigt ein Java-25-JDK.

```sh
./gradlew validatorAppZip
```

Das ZIP `interlis-validator-<version>-<plattform>.zip` liegt unter
`build/distributions/apps/` und enthält Java 25. Nach dem Entpacken ist der Einstieg
`interlis-validator/bin/interlis-validator` auf macOS und Linux und
`interlis-validator\bin\interlis-validator.bat` auf Windows. Der Launcher
verwendet das enthaltene Java aus `runtime/` und die JARs aus `lib/`. Relative Eingabepfade beziehen
sich auf das aktuelle Arbeitsverzeichnis.

```sh
/path/to/interlis-validator/bin/interlis-validator \
  --offline daten-a.xtf daten-b.xtf --refdata katalog.xtf --json
```

## Eingaben und Modellauflösung

Alle positionalen Dateien bilden einen gemeinsamen Prüfbestand. Globale
Regeln und Referenzen werden über die Dateigrenzen hinweg geprüft.
`--refdata FILE` ergänzt Referenzdaten und ist wiederholbar. Referenzobjekte
können Ziele und Zeugen sein. Als Prüfsubjekte zählen die ausgewählten
Objekte der Primärlieferung.

Standardmässig sucht das Kommando in den Verzeichnissen der Primärdateien,
danach in denen der Referenzdateien und schliesslich in
`https://models.interlis.ch/`. Wiederholtes `--repository LOCATION` ersetzt
diese Auswahl in der angegebenen Reihenfolge. Ein lokales Verzeichnis ohne
Index wird nichtrekursiv nach `.ili`-Dateien durchsucht. Mit `--offline` stehen
lokale Quellen und passende Einträge im Standardcache `~/.interlis-cache`
zur Verfügung.

Wiederholtes `--model NAME` bestimmt die primären Modellwurzeln. Standardmässig
stammen sie aus den primären Transferheadern. Alle Dateien werden gegen
dieselbe kompilierte Modell-Closure geprüft. Mit expliziter Modellwahl muss
jeder Primärheader ein passendes Modell deklarieren. Die explizit gewählten
Modelle folgen der normalen Versionswahl des Repositorys. Der Reader prüft
die im XTF-2.3-Header gelieferte Version gegen das ausgewählte Modell.
Ein Primärbasket ausserhalb
der gewählten Topics wird abgelehnt. `--` beendet die Optionserkennung.

## Prüfumfang und Speicher

Der Standardumfang ist `supplied-dataset`. `--partial` prüft einen partiellen
Bestand. Beispielsweise kann ein fehlendes externes Referenzziel darin
offenbleiben. Nachweisbare Verletzungen werden in beiden Modi gemeldet.

`--storage memory` ist der Standard und hält bis zu 1 Million Speichereinträge
und 256 MiB logische Daten im Arbeitsspeicher. `--storage mvstore` und
`--storage file-pool` verwenden ein privates Laufverzeichnis mit bis zu
50 Millionen Einträgen und je 4 GiB für logische Daten und Dateigrösse.
Der File-Pool hält Schlüssel und Dateipositionen im JVM-Heap. Er hängt Werte
auch beim Überschreiben an die Datei an. Dadurch zählen frühere Werte
bis zum Schliessen zur Dateigrösse. Alle Speicher unterliegen den Datensatzlimits
von 1’024 Eingaben, 1 Million Baskets und 10 Millionen Objekten.
`--temp-dir DIRECTORY` wählt für die dateibasierten Speicher ein bestehendes
Wurzelverzeichnis. Standardmässig dient `java.io.tmpdir` als Wurzel.
Das Laufverzeichnis wird beim Schliessen des Speichers entfernt. Erreichte
Ressourcenlimits führen zu Exitcode 3.

`--budget NAME=N` überschreibt ein Ressourcenbudget für den aktuellen Lauf.
Der Wert ist eine positive Ganzzahl. Die Option lässt sich für verschiedene
Budgets wiederholen. Jeder Name darf einmal angegeben werden. Ohne die
Option gelten die folgenden Standardwerte.

| Name | Standard | Geltungsbereich |
|---|---:|---|
| `object-work` | 1’000’000 | Arbeitsgrenze pro Objekt für Traversierung, Rundung und lokale Prüfung |
| `area-components` | 600’000 | Komponentenbesuche je Durchgang für Koordinatenursprung und Aufbereitung einer AREA-Prüfung |
| `constraint-work` | 10’000’000 | Gemeinsame Arbeitsgrenze für datensatzweite Regeln, Views und AREA-Prüfungen beim Datensatzabschluss |
| `xtf23-object-events` | 1’000’000 | XML-Ereignisse je XTF-2.3-Objekt einschliesslich seiner verschachtelten Werte |

Bei einem Abbruch am XML-Ereignisbudget eines grossen XTF-2.3-Objekts kann
der Lauf beispielsweise mit `--budget xtf23-object-events=2000000` wiederholt
werden. Die Zeichen-, Tiefen- und weiteren XML-Sicherheitsgrenzen gelten weiter.

## Ausgabe und Exitcodes

Der Textmodus schreibt auf stderr. `--json` schreibt genau ein UTF-8-Dokument
auf stdout. `--verbose` meldet Laufhinweise auf stderr. Mit
`--max-diagnostics N` lässt sich die Anzahl ausgegebener Diagnosebeispiele
begrenzen. Der Standard ist 200. Die Gesamtzahl der Verletzungen bleibt
auch bei gekürzten Beispielen sichtbar. `--help`, `--version` und
`--version --json` benötigen keine Eingabedatei.

| Code | JSON-Status | Bedeutung |
|---|---|---|
| 0 | `passed` | Prüfung im gewählten Umfang abgeschlossen und ohne Befund |
| 1 | `rejected` | Fachliche Ablehnung eines Modells, Transfers oder Datensatzes |
| 2 | `usage-error` | Bedienfehler |
| 3 | `operational-error` | Betriebs-, Ressourcen-, interner oder Ausgabefehler |
| 4 | `cancelled` | Kontrollierter Abbruch |
| 5 | `incomplete` | Relevante Prüfung offen und keine Verletzung nachgewiesen |

JSON enthält `tool`, `version`, `status`, `exitCode`, `phase`, `completed`
und `inputs`. Die Liste `inputs` nennt erkannte Dateien mit `path`, `role`
und `format` (`xtf23` oder `xtf24`). Bei einem Datenlauf erscheint
`scope` als `supplied-dataset` oder `partial-dataset`.
`completed` bestätigt das Erreichen des Dateiendes aller Eingaben, den
Datensatzabschluss und das Schliessen der Ressourcen. Nach abgeschlossenem
Datenlauf folgen `objects` (geprüfte Primärobjekte), `violations`, `diagnosticsTruncated`,
`diagnostics` und `unchecked`. Ein Diagnosebeispiel enthält den Code und die
Meldung sowie bei Datenbefunden `arguments`, `location`, `ruleLocation` und
`relatedLocations`. Ein räumlich belegter Geometriebefund ergänzt
`geometryEvidence` mit `code`, `detail`, `location`, `relatedLocations` und
`witness`. Bei einem Lückenbefund enthält `relatedLocations` die weiteren
beteiligten Flächen mit ihren Randpfaden, einschliesslich eingeschlossener
Inseln derselben Lücke. Bei nativen XTF-AREA-Werten kann
`GEOMETRY_BOUNDARY_MISMATCH` abweichende Transferdaten einer geometrisch
gemeinsamen Kante melden. Der Punkt enthält `kind` und die Ordinaten `x` und
`y` als Dezimalstrings in Achsenreihenfolge und Einheiten des Modells. `kind`
unterscheidet `intersection`, `shared-boundary`, `contained-boundary`, `outside-boundary`,
`gap-boundary` und `vertex`. Ein berechneter Punkt kann gerundete Ordinaten
haben. Die Textausgabe nennt den Punkt als `XY=(x, y)` zusammen mit den
beteiligten Datenorten. Bei verletzten Geometrieconstraints beschreibt
`geometryEvidence` den konkreten Geometriekonflikt innerhalb der Diagnose
für die verletzte Regel.

Vor dem Datensatzabschluss meldet das Kommando die
betroffene Phase und die bis dahin verfügbaren Diagnosen. Ist `completed`
false, zählt `violations` nur die bis zur Unterbrechung beobachteten
Verletzungen. Die Versionsabfrage
verwendet den Status `ok` und die Phase `version`.

Bei nachgewiesenen Verletzungen und zugleich offenen Regeln gilt Exitcode 1.
Technische Fehler und kontrollierte Abbrüche führen auch nach bereits
beobachteten Verletzungen zu Exitcode 3 beziehungsweise 4. Schlägt das
Schreiben fehl, ist ein vollständiges JSON-Dokument unter Umständen nicht
mehr verfügbar.
