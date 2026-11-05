# Image dependencies for Android

Builds Assimp, OpenCV, libjpeg-turbo and libpng for arm64-v8a, Android API 26 and NDK r30, with the versions, flags
and checksums of OrcaSlicer `deps/{Assimp,OpenCV,JPEG,PNG}`. Libraries are static, with PIC and `c++_static`.
Tested on Windows with CMake 4.4.4, Ninja 1.13.2 and NDK 30.0.16248370.

## Archives

Save all archives into one folder under these names:

| File | Source | SHA-256 |
|---|---|---|
| `assimp-5.4.3.tar.gz` | [Assimp v5.4.3](https://github.com/assimp/assimp/archive/refs/tags/v5.4.3.tar.gz) | `66dfbaee288f2bc43172440a55d0235dfc7bf885dda6435c038e8000e79582cb` |
| `opencv-4.14.0.tar.gz` | [OpenCV 4.14.0](https://github.com/opencv/opencv/archive/refs/tags/4.14.0.tar.gz) | `ee8fb9b30eb60850431b4656447080e3737b56e45719c92b67f245950609f86e` |
| `jpeg-3.2.0.zip` | [libjpeg-turbo 3.2.0](https://github.com/libjpeg-turbo/libjpeg-turbo/archive/refs/tags/3.2.0.zip) | `1b4c2cb6cba1ca5e53b9e85eeeab02154cab4b70894c96e97a9c747a26d19d63` |
| `png-1.6.59.zip` | [libpng v1.6.59](https://github.com/glennrp/libpng/archive/refs/tags/v1.6.59.zip) | `6fd97a1e5e2ac55cec0a538a9800a351eb3844086736291844ebd12db72ba433` |

`build.ps1` verifies every checksum before unpacking and stops on a mismatch; `-VerifyOnly` only checks them.
`tar.exe` must be on `PATH`. Archives are unpacked into `$Work/sources`, so use a new work folder.

## Build

```powershell
.\build.ps1 `
  -Ndk 'D:\Android\android-ndk-r30' `
  -Cmake 'D:\tools\cmake\bin\cmake.exe' `
  -Ninja 'D:\tools\ninja.exe' `
  -Archives 'D:\upstream-archives' `
  -Work 'D:\upstream-image-build' `
  -Prefix 'D:\upstream-image-install' `
  -Jobs 4
```

Output in `$Prefix`: `lib/libassimp.a`, `lib/libjpeg.a`, `lib/libpng.a`,
`sdk/native/staticlibs/arm64-v8a/libopencv_world.a`; package configs in `lib/cmake/assimp-5.4` and `sdk/native/jni`.
Use `find_package(OpenCV CONFIG REQUIRED COMPONENTS world)` and the `opencv_world` target.

## Notes

- The OpenCV export stores absolute paths to `libjpeg.a` and `libpng.a`; rebuild if the prefix is moved.
- `BUILD_ANDROID_EXAMPLES` and `INSTALL_ANDROID_EXAMPLES` are switched off explicitly.
- `CMAKE_POLICY_VERSION_MINIMUM=3.5` lets CMake 4 read older CMake files.
- PNG and OpenCV use the NDK zlib; Assimp uses its own static zlib, as upstream does.
- `link-check` links `cv::imdecode` and `Assimp::Importer` together with `--no-undefined` to check the prefix.
