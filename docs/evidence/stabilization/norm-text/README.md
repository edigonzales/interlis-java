# Unveränderte historische Textkoordinaten

Diese beiden Gzip-Dateien enthalten **keine neue Norminventur**. Sie erhalten
nur die bereits in `g2-source-inventory.json` aufgezeichneten Seiten des
Original-PDFs, extrahiert mit **pypdf 6.10.0**. Der Export für L0B-2 wurde vor
Entfernung der alten Werkzeuge gegen sämtliche 435 bestehenden Seitenhashes,
2721 Absatzbereiche und alle vorhandenen atomaren/Predefined-Zitate geprüft.
Beide Original-PDF-SHA und URLs stehen unverändert in `g2-rule-matrix.json`.

| Datei | Seiten | SHA-256 der Gzip-Datei |
|---|---:|---|
| ili23-pypdf-6.10.0.json.gz | 195 | `0fdf674b95c5a066a84d95efe43c2a43876de3c19cc7cdc69e9da42ad4ad2e26` |
| ili24-pypdf-6.10.0.json.gz | 240 | `9ef53d0b6f28427cf274df7e19596e8c9349bea5bea11b15fdf2797e7e7d4ac0` |

Das JSON enthält Dokumentversion, PDF-SHA, Extraktor, Einheit und exakte
Seitentexte. Die alten Offsets zählen **Unicode-Codepoints**, nicht UTF-16.
Es wird weder neu mit PDFBox extrahiert noch eine Gleichheit verschiedener
Extraktoren angenommen. Gzip-Mtime ist deterministisch null.

`./gradlew g2EvidenceTest` prüft mit Java/JUnit die Original-PDF-Bytes, Version,
Extraktorbindung, sämtliche gespeicherten Seitenhashes, exakten Bereiche und
Zitathashes. Die Originale liegen im Cache
`~/.cache/interlis-java/norms/ili{23,24}.pdf`; `INTERLIS_NORM_CACHE` kann ihn
ersetzen (relative Pfade beziehen sich auf das Repository). Fehlende PDFs werden
von den vorhandenen Original-URLs geladen; falsche Cachebytes scheitern.

Die Textdateien sind Lesebelege, keine fachliche Abnahme. Offene Normauslegungen,
Nutzerentscheidungen und die späteren G2-Nachweise bleiben offen. Alte
Generatorfassungen sind im Git-Basiscommit
`2d33c71c7afde03b8f43a7923e0eaeb1a69d9045` erhalten; sie sind keine aktive
Build- oder Reviewvoraussetzung.
