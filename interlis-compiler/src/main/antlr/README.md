# ANTLR grammar provenance

`InterlisLexer.g4` and `InterlisParser.g4` derive from
[geostandards-ch/interlis-antlr4](https://github.com/geostandards-ch/interlis-antlr4/tree/f8f97238c2540a49915f8bb97ee99bbf434b74da).
The [license](LICENSE) and [notice](NOTICE) accompany the grammar.
The generator and runtime are pinned to ANTLR 4.13.2.

The grammar's start rule does not require EOF. The compiler adapter enforces
full input consumption. The optional `viewDef` block generates ANTLR warning
154. The [syntax contract](../../../../docs/contracts/sources-and-syntax.md)
covers the full-file and recovery checks.
