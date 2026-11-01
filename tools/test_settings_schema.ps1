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
    (Join-Path $schemaJava 'com\orcamobile\app\settings\SettingsSchema.java'),
    (Join-Path $schemaJava 'com\orcamobile\app\OrcaIniCodec.java'),
    (Join-Path $PSScriptRoot 'tests\SettingsSchemaTest.java'),
    (Join-Path $PSScriptRoot 'tests\OrcaIniCodecTest.java')
)
$schemaSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $schemaClasses @schemaSources
if($LASTEXITCODE -ne 0){throw 'Schema tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($schemaClasses+';'+$JsonJar) SettingsSchemaTest (Join-Path $schemaProjectRoot 'app\src\main\assets\orca_settings_runtime.json')
if($LASTEXITCODE -ne 0){throw 'Schema tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($schemaClasses+';'+$JsonJar) com.orcamobile.app.OrcaIniCodecTest
if($LASTEXITCODE -ne 0){throw 'INI codec tests failed'}
