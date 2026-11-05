param([string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$controlProject=Split-Path -Parent $PSScriptRoot
$controlClasses=Join-Path $controlProject ('build\control-preferences-jvm-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $controlClasses -Force | Out-Null
$controlSources=@((Join-Path $controlProject 'app\src\main\java\com\keplerslicer\app\ControlPreferences.java'),
                  (Join-Path $PSScriptRoot 'tests\ControlPreferencesTest.java'))
$controlSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\control_stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
# Sources moved into the core/engine modules are found at their new place.
$controlSources=@($controlSources | ForEach-Object { $p=[string]$_; if(-not (Test-Path -LiteralPath $p)){ foreach($alt in @($p.Replace('\app\src\main\java\com\keplerslicer\app\','\core\src\main\java\com\keplerslicer\app\'),$p.Replace('\app\src\main\java\com\keplerslicer\engine\','\engine\java\com\keplerslicer\engine\'))){ if(Test-Path -LiteralPath $alt){$p=$alt;break} } }; $p }) # Resolve-KeplerSource
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -d $controlClasses @controlSources
if($LASTEXITCODE -ne 0){throw 'Control scheduler tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $controlClasses com.keplerslicer.app.ControlPreferencesTest
if($LASTEXITCODE -ne 0){throw 'Control scheduler tests failed'}
