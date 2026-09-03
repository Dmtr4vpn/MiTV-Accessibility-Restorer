# MiTV Accessibility Restorer

Android TV-приложение для автоматического восстановления повседневного
окружения Xiaomi Mi TV S75 после cold boot и горячего пробуждения (STR).

- package: `com.mitv.accessibilityrestorer`
- versionName: `4.0.0`
- versionCode: `9`
- minSdk: `21`
- targetSdk: `28`
- compileSdk: `35`
- целевое устройство: Xiaomi Mi TV S75 / MiTV-MFTR0
- прошивка: MiTV OS 2.8.1712 / Android 11

## Назначение

При cold boot Restorer восстанавливает Accessibility Button Mapper и Projectivy,
после чего открывает Projectivy. При STR приложение дополнительно восстанавливает
TorrServe Accessibility и параллельно помогает запустить v2RayTun VPN.

Обычный визуальный путь STR:

```text
Wake -> black RecoveryCover -> Projectivy
```

Restorer не меняет Android default HOME и не отключает `com.mitv.tvhome`.

## Cold boot

Cold boot использует Direct Boot, `LOCKED_BOOT_COMPLETED` и резервный
`BOOT_COMPLETED`, защиту по `BOOT_COUNT`, ранний запуск после `2500 ms` и
одноразовый fallback alarm на `10000 ms`.

В cold-boot ветку не входят TorrServe, v2RayTun VPN assist, STR cover или
многофазный STR reset.

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

Activity соответствующего приложения используется только как emergency
fallback. До STR phases в нормальном пути Activity не открываются.

## TorrServe

STR проверяет пакет `ru.yourok.torrserve` и AccessibilityService:

```text
ru.yourok.torrserve/ru.yourok.torrserve.server.local.services.GlobalTorrService
```

Target появляется только в `ALL FINAL`. Restorer не открывает TorrServe Activity
и не вызывает закрытый `BCReceiver`. Запуск процесса и native `torrserver`
ожидается как системный эффект включения AccessibilityService.

## v2RayTun VPN assist

VPN helper работает только в STR и параллельно Accessibility recovery. Он
проверяет активный `TRANSPORT_VPN` и никогда не отправляет toggle, если VPN уже
активен.

Если `com.v2raytun.android` stopped, отправляется один explicit broadcast:

```text
action:    com.v2raytun.android.action.widget.click
component: com.v2raytun.android/.receiver.WidgetProvider1x1
flag:      FLAG_INCLUDE_STOPPED_PACKAGES
```

Для уже работающего процесса без VPN используется grace period `2000 ms`, затем
повторно проверяются VPN и `FLAG_STOPPED`. Непосредственно перед единственным
broadcast VPN проверяется ещё раз. После trigger выполняется advisory poll
`250 ms` с timeout `5000 ms`. VPN helper не задерживает final Projectivy.

## Установка

```bash
adb install -r MiTVAccessibilityRestorer-4.0.0.apk
adb shell pm grant com.mitv.accessibilityrestorer android.permission.WRITE_SECURE_SETTINGS
adb shell settings put system start_3rd_app com.mitv.accessibilityrestorer
adb shell settings get system start_3rd_app
adb shell am start -n com.mitv.accessibilityrestorer/.MainActivity
```

Ручная диагностика:

```bash
adb shell am start -n com.mitv.accessibilityrestorer/.ControlActivity
```

## Диагностика

```bash
adb shell settings get secure enabled_accessibility_services
adb shell settings get secure accessibility_enabled
adb shell dumpsys accessibility
adb shell dumpsys connectivity
adb shell "logcat -d | grep -iE 'MiTVRestorer|accessibilityrestorer|AndroidRuntime'"
```

`execution=COMPLETED` означает выполнение доступных приложению операций, но не
доказывает реальный binding. Bound/Binding/Crashed проверяются только через
`adb shell dumpsys accessibility`.

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

## Известные ограничения

- APK не имеет аналога `dumpsys accessibility` и не подтверждает Bound/Crashed.
- Любой уже активный VPN transport предотвращает v2RayTun toggle.
- Invisible receiver и service components зависят от версий целевых приложений.
- Объединённый APK `4.0.0` требует отдельной проверки на физическом телевизоре.
