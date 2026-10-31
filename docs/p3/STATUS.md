# P3 – operativer Status

- **IN ARBEIT**, gesamter Meilenstein autonom beauftragt; **G3 offen**.
- Aktuell **P3-2 – LSP**, WP-31, AC-P3-05–08. P3-3 bis P3-5 bleiben offen.
- P3-3: unabhängige [Browser-/Diagrammarbeiten](implementation/P3-3.md) begonnen,
  während P3-2-Review/Plattformmatrix laufen; keine vorweggenommene Abnahme.
- [Plan, Kriterien und Anforderungen](EXECUTION_PLAN.md); die freigegebenen
  [G2-Norm-/Kompatibilitätsprofile](../g2/COMPILER_NORM_DECISIONS.md) bleiben erhalten.

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

## Laufend: P3-2

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
P3-3 Browser/Diagramm, P3-4 Corpus/Belastung und P3-5 SDK/Gesamtgate beginnen
anschliessend automatisch. Der Benutzer wird nicht nach Teilphasen zum
Weiterfahren aufgefordert.
