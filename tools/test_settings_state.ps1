param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$JavaHome='C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$stateProjectRoot=Split-Path -Parent $PSScriptRoot
$stateTestRoot=Join-Path $stateProjectRoot ('build\settings-state-jvm-'+[guid]::NewGuid().ToString('N'))
$stateClasses=Join-Path $stateTestRoot 'classes'
New-Item -ItemType Directory -Path $stateClasses -Force | Out-Null
$stateJavaRoot=Join-Path $stateProjectRoot 'app\src\main\java'
$stateSources=@(
    'com\keplerslicer\app\SettingsState.java',
    'com\keplerslicer\app\StorageNames.java',
    'com\keplerslicer\app\CompatibilityResources.java',
    'com\keplerslicer\app\PrinterProfileCatalog.java',
    'com\keplerslicer\app\PrintProfileCatalog.java',
    'com\keplerslicer\app\ObjectSettingsStore.java',
    'com\keplerslicer\app\settings\PresetRepository.java',
    'com\keplerslicer\app\settings\PresetJson.java',
    'com\keplerslicer\engine\ExperimentalSlicer.java'
) | ForEach-Object {Join-Path $stateJavaRoot $_}
$stateSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$stateSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\state_stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$stateSources+=Join-Path $PSScriptRoot 'tests\SettingsStateTest.java'
$stateSources+=(Join-Path (Split-Path -Parent $PSScriptRoot) 'core\src\main\java\com\keplerslicer\app\L.java')
# Sources moved into the core/engine modules are found at their new place.
$stateSources=@($stateSources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $stateClasses @stateSources
if($LASTEXITCODE -ne 0){throw 'Settings-state tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($stateClasses+';'+$JsonJar) com.keplerslicer.app.SettingsStateTest (Join-Path $stateTestRoot 'data') (Join-Path $stateProjectRoot 'app\src\main\assets')
if($LASTEXITCODE -ne 0){throw 'Settings-state tests failed'}
