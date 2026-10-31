# Deterministische TeaVM-Klassenreihenfolge

Der P3-SDK-Nachweis verlangt zwei unabhängige saubere Builds mit bytegleichen
JavaScript-/Wasm- und Browserdateien. Die echte Gegenprobe am Produkt80326cd
bestand für sämtliche JVM-Dateien und das SDK-ZIP, scheiterte aber an
JavaScript, Wasm und deren Browsermanifest. Die Rohversuche sind unter
`build/reports/p3/P3-5/failed-repro-80326cd/` erhalten.

`TeaVM.ListableClassReaderSourceAdapter` sammelt Klassennamen in0.16.0 mit
`Collectors.toUnmodifiableSet()`. Die JDK-Implementierung dieser ungeordneten
Menge hat keine stabile Iterationsreihenfolge. Die Ausgabereihenfolge
synthetischer Klassen variierte im tatsächlichen JavaScriptvergleich;
Wasm-Funktionsreferenzen und damit die Dateihashes ebenfalls.

`gradle/deterministic-teavm.gradle` ordnet die drei Klassenlisten für
Analyse, Lazy- und Eager-Ausgabe lexikalisch. Die beiden unveränderlichen
Sichten bleiben unveränderlich. Filtern,
Mitgliedschaft und die Identität zurückgegebener Klassen bleiben erhalten.
Die zunächst allein geordnete Analyseansicht beseitigte nicht alle
Byteunterschiede; deshalb werden beide Ausgabepipelines ebenfalls geordnet.
Die Änderung wird aus dem unveränderten [TeaVM-0.16.0-Quellartefakt](https://repo.maven.apache.org/maven2/org/teavm/teavm-core/0.16.0/teavm-core-0.16.0-sources.jar)
abgeleitet; Originaldatei und Apache-2.0-Lizenzkopf bleiben im Build erhalten.
Das Originalprojekt ist [TeaVM](https://github.com/konsoletyper/teavm).

| Eingang | SHA-256 |
|---|---|
| Original-Quellarchiv0.16.0 | `687cabf23e0d17a15be30fbc070809c2931714340aca224120fe31579f95519b` |
| Original-Core-JAR0.16.0 | `c94539912c1b951fa741aecfcb6b4acad75c2e2360dc8859b1edd0c44175608a` |
| Original `org/teavm/vm/TeaVM.java` | `4680ff43f66630e27298c39012e4f94239f294c7647bca54c6dc696192f36cb6` |

Abweichende Eingangsbytes oder mehr/weniger als die drei exakten Ersetzungen
schliessen den Build. Nur `TeaVM` und seine verschachtelten Klassen werden
mit Java25/`--release17` passend zum Originaltarget kompiliert. Alle anderen
Core-JAR-Einträge werden unverändert übernommen; ZIP-Reihenfolge und
Zeitstempel sind deterministisch. Die offiziellen Gradle-Generierungstasks
verwenden dieses Core-JAR ausschliesslich im eigenen Compilerprozess.
Die gepinnte TeaVM-Version und sämtliche Produktions-/SDK-Abhängigkeiten
bleiben erhalten; das korrigierte Tool-JAR wird nicht mit dem Java-SDK geliefert.

`orderedTeaVmCheck` prüft80 Permutationen mit40 Klassen auf Sortierung,
Filterung, Identität und Unveränderlichkeit. Der vollständige Nachweis sind
weiterhin zwei separate saubere Erzeugungen einschliesslich aller tatsächlich
ausgelieferten Bytes und anschliessend alle fachlichen Laufzeitgates.
`build/web/build-manifest.json` liefert Patch-ID, Originalpins, korrigierten
Quellhash und tatsächlich verwendeten Tool-JAR-Hash mit. Weder eine
nachträgliche Byte-Normalisierung der Compilerprodukte noch eine gelockerte
Reproduzierbarkeitserwartung ersetzt diese Prüfung.
