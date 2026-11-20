# Originalmodelle und Gegenproben für dokumentierte Sprachentscheidungen

Die [Originalmodelle](originals/) stammen unverändert aus
[ili2c, Commit `4ea207f`](https://github.com/claeis/ili2c/tree/4ea207f388ef7d91b03d968cc1636f4de84e36ea/test/data).
Die [JUnit-Tests](../../../interlis-compiler-cli/src/test/java/guru/interlis/cli/LanguageDecisionTest.java)
laden Originale und Gegenproben direkt. Die [Codeberg-Baseline](../baseline.json)
erklärt die Abweichungen im Kompilierausgang der externen Suite.

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
- `attribute_GenerateXMLSchemaWithTransientAttribute`: `#true` statt `#True` verwenden. Die vordefinierten BOOLEAN-Werte sind kleingeschrieben. Auch ein transientes Attribut verlangt einen typgerechten Ausdruck.
- `roadsExgm2ien`: `Rotation := INTERLIS.convertUnit(NamOri)` verwenden. Quell- und Zieltyp deklarieren Grad als getrennte konkrete Einheiten mit gemeinsamer Ableitung aus `INTERLIS.rad`. Die Kontrolle verwendet alle fünf Modelle, davon vier unveränderte Originale aus dem vorhandenen Roads-Fixturebestand.

- `expressions_ThisAreaThatArea`: nur das verbotene `ALL OF` der AREA-Inspektion entfernen; THISAREA und THATAREA bleiben erhalten.
- `attributeComplete`: CIRCULAR bei den zwei kontinuierlichen Unterteilungen ergänzen.
- `Constraint23Test.setCstrt`: die individuellen booleschen Werte in die WHERE-Filter verschieben; die beiden übersetzten Hauptausdrücke prüfen objectCount(ALL).
- `iliconversion_ConversionOfSETCONSTRAINT`: DEFINED(attr2) in den WHERE-Filter übernehmen; der Hauptausdruck prüft objectCount(ALL).
- `uniqueCstrtLocal`: in beiden Übersetzungen `BAG OF` verwenden, sodass der lokale Schlüssel über eine Unterstruktursammlung definiert ist.

Die beiden SET-Gegenproben folgen dem ausdrücklich bestätigten SET-Kontextprofil.
Die [Normauslegung](../../../docs/contracts/norm-decisions.md) erläutert die bestätigten Sprachentscheidungen.

Jede Korrektur erhält die übrige Modellstruktur. Damit darf eine Ausnahme keine
weitere Implementierungslücke im betreffenden Original verdecken. Die Dateien
sind Testdaten, kein vollständiger Nachweis der jeweiligen Sprachfamilie.
