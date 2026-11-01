# HTTP-Netzwerkpolicy – Implementierung und Nachweise

Basis 958f186a92361c3b827a5f0de13a8afee3fe0294, Arbeitsbaum zu Beginn sauber;
Cache-Logging bereits enthalten. WP-26/30, RES-009, ARCH-005, CLI-001–007,
API-001–006. NET-01–05 sind implementiert, gezielt verifiziert und unabhängig mit PASS
abgenommen. Geprüfter Snapshot: 53eb35743ee3e51afa809f74daea575c5a32ccf5.

## Ergebnis

FetchPolicy besitzt jetzt offline/maxBytes/connectTimeout/readTimeout/
Optional<Duration> requestTimeout; Defaults 15/40/off. Alte Signaturen bewusst
entfernt und alle aktiven Verbraucher migriert. Historische Reviewproben unter
docs/implementation bleiben unverändert und sind keine aktuellen SDK-Beispiele.
Repositorykatalogrevision v4 berücksichtigt alle drei Zeitlimits.

HttpSourceLoader misst monoton, setzt Inaktivität bei Headern/nicht leeren Daten
zurück, begrenzt optional den vollständigen Austausch und verwendet frische
Budgets pro Redirect/Cache-Retry. Requests und Bodyempfang werden bei Timeout
oder Abbruch beendet. Cache/Offline/Pin/Lock/Diagnoseentscheidungen bleiben erhalten.
Proxywahl pro Loader: JVM-Selector bei Konstruktion übernehmen, direkt oder ein
expliziter HTTP-Proxy für HTTP/HTTPS-CONNECT. Keine globale Produktionsmutation.

CLI und Properties bieten die vereinbarten Optionen und Proxypräzedenz.
Validierung erfolgt vor Quell-/Netzwerkzugriffen, auch bei explain-config.
Defaultwerte stammen aus FetchPolicy; Herkunft und effektiver Proxymodus sind
sichtbar. README, Fachverträge, API-Register, Abnahmen und Index sind synchron.
Das SDK-Beispiel examples/sdk/RepositoryNetworkExample.java wird gegen die
Distribution kompiliert und ausgeführt.

## Tatsächliche Verifikation

Java-Toolchain 25.0.3 Temurin, Gradle-Wrapper 9.8.1, macOS arm64; Node v22.13.0 für den
lokalen Prozesshelfer (kein portabler Node-/Browsernachweis). Originalversionen
in evidence/toolchain.json. Der ursprüngliche Gradle-Launcher verwendete Java21;
die abschliessende gezielte Wiederholung verwendet nachweislich Java25 für Launcher
und Daemon (evidence/java25/toolchain.json). Die Tests selbst verwenden durchgehend
die Java25-Toolchain. Ursprüngliche Exits in evidence/executions.json, ergänzende
Exits in evidence/java25/summary.json und evidence/sdk-processes.json.

- Repository vollständig, weil FetchPolicy alle Repositoryverträge betrifft:
  **86 Tests / 15 Suites**, keine Fehler/Skips. Enthält Netzwerk-, Cache-,
  Offline-, Pin-/Lock-, Abschluss-, Diagnose- und Revisionsgegenproben.
- CLI gezielt: **40 Tests / 7 Suites**, keine Fehler/Skips. Auswahl:
  RepositoryNetworkConfigurationTest, P3ConfigurationContractTest,
  DefaultRepositoryCacheTest, SemanticRepositoryCommandsTest,
  RepositoryCacheRendererTest, VerboseRepositoryCacheTest, CliApplicationTest.
- **17 reale Distributionsprozesse**, alle Erwartungen erfüllt: vier Befehle
  über Proxy, JVM-Default, direkter Override, Properties, Explain, Usagefehler,
  Read-/Requesttimeout, fortschreitender Download, Cache/Offline ohne Netz.
  Ursprüngliche Requests, Ausgaben, Exits und JAR-Hashes in evidence/processes.json.
- **Zwei SDK-Prozesse** (javac/java) gegen gebaute JARs erfolgreich; tatsächliche
  Kommandos/Exits in evidence/sdk-processes.json.
- Repository-Javadoc erfolgreich. Ein vorhandenes falsch platziertes @param
  query am catalog-Einstieg wurde korrigiert; verbleibende Warnungen betreffen
  überwiegend bestehende fehlende Dokumentation. git diff --check erfolgreich.

Relevante Läufe:

```sh
./gradlew :interlis-repository:test :interlis-compiler-cli:test --tests 'guru.interlis.cli.RepositoryNetworkConfigurationTest' --tests 'guru.interlis.cli.P3ConfigurationContractTest' --tests 'guru.interlis.cli.DefaultRepositoryCacheTest' --tests 'guru.interlis.cli.SemanticRepositoryCommandsTest' --tests 'guru.interlis.cli.RepositoryCacheRendererTest' --tests 'guru.interlis.cli.VerboseRepositoryCacheTest' --tests 'guru.interlis.cli.CliApplicationTest' --console=plain
./gradlew :interlis-repository:javadoc :interlis-compiler-cli:test --tests '*RepositoryNetworkConfigurationTest' --tests '*P3ConfigurationContractTest' --tests '*DefaultRepositoryCacheTest' --tests '*SemanticRepositoryCommandsTest' --tests '*RepositoryCacheRendererTest' --tests '*VerboseRepositoryCacheTest' --tests '*CliApplicationTest' :interlis-compiler-cli:installDist --console=plain
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem node tools/verification/http-network-processes.cjs
```

Repositorytestbelege stammen aus targeted-tests.log; danach nur Javadoc des
RepositoryClient korrigiert, keine fachliche Produktänderung im Repositorymodul.
CLI-Finalbelege stammen aus cli-final-tests-r2.log nach Ableitung der Defaults
über FetchPolicy; finale Distributionsprozesse aus processes-final.log.

Zwischenfehler sind unverändert aufbewahrt: fehlende InterruptedException-
Behandlung im neuen Testhelfer; Header-Reset-Testserver ohne Flush; erster
Distributionsversuch mit geerbtem Java21 statt Java25; bestehender Javadocfehler.
Nach Ursachenprüfung jeweils gezielt korrigiert und erneut geprüft. Diese Läufe
sind keine PASS-Nachweise. Die separate Headergegenprobe ist mit Flush erfolgreich.
Keine Goldens, XFAILs, Originalpins oder Diagnoseerwartungen gelockert.

Keine Korpus-/Benchmark-/Browser-/Releasevollprüfung fällig: ausschliesslich
JVM-Host und CLI betroffen, keine Kern-/Bridge-/Wireänderung. Keine breiten
Läufe durch den Reviewer wiederholen. Kein Push, Release oder G2-Statuswechsel.

## Unabhängiger Review

Fester Kandidat, sauberer separater Checkout, Produkt-/Test-/Evidenzmanifest und
bytegleiche Distribution. OpenCode v2.0.28 mit deepseek/deepseek-flash direkt über
api.deepseek.com; aktueller lokaler Modellkatalog und Konfigurationsquellen zeigen
keinen DeepSeek-Endpunktoverride (evidence/provider.json, configuration-check.json).
Python-freier Guard, Originalbelegaudit, eigene gezielte Gegenproben und
regulärer Prozessabschluss erforderlich. Der unabhängige Review ist regulär abgeschlossen: **PASS für NET-01–05**,
keine offenen Muss-Findings; ProzessExit0, Guard passed/Exit0, Reviewcheckout
sauber auf 53eb35743ee3e51afa809f74daea575c5a32ccf5. 91 Assistantnachrichten im
bereinigten Sessionexport verwenden ausschliesslich deepseek/deepseek-flash.
[Unveränderter Reviewbericht](review/review.md), [Urteil](review/verdict.json),
[Prozess-/Modell-/Hashbindung](review/run-manifest.json).

Der Reviewer auditiert unabhängig alle 942 Produkt-/Test-/Vertragsdateien,
42 ursprünglichen Evidenzdateien und sieben JARs: keine Hashabweichung. Er prüft
126 Fälle/22 Suites, 17 Distributions- und zwei SDK-Prozesse anhand der Rohbelege.
Eigene Gegenproben: zehn erfolgreiche Java-Prüfungen einschliesslich TLS-Connect,
Fingerprint, Read-/Requesttimeout, Redirect, Teilcache und Isolation sowie zwölf
zusätzliche CLI-Prozessfälle. SDK-Beispiel erneut gegen JARs geprüft. Die
Javadoc-bedingte Belegwiederverwendung wird über identische ohne Debuginformation
kompilierte RepositoryClient-Klassen unabhängig bestätigt. Quellen und tatsächliche
Kommandos/Resultate unter review/probes und review/probe-processes.json.

Zwei ausdrücklich nicht blockierende Beobachtungen bleiben transparent:
OBS-01 betrifft die Aussagekraft eines ursprünglichen Prozessfalls; JUnit und
eigene unabhängige Proben unterscheiden Gesamtlimit und Readinaktivität eindeutig.
OBS-02 ist ein bereits vor dem Kandidaten bestehender Fehler: ein syntaktisch
ungültiges Redirect-Location kann als IllegalArgumentException statt typisiertem
IO_FAILURE austreten. Kein NET-Abnahmekriterium ist dadurch offen. Nächster
fachlicher Folgepunkt ist ein separater gezielter Fix dieser Redirectfehlerabbildung.
Die Originalberichte werden dafür nicht umgeschrieben.

## Ergänzender Java25-Lauf und Abschluss

Nach Erstellen des unveränderten Kandidaten wurde die Gradle-Launcherabweichung
zu Java21 erkannt. Deshalb wurden genau dieselben 126 Fälle/22 Suites mit
explizitem JAVA_HOME für Java25 erneut ausgeführt: **0 Fehler/Skips, Exit0**.
Dies ist eine begründete Toolchainkorrektur, kein zusätzlich gezählter Fallumfang.
Roh-XMLs und tatsächliches Kommando unter evidence/java25/, Buildlog unter
evidence/java25-final-tests.log. Produkt, Tests und Distributions-JARs bleiben
bytegleich zum unabhängig geprüften Kandidaten; dies wurde abschliessend gegen
das ursprüngliche Manifest geprüft. Der Review auditiert die ursprünglichen
Rohbelege; der ergänzende Lauf ist separat ausgewiesen.

Nach dem Kandidaten ändern sich ausschliesslich zusätzliche Nachweise und
Implementierungsstatus/-bericht. Keine offene Pflichtprüfung oder Blocker dieses
Auftrags; keine Produktkorrektur nach dem PASS. Der Benutzerbranch und sein Index
bleiben unverändert; die Implementierung liegt im Arbeitsbaum und im isolierten
Review-Snapshotcommit. Kein Push oder Release.
