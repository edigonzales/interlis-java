# View-Vererbung und Übersetzungsidentitäten

21. Oktober 2026. Fortsetzung der [Inspektionsfaktoren](inspection-factors.md).
WP-22/23; META-006/008/011/014, SEM-008/010/014, RES-003/006, TEST-011/012.
B1–B3 bleiben PARTIAL, P2/P3 und G2/G3 offen.

## Umsetzung und fachlicher Vertrag

Grundlage sind [2.3 §2.5/2.6/2.15](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und [2.4 §3.5/3.6/3.15](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
View-Attribute verwenden die Attributregeln; Übersetzungen müssen strukturell
übereinstimmen. Ableitungsalternativen gelten von Basis zu Erweiterung; die
letzte definierte Alternative gewinnt.

- View-Überschreibungen dürfen MANDATORY nicht entfernen, TRANSIENT nicht
  wechseln und eine Domain nicht durch eine unverwandte Domain gleichen
  Werttyps ersetzen. Die entsprechenden Eigenschaften verwenden denselben
  kleinen Prüfer wie Klassenattribute; die View-Typprüfung verwendet weiterhin
  die gemeinsame Typverträglichkeit.
- `MANDATORY` ohne expliziten Typ übernimmt auch den gebundenen Typ eines
  geerbten ALL-OF-Attributs. Eine synthetische Attributidentität benötigt dazu
  keine künstliche Syntaxdeklaration.
- Effektive View-Mitglieder behalten den Weg vom deklarierenden Besitzer
  bis zur verwendenden Sicht, die unmittelbare Überschreibungsidentität,
  Position und Herkunft. Lokales Überschreiben ist `OVERRIDDEN`, blosse
  Weitervererbung `INHERITED`.
- UNION-/Abstraktionsprüfungen berücksichtigen auch die geerbten
  Ableitungsalternativen. Lokale `AttributeSymbol.derivations()` und die
  vorhandene `overrides()`-Kette bleiben getrennt erhalten. Die ausgewählte
  Schema-Closure enthält die benötigten Ausdrücke und Typen.
- Übersetzungen ordnen das synthetische AGGREGATES-Attribut zu und vergleichen
  Attributtypziele und Überschreibungsziele auch bei Views. Identische,
  nicht übersetzte Importziele dürfen erhalten bleiben. Widersprüchliche
  Mehrfachzuordnungen derselben Symbolidentität werden nicht überschrieben.
- Ein Übersetzungsmodell nimmt sein Originalmodell und dessen Imports in
  die Prüf-Closure auf. Dies macht den Namensraum des Originals nicht zu
  einem Import. Fehlende oder mehrdeutige Originale bleiben Eingabefehler.

Die Änderungen bleiben im vorhandenen Graph-/View-Aufbau; es entstehen weder
neue öffentliche Signaturen noch ein zweites Attributmodell. API-Migration,
Javadoc und Spezifikation präzisieren die vorhandene Ableitungskette.

## Reproduktion und Abnahme

Vor den Korrekturen schlugen fünf gezielte Tests wegen tatsächlicher Fehler
fehl (MANDATORY entfernen, MANDATORY nach ALL OF, effektiver Vererbungspfad,
UNION-Alternativen, AGGREGATES-Übersetzung). Der erste Importtest hatte zusätzlich
eine unvollständige Test-Closure. Nach deren Korrektur blieb die fehlende
Originalmodell-Importbindung als Implementierungsfehler reproduzierbar.

`ViewInheritanceTranslationContractTest` enthält zehn neue Testmethoden für
2.3/2.4, positive und negative Überschreibungen, öffentliche Modellinhalte,
importierte Typen in beiden Dateireihenfolgen, drei Quellrevisionen mit frischer
und wiederverwendeter Analyse sowie den Gatefehler im Originalmodell.
Zwei zusätzliche `WorkspaceTest`-Methoden prüfen die Import-Closure des
Originals, Fehler/Korrektur nach Edits und ein später hinzugefügtes Original.
32 neue portable Fälle prüfen Annahme/Ablehnung über dieselben Laufzeitpfade.
Der erste vollständige Lauf legte die fehlende Workspace-Vorbereitung offen;
sein Fehlerprotokoll bleibt unter Build-Ausgaben erhalten. Nach der Korrektur
werden alle betroffenen Laufzeitpfade erneut geprüft.

Der [Prozessvergleich](view-inheritance-reference-comparison-2026-10-21.json)
hält 126 Paare mit Quellen, Hashes, Ausgaben und Exitcodes fest. Alle 126 eigenen
Erwartungen bestehen; 88 fachliche Referenzentscheidungen stimmen überein,
20 unterscheiden sich und 18 ili2c-Prozesse zeigen interne Fehler.
Die Erkennung erfasst nun auch ausgegebene NullPointerException-Nachrichten
ohne Stacktrace. Diese zählen nicht als fachliche Ablehnung. Neue Beispiele
sind AGGREGATES in einer Erweiterung, übersetzte Aggregation ALL und eine
FINAL-View-Erweiterung. Aggregation EQUAL wird zusätzlich separat geprüft.
Die unvollständige MANDATORY-UNION wird von ili2c angenommen; die eigene
Ablehnung bleibt gemäss der bereits dokumentierten Unionregel bestehen.
Suitepins, Erwartungen und XFAILs wurden nicht geändert.

Vollständige Kommandos, Werkzeugstände, Quellbaumdigest, Artefakthashes und
aktuelle Ergebnisse stehen im [Verifikationsmanifest](verification-2026-10-21-view-inheritance.json).

## Neue offene Reproduktion und verbleibende Ursachen

[Übersetzte Importmodelle](view-translated-import-probes-2026-10-21.json):
Ein Basismodell importiert `Original`; seine Übersetzung importiert dessen
Übersetzung `Translated`. Die Views projizieren die fachlich entsprechende
Klasse. In beiden Sprachversionen akzeptiert ili2c 5.6.9 diese Quellen;
der eigene Compiler lehnt sie mit `TRANSLATION_REFERENCE` ab. Die Zuordnung
enthält die Symbolpaare der tatsächlich verwendeten übersetzten Imports noch
nicht. Dieser Fall liegt ausserhalb des eingefrorenen Korpus und wird nicht als
neuer Suite-FAIL oder erwartete Normabweichung verrechnet.

Nächster fachlicher Schritt in B1–B3/SEM-010/014: Korrespondenz entlang der
gewählten übersetzten Import-Closure herstellen, mit umbenannten Symbolen,
mehreren Übersetzungen, negativen Zielvertauschungen und Dateireihenfolge.
Die verbleibende globale Namensnormalisierung ersetzt keine solche Bindung.
Anschliessend fehlen weiterhin vollständige Übersetzungs-/Transferkombinationen,
abgeleitete Assoziationsvererbung und weitere Rollen-/Strukturmengenpfade.

Geometrieinspektionen bleiben wegen Segmenthierarchie, StartSegment-Bedingung
und Linienmetadaten gesperrt (B3/C1/C3). Die ursprünglichen Ursachen bei
Textverengung, OID/HIDING, numerisch→Boolean sowie Metadaten/GRAPHIC bleiben
separat zugeordnet. C1–C4/D und die sechs numerischen Einheitenregressionen
sind weiterhin offen; ebenso die übrigen S0–S6-Pflichten.

## Vollständiger Wiederholungslauf

344 JVM-Tests: PASS, keine Fehler/übersprungenen Tests. 1192 gemeinsame Fälle
unter TeaVM-JavaScript und WasmGC/Node sowie Chromium, Firefox, WebKit und
echtem Safari: PASS. Web-Image-Build, Node und drei Browser: PASS.
SDK-Beispiel und neun CLI-Prozesse auf macOS arm64: PASS.
Der kombinierte Gradle-Lauf endet regulär mit Exitcode 1 ausschliesslich wegen
der offenen Konformität: 556 PASS, 1 XFAIL, 36 FAIL, 0 ERROR/XPASS.
Kein bisheriger Suite-FAIL geschlossen, kein neuer Suite-FAIL. Der neue
übersetzte-Import-Fall bleibt separat offen. Keine vollständige
Performance-/Retention-/Linux-/Windows- oder Release-Abnahme.
