# A2-Review Versuch 2: NACHARBEIT

## Urteil und Grenze

Der gezielte technische Recheck schliesst **A2-R1**. Der vollständige Schritt
A2 bleibt jedoch **NACHARBEIT**, weil **A2-R2, A2-R3 und A2-R4** unverändert
blockieren. Dies ist keine A2-Teilabnahme und keine Freigabe von Phase A, G2
oder P3.

Der explizite Modellparameter `gpt-5.6-sol` wurde beim Start dieses unabhängigen
Reviewers vom Tool akzeptiert; daraus wird keine Selbstauskunft zur
Modellidentität abgeleitet.

Massgeblich war
`build/reports/g2/A2/packet-v2/manifest.json` mit SHA-256
`ddcd565f085a2e117b1cd66baf2e18547868ca47e810faf9be7dbaeba9d993a5`
und After-Source-Digest
`0311fff8c0b676d3df0ebcbca18a6100942bb2de25af15575c89c21b7237a878`.

## A2-R1: geschlossen

Die Korrektur bindet jetzt die im ersten Review ungeschützten Felder an den
reproduzierbaren Nachweis:

- Der tatsächliche `step` jeder der 332 Legacy-Zeilen wird gegen die feste
  Zuordnung geprüft; jede vollständige Legacy-Zeile besitzt zusätzlich einen
  `mappingSha256`.
- Alle zehn `atomicRules` werden vollständig in den Scan übernommen. Gültige
  Schritte, Requirement-IDs, die drei nichtleeren Acceptance-Felder und die
  Normversionsstruktur werden geprüft.
- Kandidaten werden auf eine eindeutige Testklasse und, falls angegeben, eine
  vorhandene `@Test`-Methode aufgelöst. Die vollständige Kandidatenquelle wird
  gehasht.
- Fehlende Links erscheinen als `MISSING_CASE_LINK`; klassenweite Links als
  `CLASS_ONLY_NOT_AN_ASSERTION`. Beides wird nicht als Abnahme ausgegeben.
- Der versionierte Inventurtest vergleicht Legacy-/Atomic-Mappings,
  Kandidatenlinks und Quellenhashes mit der aktuellen Berechnung.

Meine eigene Gegenprobe umfasst zwölf Mutationen beziehungsweise
Sichtbarkeitsprüfungen. Eine falsche Legacy-Schrittzuordnung, unbekannte
Requirements, eine erfundene Klasse und eine erfundene Testmethode werden
abgelehnt. Änderungen an Legacy-Requirements, Negativfall und öffentlichem
Vertrag sowie an Atomic-Schritt, Requirements und Acceptance verändern den
gepinnten Output. Auch eine Änderung am Inhalt einer Kandidatenquelle verändert
den Quellenhash. Fünf fehlende und zwei klassenweite Kandidatenlinks bleiben
ausdrücklich als Nichtnachweis sichtbar. Das Ergebnis liegt in
`counterprobe-r1.json`.

Diese Schliessung belegt die Nachweisbindung und Fehlgrün-Abwehr. Sie erklärt
die Zuordnungen nicht fachlich vollständig richtig; diese Pflicht bleibt in
A2-R3 und A2-R4.

## Verbleibende blockierende Befunde

### A2-R2: offen

Die frühere Bezeichnung wurde korrekt auf 21 `standardFunctions` präzisiert,
und `otherPredefinedContents` steht ausdrücklich auf `MISSING_EVIDENCE`.
Versionsabhängige reservierte Wörter und Kontexttabellen sowie die übrigen
vordefinierten Typen, Einheiten, Klassen und Strukturen fehlen weiterhin als
einzeln versioniertes Regel-/Abnahmeinventar.

### A2-R3: offen

Die sachfremden Kandidaten für `NAME.case` und `STRING.singleLine` wurden
korrekt entfernt. Dadurch sind fünf Regeln ohne Falllink und zwei weitere nur
mit klassenweitem, ausdrücklich nicht beweiskräftigem Link sichtbar. Die zehn
Anfangsregeln benötigen weiterhin regelgenaue nummerierte und unnummerierte
Verträge, positive/negative Fälle und Modell-/Queryassertions. Die offene
TYPE-MODEL-Frage bleibt getrennt als Nutzerentscheidung markiert.

### A2-R4: offen

Der Pflichtumfang ist weiterhin nicht geliefert: Sachabsätze und normative
Anhänge beider Handbücher, unnummerierte Spezifikationsverträge,
Syntax-zu-Norm-zu-AST/Modellzuordnung, konkrete Fälle und die Revalidierung der
18 normativen Ausnahmen. TOC-Locator und ein grüner Scanner ersetzen diese
Abnahme nicht.

## Verifizierte Evidenz und Scope

- Manifest-, Archiv-, Norm-, Patch-, Vorreview- und Evidenzhashkette stimmt.
- Der Patch bildet 1083 Before-Dateien mit genau 20 Änderungen vollständig auf
  alle 1095 After-Dateien ab; Hashes, Grössen und Ausführbarkeitsbits stimmen.
- Der neue Scan ist byteidentisch zur versionierten Quellinventur
  (`8c2f82...fc41fc3`) und meldet weiterhin `MISSING_EVIDENCE`.
- Der Scan enthält 321 Grammatikproduktionen, 1218 Syntaxstellen, 289
  nummerierte Requirements, 21 Standardfunktionssignaturen, 332 Legacy-Zeilen
  und zehn Atomic-Regeln.
- Normative Erwartungen, Konformitätsbaseline, A1-Suiteprofil und Buildpins sind
  unverändert. Keine Produktionssemantik wurde geändert.
- Die acht neuen `areAreas2`/`areAreas3`-Fixtures samt Beobachtungen sind
  ausschliesslich als offene E2-Normfrage erfasst. Es gibt keine neue normative
  Erwartung, keine neue XFAIL-Ausnahme und keine Nutzerfreigabe.

## Ausgeführte Prüfungen

1. `g2_evidence.py verify --snapshot .../input-v2`: bestanden; erwarteter
   Source-Digest.
2. Vollständige Patchrekonstruktion und `audit.py`: bestanden; maschinenlesbares
   Ergebnis in `audit.json`.
3. `python3 -m unittest ... test_g2_inventory.py -v`: 11 Tests bestanden.
4. `JAVA_HOME=...25.0.3-tem ./gradlew g2EvidenceTest --rerun-tasks`: 20 Tests,
   `BUILD SUCCESSFUL in 1s`.
5. `g2_inventory.py` mit pypdf 6.10.0 und beiden gepinnten PDFs: bestanden;
   byteidentische Inventurausgabe.
6. `counterprobe_r1.py`: zwölf unabhängige R1-Gegenproben bestanden.

Ein vollständiger Compiler-/Portable-/Browser-/SDK-Phasenlauf wurde nicht
wiederholt: Der gezielte R1-Stand ändert keine Produktionssemantik, und A2 ist
wegen R2–R4 noch nicht phasenabnahmefähig. Es wurde keine Normentscheidung
getroffen. Ein neuer technischer Blocker wurde nicht gefunden.

Nächster Schritt ist die vollständige Schliessung von A2-R2, A2-R3 und A2-R4;
danach folgt ein neues unabhängiges A2-Gesamtreview. A3 bleibt bis dahin
nachgeordnet.
