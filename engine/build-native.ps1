<# Build the configured, pinned Android engine and stage a verified ARM64 JNI
   library. SDK/dependencies must already be cross-built; see README.md and
   configure-headless.ps1. Publishing is explicit and preserves the old binary. #>
param(
    [Parameter(Mandatory=$true)][string]$Build,
    [Parameter(Mandatory=$true)][string]$Cmake,
    [Parameter(Mandatory=$true)][string]$Ndk,
    [ValidateRange(1,64)][int]$Parallel=2,
    [switch]$PublishToApp
)
$ErrorActionPreference='Stop'
$nativeProjectRoot = Split-Path -Parent $PSScriptRoot
$nativeBuildRoot = (Resolve-Path -LiteralPath $Build).Path
$nativeCmake = (Resolve-Path -LiteralPath $Cmake).Path
$nativeNdk = (Resolve-Path -LiteralPath $Ndk).Path
$nativeCache = Get-Content -LiteralPath (Join-Path $nativeBuildRoot 'CMakeCache.txt') -Raw
function CacheValue([string]$Name) {
    $nativeMatch = [regex]::Match($nativeCache, '(?m)^'+[regex]::Escape($Name)+':[^=]+=([^\r\n]+)')
    if (-not $nativeMatch.Success) { throw "Native CMake cache lacks $Name" }
    $nativeMatch.Groups[1].Value
}
$nativeSourceRoot = CacheValue 'CMAKE_HOME_DIRECTORY'
$nativeJni = CacheValue 'KEPLER_SLICE_JNI'
if ((CacheValue 'ANDROID_ABI') -ne 'arm64-v8a' -or (CacheValue 'ANDROID_PLATFORM') -ne 'android-26') {
    throw 'This native delivery requires arm64-v8a at Android API26'
}
if ((Resolve-Path -LiteralPath $nativeJni).Path -ne (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'src/slice_jni.cpp')).Path) {
    throw 'The configured JNI bridge is not this source tree'
}
if ((& git -C $nativeSourceRoot rev-parse HEAD) -ne '6d2869c430284714f0a22570c38d3b359965a802') {
    throw 'The configured source is not the pinned upstream revision'
}
$nativeTools = Join-Path $nativeNdk 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$nativeReadelf = Join-Path $nativeTools 'llvm-readelf.exe'
$nativeStrip = Join-Path $nativeTools 'llvm-strip.exe'
$nativeStage = Join-Path $nativeBuildRoot 'kepler-stage'
New-Item -ItemType Directory -Force -Path $nativeStage | Out-Null
$nativeLog = Join-Path $nativeStage 'build.log'
$nativeBridgeFiles = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -File | Where-Object { $_.Extension -in @('.cpp','.hpp','.inc') } | Sort-Object Name)
$nativeSourceFingerprints = @($nativeBridgeFiles | ForEach-Object { [ordered]@{path=('engine/src/'+$_.Name);sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
& $nativeCmake -S $nativeSourceRoot -B $nativeBuildRoot '-DCMAKE_EXPORT_COMPILE_COMMANDS=ON' 2>&1 | Tee-Object -FilePath $nativeLog
if ($LASTEXITCODE -ne 0) { throw 'Native CMake regeneration failed' }
& $nativeCmake --build $nativeBuildRoot --target kepler_slicer_experimental --parallel $Parallel 2>&1 | Tee-Object -FilePath $nativeLog -Append
if ($LASTEXITCODE -ne 0) { throw 'Native JNI build failed' }
$nativeUnstripped = Join-Path $nativeBuildRoot 'src/libkepler_slicer_experimental.so'
$nativeHeader = (& $nativeReadelf -h $nativeUnstripped) -join "`n"
if ($LASTEXITCODE -ne 0 -or $nativeHeader -notmatch 'Machine:\s+AArch64' -or $nativeHeader -notmatch 'Type:\s+DYN') {
    throw 'Native output is not an AArch64 shared library'
}
$nativeDynamicSymbols = (& $nativeReadelf --dyn-syms --wide $nativeUnstripped) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect native exported symbols' }
foreach ($nativeRequired in @('sliceModelWithSettings','processSettingsMetadata','prepareStep')) {
    if ($nativeDynamicSymbols -notmatch ('(?m)Java_com_keplerslicer_engine_ExperimentalSlicer_'+$nativeRequired+'\s*$')) {
        throw "Missing required JNI export: $nativeRequired"
    }
}
foreach ($nativeClass in @('ExperimentalSlicer','NativeModelIO')) {
    $nativeDeclarationFile = Join-Path $nativeProjectRoot ('app/src/main/java/com/keplerslicer/engine/'+$nativeClass+'.java')
    $nativeDeclarations = [regex]::Matches((Get-Content -LiteralPath $nativeDeclarationFile -Raw),'public\s+static\s+native\s+[\w\[\]]+\s+(\w+)\s*\(')
    if ($nativeDeclarations.Count -eq 0) { throw "No JNI declarations found for $nativeClass" }
    foreach ($nativeDeclaration in $nativeDeclarations) {
        $nativeExpectedExport = 'Java_com_keplerslicer_engine_'+$nativeClass+'_'+$nativeDeclaration.Groups[1].Value
        if ($nativeDynamicSymbols -notmatch ('(?m)'+[regex]::Escape($nativeExpectedExport)+'\s*$')) {
            throw "APK declares an unlinked native method: $nativeExpectedExport"
        }
    }
}
foreach ($nativeSourceFingerprint in $nativeSourceFingerprints) {
    $nativeSourceFile = Join-Path $nativeProjectRoot $nativeSourceFingerprint.path
    if ((Get-FileHash -LiteralPath $nativeSourceFile -Algorithm SHA256).Hash -ne $nativeSourceFingerprint.sha256) {
        throw "Native source changed during compilation; rebuild required: $($nativeSourceFingerprint.path)"
    }
}
$nativeStaged = Join-Path $nativeStage 'libkepler_slicer_experimental.so'
$nativePartial = Join-Path $nativeStage ('libkepler_slicer_experimental.'+[guid]::NewGuid().ToString('N')+'.so')
try {
    & $nativeStrip --strip-unneeded $nativeUnstripped -o $nativePartial
    if ($LASTEXITCODE -ne 0) { throw 'Native debug-symbol strip failed; unstripped output remains available' }
    $nativeStagedHeader = (& $nativeReadelf -h $nativePartial) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $nativeStagedHeader -notmatch 'Machine:\s+AArch64') { throw 'Invalid staged native ELF' }
    Move-Item -LiteralPath $nativePartial -Destination $nativeStaged -Force
} finally {
    if (Test-Path -LiteralPath $nativePartial) { Remove-Item -LiteralPath $nativePartial }
}
$nativeHash = (Get-FileHash -LiteralPath $nativeStaged -Algorithm SHA256).Hash
$nativeManifest = [ordered]@{
    schema='kepler.native-build.v1'; abi='arm64-v8a'; android_api=26
    upstream_revision='6d2869c430284714f0a22570c38d3b359965a802'
    sha256=$nativeHash; bytes=(Get-Item -LiteralPath $nativeStaged).Length
    unstripped_sha256=(Get-FileHash -LiteralPath $nativeUnstripped -Algorithm SHA256).Hash
    debug_symbols_stripped=$true; source_tree=(Resolve-Path -LiteralPath $nativeProjectRoot).Path
    bridge_sources=$nativeSourceFingerprints
    headless_patch_sha256=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'android-headless.patch') -Algorithm SHA256).Hash
    source_project_commit=(& git -C $nativeProjectRoot rev-parse HEAD)
    source_project_dirty=[bool]((& git -C $nativeProjectRoot status --porcelain=v1).Count)
    exported_jni=@([regex]::Matches($nativeDynamicSymbols,'Java_com_keplerslicer_engine_[A-Za-z0-9_]+') | ForEach-Object Value | Sort-Object -Unique)
    published_to_app=[bool]$PublishToApp; built_utc=[datetime]::UtcNow.ToString('o')
}
if ($PublishToApp) {
    $nativeAppFile = Join-Path $nativeProjectRoot 'engine/jniLibs/arm64-v8a/libkepler_slicer_experimental.so'
    if (Test-Path -LiteralPath $nativeAppFile) {
        $nativeOldHash = (Get-FileHash -LiteralPath $nativeAppFile -Algorithm SHA256).Hash
        $nativeBaselineRoot = Join-Path (Split-Path -Parent $nativeProjectRoot) ('Kepler-Native-Phase2-baseline/'+$nativeOldHash)
        New-Item -ItemType Directory -Force -Path $nativeBaselineRoot | Out-Null
        $nativeBaseline = Join-Path $nativeBaselineRoot 'libkepler_slicer_experimental.so'
        if (Test-Path -LiteralPath $nativeBaseline) {
            if ((Get-FileHash -LiteralPath $nativeBaseline -Algorithm SHA256).Hash -ne $nativeOldHash) { throw 'Native baseline preservation hash mismatch' }
        } else { Copy-Item -LiteralPath $nativeAppFile -Destination $nativeBaseline }
        $nativeManifest.previous_app_sha256=$nativeOldHash
    }
    Copy-Item -LiteralPath $nativeStaged -Destination $nativeAppFile
    if ((Get-FileHash -LiteralPath $nativeAppFile -Algorithm SHA256).Hash -ne $nativeHash) { throw 'Native publish hash mismatch' }
}
$nativeManifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $nativeStage 'native-build.json') -Encoding utf8
Write-Host "Verified JNI: $nativeStaged"
Write-Host "SHA-256: $nativeHash"
Write-Host "Published to app: $PublishToApp"
