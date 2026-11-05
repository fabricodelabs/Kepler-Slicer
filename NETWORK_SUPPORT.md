# Kepler Slicer — рабочая матрица сетевого переноса

Матрица описывает исходный код и локальные проверки. Через Android UI проверен сценарий OctoPrint на локальном имитаторе; подключение к реальным принтерам, облачным аккаунтам и серверам не проверено. Обычная передача G-code не отправляет команду запуска; отдельные действия OctoPrint, Moonraker и UltiMaker запускают печать после предупреждения. Поведение удалённого сервера не гарантировано.

| Тип из upstream | В коде Android | Проверка и существенные границы |
|---|---|---|
| OctoPrint | Проверка API, статус, multipart-загрузка. | На локальном имитаторе через Android UI выполнена загрузка с `print=false`, затем отдельная команда `select` с `print=true`; в режиме без сети повторного запроса не было. Реальный принтер не проверен. |
| Moonraker | Проверка API, состояние, загрузка в хранилище. | Loopback: версия, состояние, `print=false` и отдельная команда старта; отдельный запуск переданного файла подключён к UI; UI-проверка ожидается. |
| PrusaLink | Проверка API, состояние, PUT/POST-загрузка, API key и Digest. | Loopback: PUT без запуска, Digest challenge/qop. Команда старта не подключена. |
| PrusaConnect | Проверка API, загрузка. | Loopback: multipart без `to_print`/`to_queue`, статус 202. Статус принтера и старт/очередь в UI не реализованы. |
| Duet | Подключение к RRF/DSF и загрузка. | Loopback RRF и DSF, включая неверный пароль; старт и статус в общем UI не реализованы. |
| UltiMaker | Проверка Digest и код сопряжения. | Loopback проверки и сопряжения. Простая загрузка блокируется, поскольку upstream-метод запускает печать; сопряжение и отдельное действие «передать и начать печать» подключены к UI, новая UI-интеграция пока не проверена. |
| FlashAir | Проверка и загрузка на карту. | Loopback; статуса и команды старта нет. |
| AstroBox | Проверка API и multipart-загрузка. | Loopback; общий статус сообщает unknown. |
| Repetier Server | Проверка и загрузка на выбранный принтер/группу. | Loopback; общий статус сообщает unknown. |
| MKS | Проверка TCP и HTTP-загрузка. | Loopback TCP + HTTP; общий статус сообщает unknown. |
| ESP3D | Проверка и multipart-загрузка. | Loopback; имя передаётся в формате 8.3, общий статус сообщает unknown. |
| Creality Print | Проверка и multipart-загрузка с bearer. | Loopback; общий статус сообщает unknown. |
| Flashforge | Варианты HTTP и TCP, загрузка без запрошенного запуска. | Loopback для обоих вариантов; общий статус сообщает unknown. |
| ElegooLink | Octo-совместимый fallback, CC1/CC2 и загрузка чанками. | Loopback fallback, CC1 и CC2; общий статус сообщает unknown. |
| Prusa SL1 / SL1S | Проверка версии и транспорт SLA-файлов `.sl1` / `.sl1s`; текущий UI отвергает FFF G-code. | Loopback проверки версии и отказа неподдерживаемого старта; загрузка SL1 отдельно не проверена, статус unknown, SLA-нарезка не реализована. |
| Obico | Bearer, проверка, получение списка принтеров, multipart-загрузка с `print=false`. | Loopback Bearer + передача, нужен `printer_id`; отдельное OAuth-подключение Obico пока не выполнено. |
| SimplyPrint | Bearer, TokenInfo, TempUpload и передача чанками. Возвращается ссылка для отдельного завершения импорта пользователем. | TempUpload и чанки проверены на локальном mock; PKCE, token exchange и refresh — на loopback. Вход подключён к UI; реальный сервис, аккаунт и новая UI-интеграция ещё не проверялись. |
| 3DPrinterOS | Проверка session, проекты и типы, multipart-загрузка и необязательное обновление типа. | Loopback session + project + file ID, получение token и опрос login. Вход подключён к UI; реальный сервис, аккаунт и новая UI-интеграция ещё не проверялись. |

**Upstream Cloud:** реализован экспериментальный вход внешним браузером с PKCE и локальным callback, шифрованное хранение сессии, обновление токена, pull профилей и удалений, локальное зеркало с атомарным курсором, явное редактирование и отправка выбранного JSON, выявление конфликтов и экспорт через SAF. Проверки выполнялись с loopback и JVM-подменами, а не с реальными аккаунтами. Серверная политика авторизации и все провайдеры не подтверждены. Импортированные профили пока не применяются нативным движком к нарезке; это не синхронизация всех возможностей desktop.

**Bambu (LAN, 2026-11-05):** status and AMS over the printer MQTT (port 8883, user `bblp`, access code; serial in the User field), private LAN addresses only. Upload over implicit FTPS (port 990, TLS session reuse on the data channel); starting the uploaded file (`gcode_file`), pause/resume/stop, speed, chamber light, aux fan and axis moves are separate actions confirmed by the user. Tested against loopback simulators (`tools/test_bambu_lan.ps1`), not a real printer. Bambu cloud is not implemented.

**Job control (2026-11-05):** OctoPrint (`api/job`, `api/printer/printhead`, `api/printer/command`), Moonraker (`printer/print/*`, `printer/gcode/script`) and PrusaLink (`api/v1/job/{id}`) pause/resume/cancel and speed (M220) from the Device screen; each command is confirmed and sent once, never retried. Axis moves only while idle.

**More hosts (2026-11-05):** live status and pause/resume/stop for Duet (RRF and DSF; speed and axis moves by G-code, start of an uploaded file with M32), Repetier Server, Creality OS (K1/K2 WebSocket 9999, also the light) and Elegoo (Neptune via the OctoPrint API; Centauri Carbon via SDCP, also the light). Centauri Carbon 2 control is not offered. Loopback simulators only (`tools/test_printer_control.ps1`).

**Bambu (cloud, 2026-11-05):** the user signs in with their Bambu Lab account (password or e-mail code; region from Preferences); the app lists the account printers and reads status/AMS and sends commands over the account MQTT broker with certificate checks. Files are sent over LAN only. Not verified against the real service.

**Bambu (cloud):** официальный README Bambu Studio указывает на сетевой плагин с non-free библиотеками. Официальный Android SDK для этого плагина в доступной документации не обнаружен; это не доказывает его отсутствия. Bambu LAN/cloud, аккаунт, камера и отправка в текущем Android-порту не заявлены работающими.

Источники: [исходный upstream на использованном коммите](https://github.com/OrcaSlicer/OrcaSlicer/tree/6d2869c430284714f0a22570c38d3b359965a802), [Bambu Studio README](https://github.com/bambulab/BambuStudio/blob/master/README.md), [руководство upstream Cloud](https://cloud.orcaslicer.com/wiki/). Практическая доступность сервисов, пользовательские данные, совместимость устройств и пригодность G-code к печати требуют отдельной проверки.


