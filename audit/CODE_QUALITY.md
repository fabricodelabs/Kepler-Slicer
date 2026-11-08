# Code Quality Report

## Static analysis results
| Tool | Scope | Result |
|---|---|---|
| Android Lint (AGP 9.4.0) | `:app`, `:engine` debug | before: 1 error, 410 warnings; after: 0 errors, 409 warnings |
| Semgrep 1.184.0 | app, core, engine (src/java/obn), tools | 104 findings, all triaged; no true positive besides those covered in `SECURITY_AUDIT.md` (see false-positive list there) |
| clang-tidy (NDK r30), `clang-analyzer-*`, `bugprone-*`, selected `cert-*` | 8 Kepler bridge sources with the engine compile database | 4 warnings, all non-issues (below) |

### Lint
| ID | Count | Assessment | Action |
|---|---|---|---|
| WrongConstant (Error) | 1 | `KeplerUi` restored a saved visibility through an `int` variable; value always valid but unchecked | **Fixed** (`b6c27e0`): mapped to `View.VISIBLE/INVISIBLE/GONE` |
| DataExtractionRules | 1 | see SEC-05 | **Fixed** |
| UnusedResources | 283 | leftovers from earlier UI generations (strings/drawables); APK size only | Not fixed: needs a careful sweep, some strings are referenced dynamically by name |
| ObsoleteSdkInt | 37 | `SDK_INT >= 28/30/34` checks with minSdk 33 | Not fixed (harmless) |
| SetTextI18n | 32 | concatenated UI strings | Not fixed (localisation quality) |
| PluralsCandidate | 18 | count strings without plurals | Not fixed |
| UseRequiresApi, DiscouragedApi, Typos, ViewConstructor, ClickableViewAccessibility | 23 | minor | Not fixed |
| DrawAllocation | 5 | PERF-01 | Not fixed |
| UsableSpace | 3 | PERF-02 | Not fixed |
| CustomSplashScreen | 1 | SPL-01 | Not fixed |

### clang-tidy
| Location | Check | Assessment |
|---|---|---|
| `slice_jni.cpp:444` | bugprone-inc-dec-in-conditions | `++n >= length \|\| chars[n]…`: `\|\|` is a sequence point, logic correct (surrogate pair decoding) |
| `slice_jni.cpp:489` | implicit-widening-of-multiplication | constant `4u*1024u*1024u`, no overflow |
| `project_inspector.cpp:49` | implicit-widening-of-multiplication | constant `2*SHA256_DIGEST_LENGTH` |
| `model_io.cpp:90` | bugprone-empty-catch | intentional: plate grid origin falls back to no shift |

## Manual review findings
| ID | Severity | Component | Finding | Status |
|---|---|---|---|---|
| QA-01 | Medium | JNI bridges | Modified UTF-8 not guaranteed (SEC-07) | Fixed |
| QA-02 | Low | `SheetDialog` (tablet) | desktop dialogs used the phone sheet's preferred height and filled the screen (seen with "Open 3MF project") — regression from the tablet work | Fixed (`b6c27e0`) |
| QA-03 | Low | `MainActivity` import flow | after "Replace" is confirmed and the new file fails (e.g. refused 3MF), the workspace shows "No model on the plate yet" instead of the previous model | Requires verification: observed once on the emulator; the previous model file stays on disk |
| QA-04 | Low | `MainActivity`, `SettingsActivity`, `NetworkActivity` | very large classes with long one-line methods; hard to review | Not refactored (no proven defect) |
| QA-05 | Low | background threads | raw threads outlive activities until their work ends (stale results are dropped by sequence checks) | Accepted |
| QA-06 | Info | tests | host suites need explicit source lists; new core classes must be added to the scripts (`test_general_preferences.ps1` needed `ArchiveGuard`) | Fixed for this audit |

No Kotlin code in the project (Detekt/ktlint not applicable); no coroutines.
