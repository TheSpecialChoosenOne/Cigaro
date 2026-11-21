# Cigaro Shaders 26.3 — standalone post-effect pack

Статус: проверено на ванильном клиенте Minecraft 26.3 (protocol 777) и Paper 26.3 build 211. Все 12 post-effect'ов загружаются, компилируются и снимаются без ошибок.

Канонический пак: `Cigaro_Shaders_26_3_RP/`
Готовый архив: `Cigaro_Shaders_26_3_RP.zip`
SHA-256: `81636fe1bc15c9f6a246db57cf0757449f363db0a465f60efd70e09f4f225010`
Генератор: `shadergen.py`
Исходник эффекта: `cigaro_fx.fsh`

## Сборка

```bash
python3 resourcepack/shaders/shadergen.py
```

`shadergen.py` читает `cigaro_fx.fsh`, пишет канонический пак, его зеркало `shader_rp/` (для старых скриптов) и пересобирает zip.

## Что было сломано и исправлено

- `pack.mcmeta` использовал старые `pack_format: 91` и `supported_formats`. Для 26.3 нужны `min_format`/`max_format` = `[97, 1]`; иначе клиент не считает пак совместимым.
- В шейдерах стоял `#moj_import <minecraft:globals.glsl>`. В 26.3 включения обрабатываются через `#include <minecraft:globals.glsl>`.
- Не хватало `#extension GL_ARB_separate_shader_objects : require`.
- Вход `texCoord` и выход `fragColor` не имели явных `layout(location = 0)`.
- `SamplerInfo` для пасса с двумя входами должен быть `OutSize`, `InSize`, `FadeSize`; для пасса с одним входом — `OutSize`, `InSize`.
- Цели `fade_a`/`fade_b` сделаны `persistent`, размер 2×2. Это 16-битный таймер прогресса, который живёт между кадрами. При снятии эффекта `GameRenderer` закрывает persistent-цели, поэтому повторное добавление снова начинает fade-in с нуля.
- Старые эффекты были построены на симметричных `sin(t)` и плоском `col * tint`. Новый `cigaro_fx.fsh` использует апериодические суммы частот, физиологические кривые, split toning, lift blacks и пространственно-зависимые искажения.

## Эффекты

| ID | Режим | Визуал | Fade-in (скорость/кадр) |
|---|---|---|---:|
| `cigaro:puff` | 0 | Classic Tobacco: асимметричное дыхание, зум ниже центра, радиальный дым по периферии, пепельные тени и медовые света | 0.014 |
| `cigaro:mint_chill` | 1 | Menthol Freeze: резкий импульс на входе, unsharp mask, стальные тени, кристальный циан, морозные пятна по углам | 0.015 |
| `cigaro:spice_heat` | 2 | Spice & Heat: конвективное марево из трёх волн, дрожащая хроматическая аберрация, тёплый bloom, терракотовый грейд | 0.012 |
| `cigaro:shadow_noir` | 3 | Berry / Sweet Capsule (legacy ID): ленивый дрейф, Black Pro-Mist bloom, vibrance, ягодные тени | 0.011 |
| `cigaro:withdrawal` | 4 | Nicotine Rush: двойной удар сердца, саккады, затемнение краёв на пике, зерно в тенях, вымывание цвета | 0.008 |
| `cigaro:killer_trip` | 5 | Herbal Daze: покачивание по Лиссажу, инерционный ghosting, спектральная CA, cross-processing, лёгкая соляризация | 0.007 |

`_out`-варианты используют тот же шейдер, но `ModeAndDir.y = -1`, поэтому стартуют с полной интенсивности и гаснут. Скорость fade-out в 1.2 раза выше fade-in.

`shadow_noir` сохранён как legacy ID, чтобы не ломать датапак и будущую интеграцию. Визуально это уже Berry / Sweet Capsule, а не ч/б нуар.

## Как применять

```mcfunction
posteffect add @s cigaro:puff
posteffect remove @s cigaro:puff
posteffect add @s cigaro:puff_out
```

Для перехода in → out:

1. В одном тике снять in-эффект и добавить out-эффект.
2. Не держать in и out одновременно — иначе оба пасса суммируются.
3. После завершения fade-out снять out-эффект.

Пример для серверной интеграции:

```text
posteffect remove <player> cigaro:puff
posteffect add <player> cigaro:puff_out
# через ~1 секунду:
posteffect remove <player> cigaro:puff_out
```

## Таймер и ограничение

Прогресс хранится в двух 8-битных каналах persistent-таргета: `R = floor(p * 255) / 255`, `G = fract(p * 255)`, чтение — `R + G / 255`. Это даёт 16 бит точности без float-таргета.

Скорость задаётся в единицах прогресса **за кадр**, а не за секунду: в `Globals` 26.3 нет delta-time/real-time uniform. При 60 FPS значения из таблицы дают примерно 1.0–2.4 с до полного проявления; на 120+ FPS — примерно вдвое быстрее. Если нужен другой темп, меняйте `fade_speed` в `shadergen.py` и пересобирайте пак.

Внутри шейдера время анимации берётся из `GameTime * 1200.0` — это секунды внутри суточного цикла (26.3 нормализует `GameTime` в 0..1). Поэтому дыхание, пульс, дрейф и марево не зависят от fade-таймера и продолжаются во время hold/out.

## Проверка

Тестовый клиент загрузил пак без предупреждений о формате:

```text
Reloading ResourceManager: vanilla, vanilla, file/Cigaro_Shaders_26_3_RP.zip
```

Проверено:

- все 12 post-effect'ов добавляются и снимаются командой `posteffect add/remove`;
- в логе нет ошибок компиляции/линковки шейдеров;
- `posteffect list Test` после снятия пуст;
- единственное предупреждение — ванильное `Requested post effect does not exist: minecraft:end_of_frame`, оно не связано с паком.

На момент последней проверки тестовый сервер и клиент оставлены запущенными.

## Датапак для теста

Отдельный Paper-датапак: `datapacks/cigaro_effect_tester/` — `/ce <effect> [seconds]` и `/ce remove [effect|all]`. Tab completion и подсказки даёт мини-плагин `tools/ce-tester-plugin` (`CeTester`). Датапак сам включает in-эффект, держит заданное время, переключает на `_out` и снимает его. Это не часть resource pack и не требует интеграции в Cigaro.
