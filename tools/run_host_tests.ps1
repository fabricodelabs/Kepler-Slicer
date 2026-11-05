# Runs every JVM host test suite in tools/ (the native STEP options suite needs a C++ toolchain and is skipped).
# Local:  pwsh tools/run_host_tests.ps1 -JsonJar <org.json jar>
# CI:     the GitHub workflow passes the runner's JDK and Android platform jar.
param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }),
    [string]$AndroidJar = ''
)
$ErrorActionPreference = 'Stop'
$failed = @()
foreach ($suite in Get-ChildItem -LiteralPath $PSScriptRoot -Filter 'test_*.ps1' | Sort-Object Name) {
    if ($suite.Name -eq 'test_step_import_options.ps1') { continue }
    $params = @{ JavaHome = $JavaHome }
    $declared = (Get-Command $suite.FullName).Parameters.Keys
    if ($declared -contains 'JsonJar') { $params.JsonJar = $JsonJar }
    if ($AndroidJar -and ($declared -contains 'AndroidJar')) { $params.AndroidJar = $AndroidJar }
    Write-Host "== $($suite.Name)"
    try { & $suite.FullName @params; if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw "exit $LASTEXITCODE" } }
    catch { Write-Host "FAILED: $($suite.Name): $_"; $failed += $suite.Name }
}
if ($failed.Count -gt 0) { throw "Host test suites failed: $($failed -join ', ')" }
Write-Host 'All host test suites passed.'
