# G2 E1 – Namensbindung und einfache vordefinierte Domänen

## Beauftragter Teilumfang und Abnahme

E bleibt in der Reihenfolge E1–E5 offen. Diese Lieferung bearbeitet
SYN-013, META-003/005/006/011/014/015 und SEM-002/005/010/014:

- Unbekannte `INTERLIS.*`-Domänen sind reguläre gebundene Quellfehler mit
  Originalbereich; keine synthetische Generic-Domäne und kein interner Fehler.
- Die laut Grammatik verwendbaren einfachen Domänen
  `INTERLIS.INTERLIS_1_DATE`, `INTERLIS.HALIGNMENT`, `INTERLIS.VALIGNMENT`
  erhalten wirkliche Typen, FINAL-Eigenschaft und vordefinierte Herkunft.
- Ein expliziter falscher Präfix bei Funktionen und ALL-OF-Domänen wird nicht
  durch wiederholtes Abstreifen von Namensteilen repariert. Eigene qualifizierte,
  importierte und unqualifizierte Namen bleiben gemäss Sichtbarkeit bindbar.
- FINAL-Domänen dürfen verwendet, aber nicht erweitert werden.

Grundlage: 2.3 §2.5.4, §2.8, §2.8.3 und Anhang A (S. 30, 37–40, 94),
2.4 §3.5.4, §3.8, §3.8.3 und Anhang A (S. 38, 46–52, 121).
Dokumentpins stehen in `g2-rule-matrix.json`.

Abnahme: positive/negative Modell- und Querytests in beiden Versionen;
gebundene Referenzidentität und Bereich, importierte Quellen in beiden
Reihenfolgen, frisch/wiederverwendet und gültig → ungültig → gültig;
portable JVM/TeaVM-/Browserprüfung, eingefrorene Konformität und direkte
Prozessvergleiche. Erst ausgeführte Nachweise erhalten VERIFIED.

Nichtziele dieser Teillieferung: vollständige E1-Abnahme, alle übrigen
vordefinierten Modellinhalte, neue Schlüsselwortschreibweisen, vollständige
Textwert-/Einheiten-/Enumsemantik (E2), Modellklassen-/LineCoord-Abstraktion
(E1/E3), CONTRACTED-Übersetzungsentscheidung (E1/E5), Overlays und Beschaffung.
Keine API-Fassade, neue Module oder neue Auflösungsarchitektur.

## Ausgangsbeobachtungen

Die ersten Rohproben liegen unter
`build/reports/stabilization/g2-e1-predefined/baseline.json`.
Ein unbekannter Attributtyp endet mit Exitcode 3 und `ILI-INF-0001`.
Die darin ebenfalls verwendeten Domänenaliase `DOMAIN D=INTERLIS.…`
sind keine zulässigen positiven Kontrollen: die DomainDef-Produktion erlaubt
dort keinen DomainRef. Auch reservierte Schlüsselwörter werden durch Anhang A
nicht automatisch als qualifizierte Namen zugelassen. Diese Proben rechtfertigen
keine Grammatikänderung. Für die Abnahme werden syntaktisch gültige
Attributverwendungen und echte Erweiterungen verwendet.

## Implementiert und ausgeführt

Die drei vordefinierten Domänen werden im bestehenden GraphBuilder aufgebaut.
Der Syntaxbaum behält die HALIGNMENT-/VALIGNMENT-Namenssegmente.
Funktions- und ALL-OF-Bindung verwenden denselben vorhandenen Binder;
Präfix-/Suffix-Sonderauflösung und synthetische Ersatzdomäne entfallen.
Es gibt keine neue Produktionsklasse und keine öffentliche Signaturänderung.
Die FINAL-Prüfung berücksichtigt bereits die indexierten Modifier, unabhängig
vom Aufbauzeitpunkt der Basis.

`PredefinedBindingContractTest` enthält sieben JVM-Verträge für effektive
Typen, Aliasziele, Herkunft, FINAL, Originalbereiche, Navigation,
Imports/Mehrdeutigkeit, Quellenreihenfolge und gültig → ungültig → gültig.
Fehlende Namen liefern `ILI-SEM-0001`, mehrdeutige Funktionsnamen
`ILI-SEM-0002`, FINAL-Erweiterungen `ILI-SEM-0010` mit `DOMAIN_FINAL`.
Die Fehler verursachen SOURCE_ERRORS; es entsteht kein CompiledSchema.

Der abschliessende Gradle-Lauf endet **erfolgreich mit Exitcode 0**:

- **489 JVM-Tests und 20 Wrappertests** bestehen; SDK-Beispiel, CLI,
  Workspace, LSP, Modell, Projektion und Diagramm-/Adapterprüfung eingeschlossen.
- **1782 portable Fälle je Laufzeit** bestehen auf TeaVM-JavaScript,
  WasmGC/Node, Chromium, Firefox, WebKit und **echtem Safari**. Der aktuelle
  Web-Image-Build sowie seine Node-/Browserprüfungen bestehen ebenfalls.
  Die 40 neuen portablen Fälle umfassen Imports, Mehrdeutigkeit und Editfolgen.
- **26 Prozesspaare mit ili2c 5.6.9** erreichen alle festgelegten eigenen
  Ergebnisse und stimmen vollständig mit der Referenz überein. Ablehnungen
  werden zusätzlich über JSON-Diagnosen als Quellfehler geprüft.
- Neun CLI-/SDK-Prozessprüfungen bestehen.
- Die unveränderten 593 Suitefälle ergeben **574 PASS / 19 XFAIL /
  0 FAIL / 0 ERROR / 0 XPASS**; roh **574 PASS / 1 XFAIL / 18 FAIL**.

Es wurde **kein bisheriger Suite-FAIL umklassifiziert**: diese E1-Korrekturen
betreffen zusätzliche Sprach-/Modellfälle ausserhalb der eingefrorenen Suite.
Quellenpins, ursprüngliche Referenzerwartungen und XFAIL-Liste bleiben unverändert.

[Verifikationsmanifest](verification-2026-10-25-g2-E1.json),
[genaue Quellen, Hashes und Prozessausgaben](g2-E1-binding-reference-2026-10-25.json).
Die Rohberichte und Logs liegen unter
`build/reports/stabilization/g2-e1-binding/final/`. Die erste erfolgreiche
Zwischenprüfung mit 1778 portablen Fällen ist historisch im Build-Log erhalten;
der Abschlussnachweis verwendet den späteren Lauf mit 1782 Fällen.

## Verbleibende Ursachen und nächster Abschnitt

Die 125 Grammatikproduktionen waren bisher pauschal E1 zugeordnet.
Ihre fachlichen Familien sind nun E1–E5 zugeordnet; sämtliche unvollständig
geprüften Produktionen bleiben OPEN. Fünf präzise Bindungsregeln sind aktuell
nachgewiesen. Die Gesamtmatrix enthält **229 Prüfregeln: 30 VERIFIED,
1 IMPLEMENTED und 198 OPEN**. Die Zahlen sind ein Arbeitsinventar und keine
Vollständigkeitsquote der Sprache; weitere Normabsätze/Alternativen sind
weiterhin einzeln abzugleichen.

Vier getrennte Prozessproben belegen zwei nächste E1-Arbeiten:

1. **Parameterlose Funktionen:** 2.3 §2.14 S. 65 verlangt mindestens einen
   Parameter; 2.4 §3.14 S. 85 erlaubt auch die leere Liste. Der eigene Compiler
   akzeptiert bisher beide; ili2c lehnt nur 2.3 ab. Versionsprofil,
   Syntax-/Negativ-/Editorfälle fehlen noch.
2. **METAOBJECT_TRANSLATION:** Der vordefinierte Inhalt aus Anhang A
   (2.3 S. 95, 2.4 S. 122) fehlt. Die eigenen beiden Originale werden abgelehnt,
   ili2c akzeptiert sie. Name/NameInBaseLanguage, beide UNIQUE-Regeln und ihre
   öffentlichen Bindungen sind im bestehenden MetadataBindings zu ergänzen.

Auch die vollständigen übrigen E1–E5-Pflichten, insbesondere Typrelationen,
Builtins, Abstraktions-/Übersetzungsfragen und Quell-Metaattribute, bleiben offen.
Detaillierte JVM-/TeaVM-Graphparität, unabhängige Prüfung der 18 normativen
Referenzausnahmen, F/G, reale eingefrorene Closures und die übrigen
P3-Pflichten sind nicht durch die portable Annahme-/Ablehnungsbilanz erledigt.
**E, G2 und P3 bleiben offen.**

## Weiterer Normabgleich E4

Alle 19 expliziten BuiltinFunctions-Einträge sind einzeln als offene Prüfregeln
zugeordnet; ein vorhandenes `complete=true` ist noch kein vollständiger Normnachweis.
Zusätzlich fehlen die in 2.4 §3.14, S. 87 beschriebenen Funktionen
`areAreasWithoutGaps` und `areAreasWithoutGaps2` im Signaturkatalog.
Sie stehen als eigene offene E4-Regeln in der bestehenden Matrix.
Bei `areAreas2` widersprechen sich Funktionskapitel (`OBJECTS`, S. 87)
und illustrativer Anhang A (`OBJECT`, S. 122); dies ist vor Freigabe mit
versionsgerechten Kontrollen zu entscheiden. Keine neue Referenzausnahme.
