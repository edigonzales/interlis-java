# P3 – operativer Status

- **IN ARBEIT**, gesamter Meilenstein autonom beauftragt; **G3 offen**.
- Aktuell **P3-2 – LSP**, WP-31, AC-P3-05–08. P3-3 bis P3-5 bleiben offen.
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

Historischer [P3-Voraudit](reviews/P3-voraudit.md): AUDIT mit 15 Musslücken,
kein G3-PASS; Bericht-SHA-256
`ef7ac8ac673710d28c0139915af5f3fcc4043a3fe427e9228c71e46ee5c311d2`.
P3-3 Browser/Diagramm, P3-4 Corpus/Belastung und P3-5 SDK/Gesamtgate beginnen
anschliessend automatisch. Der Benutzer wird nicht nach Teilphasen zum
Weiterfahren aufgefordert.
