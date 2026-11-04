# Umsetzung

Ausgang `e389c8eecf50f7672d418df59f37341e47a44038`, sauberer main.
VM-01–18; BUILD-001/003/004/007/008 und WP-00/01/30 betroffen.
Keine Semantik-/API-/Toolpinänderung beauftragt.
V0 in Arbeit; V1–V4 und unabhängiger Gesamt-Review offen.
Rohbelege: `/Users/stefan/sources/interlis-java-verification-evidence/`.
OpenCode 2.0.28: run --standalone/--auto/--model/--format/--file bestätigt.
DeepSeek-Alias nach offizieller API-Dokumentation vom 04.11.2026:
https://api-docs.deepseek.com/quick/_start/pricing/ nennt deepseek-flash als
DeepSeek-V4.1-Flash-1017 am direkten Endpoint api.deepseek.com.
Noch kein fachlicher Reviewer gestartet.

V0 abgeschlossen: gezielte Baseline Exit 0, vollständiges Quell-/Methodeninventar
und konkrete Disposition vor V1. Zählung der Dateien im Inventar ist keine Fallzahl.
V1 beginnt: VM-01/03/04/05/11; erwartete Beobachtung: unveränderte Host-/Produktfälle
kompilieren und bestehen an den neuen Besitzern, alle JavaExec-FQCNs erreichbar.

V1 abgeschlossen: beide Compiles, 18 Toolfälle und 9 verschobene Produktfälle
bestehen; vier Zielgraphen erfolgreich aufgelöst. RepositoryReferenceCheck braucht
die explizite Repository-Dependency. Produktmodule hängen nicht von Verifikation ab.
V2 beginnt: VM-02/06–10/13; aktuelle Bindungen plus gezielte frische Produzenten,
Validatorgegenproben und methodengenaue Stilllegung historischer Planungsprüfer.

V2 abgeschlossen: alle drei alten Source-Sets entfernt; 76 Unitfälle,
4 explizite Contracts und 32 gezielte fachliche Ersatzfälle bestanden.
Ausgewählte echte Produktassertions und Normbindings erfolgreich;
leere Contractauswahl scheitert nachweislich. Historische Originalinputs unverändert.
V3 beginnt: VM-11–14/16; Reportleser, konsistentes Java-/Node-Profil,
Fingerprintparität und aktive Task-/Dokureferenzen prüfen.

V3 abgeschlossen: Java-Berichts-/Fingerprintgegenproben und 94 Nodefälle
bestehen; Profil-Delta 1411/352 nachvollziehbar. Neue Reportpfade sind Pflicht,
alte Pfade kein Fallback. Rootgraphen zyklusfrei; check ohne Normvorbereitung.
V4 beginnt: VM-04/15–17; frische lokale Unit-/CLI-/SDK-/LSP-Integration,
Quellen-/Artefaktkontrolle und Kandidatencommit vor unabhängigem Review.

V4 abgeschlossen: frische Compile-/Unit-/Contractgruppe, neun echte SDK-Prozesse,
entpackter SDK-Consumer und LSP-Distribution erfolgreich. Isolationsprobe prüft
Produkt-JAR-Inhalte, ZIP und alle produktseitigen compile/runtime/test-Dependencies.
VM-01–17 implementiert und gezielt verifiziert. Keine Jenesis-/JPMS-/Versions-
oder Compilersemantikänderung. Historische Originalbelege unverändert.
Kandidaten-SHA wird nach dem Commit extern festgehalten, um keine Selbstreferenz
zu erzeugen; dieser Stand ist noch nicht unabhängig abgenommen (VM-18 offen).
Gesamt-Review folgt erst jetzt nach Kandidatencommit. Ergebnisartefakte verbleiben
extern unter `/Users/stefan/sources/interlis-java-verification-evidence/reviews/`.

Erster Gesamt-Review auf decd97c: FAIL mit vier berechtigten Findings.
R1–R4 korrigiert: aktuelle55 CLI-Fälle/Katalogv4 im Nodevalidator, aktive
P3-Doku, Inventarziele, ungenutzte Projektion-Dependency. Zusätzlich verlorenes
fullCorpusJvm-Ordering am richtigen Taskbesitzer wiederhergestellt.
Gezielte Folgeprüfungen:95 Nodefälle,7 Coverage-Unitfälle,55 echte CLI-Fälle,
9 SDK-Kommandos/Archivconsumer und tatsächliche Produzent-/Validator-Gegenprobe
bestanden; vier Orderingkanten im Dry-run bestätigt. Details im TEST_LEDGER.
Neuer Kandidat wird committet; VM-18 bleibt bis zum frischen Gesamt-Review offen.

Frischer Gesamt-Review auf1bb9aa9: R1–R4 erfüllt, FAIL wegen neuem R5.
R5 nun vollständig korrigiert: fullCorpus-Produzent und alle ursprünglichen
Lauf-/Drucktestreihenfolgen am neuen Besitzer. Drei reale Taskgraphprüfungen,
sauberer echter Vorbereitungsprozess (347/366, kein Korpusbenchmark) und sieben
frisch ausgeführte Coverage-Gegenproben erfolgreich. Normale Testinputs binden
die tatsächlich gelesenen Quellen/Manifeste, damit Datenänderungen die Tests
invalidieren. Die zusätzlich geprüften25 Artefaktvergleichsfälle sind mit ihrer
1bb9aa9-Herkunft dokumentiert. VM-18 bleibt für den neuen Kandidaten offen;
finales unabhängiges Urteil wird extern aufbewahrt, ohne Nachtragscommit.
