param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$JavaHome='C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$projectSettingsRoot=Split-Path -Parent $PSScriptRoot
$projectSettingsClasses=Join-Path $projectSettingsRoot ('build\project-settings-jvm-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $projectSettingsClasses -Force | Out-Null
$projectSettingsSources=@(
    (Join-Path $projectSettingsRoot 'app\src\main\java\com\keplerslicer\app\ProjectSettingsSnapshot.java'),
    (Join-Path $PSScriptRoot 'tests\ProjectSettingsSnapshotTest.java')
)
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $projectSettingsClasses @projectSettingsSources
if($LASTEXITCODE -ne 0){throw 'Project settings host contract tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($projectSettingsClasses+';'+$JsonJar) com.keplerslicer.app.ProjectSettingsSnapshotTest
if($LASTEXITCODE -ne 0){throw 'Project settings host contract tests failed'}
