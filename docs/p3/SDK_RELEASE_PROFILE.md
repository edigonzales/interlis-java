# P3 – SDK-Kandidat und Freigabeprofil

P3 bereitet lokale Artefakte vor. Ein G3-PASS ist keine Veröffentlichung und
keine uneingeschränkte Drop-in-Zusage: die bestätigten Norm-/Kompatibilitäts-
profile und die expliziten nicht unterstützten CLI-Generatoroptionen gelten.

## Getrennte Versionen

| Vertrag | Aktueller Wert | Bedeutung |
|---|---|---|
| Werkzeug/Artefakte | 0.2.0-SNAPSHOT | Entwicklungskandidat; kein stabiler1.0-Release |
| Java-API | 0.2 | Öffentliche guru.interlis-API; interne Packages ausgeschlossen |
| Diagnosekatalog | interlis-diagnostics/v1 | Stabile Codes und strukturierte Argumente |
| Compilerbridge | interlis.compiler/1.0 | Versionierte Requests/Replies und typisierte Ergebnisse |
| Quellformatierung | 1.0 | Deterministische Formatierungsoptionen und Ergebnisvertrag |
| Diagramm-/Optionsschema | 1.0 | Kanonische IDs/Anker sowie additive explizite Optionsfelder |
| Sprachprofil | INTERLIS2.3 und2.4 | Bewusst bestätigte Profile stehen in COMPILER_NORM_DECISIONS.md |

`version --json`, `capabilities` und das mitgelieferte Versionsmanifest müssen
übereinstimmen. Snapshot-/Schemahandles gehören zu ihrer Sitzung/Generation;
sie werden nicht persistiert oder zwischen Clients übertragen.

## Migration gegenüber dem früheren Entwicklungsstand

- `DiagramOptions` hat explizite optionale `translationModel` und
  `neighbourhoodDepth`. Der bisherige Konstruktor bleibt als Überladung erhalten.
  Java-Recordpatterns müssen auf die aktuelle Komponentenliste angepasst werden.
  TRANSLATED verlangt ein tatsächlich gebundenes Übersetzungsmodell desselben
  Snapshots, NEIGHBOURHOOD eine explizite Tiefe0..32. Kanonische Namen/IDs und
  Originalquellanker ändern sich bei der Anzeigesprache nicht.
- `rejection.requestedRoots` enthält die ursprünglichen strukturierten
  RootSelectorDto-Werte, `resolvedRoots` die konkretisierten Gate-Selektoren.
  Java-/Optional-toString-Ausgaben waren ein Adapterfehler und sind kein
  unterstütztes Serialisierungsformat. BySource verwendet `source`.
- Eine ausdrücklich leere Diagrammauswahl ist ungültig. Bei fehlender Auswahl
  gilt die angeforderte Modellclosure. Budgets gelten gemeinsam pro Knoten;
  echte Kürzungen ergeben LIMITED mit Hinweis. `showTypes=false` gilt für alle
  Mitgliedsarten. Unbekannte/falsch typisierte Optionen werden ausdrücklich
  abgelehnt; sie werden nicht still ignoriert.
- `CompilerClient` hält einen vollständigen bestätigten Mehrdatei-/Konfigurations-
  stand und stellt nach Workerabbruch neue Handles bereit. Alte Handles werden
  abgelehnt. `closeSession` ist für dasselbe eigene Handle idempotent; parallele
  Abschlüsse werden zusammengeführt. Der Eröffnungsfingerprint entspricht der
  vollständigen Konfigurationsquittung einschliesslich Cachegrenzen.
- Fachliche Verhaltensänderungen sind separat dokumentiert: insbesondere die
  ausdrücklich freigegebenen REFSYSTEM-Körbe in TYPE MODEL sowie die Korrekturen
  bei qualifizierten vordefinierten Typen, Rollenräumen, ALL-OF-Vererbung und
  aufgeschobener generischer Referenzsystembindung. Keine automatische
  Lockerung der bestätigten strikten Normprofile aus Referenzerfolgen.

## Abnahme und Belege

Implementiert, noch nicht gesamthaft abgenommen: `sdkArchiveCheck` prüft den
Consumer aus dem entpackten SDK einschliesslich Launcher. `platformCheck`
führt die JVM-/CLI-/LSP-/SDK-Prozesse aus. `sdkReproducibilityCheck` baut zwei
separate saubere Checkouts und vergleicht die ausgelieferten Dateien byteweise.
Das tatsächliche Browserverzeichnis enthält `build-manifest.json` mit
Quell-/Commitbindung, getrennten Versionen und Ressourcenhashes.
Es nennt auch die Originalpins und den tatsächlichen Core-JAR-Hash der
gezielten [TeaVM-Toolkorrektur](../../tools/teavm/README.md), welche stabile
Klassenreihenfolgen für bytegleiche JavaScript-/Wasm-Erzeugung herstellt.

Der Ablauf am sauberen Schlussprodukt ist:

1. Commit auf den autorisierten Arbeitsbranch pushen und beide Jobs von
   `.github/workflows/p3-platforms.yml` erfolgreich abschliessen lassen.
2. Mit Node24 `tools/release/platform-evidence.cjs` ausführen; bei Bedarf zeigt
   `GH_EXECUTABLE` auf das vorhandene `gh`. Der Collector prüft den aktuellen
   Commit, beide echten Jobs und die GitHub-Archivprüfsummen.
3. Unter Java25, Node24, GraalVM25/Binaryen133 und den gepinnten echten Browsern
   `node tools/verification/without-python.cjs ./gradlew --no-daemon
   releaseCheckP3 -PnodeExecutable=/absoluter/pfad/zu/node` ausführen.
   Gleichwertig ist `-PreleaseProfile=P3 releaseCheck`. Auf der macOS-
   Referenzmaschine hält temporäres `caffeinate -diu` während des langen
   UI-Laufs den Bildschirm aktiv; keine globalen Energieeinstellungen ändern.
4. Internes `build/reports/p3/release/result.json`, regulären Prozessabschluss
   und anschliessend `build/reports/python-free/environment.json` mit
   `status=passed` gemeinsam prüfen. Danach ist der unabhängige kumulative
   OpenCode-Review über die direkte DeepSeek API am selben Produkt fällig.

Fehlende, alte, partielle, fremde oder übersprungene Nachweise schliessen das
Gate. Keine Budgets oder fachlichen Erwartungen werden dabei automatisch
angepasst. Versionierte Spezifikation, Originalpins und unveränderte unabhängige
Reviews bleiben die Vertrags-/Abnahmequellen. Ein späterer Dokumentcommit kann
den geprüften Produktcommit benennen; er etikettiert dessen Rohbelege nicht um.
