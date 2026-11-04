param([string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$cameraProject=Split-Path -Parent $PSScriptRoot
$cameraClasses=Join-Path $cameraProject ('build\camera-controls-jvm-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $cameraClasses -Force | Out-Null
$cameraSources=@((Join-Path $cameraProject 'app\src\main\java\com\keplerslicer\app\CameraGestureMath.java'),
                 (Join-Path $PSScriptRoot 'tests\CameraGestureMathTest.java'))
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -d $cameraClasses @cameraSources
if($LASTEXITCODE -ne 0){throw 'Camera control tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $cameraClasses com.keplerslicer.app.CameraGestureMathTest
if($LASTEXITCODE -ne 0){throw 'Camera control tests failed'}
