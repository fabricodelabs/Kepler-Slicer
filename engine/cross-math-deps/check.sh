#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT}"
: "${BUILD_ROOT:?Set BUILD_ROOT used by build.sh}"
: "${BOOST_PREFIX:?Set BOOST_PREFIX to the independently built Android Boost prefix}"
here="$(cd "$(dirname "$0")" && pwd -P)"
cmake_path() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
prefix="$(cmake_path "$BUILD_ROOT/prefix")"
boost="$(cmake_path "$BOOST_PREFIX")"
ndk="$(cmake_path "$ANDROID_NDK_ROOT")"
src="$(cmake_path "$here/check")"
build="$(cmake_path "$BUILD_ROOT/kepler-cgal-probe-build")"
cmake -S "$src" -B "$build" -G Ninja -DCMAKE_BUILD_TYPE=Release \
  "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  "-DCMAKE_PREFIX_PATH=$prefix;$boost" "-DCMAKE_FIND_ROOT_PATH=$prefix;$boost" \
  "-DGMP_INCLUDE_DIR=$prefix/include" "-DGMP_LIBRARIES=$prefix/lib/libgmp.a" \
  "-DMPFR_INCLUDE_DIR=$prefix/include" "-DMPFR_LIBRARIES=$prefix/lib/libmpfr.a"
cmake --build "$build" -j "${JOBS:-4}"
echo 'Probe linked; it has not been run on Android.'
