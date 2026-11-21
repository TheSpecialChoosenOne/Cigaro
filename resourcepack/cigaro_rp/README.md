# Cigaro Resource Pack

Ресурспак плагина **Cigaro 1.1.0** для Minecraft **26.3** (Paper 26.3 build 211).
Содержит предметы, 2D-иконки, 3D-модели и модели всех станций.

## Установка

1. Возьмите `Cigaro-ResourcePack-1.1.0.zip` из `build/distributions/`.
2. Положите zip в папку `resourcepacks` клиента (распаковывать не нужно).
3. В игре: `Esc` → `Options` → `Resource Packs` → включите
   «Cigaro Plugin Resource Pack» → `Done`.
4. Для раздачи с сервера используйте `server.properties` (`resource-pack`) или
   плагин раздачи ресурспаков.

Папку `cigaro_rp` также можно скопировать в `resourcepacks` целиком — клиент
принимает и папку, и zip.

## Совместимость

- `pack.mcmeta`: `min_format` / `max_format` = `[97, 1]` (Minecraft 26.3).
- Проверено на ванильном клиенте 26.3: пак загружается без ошибок
  missing texture / bake / model.
- `pack.png` — настоящий PNG 96×96 (не WebP).
- Плагин передаёт модели через строковый `minecraft:custom_model_data`;
  предметы выбираются в `assets/minecraft/items/`.

## Что внутри

- **151** модель в `assets/cigaro/models/`.
- **125** PNG-текстур в `assets/cigaro/textures/`.
- **25** item-definitions в `assets/minecraft/items/`.
- 2D-иконки для инвентаря и GUI.
- 3D-модели сигарет, сигар, пачек, трубки и станций.
- Модели 11 станций и их GUI-иконки.

## Станции

| Станция | Базовый предмет | Модель |
|---|---|---|
| Сушилка | `polished_blackstone` | `station_drying_rack` / `drying_rack_2d` |
| Скручивающая машина | `lodestone` | `station_rolling_machine` |
| Стол для резки | `smooth_stone` | `station_cutting_table` |
| Упаковочная машина | `polished_deepslate` | `station_packing_machine` |
| Инфузер | `calcite` | `station_infuser` |
| Стол купажа | `smithing_table` | `station_blending_table` |
| Кальян | `lightning_rod` | `station_hookah` |
| Авто-сушка | `tuff` | `station_auto_drying_rack` |
| Режущая машина | `iron_block` | `station_cutting_machine` |
| Скручивающий пресс | `quartz_block` | `station_rolling_press` |
| Упаковочный пресс | `packed_mud` | `station_packing_press` |

В мире станции отображаются через `ItemDisplay` + `Interaction`, а не через
реальный блок. Плагин ставит `ItemDisplay` с масштабом `2.0`; в моделях станций
`display.fixed.scale` равен `0.5`, поэтому итоговый размер совпадает с блоком.
Сушилка в инвентаре использует 2D-модель `drying_rack_2d`, а в мире —
3D-модель `station_drying_rack`.

## Технические детали

- Модели с `parent: minecraft:item/generated` — 2D-иконки; модели с `elements` —
  3D-предметы и станции.
- Все 11 станционных моделей используют собственные block-текстуры
  `station_*` (64×64); UV нормализованы в диапазон `0..16`.
- Текстуры станций лежат в `assets/cigaro/textures/block/station_*.png`.
- В `assets/minecraft/items/` используются `minecraft:select` по строковому
  `custom_model_data`; fallback для block-item — `minecraft:block/<base>`,
  не `minecraft:item/<base>`.
- В моделях `tobacco_crop_*_stage3` добавлен алиас `missing`, потому что
  Blockbench экспортировал часть невидимых граней как `#missing`.
- Растения табака используют отдельные текстуры для каждого сорта:
  `virginia_crop_stage{0..3}_texture`, `burley_crop_stage{0..3}_texture`,
  `oriental_crop_stage{0..3}_texture`, `dark_fired_crop_stage{0..3}_texture`.
  UV моделей и размеры PNG согласованы: стадии 0–1 — 32×32, стадия 2 — 64×64,
  стадия 3 — 40×40. Общие `tobacco_crop_stage*_texture` из пака удалены, потому
  что плагин всегда создаёт растения с CMD конкретного сорта.
- Модели стадий 0 и 1 пересобраны как две пересекающиеся плоскости (±45°):
  старые плоскости были параллельны, из-за чего всходы почти исчезали при
  взгляде сбоку. Геометрия центрирована по блоку.
- `TobaccoManager` поднимает stage0 на `0.5` блока (`DISPLAY_Y_OFFSET`): при
  `ItemDisplayTransform.FIXED` модель центрируется, и без смещения всходы
  уходят под поверхность блока.
- Новые 2D-иконки: восемь `flavor_bottle_*`, `fuel_pellets`,
  `tobacco_fertilizer` (в исходниках файл назывался `tobacco_fertillizer.png`).
- Удалены неиспользуемые дубли: `block/drying_rack`, старые
  `elite_leaf_{virginia,burley,oriental,dark_fired}`, `nicotine_patch_3d`,
  а также их текстуры и `smoketexture_all_2`. Элитные листья используют общие
  модели `tobacco_leaf_*`; никотиновый пластырь — 2D-модель `nicotine_patch`.
- Статический аудит: все JSON разобраны, все texture/model-ссылки существуют,
  PNG корректны, неиспользуемых моделей и текстур нет.

## Проверка на 26.3

- Paper 26.3 build 211 / Java 25: плагин загружается, БД и рецепты работают.
- Все 11 станций поставлены на живом сервере; для каждой созданы `ItemDisplay`
  и `Interaction`; модели видны в мире.
- Все 11 иконок станций и предметов проверены в GUI.
- Авто-сушка пережила рестарт сервера; её `ItemDisplay` восстановился.
- Взаимодействия: авто-сушка принимает топливо (проверено +1280 с),
  сушилка выдаёт статус по ПКМ.
- Растения табака: четыре сорта на stage3 визуально различаются; стадии 0–3
  проверены с теми же `scale`/`yOffset`, что задаёт `TobaccoManager`. Все
  всходы видны, пропавших текстур и пустых моделей нет.
- После загрузки пака и моделей в клиентском логе нет missing texture,
  bake- и model-ошибок.

## Лицензия

Использовать только с плагином Cigaro.
