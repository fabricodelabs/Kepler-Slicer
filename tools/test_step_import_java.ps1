param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$AndroidJar='C:/Users/tails/AppData/Local/Android/Sdk/platforms/android-37.0/android.jar',
    [string]$JavaHome='C:/Program Files/Android/Android Studio/jbr'
)
$ErrorActionPreference='Stop'
$stepProjectRoot=Split-Path -Parent $PSScriptRoot
$stepTestRoot=Join-Path $stepProjectRoot ('build/step-java-'+[guid]::NewGuid().ToString('N'))
$stepClasses=Join-Path $stepTestRoot 'classes'
$stepGenerated=Join-Path $stepTestRoot 'generated/com/keplerslicer/app'
New-Item -ItemType Directory -Force -Path $stepClasses,$stepGenerated | Out-Null
$stepResourceNames=([xml](Get-Content (Join-Path $stepProjectRoot 'app/src/main/res/values/strings_step_import.xml') -Raw)).resources.string.name
$stepResourceBody='package com.keplerslicer.app; public final class R {public static final class string {'
$stepResourceIndex=1
foreach($stepResourceName in $stepResourceNames){$stepResourceBody+='public static final int '+$stepResourceName+'='+$stepResourceIndex+';';$stepResourceIndex++}
$stepResourceBody+='} public static final class style {public static final int KeplerSheetAnim=1;public static final int KeplerSheetDialog=2;} public static final class interpolator {public static final int kv_ios=1;public static final int kv_ios_out=2;}}'
$stepResourceFile=Join-Path $stepGenerated 'R.java'
$stepResourceBody | Set-Content -LiteralPath $stepResourceFile -Encoding utf8NoBOM
$stepSources=@((Join-Path $stepProjectRoot 'app/src/main/java/com/keplerslicer/app/StepImportPreferences.java'),(Join-Path $stepProjectRoot 'core/src/main/java/com/keplerslicer/app/EngineText.java'),(Join-Path $stepProjectRoot 'core/src/main/java/com/keplerslicer/app/ModelFilename.java'),(Join-Path $PSScriptRoot 'tests/StepImportPreferencesTest.java'),$stepResourceFile)
$stepSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests/step_stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$stepSources+=(Join-Path (Split-Path -Parent $PSScriptRoot) 'core\src\main\java\com\keplerslicer\app\L.java')
$stepSources+=(Join-Path $stepProjectRoot 'app/src/main/java/com/keplerslicer/app/glass/SheetDialog.java')
$stepSources+=(Join-Path $stepProjectRoot 'app/src/main/java/com/keplerslicer/app/glass/Desktop.java')
# Sources moved into the core/engine modules are found at their new place.
$stepSources=@($stepSources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -cp ($JsonJar+';'+$AndroidJar) -d $stepClasses @stepSources
if($LASTEXITCODE -ne 0){throw 'STEP Java host compilation failed'}
& (Join-Path $JavaHome 'bin/java.exe') -cp ($stepClasses+';'+$JsonJar+';'+$AndroidJar) com.keplerslicer.app.StepImportPreferencesTest (Join-Path $stepTestRoot 'data')
if($LASTEXITCODE -ne 0){throw 'STEP Java host checks failed'}
