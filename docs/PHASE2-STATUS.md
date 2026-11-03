# Kepler Slicer — статус Фазы 2 (Android)

> Обновление 2026-11-02 после согласования native-плана: **52 реальные native-проверки прошли на установленном APK 9FD3374C… / native 8C146C74… через computer use.** Фаза 2 не завершена; повторные UI-сценарии финального APK продолжаются. Текущий инвентарь потребителей и **8 полностью заблокированных пунктов** — в [PREFERENCES-39-STATUS.md](PREFERENCES-39-STATUS.md), новый срез — в разделе [«Native Preferences: реализация после согласования»](#native-preferences-реализация-после-согласования) в конце этого файла. [PREFERENCES39-VERIFICATION.md](PREFERENCES39-VERIFICATION.md) описывает историческую сборку D48B…, 373 host checks и прежние 13 блокировок до native-изменений. Ни её CU/hashes, ни отчёт 61C9… ниже не переобозначены как доказательства новой сборки.

## Исторический отчёт до согласованных native-изменений

Дата среза: 2026-11-01. Основание: `SPEC.md` в `Desktop/orca-mobile-handoff`, разделы 0.5, 5, 9–11; копия [ТЗ](handoff/SPEC.md) хранится в проекте и обновлена побайтно с оригинала (SHA-256 `994F8399BCAD8655C2767BE4454CA5BE7A51EBB4BBD363C8D49D48FCAA052168`). Разрешена Фаза 2: Settings, Process, Filament, Printer, Preferences, поиск, пресеты и проверка покрытия. iOS отложен. Фазы 3–7 не разрешены этим отчётом.

**Фаза 2 не объявляется завершённой.** Текущий 61C9… с исправлением identity установлен с разрешения владельца через Android Studio; новый Save As и JSON export проверены через CU и извлечение файла. Исторические E327… проверки picker, выбранного контраста, JSON conflict и 3MF сохранены отдельно. Полная приёмка Фазы 2, успешный импорт нового профиля, ZIP UI и сравнение с настольной Orca остаются открытыми.

Основной `Kepler-Slicer-Glass-Phase2.apk` соответствует **61C9…**, code commit `1835779`. Исторический E327… сохранён отдельным `Kepler-Slicer-Glass-Phase2-E327.apk`. Восемь прежних снимков не переобозначены; дополнительный Save As/export proof 61C9… указан отдельно. Source archive упаковывается после фиксации code/docs snapshot; его метаданные находятся в [release.json](../../Kepler-Glass-Phase2-review/release.json), hash архива не встраивается в его собственные документы.

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

## Исторический путь настроек к неизменённому ядру

1. `PresetRepository` читает оригинальные JSON из неизменяемого `orca_profiles.zip`, разрешает цепочку наследования и сохраняет неизвестные поля, массивы, null и типы JSON. Значение дочерней опции заменяет значение родителя целиком. Произвольные значения по умолчанию при разрешении JSON не придумываются.
2. `SettingsState.capture()` фиксирует выбранные Printer / Process / Filament и ближайших system ancestors. Snapshot содержит замороженные копии и стабильный fingerprint; повторное чтение защищает от смены выбранного профиля во время подготовки.
3. `CompatibilityResources` копирует нужные vendor directories, включая vendor каждого overlay, в закрытый кэш. Проверяются kind/path, выход за каталог и наличие исходного файла. Оригинальный asset не перезаписывается.
4. Overlay устанавливается только вместо leaf JSON его доверенного system ancestor в частной копии. Исходные name/type/inherits и другие поля идентичности сохраняются; options и неизвестные JSON-поля передаются оригинальному Orca JSON loader. Валидация и нарезка используют тот же зафиксированный набор профилей.
5. Изменённые настройки Process проходят существующую native-валидацию. Один объект может наложить свои Process values поверх глобального профиля; результат нарезки связан с fingerprint профилей, overrides и выбранной модели/пластины. Несовпадение scope приводит к ошибке, а не к применению настроек к другой модели.
6. `ObjectSettingsStore.current()` получает native metadata на worker. После save рядом с hash(file + plate) атомарно хранится доказательство `native_object_count=1`. UI-safe `snapshot()` не импортирует OBJ/3MF/mesh; старые или повреждённые непустые записи без доказательства требуют снова открыть и сохранить настройки объекта. Очистка и rollback затрагивают и data, и count.

Чтение значений для показа отдельно отслеживает источник: типизированные Process metadata, сериализованные metadata / проверенный resolved INI, definition default. В коде приблизительные сериализованные значения отмечаются `≈`, fallback из определения — `†`; они не объявляются точным effective value выбранного профиля и не записываются обратно как неизменённые overrides. Stream-сериализация INI может округлять числа. На установленных 30B9… и E327… Process загрузился без ошибки INI и легенда `† / ≈` видна; это проверка отображения, а не доказательство точности всех effective values.

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
| `tools/test_presets.ps1` с официальной OrcaBadge.3mf | 81 assertions после identity fix (ранее 59) | Прежние проверки и 22 regression assertions: system/user → новый Save As очищает ASCII-case `setting_id`; existing Save/import/export сохраняют identity; unknown Unicode keys и parent links не теряются |
| `tools/test_settings_state.ps1` | 87 assertions | Immutable overlays/fingerprints, path/vendor safety, selection/rollback, однообъектный scope и proof, отсутствие native mesh load на UI fingerprint path |
| `tools/test_process_overrides.ps1` | 26 assertions | Привязка overrides и результата к выбору/идентичности, конкурентные изменения, транзакционные отказы |
| `tools/test_settings_schema.ps1` | Settings schema host checks passed; Orca INI codec checks passed | Схема 431/168/230, typed parsing/validation, immutable metadata, строки/векторы/percent/nullable и экранирование INI |
| `tools/test_native_config_values.ps1` | 32 checks, **JNI not executed** | Источники значений, fallback/ошибки, сериализованные типы и отсутствие ложного утверждения о precise effective defaults; реальные JNI/device этим набором не проверяются |
| `tools/tests/test_settings_coverage.py --orca work/OrcaSlicer` | Host checks passed; overall audit remains incomplete | Обнаружение устаревшего audit и отсутствующего UI key на искусственном inventory; отдельный прогон с реальным runtime inventory описан ниже |
| `:app:compileDebugJavaWithJavac` | BUILD SUCCESSFUL | Компиляция Android Java на текущем срезе; не замена runtime-проверке |
| `:app:assembleDebug` | BUILD SUCCESSFUL; текущий 61C9… установлен | Ограниченные runtime-проверки разных сборок описаны ниже; весь объём Фазы 2 не закрыт |
| `:app:lintDebug` | Не проходит: 8 существующих ошибок | Три старых обработчика Back, два API 27 атрибута при minSdk 26 и три старых WrongConstant. Новых ошибок Фазы 2 в этом запуске не найдено; lint нельзя считать зелёным |

Актуальный статический audit: 598 literal calls (525 direct + 73 reference-only); 0 missing schema keys/placements; 42 dynamic calls и 7 dynamic definitions разобраны с привязкой к хешам исходников; schema stale=false, audit_error=null. Дополнительно проверен отдельно экспортированный через CU runtime inventory `runtime_adapter_rows_all_sections_advanced` (431 Process, 168 Filament, 230 Printer; SHA-256 `AFF339D0A799B11988F9CB60B22C6D128B6EB2611ABC4650CC2192FDA2FB6BE0`): отсутствующих в inventory ключей среди разобранных literal и dynamic calls — 0. `extruders_count` является отдельным desktop UI-псевдополем, а не preset key. Inventory отражает объявленные строки adapter, но не доказывает работоспособность каждого редактора, применение значений или всю условную desktop UI; общий статус остаётся **`incomplete`**.

Дополнительные object/plate/SLA scopes учитываются отдельно. В частности, `curr_bed_type`, `first_layer_sequence_choice`, `other_layers_sequence_choice` не подменяются обычными preset options. Остаются runtime conditions, extruder/index variations, helpers и действия без буквального ConfigOption. Итоговый статус **`incomplete`** намеренный; критерий приёмки ТЗ 10.2 пока открыт.

## Проверено через computer use и что ещё ожидается

Наблюдения ниже переданы root-агентом после работы через Windows UI / Android Studio, без shell adb. Результаты четырёх установленных сборок разделены; действия, проверенные на одной сборке, не объявляются автоматически проверенными на другой.

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

Исторические семь снимков 30B9… сохранены в `Kepler-Glass-Phase2-review/previous-30B9/`. В текущей галерее `filament.png`, `printer.png`, `preferences.png`, `editor-ime.png` остаются снимками **30B9…**. `settings.png`, `process.png`, `preset-picker.png`, `transfer.png` заменены или добавлены по результатам **E327…**. Пофайловые SHA-256 и сборки указаны в [release.json](../../Kepler-Glass-Phase2-review/release.json).

### Исторический установленный E327…: ограниченная runtime-проверка

APK 219 819 620 байт (≈219,8 МБ) установлен успешно через Android Studio **Shift+F10** после разрешения владельца. SHA-256:

`E327B46319299B6670C44B79F81E66456D77A936C1398E956A2E4A1366EB77CE`

| Сценарий на E327… | Подтверждённое наблюдение | Предел проверки |
|---|---|---|
| Сохранённое состояние | geometry.3mf, CORE One 0.6, Prusament PLA и пользовательский Process 0.24 сохранились | Все модели и комбинации профилей не проверены |
| Process | Native values загрузились без ошибки INI; Quality: 109 строк в Advanced, легенда `† / ≈` видна | Все effective values и редакторы не проверены |
| Picker | Компактное поле поиска не закрывает список; запрос `core` фильтрует список; пользовательская строка отмечена выбранной | Полная совместимость и все фильтры не проверены |
| JSON export | Выбранный user Process экспортирован через SAF в Downloads; статус `Document saved.` | Desktop import/export этого файла не проверен |
| Повторный импорт того же JSON | Отказ `A user preset with this name already exists` | Это проверенный conflict, **не dedup и не успешный импорт нового профиля** |
| Открытие 3MF | project.3mf ≈817 КБ открыт, config `{}`, embedded presets 0; соответствующие embedded actions отключены | Проект с embedded presets/активными настройками этим не проверен |
| Невалидный JSON | Ввод `{` и Save отклонён: `End of input at character 1`, до открытия SAF | Другие синтаксические и семантические ошибки не проверены |
| Восстановление / отмена | Повторное открытие оригинала вернуло `{}`; отмена Save в SAF дала `Operation canceled.`, загруженная конфигурация сохранилась | Отмена во время копирования большого output не проверена |
| Save new 3MF | Новое уникальное `qproject-settings.3mf` сохранено через SAF; `Document saved.`; извлечённый output затем сравнен на host, результаты ниже | Проверены распакованные payloads проекта с config `{}`; это не сохранение реально изменённой настройки и не desktop round-trip |

Picker снят как `Screenshot_20261101_111754` → `preset-picker.png`; transport — `Screenshot_20261101_112200` → `transfer.png`. Все текущие снимки и их сборки перечислены в [пакете проверки](../../Kepler-Glass-Phase2-review/index.html) и `release.json`.

На 30B9… поиск picker перекрывал список, а вложенное поле Printer имело контраст **3.912:1**. На E327… в трёх выбранных полях (`printer_name`, `nozzle`, `filament`) подтверждён непрозрачный фон RGB(90,95,105) / token `#5A5F69` и контраст белого **6.411:1**. Измерения, координаты и SHA снимков сохранены в [e327-contrast.json](../../Kepler-Glass-Phase2-review/evidence/e327-contrast.json); parent card — 4.983:1, выбранная точка фона — 7.255:1. Это выборочные измерения, **не полный аудит контраста UI**.

### Извлечённые export artifacts E327…

Файлы, сохранённые Android-приложением через SAF и извлечённые через Device Explorer, проверены на host. Машинный отчёт со статическим review upstream identity: [e327-export-verification.json](../../Kepler-Glass-Phase2-review/evidence/e327-export-verification.json), SHA-256 `48DC97E71BAD3E265FE38B0220BA3FE220A475FF34D245B36CBC4ADCCD635EC6`.

| Артефакт | Размер | Проверенные факты |
|---|---:|---|
| [e327-process-024.json](../../Kepler-Glass-Phase2-review/evidence/e327-process-024.json) | 1 031 bytes | `type=process`, `from=user`, `layer_height` — строка `"0.24"`, `instantiation` — строка `"true"`; пользовательское name равно `print_settings_id`; `inherits` соответствует единственному системному родителю `Prusa/process/0.20mm SPEED @CORE One 0.6.json` |
| [e327-project-source.3mf](../../Kepler-Glass-Phase2-review/evidence/e327-project-source.3mf) | 817 091 bytes | Исходный проект для сравнения: 16 ZIP entries, config семантически `{}`, embedded preset entries по правилам приложения — 0 |
| [e327-project-saved.3mf](../../Kepler-Glass-Phase2-review/evidence/e327-project-saved.3mf) | 819 628 bytes | 16 ZIP entries; добавленных/удалённых нет; у 15 entries распакованные bytes совпадают. Отличается только `Metadata/project_settings.config`: 6 → 2 bytes, оба JSON семантически `{}` |

В экспортированном user Process значение `setting_id` **совпадает с системным родителем**. Статический review закреплённой upstream revision показывает расхождение с desktop Save As, который очищает identity нового Preset. Однако просмотренный прямой JSON-import извлекает metadata ID и не назначает его импортированному Preset; коллизия в этом пути **не доказана**. Cloud/другие пути и фактический импорт в desktop не проверены. Нельзя считать файл доказанно совместимым desktop round-trip только по валидному JSON и правильному `layer_height`.

Это исторический export **E327… до исправления creation identity**. Текущий 61C9… меняет только создание нового профиля; уже сохранённые user identities автоматически не мигрируют и существующий экспорт не переписывается.

Сравнение 3MF подтверждает сохранность остальных распакованных payloads именно этой пары файлов. ZIP metadata/compression и побайтная идентичность всего архива не сравнивались; реального семантического изменения project config не было. Поддержка активного embedded config текущим native slicing path не доказана. SHA-256 каждого исходного/выходного файла приведён в машинном отчёте и `release.json`.

Промежуточный кандидат `50E01266175A2DDF9648A89EC767E792292645765A01CD3FFBD389A9985F5E91` **не устанавливался**. Остаются не проверены через CU: успешный импорт нового JSON-профиля, ZIP import/export, изменение/save object overrides, редактирование валидного project config с проверкой полученного файла, все типы редакторов, поворот и остальные error/cancel-сценарии. Проверки на D5D3… и 30B9… сохраняются как исторические результаты.

Остаются не подтверждены реальным desktop UI: двусторонний JSON/ZIP/3MF round-trip без потерь и одинаковый G-code на одинаковых моделях/настройках. Нужны выбранные владельцем версия desktop Orca и критерий сравнения. Перечисленные runtime-сценарии E327… проверены; **Фаза 2 остаётся incomplete**, не phase complete.

### Исторический установленный 61C9…: новый Save As и export проверены

[Kepler-Slicer-Glass-Phase2.apk](../../Kepler-Slicer-Glass-Phase2.apk) и [identity-fix alias](../../Kepler-Slicer-Glass-Phase2-identity-fix.apk), **219 490 100 bytes**, SHA-256:

`61C90994B8C6C3014BAB7466A1239DE997767339E1E14F4B7E2027EFB5CE563D`

`PresetRepository.saveAs()` клонирует draft и удаляет из новой source только top-level aliases `setting_id` без учёта **ASCII-регистра**, как для новой identity в desktop Save As. Unknown Unicode-поля, imports, system JSON и identity при существующем Save сохраняются. `parent_id` / `inherits` остаются связью с родителем; неподтверждённая cloud/base_id логика не добавлена.

Host-проверки нового кода: **81** preset assertions с официальной OrcaBadge, **87** SettingsState assertions, `assembleDebug` — успешно. Native SHA-256 остаётся `28EBDE4D17FFA8D5EAF53C244C49DC9B3DEC46C02068FCDE0D7479EC0F4B18E3`. Перед исправлением regression воспроизводил копирование parent ID; после исправления проходит.

61C9… успешно установлен через Android Studio **Shift+F10** после нового разрешения владельца. geometry.3mf, CORE One 0.6 и прежний user Process 0.24 восстановились; Process загрузился без INI error. Save As из старого user в `0.20mm SPEED @CORE One 0.6 — Kepler — Kepler` прошёл Orca validation, сохранил 0.24; JSON экспортирован через SAF, показан `Document saved.`

Извлечённый [61c9-saveas-process-024.json](../../Kepler-Glass-Phase2-review/evidence/61c9-saveas-process-024.json), **1 028 bytes**, SHA-256 `DD1E6959208EAF7180C4060D01D8828EC5A32FD9B3FEACE8407987898EFC8F84`, подтверждает: `setting_id` и его ASCII-case aliases отсутствуют, `layer_height` — строка `"0.24"`, новое `name` равно `print_settings_id`, `inherits` — имя предыдущего пользовательского профиля. Это ожидаемый parent link, а не замена его новым system profile. [61c9-saveas-verification.json](../../Kepler-Glass-Phase2-review/evidence/61c9-saveas-verification.json), SHA-256 `9582F28542566B2F74EFDE50BEEFE8A6C041A171F381CB1BAB112CF5711548F5`, подтверждает сохранность всех **22 non-identity полей и их JSON-типов** по сравнению с предыдущим export. Такое сравнение не проверяет состояние старого preset в storage. Creation-only fix не выполняет автоматическую миграцию старых identities; desktop/cloud совместимость не заявляется.

Дополнительный [saveas-export-61c9.png](../../Kepler-Glass-Phase2-review/saveas-export-61c9.png), capture `Screenshot_20261101_113930`, относится к **61C9…**; остальные восемь снимков сохраняют прежние E327… / 30B9… provenance. Исторический [E327 APK](../../Kepler-Slicer-Glass-Phase2-E327.apk) и его export artifacts остаются отдельными доказательствами. Source ZIP metadata находятся в `release.json`; архив упаковывается после code/docs snapshot, без встраивания собственного hash.

## Исторический baseline Preferences: 39 явно не перенесённых пунктов

В текущем `PreferencesActivity` 39 вызовов `unavailable(...)` с **39 уникальными resource keys**. Ранее упоминавшиеся 33 относятся к более раннему срезу. Все перечисленные строки показывают «Не перенесено» и объяснение; они не имитируют работающую настройку. Префикс каждого ключа ниже — `phase2_preferences_`:

- General: `language`, `home`, `single`, `splash`, `downloads`, `load`, `recent_add`, `gcode_warning`, `step`, `backup`, `group_presets`.
- Control: `flush`, `arrange`, `auto_slice`, `temperature`, `camera_style`, `zoom_center`, `free_camera`, `swap`, `clear_projects`, `clear_presets`, `clear_sync`.
- Online: `region`, `stealth`, `test`, `stable`, `auto_sync`, `update_profiles`, `filament_sync`, `plugin_enable`, `plugin_version`.
- Associate: `associate_stl`, `associate_3mf`, `associate_obj`.
- Developer: `developer_mode`, `ams`, `storage_abnormal`, `log`, `reload`.

Сетевые протоколы/Device, новые инструменты Workspace, полноценные plates, калибровки и доработка Preview относятся к последующим фазам ТЗ. Их зависимости не являются разрешением начать эти фазы и не снимают отмеченные ограничения текущего интерфейса.

## Историческое предложение native API для Objects / plates

Это предложение относилось к срезу до согласования по **SPEC 0.5**. Ниже сохранена его исходная граница; новый согласованный Preferences-контракт описан отдельно в конце файла. Он не означает выполнение Фазы 3 или всего предложенного здесь API редактируемых модельных сессий.

Чтобы снять ограничение одного объекта корректно, нужен типизированный API исходной модели и конфигураций, а не подмена глобального Process для нескольких объектов:

1. `openModelSession(sourceHash, file, selectedPlate)` → session/revision и стабильные ID исходных `ModelObject`, `ModelVolume`, `ModelInstance`, пластин и связей object/instance → plate. Сохранять исходные геометрию, единицы, transform, modifiers и embedded config; stale revision/ID отклонять.
2. `applyScopedConfig(session, expectedRevision, target, ConfigOptionJSON)` → validated snapshot / structured errors. Target явно различает project, plate, object, volume, instance. JSON проходит **существующий Orca config loader**, ConfigOptionDef/type/allowed-scope validation и наследование, с сохранением неизвестных полей для transport. Неверный тип/неподдержанный scope не должен тихо превращаться в глобальное значение.
3. Привязывать загруженные ConfigOptionJSON к native `ModelObject.config` / `ModelVolume.config`; plate config — к исходной plate mapping. Для `ModelInstance` нужна отдельно согласованная mapping и семантика приоритетов: в текущем upstream `ModelInstance` нет собственного поля `config`, поэтому его нельзя считать готовой точкой применения. Хранить исходный target/instance ID и не терять эту связь при выборе/копировании модели. Проверить приоритеты project → plate → object → volume/instance на desktop fixtures, прежде чем включать UI.
4. `dumpEffectiveConfig(kind, selectionSnapshot, optionalTarget)` для **process / filament / printer** → точные typed JSON значения и definition metadata/defaults с источником каждого значения. Сохранять различие percent / абсолютного float-or-percent, nullable values, vectors, enum keys и double precision; не получать точные defaults обратным разбором форматированного INI. Это заменило бы нынешние display-only/приблизительные fallback values.
5. `sliceModelSession(session, expectedRevision, configSnapshot)` должен использовать эти же scope mappings и settings revision, отдавать результат с fingerprint; экспорт новой 3MF должен сохранять конфигурации по их исходным scope и все остальные entries. Нужны desktop round-trip fixtures с несколькими object/volume/instances и plate configs, сравнение effective configs и G-code до снятия существующих guards.

До такого согласования сохраняется текущий безопасный путь JSON overlays и явный отказ там, где native project/plate/object semantics ещё не поддержаны.

## Native Preferences: реализация после согласования

Срез 2026-11-02: **52 реальные native-проверки прошли на финальном установленном APK; UI-приёмка продолжается, Фаза 2 не завершена.** Владелец явно разрешил изменения ядра: «Разрешаю изменения ядра по этому плану». Основание и согласованный объём — [PREFERENCES-NATIVE-PLAN.md](PREFERENCES-NATIVE-PLAN.md). Разрешение относится к Android Preferences Фазы 2 и необходимым потребителям; iOS отложен, Фазы 3–7 не открыты. Установки и повторная проверка через computer use разрешены без нового запроса.

В отличие от исторических отчётов выше, JNI и native-библиотека изменены. Старый SHA `28EB…`, старые APK, снимки и тестовые количества остаются доказательствами только своих сборок. Сейчас установлен APK **221 194 096 bytes**, SHA-256 `9FD3374C652D926F5887068C4618B75F999B0FAC4A5F68ADD4B314307B6CECF0`, с ARM64 native SHA-256 `8C146C742B8A3B23B4ADE5C5E0E54A3EAE0705A52EDDFAC8CD64406D249DE0E1`. Установка через Android Studio/computer use прошла за **5 s 106 ms**. Отдельный release verification report сохранит снимки, подробные результаты сборки/lint и финальных UI-сценариев; этот раздел их не подменяет.

| Согласованный контракт | Реальный потребитель / состояние | Оставшаяся граница |
|---|---|---|
| 3MF: полный snapshot и явный режим загрузки | Worker получает версионированные типизированные global/plate/object/volume/layer/material и embedded-preset значения с ID, привязанными к архиву. Импорт предлагает полный проект с выбранным либо встроенным принтером или явно выбранную геометрию. Нарезка полного режима использует исходную native-модель и конфигурацию. Запоминаемый ответ о принтере действительно используется и очищается отдельным действием. | Полные multi-plate и ненулевой plate origin отклоняются, пока нет достоверного преобразования к рабочему столу. Произвольные missing parents/неполные embedded printer profiles не объявлены автоматически разрешёнными. Неизвестные/подменённые и несовместимые конфиги явно отклоняются; fallback в геометрию отсутствует. |
| 3MF: значения и состояние приложения | Mode, исходный hash, plate и material edits сохраняются с текущей моделью; профили/overrides и эти значения входят в проверку актуальности результата. Для встроенного принтера используются его реальные bed/header metadata. Settings показывает effective global/plate значения проекта отдельно от библиотеки. | Проектные значения **только для чтения**: Save/Save As/Use/Delete как для preset отключены, в библиотеку они не записываются. Настоящий редактируемый project draft и UI отдельных object/volume/layer scopes ещё не реализованы. |
| STEP/STP | Android OCCT, реальные linear/angular tolerances и split-volume options, диалог перед импортом, сохранение оригинала и проверяемый derived cache. Подключён фактический импорт и recent-list preference. | После исправления размещения историческая native DA652B… успешно нарезала STEP при 0.003 mm / 0.5 rad: 2 371 725 bytes. Повторный UI-сценарий на финальном APK 9FD3374C… ещё ожидается. Сравнение двух допусков и desktop quality parity не доказаны. |
| Auto flushing / материалы | Реальный upstream калькулятор с оригинальными datasets, native snapshot слотов, матрицы и совместимости. Редактор физических цветов в настроенной 3MF запускает пересчёт только после изменённого и подтверждённого цвета, когда preference включён; ручные посторонние ячейки сохраняются. | Виртуальные mixed slots доступны только для чтения; это не полный редактор назначения материалов и не AMS/device sync. Исходный архив не переписывается. Особенность support-ветки закреплённого desktop-кода и её граница описаны в [native-preferences-controls.md](../engine/native-preferences-controls.md); G-code parity не доказан. |
| Clone / Arrange | Реальные native instances, новые ID клонов, upstream Arrange и проверка стола/exclusions/высоты. Model Details создаёт 1–3 копии одного plain STL/OBJ; замороженное состояние проверяется до публикации. Исторический APK 1E2F… прошёл OBJ clone + Arrange с двумя добавленными копиями: 36 preview triangles, 42 × 42 × 20 mm. | Повторный UI-сценарий финального APK ещё ожидается. Configured-3MF clone/export, belt/IMEX и полный registry пластин не реализованы. Подмена Arrange центрированием не используется. |
| Mixed temperatures / logging | Предупреждение и opt-in управляют настоящим `Print::set_check_multi_filaments_compatibility`; флаг фиксируется для нарезки. Native Boost severity setter/readback подключён отдельно от Java filtering. | Несвязанные temperature/config errors остаются ошибками. Prerelease-ядро может принудительно оставлять более подробный native log level. Реальная печать и cloud/device проверка не выполнены. |

По текущей матрице **8 пунктов полностью заблокированы: #23, #28, #29, #30, #31, #36, #37, #39**. Это region, обновление встроенных профилей, filament device sync, включение/версия/перезагрузка Bambu plug-in, AMS bypass и abnormal-storage gate. Изменение native Preferences не поставляет совместимый Android Bambu SDK/provider. Остальные 31 имеют потребитель или платформенную адаптацию с ограничениями; это не 31 полностью эквивалентная desktop-функция. Актуальные границы каждого пункта — в [матрице](PREFERENCES-39-STATUS.md).

Проверки текущего кода разделены по уровню доказательств:

- На host прошли **52** проверки `ProjectSettingsSnapshot` и **45** проверок `NativeConfigValues`, включая project consumer, фильтрацию scope, stale rejection, очистку временной копии и отсутствие INI fallback при ошибке проекта. JNI в этих Java suites — явная заглушка; числа не являются native/device-проверками.
- На установленном **APK 9FD3374C… / native 8C146C74… через computer use прошли 52 реальные native assertions: 32 control + 20 project-fixture** с реальными `store_bbs_3mf`/load/config scopes. Это исполнение JNI на устройстве, отдельно от Java host stubs и compile/link. Оно не исчерпывает все комбинации nullable/type и произвольные desktop projects.
- Исторический первый двухматериальный PLA fixture остановился с реальным `Prime tower is partially outside printable area`. Fixture v2 получил явные test bed/tower coordinates; на **native DA652B… полный режим 1 с выбранным принтером успешно создал 745 554 bytes G-code**. На той же исторической native-сборке STEP создал 2 371 725 bytes при 0.003 mm / 0.5 rad; исторический APK 1E2F… прошёл OBJ clone/Arrange, как указано выше. Эти наблюдения не переобозначены как UI-результаты финального APK 9FD3374C…: повторные сценарии пока ожидаются. 3MF fixture содержит частичные тестовые настройки поверх selected native base, а не проверенный production printer profile; прежний guard не отключался ради теста.

Реальных принтеров и облачных аккаунтов для этой проверки нет. Полный desktop round-trip, совпадение effective config/G-code, физическая печать, положительные сценарии всех новых native-потребителей и итоговая визуальная/доступностная приёмка остаются открытыми. **Фаза 2 не завершена.**
