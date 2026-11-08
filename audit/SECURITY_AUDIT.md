# Kepler Slicer — Security Audit

Date: 2026-11-08. Branch `audit/full-security-review`, base `1adf334`. Scope: `app`, `core`, `engine` (JNI bridge,
build scripts, prebuilt `.so`), `engine/obn` (open-bamboo-networking bridge), manifest, Gradle, resources.
Upstream OrcaSlicer (libslic3r, 3MF/STEP readers) and open-bamboo-networking were reviewed only where Kepler
feeds them untrusted data; they were not audited line by line.

Method: manual review against OWASP MASVS v2 / MASTG, CWE; Android Lint 9.4 (AGP), Semgrep 1.184.0
(`p/java`, `p/c`, `p/secrets`, `p/owasp-top-ten`, `p/mobsfscan`), clang-tidy (NDK r30) on the JNI bridge,
OSV.dev queries, binary inspection of the shipped `.so`, UBSan + fuzzing of new native code, JUnit and the host
suites, and tests on the Pixel_Tablet emulator (API 36). Tool versions and commands: `TEST_RESULTS.md`.

This audit does not show that the application is free of vulnerabilities; it covers what the tools and time allowed.

## Summary

| ID | Severity | Title | Status |
|---|---|---|---|
| SEC-03 | High | Engine built with expat 2.2.0 (known memory-corruption CVEs) parsing untrusted 3MF XML | Fixed |
| SEC-02 | High | 3MF decompression bomb reaches the native ZIP reader without limits | Fixed |
| SEC-01 | Medium | Exported launcher accepts `file://` model links (confused deputy over private files) | Fixed |
| SEC-08 | Medium | Bambu plugin linked with OpenSSL 1.1.1w (EOL) and curl 7.75.0 | Fixed (OpenSSL 3.5.9, curl 8.23.0) |
| SEC-04 | Medium | Bambu cloud token written in plain text by the network plugin | Fixed |
| SEC-07 | Medium | Untrusted text passed to `NewStringUTF`/`ThrowNew` (process abort on emoji/malformed bytes) | Fixed |
| SEC-05 | Low | App data included in Android 12+ device-to-device transfer | Fixed |
| SEC-06 | Low | Unbounded copy of an imported content URI (storage exhaustion) | Fixed |
| SEC-09 | Low (accepted risk) | Cleartext HTTP allowed for all hosts | Not fixed — by design, see below |
| SEC-10 | Info | R8/minification disabled in release | Not fixed — by design |
| SEC-11 | Info | Release signing, secrets, exported components, providers | No issue found |

CVSS 3.1 estimates assume the attacker controls a file the user opens (AV:L/UI:R) or a device on the same network
(AV:A); they are guidance, not measured scores.

---

### SEC-03 — High — expat 2.2.0 in the slicing engine
- **Component**: native engine `libkepler_slicer_experimental.so`; 3MF reader (OrcaSlicer `Format/3mf.cpp`, `bbs_3mf.cpp`).
- **Location**: build input `work/OrcaHeadless/deps_src/expat` (expat 2.2.0, 2016) selected by the engine CMake cache
  (`expat_SOURCE_DIR`); the shipped binary contained the string `expat_2.2.0`.
- **Cause**: upstream OrcaSlicer vendors an old expat copy in `deps_src`; the Android build used it.
- **Conditions**: the user opens or receives a crafted `.3mf` (from a website, chat, model link, share intent).
- **Impact**: expat < 2.4.5/2.6.3/2.7 has heap overflows, integer overflows and use-after-free reachable from
  document content: CVE-2022-25235, CVE-2022-25236 (malformed UTF-8 / namespace separators, RCE class),
  CVE-2022-22822 … CVE-2022-22827, CVE-2022-23852, CVE-2022-23990 (integer overflow → heap overflow),
  CVE-2022-40674, CVE-2022-43680 (use-after-free), CVE-2021-45960, CVE-2021-46143, CVE-2024-45490/45491/45492,
  CVE-2024-50602, CVE-2024-8176 (stack overflow by recursion), CVE-2023-52425 (DoS). Not every CVE is reachable
  through the OrcaSlicer API (some need namespaces or specific calls), but the parser runs on attacker data in-process.
  CVSS ≈ 7.8 (AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:H/A:H).
- **Evidence**: version macros of `deps_src/expat/expat.h` (2.2.0); string scan of the `.so`.
- **Fix**: expat 2.10.0 (release `R_2_10_0`, 2026-11-02, fixes four further vulnerabilities; tarball SHA-256
  `5466a341…dea4c` equal to the GitHub digest, GPG signature of Sebastian Pipping verified) vendored in
  `engine/third_party/expat` with an Android `expat_config.h` (entropy: `arc4random_buf`, `/dev/urandom` fallback).
  `engine/build-native.ps1` copies it over `deps_src/expat` before every build, so the fix survives a fresh checkout.
- **Re-check**: rebuilt engine contains `expat_2.10.0` only; `tools/native_tests` (UBSan, on the emulator): well-formed
  3MF XML parses, malformed XML is rejected, a "billion laughs" document stops with
  `XML_ERROR_AMPLIFICATION_LIMIT_BREACH`; 3MF import, slicing and layer preview work in the app.
- **Status**: fixed (commit `41beb8b`).

### SEC-02 — High — 3MF decompression bomb
- **Component**: model import (`CurrentModelStore.prepare` → `ImportedModelPreview.read` → `NativeModelIO.inspect`).
- **Location**: `app/.../CurrentModelStore.java` 44–72 (before the fix): a `.3mf` went straight to the native reader.
- **Cause**: OrcaSlicer's miniz-based reader inflates entries with no output cap; Kepler did not check the archive.
- **Conditions**: opening a crafted `.3mf` (CWE-409).
- **Impact**: a 1 MB file expanding to 1 GiB+ of XML exhausts memory/CPU and kills the app (DoS); the parser state
  grows with the expanded content. CVSS ≈ 5.5 (AV:L/AC:L/PR:N/UI:R/A:H).
- **Fix**: `core/.../ArchiveGuard.java`: every entry is inflated once and counted (actual bytes, not the sizes in the
  archive, which can lie): at most 20 000 entries, 4 GiB in total, ratio ≤ 1000 above 16 MB per entry, entry size must
  match the central directory, no absolute/`..`/backslash/NUL paths. Called before the native reader for `.3mf`.
- **Re-check**: `ArchiveGuardTest` (5 cases: normal project passes; 64 MB zero entry, total cap, unsafe names and
  non-ZIP rejected). Emulator: a 1.0 MB `.3mf` that inflates to 1 GiB, opened through a `content://` VIEW intent, is
  refused with "The 3MF archive looks like a ZIP bomb…" and the process keeps running; a real 3MF still opens.
- **Status**: fixed (commit `c39ac98`).

### SEC-01 — Medium — `file://` links accepted by the exported launcher
- **Component**: `LaunchActivity` (exported, VIEW/SEND filters) → `GeneralPreferences.modelUri` → `CurrentModelStore.prepare`.
- **Location**: `GeneralPreferences.java:87-89` and the `<data android:scheme="file"/>` filter in `AndroidManifest.xml`.
- **Cause**: `ContentResolver.openInputStream(file://…)` opens the path with Kepler's own UID.
- **Conditions**: any installed app sends `VIEW file:///data/data/com.keplerslicer.app/...`.
- **Impact**: confused deputy (CWE-441 / CWE-22): Kepler copies one of its private files into its model store; a file
  that parses as a model (e.g. a stored 3MF project or backup archive) could then be exported or shared by the user
  without realising its origin. Plain-text preference files do not parse as models, which limits the impact.
- **Fix**: only `content://` URIs are accepted; the `file` scheme was removed from the intent filter.
- **Re-check**: `am start -a VIEW -d file:///data/data/com.keplerslicer.app/... -t model/stl` → "unable to resolve
  Intent"; `content://` import works.
- **Status**: fixed (commit `614bb05`).

### SEC-08 — Medium — OpenSSL 1.1.1w (EOL) and curl 7.75.0 in the Bambu plugin
- **Component**: `engine/jniLibs/arm64-v8a/libbambu_networking.so` (open-bamboo-networking, built by
  `engine/obn/build-obn.ps1` against the prefix `work/boost-android-arm64`).
- **Evidence**: `OpenSSL 1.1.1w 11 Sep 2023` string in the binary; `curlver.h` 7.75.0-DEV. OSV lists 22 CVEs for curl
  7.75.0 in the tracked range (CVE-2021-22876 … CVE-2022-35252); later ones (e.g. CVE-2023-38545, CVE-2023-46218,
  CVE-2024-7264, CVE-2024-2398) also apply to 7.75. OpenSSL 1.1.1 has had no public fixes since 2023-09
  (post-EOL CVEs: CVE-2023-5678, CVE-2024-0727, CVE-2024-5535, CVE-2024-9143, CVE-2024-13176).
- **Applicability**: only the Bambu plugin links them; the slicing engine does not. LAN MQTT/FTPS go through OpenSSL
  (certificates checked against the Bambu CA and the printer serial); curl is used for Bambu cloud HTTP, always to
  `*.bambulab.com` with chain and host verification. Most listed curl CVEs need features not used here (SOCKS5,
  cookies, HSTS, MQTT in curl). Exploitation needs a malicious server/MITM with a valid certificate or a hostile LAN
  device answering TLS. Risk: medium, mainly lack of future fixes.
- **Fix** (after the owner's approval): OpenSSL 3.5.9 (LTS, supported to 2030) and curl 8.23.0, verified (SHA-256 equal
  to the GitHub release digests; GPG "Good signature from OpenSSL" and "from Daniel Stenberg"), built static for
  arm64/API 26 by `engine/obn/build-obn-deps.sh` (OpenSSL: no-shared, no-legacy, no-engine, no-module, no-comp;
  curl: OpenSSL + platform zlib, no SSH/IDN/PSL/HTTP2/LDAP). `build-obn.ps1 -Prefix work/obn-deps-arm64` rebuilt the
  plugin; the OpenSSL 1.1 shim was removed. Git for Windows' Perl lacks three core modules; minimal stand-ins are in
  `engine/obn/perl-shim` (build-time only).
- **Re-check**: the plugin contains `OpenSSL 3.5.9` and `curl-8.23.0`, no 7.75/1.1.1 strings; all 11 JNI exports;
  NEEDED only libz/libm/libdl/libc. `tools/native_tests/tls_check.c` (same static libs) on the emulator against a
  local TLS server: trusted CA → HTTP 200, other CA → CURLE_PEER_FAILED_VERIFICATION, wrong host name → refused.
  App: plugin loads, LAN error path unchanged. Not tested: LAN TLS to a real Bambu printer, cloud broker.
- **Status**: fixed (branch `fix/obn-openssl3-curl8`).

### SEC-04 — Medium — Bambu cloud token in plain text
- **Component**: `BambuObnTransport` / open-bamboo-networking `Agent::set_config_dir` (`agent.cpp:2268`, `obn.auth.json`).
- **Cause**: the plugin persists the session passed to `change_user` (access token) as plain JSON in its config folder;
  Kepler itself stores the token encrypted (AES-GCM, Android Keystore, `NetworkSecrets`). Introduced with the OBN
  integration (`ca77dc9`).
- **Impact**: CWE-312; the token is readable by anyone with access to the app sandbox (root, backup/transfer tools)
  and bypasses the Keystore protection.
- **Fix**: `forgetSession()` deletes `obn.auth.json` (and `.tmp`) when the agent starts and right after every cloud
  sign-in; the plugin keeps the session in memory (`BambuObnTransport.java` 49, 56, cloud branch of `open`).
- **Re-check**: after start and a connection attempt, `files/bambu_networking/config` holds only `obn.conf`, `obn.env`.
  A real cloud sign-in could not be tested (no account in the test environment).
- **Status**: fixed (commit `951087b`); cloud path requires verification with a real account.

### SEC-07 — Medium — Modified UTF-8 at the JNI boundary
- **Component**: `engine/src/slice_jni.cpp` (14 `NewStringUTF`, 43 `ThrowNew`), `engine/obn/kepler_obn_jni.cpp:138,204`.
- **Cause**: JNI expects Modified UTF-8. Printer MQTT reports, plugin messages, profile names and engine error texts
  (which quote file content such as object names) can contain supplementary characters, malformed bytes or NUL.
- **Impact**: CheckJNI (debuggable builds) aborts the process ("input is not valid Modified UTF-8"); release builds get
  undefined text. A device on the LAN sending an emoji in a report could crash the app (CWE-20/CWE-176, DoS).
- **Fix**: `engine/src/jni_text.hpp` (`jni_safe_utf8`, `jni_string`, `jni_throw_new`): valid BMP UTF-8 is kept,
  supplementary characters, overlong/surrogate/truncated sequences and NUL become `?`. All call sites in both
  bridges use it; both libraries were rebuilt.
- **Re-check**: `tools/native_tests/native_safety_test.cpp` under UBSan: unit cases + 200 000 random inputs, output
  always valid Modified UTF-8. App: slicing, preview, Bambu error path, no JNI abort in logcat.
- **Status**: fixed (commit `41beb8b`).

### SEC-05 — Low — device-to-device transfer
- `android:allowBackup="false"` disables cloud backup, but on Android 12+ device transfer still copies app data
  (including, before SEC-04, the plugin token). `res/xml/data_extraction_rules.xml` now excludes every domain for
  cloud backup and device transfer. Lint `DataExtractionRules` warning gone. Fixed (`614bb05`).

### SEC-06 — Low — unbounded import copy
- `CurrentModelStore.prepare` copied a content URI without limit; a provider streaming endlessly could fill storage
  (CWE-400). Imports now stop at 2 GiB with "The model file is too large." Fixed (`c39ac98`).
  Downloads from links were already limited to 512 MB.

### SEC-09 — Low — cleartext traffic (accepted risk)
- `android:usesCleartextTraffic="true"` applies to all hosts. LAN printer APIs (OctoPrint, Moonraker, PrusaLink,
  Duet, …) are plain HTTP and live on arbitrary private addresses that a network security config cannot enumerate.
  Consequence: an API key typed for an `http://` address on a public network is sent in clear. Mitigations present:
  stealth mode refuses non-LAN hosts (`NetworkPolicy.checkUri`), redirects are not followed automatically,
  responses are size-limited (`NetworkHttp`). Recommendation: warn when a connection with credentials uses `http://`
  to a non-private address. Not changed: the warning is a product decision and needs UI text.

### SEC-10 — Info — R8 disabled
- `minifyEnabled false` (documented in `app/build.gradle`: JNI and JSON-driven settings rely on names). Impact limited
  to easier reverse engineering of an open-source (AGPL) app; no secrets ship in the APK (checked: no keys, tokens or
  signing data in sources, resources or git history). Not changed.

### SEC-11 — No issue found
- Exported components: only `LaunchActivity` (launcher + VIEW/SEND). `ShareProvider` is not exported, read-only,
  canonicalises paths and serves only `cache/share|shapes|url-import`. No `PendingIntent`, `WebView`,
  `addJavascriptInterface`, `Runtime.exec`, `DexClassLoader` or dynamic `System.load` of writable paths.
- Credentials: AES-256-GCM with a non-exportable Android Keystore key, random IV, record id as AAD (`NetworkSecrets`).
- TLS: no custom `TrustManager`/`HostnameVerifier` remain; Bambu LAN TLS is verified by the plugin against the Bambu
  CA bundle and the printer serial; cloud MQTT verifies chain and host name (compiled-in CA bundle).
- HTTP client (`NetworkHttp`): http/https only, no user-info/fragment, manual redirects re-checked by policy, 2 MB
  response cap, timeouts. Model links: 512 MB cap, file-name sanitising, HTML refused.
- Logs: no secrets in Kepler logs; the plugin logs token sizes only.
- Signing: keystore read from outside the repository; `local.properties`, `release-out/` ignored; no secrets in history.
- STL/G-code parsers: size/count checks, finite-coordinate checks, line-length and layer caps (`StlMesh`, `GcodeAnalysis`).

## False positives (Semgrep)
- `android_hidden_ui` (58): ordinary `setVisibility(GONE)`.
- `hardcoded_api_key` / `hardcoded_username` (26): preference and JSON key names, not secrets.
- `weak_hash`/`use-of-md5` in `LegacyHostFiles`: checksum required by the printer upload protocol.
- `sha1_hash` in `MiniWebSocket`: `Sec-WebSocket-Accept` is defined with SHA-1 by RFC 6455.
- `unencrypted-socket` (4): LAN printer protocols without TLS (Elegoo, legacy TCP console, WebSocket, OAuth loopback).
- `android_zip_slip` in `CompatibilityResources`: the entry is matched by exact name from a bundled asset.

## Not verified
- Upstream OrcaSlicer/OCCT parsing of STEP/OBJ beyond Kepler's checks; fuzzing of the native 3MF/STEP readers
  (no on-device fuzzing harness with the full engine was feasible in this session).
- Bambu cloud sign-in with a real account; LAN with a real printer.
- AddressSanitizer runs of the full engine (needs an ASan build of the 63 MB engine and an ASan runtime on device).
- CodeQL, OWASP Dependency-Check, SpotBugs, Detekt/ktlint (not installed; no Kotlin in the project).
