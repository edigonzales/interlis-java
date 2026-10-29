# Pinned compiler conformance suite

This directory records the external INTERLIS compiler conformance run.

| Item | Value |
|---|---|
| Suite | `interlis-compiler-testsuite` |
| Source | `https://codeberg.org/edigonzales/interlis-compiler-testsuite` |
| Commit | `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` |
| Archive SHA-256 | `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` |
| Selection | INTERLIS 2.3 and 2.4 only (`^(ili23|ili24)\.`) |
| Excluded | INTERLIS 1.0, with the suite's own language field |
| Runner | pinned `InterlisConformance.java` from the suite archive |
| Compiler | `interlis-cli check` |

Reproduce:

```sh
./gradlew :interlis-cli:installDist
./gradlew conformanceCheck
```

Since L0B-1, Gradle invokes the unchanged pinned `InterlisConformance.java`
directly. `gradle/conformance.gradle` verifies the cached archive before every
extraction, creates the same run configuration and preserves the raw runner exit.
The CLI test helper `NormativeConformance` only assesses the 19 explicit project
exceptions and writes the separate project report; it does not run or classify
the suite. Its negative contracts and the 19 pinned portable originals are JUnit
tests behind `conformanceWrapperTest`. This path no longer calls Python.
The results below remain historical; current delivery evidence is in
[L0B-1](../../g2/implementation/L0B-1.md).

L1-2 retains the original 18 entries unchanged and adds the strict
`ili23.Attribute23Test.attributeComplete` CIRCULAR case. The original source
is pinned and rejected with exactly two semantic diagnostics; its positive
control only adds CIRCULAR to the two continuous subdivisions. This follows
the CIRCULAR rule retained in the user's approved numeric profile. The
[L1-2 report](../../g2/implementation/L1-2.md) records the scope and independent
review; no separate explicit user approval of that fixture name is claimed.

The task is deliberately separate from `check` and `releaseCheck`: the
external suite is large, network-dependent on first use, and its baseline is a
compatibility observation, not a P1 release gate. The full runner report stays
in `build/conformance`; only the compact summary and a log tail are committed
here.

The complete failure list is in `backlog.json`; the human-readable clustering is
in `backlog.md`. `ROLE_CARDINALITY_DEFAULT` is now modeled together with the
association/role validation bundle. Activating the default alone had produced
false accepts; the bundle now rejects missing names, duplicate role names,
additional roles in extended associations, multiple aggregation roles, invalid
composition cardinalities and role namespace conflicts.

`baseline.json` contains exactly one justified XFAIL entry for
`ili23.Attribute10Test.attributeComplete` (INTERLIS 1 transfer, out of scope).
Every further deviation must be justified before it is added; a historical
ili2c XFAIL is not a baseline for this compiler.

## Current result (2026-10-18)

- Selected: **593** of 610 suite cases (INTERLIS 2.3/2.4).
- Excluded: **17** INTERLIS 1.0 cases, by language version.
- Result: **592 PASS, 1 XFAIL, 0 FAIL, 0 ERROR, 0 XPASS**.
- Translation: **36/36 Single-Feature-Accept-Fälle PASS**, **0** False Accepts in Translation-Reject-Fällen (`translation-summary.json`).
- Overall: **PASS**. The only XFAIL
  (`ili23.Attribute10Test.attributeComplete`) requires `TRANSFER INTERLIS1;`
  and `TABLE` syntax. **INTERLIS 1 is out of scope** by decision dated
  2026-10-19 (`scope-decision-interlis1.md`); this fixture is tracked in
  `baseline.json` but is not an acceptance criterion for the 2.3/2.4 compiler.
  In-scope: **592 of 592** PASS. The compact result is in
  `conformance-summary.json`; `xpassPolicy: fail` makes an unexpected pass
  visible instead of hiding it.

The former `ERROR` `ili24.Attribute24Test.text_List` (`LIST {0..*} OF
TEXT*20`) was a real lowering bug: an optional `structureRef()` list was
checked with `!= null` instead of `isEmpty()`. It is fixed and covered by
`CollectionTypeContractTest`; the case is now PASS.

## L0B-3: freigegebene OID-Kompatibilität (29.10.2026)

Die Nutzerentscheidung erlaubt 2.3-OID- und Basket-OID-Regeln ohne CONTRACTED.
Sechs bisherige Normausnahmen sind dadurch reguläre PASS-Fälle. Der externe
Runner, Suite-/Archivpins, Sprachfilter und Originalquellen sind unverändert.
Alle **18** expliziten Projektprüfungen bleiben aktiv: sechs freigegebene
Kompatibilitätsfälle und zwölf verbleibende normative Abweichungen. Die sechs
Fälle behalten Original-/Positivkontrollen und erhalten eine gepinnte negative
OID-Domänenkontrolle mit exakten strukturierten Diagnosen. Fehlende Freigabe,
fehlende/falsche Kontrolle, Hash-/Exit-/Diagnoseabweichung oder XPASS scheitern.
Reguläre rohe PASS werden nicht als XFAIL umklassifiziert. Aktuelle Bilanz bei
voller Auswahl: roh **580 PASS / 12 FAIL / 1 XFAIL**, Projekt **580 PASS / 13 XFAIL**.
Die ältere Ergebnisbeschreibung oben bleibt als datierter Nachweis erhalten.
