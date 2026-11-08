# Remediation Log

Branch `audit/full-security-review` (from `1adf334`). One logical change per commit; author Rodion.

| Order | Commit | Findings | Change | Verification |
|---|---|---|---|---|
| 1 | `614bb05` | SEC-01, SEC-05 | `GeneralPreferences.modelUri` accepts `content://` only; `file` scheme removed from the VIEW filter; `res/xml/data_extraction_rules.xml` excludes all domains from cloud backup and device transfer | `am start … file:///data/data/…` → unresolved; content import works; Lint DataExtractionRules gone |
| 2 | `c39ac98` | SEC-02, SEC-06 | `core/ArchiveGuard` (entry count, total inflated size, ratio, paths, size consistency) before the native 3MF reader; import copy capped at 2 GiB; English messages; host test inputs made real ZIPs | `ArchiveGuardTest` 5/5; host suites pass; emulator: 1 GiB bomb refused, real 3MF opens |
| 3 | `41beb8b` | SEC-03, SEC-07 | expat 2.10.0 vendored (`engine/third_party/expat`) and synced by `build-native.ps1`; `engine/src/jni_text.hpp` used for every `NewStringUTF`/`ThrowNew` in `slice_jni.cpp` and the OBN bridge; both libraries rebuilt; `tools/native_tests` | engine contains `expat_2.10.0`; native tests (UBSan, 200 000 fuzz inputs, billion laughs) pass on the emulator; slicing and preview work |
| 4 | `951087b` | SEC-04 | `BambuObnTransport.forgetSession` deletes the plugin's `obn.auth.json` at start and after cloud sign-in | config dir contains only `obn.conf`, `obn.env`; LAN error path unchanged |
| 5 | `b6c27e0` | Lint WrongConstant, QA-02 | visibility restore mapped to constants; desktop dialogs wrap their content | Lint: 0 errors; dialog checked visually |
| 6 | (this commit) | — | `audit/` reports, session notes | — |

## Not remediated
| ID | Reason | Next step |
|---|---|---|
| SEC-08 OpenSSL 1.1.1w / curl 7.75.0 in the Bambu plugin | building the verified OpenSSL 3.5.9 / curl 8.23.0 sources was blocked by the session safety policy | owner approves running the vendor build scripts; then rebuild the prefix and `build-obn.ps1` |
| SEC-09 cleartext for all hosts | required by LAN printer protocols | product decision: warn for credentials over `http://` to public addresses |
| SEC-10 R8 off | documented design choice | — |
| SPL-01 double splash | product decision | migrate to the SplashScreen API theme |
| QA-03 empty workspace after a refused import | needs a reproducible case and UX decision | verify, keep the previous model until the new one is accepted |
| PERF-01/02, Lint warnings | low impact | cleanup sweep |

## Rollback
Each commit is independent except 3 (engine + plugin binaries rebuilt together). Reverting commit 3 restores the
previous binaries; `build-native.ps1` without the expat step builds against upstream `deps_src/expat` again.
