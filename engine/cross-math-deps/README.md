# Math dependencies for Android

Builds GMP, GMPXX, MPFR and the header-only CGAL for arm64-v8a, Android API 26, with the versions and options of
OrcaSlicer at commit `6d2869c430284714f0a22570c38d3b359965a802`. `build.sh` checks that commit in `UPSTREAM_SOURCE`
and verifies the archive checksums before unpacking.

| Dependency | Archive | SHA-256 |
|---|---|---|
| GMP 6.3.0 | `https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz` | `a3c2b80201b89e68616f4ad30bc66aee4927c3ce50e33929ca819d5c43538898` |
| MPFR 4.2.2 | `https://ftp.gnu.org/gnu/mpfr/mpfr-4.2.2.tar.bz2` | `9ad62c7dc910303cd384ff8f1f4767a655124980bb6d8650fe62c815a231bb7b` |
| CGAL 6.2.1 | `https://github.com/CGAL/cgal/releases/download/v6.2.1/CGAL-6.2.1.zip` | `eebd737d9b7f0199647ba5c1f9f6c7a3d0651aacef9c2fd4dedc52da6c25edbe` |

## Requirements

Android NDK, Git, Bash, curl, sha256sum, tar (xz/bzip2), unzip, GNU patch, make and m4, CMake, Ninja, and a native C
compiler for the GMP generators (`HOST_CC`). `check.sh` also needs Boost built for Android with the same ABI and API.
On Windows, run Git Bash from an environment where the native compiler works (for example clang with MSVC set up).

## Build

```bash
export UPSTREAM_SOURCE=/path/to/OrcaSlicer
export ANDROID_NDK_ROOT=/path/to/android-ndk
export BUILD_ROOT=/path/without/spaces/kepler-math-build
export HOST_CC=/path/to/native/clang
bash ./build.sh

export BOOST_PREFIX=/path/to/android-boost-prefix
bash ./check.sh
```

`BUILD_ROOT` and the NDK path must not contain spaces, and `BUILD_ROOT/src` must not exist yet. GMP is built static
with `--disable-assembly` and the C++ API; MPFR static with `--enable-thread-safe`, using the `configure` shipped in
its archive. `check.sh` links a test shared library with CGAL, GMP and MPFR.

## License

Keep the license of every component you redistribute (GMP and MPFR: LGPL; CGAL: GPL/LGPL).
