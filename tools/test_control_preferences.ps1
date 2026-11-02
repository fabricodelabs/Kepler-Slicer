param([string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$controlProject=Split-Path -Parent $PSScriptRoot
$controlClasses=Join-Path $controlProject ('build\control-preferences-jvm-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $controlClasses -Force | Out-Null
$controlSources=@((Join-Path $controlProject 'app\src\main\java\com\orcamobile\app\ControlPreferences.java'),
                  (Join-Path $PSScriptRoot 'tests\ControlPreferencesTest.java'))
$controlSources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\control_stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -d $controlClasses @controlSources
if($LASTEXITCODE -ne 0){throw 'Control scheduler tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $controlClasses com.orcamobile.app.ControlPreferencesTest
if($LASTEXITCODE -ne 0){throw 'Control scheduler tests failed'}
