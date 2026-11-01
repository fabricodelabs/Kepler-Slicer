# Android-сборка графических зависимостей OrcaSlicer

Скрипт собирает исходные зависимости официального OrcaSlicer для `arm64-v8a`, Android API 26 и NDK r30. Он **не собирает движок OrcaSlicer и не добавляет его в приложение**. Проверенная среда: Windows, CMake 4.4.4, Ninja 1.13.2, Android NDK 30.0.16248370. Библиотеки собираются статически с PIC и стандартным для NDK `c++_static`.

Версии, флаги и контрольные суммы взяты из `deps/{Assimp,OpenCV,JPEG,PNG}` официального репозитория OrcaSlicer. Для CMake версии 3.22 и выше рецепт Orca выбирает Assimp 5.4.3.

| Архив (сохранить под этим именем) | Исходный URL | SHA-256 |
| --- | --- | --- |
| `assimp-5.4.3.tar.gz` | [Assimp v5.4.3](https://github.com/assimp/assimp/archive/refs/tags/v5.4.3.tar.gz) | `66dfbaee288f2bc43172440a55d0235dfc7bf885dda6435c038e8000e79582cb` |
| `opencv-4.14.0.tar.gz` | [OpenCV 4.14.0](https://github.com/opencv/opencv/archive/refs/tags/4.14.0.tar.gz) | `ee8fb9b30eb60850431b4656447080e3737b56e45719c92b67f245950609f86e` |
| `jpeg-3.2.0.zip` | [libjpeg-turbo 3.2.0](https://github.com/libjpeg-turbo/libjpeg-turbo/archive/refs/tags/3.2.0.zip) | `1b4c2cb6cba1ca5e53b9e85eeeab02154cab4b70894c96e97a9c747a26d19d63` |
| `png-1.6.59.zip` | [libpng v1.6.59](https://github.com/glennrp/libpng/archive/refs/tags/v1.6.59.zip) | `6fd97a1e5e2ac55cec0a538a9800a351eb3844086736291844ebd12db72ba433` |

Сохраните все архивы в одну папку. `build.ps1` сам проверяет все четыре SHA-256 **до распаковки** и останавливается при несовпадении. Для отдельной проверки используйте `-VerifyOnly` с теми же аргументами. Скрипт требует `tar.exe` в PATH и не скачивает или изменяет исходный OrcaSlicer. Он распаковывает архивы в `$Work/sources`; если папка существует, выберите новую рабочую папку. Не помещайте рабочий каталог и результат внутрь исходного дерева проекта, если они не должны попасть в архив исходников.

Пример из PowerShell, указав пути вашей машины:

```powershell
.\build.ps1 `
  -Ndk 'D:\Android\android-ndk-r30' `
  -Cmake 'D:\tools\cmake\bin\cmake.exe' `
  -Ninja 'D:\tools\ninja.exe' `
  -Archives 'D:\orca-archives' `
  -Work 'D:\orca-image-build' `
  -Prefix 'D:\orca-image-install' `
  -Jobs 4
```

`$Prefix` можно совместить с префиксом других зависимостей при последующей интеграции, но CMake package-файлы содержат конкретные пути сборочной машины. При переносе префикса OpenCV export сохраняет **абсолютные пути** к `libjpeg.a` и `libpng.a`; потребуется пересборка или исправление конфигурации. Проверяйте пути перед использованием на другой машине.

Для Android дополнительно отключены `BUILD_ANDROID_EXAMPLES` и `INSTALL_ANDROID_EXAMPLES`: общий флаг `BUILD_EXAMPLES=OFF` их не отключает. `CMAKE_POLICY_VERSION_MINIMUM=3.5` нужен CMake 4 для старых CMake-файлов, PIC — для будущей общей JNI-библиотеки. PNG и OpenCV используют системную zlib NDK; Assimp — собственную статическую zlib в соответствии с рецептом OrcaSlicer. При интеграции нужно проверить общую линковку и совместимость libc++ со всеми зависимостями.

Результаты в выбранном `$Prefix`: `lib/libassimp.a`, `lib/libjpeg.a`, `lib/libpng.a`, `sdk/native/staticlibs/arm64-v8a/libopencv_world.a`; конфигурации пакетов — `lib/cmake/assimp-5.4` и `sdk/native/jni`. Для монолитного OpenCV используйте `find_package(OpenCV CONFIG REQUIRED COMPONENTS world)` и цель `opencv_world`: запрос отдельных компонентов `core`, `imgcodecs`, `imgproc` может вернуть имена отдельных библиотек, которые в этой сборке отсутствуют.

Папка `link-check` содержит минимальный исходник для проверки совместной линковки `cv::imdecode` и `Assimp::Importer` с `--no-undefined`. Укажите NDK toolchain, `ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-26`, `OpenCV_DIR=$Prefix/sdk/native/jni` и `assimp_DIR=$Prefix/lib/cmake/assimp-5.4` при конфигурации CMake. Такая проверка успешно создала AArch64 `.so` с системными зависимостями `libdl`, `libm`, `liblog`, `libz`, `libc` в указанной среде; **это проверка линковки, не запуск на устройстве и не проверка всего движка**.
