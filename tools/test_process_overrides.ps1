param([Parameter(Mandatory=$true)][string]$JsonJar,[string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$processProjectRoot=Split-Path -Parent $PSScriptRoot
$processTestRoot=Join-Path $processProjectRoot ('build\process-overrides-jvm-'+[guid]::NewGuid().ToString('N'))
$processClasses=Join-Path $processTestRoot 'classes'
New-Item -ItemType Directory -Path $processClasses -Force | Out-Null
$processJavaRoot=Join-Path $processProjectRoot 'app\src\main\java\com\keplerslicer\app'
$processSources=@((Join-Path $processJavaRoot 'ProcessOverridesStore.java'),(Join-Path $processJavaRoot 'ResultFingerprint.java'))
$processSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$processSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\process_stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$processSources+=Join-Path $PSScriptRoot 'tests\ProcessOverridesStoreTest.java'
$processSources+=(Join-Path (Split-Path -Parent $PSScriptRoot) 'core\src\main\java\com\keplerslicer\app\L.java')
# Sources moved into the core/engine modules are found at their new place.
$processSources=@($processSources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $processClasses @processSources
if($LASTEXITCODE -ne 0){throw 'Process overrides tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($processClasses+';'+$JsonJar) com.keplerslicer.app.ProcessOverridesStoreTest (Join-Path $processTestRoot 'data')
if($LASTEXITCODE -ne 0){throw 'Process overrides tests failed'}
