# Gezielte Testmatrix — keine pauschalen Vollsuiten

## 1. Auswahlregel

Diese Matrix benennt die kleinsten verbindlichen Prüffragen für den neuen Clock-Fix. Bestehende Fälle aus `compiler-ape-feasibility/TEST_MATRIX.md` und deren realen Harnesses wiederverwenden. Kein zweites Compiler-Testframework und keine Neuimplementierung des CLI-Paritätsvergleichs.

Fallzustände: `PASSED`, `FAILED`, `NOT_RUN_BLOCKED`, `NOT_APPLICABLE`. Letzteres nur für die nicht gewählte Patchroute oder tatsächlich nicht betroffene optionale Ziele, niemals für fehlende zugesagte Pflichtplattformen. Ein erwarteter Fehlerfall besteht nur bei überprüfter richtiger Fehlerursache.

## 2. Guards und Struktur — G01 bis G10

Diese Tests benötigen überwiegend keine Native-Erzeugung. Mutationen erfolgen auf privaten Kopien.

| ID | Prüffrage / erwartete Beobachtung | Bezug |
|---|---|---|
| G01 | Richtiger Vendorlock/-baum und genaues erwartetes Ziel-Classfile werden akzeptiert; alle ursprünglichen Pins stimmen. | CP-02–03 |
| G02 | Ein geänderter Archiv-/JAR-/Klassenhash oder eine geänderte Patchquelle wird ohne passenden, bewusst geprüften Erwartungspin abgelehnt. | CP-09–10 |
| G03 | Keine Zielmethode, falscher Descriptor, mehrere Clock-Aufrufe oder mehrdeutige Multi-Release-/JAR-Einträge: Abbruch vor Build. | CP-03, CP-09 |
| G04 | Bereits korrigierter Clock-Aufruf bzw. doppelte Patchanwendung wird erkannt und nicht erneut still gepatcht. | CP-09, CP-29 |
| G05 | Unveränderte `nanoTime`-Instruktionen/Annotationen, andere Methoden, Felder und Klasse; keine numerische Clock-ID. | CP-04–06 |
| G06 | Route A: fehlendes Ersatz-JAR, zu breite/falsche Deaktivierung, fehlender Cosmo-Guard oder falsches Inventar scheitert. | CP-07, CP-09 |
| G07 | Route B: nur erwarteter Methodendelta und Fehlertext zulässig; Mutation an `nanoTime`, anderer Klasse oder zusätzlicher Datei scheitert. | CP-04–08 |
| G08 | Route B: Originalinstallation unverändert; Ableitung ausserhalb Original; Signaturen nicht entfernt; fremde Symlinks/Hardlinkmutation ausgeschlossen. | CP-02, CP-08, CP-14 |
| G09 | Patch-JAR nur erwartete Klasse/Metadaten; keine Produkt-/SVM-/LSP-/Testlibraries hineinkopiert; schneller zweiter Build reproduziert Patch-JAR. | CP-10–11 |
| G10 | Falsche Strategie oder stiller Fallback wird abgelehnt; aktive Quellen enthalten nur gewählten Weg. | CP-08, CP-28 |

G06/G09 sind A-spezifisch, G07/G08 B-spezifisch. Nicht beide Strategien implementieren, um alle Zeilen ausführbar zu machen. Durch Codeinspektion plus relevante Tests muss die Nichtanwendbarkeit nachvollziehbar sein.

## 3. Runtime-Minimalprofil — RT-01 bis RT-11

Eine kleine kombinierte RuntimeProbe pro relevanter Architekturkonfiguration bauen, dann wiederholt mit unterschiedlichen Modi/Fixtures ausführen. Keine neue Native-Erzeugung pro Zertifikatfall.

| ID | Prüfung | Erfolgskriterium |
|---|---|---|
| RT-01 | Originale Clock-Negativkontrolle | Unveränderte defekte Runtime reproduziert grobe Diskrepanz; vorhandene gebundene Originalbelege können den Lauf ersetzen. |
| RT-02 | Korrigierte Wallzeit | Mehrere Samples stimmen innerhalb erklärter Toleranz mit `Instant` und unabhängigem Hostzeitintervall überein; keine Daten nahe 1970. |
| RT-03 | `nanoTime` | Differenzen über kurze Wartephase plausibel und nicht rückwärts; kein Epochenvergleich, kein Anspruch strikter Steigerung jedes sofortigen Aufrufs. |
| RT-04 | Thread und Neuausführung | Zweiter Thread und frischer Prozess liefern aktuelle Kalenderzeit, keinen gespeicherten Build-/Startwert. |
| RT-05 | Tatsächlicher Selector | Provider-/Selectorinstanz entspricht der geprüften Poll-Wahl; kein Beleg allein aus gesetzter Property. |
| RT-06 | HTTP | Loopbackserver sieht echten Request; erwarteter Status und Bodyhash stimmen; kein Cache-/JVM-Fallback. |
| RT-07 | TLS positiv | Gültige SAN/CA/Leaf-Zeit; isolierter Truststore; echter erfolgreicher Request/Response. |
| RT-08 | CA / Host negativ | Unbekannte CA und falscher Host werden jeweils am erwarteten Trust-/Namensvertrag abgelehnt, nicht an Clock oder Transport. |
| RT-09 | Zeit negativ | Gültige CA mit abgelaufenem bzw. noch nicht gültigem Leaf: erwartete Expired-/NotYetValid-Ursache; direkte Zertifikatskontrolle ergänzt den Handshake. |
| RT-10 | TLS positiv danach / Standardtrust | Positiver Kontrollfall bleibt erfolgreich; ohne Testtrust ist Test-CA nicht akzeptiert; keine Test-CA in Standardimage. |
| RT-11 | Öffentlicher HTTPS-Smoke | Kleiner erlaubter öffentlicher Zugriff mit normalem Trust; Umweltfehler getrennt protokollieren, keine zufällige Wiederholung bis grün. |

Clocktoleranz vorab festlegen, z.B. höchstens wenige Sekunden bis zehn Sekunden zur konservativen robusten Kontrolle; grosse Abweichungen niemals akzeptieren. Zeitpunkt vor/nach Prozessstart statt eines beim Build eingebrannten Datums. Wallzeit ist nicht monoton spezifiziert. Eine zufällige Hostuhrkorrektur untersuchen, nicht sofort als Produktfehler umklassifizieren.

Bei TLS müssen CA, EKU und SAN der Zeitfixtures korrekt sein. Ein „future“-Leaf mit zugleich falschem Host ist kein sauberer Zeitfall. JVM/Hostprüfer bestätigt Zertifikatsprofil vor Native-Vergleich. Keine Systemuhr verstellen; keine Trust-all-Konfiguration.

## 4. Integration und Provenienz — M01 bis M07

| ID | Gegenprobe | Erwartung |
|---|---|---|
| M01 | `--compat` fehlt/ist relativ/fremd/Schema falsch | Parser bzw. Receiptverifikation verweigert vor Native-Build. |
| M02 | Patch-JAR/abgeleitetes JAR nach Vorbereitung verändert | Neue Prüfung scheitert trotz vorhandenem Erfolgsreceipt. |
| M03 | Mutation mit angepasstem eigenen Hash, aber unpassender Spec/Producerbasis | Herkunfts-/Policyabgleich erkennt Widerspruch; keine reine Selbstbescheinigung. |
| M04 | x64/ARM mit anderem Patchdigest, anderer Strategie oder fehlendem Patch | `readSlice`/Fusion lehnt ab, bevor fixup/apelink läuft. |
| M05 | Geänderte/zusätzliche Runtime-JAR oder Patch-JAR in Produktclosure | Unveränderte sieben Produktinputs werden durchgesetzt. |
| M06 | Falscher Binaryhash, fehlender Case, fremder TLS-Bericht, generisches Error statt passender Ursache | Nachweisprüfung lehnt unvollständigen oder falschen Erfolg ab. |
| M07 | Normaler JVM-/inspect-Pfad ohne installierte native Toolchain sowie begrenzter Timeoutfehler | Keine native Pflicht; nur eigene gestartete Container werden bereinigt; Timeout bleibt FAILED. |

Für M03 keine kryptografische Unmöglichkeit behaupten: alle lokalen Trustanker samt Ursprung zu manipulieren liegt ausserhalb dieser einfachen Manifestkette. Geprüft wird, dass ein beliebig neu gehashtes Artefakt nicht ohne passende gebundene Herkunft/erlaubten Delta durchrutscht.

## 5. Echte Compilerintegration — bestehende Fälle statt neuer Vollsuite

Auf dem finalen neuen APE mindestens die vorhandenen kleinen K01–K09 und F01–F06 prüfen, soweit die tatsächlich aktuellen Harnesses diese Verträge so benennen. Bei Namens-/Fallzahländerungen eine vollständige Zuordnung erstellen, nicht pauschal annehmen.

Dazu zählen insbesondere `version`/Capabilities, gültiges/ungültiges Modell, Importe/Diagnosen, JSON, Diagrammausgabe und Formatierungsmodi mit Schutz der Originaldatei. Grosszahlen, Unicode, Reihenfolgen und Exitcodes nicht anders normalisieren. Der bekannte unabhängige JVM-Syntaxfehler bleibt ein bekannter Baselinefall, kein durch diesen Patch zu reparierendes Produktproblem.

Das bestehende `network-probes.cjs` führt im Referenzstand diese 18 gezielten Varianten: N01; N02-offline/-miss/-corrupt; N03; N04-untrusted/-host; N03-after; N06-http/-connect/-direct; N07; N08-progress/-total; N09-redirect/-missing/-corrupt; N05. Die tatsächliche Implementierung zählt, nicht eine frei reduzierte neue Liste. [S4]

Für den neuen Kandidaten die lokalen Varianten ausführen. Zwei neue benannte Fälle N04-expired und N04-not-yet-valid ergänzen, wenn der bestehende Produktharness sie mit denselben Fixtures ausdrücken kann. Deren erwartete Ursache ist jedenfalls am Minimalprogramm zwingend zu prüfen; der Compiler darf seine vorhandene strukturierte Fehlerabstraktion behalten.

N05/public separat behandeln: kein Internetzugriff ist nicht gleich Runtimefehler. Ein vollständiger Netzwerkanspruch bleibt ohne entsprechenden Nachweis offen. Offlinefälle brauchen serverseitig beobachtete Null-Netzaktivität und geeignete Cachezustände; „kein Body empfangen“ allein genügt nicht.

## 6. Zielplattformmatrix

| Ziel | Mindestumfang des korrigierten Artefakts | Nicht als Ersatz akzeptiert |
|---|---|---|
| macOS arm64 (primärer verfügbarer Host) | RT-Profil, kleine komplette CLI-/Netzwerkmatrix, finaler Bundle-/Loader-/Relokationsstart ohne JVM/Builder, Dateiidentität vor/nach | Nur erneuter JVM-Lauf oder alte ungepatchte APE-Belege |
| Linux x64 nativ | Clock-/Poll-/HTTP/TLS-Smoke mit positiv/negativ, kleiner Compilercheck+Import, Proxy/Timeoutgrundfall, Start ohne JVM, identischer Universalhash | Docker-x64-Emulation auf Mac oder bloss erfolgreiche Linkerzeugung |
| Windows x64 nativ | Entsprechender kleiner Runtime-/Compiler-/Netzwerkumfang, Unicodepfade/Argumente, tatsächlicher unterstützter Startweg, identischer Universalhash | WSL, Wine oder Linux-Ausführung |

Andere Plattformen optional, nicht still neuer Scope. Ein vorhandener Runner darf genutzt werden, eine neue kostenpflichtige/unerlaubte Infrastruktur nicht. Fehlende Ziele im Endbericht explizit. Native macOS-Runtimeplus kann für `patchStatus=VALIDATED_ON_AVAILABLE_HOSTS` reichen; für plattformübergreifendes `compilerApeStatus=GO` müssen sämtliche geforderten echten Zielnachweise vorliegen.

## 7. Abschlusschecks

Keine normale Build-/Test-/Extensionoperation hat eine native Pflicht erhalten. Mitgeführte Loader-/Dateimenge und Notices passen zum finalen Bundle; keine Substitutions-/Builder-JARs als versteckte Laufzeitvoraussetzung. `git diff --check`, Dokumentverweise, aktive Altlastensuche, eindeutige gewählte Strategie und Entfernungshinweise prüfen.

Zuerst gezielt testen, dann geprüften Kandidaten committen, dann independent Review. Die vollständige Testauswahl und jeder bewusste Nichtlauf gehören ins Testledger.
