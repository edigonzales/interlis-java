# Positive Gegenproben für bewusst abweichende Referenzerwartungen

Diese 17 Dateien sind gezielt korrigierte Fassungen der in
`docs/evidence/conformance/normative-expectations.json` genannten ili2c-Testfälle
(Upstream `claeis/ili2c`, Commit `4ea207f388ef7d91b03d968cc1636f4de84e36ea`).
Die Originaldateien, Suite-Erwartungen und Quellenpins werden nicht geändert.
Die Herkunft jedes Falls, Original-/Kontrollhashes, Normstellen und exakten
negativen Diagnosen stehen im Manifest. Die Gegenproben gehören zum normalen
`conformanceCheck`; ihr Inhalt wird dabei nicht erneuert.

Änderungen gegenüber den Originalen:

- Zwei Laufzeitparameterfälle: `CONTRACTED` vor den Modellköpfen in 2.3.
- Sechs OID-Fälle: `CONTRACTED` vor den Modellköpfen in INTERLIS 2.3.
- Assoziation als Ziel: `OID` an `a2b`.
- FORMAT: Monate/Tage der beiden XMLDate-Grenzen zweistellig.
- Referenzattribut in 2.4: `OID AS INTERLIS.STANDARDOID` an `ClassA`.
- `assocComplete`: `OID` an `assocA3` und der übersetzten `assocB3`.
- `roleComplete`: auch die Geschwisterrollen in `assocA2p`/`assocB2p` verstecken.
- `expressions_ParentPathElement`: Zielattribut auf `TEXT*20` erweitern.
- `view_AcceptBasicViewProjectionDef`: Funktionsergebnis auf `TEXT*80` begrenzen.
- `iliconversion_CheckViewDefWithALLOF`: boolesches `attr3` statt numerischem `attr4` zuweisen.

- `expressions_ThisAreaThatArea`: nur das verbotene `ALL OF` der AREA-Inspektion entfernen; THISAREA und THATAREA bleiben erhalten.

Jede Korrektur erhält die übrige Modellstruktur. Damit darf eine Ausnahme keine
weitere Implementierungslücke im betreffenden Original verdecken. Die Dateien
sind Testdaten, kein vollständiger Nachweis der jeweiligen Sprachfamilie.
