# K3: CONTEXT, GENERIC und DEFERRED

WP-24/25; META-003/011/014, SEM-010/014/017/019, SYN-004/013/015,
TEST-011/012. [2.4 §3.5.2 und §3.8.8](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf)
regeln die Aufschiebung am Topic, modellbezogene Kontextwirkung und
Spezialisierung bereits wirkender Alternativen.

## Geschlossene Ausgangs-FAIL

Alle sechs Fälle unter `ili24.GenericValueRanges24Test` sind jetzt PASS:
`contextInSameModel`, `contextInDifferentModel`, `genericInStructAttr`,
`multipleContexts`, `multipleContextsB`, `multipleContextsExtending`.
Die zehn negativen Originalfälle bleiben fachlich abgelehnt. Die pauschale
CONTEXT-Sperre ist durch strukturierte Syntax und gebundene Modellinhalte ersetzt.
Es wurde keine neue normative Ausnahme eingeführt.

`ContextSymbol` hält generische Domainidentitäten und geordnete Alternativen.
`TopicSymbol.genericBindings` unterscheidet konkrete, legal aufgeschobene und
abstrakte Verwendungen. Imports übertragen Kontextwirkungen transitiv;
Spezialisierungen und Konflikte werden gegen gebundene Ziele geprüft.
Struktur-Unterklassen und Collections zählen zur Verwendung im betroffenen Topic.
Übersetzungen prüfen Mappingziele; Positionsabfragen verwenden dieselben Referenzen.
Die globale Quelltextauswertung für GENERIC/CONTEXT/DEFERRED ist entfernt.

Notwendige Grundlagenkorrekturen: Domainmodifikatoren wurden zuvor auf den ganzen
DOMAIN-Block übertragen; NUMERIC-/konkrete Koordinatenachsen wurden nach Parse-
Kind gruppiert statt in Quellreihenfolge erhalten. Beide Fehler sind korrigiert
und durch öffentliche Modelltests sowie portable Gegenfälle nachgewiesen.

## Ausgeführte Prüfungen

[Manifest](verification-2026-10-21-k3.json): 367 JVM-Tests (elf neue K3-Methoden),
15 Wrappertests und 1308 portable Fälle (34 neue K3-Fälle), alle erfolgreich.
TeaVM-JavaScript und WasmGC unter Node sowie reale Browser Chromium, Firefox,
WebKit und Safari bestehen. Web-Image besteht unter Node und den drei Playwright-
Browsern; SDK und neun CLI-Prozesse auf macOS arm64 bestehen. Alle Prozesse enden
regulär. Der kombinierte Gradle-Lauf endet allein wegen Konformität mit Exitcode 1.

Roher Referenzvergleich: **565 PASS, 1 XFAIL, 27 FAIL**.
Projektbewertung: **565 PASS, 17 XFAIL, 11 FAIL**, 0 ERROR/XPASS.
Alle bisherigen 16 Normausnahmen sind erneut überprüft.
[38 Prozessnachweise](k3-reference-comparison-2026-10-21.json) vergleichen die
16 positiven/negativen Originalfälle sowie drei Achsen-Gegenproben mit ili2c 5.6.9.
Imports in umgekehrter Dateireihenfolge und frische gegen wiederverwendete Analyse
nach geändertem importiertem Kontext sind im JVM-Vertrag geprüft.

Ein früher direkter Probelauf verwendete Java 21 statt 25 und endete mit
UnsupportedClassVersionError. Er ist kein fachlicher Nachweis. Der korrigierte
Vergleich verwendet explizit Java 25; das Manifest enthält den abschliessenden
Lauf nach der Achsenkorrektur, nicht den vorherigen 1305-Fälle-Lauf.

## Verbleibende Ursachen

Elf Katalogfälle: K4 umfasst acht Metadaten-/Linienformfälle und zwei abhängige
Geometrieinspektionen; K5 umfasst roadsExgm2ien mit GRAPHIC und zusätzlichen
Ziel-/OID-Ursachen. K6 umfasst die sechs bekannten numerischen Einheitenregressionen.
Gebundene Referenzsysteme und Achseneinheiten der K4-Originale sowie vollständige
Linienmetadaten bleiben Teil von K4; K3 belegt keine vollständige Koordinatenfamilie.
Der unabhängige übersetzte-Import-Fehler bleibt nachgeordnet. P3 bleibt offen;
Plattform-, Performance-, Retention-, Workspace-, LSP- und CLI-Pflichten sind
mit diesem Lauf nicht vollständig abgenommen.
