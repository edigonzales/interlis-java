# Zusammenführung des VS-Code-Corezweigs mit aktuellem main

Auf ausdrücklichen Nutzerauftrag wird main7698c42 mit dem unabhängig V5-geprüften
Core6983baf zusammengeführt. Der Extension-Dokuabschluss30b9d2c enthält weiterhin
unverändert das geprüfte Paar Core6983baf / Extension3a88850; die qualifizierten
VSIXe und deren Lock werden nicht neu gebaut oder auf den Merge umgebunden.
Die ursprüngliche kumulative Abnahme ist kein Review dieses neuen kombinierten Corestands.

Die neueren Cache-/HTTP-Arbeiten auf main bleiben erhalten. Vier additive
Spezifikationsergänzungen wurden zusammengeführt; der eingefrorene LSP-Pfad steht
nun in Kapitel08 §18 neben Cachevertrag §17. RepositoryClosureService verbindet
den unveränderten chronologischen Observer mit eingefrorenem Capture ohne
Live-Ownerzugriff. Die von main entfernte zweite Onlineprüfung bleibt entfernt.
LSP migriert die FetchPolicy auf15s Connect/40s Read/kein Gesamtlimit und reicht
RepositoryCacheRequest inklusive MD5 und Cachefrist durch den Coalescer weiter;
unterschiedliche Requests dürfen keine gemeinsame laufende Antwort erhalten.
Ein neuer paralleler Regressionstest prüft genau diese Grenze.

Gezielte Integrationstests unter Java25.0.3 und dem Python-Ausführungs-Denyguard:
Repository112/112, CLI141/141 und LSP76/76, null Fehler/Skips. Im ersten Lauf waren
Repository und CLI grün; ein LSP-Test erwartete noch ETag-Revalidierungsverkehr.
Seine Warmcache-Prüfung verlangt nun gemäss aktuellem ili2c-Vertrag null HTTP;
der kalte Lauf muss Index und beide transitiven Modelle tatsächlich herunterladen.
Danach gesamte LSP-Suite76/76 grün, Guard und ProzessExit0. Originalfehlversuch,
finale Logs und JUnitberichte liegen unter evidence; keine GUI-Neuinstallation,
kein weiterer Vollkorpus-, Plattform- oder Release-Lauf für diesen Merge.
