# MiTV Accessibility Restorer 4.0.0

Финальная локальная кандидатная сборка для Xiaomi Mi TV S75 / MiTV-MFTR0 с
MiTV OS 2.8.1712 / Android 11.

## Изменения

- Исправлено восстановление v2RayTun после подтверждённого Xiaomi
  `ConnectivityManager` `SecurityException`.
- `ConnectivityManager` теперь всегда получается от application context пакета
  `com.mitv.accessibilityrestorer`.
- При `SecurityException` выполняется максимум три попытки с паузой `100 ms`.
- Если VPN остаётся `UNKNOWN`, widget trigger разрешён только после повторного
  подтверждения `FLAG_STOPPED=true`; для `stopped=false` blind toggle запрещён.
- Сохранены один trigger за STR, grace `2000 ms`, poll `250 ms` и timeout
  `5000 ms`.
- После успешного core cold boot существующий `V2RayVpnAssist` теперь запускается
  отдельным асинхронным post-boot шагом. Projectivy и результат core не ждут VPN.
- Добавлен first-run экран для ТВ-пульта с проверкой основных и необязательных
  компонентов, разрешения и внутренним маркером `setup_completed`.
- Добавлен `INSTALL.cmd`: установка, выдача/readback `WRITE_SECURE_SETTINGS` и
  открытие setup UI без автоматического uninstall.
- Стандартная установка больше не записывает `start_3rd_app`; advisory readback
  внутри STR оставлен и не считается ошибкой.

## Сохранённое поведение

- Accelerated core cold boot не изменён; post-boot VPN запускается только после
  его успешного `SESSION FINISH`.
- Smooth STR сохраняет профиль `500 + 2500 + 1500 + 2500 + 1500 ms` и чёрный
  recovery cover.
- Button Mapper и Projectivy восстанавливаются прежним способом.
- TorrServe Accessibility добавляется только в `ALL FINAL`.
- Xiaomi tvhome остаётся fallback.

## Совместимость

- Package: `com.mitv.accessibilityrestorer`
- Version: `4.0.0` (`versionCode 11`)
- minSdk: 21
- targetSdk: 28
- compileSdk: 35

APK подписан прежним сертификатом и поддерживает `adb install -r` с версиями,
подписанными тем же ключом.

## Проверка

APK проходит локальную статическую проверку стандартным Android toolchain. Core
cold boot и STR ранее проверены на ТВ на `versionCode 10`; новый post-boot VPN шаг
в `versionCode 11` ещё должен пройти физический cold-boot test.
Публикация GitHub Release отложена до отдельного разрешения после regression test.
