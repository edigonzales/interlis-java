# Gebundene Topic-Abhängigkeiten bei Referenzattributen und Rollen

Stand: 21. Oktober 2026. WP-20/21/22; META-006/008/014/016,
SEM-008/010, SRC-002–005 und LSP-012/013 für die Quellreferenzen.
S2a/S2b bleiben PARTIAL; P2/P3 und G2/G3 sind offen. Der
[vorherige NO-OID-Nachweis](no-oid-reference-targets.md) bleibt historisch erhalten.

## Norm und umgesetzter Umfang

Grundlage sind die [Referenz 2.3](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
Abschnitte 2.5.2, 2.6.3 und 2.7.1, und die
[Referenz 2.4](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf),
Abschnitte 3.5.2, 3.6.3 und 3.7.1. Beide Versionen verlangen explizite
Datenabhängigkeiten; Erweiterungen übernehmen die Abhängigkeiten ihres Basis-Topics.
EXTERNAL regelt den Bezug auf andere Behälter und ersetzt DEPENDS ON nicht.

| Regel | Umsetzung und Nachweis |
|---|---|
| Direkte Abhängigkeit | Ein zugelassenes Referenz-/Rollenziel ausserhalb des eigenen oder eines Basis-Topics verlangt eine direkt deklarierte oder geerbte Abhängigkeit auf seinen Topic. Eine Kette von DEPENDS-ON-Kanten ist keine Erlaubnis für eine zusätzliche direkte Referenz. |
| Vererbung | Topic-Abhängigkeiten werden entlang der gebundenen Basiskette gelesen. Eine einzelne geerbte Struktur übernimmt hingegen keine Abhängigkeitsdeklarationen ihres bisherigen Topics: Ihr neuer verwendender Topic muss die Referenzen erlauben. |
| Zielmengen | Sämtliche OR-Alternativen und Einschränkungswurzeln werden geprüft. Eine Restriktion ersetzt die allgemeine Basismenge; eine Abhängigkeit auf den ausgeschlossenen Primär-Topic genügt nicht für den tatsächlich erlaubten Ziel-Topic. |
| Identität | Topic-IDs statt Namensgleichheit entscheiden. Gleichnamige Klassen in fremden Topics und gleichnamige Topics in verschiedenen importierten Modellen umgehen die Prüfung nicht. Die früheren Namensheuristiken für vermeintlich geerbte Ziele sind entfernt. |
| Strukturverwendung | Referenzen in verschachtelten Strukturwerten werden im verwendenden Topic geprüft, auch über Collections. Diese Auslegung folgt aus der datenmässigen Unabhängigkeit nach 2.5.2/3.5.2: Die Verwendung einer Vorlage darf keine nicht deklarierte Datenreferenz einführen. Eine besuchte Struktur wird je Verwendungskontext nur einmal traversiert. |
| MANDATORY-Überschreibung | Eine reine MANDATORY-Überschreibung übernimmt bei der Auflösung effektiver Mitglieder den gebundenen Basistyp. Die geerbte Referenzzielmenge bleibt für Abhängigkeitsprüfung und öffentliche Typabfragen vorhanden. Der unten dokumentierte frühere Zeitpunkt der Ausdrucksbindung bleibt eine separate Lücke. |
| Gebundene Namen | DEPENDS-ON-Ziele werden ohne Suffixersatz auf Topic-Identitäten gebunden und mit Quellreferenzen erhalten. Der öffentliche Navigationstest prüft den Ziel-Topic und dessen Bereich. Falsche Zielarten und unbekannte Namen sind Fehler. |
| Zyklen | Direkte, indirekte und über Vererbung entstandene Selbstabhängigkeiten sperren das Schema. Iterative Traversierung mit Arbeitsbudget-/Abbruchprüfung; kein zusätzlicher rekursiver Java-Aufrufstapel. |

Die Prüfung erfolgt nach der Bindung zusammen mit der bisherigen NO-OID-Prüfung
über effektive Mitglieder. Für eine in einem abgeleiteten Topic unqualifiziert
genannte Basisklasse wird nun die vorhandene Suche im Basis-Topic benutzt;
damit bleiben deren Vererbungsidentität und darauf aufbauende Restriktionen erhalten.

`TopicSymbol.dependencies()` enthält weiterhin direkt deklarierte, gebundene
IDs in Quellreihenfolge. `baseTopic()` bewahrt die Vererbung separat. Javadoc
und Spec 05/16/17 präzisieren diesen Vertrag; keine neue öffentliche Signatur,
kein Modul, keine zusätzliche Cacheebene und keine neue Pass-Infrastruktur.

## Ausgeführte Prüfungen

`TopicDependencyContractTest` ergänzt **zehn JVM-Verträge**: direkte/geerbte
Abhängigkeiten, Referenzrestriktionen und Rollenalternativen, effektive und
verschachtelte Mitglieder, MANDATORY, Namenskollisionen, mehrere importierte
Modelle, Zyklen, falsche Zielnamen sowie öffentliche Schema-/Navigationsabfragen.
Die gemeinsame portable Suite ergänzt **86 positive und negative Fälle**.
Die Vergleichsfälle verwenden voll qualifizierte Klassennamen; relative
Resolverkürzel sollen nicht die untersuchte Abhängigkeitsregel verdecken.

Der abschliessende kombinierte Lauf ergibt:

- **277 JVM-Tests**, keine Fehler und keine übersprungenen Tests; SDK-Beispiel erfolgreich.
- **806 portable Fälle** auf TeaVM-JavaScript/Node, WasmGC/Node, TeaVM-Worker in Chromium/Firefox/WebKit/**echtem Safari** und Web Image unter Node/Chromium/Firefox/WebKit.
- Reguläre Prozessabschlüsse mit Node 24.22.0, ohne erzwungene WasmGC-Beendigung; realer GraalVM-Web-Image-Build erfolgreich.
- Neun erfolgreiche CLI-Prozesse auf macOS arm64.
- Unveränderte Konformität: **538 PASS, 1 XFAIL, 54 FAIL, 0 ERROR/XPASS** bei 593 ausgewählten Fällen. Keine falsche Annahme in dieser eingefrorenen Suite; Pins, Baseline und XFAILs sind unverändert.

Der kombinierte Gradle-Lauf endet regulär mit Exitcode 1 ausschliesslich wegen
`conformanceCheck`. Exakte Kommandos, Rohberichte, Quellbaumdigest, Artefakthashes,
Hardware und Werkzeuge stehen im
[Verifikationsmanifest](verification-2026-10-21-depends.json).
Quellbaumdigest, Artefakt-/Rohbericht-/Loghashes, JSON-Dateien, lokale
Dokumentlinks und Codeblockgrenzen wurden abschliessend geprüft;
`git diff --check` besteht. Diese Dokumentprüfung ersetzt keine Laufzeitprüfung.
Die 54 Fälle sind im [aktuellen Fallkatalog](conformance-open-cases.json) erhalten:
45 Implementierungslücken und neun bereits dokumentierte Normabweichungen.

[86 Prozessvergleiche je Compiler](depends-reference-comparison-2026-10-21.json)
mit ili2c 5.6.9 enthalten exakte Eingaben, Inhaltshashes, Diagnosen und
fallweise Einordnung: **72 gleiche und 14 unterschiedliche Entscheidungen**,
keine unerwartete Entscheidung des aktuellen Compilers. Unterschiede betreffen
geerbte Topic-Abhängigkeiten, die vollständige Prüfung von Rollenrestriktionen
und die wirksamen Zielmengen beziehungsweise Verwendungskontexte geerbter
Referenzen. Diese begründeten Unterschiede sind nicht mit dem unveränderten
Ergebnis der eingefrorenen Konformitätssuite zu vermischen.

Die [früher dokumentierte falsche Annahme ohne DEPENDS ON](no-oid-follow-on-probes-2026-10-21.json)
ist durch die neuen direkten Negativfälle geschlossen. Alte Reports werden
nicht nachträglich geändert. Ein früher Zwischenlauf ohne die zusätzlichen
MANDATORY-Fälle ersetzt den abschliessenden Nachweis nicht.

## Offene Punkte und nächster Schritt

Eine zusätzliche [Folgeprobe](depends-follow-on-probe-2026-10-21.json) zeigt
einen eigenen Fehler: Bei einem Constraintpfad durch eine reine
MANDATORY-Überschreibung bindet der Compiler Ausdrücke, bevor der geerbte
Referenztyp verfügbar ist. Er lehnt `ref->flag` fälschlich als ungelöst ab;
ili2c akzeptiert denselben voll qualifizierten Fall. Die spätere
Abhängigkeitsprüfung und der veröffentlichte Referenztyp sind richtig,
aber der frühere Ausdrucksschritt muss denselben wirksamen Typ verwenden.
Dies ist der nächste konkrete fachliche Schritt; die Probe zählt nicht zu
den erfolgreichen portablen Fällen und nicht zu den 593 eingefrorenen Fällen.

Weiter offen: erforderliche konkrete Zielunterklassen bei abstrakten Zielmengen,
alle Kombinationen von Strukturrestriktionen und polymorphen Strukturverwendungen,
allgemeine Vorwärts-/relative Namensauflösung und sehr tiefe Vererbung über
bestehende interne Grenzen hinaus; vollständige gemischte Sprach-/Übersetzungs-
kontexte und OID-Builtinimporte. View-/Grafik-Abhängigkeiten und die restlichen
P2-Familien sind nicht durch diesen Abschnitt vollständig abgenommen.

Vollständiges `releaseCheck`, Performance-/Abbruch-/1'000-Edit-Abnahme,
Linux-/Windows-Prozesse, vollständiger geo.so.ch-Korpus und die gesamten
CLI-/LSP-Verbraucherprofile bleiben offen. **P3 ist nicht abgeschlossen.**
