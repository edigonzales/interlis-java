# INTERLIS 1 conformance scope

The compiler supports INTERLIS 2.3 and 2.4. The external test suite includes
`ili23.Attribute10Test.attributeComplete`, which uses INTERLIS 1 syntax
(`TRANSFER INTERLIS1;` and `TABLE`). The test stays in the suite.

[The conformance baseline](baseline.json) requires this case to be rejected
and classified as XFAIL. The host sets `xpassPolicy` to `fail` so unexpected
acceptance fails the run.
