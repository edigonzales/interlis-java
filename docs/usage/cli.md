# Compiler-CLI

Die [Compiler-Anleitung](compiler.md) führt anhand von Beispielen in die Bedienung ein.

`java build/InterlisJvmBuild.java sdk-package` erzeugt den modularen
Launcher unter `target/jenesis-jvm/delivery/compiler/bin`. Im entpackten
SDK liegt er unter `cli/bin`. Windows verwendet `interlis-compiler.bat`.
`help` dokumentiert die verfügbaren Befehle und ihre Optionen.

```sh
target/jenesis-jvm/delivery/compiler/bin/interlis-compiler check \
  --root Survey --diagnostic-language de \
  testdata/models/base24.ili testdata/models/survey24.ili
```

## Befehle, Ein- und Ausgaben

`check` und `compile` prüfen die angeforderte Closure. Mit `--root` bleiben
zusätzliche lokale Dateien als mögliche Eingaben verfügbar. `analyze`
liefert den Analysestand und kann bei fachlichen Fehlern eine partielle
Sicht mit Diagnosen ausgeben. `diagram` unterstützt `--format mermaid`
(Standard), `--format plantuml` und `--format json`. Mermaid stellt geeignete
binäre Assoziationen als Kanten mit Rollen und Kardinalitäten an beiden Enden
dar. Assoziationen mit eigenen Attributen oder mehr als zwei Rollen erscheinen
weiterhin als Knoten. `version --json` unterscheidet Projekt-, API-, Protokoll-,
Diagnosekatalog-, Format- und Diagrammversion.

`version` zeigt in der ersten Zeile dieselbe Buildrevision wie
`check --verbose`. In `version --json` und im daraus erzeugten
SDK-Versionsmanifest steht sie im optionalen Stringfeld `gitCommit`.
Der vollständige SHA bezeichnet `HEAD` zum Buildzeitpunkt. Lokale Änderungen
sind darin nicht erfasst. Ohne gültige eingebettete Revision entfällt die
Git-Angabe. Die Textausgabe nennt zur Fehlersuche auch die laufende JVM
und die Parserherkunft.

Exitcodes sind 0 für den erfolgreichen Befehl, 1 für fachliche Ablehnung,
2 für Bedienfehler, 3 für interne oder operationale Fehler und 4 für Abbruch.
In `format --check` bezeichnet 1 auch einen Formatierungsbedarf.
`--json` schreibt bei den unterstützten Befehlen ein vollständiges
JSON-Ergebnis nach stdout. Textdiagnosen gehen nach stderr. Datenströme und
Dateien bleiben UTF-8 und frei von automatisch eingefügten ANSI-Sequenzen.

## Diagnosen und Farbe

`--diagnostic-language de|en` wählt die Diagnoseprosa. Default ist Englisch.
`--diagnostic-context` ergänzt bei `check`, `compile`, `analyze`, `diagram` und
`format` sichere Quellausschnitte, verwandte Fundstellen und Hinweise. Der
Diagnosecode steht am Ende der ersten Zeile. Quellframes zeigen bis zu drei revisionstreue
Zeilen mit höchstens 160 sichtbaren Spalten. Bei unsicherer Unicodebreite
ersetzt ein UTF-16-Hinweis das Caret. Steuerzeichen werden druckbar escaped.

`--verbose` zeigt bei `check`, `compile`, `analyze` und `diagram` die lokale
Suche, Repositorysuche, Modellauswahl und Cachezugriffe in der tatsächlichen
Reihenfolge auf stderr. Der Schalter ist mit `--diagnostic-context`
kombinierbar, hat bei `format` keine Wirkung und erzeugt bei `--json` keine
Suchmeldungen.

Bei diesen Modellbefehlen beginnt die Textausgabe mit `--verbose` auf stderr
mit der Compilerversion und, wenn beim Build verfügbar, der Git-SHA von
`HEAD`. Sie endet mit dem Laufstatus und der lokalen Uhrzeit
im Format `yyyy-MM-dd HH:mm:ss`. Die Abschlussmeldung unterscheidet Erfolg,
Fehler und Abbruch. `--json` und `--explain-config` schalten die Laufanzeige aus.
Quellorte in Textdiagnosen und Adressen in der Such- und Cacheanzeige stehen
in spitzen Klammern.

`--color=auto|always|never` oder `--color MODE` gilt für die fünf
Diagnosebefehle. AUTO färbt nur sicher interaktives stderr. Umleitung,
nichtleeres `NO_COLOR` und `TERM=dumb` deaktivieren AUTO. Unter Windows bleibt
AUTO konservativ farbfrei. ALWAYS und NEVER legen die Entscheidung explizit
fest. JSON, Format-/Diagramm-stdout und Ausgabedateien bleiben farbfrei.

## Repositories und Locks

Bei `check`, `compile`, `analyze` und `diagram` sucht der CLI-Host fehlende
Modelle standardmässig zuerst in den Verzeichnissen der angegebenen
`.ili`-Dateien. Die Suche erfasst benachbarte `.ili`-Dateien ohne Index
und steigt nicht in Unterverzeichnisse ab. Für weiterhin fehlende Modelle
folgt `https://models.interlis.ch/`. Ein Aufruf mit vollständig lokal
aufgelösten Modellen benötigt keinen Netzwerkzugriff. Mit `--offline`
verwendet der HTTP-Fallback nur verifizierte Cacheinhalte.

Eine explizite Repositoryliste ersetzt diese Standards. `--repository`
ist wiederholbar. `--repositories "aaa.xml;bbb.xml"` fügt mehrere Einträge
in der angegebenen Reihenfolge ein und lässt sich mit `--repository`
kombinieren. Jeder Eintrag ist ein Verzeichnis oder eine Basis-URL mit
`ilimodels.xml` oder ein direkter Pfad beziehungsweise eine URL zu einem
XML-Modellindex. Relative Modelldateien beziehen sich auf das Verzeichnis
des Indexes. Mit `--root` kann ein Modell auch ohne lokale Datei
ausgewählt werden. Zur Auswahl und Reproduktion dienen `--model-pin`,
`--source-pin`, `--read-lock` und `--write-lock`. Weitere Optionen sind
`--cache`, `--no-cache`, `--offline`, `--warnings allow|reject`,
`--config FILE` und `--explain-config`.

`--config` liest explizite UTF-8-Java-Properties bis 1 MiB. Unbekannte
und doppelte Schlüssel sind Bedienfehler. CLI-Werte ersetzen den jeweiligen
Dateiwert. `--explain-config` gibt die wirksamen Werte und ihre Herkunft als
JSON aus und redigiert Zugangsdaten und Queries. Lokale Pfade beziehen sich
auf das Arbeitsverzeichnis. Die [Repositoryregeln](../contracts/repositories.md)
beschreiben Indexauswahl, Cachefristen, Offlinebetrieb, Netzwerk und Locks.

Ein Lock dokumentiert die tatsächlich aufgelösten Quellen. Beim erneuten
Lesen eines Locks für die Standardsuche müssen darin erfasste benachbarte
Modelldateien weiterhin an ihrem gespeicherten Pfad verfügbar sein.
`check` und
`compile` schreiben ihn nach erfolgreicher Kompilierung, `diagram` nach
erfolgreicher Projektion und `analyze` nach abgeschlossener Analyse
einschliesslich fachlicher Diagnosen. Ausgabekollisionen mit Eingabe- oder
Repositoryquellen werden geprüft.

## Formatieren

`format` arbeitet auf lokalen Quellen und unterstützt keine
Repositoryoptionen. `--check` prüft Formatierungsbedarf, `--write` ersetzt
die gelesene Datei nach erneuter Inhaltsprüfung atomar. Der Formatter
verwendet den Solothurner Stil, Formatversion 2.0. Die Zielbreite
`--max-line-width` ist weich und liegt zwischen 40 und 240 Codepoints.
Standard sind 120. Der [Formatvertrag](../contracts/formatting.md) beschreibt
werttragende Texte, Zeilenenden und Sicherheitsregeln.

## Netzwerkeinstellungen

Die Repositorybefehle unterstützen `--connect-timeout SECONDS`,
`--read-timeout SECONDS`, `--request-timeout SECONDS|off`,
`--proxy HOST`, `--proxyPort PORT` und `--no-proxy`. Die Defaults sind
15 Sekunden für Verbindungen, 40 Sekunden für Leseinaktivität und kein
Requestgesamtlimit. Die JVM liefert die Proxywahl, sofern kein Modus
explizit gesetzt ist. Ein manueller Proxy verwendet standardmässig Port 80.

Die Properties verwenden dieselben Namen ohne führende Bindestriche.
Die CLI überschreibt die Datei. Auf derselben Ebene schliesst aktives
`no-proxy` einen Proxyhost und Proxyport aus. CLI-`--no-proxy` entfernt
Host und Port aus der Datei. CLI-`--proxy` setzt einen expliziten Host und
übernimmt gegebenenfalls den Dateiport. Ein Port allein erfordert einen
wirksamen Host. `--explain-config` zeigt die abgeleitete Proxywahl
`system`, `direct` oder `http` und ihre Herkunft. Die Validierung erfolgt
vor Quellenzugriff und Netzbeschaffung.

Der Standardcache liegt unter `~/.interlis-cache`. Eine abweichende
`--cache`-Ablage oder `--no-cache` gilt für diesen Prozess. Offline benutzt
ausschliesslich vorhandene verifizierte Inhalte. Ein fehlender erforderlicher
Cacheeintrag führt zu `OFFLINE` mit Quelle und Grund. Locks binden ihre
Bytes über den Digest und frieren ungepinnte lokale Projekttexte nicht ein.

## Einbettung und Ausgabegrenzen

Das Kommandoschema ist die gemeinsame Quelle für Parser und Hilfe. Es
prüft unbekannte oder doppelte Optionen, fehlende Werte und `--`. Die
geparsten Vorkommen sind geordnet und unveränderlich. Ein eingebetteter
Host übergibt Arbeitsverzeichnis, Home, Streams und Compiler-Dienste
explizit. Injizierte Streams bleiben beim Aufrufer. Der Host schliesst
seinen eigenen Lauf erst nach Ausgabe und Flush ab. Ein erfolgreicher
Compilerkindlauf kann deshalb zu einem operationalen Hostfehler führen.

Datei- und Lockziele werden vor dem Schreiben geprüft. Nach Auflösung
prüft der Host zusätzlich Kollisionen gegen die tatsächlich geladene
Closure. Der Ergebniswert listet fertiggestellte Artefakte in Reihenfolge
mit Art, URI und Bytezahl, auch wenn ein späterer Schreib- oder
Flushschritt scheitert. Eine vorhandene Gate-Ablehnung bleibt strukturiert
im Ergebnis erhalten.
