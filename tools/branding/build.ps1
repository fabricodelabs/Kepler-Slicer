# Regenerates the Kepler Slicer brand kit (branding/ and the launcher/splash resources in app/src/main/res).
#   pwsh tools/branding/build.ps1
# Needs the JBR of Android Studio (or any JDK 17+) and Python with fontTools (pip install fonttools): static
# Manrope ExtraBold/Medium are cut from the app's variable font for the text outlines.
param([string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fonts = Join-Path $root 'build/brand-fonts'; $classes = Join-Path $root 'build/brandkit'
New-Item -ItemType Directory -Force -Path $fonts, $classes | Out-Null
$variable = Join-Path $root 'app/src/main/res/font/manrope_variable.ttf'
foreach ($w in @(@(800, 'ExtraBold'), @(500, 'Medium'))) {
    $out = Join-Path $fonts ("Manrope-" + $w[1] + ".ttf")
    if (-not (Test-Path $out)) {
        python -c "from fontTools.ttLib import TTFont; from fontTools.varLib import instancer; instancer.instantiateVariableFont(TTFont(r'$variable'), {'wght': $($w[0])}).save(r'$out')"
        if ($LASTEXITCODE -ne 0) { throw 'fontTools is required: pip install fonttools' }
    }
}
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -d $classes (Join-Path $root 'core/src/main/java/com/keplerslicer/app/BrandGeometry.java') (Join-Path $PSScriptRoot 'BrandKit.java')
if ($LASTEXITCODE -ne 0) { throw 'BrandKit did not compile' }
& (Join-Path $JavaHome 'bin/java.exe') '-Djava.awt.headless=true' -cp $classes com.keplerslicer.app.BrandKit $root $fonts
if ($LASTEXITCODE -ne 0) { throw 'BrandKit failed' }
