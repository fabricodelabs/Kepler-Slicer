param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$JavaHome='C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$projectSettingsRoot=Split-Path -Parent $PSScriptRoot
$projectSettingsClasses=Join-Path $projectSettingsRoot ('build\project-settings-jvm-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $projectSettingsClasses -Force | Out-Null
$projectSettingsSources=@(
    (Join-Path $projectSettingsRoot 'core\src\main\java\com\keplerslicer\app\ProjectSettingsSnapshot.java'),
    (Join-Path $PSScriptRoot 'tests\ProjectSettingsSnapshotTest.java')
)
# Sources moved into the core/engine modules are found at their new place.
$projectSettingsSources=@($projectSettingsSources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $projectSettingsClasses @projectSettingsSources
if($LASTEXITCODE -ne 0){throw 'Project settings host contract tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($projectSettingsClasses+';'+$JsonJar) com.keplerslicer.app.ProjectSettingsSnapshotTest
if($LASTEXITCODE -ne 0){throw 'Project settings host contract tests failed'}
