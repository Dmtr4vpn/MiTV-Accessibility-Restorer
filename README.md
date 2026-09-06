# FOX MiTV Restorer

Android TV-приложение для восстановления рабочего окружения Xiaomi Mi TV S75
после холодной загрузки и пробуждения из сна (STR).

- package: `com.mitv.accessibilityrestorer`
- ошибочный package v17: `foxmitv.restorer`
- versionName: `4.0.0`
- versionCode: `18`
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

## Xiaomi-compatible identity

VersionCode 18 возвращает проверенный package `com.mitv.accessibilityrestorer`.
Тест v17 показал, что Xiaomi не поднимает автоматически package
`foxmitv.restorer` после `killAllThirdApp / force-stop`, хотя recovery при явном
запуске остаётся исправным. Поэтому package identity возвращена без изменений
recovery engine.

Перед установкой v18 ошибочный v17 package необходимо удалить:

```text
adb shell pm path foxmitv.restorer
adb uninstall foxmitv.restorer
```

Это удаляет только v17 Restorer и не затрагивает Projectivy, Button Mapper,
TorrServe или v2RayTun.

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
`com.mitv.accessibilityrestorer` он без запроса удаляет только ошибочный v17
package `foxmitv.restorer`, если тот найден. Затем выдаёт и проверяет
`WRITE_SECURE_SETTINGS`, перезапускает Projectivy без очистки данных, явно
запускает `MainActivity` и ждёт READY.

READY требует пять условий два polling cycle подряд с интервалом `1000 ms`:

1. Button Mapper находится в `enabled_accessibility_services`.
2. Projectivy находится в `enabled_accessibility_services`.
3. Button Mapper находится в `Bound services`.
4. Projectivy находится в `Bound services`.
5. Foreground равен Projectivy home activity.

Timeout равен 30 секундам. Синтетический HOME test отсутствует. После READY
установщик открывает `com.mitv.accessibilityrestorer/.ControlActivity`;
физическую HOME пользователь проверяет кнопкой пульта.

Ручная миграция и установка:

```text
adb shell pm path foxmitv.restorer
adb uninstall foxmitv.restorer
adb shell pm path com.mitv.accessibilityrestorer
adb uninstall com.mitv.accessibilityrestorer
adb install FOX-MiTV-Restorer-4.0.0.apk
adb shell pm grant com.mitv.accessibilityrestorer android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.mitv.accessibilityrestorer/.ControlActivity
```

Удалять существующий `com.mitv.accessibilityrestorer` нужно только для чистой
установки v18, как предусмотрено процедурой проверки. Для дальнейших обновлений
с тем же ключом используется `adb install -r`.

## Пользовательский экран

`ControlActivity` автоматически обновляет статус в `onCreate()`, `onResume()` и
после callback ручного восстановления. На основном экране видны:

- `WRITE_SECURE_SETTINGS`;
- Button Mapper и Projectivy как core;
- TorrServe и v2RayTun/VPN как optional;
- общий результат и последнее восстановление;
- постоянная подсказка `Для выхода нажмите HOME.`.

Есть ровно одна action-кнопка: `Восстановить спецвозможности`. Она использует
существующий manual recovery. После callback ControlActivity снова выводится на
передний план и обновляет статус.

## Routing

```text
MAIN + LAUNCHER          -> com.mitv.accessibilityrestorer/.MainActivity
MAIN + LEANBACK_LAUNCHER -> com.mitv.accessibilityrestorer/.ControlActivity
MAIN + INFO              -> NO MATCH
```

`MainActivity` остаётся recovery-only bootstrap и не запускает ControlActivity.

## Recovery freeze

По сравнению с v17 commit `28b33b798a95cf8483e62c143332ef2615ffe828`
recovery-код изменён только механическим возвратом Java package namespace.
Сохранены:

- Direct Boot, `BOOT_COUNT`, EARLY_BOOT settle `2500 ms` и fallback `10000 ms`;
- STR: `500 + 2500 + 1500 + 2500 + 1500 ms`, последовательность
  `ALL_INITIAL -> BASE -> MAPPER -> ALL_FINAL`;
- RecoveryCover `#474747` для STR и чёрная cold-boot trampoline;
- Button Mapper, Projectivy final launch и optional TorrServe;
- V2RayVpnAssist: grace `2000 ms`, poll `250 ms`, timeout `5000 ms`;
- `start_3rd_app` только read-only/advisory.

Внешние package names не изменены: `flar2.homebutton`,
`com.spocky.projengmenu`, `ru.yourok.torrserve`, `com.v2raytun.android`.

## Разрешения

Manifest содержит только текущие разрешения:

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

Push и GitHub Release для этой сборки не выполняются.

## Проверка v18

После чистой установки и `adb reboot` Xiaomi снова автоматически запустила
`MainActivity`: logcat содержит `EARLY_BOOT`, `SESSION START versionCode=18` и
`SESSION FINISH success=true`. Button Mapper, Projectivy и TorrServe были Bound,
а Binding/Crashed списки пусты. Полный cold boot и STR acceptance выполняет
пользователь отдельно.
