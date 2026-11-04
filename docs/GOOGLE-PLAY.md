# Google Play release

## Build

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew --offline bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`, signed with the upload key.

- applicationId `com.keplerslicer.app` — never change it after the first release.
- Raise `versionCode` (and `versionName`) in `app/build.gradle` for every upload.
- Upload key: `C:\Users\tails\.kepler-signing\kepler-upload.jks` plus `keystore.properties` (passwords) in the same
  folder; Gradle reads it from there or from `-Pkepler.signing=<path>`. Neither file is in git. **Back up that folder.**
  Enrol in Play App Signing on the first upload: Google keeps the app signing key, and a lost upload key can be reset
  through Play support.
- Upload certificate SHA-256: `52:45:21:4A:43:0B:77:A5:44:EB:2E:BC:A6:C8:90:01:CA:2D:F3:6E:FC:B3:7B:EB:E3:07:75:3B:E6:EF:FA:94`.

## Store requirements checked

| Requirement | State |
| --- | --- |
| Target API (35+ for new apps) | targetSdk 36 |
| 16 KB page size for native code | `libkepler_slicer_experimental.so` LOAD segments aligned 0x4000 |
| Download size ≤ 200 MB (base module, compressed) | ≈180 MB (assets: upstream resources 100 MB, profiles 31 MB; engine 66 MB) |
| 64-bit native code | arm64-v8a only (32-bit devices are excluded) |
| Adaptive launcher icon | `drawable-v26/ic_launcher.xml` |
| Privacy policy URL | [PRIVACY.md](PRIVACY.md) — publish it (for example the GitHub page of this file) and enter the URL in Play Console |

If the size grows past 200 MB, move `upstream_resources.zip` and `kepler_profiles.zip` into an install-time asset pack.

## Data safety form (Play Console)

- Data collected: **none** sent to the developer. No analytics, ads or crash reporting SDKs.
- Data shared: none with the developer; files go only to the printer or cloud service the user chose
  (user-initiated transfer, not "sharing" in Play terms).
- Data encrypted in transit: HTTPS for cloud services; local printers may use plain HTTP on the user's network.
- Users can delete data: uninstall, or clear app data.
- Account creation: none in the app; third-party service sign-in happens on the service's site.

## Content and listing

- Category: Tools (or Productivity). Content rating questionnaire: no user-generated content, no ads.
- The app embeds the upstream slicing engine under AGPL-3.0: keep `LICENSE-ORCASLICER.txt` and provide the source
  (this repository) in the store description or the app's About screen.
