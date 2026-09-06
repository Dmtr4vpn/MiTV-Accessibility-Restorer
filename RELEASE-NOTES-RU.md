# FOX MiTV Restorer 4.0.0, versionCode 19

Локальная тестовая сборка для Xiaomi Mi TV S75, MiTV OS 2.8.1712, Android 11.

## Изменено

- Экран состояния больше не использует успешное состояние `найден`.
- Проверяется фактический grant `WRITE_SECURE_SETTINGS`.
- Проверяется `Settings.Secure.ACCESSIBILITY_ENABLED`.
- Для Button Mapper, Projectivy и TorrServe проверяются package, component и
  точный token в `ENABLED_ACCESSIBILITY_SERVICES`.
- Для v2RayTun показывается текущий VPN transport из `ConnectivityManager`, а не
  сохранённый результат recovery session.
- Optional TorrServe/v2RayTun не влияют на core readiness.
- UI не заявляет Bound; Bound остаётся отдельной ADB acceptance-проверкой.

## Projectivy XS

В APK v18 старого artwork не было: единственный banner уже содержал FOX. Причина
оказалась в повторном Glide URI-ключе Projectivy:

```text
android.resource://com.mitv.accessibilityrestorer/0x7f010000
```

В v19 утверждённый bitmap не изменён, но активный banner имеет новое имя
`@drawable/restorer_banner_fox` и новый ID `0x7f010001`. После безопасного
`force-stop/start` Projectivy реальная XS-карточка на телевизоре показывает FOX.

`pm clear com.spocky.projengmenu` не выполнялся. Настройки Projectivy не
очищались.

## Не изменено

- package `com.mitv.accessibilityrestorer`;
- versionName `4.0.0`;
- cold boot и STR recovery;
- все recovery timings и sequence;
- RecoveryCover `#474747`;
- Button Mapper, Projectivy, TorrServe и V2RayVpnAssist logic;
- `start_3rd_app` read-only/advisory;
- routing и permissions;
- одна кнопка `Восстановить спецвозможности`.

Recovery logic changes: **NONE**.

`INSTALL.cmd` содержательно не изменён. Файл нормализован в UTF-8 без BOM с
Windows CRLF и реально проверен через `cmd.exe /d /c "call INSTALL.cmd <nul"`.

Push и GitHub Release не выполнялись.
