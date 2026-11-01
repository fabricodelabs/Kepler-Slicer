# Экспериментальная сборка Draco для Android

Сценарий собирает Draco 1.5.7 из зависимости официального [OrcaSlicer](https://github.com/OrcaSlicer/OrcaSlicer) на коммите `6d2869c430284714f0a22570c38d3b359965a802`. Используется архив `https://github.com/google/draco/archive/refs/tags/1.5.7.zip` с SHA-256 `27b72ba2d5ff3d0a9814ad40d4cb88f8dc89a35491c0866d952473f8f9416b77`, как в `deps/Draco/Draco.cmake`. Архив проверяется до распаковки.

Нужны PowerShell, Git, CMake, Ninja, Android NDK и checkout официального OrcaSlicer с указанным коммитом. `BuildRoot` должен быть отдельным каталогом: скрипт не перезаписывает существующий `BuildRoot/src`. При необходимости передайте пути к исполняемым CMake и Ninja явно:

```powershell
./build.ps1 -OrcaSource /path/to/OrcaSlicer `
  -AndroidNdk /path/to/android-ndk `
  -BuildRoot /path/to/new/draco-build `
  -CMake /path/to/cmake -Ninja /path/to/ninja -Jobs 4
```

Сборка статическая (`BUILD_SHARED_LIBS=OFF`), Release, Android arm64-v8a/API 26. Другие настройки Draco оставлены на upstream-значениях. Результаты находятся в `BuildRoot/prefix`, в том числе `lib/libdraco.a` и CMake package `share/cmake/draco`. Скрипт также собирает пробную shared-библиотеку через официальный `FindDraco.cmake` из OrcaSlicer и цель `draco::draco`.

На Windows с NDK r30 и CMake 4.4.4 сборка и линковка проверялись для AArch64; код не запускался на Android и не проверялся внутри слайсера. Draco распространяется под Apache License 2.0; сохраняйте лицензии и уведомления при распространении. Сам OrcaSlicer распространяется под AGPL-3.0. Этот модуль не является слайсером и не входит в APK.
