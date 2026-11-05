param([string]$JavaHome='C:\Program Files\Android\Android Studio\jbr',[switch]$PackagedAsset)
$ErrorActionPreference='Stop'
$resourceProject=Split-Path -Parent $PSScriptRoot
$resourceRun=Join-Path $resourceProject ('build\engine-resources-jvm-'+[guid]::NewGuid().ToString('N'))
$resourceClasses=Join-Path $resourceRun 'classes'
New-Item -ItemType Directory -Path $resourceClasses -Force | Out-Null
$resourceSources=@(
    (Join-Path $resourceProject 'core\src\main\java\com\keplerslicer\app\EngineResourceArchive.java'),
    (Join-Path $resourceProject 'app\src\main\java\com\keplerslicer\app\EngineResources.java'),
    (Join-Path $PSScriptRoot 'tests\EngineResourcesTest.java')
)
$resourceSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\resource_stubs') -Recurse -Filter '*.java' | ForEach-Object FullName
# Sources moved into the core/engine modules are found at their new place.
$resourceSources=@($resourceSources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin\javac.exe') --release 17 -encoding UTF-8 -d $resourceClasses @resourceSources
if($LASTEXITCODE -ne 0){throw 'Production engine resource tests did not compile'}
$resourceArgs=@((Join-Path $resourceRun 'data'))
if($PackagedAsset){$resourceArgs+=Join-Path $resourceProject 'app\src\main\assets\upstream_resources.zip'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $resourceClasses com.keplerslicer.app.EngineResourcesTest @resourceArgs
if($LASTEXITCODE -ne 0){throw 'Production engine resource tests failed'}
