# STEP native integration fixtures

Copied without modification from upstream commit
`6d2869c430284714f0a22570c38d3b359965a802`, `tests/data/`.
The upstream AGPL-3.0 license is included at the project root and with the pinned
upstream source in the source archive.

| File | SHA-256 | Native check |
|---|---|---|
| `cone_tilted_seam_pcurve.step` | `5e7fed139394ec39998491ad45a87b391934c5c562fc217e4ea9e598ebf398ea` | A curved closed solid. Import at 0.003 mm / 0.5 rad and 0.5 mm / 1.0 rad; require nonempty valid meshes and compare actual triangle counts and bounds. The source bytes must remain identical. |
| `security_classification.step` | `2eb135e3752ef066d73a440ec93509bca437652962fd522b8376f2e3734a8857` | A tetrahedron with security-classification metadata. The desktop regression expects one object, one volume and four triangles. |

For both files, `prepareStep` must export a nonempty geometry 3MF into a new
private job, retain the original STEP separately, and use the normal native
inspection/preview path. Reopening the resulting 3MF must agree with the helper's
triangle count and bounds. Error/invalid-tolerance cases must not publish a
partial file or replace an existing output. These are integration acceptance
checks, not a claim that they have been run by the host parameter test.
