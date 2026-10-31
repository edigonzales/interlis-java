# P3 – fachliche Bewertung des vollständigen Corpus

**Kandidatenbewertung, unabhängige Abnahme offen.** AC-P3-12, TEST-007/010.
Die 347 unveränderten Wurzeln und ihre 366 expliziten Closurequellen stehen im
[Manifest](../evidence/p3/full-corpus/manifest.json), die exakten Erwartungen in
[assessment.json](../evidence/p3/full-corpus/assessment.json). Originalbytes,
Indexpin und historische Suite-/13er-Corpuserwartungen bleiben unverändert.

Der explizit dekodierte Referenzlauf mit ili2c5.6.9 akzeptiert alle347 Wurzeln.
Unser bewertetes Profil erwartet **262 erfolgreiche Compiles und85 begründete
Quellablehnungen**. Das ist weder Referenzgleichheit noch vollständige
Produktionskompatibilität mit diesem Bestand. Mehrere Regeln können dieselbe
Wurzel betreffen; die Zahlen der folgenden Tabelle dürfen nicht addiert werden.

| Regel / betroffene Wurzeln | Norm oder ausdrücklich gewählter Projektvertrag | Entscheidung und Kontrollnachweis |
|---|---|---|
| NO OID als Referenzziel /12 | INTERLIS2.4 §3.5.3 S.36 und §3.6.3 S.41 | Bestätigte strikte Normentscheidung erhalten; `NoOidReferenceContractTest.classNoOidReferenceBanIsSpecificTo24AndAppliesToRolesAndAttributes`, positive OID-/2.3-Kontrollen. Betrifft auch CHBase-V2-Metaklassen; REFSYSTEM-BASKET-Freigabe hebt die OID-Regel nicht auf. |
| Äussere Typnamen /56 | 2.3 §2.5.4 S.30–31;2.4 §3.5.4 S.38 | Ausdrücklicher Nutzerentscheid: strikt sperren. `ScopeNamespaceContractTest` prüft Ablehnungen sowie qualifizierte Referenzen und explizite Erweiterungen. |
| Toleranz null /3 | 2.3 §2.8.12.2 S.50;2.4 §3.8.12.2 S.64 | Null bleibt verboten; freigegebene kleine positive2.3-Toleranzen bleiben gültig. `GeometryRulesContractTest.positiveToleranceHasNoFixedMinimumAnd24RetainsDeclaredPrecision`. |
| Widersprüchliche skalare Metadaten /7 | Projektspezifikation04 E5;2.3 §2.2.8.1 und2.4 §3.2.8.1 definieren Kommentare, keinen allgemeinen Mehrfachwertvertrag | `comment`/`ili2db.dispName` bleiben skalar. Freigabe für wiederholte `fn.param` und mehrzeilige Strings bleibt erhalten. `SourceMetadataContractTest.identicalAssignmentsAreAllowedAndConflictsAreSourceErrors`. |
| Fehlendes `=` in `!!@comment STANDARDOID…` /5 | Projektspezifikation04 E5: `key=value` | Echte Parsingdiagnose; keine Laufzeitstörung. `SourceMetadataContractTest.malformedAssignmentsAreParsingErrorsWithoutAnInternalFailure` und positive korrekte Zuweisungen. |
| Obligatorischer Strukturwert ohne konkrete zulässige Struktur /2 | 2.3 §2.6.4 S.33;2.4 §3.6.4 S.41–42 | ABSTRACT beziehungsweise konkrete verfügbare Struktur erforderlich. `StructureValueContractTest.requiredWildcardsAndUnavailableConcreteStructuresNeedExplicitAbstractAttributes` prüft beide gültigen Alternativen. |
| Individueller Objektwert im SET-Hauptausdruck /1 | 2.3 §2.12 S.60;2.4 §3.12 S.78 | Nutzerentscheid erhalten; `WHERE` bleibt objektbezogen. `SetConditionContextContractTest.whereRetainsIndividualValuesAndPublicReferencesWhileTheBodyUsesTheSet`. |
| Zwei Rollen im selben tatsächlichen Navigationsnamensraum /1 | 2.3 §2.5.4 S.30–31 /§2.7.1 S.34;2.4 §3.5.4 S.38 /§3.7.1 | Echte Kollision bleibt Fehler. Unbeteiligte Klassen und die eigene Zielklasse werden nicht pauschal in den gegenüberliegenden Navigationsraum eingemischt. `RoleNamespaceContractTest` prüft beide Richtungen und Vererbung; keine allgemeine Kompatibilitätsausnahme. |
| Inkompatibles tatsächliches Funktionsargument /9 | 2.3 §2.14 S.65–66;2.4 §3.14 S.84–87; tatsächlich deklarierte Signaturen | Sieben Wurzeln übergeben Geometriewerte an `SO_FunctionsExt.area(OBJECTS…)`; zwei übergeben Objektmengen an `GeoW_FunctionsExt.Union(ANYSTRUCTURE…)`. Keine Gleichsetzung von Geometrie, Einzelobjekt, Objektmenge und Strukturwert. `StructureValueContractTest.functionContextAcceptsObjectsAsTheirStructureAndNormalAssignmentDoesNot` sowie Objekt-/Objektmengen-Funktionsverträge erhalten die erlaubten Singleton-/Einzelobjektfälle. |
| Kontinuierliche Unterteilung ohne CIRCULAR /2 | 2.3 §2.8.5 S.41;2.4 §3.8.5 S.52 | Ausdrücklichen Nutzerentscheid erhalten. `NumericSubdivisionContractTest.continuousSubdivisionsRequireCircularityAndTheCorrectConversionPeriod` enthält erfolgreiche CIRCULAR-Kontrollen. |
| Obligatorisches UNION-Attribut fehlt in einer Basis /2 | 2.3 §2.15 S.69–70;2.4 §3.15 S.91 | Pflichtangabe muss aus jeder Basis stammen. `ViewBindingContractTest.joinsAndUnionsKeepAllBasesAndCheckMandatoryValues` prüft vollständige Basen und zulässige optionale Werte. |

Die Normverweise verwenden dieselben Primärdokumente und Seitenzählungen wie
[COMPILER_NORM_DECISIONS.md](../g2/COMPILER_NORM_DECISIONS.md). Dessen bestätigte
Profile gelten unverändert. Für jede Negativwurzel enthält die JSON-Bewertung
Wurzel/Version/Datei, zugeordnete Regeln, exakte Gategründe und vollständige
Gate-Diagnosevektoren mit IDs, Argumenten und Quellbereichen. Unangeforderte
Modelldiagnosen werden im vollständigen Plattformcapture erhalten, beeinflussen
aber gemäss Closurevertrag nicht nachträglich die Gültigkeit der Wurzel.

## Vor dem Einfrieren korrigierte Compilerfehler

REFSYSTEM BASKET in TYPE MODEL wurde ausdrücklich wie ili2c freigegeben und in
der Normübersicht dokumentiert. Andere gefundene Fehler waren keine neuen
Kompatibilitätsentscheidungen: qualifizierte vordefinierte Typen, Zeitklassen,
Rollen nach Topicnavigation, vollständige ALL-OF-Vererbung, legale generische
Referenzsystembindung und fälschlich topicweit verglichene Rollen-/Attributnamen.
Die generische Referenz darf offen bleiben; konkrete CONTEXT-Alternativen müssen
die tatsächlichen Referenzsystemverträge erfüllen (2.4 §3.8.8 S.57–58).

Die drei durch den Rollenfehler betroffenen Wurzeln sind nach der Korrektur
positiv. Die anfänglichen259/88 sowie fehlerhaften generischen Diagnosen sind
explorative Rohbelege und keine freigegebenen Erwartungen. Neue Regressionen
prüfen gültige Namensräume, echte Kollisionen und Vererbung. Lange Kommentare
lösen auf WebKit keinen rekursiven Regex-Stacküberlauf mehr aus; UTF-16-Bereiche
und Tokenklassifikation bleiben gleich.

## Mess- und Abnahmegrenzen

[Sollbudgets](../evidence/p3/full-corpus/budgets.json) wurden getrennt von den
Istwerten nach der ersten vollständigen JVM-/Node-/Chromium-Kalibrierung
festgelegt. Das neue `performanceCheck` verlangt alle347 Wurzeln auf JVM,
WasmGC/Node, Chromium, Firefox, WebKit und echtem Safari, je drei unveränderte
Warmwiederholungen, vollständige fachliche Parität, exakte Erwartungen,
Artefaktgrössen und Ereignisdruck. Alte Benchmarktasks bleiben historische
Einzelprüfungen und ersetzen dieses Gate nicht.

Je1000 echte Textänderungen der grössten Originalclosure (247130 UTF-16-Einheiten)
auf JVM und Wasm/Node prüfen Cache-/Snapshotgrenzen4, Freigabe, abgelaufene
Handles und1000 zurückgewiesene alte Workspaceversionen. JVM prüft zusätzlich
kooperativen Abbruch nach tatsächlicher Arbeit und anschliessende Erholung.
Browserabbruch/-Wiederherstellung und Kapazitätsfehler besitzen eigene reale
Transport-/Clienttests; es wird kein1000-Edit-Browserlauf daraus abgeleitet.
JVM-Heap beziehungsweise Node-Prozess-RSS enthalten Parserbäume, Quellen,
Compilerwerte und JSON; das sind keine getrennten Heapanteile. Die Browser
besitzen keine browserübergreifend verlässliche Heapmessung, deshalb wird dort
kein erfundener RAM-Wert angegeben. Kalter Browserstart bedeutet frischen
Kontext/Worker und lokalen HTTP-Lader, keinen WAN-Download; OS-Dateicaches
wurden nicht geleert. Alle Zeiten sind Wandzeiten auf dem dokumentierten M1.
