<#
Builds open-bamboo-networking (https://github.com/ClusterM/open-bamboo-networking, AGPL-3.0) for Android arm64 as
libbambu_networking.so and publishes it next to the engine library. The upstream build supports desktop systems
only; for Android the curl dependency is taken from the prebuilt engine prefix instead of pkg-config.

  pwsh -File engine/obn/build-obn.ps1 -Source <open-bamboo-networking checkout> -Prefix <android prefix with
       openssl and curl> -Uthash <uthash checkout> -Cmake <cmake.exe> -Ninja <ninja.exe> -Ndk <android-ndk-r30>
       -Build <new build directory> [-PublishToApp]
#>
param(
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][string]$Prefix,
    [Parameter(Mandatory=$true)][string]$Uthash,
    [Parameter(Mandatory=$true)][string]$Cmake,
    [Parameter(Mandatory=$true)][string]$Ninja,
    [Parameter(Mandatory=$true)][string]$Ndk,
    [Parameter(Mandatory=$true)][string]$Build,
    [switch]$PublishToApp
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Source = (Resolve-Path -LiteralPath $Source).Path; $Prefix = (Resolve-Path -LiteralPath $Prefix).Path
$Uthash = (Resolve-Path -LiteralPath $Uthash).Path; $Ndk = (Resolve-Path -LiteralPath $Ndk).Path
$Cmake = (Resolve-Path -LiteralPath $Cmake).Path; $Ninja = (Resolve-Path -LiteralPath $Ninja).Path
New-Item -ItemType Directory -Force -Path $Build | Out-Null; $Build = (Resolve-Path -LiteralPath $Build).Path

# Android branch for the curl/pkg-config parts of the upstream CMakeLists (idempotent).
$lists = Join-Path $Source 'CMakeLists.txt'
$text = (Get-Content -LiteralPath $lists -Raw).Replace("`r`n", "`n")
if ($text -notmatch 'OBN_ANDROID_CURL') {
    $text = $text.Replace("else()`n    find_package(PkgConfig REQUIRED)", "else()`n    if(NOT ANDROID)`n        find_package(PkgConfig REQUIRED)`n    endif()")
    $text = $text.Replace("else()`n    pkg_check_modules(CURL REQUIRED IMPORTED_TARGET libcurl)", "elseif(ANDROID)`n    # OBN_ANDROID_CURL: static libcurl of the Kepler engine prefix`n    target_include_directories(obn_curl_iface INTERFACE `"`${OBN_ANDROID_PREFIX}/include`")`n    target_link_libraries(obn_curl_iface INTERFACE `"`${OBN_ANDROID_PREFIX}/lib/libcurl.a`" OpenSSL::SSL OpenSSL::Crypto ZLIB::ZLIB)`nelse()`n    pkg_check_modules(CURL REQUIRED IMPORTED_TARGET libcurl)")
    if ($text -notmatch 'OBN_ANDROID_CURL') { throw 'Upstream CMakeLists changed: Android patch did not apply' }
    # Kepler JNI bridge lives in the same library (shared C++ runtime with the plugin ABI).
    $text += "`nif(ANDROID AND OBN_KEPLER_JNI)`n    target_sources(bambu_networking PRIVATE `"`${OBN_KEPLER_JNI}`")`nendif()`n"
    Set-Content -LiteralPath $lists -Value $text -NoNewline -Encoding utf8NoBOM
}
# Android, like Windows, has no OpenSSL trust store: compile in the vendored root bundle and use it for the
# cloud broker (*.bambulab.com, publicly trusted), with full chain and host name checks.
$text = (Get-Content -LiteralPath $lists -Raw).Replace("`r`n", "`n")
if ($text -notmatch 'if\(WIN32 OR ANDROID\)\n    set\(_obn_cacert_path') {
    $text = $text.Replace("if(WIN32)`n    set(_obn_cacert_path", "if(WIN32 OR ANDROID)`n    set(_obn_cacert_path")
    if ($text -notmatch 'if\(WIN32 OR ANDROID\)') { throw 'Upstream CMakeLists changed: CA bundle patch did not apply' }
    Set-Content -LiteralPath $lists -Value $text -NoNewline -Encoding utf8NoBOM
}
$agent = Join-Path $Source 'src/agent.cpp'
$text = (Get-Content -LiteralPath $agent -Raw).Replace("`r`n", "`n")
if ($text -notmatch '__ANDROID__\)\n    cloud_ca = ') {
    $text = $text.Replace("#if defined(_WIN32)`n    cloud_ca = ", "#if defined(_WIN32) || defined(__ANDROID__)`n    cloud_ca = ")
    if ($text -notmatch '__ANDROID__\)\n    cloud_ca = ') { throw 'Upstream agent.cpp changed: cloud CA patch did not apply' }
    Set-Content -LiteralPath $agent -Value $text -NoNewline -Encoding utf8NoBOM
}

& $Cmake -S $Source -B $Build -G Ninja "-DCMAKE_MAKE_PROGRAM=$Ninja" `
    "-DCMAKE_TOOLCHAIN_FILE=$Ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 `
    -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release `
    "-DOBN_ANDROID_PREFIX=$Prefix" "-DOPENSSL_ROOT_DIR=$Prefix" -DOPENSSL_USE_STATIC_LIBS=TRUE `
    "-DCMAKE_FIND_ROOT_PATH=$Prefix" "-DCMAKE_C_FLAGS=-I$Uthash/src -DSSL_get1_peer_certificate=SSL_get_peer_certificate" "-DCMAKE_CXX_FLAGS=-I$Uthash/src -DSSL_get1_peer_certificate=SSL_get_peer_certificate" `
    "-DOBN_KEPLER_JNI=$(Join-Path $PSScriptRoot 'kepler_obn_jni.cpp')" `
    -DOBN_CLIENT_TYPE=orca_slicer "-DOBN_VERSION=02.08.04.99" -DOBN_INSTALL_CONF=OFF -DOBN_ENABLE_TESTS=OFF
if ($LASTEXITCODE -ne 0) { throw 'open-bamboo-networking configure failed' }
& $Cmake --build $Build --target bambu_networking -j 12
if ($LASTEXITCODE -ne 0) { throw 'open-bamboo-networking build failed' }
$lib = Get-ChildItem -LiteralPath $Build -Recurse -Filter 'libbambu_networking*.so' | Select-Object -First 1
if (-not $lib) { throw 'libbambu_networking.so was not produced' }
$strip = Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
$out = Join-Path $Build 'stripped/libbambu_networking.so'; New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null; Copy-Item -LiteralPath $lib.FullName -Destination $out -Force; & $strip --strip-unneeded $out
Write-Host "Built: $out ($([math]::Round((Get-Item $out).Length / 1MB, 1)) MB)"
if ($PublishToApp) {
    $dest = Join-Path $root 'engine/jniLibs/arm64-v8a/libbambu_networking.so'
    Copy-Item -LiteralPath $out -Destination $dest -Force
    Write-Host "Published to app: $dest"
}
