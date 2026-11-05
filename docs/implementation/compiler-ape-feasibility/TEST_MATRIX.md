# Gezielte Testmatrix — Compiler-APE

## 1. Regeln für diese Matrix

Die IDs benennen **Prüffragen**, nicht zwingend je einen eigenen teuren Build. Ein gemeinsamer Fixtureaufbau und wenige echte Prozessaufrufe dürfen mehrere Fragen beantworten. Trotzdem müssen alle deklarierten Beobachtungen im Report einzeln nachvollziehbar sein. Keine Erfüllung durch blosses Auflisten einer ID.

Vorhandene kleine CLI-, SDK-, Formatter- und Repositoryfixtures wiederverwenden, soweit sie ohne LSP/Browser sinnvoll laufen. Ihre Herkunft und Hashes erfassen. Keine umfangreichen Produktfixturedateien kopieren, wenn ein stabiler expliziter Verweis genügt. Änderungen an bestehenden erwarteten fachlichen Ergebnissen sind nicht Bestandteil dieser Migration.

Erwartungen ergeben sich aus gültigem CLI-Vertrag, bestehenden Tests und echter JVM-Referenz derselben Jenesis-JARs. Die JVM ist ein Vergleich, nicht ein Orakel, das offensichtliche Fehler legitimiert. Bei bereits fehlerhafter Baseline den Befund getrennt melden. Nicht beide Implementierungen auf eine falsche Erwartung normieren.

K und F bilden die native Kernparität. N prüft Netzwerk-/Repositoryfunktionen. H untersucht Hostabhängigkeiten auf dem primären Host. P ist der kleine Wiederholungsumfang auf jeder Pflichtplattform. U prüft die neue Build-/Nachweislogik mit billigen positiven und negativen Kontrollen.

## 2. K — reale Compiler-/CLI-Parität

| ID | Fall | Muss beobachtet werden |
|---|---|---|
| K01 | `help`, `version`, `version --json`, `capabilities` | Exitcodes, Werkzeug-/API-/Diagnose-/Formatversionen und Fähigkeiten stimmen; kein Stacktrace/Native-Buildertext auf Produktstdout. Laufzeitversionszeile darf sich begründet unterscheiden. |
| K02 | Kleines gültiges Modell in INTERLIS 2.3 und 2.4 | Tatsächliche Annahme durch `check` und den bestehenden `compile`-Alias; geordnete Ausgabe/Exitcode wie JVM. Keine Datensatzconstraint-Auswertung behaupten. |
| K03 | Syntaxfehler und separat semantischer Fehler | Tatsächliche Ablehnung; Diagnosecode, Schwere, Fundstelle und Reihung prüfen, kein Crash und kein leeres vermeintliches Erfolgsresultat. |
| K04 | Zwei lokale Modelle mit IMPORTS und expliziter Rootwahl | Gleiche transitive Modellauflösung, keine Quellen unterschlagen, Quelle/Root und Ergebnis stimmen. |
| K05 | `analyze` und `--json` | Strukturierte Diagnose-/Ergebnisdaten verlustfrei vergleichen; Arrayreihenfolge, Quellranges und grosse Zahlen nicht normalisieren. |
| K06 | `diagram` in den vom aktuellen CLI tatsächlich zugesagten Formaten | Kleine reale Projection mit mindestens einer Beziehung; vollständiger Text/JSON, benötigte Ressourcen vorhanden. Kein GUI-Rendererlauf. |
| K07 | DE-/EN-Diagnosen und Unicode/Text-/Dezimalfixture | Reale Sprachressourcen, Umlaute/Supplementary Characters, Tokenwerte und exakte Zahlen bleiben korrekt. |
| K08 | Ungültige Option, fehlende Datei, falsche Konfiguration | Gleiche Fehlerklasse und vertraglicher Exitcode; keine erfundene Zahl, kein stiller Java-/Onlinefallback. |
| K09 | Farben/Redirect und Streams | `--color never` bzw. nichtinteraktive Defaults farbfrei, `--color always` nur dort wo vertraglich zulässig; JSON/format/diagram-stdout bleibt sauber. Ein tatsächlicher PTY-Fall nur falls für eine konkrete native Terminalfrage nötig. |

Eingabemodell möglichst klein, aber repräsentativ: Domäne, Klasse, Vererbung oder Assoziation, ein Constraint und ein Literal. `compile` bedeutet hier Modellkompilierung/-prüfung; weder Java-Bytecodeerzeugung noch Prüfung konkreter XTF-Objekte.

## 3. F — vorhandene Formatierung, kein neuer Stil

| ID | Fall | Muss beobachtet werden |
|---|---|---|
| F01 | `format Datei.ili` | Ausgabe stimmt mit JVM überein; Originaldatei unverändert. |
| F02 | `format --check` vor/nach Formatierung | Änderungsbedarf liefert bestehenden Fehlerstatus, danach Erfolg; keine Schreiboperation. |
| F03 | `format --write` | Datei tatsächlich geändert; resultierende Bytes stimmen mit JVM und erneutem Check überein. |
| F04 | `--indent 4` und LF/CRLF | Vorhandene Optionssemantik; Kommentare/Unicode und werttragende mehrzeilige Literale/Metaattribute nicht verändert. |
| F05 | Syntaktisch ungültige Quelle | Kein Teilprodukt, Originalbytes erhalten; sinnvoller Fehler statt Crash. |
| F06 | Nicht beschreibbares Ziel oder schmaler nachweisbarer IO-Fehler | Original bleibt erhalten; temporäre Dateien werden ordentlich behandelt, soweit bestehender Vertrag; keine unbelegte Mehrdatei-Transaktionsgarantie. Symlinkverhalten wie bestehender Hostvertrag. |

Auf Systemen, auf denen Berechtigungsentzug unter dem aktuellen Account keine reale Schreibsperre erzeugt, einen tatsächlich wirksamen kontrollierten Fehler wählen oder den Fall offen kennzeichnen. Ein Root-User, der trotz `chmod` schreiben kann, ist keine bestandene Negativprobe.

## 4. N — Repository, TLS, Cache, Proxy und Timeouts

| ID | Fall | Muss beobachtet werden |
|---|---|---|
| N01 | Kontrolliertes lokales Repository über HTTP mit Index und transitiven Imports | Root ohne lokale Source findet exakte Dateien; Requests und Bytes werden erfasst; gültiges Modell tatsächlich kompiliert. |
| N02 | Warmcache, anschliessend Server aus und `--offline` | Gleiche Quellen aus Cache; null neue Netzrequests; fehlender/kaputter Cache scheitert sichtbar. |
| N03 | Kontrolliertes HTTPS mit isolierter Test-CA | Gültiger TLS-/Hostnamepfad und Kompilierung; Runtime-Truststore der konkreten Toolchain wirklich wirksam. Finaler Standardkandidat bleibt unverändert. |
| N04 | HTTPS mit unbekannter CA und separat falschem Hostnamen | Beide werden abgelehnt; positiver Kontrollfall vorher/nachher zeigt, dass nicht ein allgemeiner Defekt jede Verbindung scheitern lässt. |
| N05 | Öffentlicher HTTPS-Repositorysmoke mit Standardtrustbasis | Finaler Standardkandidat ohne Testtrustoverride lädt und kompiliert eine kleine ausgewählte Modellclosure. URL/Responsehashes/Datum erfassen; Netzblockade ist kein technischer Negativbeweis. |
| N06 | Kontrollierter Proxy, einschliesslich HTTPS CONNECT | Zielzugriffe gehen nachweislich durch Proxy; `--no-proxy` umgeht ihn nachweislich. Keine neue Proxy-Authentisierungsfunktion entwickeln. |
| N07 | Server akzeptiert Verbindung, sendet aber nicht weiter | Kleiner Read-Inaktivitätstimeout greift mit kontrolliertem Fehler; kein unbegrenzter Hänger. Timeoutparameter wie bestehender CLI-Vertrag. |
| N08 | Fortlaufend sendender Server und optionaler Request-Gesamttimeout | Inaktivität und Gesamtdauer werden nicht verwechselt; expliziter Gesamttimeout beendet den langsamen fortlaufenden Fall. Kleiner begrenzter Fixturelauf. |
| N09 | Expliziter Cacheort / fehlende Datei / korrumpierte Antwort / einfacher Redirect | Relevante vorhandene Integritäts-/Ladefehler bleiben sichtbar. Nur vorhandene unterstützte Redirectregeln nachweisen, keine zusätzliche HTTP-Policy implementieren. |

Die Serversimulation liegt auf Loopback und zählt echte Zugriffe. Kein Unit-Mock ersetzt die Nutzung der nativen HTTP-/TLS-/Dateifunktionen. Synthetische kaputte Reports prüfen nur den Harness, nicht das Netzwerk.

Für N05 eine minimale stabile öffentliche Quelle auswählen und Responses dokumentieren. Keine 100+-Modelle-Korpusauflösung. N01–N04 und N06–N09 sind kontrollierte lokale Tests und dürfen nicht von einem fremden Live-Dienst abhängen.

## 5. H — Hostzustand und Verlagerung

| ID | Fall | Muss beobachtet werden |
|---|---|---|
| H01 | Binary und Fixtures an neuen Ort kopieren; anderer CWD | Keine Abhängigkeit von Buildpfaden; relative Modellpfade beziehen sich auf Aufruf-CWD. |
| H02 | Installations-/Modellpfade mit Leerzeichen, Umlauten und Emoji | Argumente bleiben getrennt und unverändert, auch Windows; keine ANSI-Codepage-Verluste. |
| H03 | Frisches HOME/Temp, PATH ohne Java; JDK/Buildbaum wirklich unzugänglich | Compiler läuft trotzdem; private Loaderextraktion/Cache und notwendige OS-Ressourcen vollständig ausweisen. |
| H04 | Leeres/nicht beschreibbares Temp- oder Cacheverzeichnis; `--no-cache` | Kontrolliertes Verhalten, verständliche Fehler, kein heimlicher Schreibzugriff auf ursprüngliches Entwickler-HOME. |
| H05 | Prozessbeendigung in begrenztem Lauf | Eigener Prozess wird zuverlässig beendet, kein hängenbleibender Builder/Server; Signale nicht als normalen Cancellation-Exitcode ausgeben, wenn der Produktvertrag das nicht vorsieht. |

H03 ist ein Nachweis der tatsächlichen Zugriffsschranke, nicht nur einer leeren Environment-Variable. Wo Sandbox/VM nicht verfügbar ist, den erreichten Teilbeleg präzise benennen und die fehlende Hermetikprüfung offenlassen.

## 6. P — kleiner nativer Pflichtumfang je Zielsystem

| ID | Probe auf Linux x64, Windows x64 und macOS arm64 | Erwartung |
|---|---|---|
| P01 | Kandidat kopieren/entpacken, SHA vor/nach Lauf | Ein identischer finaler Universalhash auf allen Zielen; keine Architekturdateien vertauscht. |
| P02 | Normaler Terminal-/Shellstart | Echte Version und kleiner gültiger Compile, dokumentierte Shell und Loaderbedingungen. |
| P03 | Direkter Kindprozess ohne Shell | Start, geordnete Streams, Exitcode; Scheitern als eigener Befund, nicht hinter Shellfallback verbergen. |
| P04 | K02/K03/K04/K05 in kompaktem Fixture | Echte gültige/ungültige Modelle, Imports und JSONdiagnosen, einschliesslich mindestens einer 2.4-Quelle. |
| P05 | F01/F02/F03 und H02 | Formatieren/Schreiben und Unicodepfade funktionieren auf dem wirklichen Dateisystem. |
| P06 | N01/N02 | Lokale HTTP-Auflösung und netzfreier Offlinecache auf jeder Plattform. |
| P07 | N03/N04 | TLS mit gültigem Host und sichere Ablehnung fehlerhafter Zertifikate/Hostnamen je Plattform. |
| P08 | N06/N07/N08 in kleinem gemeinsamen Aufbau | OS-spezifische Proxy-/Socket-/Timeoutpfade real geprüft. |
| P09 | H01/H03 und erster Loaderstart | Ohne JVM/Builderbaum, mit frischem privatem Cache; benötigte Zusatzprogramme/Beigaben erfasst. |

K01, K06–K09, F04–F06, N05/N09 und H04/H05 müssen nicht pauschal auf allen Zielen wiederholt werden. Gezielte Wiederholung ist erforderlich, wenn ein konkreter Plattformbefund dort dieselbe Funktion betrifft. Bei einer später geänderten Universaldatei ist die kleine P-Matrix für den neuen Hash notwendig; reine Report-/Dokuänderungen lösen sie nicht aus.

## 7. U — billige Gegenproben für Build und Evidenz

| ID | Mutation / Fall | Erwartete Ablehnung oder Beobachtung |
|---|---|---|
| U01 | Falscher Toolchainhash, fehlendes apelink, unpassende Architekturdatei | Kein Buildstart/keine Fusion, klare Ursache. |
| U02 | Fremde/missing/zusätzliche JAR, anderer Hash, alter Manifestfingerprint | Keine stillschweigende Closureergänzung; kein Build aus fremden Eingaben. |
| U03 | Geänderter Input während Snapshot/Build | Ergebnis nicht als gültiger Kandidat freigeben. Kontrollierter Hook nur im Harness, keine Produktbackdoor. |
| U04 | Halb geschriebenes Ergebnis, Timeout, Signal, leeres Reportset | Kein PASS; Fehlerursache und Rohlogs bleiben erhalten. |
| U05 | Ein Case/eine Plattform fehlt, doppelter Case, Skip/Fehler verschwiegen | Die behauptete vollständige Abnahme scheitert. INCONCLUSIVE ist offen und kein Produkt-PASS. |
| U06 | Fremder Binaryhash, manipulierte Datei oder gemischte Architektureingaben | Nachweise werden nicht auf die neue Datei übertragen. |
| U07 | Überbreite Normalisierung entfernt Diagnose/Exitcode/Decimal/Arrayelement | Comparator muss den Unterschied weiterhin erkennen. Positive Kontrolle mit echten äquivalenten Ergebnissen. |
| U08 | Archiv enthält zusätzlichen Runtimebestandteil oder verliert Executablebit | Lieferprüfung scheitert oder weist konkrete notwendige Hostvorbereitung aus; nicht einfach ignorieren. |
| U09 | Unveränderter Wiederholungsaufruf / Änderung nur nativer Konfiguration | Nur verifizierte identische Schritte wiederverwenden; relevante Änderung invalidiert native Buildinputs. Keine neue globale Cacheplattform. |
| U10 | Normaler JVM-/Gradle-/Editorpfad ohne APE-Konfiguration | Kein Native-Build/Download/Toolchainzwang; kleine vorhandene Einstiegs-/Taskgraphkontrolle genügt. |

Mutationstests ausschliesslich in separaten Scratchkopien. Quell-, Vendor- und ursprüngliche Testbelege nicht verändern.

## 8. Vergleich und erlaubte Unterschiede

Prüfe stdout, stderr und Exitcode getrennt. Ein Produktprozess mit erwartetem nichtnull Exitcode ist ein erfolgreicher **negativer Test**, nicht ein global ignorierter Fehler. Unbekannte Exits, Hänger und ungeparste Ausgabe sind Fehler.

Erlaubte projektspezifische Normalisierungen müssen einzeln festgeschrieben sein:

- bekannte unterschiedliche absolute temporäre Testwurzeln, ausschliesslich an den dazugehörenden Pfadfeldern;
- echte JVM-/Native-Laufzeitversionsangabe dort, wo die CLI sie explizit als Runtimeinformation ausgibt;
- dokumentierte technische Laufzeitmesswerte und zufällige Lauf-IDs, nicht fachliche IDs.

Nicht erlauben: Sortieren von Diagnose-/Modell-/Memberarrays, Entfernen von Warnungen oder Fehlern, Runden von Dezimalzahlen, Zusammenfassen von whitespace innerhalb Strings/Kommentaren/Metawerten oder Streichen unbekannter JSONfelder. Objekt-Keyreihenfolge darf bei einem echten strukturierten JSONvergleich ohne Zahlenverlust ignoriert werden. Einen Parser verwenden, der grosse Zahlen nicht über JavaScript-Double unbemerkt verändert.

## 9. Kleine Messungen

Eine isolierte Erstbeobachtung und beispielsweise fünf frische Folgeprozesse für `version` und einen kleinen lokalen Compile reichen zur Orientierung. Dieselben Inputs, gleiche Maschine und nachvollziehbarer Zustand. OS-Caches nicht als „kalt“ bezeichnen, wenn sie nicht kontrolliert sind; keine privilegierten Cacheflushes.

Erfasse Binary-/Archiv-/Beigabengrösse, Builderlaufzeit, verfügbare Peak-RAM-Messung und beobachtete Prozesszeiten. Mediane und Stichprobengrösse nennen; keine performanceseitige Freigabe oder feste Geschwindigkeitszusage ableiten. Native-Builds nicht allein wegen einer Messabweichung mehrfach neu erzeugen.
