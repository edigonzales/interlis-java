# G2 E1 – Funktionsprofil und METAOBJECT_TRANSLATION

## Teilumfang und Abnahme vor Umsetzung

WP-21/22/24/27; SYN-004/005/006/010/013/015,
META-003/006/008/011/015, SEM-005/010/014 und TEST-011/012.
Ausgangspunkt: 489 JVM-Tests, 1782 portable Fälle und 574 PASS / 19 XFAIL
im letzten verifizierten Lauf. Historische Berichte bleiben erhalten.

Bearbeitet werden die Matrixregeln `PROFILE.emptyFunctionParameters` und
`PREDEFINED.metaobjectTranslation` sowie die unmittelbar zugehörige
versionsgerechte leere Aufrufliste:

- Funktionsdeklaration und -aufruf benötigen in 2.3 mindestens einen Parameter
  beziehungsweise ein Argument; 2.4 erlaubt auch leere Listen. Der bestehende
  Sprachprofilvertrag liefert eine originale Klammerbereichsdiagnose auch im
  Editor und bei Ausdrucksfragmenten. Argumentanzahl/Typprüfung bleiben nötig.
- METAOBJECT_TRANSLATION ist in beiden Versionen eine eigene konkrete
  vordefinierte Klasse ohne Basisklasse. Sie enthält Name und NameInBaseLanguage
  als MANDATORY NAME, je eine separate UNIQUE-Regel, geordnete öffentliche
  Attribute, Typen, Ausdruckspfade, Herkunft und Bindungen.
- Imports, Vererbung, Übersetzungen, Quellreihenfolge, Queries und frische/
  wiederverwendende Editfolgen verwenden die bestehenden gemeinsamen Modelle.

Norm: 2.3 §2.13 (S. 63), §2.14 (S. 65), Anhang A (S. 95);
2.4 §3.13 (S. 82), §3.14 (S. 85), Anhang A (S. 122).
URLs/Dokumentpins stehen in der G2-Regelmatrix. Die vier ursprünglichen
Reproduktionen sind in `g2-E1-binding-reference-2026-10-25.json` erhalten.

Nichtziele: Funktionsausführung, Transferdaten-/Eindeutigkeitsprüfung zur Laufzeit,
Metadatenbeschaffung und übrige offene E1–E5-/F-/G-Verpflichtungen.
Keine neue Abstraktionshierarchie oder öffentliche Konstruktorvariante.
G2 und P3 bleiben offen.

## Notwendige Grundlagenkorrekturen innerhalb dieser Lieferung

Die öffentliche Nutzung der neuen Basisklasse hat drei unmittelbar betroffene
Grundlagenfehler nachgewiesen:

- Die Attribut-Überschreibungsprüfung überging vordefinierte Attribute mangels
  Quelldeklarationsindex. Sie verwendet nun deren gebundene Namen/Typen.
  NAME und URI veröffentlichen dabei die festen Grenzen 255/1023; sonst wurde
  eine unveränderte NAME-Überschreibung fälschlich als Aufweitung gewertet.
- Ein dadurch erstmals geprüfter bestehender COORDSYSTEM.Axis-Override benötigt
  die vorhandene gemeinsame Struktur-/Collection-Ziel- und Kardinalitätsregel.
  Gültige Einschränkung, falsche Zielstruktur, BAG/LIST-Wechsel und Aufweitung
  sind mit Compiler- und öffentlichen canExtend-Abfragen gegengeprüft.
- CLASS RESTRICTION kann im gemeinsamen Grammatikzweig seine Einschränkung als
  äussere structureRef erhalten. Die Abbildung priorisiert den umgebenden Typ
  und bewahrt sämtliche Einschränkungen, statt das erste Ziel als Typ zu nehmen
  oder die Einschränkung wegzulassen. Vordefinierte Klassenziele verwenden den
  vorhandenen Binder auch in Funktionssignaturen.

Die neue Klammerprofilprüfung hat im ersten Versuch bei fehlender RPAR einen
Nullzugriff ausgelöst. Dieser wurde vor Abnahme korrigiert und mit STRICT-/EDITOR-
und portablen unvollständigen Listen abgesichert. Nur geschlossene originale
Listen erhalten die Profilprüfung; Syntaxrecovery liefert ihre eigenen Fehler.

## Separat offener Recovery-Fund

Ein abgebrochener gewöhnlicher Funktionsaufruf `f(` innerhalb einer Constraint
kann bereits vom Parser als Attribut ohne Typ wiederaufgenommen werden.
Das Schema wird abgelehnt; zusätzlich zu Quellfehlern erscheint bisher
INCOMPLETE_SUPPORT/MISSING_ATTRIBUTE_TYPE. Dies ist keine gültige Sprachfunktion
und rechtfertigt keine XFAIL-Ausnahme. Die reine Fehlerklassifikation dieses
Recovery-Falls bleibt als eigene E1-Regel offen. Die Recovery-Regression dieser
Lieferung verlangt fehlertolerante Syntaxsnapshots und Ablehnung ohne internen
Fehler, behauptet aber noch keine ausschliessliche SOURCE_ERRORS-Klassifikation
für diese abgebrochene Eingabe. Gültige Gegenfälle und gezielte fachliche
Ablehnungen bleiben von diesem Sonderfall getrennt.

## Ausgeführter Abschlussnachweis

Die beiden ursprünglichen offenen Funde `EMPTY_FUNCTION_PARAMETERS_23` und
`PREDEFINED_METAOBJECT_TRANSLATION` sind mit den beschriebenen öffentlichen
Inhalten und Fehlerpfaden behoben. Die vier ursprünglichen Quellen liegen
byteidentisch gepinnt unter `testdata/conformance/g2-e1-functions-metadata/`.
`E1FunctionMetadataContractTest` enthält 13 JVM-Verträge; keine neue
Produktionsklasse, öffentliche API-Signatur oder neue Fassade war nötig.

Der abschliessende Gesamtaufruf endet **BUILD SUCCESSFUL / Exitcode 0**:

- **502 JVM-Tests und 20 Wrappertests** bestehen. `check` umfasst SDK-Beispiel,
  CLI, Workspace, LSP, Modell, Projektion und betroffene Typ-/Sprachqueries.
- **1848 portable Fälle je Laufzeit** bestehen auf TeaVM-JavaScript,
  WasmGC/Node, Chromium, Firefox, WebKit und **echtem Safari**. Aktueller
  Web-Image-Build sowie Node-/Browserprüfungen bestehen ebenfalls.
  54 neue statische Fälle und zwölf fortgesetzte Edits ergänzen den Bestand.
- **54 reale Prozesspaare mit ili2c 5.6.9** erreichen alle festgelegten eigenen
  Ergebnisse und stimmen in Annahme/Ablehnung vollständig überein.
  Die JSON-Diagnosen prüfen reguläre Quellfehler; die zwei unvollständigen
  gewöhnlichen Aufrufe sind ausdrücklich als offener Recovery-Sonderfall
  gekennzeichnet. Die Übereinstimmung allein belegt deren Modellinhalte nicht;
  dafür stehen die JVM-Modell-/Querytests.
- Neun CLI-/SDK-Prozessprüfungen und Diagramm-/Adapterprüfung bestehen.
- Die unveränderten **593 eingefrorenen Suitefälle** ergeben **574 PASS /
  19 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**. Der sichtbare Rohvergleich bleibt
  **574 PASS / 1 XFAIL / 18 FAIL**.

Es wurde kein bisheriger Suite-FAIL umklassifiziert und keine neue
Referenzausnahme angelegt. Die zwei zusätzlichen E1-Funde liegen ausserhalb
der eingefrorenen Suite. Quellenpins und ursprüngliche Erwartungen bleiben
unverändert. Die 18 normativen Referenzabweichungen benötigen weiterhin ihre
unabhängige Prüfung; INTERLIS 1 bleibt die separate Scope-Ausnahme.

Ein erster Gesamtlauf des aktuellen Quellstands überschritt in Firefox die
bestehende 60-Sekunden-Grenze; die übrigen Laufzeitprüfungen bestanden.
Die Grenze wurde nicht verändert. Eine Browserwiederholung und der spätere
Gesamtaufruf bestanden auch in Firefox. Ursache des ersten Timeouts ist nicht
nachgewiesen; dies ist kein Performance-Abnahmenachweis. Der fehlgeschlagene
Browserbericht und das Log bleiben unter
`build/reports/stabilization/g2-e1-function-metadata/failed-firefox/` erhalten.
Ein Wiederholungsaufruf ohne GraalVM-Argument scheiterte an der Konfiguration;
auch dieses Log wird erhalten, nicht als Prüfresultat gewertet.

[Verifikationsmanifest](verification-2026-10-25-g2-E1-functions-metadata.json),
[Prozessquellen, Pins und Ausgaben](g2-E1-functions-metadata-reference-2026-10-25.json).
Die übernommenen Rohberichte/Logs liegen unter
`build/reports/stabilization/g2-e1-function-metadata/verified/`.
Manifest und Berichte nennen Quellstand, Werkzeugversionen und Artefakthashes.

## Verbleibende E1-Ursachen und nächster Schritt

Die Matrix enthält **238 einzelne Prüfregeln: 37 VERIFIED, 1 IMPLEMENTED,
200 OPEN**. Das ist ein Arbeitsinventar und keine Vollständigkeitsquote;
zusätzliche Normabsätze/Alternativen sind weiterhin zu prüfen. Sieben genaue
Regeln dieser Lieferung sind nachgewiesen. Die ursprüngliche Funktions- und
METAOBJECT_TRANSLATION-Regel sind geschlossen, die allgemeinen Produktions-
und Featurefamilien bleiben ohne vollständige Einzelabnahme offen.

Neben der oben dokumentierten Recovery-Klassifikation sind nun drei konkrete
Modellkopfregeln einzeln E1 zugeordnet:

- `NOINCREMENTALTRANSFER` muss in 2.4 im Syntax-/Semantikmodell erhalten bleiben
  und in 2.3 profilgerecht abgewiesen werden.
- `CHARSET` benötigt gebundenen expliziten Wert beziehungsweise normativen
  Default, Herkunft und Verbraucherzugriff; das 2.3-Profil darf die 2.4-Syntax
  nicht still akzeptieren.
- `XMLNS` benötigt ebenso Originalangabe, wirksame Modellinformation und die
  richtige Versions-/Übersetzungsprüfung.

Sechs unabhängige Prozessproben zeigen aktuell Annahme aller drei Optionen in
beiden Versionen beim eigenen Compiler. ili2c weist jeweils nur 2.3 ab.
`SyntaxTreeBuilder.buildModel`, `ModelSyntax` und `ModelSymbol` lassen die
Optionen bislang weg; der vorhandene `P2HeaderOptionsTest` beweist ausschliesslich
Annahme. Normgrundlage ist 2.4 §3.5.1, S. 33–34, im Vergleich zur 2.3-
Modellproduktion §2.5.1, S. 26–28. Diese Funde ändern keine Baseline und werden
als nächste E1-Lieferung bearbeitet, einschliesslich positiver/negativer
öffentlicher Modell- und Editornachweise.

Danach folgen die übrigen E1–E5-Regeln in vereinbarter Abhängigkeit. Die
Modellklassen-/LineCoord-Abstraktion, CONTRACTED-Übersetzungsfrage und offene
Vorwärtsbindung bleiben sichtbar. Vollständige detaillierte JVM-/TeaVM-
Graphparität, F/G, reale eingefrorene Closures sowie weitere P3-Verbraucher-,
Plattform- und Performancepflichten sind nicht durch diese Lieferung erfüllt.
**E1, E, G2 und P3 bleiben offen.**
