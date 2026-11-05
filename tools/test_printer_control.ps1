param([Parameter(Mandatory=$true)][string]$JsonJar,[string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$testRoot=Join-Path $project ('build\legacy-control-jvm-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $testRoot 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$java=Join-Path $project 'app\src\main\java\com\keplerslicer\app'
$sources=@((Join-Path $java 'BambuLan.java'),(Join-Path $java 'PrinterHostClient.java'),(Join-Path $java 'PrinterDigestAuth.java'),(Join-Path $java 'LegacyPrinterHosts.java'),(Join-Path $java 'CloudPrinterHosts.java'),(Join-Path $java 'LegacyTcpConsole.java'),(Join-Path $java 'LegacyPrinterControl.java'),(Join-Path $java 'MiniWebSocket.java'),(Join-Path $java 'ElegooCc2.java'),(Join-Path $java 'BambuCloud.java'),(Join-Path $java 'TlsEngineChannel.java'),(Join-Path $java 'LegacyHostFiles.java'),(Join-Path $java 'AmsTrays.java'),(Join-Path $java 'PrinterEndpoint.java'),(Join-Path $java 'NetworkHttp.java'),(Join-Path $PSScriptRoot 'tests\LegacyControlTest.java'))
$sources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$sources+=(Join-Path (Split-Path -Parent $PSScriptRoot) 'app\src\main\java\com\keplerslicer\app\L.java')
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Printer control tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($classes+';'+$JsonJar) com.keplerslicer.app.LegacyControlTest
if($LASTEXITCODE -ne 0){throw 'Printer control tests failed'}
