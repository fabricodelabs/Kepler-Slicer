param([Parameter(Mandatory=$true)][string]$JsonJar,[string]$JavaHome='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$testRoot=Join-Path $project ('build\bambu-lan-jvm-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $testRoot 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$java=Join-Path $project 'app\src\main\java\com\keplerslicer\app'
# Throwaway self-signed key for the loopback printer simulator only.
$keystore=Join-Path $testRoot 'printer.p12'; $pass='kepler-test'
& (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -keystore $keystore -storetype PKCS12 -alias printer -keyalg RSA -keysize 2048 -validity 2 -storepass $pass -keypass $pass -dname 'CN=BBL test printer' 2>$null
if($LASTEXITCODE -ne 0){throw 'Test key generation failed'}
$sources=@((Join-Path $java 'BambuLan.java'),(Join-Path $java 'AmsTrays.java'),(Join-Path $java 'PrinterEndpoint.java'),(Join-Path $java 'NetworkHttp.java'),(Join-Path $PSScriptRoot 'tests\BambuLanTest.java'))
$sources+=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $JsonJar -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Bambu LAN tests did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ($classes+';'+$JsonJar) com.keplerslicer.app.BambuLanTest $keystore $pass
if($LASTEXITCODE -ne 0){throw 'Bambu LAN tests failed'}
