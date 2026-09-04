# FOX MiTV Restorer 4.0.0

Финальная локальная кандидатная сборка `versionCode 14` для Xiaomi Mi TV S75,
MiTV OS 2.8.1712, Android 11.

## Исправления

- Cold-boot восстановление v2RayTun больше не запускается слишком рано сразу
  после EARLY_BOOT core.
- Добавлен idempotent gate: optional worker запускается только после успешного
  core boot и получения Android `BOOT_COMPLETED`, независимо от порядка событий.
- Persistent и in-memory маркеры разрешают не более одного optional worker на
  конкретный `BOOT_COUNT`.
- После cold boot TorrServe получает аккуратный append `GlobalTorrService` в
  текущий raw `enabled_accessibility_services`. Системные и сторонние entries не
  удаляются, не переупорядочиваются и не нормализуются.
- Перед optional-операциями проверяются package и нужный component. Результаты
  `SKIPPED_NOT_INSTALLED` и `SKIPPED_COMPONENT_UNAVAILABLE` являются advisory.
- Ошибки TorrServe и v2RayTun изолированы друг от друга и не меняют core success.
- `CATEGORY_INFO` полностью удалена: Xiaomi automatic package relaunch больше не
  может открыть `ControlActivity` через package front door.
- `MainActivity` сохранена в проверенной recovery-only семантике commit `c9834bf`.
- Пользовательский Android TV entry остаётся только
  `LEANBACK_LAUNCHER -> ControlActivity`.
- `Theme.Restorer.Trampoline` стала непрозрачной и чёрной с первого кадра;
  `RecoveryCover` остаётся solid black fullscreen.
- Название изменено на `FOX MiTV Restorer`.
- Launcher mipmaps заменены прямым downscale утверждённого `3.1.png`; существующий
  FOX banner сохранён и назначен application и `ControlActivity`.
- Hardcoded diagnostic version удалена; UI и `SESSION START` читают фактические
  versionName/versionCode через `PackageInfo`.
- First Run UI показывает наличие `WidgetProvider1x1` v2RayTun, но optional
  приложения по-прежнему не блокируют завершение настройки.

## Сохранённое поведение

- Package, signing key и `versionName 4.0.0` не изменены.
- Direct Boot, `LOCKED_BOOT_COMPLETED`, `BOOT_COUNT`, EARLY_BOOT `2500 ms`,
  одноразовый fallback alarm `10000 ms` и core Mapper/Projectivy не изменены.
- `MainActivity` остаётся единственным `MAIN/LAUNCHER` recovery entry.
- `ControlActivity` имеет только `MAIN/LEANBACK_LAUNCHER`, без `INFO` и обычного
  `LAUNCHER`.
- STR sequence и профиль `500 + 2500 + 1500 + 2500 + 1500 ms` не изменены.
- TorrServe остаётся только в `ALL_FINAL` для STR.
- Существующий `V2RayVpnAssist` переиспользуется без второго VPN-алгоритма:
  3 retries по `100 ms`, grace `2000 ms`, poll `250 ms`, timeout `5000 ms`,
  не более одного widget trigger и запрет blind toggle при
  `stopped=false + VPN=UNKNOWN`.
- `start_3rd_app` остаётся read-only advisory и стандартной установкой не
  записывается.

## Совместимость

- package: `com.mitv.accessibilityrestorer`
- versionName: `4.0.0`
- versionCode: `14`
- minSdk: `21`
- targetSdk: `28`
- compileSdk: `35`

APK подписан прежним сертификатом, поэтому поддерживает `adb install -r` поверх
предыдущих сборок с тем же ключом.

## Проверка и публикация

APK прошёл локальную production-сборку, проверку подписи/выравнивания/Manifest и
полный `dexdump`. Физические cold boot, Android TV user UI и STR regression для
`versionCode 14` ещё должен выполнить пользователь. GitHub Release до этого не
публикуется.
