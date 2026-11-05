# B0 — tatsächliche Ausgangslage

Stand 05.11.2026. IDs JB-01–03/31/32/37, BUILD-002/003/007,
ARCH-001, WP-00/30. Erwartung: unveränderte Engine kann echte JPMS-Produktion
und enges White-Box-Patching im eigenen gehashten Schritt ausführen.

| Quelle | Baseline | Tree | Arbeitskopie |
|---|---|---|---|
| Core | c0aba8762b5e26b92d38a0147af572f24e52ee20 | 0c21ce375f0f4b83f9b1c3ffcb4146a929cdc6db | `/Users/stefan/sources/interlis-java-jenesis-b` |
| Extension | a5b774a4d593ca5424212c8a733fe816106639f7 | 9527090b2006f386c188a6e626e9009b4dd8e1df | `/Users/stefan/sources/interlis-vscode-jenesis-b` |

Beide neuen Worktrees verwenden `codex/jenesis-migration-b`, initial sauber.
Die ursprüngliche Corekopie war sauber; drei unversionierte `.DS_Store` der
ursprünglichen Extensionkopie bleiben dort unverändert. Keine APE-/Cosmoquellen
übernommen, kein Branchmerge, kein Nativebuild, kein Push oder Release.
Im Extensionrepo existiert keine AGENTS.md; Core-Rootregeln gelten für Core,
die Auftragsregeln ausdrücklich für beide Verbraucherquellen.

## Werkzeuge und Quellen

194 Enginefiles stimmen vollständig mit `build/JENESIS-UPSTREAM.json` überein:
Jenesis0.18.1, Commit177014053cce311924f6724d4e4a7fe5689ed9b6.
Für Ausführung explizit `/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java`;
Shellstandard Java21 ist kein Java25-Nachweis. Shell-Node22.13.0 wird nicht als
gepinntes Node24.22.0-Consumerprofil verwendet. Oracle-GraalVM25.0.3 mit
`org.graalvm.webimage.api.jmod` ist vorhanden, noch kein Backendlauf.
OpenCode2.0.28, Hilfe und vorhandenes Modell `deepseek/deepseek-flash` gelesen;
reiner No-tools-Preflight endet regulär mit PREFLIGHT_OK. Noch kein Review,
keine finale Provider-/1017-Abnahme.

Vollständige unveränderte Quellinventare, Dependencies/Scopes, gepflegte
Testquellen/Hashes, Ressourcen/Grammatiken und Packagebesitz:
extern `B0/source-inventory.json`. Zehn JVM-Projekte, keine eigenen
Produktions-Split-Packages. 196 Task-/Kantenfundstellen und 447 aktive oder
historisch einzuordnende Einstiegstreffer sind mit Datei/Zeile erfasst.
Die beiden Backendadapter bleiben eigene Gradlequellen.
`generateTestGrammarSource` ist ein Orderingrest: kein Testquellensatz enthält
eine `.g4`; Jenesis deaktiviert den Generator für Testmodule bereits ausdrücklich.

## A-Nachweise und Grenzen

Externe Ablage von A bleibt unverändert:
`/Users/stefan/sources/interlis-java-jenesis-evidence/`.
`B0/a-original-report-audit.json` prüft sämtliche im ursprünglichen A4-Plan
referenzierten Rohreporthashes, tatsächliche XML-Fälle, Fehler/Skips und Exits:
1297 Baselineinvocations und1304 Kandidateninvocations. Das ist kein neuer Lauf.
`B0/a-source-comparison.json` vergleicht2550 originale A5-Inputs mit B-Baseline:
acht Differenzen, davon ModuleArtifacts/JvmArtifactReproducibility,
Verification/JvmBuildParity, gitattributes und vier Dokumente. Die übrigen
Produkt-/Testquellen stimmen überein. Spätere A5-Refresh-/Paritätsbelege bleiben
separat; kein vollständiges A-PASS und kein JPMS-PASS aus Classpathbelegen.
Frische kleine Umgebungsreferenz: interlis-model24Tests ohne Fehler/Skips,
eigener Jenesisrun mit originaler Receipt und Rohreports.

Alle hier genannten B0-Dateien liegen unter
`/Users/stefan/sources/interlis-java-jenesis-b-evidence/B0/`.
`evidence-index.json` bindet die kompakten Belegdateien; White-Box-Rohdateien
zusätzlich vollständig in `whitebox-gate.json`. Private Probequellen und
konkrete Prozessargv stehen daneben, nicht in Produktlieferungen.
