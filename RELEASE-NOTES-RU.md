# FOX MiTV Restorer 4.0.0

Локальная кандидатная сборка `versionCode 18` для Xiaomi Mi TV S75,
MiTV OS 2.8.1712, Android 11.

## Xiaomi compatibility fix

- Android package возвращён с ошибочного v17 identity `foxmitv.restorer` на
  проверенный `com.mitv.accessibilityrestorer`.
- Manifest, Java namespace, внутренние Restorer actions, installer и ADB-команды
  обновлены механически.
- `INSTALL.cmd` удаляет только ошибочный v17 package перед установкой v18.
- Recovery logic changes: NONE.

## Сохранено из v17

- Упрощённый `ControlActivity` с одной кнопкой `Восстановить спецвозможности`.
- Автоматический refresh статусов в `onCreate()`, `onResume()` и после manual
  recovery callback.
- FOX label, квадратная иконка и Android TV banner `640x360`.
- STR cover `#474747`, все STR/cold-boot timings и recovery sequence.
- Button Mapper, Projectivy, TorrServe, V2RayVpnAssist и optional handling.
- `start_3rd_app` остаётся read-only/advisory.
- Установщик не использует синтетический HOME test.

## Маршрутизация

```text
MAIN + LAUNCHER          -> com.mitv.accessibilityrestorer/.MainActivity
MAIN + LEANBACK_LAUNCHER -> com.mitv.accessibilityrestorer/.ControlActivity
MAIN + INFO              -> NO MATCH
```

## Проверка обычной перезагрузкой

- Ошибочный v17 package удалён, v18 установлен начисто: `Success`.
- `WRITE_SECURE_SETTINGS: granted=true`, `stopped=false`.
- После `adb reboot`: `BOOT_COUNT=647`.
- Xiaomi автоматически запустила Restorer: в logcat присутствуют `EARLY_BOOT`,
  `SESSION START versionCode=18` и `SESSION FINISH success=true`.
- Button Mapper, Projectivy и TorrServe находились в Bound;
  `Binding services:{}` и `Crashed services:{}` были пустыми.
- Foreground в момент отложенного readback был штатный `com.mitv.tvhome`; полный
  cold-boot/STR acceptance остаётся пользовательской проверкой.

Push и GitHub Release не выполнялись.
