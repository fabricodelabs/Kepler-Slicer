param([Parameter(Mandatory=$true)][string]$Cxx)
$ErrorActionPreference='Stop'
$stepProjectRoot = Split-Path -Parent $PSScriptRoot
$stepTestRoot = Join-Path $stepProjectRoot ('build/step-options-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $stepTestRoot | Out-Null
$stepExecutable = Join-Path $stepTestRoot 'step-options-test.exe'
& $Cxx '-std=c++17' '-Wall' '-Wextra' '-Werror' '-static' "-I$stepProjectRoot/engine/src" (Join-Path $PSScriptRoot 'tests/StepImportOptionsTest.cpp') '-o' $stepExecutable
if ($LASTEXITCODE -ne 0) { throw 'STEP options test compilation failed' }
& $stepExecutable
if ($LASTEXITCODE -ne 0) { throw 'STEP options test failed' }
# These are guard tests only. Real OCCT import/tessellation must also be tested
# on Android with both tolerance fixtures; this test does not claim that proof.
