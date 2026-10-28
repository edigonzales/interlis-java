# Dateireihenfolgeunabhängige importierte Domaintypen

Stand: 21. Oktober 2026. WP-20/21/22; META-006/008/014/016,
SEM-008/010/014, SRC-002–005. S2a/S2b bleiben PARTIAL; P2/P3 und G2/G3 offen.
Der [vorherige MANDATORY-Nachweis](mandatory-override-types.md) bleibt historisch erhalten.

## Fachlicher Umfang und Umsetzung

Die [Referenz 2.3](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
§2.5.4/2.6.1/2.6.2, und die
[Referenz 2.4](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf),
§3.5.1/3.5.4/3.6.1/3.6.2, bestimmen Namen, Imports und Attributtypen anhand der
Deklarationen. Die Sortierung der Dateien ist kein fachliches Auswahlkriterium.
Ein qualifizierter Import erlaubt keine beliebigen unqualifizierten Zugriffe.
MANDATORY ohne erneute Typangabe übernimmt weiterhin den geerbten Typ.

Der bisherige Fehler entstand zwischen Bindung und Typprüfung: Die Domain-ID
war bekannt, ihr Typdescriptor aber noch nicht aufgebaut. Eine früh benötigte
Domain wird jetzt über die vorhandene Typzuordnung aufgelöst. Ihr späterer
Symbolaufbau übernimmt dieselbe TypeId; Domainbedingungen werden weiterhin
separat aufgebaut und geprüft. Der Descriptorzugriff liest diese Bindung,
ohne dabei neue Symbole aufzubauen. Eine kleine Zyklensicherung verhindert
rekursive Typabhängigkeiten; `DOMAIN_TYPE_CYCLE` ist ein fachlicher Fehler
mit Diagnosecode `ILI-SEM-0010`, kein interner Fehler und kein gültiges Schema.
Der vorhandene OID-Selbstzyklustest erwartet deshalb diesen präziseren Grund
statt des erst nachfolgenden `OID_EXTENSION`. Die Ablehnung bleibt bestehen.

Importierte Funktionen und lokale Funktionen mit importierten Signaturtypen,
Skalarvergleiche, geerbte MANDATORY-Attribute, Struktur-/LIST-Indexpfade,
UNIQUE/LOCAL/EXISTENCE sowie Enum-Basen und FORMAT-Komponenten nutzen dieselbe
frühe Bindung. Eine Struktur wird anhand ihrer indexierten Deklarationsart
erkannt; sie muss dafür noch keine fertige Symbolinstanz besitzen.
`DomainReferenceType` und `EffectiveType.aliasPath()` behalten ihre Identitäten.
Navigation führt weiterhin auf die importierte Deklaration.

Die bisherige globale Suche nach einem bereits aufgebauten, gleichnamigen Typ
ist entfernt. Sie konnte falsche Qualifikationen abhängig von der Dateisortierung
akzeptieren. Normale Sichtbarkeit und geerbte Topic-Mitglieder entscheiden.

Ein einwertiges Enum und eine zusammengesetzte Einheit besitzen teilweise
dieselbe Parserform. Die Syntaxerfassung verwendet nun den umgebenden
DOMAIN-/UNIT-Abschnitt; beide Deklarationsarten bleiben unterscheidbar.
Enumname, Basis und Werte stammen aus strukturierten Tokens mit ihren echten
Quellbereichen. Die bisherige Textzerlegung mit künstlich grossen Bereichen
ist entfernt. Keine neue öffentliche API, Produktionsklasse oder Pass-Infrastruktur.

## Tatsächliche Prüfungen

`ImportedDomainTypeContractTest` ergänzt zehn JVM-Tests. Sie prüfen beide
Sprachversionen und beide lexikalischen Quelldateireihenfolgen, positive und
negative Typfälle, öffentliche Typidentitäten, Navigation, die Erhaltung von
Domainbedingungen, Zyklen sowie frische gegenüber wiederverwendeter Analyse
nach Änderungen des importierten Typs. Alte Snapshots bleiben unverändert.

Die gemeinsame portable Suite ergänzt **84 Mehrdateifälle**. Ihre zwei Quellen
werden separat in dieselbe Sitzung eingespielt; beide Dateisortierungen werden
geprüft. Zusammen mit dem bestehenden Umfang sind das **948 Fälle**. Die
[Prozessvergleiche](alias-reference-comparison-2026-10-21.json) enthalten dieselben
Eingaben für unsere CLI und ili2c 5.6.9, mit Quellen, Inhaltspins, Streams,
Exitcodes und fallbezogener Bewertung: **66 AGREE, 18 DIFFER**, keine unerwartete
Entscheidung unseres Compilers. Numerische Boolean-Argumente,
LIST-Indizes oberhalb der deklarierten Grenze und FORMAT-Präzision behalten
die bereits begründeten Normregeln. Zusätzlich lehnt die Typprüfung
`INTERLIS.len(THIS)` in einer numerischen Domainbedingung ab; ili2c akzeptiert
diese zwei Fälle. Die 2.4-Referenz, §3.14/S. 86, verlangt für `len` TEXT.
Die externe Baseline wird nicht verändert.

Die [ursprünglichen Folgeproben](mandatory-follow-on-probes-2026-10-21.json)
sind mit identischen Quelltexten [erneut geprüft](alias-follow-on-probes-2026-10-21.json):
Beide Compiler akzeptieren jetzt alle fünf Eingaben, einschliesslich beider
Sortierungen in 2.3 und 2.4. Die konkrete falsche Ablehnung von `flag == #true`
ist geschlossen.

Abschliessend bestehen **299 JVM-Tests**, das SDK-Beispiel und neun macOS-CLI-
Prozesse. Alle **948 gemeinsamen Fälle** bestehen auf TeaVM-JavaScript/Node,
WasmGC/Node, im TeaVM-Worker in Chromium/Firefox/WebKit und echtem Safari sowie
auf Web Image unter Node/Chromium/Firefox/WebKit. WasmGC endet regulär; der
reale Web-Image-Build besteht. Die eingefrorene Konformität bleibt bei
**538 PASS, 1 XFAIL, 54 FAIL, 0 ERROR/XPASS**. Der kombinierte Gradle-Lauf
endet mit Exitcode 1 ausschliesslich wegen `conformanceCheck`.

Werkzeugstände, Quellbaumdigest, Artefakthashes,
Rohberichte und tatsächliche Kommandos stehen im
[Verifikationsmanifest](verification-2026-10-21-alias.json).
Der produktive TeaVM-WasmGC-Pfad bleibt erhalten; ein erfolgreicher Inhalt
allein ersetzt keinen regulären Prozessabschluss. Die kombinierten Gates
bleiben wegen der eingefrorenen Konformität rot. Die
[offenen Konformitätsfälle](conformance-open-cases-2026-10-21-alias.json)
bleiben von den neuen gezielten Proben getrennt.
Quellbaumdigest, 14 Artefakthashes, acht Rohberichte, Quellenpins und Loghashes
sind gegengeprüft; die ursprünglichen Probequellen und der offene
Konformitätsfallbestand stimmen exakt überein. JSON-Dateien, 434 lokale
Dokumentlinks, Codeblockgrenzen und `git diff --check` bestehen.

## Neue konkrete Lücke und Arbeitsgrenzen

Der Referenzvergleich entdeckte in einer Kontrollfixture eine weitere fehlende
Prüfung: Eine konkrete zusammengesetzte Einheit verwendet eine abstrakte
Komponente. ili2c lehnt dies ab; unser Compiler akzeptiert es derzeit fälschlich.
Die [vier reproduzierbaren Fälle](alias-unit-follow-on-probes-2026-10-21.json)
enthalten beide Versionen und Dateisortierungen. Dies ist **eine
Implementierungslücke**, keine freigegebene Normabweichung. Die positive
Enum-/Einheitenfixture erklärt die zusammengesetzte Einheit jetzt ausdrücklich
ABSTRACT; sie wurde anschliessend erneut auf allen Laufzeitpfaden geprüft.
Die alten ungültigen Eingaben werden nicht als erfolgreiche Regression gezählt.
§2.9/3.9 der Referenzen regelt Einheiten und deren abstrakte Zusammensetzung.

Nächster fachlicher Abschnitt: gebundene Komponenten zusammengesetzter Einheiten
und deren Abstraktions-/Erweiterungsregeln, einschliesslich importierter Einheiten
und tatsächlicher Einheitenverwendung in numerischen Typen. Bereits vorhandene
Einheitenstrings sind kein Nachweis vollständiger Semantik. Dieser Abschnitt
implementiert keine zusätzliche Einheitenengine.

Weitere offene Pflichten bleiben bestehen: sämtliche Typ-/Constraint- und
View-Kombinationen, Vererbung von Domainbedingungen, vollständige Struktur-
restriktionen und Referenzzielmengen, OID-Builtinimporte, FORMAT-Relationen und
Übersetzungen, das 2.4-Errata-Profil zur Struktur-zu-Klasse-Vererbung sowie die
vollständige Normmatrix. Aus den neuen Fällen folgt keine allgemeine Abnahme
beliebig tiefer Abhängigkeiten oder aller Import-/Resolverkombinationen.

Nicht ausgeführt beziehungsweise nicht vollständig abgenommen sind die
verbindlichen M1-Performance-/Abbruch-/1'000-Edit-Läufe, Linux-/Windows-
Distributionsprüfungen, vollständige geo.so.ch-Closure und `releaseCheck`.
Die offenen CLI-/LSP-/Resolververträge bleiben unverändert. **P3 bleibt offen.**
