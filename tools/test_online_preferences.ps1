param([Parameter(Mandatory=$true)][string]$JsonJar,[string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$testRoot=Join-Path $project ('build\online-preferences-jvm-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $testRoot 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$java=Join-Path $project 'app\src\main\java\com\orcamobile\app'
$sources=@((Join-Path $java 'OnlineRules.java'),(Join-Path $java 'NetworkHttp.java'),(Join-Path $java 'OrcaCloudSync.java'),(Join-Path $java 'OrcaCloudPresetStore.java'),(Join-Path $PSScriptRoot 'tests\OnlineRulesTest.java'),(Join-Path $PSScriptRoot 'tests\CloudMirrorTest.java'))
$sources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$sources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\cloud_stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 --add-modules jdk.httpserver -cp $JsonJar -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Online preference tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') --add-modules jdk.httpserver -cp ($classes+';'+$JsonJar) com.orcamobile.app.OnlineRulesTest
if($LASTEXITCODE -ne 0){throw 'Online preference tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($classes+';'+$JsonJar) com.orcamobile.app.CloudMirrorTest (Join-Path $testRoot 'mirror')
if($LASTEXITCODE -ne 0){throw 'Cloud mirror tests failed'}
