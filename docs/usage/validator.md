# Validator-Kommando

Der JVM-Validator prüft in B8-1 eine primäre XTF-Datei mit der bestehenden
Datensatz- und JTS-Geometrieprüfung. Unterstützt werden XTF 2.3 und 2.4
gemäss den [Transferverträgen](transfer.md). Der Befehl erkennt das Format
am XML-Header. Die Dateiendung ist frei.

Der Modulbuild benötigt Java 25 und läuft mit Jenesis.

```sh
java build/jenesis/Make.java +interlis-validator-cli
```

Mit den Runtime-JARs auf dem Modulpfad lautet der Aufruf
`java --module-path <JAR-Verzeichnis> --module guru.interlis.validator.cli/guru.interlis.validator.cli.Main daten.xtf`.
Die separaten Launcher und das entpackbare ZIP folgen mit der Auslieferung.

Ohne weitere Optionen sucht der Befehl Modelle neben der Datenlieferung
und im Standardrepository `https://models.interlis.ch/`.
`--repository LOCATION` ersetzt diese Auswahl und kann mehrfach verwendet
werden. Ein lokales Verzeichnis ohne Index wird direkt nach `.ili`-Dateien
durchsucht. `--offline` unterbindet HTTP-Zugriffe.
Mit `--model NAME` lassen sich die primären Modellroots vorgeben.
XTF-Header und Modellzuordnung werden weiterhin geprüft.

`--help` und `--version` funktionieren ohne Eingabedatei.
`--verbose` zeigt zusätzlich das gewählte XTF-Format an.
Nach `--` wird ein Argument als Dateiname gelesen.

Diagnosen und Zusammenfassungen erscheinen auf stderr. Der Befehl liefert
0 bei abgeschlossener Prüfung ohne Verletzung, 1 bei fachlicher Ablehnung,
2 bei Bedienfehlern, 3 bei Betriebsfehlern, 4 bei kontrolliertem Abbruch
und 5 bei relevanten ungeprüften Regeln ohne nachgewiesene Verletzung.
