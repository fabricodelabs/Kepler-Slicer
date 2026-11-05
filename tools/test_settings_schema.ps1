param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$JavaHome='C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$schemaProjectRoot=Split-Path -Parent $PSScriptRoot
$schemaClasses=Join-Path $schemaProjectRoot ('build\schema-jvm-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $schemaClasses -Force | Out-Null
$schemaJava=Join-Path $schemaProjectRoot 'app\src\main\java'
$schemaSources=@(
    (Join-Path $schemaJava 'com\keplerslicer\app\settings\SettingsSchema.java'),
    (Join-Path $schemaJava 'com\keplerslicer\app\EngineText.java'),
    (Join-Path $schemaJava 'com\keplerslicer\app\ProfileIniCodec.java'),
    (Join-Path $PSScriptRoot 'tests\SettingsSchemaTest.java'),
    (Join-Path $PSScriptRoot 'tests\ProfileIniCodecTest.java')
)
$schemaSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$schemaSources+=(Join-Path (Split-Path -Parent $PSScriptRoot) 'core\src\main\java\com\keplerslicer\app\L.java')
# Sources moved into the core/engine modules are found at their new place.
$schemaSources=@($schemaSources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $schemaClasses @schemaSources
if($LASTEXITCODE -ne 0){throw 'Schema tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($schemaClasses+';'+$JsonJar) SettingsSchemaTest (Join-Path $schemaProjectRoot 'app\src\main\assets\kepler_settings_runtime.json')
if($LASTEXITCODE -ne 0){throw 'Schema tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($schemaClasses+';'+$JsonJar) com.keplerslicer.app.ProfileIniCodecTest
if($LASTEXITCODE -ne 0){throw 'INI codec tests failed'}
