# ANTLR grammar provenance

The files `InterlisLexer.g4` and `InterlisParser.g4` are copied unchanged from
`geostandards-ch/interlis-antlr4` at commit
`f8f97238c2540a49915f8bb97ee99bbf434b74da` (the pinned grammar of the
executed syntax spike, `docs/evidence/wasm-spike`). `LICENSE` and `NOTICE`
belong to that grammar and are kept next to it.

ANTLR generator and runtime are pinned to 4.13.2 in `gradle/libs.versions.toml`.
The grammar's start rule does not require EOF; the adapter enforces full input
consumption. The known ANTLR 154 warning for the optional `viewDef` block is
kept visible and is tracked in `docs/implementation-status.md`.
