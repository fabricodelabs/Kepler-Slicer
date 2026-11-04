param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$JavaHome='C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$nativeProjectRoot=Split-Path -Parent $PSScriptRoot
$nativeTestRoot=Join-Path $nativeProjectRoot ('build\native-config-jvm-'+[guid]::NewGuid().ToString('N'))
$nativeClasses=Join-Path $nativeTestRoot 'classes'
New-Item -ItemType Directory -Path $nativeClasses -Force | Out-Null
$nativeJava=Join-Path $nativeProjectRoot 'app\src\main\java'
$nativeSources=@(
    (Join-Path $nativeJava 'com\keplerslicer\app\settings\SettingsSchema.java'),
    (Join-Path $nativeJava 'com\keplerslicer\app\EngineText.java'),
    (Join-Path $nativeJava 'com\keplerslicer\app\ProfileIniCodec.java'),
    (Join-Path $nativeJava 'com\keplerslicer\app\NativeConfigValues.java'),
    (Join-Path $nativeJava 'com\keplerslicer\app\NativeTypedConfigSnapshot.java'),
    (Join-Path $nativeJava 'com\keplerslicer\app\ProjectSettingsSnapshot.java'),
    (Join-Path $PSScriptRoot 'tests\ProjectSettingsSnapshotTest.java'),
    (Join-Path $PSScriptRoot 'tests\NativeConfigValuesTest.java'),
    (Join-Path $PSScriptRoot 'tests\NativeTypedConfigSnapshotTest.java')
)
$nativeSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$nativeSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\native_config_stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $nativeClasses @nativeSources
if($LASTEXITCODE -ne 0){throw 'Native config value host tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($nativeClasses+';'+$JsonJar) com.keplerslicer.app.NativeTypedConfigSnapshotTest (Join-Path $nativeProjectRoot 'app\src\main\assets') (Join-Path $nativeTestRoot 'parser-data')
if($LASTEXITCODE -ne 0){throw 'Native typed snapshot host tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($nativeClasses+';'+$JsonJar) com.keplerslicer.app.NativeConfigValuesTest (Join-Path $nativeProjectRoot 'app\src\main\assets') (Join-Path $nativeTestRoot 'data')
if($LASTEXITCODE -ne 0){throw 'Native config value host tests failed'}
