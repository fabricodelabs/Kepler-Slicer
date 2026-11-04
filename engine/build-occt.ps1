<# Build the upstream-pinned OCCT 8.0.1 for Android arm64. See README.md. #>
param(
    [Parameter(Mandatory=$true)][string]$Ndk,
    [Parameter(Mandatory=$true)][string]$Cmake,
    [Parameter(Mandatory=$true)][string]$Ninja,
    [Parameter(Mandatory=$true)][string]$UpstreamSource,
    [Parameter(Mandatory=$true)][string]$OcctSource,
    [Parameter(Mandatory=$true)][string]$Build,
    [Parameter(Mandatory=$true)][string]$Prefix,
    [int]$Jobs = 4
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
if ($Jobs -lt 1) { throw 'Jobs must be positive' }
$ndkPath = Resolve-Required $Ndk
$cmakeExe = Resolve-Required $Cmake
$ninjaExe = Resolve-Required $Ninja
$upstream = Resolve-Required $UpstreamSource
$source = Resolve-Required $OcctSource
New-Item -ItemType Directory -Force -Path $Build, $Prefix | Out-Null
$buildPath = Resolve-Required $Build
$prefixPath = Resolve-Required $Prefix
$toolchain = Resolve-Required "$ndkPath/build/cmake/android.toolchain.cmake"
$patch = Resolve-Required "$upstream/deps/OCCT/0001-BRepMesh-seam-pcurve-at-edge-parameter.patch"
& git -C $source apply --check --ignore-space-change --whitespace=fix $patch
if ($LASTEXITCODE -eq 0) { Run-Checked 'git' @('-C', $source, 'apply', '--ignore-space-change', '--whitespace=fix', $patch) }
else {
    & git -C $source apply --reverse --check --ignore-space-change --whitespace=fix $patch
    if ($LASTEXITCODE -ne 0) { throw 'OCCT source does not match the upstream patch.' }
}
$env:PATH = "$(Split-Path $ninjaExe);$env:PATH"
Run-Checked $cmakeExe @('-S', $source, '-B', $buildPath, '-G', 'Ninja', "-DCMAKE_MAKE_PROGRAM=$ninjaExe",
    "-DCMAKE_TOOLCHAIN_FILE=$toolchain", '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-26',
    '-DCMAKE_POLICY_VERSION_MINIMUM=3.10', '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_CXX_STANDARD=17',
    '-DCMAKE_POSITION_INDEPENDENT_CODE=ON', '-DBUILD_LIBRARY_TYPE=Static', '-DINSTALL_DIR_LAYOUT=Unix',
    '-DINSTALL_DIR_BIN=bin/occt', '-DINSTALL_DIR_LIB=lib/occt', '-DINSTALL_DIR_INCLUDE=include/occt',
    '-DINSTALL_DIR_CMAKE=lib/cmake/occt', '-DUSE_TK=OFF', '-DUSE_TBB=OFF', '-DUSE_FREETYPE=OFF',
    '-DUSE_FFMPEG=OFF', '-DUSE_VTK=OFF', '-DBUILD_DOC_Overview=OFF',
    '-DBUILD_MODULE_ApplicationFramework=OFF', '-DBUILD_MODULE_Draw=OFF', '-DBUILD_MODULE_FoundationClasses=OFF',
    '-DBUILD_MODULE_ModelingAlgorithms=ON', '-DBUILD_MODULE_ModelingData=OFF', '-DBUILD_MODULE_Visualization=OFF',
    "-DCMAKE_INSTALL_PREFIX=$prefixPath")
Run-Checked $cmakeExe @('--build', $buildPath, '--target', 'install', '--parallel', "$Jobs")
