# A2-R2 Review: OPEN; A2 bleibt NACHARBEIT

## Urteil

**A2-R2 bleibt OPEN.** Das Inventar der 130 Anhang-A-/Funktionsdeklarationen und das Sprachprofil sind weitgehend vollständig und reproduzierbar. Zwei konkrete Inventarfehler verhindern aber den R2-Abschluss. **A2 insgesamt bleibt NACHARBEIT**; A2-R3 und A2-R4 sind weiterhin offen.

Der explizite Modellparameter `gpt-5.6-sol` wurde durch die übergeordnete Orchestrierung angefordert und akzeptiert. Daraus wird keine Modellidentität per Selbstauskunft abgeleitet.

Massgeblich war `build/reports/g2/A2/r2-packet/manifest.json` mit SHA-256 `ec594de117e26ce037ffa5b726c2ac81545648a594f42ef67d55e11431a3f3a8` und After-Source-Digest `2ec0ae2fc12a40688a2fa6ca4a14eb08028006c84dfedd91900890900989802b`.

## Blockierende R2-Befunde

### A2-R2-SPECIAL-SCOPE: Sonderwerte/-typen sind nicht versions- und normbezogen inventarisiert

Alle 21 Zeilen unter `specialValuesAndTypes` besitzen einen Schritt, Quellenanker und generische Abnahmetexte, aber weder ein explizites Sprach-/Versionsfeld noch eine Norm-/Regelverknüpfung. `g2_predefined.inventory` verlangt diese Felder ebenfalls nicht. Damit ist das im vorherigen unabhängigen Review verlangte Einzelinventar «versionsbezogen mit Quelle, Regel, Schritt und konkreter Abnahme» für diesen Teil nicht erfüllt.

Das ist nicht bloss redaktionell: Ohne Versions-/Regelbindung kann der Scan bei diesen Zeilen eine falsche Version oder eine nicht belegte Funktionsfamilie nicht erkennen. Für interne Deskriptorvarianten genügt eine explizite Verknüpfung zu den bereits katalogisierten Funktionsdeklarationen; ein vollständiger Absatz-Audit wird dadurch nicht aus R4 vorgezogen.

### A2-R2-OBJECT-ASSERTION: Geplante OBJECT-Abnahme widerspricht der aktuellen Factory

`BuiltinFunctions.Value.OBJECT` kommt in keiner einzigen aktuellen `SIGNATURES`-Deklaration vor. `BUILTIN_VALUE:OBJECT` plant trotzdem: «Inspect explicit signatures using OBJECT» und einen Negativfall gegen einen OBJECT-Formalparameter. Dieser Test ist im eingefrorenen Stand nicht ausführbar.

Genau hier liegt die offene Normfrage: INTERLIS 2.4, Kapitel 3.14 deklariert `areAreas2` mit `OBJECTS`; Anhang A deklariert `areAreas2` mit `OBJECT`. Die aktuelle Factory verwendet `OBJECTS`. Der vorhandene separate Normbeleg und `pendingUserDecision=BUILTIN_AREAS2_AREAS3_NORM_PROFILE` halten den Konflikt korrekt offen, aber die Sondertypzeile drückt diese Bedingtheit nicht aus und klingt wie eine bereits gewählte Erwartung.

Erforderlich ist keine Normentscheidung: Die Zeile muss den aktuellen Nullgebrauch festhalten, auf die ausstehende Nutzerentscheidung verweisen und ihre geplante Abnahme konditional formulieren. Die `areAreas2`-Zuordnung soll beide exakten Normstellen und die tatsächliche Factorywahl sichtbar verbinden.

## Positiv verifiziert

- Beide eingefrorenen Anhänge A wurden vollständig gegen den Katalog gelesen. Die 130 Zeilen decken 1 Modell, 2 Linienformen, 26 Einheiten, 16 Domänen, 10 Enumwerte, 8 Klassen, 12 Strukturen, 1 Topic, 1 Metadatenbasket, 2 Metaobjekte, 21 Attribute, 5 Parameter, 4 Constraints und 21 Funktionen ab. 121 Zeilen gelten für 2.3, 129 für 2.4.
- Das unabhängige Funktionsset stimmt: 13 Funktionen in 2.3, 21 in 2.4 und 21 Namen in der Factory. Nur im 2.4-Funktionskapitel stehen `areAreasWithoutGaps` und `areAreasWithoutGaps2`; nur im 2.4-Anhang steht `areAreas3`.
- Die 154 reservierten Wörter von 2.3 und 168 von 2.4 stimmen ohne Fehl-/Zusatzwort mit den gepinnten Tabellen überein. 13 Features, elf kontextabhängige Tokens, vier Klassifikationsbereiche, 19 Profilaufrufe und 13 `PredefinedOrigin`-Erzeuger sind sichtbar.
- Die Trennung ist erhalten: `SurfaceEdge`, `SurfaceBoundary` und `LineGeometry` sind als kontextgebundene Inspektionsstrukturen markiert; gewöhnliche Bindung und vollständige öffentliche Modellinhalte werden nicht behauptet. Diese sowie INTERLIS-/Zeitmodell- und NAME/URI/BOOLEAN-Lücken bleiben B1, nicht R2-Implementierungsarbeit.
- Keine Produktions- oder öffentliche API wurde geändert. Normative Erwartungen (`2ea28d…e5a9`), Konformitätsbaseline (`33893a…a59`), A1-Profil (`a28480…3289`) und Buildpins blieben unverändert. Es gab keine neue XFAIL-/Normentscheidung.

## Reproduzierbarkeit und Gegenprobe

Die Hash-/Patchkette ist vollständig: 1099 Before-Dateien, 1105 After-Dateien, genau 13 Änderungen, keine `ignoredGeneratedRemovals`; die Patchrekonstruktion stimmt in Hash, Grösse und Ausführbarkeitsbit mit dem After-Manifest überein. Beide Norm-PDF-Hashes stimmen.

Ausgeführt wurden:

1. Snapshotprüfung mit isoliertem Git-Index und `--sources`: PASS, erwarteter Source-Digest.
2. Direkte Python-Suite: 26/26 PASS.
3. `JAVA_HOME=...25.0.3-tem ./gradlew g2EvidenceTest --rerun-tasks`: 26/26 PASS, `BUILD SUCCESSFUL`.
4. `author_g2_predefined.py` mit Python 3.12.14 / pypdf 6.10.0: byteidentischer Katalog `a0781d…9c24c`.
5. `g2_inventory.py` mit beiden PDFs: byteidentischer Scan `d3ab51…cd1b8`.
6. Eigene Gegenprobe: fehlende kapitel-only-Funktion, falsche Version und fehlender Kontexttoken wurden erkannt. Sie deckte zusätzlich die 21 fehlenden Versions-/Normbindungen und den Nullgebrauch von `Value.OBJECT` auf. Artefakt: `counterprobe.json`.

Ein kompletter Compiler-/Portable-/Browser-/SDK-Phasenlauf wurde nicht wiederholt, weil keine Produktionssemantik/API geändert wurde und A2 nicht phasenabnahmefähig ist. Die spätere Prozessbeobachtung unter `build/reports/g2/A2/r2/binding-observations/report.json` wurde nur als B1-Grenzbestätigung gelesen; sie gehört nicht zum eingefrorenen R2-Nachweis.

## Nächster Schritt

Die beiden R2-Inventarfehler korrigieren und R2 erneut fokussiert prüfen lassen. Danach bleiben A2-R3, A2-R4 und ein frisches A2-Gesamtreview Pflicht; A3 bleibt nachgeordnet.
