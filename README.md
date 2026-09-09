# FOX MiTV Restorer

Актуальная версия Android TV-приложения для восстановления рабочего окружения
Xiaomi Mi TV после загрузки и пробуждения из сна.

- Product: `FOX MiTV Restorer`
- package: `com.mitv.accessibilityrestorer`
- versionName: `4.0.0`
- versionCode: `19`
- minSdk: `21`
- targetSdk: `28`
- compileSdk: `35`
- проверенная платформа: Xiaomi Mi TV S75, MiTV OS 2.8.1712, Android 11

Приложение ориентировано на особенности загрузки и STR в Xiaomi Mi TV.
Совместимость со всеми устройствами Android TV не заявляется.

## Что восстанавливается

Основные компоненты:

- Button Mapper;
- Projectivy Launcher.

Необязательные компоненты:

- TorrServe;
- v2RayTun.

Отсутствие TorrServe или v2RayTun не считается неисправностью основной части
Restorer.

При восстановлении приложение сохраняет все существующие записи в
`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` без изменения их написания и
добавляет только отсутствующие целевые службы. Затем устанавливается
`Settings.Secure.ACCESSIBILITY_ENABLED=1`.

## Автоматическая работа

Cold boot:

```text
BLACK -> Projectivy
```

STR / Sleep -> Wake:

```text
#474747 -> Projectivy
```

Используются Direct Boot, `BOOT_COUNT`, одноразовые boot/fallback-триггеры и
защита от повторного выполнения в одной загрузочной сессии. Постоянного Service,
фонового мониторинга и повторяющихся alarm нет.

После основного восстановления отдельно обрабатываются необязательные TorrServe
и v2RayTun. Финальный пользовательский экран после автоматического восстановления
- Projectivy Launcher.

## Экран состояния

`ControlActivity` автоматически обновляет состояние при открытии, возврате на
экран и после ручного восстановления. Проверяются:

- фактический grant `WRITE_SECURE_SETTINGS`;
- `Settings.Secure.ACCESSIBILITY_ENABLED`;
- package, AccessibilityService и точный enabled-token Button Mapper;
- package, AccessibilityService и точный enabled-token Projectivy Launcher;
- такое же состояние необязательного TorrServe;
- текущий VPN transport через `ConnectivityManager` для v2RayTun.

UI не заявляет статус Bound, потому что обычный APK не может достоверно получить
его через публичный Android API. Для этого используется отдельная ADB-проверка
`dumpsys accessibility`.

На экране одна кнопка: `Восстановить спецвозможности`. Для выхода используется
физическая кнопка HOME на пульте.

## Установка

Рекомендуемая структура Windows-комплекта:

```text
ADBAppControl-1.8.6\
  INSTALL.cmd
  adb\adb.exe
  install\FOX-MiTV-Restorer-4.0.0.apk
  install\другие-приложения.apk
```

`INSTALL.cmd` обрабатывает все APK из `install`, сравнивает `versionCode`, при
необходимости устанавливает или обновляет приложение, выдаёт и проверяет
`WRITE_SECURE_SETTINGS`, запускает recovery bootstrap и открывает
`ControlActivity` после успешной проверки.

Ручная установка или обновление:

```text
adb install -r FOX-MiTV-Restorer-4.0.0.apk
adb shell pm grant com.mitv.accessibilityrestorer android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.mitv.accessibilityrestorer/.ControlActivity
```

После установки один раз проверьте физическую кнопку HOME. Ожидаемый результат:
Projectivy Launcher. Синтетическая HOME через ADB не используется.

## Android TV routing

```text
MAIN + LAUNCHER          -> com.mitv.accessibilityrestorer/.MainActivity
MAIN + LEANBACK_LAUNCHER -> com.mitv.accessibilityrestorer/.ControlActivity
MAIN + INFO              -> NO MATCH
```

`MainActivity` является только recovery bootstrap. Пользовательская карточка
Android TV открывает `ControlActivity`.

## Графика

- системная иконка: `@mipmap/ic_launcher`;
- круглая иконка: `@mipmap/ic_launcher_round`;
- Android TV banner: `@drawable/restorer_banner_fox`;
- banner в APK: PNG, `640x360`, с надписью FOX.

Отображение FOX в XS-карточке Projectivy физически проверено на телевизоре.

## Разрешения и ограничения

Manifest содержит:

- `android.permission.RECEIVE_BOOT_COMPLETED`;
- `android.permission.WRITE_SECURE_SETTINGS`;
- `android.permission.ACCESS_NETWORK_STATE`.

В приложении нет `INTERNET`, `WRITE_SETTINGS`, `WAKE_LOCK`, собственного
AccessibilityService, постоянного Service/ForegroundService, WorkManager,
JobScheduler и повторяющихся alarm.

## Сборка

Используется официальный Android toolchain: `aapt2`, `javac`, D8, `zipalign`,
`apksigner`. Signing key не хранится в репозитории и не включён в source bundle.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 `
  -JavaHome '<jdk17>' `
  -AndroidSdkRoot '<android-sdk>' `
  -Keystore '<restorer-keystore>' `
  -KeystorePassword '<password>' `
  -KeyAlias '<alias>'
```

## Публикация

- Repository: https://github.com/Dmtr4vpn/FOX-MiTV-Restorer
- Release: https://github.com/Dmtr4vpn/FOX-MiTV-Restorer/releases/tag/v4.0.0
