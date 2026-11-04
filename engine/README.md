# Kepler Slicer: проверка нативного кода upstream

Обновление 2026-11-02: владелец согласовал расширение JNI/ядра для Preferences Фазы 2. Актуальные контракты STEP, воспроизводимая сборка и границы проверки описаны в [NATIVE-PREFERENCES-BUILD.md](NATIVE-PREFERENCES-BUILD.md). Ниже сохранена история первоначального порта и его зависимостей; она не является отчётом о проверке новой сборки.

Это отдельные эксперименты по сборке официального исходного кода [upstream](https://github.com/OrcaSlicer/OrcaSlicer) на Android. Небольшая проба компилирует `deps_src/semver/semver.c` и сравнивает версии; отдельная headless-конфигурация собирает настоящий `libslic3r` и экспериментальный JNI. Экспериментальный движок включён в проект основного APK для `arm64-v8a`; его новый экран нарезки пока не прошёл полноценную проверку. В отдельном тестовом приложении выполнена одна пробная нарезка на эмуляторе Android 17 с ARM-трансляцией; соответствие desktop и пригодность результата к печати не подтверждены.

Фиксированный исходный коммит: `6d2869c430284714f0a22570c38d3b359965a802`. Upstream распространяется под AGPL-3.0; файлы semver содержат указание авторства Tomas Aparicio и MIT-лицензии. При распространении производного приложения нужно предоставить соответствующий исходный код и сохранить указания об авторстве и лицензиях.

## Конфигурация эксперимента

Нужны Git, CMake 3.22+, Android NDK и checkout официального upstream на указанном коммите. Например, из PowerShell, заменив пути на собственные:

```powershell
cmake -S . -B build-arm64 -G Ninja `
  -DCMAKE_TOOLCHAIN_FILE="C:/Android/Sdk/ndk/27.2.12479018/build/cmake/android.toolchain.cmake" `
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 `
  -DUPSTREAM_SOURCE="C:/src/OrcaSlicer"
cmake --build build-arm64
```

`CMakeLists.txt` откажется собирать модуль без Android toolchain или при несовпадении коммита. Проверена кросс-компиляция для `arm64-v8a` с Android NDK r30 и CMake 4.4.4; полученная библиотека содержит JNI-символ и имеет архитектуру AArch64. Работа JNI на физическом Android-устройстве ещё не проверялась.

## Что требуется для расширения слайсинга

`src/libslic3r` — C++17-библиотека с печатным конвейером (`Print.cpp`), параметрами (`PrintConfig.cpp`), чтением моделей и профилей. Для `arm64-v8a` она собрана и связана с JNI; для других ABI Android понадобится отдельная сборка и проверка. Официальный CMake при `SLIC3R_GUI=OFF` всё равно запрашивает настольные OpenGL и GLFW и встроенный CPython; необходима адаптация конфигурации, а не простое переключение этой опции. Нужны кросс-собранные для Android Boost, TBB, CGAL, OpenCASCADE, OpenCV, Assimp, OpenVDB и другие зависимости, а также проверка всех системных и файловых API. Приведённый здесь тест semver этих задач не решает.

## Результат исследования зависимостей

На Windows с NDK r30, CMake 4.4.4 и Ninja 1.13.2 подготовлены Boost 1.84.0, Eigen 5.0.1, oneTBB 2021.5.0, OpenSSL 1.1.1w, curl 7.75.0, cereal 1.3.0 и NLopt 2.5.0 в локальном префиксе для `arm64-v8a`, API 26. Архитектура объектов Boost, TBB, OpenSSL и curl проверена `llvm-readelf`: AArch64. При сборке статического oneTBB сам проект предупреждает, что такая конфигурация не поддерживается; её пригодность для Android предстоит проверить. Текущая экспериментальная сборка OpenSSL содержит путь сертификатов хоста: хранение и проверка доверенных CA на Android не настроены. Одна пробная нарезка косвенно проверила только использованные в ней пути библиотек; TLS и другие функции отдельно не проверены.

`android-headless.patch` добавляет в копию указанного upstream-коммита опцию `ORCA_ANDROID_ENGINE_ONLY`: она разрешена только для Android с `SLIC3R_GUI=OFF`, исключает конфигурирование настольных OpenGL/GLFW, Python, CLI и упаковку, а в `src` останавливается после добавления `libslic3r`. Для Android он также выбирает собранный `opencv_world` и использует существующий bundled target Expat. Патч проверен командой `git apply --check`. Это изолированная сборочная конфигурация, не перенос GUI, предпросмотра или командной строки.

Дополнительно для той же ABI и уровня API собраны OpenEXR 2.5.5, Blosc и OpenVDB 8.2, libnoise, SLVS и OCCT 8.0.1. После передачи совместимых префиксов, в том числе CGAL, Draco и OpenCV, headless CMake configure завершился, и настоящий `libslic3r` с экспериментальным JNI скомпилирован и слинкован для Android arm64 с `--no-undefined`. Проверены ELF-архитектура AArch64 и экспорт JNI-символа. В отдельном тестовом приложении на эмуляторе Android 17 с ARM-трансляцией библиотека загрузилась и создала непустой G-code размером 260 805 байт для одного официального тестового STL и системных профилей Prusa CORE One. В содержимом найдены заголовок upstream, 100 слоёв, команды перемещений и экструзии; соответствие desktop не проверено. Эта библиотека включена в проект основного APK, но новый пользовательский экран ещё не прошёл полноценную проверку. OpenVDB ABI 8 определён по версии без запуска `vdb_print` на устройстве.

`src/slice_jni.cpp` — эксперимент с настоящим `libslic3r`; скомпилированная версия подключена к экспериментальному экрану нарезки приложения. Патч позволяет указать его путь через `KEPLER_SLICE_JNI` и создать цель `kepler_slicer_experimental`. Код вызывает загрузку STL, проверку конфигурации и `Print::apply/process/export_gcode` из upstream и передаёт исключения в Java. Новый экспериментальный `sliceSystemProfiles` получает системные профили через `PresetBundle` и передаёт конфигурацию прямо в память, без INI; он слинкован и прошёл указанную единственную пробную нарезку на эмуляторе. Старый `sliceStl` принимает произвольный INI, не проверяет его полноту и не предназначен для пользовательского интерфейса. Указанный вызывающей стороной data-каталог должен быть приватным каталогом приложения; результат записывается в новый дочерний каталог задания. Файловый доступ, размещение на столе, Unicode-пути, отмена и потокобезопасность ещё требуют проверки. Один успешный тест не подтверждает корректность нарезки или безопасность результата; код не пригоден для управления печатью.

Ниже приведены параметры, с которыми были собраны библиотеки. Архивы и контрольные суммы взяты из `deps/` указанного коммита upstream:

| Пакет | Официальный архив | SHA-256 |
| --- | --- | --- |
| Boost 1.84.0 | [Архив](https://github.com/boostorg/boost/releases/download/boost-1.84.0/boost-1.84.0.tar.gz) | `4d27e9efed0f6f152dc28db6430b9d3dfb40c0345da7342eaa5a987dde57bd95` |
| Eigen 5.0.1 | [Архив](https://gitlab.com/libeigen/eigen/-/archive/5.0.1/eigen-5.0.1.zip) | `0dbb1f9e3aaad66f352c03227d8c983f6f0b49e0b07e71a7300f4abcc01aee12` |
| oneTBB 2021.5.0 | [Архив](https://github.com/oneapi-src/oneTBB/archive/refs/tags/v2021.5.0.zip) | `83ea786c964a384dd72534f9854b419716f412f9d43c0be88d41874763e7bb47` |
| OpenSSL 1.1.1w | [Архив](https://github.com/openssl/openssl/archive/OpenSSL_1_1_1w.tar.gz) | `2130e8c2fb3b79d1086186f78e59e8bc8d1a6aedf17ab3907f4cb9ae20918c41` |
| curl 7.75.0 | [Архив](https://github.com/curl/curl/archive/refs/tags/curl-7_75_0.zip) | `a63ae025bb0a14f119e73250f2c923f4bf89aa93b8d4fafa4a9f5353a96a765a` |
| cereal 1.3.0 | [Архив](https://github.com/USCiLab/cereal/archive/refs/tags/v1.3.0.zip) | `71642cb54658e98c8f07a0f0d08bf9766f1c3771496936f6014169d3726d9657` |
| NLopt 2.5.0 | [Архив](https://github.com/stevengj/nlopt/archive/v2.5.0.tar.gz) | `c6dd7a5701fff8ad5ebb45a3dc8e757e61d52658de3918e38bab233e7fd3b4ae` |
| Blosc (закреплённая версия upstream) | [Архив](https://github.com/tamasmeszaros/c-blosc/archive/refs/heads/v1.17.0_tm.zip) | `dcb48bf43a672fa3de6a4b1de2c4c238709dad5893d1e097b8374ad84b1fc3b3` |
| OpenEXR 2.5.5 | [Архив](https://github.com/AcademySoftwareFoundation/openexr/archive/refs/tags/v2.5.5.zip) | `0307a3d7e1fa1e77e9d84d7e9a8694583fbbbfd50bdc6884e2c96b8ef6b902de` |
| OpenVDB (закреплённый коммит upstream) | [Архив](https://github.com/tamasmeszaros/openvdb/archive/a68fd58d0e2b85f01adeb8b13d7555183ab10aa5.zip) | `f353e7b99bd0cbfc27ac9082de51acf32a8bc0b3e21ff9661ecca6f205ec1d81` |
| libnoise 1.0 | [Архив](https://github.com/SoftFever/Orca-deps-libnoise/archive/refs/tags/1.0.zip) | `96ffd6cc47898dd8147aab53d7d1b1911b507d9dbaecd5613ca2649468afd8b6` |
| SLVS (закреплённый коммит upstream) | [Архив](https://github.com/JacobStoren/SolveSpaceLib/archive/4d8704523e4bf212fadf5189f92484244f670fea.zip) | `1c4bdde9c3c6ef20ea4b50b73601de56769f2eb131b36927d7c6489f102e6c30` |
| OCCT 8.0.1 | [Архив](https://github.com/Open-Cascade-SAS/OCCT/archive/refs/tags/V8_0_1.zip) | `7c033d917ee8f040c0512d289dcc5f02c148889d5bac17c3e25639accb44f0da` |

`build-android-deps.ps1` воспроизводит конфигурации этих семи пакетов из заранее проверенных и распакованных архивов. Ему также нужны Git for Windows с Perl, полный набор модулей Perl и GNU make в MSYS-окружении. При распаковке NLopt в Windows пришлось исключить документационный симлинк `doc/nlopt-mkdocs-theme/img/favicon.png`, который не участвует в сборке. Синтаксис скрипта проверен; команды выполнялись по отдельности, полный скрипт целиком пока не запускался.

`build-vdb-deps.ps1` содержит параметры сборки Blosc, OpenEXR и OpenVDB и применяет официальный патч к исходникам OpenVDB. Синтаксис проверен; отдельные команды выполнены, повторный запуск полного скрипта ещё не проверялся. Сборка OCCT 8.0.1 со штатным патчем и включённым модулем ModelingAlgorithms завершилась; проверенные объекты имеют архитектуру AArch64.

`build-occt.ps1` описывает параметры этой сборки OCCT. `configure-headless.ps1` показывает, как передать разные Android-префиксы зависимостей и путь экспериментального JNI в headless-конфигурацию. Это заготовки: их полный повторный запуск не проверен. Проверяйте архитектуру каждой библиотеки; CMake может найти библиотеку хоста при смешанных префиксах.

После проверки SHA-256 и распаковки архивов укажите пути к их каталогам, NDK и общему префиксу. Используйте установленный CMake и Ninja в `PATH`:

```powershell
$toolchain = 'C:/Android/Sdk/ndk/30.0.0/build/cmake/android.toolchain.cmake' # замените фактическим путём
$prefix = 'C:/work/kepler-deps-arm64'
$common = @('-G','Ninja', "-DCMAKE_TOOLCHAIN_FILE=$toolchain", '-DANDROID_ABI=arm64-v8a',
    '-DANDROID_PLATFORM=android-26', '-DCMAKE_BUILD_TYPE=Release',
    '-DCMAKE_POSITION_INDEPENDENT_CODE=ON', "-DCMAKE_INSTALL_PREFIX=$prefix")

cmake -S C:/src/boost-1.84.0 -B C:/work/boost-arm64 @common `
  '-DBUILD_SHARED_LIBS=OFF' '-DBUILD_TESTING=OFF' '-DBOOST_LOCALE_ENABLE_ICU=OFF' `
  '-DBOOST_IOSTREAMS_ENABLE_BZIP2=OFF' '-DBOOST_IOSTREAMS_ENABLE_ZSTD=OFF' `
  '-DBOOST_EXCLUDE_LIBRARIES=contract;fiber;numpy;stacktrace;wave;test'
cmake --build C:/work/boost-arm64 --target install --parallel 4

cmake -S C:/src/eigen-5.0.1 -B C:/work/eigen-arm64 @common `
  '-DEIGEN_BUILD_TESTING=OFF' '-DEIGEN_BUILD_BLAS=OFF' '-DEIGEN_BUILD_LAPACK=OFF'
cmake --build C:/work/eigen-arm64 --target install --parallel 4

cmake -S C:/src/oneTBB-2021.5.0 -B C:/work/tbb-arm64 @common `
  '-DCMAKE_POLICY_VERSION_MINIMUM=3.10' '-DBUILD_SHARED_LIBS=OFF' '-DTBB_TEST=OFF' `
  '-DTBB_DISABLE_HWLOC_AUTOMATIC_SEARCH=ON' '-DTBB_ENABLE_IPO=OFF' `
  '-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=OFF' '-DCMAKE_DEBUG_POSTFIX=_debug'
cmake --build C:/work/tbb-arm64 --target install --parallel 4

cmake -S C:/src/OrcaSlicer -B C:/work/upstream-arm64 @common `
  '-DSLIC3R_GUI=OFF' '-DSLIC3R_BUILD_TESTS=OFF' `
  '-DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH' "-DCMAKE_PREFIX_PATH=$prefix" `
  "-DBoost_DIR=$prefix/lib/cmake/Boost-1.84.0"
```

Последняя команда без дополнительных зависимостей остановится на OpenSSL; экспериментальная конфигурация с headless-патчем и дополнительными библиотеками завершилась. При использовании `CMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH` нужно проверять, что каждый найденный пакет собран для Android, а не для компьютера-хоста. В официальной суперсборке `deps/deps-linux.cmake` перезаписывает `DEP_CMAKE_OPTS`, поэтому передача ABI только через эту переменную не попадает в дочернюю конфигурацию; сборка Boost таким способом ошибочно выбрала `armeabi-v7a` и была остановлена. Также без headless-патча корневой CMake выбрал пакет `uv` для Linux aarch64, который не является Android-сборкой. Для кросс-сборки штатная проверка кодировки заменена отдельной проверкой UTF-8/BOM на хосте: собранный для Android инструмент upstream на Windows не запускается.
