param([Parameter(Mandatory=$true)][string]$JsonJar,[string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$testRoot=Join-Path $project ('build\cloud-preset-jvm-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $testRoot 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$java=Join-Path $project 'app\src\main\java\com\orcamobile\app\settings'
$sources=@((Join-Path $java 'PresetJson.java'),(Join-Path $java 'PresetRepository.java'),(Join-Path $PSScriptRoot 'tests\CloudPresetRepositoryTest.java'))
$sources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Cloud preset tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($classes+';'+$JsonJar) com.orcamobile.app.settings.CloudPresetRepositoryTest (Join-Path $testRoot 'data')
if($LASTEXITCODE -ne 0){throw 'Cloud preset tests failed'}
