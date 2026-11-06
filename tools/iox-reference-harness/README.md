# IOX-Referenzharness P4.0-B

Der Harness führt die Legacy-Reader und den ilivalidator getrennt vom
Produktbuild aus. Er verwendet die offizielle Distribution von ilivalidator
1.15.0 mit iox-api 1.0.4, iox-ili 1.24.4, ili2c 5.6.8 und JTS 1.14.0.
Der [Lock](legacy-lock.json) bindet das ZIP sowie alle 14 JARs an SHA-256
und hält die von der CLI gemeldeten Quellstände fest. Der JVM-Classpath
verwendet die Reihenfolge aus dem Distributionsmanifest.
[Upstream-Release](https://github.com/claeis/ilivalidator/releases/tag/ilivalidator-1.15.0)

## Ausführen

Vom Repository-Root mit Java und javac 25 sowie Python ab 3.9. Für den
Download wird curl benötigt. Alternativ verwendet der Harness ein lokal
vorliegendes ZIP nach derselben Hashprüfung.

```sh
python3 tools/iox-reference-harness/run.py
python3 tools/iox-reference-harness/run.py --archive /absolute/ilivalidator-1.15.0.zip
python3 tools/iox-reference-harness/run.py --archive /absolute/ilivalidator-1.15.0.zip \
  --case read-24 --case validate-23-area-overlap --output /tmp/iox-reference-review
```

Standardmässig erzeugt der Runner ein frisches temporäres Verzeichnis.
Ein vorhandenes Ausgabeverzeichnis führt vor dem Lauf zu einem
Nutzungsfehler mit Exit 2. Jeder Fall kopiert sein
lokales Modell und seine XTF-Datei in ein eigenes Verzeichnis und startet
eine frische JVM mit `-Xmx2g`. Der Validator bekommt nur dieses lokale
Modellverzeichnis und ein leeres Plugin-Verzeichnis. Die Modellabschlüsse
der Testfälle benötigen keine Repositories.

`report.json` erfasst Befehle, Toolversionen, Eingabe- und Ausgabehashes,
Exits, ausgewählte und ausgelassene Fälle sowie die Messwerte. Die
Originalstreams stehen je Fall in `stdout.log` und `stderr.log`. Ihre
`logSha256`-Werte binden die Rohbytes dieses Laufs. Absolute Pfade,
Zeitstempel und Laufzeiten im Validator-Log führen bei Wiederholung zu
anderen Rohhashes. Für den Fallvergleich gelten Quell- und Eingabehashes,
Sollereignisse und die geprüften Logmerkmale.
Einzelaufrufe bleiben im Bericht als Teilprüfung sichtbar. Abweichungen
führen zu Runner-Exit 1. Exit 0 besagt, dass alle ausgewählten Fälle ihre
jeweilige Erwartung erfüllt haben. Dazu gehören korrekt abgewiesene
Negativfälle und ausdrücklich registrierte Legacy-Fehler.

## Vergleichsformat

Der Reader emittiert UTF-8 JSON Lines in IOX-Ereignisreihenfolge. Der Start
eines Transfers enthält die von IOX gelieferten Headerobjekte samt deren
synthetischen IDs. Ein Basket trägt Typ, BID, Modus, Konsistenz, Zustände,
Topicliste und Domänenbindungen. Ein Objekt trägt IOM-Tag, OID und davon
getrennte Referenzmetadaten. Das Feld `oid` gibt die vom Reader aus
XTF-`TID` gelesene IOM-Identität wieder. Verschachtelte IOM-Werte bleiben
rekursiv.
Primitive Werte bleiben Strings mit ihrer von IOX gelieferten Schreibweise,
einschliesslich Koordinaten und Bogenstützpunkten. Wiederholte Werte und
Geometriesegmente bleiben in ihrer gelieferten Reihenfolge. Attributnamen
und ungeordnete Metadaten-Maps werden für den Vergleich sortiert.
`kind`, `operation` und `consistency` sind die Legacy-IOX-Zahlen.
`orderPosition: 0` gibt den IOX-Default wieder. Quellpositionen und die
ursprüngliche XML-Attributreihenfolge liegen ausserhalb dieses Formats.

Der Validator wird über seinen ausgelieferten CLI-Einstiegspunkt gestartet.
Die Erwartungen prüfen Exit, Abschlussmeldung, beide Validierungspässe,
fallspezifische Log-Marker sowie die vollständige Menge der Error- und
Warning-Zeilen samt Häufigkeit. Nichtleere Zeilen ausserhalb der Präfixe
`Info: `, `Error: ` und `Warning: ` führen ebenfalls zum Fehler. Die
Reihenfolge der Diagnosen spielt im Vergleich keine Rolle.
Reader-Fehler werden separat durch die gelesenen
Teilereignisse und einen strukturierten IOX-Fehler auf stderr geprüft.

```sh
python3 -B tools/iox-reference-harness/test_run.py
```

## Externe Validator-Testsuite für XTF 2.3

Der [Fallkatalog](../../testdata/reference/suite-interlis/cases.json) erschliesst
223 Originalfälle aus [suite-interlis](https://github.com/geostandards-ch/suite-interlis).
Am Pin ist keine eigenständige Lizenzdatei verzeichnet. Lege den
gepinnten Checkout ausserhalb des Produktrepositorys an.

```sh
git clone --no-checkout https://github.com/geostandards-ch/suite-interlis.git /tmp/suite-interlis
git -C /tmp/suite-interlis checkout --detach f30711184d2374feacb81c5742382d35597164ca
python3 -B tools/iox-reference-harness/suite_inventory.py --checkout /tmp/suite-interlis
python3 -B tools/iox-reference-harness/test_suite_inventory.py
```

Die Inventarprüfung läuft offline. Sie prüft den Git-Pin, die SHA-256-Werte
der XTF- und Modelldateien, die Java-Assertions und die referenzierten
Ergebniszeilen der englischen Dokumentation. Die Pfade und Zeilen im Katalog
beziehen sich auf den gepinnten Checkout. Die Normabschnitte beziehen sich
standardmässig auf das [Referenzhandbuch 2.3 vom 13. April 2006](../../testdata/reference/norms/documents.json).
Historische Annahmen und Skips gehören zu den jeweils ausgewiesenen
Originalprofilen, darunter ilivalidator 1.11.14.

`phases` ordnet einen Fall dem XTF-2.3-Format (P4.2), der Datenvalidierung
(P4.4 und folgende) oder der Geometrie- und Topologieprüfung (G1 und
folgende) zu. Bei mehreren Phasen braucht jede ihren eigenen Nachweis.
Für P4.3 werden getrennte XTF-2.4-Fälle nach versionsspezifischer Prüfung
benötigt. Ein erfolgreicher Inventarlauf bestätigt Quellen und Erwartungen.

### Einzelne Originalfälle mit ilivalidator ausführen

```sh
python3 -B tools/iox-reference-harness/suite_run.py \
  --checkout /tmp/suite-interlis --archive /absolute/ilivalidator-1.15.0.zip \
  --case RXW.T02b --case RKo.T06c --output /tmp/suite-interlis-run
python3 -B tools/iox-reference-harness/test_suite_result.py
```

Der Runner verlangt das lokale ZIP aus [dem bestehenden Lock](legacy-lock.json).
Er prüft den Git-Pin und die Eingabehashes der ausgewählten Fälle. Pro Fall
startet er ilivalidator 1.15.0 mit `-Xmx2g`, einem isolierten Arbeitsverzeichnis
und ausschliesslich den geprüften lokalen Modellen. `--output` bezeichnet ein
neues Verzeichnis ausserhalb des Produktrepositorys und des Suite-Checkouts.
Dort stehen `report.json` und die gehashten Rohstreams je ausgeführtem Fall.
Ohne `--output` wird ein temporäres Verzeichnis angelegt. Pro Aufruf sind bis
zu zwölf IDs und pro Prozess bis zu 30 Sekunden vorgesehen.

Der Bericht trennt die Upstream-Erwartung, den Normbezug, die tatsächliche
Beobachtung und den Vergleich. Eine bestätigte Akzeptanz setzt beide
Validierungspässe voraus. Bei einer erwarteten Ablehnung müssen der
kontrollierte Prozessabschluss und die fallspezifische Diagnose
übereinstimmen. Für weitere Negativfälle kann der Runner den Prozess
beobachten, auch wenn die fachliche Ablehnung noch keinen Vergleichsmassstab
für 1.15.0 hat. Bei `RHE.T01a` endet 1.15.0 beispielsweise bereits bei der
Modellerkennung. Der JVM-Exit allein bestätigt die beabsichtigte
Headerprüfung nicht. Der Runner liefert Exit 0, wenn alle ausgewählten
Fälle `PASS` erreichen.

## Korpus und beobachteter Lauf

Die [Fallliste](../../testdata/reference/iox/cases.json) registriert die
eigens erstellten, kleinen INTERLIS-2.3- und 2.4-Modelle, Transfers und
Sollereignisse. Die Reader-Goldens decken POLYLINE mit geraden Segmenten
und ARC ab. Die AREA-Fälle mit SURFACE- und BOUNDARY-Elementen laufen
ausschliesslich im Validator. SURFACE, MULTISURFACE und BOUNDARY liegen
ausserhalb des Reader-Prüfumfangs.

[Der Beleg vom 6. November 2026](../../testdata/reference/iox/observed.json)
bindet den Lauf an Harness-, Modell- und XTF-Bytes. Die Toolchain-Prüfung
verlangt Java und javac der Hauptversion 25. Der Bericht hält die genauen
Versionsausgaben und die Plattform fest. Beobachtungen und Messwerte
gelten für den Lauf mit Temurin 25.0.2+10 und Python 3.9.6 auf macOS
arm64. In den protokollierten
Befehlen stehen `$REPO` und `$SCRATCH` für das Repository und den
temporären Belegbereich. Die zehn Fälle liefen bei gesperrtem Netzwerk
vollständig mit neun PASS und einem erwarteten XFAIL. Dabei bezeichnet
PASS bei einem Negativfall die bestätigte Zurückweisung.

| Fall | JVM-Exit | Beobachtung |
| --- | ---: | --- |
| `read-23` | 0 | Neun Ereignisse für zwei Baskets mit Objektidentitäten, eingebetteter Rolle mit REF und BID sowie geordneter Strukturliste |
| `read-24` | 0 | Sieben Ereignisse mit geordneter Standalone-Rolle und Polyline aus Koordinate, Bogen und Koordinate |
| `read-24-missing-bid` | 2 | Transferstart, danach `IoxSyntaxException` am Basket ohne BID |
| `read-24-standalone-bid` | 0 | XFAIL mit zusätzlichem REF ohne Ziel-OID |
| `validate-23` | 0 | Drei Objekte, Rollenprüfung und globale UNIQUE-Prüfung |
| `validate-24` | 0 | Drei Objekte und Zielprüfung beider Standalone-Rollen |
| `validate-23-duplicate-bid` | 1 | Doppelte BID in einem Transfer wird gemeldet |
| `validate-23-global-unique` | 1 | UNIQUE meldet für `item-2` den schon von `item-1` verwendeten Wert über Basketgrenzen |
| `validate-23-area-shared-edge` | 0 | AREA-Prüfung akzeptiert zwei Polygone mit gemeinsamer Kante |
| `validate-23-area-overlap` | 1 | AREA-Prüfung meldet zwei Schnittpunkte der überlappenden Polygone |

`read-24-standalone-bid` ist eine Robustheitsprobe. Die beschriebene
XTF-2.4-Rollencodierung führt REF und gegebenenfalls eine Ordnungsposition
(§§ 4.3.5, 4.4.4.7). Das Schemafragment für `RoleDef` (§ 4.4.5)
enthält ein laxes `anyAttribute`. Der Fall ergänzt eine BID bei genau
einer Standalone-Rolle. Der Reader
legt dafür zwei REF-Unterobjekte an und lässt beim zweiten die Ziel-OID
leer. Der XFAIL gilt ausschliesslich für diese gespeicherte Beobachtung
am Pin. Eine Änderung des Eventstroms verlangt eine erneute Bewertung.
[Normpins](../../testdata/reference/norms/documents.json),
[2.4-Reader im Pin](https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iom_j/xtf/Xtf24Reader.java#L1207-L1220)

Die reduzierte BID-Negativkontrolle wurde in 1.15.0 und in einer separaten
CLI-Probe mit 1.14.7 jeweils abgewiesen. Der Abschnitt
`historicalDuplicateBid` im Laufbeleg enthält den Befehl mit
Netzwerksperre, den Exit 1 sowie Versionen, Archiv-, Eingabe- und
Rohloghashes. `$HISTORICAL` steht für das Belegverzeichnis mit der
entpackten Distribution und den kopierten Eingaben. Die Originaldatei
des Berichts bleibt eine offene Reproduktion.
[ilivalidator #443](https://github.com/claeis/ilivalidator/issues/443)

## Messgrenzen

`wallMillis` umfasst den JVM-Prozess einschliesslich Start, Modellverarbeitung
und Ausgabe. `sampledPeakHeapBytes` erfasst den höchsten abgetasteten
Heap-Verbrauch über `MemoryMXBean`, `heapLimitBytes` das gemeldete
Heap-Limit. Jeder Fall prüft dieses Limit auf 2'147'483'648 Byte.
`sampledPeakTempBytes` summiert die logischen Dateigrössen im
isolierten Temp-Verzeichnis je Stichprobe und hält den höchsten Wert fest.
Beide Abtastungen haben ein Sollintervall von 5 ms. Kurzlebige Spitzen
können zwischen Stichproben liegen. Der dokumentierte Gesamtlauf
beobachtete 107.93 bis 154.47 ms Wandzeit, 12'712'112 bis 17'954'992
Byte Heap und bis zu 1'170 Byte temporäre Dateien.
Es sind Smoke-Messungen an kleinen Eingaben. RSS, native Allokationen,
physisch belegte Blöcke und das Verhalten bei grossen Datenmengen brauchen
eigene Messungen.

## Geometrie und Ressourcen messen

Der Benchmarkmodus verwendet ein lokales, hashgeprüftes ZIP und explizit
gewählte Fälle. Die [Profile](../../testdata/reference/iox/geometry-cases.json)
erfassen Prüfpunkte, Sollbefunde und Geometriezahlen. Sie enthalten
Gitter mit 100 und 1’000 Polygonen, gemeinsame Ränder, Rundung,
Selbstschnitt sowie Bogen- und `areAreas(ALL, …)`-Proben.

```sh
python3 -B tools/iox-reference-harness/run.py --benchmark \
  --archive /absolute/ilivalidator-1.15.0.zip --output /tmp/iox-geometry \
  --case area-grid-disjoint-1000 --case area-grid-shared-edges-1000
```

Jeder Aufruf startet die Fälle seriell in frischen JVMs unter `-Xmx2g`
mit beiden Validierungspässen und `--trace`. Standardmässig gelten pro
Aufruf höchstens acht Fälle und pro Fall 30 s sowie 64 MiB abgetasteter
Scratch inklusive Logs. `--max-cases`, `--timeout-seconds` und
`--temp-limit-bytes` setzen die Limits innerhalb fester Obergrenzen.
Der Runner prüft die Eingabegrössen vor dem Start. Bei einem Limitstopp
meldet er Grund, JVM-Exit und Bereinigung. Der Budgettest tastet logische
Dateigrössen ab. Zwischen zwei Stichproben kann die Belegung weiter steigen.
Rohlogs, GC-Log und Bericht bleiben im neuen Ausgabeverzeichnis ausserhalb
des Repositorys.

Wandzeit, Prozess-CPU und RSS umfassen den JVM-Start. Der abgetastete
Heap und die kumulierte GC-Zeit sind JVM-Messwerte. RSS und CPU stammen,
soweit verfügbar, aus dem OS-Kindprozessstatus. I/O-Mengen und tatsächliche
räumliche Kandidaten bleiben offen. Die erkannten `filesize`-Zeilen im
Trace geben Poolgrössen beim Schliessen an. Sie ergänzen die abgetastete
Temp-Spitze und den Temp-Endbestand.
`PASS` bedeutet weiterhin, dass der Lauf den Sollbefund erfüllt hat,
einschliesslich der erwarteten Zurückweisung eines Negativfalls.
