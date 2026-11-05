param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$JavaHome='C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$generalRoot=Split-Path -Parent $PSScriptRoot
$generalTest=Join-Path $generalRoot ('build\general-jvm-'+[guid]::NewGuid().ToString('N'))
$generalClasses=Join-Path $generalTest 'classes'
New-Item -ItemType Directory -Path $generalClasses -Force | Out-Null
$generalJava=Join-Path $generalRoot 'app\src\main\java\com\keplerslicer\app'
$generalSources=@('GeneralBackupArchive.java','GeneralProjectPolicy.java','ProjectMaterialValidation.java','ModelTransform.java') | ForEach-Object {Join-Path $generalJava $_}
$generalSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\general_stubs') -Recurse -Filter '*.java' | ForEach-Object FullName
$generalSources+=Join-Path $PSScriptRoot 'tests\GeneralPreferencesTest.java'
$generalSources+=(Join-Path (Split-Path -Parent $PSScriptRoot) 'app\src\main\java\com\keplerslicer\app\L.java')
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $generalClasses @generalSources
if($LASTEXITCODE -ne 0){throw 'General preference host tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($generalClasses+';'+$JsonJar) com.keplerslicer.app.GeneralPreferencesTest (Join-Path $generalTest 'data')
if($LASTEXITCODE -ne 0){throw 'General preference host tests failed'}
$restoreClasses=Join-Path $generalTest 'restore-classes'
New-Item -ItemType Directory -Path $restoreClasses -Force | Out-Null
$restoreSources=@('CurrentModelStore.java','ProjectMaterialValidation.java','ModelTransform.java') | ForEach-Object {Join-Path $generalJava $_}
$restoreSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Recurse -Filter '*.java' | Where-Object {$_.FullName -notmatch '[\\/]net[\\/]Uri.java$'} | ForEach-Object FullName
$restoreSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\model_restore_stubs') -Recurse -Filter '*.java' | ForEach-Object FullName
$restoreSources+=Join-Path $PSScriptRoot 'tests\ModelRestoreTest.java'
$restoreSources+=(Join-Path (Split-Path -Parent $PSScriptRoot) 'app\src\main\java\com\keplerslicer\app\L.java')
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $restoreClasses @restoreSources
if($LASTEXITCODE -ne 0){throw 'Model restore host tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($restoreClasses+';'+$JsonJar) com.keplerslicer.app.ModelRestoreTest (Join-Path $generalTest 'restore-data')
if($LASTEXITCODE -ne 0){throw 'Model restore host tests failed'}
