# L4-1 – zwei ergänzende Profilfragen

Status: **USER_DECIDED**, tatsächliche Antworten vom30.10.2026:
„Auch äussere Typnamen strikt sperren“ und
„URI-Referenzen inklusive relativ erhalten (empfohlen)“.
Die folgenden Absätze erhalten die vorgelegten Alternativen; gewählt sind
die strikte äussere Namensprüfung und URI-Referenzen einschliesslich relativ.
Die bereits bestätigten Fälle bleiben gültig.
[Vollständige gepinnte Normabsätze](L4-1-profile-questions.json).

**Äussere Typnamen überdecken:**2.3 §2.5.4 /2.4 §3.5.4 beschreiben die
Übernahme der Namenskategorien des übergeordneten Modellierungselements und
verbieten Kollisionen mit übernommenen Namen. Unser bisheriger Compiler und
ili2c akzeptieren jedoch etwa `DOMAIN X=TEXT; TOPIC T=CLASS X=END X; END T;`.
Die eigenen Referenzproben mit gültigem Herausgeber-URI sind erfolgreich.
Empfehlung: bestehende lokale Überdeckung wieili2c zulassen, qualifizierte
Zielidentitäten getrennt halten. Doppelte Typnamen im selben Deklarationsraum
bleiben unzulässig. Alternative: auch äussere Namen strikt sperren.

**Herausgeber nach AT:** Die Norm verlangt einen URI und verweist aufRFC2396;
die allgemeine URI-Familie ist auf1023Zeichen begrenzt. ili2c verlangt beim
Herausgeber einen absoluten URI und lehnt `AT "x"` ab. Unser bisheriger
Compiler erhält auch relative Werte. RFC2396 beschreibt absolute und relative
Formen in derselben allgemeinen URI-Syntax
([Primärquelle](https://www.rfc-editor.org/rfc/rfc2396.html)). Empfehlung:
URI-Referenzen einschliesslich relativer Werte erhalten; syntaktische Defekte
(etwa Leerzeichen oder kaputte Prozentkodierung), unkodierte Nicht-ASCII-Zeichen
und überlange Werte gezielt abweisen. Die Prüfung erfolgt rein syntaktisch
ohne Hostauflösung. Alternative: wieili2c nur absolute Herausgeber-URIs zulassen.

Auswirkung auf den unveränderten historischen Corpus: Die konkrete Quelle
`AGI/SO_AGI_Metadata_20230304.ili` (SHA-256
`1dd1c4fbfcabfb9966478e51b062178bf22a36206210126c1ae263ea3807200d`)
deklariert eine modellweite Struktur und eine lokale Klasse namens FileFormat.
Die gewählte strikte Regel verlangt deshalb auch dort eine Ablehnung; sie wird
nicht für den Benchmark abgeschwächt. `testdata/g2/corpus-expectations.json`
hält exakt diesen Ausgang mit Code, Severity, Phase, MessageKey, UTF-16-Bereich,
Argumenten und Multiplizität fest. Eine separat gepinnte Kontrollkopie benennt
nur die lokale Klasse in PublishedFileFormat um. Öffentliche JVM-Assertions
prüfen deren tatsächliche Basisstruktur und geerbte Member bei Fresh/Reuse.
Das neue Messprofil enthält elf erfolgreiche Compiles und zwei streng
geprüfte Ablehnungen; die Formatierung bleibt für alle13 erfolgreich. Original-,
Index-, Seed- und Suitepins werden nicht geändert. Dies ist die konkrete
Umsetzung der bereits gewählten Regel, keine zusätzliche Kompatibilitätsfreigabe.

Der vollständige13-Originale-Abgleich (`corpus-all-outcomes.json`) erfasst auch
`AWJF/SO_AWJF_Gesuchsteller_20210202.ili`: Thema und Klasse heissen beide
Gesuchsteller. Auch dieses unveränderte Original bleibt nach derselben gewählten
Regel abgelehnt. Die separate Kontrolle benennt nur die Klasse in Applicant um;
ihre tatsächlichen fünf geordneten Attribute werden bei Fresh/Reuse geprüft.
Beide Fälle mit sämtlichen Pins/Diagnosefeldern stehen im gemeinsamen kleinen
Corpusprofil. Die übrigen elf Originale kompilieren unverändert erfolgreich.
