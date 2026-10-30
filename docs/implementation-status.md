# Implementierungsstatus interlis-java

Der aktuelle operative G2-Stand wird ausschliesslich in [docs/g2/STATUS.md](g2/STATUS.md)
geführt; massgeblich ist [docs/g2/EXECUTION_PLAN.md](g2/EXECUTION_PLAN.md).

Seit dem Nutzerauftrag vom 30.10.2026 wird die gesamte G2-Phase 2 autonom
bearbeitet: L2-1 (WP-22, AC2-01/02), danach L2-2 und L2-3 mit unabhängigen
OpenCode-/DeepSeek-Direktreviews und kumulativem T1/T2/T3-Gate. Der aktuelle
Abschnitt ergänzt die abhängige Funktionsargument-/Ergebnisbindung und
präzisiert EXISTENCE-Fehler. Implementierung, offene Profilentscheidungen und
tatsächliche Nachweise: [L2-1](g2/implementation/L2-1.md). Operativer Fortschritt
und unabhängiges Urteil ausschliesslich in STATUS. Die drei späteren
Profilantworten sind eingegangen; SET-Hauptausdrücke werden nun ohne
individuelle Objektwerte geprüft, WHERE und die beiden bestätigten
Kompatibilitätsprofile bleiben erhalten. Nachweise und Abnahme im Lieferbericht.
L2-1 hat am Produktcommit900706a den unabhängigen PASS für AC2-01/02 erhalten;
der autonome Auftrag läuft mit L2-2 (WP-23, Views/Inspektionen) weiter.
Der [L2-2-Lieferbericht](g2/implementation/L2-2.md) beschreibt das vererbte
AREA-Verbot, die versionsgerechte Rollenübernahme und die gemeinsame
Teilnehmernavigation samt öffentlichen Resultat-/Reihenfolge-/Importtests.
L2-2 wurde am Produktcommit30ef129 unabhängig mit PASS abgenommen; L2-3
führt die restliche Phase2 und ihre kumulativen Plattformgates durch.
[L2-3](g2/implementation/L2-3.md) ergänzt tatsächliche übersetzte Rollen-/
Metadaten-/Linienform-/Generic-Closures, die fehlende LineCoord-Quellabstraktion
und geerbte Assoziationsbasen. Die volle T1/T2/T3-Ausführung und unabhängige
kumulative Abnahme sind am Schlussprodukt d358e3c durchgeführt und belegen
AC2-01–07. Alle Pfade einschliesslich echtem Safari/Web Image, SDK/LSP,
Conformance und Pythonfreiheit bestehen; Bericht, Transportprovenienz und
aktueller Status stehen unter [STATUS](g2/STATUS.md). Phase3 wurde nicht begonnen.
L0A (AC0A-01–04; SRC-002/005, SYN-002/005/006/015, SEM-003) behebt den offenen
äusseren Blockkommentar. Implementierung und tatsächliche Prüfläufe stehen im
[Lieferbericht](g2/implementation/L0A.md); die unabhängige Abnahme bleibt separat.
Die folgenden Lieferabschnitte dokumentieren Implementierung und Nachweise;
der aktuelle operative Stand bleibt in STATUS.

L0B-1 (AC0B-01–03; BUILD-001/002/004/007/008, TEST-002/003/009/011/012)
ersetzt den Python-Conformancepfad durch den direkten externen Java-Runner und
JUnit-Verträge für die zusätzlichen Normprüfungen. Nachweise und Abgrenzung:
[Lieferbericht](g2/implementation/L0B-1.md), [Migrationsmatrix](g2/python-migration.md).
Operativer Fortschritt und unabhängige Abnahme bleiben in `docs/g2/STATUS.md`.

L0B-2 (AC0B-04–05; BUILD-001/004/007/008, TEST-002/003/009/011/012)
ersetzt die aktiven Python-Evidenz-/Normprüfungen durch Java/JUnit und entfernt
historische Autorenwerkzeuge. Quellen, Normpins, Zitatkoordinaten und offene
Abnahmegrenzen bleiben gebunden; **25** weitere Pythondateien sind entfernt.
Tatsächliche Nachweise: [Lieferbericht](g2/implementation/L0B-2.md).
Operativer Status und unabhängiges Urteil bleiben ausschliesslich in
[docs/g2/STATUS.md](g2/STATUS.md).

L0B-3 (AC0B-06–08, kumulativ AC0B-01–08; BUILD-001/004/007/008,
TEST-002/003/009/011/012) entfernt die letzten neun Pythonwerkzeuge und ersetzt
aktive SDK-/Referenz-/Corpus-/Messprüfungen durch Java/Gradle. Der vollständige
Gesamtlauf mit gesperrtem Pythonzugriff besteht einschliesslich realem Safari und
Web Image. Freigegebene Metadaten- und 2.3-OID-Kompatibilitätsfixes sind enthalten;
die sechs betroffenen Suitefälle bestehen, alle 18 Zusatzprüfungen bleiben aktiv.
Details, öffentliche Verträge und tatsächliche Zahlen:
[Lieferbericht](g2/implementation/L0B-3.md). Unabhängiges Urteil weiterhin separat
in [STATUS](g2/STATUS.md).

Phase 1 ist seit dem Nutzerauftrag vom 29.10.2026 durchgehend bis zur
unabhängigen Phasenabnahme beauftragt. L1-1 (AC1-01/02; SRC-002/005,
SYN-002/004/005/006/015, META-003/006/008/010/011/014/015, SEM-003/005/010,
TEST-003/009/011/012) ergänzt die gewöhnliche Bindung vordefinierter
Geometriestrukturen, kanonische INTERLIS-Modellowner und deren Import-/
Quellqueries. Die ausdrücklich freigegebene TYPE-MODEL-Kompatibilität erlaubt
Strukturen und abstrakte Klassen in beiden Sprachen; Topics bleiben verboten.
Der operative Status und unabhängige Teil-/Phasenurteile bleiben ausschliesslich
in [STATUS](g2/STATUS.md); Phase 2 ist seit dem neuen Auftrag in Bearbeitung.

Der unabhängige L1-1-Nachweis auf `6cbf8de422cd8c0c8c9ec1ea900d729053ea660f`
besteht nach den beiden dokumentierten Korrekturen für gemischte Snapshotsprachen
und die Linienformreihenfolge: 739 JVM-/136 Evidenz-/15 Assertion-/15 Hosttests,
3299 Fälle je portabler Laufzeit, T2-Projektprofil 580 PASS/13 XFAIL sowie 409
eigene Reviewerchecks. Der [unveränderte Bericht](g2/reviews/L1-1-review.md)
belegt diesen Teilabschnitt; die kumulative Phase-1-Abnahme erfolgte in L1-3.
Die drei inzwischen freigegebenen L1-2-Kompatibilitätsprofile und der operative
Fortschritt stehen in STATUS; die Unterteilungsimplementierung ist in L1-2 abgenommen.

L1-2 (AC1-03/04; SYN-004/010/013/015, SRC-002/005, META-006/008/009/011/014/016,
SEM-003/005/010/011/014/017/019, TEST-002/003/009/011/012) implementiert numerische
Unterteilungen über effektive Vorgänger und exakte Einheitenfaktoren sowie den
CONTINUOUS-/SUBDIVISION-Syntaxvertrag. Drei Kompatibilitätsprofile sind explizit
freigegeben. L1-2-Prüfungen: 753 JVM-/136 Evidenz-/15 Assertion-/15
Hosttests ohne Fehler/Skips, 3365 Fälle je portabler Laufzeit. Eine neue korrekte
Ablehnung im unveränderten T2-Positivfall ohne CIRCULAR wird als konkrete
19. strenge Referenzausnahme auf Grundlage der bereits bestätigten
CIRCULAR-Prüfung erfasst; die bisherigen 18 Einträge bleiben unverändert. L1-2 hat am Code-SHA
`39f48ca239c44b27a81e5562a58c717c90253534` den unabhängigen PASS erhalten;
[unveränderter Review](g2/reviews/L1-2-review.md), gleiche frische Testzahlen
und eigene Reviewer-Gegenproben. Die Phase-1-Abnahme erfolgte mit L1-3. Vollständige
Änderungen, Normstellen, Original-/Kontrollhashes, tatsächlich ausgeführte
Kommandos und Grenzen stehen im [L1-2-Bericht](g2/implementation/L1-2.md).

L1-3 (AC1-05–08; META-001/003/004/008/011/014/016,
SEM-002/003/005/007/008/009/010/011/017/019/020) korrigiert effektive
Besitzerpfade, FINAL-/EXTENDED-Prüfungen, gemeinsame Assoziationsattributregeln
und Topiczyklen. Eigener voller OS-gesperrter Lauf: Exit0,4m12s,71 Tasks,
765 JVM-/136 Evidenz-/15 Assertion-/15 Hosttests ohne Fehler/Skips; alle
portablen/Browser-/Safari-/Web-Image-Läufe je 3405 PASS, T2-Projekt579/14,
SDK 9 sowie SDK-Paket/LSP-Distribution. Die zwölf neuen öffentlichen
Regressionstests und 40 gemeinsamen portablen Fälle sind ausgeführt.
[Bericht](g2/implementation/L1-3.md). **Phase1 ist unabhängig mit PASS abgenommen**,
kumulativ AC1-01–08 auf `62c0870a58db677a796143aa8f89578f2e3d7f43`.
[Unveränderter Review](g2/reviews/L1-3-review.md): kompletter frischer
OS-gesperrter Lauf Exit0/4m20s/71 Tasks mit denselben Testzahlen und 436
eigenen Checks,14 rote Vorherbefunde am Basiscommit, keine Muss-Findings.
Keine fällige Phase-1-Prüfung ausgelassen. Phase2 wurde nicht begonnen;
globale D2-Revalidierung und G2-Gesamtfreigabe bleiben getrennt.

## Historisch: G2 A–I – Phase-A-Reviewpakete vorhanden; vollständiger A3-Vergleich und Abnahmen offen

Massgeblich ist der [Ausführungs- und Reviewvertrag](evidence/stabilization/g2-completion-plan.md).
Der kompakte [Phase-A-Status](evidence/stabilization/phase-a-dashboard.md) fasst Gates, Evidenz und offene Entscheidungen zusammen. Die vorhandenen A2-R3-/R4-/A3-Artefakte und Reviewpakete sind Zwischenstände; der vollständige A3-Testexport und semantische Vergleich fehlen. **A2/A3 sind nicht unabhängig abgenommen**, und 18 Normausnahmen sowie zwei Normprofile bleiben Nutzerentscheidungen.
A1 ist unabhängig mit angefordertem `gpt-5.6-sol` abgenommen. Sein vollständiger
Ausgangslauf umfasst 688 JVM-Tests, 3249 portable Fälle je Laufzeit/Browser und
574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS im Projektprofil. Safari, Web Image und
SDK/CLI sind darin enthalten. Der A1-Lauf bleibt die letzte unabhängig
abgenommene Sprach-/Plattformbasis.

Am 28.10. wurden die Plattformgates erneut vollständig ausgeführt:
**700 JVM-Tests**, **3249 portable Fälle** auf TeaVM-JS/WasmGC,
Chromium/Firefox/WebKit, **echtem Safari** und Web Image, alle **PASS**.
Nach den A3-Exporten sind es **3257 portable Fälle** (siehe unten).
Das ist aktuelle Regressionsevidenz, keine neue Sprachen- oder A2/A3-Abnahme.
[Gate-Nachweis](evidence/stabilization/verification-2026-10-28-g2-A2-platform-gates.json).

Die gepinnte externe Konformitätssuite wurde ebenfalls erneut ausgeführt:
**593 ausgewählt**, **574 PASS / 19 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**.
Die 19 XFAIL sind die 18 normativen Referenzausnahmen plus der separate
INTERLIS-1-Profilfall; ihre Nutzer-Revalidierung bleibt offen. Keine Erwartung
oder XFAIL wurde geändert.
[Konformitätsnachweis](evidence/stabilization/verification-2026-10-28-g2-A2-conformance.json).

**A2: Nacharbeit.** A2-R1 (Nachweisbindung der Zuordnungen) und jetzt A2-R2
(vordefinierte Inhalte/lexikalisches Einzelinventar) sind unabhängig geschlossen.
Das [frische R2-Review](evidence/stabilization/g2-reviews/A2-R2-attempt2.md)
bestätigt explizite Versions-/Regelzuordnungen, tatsächliche Signaturverwendungen
und die konditionale Darstellung des unbenutzten OBJECT-Zweigs. Beide exakten
2.4-Normstellen zu areAreas2 bleiben sichtbar, ohne Nutzerentscheidung zu ersetzen.
[Geprüfter R2-Nachweis](evidence/stabilization/verification-2026-10-27-g2-A2-R2-reviewed.json).

Der [Einzelkatalog](evidence/stabilization/g2-A2-predefined-inventory.md) enthält
130 vordefinierte Deklarationen/Mitglieder/Constraints, 21 Sonderwerte und
interne Typvarianten, 154/168 reservierte Wörter, 13 Featurezweige, elf
Kontextklassifikationen und deren Quell-/Normbezüge. Die reservierten Tabellen
stimmen mit den gepinnten Normen überein. **29 technische Tests** bestehen;
der Reviewer hat eigene Manipulationsproben ausgeführt. Das Inventar beweist
keine vollständige Implementierung oder fachliche Einzelabnahme dieser Inhalte.

16 [zusätzliche Bindungs-Prozesspaare](evidence/stabilization/g2-A2-predefined-binding-observations-2026-10-27.json)
sind mit vollständigen Quellen/Diagnosen festgehalten. Sechs Ergebnisse bei
gewöhnlichen SurfaceEdge-/SurfaceBoundary-/LineGeometry-Verwendungen weichen
ab (eigener Compiler ungebunden, ili2c akzeptiert). Das bleibt ein konkreter
B1-Folgepunkt. Beide Compiler lehnen qualifiziertes INTERLIS.NAME/URI/BOOLEAN ab;
daraus entsteht keine Forderung nach zusätzlicher Quellsyntax. Keine neue
XFAIL-Ausnahme und keine geänderte normative Erwartung.

**A2-R3: Nacharbeit ausgeführt, Review ausstehend.** Alle 22 atomaren Regeln
sind jetzt durchgehend als `PARTIAL_EVIDENCE` ausgewiesen; 27 Teilbelege
verweisen auf 15 tatsächlich ausgeführte JVM-Methoden. Neu sind dauerhafte
Vertragstests für Gross-/Kleinschreibung, Namensalphabet/-länge inklusive
Rename, String-Escapes/Zeilenenden/Recovery, Zeilen-/Block-/verschachtelte
Kommentare, Erklärungs-Delimiter, TYPE-MODEL-Queries, Übersetzungsordnung sowie
Import-, Numerik- und Property-Kontexte.
[Assertion-Evidenz](evidence/stabilization/g2-A2-R3-assertions-2026-10-28.json),
[Fortschrittsnachweis](evidence/stabilization/verification-2026-10-28-g2-A2-R3-progress-v3.json),
[Matrix](evidence/stabilization/g2-rule-matrix.json) und
[Quellinventur](evidence/stabilization/g2-source-inventory.json) sind
hashkonsistent; `--require-run-evidence` prüft die rohen XMLs.
**615 Compiler-Tests, 0 Fehler/übersprungen; die G2-Evidenzsuite umfasst aktuell 56 Tests.**

Das [neueste R3-Zwischenreview](evidence/stabilization/g2-reviews/A2-R3-review-2026-10-28.md)
urteilt weiterhin `NACHARBEIT`. Sein blockierender Fund
`A2-R3-RAW-CLASS-001` betraf einen **vor** der Korrektur eingefrorenen Input;
der Live-Stand prüft `testsuite@name` und `testcase@classname` und enthält den
Negativtest für „falsche Klasse, gleicher Methodenname“. Das ist keine
Selbstabnahme: ein frisches Paket mit dem korrigierten Werkzeug und ein neues
unabhängiges Review sind erforderlich. R3 bleibt bis dahin offen.

Bekannte, ausdrücklich offene Punkte: `A2-R3-COMMENT-NESTED-001` (ein äusserer,
nach einem geschlossenen inneren Block offener Kommentar wird akzeptiert; B3),
Property-Reihenfolge/-Ranges (B3), `TYPE_MODEL_STRUCTURE_BOUNDARY` (B2,
Nutzerentscheidung) und `NUMERIC_SCALING_PAIR_AND_CONSTANT_PROFILE` (C2,
Nutzerentscheidung).

**A2-R4: Sectioninventur und Bedingungsliste vorhanden, Ausführung offen.** Aus beiden
gepinnten Normen liegen jetzt **311 TOC-Abschnitte** und **2721 Absätze** mit
exakten PDF-Seiten-Slices, Start-/End-Offsets und SHA-256-Bindungen vor. Die
[Section-Crosswalk](evidence/stabilization/g2-norm-crosswalk.json) ordnet jeden
Abschnitt einem Schritt, einer späteren Phase oder ausdrücklich „erklärend“/
„ausserhalb“ zu: 77/78 Compilerregel-Abschnitte in 2.3/2.4, 1651
Compilerregel-Absätze, 385 spätere Ausführungsabsätze, 4 ausserhalb, 681
erklärend; kein Abschnitt ist `AUDIT_REQUIRED` oder `UNCLASSIFIED`.
[Absatzinventur](evidence/stabilization/g2-norm-inventory.json) und
[Fortschrittsnachweis](evidence/stabilization/verification-2026-10-28-g2-A2-R4-section-inventory.json)
sind hashkonsistent; **58 Evidenztests** bestehen.

Zusätzlich liegt eine [Bedingungsliste](evidence/stabilization/g2-norm-conditions.json)
vor: 777 Bedingungen, davon 369 Compilerregel-Bedingungen aus den 1651
Compilerregel-Absätzen, mit **1476 geplanten Fällen** (positiv, negativ, Modell,
Query). 19 dieser Bedingungen sind bereits mit den beobachteten Assertions der
22 atomaren Regeln verknüpft; 369 Compiler-Bedingungen tragen zusätzlich
`CANDIDATE_ONLY`-Testkandidaten, keine bleibt ohne Kandidaten. 245
vordefinierte Inhaltszeilen sind dem A2-Katalog zugeordnet; sieben erklärende
Fragmente sind ausgenommen; 163 nicht-Compiler-Bedingungen haben keine
Kandidaten.
Die übrigen Fälle bleiben geplant. Jeder
Compilerregel-Absatz gehört genau einer Bedingung; die
[Fortschrittsprüfung](evidence/stabilization/verification-2026-10-28-g2-A2-R4-conditions.json)
bestätigt Vollständigkeit und Hashbindung. Die Fälle sind ausdrücklich
**PLANNED**, nicht ausgeführt; für 19 Bedingungen sind Matrixregeln und
Teilbelege verknüpft, für die übrigen fehlen konkrete Matrixregeln,
Implementierungsorte und Ausführung. Damit ist R4 **nicht abgenommen**.

Für die **18 normativen Referenzausnahmen** liegt ein
[Nutzerentscheidungspaket](evidence/stabilization/g2-normative-decision-packet-2026-10-28.md)
mit beiden Normversionen, exakten Absatzauszügen, den unveränderten
Ist-/Referenzerwartungen und einer Empfehlung vor. Alle 18 Fälle bleiben
`PENDING_USER_REVALIDATION`; keine Erwartung, kein XFAIL und keine
Referenzfreigabe wurde geändert.
[Fortschrittsprüfung](evidence/stabilization/verification-2026-10-28-g2-A2-R4-decisions.json).

Für die **Grammatik** liegt eine eigene
[Occurrence-Inventur](evidence/stabilization/g2-grammar-inventory.json) vor:
321 Produktionen (125 Parser, 196 Lexer) und **1218 Alternativen/Quantoren**
mit Owner-Step, Matrix- bzw. Worklist-Regel und Requirements. Alle 1218
Vorkommen sind zugeordnet; die 196 Lexer-Produktionen sind bis zur
Matrix-Erweiterung ausdrücklich Worklist-Regeln. Ausführung und unabhängige
Abnahme sind offen.
[Fortschrittsprüfung](evidence/stabilization/verification-2026-10-28-g2-A2-R4-grammar.json).

Die **21 Standardfunktionen** sind in einer
[Builtin-Inventur](evidence/stabilization/g2-builtin-inventory.json) mit
Signatur, Ergebnis-/Argumenttypen, Version, Kontextregel und je vier geplanten
Fällen erfasst (84 Fälle). `areAreas2`/`areAreas3` bleiben
`USER_DECISION_PENDING`; alle übrigen sind `PLANNED_NOT_ACCEPTED`.
[Fortschrittsprüfung](evidence/stabilization/verification-2026-10-28-g2-A2-R4-builtins.json).

Die **Spezifikationsverträge** sind in einer
[Contract-Inventur](evidence/stabilization/g2-spec-contract-inventory.json)
erfasst: 289 nummerierte Anforderungen, davon 45 mit Matrixlink, **80
G2-Anforderungen mit vorgeschlagenem Owner-Step ohne konkrete Abnahme** und
**164 ausdrücklich ausserhalb G2**; zusätzlich zwölf explizit erkannte
unnummerierte Normzeilen. Die 80 offenen Abnahmen und die vollständige
Prosa-Auditierung sind ausdrücklich offen.
[Fortschrittsprüfung](evidence/stabilization/verification-2026-10-28-g2-A2-R4-spec-contracts.json).

Ein [Test-Kandidatenindex](evidence/stabilization/g2-test-candidate-index.json)
verbindet 369 Compilerregel-Bedingungen und 80 G2-Anforderungen mit 615
tatsächlichen JVM-Testmethoden: 669 Zeilen haben Kandidaten, 32 nicht
(369 von 369 Compiler-Bedingungen, 55 von 80 Anforderungen). Die
Kandidaten sind **CANDIDATE_ONLY**, keine Assertions- oder Ausführungsnachweise;
Methodenranges und -hashes werden gegen die Testquellen geprüft.
[Fortschrittsprüfung](evidence/stabilization/verification-2026-10-28-g2-A2-R4-test-candidates.json).

**Review-Pakete:** R3 liegt als
[v3-Paket](../build/reports/g2/A2/r3-20261028-packet-v3/manifest.json) bereit;
für die strukturelle R4-Inventur wurde ein
[R4-Paket v5](../build/reports/g2/A2/r4-20261028-packet-v5/manifest.json) und für
die A3-Gegenprüfungen ein
[A3-Paket v2](../build/reports/g2/A3/a3-20261028-packet-v2/manifest.json) mit
Vorher-/Nachher-Snapshot, Patch, Artefakt-/Werkzeughashes und Prüfauftrag
erzeugt. Die Patches reproduzieren den jeweiligen Nachherstand dateigenau.
Keines der Pakete erhebt eine Abnahme.

**A3: Gegenprüfungen portiert und gepaart.** Das
[Paar-Artefakt](evidence/stabilization/g2-a3-semantic-pairs.json) bindet die
drei JVM-Gegenprüfungen an ihre portablen Gegenstücke mit Quell-/Methodenhashes.
 Drei JVM-Gegenprüfungen für
verlorene/effektive Mitglieder, falsche Ziele und vertauschte
Korrespondenzreihenfolge bestehen. Acht neue portable Fälle sind in
`stabilization-contract.cjs` exportiert: gleichnamige Enum-Ziele, vertauschte
und passende Übersetzungsreihenfolge sowie die Sichtbarkeit eines geerbten
Mitglieds über die öffentliche Completion-Abfrage. **3257 Fälle** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image.
Die drei Gegenprüfungstypen sind als JVM-/portable Paare abgeglichen
(`COUNTERCHECK_PAIRS_MATCHED`). Keine neue Bridge-API, keine normative Änderung.
Die vollständige A3-Testexport-/Vergleichsabnahme und das unabhängige Review
fehlen weiterhin.
[Nachweis](evidence/stabilization/verification-2026-10-28-g2-A3-portable-counterchecks.json).

**Nächster Schritt: Ausführung und Zuordnung der geplanten Fälle**, beginnend
mit den lexikalischen und Modellkopf-Bedingungen sowie den Grammatikvorkommen;
parallel die 18 Referenzausnahmen und offenen Nutzerentscheidungen. Danach
A2-Gesamtreview, A3 und Phasenreview. Keine Sprachkorrektur wird vorgezogen.

**Phase A/G2/P3 offen.** Die 18 normativen Referenzausnahmen werden weiterhin
vom Nutzer revalidiert; der separate INTERLIS-1-Profilfall bleibt getrennt.
Historische 132 VERIFIED-Matrixzeilen sind Testevidenz, keine unabhängige Abnahme
unter diesem Plan. Normfreigaben erteilt ausschliesslich der Nutzer.

## Historischer Stand: E4 direkte Constraintnamen geprüft

Direkte benannte 2.4-Constraints teilen je Klasse/Struktur/Assoziation/View einen
Namensraum über alle Familien und same-topic CONSTRAINTS OF. Duplikate zeigen
auf den zweiten tatsächlichen Namen; externe Namen sind öffentlich abfragbar.
Die vorhandene Domainregel bleibt erhalten. Keine neue API/Klasse.

**688 JVM-Tests, 20 Wrappertests, 3249 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**.
24 direkte Prozesspaare erfüllen eigene Erwartungen, 14 stimmen fachlich mit
ili2c überein. Die zehn Duplikatannahmen der Referenz bleiben separat sichtbar;
keine neue Ausnahme oder geänderte Suitepins.

**Weiter selbständig:** E2 numerische Unterteilungen anhand effektiver Vorgänger
und vererbter Marker. Vererbte Constraintnamen, E1-Inhalte/Namenskategorien,
Skalierungs-/OID-Reste, weitere E3–E5-Regeln, Norminventar, Graphparität, reale
Closures, F/G und P3-Pflichten bleiben offen. **E/G2/P3 offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-27-g2-E4-constraint-names.json),
[Prozessvergleich](evidence/stabilization/g2-E4-constraint-names-reference-2026-10-27.json).

## Historischer Stand: E2 skalierte Typgrenzen geprüft

Ungültige Float-Mantissen in numerischen Typgrenzen werden mit exakten
Quellsyntaxdiagnosen abgelehnt. Nullmantissen, Vorzeichen, Präzision und
Lexeme bleiben erhalten. Domänen, Attribute, Koordinatenachsen, numerische OIDs,
FORMAT-Komponenten, Fragmente und importierte Editfolgen sind geprüft.

**683 JVM-Tests, 20 Wrappertests, 3224 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**.
76 direkte Prozesspaare erfüllen die eigenen Erwartungen, 74 stimmen fachlich
mit ili2c überein. Keine neue Ausnahme oder geänderte Suitepins.

**Weiter selbständig:** E4 Constraintnamen, E2 Unterteilungen/Skalierungsreste
und übrige E1–E5. Allgemeines Konstantenprofil und Exponentenverhältnis bleiben
separat normativ offen, ebenso vollständige vordefinierte Inhalte, OID-/
Namenskategorien, zusätzliche LineCoord-Attribute, Norminventar, Graphparität,
reale Closures, F/G und weitere P3-Pflichten. **E/G2/P3 offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-27-g2-E2-scaled-boundaries.json),
[Prozessvergleich](evidence/stabilization/g2-E2-scaled-boundaries-reference-2026-10-27.json).

## Historischer Stand: E1 vordefinierte Namensbindung geprüft

Qualifizierte und mit UNQUALIFIED INTERLIS eingeführte Namen verwenden
dieselben kanonischen Ziele. Domänen, Strukturen/Basen, Einheiten, FORMAT, OID AS
und Funktionen verwenden den gemeinsamen Binder. Mehrdeutige Userimports
werden nicht mehr durch eine Builtin-Funktion ersetzt. Keine neue API/Klasse.

**677 JVM-Tests, 20 Wrappertests, 3146 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**.
67 direkte Prozesspaare stimmen fachlich mit ili2c überein. Keine neue Ausnahme
oder geänderte Suitepins.

**Weiter selbständig:** E2 Bereichsmantissen, danach übrige E1–E5. Die Priorität
eines einzelnen Userimports gegenüber INTERLIS bleibt separat normativ offen,
ebenso vollständige vordefinierte Inhalte, OID-/Subdivisionregeln, zusätzliche
LineCoord-Attribute, Norminventar, Graphparität, reale Closures, F/G und weitere
P3-Pflichten. **E/G2/P3 offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-27-g2-E1-predefined-names.json),
[Prozessvergleich](evidence/stabilization/g2-E1-predefined-names-reference-2026-10-27.json).

## Historischer Stand: E5 Geometrie-Übersetzung geprüft

Identische geometrische Inspektionen erhalten geprüfte Korrespondenzen ihrer
Kontextkopien, Mitglieder und Constraints. Pfade, Restriktionen, Besitzer und
Geometrien werden vor der Ergänzung geprüft; verschiedene Vertexkontexte
bleiben getrennt. Explizite Toleranzen vergleichen ihren exakten Wert.

**670 JVM-Tests, 20 Wrappertests, 3077 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**.
36 Prozesspaare erfüllen die eigenen Erwartungen; 20 fachliche Referenzübereinstimmungen,
0 interne Referenzfehler separat. Keine neue Ausnahme oder geänderte Suitepins.

**Weiter selbständig:** E1 gemeinsame vordefinierte Namensbindung und E2
Bereichsmantissen, danach übrige E1–E5-Regeln. OID-/Subdivisionregeln, vollständige
vordefinierte Modellinhalte, Norminventar, Graphparität, reale Closures, F/G und
weitere P3-Pflichten bleiben offen. **E/G2/P3 offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-27-g2-E5-geometry-context.json),
[Prozessvergleich](evidence/stabilization/g2-E5-geometry-context-reference-2026-10-27.json).

## Historischer Stand: E2 benannte Collectiontypen geprüft

2.4 bindet benannte BAG-/LIST-Elemente als Domänen oder Strukturen. 2.3 bleibt
strukturgebunden; ANYSTRUCTURE und explizite Restriktionen bleiben strikt.
Öffentliche Typen, Navigation, Domain-Vererbung, Imports, Dateireihenfolge und
Fresh/Reuse-Editfolgen sind geprüft. Keine neue API oder Produktionsklasse.

**662 JVM-Tests, 20 Wrappertests, 3041 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**.
32 Prozesspaare erfüllen die eigenen Erwartungen; 29 fachliche ili2c-Übereinstimmungen.
Drei interne Referenzfehler sind separat ausgewiesen.
Keine neue Ausnahme oder geänderte Suitepins.

**Weiter selbständig:** E5 Geometriekontexte/Toleranzen, danach übrige E1–E5.
Allgemeine OID-Relationen und die separat reproduzierte MANDATORY-Erweiterung,
vordefinierte Modellinhalte, Norminventar, Graphparität, reale Closures, F/G und
weitere P3-Pflichten bleiben offen. **E/G2/P3 offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-27-g2-E2-named-collections.json),
[Prozessvergleich](evidence/stabilization/g2-E2-named-collections-reference-2026-10-27.json).

## Historischer Stand: E1 kanonische OID-Domänen geprüft

Vordefinierte OIDs haben je Sprachversion eine gemeinsame Identität und
vordefinierte Herkunft. Öffentliche Queries, Navigation, Trägertypen und die
verschiedenen 2.3-/2.4-Basen/Modifikatoren sind geprüft. Identische UUIDOIDs
aus importierter Funktionssignatur und lokalem Attribut sind kompatibel.

**658 JVM-Tests, 20 Wrappertests, 3007 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**.
28 Prozesspaare erfüllen die eigenen Erwartungen; 23 rohe ili2c-Übereinstimmungen.
Keine neue Ausnahme oder geänderte Suitepins.

**Weiter selbständig:** E2 benannte Collectiontypen; E5 Geometriekontexte und
Toleranzvergleich; danach übrige E1–E5-Regeln. Vollständige vordefinierte
Modellinhalte, allgemeine OID-Relationen, Norminventar, Graphparität, reale
Closures, F/G und weitere P3-Pflichten bleiben offen. **E/G2/P3 offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-26-g2-E1-oid-identities.json),
[Prozessvergleich](evidence/stabilization/g2-E1-oid-identities-reference-2026-10-26.json).

## Historischer Stand: E5 gebundene Übersetzungen geprüft

Übersetzungen vergleichen gebundene Deklarationen, Typen, Werte, Pfade und
Constraints. Volltext-Lexing, globale Namensersetzung und tiefenbegrenzte
Typformstrings sind entfernt. Domain-Constraints besitzen Korrespondenzen;
benannte Constraints behalten den tatsächlichen Namensbereich.

**652 JVM-Tests, 20 Wrappertests, 2977 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**.
82 Prozesspaare erfüllen alle eigenen Erwartungen; 77 rohe ili2c-Übereinstimmungen.
Keine neue Ausnahme oder geänderte Suitepins.

**Weiter selbständig:** E1 vordefinierte Identitäten/Namensauflösung; danach
übrige E1–E5-Regeln. Norminventar, Graphparität, reale Closures, F/G und weitere
P3-Pflichten bleiben offen. **E/G2/P3 bleiben offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-26-g2-E5-bound-translations.json),
[Prozessvergleich](evidence/stabilization/g2-E5-bound-translations-reference-2026-10-26.json).

## Historischer Stand: E5 Quell-Metadaten geprüft

Quell-Metaattribute, Dokumentation und Funktionserläuterungen werden strukturiert
erfasst, mit ursprünglichen Bereichen veröffentlicht und in Hover verwendet.
Konflikte sind Quellfehler. Übersetzungen prüfen CRS an den tatsächlich
zugeordneten Deklarationen und unterscheiden Erläuterungspräsenz vom Wortlaut.

**640 JVM-Tests, 20 Wrappertests, 2889 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**.
54 Prozesspaare erfüllen die eigenen Erwartungen; 28 rohe ili2c-Übereinstimmungen.
Zwei Fehler des ersten vollständigen Laufs wurden korrigiert und nachgeprüft.
Keine neue Ausnahme oder geänderte Suitepins.

**Weiter selbständig:** E5 vollständiger gebundener Übersetzungsvergleich;
anschliessend übrige E1-/E2-/E3-/E4-/E5-Regeln. Norminventar, Graphparität, reale
Closures, F/G und weitere P3-Pflichten sind offen. **E/G2/P3 bleiben offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-25-g2-E5-source-metadata.json),
[Prozessvergleich](evidence/stabilization/g2-E5-source-metadata-reference-2026-10-25.json).

## Historischer Stand: E1 Wort-/Primitivprofile geprüft

Die vollständigen reservierten Worttabellen (154 für 2.3, 168 für 2.4) steuern
Quelle, Fragment und Rename. Neue 2.4-Wörter bleiben in 2.3 legale Namen;
XML ist kontextabhängig, REFSYS-Literale gehören zu 2.4. Generated Boolean-Typen
tragen die Sprache ihres Besitzers, auch in gemischten Closures. Recovery-Lücken
bei fehlenden Namen/Typen führen nicht mehr zu Parserabstürzen. Die ANTLR-
Regeneration verhindert veraltete Tokennummern und erzeugt wiederholt gleiche
Lexer-/Parser-/Vokabularhashes. Keine neue öffentliche API-Signatur.

**629 JVM-Tests, 20 Wrappertests, 2833 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. Gesamtaufruf Exit 0 in 3m39s.
**574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**. 356 zusätzliche Prozesspaare erfüllen
alle eigenen Erwartungen; 292 rohe ili2c-Übereinstimmungen. Keine neue Ausnahme
oder geänderte Suitepins. Unterschiede bei Legacywörtern bleiben separat sichtbar.

**Weiter selbständig:** E5 Quell-Metaattribute/Dokumentation und gebundener
CRS-Übersetzungsvergleich; danach übrige E1-/E2-/E3-/E4-/E5-Regeln. Vollständige
vordefinierte Modellinhalte und weitere Sprachregeln bleiben offen, ebenso
Normmatrix, Graphparität, reale Closures, F/G und weitere P3-Pflichten.
**E/G2/P3 offen; die gesamte E-Folge bleibt freigegeben.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-25-g2-E1-lexical-profiles.json),
[Prozessvergleich](evidence/stabilization/g2-E1-lexical-profiles-reference-2026-10-25.json).

## Historischer Stand: E2 numerische Vollständigkeit geprüft

Unvollständige konkrete Numeric-/Coordinate-Deklarationen werden abgelehnt;
eigene ABSTRACT-Attribute, formale NUMERIC-Signaturen, erschlossene View-Typen,
LineCoord-Platzhalter und legale generische Koordinaten bleiben unterscheidbar.
Konkrete Grenzen dürfen auch nach Vererbung keine abstrakte Einheit behalten.
Fehlende explizite Einheiten-/Referenzziele veröffentlichen ErrorType mit den
tatsächlichen Diagnosen. Gemeinsame Aliasse werden nicht verändert.

**622 JVM-Tests, 20 Wrappertests, 2477 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. Gesamtaufruf Exit 0 in 3m25s.
**574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**. Alle 62 zusätzlichen Prozessfälle
stimmen mit ili2c 5.6.9 überein. Keine neue Ausnahme oder veränderte Suitepins.

**Weiter selbständig:** E1 versionsgebundene vordefinierte Identitäten und
Wortprofile; anschliessend übrige E2-/E3-/E4-/E5-Regeln. LineCoord-Verwendungen
und noch nicht festgelegte Koordinatendimensionen bleiben eigene offene Regeln.
Normmatrix, Graphparität, reale Closures, F/G und weitere P3-Pflichten sind offen.
**E/G2/P3 offen; die gesamte E-Folge bleibt freigegeben.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-25-g2-E2-numeric-abstraction.json),
[Prozessvergleich](evidence/stabilization/g2-E2-numeric-abstraction-reference-2026-10-25.json).

## Historischer Stand: E2 FORMAT-Relationen geprüft

Compiler und öffentliche Typabfragen verwenden dieselben FORMAT-Regeln für
Layoutpräfixe, gebundene Komponenten sowie explizite, geerbte und verschachtelte
Grenzen. Die pauschale Override-Freigabe ist entfernt; inline deklarierte
Attributformate kennen ihre geerbten Identitäten bereits beim Typaufbau.
Ungültige Bindungen veröffentlichen ErrorType und echte Diagnose-IDs. Der
vorhandene FORMAT-Prüfer liegt gemeinsam in interlis-model. Auch eine leere
SurfaceBag-Konstante verwendet die gemeinsame UNDEFINED-Wurzelauswahl.

**614 JVM-Tests, 20 Wrappertests, 2415 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. Gesamtaufruf Exit 0 in 3m36s.
**574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**. 43 zusätzliche Prozesspaare erfüllen
alle eigenen Erwartungen; 33 rohe ili2c-Übereinstimmungen. Keine neue Ausnahme
oder veränderte Suitepins.

**Weiter selbständig:** numerische/Koordinaten-Abstraktion und explizite
Einheiten-/Zielbindungen; anschliessend übrige E1-/E3-/E4-/E5-Regeln. Die
ABSTRACT-Klasse-/Topic-Prüfung existiert bereits; weitere Familien bleiben
zu prüfen. Normmatrix, Graphparität, reale Closures, F/G und weitere P3-Pflichten
bleiben offen. **E/G2/P3 offen; gesamte E-Folge freigegeben.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-25-g2-E2-format-relations.json),
[Prozessvergleich](evidence/stabilization/g2-E2-format-relations-reference-2026-10-25.json).

## Historischer Stand: E4-Definiertheit geprüft

UNDEFINED und leere TEXT-Konstanten verwenden gemeinsame Typregeln. Syntax und
öffentliche Funktionssignaturen erhalten MANDATORY für Parameter und Resultate,
einschliesslich Domänenvererbung. Explizit undefinierte Pflichtargumente,
Attributableitungen und Grafikparameter werden abgelehnt. Öffentliche Typabfragen
erhalten die Pflichtigkeit benannter Domänen. Übersetzungen prüfen diese Flags.
UNDEFINED ist ein Wert, keine zulässige Typerweiterung. Die interne pauschale
Builtin-Sperre entfällt; offene Einzelregeln bleiben in der Matrix sichtbar.

**605 JVM-Tests, 20 Wrappertests, 2372 portable Fälle je Laufzeit** bestehen auf
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari und Web Image. SDK/CLI
und Diagramm bestehen. Gesamtaufruf Exit 0 in 3m35s.
**574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**. 84 zusätzliche Prozesspaare liefern
alle erwarteten eigenen Resultate; 60 stimmen roh mit ili2c überein. Keine neue
Ausnahme oder geänderte Suitepins.

**Weiter selbständig:** gemeinsame FORMAT-Relationen und numerische Abstraktion
in E2; dabei wird auch die leere SurfaceBag-Konstante auf dieselbe UNDEFINED-
Wurzelauswahl umgestellt. Anschliessend die übrigen E1-/E3-/E5-Regeln. Vollständige
Normmatrix, Graphparität, reale Closures, F/G und weitere P3-Pflichten bleiben
offen. **E/G2/P3 offen; die gesamte E-Folge bleibt freigegeben.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-25-g2-E4-undefined.json),
[Prozessvergleich](evidence/stabilization/g2-E4-undefined-reference-2026-10-25.json).

## Historischer Stand: E4-Funktionskontexte geprüft

Abhängige ATTRIBUTE-Argumente verwenden tatsächliche Aufrufargumente, auch bei
benutzerdefinierten Funktionen. areAreas und die String-/WithoutGaps-Varianten
prüfen gebundene Eigentümer und Werte; zusätzliche Stringpfade bleiben mit
Syntax, Identitäten, Quellbereichen und Wurzelabhängigkeiten öffentlich erhalten.
Fragmentparsing bewahrt UTF-16-/Escape-Bereiche und Lexerfehler; ein leerer Pfad
verursacht keinen internen Fehler. Die Schemaauswahl behält diese Bindungen.
convertUnit prüft gebundene Umrechnungsbeziehungen im Zielkontext, ohne Werte
zu berechnen. Numerische Konstanten behalten Einheiten und DeclaredOrigin;
PI/LNBASE bleiben symbolisch. Übersetzungen prüfen tatsächliche Funktionsziele.

**594 JVM-Tests, 20 Wrappertests, 2288 portable Fälle** auf TeaVM-JS/WasmGC,
Chromium/Firefox/WebKit, echtem Safari und Web Image bestehen. Vier zusätzliche
Querysitzungen insgesamt pro Laufzeit prüfen wirkliche Enum-/Funktions-/Pfad-
Identitäten. SDK/CLI/Diagramm bestehen. Gesamtaufruf Exit 0 in 3m37s.
**574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**. 90 zusätzliche Prozesspaare mit
allen erwarteten eigenen Resultaten, 51 rohe ili2c-Übereinstimmungen. Keine
neue Ausnahme oder veränderten Suitepins. Die ersten unpräzisen positiven
NUMERIC-Fixtures und ihre Ergebnisse bleiben als historischer Rohstand erhalten.

**Weiter selbständig:** allgemeine UNDEFINED-/Argumentregeln, E2 FORMAT-
Relationen/numerische Abstraktion sowie übrige E1-/E3-/E5-Regeln. Die neue
Abstraktionslücke ist reproduziert und ausdrücklich erfasst; keine Normausnahme.
Die gesamte E-Folge bleibt freigegeben. Vollständige Normmatrix, Graphparität,
reale Closures, F/G und weitere P3-Pflichten sind offen. **E/G2/P3 offen.**

[Fortlaufender Nachweis](evidence/stabilization/g2-E-progress.md),
[aktuelles Manifest](evidence/stabilization/verification-2026-10-25-g2-E4-function-contexts.json),
[Prozessvergleich](evidence/stabilization/g2-E4-function-contexts-reference-2026-10-25.json).

## Historischer Teilnachweis: G2 E1 – Funktionsprofil und Übersetzungsklasse geprüft (25. Oktober 2026)

Die beiden Funde `EMPTY_FUNCTION_PARAMETERS_23` und
`PREDEFINED_METAOBJECT_TRANSLATION` sind behoben und mit Modell-/Queryverträgen
geprüft. Leere Deklarations-/Aufruflisten folgen dem 2.3/2.4-Profil, auch in
STRICT/EDITOR und Ausdrucksfragmenten. Die vordefinierte konkrete Klasse
enthält zwei Pflichtattribute und zwei unabhängige gebundene UNIQUE-Schlüssel
mit Herkunft und Reihenfolge. Imports, Vererbung, Übersetzungen, Funktionsziele,
Sprachqueries und frische/wiederverwendende Edits sind geprüft.

Notwendige Grundlagenkorrekturen: vordefinierte Attribute werden bei
Überschreibungen nicht mehr übergangen; NAME/URI erhalten die festen Grenzen;
Struktur-/Collection-Overrides verwenden vorhandene gemeinsame Typregeln;
CLASS-RESTRICTION behält seinen Typ und die gebundenen Einschränkungen.
Die gemeinsame Profilprüfung ist gegen fehlende/recoverte Klammern abgesichert.
Keine neue Produktionsklasse und keine öffentliche Signaturänderung.

**502 JVM-Tests, 20 Wrappertests und 1848 portable Fälle** bestehen auf
TeaVM-JavaScript/WasmGC, Chromium/Firefox/WebKit, **echtem Safari** und Web-Image.
SDK-Beispiel, neun CLI-/SDK-Prozessprüfungen und Diagramm-/Adapterprüfungen
bestehen. Alle 54 zusätzlichen Prozesspaare stimmen mit ili2c 5.6.9 überein.
Der abschliessende Gesamtaufruf endet erfolgreich mit Exitcode 0. Ein früherer
Firefox-Timeout und ein Wiederholungsfehler mit fehlendem GraalVM-Argument
bleiben dokumentiert; unveränderte Browsergrenzen bestehen im Abschlusslauf.
Dies belegt keine Performance-Abnahme.

Die unveränderte eingefrorene Konformität bleibt **574 PASS / 19 XFAIL /
0 FAIL/ERROR/XPASS** (roh 574 PASS / 1 XFAIL / 18 FAIL). Keine neue
Referenzausnahme und kein umklassifizierter bisheriger Suite-FAIL: die zwei
behobenen E1-Funde lagen zusätzlich zur Suite vor. Die vier Originale sind
byteidentisch gepinnt. Die 18 normativen Referenzabweichungen benötigen weiterhin
unabhängige Prüfung; INTERLIS 1 bleibt separat.

**Offen und als Nächstes E1:** NOINCREMENTALTRANSFER, CHARSET und XMLNS
versionsgerecht abbilden und mit tatsächlichen Werten/Defaults/Herkunft im
öffentlichen Modell erhalten. Sechs Prozessproben belegen die fehlende
2.3-Abweisung; ein bestehender Akzeptanztest belegt deren Modellinhalte nicht.
Separat bleibt die Recovery-Klassifikation eines abgebrochenen gewöhnlichen
`f(` offen: es entsteht kein internes Fehlerresultat/Schema mehr, aber zusätzlich
zu Quellfehlern bisher INCOMPLETE_SUPPORT/MISSING_ATTRIBUTE_TYPE.

Die Regelmatrix enthält **238 Regeln: 37 VERIFIED / 1 IMPLEMENTED / 200 OPEN**;
sie ist weiterhin ein unvollständiges Normabdeckungsinventar. Übrige E1–E5-
Regeln, unabhängige Normentscheidungen, detaillierte Graphparität, F/G, reale
Closures und weitere P3-Pflichten bleiben offen.
**E1, E, G2 und P3 sind nicht abgeschlossen.**

[Umfang, Abnahme und verbleibende Ursachen](evidence/stabilization/g2-E1-functions-metadata.md),
[Verifikationsmanifest](evidence/stabilization/verification-2026-10-25-g2-E1-functions-metadata.json),
[Prozessvergleich](evidence/stabilization/g2-E1-functions-metadata-reference-2026-10-25.json),
[Fallkatalog](evidence/stabilization/conformance-open-cases.json).
Die folgenden Abschnitte dokumentieren frühere Läufe.

## Historischer Teilnachweis: G2 E1 – Bindungs-Teilumfang geprüft (25. Oktober 2026)

E ist begonnen und bleibt offen. Unbekannte INTERLIS-Domänen erzeugen nun
reguläre Quellfehler statt eines internen Fehlers. INTERLIS_1_DATE,
HALIGNMENT und VALIGNMENT sind tatsächlich gebundene vordefinierte Domänen
mit Typen, FINAL, Herkunft und Navigationszielen. FINAL-Domänen sind verwendbar,
aber nicht erweiterbar. Funktionen und ALL-OF-Domänen behalten ihre vollständige
Qualifikation; falsche Präfixe werden nicht mehr zugunsten lokaler Namen entfernt.
Die Sonderauflösung und Ersatzdomäne entfallen zugunsten des vorhandenen Binders.
Keine neue Produktionsklasse und keine öffentliche Signaturänderung.

**489 JVM-Tests, 20 Wrappertests und 1782 portable Fälle** bestehen auf
TeaVM-JavaScript/WasmGC, Chromium/Firefox/WebKit, **echtem Safari** und Web-Image.
SDK-Beispiel, neun CLI-/SDK-Prozessprüfungen sowie Diagramm-/Adapterprüfungen
bestehen. 26 zusätzliche Prozesspaare stimmen vollständig mit ili2c 5.6.9
überein; öffentliche Modell-/Typ-/Sprachqueries, Imports/Mehrdeutigkeit,
Quellenreihenfolge und frische/wiederverwendende Editfolgen sind geprüft.
Der abschliessende Gesamtlauf endet erfolgreich mit Exitcode 0.

Die eingefrorene Konformität bleibt bei **574 PASS / 19 XFAIL /
0 FAIL/ERROR/XPASS** (roh 574 PASS / 1 XFAIL / 18 FAIL).
Keine neue XFAIL-Ausnahme, keine geänderten Suitepins/Erwartungen und kein
umklassifizierter bisheriger Suite-FAIL. Die zusätzlichen E1-Fehler wurden
als reguläre Sprach-/Modellfehler behoben.

Die Grammatik-/Dienstfamilien sind E1–E5 zugeordnet; fünf konkrete
Bindungsregeln sind nachgewiesen. Die Matrix hat **229 Prüfregeln:
30 VERIFIED / 1 IMPLEMENTED / 198 OPEN**. Dies ist noch keine vollständige
Normabdeckung. Alle 19 bisherigen Builtins sind einzeln zu prüfen; zusätzlich
fehlen die zwei 2.4-Builtins areAreasWithoutGaps/areAreasWithoutGaps2.
Die widersprüchliche areAreas2-Signatur in Funktionskapitel/Anhang A bleibt
vor Freigabe zu entscheiden.

**Als Nächstes E1:** das 2.3-Verbot leerer Funktionsparameterlisten im
Versionsprofil umsetzen, danach METAOBJECT_TRANSLATION samt Attributen und
beiden UNIQUE-Regeln vervollständigen. Vier neue Prozessproben belegen diese
beiden offenen Ursachen; ihre gültigen 2.4-/Referenzkontrollen bleiben erhalten.
Die übrigen E1–E5-Regeln folgen in der vereinbarten Reihenfolge.
Modellklassen-/LineCoord-Abstraktion und CONTRACTED-Übersetzungsfrage bleiben
sichtbar. Die unabhängige XFAIL-Normprüfung, F/G, detaillierte Graphparität,
reale eingefrorene Closures und übrige P3-Pflichten bleiben offen.
**E, G2 und P3 sind nicht abgeschlossen.**

[Umfang und verbleibende Ursachen](evidence/stabilization/g2-E1-binding.md),
[Verifikationsmanifest](evidence/stabilization/verification-2026-10-25-g2-E1.json),
[Prozessvergleich](evidence/stabilization/g2-E1-binding-reference-2026-10-25.json),
[Fallkatalog](evidence/stabilization/conformance-open-cases.json).
Die folgenden Abschnitte dokumentieren frühere Läufe.

## Historischer Teilnachweis: G2 D – Signaturklasse nachgewiesen (25. Oktober 2026)

`GRAPHIC_RESPONSIBLE_SIGNATURE_CLASS` ist für den statischen Compilerumfang
behoben und geprüft. Die Verantwortung folgt dem Sign-Metaobjekt und den
verwendeten Parameter-Eigentümern im jeweiligen Zuweisungskontext. Zusätzliche
Pflichtparameter, geerbte Werte, engere Überschreibungen, Bedingungen und
ACCORDING-Fälle sind geprüft. Explizite METAOBJECT-OF-Beschränkungen bleiben auch
bei geerbten Werten verbindlich; andere METAOBJECT-Parameter behalten ihre
Kontexttypprüfung. Der vorhandene GraphicBuilder und die öffentlichen
Modellverträge bleiben tragend; Konstruktoren/Einstiegspunkte sind unverändert.

**482 JVM-Tests, 20 Wrappertests und 1742 portable Fälle** bestehen auf
TeaVM-JavaScript/WasmGC, Chromium/Firefox/WebKit, **echtem Safari** und Web-Image.
SDK-Beispiel, neun CLI-/SDK-Prozessprüfungen und Diagramm-/Adapterprüfungen
bestehen. 68 zusätzliche Prozesspaare mit ili2c 5.6.9 erreichen die festgehaltenen
eigenen Erwartungen; 44 Referenzresultate stimmen überein. Positive und negative
öffentliche Modell-/Querytests prüfen beide Versionen, Imports, Quellenreihenfolge,
Übersetzungen und gültig → ungültig → gültig mit frischer/wiederverwendender Analyse.

Die sechs unveränderten Originalquellen sind gepinnt. Die S-/Derived-Fassungen
werden akzeptiert; unvereinbare Other-Ziele bleiben Quellfehler. D war ein
zusätzlicher katalogisierter Sprachfund ausserhalb der 593 Suitefälle; es wurde
kein bisheriger Suite-FAIL umklassifiziert. Die vollständige Konformität bleibt
bei **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS** (roh 574 PASS / 1 XFAIL / 18 FAIL).
Der abschliessende Gesamtlauf endet erfolgreich mit Exitcode 0. Es gibt keine
neue XFAIL-Ausnahme und keine geänderte Referenzerwartung. Ein neuer
unabhängiger Prüfpunkt zur Vorwärtsbindung von METAOBJECT-OF-Zielklassen
(`GRAPHIC_METAOBJECT_PARAMETER_FORWARD_TARGET`) ist E1/E5 zugeordnet;
die deklarationsumgeordneten Kontrollen stimmen bei beiden Compilern überein.

Nächster Abschnitt ist **E**: die vollständige G2-Regelmatrix mit Normstellen,
Implementierung, öffentlichen Verträgen und konkreten Nachweisen abgleichen;
daraus E1–E5 in geplanter Abhängigkeit bearbeiten. Die vorgemerkten Fragen zu
Modellklassen, LineCoord-Abstraktion und CONTRACTED bei 2.4-Übersetzungen bleiben
dabei sichtbar. Die 18 Referenzabweichungen benötigen weiterhin unabhängige
Normprüfung; INTERLIS 1 bleibt separat. F/G, detaillierte JVM-/TeaVM-Graphparität,
reale eingefrorene Closures und die übrigen P3-Pflichten bleiben offen.
**G2 und P3 sind nicht abgeschlossen.**

[Umfang und Grenzen](evidence/stabilization/g2-signatures.md),
[Verifikationsmanifest](evidence/stabilization/verification-2026-10-25-g2-D.json),
[Prozessvergleich](evidence/stabilization/g2-signatures-reference-2026-10-25.json),
[Fallkatalog](evidence/stabilization/conformance-open-cases.json).
Die folgenden Berichte dokumentieren ihre jeweiligen früheren Läufe.

## Historischer Teilnachweis: 2.3-Toleranzfall als XFAIL geprüft (25. Oktober 2026)

`ili23.LineType23Test.overlapTooSmallFail` ist auf ausdrücklichen Nutzerwunsch
als Referenzabweichung XFAIL erfasst. Der unveränderte Referenztest erwartet
Ablehnung; die eigene Erwartung bleibt Annahme. Original und positive Kontrolle
müssen ohne Diagnosen erfolgreich sein; die gepinnte Nulltoleranz-Kontrolle muss
mit der exakten strukturierten Diagnose `OVERLAP_POSITIVE` scheitern.
Prozessfehler, Warnungen, Implementierungssperren oder geänderte Pins erhalten
keine Ausnahme. TEST-011/012 und der Wrappervertrag in Spec 13 sind angepasst.

Aktuell ausgeführt: **20 Wrappertests** und die vollständigen **593
Konformitätsfälle**. Ergebnis: **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS**;
der Gradle-Aufruf endet erfolgreich mit Exitcode 0. Der unveränderte Rohvergleich
bleibt bei 574 PASS / 1 XFAIL / 18 FAIL. Die 19 XFAIL bestehen aus 18
Referenzabweichungen und der separaten INTERLIS-1-Ausnahme.

Die unabhängige Normprüfung des Toleranzfalls bleibt offen. Die Klassifizierung
ist eine ausdrücklich beauftragte Ausnahme, keine fachliche Schliessung und
keine automatische Baselineänderung. Compilersemantik und ursprüngliche
Suiteerwartungen sind unverändert. Die früher geprüften 471 JVM-Tests und
1676 portablen Fälle wurden hier nicht wiederholt; alle zugehörigen
Compiler-/Runtime-Artefakthashes stimmen weiterhin mit dem C-Nachweis überein.
Die zusätzliche LineCoord-Abstraktionsfrage sowie D–G und die übrigen
P3-Pflichten bleiben offen. **G2 und P3 sind nicht abgeschlossen.**

[Nachweis und Grenzen](evidence/stabilization/g2-overlap-xfail.md),
[Verifikationsmanifest](evidence/stabilization/verification-2026-10-25-overlap-xfail.json),
[Fallkatalog](evidence/stabilization/conformance-open-cases.json).
Die folgenden C-/B-/A-Berichte dokumentieren ihre jeweiligen früheren Läufe.

## Historischer Teilnachweis G2 C: geprüft, Normentscheidung offen (25. Oktober 2026)

Die ursprünglichen Geometriefunde sind bearbeitet: effektive Vererbung vor
Ausdrucksbindung, gemeinsame öffentliche Erweiterungsregeln, exakte explizite
und symbolische Spurbreitentoleranz sowie vollständige kontextbezogene Inhalte
eigener Inspektionssegmente. Beide unveränderten Custom-Segment-Originale liefern
nutzbare Schemata. Importierte Einheiten werden vor Beziehungsprüfungen bei Bedarf
gebunden. API 0.2, Verbraucher, Spezifikation und Migration sind synchronisiert.

**471 JVM-Tests, 16 Wrappertests und 1676 portable Fälle** bestehen;
TeaVM-JavaScript/WasmGC, Chromium/Firefox/WebKit, **echtes Safari**, Web-Image,
SDK und neun CLI-/SDK-Prozessprüfungen sind aktuell nachgewiesen. 60 zusätzliche
Prozesspaare mit ili2c 5.6.9 erreichen die festgehaltenen eigenen Erwartungen;
38 Resultate stimmen überein. Die offenen normativen Bewertungen dieser Proben
sind separat dokumentiert und ergeben keine neuen Suiteausnahmen.

Der vollständige Konformitätslauf ergibt **574 PASS / 18 XFAIL / 1 FAIL /
0 ERROR / 0 XPASS** (roh 574 PASS / 1 XFAIL / 18 FAIL). Der einzige offene FAIL
ist `ili23.LineType23Test.overlapTooSmallFail`: Die zusätzliche 2.3-Präzisionspflicht
bleibt auf ausdrücklichen Nutzerwunsch **separat normativ zu prüfen; keine
automatische Ausnahme**. Originalerwartung, Suitepins und XFAIL-Liste bleiben
unverändert. Der vollständige Gradle-Aufruf endet deshalb regulär mit Exitcode 1;
ausschliesslich `conformanceCheck` scheitert im letzten Lauf. Er wird nicht als
erfolgreich bezeichnet.

Die Quellabstraktion zusätzlicher selbst deklarierter `LineCoord`-Mitglieder
ist als neuer unabhängiger Prüfpunkt E2/E3 vorgemerkt. Die beiden originalen
Custom-Segmente mit eigenen TEXT-Mitgliedern sind davon getrennt nachgewiesen.
17 bestehende Normausnahmen benötigen weiterhin die unabhängige Prüfung;
INTERLIS 1 bleibt separat. D (verantwortliche Signaturklasse) kann unabhängig
folgen; E–G, detaillierte JVM-/TeaVM-Graphparität, reale gefrorene Closures und
die übrigen P3-Verbraucher-/Plattform-/Performancepflichten bleiben offen.
**C ist nicht vollständig abgenommen; G2 und P3 bleiben offen.**

[Umfang, Normstellen und Grenzen](evidence/stabilization/g2-geometry.md),
[Verifikationsmanifest](evidence/stabilization/verification-2026-10-25-g2-C.json),
[Prozessvergleich](evidence/stabilization/g2-geometry-reference-2026-10-25.json),
[Fallkatalog](evidence/stabilization/conformance-open-cases.json).
Die folgenden B-/A-Berichte sind historische Teilnachweise.

## Historischer Teilnachweis G2 B (25. Oktober 2026)

**Damals 455 JVM-Tests, 16 Wrappertests und 1628 portable Fälle.**
`check`, SDK, neun CLI-Prozessprüfungen, TeaVM-JS/WasmGC, Chromium/Firefox/WebKit,
echtes Safari und Web-Image bestehen. Der eingefrorene Konformitätslauf bleibt
bei **575 PASS / 18 XFAIL / 0 FAIL/ERROR/XPASS**; roh 575 PASS / 1 XFAIL / 17 FAIL.
Die 18 XFAIL sind weiterhin 17 dokumentierte Normentscheidungen plus INTERLIS 1;
B hat keine Ausnahme hinzugefügt oder geändert. Die unabhängige Normprüfung
bleibt offen.

**G2 B implementiert:** Die beiden gepinnten Originalfunde
`VIEW_TRANSLATED_IMPORT_CLOSURE` (2.3/2.4) liefern korrekte View-Basen,
Attribute, Typen und öffentliche Übersetzungsbeziehungen. Importkorrespondenzen
folgen den tatsächlichen Bindungen, werden transitiv konfliktgeprüft vereinigt
und hängen nicht von unbenutzten Sprachvarianten ab. Identische übersetzte
Elementnamen in verschiedenen Modellen behalten ihre jeweiligen Ziele.
`ModelTranslation.sourceTarget/correspondences` und `TranslationLink` erhalten
getrennte Identitäten und Namen innerhalb der Vorab-API 0.2; Migration und
Vertragskapitel sind synchronisiert.

Notwendige Grundlagenkorrekturen: deklarierte Enum-Knoten werden mit zugeordnet;
ihre Ordinalwerte stammen aus dem effektiven Enum-Baum. Die drei unveränderten
Enum-Originale sichern die im ersten vollständigen B-Lauf aufgedeckte Regression.
Gleiche Quellreferenzen werden nicht doppelt veröffentlicht. Explizite
Originalmodellversionen werden im Compiler und Workspace-Resolver berücksichtigt;
unbenutzte andere Versionen blockieren nicht die gewählte Closure. Die
zugehörigen positiven, negativen und öffentlichen Tests laufen in beiden
Sprachversionen und mit umgekehrter Dateireihenfolge.
Auch EXTENDS auf vordefinierte Datumsdomänen und -strukturen verwendet jetzt
die normalen Bindungswege; geerbte FORMAT-Teile und Grenzen bleiben erhalten.
Ein öffentlicher JVM-Vertrag und acht weitere portable Fälle sichern Vererbung
und Fehlerpfad ab. Unbekannte `INTERLIS.*`-Objektbasen erzeugen jetzt
Namensfehler; es entsteht kein gültiges Schema mit verlorener Basis.

Die zusätzlichen Prozessvergleiche ergeben **27 erwartete eigene Resultate,
23 Übereinstimmungen mit ili2c 5.6.9 und keine erkannten Prozessfehler**. Die vier
Abweichungen betreffen unbenutzte Sprachvarianten und widersprüchliche transitive
Importzuordnungen; sie sind separat dokumentiert, keine neuen Suite-XFAIL.
Die zwei ursprünglichen Importfunde und drei Enum-Originale stimmen überein.

[Lieferung B und Provenienz](evidence/stabilization/verification-2026-10-25-g2-bindings.json),
[Prozessvergleich](evidence/stabilization/g2-translated-import-reference-2026-10-25-bindings.json),
[Fallkatalog](evidence/stabilization/conformance-open-cases.json),
[offene G2-Matrix](evidence/stabilization/g2-rule-matrix.json).

**Nächster beauftragter Abschnitt C:** effektive Geometrie vor Ausdrucksbindung,
versionsgerechte Präzision/Vollständigkeit und öffentliche Erweiterungsregeln,
anschliessend eigene Inspektionssegmente. In 2.4 ist eine Fläche ohne explizite
Toleranz legal; die implizite Spurbreitentoleranz bleibt symbolisch darzustellen.
Die weiteren Abschnitte D–G, der vollständige Einzelregelabgleich, detaillierte
JVM-/TeaVM-Graphparität und reale eingefrorene Modellclosures sind offen.
Die Matrix ist weiterhin ein unvollständiger Audit-Arbeitsstand, keine
vollständige Normabdeckung. **G2 und P3 bleiben offen.**
Ein unabhängiger [Modell-Scope-Vergleich](evidence/stabilization/g2-model-scope-probes-2026-10-25.json)
bleibt E1/E3 zugeordnet: explizite ABSTRACT-Pflicht oder nur fehlende
Instanziierbarkeit ausserhalb eines Topics. Es gibt dafür noch keine
Normentscheidung und keine neue Ausnahme.
Auch die [CONTRACTED-Übersetzungsprobe für 2.4](evidence/stabilization/g2-contracted-translation-probes-2026-10-25.json)
bleibt E1/E5 zugeordnet: Beide Compiler lehnen verschiedene Marker ab.
Strukturgleichheit und das seit 2.4 funktionslose Schlüsselwort müssen dafür
noch fallbezogen abgeglichen werden; 2.3 bleibt separat.


## G2-Stabilisierung: Lieferung A nachgewiesen, G2/P3 offen (25. Oktober 2026)

Der unveränderte vollständige Ausgangslauf vom 24. Oktober ist gesichert:
434 JVM-Tests, 1565 portable Fälle und 574 PASS / 19 XFAIL.
Die [G2-Matrix](evidence/stabilization/g2-rule-matrix.json) ist eine offene
Auditliste: 174 Produktions-/Prüfvertrags-/Resolverzeilen, davon zunächst zwei
konkrete Datums-/Zeitregeln aktuell nachgewiesen. Der vollständige Abgleich
aller Normabsätze und die Aufteilung zusammengesetzter Regeln sind noch offen.
Grammatik- und Klasseninventar gelten ausdrücklich nicht als Normabdeckung.

**G2 A implementiert und geprüft:** XMLDate/XMLTime/XMLDateTime haben gebundene
Strukturen, Attribute, Präzision, Einheiten und Referenzsysteme. Der gemeinsame
FORMAT-Prüfer ersetzt die pauschale Datumserkennung. 2.4-Zeit-Schlüsselwörter
binden dieselben Domänen; `TemporalType` entfällt innerhalb API 0.2.
In 2.3 bleibt `2010-1-1` gültig, in 2.4 gelten die expliziten Breiten.
Nur die falsche XMLDate-Referenzausnahme wurde entfernt; Originalquelle,
Suitepins und Referenzerwartung bleiben unverändert.

**Ausgeführt:** 442 JVM-Tests, 16 Wrappertests, SDK und neun CLI-Prozesse;
1593 portable Fälle auf TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtem Safari
und Web-Image. Vollständige Konformität: **575 PASS / 18 XFAIL,
0 FAIL/ERROR/XPASS** (roh 575 PASS / 1 XFAIL / 17 FAIL). Die 28 zusätzlichen
Zeit-Prozesspaare liefern 28 erwartete eigene Resultate und 18 Übereinstimmungen;
zwei Referenzprozesse enthalten nach Ablehnung verschachtelter Formate einen
internen Fehler. Diese Beobachtungen sind separat dokumentiert, keine neuen
Suiteausnahmen. Öffentliche Detailmodell-Queries laufen auf JVM; vollständige
semantische Graphparität auf portablen Laufzeiten bleibt G2-Nachweispflicht.

[Ausgangslauf](evidence/stabilization/verification-2026-10-24-g2-baseline.json),
[Lieferung A](evidence/stabilization/verification-2026-10-25-g2-A.json),
[Prozessvergleich](evidence/stabilization/g2-temporal-reference-2026-10-25.json),
[Fallkatalog](evidence/stabilization/conformance-open-cases.json).

**Damals nächste Lieferung B (inzwischen oben nachgewiesen):**
`VIEW_TRANSLATED_IMPORT_CLOSURE` in 2.3/2.4:
Korrespondenzen aus tatsächlich gebundenen Importzielen bilden, globale
Sprachvariantenwahl entfernen und öffentliche Übersetzungsbeziehungen ergänzen.
C–G (Geometrie/Inspektion, Signaturverantwortung, vollständige Sprachmatrix,
echte Overlays, Resolver-/Offlinebeschaffung) bleiben beauftragt und offen.
Die unabhängige Prüfung der 17 übrigen Normausnahmen bleibt offen;
INTERLIS 1 ist eine separate Profilausnahme. **G2 und P3 bleiben offen.**

## Historischer Stand vor G2


Aktuelle Teilabnahme **19 B1–B3-Originalfälle geschlossen**: Jeder bisherige
`REFERENCE_PASS_ACCEPTANCE_PENDING`-Fall ist mit seiner unveränderten Quelle
und konkreten öffentlichen Modell-/Querywerten abgeglichen. Hinzu kommen
Wiederverwendungs- und Diagrammprüfungen für alle 19 Originale. Dabei wurde ein
Grundlagenfehler der Deklarationsindexierung korrigiert: Reihenfolge und
ID-Vergabe sind getrennt; Modell- und Constraintanker stimmen mit der
Wiederverwendung überein, auch für eindeutig unveränderte unbenannte Constraints.
Die öffentliche API bleibt unverändert.

**434 JVM-Tests, 16 Wrappertests und 1565 portable Fälle** bestehen auf
TeaVM-JavaScript/WasmGC, Chromium/Firefox/WebKit, **echtem Safari** und
Web-Image. SDK-Beispiel und neun CLI-Prozessprüfungen bestehen. Eigener Compiler
und ili2c 5.6.9 akzeptieren alle 19 unveränderten Originale in **38 regulär
beendeten Prozessen**. Der vollständige Konformitätslauf bleibt bei
**574 PASS, 19 XFAIL, 0 FAIL/ERROR/XPASS**; roh **574 PASS, 1 XFAIL, 18 FAIL**.
Es gibt keine neue XFAIL-Ausnahme und keinen neuen Referenz-PASS: Geschlossen
wurde die explizite fachliche Abnahme der bereits zuvor akzeptierten 19 Fälle.
Die Zahl 19 dieser Abnahmen ist unabhängig von den 19 XFAIL.

[Einzelabgleich und Grenzen](evidence/stabilization/b1-b3-original-acceptance.md),
[Verifikationsmanifest](evidence/stabilization/verification-2026-10-22-b1-b3-originals.json),
[Fallkatalog](evidence/stabilization/conformance-open-cases.json).
Die historischen K6-Nachweise bleiben unverändert erhalten:
[Einheitenfälle](evidence/stabilization/k6-units.md),
[K6-Manifest](evidence/stabilization/verification-2026-10-22-k6-units.json).
Alle sechs zusätzlichen K6-Einheitenregressionen bleiben geschlossen.

Die 18 Normausnahmen sind technisch durch exakte Diagnosen und Gegenproben
abgesicherte Projektentscheidungen. Ihre unabhängige fachliche Zweitprüfung
bleibt offen; das separate INTERLIS-1-XFAIL bleibt bestehen. Eine grüne
Projektbilanz ist keine uneingeschränkte ili2c-Kompatibilitäts- oder
Normvollständigkeitszusage.

Als Nächstes folgen die separat vorgemerkten Sprachfunde: übersetzte Imports,
geometrische Toleranzpräzision und Vollständigkeit konkreter Flächentypen,
Inspektion eigener Kurvensegmente sowie die verantwortliche Signaturklasse
bei Wiederverwendung einer Basissignatur. Diese Lieferung hat keinen neuen
unabhängigen Sprachfall aufgenommen. Die übrigen Sprach-/Workspace-/LSP-/CLI-/
Korpus-/Plattform-/Performancepflichten aus S0–S6 bleiben bestehen.
**P3 bleibt offen.** Die älteren Abschnittsbilanzen unten sind historische
Teilnachweise; diese aktuelle Bilanz und der aktuelle Fallkatalog haben Vorrang.

Stand: **22. Oktober 2026**, laufende Stabilisierung S0–S6 gemäss Nutzerauftrag.
**P2 und P3 sind nicht vollständig abgenommen. G2 und G3 bleiben offen.**
Die früheren Aussagen «P2 abgeschlossen» und «P3 abgeschlossen» werden durch
diesen geprüften Zwischenstand ersetzt. Historische Berichte unter
`docs/evidence/p1`, `p2`, `p3` und `conformance` dokumentieren ihre damaligen
Versuche; sie sind kein aktueller Vollständigkeitsnachweis.

## 1. Status und Grenzen

`VERIFIED` gilt nur für den konkret genannten ausgeführten Testumfang.
`PARTIAL` bedeutet implementierte und teilweise geprüfte Änderungen bei offenem
Abschnitt. `OPEN` bezeichnet weiterhin notwendige Arbeit. Eine grüne Testanzahl
ersetzt weder die Normmatrix noch ein vollständiges Verbraucherprofil.

Bestehende Module und Fabriken bleiben erhalten. Die Vorab-API ist wegen
fachlich notwendiger typisierter Funktionssignaturen auf **0.2** angehoben;
Toolversion **0.2.0-SNAPSHOT**. Siehe [Migration](api-migration-0.2.md).
P4/P5, Generatoren, IlisMeta-Export, Legacy-Java-API und Veröffentlichung gehören
nicht zum laufenden Auftrag. Die früher akzeptierte Restlücke von 258
Javadoc-Warnungen bleibt von der P3-Vollständigkeit getrennt.

## 2. Abschnittsbilanz

| Abschnitt / Anforderungen | Tatsächlich umgesetzt | Offene Abnahme und nächste Arbeit |
|---|---|---|
| **S0 PARTIAL** — WP-27; SEM-002–005/010/014/018, META-002/010/014, TEST-001/004/011/012 | Modellbezogene Gatefilterung bei mehreren Modellen je Datei; explizite Implementierungslücke für eigene Inspektionssegmente; die geprüften K2–K5-Familien sind freigegeben. CLI meldet `P3_OPEN`. | Vollständige P0–P3-Zuordnung jedes Normfalls zu Implementierung und ausführbarem Nachweis fehlt. Weitere still unvollständige Konstrukte müssen geprüft werden. |
| **S1 PARTIAL** — INC-002/012/021, LSP-012/013, SRC-002–005, FMT-001–004, CLI-007 | Batch-Tombstones erst beim Commit; quell-/revisionsgebundene Positionsabfragen; Rename mit Referenzsegmenten und deklarationsgebundenem END-Namen; Lexerprüfung neuer Namen; Kommentare und Strings bleiben beim Formatieren erhalten; atomarer Dateiaustausch ohne nichtatomaren Fallback; Ausgabefehler ergeben keinen CLI-Erfolg. | Vollständige Bindungs- und Sichtbarkeitsbilanz für Rename; Fehlerpfade der Dateiersetzung und geschlossene Projektdateien; Portabilitätsnachweise für alle Fälle. |
| **S2a PARTIAL** — WP-20/22; META-/SEM-/SYN-P2-Verträge | K6 ergänzt exakte strukturierte Umrechnungsfaktoren, Nullfaktor-/Nullteilerdiagnosen, frühe skalare Einheitenvererbung und gemeinsame Erweiterungsqueries.  Strukturierte Funktionssyntax und `TypeId`-Signaturen; Arity, Typverträglichkeit und Ergebnistyp; expliziter Builtinkatalog mit geprüften Textfunktionen; gemeinsame Zuweisungsregeln und CONTRACTED nach Version. Domainbedingungen bleiben typisiert und geordnet erhalten. Externe Constraints binden nur vorhandene Ziele im eigenen Topic oder dessen Vererbungsweg. `OBJECT OF` und `OBJECTS OF` binden Klassen, ANYCLASS und Einschränkungen; Unterklassen, Objektergebnisse und Identitätsvergleiche nutzen gemeinsame Typregeln. Signaturreferenzen bleiben navigierbar; nächste lexikalische Scopes und Importidentitäten werden berücksichtigt. `THIS` behält nach einem Objektpfad dessen Ziel und kann keinen skalaren Pfad neu öffnen. `ALL` besitzt eine gebundene Auswahl, `objectCount` nutzt die gemeinsamen Typregeln. Objekt-/Skalarpfade behalten ihre Typen; ungelöste Pfade sind Sprachfehler. Signaturen und Pfadzwischentypen bleiben im ausgewählten Schema verfügbar. UNIQUE/LOCAL/EXISTENCE verwenden die gemeinsame Pfadbindung mit vollständigen Schlüsseln, WHERE, BASKET und Zielalternativen. Identitäten, Typen und Quellbereiche bleiben über ExpressionId zugänglich. EXISTENCE trennt Wertvergleich von Bereichszuweisung; weitere Ausrichtungs-/Blackbox-/Zeit-/Enum-Fälle, Singleton-Strukturen sowie LIST-/Koordinatenindizes sind geprüft. Effektive Enum-Vererbung und FINAL-Teilbäume, gebundene FORMAT-Teile samt Präzision, Skalierung, Auffüllung und verschachtelten Grenzen, typisierte OID-AS-Bindung/Vererbung/Übersetzung, versionsabhängige OID-Folgeregeln, abstrakte FORMAT-Komponenten samt effektiver Konkretisierung sowie ganze EXISTENCE-Collectionwerte sind im beschriebenen Teilumfang implementiert und geprüft. NO-OID-Regeln für Referenzattribute und Rollen unterscheiden 2.3/2.4, prüfen effektive Identität, Restriktionen und EXTENDED. Referenztypen erhalten Primärziel und Zielrestriktionen; Typabfragen und Pfade verwenden dieselben Bindungen. EXTERNAL und gebundene DEPENDS-ON-Ziele werden für alle Referenzrestriktionen und Collection-Elemente geprüft, ebenso geerbte Mitglieder und verschachtelte Strukturverwendungen. Topic-Abhängigkeiten werden geerbt; Zyklen sperren das Schema. MANDATORY-Überschreibungen übernehmen vor der Ausdrucksbindung die gebundene TypeId; skalare Typen, Referenzen, Funktionen, UNIQUE/EXISTENCE und Indexpfade nutzen dieselbe Bindung. Explizites NUMERIC wird nicht als ausgelassener Typ behandelt. Klassen mit Strukturbasis behalten ihre Basiskante; INTERLIS.REFSYSTEM behält den vollständigen Syntaxnamen. Importierte Domain-/Strukturtypen, Funktionssignaturen, Enum-Basen und FORMAT-Komponenten werden vor der Ausdrucksprüfung unabhängig von der Dateisortierung gebunden. Domainbedingungen bleiben geprüft; Typzyklen sind Fehler. Die globale Namenssuffixsuche ist entfernt; einwertige Enums bleiben von zusammengesetzten Einheiten unterscheidbar. Einheitenkomponenten, Kurzzeichen, EXTENDS- und Umrechnungsziele sind gebunden; Abstraktion, Komponentenverengung, Operatorfolge, Zyklen und doppelte Kurzzeichen werden geprüft. Importierte und vordefinierte Komponenten behalten öffentliche Identitäten; Numerikeinheiten nutzen dieselbe Bindung. | Vollständige Attributpfad- und Enum-Funktionsargumente, weitere Objekt-/Enum-/Geometrie-Builtins; vollständige Assoziations-/View-Typbeziehungen; alle Typfamilien und Einheiten; Vererbung von Domainbedingungen; allgemeine Strukturmengen-/Inspektionsschritte; übrige EXISTENCE-Typfamilien, vollständige Enum-/FORMAT-Relationen, weitere abstrakte FORMAT-Kombinationen, vollständige Referenzziel-/Topic-Abhängigkeitskombinationen; vollständige Strukturtypbindung und Collection-Elementfamilien; vollständige Constraints; gesamte Norm- und JVM/TeaVM-Matrix. Keine vollständige S2a-Abnahme. |
| **S2b PARTIAL** — WP-21/23; META-003/006/008/014, SEM-008/010/014 | Rollen-Zielmengen mit gebundenen OR-Basen und Restriktionen; Typprüfung aller möglichen Ziele und Diagrammkanten je Hierarchiewurzel. Gebundene Assoziationsbasis, effektive Rollen/Attribute mit Herkunft und stabiler Reihenfolge; übernommene Rollen- und Linkkardinalitäten. EXTENDED/HIDING, Ziel-/Kardinalitätsverengung, FINAL, EXTERNAL und widersprechende Geschwister geprüft. Externe Constraints behalten ihren deklarierenden Topic getrennt vom Objektkontext; lokale EXTENDED-Ziele werden über Identitäten angewandt. N-stellige und qualifizierte Ausdruckszugänge erhalten Rollen-/Assoziationsidentitäten; mehrwertige Rollen-/Referenzattributfortsetzungen bleiben Mengen und prüfen alle Zielalternativen. Strukturierte View-Formationen für Projektion, Join, Union, Aggregation und Strukturinspektion; gebundene Basen, Selektionen, Ableitungsausdrücke, ALL OF und effektive Mitglieder; View-Vererbung, Rollenpfade, abgeleitete Assoziationen und Diagrammkanten sind im Teilumfang geprüft. View-Überschreibungen erhalten MANDATORY/TRANSIENT/Domainidentität, geerbte Alternativen und den effektiven Vererbungspfad. MANDATORY nach ALL OF, AGGREGATES-Korrespondenz, importierte Typidentitäten und die Prüf-Closure von Übersetzungsoriginalen sind gezielt geprüft. Die 19 älteren B1–B3-Originale sind einzeln mit Modell-/Querywerten, Wiederverwendung, Diagramm und portabler Annahme abgeglichen. K4 ergänzt Standard-Geometrieinspektionen mit tatsächlichem Koordinatentyp, Segmenthierarchie, StartSegment-Bedingung und versionsgerechten Linienattributen. | Vollständige Überlagerungs-/Transferordnung und sämtliche Normkombinationen weiter prüfen; vollständige Constraint-/View-Pfadkombinationen prüfen. Benannte/Inline-Inspektionsfaktoren auf Strukturmengen, OF-Einschränkungen, Imports und Wiederverwendung sind geprüft. B1–B3 bleiben teilweise abgenommen: eigene Kurvensegmente in Inspektionen und weitere Vererbungs-/Übersetzungs-/Pfadkombinationen fehlen. Keine vollständige S2b-Abnahme. |
| **S2c PARTIAL** — WP-24/25 | K2–K5: Laufzeitparameter, CONTEXT/GENERIC/DEFERRED, Linienformen, Metadatenbaskets/Metaobjekte sowie Grafiken und Signaturparameter strukturiert gebunden. Numerische Referenzsysteme/Achsen, Unit-Parameter, vordefinierte Hierarchie und Übersetzungsidentitäten öffentlich repräsentiert; keine globale Quelltextsemantik für diese Bindungen. | Vollständige Metaattribut-/Overlay- und Übersetzungsabnahme einschliesslich übersetzter Imports; sämtliche Normkombinationen. |
| **S3 PARTIAL** — WP-26; INC-006–019/021/022, RES-001–012, SEM-011–014/016 | Deklarationsreihenfolge unabhängig von ID-Vergabe; eindeutige Modell-/Constraintanker bei Wiederverwendung; Originalfallvergleich inklusive aktueller/veralteter Navigationscursor. Parsing ausserhalb des Workspace-Monitors; SourceInventory übernimmt vorhandene Syntax; Cacheeinträge nach Quelle/Revision/Text/Parseoptionen; Entry-/Textgrenzen mit Eviction; bedingte Annahme; HTTP-Grössen-/Zeit-/Abbruchprüfung während des Empfangs; Abbruch/Arbeitsbudget in Semantikschleifen. | Typisierter Resolverkatalog und Auswahlpolicy; integrierte Hostbeschaffung, Offline/Locks; vollständige Invalidierungs- und Fresh-vs-Reuse-Matrix; gemessene Abbruchlatenz; vollständige Mess- und Invalidierungsbilanz. Parse-, Reuse-, Eviction- und Engineaufrufzähler sind für die ergänzten Fälle geprüft. |
| **S4 PARTIAL** — WP-31; LSP-001–016, INC-018/022, WASM/VIZ | LSP ignoriert alte Dokumentversionen, behandelt Löschungen und lädt beim Schliessen Disktext; begrenzter Compileexecutor. Browser trennt Source-/Workspacerevision und Worker-Generation, verwirft alte Resultate und gibt angezeigte Snapshots frei. | LSP-Analyse noch synchron im Dokumentmonitor; nur neuester Hintergrundauftrag, Konfiguration, geschlossene Dateien beim Rename, semantische Completion/Tokens; vollständiger Mehrdatei-/Konfigurationszustand beim Workerrestart und vollständige Handle-Retention fehlen. Insbesondere abgelehnte UI-Compiles und Benchmarkläufe müssen ihre Snapshots zuverlässig freigeben. |
| **S5 PARTIAL** — WP-30/33; CLI-001–007, SDK-001–003, COMPAT-001–003, API-001–006 | CLI-Flush-/Fehlerabschluss; API-Migration; SDK-Beispiel gegen gebaute JARs; neue Prozessberichte unter Build-Ausgaben. | ili2c-5.6.9-Prüfprofil samt Launcher, Modellbeschaffung, Optionen, Defaults, Streams/Exitcodes; Konfigurationspräzedenz und gemeinsamer Host-/Compilerlauf; Linux-/Windows-/macOS-Prozessmatrix. |
| **S6 PARTIAL** — WP-32/33; INC-017, TEST-007–010/013, WASM-015/018 | Konformität und Performance als releaseCheck-Abhängigkeiten; regulärer Prozessabschluss erforderlich; geprüfter Node-Hostpin 24.22.0 mit Versionstest; SIGKILL-Erfolgsersatz entfernt; alte Reports vor Contract-/Konformitätsläufen gelöscht; Korpusdateien gegen vorhandene Inhaltspins geprüft; Rohberichte unter build/. | Vollständiger eingefrorener geo.so.ch-Korpus mit Closure; Edit-/Retention-/Abbruchmessungen statt Seed-Benchmarks; alle Plattformnachweise und vollständige automatische Gateprüfung. |

## 3. Tatsächlich ausgeführte Prüfungen

Aktueller verifizierter Arbeitsstand: Basiscommit
`3820c6b5dead437cdaeb9569fbfe726abec25d6e` mit uncommitteten Änderungen.
Quellbaumdigest, Artefakthashes, Werkzeuge, exakte Kommandos und übernommene
Ergebnisdaten stehen im [Verifikationsmanifest](evidence/stabilization/verification-2026-10-22-b1-b3-originals.json).

| Prüfung | Tatsächliches Resultat und Aussagegrenze |
|---|---|
| `check` und `:interlis-cli:installDist` | **PASS**: 434 JVM-Tests, 0 Fehler, 0 übersprungen. SDK-Beispiel kompiliert und ausgeführt. Java 25.0.3/Gradle 9.8.1 auf macOS arm64. |
| TeaVM-JavaScript unter Node 24.22.0 | **PASS**: 1565 gemeinsame Funktions-, Domain-, Pfad- und Scopefälle; reguläres Prozessende. |
| `checkPortable` / TeaVM-WasmGC unter Node 24.22.0 | **PASS im geprüften Umfang**: 1565 gemeinsame Fälle und bestehender Bridgevertrag, reguläres Prozessende bei normaler Optimierung. Node 22.23.1 bleibt als V8-Hintergrundcompiler-Stillstand reproduziert. Der Hostpin 24.22.0 wird vor Tests geprüft; keine erzwungene Beendigung als Erfolgsersatz. |
| `checkBrowser`, TeaVM-Worker | **PASS im geprüften Umfang**: 1565 gemeinsame Semantikfälle sowie Editieren, Ablehnung eines defekten Modells, erfolgreiche erneute Kompilierung, exakte Dezimalwerte, Diagrammadapter und Worker-Neustart in Chromium, Firefox, WebKit und **echtem Safari**. Playwright 1.64.1 aus dem Repository-Lock; Safari 27.0 mit aktivierter Remote-Automation. Keine vollständige S4-/Retention-Abnahme. |
| Web-Image-Untersuchung | **PASS im geprüften Umfang**: realer Build mit Oracle GraalVM 25.0.3 und Binaryen 133; 1565 gemeinsame Fälle unter Node sowie Chromium/Firefox/WebKit, reguläres Prozessende. Kein Wechsel des Produktionspfads. |
| `conformanceCheck` | **PASS unter dokumentierten Projektentscheidungen**: 574 PASS, 19 XFAIL, 0 FAIL/ERROR/XPASS bei 593 ausgewählten Fällen; roh 574 PASS, 1 XFAIL, 18 FAIL. Die 18 Normausnahmen brauchen weiterhin die unabhängige Zweitprüfung. Keine neue Referenzausnahme oder Pinänderung in dieser Lieferung. |
| Originalprozessvergleich | **PASS**: 19/19 eigene JSON-Annahmen ohne Diagnosen und 19/19 ili2c-Annahmen, keine Prozessfehler/Timeouts, aktuelle Artefakthashes geprüft. |
| Wrapper | **PASS**: 16 Tests, einschliesslich Byte-/Pinabgleich der Originale. |
| `sdkProcessCheck` | **PASS**: neun reale Prozesse der neuen CLI auf macOS arm64. Dies belegt weder das ili2c-Prüfprofil noch Linux/Windows. |
| Vollständiges `releaseCheck`, Performance und Plattformabnahme | **OFFEN / nicht abgenommen**: kein vollständiger M1-Editor-/Batch-/Abbruch-/1'000-Edit-Nachweis, keine Linux-/Windows-Prozessmatrix, unvollständiger P2- und CLI-/LSP-Umfang. |

Der aktuelle kombinierte Gradle-Lauf endet regulär mit Exitcode 0. Die rohe
Referenzablehnung bleibt separat sichtbar; die 18 Normentscheidungen werden
explizit geprüft. Die 19 Einzelfallabnahmen sind im
[Originalabgleich](evidence/stabilization/b1-b3-original-acceptance.md) dokumentiert.

### Frühere Teilnachweise vor K1

Die folgenden Absätze bleiben historische Nachweise. Der damalige kombinierte
Gradle-Lauf endete mit Exitcode 1 allein wegen `conformanceCheck`.
A hat den Ausgangsstand vor fachlichen Änderungen
bestätigt. B1/B2/B3 sind im [View-Nachweis](evidence/stabilization/view-bindings.md)
als **PARTIAL** dokumentiert: 13 zusätzliche JVM-Testmethoden, 52 zusätzliche
portable Fälle und 52 Prozesspaare mit ili2c 5.6.9. Alle eigenen Erwartungen
bestehen, 42 Referenzentscheidungen stimmen überein. Die zehn Unterschiede
enthalten auch zwei ausdrücklich gesperrte Geometriefälle und sind nicht
pauschal Normabweichungen. Spec 04/05/16/17 und API-Migration sind synchronisiert.

Die [Fortsetzung zu Inspektionsfaktoren](evidence/stabilization/inspection-factors.md)
ergänzt zehn JVM-Testmethoden und 42 portable Fälle. Öffentliche BAG-Typen,
Element-/Ausgangsidentitäten, OF-Einschränkungen und geerbte Inspektionen sind
geprüft. Der Parser verwirft den Faktor nicht mehr zugunsten des OF-Pfads;
Topic-Abhängigkeiten werden nach vollständigem Aufbau geprüft. Ungültige
PARENT-Kontexte sind Sprachfehler. Die gesamte neue View-Prozesssammlung
umfasst 94 Paare: 94 eigene Erwartungen erfüllt, 66 fachliche
Referenzübereinstimmungen, 18 abweichende Entscheidungen und zehn interne
ili2c-Prozessfehler (davon zwei bei gültigen geerbten Inspektionssichten).
Interne Abbrüche zählen weder als fachliche Ablehnung noch als Übereinstimmung. Die Suitebilanz
bleibt unverändert; in diesem Abschnitt wurde kein bisheriger Suite-FAIL geschlossen.

Die [Fortsetzung zur View-Vererbung und Übersetzung](evidence/stabilization/view-inheritance-translations.md)
ergänzt zehn Compiler- und zwei Workspace-Testmethoden sowie 32 portable Fälle.
MANDATORY/TRANSIENT/Domainidentität, ALL-OF-Typübernahme, geerbte
Ableitungsalternativen, effektive Vererbungswege, AGGREGATES-Korrespondenz und
importierte Typziele sind geprüft. Der erste vollständige Lauf fand eine fehlende
Workspace-Vorbereitung der Originalmodell-Imports; nach deren Korrektur besteht
der vollständige Wiederholungslauf bis auf die unveränderte Konformitätsbilanz.
Der aktuelle Prozessvergleich umfasst 126 Paare: 126 eigene Erwartungen,
88 fachliche Übereinstimmungen, 20 Unterschiede und 18 interne Referenzfehler.
Zusätzlich bleibt eine neue Reproduktion in 2.3/2.4 offen: Symbolkorrespondenz
zwischen übersetzten Importmodellen fehlt. Diese Fälle stehen ausserhalb der
gepinnten Suite und sind keine erwartete Normabweichung. Quelle und Ausgaben
stehen im [Nachweis](evidence/stabilization/view-translated-import-probes-2026-10-21.json).

**Fallbilanz:** 19 ursprüngliche FAIL jetzt Referenz-PASS; 35 ursprüngliche FAIL
bleiben offen. Dazu kommt ein neu offengelegter FAIL bei `myClass` in einem
abgeleiteten Attribut (C1), insgesamt 36. Die formale B1–B3-Abnahme bleibt offen;
Referenz-PASS wird im Katalog nicht als vollständige Lieferung ausgewiesen.
Die sechs numerischen Einheitenregressionen bleiben zusätzlich offen in D.

Der [Fachnachweis](evidence/stabilization/composed-units.md) und
[118 Prozessvergleiche je Compiler](evidence/stabilization/units-reference-comparison-2026-10-21.json)
halten Normauslegung, Implementierung und ili2c-Verhalten getrennt fest:
118 übereinstimmende Entscheidungen, keine unerwartete Entscheidung unseres
Compilers in diesem Umfang. Der historische Einheitenstand hatte 45 Implementierungslücken und neun
dokumentierte Normabweichungen; die aktuelle Bilanz steht oben.
Pins, Baseline und XFAILs bleiben unverändert.

Die vier bisherigen [Abstraktionsfehler](evidence/stabilization/alias-unit-follow-on-probes-2026-10-21.json)
sind mit identischen Quellen [erneut geprüft](evidence/stabilization/units-follow-on-probes-2026-10-21.json):
Beide Compiler lehnen sie jetzt in 2.3 und 2.4 unabhängig von der Dateisortierung
ab. Kurzzeichen und Komponenten binden über Identitäten; die globale Alias-
Textsuche ist entfernt. Definitionstext abgeleiteter Einheiten bleibt für
symbolische Umrechnungen erhalten und bedeutet keine vollständige Prüfung.

**Aktuelle Lieferfolge:** [K1–K6](evidence/stabilization/conformance-deliveries.md).
Der neue Ausgangslauf bestätigt 556 PASS / 1 XFAIL / 36 FAIL. K1 bewertet
14 strittige Referenzfälle mit eigenen gepinnten Erwartungen; K2–K6 bearbeiten
die verbleibenden 22 Implementierungsfälle und sechs Einheitenregressionen.
Die unabhängige fehlende Korrespondenz übersetzter Importmodelle bleibt
vorgemerkt. Struktur-Inspektionsfaktoren sind im nachgewiesenen Umfang
implementiert; Standard-Geometrieinspektionen wurden in K4 ergänzt. Eigene
Kurvensegmente bleiben ausdrücklich gesperrt.
Weitere konkrete Zielunterklassen-/Abstraktionsregeln, polymorphe Struktur-
restriktionen, sehr tiefe Vererbung, vollständige gemischte Topic-Vererbung,
OID-Builtinimporte und Referenzübersetzungen bleiben zu prüfen.
Bei FORMAT bleiben vollständige Typrelationen, weitere abstrakte Attribut- und
verschachtelte Strukturkombinationen sowie interne Zeitstrukturen offen.
Alle übrigen Enum-/Collection-/Mengen-/View-/Transferpflichten bleiben bestehen.
Die [Restliste](evidence/stabilization/composed-units.md) trennt diese
Lücken von geprüften Fällen. Keine vollständige S2a-/S2b- oder P3-Abnahme.
Die [Bereichsauslegung](evidence/stabilization/existence-types-and-indices.md)
bleibt gültig: EXISTENCE ist keine Zuweisung des gesamten Wertebereichs.

Der [WasmGC-Abschlussnachweis](evidence/stabilization/wasmgc-process-exit.md)
enthält isolierte Lade-/API-/Semantikversuche, native Stacks, Node-/V8-Versionen
und Artefakthashes. Node 24.22.0 behebt den Prozessabschluss im geprüften Hostprofil;
Browser-WasmGC bleibt der Produktionspfad. Daraus folgt keine Performanceabnahme.
Die frühere [Verifikation](evidence/stabilization/verification-2026-10-20.json)
bleibt als historischer Arbeitsstand erhalten.

Die externe Konformitätssuite ermittelt die Version mit `-version`. Diese
Legacyoption ist noch nicht implementiert, weshalb ihr Rohreport dort Usage-Text
enthält. Die tatsächliche Toolversion 0.2.0-SNAPSHOT und Artefakthashes sind
separat durch SDK-/Capabilityausgaben und das Verifikationsmanifest belegt.

Rohberichte bleiben unter `*/build/test-results/test`, `build/conformance`,
`build/reports/stabilization`, `build/reports/sdk`,
`interlis-wasm-teavm/build/*-result*.json` und
`interlis-wasm-webimage/build/webimage*`. Historische Evidenz wird nicht
überschrieben. Keine Aussage in dieser Tabelle schliesst G2 oder G3.

## 4. Verbindliche Freigabebedingungen

P3 wird erst abgeschlossen, wenn **G0–G3 mit aktuellen Nachweisen erfüllt** sind.
Das umfasst vollständige P2-Norm-/Featuredeckung, das vereinbarte ili2c-Prüfprofil,
SDK, LSP/Browser/Diagramme, Chrome/Firefox/echtes Safari, die drei Plattformen
und die Web-Image-Untersuchung mit Ergebnis oder reproduzierbarem Blocker.

Auf Apple M1 MacBook Air, 16 GiB: JVM-Editor p95 ≤ 1 s, Browsereditor p95 ≤ 2 s
bei Closure bis 1 Mi UTF-16-Einheiten; Batch ≤ 30 s innerhalb Standardbudgets
mit lokalen Quellen, inklusive Prozessstart; Abbruch ≤ 250 ms mit anschliessend
nutzbarer Sitzung. Fünf Warmups, mindestens 30 Messungen pro Editart und ein
1'000-Edit-Lauf sind Pflicht. Diese Werte sind **Sollwerte, keine Messergebnisse**.
Der bisherige 13-Dateien-Seed-Korpus ersetzt diese Abnahme nicht.

## 5. Fortsetzung

Die gesamte Folge E1–E5 des vollständigen G2-Plans ist zur selbständigen
Umsetzung freigegeben. Massgeblich sind der aktuelle Stand am Anfang dieses
Dokuments, die G2-Regelmatrix und der fortlaufende E-Nachweis. Nächster fachlicher
Schwerpunkt ist NUMERIC_SUBDIVISION_RELATIONS: effektive Vorgängerattribute,
vererbte Marker, numerische/Ganzzahligkeits-/Positivitätsregeln und die zirkuläre
kontinuierliche Unterteilung mit exakten gebundenen Einheitenverhältnissen.

Weitere konkrete offene Ursachen: vererbte Constraintnamensräume, SET-Bedingungs-
kontexte, allgemeines Konstanten-/Skalierungsprofil, OID-Relationen, vollständige
vordefinierte Modellinhalte und Namenskategorien, zusätzliche LineCoord-Attribute
sowie noch nicht einzeln nachgewiesene E3-/E5-Kombinationen. Die Matrix enthält
weiterhin offene Produktions-/Normzeilen; die gezielten Teillieferungen ersetzen
deren vollständigen Abgleich nicht.

K1–K6 und die 19 älteren B1–B3-Einzelfallabnahmen sind im festgelegten
Katalogumfang geprüft. Die unabhängige Normprüfung der Referenzausnahmen,
vollständige Graphparität, reale Closures, F/G und alle übrigen P3-Pflichten
bleiben offen. E, G2 und P3 sind nicht abgeschlossen.


Die [Lieferfolge K1–K6 (mit historischer A–D-Zuordnung)](evidence/stabilization/conformance-deliveries.md) und der
[Fallkatalog](evidence/stabilization/conformance-open-cases.json) steuern die
weitere Umsetzung. Jeder Fall wird einschliesslich aller zusätzlichen Ursachen
abgenommen. Unabhängige Folgefragen werden reproduzierbar festgehalten und
explizit priorisiert; sie ersetzen den Katalog nicht. Vollständige P2-Abdeckung
und alle übrigen S0–S6-Verträge bleiben erforderlich.

Workspace-/Adapterverträge weiter gezielt absichern; keine neue Framework-/Pass-
Infrastruktur. Normabweichungen einzeln begründen, Goldens und XFAILs nicht zur
Herstellung grüner Gates lockern. Den grossen GraphBuilder entlang tatsächlich
bearbeiteter Verantwortungen vereinfachen. Offene Abschnitte bleiben offen,
bis ihre Abnahme vorliegt.
