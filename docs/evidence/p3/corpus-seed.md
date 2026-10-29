# Unveränderte Corpus-Eingaben für L0B-3

`corpus-seed.zip` erhält **genau** den schon in `geo-corpus.json` gepinnten
Index und die 13 Quelldateien; keine Auswahl, Modellversion oder Prüfsumme wurde
geändert. Der aktuelle Live-Download besitzt inzwischen einen anderen Index
und andere Bytes für zwei HBA-Modelle. Diese neueren Dateien werden ausdrücklich
nicht anstelle der gepinnten Eingaben akzeptiert.

Die fehlenden Originale wurden am 29.10.2026 aus der offiziellen Veröffentlichung
[`sogis/interlis-repository:1.2.1817`](https://hub.docker.com/r/sogis/interlis-repository/tags?name=1.2.1817)
vom 16.10.2026 wiederhergestellt. Linux/arm64-Manifest:
`sha256:29c906322b091c2eccaefc3e69855bc08292c6d3b649f8d2d5d11c13985ef3ba`.
Original-Indexlayer:
`sha256:3878f4209a78f13a555bf019d0d58a04c0086966436c5ca54299ccfa79b2fe82`.
Original-HBA-Layer:
`sha256:f927f16fef6eca41334dd8aa0a544be7492c2cb5dbb9ae802631a59a8d1818a6`.
Die heruntergeladenen Layer wurden gegen ihre OCI-Digests geprüft; extrahierte
Dateien stimmen exakt mit allen bestehenden SHA-256-/Bytepins überein.
Die übrigen elf Originaldateien wurden von ihren bestehenden offiziellen URLs
bezogen und ebenfalls exakt gegen die bisherigen Pins geprüft.

Das ZIP enthält flache Namen (`/` → `__`) plus `ilimodels.xml`, ohne zusätzliche
Dateien. Java prüft Eintragsmenge, alle Originalhashes, Indexmetadaten und
Dateigrössen; fehlende Cachedateien werden daraus kopiert. Vorhandene falsche
Cachebytes führen weiterhin zum Fehler. Der Corpus ist damit offline
reproduzierbar; die Original-URLs bleiben Provenienz.

Cache: `~/.cache/interlis-java/geo-corpus`; `GEO_CORPUS_CACHE` überschreibt ihn,
sonst bleibt auch das bisherige `CONFORMANCE_CACHE` unterstützt. Relative Pfade
beziehen sich auf das Repository. Die Node-Benchmarks verwenden denselben Cache.
Das Archiv ist ein Originalinput-Beleg, keine neue fachliche Abnahme und keine
Erweiterung des historischen 13-Modell-Corpus.
