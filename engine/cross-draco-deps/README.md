# Draco for Android

Builds Draco 1.5.7, the version OrcaSlicer pins at commit `6d2869c430284714f0a22570c38d3b359965a802`
(`deps/Draco/Draco.cmake`).

| Archive | SHA-256 |
|---|---|
| [draco 1.5.7](https://github.com/google/draco/archive/refs/tags/1.5.7.zip) | `27b72ba2d5ff3d0a9814ad40d4cb88f8dc89a35491c0866d952473f8f9416b77` |

The checksum is verified before unpacking.

## Requirements

PowerShell, Git, CMake, Ninja, Android NDK and an OrcaSlicer checkout at the pinned commit. `BuildRoot` must be a new
directory; the script never overwrites an existing `BuildRoot/src`.

## Build

```powershell
./build.ps1 -UpstreamSource /path/to/OrcaSlicer `
  -AndroidNdk /path/to/android-ndk `
  -BuildRoot /path/to/new/draco-build `
  -CMake /path/to/cmake -Ninja /path/to/ninja -Jobs 4
```

Static Release build for arm64-v8a, API 26; other Draco options keep the upstream defaults. Output in
`BuildRoot/prefix`: `lib/libdraco.a` and the CMake package `share/cmake/draco`. The script also links a test shared
library through upstream `FindDraco.cmake` and the `draco::draco` target.

## License

Draco is licensed under the Apache License 2.0; keep its license and notices when redistributing.
