# Clock-Patch — operativer Stand

Baseline: `075ae52b659cbd8b4f8232787aaa80b4ac539a75`, Tree
`5cd890ad5de79a33d2c815b2e05a2ba86c6189c3`, bestehender APE-Branch,
initial sauber. Keine Fremdänderungen. Die neue Auftragsspezifikation ist bereits
in der Baseline enthalten; kein Reset auf den früheren Referenzcommit.

C0 bestanden (CP-01–03/09–11/30): ursprüngliche Archive und komplette Installation
verifiziert; genau ein Ziel-Classfile in den tatsächlichen Builder-JARs; Option
`-H:DisableSubstitution` und Auswahl per annotierter Klasse/Methode im echten
Prozessor nachgewiesen. Original-Clockfehler durch gehashte historische Rohreceipts
und unveränderte Probequelle bestätigt. Sieben echte Jenesis-JARs mit aktuellem
Java-Eingabevalidator akzeptiert. Mac arm64 verfügbar, Linux x64 emuliert;
zusätzliche native Linux-/Windowsrunner nicht nachgewiesen.

C1 auf Nutzerwunsch abgebrochen: Route A, separates Build-only-Substitutions-JAR. Noch kein korrigiertes
Native- oder Compiler-PASS. Toleranz für Clockproben vorab10s; Native-Probeheap3GiB,
2Threads,1200s intern/1350s äusseres Limit. Compilerlimits unverändert.

Belege: `/Users/stefan/sources/interlis-java-clock-patch-evidence`.
OpenCode2.0.28 und aktuelle offizielle direkte DeepSeek-Flash1017-Zuordnung geprüft;
technische Authprobe `TECHNICAL_PREFLIGHT_OK`, kein fachlicher Review gestartet.

Abbruch auf ausdrücklichen Nutzerwunsch am05.11.2026: eigener laufender
Nativecontainer entfernt (Exit0); danach keine weiteren Tests oder Reviews.
C0 bestanden; C1 nicht abgeschlossen, C2–C5 nicht begonnen.
Abbruchbeleg: `c1/user-abort.json`.

Auf nachfolgenden ausdrücklichen Nutzerauftrag wird dieser unfertige Stand als
abgebrochener Versuch lokal committet. Die Quellen sind ein Zwischenstand,
keine freigegebene Patchimplementierung. Kein C5-Abschluss, keine weiteren
Tests, Builds oder Reviews; Originalbelege bleiben extern erhalten.
