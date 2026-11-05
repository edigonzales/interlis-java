# Operativer Stand

F0 und F1 abgeschlossen; Details in BASELINE.md, TOOLCHAIN_REPORT.md und
TEST_LEDGER.md. Originale Fehlversuche bleiben erhalten.

F2 abgeschlossen: echte Jenesis-Compilerclosure für beide Architekturen gebaut,
Originalbilder unverändert gelassen, fixup-Kopien tatsächlich mit apelink fusioniert.
Die identische Datei `db9775eb7cf3270c7c55c48c41441a159bddaabd1c2a854356df99423fb83408`
besteht je 16 feste Kernfälle auf echtem macOS arm64 und emuliertem Linux x64.
Beide verwenden deklarierte mitgelieferte Loader. Das ist kein nativer
Linux-/Windowsnachweis und keine vollständige Netzwerk-/Portabilitätsfreigabe.

Bekannter zusätzlicher Baselinefehler: `TEXT*;` verursacht auf JVM und Native
Exit 3 statt der erwarteten Syntaxablehnung. Originalcase bleibt FAILED und
unverändert gesichert; der feste Syntaxgatefall verwendet den bereits bestehenden
TRAILING-Vertrag. Kein Produktfix oder Wegnormalisieren dieses Fehlers.

F3 nicht bestanden; terminaler F5-Abschluss: NO_GO. Der zunächst nachgewiesene
EPoll-Fehler lässt sich durch Standard-Providerwahl beim Imagebau beheben,
ist also kein terminaler Blocker. Der anschliessende reine Java-TLS-Reproducer
legt einen unabhängigen Vendorfehler offen: System.currentTimeMillis liest
CLOCK_MONOTONIC statt Wallzeit und lässt sichere Zertifikatsprüfung scheitern.
Unverändertes Vendorbytecode und direkte Clockprobe sind Originalnachweise.
Eine eigene JDK-/GraalVM-Substitution wäre nach Abschnitt2 ausserhalb des Scopes.

Lokale K01–09/F01–06/H01–05 sind am ursprünglichen Compilerartefakt belegt;
JVM-Netzwerkbaseline17 plus beschädigter Offlinecache bestehen. Native N01 ist
FAILED; weitere native Netzwerk- und F4-Pflichtgates NOT_RUN_BLOCKED. Linuxx64-
Emulation wird nicht als nativer Linuxnachweis ausgegeben; Windows fehlt.
Bewusst abgebrochene/äusserlich getimte Neubauversuche bleiben ohne PASS.

F5: Report-/Input-/Artefakt-/Prozessgegenproben gezielt geprüft; Entscheidung,
Befehle, Lieferinventar und Grenzen dokumentiert. Kein Push oder Release.
Unabhängiger Gesamt-Review folgt erst auf dem geprüften lokalen Commit;
Reviewurteil bleibt bis dahin offen und wird extern aufbewahrt.

Rohbelegroot: `/Users/stefan/sources/interlis-java-ape-evidence`.
