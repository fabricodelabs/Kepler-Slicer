# Kepler Slicer — Политика конфиденциальности / Privacy Policy

Дата вступления в силу / Effective date: 2026-11-04

## Русский

Kepler Slicer — приложение для подготовки 3D-моделей к печати. Нарезка выполняется на устройстве.

**Что приложение не делает.** В приложении нет рекламы, аналитики, трекеров и сбора сведений об использовании.
Разработчик не получает ваши модели, профили или учётные данные. Приложение ничего не продаёт и не передаёт третьим лицам.

**Данные на устройстве.** Модели, G-code, профили принтеров и материалов, настройки и резервные копии хранятся в
закрытой папке приложения. Токены и пароли подключений шифруются ключом Android Keystore. Удаление приложения
удаляет эти данные. Резервное копирование Android для приложения отключено.

**Сеть используется только по вашему действию:**
- **Принтеры в локальной сети** (OctoPrint, Moonraker, PrusaLink и другие): приложение отправляет выбранный файл
  и запрашивает состояние только на адрес, который вы указали.
- **Облачные сервисы печати** (SimplyPrint, 3DPrinterOS, Obico, PrusaConnect и другие): вход выполняется на сайте
  сервиса, файл отправляется только в выбранный вами аккаунт. Обработка данных регулируется политикой этого сервиса.
- **Облако профилей upstream** (auth.orcaslicer.com, api.orcaslicer.com): по вашему входу синхронизируются ваши
  пользовательские профили.
- **Проверка обновлений** (api.github.com) и **импорт по ссылке**: запрос уходит только на нужный адрес.

Режим «Без сети» в настройках блокирует новые сетевые операции.

**Разрешения:** доступ к интернету и к состоянию сети и Wi-Fi нужен для работы с принтерами и сервисами из списка выше.
Файлы выбираются через системное окно выбора; доступа ко всему хранилищу приложение не запрашивает.

**Дети.** Приложение не предназначено для сбора данных детей и не собирает их.

**Изменения.** Новая версия политики публикуется на этой странице с новой датой.

**Контакты:** https://github.com/nitronov/Kepler-Slicer/issues

## English

Kepler Slicer prepares 3D models for printing. Slicing runs on the device.

**What the app does not do.** No ads, analytics, trackers or usage collection. The developer does not receive your
models, profiles or credentials, and nothing is sold or shared with third parties.

**Data on the device.** Models, G-code, printer and material profiles, settings and backups are stored in the app's
private storage. Connection tokens and passwords are encrypted with an Android Keystore key. Uninstalling the app
removes this data. Android backup is disabled for the app.

**Network access happens only when you act:**
- **Printers on your local network** (OctoPrint, Moonraker, PrusaLink and others): the selected file and status
  requests go only to the address you entered.
- **Cloud print services** (SimplyPrint, 3DPrinterOS, Obico, PrusaConnect and others): you sign in on the service's
  site; files go only to the account you chose. That service's own privacy policy applies.
- **Upstream profile cloud** (auth.orcaslicer.com, api.orcaslicer.com): after you sign in, your user profiles sync.
- **Update check** (api.github.com) and **import from URL**: a request goes only to that address.

The Offline mode setting blocks new network operations.

**Permissions:** internet, network state and Wi-Fi state are used for the printers and services above. Files are
chosen with the system picker; the app does not request access to all storage.

**Children.** The app is not directed at children and does not collect their data.

**Changes.** A new version of this policy is published on this page with a new date.

**Contact:** https://github.com/nitronov/Kepler-Slicer/issues
