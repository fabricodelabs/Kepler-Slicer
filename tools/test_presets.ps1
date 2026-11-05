param(
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Official3mf = ''
)
$ErrorActionPreference='Stop'
$phaseProjectRoot=Split-Path -Parent $PSScriptRoot
$phaseTestRoot=Join-Path $phaseProjectRoot ('build\preset-jvm-'+[guid]::NewGuid().ToString('N'))
$phaseClasses=Join-Path $phaseTestRoot 'classes'
New-Item -ItemType Directory -Path $phaseClasses -Force | Out-Null
$phaseSourceRoot=Join-Path $phaseProjectRoot 'app\src\main\java\com\keplerslicer\app\settings'
$phaseSources=@(
    (Join-Path $phaseSourceRoot 'PresetJson.java'),
    (Join-Path $phaseSourceRoot 'PresetRepository.java'),
    (Join-Path $phaseSourceRoot 'ProjectConfigArchive.java'),
    (Join-Path $PSScriptRoot 'tests\PresetRepositoryTest.java')
)
$phaseSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
# Sources moved into the core/engine modules are found at their new place.
$phaseSources=@($phaseSources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $phaseClasses @phaseSources
if($LASTEXITCODE -ne 0){throw 'Preset tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($phaseClasses+';'+$JsonJar) com.keplerslicer.app.settings.PresetRepositoryTest (Join-Path $phaseTestRoot 'data') (Join-Path $phaseProjectRoot 'app\src\main\assets') $Official3mf
if($LASTEXITCODE -ne 0){throw 'Preset tests failed'}
