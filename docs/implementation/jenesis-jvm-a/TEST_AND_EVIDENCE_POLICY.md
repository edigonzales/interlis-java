# Test- und Nachweispolitik — besonders nach Reviewkorrekturen

## 1. Grundregel

Teste die kleinste Menge, die die durch die Änderung entstandenen offenen Fragen tatsächlich beantwortet. Weniger testen ist kein Selbstzweck; alles wiederholen ist kein Qualitätsbeweis. Ein neuer Commit, ein Phasenwechsel oder eine neue Reviewrunde allein löst keinen Vollauf aus.

Für die erstmalige Buildmigration sind alle normalen JVM-Testfälle der zehn Module unter Jenesis einmal notwendig. Ein Buildsystemwechsel kann Quellenauswahl, Kompilierung, Ressourcen, Dependencyauflösung und Testdiscovery ändern. Diese erstmalige Abdeckung darf anschliessend **zusammengesetzt** aus weiterhin gültigen Originalbelegen und gezielten neuen Fixnachweisen bestehen. Sie muss nicht nach jedem Fix nochmals monolithisch erzeugt werden.

Wasm-/Web-Image-/Browsermatrix-/Vollkorpus-/Benchmark-/vollständige P3-Läufe sind keine pauschalen Pflichtprüfungen dieses Auftrags. Der begrenzte echte Editor-Verbraucher-/Extension-Host-Smoke aus `EDITOR_COMPATIBILITY.md` ist eine eigene notwendige Prüfung, nicht eine neu geforderte vollständige GUI-Abnahme. APE/Cosmopolitan und explizites JPMS werden nicht getestet, weil nicht implementiert. Werden sie aus einer konkreten Abweichung wirklich erforderlich, Anlass und erwartete Beobachtung dokumentieren. Kein Ausführen nur, weil ein Aggregationstask bequem ist.

## 2. Vor jedem Folgetest ein kurzer Impacteintrag

Nach jedem Reviewfinding oder nachträglichen Fix im `TEST_LEDGER.md` erfassen:

```text
Finding / Änderung:
Betroffener JA-Vertrag:
Geänderte Sources/Konfiguration/Inputs:
Mögliche abhängige Verbraucher:
Konkrete Gegenprobe:
Erneut notwendige Tests:
Wiederverwendete Nachweise + Gleichheitsbegründung:
Nicht erneut ausgeführte Bereiche + Begründung:
Ergebnis / verbleibende offene Frage:
```

Ein Satz pro Feld genügt, solange er konkret ist. Keine lange neue Spezifikation je Fix und keine schematische Checkbox „alle Tests“.

## 3. Risikobasierte Auswahlmatrix

| Änderung | Erneut ausführen | Regelmässig wiederverwendbar |
|---|---|---|
| Reine Erläuterung/Tippfehler ausserhalb ausführbarer Inputs | Links/Scope-/Dokumentprüfung | Kompilierung, Produkt- und Prozessfälle bei unveränderten relevanten Bytes |
| Dokument ist tatsächlicher gehashter Vertragsinput | betroffener Binding-/Hash-/Contract-Unitfall; nötiger Consumer | Unabhängige Produktsuite, wenn Produkt/Selektion/Beobachtung unverändert |
| OpenCode-Runner/Metadatenvalidierung | Runner-Negativtests/Preflight, echte neue Reviewrunde | Produktbuilds und gültige Produkttests |
| POM-Scope, BOM, Exclusion oder Versionpin | neue Closure, betroffene Compile-/Test-/Runtime-Verbraucher, Isolation | Tatsächlich unveränderte unabhängige Modulgruppen |
| Gemeinsame javac-Konfiguration oder JDK geändert | alle davon neu erzeugten Module, notwendige Tests/ABI-/Artefaktprüfung | Kein pauschales Weiterverwenden alter Compilenachweise; normale Produktupgrades sind in A ohnehin nicht beauftragt |
| Nur ANTLR-Konfiguration/Generatoranbindung | saubere und inkrementelle Parsererzeugung; Parser-/Compilerfälle; abhängiger SDK-Smoke | Fremde LSP-/Repositoryfälle nur bei tatsächlich unveränderten Klassen/Interfaces |
| Fixtureliste/Cachekey | gezielter betroffener Test, reale unverändert→Fixtureänderung-Gegenprobe | Nicht betroffene Module, breite Benchmarks |
| Tag-/Methodenfilter oder Testdiscovery | Discover-/Fallmengenvergleich für betroffene Module, fehlende/leere Auswahl, betroffene echte Fälle | Unveränderte andere Module; keine globale Vollsuite ohne globalen Discoveryeffekt |
| Native XML-/Reportadapter | synthetische positive/negative Reports plus ein realer kleiner Produzent→Adapter→Consumer-Lauf | Fachliche Produktergebnisse bei identischen geladenen Artefakten |
| Layoutpfad/Runmanifest | Path-/Symlink-/Fremdreportfälle und betroffene kleine Integration für Gradledefault und Jenesis | Compilerkern, vollständiger Korpus |
| CLI-/LSP-Launcher oder Runtimeclosure | echte Prozessfälle der betroffenen App, Unicode/Spaces/Exits bzw. Framing | Unveränderte Kernunitfälle |
| SDK-Archiv/-Rechte/-Inventar | Paketierung, entpackter Consumer, Hash-/Leak-/Rechtegegenprobe; bei Byteänderung passende Reproduzierbarkeit | Breite Semantiktests derselben JARs |
| Nur Editor-Schattenbundle-/Lock-/Probenadapter | gültiges Bundle, eigene Hash-/Inventarfehler, echter Launchplan und ein betroffener kleiner Consumerlauf | Compiler-Normaltests und gesamte GUI-/UML-Matrix bei identischen Server-/Frontendbytes |
| LSP-JAR-Inhalt/Runtimeclosure im neuen Build geändert | LSP-Unit-/Protokolltests nach Impact, echter schmaler Extension-Host-Smoke gegen neue JARs | Unabhängige Compilergruppen und unveränderte Runtime-Qualifikation |
| Bestehender Gradle-Editor-Staging-/Clean-Pfad berührt | Default-Staging/dev:prepare in isolierter Kopie, Quellen-/Lockschutz und Bundleinventar | APE-/Native-Prüfungen sowie unveränderte normale JVM-Fälle |
| Nur Testlock eines Schattenbundles aus identischen Artefakten neu erzeugt | Lock-/Provenienz-/Consumerbinding prüfen; bei neuem gehashtem Runtimevertrag passende Launchprobe | Bereits zu denselben Bytes vorliegende semantische und UI-Nachweise; kein erfundener frischer Lauf |
| Gemeinsamer Buildadapter/Engineschnittstelle | betroffene Adapterfälle, saubere betroffene Modulgruppe, Discovery-/Fremdartefaktprobe | Nur durch überprüften unveränderten Input-/Artefaktstand begründbar |
| Unveränderte Inhalte nur neu committet | erforderliche SHA-/Runbindungsprüfung; Review auf neuem SHA | Alte Laufzeitergebnisse mit Originalherkunft, kein fingierter neuer Lauf |

Bei Unsicherheit erweitere gezielt zur ersten betroffenen Verbrauchergrenze. Ein rein textueller Diff ist nicht immer hinreichend: geänderte Scopeauflösung, Generatoren, Ressourcen, Reflection und ServiceLoader können indirekte Effekte haben.

## 4. Nachweise unterscheiden

| Beobachtung | Belegt | Belegt nicht |
|---|---|---|
| Buildgraph/Dry-run | Auswahl und Reihenfolge | erfolgreiche Ausführung |
| Fixturevalidator | Annahme-/Ablehnungslogik | tatsächliche Produkt-/Plattformausführung |
| Engine-Cachehit | wiederverwendbares Ergebnis bei nachgewiesenen identischen Inputs | frisch gestartete Test-JVM |
| ausgeführter neuer JVM-Test | genau ausgewählte Fälle gegen protokollierte Artefakte | andere nicht ausgewählte Module |
| aktualisiertes Sollinventar | eine beabsichtigte Auswahl | erfolgte Ausführung |
| JAR-Inhaltsvergleich | Klassen/Ressourcenidentität nach Vergleichsregeln | allein die gesamte korrekte Anwendungsausführung |
| erfolgreicher `stage:core`-/F5-Vorbereitungslauf | bisherige Gradle-/Extensionkopplung funktioniert | Extension verwendet bereits Jenesis-JARs |
| Node-Launchplan plus direkter LSP-Prozess | echte Inventarprüfung und protokollkonformer Serverprozess | Aktivierung/Navigation im echten Extension-Host |
| kleiner echter Extension-Host-Smoke mit Jenesis-JARs | genau dokumentierte Editor-Verbraucherfälle auf diesem Host | ganze GUI-, Runtime-, Remote- oder Multiplattformfreigabe |
| vorhandene JRE-/jlink-Runtime unverändert wiederverwendet | Lauf mit diesem protokollierten Runtimeartefakt | neu ausgeführter jlink-/Grössenoptimierungs- oder Plattformvergleich |
| OpenCode-Exit 0 | regulärer Prozessabschluss | fachliches PASS |
| vorhandener alter PASS | Erfolg des damaligen Kandidaten/Umfangs | Erfolg eines neuen Kandidaten ohne erneute unabhängige Beurteilung |

## 5. Ausführungsbeleg

Jeder relevante Run erhält mindestens:

```json
{
  "schemaVersion": "1.0",
  "builder": "jenesis",
  "scope": "selected-jvm-tests",
  "execution": "EXECUTED",
  "candidate": {"commit": "<actual>", "tree": "<actual>", "dirty": true},
  "toolchain": {"jdk": "<actual>", "jenesis": "<actual-pinned-version>"},
  "selection": {"modules": ["<actual>"], "filter": "<actual-or-null>", "tags": "<actual>"},
  "inputFingerprint": "<actual-sha256>",
  "artifactInventory": "<path-to-hashed-inventory>",
  "command": ["<actual-executable>", "<actual-argument>"],
  "workingDirectory": "<actual>",
  "exitCode": 0,
  "signal": null,
  "rawReports": [{"path": "<actual>", "sha256": "<actual>"}],
  "testInventory": "<actual-normalized-inventory>",
  "limitations": []
}
```

Beispielstruktur, **kein auszufüllender PASS-Vordruck**. Tatsächliche Zeitangaben und Testsummen ergänzen. Bei `REUSED` Originalbeleg und überprüfte Gleichheitsbasis angeben, nicht `EXECUTED` stehen lassen. Nicht ausgeführte Profile ausdrücklich kennzeichnen. Nur echte Logs/Reports referenzieren; keine Platzhalter im finalen Nachweis.

Die Baseline muss ebenfalls ihre echte Toolchain, Auswahl und Artefakte enthalten. Zähler aus früheren Statusdateien ohne originale zuordenbare Ausführung sind keine genügende vollständige Baseline. Bereits vorhandene lokale Rohbelege können A0 Aufwand ersparen, sofern Scope und relevante Inputs wirklich passen.

### 5.1 Editor-Nachweise

Zusätzlich Core- und Extension-SHA/Tree/Dirtyzustand, Scope des Referenzbundlebaus, gehashte Renderer-/Serverinventare, neues Schatten-Testlock, tatsächlich geladene JARs, Extension-Hostversion, Runtimeherkunft (`bundled` oder ausdrücklich `developer override`), Proben und Originalreportpfade erfassen. Bei einem Development-Override keine mitgelieferte Runtime als getestet behaupten. Die bestehende Runtime-Paketierung wird nicht durch den Override ersetzt oder verändert.

Sämtliche Mutationen und neue Testlocks leben nur im Scratchbereich. Die versionierten Release-Locks und vorhandenen freigegebenen Pakete bleiben unverändert. Ein vorheriger PASS für ein anderes Core-/Extension-Paar gilt nicht automatisch für den neuen Kandidaten.

## 6. Native und projizierte Testreports

Unveränderte Native-Reports in runbezogenen Verzeichnissen sichern. Ihre Hashes bilden den Ursprung. Ein Adapter liefert zusätzlich die logischen Identitäten, nicht nachträglich eine andere Geschichte.

Jede Formatprojektion nennt Formatversion, Adapterversion/-quellhash, Eingangshashes, Mappingregeln und Ausführungsscope. Eine nachträglich geänderte Originaldatei macht die Projektion ungültig. Unerkannte Testnamen, mehrdeutige Parametermappings, fehlende Containersource oder doppelte Identitäten führen zum Fehler, nicht zu einer geratenen Zuordnung.

Ein erfolgreiches Fazit darf nicht aus einer Tabelle erwarteter Methoden erzeugt werden. Die Tabellen liefern die Sollmenge; der Listener/native Report liefert die Istmenge. Beides muss vollständig zusammenpassen. Skips/Aborts und Fehler bleiben im Adapter sichtbar und verhindern die Pflichtabnahme.

## 7. Wiederverwendung nach einem Fix

Wiederverwendbar ist ein konkreter Nachweis, wenn alle für seine Aussage relevanten Inputs gleich geblieben sind: Produktquellen/Bytecode, Toolchain, Dependencies, Ressourcen, Fixtures, Testauswahl, Runner-/Adapterkonfiguration, Plattform und gegebenenfalls Package/Launcher.

Der Beleg bleibt am ursprünglichen Commit. Ein zusätzlicher Verweis kann feststellen: „Quell-/Artefaktmenge X ist gegenüber Run Y unverändert; dessen Ergebnis wird für diesen Teil des neuen Kandidaten weiterverwendet.“ Hashes und `dirty` werden nie rückwirkend verändert.

Ein neuer Sourcefingerprint des Gesamtprojekts kann sich durch Doku oder Reviewcode ändern, obwohl ein Teilnachweis weiterhin passt. Dann ist eine explizite inhaltsbezogene Reusebewertung erforderlich. Ein strikter aktueller Releasevalidator darf deshalb nicht heimlich gelockert werden; falls er einen frischen passenden Teilreport braucht, wird dieser korrekt erzeugt. A ist keine vollständige P3-Neufreigabe.

## 8. Mindestgegenproben für die Implementierung

Die folgenden Risikoklassen sind mit kleinen Tests abzudecken; sie sind keine Aufforderung zu 24 breiten Suiten:

1. Gradle-Clean lässt Engine und eigene Buildquellen unverändert.
2. Jenesis-Discovery nimmt kein historisches POM und kein Wasm-Projekt auf.
3. Fremdes eigenes JAR aus Gradleausgabe/Local-Maven wird abgelehnt.
4. ANTLR-Tool ist nicht in CLI-/Compiler-Runtime.
5. Entfernte Grammatik/generierte Klasse hinterlässt keine nutzbare Altausgabe.
6. POM-/Toolpinabweichung scheitert vor einer vermeintlichen Paritätsfreigabe.
7. Package-private bestehender Test funktioniert unverändert.
8. Nur getaggte Contractmethoden werden getrennt; normale Negativtests derselben Klasse bleiben aktiv.
9. Eine leere erwartete Test-/Contractauswahl scheitert.
10. Ein verlorener Parameterfall scheitert am Ist-/Sollvergleich.
11. Datenänderung invalidiert betroffenen Test; unverändert kann gültig wiederverwendet werden.
12. Fremde Run-ID, falsches Artefakt, fehlender/alter Produzentenreport scheitern.
13. XML-DTD/externe Entitäten, widersprüchliche Summen und Fehlerknoten werden nicht akzeptiert.
14. Projektion kann Fehler/Skips/Duplikate nicht „wegnormalisieren“.
15. Ein fehlender Jenesisoutput führt nicht zu einem Gradle-Fallback.
16. CLI-Prozess mit Unicode-/Spacepfad und relevanten Exitcodes funktioniert.
17. LSP-Prozess hält Framing/stdout-Vertrag ein.
18. Produktpaket mit Verifikations-/Build-/Test-JAR-Leak scheitert.
19. Entpacktes SDK funktioniert ohne Zugriff auf Buildklassen des Checkouts.
20. Absichtliche unerlaubte JAR-/Paketdifferenz scheitert am Reproduzierbarkeitsgate auch bei Engine-Exit 0.
21. Bestehender Extension-Staging-/Vorbereitungspfad funktioniert nach den Core-Buildänderungen weiterhin und verändert keine geschützten Benutzer-/Releasequellen.
22. Schattenbundle enthält die vollständige gehashte Jenesis-LSP-Closure; fehlende/veränderte/zusätzliche JARs scheitern am realen Extensionvalidator.
23. Kleiner echter Extension-Host-Lauf verwendet nachweislich diese JARs und besteht Diagnose, Navigation und Stop/Restart; kein heimliches Gradle-Neustaging dazwischen.
24. Der normale JVM-Aufruf startet weder Editor-/Frontend-/Runtime-Paketierung noch APE-, jlink-, Native- oder Wasm-Aufgaben; der Compiler-APE-Folgepfad ist nur dokumentiert.

Gegenproben mit Mutationen ausschliesslich in separaten Scratchkopien oder kleinen Fixtures. Originalquellen und historische Normbelege nicht temporär beschädigen. Vor negativen Mutationen eine gültige positive Kontrolle prüfen, damit ein unabhängiger bereits bestehender Fehler keine Gegenprobe scheinbar bestätigt.

## 9. Rolle des unabhängigen Reviewers

Der zweite Agent prüft Zielerreichung und Code des Gesamtauftrags. Er muss die ursprünglichen breiten Laufbelege lesen können und ihre Zuordnung prüfen. Er muss eigene gezielte Gegenproben wählen und ausführen. Er soll **nicht** den vollständigen Gradle-/Jenesis-Doppellauf, alle Contracts, Korpusprüfungen und Benchmarks blind nochmals ausführen.

Nach einer Korrektur ist das **Urteil** neu und auf den neuen Kandidaten bezogen. Der **Testumfang** darf enger sein: betroffene Gegenproben erneut, unveränderte Belege und Reviewerkenntnisse nachvollziehbar prüfen. Ein frischer Reviewprozess erhält deshalb sowohl den gesamten Auftrag/kumulativen Diff als auch die bisherigen Findings, Auflösungen und Originalbelegindizes. Nicht nur der letzte Fixhunk wird beurteilt.
