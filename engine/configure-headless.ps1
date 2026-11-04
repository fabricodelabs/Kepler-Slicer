<# Configure the experimental Android libslic3r target. Requires all dependencies;
   no G-code or functioning APK is produced by configure alone. See README.md. #>
param(
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][string]$Ndk,
    [Parameter(Mandatory=$true)][string]$Cmake,
    [Parameter(Mandatory=$true)][string]$Ninja,
    [Parameter(Mandatory=$true)][string]$MainPrefix,
    [Parameter(Mandatory=$true)][string]$ImagePrefix,
    [Parameter(Mandatory=$true)][string]$VdbPrefix,
    [Parameter(Mandatory=$true)][string]$GmpPrefix,
    [Parameter(Mandatory=$true)][string]$DracoPrefix,
    [Parameter(Mandatory=$true)][string]$OcctPrefix,
    [Parameter(Mandatory=$true)][string]$Build
)
$ErrorActionPreference = 'Stop'
function Existing([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Required path missing: $Path" }
    (Resolve-Path -LiteralPath $Path).Path.Replace('\','/')
}
$sourcePath = Existing $Source
$ndkPath = Existing $Ndk
$cmakeExe = Existing $Cmake
$ninjaExe = Existing $Ninja
$main = Existing $MainPrefix
$images = Existing $ImagePrefix
$vdb = Existing $VdbPrefix
$gmp = Existing $GmpPrefix
$draco = Existing $DracoPrefix
$occt = Existing $OcctPrefix
$jni = Existing (Join-Path $PSScriptRoot 'src/slice_jni.cpp')
New-Item -ItemType Directory -Force -Path $Build | Out-Null
$buildPath = Existing $Build
$toolchain = Existing "$ndkPath/build/cmake/android.toolchain.cmake"
$commit = & git -C $sourcePath rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $commit -ne '6d2869c430284714f0a22570c38d3b359965a802') {
    throw 'Use the pinned official upstream checkout with android-headless.patch applied.'
}
if (-not (Select-String -LiteralPath "$sourcePath/CMakeLists.txt" -SimpleMatch 'ORCA_ANDROID_ENGINE_ONLY' -Quiet)) {
    throw 'Apply android-headless.patch to the checkout before configuring.'
}
$env:PATH = "$(Split-Path $ninjaExe);$env:PATH"
& $cmakeExe -S $sourcePath -B $buildPath -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninjaExe" `
    "-DCMAKE_TOOLCHAIN_FILE=$toolchain" '-DANDROID_ABI=arm64-v8a' '-DANDROID_PLATFORM=android-26' `
    '-DSLIC3R_GUI=OFF' '-DORCA_ANDROID_ENGINE_ONLY=ON' '-DSLIC3R_BUILD_TESTS=OFF' '-DCMAKE_BUILD_TYPE=Release' `
    '-DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH' '-DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=BOTH' `
    '-DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=BOTH' "-DCMAKE_PREFIX_PATH=$main;$images;$vdb;$gmp;$draco;$occt" `
    "-DBoost_DIR=$main/lib/cmake/Boost-1.84.0" "-DOPENSSL_INCLUDE_DIR=$main/include" `
    "-DOPENSSL_CRYPTO_LIBRARY=$main/lib/libcrypto.a" "-DOPENSSL_SSL_LIBRARY=$main/lib/libssl.a" `
    "-DCURL_LIBRARY=$main/lib/libcurl.a" "-DCURL_INCLUDE_DIR=$main/include" `
    "-DPNG_LIBRARY=$images/lib/libpng16.a" "-DPNG_PNG_INCLUDE_DIR=$images/include" `
    "-DJPEG_LIBRARY=$images/lib/libjpeg.a" "-DJPEG_INCLUDE_DIR=$images/include" `
    "-DOpenCV_DIR=$images/sdk/native/jni" "-Dassimp_DIR=$images/lib/cmake/assimp-5.4" `
    "-DOPENVDB_ROOT=$vdb" "-DOPENVDB_INCLUDEDIR=$vdb/include" "-DOPENVDB_LIBRARYDIR=$vdb/lib" `
    "-DBlosc_ROOT=$vdb" "-DIlmBase_DIR=$vdb/lib/cmake/IlmBase" "-DCGAL_DIR=$gmp/lib/cmake/CGAL" `
    "-DDraco_DIR=$draco/share/cmake/draco" "-DOpenCASCADE_DIR=$occt/lib/cmake/occt" "-DKEPLER_SLICE_JNI=$jni"
if ($LASTEXITCODE -ne 0) { throw "Configuration failed (exit $LASTEXITCODE); see CMake output." }
Write-Host 'Configured only. Compile and device verification are separate, required steps.'
