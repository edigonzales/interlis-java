# Auftrag A: operativer Status

A0 abgeschlossen: unveränderte Engine praktisch erprobt, frische normale
Gradle-Testbaseline aller zehn Module erfolgreich. Noch kein Produktumbau,
kein unabhängiger fachlicher Review und kein Kandidatencommit.

A0-Nachgate: `final-probe.cjs` besteht Runtime-/Projektconsumer, echte
Parameteridentität, nichtleere Kontrolle/Leerauswahlablehnung und reinen
Erzeugungslauf. Öffentlicher API-Einstieg real ausgeführt (`api.log`).
A1 abgeschlossen: Modellmodul mit strikten Pins und24Tests grün;
Sources-JAR, Discovery, Versions-/Artefaktfehler, Clean/Ignore und
unveränderte136-Task-Gradlegraphen geprüft. A2 abgeschlossen: neun Produktmodule,1218 normale Produktfälle,
ANTLR-Inkremental-/Löschprobe, reale Fixture-/Hookinvalidierung, vier Scopes
und SDK-Example gegen Jenesis-JARs erfolgreich. A3 abgeschlossen:86 normale Verificationfälle,272 frische
Produzenteninvocations und4 Consumer, Source-/Artefakt-/Rawreportbindung,
Integritätsgegenproben, Verification-Cachefolge und Gradledefaults erfolgreich.
A4 abgeschlossen: vollständiger logischer Vergleich1297 ursprünglicher Invocations,
9 Produkt-JAR-/Sources-POM-Verträge, SDK/CLI/LSP und E-01–07 mit echter
unveränderter Extension grün. A5-Erzeugungs-/Gegengates abgeschlossen:81 bytegleiche Lieferartefakte in zwei
unabhängigen Outputs, echter Differenzfehler,136 unveränderte Gradletasks und
Source-/Stage-/Ignoregrenzen bestätigt. Diff-/Stageprüfung erfolgreich; dieser Stand wird als vollständiger lokaler
Kandidat committet. A0–A5 sind abgeschlossen. JA-30–32 bleiben ausdrücklich bis
zum externen Review offen; dessen exakte Commit-/Treebindung liegt ausserhalb
des Produktbaums im Reviewrequest. Kein unabhängiger PASS wird vorweggenommen.
[Ausgangslage](BASELINE.md), [Enginebefunde](CAPABILITY_REPORT.md),
[Testledger](TEST_LEDGER.md). Rohbelege extern unter
`/Users/stefan/sources/interlis-java-jenesis-evidence/a0`.
