# Enum-Vererbung, FORMAT/OID und EXISTENCE-Collectionwerte

Stand 20. Oktober 2026. Teilabschnitt von S2a, WP-20/22:
META-006/014/016, SEM-008/010/014, SRC-002–005 und LSP-012/013.
**P2/P3 und G2/G3 bleiben offen.**

## Fachliche Grundlage und Entscheidungen

Primärquellen sind die
[INTERLIS-2.3-Referenz](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und die [INTERLIS-2.4-Referenz](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf),
insbesondere §§2.8.2/3.8.2, §§2.8.6/3.8.6, §§2.8.9/3.8.9,
§§2.12/3.12 und für Collections §3.6.4.

- Enum-Erweiterungen ergänzen Bäume unter Erhalt geerbter Geschwisterreihenfolge.
  Gewöhnliche Enum-Werte sind Blätter. FINAL schliesst eine Geschwisterliste;
  ein Abschluss an der Wurzel verbietet nicht die Verfeinerung eines bestehenden
  Blattes. Geschriebene Punktnamen müssen geerbte Elemente bezeichnen.
  CIRCULAR-Erweiterungen sind unzulässig.
- FORMAT erhält gebundene Strukturen, Attribute, verschachtelte Formatdomänen
  und geerbte Präfixe. Grenzen müssen zur Struktur passen und die Basiskette
  einhalten. Ein fehlender Bereich an einer Zwischenstufe hebt ältere Grenzen
  nicht auf. DATE/TIMEOFDAY/DATETIME entsprechen in 2.4 den XML-Zeittypen.
- OID behält seinen Trägertyp. Konkrete OID-Domänen dürfen nicht erweitert
  werden. Die Norm erlaubt ANYOID ausdrücklich als Attributwertebereich; seine
  Werte tragen zusätzlich die konkrete OID-Domäne. `UUIDOID` ohne Qualifikation
  ist kein Sprachschlüsselwort für einen eingebauten Typ, sondern benötigt eine
  sichtbare Deklaration. Die vordefinierte Domäne heisst `INTERLIS.UUIDOID`.
- Für EXISTENCE wird der ganze Collection-Attributwert verglichen. LIST bleibt
  geordnet, BAG ungeordnet. Die Implementierung verlangt gleiche Collectionart
  und kompatible Elementwerte. Kardinalitätsintervalle müssen nicht vollständig
  ineinander enthalten sein; das wäre eine Zuweisungsforderung. Keine
  automatische Abflachung oder Fortsetzung durch jedes Strukturelement.
  Diese Collection-Regel ist eine begründete Auslegung der Wertsemantik,
  keine Behauptung eines ausdrücklich ausformulierten Normalgorithmus.
  Der Compiler prüft das Modell; er führt keine Transferdatenvalidierung aus.

## Umsetzung und öffentlicher Zugriff

Der bestehende Syntaxaufbau erfasst die Angaben einmal. `GraphBuilder` verwendet
konkrete private Helfer; keine neue Pass-Infrastruktur, Module oder Fassaden.

`EnumerationType` enthält den effektiven Baum samt geerbten Quellbereichen,
FINAL-Teilbäumen und `baseType`. Domänen- und Inline-EXTENDED-Enums verwenden
dieselbe Zusammenführung. Compiler und öffentliche Typqueries teilen die
Identitätsprüfung. Elternknoten, die durch Erweiterung ihre Blatteigenschaft
verlieren, werden nicht weiter als gewöhnliche Enum-Konstanten akzeptiert.

FORMAT-Teile enthalten gebundene Attribut-/Domänen-IDs, Stellenzahl, Literal
und Herkunft. Navigation und Rename verwenden die einzelnen Namensbereiche.
Die Prüfung von Formatwerten baut ausschliesslich auf diesen Teilen auf;
kein zweites Durchsuchen kompletter Modelltexte. OID-Trägertypen werden von
der Schema-Closure erhalten. Die fünf vordefinierten OID-Domänen haben
synthetische Herkunft; ihre Identitäten sind snapshotlokal und derzeit je
verwendendem Modell materialisiert. ANYOID behält seinen ABSTRACT-Modifikator.

Domain-EXTENDS übernimmt nun den vollständigen strukturierten Referenznamen,
auch `INTERLIS.ANYOID`. Eine fehlgeschlagene Bindung wird diagnostiziert; die
globale Suche nach einer zufällig gleichnamigen Domäne entfällt. Alte positive
Fixtures mit undeklariertem `UUIDOID` verwenden jetzt die qualifizierte Domäne;
die ungültige Variante hat negative Regressionen. Externe Baselines bleiben
unverändert.

EXISTENCE-Endwerte bleiben als `CollectionType` mit Elementtyp und Kardinalität
im gebundenen Pfad erhalten. Es entstehen keine erfundenen Elementpfade.
Primitive Collections werden für 2.4 zugelassen; 2.3 verlangt Strukturelemente.
UNIQUE erhält dadurch keine Erlaubnis für ganze Struktur-/Collection-Schlüssel.

## Referenzvergleich und Nachweise

Die [107 Prozessvergleiche je Compiler](type-values-reference-comparison-2026-10-20.json)
halten Originaleingaben, Inhaltshashes, Kommandos, Exitcodes und Ausgaben je
Compiler fest. Referenz bleibt ili2c
5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722.
79 Ausgänge stimmen überein; 28 Abweichungen sind einzeln eingeordnet.
Alle 107 aktuellen Ausgänge entsprechen den begründeten Vertragserwartungen.

Beobachtete Abweichungen werden nicht durch Anpassung der externen Baseline
versteckt:

- ili2c akzeptiert untersuchte CIRCULAR- und konkrete OID-Erweiterungen,
  umgekehrte FORMAT-Grenzen, Stellenzahl null und einen ersetzten geerbten
  Formatpräfix. Hier greifen die beschriebenen Normregeln.
- ili2c lehnt ANYOID im konkreten Attribut und dessen EXISTENCE-Vergleich mit
  konkretem OID ab. Hier gilt die ausdrückliche Zulassung im Referenzhandbuch;
  unterschiedliche Trägerarten bleiben unterscheidbar.
- ili2c akzeptiert untersuchte EXISTENCE-Vergleiche von BAG/LIST, beliebigen
  Strukturklassen und Collection/Einzelstruktur. Hier gelten Collectionart,
  Elementidentität beziehungsweise höchstens einwertige Strukturkonversion.
- Nicht verwandte Formate und Enum-Bäume bleiben teilweise explizite
  Implementierungslücken (`EXISTENCE_TYPE_COMPATIBILITY`), keine pauschale
  Behauptung ungültiger Modelle. Direkt verschachtelte Formate mit eigenen
  Grenzen sind noch mit `FORMAT_NESTED_BOUNDS` gesperrt.
- ili2c lehnt im untersuchten dreistufigen FORMAT-Beispiel die ergänzte
  lexikalische Form ab. Hier bleiben geerbter Präfix und Zusatzattribut
  erhalten; die neue Grenze wird gegen alle Vorfahren geprüft. Der
  entsprechende Fall mit Ausweitung des geerbten Bereichs wird abgewiesen.

`ExistenceValueFamiliesContractTest` prüft neben Annahme/Ablehnung die effektiven
Typen, Identitäten, Herkunft, Reihenfolge, öffentliche Zuweisungsabfrage sowie
Navigation/Rename. Der gemeinsame portable Vertrag prüft die Ausgänge derselben
Featurefamilien in allen ausgeführten Laufzeiten. Das
[Verifikationsmanifest](verification-2026-10-20-type-values.json) enthält die
tatsächlichen Resultate, Artefakthashes und Umgebungen. Die ältere
[EXISTENCE-Evidenz](existence-types-and-indices.md) bleibt historisch erhalten.

Abschliessend ausgeführt:

- **238 JVM-Tests PASS**, keine Fehler oder übersprungenen Fälle.
- **501 gemeinsame portable Fälle PASS**: TeaVM-JavaScript/WasmGC unter
  Node 24.22.0; Worker in Chromium, Firefox, WebKit und echtem Safari 27.0;
  realer Web-Image-Build mit Node und den drei Playwright-Browsern.
  Alle beteiligten Prozesse enden regulär.
- SDK-Beispiel und neun reale CLI-/SDK-Prozessfälle auf macOS arm64 PASS.
- Konformität unverändert: **547 PASS, 1 XFAIL, 45 FAIL**, keine ERROR/XPASS,
  dieselben offenen Fall-IDs und keine falsche Annahme in der gepinnten Suite.
  Der kombinierte Gradle-Lauf endet nach 2 min 35 s mit Exitcode 1 ausschliesslich
  wegen `conformanceCheck`. Das ist kein grüner Gesamtbuild oder Releasegate.

Kommandos, Toolchains, Quellbaumdigest und Artefakthashes stehen im Manifest;
Rohberichte liegen unter den dort genannten Buildpfaden. Nicht ausgeführt:
vollständige Performance-/Retention-/Abbruchabnahme, Linux-/Windows-Prozesse
und vollständiges `releaseCheck`.

## Noch offene Pflichtarbeit

- Vollständige Enum-Übersetzungen, ALL OF/OTHERS und sämtliche Funktions- und
  Vergleichskombinationen; nicht verwandte Enum-Bäume sind nicht vollständig
  abgenommen.
- FORMAT: vollständige numerische Präzisions-/Skalierungs- und Auffüllregeln,
  verschachtelte Grenzen einschliesslich geerbter Grenzen, alle EXTENDED-
  Attributkombinationen, Formatdomänenübersetzung und öffentliche Typrelationen.
  Die bewahrte Stellenzahl allein belegt noch keine vollständige lexikalische
  Prüfung. Vordefinierte Zeitformate besitzen eine explizite Art, aber noch
  keinen vollständig materialisierten internen Strukturgraphen.
- OID: vollständige Bindung der bisherigen `OID AS`-Headerangaben, Regeln für
  NOOID/ANYOID an Klassen und Topics sowie allgemeine OID-Zuweisungsrelationen.
  Die vorhandenen Header bleiben Strings; sie sind kein neuer typisierter
  OID-Regelvertrag. XML-ID-/UUID-Transferprüfung gehört zur späteren
  Datenvalidierung, nicht zur hier geprüften Modellkompilierung.
- Collection-Vergleich über alle weiteren Elementfamilien, Übersetzungen,
  allgemeine Strukturmengen-/Inspektionspfade und sämtliche View-Kombinationen.
  Die geprüften ganzen Endwerte ersetzen diese Pfadarbeit nicht.
- Unverändert: vollständige P0–P3-Normmatrix, 45 offene Suitefälle, S2c,
  CLI-Kompatibilitätsprofil, Resolver/Offline, LSP-Nebenläufigkeit und
  geschlossene Projektdateien, vollständiger geo.so.ch-Korpus, M1-Performance/
  Retention/Abbruch und Linux-/Windows-Distribution. `releaseCheck` ist nicht
  vollständig abgenommen.

Nächster fachlicher Abschnitt: FORMAT-Grenzen und Präzision vervollständigen,
OID-Header an gebundene Domänen anschliessen; danach die restlichen Enum-/
Collection-Relationen und allgemeinen Mengenpfade. Keine Testanzahl schliesst P3.
