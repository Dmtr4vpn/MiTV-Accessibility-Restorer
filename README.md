# FOX MiTV Restorer

Android TV-приложение для восстановления рабочего окружения Xiaomi Mi TV S75
после холодной загрузки и пробуждения из сна (STR).

- package: `com.mitv.accessibilityrestorer`
- versionName: `4.0.0`
- versionCode: `19`
- minSdk: `21`
- targetSdk: `28`
- compileSdk: `35`
- проверенная среда: Xiaomi Mi TV S75, MiTV OS 2.8.1712, Android 11

Package является частью Xiaomi compatibility contract и не меняется.

## Что восстанавливается

Core-компоненты:

- Button Mapper;
- Projectivy Launcher.

Необязательные компоненты:

- TorrServe;
- v2RayTun.

Отсутствие необязательного компонента не делает core recovery неуспешным.
Restorer не меняет Android default HOME, не отключает Xiaomi launcher, не
использует постоянный Service и не выполняет периодический мониторинг.

## Экран состояния

`ControlActivity` выполняет read-only проверку при `onCreate()`, `onResume()` и
после обоих исходов ручного recovery. Экран показывает только состояния,
которые APK может доказать обычными Android API:

- фактический grant `WRITE_SECURE_SETTINGS`;
- `Settings.Secure.ACCESSIBILITY_ENABLED`;
- наличие package и AccessibilityService Button Mapper и Projectivy;
- точное присутствие их service tokens в
  `ENABLED_ACCESSIBILITY_SERVICES`;
- такое же состояние optional TorrServe;
- текущее наличие VPN transport через `ConnectivityManager` для v2RayTun.

Экран не заявляет `Bound`: обычный APK не может достоверно получить этот статус.
Bound проверяется отдельно через ADB `dumpsys accessibility`.

Есть одна action-кнопка: `Восстановить спецвозможности`. Она использует
существующий manual recovery. Для выхода используется физическая HOME.

## Установка

Рекомендуемая структура:

```text
ADBAppControl-1.8.6\
  INSTALL.cmd
  adb\adb.exe
  install\FOX-MiTV-Restorer-4.0.0.apk
  install\другие-приложения.apk
```

`INSTALL.cmd` перебирает APK, сравнивает `versionCode`, выдаёт и проверяет
`WRITE_SECURE_SETTINGS`, запускает recovery bootstrap и ждёт READY.

READY означает пять условий два polling cycle подряд:

1. Button Mapper находится в `enabled_accessibility_services`.
2. Projectivy находится в `enabled_accessibility_services`.
3. Button Mapper находится в `Bound services`.
4. Projectivy находится в `Bound services`.
5. Projectivy home находится в foreground.

Интервал равен `1000 ms`, timeout равен 30 секундам. Синтетическая HOME через
ADB не используется. Физическую HOME пользователь проверяет кнопкой пульта.

Ручное обновление v18 до v19:

```text
adb install -r FOX-MiTV-Restorer-4.0.0.apk
adb shell pm grant com.mitv.accessibilityrestorer android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.mitv.accessibilityrestorer/.ControlActivity
```

## Routing

```text
MAIN + LAUNCHER          -> com.mitv.accessibilityrestorer/.MainActivity
MAIN + LEANBACK_LAUNCHER -> com.mitv.accessibilityrestorer/.ControlActivity
MAIN + INFO              -> NO MATCH
```

`MainActivity` остаётся recovery-only bootstrap и не запускает ControlActivity.

## Projectivy XS artwork

Projectivy 4.71 получает карточку приложения через
`ActivityInfo.getBannerResource()`, строит URI
`android.resource://<package>/<resourceId>` и загружает его через Glide.

В v18 FOX banner был единственным banner в APK, но снова получил исторический
resource ID `0x7f010000`. Projectivy использовал bitmap, сохранённый для старого
URI-ключа этого же package. В v19 тот же утверждённый FOX bitmap имеет новое имя
`@drawable/restorer_banner_fox` и ID `0x7f010001`. Старый cache key больше не
используется. После обычного `force-stop/start` Projectivy реальная XS-карточка
на ТВ показывает FOX.

`pm clear com.spocky.projengmenu` не выполнялся. Layout, favorites и остальные
настройки Projectivy не очищались.

## Recovery freeze

По сравнению с base commit `8e0f3d77a4aa7c1d16a75a4024345efe4b5dad8b`
recovery logic changes: **NONE**.

Не изменены Direct Boot, `BOOT_COUNT`, EARLY_BOOT, BOOT_FALLBACK, STR timings,
`ALL_INITIAL -> BASE -> MAPPER -> ALL_FINAL`, Projectivy final launch, Button
Mapper, TorrServe, V2RayVpnAssist, `start_3rd_app` read-only handling,
RecoveryCover `#474747` и чёрная cold-boot trampoline.

## Разрешения

- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.WRITE_SECURE_SETTINGS`
- `android.permission.ACCESS_NETWORK_STATE`

Нет `INTERNET`, `WRITE_SETTINGS`, `WAKE_LOCK`, Android Service/ForegroundService,
WorkManager, JobScheduler и repeating alarm.

## Сборка

Используется официальный toolchain: `aapt2`, `javac`, D8, `zipalign`,
`apksigner`. Signing key не включён в source ZIP.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 `
  -JavaHome '<jdk17>' `
  -AndroidSdkRoot '<android-sdk>' `
  -Keystore '<existing-restorer-keystore>' `
  -KeystorePassword '<password>' `
  -KeyAlias '<alias>'
```

Push и GitHub Release для v19 не выполняются.
