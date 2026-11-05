# Modules and the Kotlin question (2026-11-05)

## Modules

| Module | Kind | Contents | Tests |
|---|---|---|---|
| `:app` | Android application | Screens, glass UI, 3D view, Android storage (SharedPreferences, Keystore), discovery, activity lifecycle | Host suites in `tools/` (PowerShell + stubs) |
| `:core` | Java library (no Android) | Printer protocols (Bambu LAN/cloud/SSDP, Moonraker, OctoPrint, Duet, Repetier, Creality, Elegoo), MQTT/WebSocket/TLS channels, HTTP transport, model transform and geometry, G-code analysis, profile and project parsing | JUnit: `./gradlew :core:test` |
| `:engine` | Android library | JNI bridge (`ExperimentalSlicer`, `NativeModelIO`, `NativeVersion`) and `libkepler_slicer_experimental.so` (built by `engine/build-native.ps1`, published to `engine/jniLibs`) | JNI export check in `build-native.ps1` |

`:core` keeps the `com.keplerslicer.app` package, so the app still uses its package-private API. A class can move
to `:core` only when it and everything it uses are free of Android (`android.*`, `R`, `Context`).

Checks: `./gradlew assembleDebug :core:test` and `pwsh tools/run_host_tests.ps1 -JsonJar <org.json jar>`;
`.github/workflows/ci.yml` runs all of them on every push.

## Should the app be rewritten in Kotlin?

Recommendation: **no full rewrite. New code may be Kotlin; existing Java stays and is converted only where a file
is being rewritten anyway.**

Why not a rewrite:

- The risk is in behaviour, not in the language. About 17 000 lines of Java with many hand-checked guards
  (cancellation, lifecycle, fingerprints, validation) would all have to be re-verified; the host test suites cover
  protocols and settings, not the screens, so a rewrite would mostly be checked by hand on the emulator.
- No user-visible gain. Kotlin does not make slicing, printing or the UI faster; the engine is C++.
- The Android framework is the same in both languages; the current Views/glass UI would be ported line by line.
- Java and Kotlin mix freely in one module, so there is no deadline forcing a conversion.

Where Kotlin does help, and is worth adopting gradually:

- Coroutines with lifecycle scopes would replace the ~60 hand-made `new Thread` + `runOnUiThread` + `isDestroyed()`
  patterns (audit item R5) with cancellation that follows the screen.
- Null-safety and data classes suit the settings, profile and network models.
- Jetpack Compose (Kotlin only) would be the natural way to rebuild screens later, but that is a UI redesign, not a
  language change.

Suggested path: enable Kotlin in `:app` (AGP 9 has built-in Kotlin support), write new screens and helpers in
Kotlin, and convert a Java file only when it is being reworked anyway (first candidates: background work in
`DeviceActivity`/`NetworkActivity`, then `MainActivity` tool controllers as they are split out). Keep `:core` in Java
until there is a reason to change it: it is small, tested, and shared with the PowerShell host tests.
