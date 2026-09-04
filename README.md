# MiTV Accessibility Restorer

Android TV-приложение для автоматического восстановления окружения Xiaomi Mi TV
S75 после холодной загрузки и горячего пробуждения (STR).

- package: `com.mitv.accessibilityrestorer`
- versionName: `4.0.0`
- versionCode: `10`
- minSdk: `21`
- targetSdk: `28`
- compileSdk: `35`
- целевое устройство: Xiaomi Mi TV S75 / MiTV-MFTR0
- проверенная базовая прошивка: MiTV OS 2.8.1712 / Android 11

## Назначение

При cold boot Restorer восстанавливает Accessibility Button Mapper и Projectivy,
после чего открывает Projectivy. При STR приложение дополнительно восстанавливает
TorrServe Accessibility и параллельно помогает запустить v2RayTun VPN.

Обычный визуальный путь STR:

```text
Wake -> black RecoveryCover -> Projectivy
```

Restorer не меняет Android default HOME и не отключает `com.mitv.tvhome`.

## Первичная настройка

Стандартная установка выполняется файлом `INSTALL.cmd` из папки с APK. Скрипт:

1. Проверяет наличие `adb` и ровно одного авторизованного устройства.
2. Выполняет `adb install -r`.
3. Выдаёт и проверяет `WRITE_SECURE_SETTINGS`.
4. Открывает `ControlActivity` с экраном первоначальной настройки.

Экран показывает основные компоненты Button Mapper/Projectivy, необязательные
интеграции TorrServe/v2RayTun и реальное состояние `WRITE_SECURE_SETTINGS`.
Кнопка «Завершить настройку» записывает `setup_completed=true` только при наличии
разрешения и двух основных AccessibilityService. TorrServe и v2RayTun не являются
обязательными для завершения.

APK не пытается сам выполнять `pm grant`, root, Shizuku или локальный ADB. Если
разрешение отсутствует, UI предлагает повторную проверку и показывает команду для
ручной выдачи.

## Установка

1. Включите ADB debugging на ТВ.
2. Подключите ТВ к ПК и подтвердите отладку.
3. Поместите `INSTALL.cmd` рядом с `MiTVAccessibilityRestorer-4.0.0.apk`.
4. Запустите `INSTALL.cmd`.
5. Завершите настройку на открывшемся экране ТВ.

Расширенная ручная установка:

```bash
adb install -r MiTVAccessibilityRestorer-4.0.0.apk
adb shell pm grant com.mitv.accessibilityrestorer android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.mitv.accessibilityrestorer/.ControlActivity
```

Стандартный installer не настраивает `Settings.System["start_3rd_app"]`. Текущая
STR-архитектура рассчитана на работу без этого шага установки; физическая
clean-install проверка без vendor setting пока ожидается. Внутренний advisory
readback `ALREADY_ARMED`/`MISSING_UNARMED` сохранён и не считается ошибкой.

## Cold boot

Cold boot использует Direct Boot, `LOCKED_BOOT_COMPLETED` и резервный
`BOOT_COMPLETED`, защиту по `BOOT_COUNT`, ранний запуск после `2500 ms` и
одноразовый fallback alarm на `10000 ms`.

В cold-boot ветку не входят TorrServe, v2RayTun VPN assist, first-run setup, STR
cover или многофазный STR reset.

## STR

После определения `isInteractive=true` приложение ждёт `500 ms`, невидимо
снимает stopped-state с Button Mapper и Projectivy, затем выполняет:

```text
ALL INITIAL  2500 ms  (Button Mapper + Projectivy)
BASE         1500 ms  (без управляемых targets)
MAPPER       2500 ms  (Button Mapper)
ALL FINAL    1500 ms  (Button Mapper + Projectivy + TorrServe)
final Projectivy launch
```

Чужие Accessibility entries сохраняются в исходном raw-виде и порядке. Если
TorrServe или его `GlobalTorrService` отсутствует, target не добавляется и
основное восстановление Button Mapper/Projectivy не считается ошибочным.

При измеримой ошибке допускается одна повторная попытка с паузой `1000 ms` и
профилем `4000 / 3000 / 3000 / 3000 ms`. Всего не более двух attempts.

## Button Mapper и Projectivy

Для invisible unstop используются explicit broadcast с
`FLAG_INCLUDE_STOPPED_PACKAGES`, проверкой `ApplicationInfo.FLAG_STOPPED`, poll
`200 ms` и timeout `3000 ms`.

- Button Mapper: `flar2.homebutton/a.s`, затем `flar2.homebutton/a.r`.
- Projectivy: `com.spocky.projengmenu/.services.StartUpBootReceiver`.

Activity соответствующего приложения используется только как emergency fallback.

## TorrServe

STR проверяет пакет `ru.yourok.torrserve` и AccessibilityService:

```text
ru.yourok.torrserve/ru.yourok.torrserve.server.local.services.GlobalTorrService
```

Target появляется только в `ALL FINAL`. Restorer не открывает TorrServe Activity,
не вызывает закрытый `BCReceiver` и не выполняет отдельный unstop.

## v2RayTun VPN assist

VPN helper работает только в STR и параллельно Accessibility recovery. Для
`ConnectivityManager` всегда используется application context Restorer.

При `SecurityException` определение VPN повторяется до трёх раз с паузой `100 ms`.
Если после retries состояние остаётся `UNKNOWN`, widget trigger разрешён только
когда повторный readback подтверждает `FLAG_STOPPED=true`. Для `stopped=false`
blind toggle запрещён.

Trigger не изменён и отправляется не более одного раза за STR session:

```text
action:    com.v2raytun.android.action.widget.click
component: com.v2raytun.android/.receiver.WidgetProvider1x1
flag:      FLAG_INCLUDE_STOPPED_PACKAGES
```

Для работающего процесса без VPN сохраняется grace `2000 ms`. После trigger
выполняется advisory poll `250 ms` с timeout `5000 ms`; helper не задерживает
final Projectivy.

## Диагностика

```bash
adb shell settings get secure enabled_accessibility_services
adb shell settings get secure accessibility_enabled
adb shell dumpsys accessibility
adb shell dumpsys connectivity
adb shell "logcat -d | grep -iE 'MiTVRestorer|accessibilityrestorer|AndroidRuntime'"
```

`execution=COMPLETED` означает выполнение доступных приложению операций, но не
доказывает реальный binding. Bound/Binding/Crashed проверяются через `dumpsys`.

## Разрешения

Manifest содержит только:

- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.WRITE_SECURE_SETTINGS`
- `android.permission.ACCESS_NETWORK_STATE`

`INTERNET`, постоянный Service, WorkManager, JobScheduler и repeating alarm не
используются.

## Сборка

Используется официальный Android toolchain: `aapt2`, `javac`, `D8`, `zipalign`,
`apksigner`. Signing key в исходники не входит.

```powershell
$env:MITV_RESTORER_KEYSTORE_PASSWORD = '<password>'
$env:MITV_RESTORER_KEY_ALIAS = '<alias>'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 `
  -JavaHome '<jdk17>' `
  -AndroidSdkRoot '<android-sdk>' `
  -Keystore '<existing-restorer-keystore>'
```

## Ограничения

- Новый APK `versionCode 10` ещё не проверен на физическом телевизоре.
- APK не имеет аналога `dumpsys accessibility` и не подтверждает Bound/Crashed.
- Любой уже активный VPN transport предотвращает v2RayTun toggle.
- Invisible receiver и service components зависят от версий целевых приложений.
