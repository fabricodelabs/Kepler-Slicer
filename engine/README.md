# Native engine

The `engine` module holds the JNI bridge (`java/com/keplerslicer/engine`) and the native OrcaSlicer engine
`jniLibs/arm64-v8a/libkepler_slicer_experimental.so`. The library is prebuilt and committed, so building the app does
**not** require the steps below — they are only needed to change the C++ side (`src/`).

## Sources

| Path | Purpose |
|---|---|
| `src/slice_jni.cpp` | JNI entry points: slicing, model inspection and editing tools, settings, profiles |
| `src/model_io.cpp`, `src/model_transform.hpp` | Loading STL/OBJ/3MF/STEP, plates, transform and sinking below the plate |
| `src/native_preferences.cpp`, `src/system_profile_config.cpp` | Settings resolution and printer/process/filament profiles |
| `src/step_import.cpp` | STEP import |
| `java/com/keplerslicer/engine` | Java side of the bridge (`ExperimentalSlicer`, `NativeModelIO`, `NativeVersion`) |
| `android-headless.patch` | Adapts the OrcaSlicer CMake build to a headless Android library |

The engine is built from OrcaSlicer pinned at commit
[`6d2869c`](https://github.com/OrcaSlicer/OrcaSlicer/tree/6d2869c430284714f0a22570c38d3b359965a802); the build refuses
any other commit.

## Building

Requirements: Windows with PowerShell 7, CMake 3.22+, Ninja, Android NDK r30, Git, and a separate OrcaSlicer checkout at
the pinned commit with `android-headless.patch` applied.

1. Cross-build the dependencies once (Boost, TBB, CGAL, OpenCASCADE, OpenVDB and others):
   `build-android-deps.ps1`, `build-occt.ps1`, `build-vdb-deps.ps1`.
2. Configure the headless engine: `configure-headless.ps1`.
3. Build, verify the JNI exports and publish the library into `engine/jniLibs`:

```powershell
pwsh -File engine/build-native.ps1 -Build <build dir> -Cmake <cmake.exe> -Ndk <android-ndk-r30> -Parallel 12 -PublishToApp
```

`build-native.ps1` checks that every `native` method of the Java bridge has a matching exported symbol before it
publishes; the previous library is kept next to the build. If Gradle holds the old library open, run
`./gradlew --stop` first.

Details of the settings and STEP contracts: [NATIVE-PREFERENCES-BUILD.md](NATIVE-PREFERENCES-BUILD.md).

## License

OrcaSlicer and this derived engine are licensed under the GNU AGPL-3.0 ([LICENSE-ORCASLICER.txt](../LICENSE-ORCASLICER.txt)).
The bundled semver code is MIT-licensed (Tomas Aparicio), as stated in its source files.
