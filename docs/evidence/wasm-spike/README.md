# INTERLIS: Java 25 → WebAssembly feasibility spike

Dieser kleine, ausgeführte Versuch prüft den **unveränderten ANTLR4-Parser von geostandards-ch mit modernem Java** auf JVM, TeaVM WebAssembly GC und TeaVM JavaScript. Er ist **kein vollständiger INTERLIS-Compiler** und prüft keine fachliche Modellsemantik.

## Verifiziert am 18. Oktober 2026

Alle im `CompilerSpike.main()` implementierten Prüfungen bestanden auf:

| Ziel | Toolchain |
|---|---|
| JVM | Eclipse Temurin 25.0.4.1+1, Java-Release 25 |
| WebAssembly GC | TeaVM 0.16.0, Node.js 24.19.0 mit `--experimental-wasm-exnref` |
| JavaScript | TeaVM 0.16.0, Node.js 24.19.0 |

Build: Apache Maven 3.9.16; ANTLR-Generator und Runtime **4.13.2**. Die Grammatik stammt unverändert aus [`geostandards-ch/interlis-antlr4`](https://github.com/geostandards-ch/interlis-antlr4/tree/f8f97238c2540a49915f8bb97ee99bbf434b74da), Commit **f8f97238c2540a49915f8bb97ee99bbf434b74da**. `LICENSE` und `NOTICE` im Projektwurzelverzeichnis gehören zu dieser Grammatik und wurden unverändert übernommen.

Der Test verwendet die gesamte generierte Grammatik, nicht eine eigens verkleinerte Beispielgrammatik. Die Testmodelle selbst sind bewusst klein. Getestet wurden:

- gemeinsame Syntax unter INTERLIS 2.3;
- Syntax mit `MULTICOORD` unter INTERLIS 2.4;
- Fehlererkennung und Erhaltung einer Modellprojektion nach fehlendem Semikolon;
- explizite Kontrolle auf vollständig verbrauchte Eingabe;
- Records inklusive `equals`, `hashCode`, `toString`;
- sealed Interface und exhaustiver Switch mit Record Patterns;
- ausgewählte exakte `BigDecimal`-/`BigInteger`-Operationen jenseits von 2^53;
- Unicode-Regex sowie Zeichenketten und Kommentare mit Umlaut und Emoji;
- Umrechnung der ANTLR-Codepoint-Offsets in Java-/LSP-UTF-16-Offsets;
- exportierte Java-Funktion: JavaScript-String hinein, JSON-String zurück.

Die fünf gemeinsamen Ergebniszeilen waren auf allen drei Zielen identisch. JavaScript und Wasm lieferten auch dieselbe zusätzliche Exportantwort. Die beobachteten Outputs, Checksummen und unkomprimierten Artefaktgrössen stehen in `results.json`.

## Reproduzieren

Voraussetzungen: JDK 25, Maven und Node.js. Keine Preview-Features erforderlich. Die Standard-Maven-Netzwerk- und Zertifikatskonfiguration der eigenen Umgebung wird verwendet; dieses Projekt enthält keine Proxy-, Konto- oder Credential-Konfiguration.

```sh
mvn -B -ntp package

# JVM; unter Windows den Classpath-Separator ; verwenden.
java -cp "target/classes:$HOME/.m2/repository/org/antlr/antlr4-runtime/4.13.2/antlr4-runtime-4.13.2.jar" spike.CompilerSpike

# WasmGC: Runtime und .wasm werden direkt lokal durch Node geladen.
node --experimental-wasm-exnref run-node.cjs wasm

# JavaScript-Backend
node run-node.cjs js
```

Bei abweichendem lokalem Maven-Repository den JVM-Classpath anpassen. Die Maven-Konfiguration erzeugt `target/wasm/interlis.wasm`, `target/wasm/classes.wasm-runtime.js` und `target/js/interlis.js`. Die Loader-Datei heisst `classes.wasm-runtime.js`, weil der separate Maven-Copy-Goal diesen Standardnamen verwendet. Eine Webserver- oder Servlet-Installation ist für die Node-Prüfung nicht erforderlich.

`run-node.cjs` führt zuerst die internen Prüfungen aus und ruft dann zusätzlich die mit `@JSExport` exportierte Java-Methode auf. Bei einer fehlgeschlagenen Prüfung endet der jeweilige Aufruf mit einem Fehler.

## Grenzen und offene Arbeit

**Geprüft ist die technische Tragfähigkeit dieser Kombination für den getesteten Pfad.** Nicht geprüft sind vollständige INTERLIS-Konformität, Imports/Repositoryauflösung, Binding, Typprüfung, Übersetzung, Metamodellvollständigkeit, Transfereigenschaften, ili2c-Kompatibilität, speichersparende Langzeit-Sessions, inkrementelle Semantik, grosse echte Modellbestände und Browserläufe in Chrome/Firefox/Safari. Es wurden keine belastbaren Performancebenchmarks durchgeführt.

Die übernommene Grammatik ist laut Upstream auf INTERLIS 2.4 ausgerichtet. Ihre Startregel akzeptiert ein allgemeines Dezimaltoken als Version und enthält keine 2.3/2.4-Freigabelogik. Der erfolgreiche 2.3-Test bestätigt daher nur dieses Beispiel. Die Startregel enthält auch kein `EOF`; der Java-Adapter prüft den Vollverbrauch deshalb selbst. Die Grammatik wurde nicht korrigiert, um den Portierbarkeitsversuch von einer fachlichen Weiterentwicklung zu trennen.

ANTLR meldete beim Generieren eine Warnung zu einem optionalen Block in `viewDef`, dessen Alternative leer sein kann (Warnung 154, Parsergrammatik Zeile 517). Lexer- und Parserfehler werden im Adapter beide eingesammelt. Die kleine JSON-Antwort ist nur eine Modellnamen-/Themenanzahl-Projektion, kein TransferDescription-Ersatz. Die Java-Methode `compile` führt in diesem Versuch ausschliesslich Syntaxanalyse aus.

## Folgerung für die Zielarchitektur

Ein Java-25-Kern mit Records, sealed Hierarchien, Pattern Matching, ANTLR4 und ausgewählten exakten Zahloperationen muss nicht vorsorglich auf Java-8-Techniken reduziert werden. Plattformzugriff sollte in Adaptern bleiben. Für das Zielprojekt: SourceBuffer hinein; immutable Syntax-/Semantik-Snapshots und strukturierte Diagnosen heraus; Dateisystem, HTTP, LSP-Transport und UI ausserhalb. Die `@JSExport`-Annotation dieses Ein-Datei-Versuchs gehört im eigentlichen Projekt in einen separaten TeaVM-Adapter.

## GraalVM Web Image: recherchiert, hier nicht ausgeführt

Offizielle aktuelle Dokumentation beschreibt Web Image als experimentelles Native-Image-Backend ab **Oracle GraalVM 25.1**, mit **Binaryen >=119**:

```sh
native-image --tool:svm-wasm MainClass
```

Es erzeugt ein WasmGC-Modul mit JavaScript-Wrapper und benötigt Wasm-Garbage-Collection, Exception-Handling und Typed Function References. Das Ergebnis ist derzeit kein alleinstehendes WASI-Modul für `wasmtime`. Der Web-Image-Pfad wurde in diesem Versuch nicht gebaut; es war keine entsprechende Toolchain vorhanden. Native Image für native Betriebssystem-Executables und GraalWasm als Wasm-Laufzeit sind davon zu unterscheiden.

Primärquellen:

- [Web Image](https://www.graalvm.org/latest/reference-manual/web-image/)
- [Web Image API](https://www.graalvm.org/latest/reference-manual/web-image/api-guide/)
- [Graal-Quellstand](https://github.com/oracle/graal/blob/c40ade41d719578019e26b44a24c0c1c28f70ef1/web-image/README.md)
- [Offizielles javac-in-Wasm-Beispiel](https://github.com/graalvm/graalvm-demos/tree/master/web-image/javac)
- [TeaVM 0.16.0 Release](https://github.com/konsoletyper/teavm/releases/tag/0.16.0)
- [TeaVM Getting started](https://teavm.org/docs/intro/getting-started.html)
- [TeaVM 0.13: Java 25 und WasmGC-Coroutinen](https://teavm.org/docs/release-notes/0.13.0.html)
- [TeaVM 0.14: Entfernung der alten non-GC-/WASI-Backends](https://teavm.org/docs/release-notes/0.14.0.html)
- [TeaVM Java-Klassenbibliothek](https://teavm.org/docs/runtime/java-classes.html)
- [TeaVM WasmGC Loader](https://teavm.org/docs/wasm-gc-backend/loader.html)

## Dateiumfang

Enthalten sind nur Quellcode, Buildkonfiguration, die gepinnte Grammatik samt ihren Lizenzhinweisen, lesbare Testfixtures, der Node-Runner und Ergebnisdaten. JDK/Maven-Binaries, Maven-Caches, lokale Einstellungen und erzeugte Buildartefakte sind nicht enthalten. Die Fixtures spiegeln die in `CompilerSpike.java` eingebetteten Modelle wider; die automatischen Prüfungen verwenden die eingebetteten Texte.
