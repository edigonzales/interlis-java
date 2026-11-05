# F0 — tatsächliche Baseline

Ausgangscommit `2a2b0272761caa2b65d559c07909970f4a1290ff`, Tree
`a12685c9c2e975cf2d47cbebbdae4833f9b09f32`; initial sauber, keine Fremdänderungen.
Produkt 0.2.0-SNAPSHOT, Jenesis 0.18.1 am unveränderten Upstreampin
177014053cce311924f6724d4e4a7fe5689ed9b6. Explizite JVM:
`/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java` (25.0.3+9).
Shell-Java21 ist keine verwendete Produkttoolchain. Host Darwin24.6.0 arm64.

Auftrag-A-Status im Repo endet vor dem externen Review. Lokal vorhanden:
`/Users/stefan/sources/interlis-java-jenesis-reviews/4c5e133400da4643a33976c19fb7ab7f18d89cfb/round-01/`:
Originalverdict PASS, regulärer Abschluss und Nachvalidierung PASS, OpenCode 2.0.28,
direkte DeepSeek API/Flash1017. Dieser alte Review bleibt an seinem SHA gebunden;
er ist keine APE-Abnahme. Keine A-Volltests wiederholt. Die neue F0-Erzeugung
nutzt `JenesisBridge.run(BuildRequest("artifacts",...))`, tests(false), stage.
Engine entdeckt zehn Module; native Inputs enthalten nur CLI, Compiler, Model,
Workspace, Repository, Projection und ANTLR-Runtime4.13.2. Keine LSP-, Bridge-,
Test-, Gson- oder Buildtool-JARs im Compilerclasspath.

`guru.interlis.cli.Main` verwendet echte native argv ohne Windows-JVM-ENV-Bridge.
Diagnose-/Sprach-/Versionsdaten liegen in Java; ANTLR-Parser sind generiert.
Ressourcensuche ergab keine produktiven ServiceLoader-/Reflectionregistrierungen
auf Verdacht. Unicode-Lizenz liegt als Compilerressource vor. Hostintegration:
CliEnvironment.system (HOME/CWD/Terminal), Repository-Datei-/HTTP/TLS-/Cacheadapter,
atomare CLI-Dateiausgabe. Keine Reachabilitykonfiguration vor F1 eingeführt.
Bestehende kleine Netzwerkfixtures: `tools/verification/http-network-processes.cjs`
und Repository-/CLI-Vertragstests. Neue F0-Modelle unter `testdata/ape/`.

Docker Desktop verfügbar: Linux aarch64, 4 CPUs, 8320294912 Bytes RAM.
Vorhandenes x64-Builderimage:
`sha256:063b17654e59a88128e193fcc8cc4a094d37921b20bbc07eed35aa0e01004aff`,
Ubuntu24.04.5. `docker run --platform linux/amd64` startet x86_64 unter Emulation.
Dies ist Buildhilfe, kein nativer Linux-x64-Zielnachweis. macOS-arm64-Host ist
verfügbar; nativer Windows-x64-/Linux-x64-Runner bisher nicht festgestellt.
Keine Runnerbuchung, kein Push oder neues Konto.

OpenCode v2.0.28 vorhanden; run unterstützt standalone/auto/model/format/file.
`opencode models --standalone` nennt `deepseek/deepseek-flash`.
Offizielle Aliaszuordnung am05.11.2026 geprüft:
https://api-docs.deepseek.com/api_samples/chat_python_v3.1_terminus/
nennt DeepSeek-V4.1-Flash-1017, Endpoint https://api.deepseek.com.
Technische isolierte Authprobe abgeschlossen: PREFLIGHT_OK, keine Produktprüfung.
Session `ses_e55f91932ffeKXJA461rqOfG2U`, tatsächliches Modell
`deepseek-flash`, Provider `deepseek`. Kein fachlicher Review vor dem Commit.
