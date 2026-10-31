# P3 – operativer Status

- **IN ARBEIT**, gesamter Meilenstein autonom beauftragt; **G3 offen**.
- Aktuell **P3-3 – Browser und Diagramm**, WP-31, AC-P3-09–11.
  [Browser-/Diagrammarbeiten](implementation/P3-3.md) laufen; P3-4/P3-5/G3 offen.
- [Plan, Kriterien und Anforderungen](EXECUTION_PLAN.md); die freigegebenen
  [G2-Norm-/Kompatibilitätsprofile](../g2/COMPILER_NORM_DECISIONS.md) bleiben erhalten.

## Laufend: P3-3 – Reviewkorrekturen

[Unveränderter erster Review](reviews/P3-3-7440fb9-FAIL.md): **FAIL**, Kandidat
`7440fb93a9cf566d0da1866063361daf63ee9fcf`, Bericht-SHA-256
`c681ba4527c536f8b3d41fe5515b78416defb02f4cc150c84f01a3cf1baa46ed`.
Vier Mussbefunde: partielle Diagrammzustände, explizit leere Auswahl,
Wire-Diagrammoptionen und idempotentes Schliessen. Neue Reviewerproben50/50
und76/76 positiv; separate negative Proben reproduzieren F1–F4.
Kumulative JVM-/Portable-/Browser-/Safari-/WebImage-/SDK-Prüfungen grün.
Direkte DeepSeek API, sauberer separater Checkout und OS-Python-Sperre;
Transportexit1/Manifest failed, kein positiver Abnahmenachweis. Rohbelege unter
`build/review/P3-3-r1/raw/`. Korrektur im separaten Checkout in Arbeit.
Unabhängige P3-4-Corpusvorbereitung läuft weiter; keine Corpusabnahme.

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
