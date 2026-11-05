param([Parameter(Mandatory=$true)][string]$JsonJar,[string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$testRoot=Join-Path $project ('build\online-preferences-jvm-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $testRoot 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$java=Join-Path $project 'app\src\main\java\com\keplerslicer\app'
$core=Join-Path $project 'core\src\main\java\com\keplerslicer\app'
$sources=@((Join-Path $core 'OnlineRules.java'),(Join-Path $core 'NetworkHttp.java'),(Join-Path $core 'StorageNames.java'),(Join-Path $PSScriptRoot 'tests\OnlineRulesTest.java'))
$sources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$sources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\cloud_stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$sources+=(Join-Path (Split-Path -Parent $PSScriptRoot) 'core\src\main\java\com\keplerslicer\app\L.java')
# Sources moved into the core/engine modules are found at their new place.
$sources=@($sources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 --add-modules jdk.httpserver -cp $JsonJar -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Online preference tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') --add-modules jdk.httpserver -cp ($classes+';'+$JsonJar) com.keplerslicer.app.OnlineRulesTest
if($LASTEXITCODE -ne 0){throw 'Online preference tests failed'}
