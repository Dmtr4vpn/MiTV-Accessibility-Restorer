# FOX MiTV Restorer 4.0.0

Локальная кандидатная сборка `versionCode 17` для Xiaomi Mi TV S75,
MiTV OS 2.8.1712, Android 11.

## Новый package

- Android package полностью переименован из `com.mitv.accessibilityrestorer` в
  `foxmitv.restorer`.
- Java namespace, Manifest, внутренние actions, installer и ADB-команды обновлены.
- Старый и новый Restorer нельзя держать одновременно. `INSTALL.cmd` удаляет
  только legacy package перед установкой нового и не очищает связанные приложения.
- Private data старого package не переносится.

## Упрощённый ControlActivity

- Удалены кнопки `Проверить снова`, `Завершить настройку`, `Диагностика` и
  `Восстановить сейчас`.
- Оставлена одна кнопка `Восстановить спецвозможности`, использующая существующий
  manual recovery.
- Статусы WRITE_SECURE_SETTINGS, Button Mapper, Projectivy, optional TorrServe и
  optional v2RayTun/VPN показываются сразу на основном экране.
- Refresh выполняется в `onCreate()`, `onResume()` и после callback manual recovery.
- После manual recovery ControlActivity возвращается на передний план. Синтетический
  HOME не используется.

## Recovery freeze

В `MainActivity`, `RecoveryEngine`, `RecoveryState`, `BootReceiver`,
`RecoveryCoverActivity` и `V2RayVpnAssist` изменена только строка Java package.
Остальной текст файлов совпадает с commit
`536bc491676cd7bf913052cf5e0272eed3577325`.

Cold boot, EARLY_BOOT, BOOT_FALLBACK, BOOT_COUNT, STR timings,
RecoveryCover `#474747`, последовательность ALL/BASE/MAPPER/ALL, Projectivy final
launch, TorrServe, V2RayVpnAssist, optional handling и `start_3rd_app` read-only
не изменены.

## Проверка на ТВ

- Legacy package найден и удалён: `Success`.
- `foxmitv.restorer` установлен напрямую: `Success`.
- WRITE_SECURE_SETTINGS: `granted=true`.
- versionCode=17, versionName=4.0.0, stopped=false, notLaunched=false.
- LAUNCHER разрешился в MainActivity, LEANBACK в ControlActivity, INFO не найден.
- Реальный экран 1920×1080 содержит все статусы и ровно одну action-кнопку.
- Перед shutdown Button Mapper, Projectivy и TorrServe находились в Bound;
  `Binding services:{}` и `Crashed services:{}` были пустыми.
- Выполнен `adb shell reboot -p`; после shutdown устройство стало `offline`.

Физический cold boot выполняет пользователь. Push и GitHub Release не выполнялись.
