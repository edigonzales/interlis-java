# Browserclient

Die Demo analysiert und kompiliert den Modelltext mit TeaVM-WasmGC in
einem Worker. Ein gültiges Ergebnis lässt sich als Diagramm anzeigen.
Der JavaScript-Backendpfad und GraalVM Web Image dienen als weitere
Laufzeitpfade. Die [Bridge](../contracts/bridge-protocol.md) ist die
gemeinsame Protokollgrenze.

Mit den [Buildvoraussetzungen](../development/build.md) und den beiden
Werten aus dem aktuellen Wasm-Export starten.

```sh
java build/InterlisJvmBuild.java wasm --profile browser-prepare \
  --inputs "$JVM_INPUTS" --inputs-sha256 "$JVM_INPUTS_SHA256"
jwebserver -p 8080 -d interlis-wasm-teavm/build/web
```

Öffne [die lokale Demo](http://127.0.0.1:8080/) über HTTP, damit Worker,
ES-Module und Wasm zusammen geladen werden. Der TypeScript-Client besitzt
Sitzung und Transport. Er unterstützt Modelltexte, Analyse, Compile,
revisionstreue Abfragen und Diagramme. Fremde Modelle beschafft der Host
nach expliziter Policy. Die Bridge führt keine versteckten Abrufe aus.
Der [Buildvertrag](../development/build.md#portable-backends) beschreibt die direkten Browserprüfungen.
