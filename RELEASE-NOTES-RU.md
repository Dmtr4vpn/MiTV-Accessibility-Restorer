# MiTV Accessibility Restorer 4.0.0

Рабочий релиз для Xiaomi Mi TV S75 / MiTV-MFTR0 с прошивкой MiTV OS 2.8.1712
на Android 11.

## Основное

- Сохранён быстрый accelerated cold boot с автоматическим запуском Projectivy.
- Smooth STR занимает около 9 секунд и скрыт непрозрачным чёрным recovery cover.
- Восстанавливаются Accessibility Button Mapper и Projectivy.
- Button Mapper восстанавливает прямой переход Home -> Projectivy без мелькания
  Xiaomi Home; штатный Xiaomi tvhome остаётся fallback.
- TorrServe MatriX добавляется только в финальную STR-фазу Accessibility, что
  позволяет системе запустить TorrServe и native `torrserver` без открытия UI.
- v2RayTun VPN assist параллельно проверяет активный VPN и при необходимости
  отправляет не более одного безопасного widget broadcast без открытия UI.

## Совместимость

- Package: `com.mitv.accessibilityrestorer`
- Version: `4.0.0` (`versionCode 9`)
- minSdk: 21
- targetSdk: 28
- compileSdk: 35

APK подписан прежним сертификатом и поддерживает обновление через
`adb install -r` с предыдущих сборок, подписанных тем же ключом.

## Проверка

Механизмы основаны на ранее выполненных физических проверках Smooth STR,
TorrServe Accessibility auto-start и v2RayTun widget broadcast. Объединённый APK
`4.0.0` должен быть отдельно проверен на физическом телевизоре, включая
Bound/Binding/Crashed, native `torrserver`, VPN transport и Home remap.
