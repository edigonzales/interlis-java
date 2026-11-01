# Standardmässiger Repositorycache – Umsetzung und Nachweis

Auftrag vom 01.11.2026. Ausgangscommit
`fd07e194f14c8ee46debe930871a6c18fb72242d`, zu Beginn sauberer Arbeitsbaum.
WP-26, RES-001/004/007–011 und neue Anforderung RES-013; CLI-Konfiguration
und Beobachtung gemäss CLI-003/004/006 sowie LOG-002/003/005.

## Implementiert

- Der Prozess-CLI verwendet standardmässig `user.home/.interlis-cache`.
  Der Homepfad ist injizierbar; bestehende eingebettete CLI-Konstruktoren bleiben
  erhalten und verwenden ihr explizites Arbeitsverzeichnis als isoliertes Home.
  Vollständige lokale Closures und reine Konfigurationsausgaben erzeugen keinen Cache.
- Bestehende SHA-256-Inhaltsdateien bleiben unverändert adressierbar. Ein auf
  64 KiB begrenzter, strikt versionierter XML-Herkunftsindex unter metadata-v1
  ordnet vollständige Quell-URLs den Inhaltsdigests und HTTP-Validatoren zu.
  Inhalte werden vor den atomaren Metadaten gespeichert. Fehler und Korruption
  sind Misses; parallele Instanzen veröffentlichen keine Teilmetadaten.
- HTTP revalidiert bei jeder Beschaffung, einschliesslich Abschlussvergleich:
  ETag hat Vorrang vor Last-Modified. 304 verwendet digestgeprüfte Bytes;
  ein fehlender/beschädigter Body löst einmal unbedingtes Nachladen aus.
  Ohne Validatoren wird vollständig geladen. Redirectvalidatoren gelten nur
  für ihr zugehöriges Ziel. no-store und nicht unterstützte Vary-Profile werden
  auch durch den nachgelagerten Closurehost nicht persistiert.
- Offline ohne Lock verwendet ausschliesslich den letzten lokal gespeicherten
  Stand der konfigurierten Repositorysuche. Optionale fehlende Suchmetadaten
  bleiben erklärbare Ausfälle; ein erforderlicher fehlender Inhalt ergibt
  OFFLINE mit Quelle. Pins/Locks wählen weiterhin den exakten Digest, auch
  nachdem die URL auf neueren Inhalt zeigt. Kein stiller Online-Netzfehlerfallback.
- `SourceLoader.loadCached` ist ein expliziter Offlineport. Die Defaultmethode
  ruft bestehende funktionale Provider nicht auf. Cachefähige HTTP-/Routinghosts
  implementieren ihn; Dateien und bereits geladene Workspacequellen behalten
  ihre bisherigen Regeln. Compilerkern, LSP-Remoteumfang und portable Wireformate
  bleiben unverändert.
- `--cache DIR`, `--no-cache`, Properties-Präzedenz und `--explain-config` sind
  verdrahtet. Ungültige Pfade und widersprüchliche Einstellungen sind Nutzungsfehler.
  Eine nicht beschreibbare Ablage meldet einmal pro Hostzugriff eine strukturierte
  operative Cachewarnung auf stderr, verhindert aber keinen erfolgreichen
  Online-Download. Diese operative Warnung ändert keine Compiler-Gateentscheidung.
  Cacheereignisse benutzen den vorhandenen RunContext und die Lifecycle-Sequenz;
  sie enthalten keine URLs, Zugangsdaten oder Validatoren.

Die API-/Fachverträge stehen in [Kapitel 08](../../spec/08-modellaufloesung-und-repositories.md#13-standardcache-und-http-revalidierung-res-013),
[Kapitel 16](../../spec/16-api-register-und-vertragstests.md) und
[Kapitel 18](../../spec/18-cli-sdk-und-kompatibilitaetsadapter.md#standardcache-hostumgebung-und-beispiele).
Abnahmen, Arbeitsauftrag, Entscheidung und Anforderungsindex sind in Kapitel
13/14/15/17 synchronisiert; README enthält Bedienung und Beispiele.

## Tatsächlich geprüft

macOS / arm64. Wrapper Gradle 9.8.1, Java-25-Toolchain ohne Preview.
Die separaten CLI-Prozesse liefen ausdrücklich mit Temurin 25.0.3+9-LTS.
Die installierte Distribution wurde aus dem Arbeitsstand neu gebaut.

Aufbewahrt sind **101 unterschiedliche JUnit-Testfälle in 19 Suites**, keine
Fehler, Errors oder Skips: Repositorymodul 63/12, CLI 36/6 und Lifecycle 2/1.
Der Repositorylauf enthält die kleine vollständige Modulsuite einschliesslich
gefrorener lokaler Referenzfälle; dies ist kein vollständiger externer Modellkorpus.
Die CLI-Auswahl umfasst Cache, Konfiguration, Repositorydiagnosen, semantische
Repositorybefehle und Lifecycle. Die zweite Gradle-Kommandozeile bindet die
--tests-Optionen an die CLI-Aufgabe; das Repositorymodul wird vollständig geprüft.

```sh
# Ursprüngliche Lifecycle-Gegenprobe (Exit 0):
./gradlew :interlis-model:test --tests '*RunLifecycleCacheTest' --console=plain

# Zusammenhängender Vertragslauf und Distribution (Exit 0):
./gradlew :interlis-repository:test :interlis-compiler-cli:test \
  --tests '*RepositoryCacheContractTest' --tests '*DefaultRepositoryCacheTest' \
  --tests '*RepositoryClosureContractTest' --tests '*RepositoryCliContractTest' \
  --tests '*P3ConfigurationContractTest' --tests '*HttpSourceLoaderTest' \
  --tests '*ResolutionLockContractTest' --tests '*RepositorySearchContractTest' \
  --tests '*RepositoryParityIntegrationTest' --tests '*RepositoryDiagnosticsContractTest' \
  --tests '*SemanticRepositoryCommandsTest' --tests '*P3CliLifecycleContractTest' \
  :interlis-model:test --tests '*RunLifecycleCacheTest' \
  :interlis-compiler-cli:installDist --console=plain

# Letzte gezielte Metadaten-Gegenprobe nach Headerhärtung (Exit 0):
./gradlew :interlis-repository:test --tests '*RepositoryCacheContractTest' \
  :interlis-compiler-cli:installDist --console=plain
```

Der letzte Lauf ersetzt die aufbewahrte Cache-Suite durch den finalen Nachweis;
die unveränderten übrigen Suites werden wiederverwendet. Die zusätzliche
Headerpräzisierung weist nicht als HTTP-Header verwendbare Unicodewerte in manipulierten
Metadaten als Cachemiss ab. Sie verändert keine fachliche Modellwahl. Eine anschliessende gezielte
Gegenprobe erhält zusätzlich die konkrete Quellidentität beim Fehlen eines
gepinnten Offlineindexes. Dafür wurden nur Cache- und Closuretests wiederholt:

```sh
./gradlew :interlis-repository:test --tests '*RepositoryCacheContractTest' \
  --tests '*RepositoryClosureContractTest' :interlis-compiler-cli:installDist --console=plain
```

Auch dieser Lauf endet mit Exit 0. Seine beiden XML-Suites ersetzen die älteren
Versionen; die anderen unveränderten Suites bleiben aufbewahrt.

**15 reale CLI-Prozesse** wurden gegen lokale HTTP-Server und isolierte
user.home-Verzeichnisse ausgeführt. Zehn prüfen Standardpfad, lokale Kompilierung,
Erstdownload/Lock, Revalidierung, alternative Ablage, Abschaltung, Offline ohne
und mit Lock und beschädigten Cache. Im ersten Lauf werden zwei Dateiinhalte
übertragen; der zweite Lauf überträgt keinen zusätzlichen Inhalt. Die weiteren
fünf Prozesse prüfen am finalen Produkt die Headerpräzisierung: gültiges ETag/304,
manipulierte Metadaten offline ohne HTTP, unbedingte Reparatur und Offlineerfolg.
Die ersten zehn Prozesse behalten ihre ursprünglichen JAR-Hashes; ihr Produktstand
unterscheidet sich vom finalen durch die anschliessende Headerbereichsprüfung
und die ergänzte Quellprovenienz im Fehlerfall eines fehlenden gepinnten Indexes.
Die fünf Validatorprozesse liegen vor dieser letzten, rein diagnostischen
Quellprovenienzkorrektur; ihre Szenarien betreten diesen Pinfehlerpfad nicht.
Der finale Fehlerpfad wird durch den letzten gezielten JUnit-Lauf nachgewiesen.

Eine erste Prozessprobe wurde wegen einer falschen Harness-Erwartung beendet:
Sie erwartete bei fehlendem Offlineinhalt Exit 5, beobachtete korrekt Exit 1.
Nach Prüfung der vorhandenen ExitCodePolicy wurde nur die Erwartung korrigiert;
der wiederholte Lauf mit aufbewahrten Einzelergebnissen bestand. Dieser Abbruch
wird nicht als erfolgreicher vollständiger Nachweis gezählt.

### Rohbelege und Provenienz

- [JUnit-Bilanz](evidence/junit-summary.json), dazu Original-XMLs unter
  evidence/interlis-model, evidence/interlis-repository und evidence/interlis-compiler-cli.
- [Vertragslauf/Distribution](evidence/targeted-gradle.log) und
  [Headergegenprobe](evidence/invalid-validator-gradle.log) sowie
  [finale Gegenprobe zur Lockquellprovenienz](evidence/locked-source-gradle.log).
- [Zehn ursprüngliche CLI-Prozesse](evidence/cli-processes.json) und
  [fünf finale Validatorprozesse](evidence/validator-processes.json), jeweils mit
  Kommando, tatsächlichem Exit, stdout/stderr, HTTP-Zählern und JAR-Hashes.
- [Finale Produktionsdateien/Buildinputs](evidence/product-manifest.json)
  mit Ausgangscommit und SHA-256 je Datei. Die Prozessmanifeste enthalten auch
  die verwendeten unveränderten Compiler-/Workspace-/Projektions-JARs.

## Grenzen und Abschluss

Keine fälligen Prüfungen dieses Auftrags offen, keine Blocker. Keine Korpus-,
Benchmark-, Browser-, Plattform- oder Releasevollprüfung durchgeführt: geändert
sind JVM-Hostbeschaffung, CLI-Konfiguration und die additive Lifecycle-Methode;
kein portabler Semantik-/Wirevertrag. Commit und Push anschliessend auf
ausdrücklichen Nutzerauftrag; kein Release.
Die lokale installDist-Ausgabe wurde aktualisiert.

Keine automatische Bereinigung, kein Cacheaustausch mit ili2c und keine neue
LSP-Remoteauflösung. Ohne HTTP-Validatoren bleiben volle Downloads erforderlich;
Offline ohne Lock bezeichnet den zuletzt bekannten lokalen Stand, keine Zusage
über den aktuellen Serverstand. Ein Lock friert keine ungepinnten lokalen
Projekttexte ein. Der beauftragte Abschnitt ist implementiert und gezielt
verifiziert; weitere Cachewartung benötigt einen neuen fachlichen Auftrag.
