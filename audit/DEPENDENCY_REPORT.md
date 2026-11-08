# Dependency Report (Software Composition Analysis)

Sources: Gradle files, `engine/jniLibs` binary string scan, build prefixes in `work/`, OSV.dev API (2026-11-08).
OWASP Dependency-Check and OSV-Scanner were not installed; OSV was queried directly over its public API.

## JVM / Gradle
| Dependency | Version | Scope | Known vulnerabilities | Note |
|---|---|---|---|---|
| Android Gradle Plugin | 9.4.0 | build | — | Lint: newer version available (GradleDependency, low) |
| org.json:json | 20231013 | compileOnly / test (runtime uses Android's org.json) | none (OSV) | CVE-2023-5072 fixed in this version |
| junit:junit | 4.13.2 | test | none (OSV) | CVE-2020-15250 fixed in 4.13.1 |

No other Java/Kotlin libraries; no dependency is fetched at runtime.

## Native — slicing engine (`libkepler_slicer_experimental.so`)
| Component | Version | Status |
|---|---|---|
| expat | **2.2.0 → 2.10.0** | Fixed (SEC-03). CVEs of 2.2.0 listed in `SECURITY_AUDIT.md` |
| Open CASCADE (STEP) | 8.0 | current major; no CVE checked against OCCT in OSV |
| oneTBB | 2021.5 | old but no known security CVE affecting this use |
| Boost | 1.84 | no known applicable CVE |
| libpng | 1.6.59 | current |
| libjpeg-turbo | 3.2.0 | current |
| zlib | system `libz.so` (Android platform) | updated by the OS |
| OrcaSlicer libslic3r | commit 6d2869c (pinned) | upstream; not audited line by line |

## Native — Bambu plugin (`libbambu_networking.so`)
| Component | Version | Status |
|---|---|---|
| open-bamboo-networking | commit 95d42f8 (AGPL-3.0) | reviewed where Kepler feeds data (auth store, logs) — SEC-04 fixed |
| OpenSSL | **1.1.1w (EOL 2023-09)** | Not fixed (SEC-08). Post-EOL CVEs: CVE-2023-5678, CVE-2024-0727, CVE-2024-5535, CVE-2024-9143, CVE-2024-13176 |
| curl | **7.75.0** | Not fixed (SEC-08). OSV (Alpine 3.13 tracking): CVE-2021-22876, 22890, 22898, 22901, 22922, 22923, 22924, 22925, 22945, 22946, 22947, CVE-2022-22576, 27774, 27775, 27776, 27781, 27782, 32205, 32206, 32207, 32208, 35252; later e.g. CVE-2023-38545, CVE-2023-46218, CVE-2024-2398, CVE-2024-7264 |
| mosquitto (client lib) | vendored by OBN 2.1.2 | client-side only; broker CVEs not applicable |
| cJSON | 1.7.18 (vendored by OBN) | current; CVE-2024-31755 fixed in 1.7.18 |
| uthash | header-only | — |

### Applicability of the curl/OpenSSL CVEs
Only the Bambu plugin links them. curl serves Bambu cloud HTTPS to `*.bambulab.com` with full verification; SOCKS,
cookies, HSTS, curl's MQTT and FTP modules (most listed CVEs) are not used. OpenSSL serves TLS to Bambu printers
(LAN) and the cloud broker. Exploitation needs a hostile server/MITM with a trusted certificate or a hostile device
on the LAN completing a TLS handshake. Severity medium; the main problem is the absence of future fixes.

### Remediation prepared
OpenSSL 3.5.9 (LTS to 2030) and curl 8.23.0 were downloaded to `work/obn-deps-download` and verified
(SHA-256 equals the GitHub release digest; GPG: "Good signature from OpenSSL", "Good signature from Daniel
Stenberg"). Building them was blocked by the session's safety policy for executing code from external sources and
needs the owner's approval.

## Bundled third-party sources added by this audit
- `engine/third_party/expat` — expat 2.10.0, MIT (`COPYING` kept), release tarball SHA-256
  `5466a341f9c692ef7a958a6881311be597f8e1ce345984cf78414f54793dea4c`, signed by Sebastian Pipping
  (key CB8DE70A90CFBF6C3BF5CC5696262ACFFBD3AEC6).

## Unused or questionable
- No unused Gradle dependencies (there are none besides the modules).
- 283 unused Android resources (Lint `UnusedResources`) — size only, see `CODE_QUALITY.md`.
- Permissions: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE — all used
  (printer networking, mDNS/SSDP discovery). No storage, camera, location or notification permissions.
