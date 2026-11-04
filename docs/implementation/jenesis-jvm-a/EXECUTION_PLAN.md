# Ausführungsplan — Auftrag A

## Ablauf und Bedeutung der Gates

**A0 → A1 → A2 → A3 → A4 → A5 → R.** Codex implementiert und prüft A0–A5 selbst. Jede Phase endet mit einem tatsächlichen erfolgreichen Gate für ihren Umfang. Kein unabhängiger Review zwischen den Implementierungsphasen. Erst der geprüfte, committete Gesamtkandidat geht an OpenCode/DeepSeek.

Die Phasengates sind eine Abfolge nachgewiesener Eigenschaften, keine Aufforderung, an jeder Grenze dieselbe vollständige Testsuite zu starten. Bereits ausgeführte, weiterhin gültige Nachweise werden nach `TEST_AND_EVIDENCE_POLICY.md` wiederverwendet. Änderungen an gemeinsamen Inputs können frühere Teilgates wieder öffnen; nur deren betroffene Aussagen sind erneut nachzuweisen.

Vor jeder Phase kurz festhalten: betroffene JA-Kriterien, geänderte Eingaben, erwartete Beobachtung und kleinster ausreichender Testumfang. Nach jeder Phase: tatsächliche Kommandos, Exits, Umfang, Belegpfade und eventuelle Einschränkungen. Ein geplanter Test ist kein Ergebnis.

## A0 — Ausgangslage und Enginefähigkeiten prüfen

### Ziel

Einen verlässlichen Referenzstand, eine präzise Build-/Testinventur und einen kleinen praktischen Nachweis schaffen, dass das gepinnte Jenesis die erforderliche POM-/Classpath-Arbeitsweise unterstützt. Keine Produktmigration allein aufgrund von Dokumentationsbeispielen beginnen.

### Arbeitsschritte

1. Repository-/Branchzustand erfassen, geltende Arbeitsregeln lesen, fremde Änderungen erhalten. Tatsächlichen `BASE_SHA` und Tree dokumentieren. Die Spezifikationsreferenz `84fe455…` ist kein Reset-Ziel.
2. Lokale Verification-Abnahmebelege nur dann als vorhandene Belege anführen, wenn sie wirklich gelesen und dem betreffenden Stand zugeordnet wurden. Ein in GitHub offener Status wird weder als endgültiger Fehler noch als erfundener PASS interpretiert.
3. Eine separate saubere Gradle-Referenzkopie und einen nachvollziehbaren Kandidatenarbeitsstand vorbereiten. Keine parallelen Läufe in dieselben Outputverzeichnisse schreiben lassen.
4. `settings.gradle`, alle zehn JVM-Builddateien, gemeinsame Konventionen, ANTLR, Test-/Contractaufgaben, Startskripte, Ressourcen und SDK-Struktur vollständig inventarisieren. Besondere Aufmerksamkeit gilt `gradle/verification-contracts.gradle`, `VerificationLayout`, `CompilerCoverageContractTest` und den kürzlich korrigierten externen Testinputs.
5. Effektive Main-/Test-/Tool-Closures durch Gradle auslesen, einschliesslich tatsächlicher transitiver Versionen und JAR-Hashes. Einen kurzlebigen ausschliesslich lesenden Inventaradapter verwenden, falls nötig; kein zweites dauerhaftes Gradle-Pluginframework entwickeln.
6. Tatsächliche JDK-, Node-, Wrapper- und Bibliotheksversionen festhalten. Der Enginebootstrap verwendet dasselbe geeignete JDK-Profil. Produktpins bleiben unverändert.
7. Gradle-Testinventare aus realen aktuellen Berichten gewinnen. Vorhandene Rohbelege dürfen nach Input-/Toolchainprüfung verwendet werden. Fehlen sie, normale JVM-Tests gezielt je Modul ausführen — nicht den Root-`releaseCheck`, keine Browser-/Wasm-/Performanceprofile. Die vollständige Gradle-Normaltestbaseline muss spätestens für A4 vorliegen; ihre Erzeugung darf über A0–A3 verteilt werden, ohne Tests mehrfach auszuführen.
8. Die aktuelle Contractauswahl mit Methoden und Parameterinvocations gesondert erfassen. Normale Tests und die explizite Auswahl überlappen bewusst; sie dürfen nicht zu einer künstlichen Zahl einzigartiger Produktfälle zusammengezählt werden.
9. In einem isolierten Probeprojekt das unveränderte Jenesis-Release 0.18.1 am festgehaltenen Commit prüfen. Vorhandene offizielle Beispiele des Releases nutzen, aber nicht den Produktauftrag durch eine vollständige Migration dieses Probeprojekts ersetzen.
10. OpenCode-Version, CLI-Hilfe, Zugang zum direkten Provider und Modellzuordnung technisch vorprüfen. **Noch keinen fachlichen Reviewer starten.**
11. Den echten Extension-Verbraucher inventarisieren: vorhandenes `interlis-vscode`-Checkout mit SHA/Tree/Dirtyzustand, `stage-core.cjs`, `prepare-dev.cjs`, `src/host/launch.ts`, Runtime-/Lockschema, vorhandene gezielte Extension-Host-Tests. Dokumentiere die getrennten CLI-Module-Path- und LSP-Classpath-Startweisen.
12. Eine isolierte Verbraucherkopie, einen vorhandenen geeigneten VS-Code-/Codium-Testhost und Runtime-/Node-Voraussetzungen gemäss `EDITOR_COMPATIBILITY.md` feststellen. Fehlende Tools früh als konkrete Voraussetzungen lösen; keine globale Installation, kein Runtimeupgrade und kein stilles Umschalten des Nutzereditors. Noch keine APE-/CosmoGVM-Toolchain beschaffen.

### Capability-Proben

In kleinen Fällen praktisch nachweisen:

- POM-Multiprojektauflösung auf dem Classpath mit Main/Test, vorhandene package-private Testzugriffe;
- BOM-/Runtime-/Testscope und genaue Toolpins;
- ANTLR 4.13.2 mit Visitor und gemeinsamem Lexer-/Parservokabular;
- echte JUnit-Normal-/Methoden-/Tagauswahl einschliesslich eines gemischten Tests mit getaggter und ungetaggter Methode;
- Fehler, leere Auswahl und fehlender erwarteter Test werden nicht grün;
- `repo.root`/weitere Properties erreichen die Test-JVM;
- tatsächlicher Reporttyp, Parameteridentitäten und geeigneter Engineergebniszugriff;
- mindestens ein relevanter externer Fixturefingerprint beeinflusst den tatsächlichen Testcache;
- Main-/Test-/Artefakterzeugung und reine Wiederholungserzeugung sind über unterstützte Engineaufrufe steuerbar.

Nicht jede Probe braucht ein eigenes Projekt. Ein kleines nachvollziehbares Multiprojektfixture kann mehrere Eigenschaften prüfen. Geeignete unveränderte Upstream-Tests sind ergänzende Belege, ersetzen aber nicht den Integrationsnachweis für die benötigte Konfiguration.

### Gate A0

`BASELINE.md` enthält den aktuellen Scope und die Abhängigkeits-/Quellinventare; `CAPABILITY_REPORT.md` enthält tatsächlich bestätigte APIs/Properties/Flags für den gepinnten Enginecommit. Noch offene vollständige Referenztestinventare sind explizit für A4 eingeplant. Zwingende fehlende Enginefähigkeit ist ein konkreter Blocker, kein Anlass für eine versteckte Gradle-Delegation.

Kriterien: JA-01–05, JA-12, JA-36; Vorbereitung JA-10/13/15/30/33–35.

## A1 — Engine, POMs und sichere Koexistenz

### Ziel

Die neue Buildspur ist fest gepinnt, entdeckt ausschliesslich den gewünschten JVM-Verbund und kann gefahrlos neben Gradle bestehen.

### Implementierung

- Root-/Kind-POMs für die zehn Projekte anlegen. DependencyManagement und tatsächliche Scopes gemäss Baseline, keine wirkungslosen Maven-Pluginaufträge als Ersatz für Jenesis-Konfiguration.
- Engine unverändert übernehmen, Herkunft/Treehash und Lizenz festhalten.
- Kleine Version-/Scopevorprüfung in `JvmBuildInputs` und versionsgebundene Enginekapselung in `JenesisBridge` anlegen. Noch keine grosse Orchestrierung auf Verdacht.
- `InterlisJvmBuild` mit korrekter Argumentbehandlung, Rootauflösung und Fehlerweitergabe einführen. Bekannte eingeschränkte Auswahl muss als solche im Ergebnis stehen.
- Maven-Layout, Discoveryausschlüsse und getrennte Targetwurzeln einrichten. Historische POMs und beide Wasm-Module dürfen nicht versehentlich bauen.
- Root-Clean und `.gitignore` gezielt absichern. Die vollständige bisherige Root-Löschmenge ersetzen, nicht eine neue zusätzliche Löschoperation neben der gefährlichen alten registrieren.
- Prozesskonfigurationen auf First-match-/Overrideverhalten prüfen. Allgemeine javac-/Testoptionen dürfen durch Modulspezialisierung nicht verloren gehen.
- Engine-/ANTLR-/JUnit-/Buildtool-Pins kontrolliert erzeugen und strict prüfen; keinen beweglichen Tooldefault übernehmen.

### Gezielte Prüfung

1. `interlis-model` wirklich mit Jenesis kompilieren, JAR/Sources-JAR und vorhandene Tests erzeugen. Engineclasspath und tatsächlich verwendete Optionen erfassen.
2. Dependency-/Discoveryinventar: genau zehn Produkt-/Verifikationsprojekte, nicht die beiden Wasm-Backends; Aggregator und Engine separat ausweisen.
3. Clean-/Ignoreprobe in wegwerfbarer Kopie: versionierte Quellhashes vor/nach, generierte Sentineldateien werden entfernt, Quellen bleiben erhalten. Ebenso korrekt ignorierte Outputs und tatsächlich trackbare Engine-/Adapterquellen prüfen.
4. Fehlerproben: unbekanntes Modul/Profil, Produktversionsdrift, fehlendes eigenes Artefakt. Keine erfolgreiche leere Auswahl und kein Fallback auf alte eigene SNAPSHOTs.
5. Bestehende Gradle-Konfiguration und ein enger unveränderter Modulbuild müssen weiterhin funktionieren. Kritische Rootgraphen dürfen durch den Clean-Fix keine neuen Abhängigkeiten erhalten.

### Gate A1

Engine-/Scope-/Clean-Sicherheit bestätigt; normales Modellmodul unter Jenesis grün. Alle POM-Deklarationen sind vorhanden und plausibel auflösbar. Die restlichen Produktmodule müssen zu diesem Zeitpunkt noch nicht bereits vollständig getestet sein. Discoveryausschlüsse dürfen die bestehenden `editorBundle`-/Node-Ressourcen nicht löschen, und der Clean-Fix verändert keine F5-/Stagingargumente.

Kriterien: JA-01–07, JA-12, JA-24, JA-25.

## A2 — Produktmodule, Generator und gewöhnliche Tests

### Ziel

Die neun JVM-Produkt-/Bridgemodule bauen mit unverändertem Quellcode und unveränderten Dependencies. `interlis-verification` folgt mit seinen besonderen Inputs und Contractkonsumenten in A3.

### Implementierung

- Abhängigkeitsreihenfolge aus dem tatsächlichen Graphen nehmen: Modell → Compiler → Workspace/Projection → Repository/Documentation/Bridge → CLI/LSP; keine starre Reihenfolge behaupten, wo parallele Zweige möglich sind.
- ANTLR-Ausgabe nur unter der Jenesis-Targetwurzel. Gleichzeitige Tokenvokabular-/Grammarinputs, Package, Visitor-/Listeneroptionen und Tool-Runtime-Isolation übernehmen.
- Normale Java-/Ressourcen-/Sources-JAR-Verarbeitung mit vorhandenen Lizenzen und Schemata herstellen.
- Testproperties und Dateiresourcen korrekt übergeben. Vorhandene White-Box-Tests unverändert auf dem Classpath ausführen.
- `JvmBuildInputs.snapshot` auf die tatsächlich gelesenen externen modulbezogenen Fixtures und Testquellenbindungen ausdehnen. Nicht mit einer globalen zufälligen Run-ID jedes Mal sämtliche Tests erzwingen.
- Engineinventar mit tatsächlich benutzten Classpaths und JARs erfassen. Keine Heuristik „erste/neueste passende Datei“.

### Gezielte Prüfung

- Je migriertem Modul zunächst Compile/JAR und dessen konkrete Generator-/Ressourcen-/White-Box-Risiken testen.
- Für den Compiler Lexer-/Parseränderung und Grammatikentfernung ausschliesslich in einem separaten Fixture bzw. einer Scratchkopie prüfen. Originalgrammatiken bleiben unverändert.
- Die normalen Modul-JVM-Tests sukzessive wirklich ausführen; ihre Ergebnisse zählen zum vollständigen A4-Nachweis und werden dort bei identischem Inputstand nicht erneut gestartet.
- Zwischen effektiven Gradle-/Jenesis-Scopes und aufgelösten Fremdartefakten vergleichen. ANTLR-Tool/JUnit/Testhelper fehlen im Produktruntimeinventar.
- Mindestens eine echte Property-/Ressourcenprobe und ein relevanter Drei-Schritt-Cacheversuch. Die endgültige gesamte externe Inputmatrix wird in A3/A4 vervollständigt.
- SDK-Javabeispiel gegen gebaute Jenesis-JARs kompilieren und starten, soweit bereits ohne Distributionsaufbau möglich.

### Gate A2

Alle neun Produktmodule kompilieren und paketieren; die für diese Phase ausgewählten echten Tests bestehen. Alle bis hier entstandenen Artefakt-/Dependencyabweichungen sind erklärt oder behoben. Ein ausstehender normaler Test muss spätestens in A4 laufen und wird nicht als bereits bestanden gezählt.

Kriterien: JA-03–05, JA-08–13, JA-19/20/25.

## A3 — Verification, Compiler-Contracts und belastbare Reports

### Ziel

Das neue Verifikationsmodul funktioniert unter Jenesis mit gewöhnlichen Tests und ausdrücklich getrennten aktuellen Compiler-Contracts. Seine Nachweise stammen aus echten Jenesisläufen.

### Implementierung

- Verifikations-POM und effektive Main-/Testscopes vollständig herstellen; CLI bleibt eine Testdependency. Kein Verifikationsartefakt in Produkte aufnehmen.
- `VerificationLayout` um ausdrücklich gewähltes Jenesislayout erweitern. Ohne Zusatzkonfiguration bleibt das Gradleverhalten gleich. Gehashte Layoutdatei aus dem echten Ergebnisinventar erzeugen; keine fremden absoluten Pfade übernehmen.
- Die für Auftrag A benötigten `HostChecks`-/SDK-/Contractlesestellen anbinden. Nicht unaufgefordert alle historischen oder P3-Wasm-Reportkonsumenten umschreiben.
- `TestReportInventory` mit echten Native-Reports und minimalem eindeutigem Identitätsmapping implementieren. Bei erforderlicher Zusatzidentität nur eine kleine unterstützte JUnit-Listeneranbindung, keine zweite Testengine.
- `CompilerContractRun` implementieren: aktuelle Auswahl → vorbereitete Norminputs → selektierte echte Produzenten → gehashte rungebundene Reports → explizite Consumer.
- Layout-/Reportschnittstelle zu `CompilerCoverageContractTest.validateExecutedAssertions`, `JUnitReportAssertions` und Normpfaden anpassen, nicht die fachlichen Assertions lockern.
- Externe Inputmatrix, aktuelle Build-/POM-/Enginebindungen sowie Java-/Node-Sourcefingerprintparität vervollständigen. Historische Dokumente und Rohbelege bleiben unverändert.

### Gezielte Prüfung

1. Sämtliche normalen Verifikationstests unter Jenesis ausführen, insbesondere ungetaggte Negativtests in den Contractklassen. Getaggte Methoden dürfen dabei nicht beiläufig mitlaufen.
2. Einen kompletten aktuellen `contracts`-Aufruf ausführen: ausgewählte Producerinvocations frisch, anschliessend die getaggten Consumer. Bereits kompilierte identische Klassen müssen nicht neu kompiliert werden.
3. Positive Kontrolle vor negativen Mutationen. Fehlende/zusätzliche Parameterinvocation, leere Auswahl, fremder Run/Tree, verändertes XML, ungültiger Pfad, unvollständige Summen und verlorener Fehler müssen scheitern.
4. Native- zu Vergleichsreport: Identität und Vollständigkeit prüfen; Rohberichte unverändert erhalten. Keine inhaltliche Zeile nur aus einem Erwartungsmanifest erzeugen.
5. Fixture-Invalidierung tatsächlich beobachten: Baseline ausgeführt, unverändert wiederverwendet, nur relevante externe Datei geändert → betroffener Test neu. Andere unveränderte Modulnachweise nicht pauschal invalidieren.
6. Eine passende ursprüngliche Gradle-Defaultprobe nach Layoutänderung ausführen. Falls Gradle-Contractleser betroffen sind, die ausgewählten ursprünglichen Contracts einmal prüfen, statt den ganzen P3-Build zu starten.

### Gate A3

Normal- und Contractprofile sind disjunkt ausgewählt und jeweils nicht leer. Producer-/Consumer-Zuordnung, Rohreportprojektion und Pfadisolation bestehen positive und negative Prüfungen. Keine alten Gradleberichte als Jenesisnachweis. Relevante Gradledefaults bleiben funktional.

Kriterien: JA-10–17, JA-19/22/24/25.

## A4 — JVM-Buildparität und bestehende Distributionen

### Ziel

Der vollständige JVM-Test- und Artefaktumfang von Auftrag A ist nachgewiesen. Vorhandene CLI-, LSP- und SDK-Formen werden durch die neue Spur produziert und tatsächlich benutzt.

### Implementierung

- `ExistingJvmDistribution` umsetzen: Launcher aus nachvollziehbaren versionierten Baseline-Texttemplates, vollständige korrekte Runtimeclosure, vorhandene Dateinamen/Modulnamen, Unixrechte, bestehendes SDK-ZIP.
- Die aus Gradle bekannte Windowsargument-/Unicodebehandlung und Modulpfadstartersemantik der **Compiler-CLI** erhalten. Der **LSP in der Extension bleibt Classpath-basiert**. Keine neue jpackage-/Native-Variante.
- `JvmBuildParity` mit Modul-/Scope-/Artefakt-/Testinventaren fertigstellen. Kleine erwartete Builder-Metadatenunterschiede konkret dokumentieren; keine pauschale Ausschlussliste für ganze Manifest-/META-INF-Bereiche.
- Entwicklerkommandos und schmale Modultestfilter abschliessen. Eingeschränkte Läufe tragen ihren Umfang und dürfen keinen vollständigen PASS liefern.
- Die schmale Editorprobe nach `EDITOR_COMPATIBILITY.md` ergänzen. Eine vorhandene Assemblyfunktion bevorzugen; kein dauerhafter zweiter Extensionbuilder. `editor-check` wird nur explizit aufgerufen und verändert keine regulären Core-/Extensionausgaben.
- Das bestehende normale Runtimeinventar der Compiler-CLI vollständig ausweisen. Keine APE-Codepfade, keine native Toolchain, keine neuen JPMS-Testzugriffe.

### Gezielte Prüfung

- Alle vorhandenen normalen JVM-Testidentitäten der zehn Module müssen nun mindestens einmal gegen den relevanten Jenesisstand erfolgreich vorliegen. Noch nicht abgedeckte Module/Tests ausführen; gültige A1–A3-Ergebnisse nicht routinemässig wiederholen.
- Gradlebaseline und Jenesiskandidat logisch vergleichen: Tests einschliesslich Parameterfälle, Dependencies und Toolgrenzen, Klassen-/Ressourcen-/Sourcesinventare, öffentliche API und vorhandene SDK-Kommandos. Neue Adaptertests getrennt zählen.
- Echte CLI-/SDK-Prozesse gegen Jenesisprodukte, inklusive negativem Modell/Bedienfehler, stdout/stderr, Exitcodes und der relevanten Unicode-/Argumentfälle des Hosts.
- SDK entpacken und einen separaten Consumer ohne Gradleprodukte im Classpath starten. Archivlayout, Rechte und enthaltene JARs prüfen.
- Echter LSP-Prozess mit initialize/initialized/shutdown/exit und gültigem Framing; keine Startmeldung auf stdout.
- Separat den bisherigen `stage:core`-/F5-Vorbereitungsvertrag in einer isolierten Extensionkopie tatsächlich ausführen; dessen nötiger Gradle-Aufruf wird ausdrücklich als **Referenzverbraucher**, nicht Jenesisproduzent bilanziert. Sauberen Zwischencommit oder vorhandenen ehrlichen Development-Modus verwenden; niemals einen Clean-Releasezustand vortäuschen.
- Schattenbundle mit identischen relevanten Nichtserverressourcen und ausschliesslich neuen Jenesis-LSP-JARs erzeugen. Neues vollständiges Testlock aus den tatsächlichen Bytes, Original-Release-Lock unverändert. Die realen Extension-Launch-/Inventarfunktionen benutzen, nicht nachprogrammieren.
- Ein echter isolierter Extension-Host-Smoke auf dem verfügbaren Entwicklungs-Host: Aktivierung/initialize, deterministische Diagnose für eine kleine lokale Datei, echte Definition/Navigation, Stop/Restart und erneute Diagnose. Keine gesamte interaktive GUI-/UML-/Multiplattformprüfung verlangen. Nur ein Protokollprozess ohne Extension-Host erfüllt diesen Teil nicht.
- Gültige positive Kontrolle, dann fehlende/veränderte/zusätzliche Server-JAR in ausschliesslich der Schattenkopie: vorhandener Extensionvalidator muss vor dem Serverstart ablehnen. Test und Belege an tatsächlich geladene Jenesis-JARs binden.
- Unter `EDITOR_COMPATIBILITY_REPORT.md` Core-/Extension-SHA, ursprüngliche Dirtyzustände, Renderer-/Server-/Lock-/Runtimehashes, echte Hostversion, Proben und Abnahmegrenzen dokumentieren. Kein Nachweis einer neu veröffentlichten VSIX.
- Build-/Test-/Generator-/Verification-Leaks in CLI-/LSP-/SDK-Artefakten negativ prüfen.
- Prozessclasspath und Herkunft kontrollieren; bei unzugänglichen Gradle-Ausgaben muss der neue JVM-Build seine Aufgabe erfüllen können. Keine globale Cachelöschung erforderlich.

Plattformgrenze: Auf dem tatsächlich verfügbaren Host ausführen. Bestehende launcherbezogene Windows-/Linuxverträge strukturell und mit passenden verfügbaren Tests prüfen; eine nicht ausgeführte andere Plattform nicht behaupten. Auftrag A ersetzt keine vollständige neue Multi-OS-Distributionsfreigabe. Veränderte plattformspezifische Launchersemantik wäre nicht mehr blosse Buildparität und ist ohne deren passenden Nachweis nicht freizugeben.

### Gate A4

`PARITY_REPORT.md` weist vollständige normale JVM-Testparität, die ausdrücklichen Contracts und kleine echte Integrationen aus. Jede Differenz ist entweder zulässige erklärte Builder-Metadatenvariation, nachvollziehbarer neuer Adaptertest oder ein behobener Fehler. Keine offenen verlorenen Testfälle oder geänderten Produktdependencies. Beide Editor-Nachweisrichtungen und ihr echter kleiner Verbraucherlauf sind erfolgreich, ohne Extensionquellen oder Release-Locks dauerhaft zu verändern.

Kriterien: JA-09–20, JA-22/23/27, JA-33–36.

## A5 — Wiederholbarkeit, Abschlussbereinigung und Kandidatencommit

### Ziel

Die JVM-Spur ist wiederholbar, parallel benutzbar und dokumentiert. Der gesamte Auftrag liegt als tatsächlich geprüfter Commit vor.

### Arbeit und Prüfungen

1. Unter demselben definierten Toolchain-/Hostprofil zwei unabhängige saubere Jenesis-Erzeugungen der vorgesehenen JVM-Lieferartefakte vergleichen. Keine Wiederverwendung generierter Produkte/Buildcacheausgaben; offizielle Dependencycaches sind zulässig.
2. Bereits vorhandene saubere Erzeugung aus A4 als ersten Lauf verwenden, wenn ihre vollständigen Inputs, Cachebedingungen und Produkte belegbar passen. Den zweiten Lauf auf Erzeugung und Vergleich begrenzen, soweit nicht ein neues Risiko echte weitere Tests erfordert.
3. Unterschiede in produktreproduzierbarkeitspflichtigen Bytes müssen das Projektgate tatsächlich scheitern lassen. Eine reine Jenesis-Diagnose mit Exit 0 reicht nicht.
4. Clean-/Ignore-Schutz, Discoverygrenzen und Delta der öffentlichen Stageinventare abschliessend prüfen. Einzelne bereits unverändert geprüfte Gegenproben dürfen mit Begründung wiederverwendet werden.
5. Bestehende Gradle-Rootkommandos und Taskgraphen kontrollieren; insbesondere kein unbeabsichtigter neuer Browser-/Wasm-/Contractlauf beim normalen JVM-Aufruf. Dry-run als Graphnachweis ausweisen, nicht als Produktlauf.
6. Alle aktiven Aufrufbeispiele und Arbeitsregeln konsistent aktualisieren: zwei JVM-Buildbeschreibungen vorübergehend, keine bereits erfolgte Gesamtmigration oder Pflicht zur früheren B-Reihenfolge behaupten. Compiler-APE kommt nur als separat beauftragbares nächstes Ziel, JPMS bleibt unabhängig; der LSP behält JVM/Extensionruntime.
7. `STATUS.md`, `BASELINE.md`, `CAPABILITY_REPORT.md`, `PARITY_REPORT.md`, `TEST_LEDGER.md`, `EDITOR_COMPATIBILITY_REPORT.md` vervollständigen. Kein künstlich grüner Eintrag für nicht fällige oder blockierte Profile.
8. `git diff --check`, kumulativen Diff, Quell-/Abhängigkeitsgrenzen und staged Inhalte prüfen. Nur Auftragsänderungen committen. Keine Secrets, Rohlogmassen, Tempworktrees oder Fremdänderungen.
9. Finalen Kandidatencommit, Tree und Ausgangs-SHA extern festhalten. Vorcommitbelege behalten ihre ursprüngliche Herkunft; ihre Zuordnung zum Kandidaten erfolgt über nachgewiesene unveränderte relevante Inhalte.

### Gate A5

JA-01–29 und JA-33–36 sind für den Implementierungsumfang erfüllt; JA-30–32 stehen ausdrücklich noch aus. Der Kandidat ist committet. Keine Behauptung einer unabhängigen Abnahme vor R.

## R — Ein abschliessender unabhängiger Gesamt-Review

Jetzt den in `OPENCODE_REVIEW_PROMPT.md` beschriebenen Prozess autonom starten. Exakter committeter Kandidat, frische Sitzung, OpenCode 2.x, direkter DeepSeek-Provider mit nachgewiesener Flash-1017-Zuordnung.

Der Reviewer prüft sämtliche JA-Kriterien, den kumulativen Diff, die ursprünglichen Rohbelege und eigene gezielte Gegenproben. Er ist kein blosses Testwiederholungsprogramm. Umfangreiche erfolgreiche Testläufe des unveränderten relevanten Stands werden nicht nochmals durchgeführt, nur weil die prüfende Instanz wechselt.

Bei berechtigten Findings:

1. Betroffenen Vertrag/Inputs und Verbraucher identifizieren.
2. Gegenprobe und kleinste passende Korrektur.
3. Nur erforderliche Tests gemäss Impactmatrix erneut ausführen; unveränderte Belege offen wiederverwenden.
4. Betroffenes Teilgate schliessen; nicht automatisch bei A0 neu beginnen.
5. Neuen Kandidaten committen.
6. Frischen unabhängigen **Gesamt-Review** des neuen Kandidaten starten, mit früheren Findings und deren Behandlung.

Ein Review-PASS für einen früheren Commit gilt nicht für den neuen. Ein frischer Gesamt-Review verlangt aber keine frische komplette Testsuite.

## Ergebnisgrenzen

Nicht fällige Wasm-/Browsermatrix-/Performance-/Mehrplattform-/P3-/APE-/JPMS-Profile bleiben `NOT_IN_SCOPE`. Der schmale Extension-Host-Smoke ist hingegen ausdrücklich fällig; ihn nicht als „Browserprüfung ausserhalb des Scopes“ überspringen. Ein notwendiger, aber fehlender JVM-Nachweis bleibt `BLOCKED`. Beides darf nicht vermischt werden.

Erfolgreicher Abschluss erst nach gültigem unabhängigen PASS und unverändertem finalen Kandidaten. Finale Reviewdateien extern belassen; keinen nachgeschobenen ungeprüften Dokumentationscommit allein zum Ablegen des Urteils erzeugen.
