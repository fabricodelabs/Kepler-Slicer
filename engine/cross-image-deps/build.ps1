<# Build the upstream-pinned image dependencies for Android. See README.md. #>
param(
    [Parameter(Mandatory=$true)][string]$Ndk,
    [Parameter(Mandatory=$true)][string]$Cmake,
    [Parameter(Mandatory=$true)][string]$Ninja,
    [Parameter(Mandatory=$true)][string]$Archives,
    [Parameter(Mandatory=$true)][string]$Work,
    [Parameter(Mandatory=$true)][string]$Prefix,
    [ValidateRange(1,128)][int]$Jobs=4,
    [switch]$VerifyOnly
)
$ErrorActionPreference='Stop'
function Require-Path([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Required path missing: $Path" }
    (Resolve-Path -LiteralPath $Path).Path
}
function Run-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed: $Exe (exit $LASTEXITCODE)" }
}
$ndkPath=Require-Path $Ndk
$cmakeExe=Require-Path $Cmake
$ninjaExe=Require-Path $Ninja
$archivePath=Require-Path $Archives
$toolchain=(Require-Path (Join-Path $ndkPath 'build/cmake/android.toolchain.cmake')).Replace('\','/')
$packages=@(
    @{file='assimp-5.4.3.tar.gz'; hash='66dfbaee288f2bc43172440a55d0235dfc7bf885dda6435c038e8000e79582cb'; folder='assimp-5.4.3'},
    @{file='opencv-4.14.0.tar.gz'; hash='ee8fb9b30eb60850431b4656447080e3737b56e45719c92b67f245950609f86e'; folder='opencv-4.14.0'},
    @{file='jpeg-3.2.0.zip'; hash='1b4c2cb6cba1ca5e53b9e85eeeab02154cab4b70894c96e97a9c747a26d19d63'; folder='libjpeg-turbo-3.2.0'},
    @{file='png-1.6.59.zip'; hash='6fd97a1e5e2ac55cec0a538a9800a351eb3844086736291844ebd12db72ba433'; folder='libpng-1.6.59'}
)
foreach ($p in $packages) {
    $archive=Require-Path (Join-Path $archivePath $p.file)
    $actual=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $p.hash) { throw "SHA-256 mismatch for $($p.file): $actual" }
    Write-Host "Verified $($p.file): $actual"
}
if ($VerifyOnly) { return }
New-Item -ItemType Directory -Force -Path $Work, $Prefix | Out-Null
$workPath=(Resolve-Path -LiteralPath $Work).Path
$prefixPath=(Resolve-Path -LiteralPath $Prefix).Path.Replace('\','/')
$sourceRoot=Join-Path $workPath 'sources'
if (Test-Path -LiteralPath $sourceRoot) { throw "Source directory already exists: $sourceRoot. Use a fresh work directory." }
New-Item -ItemType Directory -Path $sourceRoot | Out-Null
$tar=(Get-Command tar.exe -ErrorAction Stop).Source
foreach ($p in $packages) { Run-Checked $tar @('-xf',(Join-Path $archivePath $p.file),'-C',$sourceRoot) }
$common=@('-G','Ninja',"-DCMAKE_MAKE_PROGRAM=$ninjaExe", "-DCMAKE_TOOLCHAIN_FILE=$toolchain", '-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-26','-DCMAKE_BUILD_TYPE=Release',"-DCMAKE_INSTALL_PREFIX=$prefixPath",'-DBUILD_SHARED_LIBS=OFF','-DCMAKE_POSITION_INDEPENDENT_CODE=ON','-DCMAKE_POLICY_VERSION_MINIMUM=3.5')
function Build-Cmake([string]$Name,[string]$Folder,[string[]]$Options) {
    $source=Require-Path (Join-Path $sourceRoot $Folder)
    $build=Join-Path $workPath "build-$Name"
    Run-Checked $cmakeExe (@('-S',$source,'-B',$build) + $common + $Options)
    Run-Checked $cmakeExe @('--build',$build,'--target','install','--parallel',"$Jobs")
}
Build-Cmake 'jpeg' 'libjpeg-turbo-3.2.0' @('-DENABLE_SHARED=OFF','-DENABLE_STATIC=ON')
Build-Cmake 'png' 'libpng-1.6.59' @('-DPNG_SHARED=OFF','-DPNG_STATIC=ON','-DPNG_PREFIX=prusaslicer_','-DPNG_TESTS=OFF','-DDISABLE_DEPENDENCY_TRACKING=OFF')
Build-Cmake 'assimp' 'assimp-5.4.3' @('-DASSIMP_BUILD_USE_CCACHE=OFF','-DASSIMP_BUILD_TESTS=OFF','-DASSIMP_BUILD_SAMPLES=OFF','-DASSIMP_BUILD_ASSIMP_TOOLS=OFF','-DASSIMP_INSTALL_PDB=OFF','-DASSIMP_NO_EXPORT=ON','-DASSIMP_BUILD_ALL_IMPORTERS_BY_DEFAULT=OFF','-DASSIMP_BUILD_GLTF_IMPORTER=ON','-DASSIMP_BUILD_OBJ_IMPORTER=ON','-DASSIMP_BUILD_FBX_IMPORTER=ON','-DASSIMP_BUILD_ZLIB=ON','-DASSIMP_WARNINGS_AS_ERRORS=OFF','-DBUILD_WITH_STATIC_CRT=OFF')
$opencv=@('-DBUILD_PERE_TESTS=OFF','-DBUILD_TESTS=OFF','-DBUILD_opencv_python_tests=OFF','-DBUILD_EXAMPLES=OFF','-DBUILD_ANDROID_EXAMPLES=OFF','-DINSTALL_ANDROID_EXAMPLES=OFF','-DBUILD_JASPER=OFF','-DBUILD_JAVA=OFF','-DBUILD_JPEG=OFF','-DBUILD_PNG=OFF','-DBUILD_APPS_LIST=version','-DBUILD_opencv_apps=OFF','-DBUILD_opencv_java=OFF','-DBUILD_OPENEXR=OFF','-DBUILD_TBB=OFF','-DBUILD_WEBP=OFF','-DBUILD_ZLIB=OFF','-DWITH_1394=OFF','-DWITH_CUDA=OFF','-DWITH_EIGEN=OFF','-DWITH_IPP=OFF','-DWITH_ITT=OFF','-DWITH_FFMPEG=OFF','-DWITH_GPHOTO2=OFF','-DWITH_GSTREAMER=OFF','-DOPENCV_GAPI_GSTREAMER=OFF','-DWITH_GTK_2_X=OFF','-DWITH_JASPER=OFF','-DWITH_LAPACK=OFF','-DWITH_MATLAB=OFF','-DWITH_MFX=OFF','-DWITH_DIRECTX=OFF','-DWITH_DIRECTML=OFF','-DWITH_OPENCL=OFF','-DWITH_OPENCL_D3D11_NV=OFF','-DWITH_OPENCLAMDBLAS=OFF','-DWITH_OPENCLAMDFFT=OFF','-DWITH_OPENEXR=OFF','-DWITH_OPENJPEG=OFF','-DWITH_QUIRC=OFF','-DWITH_VTK=OFF','-DWITH_JPEG=ON','-DWITH_WEBP=OFF','-DWITH_TIFF=OFF','-DBUILD_TIFF=OFF','-DWITH_AVIF=OFF','-DWITH_IMGCODEC_GIF=OFF','-DWITH_KLEIDICV=OFF','-DENABLE_PRECOMPILED_HEADERS=OFF','-DINSTALL_TESTS=OFF','-DINSTALL_C_EXAMPLES=OFF','-DINSTALL_PYTHON_EXAMPLES=OFF','-DOPENCV_GENERATE_SETUPVARS=OFF','-DOPENCV_INSTALL_FFMPEG_DOWNLOAD_SCRIPT=OFF','-DBUILD_opencv_python2=OFF','-DBUILD_opencv_python3=OFF','-DWITH_OPENVINO=OFF','-DWITH_INF_ENGINE=OFF','-DWITH_NGRAPH=OFF','-DBUILD_WITH_STATIC_CRT=OFF','-DBUILD_LIST=core,imgcodecs,imgproc,world','-DBUILD_opencv_highgui=OFF','-DWITH_ADE=OFF','-DBUILD_opencv_world=ON','-DWITH_PROTOBUF=OFF','-DWITH_WIN32UI=OFF','-DHAVE_WIN32UI=FALSE','-DWITH_CAROTENE=OFF',"-DJPEG_LIBRARY=$prefixPath/lib/libjpeg.a", "-DJPEG_INCLUDE_DIR=$prefixPath/include", "-DPNG_LIBRARY=$prefixPath/lib/libpng.a", "-DPNG_PNG_INCLUDE_DIR=$prefixPath/include")
Build-Cmake 'opencv' 'opencv-4.14.0' $opencv
