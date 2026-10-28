# NO OID bei Referenzattributen und Rollen-Zielmengen

Stand: 21. Oktober 2026. WP-20/21/22; META-006/008/014/016,
SEM-008/010, SRC-002–005 und LSP-012/013 für erhaltene Referenzbereiche.
S2a/S2b bleiben PARTIAL, P2/P3 und G2/G3 offen. Der
[vorherige Nachweis](oid-followups-and-abstract-format.md) bleibt historisch erhalten.

## Normgrundlage und Umsetzung

Massgeblich sind die [Referenz 2.3](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
Abschnitte 2.5.3, 2.6.3 und 2.7.1, sowie die
[Referenz 2.4](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf),
Abschnitte 3.5.3, 3.6.3 und 3.7.1. ili2c 5.6.9 bleibt Vergleichsreferenz;
abweichendes Referenzverhalten ersetzt diese Normregeln nicht.

| Gegenstand | Implementierter und geprüfter Teilumfang |
|---|---|
| Klassen als Ziele | In 2.4 sperrt effektives explizites oder implizites NO OID Referenzattribute und Rollen. Das Verbot wird nicht auf 2.3 übertragen. EXTERNAL hebt es nicht auf. |
| Herkunft der Identität | Objektregeln aus Klasse, Vererbung und Topic-Default zählen; Basket-OID und Identität des referenzierenden Objekts zählen nicht. Explizites NO OID am Ziel bleibt wirksam. |
| Zielmengen | Jede zugelassene Restriktionswurzel und jede Rollenalternative wird geprüft. Einschränkungen ersetzen die allgemeine Basismenge. Ein gültiges erstes Ziel verdeckt kein ungültiges weiteres Ziel. |
| Vererbung und Kontext | Effektive Mitglieder und der lokale EXTENDED-Kontext bestimmen die verwendeten Ziele. Gemischte Quelldateien prüfen die Sprache der referenzierenden Deklaration und die gebundenen Identitätsregeln des Ziels. |
| Assoziationen als Ziele | In beiden Sprachversionen benötigen sie eigene Identität aus OID AS bzw. OID-Eigenschaft. Identifikation nur durch die Rollen reicht nicht. |
| Referenztyp | Primärziel, Restriktionen und EXTERNAL bleiben typisiert erhalten. Gemeinsame Typqueries und Attributüberschreibung prüfen Mengeninklusion; Pfade verwenden den gemeinsamen Zielkontext. Keine Wahl des ersten Listenelements. |
| Quellbindung | Syntaxkinder und Namensreferenzen enthalten alle Einschränkungsnamen mit eigenen UTF-16-Bereichen, auch in Collections. Eine öffentliche Navigationsabfrage prüft das tatsächliche Restriktionsziel. |
| EXTERNAL | Topicübergreifende Referenzattribute benötigen EXTERNAL, auch über ANYCLASS-Restriktionen und Collection-Elemente. Geerbte Ziele im Topic-Vererbungsweg bleiben zulässig. Die bestehende Rollenprüfung und die neue Attributprüfung verwenden einen gemeinsamen internen Helfer. |

ANYCLASS wird als offene polymorphe Zielmenge repräsentiert, nicht als konkrete
Klasse mit erfundener NO-OID-Festlegung. Explizite Restriktionen werden statisch
geprüft; es wird keine zufällig vorhandene Closure als abgeschlossene Menge
aller zukünftigen Objekte verwendet. Konkrete Transferreferenzen sind später
datenbezogen zu validieren. Damit ist insbesondere **noch keine vollständige
Prüfung der Existenz zulässiger konkreter Unterklassen** nach 2.6.3/3.6.3 belegt.

Die unveröffentlichte Vorab-API 0.2 erhält für `ReferenceType` ein optionales
Primärziel und eine unveränderliche Restriktionsliste. `ReferenceTypeSyntax`
erhält die zugehörigen Namen. Interne Verbraucher, Spec 04/05/16 und
[Migration](../../api-migration-0.2.md) sind synchronisiert. Kein neues Modul,
keine parallele Alt-API und keine zusätzliche Pass-Infrastruktur.

## Tatsächlich ausgeführte Prüfungen

`NoOidReferenceContractTest` ergänzt **elf JVM-Verträge**. Die gemeinsame
portable Suite ergänzt **67 Fälle**. Ältere positive Rollen-/Referenzmodelle
bekommen explizite gültige Objektidentität; OID-Negativfälle bleiben erhalten.
Die angepassten Dateien `base24.ili`/`survey24.ili` wurden zusätzlich mit beiden
Compilern erfolgreich geprüft; ihre aktuellen Inhaltshashes und Prozessausgaben
stehen im Referenzvergleich. Frühere Referenzberichte werden nicht überschrieben.

Der vollständige abschliessende Lauf umfasst:

- **267 JVM-Tests**, keine Fehler und keine übersprungenen Tests; SDK-Beispiel erfolgreich.
- **720 portable Fälle** auf TeaVM-JavaScript/Node, WasmGC/Node, TeaVM-Worker in Chromium/Firefox/WebKit/**echtem Safari** sowie Web Image unter Node/Chromium/Firefox/WebKit.
- Reguläre Prozessabschlüsse mit dem gepinnten Node 24.22.0; kein erzwungener WasmGC-Abschluss. Reale GraalVM-Web-Image-Erzeugung erfolgreich.
- Neun erfolgreiche CLI-Prozesse auf macOS arm64.
- Konformität **538 PASS, 1 XFAIL, 54 FAIL, 0 ERROR/XPASS**, 593 unverändert ausgewählte Fälle; keine falsche Annahme innerhalb dieser Suite.

Der kombinierte Gradle-Lauf endet mit Exitcode 1 ausschliesslich wegen
`conformanceCheck`. Exakte Kommandos, Quellbaumdigest, Artefakthashes, Plattform,
Werkzeugversionen und Rohberichte stehen im
[Verifikationsmanifest](verification-2026-10-21-no-oid.json).
Abschliessend sind Quellbaumdigest, sämtliche Manifest-Artefakt-/Rohbericht-/Loghashes,
JSON-Dateien, lokale Dokumentlinks und Markdown-Codeblockgrenzen geprüft;
`git diff --check` besteht. Diese Dokumentprüfung ist kein zusätzlicher Runtime-Nachweis.
Der erste Gesamtlauf deckte eine falsche Annahme bei
`RefAttrCrossTopic_Fail` auf: Nach verbesserter Zielbindung fehlte die
EXTERNAL-Prüfung. Die Korrektur und elf zusätzliche portable Scopefälle sind
im abschliessenden Lauf enthalten. Der Zwischenlauf gilt nicht als Abnahme.

Die [67 Prozessvergleiche pro Compiler](no-oid-reference-comparison-2026-10-21.json)
enthalten exakte Eingaben, Hashes, Diagnosen und eine fallweise Normeinordnung:
39 gleiche und 28 unterschiedliche Annahmeentscheidungen, keine unerwartete
Entscheidung des aktuellen Compilers. ili2c liefert in sechs Fällen einen
internen AssociationDef/Table-Castfehler. Gleiche Ablehnung ist dort kein
Beweis für gleiche fachliche Prüfung.

Die eingefrorene Konformitätssuite hat gegenüber dem vorherigen Lauf zwei
zusätzliche Abweichungen:

| Fall | Einordnung |
|---|---|
| `ili23.Association23Test.association_AssociationAsTarget` | Assoziation ohne eigene OID wird als Rollenziel verwendet; Ablehnung gemäss 2.7.1. |
| `ili24.Attribute24Test.attributeRef_RefAttrInClass` | Referenzattribut zeigt auf eine Klasse mit implizitem NO OID; Ablehnung gemäss 3.5.3. |

Damit verbleiben 45 Fälle mit Implementierungslücken und neun dokumentierte
Normabweichungen. Alle 54 Fälle sind im [Fallkatalog](conformance-open-cases.json)
enthalten; Pins, Baseline und XFAILs bleiben unverändert.

## Konkrete Restpunkte und nächste Arbeit

Eine zusätzliche, ausserhalb der eingefrorenen Suite ausgeführte
[Probe](no-oid-follow-on-probes-2026-10-21.json) zeigt eine **falsche Annahme**:
EXTERNAL ohne das erforderliche DEPENDS ON wird akzeptiert, während ili2c
ablehnt. Die neue EXTERNAL-Prüfung ersetzt keine Abhängigkeitsprüfung.
Als nächster Abschnitt sind gebundene Topic-Abhängigkeiten für Referenzattribute
und Rollen, geerbte Abhängigkeiten und sämtliche Zielrestriktionen zu prüfen.
Diese bekannte Lücke ist weder durch die grüne portable Suite erledigt noch
als zulässige Normabweichung umklassifiziert.

Weitere offene Abnahmen: erforderliche konkrete Zielunterklassen bei abstrakten
Zielmengen, vollständige Kombinationen geerbter Referenzattribute/Topic-Erweiterungen
in gemischten Sprachversionen, Referenzübersetzungen und alle Scope-/Importfälle.
Die zusätzliche Vorwärtsrestriktionsprobe wird von beiden Compilern abgelehnt;
daraus wird keine umfassende Namensauflösungsabnahme abgeleitet.
OID-Builtinimporte, übrige abstrakte FORMAT-/Übersetzungskombinationen sowie
Enum-/Collection-/View-/Transferpflichten bleiben offen. Keine neue Geometrie-
oder Transferdatenimplementierung gehört zu diesem Abschnitt.

Vollständiges `releaseCheck`, M1-Editor-/Batch-/Abbruchbudgets, 1'000-Edit-Retention,
Linux-/Windows-Prozesse, vollständiger geo.so.ch-Korpus sowie Legacy-CLI- und
LSP-Verbraucherprofile sind weiterhin nicht vollständig abgenommen. **P3 bleibt offen.**
