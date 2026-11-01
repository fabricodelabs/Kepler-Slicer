# Экспериментальная кросс-сборка математических зависимостей

Эти скрипты собирают **только** GMP, GMPXX, MPFR и заголовочную библиотеку CGAL для Android arm64-v8a, API 26. Они не собирают `libslic3r` и не создают G-code. Параметры и версии взяты из официального [OrcaSlicer](https://github.com/OrcaSlicer/OrcaSlicer) на коммите `6d2869c430284714f0a22570c38d3b359965a802`; `build.sh` проверяет этот коммит в `ORCA_SOURCE` и SHA-256 архивов перед распаковкой.

| Зависимость | Официальный архив | SHA-256 |
|---|---|---|
| GMP 6.3.0 | `https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz` | `a3c2b80201b89e68616f4ad30bc66aee4927c3ce50e33929ca819d5c43538898` |
| MPFR 4.2.2 | `https://ftp.gnu.org/gnu/mpfr/mpfr-4.2.2.tar.bz2` | `9ad62c7dc910303cd384ff8f1f4767a655124980bb6d8650fe62c815a231bb7b` |
| CGAL 6.2.1 | `https://github.com/CGAL/cgal/releases/download/v6.2.1/CGAL-6.2.1.zip` | `eebd737d9b7f0199647ba5c1f9f6c7a3d0651aacef9c2fd4dedc52da6c25edbe` |

Нужны Android NDK с toolchain для используемого хоста, Git, Bash, curl, sha256sum, tar с поддержкой xz/bzip2, unzip, GNU patch, GNU make, GNU m4, CMake и Ninja. Нужен также нативный C-компилятор для генераторов GMP. Для проверки CGAL нужен отдельно собранный для Android Boost. При работе в Windows через Git Bash требуется подготовить make и m4 и запустить оболочку из среды сборки с доступными Windows SDK и библиотеками компилятора; `HOST_CC` должен указывать на работающий нативный компилятор, например clang с подготовленной средой MSVC.

```bash
export ORCA_SOURCE=/path/to/official/OrcaSlicer
export ANDROID_NDK_ROOT=/path/to/android-ndk
export BUILD_ROOT=/path/without/spaces/kepler-math-build
export HOST_CC=/path/to/native/clang
bash ./build.sh

# Boost должен быть собран отдельно для той же ABI и API.
export BOOST_PREFIX=/path/to/android-boost-prefix
bash ./check.sh
```

`BUILD_ROOT` и путь NDK не должны содержать пробелов; каталог `BUILD_ROOT/src` не должен существовать. Скрипт не удаляет существующий исходный каталог. Архивы, исходники, журналы консоли, результат установки и промежуточные файлы остаются в `BUILD_ROOT`. GMP использует `--disable-assembly`, статические библиотеки и C++ API; MPFR — статическую библиотеку и `--enable-thread-safe`. Для MPFR используется `configure` из официального архива вместо повторного запуска `autoreconf`. `check.sh` компилирует и линкует тестовую `.so` с CGAL, GMP и MPFR; на устройстве её не запускает.

Сборка эквивалентных исходников и параметров, а также скрипт `check.sh` проверены с Windows, NDK r30 и CMake 4.4.4. Сам упакованный `build.sh` проверен синтаксически, но ещё не проверен полным повторным запуском с пустого каталога. При cross configure MPFR не может выполнить проверочные программы и делает предположения о TLS и свойствах чисел с плавающей точкой; NDK Clang также сообщает, что игнорирует `-ffloat-store`. Перед применением в слайсере необходимо проверить поведение и поточную безопасность на Android. Сверяйте и сохраняйте лицензии каждого включаемого компонента; сам OrcaSlicer распространяется под AGPL-3.0.
