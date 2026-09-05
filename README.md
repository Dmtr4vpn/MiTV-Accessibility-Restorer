# FOX MiTV Restorer

Android TV-приложение для восстановления рабочего окружения Xiaomi Mi TV S75
после холодной загрузки и пробуждения из сна (STR).

- package: `com.mitv.accessibilityrestorer`
- versionName: `4.0.0`
- versionCode: `16`
- minSdk: `21`
- targetSdk: `28`
- compileSdk: `35`
- проверенная базовая среда: Xiaomi Mi TV S75, MiTV OS 2.8.1712, Android 11

## Назначение

Основными компонентами считаются Button Mapper и Projectivy Launcher. TorrServe
и v2RayTun являются необязательными интеграциями: их отсутствие или ошибка не
меняют успешность core-восстановления.

Restorer не меняет Android default HOME, не отключает `com.mitv.tvhome`, не
использует постоянный Android Service и не выполняет периодический мониторинг.

## Установка

Рекомендуемый комплект имеет структуру:

```text
ADBAppControl-1.8.6\
  INSTALL.cmd
  adb\adb.exe
  install\*.apk
```

`INSTALL.cmd` подключает телевизор, последовательно обрабатывает все APK и
сравнивает именно `versionCode`:

```text
package отсутствует                 -> adb install
installedVersionCode < apkVersion   -> adb install -r
installedVersionCode = apkVersion   -> установка пропущена
installedVersionCode > apkVersion   -> downgrade пропущен
```

После любого результата для Restorer скрипт проверяет/выдаёт
`WRITE_SECURE_SETTINGS`, проверяет наличие Button Mapper и Projectivy,
без очистки данных перезапускает Projectivy, запускает проверенный recovery,
до 30 секунд ждёт обе Enabled/Bound-службы и Projectivy в foreground два poll
подряд, проверяет физический маршрут HOME в Projectivy и только затем открывает
`ControlActivity`. Автоматический uninstall
и `pm clear` не выполняются. Подробности записываются с нуля в один
`INSTALL-LOG.txt`.

Успешность post-install bootstrap означает одновременно: оба целевых компонента
есть в `enabled_accessibility_services`, Button Mapper и Projectivy находятся в
секции `Bound services`, а foreground уже равен
`com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity`. Все пять
условий должны сохраниться два poll подряд с интервалом `1000 ms`. Затем скрипт
ждёт `1500 ms`, отправляет `KEYCODE_HOME`, ждёт ещё `1500 ms` и требует тот же
foreground. Timeout ожидания core составляет 30 секунд. При ошибке UI
открывается для диагностики, но установщик возвращает ненулевой код.

Ручная установка:

```text
adb install -r FOX-MiTV-Restorer-4.0.0.apk
adb shell pm grant com.mitv.accessibilityrestorer android.permission.WRITE_SECURE_SETTINGS
adb shell am force-stop com.spocky.projengmenu
adb shell am start -W -n com.mitv.accessibilityrestorer/.MainActivity
adb shell input keyevent 3
adb shell dumpsys window
adb shell am start -n com.mitv.accessibilityrestorer/.ControlActivity
```

Стандартная установка и APK не записывают `Settings.System.start_3rd_app`.
Пустое значение допустимо и логируется только как advisory `MISSING_UNARMED`.

## Cold boot

Core-путь сохранён:

```text
MAIN/LAUNCHER -> BOOT_PENDING -> EARLY_BOOT (2500 ms)
-> Button Mapper + Projectivy -> Projectivy final launch -> core SUCCESS
```

Direct Boot использует `LOCKED_BOOT_COMPLETED`, `BOOT_COUNT`, Device Protected
Storage и резервный одноразовый alarm на `10000 ms`. `BOOT_COMPLETED` остаётся
вторым системным событием.

Необязательное восстановление запускается отдельным асинхронным worker только
когда одновременно выполнены оба условия:

```text
coreBootSuccess=true
android.intent.action.BOOT_COMPLETED получен
```

Порядок этих событий не важен. Маркер `lastPostBootOptionalBootCount` записывается
до запуска worker, поэтому на один `BOOT_COUNT` возможно не более одного запуска.
Worker не задерживает Projectivy и не меняет уже записанный core `SESSION FINISH`.

В `POST_BOOT_OPTIONAL_RECOVERY`:

- TorrServe: при наличии пакета и `GlobalTorrService` компонент аккуратно
  добавляется к текущему raw-списку `enabled_accessibility_services`;
- v2RayTun: при наличии пакета и `WidgetProvider1x1` переиспользуется
  существующий `V2RayVpnAssist`.

Для TorrServe не выполняются full reset, Activity, custom broadcast или unstop.
Существующие Xiaomi и сторонние Accessibility entries, их написание и порядок
сохраняются. Уже существующий TorrServe token не дублируется.

## STR

Проверенная последовательность и тайминги не изменены:

```text
interactive settle  500 ms
ALL_INITIAL         2500 ms
BASE                1500 ms
MAPPER              2500 ms
ALL_FINAL           1500 ms
Projectivy final launch
```

`RecoveryCover` для STR/fast reboot теперь однотонный `#474747`, чтобы начало
recovery было заметно до запуска Projectivy. Чёрная cold-boot trampoline theme
не изменена. Сохранены invisible unstop Button Mapper/Projectivy,
TorrServe только в `ALL_FINAL`, параллельный `V2RayVpnAssist` и одна conservative
retry при измеримой core-ошибке. Если TorrServe отсутствует, `ALL_FINAL` содержит
только core targets, а STR продолжается штатно.

## v2RayTun

Проверяются пакет `com.v2raytun.android` и receiver:

```text
com.v2raytun.android/.receiver.WidgetProvider1x1
```

Сохраняется существующий trigger:

```text
action: com.v2raytun.android.action.widget.click
flag:   Intent.FLAG_INCLUDE_STOPPED_PACKAGES
```

VPN определяется через `ConnectivityManager` application context Restorer. При
`SecurityException` выполняются три попытки с паузой `100 ms`. Сохраняются grace
`2000 ms`, poll `250 ms`, timeout `5000 ms` и максимум один widget trigger.
`stopped=false + VPN=UNKNOWN` никогда не приводит к blind toggle.

## Пользовательский экран

`MainActivity` остаётся единственным `MAIN/LAUNCHER`, который Xiaomi использует
как recovery/bootstrap entry при cold boot и STR. Она всегда выполняет recovery
semantics, имеет непрозрачную чёрную trampoline theme и никогда не маршрутизирует
пользовательский запуск в UI по runtime-эвристике.

Пользовательские entry points принадлежат `ControlActivity`:

```text
MAIN + LEANBACK_LAUNCHER -> ControlActivity
```

Обычный `LAUNCHER` и `INFO` у `ControlActivity` отсутствуют. Поэтому Xiaomi
automatic package relaunch остаётся на чёрной `MainActivity`, а Android TV/
Projectivy-карточка открывает `ControlActivity` через `LEANBACK_LAUNCHER`.

`ControlActivity` показывает core-компоненты, необязательные TorrServe/v2RayTun,
состояние `WRITE_SECURE_SETTINGS`, диагностику и кнопку «Восстановить сейчас».
Отсутствие optional packages не блокирует завершение первичной настройки.

Системное имя приложения: `FOX MiTV Restorer`. Launcher icon технически уменьшена
из утверждённого `3.1.png`; adaptive-icon override отсутствует. Android TV banner
использует существующий FOX artwork, технически уменьшенный до `640x360` PNG.
В APK существует ровно один banner resource, общий для application и
`ControlActivity`. Аудит установленного Projectivy 4.71 показал: пользовательское
artwork карточки имеет приоритет; иначе при TV aspect ratio `16:9` используется
`ActivityInfo.banner`, а при `1:1` — launcher icon. Размер карточки XS сам по
себе другой APK banner не выбирает. Версия в UI и `SESSION START`
читается из установленного `PackageInfo`, а не из hardcoded константы.

## Разрешения

Manifest содержит ровно три разрешения:

- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.WRITE_SECURE_SETTINGS`
- `android.permission.ACCESS_NETWORK_STATE`

Нет `INTERNET`, `WRITE_SETTINGS`, `WAKE_LOCK`, Android Service/ForegroundService,
WorkManager, JobScheduler или repeating alarm.

## Сборка

Используется официальный toolchain: `aapt2`, `javac`, D8, `zipalign`,
`apksigner`. Signing key в Source ZIP и deliverables не включён.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 `
  -JavaHome '<jdk17>' `
  -AndroidSdkRoot '<android-sdk>' `
  -Keystore '<existing-restorer-keystore>' `
  -KeystorePassword '<password>' `
  -KeyAlias '<alias>'
```

## Ограничения проверки

Recovery-код `versionCode 15` физически проверен пользователем и в v16 не
изменён. APK `versionCode 16` должен пройти на телевизоре post-install HOME,
compact UI, banner XS, один STR и один cold-boot regression test. Локальная сборка не может
заменить фактическую проверку ТВ через `dumpsys`.
