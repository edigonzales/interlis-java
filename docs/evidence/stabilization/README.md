# Stabilisierung: Nachweise und verbleibende Abnahmen

Stand 21. Oktober 2026. Dieser Zwischenstand ersetzt keine vollständige P2-/P3-Abnahme.
Massgeblich sind der [aktuelle Status](../../implementation-status.md), die
Spezifikation und der freigegebene Stabilisierungsplan. Vorherige Berichte
werden nicht überschrieben. Rohberichte entstehen unter `build/`.

## Ausführbare Regressionen

| Vertrag / IDs | Test und beobachteter Inhalt |
|---|---|
| SEM-002–005/014, META-014 | `CompiledSchemaSelectionTest`: ein fehlerhaftes unabhängiges Modell in derselben Datei blockiert die gültige Auswahl nicht; Auswahl des fehlerhaften Modells bleibt abgelehnt. |
| SEM-010/018, META-010 | `ViewContractTest`, `ViewFormationSemanticsContractTest`, `CompilerEngineTest`: noch unvollständige View-Modelle bleiben inspizierbar, erzeugen aber kein gültiges Schema. Die früheren positiven Erwartungen waren kein Nachweis nutzbarer View-Inhalte. |
| INC-002/012/021 | `WorkspaceTest.failedBatchPreservesDeletedSourceRevision`: fehlgeschlagener Batch verändert weder Quellen noch Tombstone-Revisionen; Wiederanlegen setzt die Revision fort. |
| SRC-002–005, LSP-012/013 | `RenameSafetyTest`: gleiche Offsets in unterschiedlichen Dateien; lokale END-Namen und Kommentare; echte Aufrufposition von prepareRename; separate Ziele qualifizierter Namenssegmente; Schlüsselwörter, Namenslänge und Kollisionen. |
| FMT-001–004 | `FormatterContractTest`: Idempotenz, genaue Token-/Kommentarreihenfolge, mehrzeilige Kommentare, Stringinhalt, Unicode, LF/CRLF/CR und beide Sprachversionen. Kein zweites Ausgeben mehrzeiliger Kommentare. |
| CLI-007 | `CliApplicationTest.brokenOutputNeverReportsSuccess`: Schreib-/Flushfehler führen zu `OPERATIONAL_FAILURE`. Der nichtatomare Fallback beim Dateiaustausch ist entfernt; die vollständige Dateifehlermatrix bleibt offen. |
| WP-22; META-/SEM-Funktionsverträge | `FunctionContractTest`: gebundene Parametertypen/Ergebnistypen, Arity, Argumentverträglichkeit, Typen von Textliteralen, qualifizierte Aufrufe, CONTRACTED nach Sprachversion und explizite Builtin-Signaturen. |
| WP-22; META-003/006/014/016, SEM-008/010 | `ObjectFunctionContractTest`: OBJECT OF mit gebundenen Klassen und Einschränkungen, Unterklassen, Objektergebnissen, Identitätsvergleichen und öffentlichen Typqueries; keine Skalare/Mengen als Einzelobjekte; Signaturreferenzen, Imports und lokale Scopes; THIS nach Objekt- und Skalarpfaden; Rollen im Kontext einer Assoziationsinstanz. |
| WP-21/22; META-003/006/008/014, SEM-008/010/014 | `AssociationAccessContractTest`: binäre Klassen-/Assoziationszugänge, beide Rollenidentitäten, Kardinalität und Zwischen-/Endtyp; Singleton/Menge, `ALL` samt Auswahl, Einschränkungen und Funktionsresultaten; Typqueries; EXTERNAL, Mehrdeutigkeit, Topic-/Klassenscopes; geerbte Topic-Ziele, ANYCLASS und Assoziation als Rollenziel; vollständige Signaturen und Zwischentypen im ausgewählten Schema. N-stellige Zugänge werden zusätzlich im folgenden Pfadvertrag geprüft. |
| WP-21/22; META-003/006/008/014, SEM-008/010/014 | `RoleTargetInheritanceContractTest`: OR-/RESTRICTION-Ziele und Navigationsreferenzen; gemeinsame Pfadattribute und Typqueries für alle Zielalternativen; effektive Rollenreihenfolge, Herkunft, Kardinalitäten und Assoziationsbasis; HIDING gegenüber allgemeinen Zugängen und widersprechenden Geschwistern; FINAL, EXTERNAL, unerlaubte Erweiterungen, Zyklen und EXTENDED-Topic-Kontext. `DiagramProjectionTest` prüft alle Zielkanten und geerbten Rollen. |
| WP-21/22; META-003/006/008/014, SEM-008/010/014 | `QualifiedAssociationPathContractTest`: n-stellige und qualifizierte Ausgangsrollenzugänge; Rollen-/Assoziationsreferenzen und exakte Renamebereiche; Mengenfortsetzung über Rollen und Referenzattribute; alle Zielalternativen; skalare Mehrfachwerte, HIDING/EXTERNAL und Mehrdeutigkeit. |
| WP-22; META-006/014/016, SEM-008/010/014, SRC-002–005, LSP-012/013 | `ConstraintPathBindingContractTest`: vollständige UNIQUE-Schlüssel, WHERE, BASKET, LOCAL-Präfix und Feldscopes; alle EXISTENCE-Alternativen, Typen, Abhängigkeiten und vollständige Qualifikation; Schema-Closure, Navigation/Rename; Strukturen, Geometrie, weitere EXISTENCE-Typfamilien, Bereichsvergleich getrennt von Zuweisung sowie LIST-/Achsenindizes und explizite Lücken. |
| WP-20/22; META-006/014/016, SEM-008/010/014, SRC-002–005, LSP-012/013 | `ExistenceValueFamiliesContractTest`: effektive Enum-Vererbung und FINAL-Teilbäume, öffentliche Typqueries, gebundene FORMAT-Bestandteile/Vererbungsgrenzen samt Rename, OID-Trägertypen und Basisbindung sowie ganze EXISTENCE-Collectionwerte. Vollständiger Umfang und Grenzen im Folgenachweis. |
| WP-20/22; META-006/014/016, SEM-008/010/014, SRC-002–005, LSP-012/013 | `FormatPrecisionOidBindingContractTest`: exakte Präzision/Skalierung/Auffüllung, verschachtelte FORMAT-Grenzen und Konstanten; getrennte gebundene OID-Regeln, Herkunft, Scope, Navigation/Rename, Closure, Vererbung und Übersetzung. Mehrfachlinks, implizites NO OID und Strukturbasen sind geprüft. |
| WP-22; Fehlerzustände | `ExpressionConstraintContractTest.externalConstraintBindsItsClassAndMissingAttributeIsAUserError`: externe Constraints binden im Zielscope; unaufgelöster EXISTENCE-Quellpfad bleibt ein Sprachfehler mit `UnresolvedStep`, kein interner Fehler. |
| WP-22; META-006/014, SEM-008/010 | `ExpressionConstraintContractTest`: skalare Werte beenden einen Pfad; Objektverweise behalten ihren Typ; Backslash/Index/Inspektionsoperatoren verschwinden nicht als normale Namen. Externe Constraints benötigen ein vorhandenes Ziel im eigenen Topic oder dessen Vererbungsweg. |
| WP-22; SYN-/META-/SEM-Constraints | `ExpressionConstraintContractTest.domainConstraintsAreBoundToTheirOwnTypeAndRemainInDeclarationOrder`: benannte Domainbedingungen bleiben erhalten, `THIS` trägt den Domain-Typ; nichtboolesche Ausdrücke, doppelte Namen und 2.3-Syntax werden abgelehnt. Geerbte Bedingungen sind noch nicht vollständig abgenommen. |
| INC-006–009/013–019/021 | `WorkspaceTest.syntaxIsSharedWithResolverAndBoundedAfterAcceptance`: identisches Syntaxobjekt für Resolver/Engine, echte Parse-/Reusezähler, Cachegrenzen mit Eviction und getrennte Zählung externer Annahme/Engineaufrufe. Bestehende Fresh-vs-Reuse- und Stale-Tests laufen mit. |
| RES-001–012, SEM-011/014 | `HttpSourceLoaderTest`: Grössenlimit bei laufendem Chunked-Body, Abbruch während wartender Bodybytes sowie bestehende Timeout-/Redirect-/Offlinefälle. Keine Aussage zu integrierter Offline-Closure. |
| LSP-001–016 (Teilumfang) | `LspFeatureTest`: alte Dokumentversionen werden ignoriert, externe Löschung entfernt die Quelle, Schliessen verwirft ungespeicherte Texte zugunsten des Diskstands. Bestehende stdio-/Cancellationtests laufen mit. |
| WP-20/22; META-006/014/016, SEM-008/010/014 | `OidFollowupAbstractFormatContractTest`: OID-Versionen, feste Topic-Zuordnung, Domain-Erweiterung und Zyklusfehler; abstraktes FORMAT, effektive Attributziele, Herkunft, Präzision und verschachtelte Abstraktion. |
| WP-20/21/22; META-006/008/014/016, SEM-008/010, SRC-002–005, LSP-012/013 | `NoOidReferenceContractTest`: versionsabhängige NO-OID-Zielregeln, effektive Identität, Referenzrestriktionen und Typabfragen, Quellnavigation, EXTERNAL, Collection-Elemente, EXTENDED und gemischte Quelldateien. Noch keine vollständige Referenz-/Topic-Abnahme. |
| WP-20/21/22; META-006/008/014/016, SEM-008/010, SRC-002–005, LSP-012/013 | `TopicDependencyContractTest`: direkte/geerbte Abhängigkeiten, gesamte Zielmengen, Verwendungskontexte, MANDATORY-Typen, Zyklen, Namenskollisionen und öffentliche Schema-/Navigationsabfragen. |
| WP-20/21/22; META-006/008/014/016, SEM-008/010/014, SRC-002–005 | `MandatoryOverrideTypeContractTest`: frühe Typbindung bei MANDATORY, unmittelbare Überschreibungsidentitäten, Referenz-/Skalar-/Indexpfade, Funktionen, UNIQUE/EXISTENCE, EXTENDED und importierte Quellen; fehlende Basen, Zyklen und explizite Typen. `DiagramProjectionTest` prüft zusätzlich die erhaltene Strukturbasiskante. |
| WP-20/21/22; META-006/008/014/016, SEM-008/010/014, SRC-002–005 | `ImportedDomainTypeContractTest`: beide Dateisortierungen importierter Domains/Strukturen, Funktionssignaturen, MANDATORY, Enum-/FORMAT-Basen, Domainbedingungen, Typidentität, Navigation, Zyklen und Fresh-vs-Reuse. |
| WP-20; META-001/003/006/007/008/014/015, SEM-008/010/014, SRC-002–005 | `ComposedUnitContractTest`: gebundene Komponenten, Kurzzeichen, Reihenfolge, Numerik, Abstraktions-/Erweiterungsfehler, Imports, Builtins, Zyklen, Navigation und Fresh-vs-Reuse. |
| WASM-/TEST-Portabilität | `stabilization-contract.cjs`: 1066 gemeinsame Funktions-, Domain-, Pfad- und Scopefälle auf TeaVM-JavaScript, WasmGC und Web Image sowie im Browser. Ein erfolgreicher Testinhalt ohne reguläres Prozessende ist kein PASS des Runtime-Gates. Tatsächlich bestandene Laufzeitprofile stehen im Verifikationsmanifest. |

Der aktuelle Lauf umfasst **309 JVM-Tests und 1066 gemeinsame portable Semantikfälle**.
Konformität bleibt unverändert rot: **538 PASS, 1 XFAIL, 54 FAIL, 0 ERROR/XPASS**.
[Zusammengesetzte Einheiten](composed-units.md) und
[118 Prozessvergleiche je Compiler](units-reference-comparison-2026-10-21.json)
dokumentieren zehn zusätzliche JVM-Tests und 118 portable Fälle.
Alle 118 Prozessentscheidungen stimmen überein; keine unerwartete Entscheidung
unseres Compilers in diesem Umfang.
Die vier ursprünglichen Abstraktionsfehler sind geschlossen; beide Compiler
lehnen die [identischen Quellen](units-follow-on-probes-2026-10-21.json) jetzt ab.
[Ältere Nachweise](imported-domain-types.md) bleiben historisch erhalten;
Pins, Baseline und XFAILs bleiben unverändert.

Nächste fachliche Arbeit: exakte numerische Umrechnungsregeln und die
Einheitenverträglichkeit numerischer Domain-Erweiterungen. Die Folgeproben
enthalten sechs falsche Annahmen für Faktor null, Division durch null und
den Wechsel von Meter zu Sekunde, jeweils in 2.3 und 2.4. Diese Fälle bleiben
ausserhalb der eingefrorenen Suite ausdrücklich offen. Der
[Fachnachweis](composed-units.md) trennt gebundene Kompositionen von noch
unvollständiger Umrechnungssemantik. Eine grüne Testanzahl ist keine P3-Abnahme.

Diese Tabelle ordnet die geänderten Fälle zu. Die vollständige P0–P3-Normmatrix
ist weiterhin ein offener S0-/WP-27-Punkt.

## Normentscheidungen

- **CONTRACTED:** 2.3 fordert den Modifikator für Funktionsdeklarationen und OID AS; 2.4
  hat seine Bedeutung aufgehoben. Keine pauschale Pflicht und keine neue
  Warnung in 2.4.
- **Funktionsargumente:** Numerisch → Boolean wird abgelehnt. Der beobachtete
  ili2c-5.6.9-Erfolg eines solchen Aufrufs ist keine Grundlage zur Lockerung
  der Sprachregel. Arity und Ergebnis müssen unabhängig davon korrekt sein.
- **Standardfunktionen:** Namen sind case-sensitiv. `len`/`lenM` liefern einen
  numerischen Ergebnistyp, `trim`/`trimM` den jeweiligen Texttyp. Es gibt keine
  Ableitung aus Namensbestandteilen wie `contains("text")`. Komplexe noch nicht
  geprüfte Signaturen, insbesondere Objekt-/Enum-/Einheiten-/Geometrieverträge,
  bleiben ausdrücklich `BUILTIN_SIGNATURE`-Lücken. Der Widerspruch zwischen
  Fliesstext und Anhang zu `areAreas2` in der 2.4-Referenz ist vor dessen
  Freigabe zu klären; die aktuelle Katalogeintragung folgt Anhang A und bleibt gesperrt.
- **Unvollständige Views/Deklarationen:** Das vorsorgliche Schliessen des Gates
  ist eine Zwischenmassnahme S0. Diese Features bleiben verpflichtender P2-Umfang.
  Kein XFAIL und keine Referenzerwartung wurde dafür gelockert.
- **Einzelne Funktionsobjekte:** `OBJECT OF` bewahrt die Basisklasse und geordnete
  Einschränkungen als Identitäten. Unterklassen sind zulässige Argumente;
  Skalare und Objektmengen sind keine Einzelobjekte. Auch Objektergebnisse
  bleiben als Argument und bei Identitätsvergleichen nutzbar; Ordnungsvergleiche
  sind ausgeschlossen (2.3 §§2.13/2.14). Ein direkter Rollenschritt auf einer
  Assoziationsinstanz wählt einen Beteiligten; Beziehungskardinalität und
  Anzahl Beteiligter dieses Schritts dürfen nicht verwechselt werden (§2.7.1).
  Binäre Klassenzugänge und `\rolle` sind jetzt gebunden. Ein mehrwertiger Zugang
  bleibt eine Menge; er wird nicht als einzelnes Objekt ausgegeben. Die eigene
  Assoziationskardinalität multipliziert bei `\rolle` die Zahl der Linkinstanzen;
  mehrfache Links je Teilnehmerkombination verlangen OID (§2.7.1).
- **Rollen-Zielmengen und Vererbung:** OR-Alternativen bleiben getrennte
  Basis-/Restriktionswerte; eine Alternative darf keine Spezialisierung einer
  anderen sein. Restriktionen müssen zur Basis gehören. Vererbung übernimmt
  ausgelassene Rollen und Kardinalitäten, darf aber weder Ziele noch Grenzen
  erweitern, FINAL überschreiben oder EXTERNAL ändern. HIDING an der
  Ausgangsrolle beschränkt für die spezialisierte Teilnehmerklasse den Zugang
  auf die spezialisierte Beziehung; ohne HIDING bleibt der allgemeine Zugang
  erhalten. Widersprechende Geschwister werden abgewiesen (2.3 §§2.7.1/2.7.5,
  2.4 §§3.7.1/3.7.5). Die früheren eigenen Testfixtures mit alleinstehendem
  `CLASS ... (EXTENDED)` beziehungsweise nur einer Assoziationsrolle waren
  ungültig. Ihre positiven Testzwecke verwenden jetzt gültige Modelle; die
  ungültigen Varianten haben ausdrückliche Negativtests. Externe Suitepins,
  Baseline und XFAILs bleiben unverändert.
- **Objektmengen:** `OBJECTS OF` und `ALL` teilen die gebundenen Basis-/Restriktionsregeln
  mit `OBJECT OF`. `ALL` ohne Auswahl bezieht sich auf die Kontextklasse.
  `objectCount` verwendet denselben Zuweisungsvertrag wie Benutzerfunktionen.
  Ein statisch einzelnes Objekt ist als Singleton zulässig, eine mehrwertige
  Menge nicht an einem Einzelargument. Einschränkungen sind statische
  Typbedingungen, keine Laufzeitfilter (2.3 §§2.13/2.14, 2.4 §§3.13/3.14).
- **Domainbedingungen und Pfade:** 2.4 §3.8 verlangt benannte logische
  Domainbedingungen; die 2.3-Regel §2.8 enthält diese Erweiterung nicht.
  `THIS` übernimmt den Kontexttyp. Ein Objekt ist kein Boolean; ein skalarer
  Wert eröffnet keinen weiteren Attributscope. N-stellige und qualifizierte
  Ausgangsrollenzugänge sowie mehrwertige Rollen-/Referenzattributfortsetzungen
  sind im [neuen Abschnitt](qualified-paths.md) implementiert und geprüft.
  UNIQUE/LOCAL/EXISTENCE verwenden inzwischen denselben Binder; siehe
  [Constraint-Pfadabschnitt](constraint-paths.md). LIST-/Achsenindizes und weitere
  Typfamilien sind im [Folgeabschnitt](existence-types-and-indices.md) geprüft.
  Ganze Collection-Endwerte sind jetzt im [Folgeabschnitt](enum-format-oid-collections.md)
  geprüft. Allgemeine Strukturmengen, Inspektionen und übrige Typfamilien bleiben offen.

Primärquellen:
[Referenz 2.3, Kapitel 2.13/2.14](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
[Referenz 2.4, Kapitel 3.13/3.14 und Anhang A](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
Der ili2c-Pin bleibt 5.6.9, ZIP-SHA-256
`0d6fc5dc739a490ef6b027072d58281bb0731c932f03d95662f83d20dc07c8ee`.

## Reproduktion und Aussagegrenzen

JVM: Java 25.0.3 (Temurin), Repositorywrapper Gradle 9.8.1. Node-Verträge
verwenden explizit `-PnodeExecutable=/absoluter/pfad/zu/node`; in diesem Versuch
Node 24.22.0. Der blosse PATH eines gestarteten Gradle-Daemons wird nicht als
Nachweis einer Node-Version verwendet. Der abschliessende Lauf verwendet den
Repositorypin Playwright 1.64.1 nach `npm ci --ignore-scripts` und die damit
installierten Browser. Frühere Diagnoseversuche mit der gebündelten Version
1.62.1 ersetzen diesen Pin nicht. Safari wird getrennt mit dem
macOS-safaridriver geprüft.
Die exakten aktuellen Resultate und Artefakthashes stehen in
[verification-2026-10-21-units.json](verification-2026-10-21-units.json).

```sh
./gradlew check
./gradlew :interlis-wasm-teavm:jsContractTest \
  :interlis-wasm-teavm:wasmContractTest -PnodeExecutable=/absoluter/pfad/zu/node
./gradlew :interlis-wasm-teavm:browserContractTest \
  :interlis-wasm-teavm:safariContractTest -PnodeExecutable=/absoluter/pfad/zu/node
./gradlew conformanceCheck sdkProcessCheck
```

Playwright wird in `interlis-wasm-teavm` mittels `npm ci --ignore-scripts`
installiert; `node node_modules/playwright/cli.js install chromium firefox webkit`
beschafft die zugehörigen Browser. Safari benötigt aktivierte Remote-Automation; der Test beendet keine
fremden Safari-Prozesse. Ein Treiberstartfehler bleibt ein fehlender Nachweis.
Die unterstützte Produktionsstrecke bleibt TeaVM-WasmGC.

Die [vorherige Objekt-Verifikation](verification-2026-10-20-objects.json)
bleibt ebenfalls als historischer Stand erhalten.
Die frühere [Verifikation mit Node 22](verification-2026-10-20.json) bleibt
historische Evidenz. Der [Abschlussnachweis](wasmgc-process-exit.md) lokalisiert
den Stillstand in V8-Hintergrundkompilierung und dokumentiert den geprüften
Wechsel des Hostpins auf Node 24.22.0. Normale Optimierung und regulärer
Prozessabschluss bleiben Pflicht; `--liftoff-only` ist nur ein Diagnosevergleich.

Performance-, Retention-, vollständige CLI-Kompatibilitäts- und dreifache
Plattformabnahme sind weiterhin offen. Die bisherigen Seed-Benchmarks und neun
CLI-Prozessfälle dürfen nicht als deren vollständiger Ersatz ausgewiesen werden.
