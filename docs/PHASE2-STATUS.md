# Kepler Slicer — статус Фазы 2 (Android)

Дата среза: 2026-11-01. Основание: `SPEC.md` в `Desktop/orca-mobile-handoff`, разделы 0.5, 5, 9–11; копия [ТЗ](handoff/SPEC.md) хранится в проекте. Разрешена Фаза 2: Settings, Process, Filament, Printer, Preferences, поиск, пресеты и проверка покрытия. iOS отложен. Фазы 3–7 не разрешены этим отчётом.

**Фаза 2 не объявляется завершённой.** Исправленный кандидат 30B9… установлен с разрешения владельца и проверен через computer use. В нём найдены проблемы picker и контраста; их исправления включены в новый, ещё не установленный кандидат E327…. Финальная runtime-проверка, приёмка покрытия и сравнение с настольной Orca остаются открытыми.

## Реализовано и ограничения

| Требование ТЗ | Текущее состояние кода | Ограничение / что ещё подтвердить |
|---|---|---|
| Settings в Glass UI | Нативные Java/Views; хаб Printer, Filament, Process и переходы к нужным разделам; нижняя навигация | Полное совпадение со всеми макетами с допуском ≤4 dp не измерено; визуальная приёмка финального APK ожидается |
| Process: все вкладки | Quality, Strength, Speed, Support, Multimaterial, Others, Notes; строки из схемы с группами | Наличие строки в схеме не доказывает эквивалентность её поведения всем условиям десктопа |
| Filament / Printer | Отдельные редакторы из той же схемы, выбор системных и пользовательских профилей | Сейчас один активный слот материала; полноценные несколько материалов/экструдеров и Workspace не следует считать перенесёнными этой фазой |
| Метаданные опций | Ключ, label, tooltip, тип, единицы, границы, enum, режим, страница/группа и ссылки на исходник; 431 Process + 168 Filament + 230 Printer = 829 строк | Статический генератор сохраняет неразрешённые выражения как неизвестные; runtime-условия и точные значения по умолчанию требуют сверки |
| Виджеты | bool, числа/единицы, проценты и float-or-percent, enum, строки/многострочный текст, векторы и составные значения; проверка ввода | Кнопки/действия десктопа без обычного ConfigOption не становятся рабочими от наличия схемы. Векторы местами редактируются как структурированное значение, а не специализированным визуальным контролом |
| Advanced, поиск и помощь | Фильтр видимости; поиск label/key по разделам и вкладкам; переход/подсветка; помощь с ключом и исходником по долгому нажатию | Полная матрица условной видимости Simple/Advanced/Expert по всем принтерам и возможностям не подтверждена |
| Длинные списки | Виртуализированный `ListView` с `SettingsOptionAdapter`, закреплённые шапка/вкладки/навигация | Производительность всех крупных профилей, крупного шрифта и API 26–30 не измерена |
| Пресеты | System/user, наследование, draft/dirty, Save, Save as, Reset, Delete; system неизменяемы; транзакционные проверки конфликтов и родителей | Реальный desktop round-trip ещё не проверен; импорт ZIP с сопутствующими файлами явно отклоняется |
| Global / Objects | Глобальные профили; типизированные локальные Process overrides для одного текущего native object, привязанные к private filename и plate | **Только один native object.** Два и более объекта явно отклоняются. Нет отдельных overrides объёма, экземпляра и полноценной конфигурации пластин |
| JSON / ZIP / 3MF конфигурации | Доступный экран `SettingsTransferActivity`, SAF, фоновые операции и ошибки; просмотр/импорт embedded presets; новая 3MF с заменой `project_settings.config` | Конфиг проекта не применяется молча к нарезке. Изменение конфигурации существующей 3MF не означает поддержку её активных настроек текущим native slicer |
| Preferences | General / Control / Online / Associate / Developer; подключённые настройки и действия используют существующие Android-механизмы | **39 уникальных пунктов явно помечены «Не перенесено»**; список ниже. Это незавершённая часть Фазы 2, даже если часть зависимостей относится к последующим фазам |
| Состояния и доступность | Loading/error/empty, блокировка действий на время операции, подписи и системный Back; строки новых экранов в ресурсах | TalkBack, динамический шрифт, все offline/disabled-сценарии и контраст каждого состояния не прошли полный аудит |
| Покрытие десктопа | Генератор, независимый статический auditor и экспорт inventory строк runtime adapter | Статус auditor — **`incomplete`**; критерий «0 необъяснённых пропусков» полностью не доказан |

## Путь настроек к неизменённому ядру

1. `PresetRepository` читает оригинальные JSON из неизменяемого `orca_profiles.zip`, разрешает цепочку наследования и сохраняет неизвестные поля, массивы, null и типы JSON. Значение дочерней опции заменяет значение родителя целиком. Произвольные значения по умолчанию при разрешении JSON не придумываются.
2. `SettingsState.capture()` фиксирует выбранные Printer / Process / Filament и ближайших system ancestors. Snapshot содержит замороженные копии и стабильный fingerprint; повторное чтение защищает от смены выбранного профиля во время подготовки.
3. `CompatibilityResources` копирует нужные vendor directories, включая vendor каждого overlay, в закрытый кэш. Проверяются kind/path, выход за каталог и наличие исходного файла. Оригинальный asset не перезаписывается.
4. Overlay устанавливается только вместо leaf JSON его доверенного system ancestor в частной копии. Исходные name/type/inherits и другие поля идентичности сохраняются; options и неизвестные JSON-поля передаются оригинальному Orca JSON loader. Валидация и нарезка используют тот же зафиксированный набор профилей.
5. Изменённые настройки Process проходят существующую native-валидацию. Один объект может наложить свои Process values поверх глобального профиля; результат нарезки связан с fingerprint профилей, overrides и выбранной модели/пластины. Несовпадение scope приводит к ошибке, а не к применению настроек к другой модели.
6. `ObjectSettingsStore.current()` получает native metadata на worker. После save рядом с hash(file + plate) атомарно хранится доказательство `native_object_count=1`. UI-safe `snapshot()` не импортирует OBJ/3MF/mesh; старые или повреждённые непустые записи без доказательства требуют снова открыть и сохранить настройки объекта. Очистка и rollback затрагивают и data, и count.

Чтение значений для показа отдельно отслеживает источник: типизированные Process metadata, сериализованные metadata / проверенный resolved INI, definition default. В коде приблизительные сериализованные значения отмечаются `≈`, fallback из определения — `†`; они не объявляются точным effective value выбранного профиля и не записываются обратно как неизменённые overrides. Stream-сериализация INI может округлять числа. На установленном 30B9… Process загрузился без ошибки INI и легенда `† / ≈` видна; это проверка отображения, а не доказательство точности всех effective values.

Подключённая библиотека и JNI/core в Фазе 2 не изменялись. SHA-256 [libkepler_slicer_experimental.so](../app/src/main/jniLibs/arm64-v8a/libkepler_slicer_experimental.so) совпадает с зафиксированным исходным состоянием:

`28EBDE4D17FFA8D5EAF53C244C49DC9B3DEC46C02068FCDE0D7479EC0F4B18E3`

Текущие native guards сохраняются: 3MF с непустым project config, embedded presets/assets, заменёнными/нераспознанными настройками, активным plate config, custom plate G-code, неподдержанными единицами/transform или несколькими пластинами не выдаётся за корректно нарезанный проект. Исходный проект сохраняется. Успешный пример geometry-only 3MF не доказывает поддержку произвольного Orca project.

## Пресеты и форматы обмена

Источник закреплён на Orca revision `6d2869c430284714f0a22570c38d3b359965a802`. В индексе **14 158** preset JSON; 458 JSON-документов моделей/ID maps и других непрофильных данных исключены явно.

| Kind | Все индексированные JSON, включая bases | Instantiated профили |
|---|---:|---:|
| Process | 3 746 | 3 239 |
| Filament | 8 946 | 7 570 |
| Printer / machine | 1 466 | 1 188 |
| Всего | 14 158 | 11 997 |

Для индексированных цепочек проверено 0 missing / ambiguous / cyclic parents. Старый printer catalog содержал 1 100 instantiated entries; ещё 88, включая вложенные vendor paths, доступны через доверенный system-ancestor factory. Это не разрешает импорт произвольного JSON как системного принтера.

Один preset экспортируется в исходной Orca JSON-схеме. JSON wrapper `kepler-presets-v1` обозначен как собственный формат обмена Kepler; он **не называется** desktop Orca JSON-bundle. Orca ZIP содержит отдельные preset JSON и пользовательских ancestors для оригинального ZIP-import route. ZIP с companion bed models/textures или иными non-JSON entries отклоняется целиком: эти assets пока не переносятся и не отбрасываются молча.

`ProjectConfigArchive` читает `Metadata/project_settings.config` и embedded preset JSON существующего проекта. Сохранение создаёт новую 3MF; содержимое всех остальных ZIP entries, включая геометрию, thumbnails, plate data и неизвестные entries, сохраняется побайтно после распаковки. Байт-в-байт совпадение сжатого ZIP, всех ZIP attributes или подписи архива не обещается. Нет пересоздания геометрии. Подробнее: [PRESET-REPOSITORY.md](PRESET-REPOSITORY.md).

## Проверки на host

Повторный запуск 2026-11-01: все перечисленные suites прошли. Используется настоящий `org.json`; Android adapters и JNI boundary там, где они нужны, — явные test doubles.

| Проверка | Результат | Что доказывает |
|---|---|---|
| `tools/test_presets.ps1` с официальной OrcaBadge.3mf | 59 assertions | Наследование, неизвестные поля/null, запрет изменения system, draft/reset/save, конфликты, transactional import, JSON/ZIP ancestors, сохранность остальных 3MF payloads |
| `tools/test_settings_state.ps1` | 87 assertions | Immutable overlays/fingerprints, path/vendor safety, selection/rollback, однообъектный scope и proof, отсутствие native mesh load на UI fingerprint path |
| `tools/test_process_overrides.ps1` | 26 assertions | Привязка overrides и результата к выбору/идентичности, конкурентные изменения, транзакционные отказы |
| `tools/test_settings_schema.ps1` | Settings schema host checks passed; Orca INI codec checks passed | Схема 431/168/230, typed parsing/validation, immutable metadata, строки/векторы/percent/nullable и экранирование INI |
| `tools/test_native_config_values.ps1` | 32 checks, **JNI not executed** | Источники значений, fallback/ошибки, сериализованные типы и отсутствие ложного утверждения о precise effective defaults; реальные JNI/device этим набором не проверяются |
| `tools/tests/test_settings_coverage.py --orca work/OrcaSlicer` | Host checks passed; overall audit remains incomplete | Обнаружение устаревшего audit и отсутствующего UI key на искусственном inventory; отдельный прогон с реальным runtime inventory описан ниже |
| `:app:compileDebugJavaWithJavac` | BUILD SUCCESSFUL | Компиляция Android Java на текущем срезе; не замена runtime-проверке |
| `:app:assembleDebug` | BUILD SUCCESSFUL; APK E327… собран | Установка этого кандидата ещё ожидается |
| `:app:lintDebug` | Не проходит: 8 существующих ошибок | Три старых обработчика Back, два API 27 атрибута при minSdk 26 и три старых WrongConstant. Новых ошибок Фазы 2 в этом запуске не найдено; lint нельзя считать зелёным |

Актуальный статический audit: 598 literal calls (525 direct + 73 reference-only); 0 missing schema keys/placements; 42 dynamic calls и 7 dynamic definitions разобраны с привязкой к хешам исходников; schema stale=false, audit_error=null. Дополнительно проверен отдельно экспортированный через CU runtime inventory `runtime_adapter_rows_all_sections_advanced` (431 Process, 168 Filament, 230 Printer; SHA-256 `AFF339D0A799B11988F9CB60B22C6D128B6EB2611ABC4650CC2192FDA2FB6BE0`): отсутствующих в inventory ключей среди разобранных literal и dynamic calls — 0. `extruders_count` является отдельным desktop UI-псевдополем, а не preset key. Inventory отражает объявленные строки adapter, но не доказывает работоспособность каждого редактора, применение значений или всю условную desktop UI; общий статус остаётся **`incomplete`**.

Дополнительные object/plate/SLA scopes учитываются отдельно. В частности, `curr_bed_type`, `first_layer_sequence_choice`, `other_layers_sequence_choice` не подменяются обычными preset options. Остаются runtime conditions, extruder/index variations, helpers и действия без буквального ConfigOption. Итоговый статус **`incomplete`** намеренный; критерий приёмки ТЗ 10.2 пока открыт.

## Проверено через computer use и что ещё ожидается

Наблюдения ниже переданы root-агентом после работы через Windows UI / Android Studio, без shell adb. Две установленные сборки и новый неустановленный кандидат различаются явно.

### Первый установленный кандидат

APK SHA-256:

`D5D3AF7DC68F1F0B0EEEA0F96296C1E423BF2B7B2E0F31D199D39379EFA729F7`

| Сценарий на D5D3… | Подтверждённое наблюдение |
|---|---|
| Settings hub | Открывается |
| Process | Профиль и строки настроек загружаются |
| Изменение и Save as | Высота слоя 0.24 введена; пользовательский профиль сохранён; после повторного открытия значение 0.24 присутствует |
| Geometry-only 3MF | Модель нарезана; показан G-code размером 218 814 bytes |
| SAF export | Через системный диалог сохранён `Downloads/geometry.gcode` |

G-code затем извлечён через Android Studio Device Explorer в [geometry-layer-024.gcode](../../Kepler-Glass-Phase2-review/evidence/geometry-layer-024.gcode). На host подтверждены размер **218 814 bytes** и SHA-256:

`952E21054B0FCC78DD9C2C53D3C4576B0C0DF0AD954BD1CBC9F68A9986384897`

В конфигурационных комментариях файла: `layer_height = 0.24`, `nozzle_diameter = 0.6`. Первый слой содержит `Z:0.2 / HEIGHT:0.2`, следующий — `Z:0.44 / HEIGHT:0.24`. Это подтверждает использование 0.24 для последующих слоёв на одной модели и выбранном наборе профилей. Комментарий `print_settings_id = 0.20mm SPEED @CORE One 0.6` сохраняет original system identity, поскольку private overlay оставляет идентификатор исходного профиля; это не утверждение о неизменённой высоте слоя. UI результата использует пользовательские display names. **Desktop G-code parity этим примером не установлена.**

### Установленный исправленный кандидат 30B9…

APK установлен успешно после разрешения владельца. SHA-256:

`30B9DEBACDD456ED4C8B1761E39C8D6FE443E5EC4D7B1A4A0B4ECEB93D1383AF`

| Сценарий на 30B9… | Подтверждённое наблюдение | Предел проверки |
|---|---|---|
| Process / сохранённый профиль | Загружается без ошибки INI; пользовательский preset с 0.24 сохранился; легенда `† / ≈` видна | Точность всех effective defaults не проверена |
| Quality / Advanced | Переключение Advanced изменило число строк 109 → 7 | Не полная проверка всех режимов и вкладок |
| Filament | Загружены Filament: 44 строки и Cooling: 23 строки | Изменение и сохранение материала не проверены |
| Printer | Basic information: 55 строк загружены | Изменение принтера и полная проверка совместимости не выполнены |
| Preferences | General открыт | Все вкладки/контролы не проверены; 39 функций остаются явно не перенесёнными |
| Objects | Object 1 для geometry.3mf открыт без зависания; редактор открыт и закрыт через Cancel | Изменение и save object overrides **не проверены** |
| Числовой редактор / IME | Компактный диалог; Save / Cancel доступны при открытой клавиатуре | Остальные типы редакторов и поворот требуют проверки |
| Транспорт 3MF | Открыт `project.3mf` ≈817 КБ: config `{}`, embedded presets 0; Save NEW 3MF сохранил `Downloads/project-settings.3mf`, показан `Document saved` | Config не редактировался. Desktop comparison и сравнение сохранённого файла с исходным в этом CU-сценарии не выполнены |

Снимки [пакета проверки](../../Kepler-Glass-Phase2-review/index.html) `settings.png`, `process.png`, `filament.png`, `printer.png`, `preferences.png`, `transfer.png`, `editor-ime.png` сняты **на 30B9…**, а не на текущем новом кандидате. Для picker оставлено место ожидания снимка после исправления и установки.

### Найдено на устройстве; исправления пока не установлены

- В picker поле поиска занимало всю высоту и закрывало список. В исходниках высота исправлена на 52 dp; runtime-подтверждение исправления ожидается.
- Во вложенном поле Printer на 30B9… измерен контраст **3.91:1**, ниже требуемого 4.5:1. Для этого поля в новом кандидате используется непрозрачная заливка token `#5A5F69`; расчётный контраст с белым **6.41:1**. Это расчёт, а не повторный замер установленного исправления.

Новый готовый APK 219 819 620 байт (≈219,8 МБ), SHA-256:

`E327B46319299B6670C44B79F81E66456D77A936C1398E956A2E4A1366EB77CE`

Установка и проверка E327… ожидают ответа владельца на запрос установки. Промежуточный кандидат `50E01266175A2DDF9648A89EC767E792292645765A01CD3FFBD389A9985F5E91` **не устанавливался**. Для E327… прежде всего нужны повторные CU-проверки picker и контраста; остаются проверки поиска, типов редакторов, object save, ошибок/отмены/поворота, JSON/ZIP/3MF transport и повторной нарезки/экспорта. Нельзя переносить результаты 30B9… или D5D3… на новый APK без проверки.

Остаются не подтверждены реальным desktop UI: двусторонний JSON/ZIP/3MF round-trip без потерь и одинаковый G-code на одинаковых моделях/настройках. Нужны выбранные владельцем версия desktop Orca и критерий сравнения. Фаза 2 остаётся **runtime pending**, а не phase complete.

## Preferences: явно не перенесённые пункты

В текущем `PreferencesActivity` 39 вызовов `unavailable(...)` с **39 уникальными resource keys**. Ранее упоминавшиеся 33 относятся к более раннему срезу. Все перечисленные строки показывают «Не перенесено» и объяснение; они не имитируют работающую настройку. Префикс каждого ключа ниже — `phase2_preferences_`:

- General: `language`, `home`, `single`, `splash`, `downloads`, `load`, `recent_add`, `gcode_warning`, `step`, `backup`, `group_presets`.
- Control: `flush`, `arrange`, `auto_slice`, `temperature`, `camera_style`, `zoom_center`, `free_camera`, `swap`, `clear_projects`, `clear_presets`, `clear_sync`.
- Online: `region`, `stealth`, `test`, `stable`, `auto_sync`, `update_profiles`, `filament_sync`, `plugin_enable`, `plugin_version`.
- Associate: `associate_stl`, `associate_3mf`, `associate_obj`.
- Developer: `developer_mode`, `ams`, `storage_abnormal`, `log`, `reload`.

Сетевые протоколы/Device, новые инструменты Workspace, полноценные plates, калибровки и доработка Preview относятся к последующим фазам ТЗ. Их зависимости не являются разрешением начать эти фазы и не снимают отмеченные ограничения текущего интерфейса.

## Предложение native API для Objects / plates — не реализовано

Это предложение для отдельного согласования по **SPEC 0.5**: без разрешения ядро менять нельзя. Оно не является выполнением Фазы 3, не добавлено в JNI и не меняет библиотеку из этого среза.

Чтобы снять ограничение одного объекта корректно, нужен типизированный API исходной модели и конфигураций, а не подмена глобального Process для нескольких объектов:

1. `openModelSession(sourceHash, file, selectedPlate)` → session/revision и стабильные ID исходных `ModelObject`, `ModelVolume`, `ModelInstance`, пластин и связей object/instance → plate. Сохранять исходные геометрию, единицы, transform, modifiers и embedded config; stale revision/ID отклонять.
2. `applyScopedConfig(session, expectedRevision, target, ConfigOptionJSON)` → validated snapshot / structured errors. Target явно различает project, plate, object, volume, instance. JSON проходит **существующий Orca config loader**, ConfigOptionDef/type/allowed-scope validation и наследование, с сохранением неизвестных полей для transport. Неверный тип/неподдержанный scope не должен тихо превращаться в глобальное значение.
3. Привязывать загруженные ConfigOptionJSON к native `ModelObject.config` / `ModelVolume.config`; plate config — к исходной plate mapping. Для `ModelInstance` нужна отдельно согласованная mapping и семантика приоритетов: в текущем upstream `ModelInstance` нет собственного поля `config`, поэтому его нельзя считать готовой точкой применения. Хранить исходный target/instance ID и не терять эту связь при выборе/копировании модели. Проверить приоритеты project → plate → object → volume/instance на desktop fixtures, прежде чем включать UI.
4. `dumpEffectiveConfig(kind, selectionSnapshot, optionalTarget)` для **process / filament / printer** → точные typed JSON значения и definition metadata/defaults с источником каждого значения. Сохранять различие percent / абсолютного float-or-percent, nullable values, vectors, enum keys и double precision; не получать точные defaults обратным разбором форматированного INI. Это заменило бы нынешние display-only/приблизительные fallback values.
5. `sliceModelSession(session, expectedRevision, configSnapshot)` должен использовать эти же scope mappings и settings revision, отдавать результат с fingerprint; экспорт новой 3MF должен сохранять конфигурации по их исходным scope и все остальные entries. Нужны desktop round-trip fixtures с несколькими object/volume/instances и plate configs, сравнение effective configs и G-code до снятия существующих guards.

До такого согласования сохраняется текущий безопасный путь JSON overlays и явный отказ там, где native project/plate/object semantics ещё не поддержаны.
