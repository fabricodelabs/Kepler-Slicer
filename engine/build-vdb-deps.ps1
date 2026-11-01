<#
Build Blosc, OpenEXR/IlmBase and OpenVDB for Android arm64, from the
upstream-pinned archives described in README.md. This is experimental and
does not build libslic3r. Supply clean, extracted sources; OpenVDB is patched
with the patch from the pinned official OrcaSlicer checkout.
#>
param(
    [Parameter(Mandatory=$true)][string]$Ndk,
    [Parameter(Mandatory=$true)][string]$Cmake,
    [Parameter(Mandatory=$true)][string]$Ninja,
    [Parameter(Mandatory=$true)][string]$OrcaSource,
    [Parameter(Mandatory=$true)][string]$Sources,
    [Parameter(Mandatory=$true)][string]$BoostPrefix,
    [Parameter(Mandatory=$true)][string]$Work,
    [Parameter(Mandatory=$true)][string]$Prefix
)
$ErrorActionPreference = 'Stop'
function Resolve-Required([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Required path missing: $Path" }
    (Resolve-Path -LiteralPath $Path).Path.Replace('\','/')
}
function Run-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed: $Exe (exit $LASTEXITCODE)" }
}
$ndkPath = Resolve-Required $Ndk
$cmakeExe = Resolve-Required $Cmake
$ninjaExe = Resolve-Required $Ninja
$sourceRoot = Resolve-Required $Sources
$boost = Resolve-Required $BoostPrefix
$orca = Resolve-Required $OrcaSource
New-Item -ItemType Directory -Force -Path $Work, $Prefix | Out-Null
$workPath = Resolve-Required $Work
$prefixPath = Resolve-Required $Prefix
$toolchain = Resolve-Required "$ndkPath/build/cmake/android.toolchain.cmake"
$env:PATH = "$(Split-Path $ninjaExe);$env:PATH"
$common = @('-G','Ninja', "-DCMAKE_MAKE_PROGRAM=$ninjaExe", "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
    '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-26', '-DCMAKE_BUILD_TYPE=Release',
    '-DCMAKE_POLICY_VERSION_MINIMUM=3.10', '-DCMAKE_POSITION_INDEPENDENT_CODE=ON',
    "-DCMAKE_INSTALL_PREFIX=$prefixPath")
function Build-Cmake([string]$Name, [string]$Folder, [string[]]$Options) {
    $source = Resolve-Required "$sourceRoot/$Folder"
    $build = "$workPath/$Name-arm64"
    Run-Checked $cmakeExe (@('-S', $source, '-B', $build) + $common + $Options)
    Run-Checked $cmakeExe @('--build', $build, '--target', 'install', '--parallel', '4')
}
Build-Cmake 'blosc' 'c-blosc-1.17.0_tm' @('-DBUILD_SHARED=OFF', '-DBUILD_STATIC=ON',
    '-DBUILD_TESTS=OFF', '-DBUILD_BENCHMARKS=OFF', '-DPREFER_EXTERNAL_ZLIB=ON')
Build-Cmake 'openexr' 'openexr-2.5.5' @('-DBUILD_SHARED_LIBS=OFF', '-DBUILD_TESTING=OFF',
    '-DPYILMBASE_ENABLE=OFF', '-DOPENEXR_VIEWERS_ENABLE=OFF', '-DOPENEXR_BUILD_UTILS=OFF')
$vdb = Resolve-Required "$sourceRoot/openvdb-a68fd58d0e2b85f01adeb8b13d7555183ab10aa5"
$patch = Resolve-Required "$orca/deps/OpenVDB/0001-clang19.patch"
& git -C $vdb apply --check --ignore-space-change --whitespace=fix $patch
if ($LASTEXITCODE -eq 0) { Run-Checked 'git' @('-C', $vdb, 'apply', '--ignore-space-change', '--whitespace=fix', $patch) }
else {
    & git -C $vdb apply --reverse --check --ignore-space-change --whitespace=fix $patch
    if ($LASTEXITCODE -ne 0) { throw 'OpenVDB source does not match the upstream patch.' }
}
Build-Cmake 'openvdb' 'openvdb-a68fd58d0e2b85f01adeb8b13d7555183ab10aa5' @(
    '-DBUILD_SHARED_LIBS=OFF', '-DOPENVDB_BUILD_PYTHON_MODULE=OFF', '-DUSE_BLOSC=ON',
    '-DOPENVDB_CORE_SHARED=OFF', '-DOPENVDB_CORE_STATIC=ON', '-DOPENVDB_ENABLE_RPATH=OFF',
    '-DTBB_STATIC=ON', '-DOPENVDB_BUILD_VDB_PRINT=OFF', '-DOPENVDB_BUILD_UNITTESTS=OFF',
    '-DDISABLE_DEPENDENCY_VERSION_CHECKS=ON', '-DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH',
    '-DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=BOTH', '-DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=BOTH',
    "-DCMAKE_PREFIX_PATH=$boost;$prefixPath", "-DBoost_DIR=$boost/lib/cmake/Boost-1.84.0",
    "-DTBB_ROOT=$boost", "-DTbb_INCLUDE_DIR=$boost/include", "-DTbb_tbb_LIBRARY_RELEASE=$boost/lib/libtbb.a",
    "-DTbb_tbbmalloc_LIBRARY_RELEASE=$boost/lib/libtbbmalloc.a", "-DBlosc_ROOT=$prefixPath", "-DOPENEXR_ROOT=$prefixPath")
