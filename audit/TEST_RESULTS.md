# Test Results

## Tools and versions
| Tool | Version | Command (abridged) | Result |
|---|---|---|---|
| Gradle / AGP | Gradle 9.8.0, AGP 9.4.0, JBR (Android Studio) | `./gradlew --offline assembleDebug :core:test :app:lintDebug` | build OK; core tests 19/19; Lint 0 errors |
| Android Lint | AGP 9.4.0 bundled | `:app:lintDebug :engine:lintDebug` | see `CODE_QUALITY.md` |
| Semgrep | 1.184.0 (pip) | `semgrep scan --metrics=off --config p/java --config p/c --config p/secrets --config p/owasp-top-ten --config p/mobsfscan app/src core/src engine/src engine/java engine/obn tools` | 104 findings triaged (`p/android` does not exist in the registry: HTTP 404) |
| clang-tidy | NDK r30 bundled | `clang-tidy -p work/orca-headless-fresh --checks=clang-analyzer-*,bugprone-*,cert-… engine/src/{slice_jni,model_io,process_settings,project_settings,project_inspector,step_import,system_profile_config,native_preferences}.cpp` | 4 warnings, non-issues |
| OSV.dev API | 2026-11-08 | `POST /v1/query` for org.json, junit, curl 7.75.0 (Alpine 3.13), OpenSSL 1.1.1w | see `DEPENDENCY_REPORT.md` |
| clang (NDK r30), UBSan | x86_64-linux-android33 | `sh tools/native_tests/run.sh <ndk> <adb>` | all passed on the emulator |
| GnuPG | Git for Windows | `gpg --verify` of expat, OpenSSL, curl tarballs | good signatures |
| Emulator | Pixel_Tablet AVD, Android 16 (API 36) | adb, screenshots | see below |

Not available / not run: CodeQL, OSV-Scanner, OWASP Dependency-Check, SpotBugs, Detekt, ktlint (no Kotlin),
AddressSanitizer build of the full engine, LeakCanary, Android Studio Profiler, instrumentation tests (the project has
none), on-device fuzzing of the native 3MF/STEP readers.

## Unit and host tests
| Suite | Result |
|---|---|
| `:core:test` (JUnit) | 19 tests, 0 failures — includes new `ArchiveGuardTest` (5) and `GcodeAnalysisTest` (1) |
| `tools/run_host_tests.ps1` (all host suites: Bambu transport, printer control, presets, settings, model restore, backups, STEP, online policy, …) | all passed after adding `ArchiveGuard` to the model-restore sources and making its 3MF inputs real ZIP files |
| `tools/native_tests/tls_check.c` (arm64, static OpenSSL 3.5.9 + curl 8.23.0) | local TLS server on the host (Python `ssl`, test CA and certificates made with `openssl req`), reached as `10.0.2.2`: trusted CA → 200; other CA → error 60; certificate for another name → error 60 — passed |
| `tools/native_tests` (UBSan) | `jni_safe_utf8`: 8 cases + 200 000 random inputs; expat 2.10.0: well-formed XML, malformed XML, billion laughs (`XML_ERROR_AMPLIFICATION_LIMIT_BREACH`) — all passed |

## Regression on the emulator (after all fixes)
| Area | Result |
|---|---|
| Build and install | OK |
| Cold/hot start, splash route | OK (390–589 ms cold, 288 ms hot) |
| Workspace UI (tablet) | OK |
| Load 3MF from a `content://` VIEW intent | OK (project mode dialog, geometry loaded) |
| Malicious 3MF (1.0 MB → 1 GiB) | refused with a message, process alive |
| `file://` VIEW intent | not resolved (intended) |
| Slicing on the plate | OK (Bambu Lab X1 profile, 11m 8s, 4.32 g) |
| Layer preview | OK |
| Printer profiles / editor windows | OK |
| Bambu LAN (simulated absent printer) | error after 12 s, no crash; no plain-text session file |
| Settings persistence | settings and selection kept across restarts |
| JNI aborts in logcat | none |

## Not verified
- Mobile (phone) interface after these changes: the fixes are interface-independent, except the dialog height change,
  which applies to tablet mode only; the phone UI was not opened in this session.
- Real printers (Bambu LAN/cloud, OctoPrint, Moonraker…), real Bambu account.
- Physical arm64 devices and low-memory devices.
