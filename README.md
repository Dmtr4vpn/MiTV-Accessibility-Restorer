# FOX MiTV Restorer

Android TV-приложение для восстановления рабочего окружения Xiaomi Mi TV S75
после холодной загрузки и пробуждения из сна (STR).

- package: `foxmitv.restorer`
- прежний package: `com.mitv.accessibilityrestorer`
- versionName: `4.0.0`
- versionCode: `17`
- minSdk: `21`
- targetSdk: `28`
- compileSdk: `35`
- проверенная среда: Xiaomi Mi TV S75, MiTV OS 2.8.1712, Android 11

## Назначение

Основными компонентами считаются Button Mapper и Projectivy Launcher. TorrServe
и v2RayTun являются необязательными: их отсутствие или ошибка не меняют
успешность core-восстановления.

Restorer не меняет Android default HOME, не отключает `com.mitv.tvhome`, не
использует постоянный Android Service и не выполняет периодический мониторинг.

## Новая application identity

Начиная с versionCode 17 приложение использует package `foxmitv.restorer`.
Старый и новый Restorer нельзя оставлять одновременно: оба получают boot events
и могут запустить recovery. Перед установкой нового APK удаляется только старый:

```text
adb shell pm path com.mitv.accessibilityrestorer
adb uninstall com.mitv.accessibilityrestorer
```

Это не удаляет данные Projectivy, Button Mapper, TorrServe или v2RayTun. Private
data старого Restorer не переносится; новый package начинает с чистого состояния.

## Установка

Рекомендуемая структура:

```text
ADBAppControl-1.8.6\
  INSTALL.cmd
  adb\adb.exe
  install\FOX-MiTV-Restorer-4.0.0.apk
  install\другие-приложения.apk
```

`INSTALL.cmd` сравнивает `versionCode` каждого APK. При обработке
`foxmitv.restorer` он без запроса удаляет только legacy package, если тот найден.
Затем выдаёт и проверяет `WRITE_SECURE_SETTINGS`, перезапускает Projectivy без
очистки данных, явно запускает `MainActivity` и ждёт READY.

READY требует пять условий два polling cycle подряд с интервалом `1000 ms`:

1. Button Mapper находится в `enabled_accessibility_services`.
2. Projectivy находится в `enabled_accessibility_services`.
3. Button Mapper находится в `Bound services`.
4. Projectivy находится в `Bound services`.
5. Foreground равен Projectivy home activity.

Timeout равен 30 секундам. Синтетический HOME test отсутствует. После READY
установщик открывает `foxmitv.restorer/.ControlActivity`; физическую HOME
пользователь проверяет кнопкой пульта.

Ручная миграция и установка:

```text
adb shell pm path com.mitv.accessibilityrestorer
adb uninstall com.mitv.accessibilityrestorer
adb install FOX-MiTV-Restorer-4.0.0.apk
adb shell pm grant foxmitv.restorer android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n foxmitv.restorer/.ControlActivity
```

## Пользовательский экран

`ControlActivity` автоматически обновляет статус в `onCreate()`, `onResume()` и
после callback ручного восстановления. На основном экране видны:

- `WRITE_SECURE_SETTINGS`;
- Button Mapper и Projectivy как core;
- TorrServe и v2RayTun/VPN как optional;
- общий результат и последнее восстановление;
- постоянная подсказка `Для выхода нажмите HOME.`.

Есть ровно одна action-кнопка: `Восстановить спецвозможности`. Она использует
существующий `RecoveryEngine.startStrRecovery(..., manual=true, ...)`. После
callback ControlActivity снова выводится на передний план и обновляет статус.
Кнопки проверки, завершения настройки, диагностики и старого ручного
восстановления удалены. `setup_completed` остаётся внутренним совместимым
маркером и не связан с пользовательским действием.

## Routing

```text
MAIN + LAUNCHER          -> foxmitv.restorer/.MainActivity
MAIN + LEANBACK_LAUNCHER -> foxmitv.restorer/.ControlActivity
MAIN + INFO              -> NO MATCH
```

`MainActivity` остаётся recovery-only bootstrap и не запускает ControlActivity.
Пользовательский UI открывается через LEANBACK, `INSTALL.cmd` или явную ADB-команду.

## Recovery freeze

Функциональность recovery идентична базе
`536bc491676cd7bf913052cf5e0272eed3577325`. В ключевых recovery-файлах изменена
только Java package declaration. Сохранены:

- Direct Boot, `BOOT_COUNT`, EARLY_BOOT settle `2500 ms` и fallback `10000 ms`;
- STR: `500 + 2500 + 1500 + 2500 + 1500 ms`, последовательность
  `ALL_INITIAL -> BASE -> MAPPER -> ALL_FINAL`;
- RecoveryCover `#474747` для STR и чёрная cold-boot trampoline;
- Button Mapper, Projectivy final launch и optional TorrServe;
- V2RayVpnAssist: grace `2000 ms`, poll `250 ms`, timeout `5000 ms`;
- `start_3rd_app` только read-only/advisory.

Внешние identities не изменены:

```text
flar2.homebutton
com.spocky.projengmenu
ru.yourok.torrserve
com.v2raytun.android
```

## Разрешения

Manifest содержит:

- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.WRITE_SECURE_SETTINGS`
- `android.permission.ACCESS_NETWORK_STATE`

Нет `INTERNET`, `WRITE_SETTINGS`, `WAKE_LOCK`, Android Service/ForegroundService,
WorkManager, JobScheduler или repeating alarm.

## Сборка

Используется официальный toolchain: `aapt2`, `javac`, D8, `zipalign`,
`apksigner`. Signing key в source ZIP и deliverables не включён.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 `
  -JavaHome '<jdk17>' `
  -AndroidSdkRoot '<android-sdk>' `
  -Keystore '<existing-restorer-keystore>' `
  -KeystorePassword '<password>' `
  -KeyAlias '<alias>'
```

## Проверка

VersionCode 17 установлен на реальный Xiaomi Mi TV S75 после удаления legacy
package. Подтверждены новый package, grant, `stopped=false`, маршруты Activity,
ControlActivity 1920x1080 с одной кнопкой и Bound-состояние Mapper/Projectivy/
TorrServe. Затем выполнен `adb shell reboot -p`; ADB перешёл в `offline`.

Физический cold boot после полного выключения выполняет пользователь. Push и
GitHub Release не выполнялись.
