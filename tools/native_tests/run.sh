#!/bin/sh
# Builds tools/native_tests for the x86_64 emulator with the NDK and runs them over adb.
# usage: sh tools/native_tests/run.sh <ndk dir> <adb>
set -e
export MSYS_NO_PATHCONV=1   # Git Bash: keep device paths as they are
NDK=$1; ADB=$2; ROOT=$(cd "$(dirname "$0")/../.." && (pwd -W 2>/dev/null || pwd))
CXX="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe"; CC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
OUT=$(mktemp -d); OUT=$(cd "$OUT" && (pwd -W 2>/dev/null || pwd)); E=$ROOT/engine/third_party/expat
for f in xcs xmlparse xmlrole xmltok random_arc4random_buf random_dev_urandom; do
  "$CC" --target=x86_64-linux-android33 -O1 -fsanitize=undefined -fno-sanitize-recover=all -DHAVE_EXPAT_CONFIG_H -DXML_STATIC -I"$E" -c "$E/$f.c" -o "$OUT/$f.o"
done
"$CXX" --target=x86_64-linux-android33 -std=c++17 -O1 -fsanitize=undefined -fno-sanitize-recover=all -static-libstdc++ -DXML_STATIC -I"$E" "$ROOT/tools/native_tests/native_safety_test.cpp" "$OUT"/*.o -o "$OUT/native_safety_test"
"$ADB" push "$OUT/native_safety_test" /data/local/tmp/ >/dev/null
"$ADB" shell chmod 755 /data/local/tmp/native_safety_test
"$ADB" shell /data/local/tmp/native_safety_test
