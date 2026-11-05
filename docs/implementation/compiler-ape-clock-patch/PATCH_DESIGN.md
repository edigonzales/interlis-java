# Technischer Entwurf bis auf Klassen- und Methodenebene

## 1. Leitregel: ein Fehler, eine Änderung, eine aktive Strategie

Das lokale Verhalten soll nachher dem Java-Vertrag entsprechen, nicht bloss den vorhandenen Test umgehen. Ausschliesslich `currentTimeMillis()J` erhält die Kalenderzeitquelle. Alle anderen Methoden und Native-Image-Prüfungen bleiben unangetastet. Der vorhandene Cosmo-Headerzugriff ist zu verwenden; **keine numerische Clock-ID wie `0` oder `1` hardcodieren**. Cosmopolitan abstrahiert plattformabhängige Konstanten. Der gepinnte Zugriff und dessen C-/CAP-Bindung müssen erhalten bleiben.

Vor der Implementierung zwischen drei Ebenen unterscheiden:

1. Gepinnte heruntergeladene Distribution und deren tatsächliche Builderklassen.
2. Öffentlich gelesener Graal-Quellstand als Erklärungs-/Vergleichsquelle.
3. Neu erstelltes Patchartefakt und daraus gebaute neue native Programme.

Ein Befund über Ebene 2 ersetzt keinen Kompilier- oder Laufnachweis über Ebene 1/3. Der komplette Ablauf verwendet weiterhin Java 25 ohne Preview und die vorhandenen Node-/Java-Helfer; kein Python- oder zusätzlicher Buildframeworkbedarf.

## 2. Binärinspektion — `InspectCosmoClock`

Vorgeschlagener Besitzer: `tools/ape/compat/cosmogvm-clock/inspector/InspectCosmoClock.java`, eigenes kleines Buildtool, kein Produkt-JAR. Unter einem privaten Buildtoolpackage oder als source-launchbare Klasse implementieren. Klassen nicht per `Class.forName()` initialisieren; Classfile strukturiert lesen.

### Datentypen

```java
record InvokeSite(String owner, String name, String descriptor, String opcode) {}
record MethodFacts(String name, String descriptor, int flags,
                   List<InvokeSite> invokes, List<String> stringConstants,
                   String structuralDigest, List<String> annotations) {}
record ClockInspection(String archiveSha256, String classEntry,
                       String classSha256, int classfileMajor,
                       List<MethodFacts> methods, boolean signedArchive) {}
```

Die tatsächliche Umsetzung darf zusätzliche explizite Felder für Parameter-/Typannotationen, Felder, Exceptiontabellen und Klassenmetadaten nutzen. Keine `Map<String,Object>`-Kopie der Compilersemantik und keine neue allgemeine Bytecodebibliothek bauen.

### Methoden

| Methode | Vertrag |
|---|---|
| `main(String[] args)` | Exakte Argumente für verifiziertes JAR, Zieleintrag und frische Ausgabedatei; Exit ungleich null bei Guardfehler; maschinenlesbares Ergebnis, keine PASS-Behauptung allein aus erfolgreichem Lesen. |
| `inspect(Path jar, String entry)` | Alle ZIP/JAR-Einträge auf Doppelung/Mehrdeutigkeit prüfen; genau die Klasse lesen; Signatur-/Manifest-/Multi-Release-Konstellation feststellen. Keine still ignorierte alternative Version derselben Klasse. |
| `inspectClass(byte[] bytes)` | Name, Classfilelevel, Methoden/Descriptor/Flags/Annotationen und Instruktionen lesen; auch syntaktisch ungültige Dateien ablehnen. |
| `requireOriginalClock(ClockInspection data, PatchExpectations expected)` | Exakte gepinnte Klasse; eine passende `currentTimeMillis()J`; genau ein erwarteter Clock-Aufruf in dieser Methode; unveränderte Umrechnung/Statuskontrolle; vorhandenes `nanoTime()J` passend zur erwarteten Originalstruktur. |
| `methodDigest(MethodFacts facts)` | Strukturelle Repräsentation unabhängig von Constant-Pool-Indizes; Opcodes, symbolische Member/Deskriptoren, Konstanten, Kontrollfluss, Handler und wirksame Annotationen nicht wegfiltern. |
| `compareUnchangedMembers(ClockInspection original, ClockInspection changed)` | Ausschliesslich freigegebene Methodendifferenz erlauben; andere Methoden, Felder, Klassensignatur und Annotationen unverändert. |

`PatchExpectations` ist aus der eingecheckten, zuvor verifizierten Patchdefinition zu lesen. Erwartete Originalhashes dürfen bei normaler Ausführung nicht aus dem aktuellen JAR neu „gelernt“ werden.

Das reale JAR wird im verifizierten installierten Vendorinventar gesucht. Nicht einen möglicherweise falschen Pfad zu `svm.jar` aus einer anderen GraalVM-Version voraussetzen. Mehrere Fundstellen sind ein Guardfehler, bis der tatsächliche effektive Builder-Modulpfad eindeutig belegt ist. Nur die tatsächlich geladene Klasse darf als Patchziel dienen.

Für Route A zusätzlich am echten `AnnotationSubstitutionProcessor` beziehungsweise an seiner tatsächlich wirksamen Nachfolgeklasse belegen, wie die Deaktivierungsoption ausgewertet wird. CLI-Hilfe, `javap` und ein minimaler Native-Lauf genügen als schmale Prüfung; keine vollständige Reverse-Engineering-Pipeline.

## 3. Route A — zusätzliche Substitute ohne geänderte Vendorbytes

### 3.1 Zielklasse

```text
guru.interlis.ape.compat.Target_java_lang_System_ApeClock
```

Sie erhält `@TargetClass(value = System.class, onlyWith = <tatsächlicher Cosmo-Guard>)`. Bevorzugt den vorhandenen `CosmoLibCSupplier` nutzen, sofern er im gepinnten Builderkontext korrekt erreichbar ist. Keine kopierte `os.name`-Heuristik: entscheidend ist das Native-Ziel `--libc=cosmo`, nicht das Betriebssystem des Buildprozesses.

Die Klasse enthält genau eine Ersatzmethode:

```java
@Substitute
// Die erforderliche Uninterruptible-Annotation entspricht der gepinnten Runtime.
public static long currentTimeMillis();
```

Diese Signatur ist ein Vertragsskelett, kein fertig kompilierbares Quellfile. Imports, Annotationpackage und optionale Annotationparameter am Vendorbinary feststellen. Insbesondere nicht ungeprüft eine neue `com.oracle.svm.shared`-API in die ältere Toolchain hineinziehen.

**Methodenalgorithmus:** passende native `timespec` auf dem Stack reservieren; über den vorhandenen Cosmo-Headerzugriff `CLOCK_REALTIME()` abfragen; vorhandenen NoTransitions-kompatiblen Clockhelper aufrufen; identische Statusprüfung wie die Originalmethode; Sekunden und Nanosekunden mit den vorhandenen Konstanten zu Millisekunden umrechnen. Fehlerliteral benennt `CLOCK_REALTIME`.

Keine Javaobjektallokation, `Instant.now()`, `Clock.systemUTC()`, JNI-Eigenimplementierung, Reflektion, Logging oder Fallback im korrigierten Methodenrumpf. Nicht über `System.currentTimeMillis()` zurückdelegieren. Keine eigene Zeitkalibrierung, kein statisches Offset, kein Environmentoverride.

Annotationen aus dem tatsächlichen Original übernehmen, wenn sie für StackValue, GC/Safepoints und Aufrufkontext benötigt werden. Nicht durch Abschalten von Builderverifikation versuchen, fehlerhafte Annotationen oder illegale Aufrufe akzeptieren zu lassen. Keine ganze Klasse `@Substitute` markieren.

### 3.2 Auswahl und Konfliktvermeidung

Zuerst exakt die Originalmethode deaktivieren; danach den Ersatz über den von dieser Version unterstützten Substitutionserkennungsweg einbinden. Kandidat der Deaktivierung steht in SPECIFICATION. Keine globale Disable-Liste, keine komplette Targetklasse, keine ungezielte Ausnahme für Java-Systemklassen.

Ob auto-discovery genügt oder explizite Native-Image-Metadaten benötigt werden, am gepinnten Builder prüfen. Nur tatsächlich notwendige Konfiguration ins lokale Patchartefakt aufnehmen. Nicht alle Klassen oder Methoden für Reflection registrieren.

Notwendige `--add-exports`/Modulzugriffe sind einzeln zu begründen, explizit in die Buildidentität aufzunehmen und auf dieses Buildtool zu beschränken. Keine allgemeine Modulpfadänderung der Anwendung; keine grossflächigen `--add-opens ALL-UNNAMED`-Sammlungen.

Ein eigener gleichnamiger `@Substitute` ohne Deaktivierung der vorhandenen Methode gilt nicht als zuverlässige Überschreibung. Ein wirklicher Konflikt muss als Fehler sichtbar bleiben. Der Validator verlangt Ersatzartefakt und Deaktivierungsoption gemeinsam; fehlt eines, wird vor Buildstart abgebrochen.

### 3.3 Kompilierung und JAR

Die Substitute gegen passende verifizierte Vendor-APIs kompilieren. Diese **Compile-only**-Abhängigkeiten werden nicht als weitere Compilerbibliotheken paketiert. Das JAR enthält nur die ausdrücklich erwartete Ersatzklasse und gegebenenfalls minimale notwendige lokale Metadaten, keine kopierten `com.oracle.*`-Klassen oder Builderlibraries.

Reproduzierbare JAR-Erzeugung: feste Eintragsreihenfolge, definierte Zeitstempel, keine Rechnerpfade, keine zufälligen Manifestfelder. Zwei schnelle Patch-JAR-Erzeugungen dürfen zur Determinismuskontrolle genutzt werden; daraus folgt keine neue Forderung nach bytegleichen vollständigen Native-Builds.

JAR-Inventar, Klassenhash, Quellhash, Compilerhash/-version und genaue Argumente im Receipt. Das JAR bleibt auf Buildseite. Seine Wirkung ist in den generierten APE-Bytes enthalten, nicht als zur Laufzeit nachladbare Java-Datei.

## 4. Route B — nur bei erwiesenem Hindernis von A

### 4.1 Entscheidungsbeleg

`RESULT.md`/Testledger hält fest: geprüfte A-Option, tatsächliche Fehlermeldung, relevante Binär-/Moduldiagnose, sachgerecht versuchte begrenzte Korrektur und warum die verbleibende Alternative eine unerlaubte Verifikationsabschwächung oder grössere Portierung benötigen würde.

Nicht beide Wege zur „Sicherheit“ parallel entwickeln. Nach B-Wahl aktive A-Substitute/Disable-Option entfernen; historische Diagnoselogs bleiben erhalten.

### 4.2 `PatchCosmoClockJar`

Vorgeschlagener Besitzer nur für B: `tools/ape/compat/cosmogvm-clock/transformer/PatchCosmoClockJar.java`. Nutzt JDK-25-Class-File API, kein ASM-Download und kein manuelles Byteoffsetpatching. [S10]

| Methode | Vertrag |
|---|---|
| `main(String[] args)` | Exakte Original-/Ziel-/Specpfade, kein Überschreiben, Eingaben und Ergebnis vor/nach Prüfung. |
| `transformTarget(byte[] original, PatchExpectations expected)` | `currentTimeMillis()J` exakt auswählen; einen `INVOKESTATIC` auf den erwarteten Time-Owner, Namen und Descriptor durch den Realtime-Namen ersetzen; nur dessen Fehlerliteral ändern. |
| `verifyDelta(byte[] before, byte[] after)` | Genau ein erlaubter Invoke- und ein erlaubter Fehlertextunterschied; Typen, Kontrollfluss, Handler, Konversionen und Annotationen erhalten; `nanoTime` und andere Mitglieder unverändert. |
| `rewriteJar(Path original, Path output, byte[] target)` | Frische temporäre Datei im isolierten Ziel; ursprüngliche Menge von Eintragsnamen exakt erhalten; alle nicht betroffenen unkomprimierten Eintragsbytes identisch; atomar veröffentlichen erst nach vollständiger Prüfung. |
| `compareArchivePayloads(Path original, Path patched)` | Genau ein abweichender Klasseneintrag; Manifest-/Service-/Modul-/Notice-/sonstige Payloads unverändert; fehlende/zusätzliche Einträge ablehnen. |

Die Class-File API kann den Constant Pool oder Offsets neu darstellen. Nicht deshalb die Prüfung anderer Methoden aufgeben: symbolisch normalisierte Instruktionen und Kontrollfluss vergleichen. Unveränderte Uninterruptible-/Substitute-/onlyWith-Metadaten belegen. Unknown-Attributeverlust ist kein akzeptierter Normalisierungseffekt.

Der Methodenverweis `CLOCK_MONOTONIC` kann im Constant Pool gemeinsam genutzt sein. Deshalb eine **neue passende Referenz für die eine Instruktion** erzeugen, nicht den gemeinsam genutzten Namen verändern. Ebenso Fehlerliteral nur in der Zielmethode ersetzen.

### 4.3 Abgeleitete Toolchain

Originalinstallation schreibgeschützt lassen. Kopie in `target/compiler-ape/compat/<digest>/vendor-derived/`, keine schreibbaren Hardlinks auf Originaldateien. Interne Symlinks sicher auflösen und prüfen. Alle übrigen Dateien und benötigten Rechte unverändert; vollständiger Inventarvergleich erlaubt nur das einzelne gepatchte JAR. JAR-interner Vergleich erlaubt nur den einzelnen Zieleintrag.

Bei Signaturen/Hashes in einem übergeordneten Moduldescriptor oder anderem geschützten Container nicht deren Schutz löschen oder auf „ignore“ stellen. Sauber nutzbare Ableitung nachweisen oder B blockieren. Die Originalinventarprüfung wird nicht gelockert. Eine neue `verifyDerivedInstallation()` prüft zusätzlich Originalbasis, erlaubtes Delta und Ergebnispin.

Nicht das bereits gebaute `native-image`-Launcherprogramm patchen und keinen fertigen Compiler binär verändern. Es ist nachzuweisen, dass der Native-Builder die geänderte Builderklasse tatsächlich konsumiert; dafür die kleine Clockprobe verwenden. Ist der relevante Code unerreichbar vorab in einen nativen Launcher eingebaut, nicht weitere beliebige Stellen patchen.

## 5. Node-Adapter `compat.cjs`

Mit JSDoc-Typen oder gleichwertig klaren Typverträgen implementieren. Die Namen dürfen im Rahmen einer kleineren Umsetzung zusammengeführt werden, Verantwortlichkeiten bleiben unverändert.

```text
readPatchSpec(absoluteSpecPath) -> PatchSpec
inspectVendor(lockFile, spec, runDirectory) -> Promise<VendorInspection>
prepareCompatibility(lockFile, specFile, outputRoot) -> Promise<receiptPath>
verifyCompatibilityReceipt(receiptPath, lockFile, specFile) -> VerifiedCompatibility
compatibilityArguments(verified, inContainerPaths) -> string[]
verifyDerivedInstallation(verifiedOriginal, derivedRoot, receipt) -> EffectiveVendor  [nur B]
```

`readPatchSpec`: Syntax/Schema/Hashes/Strategie/Target prüfen; fehlende Expected-Hashes nicht akzeptieren; nur bekannten Clockfix erlauben. Kein vom Specinhalt beliebig ausführbarer Commandhook.

`inspectVendor`: vorhandene `verifiedInstallation()` benutzen; tatsächliches JAR/Classfile lesen; laufende Original-Builderbytes und Optionen protokollieren. Originalklassenhash strikt mit dem einmal geprüften Pin vergleichen. Inspectorcodehash ebenfalls erfassen.

`prepareCompatibility`: erst Guards, dann nur die gewählte Strategie; vorhandene `record()`-/Hash-Helfer; privater Temp/Run; keine Veröffentlichung vor erfolgreichen Exits, Outputprüfung und erneutem Inputabgleich. Lock/Serialisierung über vorhandenen APE-Einstieg; direkt aufgerufener Helper darf keinen parallelen widersprüchlichen Zustand erzeugen.

`verifyCompatibilityReceipt`: erneut Spec-/Quell-/Producer-/Artefakt-/Vendorhashes vergleichen, erwartete Dateimenge prüfen, ungehashte Zusatzdateien/extern referenzierte Pfade ablehnen. Erfolgsbehauptung aus `status` oder `patchId` allein reicht nicht. Route A Vendorbaum unchanged, B allowed-delta-Verifikation.

`compatibilityArguments`: A liefert die eng gebundenen Deaktivierungs-/Einbindungsargumente, B keine zweite Substitute und keine A-Disable-Option. Keine leere Liste als Fallback bei Fehlern. Alle resultierenden Argumente im effektiven Native-Rezept.

## 6. Neue Receiptform

Das folgende JSON ist ein Schema-/Datenbeispiel, kein erfolgtes Ergebnis. Platzhalter in der tatsächlichen Implementierung durch echte Werte ersetzen, sonst verwerfen:

```json
{
  "schemaVersion": "1.0",
  "kind": "interlis-ape-clock-compatibility",
  "patchId": "cosmogvm-25.2i-r2-current-time-millis-realtime-v1",
  "strategy": "SUBSTITUTION_JAR",
  "patchSpecSha256": "<SHA256>",
  "baseVendor": {
    "lockSha256": "<SHA256>",
    "installedTreeSha256": "<SHA256>",
    "builderJarRelativePath": "<VERIFIZIERTER_RELATIVER_PFAD>",
    "builderJarSha256": "<SHA256>",
    "targetClassSha256": "<SHA256>"
  },
  "target": {
    "className": "com.oracle.svm.core.posix.cosmo.Target_java_lang_System_Cosmo",
    "methodName": "currentTimeMillis",
    "methodDescriptor": "()J",
    "originalClock": "CLOCK_MONOTONIC",
    "replacementClock": "CLOCK_REALTIME"
  },
  "output": {
    "relativePath": "compat.jar",
    "sha256": "<SHA256>",
    "bytes": 0,
    "entries": []
  },
  "sources": [],
  "producerReceipts": [],
  "compatibilityDigest": "<SHA256>"
}
```

`bytes: 0` und leere erforderliche Inventare sind in diesem Beispiel Platzhalter, in einem tatsächlichen Erfolgsreceipt unzulässig. B verwendet ein explizites `derivedToolchain`-Objekt statt eines nur umetikettierten A-JAR-Outputs. Produkt- und Probe-Manifeste referenzieren dieselbe ausgewählte Receiptidentität.

## 7. `RuntimeProbe` — ein kleiner gemeinsamer Laufprüfer

Zusätzlich zu den ursprünglichen Reproducern ein einziges kleines Programm, das ohne INTERLIS- und ohne Builderlibraries läuft. Mehrere Subkommandos im selben Native-Bild vermeiden wiederholte Native-Erzeugung für jede einzelne Frage.

| Methode | Erwartung |
|---|---|
| `main(String[] args)` | Strikte Auswahl `clock`, `selector`, `http`, `certificate`; keine willkürlichen Shellkommandos. |
| `sampleClocks(int samples, long pauseMillis)` | `currentTimeMillis`, `Instant` und `nanoTime` als Rohwerte pro Sample, Haupt-/zweiter Thread; ausgegebene Werte nicht vorab normalisieren. |
| `verifyClockSamples(...)` | Toleranz zu Host-/Referenzintervall, Konsistenz der Wallzeit, positive sinnvolle Differenzen von `nanoTime`; keine absolute `nanoTime`-Epoche verlangen. |
| `reportSelector()` | Tatsächlich ausgewählten Provider und geöffneten Selector feststellen; Propertystring allein zählt nicht. |
| `request(URI uri, Duration connectTimeout, Duration totalTimeout)` | JDK `HttpClient` wie Originalreproducer; Status/Bodyhash/Exceptionursachen; Trust aus normaler isolierter Laufzeitkonfiguration, kein eigener toleranter TrustManager. |
| `checkCertificate(Path pem)` | Zertifikat lesen und `checkValidity()` ohne vorgegebene Fakezeit prüfen; Diagnosemodus kann zusätzlich die explizite aktuelle `Instant`-Zeit vergleichen, ersetzt aber nie die Defaultkontrolle. |

Probeausgaben auf stdout strukturiert und kontrollierte Fehler als tatsächliche Exits. JVM-Kontrolle mit identischer Probequelle. Kleine Runtime-Probe darf andere erreichbare JDK-Teile enthalten als der Compiler; deshalb nach Probeerfolg weiterhin den tatsächlichen Compiler testen.

Der Harness zeichnet Hostzeit unmittelbar vor/nach dem Start mit unabhängiger Hostquelle auf und kontrolliert das Rohintervall. Mindestens mehrere Samples mit kurzer Pause; Wiederholung nach künstlichen Systemzeitsprüngen ist nicht erforderlich und nicht erlaubt.

## 8. TLS- und Harness-Ergänzungen

`fixture-server.cjs` kann bereits anhand des Profilnamens `valid`/`wrong-host` Zertifikate laden. Nur die fehlenden `expired`/`not-yet-valid`-Profile samt erklärten, isolierten Testpins ergänzen. CA-Zertifikat gültig, Leaf-Zeitprofil kontrolliert; SAN/IP passt bei Zeitfällen. Keine neue universelle PKI-Bibliothek.

Vorhandene Zertifikate unverändert wiederverwenden, wenn sie zur aktuellen Hostzeit das beabsichtigte Profil besitzen. Ansonsten bewusst neue öffentliche Testfixtures mit nachvollziehbarer Erzeugung anlegen. Zertifikatslebensdaten vor Teststart prüfen; keine globalen Uhränderungen und kein Erweitern eines Leafdatums nur, um den Runtimefehler unsichtbar zu machen.

`network-probes.cjs` benutzt bisher den primären echten macOS-Host und vergleicht JVM/native. Diesen Weg wiederverwenden. Zeit-/Trust-/Hostname-Negativursachen am Minimalprogramm explizit prüfen, wenn die Produkt-CLI eine absichtlich allgemeinere strukturierte Fehlermeldung liefert. Keine Produktdiagnose ändern, nur damit ein Test einen Stacktrace sieht.

Ein HTTP-Success muss Request/Response belegen. Beim abgelehnten TLS-Zertifikat kann der HTTP-Handler ausbleiben; das ist erwartbar. TLS-Handshakebeobachtung/Exceptions prüfen, nicht einen unmöglichen erfolgreichen HTTP-Request erzwingen.

Loopbackproxy darf nur die tatsächlich benötigten Fixtureziele bedienen. Keine frei nach aussen leitende Testproxyinstanz. Eventuelle dafür nötige Beschränkung ist lokal testbezogen, kein Auftrag für allgemeine Proxyentwicklung.

## 9. Entfernbarkeit

Das Patchprofil akzeptiert nur den alten geprüften Fehlerstand. Ein bereits korrigiertes oder unbekanntes Classfile scheitert laut Guard mit verständlichem Hinweis; keine automatische „Patch nicht mehr nötig, trotzdem PASS“-Ausgabe.

Für einen später korrigierten Vendor ist ein eigener bewusster Pinwechsel vorgesehen: neuen Originalstand ohne lokalen Fix mit denselben Proben qualifizieren, Ersatzklasse/Disable-Option oder Ableitung entfernen, aktuelle Buildidentitäten erneuern, historische Belege erhalten. Clock-/TLS-Regressionen bleiben. Keine dauerhaft leere Compat-Schicht als Legacyadapter zurücklassen.
