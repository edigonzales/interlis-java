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

Implementierter Kandidat, **unabhängiger Review offen**, AC-P3-05–08.
[Umsetzung, RED→GREEN und tatsächliche Läufe](implementation/P3-2.md).
Abgetrennte Analyse/Sprachqueries/Compilearbeit, immutable Hostsicht,
atomare Synchronisation und Konfiguration, geschlossene Projektquellen,
vollständige revisions-/schreibrechtsgeprüfte Versionsedits, Scopecompletion,
gebundene Tokens/Folding/Formatierung und echter stdio-Lebenszyklus umgesetzt.
744 Compiler- und34 LSP-Tests ohne Fehler/Skips. Aktive Evidenzbindungen
mechanisch nachgeführt; Norm-/Corpus-/Suiteerwartungen unverändert.

Breiter eigener Lauf: check/Portable/Browser/echtesSafari/WebImage/SDK/Paket
PASS, je3548 portable Fälle. Conformance577 PASS/16XFAIL. Fehlversuche und
präzisierte Completion-Abfrageposition bleiben im Bericht sichtbar. Nach der
letzten LSP-Workertrennung eigener JVM-/Evidenz-/SDK-/Distributionlauf PASS.
Neue unabhängige Gegenprobe und sauberer Kandidatenreview folgen jetzt;
dieser Stand ist noch kein P3-2-PASS und kein G3-Abschluss.

Historischer [P3-Voraudit](reviews/P3-voraudit.md): AUDIT mit 15 Musslücken,
kein G3-PASS; Bericht-SHA-256
`ef7ac8ac673710d28c0139915af5f3fcc4043a3fe427e9228c71e46ee5c311d2`.
P3-3 Browser/Diagramm, P3-4 Corpus/Belastung und P3-5 SDK/Gesamtgate beginnen
anschliessend automatisch. Der Benutzer wird nicht nach Teilphasen zum
Weiterfahren aufgefordert.
