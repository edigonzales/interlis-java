# Geometry validation fixtures

The CLI regressions use local INTERLIS models and the counterexamples from the geometry review. The controls exercise equal and unequal native XTF AREA boundaries, one junction versus several contact points, different starts of the same XTF 2.3 ring walk, exact intersections, radius feasibility and the implicit track limit. `GeometryTest` checks the reported coordinates and data paths in both CLI output formats.

`gap/AlgebraicWitness.xtf` reproduces the closed gap after a tolerated overlap from the iox-ili regression for #162. The support is moved to `(4.1,−2)` so the validator must report a calculated boundary point. [Pinned upstream regression](https://github.com/claeis/iox-ili/commit/414ff4c002d256a5471ca8f921f4250179acd1db) · [iox-ili MIT license](../../../../../interlis-validation-jts/src/test/resources/geometry/iox-overlap/LICENSE.mitx)
