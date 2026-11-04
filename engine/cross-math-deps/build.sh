#!/usr/bin/env bash
# Cross-build only the official upstream GMP/MPFR/CGAL dependencies, not libslic3r.
set -euo pipefail

: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT to the Android NDK directory}"
: "${UPSTREAM_SOURCE:?Set UPSTREAM_SOURCE to an official upstream checkout}"
: "${BUILD_ROOT:?Set BUILD_ROOT to a new, empty build directory without spaces}"
: "${HOST_CC:?Set HOST_CC to a native host C compiler; on Windows initialize the MSVC SDK environment}"

EXPECTED=6d2869c430284714f0a22570c38d3b359965a802
[[ "$(git -C "$UPSTREAM_SOURCE" rev-parse HEAD)" == "$EXPECTED" ]] || { echo 'upstream revision mismatch' >&2; exit 1; }
UPSTREAM_SOURCE="$(cd "$UPSTREAM_SOURCE" && pwd -P)"
case "$(uname -s)" in
  Linux*) host_tag=linux-x86_64; ext= ;;
  Darwin*) host_tag=darwin-x86_64; ext= ;;
  MINGW*|MSYS*|CYGWIN*) host_tag=windows-x86_64; ext=.exe ;;
  *) echo 'Unsupported host' >&2; exit 1 ;;
esac
mkdir -p "$BUILD_ROOT"
BUILD_ROOT="$(cd "$BUILD_ROOT" && pwd -P)"
NDK="$(cd "$ANDROID_NDK_ROOT" && pwd -P)"
case "$BUILD_ROOT$NDK" in *' '*) echo 'BUILD_ROOT and ANDROID_NDK_ROOT must have no spaces' >&2; exit 1;; esac
[[ ! -e "$BUILD_ROOT/src" ]] || { echo 'BUILD_ROOT already contains src; use an empty build directory' >&2; exit 1; }
for tool in curl sha256sum tar unzip patch make m4 cmake ninja git; do command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }; done
bin="$NDK/toolchains/llvm/prebuilt/$host_tag/bin"
[[ -f "$bin/clang$ext" ]] || { echo "NDK compiler not found: $bin" >&2; exit 1; }
mkdir -p "$BUILD_ROOT/downloads" "$BUILD_ROOT/src" "$BUILD_ROOT/prefix" "$BUILD_ROOT/tools"
fetch() {
  local url=$1 file=$2 expected=$3
  curl --fail --location --retry 2 -o "$file" "$url"
  printf '%s  %s\n' "$expected" "$file" | sha256sum --check --status || { echo "SHA-256 mismatch: $file" >&2; exit 1; }
}
fetch https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz "$BUILD_ROOT/downloads/gmp.tar.xz" a3c2b80201b89e68616f4ad30bc66aee4927c3ce50e33929ca819d5c43538898
fetch https://ftp.gnu.org/gnu/mpfr/mpfr-4.2.2.tar.bz2 "$BUILD_ROOT/downloads/mpfr.tar.bz2" 9ad62c7dc910303cd384ff8f1f4767a655124980bb6d8650fe62c815a231bb7b
fetch https://github.com/CGAL/cgal/releases/download/v6.2.1/CGAL-6.2.1.zip "$BUILD_ROOT/downloads/cgal.zip" eebd737d9b7f0199647ba5c1f9f6c7a3d0651aacef9c2fd4dedc52da6c25edbe
tar -xf "$BUILD_ROOT/downloads/gmp.tar.xz" -C "$BUILD_ROOT/src"
tar -xf "$BUILD_ROOT/downloads/mpfr.tar.bz2" -C "$BUILD_ROOT/src"
unzip -q "$BUILD_ROOT/downloads/cgal.zip" -d "$BUILD_ROOT/src"
(cd "$BUILD_ROOT/src/gmp-6.3.0" && patch -p1 < "$UPSTREAM_SOURCE/deps/GMP/0001-GMP_GCC15.patch")

# The generated wrapper preserves a host compiler path containing spaces.
printf '#!/usr/bin/env bash\nexec %q "$@"\n' "$HOST_CC" > "$BUILD_ROOT/tools/hostcc"
chmod +x "$BUILD_ROOT/tools/hostcc"
export CC_FOR_BUILD="$BUILD_ROOT/tools/hostcc"
export CC="$bin/clang$ext --target=aarch64-linux-android26"
export CXX="$bin/clang++$ext --target=aarch64-linux-android26"
export AR="$bin/llvm-ar$ext" RANLIB="$bin/llvm-ranlib$ext" NM="$bin/llvm-nm$ext"
export CFLAGS='-O2 -fPIC' CXXFLAGS='-O2 -fPIC'
prefix="$BUILD_ROOT/prefix"
build_triplet="$("$BUILD_ROOT/src/gmp-6.3.0/config.guess")"
jobs="${JOBS:-4}"
(cd "$BUILD_ROOT/src/gmp-6.3.0" && ./configure --build="$build_triplet" --host=aarch64-linux-android --disable-assembly --disable-shared --enable-static --enable-cxx --prefix="$prefix" && make -j"$jobs" && make install)
(cd "$BUILD_ROOT/src/mpfr-4.2.2" && ./configure --build="$build_triplet" --host=aarch64-linux-android --disable-shared --enable-static --enable-thread-safe --with-gmp="$prefix" --prefix="$prefix" && make -j"$jobs" && make install)

# Native Windows CMake expects drive-letter paths, not MSYS /c/... paths.
cmake_path() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
cm_src="$(cmake_path "$BUILD_ROOT/src/CGAL-6.2.1")"
cm_build="$(cmake_path "$BUILD_ROOT/cgal-build")"
cm_prefix="$(cmake_path "$prefix")"
cm_ndk="$(cmake_path "$NDK")"
cmake -S "$cm_src" -B "$cm_build" -G Ninja -DCMAKE_BUILD_TYPE=Release \
  "-DCMAKE_TOOLCHAIN_FILE=$cm_ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  "-DCMAKE_INSTALL_PREFIX=$cm_prefix" -DBUILD_TESTING=OFF -DCGAL_ENABLE_TESTING=OFF
cmake --install "$cm_build"
echo "Dependencies installed in $prefix. Cross-build does not validate runtime behavior."
