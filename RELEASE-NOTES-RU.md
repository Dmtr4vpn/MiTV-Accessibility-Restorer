# FOX MiTV Restorer 4.0.0

Финальная версия для Xiaomi Mi TV S75, MiTV OS 2.8.1712, Android 11.

- Package: `com.mitv.accessibilityrestorer`
- versionName: `4.0.0`
- versionCode: `19`

## Возможности

- автоматическое восстановление после cold boot и Sleep/Wake (STR);
- восстановление Button Mapper и Projectivy Launcher;
- сохранение всех существующих Accessibility-служб без изменения их строк;
- необязательное восстановление TorrServe;
- необязательное восстановление v2RayTun и текущего VPN-соединения;
- финальный запуск Projectivy Launcher;
- компактный экран фактического состояния разрешения, Accessibility и VPN;
- одна кнопка `Восстановить спецвозможности`;
- системная иконка и Android TV banner в оформлении FOX;
- корректное отображение FOX в XS-карточке Projectivy;
- Windows-установщик с обработкой APK по `versionCode` и автоматической выдачей
  `WRITE_SECURE_SETTINGS`.

Основные компоненты: Button Mapper и Projectivy Launcher.

TorrServe и v2RayTun являются необязательными. Их отсутствие не считается
ошибкой основной части Restorer.

## Проверено на телевизоре

- ordinary reboot: PASS;
- cold boot: PASS;
- STR / Sleep -> Wake: PASS;
- Button Mapper recovery: PASS;
- Projectivy recovery: PASS;
- TorrServe recovery: PASS;
- v2RayTun / VPN: PASS;
- physical HOME -> Projectivy: PASS;
- Projectivy XS FOX artwork: PASS;
- INSTALL.cmd execution: PASS.

## Ограничения

Приложение ориентировано на Xiaomi Mi TV и особенности их загрузки/STR.
Универсальная совместимость со всеми Android TV не заявляется.
