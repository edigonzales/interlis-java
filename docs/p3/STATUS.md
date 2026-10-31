# P3 – operativer Status

- **IN ARBEIT**, gesamter Meilenstein autonom beauftragt; **G3 offen**.
- Aktuell **P3-5 – SDK, Plattformen und Gesamtgate**, WP-33, AC-P3-17–20.
  [Vorbereitung](implementation/P3-5.md); finale Produktmatrix und G3 offen.
- [Plan, Kriterien und Anforderungen](EXECUTION_PLAN.md); die freigegebenen
  [G2-Norm-/Kompatibilitätsprofile](../g2/COMPILER_NORM_DECISIONS.md) bleiben erhalten.

## Abgenommen: P3-4 – vollständiger Corpus und Belastung

**PASS**, AC-P3-12–16 und kumulative P3-1–3-Gates, Produktcommit
`280589065a7900e6b3bb2a338f2cd012865091e1`.
[Unveränderter unabhängiger Review](reviews/P3-4-review.md), SHA-256
`f5cb2399cab9a595ecf9ac637749a2319e6bfff05b5c66c56037225ac3cfdc65`.
OpenCode2.0.28, direkte DeepSeek API, neuer Kontext im exakten sauberen
separaten Checkout; Transportexit0 und äusseres OS-Sperrmanifest passed.
Keine offenen MUSS-Findings. [Corpusbewertung](CORPUS_ASSESSMENT.md),
[Umsetzung und Belege](implementation/P3-4.md).

Reviewer: alle347 Closures/366 Originalquellen unabhängig geprüft,
262 Erfolge/85 ausdrücklich bewertete strikte Quellablehnungen; ili2c5.6.9
akzeptiert alle347. Eigene neue Prüfungen27/27 Provenienz,10/10 Eingänge,
17/17 CLI und19/19 Performancechecker. Aktuelle1186 JUnit ohne Fehler/Skips,
JS/WasmGC/echte Browser/Safari/WebImage je3548+24, UI3,29/7,
Conformance577/16/0 mit21 Normkontrollen, SDK9/Paket und54 Legacyprozesse.

Frischer vollständiger `performanceCheck` am Produktcommit: **PASS**,24m23s,
Quellfingerprint `750b4f5857c6a974c790e0965a9ce670078604d7feb1be264500b5cdab92290e`,
dirty=false,347 Wurzeln/sechs Laufzeiten/2000 echte Änderungen/0 Skips/
0 Semantikabweichungen. JVM-Edit-p95 104.215ms, Wasm823.135ms;
feste Budgets unverändert. Der frühere79a2040-Lauf bleibt eine getrennte
Elternvorprüfung. Rohbelege `build/review/P3-4-r1/` und kopiert unter
`build/reports/p3/P3-4/independent-r1/`.

Die Windows-/Linux-Zusatzmatrix am79a2040 ersetzt keine finale G3-Matrix.
P3-5/G3 bleiben offen. Der vollständige SDK-/Gesamtauftrag wird automatisch
fortgesetzt; historische13er-Messungen und frühere Fehlversuche bleiben erhalten.

## Abgenommen: P3-3 – Browser und Diagramm

**PASS**, AC-P3-09–11, Produktcommit
`253d9ca837d05cc86959caf04fb2f4bab34c74c0`.
[Unveränderter unabhängiger R4-Review](reviews/P3-3-review.md), Bericht-SHA-256
`7b57aeaf6ffefde51e3f23be5a1236148c9dc8c605b218e679f0a7882ef188da`.
OpenCode2.0.28, direkte DeepSeek API, neuer Kontext am exakten sauberen
separaten Checkout; gesamter Prozess OS-Python-gesperrt. Transportexit0,
äusseres Manifest passed. Keine offenen MUSS-Findings; F1–F7 geschlossen.
Reviewer:1171 aktuelle XML-Fälle ohne Fehler/Skips, JS/WasmGC je3548,
Chromium/Firefox/WebKit/echtes Safari sowie WebImageNode und drei WebImagebrowser
je3548, echte UI3,29 Transport-/7 Clientprüfungen, SDK9/Paket, Conformance
577PASS/16XFAIL/0FAIL. Rohe Referenzbewertung bleibt separat FAIL.
Neue eigene Proben127/127 und76/76, echte UI3 undSafari; am Vorgänger
reproduzieren sie12 beziehungsweise9 Fehler. Rohbelege
`build/review/P3-3-r4/`, kopierte eigene Quellen/Logs unter
`build/reports/p3/P3-3/r4-own/`. P3-4/P3-5/G3 bleiben offen.

### Unveränderte Reviewhistorie

[Unveränderter erster Review](reviews/P3-3-7440fb9-FAIL.md): **FAIL**, Kandidat
`7440fb93a9cf566d0da1866063361daf63ee9fcf`, Bericht-SHA-256
`c681ba4527c536f8b3d41fe5515b78416defb02f4cc150c84f01a3cf1baa46ed`.
Vier Mussbefunde: partielle Diagrammzustände, explizit leere Auswahl,
Wire-Diagrammoptionen und idempotentes Schliessen. Neue Reviewerproben50/50
und76/76 positiv; separate negative Proben reproduzieren F1–F4.
Kumulative JVM-/Portable-/Browser-/Safari-/WebImage-/SDK-Prüfungen grün.
Direkte DeepSeek API, sauberer separater Checkout und OS-Python-Sperre;
Transportexit1/Manifest failed, kein positiver Abnahmenachweis. Rohbelege unter
`build/review/P3-3-r1/raw/`. Korrekturen F1–F4 sind implementiert und gezielt grün.
Unabhängige [P3-4-Corpusvorbereitung](implementation/P3-4.md) läuft weiter; keine Corpusabnahme.

[Unveränderter zweiter Review](reviews/P3-3-eab87de-INCOMPLETE.md) am
`eab87de4e79833c1943fc45d2a6b3cf74e94d5b8` bestätigt F1–F4 mit243 neuen
eigenen Prüfungen, erklärt fachlich PASS. Bericht-SHA-256
`3b18058d3ed03b53487051c4c8ef31a601a0c3aef606509f7765d633ac1c743a`.
Transportexit1/äusseres Manifest failed: **keine gültige Abnahme**. Zudem reicht
die explizite Ablehnung übersetzter Labels nicht zur Erfüllung von VIZ-017;
Sprach-/Übersetzungsauswahl und begrenzte Nachbarschaft sind implementiert und
auf JVM, portabel und in echten Browsern einschliesslich verspätetem Layout geprüft.
Der Corpus deckte zusätzlich Java-Strings statt ModelSelectorDto in
`rejection.requestedRoots` auf. Die ursprünglichen und konkretisierten Wurzeln sind jetzt verlustfreie DTOs.
[Unveränderter dritter Review](reviews/P3-3-1ec148b-FAIL.md) am sauberen Kandidaten
`1ec148babdd0179a86e05d5051f55626ced63031`: **FAIL**, neue Befunde F5–F7
(gemeinsames Mitgliederbudget, vollständiger Typfilter, konsistenter Konfigurationsfingerprint).
Direkte DeepSeek API, separater Checkout, OS-Python-Sperre;
Transportexit0/äusseres Manifest passed. Bericht-SHA-256
`9882f89301386370bc0affe438dc40f289fe564a188ddf2203ceb26d94b195c4`.
F5–F7 sind durch den oben verlinkten unabhängigen R4-PASS geschlossen.

## Abgenommen: P3-1 – CLI und Formatierung

**PASS**, AC-P3-01–04 und P3-AUD-01–04, Produktcommit
`9108b0387d9f1b82f506d8dc64e301e8522137dd`.
[Implementierung und tatsächliche Läufe](implementation/P3-1.md),
[ili2c-Prüfprofil](CLI_PROFILE.md),
[unveränderter unabhängiger PASS-Review](reviews/P3-1-review.md).

OpenCode 2.0.28, direkter Provider deepseek/deepseek-flash, frischer Kontext am
exakten sauberen separaten Checkout; gesamter Prozess OS-Python-gesperrt,
Transportexit 0 und äusseres Manifest passed. Bericht-SHA-256
`9133ef5e8499f67f966e593be9b968f59510e2d0ee0a18a61fff8fcf608a5db7`.
Reviewer: 1103 aktuelle XML-Testfälle ohne Fehler/Skips (Aggregat; einzelne Tasks
UP-TO-DATE), 54 echte Profil-/JAR-Prozesse plus Encodingprobe, neun SDK-Aufrufe,
38 eigene neue Gegenproben, keine offenen P3-1-MUSS-Findings.
Der [erste FAIL-Review](reviews/P3-1-a096d7e-FAIL.md) bleibt unverändert erhalten;
F1–F5 wurden mit tatsächlichen RED→GREEN-Proben geschlossen.

**Echte Plattformmatrix am selben Produkt-SHA:**
[GitHub-Lauf 41120065983](https://github.com/edigonzales/interlis-java/actions/runs/41120065983),
Windows x64 Job `124284221132` und Linux x64 Job `124284221624` jeweils PASS:
check, Legacy-/SDK-Prozesse, SDK-Paket und LSP-Distribution. Neue GitHub-Action
nach ausdrücklicher Nutzerfreigabe implementiert, alle Actions per SHA gepinnt.
Dies ersetzt nicht die spätere Matrix am endgültigen P3-Produkt.

Zusätzliche lokale Regression am P3-1-Code: Portable und Conformance
577 PASS / 16 freigegebene XFAIL; Web Image Chromium/Firefox/WebKit je 3548.
Ein erster TeaVM-Firefox-Timeout bleibt als Fehlversuch dokumentiert. Getrennte
Gegenprüfung ohne Produkt-/Timeoutänderung: Chromium/Firefox/WebKit und echtes
Safari je 3548 PASS, Exit 0. Vollständiger frischer kumulativer Lauf bleibt G3.

## Abgenommen: P3-2 – LSP und gemeinsame Sprachabfragen

**PASS**, AC-P3-05–08, Produktcommit
`5eac6b64fb352982376f054a765597688d5f975b`.
[Unveränderter unabhängiger Review 7](reviews/P3-2-review.md), SHA-256
`12249d8c9120eb108fadebcb1850e8c377ca41e289423cf3fecd904c523b0c1d`.
OpenCode 2.0.28 mit direkter DeepSeek API, frischer Kontext, sauberer separater
Checkout und OS-Python-Sperre; Transportexit 0, äusseres Manifest passed.
Alle bisherigen Mussbefunde unabhängig geschlossen. Neue eigene Proben 24/24
und 4/4; die erste ist am Vorgänger in genau sechs F8-Prüfungen rot.
Frischer check: 1148 Fälle ohne Fehler/Skips, darunter Compiler744/LSP53;
Portable JS/WasmGC je3548, SDK9/Paket, Conformance577PASS/16XFAIL/0FAIL.
Der rohe Referenzlauf bleibt getrennt FAIL.

[Actions41164843248](https://github.com/edigonzales/interlis-java/actions/runs/41164843248)
am selben vollständigen Produkt-SHA: Windows x64 und Linux x64 jeweils PASS.
Diese externe Matrix wurde durch den Hauptlauf geprüft; der unabhängige Reviewer
hat sie ausdrücklich nicht ausgewertet. Finale Gesamtproduktmatrix bleibt P3-5.
Rohdaten: `build/review/P3-2-r7/`, eigener vollständiger Portable-/SDK-Lauf
`build/reports/p3/P3-2/compile-outcome-candidate-portable.log`.
Die nachfolgende Historie erhält sämtliche fehlgeschlagenen Versuche unverändert;
deren damalige Aussagen zu offenen P3-2-Pflichten sind historisch.

## Historie der P3-2-Korrekturen

**Erster unabhängiger Review FAIL**, Kandidat 466790c, AC05/08 FAIL und
AC06/07/kumulativ P3-1 PASS. [Unveränderter Bericht](reviews/P3-2-466790c-FAIL.md),
SHA-256 a4ec9ca453398ad5776106130a0e5eaf2d377bd967c0eb5ef60ea29f1b40f01b;
OpenCode/direkte DeepSeek API, sauberer separater Checkout, OS-Python-Sperre,
Transportexit 0/Manifest passed. Fünf Mussbefunde zu Symlinkroot/Aliasidentität,
Ressourcen-/Updatefehlern und FailureId. [Umsetzung und Korrekturen](implementation/P3-2.md).

F1–F5 im Korrekturkandidaten bearbeitet und gezielt verifiziert; eigener
check/Evidenz145/SDK 9/Paket/LSP-installDist-Lauf grün,42 LSP-Tests ohne Fehler/Skips.
Unveränderte Reviewer-Reproduktionen selbst nochmals ausgeführt und bewertet;
die fehlgeschlagene falsche Probedateibenennung bleibt dokumentiert.
Neue unabhängige Gegenprobe und sauberer Korrekturkandidatenreview folgen;
**P3-2 und G3 bleiben offen**. Norm-/Corpus-/Suiteerwartungen unverändert.

Erster Kandidat tatsächlich auf Windows/Linux x64 geprüft:
[Actions 41145332532](https://github.com/edigonzales/interlis-java/actions/runs/41145332532),
beide PASS. Heruntergeladene Artefakte belegen je 1128 JUnit ohne Fehler/Skips,
54 Legacy-Prozesse, Java 25.0.3/amd64 und identischen vollständigen Kandidaten-SHA.
Neue Plattformmatrix der Korrektur bleibt fällig. Breiter gemeinsamer
Core-/Portable-/Browser-/Safari-/WebImage-/SDK-Lauf am ersten Kandidaten PASS,
je 3548; Conformance 577 PASS / 16 XFAIL. Der rohe Referenzlauf bleibt getrennt.

Korrekturkandidat 1c19acb: Linux PASS, Windows FAIL im
[neuen Lauf 41148744929](https://github.com/edigonzales/interlis-java/actions/runs/41148744929).
Ein unregistrierter Client-URI wurde plattformabhängig umgeschrieben; die neue
plattformunabhängige RED→GREEN-Kontrolle ist behoben. Aktueller eigener
check-/Evidenz-/SDK-/Distributionlauf mit 43 LSP-Fällen grün.
Die neue [Plattformmatrix 41149619185](https://github.com/edigonzales/interlis-java/actions/runs/41149619185)
am Produkt-SHA f960cea besteht auf Windows und Linux x64.

Zweiter und dritter unabhängiger Review: weiterhin **FAIL**, ursprüngliche
F1–F5 geschlossen; neuer F6 zur physischen Identität bei Discovery und unbekannten
`didOpen`-Aliasen. Unverändert erhalten:
[Review 2](reviews/P3-2-1c19acb-FAIL.md), SHA-256
`b8608148d686070d60fff6b15f746ca1bab00317a5e378578e420101e5f56504`,
[Review 3](reviews/P3-2-f960cea-FAIL.md), SHA-256
`81170750f39d0680c03ef0318b64e7464c622442f2c2eaae07d0452425d0f20b`.
Beide direkte DeepSeek API, Transportexit 0, OS-Python-Sperre und äusseres
Manifest passed, sauberer separater Kandidatencheckout.

F6 jetzt bearbeitet: Dateischlüssel bei Discovery, bevorzugte reale Primärdatei,
asynchrone Identitätszuordnung mit geordneten Dokumentnotifications/Requests,
Erhalt des offenen Clienttexts sowie vorhandene Editadresse nach Primärlöschung.
Drei neue RED-Reproduktionen; aktuelle eigene JVM-/Evidenz-/SDK-/Distribution-
Prüfung grün, **48 LSP-Tests** ohne Fehler/Skips. Eigene Wiederholung der
unveränderten Reviewerprobe: sieben sachliche Aliasprüfungen grün, eine
unpassende Gesamtzahlannahme für die flache Symbolausgabe rot und unverändert
erhalten; getrennte Kontrolle prüft genau ein Root-Modellsymbol.
Neue unabhängige Abnahme des sauberen Kandidaten und dessen Plattformmatrix
bleiben fällig; **P3-2 und G3 offen**.

Kandidat 3018d4b: [Actions 41151205408](https://github.com/edigonzales/interlis-java/actions/runs/41151205408)
Linux PASS, Windows FAIL in genau der neuen Startup-Hardlinkkontrolle
(erwartet zwei Quellen, tatsächlich drei). Windows Java 25 liefert keinen
`fileKey`; Discovery verwendet deshalb jetzt zusätzlich `Files.isSameFile`
auf der I/O-Seite. Gleiche Dateibytes allein führen ausdrücklich nicht zur
Zusammenführung. Weitere Kontrollen prüfen diese Trennung und geordnete
Clientänderungen während der Identitätsprüfung. Aktueller eigener check-/
Evidenz-/SDK-/Distributionslauf grün, **50 LSP-Fälle** ohne Fehler/Skips.
Review 4 läuft noch am vorherigen Kandidaten; dessen mögliche Abnahme ersetzt
weder die Windows-Korrektur noch deren neue unabhängige Abnahme/Plattformmatrix.

Produkt 643fe12 besteht inzwischen tatsächlich die
[Windows-/Linuxmatrix 41151708722](https://github.com/edigonzales/interlis-java/actions/runs/41151708722).
Review4 ist **FAIL**, F1–F6 geschlossen, neuer Tokenbudgetpfad F7:
[unveränderter Bericht](reviews/P3-2-3018d4b-FAIL.md), SHA-256
`bae331d35bc985f84dde89e347e501d085c7d011a3cdbde583ab54bfbd84e691`,
Transportexit0/Manifest passed. Review5 berichtet weitere reproduzierbare
Alias-/Resynclücken: [unveränderter Bericht](reviews/P3-2-643fe12-FAIL.md),
SHA-256 `e0029d21f4118ee0be429635b3c686b6ca3adfcdb5938c170d57eaeee99fe1c0`.
Sein Transport endete mit Exit1/Manifest failed trotz geschriebenem Bericht und
sauberem Kandidatencheckout; daraus wird keine gültige Abnahme abgeleitet.
Die konkreten Reproduktionen werden als Fehlerhinweise bearbeitet.

Aktuelle Korrektur: Alias- und Dateischlüsselzuordnung auch nach Primärlöschung
über die Aliasadresse; Resynchronisation nach kanonischer Quelle statt URI;
frühe Syntax-/Capture-Budgets als RESOURCE_LIMIT, ohne falsche Missing-Input-
oder interne Fehler. Neue RED→GREEN-Kontrollen einschliesslich beider URI-
Richtungen und abgelehntem Open/Change. Neuer vollständiger Kandidatenreview
und Plattformmatrix folgen; **P3-2/G3 bleiben offen**.

Review6: [unveränderter FAIL-Bericht](reviews/P3-2-e31ad29-FAIL.md), SHA-256
`084027cd76ee6fef87584a6db3aa1db47fe36574a4f34255b43717c600ca3684`;
Transportexit1/Manifest failed, daher kein positiver Abnahmenachweis. Die
direkten AC05–08 und bisherigen Befunde sind in seinen eigenen Proben grün,
aber neuer kumulativer F8: Capture-Abbruch entwich `Workspace.compile`, statt
ein Cancelled-Ergebnis zu liefern; der unveränderte Portablefall137 ist rot.
Dieser auch selbst nachgewiesene Regress ist im gemeinsamen Compile-Einstieg
für beide Overloads korrigiert. Java-Positiv-/Negativkontrollen und PortableJS
3548 bestehen, Erwartungen unverändert. Neue vollständige Abnahme bleibt fällig.

Historischer [P3-Voraudit](reviews/P3-voraudit.md): AUDIT mit 15 Musslücken,
kein G3-PASS; Bericht-SHA-256
`ef7ac8ac673710d28c0139915af5f3fcc4043a3fe427e9228c71e46ee5c311d2`.
P3-3 Browser/Diagramm läuft; P3-4 Corpus/Belastung und P3-5 SDK/Gesamtgate folgen
automatisch. Der Benutzer wird nicht nach Teilphasen zum
Weiterfahren aufgefordert.
