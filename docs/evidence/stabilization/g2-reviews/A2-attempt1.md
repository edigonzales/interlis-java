# A2-Review: NACHARBEIT

## Urteil und Grenze

Der vollständige A2-Schritt ist **nicht abgenommen**. Das Urteil lautet
**NACHARBEIT**, nicht `WARTET AUF NORMFREIGABE`: Die Arbeit ist bereits vor den
18 späteren Nutzerentscheidungen technisch und inventarseitig unvollständig.
A2, Phase A, G2 und P3 bleiben offen.

Der technische Teilstand ist reproduzierbar und ehrlich als unvollständig
gekennzeichnet. Diese positive Teilbeobachtung ist keine A2-Teilabnahme. Das
Review ändert weder Normerwartungen noch Compilerquellen. Der explizite
Modellparameter `gpt-5.6-sol` wurde beim Start dieses unabhängigen Reviewers vom
Tool akzeptiert; daraus wird keine Selbstauskunft zur Modellidentität abgeleitet.

Massgeblich war
`build/reports/g2/A2/packet/manifest.json` mit SHA-256
`5e9a456747ef4af6aced45490aec148ff8fb95764acdab5b7d5cdeb92ca3d34e`
und After-Source-Digest
`1f6f01d45777cd8c13b5390e8619fc1ae705681159c800ef58034e425a4b6b6e`.

## Befunde

### A2-R1 – blockierend: Zuordnungen und Abnahmen sind nicht nachweisgebunden

`legacy_step()` berechnet die Legacy-Zuständigkeit aus der ID neu, vergleicht
sie aber nicht mit `rules[].step`. Der Normcheck prüft Dokument, Seite,
Seitenhash und Quote und übernimmt anschliessend nur die Regel-ID. Schritt,
Anforderungslinks, positive/negative/Modellabnahme und `candidateTests` der
`atomicRules` sind weder Bestandteil der Quellinventur noch Gegenstand einer
Invariantenprüfung.

Die eigene isolierte Gegenprobe änderte deshalb unbemerkt:

- `SYN:interlis2def` von B3 auf H3;
- `NORM:NAME.alphabet` von B3 auf H3;
- dessen Anforderungslinks auf `TEST-001`;
- die Negativabnahme auf `deliberately weakened`;
- den Kandidattest auf `NonexistentTest.weakened`.

`scan()` und der Zitatcheck blieben unverändert. Das Paketmanifest bindet den
eingefrorenen Iststand kryptografisch, belegt aber nicht die fachliche
Richtigkeit dieser Zuordnungen. Im nächsten Stand müssen Matrixschritte gegen
die feste Zuordnung geprüft, die vollständigen Atomregel-Mappings in den
reproduzierbaren Nachweis aufgenommen und ablehnende Mutationsfälle ergänzt
werden.

### A2-R2 – blockierend: weitere wirksame Sprachquellen fehlen

Die 21 `builtins` sind exakt die 21 Standardfunktionen aus
`BuiltinFunctions.java`. Als Zahl der Funktionen ist das korrekt, nicht als
vollständiges Builtin-Inventar. Es fehlen insbesondere:

- die versionsabhängigen reservierten Wörter und Kontexttabellen in
  `LanguageProfile.java` trotz des synthetischen Lexer-Tokens
  `tokens { RESERVED_WORD }`;
- vordefinierte Domänen, OID-Typen, Einheiten, Klassen und Strukturen, die unter
  anderem in `GraphBuilder`, `MetadataBindings`, `LineForms` und
  `PredefinedTemporal` aufgebaut werden, beispielsweise `ANYUNIT`, `NOOID`,
  `LineSegment` und `METAOBJECT`.

Diese Quellen benötigen einzelne versionsbezogene Inventare, Schritte und
Abnahmen für B1/B2/B3. Der Bericht sollte bis dahin ausdrücklich von
„21 Standardfunktionssignaturen“ sprechen.

### A2-R3 – blockierend: die zehn Anfangsregeln sind noch nicht konkret abgebildet

Die 20 PDF-Auszüge der zehn Regeln sind bytegenau gegen pypdf 6.10.0 geprüft.
Ihre Vertrags- und Fallzuordnung ist jedoch noch nicht belastbar:

- Alle zehn Regeln besitzen dieselben fünf generischen Requirements
  `SYN-004`, `SYN-013`, `META-006`, `TEST-011`, `TEST-012`; regelnahe Modell-
  und Semantikverträge für Übersetzung, Identität, Herkunft, Reihenfolge,
  Builtins und Sichtbarkeit fehlen.
- `COMMENT.nested`, `EXPLANATION.delimiter` und `MODEL.typeMembers` besitzen
  keinen Kandidattest.
- Der Kandidat für `STRING.singleLine` prüft Unicode-/Längenverhalten, aber
  keinen Zeilenumbruch, fehlenden Abschluss oder Escape-Fall.
- Der Kandidat für `NAME.case` prüft reservierte Tabellen und Mischschreibung,
  nicht getrennte Identitäten `A`/`a` oder eine falsch geschriebene Referenz.
- Die Negativabnahme von `MODEL.typeMembers` ist nur als ausstehende
  B2-Nutzerentscheidung formuliert und damit noch kein ausführbarer Fall.

Das sind konkrete Instanzen der bereits offen ausgewiesenen
Fall-/Assertionslücke. Jede Regel braucht regelnahe nummerierte und
unnumerierte Verträge, Original-/Gegenfälle und präzise Modell-/Queryassertions;
offene Nutzerentscheidungen bleiben davon getrennt.

### A2-R4 – blockierend: Pflichtumfang weiterhin offen

Der eingefrorene Bericht benennt zutreffend die noch fehlenden
Sachabsatz-/Anhangaudits beider Handbücher, unnummerierten Fach- und
Methodenverträge, Syntax-zu-Norm-zu-AST/Modellzuordnung, konkreten Fälle und die
Revalidierung der 18 normativen Ausnahmen. TOC-Locator und ein grüner Scanner
ersetzen diese Pflichten nicht. Daher kann das Gesamturteil nicht
`ABGENOMMEN` lauten.

## Verifizierter technischer Teilstand

- Paketmanifest, Patch, A1-/A2-Manifeste, Archiv, Berichte und beide Norm-PDFs
  stimmen mit ihren Hashes überein.
- Der Patch bildet 1075 A1-Dateien mit genau 12 Änderungen vollständig auf die
  1083 A2-Dateien ab; Hashes, Grössen und Ausführbarkeitsbits stimmen.
- Baseline und normative Erwartungen sind unverändert; keine der 18 bestehenden
  Ausnahmen wurde geändert oder freigegeben.
- Der Scannerlauf ist byte-identisch zum eingefrorenen
  `g2-source-inventory.json` (`711b4f...3c2e7`).
- ANTLR-generierte `ruleNames` bestätigen unabhängig 125 Parser- und 196
  Lexerproduktionen. Die 1218 Scannerstellen besitzen eindeutige IDs, gültige
  Bereiche und passende Texthashes.
- Alle 289 nummerierten Anforderungen stimmen in Menge und Reihenfolge mit dem
  abgeleiteten Kapitel-17-Index überein.
- Die 21 Standardfunktionen stimmen exakt mit den 21 funktionsbezogenen
  Matrixzeilen überein.
- Alle 332 aktuellen Matrixschritte stimmen im eingefrorenen Iststand mit der
  berechneten Zuordnung überein.
- Alle 20 Zitatreferenzen der zehn Atomregeln stimmen mit Seite, Seitenhash,
  Offset, Text und Quotehash in beiden PDFs überein.

## Ausgeführte Prüfungen

1. `g2_evidence.py verify --snapshot ../input`: bestanden, erwarteter
   Source-Digest. Die Variante mit `--sources` ist in der verschachtelten
   Extraktion nicht anwendbar, weil `git -C` ohne eigenes `.git` zum
   übergeordneten Arbeitsrepository aufsteigt; der direkte Vergleich aller
   1083 extrahierten Dateien gegen das Manifest bestand.
2. Patchanwendung auf A1 `input-v2` und vollständiger Datei-/Modusvergleich:
   bestanden, 12 Änderungen, 1083 After-Dateien.
3. `g2_inventory.py` mit beiden PDFs: bestanden, Ausgabe byte-identisch.
4. `python3 -m unittest ... test_g2_inventory.py -v`: 10 Tests bestanden.
5. `JAVA_HOME=...25.0.3-tem ./gradlew g2EvidenceTest --rerun-tasks
   --console=plain`: 19 Tests, `BUILD SUCCESSFUL in 2s`.
6. `./gradlew :interlis-compiler:generateGrammarSource --rerun-tasks`: bestanden;
   125/196 Regeln nachgezählt. ANTLR meldet die bestehende Warnung 154 für
   `viewDef`.
7. `counterprobe_atomic_mapping.py`: Mutation wurde **nicht** erkannt und
   belegt A2-R1. Ergebnis:
   `build/reports/g2/A2/review/counterprobe-atomic-mapping.json`.

Der Compiler-/Portable-/Browser-/SDK-Phasenlauf wurde nicht wiederholt: Das
A2-Paket ändert keine Produktionssemantik, und die Phase ist wegen des fehlenden
Inventars noch nicht abnahmefähig. Nächster Schritt ist die vollständige
Schliessung von A2-R1 bis A2-R4 in einem neuen eingefrorenen Paket; danach folgt
ein neues unabhängiges A2-Gesamtreview. A3 wird nicht vorgezogen.
