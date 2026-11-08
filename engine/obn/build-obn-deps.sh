#!/bin/sh
# Builds the TLS/HTTP dependencies of the Bambu plugin (open-bamboo-networking) for Android arm64, API 26:
#   OpenSSL 3.5.9 (LTS) and curl 8.23.0, static, into <prefix>. Replaces OpenSSL 1.1.1w / curl 7.75.0 (audit SEC-08).
# Runs in Git Bash on Windows with the NDK; mingw32-make (llvm-mingw) is the make tool.
#
#   sh engine/obn/build-obn-deps.sh <download dir> <work dir> <ndk dir> <prefix> <cmake.exe> <ninja.exe>
#
# <download dir> must hold the release tarballs; they are checked against the SHA-256 published with each release
# (the signatures were verified when they were downloaded: OpenSSL release key, Daniel Stenberg for curl).
set -e
DL=$1; WORK=$2; NDK=$3; PREFIX=$4; CMAKE=$5; NINJA=$6
HERE=$(cd "$(dirname "$0")" && pwd)
OPENSSL=openssl-3.5.9; OPENSSL_SHA=603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a
CURL=curl-8.23.0; CURL_SHA=e3de77cfa6367083c776a26647fc9f633b538c06c3baa73d931a252ac8f72b49
check() { echo "$2  $DL/$1" | sha256sum -c - >/dev/null || { echo "checksum mismatch: $1"; exit 1; }; }
check $OPENSSL.tar.gz $OPENSSL_SHA
check $CURL.tar.gz $CURL_SHA
TB=$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin
export PATH="$TB:$PATH"
mkdir -p "$WORK"; cd "$WORK"

# OpenSSL. Git for Windows' Perl lacks a few core modules; minimal stand-ins (perl-shim) are placed where every
# build step finds them (the source root and util/perl).
rm -rf $OPENSSL; tar xzf "$DL/$OPENSSL.tar.gz"; cd $OPENSSL
cp -r "$HERE/perl-shim/"* ./; cp -r "$HERE/perl-shim/"* util/perl/
PERL5LIB="$HERE/perl-shim" ./Configure linux-aarch64 no-shared no-tests no-apps no-docs no-module no-engine no-legacy no-comp \
    no-async -fPIC -D__ANDROID_API__=26 --prefix="$PREFIX" --openssldir=/system/etc/security --libdir=lib \
    CC="clang --target=aarch64-linux-android26" AR=llvm-ar RANLIB=llvm-ranlib
mingw32-make -j12 build_libs
mingw32-make install_dev
cd ..

# curl: OpenSSL and the platform zlib only; no SSH, IDN, PSL, HTTP/2, LDAP; CA bundle supplied by the plugin.
rm -rf $CURL $CURL-build; tar xzf "$DL/$CURL.tar.gz"
"$CMAKE" -S $CURL -B $CURL-build -G Ninja -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_FIND_ROOT_PATH="$PREFIX" \
    -DBUILD_SHARED_LIBS=OFF -DBUILD_STATIC_LIBS=ON -DBUILD_CURL_EXE=OFF -DBUILD_TESTING=OFF -DBUILD_EXAMPLES=OFF \
    -DBUILD_LIBCURL_DOCS=OFF -DBUILD_MISC_DOCS=OFF -DENABLE_CURL_MANUAL=OFF \
    -DCURL_USE_OPENSSL=ON -DOPENSSL_ROOT_DIR="$PREFIX" -DOPENSSL_USE_STATIC_LIBS=ON \
    -DCURL_USE_LIBPSL=OFF -DUSE_LIBIDN2=OFF -DCURL_USE_LIBSSH2=OFF -DCURL_USE_LIBSSH=OFF -DUSE_NGHTTP2=OFF \
    -DCURL_BROTLI=OFF -DCURL_ZSTD=OFF -DCURL_DISABLE_LDAP=ON -DCURL_DISABLE_LDAPS=ON \
    -DCURL_CA_BUNDLE=none -DCURL_CA_PATH=none
"$CMAKE" --build $CURL-build -j 12
"$CMAKE" --install $CURL-build
echo "OpenSSL 3.5.9 and curl 8.23.0 installed in $PREFIX"
