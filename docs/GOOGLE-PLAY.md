# Google Play release

## Build

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew --offline bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`, signed with the upload key.

The bundle has two modules: the app (`base`) and the install-time asset pack `engine_assets` (upstream engine
resources and the printer/filament/process profiles, ~130 MB). Play installs the pack together with the app and the
app reads it through the normal `AssetManager`. `assembleRelease` alone builds an APK **without** these files; the
APK for GitHub Releases is the universal APK made from the bundle:

```bash
./gradlew --offline bundleRelease :app:packageReleaseUniversalApk
# app/build/outputs/apk_from_bundle/release/app-release-universal.apk
```

Debug builds (`assembleDebug`) read the same files from `engine_assets/src/main/assets`, so development is unchanged.
To install a release exactly as Play splits it: `./gradlew :app:makeApkFromBundleForRelease`, unzip
`app/build/intermediates/apks_from_bundle/release/makeApkFromBundleForRelease/bundle.apks` and
`adb install-multiple splits/base-master.apk splits/base-arm64_v8a.apk asset-slices/engine_assets-master.apk`.

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
| Download size ≤ 200 MB (base module, compressed) | base ≈54 MB compressed (engine 66 MB raw); asset pack `engine_assets` ≈131 MB (install-time limit 1 GB) |
| 64-bit native code | arm64-v8a only (32-bit devices are excluded) |
| Adaptive launcher icon | `drawable-v26/ic_launcher.xml` |
| Privacy policy URL | [PRIVACY.md](PRIVACY.md) — publish it (for example the GitHub page of this file) and enter the URL in Play Console |

`upstream_resources.zip` and `kepler_profiles.zip` live in the install-time asset pack `engine_assets`; anything large
added later belongs there too.

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
