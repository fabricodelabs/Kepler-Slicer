param(
    [Parameter(Mandatory=$true)][string]$OrcaSource,
    [Parameter(Mandatory=$true)][string]$AndroidNdk,
    [Parameter(Mandatory=$true)][string]$BuildRoot,
    [string]$CMake = 'cmake',
    [string]$Ninja = 'ninja',
    [int]$Jobs = 4
)
$ErrorActionPreference = 'Stop'
if ($Jobs -lt 1) { throw 'Jobs must be positive.' }
$expectedRevision = '6d2869c430284714f0a22570c38d3b359965a802'
$source = (Resolve-Path -LiteralPath $OrcaSource).Path.Replace('\', '/')
$ndk = (Resolve-Path -LiteralPath $AndroidNdk).Path.Replace('\', '/')
$revision = (& git -C $source rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $revision -ne $expectedRevision) { throw 'Official OrcaSlicer revision mismatch.' }
$toolchain = "$ndk/build/cmake/android.toolchain.cmake"
if (-not (Test-Path -LiteralPath $toolchain -PathType Leaf)) { throw 'Android NDK toolchain file not found.' }
$cmakeExecutable = (Get-Command $CMake -ErrorAction Stop).Source
$ninjaExecutable = (Get-Command $Ninja -ErrorAction Stop).Source
$root = [System.IO.Path]::GetFullPath($BuildRoot).Replace('\', '/')
$sourceDir = "$root/src/draco-1.5.7"
if (Test-Path -LiteralPath "$root/src") { throw 'BuildRoot/src already exists; choose an empty build directory.' }
New-Item -ItemType Directory -Force -Path "$root/downloads", "$root/src", "$root/prefix" | Out-Null
$archive = "$root/downloads/draco-1.5.7.zip"
$uri = 'https://github.com/google/draco/archive/refs/tags/1.5.7.zip'
Invoke-WebRequest -Uri $uri -OutFile $archive
$expectedHash = '27b72ba2d5ff3d0a9814ad40d4cb88f8dc89a35491c0866d952473f8f9416b77'
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) {
    throw 'Draco archive SHA-256 mismatch; it has not been extracted.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($archive, "$root/src")
if (-not (Test-Path -LiteralPath "$sourceDir/CMakeLists.txt" -PathType Leaf)) { throw 'Unexpected archive layout.' }
& $cmakeExecutable -S $sourceDir -B "$root/build" -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninjaExecutable" `
    "-DCMAKE_TOOLCHAIN_FILE=$toolchain" '-DANDROID_ABI=arm64-v8a' '-DANDROID_PLATFORM=android-26' `
    '-DCMAKE_BUILD_TYPE=Release' '-DBUILD_SHARED_LIBS=OFF' '-DCMAKE_POLICY_VERSION_MINIMUM=3.10' `
    "-DCMAKE_INSTALL_PREFIX=$root/prefix" "-DCMAKE_PREFIX_PATH=$root/prefix"
if ($LASTEXITCODE -ne 0) { throw 'Draco configure failed.' }
& $cmakeExecutable --build "$root/build" --target install -j $Jobs
if ($LASTEXITCODE -ne 0) { throw 'Draco build/install failed.' }
$probe = (Resolve-Path -LiteralPath "$PSScriptRoot/probe").Path.Replace('\', '/')
& $cmakeExecutable -S $probe -B "$root/probe-build" -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninjaExecutable" `
    "-DCMAKE_TOOLCHAIN_FILE=$toolchain" '-DANDROID_ABI=arm64-v8a' '-DANDROID_PLATFORM=android-26' `
    '-DCMAKE_BUILD_TYPE=Release' "-DCMAKE_PREFIX_PATH=$root/prefix" "-DCMAKE_FIND_ROOT_PATH=$root/prefix" `
    "-DCMAKE_MODULE_PATH=$source/cmake/modules"
if ($LASTEXITCODE -ne 0) { throw 'Draco link-check configure failed.' }
& $cmakeExecutable --build "$root/probe-build" -j $Jobs
if ($LASTEXITCODE -ne 0) { throw 'Draco link check failed.' }
Write-Output "Draco installed in $root/prefix. Link-check built; no Android runtime test was run."
