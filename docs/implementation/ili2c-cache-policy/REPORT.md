# Cache nach ili2c: MD5 und feste Fristen

WP-26/30; RES-009/013, CLI-004/006, LOG-005/008. Ausgangscommit
`6f4017c7ab90cb678c79f547b759c3661578726a`, Arbeitsbaum anfangs sauber.
Der konkrete Nutzerauftrag ersetzt die bisherige ETag-/Abschlussprüfungssemantik.
Umgesetzt und im unten bezeichneten Umfang verifiziert. Kein Commit, Push oder Release.

## Verhalten und öffentliche Verträge

RepositoryCacheRequest(maxAge,expectedMd5) ist unveränderlich/nullfrei und
liefert metadata() mit24h, model(md5) mit7Tagen sowie unconditional() mit0.
Die additive SourceLoader.load(locator,policy,request,token)-Defaultmethode
bewahrt funktionale alte Provider; Routing, HTTP und der verifizierende
Closureloader reichen den Request weiter. RepositorySearch, RepositoryClient
und LockedRepositoryCatalog verwenden explizite Metadatenfristen; Modellabrufe
übergeben die veröffentlichte MD5 und Modellfrist, ohne URL-Endungsheuristik.

Innerhalb der Frist wird lokale SHA-256-Integrität geprüft und die optionale
Index-MD5 vor einem Modellabruf verglichen. Frist überschritten, Zukunftszeit,
fehlende/defekte Inhalte oder MD5-Abweichung verursachen einen vollständigen
Download. Genau an der Fristgrenze bleibt der Inhalt gültig. Treffer verlängern
die Frist nicht. Nach Download bleiben MD5-, Header-, SHA-256- und Pin-/Lockprüfungen
wirksam. Ein generischer HTTP-Aufruf ohne Request lädt online unbedingt.
Onlinefehler bleiben Fehler; ausschliesslich --offline verwendet alte Inhalte.

RepositoryCacheMetadata(source,responseSource,digest,downloadedAt) ersetzt
bewusst die Validatorfelder der frühen SDK-API. downloadedAt ist Instant;
EPOCH ist unbekanntes Legacyalter. Die additive HostRepositories.createObserved-
Überladung nimmt java.time.Clock entgegen. Pro URL werden atomar metadata-v2-
Einträge veröffentlicht; der Uhrwert wird nur nach vollem erfolgreichem Download
gespeichert. Keine Zeitmessung anhand gemeinsamer Digestdateien.
V1 wird nur offline gelesen, bei vorhandenen defekten V2-Einträgen ohne Rückfall.
Online wird einmal neu geladen. no-store/404 invalidieren beide URL-Zuordnungen,
damit keine v1-Kopie wieder sichtbar wird; Inhaltsdateien werden nicht bereinigt.

ETag-/Last-Modified-Speicherung, bedingte Header,304-Nutzung und Cache-Retries
sind entfernt. Eine unaufgeforderte304 ist IO_FAILURE ohne Retry. identity,
Content-Coding-, Redirect-, Timeout-, Byte-/Abbruchlimits bleiben erhalten.
Vary sperrt den Anwendungscache nicht; no-store bleibt ausgeschlossen.

verifyUnchanged/verifyUnavailable und alle Abschlusszugriffe/-ereignisse
entfallen. Aktuelle Suchschritte und atomare Workspace-Revisionsprüfung bleiben.
FRESH_HIT wird zentral DE/EN gerendert und im RunContext als Treffer gezählt.
NOT_MODIFIED, VerificationStarted und ILI-RES-0013 bleiben reserviert, werden
vom aktuellen Ablauf nicht erzeugt. Katalogversion bleibtv4, keine neuen Codes
oder Bridgeformate. Keine neue Gateentscheidung, Compilersemantik oder Abhängigkeit.

Fachverträge08 §17,09/18, Register16, Abnahme13, Arbeitspakete14 und Matrix17
sowie README/CLI_PROFILE sind synchronisiert. Frühere Vertragsabschnitte erhalten
explizite Ablösehinweise; historische Rohbelege wurden nicht verändert.
Die beiden bisherigen Cache-Prozesshelfer delegieren auf die neue gemeinsame
Abnahme unter tools/verification/ili2c-cache-policy-processes.cjs.

## Tatsächliche Prüfung

Java25.0.3 auch für Gradle-Launcher/Daemon, Gradle9.8.1, Node24.22.0,
macOS15.8.1/aarch64. [Toolchain](evidence/toolchain.json),
[alle Kommandos und tatsächlichen Exits](evidence/executions.json).

- Vor Implementierung scheitert der neue CLI-Regressionsfall an4 statt2 HTTP-
  Zugriffen: [RED-Log](evidence/red.log), [Original-XML](evidence/red/).
- Nach Korrekturen **139 unterschiedliche JUnit-Fälle in26 Suites ohne
  Fehler/Skips**: 11 Modellfälle/3 Suites,90 Repositoryfälle/13 Suites und38
  CLI-Fälle/10 Suites. [Bilanz](evidence/test-summary.json), [Original-XML](evidence/targeted/).
- Der gezielte Gruppenlauf hatte noch eine alte6-statt3-Zugriffserwartung im
  Closuretest und daher Exit1. Nach fachlicher Anpassung wurde ausschliesslich
  diese betroffene Suite wiederholt:15 Fälle ohne Fehler/Skips, Exit0.
  Die26-Suite-Bilanz übernimmt die übrigen bestandenen Originalfälle und den
  korrigierten Closurelauf; sie behauptet keinen neuen gemeinsamen Gesamtlauf.
  [Gruppenlog](evidence/targeted.log), [Originalstand](evidence/targeted-before/),
  [Closure-/SDK-/Distributionslog](evidence/closure-sdk-distribution.log),
  [Closure-XML](evidence/closure-rerun/).
- **Neun reale SDK-Prozesskommandos** bestehen, einschliesslich externer
  Installation/Compiler-/Formatter- und Manifestverträge:
  [SDK-Rohresultat](evidence/sdk-processes.json).
- **22 kontrollierte CLI-Prozessfälle** erfüllen ihre Erwartungen:
  [Log](evidence/processes-r2.log), [Kommandos, Exits, HTTP und JAR-Hashes](evidence/processes.json).
  Kaltlauf lädt Index und Modell je einmal; alle vier Befehle verwenden warm
  null HTTP. Abgelaufener Index allein lädt nur den Index, neue Index-MD5 lädt
  zusätzlich das noch frische Modell. DE/EN, Farben, JSON, Offline/Locks,
  --no-cache/no-store, Compiler-/Auflösungsfehler und unerwartete Komprimierung
  sind geprüft; kein bedingter Header oder Abschlussereignis in den Prozessen.
- **Vier öffentliche Prozesse** mit dem unveränderten Screenshot-Modell Exit0:
  je ein Initial-/Warmlauf für models.geo.admin.ch und models.interlis.ch mit
  isolierten Caches unter build/ili2c-cache-policy/public-cache. Initial jeweils
  sechs Downloads, warm jeweils null Downloads und sechs FRESH_HIT-Meldungen.
  Keine304-/Abschlussmeldung. [Originalausgaben und Eingabehash](evidence/public.json).

Die kontrollierten Uhrfälle prüfen beide TTL-Grenzen, fehlende/abweichende/
passende MD5, Instanzwechsel, unverlängerte Fristen, Zukunft, beschädigte/fehlende
Bytes, V1-Offline/V2-Migration und no-store-Invalidierung ohne Wiederbelebung
alter Zuordnungen. Die Closuregegenprobe beweist MD5-Weitergabe vor Download
und Ablehnung einer weiterhin falschen heruntergeladenen Modelldatei.
Vorhandene Pin-/Lock-, Offline-, Revisions-/Abbruch-/Parallel-, Such-, Netzwerk-
und Redirectfälle wurden gezielt mitgeprüft. Unerwartete304 erhält nur einen Abruf.

Zwischenstände bleiben transparent: ein mechanisch falsch überladener interner
readSource-Aufruf verursachte zunächst einen Compilefehler (initial-green.log),
der nächste Lauf bestand. consumers.log enthält noch eine alte3-statt2-Erwartung
für die entfernte304-Retrylogik; nach Ursachenprüfung korrigiert und im gezielten
Gruppenlauf bestanden. Der erste Prozesslauf scheiterte ausschliesslich an einem
neuen Testorakel: MD5-Abweichung emittiert korrekt CACHE_MISS vor DOWNLOAD und
hat daher eine zusätzliche INFO-Zeile. Das korrigierte Orakel prüft diese Meldung
explizit; [ursprüngliches Prozessresultat](evidence/processes-before.json) und
processes.log bleiben erhalten. Keine Produktionskorrektur für dieses Orakel.
Keine Goldens/XFAILs oder Sprach-/Semantikdiagnosen geändert.

## Auslieferung und Grenzen

installDist und distZip sind neu erstellt. Die generierte entpackte Distribution
unter interlis-compiler-cli/build/distributions/interlis-compiler-0.2.0-SNAPSHOT
ist aus installDist aktualisiert und für die vier öffentlichen Prozesse verwendet.
[Manifest](evidence/manifest.json) bindet Produkt-/Test-/Prozessquellen und ZIP/JARs
am uncommitteten Arbeitsstand. Nach den Produktprüfungen nur Dokumentation und
Rohbelege ergänzt. Der bestehende Benutzer-Cache wurde von den Gegenproben nicht
verändert; kein automatisches Löschen alter Digestdateien.

Keine offenen Pflichtprüfungen oder Blocker dieses Auftrags. Die Metadaten-SDK-
Signaturänderung ist dokumentiert; vorhandene Loader-Einstiege bleiben kompatibel.
Kein neuer Portabilitäts-/Browservertrag: JVM-Repositorytypen bleiben im Host,
FRESH_HIT wird nicht vom portablen Compiler emittiert, Katalog-/Wireversionen
sind unverändert. Kein Korpus-, Benchmark-, Browser-, Plattform- oder Releasevollauf
fällig oder ausgeführt. Der anschliessend beauftragte unabhängige Review ist unten dokumentiert.
Nächster Schritt dieses Implementierungsauftrags: keiner; Änderungen liegen zur
Durchsicht im Arbeitsbaum. Commit und Push sind durch den anschliessenden Reviewauftrag ausdrücklich autorisiert.

## Unabhängiger OpenCode-/DeepSeek-Flash-Review

Auf ausdrücklichen Folgeauftrag: OpenCodev2.0.28 mit deepseek/deepseek-flash,
Standardprovider api.deepseek.com ohne Provider-/Umgebungsübersteuerung.
Tatsächliche Sessionmodelle, ProzessExits, Guard und saubere Reviewworktrees
sind für beide Runden unter review/r1 bzw. review/r2 belegt. Keine Credentials
oder Authentisierungsdateien wurden in die Nachweise übernommen.

Runde1 auf isoliertem Snapshot c9fdd30a4c2f142f6dd5e16576333424b6e10349:
CACHE-01–06 PASS, keine Muss-Befunde; Originalquellen und Rohbelege unabhängig
auditiert.13 eigene Java- und16 Launcher-Gegenproben erfolgreich. Ein eigener
Probeaufbau erwartete zunächst einen404-Abruf trotz frischem Cache; der Reviewer
korrigierte diesen Request auf unconditional und prüfte danach erfolgreich
die Invalidierung. Das ist kein Produktionsfehler; der Originalverlauf bleibt
im komprimierten Transcript erhalten. Runtime-JARs unabhängig gegen Manifest
und Prozessbelege gebunden. Der ZIP-Eintrag lag im isolierten Checkout nicht
vor; dessen Hash wurde hier im ursprünglichen Arbeitsbaum erneut gegen das
Manifest geprüft.

Der einzige Befund N-1 war redaktionell: alte Formulierungen zu304 und zweiter
Onlineprüfung in Kapitel08 sowie der RES-013-Matrix. Hier korrigiert,
einschliesslich primärer Anforderungs-/Abnahmezeile und Beobachtungstext.
Der Bericht nannte für die Abnahmezeile zunächst versehentlich Kapitel09;
die tatsächliche Stelle ist Kapitel08:191. Historische Berichte unverändert.
[Originalreview](review/r1/review.md), [Urteil](review/r1/verdict.json),
[Originale Gegenproben](review/r1/probes/), [Laufmanifest](review/r1/run-manifest.json).

Runde2 auf c92669b9a6b6e08ea6881fbaae8a22a9ebda05b3: erneuter unabhängiger
Review PASS, N-1 behoben, null offene Befunde. Exakt zwei Spezifikationsdateien
ändern sich zum ersten Kandidaten; identische Git-Teilbäume für Produkt, Tests
und Helfer unabhängig geprüft. Deshalb Originalabnahme und eigene Gegenproben
weiterverwendet; keine unnötigen Testwiederholungen. Beide Reviewerprozesse
Exit0, Guard passed/Exit0; beide Checkouts sauber.
[Folgereview](review/r2/review.md), [Urteil](review/r2/verdict.json),
[Laufmanifest](review/r2/run-manifest.json).

Nach dem finalen Review nur diese Abschlussdokumentation und unveränderte
Reviewbelege ergänzt; keine Produkt-/Test-/Helferänderung. Kein offener
Reviewbefund oder Pflichtnachweis. Commit/Push anschliessend gemäss Nutzerauftrag,
kein Release.
