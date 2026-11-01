<#
Build upstream-pinned Boost, Eigen, oneTBB, cereal, NLopt, OpenSSL and curl.
Use a fresh working directory and verified, extracted source archives.
This builds dependencies only; it does not build libslic3r or Kepler Slicer.
See README.md for source URLs, SHA-256 values and limitations.
#>
param(
    [Parameter(Mandatory=$true)][string]$Ndk,
    [Parameter(Mandatory=$true)][string]$Cmake,
    [Parameter(Mandatory=$true)][string]$Ninja,
    [Parameter(Mandatory=$true)][string]$GitUnixBin,
    [Parameter(Mandatory=$true)][string]$Make,
    [Parameter(Mandatory=$true)][string]$PerlLibRoot,
    [Parameter(Mandatory=$true)][string]$Sources,
    [Parameter(Mandatory=$true)][string]$Work,
    [Parameter(Mandatory=$true)][string]$Prefix
)
$ErrorActionPreference = 'Stop'

function Require-Path([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Required path missing: $Path" }
    return (Resolve-Path -LiteralPath $Path).Path
}
function To-Msys([string]$Path) {
    $p = (Require-Path $Path).Replace('\','/')
    if ($p -notmatch '^([A-Za-z]):/(.*)$') { throw "Expected a drive path: $p" }
    return "/$($Matches[1].ToLower())/$($Matches[2])"
}
function Run-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed: $Exe (exit $LASTEXITCODE)" }
}

$ndkPath = Require-Path $Ndk
$cmakeExe = Require-Path $Cmake
$ninjaExe = Require-Path $Ninja
$gitBin = Require-Path $GitUnixBin
$makeExe = Require-Path $Make
$perlRoot = Require-Path $PerlLibRoot
$sourceRoot = Require-Path $Sources
New-Item -ItemType Directory -Force -Path $Work, $Prefix | Out-Null
$workPath = (Resolve-Path -LiteralPath $Work).Path
$prefixPath = (Resolve-Path -LiteralPath $Prefix).Path.Replace('\','/')
$toolchain = (Require-Path (Join-Path $ndkPath 'build/cmake/android.toolchain.cmake')).Replace('\','/')
$perlExe = Require-Path (Join-Path $gitBin 'perl.exe')
$ndkBin = Require-Path (Join-Path $ndkPath 'toolchains/llvm/prebuilt/windows-x86_64/bin')
$env:PATH = "$(Split-Path $ninjaExe);$ndkBin;$gitBin;$(Split-Path $makeExe);$env:PATH"
$common = @('-G','Ninja', "-DCMAKE_MAKE_PROGRAM=$ninjaExe", "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
    '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-26', '-DCMAKE_BUILD_TYPE=Release',
    '-DCMAKE_POSITION_INDEPENDENT_CODE=ON', "-DCMAKE_INSTALL_PREFIX=$prefixPath")

function Build-Cmake([string]$Name, [string]$Folder, [string[]]$Options) {
    $source = Require-Path (Join-Path $sourceRoot $Folder)
    $build = Join-Path $workPath "$Name-arm64"
    Run-Checked $cmakeExe (@('-S', $source, '-B', $build) + $common + $Options)
    Run-Checked $cmakeExe @('--build', $build, '--target', 'install', '--parallel', '4')
}

Build-Cmake 'boost' 'boost-1.84.0' @('-DBUILD_SHARED_LIBS=OFF', '-DBUILD_TESTING=OFF',
    '-DBOOST_LOCALE_ENABLE_ICU=OFF', '-DBOOST_IOSTREAMS_ENABLE_BZIP2=OFF',
    '-DBOOST_IOSTREAMS_ENABLE_ZSTD=OFF', '-DBOOST_EXCLUDE_LIBRARIES=contract;fiber;numpy;stacktrace;wave;test')
Build-Cmake 'eigen' 'eigen-5.0.1' @('-DEIGEN_BUILD_TESTING=OFF', '-DEIGEN_BUILD_BLAS=OFF', '-DEIGEN_BUILD_LAPACK=OFF')
Build-Cmake 'tbb' 'oneTBB-2021.5.0' @('-DCMAKE_POLICY_VERSION_MINIMUM=3.10', '-DBUILD_SHARED_LIBS=OFF',
    '-DTBB_TEST=OFF', '-DTBB_DISABLE_HWLOC_AUTOMATIC_SEARCH=ON', '-DTBB_ENABLE_IPO=OFF',
    '-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=OFF', '-DCMAKE_DEBUG_POSTFIX=_debug')
Build-Cmake 'cereal' 'cereal-1.3.0' @('-DCMAKE_POLICY_VERSION_MINIMUM=3.10', '-DJUST_INSTALL_CEREAL=ON',
    '-DSKIP_PERFORMANCE_COMPARISON=ON', '-DBUILD_TESTS=OFF')
Build-Cmake 'nlopt' 'nlopt-2.5.0' @('-DCMAKE_POLICY_VERSION_MINIMUM=3.10', '-DBUILD_SHARED_LIBS=OFF',
    '-DNLOPT_PYTHON=OFF', '-DNLOPT_OCTAVE=OFF', '-DNLOPT_MATLAB=OFF', '-DNLOPT_GUILE=OFF',
    '-DNLOPT_SWIG=OFF', '-DNLOPT_TESTS=OFF')

$env:ANDROID_NDK_HOME = To-Msys $ndkPath
$env:PERL5LIB = "$(To-Msys (Join-Path $perlRoot 'usr/lib/perl5/core_perl')):$(To-Msys (Join-Path $perlRoot 'usr/share/perl5/core_perl'))"
$prefixMsys = To-Msys $prefixPath
$openssl = Require-Path (Join-Path $sourceRoot 'openssl-OpenSSL_1_1_1w')
Push-Location $openssl
try {
    Run-Checked $perlExe @('Configure', 'android-arm64', '-D__ANDROID_API__=26', "--prefix=$prefixMsys",
        '--libdir=lib', 'no-shared', 'no-asm', 'no-ssl3-method', 'no-dynamic-engine')
    Run-Checked $makeExe @('-j4', 'build_libs')
    Run-Checked $makeExe @('-j4', 'install_dev')
} finally { Pop-Location }

Build-Cmake 'curl' 'curl-curl-7_75_0' @('-DCMAKE_POLICY_VERSION_MINIMUM=3.10', '-DBUILD_SHARED_LIBS=OFF',
    '-DBUILD_TESTING=OFF', '-DBUILD_CURL_EXE=OFF', '-DCURL_STATICLIB=ON', '-DCMAKE_USE_OPENSSL=ON',
    "-DOPENSSL_ROOT_DIR=$prefixPath", "-DOPENSSL_INCLUDE_DIR=$prefixPath/include",
    "-DOPENSSL_CRYPTO_LIBRARY=$prefixPath/lib/libcrypto.a", "-DOPENSSL_SSL_LIBRARY=$prefixPath/lib/libssl.a",
    '-DENABLE_IPV6=ON', '-DENABLE_VERSIONED_SYMBOLS=ON', '-DENABLE_THREADED_RESOLVER=ON', '-DENABLE_MANUAL=OFF',
    '-DCURL_DISABLE_LDAP=ON', '-DCURL_DISABLE_LDAPS=ON', '-DCURL_DISABLE_RTSP=ON', '-DCURL_DISABLE_DICT=ON',
    '-DCURL_DISABLE_TELNET=ON', '-DCURL_DISABLE_POP3=ON', '-DCURL_DISABLE_IMAP=ON', '-DCURL_DISABLE_SMB=ON',
    '-DCURL_DISABLE_SMTP=ON', '-DCURL_DISABLE_GOPHER=ON', '-DCURL_DISABLE_TFTP=ON', '-DCURL_DISABLE_MQTT=ON',
    '-DCMAKE_USE_GSSAPI=OFF', '-DCMAKE_USE_LIBSSH2=OFF', '-DUSE_RTMP=OFF', '-DUSE_NGHTTP2=OFF', '-DUSE_MBEDTLS=OFF')
