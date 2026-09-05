# FOX MiTV Restorer 4.0.0

Локальная кандидатная сборка `versionCode 16` для Xiaomi Mi TV S75,
MiTV OS 2.8.1712, Android 11.

## Изменения v16

- `INSTALL.cmd` считает post-install recovery готовым только при одновременном
  выполнении пяти условий: Button Mapper и Projectivy находятся в
  `enabled_accessibility_services`, обе службы находятся в `Bound services`, а
  foreground уже равен Projectivy home activity.
- Все пять условий должны быть стабильны два polling cycle подряд с интервалом
  `1000 ms`. После второго успешного poll добавлен settle `1500 ms`; HOME test
  также проверяет foreground через `1500 ms` после `KEYCODE_HOME`.
- STR/fast-reboot `RecoveryCover` имеет однотонный цвет `#474747`. Чёрная
  cold-boot trampoline theme не изменена; на cover нет текста, логотипа или
  индикатора.
- Четыре существующие кнопки `ControlActivity` расположены в сетке 2×2,
  уменьшены отступы и размеры текста. Обработчики и статусы не изменены;
  `ScrollView` сохранён как fallback.
- Единственный FOX banner технически уменьшен до `640×360` PNG. Композиция,
  кот, сердце и надпись FOX не менялись.

## Banner/XS audit

В Restorer нет banner aliases или density variants. Application и
`ControlActivity` ссылаются на один `@drawable/restorer_banner`, который в APK
упакован как `res/drawable-nodpi-v4/restorer_banner.png`.

Read-only DEX-аудит установленного Projectivy Launcher 4.71 подтвердил порядок
источников карточки: сначала custom artwork, если оно задано; иначе при TV app
aspect ratio 16:9 загружается `ActivityInfo.banner`, а при 1:1 — launcher icon.
Размер карточки XS не выбирает отдельный banner variant. Поэтому при устаревшем
XS artwork следует проверить custom artwork/cache и настройку TV app aspect
ratio в Projectivy, а не добавлять другой ресурс Restorer.

## Замороженное поведение

- Package, signing key, `versionName=4.0.0`, launcher icon `3.1.png`, manifest
  routing и permissions не изменены.
- `MainActivity.java`, `RecoveryEngine.java`, `RecoveryState.java`,
  `BootReceiver.java` и `V2RayVpnAssist.java` byte-for-byte совпадают с базовым
  commit `dfbf260f9c9ab06976d323c4582b7bc43990ce87`.
- Cold boot, EARLY_BOOT, BOOT_FALLBACK, BOOT_COUNT, optional gate, TorrServe,
  v2RayTun, Button Mapper, Projectivy final launch, STR sequence/timings и
  `start_3rd_app` read-only behavior не менялись.
- Установщик не делает `pm clear`, не переустанавливает Projectivy/Button Mapper
  и не маскирует HOME test запуском Projectivy после READY.

## Публикация

APK прошёл локальную production-сборку, подпись, zipalign, Manifest/resource
audit и полный `dexdump`. Физические post-install, STR, cold-boot, compact UI и
XS banner проверки v16 ещё требуются. Push и GitHub Release не выполнялись.
