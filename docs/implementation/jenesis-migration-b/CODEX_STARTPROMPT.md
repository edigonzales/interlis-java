# Codex-Auftrag — Jenesis-Migration B

Du implementierst Auftrag B im Projekt `edigonzales/interlis-java` und die dafür erforderlichen Verbraucheränderungen in `edigonzales/interlis-vscode`. Codex ist der Primäragent. Lies die gesamte Spezifikation dieses Ordners; sie ersetzt keinen Repositoryzustandscheck und keine geltenden Sicherheitsregeln.

## 1. Verbindliches Ziel

Überführe den in Auftrag A parallel eingeführten Jenesis-JVM-Build in den führenden und einzigen aktiven JVM-Produktbuild. Entkopple die verbleibenden Gradle-Wasm-Backends über ein geprüftes Artefaktpaket. Migriere den tatsächlichen Extension-Stagingpfad. Führe anschliessend explizite JPMS-Deskriptoren und echte modulare CLI-/LSP-Starts ein. Erhalte fachliche Compiler-, Formatter-, LSP-, Repository- und Verificationverträge.

B besteht aus B-I (B0–B4, Buildbesitz) und B-II (B5–B7, Modularisierung und Abschluss). Beide Teile sind beauftragt; keine Rückfrage allein wegen dieser Aufteilung. Ein korrekt belegter B-I-Zwischenstand ist wertvoll, aber nicht der Abschluss des Gesamtauftrags.

Die APE-Untersuchung und der lokale Clock-Patch sind ausdrücklich pausiert. Arbeite auf einem neuen B-Branch/Worktree aus der geeigneten aktuellen Core-Baseline. Kein Merge des APE-Branches, keine Native-Builds, keine Substitution und kein Entfernen seiner Belege. Falls lokale neuere Quellen bereits APE-Infrastruktur enthalten, erhalte sie und prüfe ihre Input-/API-Grenze statisch; führe sie nicht aus.

## 2. Vorgehen

1. Lies AGENTS.md in beiden Repositories, einschliesslich pfadspezifischer Regeln. Erfasse Branch, SHA/Tree, staged/unstaged/untracked Änderungen. Keine fremden Änderungen stagen, verwerfen oder verschieben.
2. Lies README, SPECIFICATION, BUILD_OWNERSHIP_AND_TASKS, ARTIFACT_HANDOFF, JPMS_AND_TESTING, EDITOR_AND_RUNTIME, CI_AND_RELEASE, EXECUTION_PLAN, TEST_AND_EVIDENCE_POLICY und OPENCODE_REVIEW_PROMPT.
3. Prüfe A anhand der tatsächlichen Quellen und vorhandenen Rohbelege. Ein alter noch offener Reviewstatus ist kein Anlass für einen ungefragten zweiten A-Vollauftrag; fehlende Belege ehrlich markieren und die für B relevanten Gates neu erbringen.
4. Erstelle die kurze operative Baseline/Disposition. Praktische Capability-Proben in B0, vor dem grossen Umbau. Baue keine Annahme aus aktueller Online-Doku ungeprüft in die gepinnte ältere Engine ein.
5. Implementiere B0–B7 in Reihenfolge. Jede Phase muss ihre notwendigen Tests erfolgreich bestehen, bevor die nächste abhängige Implementierungsphase beginnt. B5 enthält kleine Modul-Batches mit eigenem Gate.
6. Halte Testidentitäten, Produktinputs, konkrete Artefakte und tatsächliche Exits fest. Vollständige JVM-Tests einmal über den finalen relevanten modularen Produktstand abdecken; erfolgreiche Phasenläufe dürfen beitragen, wenn ihre Inputs noch identisch sind.
7. Prüfe final Scope, Diff, Restesuche, Buildgraphen, Dokumentverweise und beide Arbeitskopien. Committe den geprüften Core und die erforderlichen Extensionänderungen lokal. Kein Push, Merge, Tag, Release oder Nachricht an Dritte.
8. Starte **erst danach** einen frischen unabhängigen Gesamt-Review mit OpenCode 2.x und **DeepSeek Flash 1017 über die direkte DeepSeek-API**. Kein Reviewer nach jeder Phase. Technischer Verbindungs-/Modellpreflight vorher ist erlaubt.
9. Korrigiere berechtigte Findings selbständig. Bestimme den kleinsten ausreichenden Retest über tatsächliche geänderte Inputs und Verbraucher. Committe neue Kandidaten; starte einen neuen unabhängigen Gesamt-Review des gesamten kumulativen Diffs beider Repositories.
10. Schliesse erst mit gültigem PASS für die vertraglich verlangte lokale Implementierungsabnahme ab. Fehlende verpflichtende Integrationsnachweise oder ein nicht ausführbarer Reviewer sind BLOCKED, keine implizite Zustimmung. Eine nicht beauftragte komplette P3-Veröffentlichung ist davon getrennt.

## 3. Architekturgrenzen

- Kein eigener Jenesis-Fork, keine zweite Produktionskompilierung per Maven/Ant/eigenem javac-Skript. Test-only javac/JUnit im eng definierten Jenesis-Hook ist zulässig; das ist nicht die Produktionsbuildspur.
- Keine Komplettumstrukturierung von src/, keine neue Compilersemantik, kein Formatter-Style-Auftrag, keine neuen DB-/Validatorfeatures.
- Alle eigenen JVM-Artefakte stammen aus einem eindeutig identifizierten Jenesis-Lauf bzw. dessen nachvollziehbar wiederverwendeten Inputs. Keine alten Gradle-Dateien, keine globalen Maven-SNAPSHOTs.
- Gradle darf die eigenen Java-Adapter der zwei Wasm-Backendprojekte kompilieren. Es darf keine Quellpfade der zehn JVM-Projekte übernehmen, auch nicht über SourceSets oder Composite Builds.
- Keine pauschalen exports/opens nur für Tests oder Gson. Keine öffentlichen package-private Internas, keine massenhafte Reflection-Umschreibung.
- Kein Testpatch, Test-JAR, Verification-JAR oder Tool-/Generator-JAR in einer Produktauslieferung.
- Der Default-JVM-Build braucht weder Node/Browser, Gradle, eine Extensionarbeitskopie, Web Image noch eine native APE-Toolchain. Solche Verbraucher laufen nur in den entsprechenden ausdrücklich gewählten Profilen.
- Bestehende Runtimepins der Extension nicht ohne fachlichen Grund ändern. Kein neuer jpackage-in-VSIX-Weg. Native Signierung/Notarisierung und neue Installer sind nicht beauftragt.

## 4. Fremde Arbeitskopien und Branches

`interlis-vscode` ist eine tatsächlich zu ändernde zweite Quelle, keine informelle optionale Gegenprobe. Nutze ein vorhandenes passendes Checkout oder eine isolierte Arbeitskopie. Keine Änderungen in einem persönlichen aktiven Extensionprofil; Tests laufen in isolierten Extension-Hosts.

Beim Core zuerst den Codecommit erstellen, auf dessen SHA das Extensionbundle verweisen kann. Danach Bundle-/Lock-/Extensionänderungen erstellen und den Extensioncommit erzeugen. Der finale Review bindet **beide** SHAs und Trees. Reviewausgaben ausserhalb der versionierten Repositories vermeiden eine Selbstreferenz-Commitkette.

Kann ein erforderliches Repository oder ein fälliger Test wirklich nicht gelesen/ausgeführt werden, erledige unabhängige zulässige Vorbereitung, aber markiere die davon abhängigen Gates offen. Keine produktive Fallback-Implementierung und keine zweite alte Buildspur als vermeintliche Lösung.

## 5. Testökonomie

Ein neuer Commit, eine neue Phase oder eine neue Reviewrunde allein löst keinen Volltest aus. Nach Änderungen an Reportadapter oder Dokumentation nicht alle Modelltests, Browser und Benchmarks wiederholen. Nach geänderten Moduldeskriptoren/Launchern echte Modulauflösung und betroffene Prozesse prüfen; nach geänderten JAR-Bytes deren Verbraucherbelege aktualisieren.

Erhalte rohe Reports unverändert. Parametrisierte/dynamische Testinvocations, Fehler, Skips und Runbindung bleiben sichtbar. Leere Auswahl ist kein PASS. Früheren Classpath-PASS nicht als JPMS-PASS deklarieren. Gradle-Taskgraph oder Node-Smoketest belegt keinen tatsächlich erzeugten Wasm-/Browserlauf.

Codex und OpenCode wiederholen grosse Testmengen nicht schematisch. Der Reviewer prüft Originalbelege und ergänzt eigene gezielte Negativ- und Integrationsproben.

## 6. Abschluss

Berichte: Ergebnis B-I/B-II, tatsächliche und ausgelassene Prüfungen, Scopegrenzen, Core-/Extension-SHAs, unabhängiges Modell/Provider/Urteil, Belegpfade, verbleibende Plattform-/Releasevorbehalte und Zustand der Arbeitskopien. Kein vollständiges Produktions- oder Plattform-PASS aus bloss lokaler Codeabnahme ableiten.

Beginne mit B0 und führe den Auftrag selbständig bis zum geprüften, committeten und unabhängig validierten Ergebnis aus. Erzeuge nicht bloss einen weiteren Plan.
