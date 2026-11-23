package com.cigaro.item;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.config.LanguageManager;
import com.cigaro.data.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ItemManager {

    private final Cigaro plugin;

    public final NamespacedKey PUFFS_LEFT_KEY;
    public final NamespacedKey PUFFS_MAX_KEY;
    public final NamespacedKey CIGARO_ITEM_KEY;
    public final NamespacedKey CIGARETTE_TYPE_KEY;
    public final NamespacedKey CIGARETTE_QUALITY_KEY;
    public final NamespacedKey FLAVOR_HARMONY_KEY;
    public final NamespacedKey TOBACCO_TYPE_KEY;
    public final NamespacedKey DRYING_QUALITY_KEY;
    public final NamespacedKey PREMIUM_TOBACCO_KEY;
    public final NamespacedKey ELITE_LEAF_KEY;
    public final NamespacedKey DRYING_SECONDS_KEY;
    public final NamespacedKey CUT_QUALITY_KEY;
    public final NamespacedKey PACK_CIGARETTES_KEY;
    public final NamespacedKey PACK_COLOR_KEY;
    public final NamespacedKey PACK_COUNT_KEY;
    public final NamespacedKey FERTILIZER_USES_KEY;
    public final NamespacedKey DRYING_TIMER_KEY;
    public final NamespacedKey LIGHTER_STATS_KEY;
    public final NamespacedKey TEST_STRIP_KEY;
    public final NamespacedKey PAPER_TYPE_KEY;
    public final NamespacedKey FUEL_PELLETS_KEY;
    public final NamespacedKey AUTO_DRYER_FUEL_KEY;
    public final NamespacedKey AUTO_DRYER_ITEMS_KEY;
    public final NamespacedKey AUTO_DRYER_START_KEY;
    public final NamespacedKey CUTTING_MACHINE_FUEL_KEY;
    public final NamespacedKey CUTTING_MACHINE_ITEM_KEY;
    public final NamespacedKey ROLLING_PRESS_FUEL_KEY;
    public final NamespacedKey ROLLING_PRESS_ITEMS_KEY;
    public final NamespacedKey PACKING_PRESS_ITEMS_KEY;
    public final NamespacedKey SMOKABLE_TYPE_KEY;
    public final NamespacedKey PIPE_DATA_KEY;
    public final NamespacedKey BLEND_DATA_KEY;
    public final NamespacedKey HOOKAH_DATA_KEY;
    public final NamespacedKey SPECIAL_TYPE_KEY;
    public final NamespacedKey EXTINGUISHED_KEY;

    public ItemManager(Cigaro plugin) {
        this.plugin = plugin;
        CIGARO_ITEM_KEY = new NamespacedKey(plugin, "cigaro_item");
        CIGARETTE_TYPE_KEY = new NamespacedKey(plugin, "cigarette_type");
        CIGARETTE_QUALITY_KEY = new NamespacedKey(plugin, "cigarette_quality");
        FLAVOR_HARMONY_KEY = new NamespacedKey(plugin, "flavor_harmony");
        TOBACCO_TYPE_KEY = new NamespacedKey(plugin, "tobacco_type");
        DRYING_QUALITY_KEY = new NamespacedKey(plugin, "drying_quality");
        PREMIUM_TOBACCO_KEY = new NamespacedKey(plugin, "premium_tobacco");
        ELITE_LEAF_KEY = new NamespacedKey(plugin, "elite_leaf");
        DRYING_SECONDS_KEY = new NamespacedKey(plugin, "drying_seconds");
        CUT_QUALITY_KEY = new NamespacedKey(plugin, "cut_quality");
        PUFFS_LEFT_KEY = new NamespacedKey(plugin, "puffs_left");
        PUFFS_MAX_KEY = new NamespacedKey(plugin, "puffs_max");
        PACK_CIGARETTES_KEY = new NamespacedKey(plugin, "pack_cigarettes");
        PACK_COLOR_KEY = new NamespacedKey(plugin, "pack_color");
        PACK_COUNT_KEY = new NamespacedKey(plugin, "pack_count");
        FERTILIZER_USES_KEY = new NamespacedKey(plugin, "fertilizer_uses");
        DRYING_TIMER_KEY = new NamespacedKey(plugin, "drying_timer");
        LIGHTER_STATS_KEY = new NamespacedKey(plugin, "lighter_stats");
        TEST_STRIP_KEY = new NamespacedKey(plugin, "test_strip");
        PAPER_TYPE_KEY = new NamespacedKey(plugin, "paper_type");
        FUEL_PELLETS_KEY = new NamespacedKey(plugin, "fuel_pellets");
        AUTO_DRYER_FUEL_KEY = new NamespacedKey(plugin, "auto_dryer_fuel");
        AUTO_DRYER_ITEMS_KEY = new NamespacedKey(plugin, "auto_dryer_items");
        AUTO_DRYER_START_KEY = new NamespacedKey(plugin, "auto_dryer_start");
        CUTTING_MACHINE_FUEL_KEY = new NamespacedKey(plugin, "cutting_machine_fuel");
        CUTTING_MACHINE_ITEM_KEY = new NamespacedKey(plugin, "cutting_machine_item");
        ROLLING_PRESS_FUEL_KEY = new NamespacedKey(plugin, "rolling_press_fuel");
        ROLLING_PRESS_ITEMS_KEY = new NamespacedKey(plugin, "rolling_press_items");
        PACKING_PRESS_ITEMS_KEY = new NamespacedKey(plugin, "packing_press_items");
        SMOKABLE_TYPE_KEY = new NamespacedKey(plugin, "smokable_type");
        PIPE_DATA_KEY = new NamespacedKey(plugin, "pipe_data");
        BLEND_DATA_KEY = new NamespacedKey(plugin, "blend_data");
        HOOKAH_DATA_KEY = new NamespacedKey(plugin, "hookah_data");
        SPECIAL_TYPE_KEY = new NamespacedKey(plugin, "special_type");
        EXTINGUISHED_KEY = new NamespacedKey(plugin, "extinguished");
    }


    public String formatTypeName(CigaretteType type) {
        String key = "type_" + type.name().toLowerCase(Locale.ROOT);
        String name = lang().getRaw(key);
        return name.equals(key) ? type.getDisplayName() : name;
    }

    public String formatTobaccoTypeName(TobaccoType type) {
        String key = "tobacco_" + type.name().toLowerCase(Locale.ROOT);
        String name = lang().getRaw(key);
        return name.equals(key) ? type.getDisplayName() : name;
    }

    public String formatQualityName(CigaretteQuality quality) {
        return lang().getRaw("quality_" + quality.name().toLowerCase());
    }

    public String formatDyeColor(DyeColor dye) {
        return lang().getRaw("color_" + dye.name().toLowerCase());
    }

    public String formatDryingLevelName(DryingLevel level) {
        if (level == null) return lang().getRaw("word_raw");
        return lang().getRaw("drying_level_" + level.name().toLowerCase());
    }

    private void setCMD(ItemMeta meta, String modelId) {
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(modelId));
        meta.setCustomModelDataComponent(cmd);
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    private Component trans(String key, TagResolver... placeholders) {
        return lang().get(key, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> transLore(String key, TagResolver... placeholders) {
        List<Component> raw = lang().getLore(key, placeholders);
        List<Component> result = new ArrayList<>();
        for (Component c : raw) {
            result.add(c.decoration(TextDecoration.ITALIC, false));
        }
        return result;
    }


    public ItemStack createTobaccoSeeds(TobaccoType type, int amount) {
        ItemStack item = new ItemStack(Material.WHEAT_SEEDS, amount);
        ItemMeta meta = item.getItemMeta();

        String speedKey = type.getBaseGrowthTicks() < 5000 ? "seed_speed_fast"
                : type.getBaseGrowthTicks() < 7000 ? "seed_speed_medium"
                  : type.getBaseGrowthTicks() < 9000 ? "seed_speed_slow"
                    : "seed_speed_very_slow";
        String speed = lang().getRaw(speedKey);

        meta.displayName(trans("item_tobacco_seeds_name",
                Placeholder.unparsed("type", formatTobaccoTypeName(type))));
        List<Component> seedLore = new ArrayList<>(transLore("item_tobacco_seeds_lore",
                Placeholder.unparsed("speed", speed)));
        if (type == TobaccoType.CARAVAN) seedLore.addAll(transLore("item_caravan_seeds_story"));
        meta.lore(seedLore);

        setCMD(meta, "tobacco_seeds_" + type.name().toLowerCase());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "tobacco_seeds");
        pdc.set(TOBACCO_TYPE_KEY, PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createTobaccoSeeds(int amount) {
        return createTobaccoSeeds(TobaccoType.VIRGINIA, amount);
    }

    public boolean isTobaccoSeeds(ItemStack item) {
        return isCigaroItem(item, "tobacco_seeds");
    }

    public ItemStack createTobaccoCropItem(TobaccoType type, int stage) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        setCMD(meta, "tobacco_crop_" + type.name().toLowerCase() + "_stage" + stage);
        item.setItemMeta(meta);
        return item;
    }


    public ItemStack createTobaccoFertilizer(int amount) {
        ItemStack item = new ItemStack(Material.GREEN_DYE, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(trans("item_fertilizer_name"));
        meta.lore(transLore("item_fertilizer_lore"));

        setCMD(meta, "tobacco_fertilizer");

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "tobacco_fertilizer");
        pdc.set(FERTILIZER_USES_KEY, PersistentDataType.INTEGER, 3);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isTobaccoFertilizer(ItemStack item) {
        return isCigaroItem(item, "tobacco_fertilizer");
    }

    public int getFertilizerUses(ItemStack item) {
        if (!isTobaccoFertilizer(item)) return 0;
        Integer uses = item.getItemMeta()
                .getPersistentDataContainer()
                .get(FERTILIZER_USES_KEY, PersistentDataType.INTEGER);
        return uses != null ? uses : 0;
    }

    public ItemStack useFertilizer(ItemStack item) {
        if (!isTobaccoFertilizer(item)) return item;
        int currentUses = getFertilizerUses(item);
        if (currentUses <= 1) return null;

        ItemStack result = item.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer()
                .set(FERTILIZER_USES_KEY, PersistentDataType.INTEGER, currentUses - 1);
        result.setItemMeta(meta);
        return result;
    }


    public ItemStack createDryingTimer() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_drying_timer_name"));
        meta.lore(transLore("item_drying_timer_lore"));
        setCMD(meta, "drying_timer");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "drying_timer");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createLighterOfStats() {
        ItemStack item = new ItemStack(Material.FLINT_AND_STEEL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_lighter_stats_name"));
        meta.lore(transLore("item_lighter_stats_lore"));
        setCMD(meta, "lighter_stats");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "lighter_stats");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createTestStrip(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_test_strip_name"));
        meta.lore(transLore("item_test_strip_lore"));
        setCMD(meta, "test_strip");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "test_strip");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCigarettePaper(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_paper_name"));
        meta.lore(transLore("item_paper_lore"));
        setCMD(meta, "cigarette_paper");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cigarette_paper");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCardboard(int amount) {
        ItemStack item = new ItemStack(Material.BROWN_DYE, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_cardboard_name"));
        meta.lore(transLore("item_cardboard_lore"));
        setCMD(meta, "cardboard");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cardboard");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createFuelPellets(int amount) {
        ItemStack item = new ItemStack(Material.BROWN_MUSHROOM, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_pellets_name"));
        meta.lore(transLore("item_pellets_lore"));
        setCMD(meta, "fuel_pellets");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "fuel_pellets");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createNicotinePatch(int amount) {
        ItemStack item = new ItemStack(Material.LEATHER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_patch_name"));
        meta.lore(transLore("item_patch_lore", Placeholder.unparsed("cooldown",
                String.valueOf(plugin.getConfigManager().getPatchCooldownMinutes()))));
        setCMD(meta, "nicotine_patch");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "nicotine_patch");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCigaretteButt() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_cigarette_butt_name"));
        setCMD(meta, "cigarette_butt");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isDryingTimer(ItemStack item) { return isCigaroItem(item, "drying_timer"); }
    public boolean isLighterOfStats(ItemStack item) { return isCigaroItem(item, "lighter_stats"); }
    public boolean isTestStrip(ItemStack item) { return isCigaroItem(item, "test_strip"); }
    public boolean isCigarettePaper(ItemStack item) { return isCigaroItem(item, "cigarette_paper"); }
    public boolean isCardboard(ItemStack item) { return isCigaroItem(item, "cardboard"); }
    public boolean isFuelPellets(ItemStack item) { return isCigaroItem(item, "fuel_pellets"); }
    public boolean isNicotinePatch(ItemStack item) { return isCigaroItem(item, "nicotine_patch"); }


    public ItemStack createAutoDryingRack() {
        ItemStack item = new ItemStack(Material.TUFF);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_auto_dryer_name"));
        meta.lore(transLore("item_auto_dryer_lore"));
        setCMD(meta, "auto_drying_rack");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "auto_drying_rack");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCuttingMachine() {
        ItemStack item = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_cutting_machine_name"));
        meta.lore(transLore("item_cutting_machine_lore"));
        setCMD(meta, "cutting_machine");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cutting_machine");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRollingPress() {
        ItemStack item = new ItemStack(Material.QUARTZ_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_rolling_press_name"));
        meta.lore(transLore("item_rolling_press_lore"));
        setCMD(meta, "rolling_press");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "rolling_press");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createPackingPress() {
        ItemStack item = new ItemStack(Material.PACKED_MUD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_packing_press_name"));
        meta.lore(transLore("item_packing_press_lore"));
        setCMD(meta, "packing_press");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "packing_press");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDryingRack() {
        ItemStack item = new ItemStack(Material.POLISHED_BLACKSTONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_drying_rack_name"));
        meta.lore(transLore("item_drying_rack_lore"));
        setCMD(meta, "drying_rack");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "drying_rack");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDryingRackDisplayModel() {
        ItemStack item = new ItemStack(Material.POLISHED_BLACKSTONE);
        ItemMeta meta = item.getItemMeta();
        setCMD(meta, "drying_rack_3d");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRollingMachine() {
        ItemStack item = new ItemStack(Material.LODESTONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_rolling_machine_name"));
        meta.lore(transLore("item_rolling_machine_lore"));
        setCMD(meta, "rolling_machine");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "rolling_machine");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRollingMachineDisplayModel() {
        ItemStack item = new ItemStack(Material.LODESTONE);
        ItemMeta meta = item.getItemMeta();
        setCMD(meta, "rolling_machine_3d");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCuttingTable() {
        ItemStack item = new ItemStack(Material.SMOOTH_STONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_cutting_table_name"));
        meta.lore(transLore("item_cutting_table_lore"));
        setCMD(meta, "cutting_table");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cutting_table");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCuttingTableDisplayModel() {
        ItemStack item = new ItemStack(Material.SMOOTH_STONE);
        ItemMeta meta = item.getItemMeta();
        setCMD(meta, "cutting_table_3d");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createPackingMachine() {
        ItemStack item = new ItemStack(Material.POLISHED_DEEPSLATE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_packing_machine_name"));
        meta.lore(transLore("item_packing_machine_lore"));
        setCMD(meta, "packing_machine");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "packing_machine");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createPackingMachineDisplayModel() {
        ItemStack item = new ItemStack(Material.POLISHED_DEEPSLATE);
        ItemMeta meta = item.getItemMeta();
        setCMD(meta, "packing_machine_3d");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createInfuser() {
        ItemStack item = new ItemStack(Material.CALCITE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_infuser_name"));
        meta.lore(transLore("item_infuser_lore"));
        setCMD(meta, "infuser");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "infuser");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createInfuserDisplayModel() {
        ItemStack item = new ItemStack(Material.CALCITE);
        ItemMeta meta = item.getItemMeta();
        setCMD(meta, "infuser_3d");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBlendingTable() {
        ItemStack item = new ItemStack(Material.SMITHING_TABLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_blending_table_name"));
        meta.lore(transLore("item_blending_table_lore"));
        setCMD(meta, "blending_table");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "blending_table");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBlendingTableDisplayModel() {
        ItemStack item = new ItemStack(Material.SMITHING_TABLE);
        ItemMeta meta = item.getItemMeta();
        setCMD(meta, "blending_table_3d");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createHookah() {
        ItemStack item = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(trans("item_hookah_name"));
        meta.lore(transLore("item_hookah_lore"));
        setCMD(meta, "hookah");
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "hookah");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isAutoDryingRack(ItemStack item) { return isCigaroItem(item, "auto_drying_rack"); }
    public boolean isCuttingMachine(ItemStack item) { return isCigaroItem(item, "cutting_machine"); }
    public boolean isRollingPress(ItemStack item) { return isCigaroItem(item, "rolling_press"); }
    public boolean isPackingPress(ItemStack item) { return isCigaroItem(item, "packing_press"); }
    public boolean isDryingRack(ItemStack item) { return isCigaroItem(item, "drying_rack"); }
    public boolean isRollingMachine(ItemStack item) { return isCigaroItem(item, "rolling_machine"); }
    public boolean isCuttingTable(ItemStack item) { return isCigaroItem(item, "cutting_table"); }
    public boolean isPackingMachine(ItemStack item) { return isCigaroItem(item, "packing_machine"); }
    public boolean isInfuser(ItemStack item) { return isCigaroItem(item, "infuser"); }
    public boolean isBlendingTable(ItemStack item) { return isCigaroItem(item, "blending_table"); }
    public boolean isHookah(ItemStack item) { return isCigaroItem(item, "hookah"); }


    public ItemStack createTobaccoLeaf(TobaccoType tobaccoType, boolean elite, int dryingSeconds) {
        ItemStack item = new ItemStack(Material.KELP);
        ItemMeta meta = item.getItemMeta();

        DryingLevel level = plugin.getConfigManager().getDryingLevel(dryingSeconds);
        String prefix = elite ? lang().getRaw("word_elite") + " " : "";
        TextColor nameColor = elite ? CigaroColors.GOLD : CigaroColors.DARK;

        meta.displayName(trans("item_leaf_name",
                Placeholder.styling("color", nameColor),
                Placeholder.unparsed("prefix", prefix),
                Placeholder.unparsed("type", formatTobaccoTypeName(tobaccoType))));

        String eliteText = elite ? lang().getRaw("word_elite") : "";
        List<Component> leafLore = new ArrayList<>(transLore("item_leaf_lore",
                Placeholder.unparsed("type", formatTobaccoTypeName(tobaccoType)),
                Placeholder.unparsed("elite_text", eliteText),
                Placeholder.styling("level_color", level.getColor()),
                Placeholder.unparsed("level_name", formatDryingLevelName(level))));
        if (tobaccoType == TobaccoType.CARAVAN) leafLore.addAll(transLore("item_caravan_leaf_story"));
        meta.lore(leafLore);

        boolean isDried = level != DryingLevel.RAW && dryingSeconds > 0;

        String modelId;
        if (isDried) {
            modelId = "tobacco_leaf_dried_" + tobaccoType.name().toLowerCase();
        } else {
            modelId = elite
                    ? "elite_leaf_" + tobaccoType.name().toLowerCase()
                    : "tobacco_leaf_" + tobaccoType.name().toLowerCase();
        }
        setCMD(meta, modelId);

        String pdcId = elite ? "elite_leaf" : "tobacco_leaf";
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, pdcId);
        pdc.set(TOBACCO_TYPE_KEY, PersistentDataType.STRING, tobaccoType.name());
        pdc.set(DRYING_SECONDS_KEY, PersistentDataType.INTEGER, dryingSeconds);
        pdc.set(DRYING_QUALITY_KEY, PersistentDataType.STRING, level.name());

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createTobaccoLeaf(TobaccoType tobaccoType, boolean elite) {
        return createTobaccoLeaf(tobaccoType, elite, 0);
    }

    public ItemStack createTobaccoLeaf(boolean elite) {
        return createTobaccoLeaf(TobaccoType.VIRGINIA, elite, 0);
    }

    public boolean isTobaccoLeaf(ItemStack item) {
        return isCigaroItem(item, "tobacco_leaf") || isCigaroItem(item, "elite_leaf");
    }

    public boolean isEliteLeaf(ItemStack item) {
        return isCigaroItem(item, "elite_leaf");
    }

    public boolean isDriedTobaccoLeaf(ItemStack item) {
        if (!isTobaccoLeaf(item)) return false;
        Integer dryingSec = item.getItemMeta().getPersistentDataContainer()
                .get(DRYING_SECONDS_KEY, PersistentDataType.INTEGER);
        return dryingSec != null && dryingSec > 0;
    }

    public int getDryingSeconds(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(DRYING_SECONDS_KEY, PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }

    public DryingLevel getDryingLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return DryingLevel.RAW;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(DRYING_QUALITY_KEY, PersistentDataType.STRING);
        if (val == null) return DryingLevel.RAW;
        try { return DryingLevel.valueOf(val); }
        catch (IllegalArgumentException e) { return DryingLevel.RAW; }
    }


    public ItemStack createCutTobacco(TobaccoType tobaccoType, boolean elite,
                                      DryingLevel dryingLevel, int dryingSeconds,
                                      int cutQuality) {
        ItemStack item = new ItemStack(Material.DRIED_KELP);
        ItemMeta meta = item.getItemMeta();

        String cutLabelKey = cutQuality >= 2 ? "cut_quality_perfect"
                : cutQuality >= 1 ? "cut_quality_good"
                  : cutQuality >= 0 ? "cut_quality_rough" : "cut_quality_bad";
        String cutLabel = lang().getRaw(cutLabelKey);
        TextColor cutColor = cutQuality >= 2 ? CigaroColors.PERFECT
                : cutQuality >= 1 ? CigaroColors.GOLDEN
                  : cutQuality >= 0 ? CigaroColors.SMOKE : CigaroColors.EMBER;

        TextColor nameColor = elite ? CigaroColors.GOLD : CigaroColors.DARK;
        String prefix = elite ? lang().getRaw("word_elite") + " " : "";
        String eliteText = elite ? lang().getRaw("word_elite") : "";

        meta.displayName(trans("item_cut_tobacco_name",
                Placeholder.styling("color", nameColor),
                Placeholder.unparsed("prefix", prefix),
                Placeholder.unparsed("type", formatTobaccoTypeName(tobaccoType))));

        TextColor levelColor = dryingLevel != null ? dryingLevel.getColor() : CigaroColors.ASH;
        String levelName = formatDryingLevelName(dryingLevel);

        meta.lore(transLore("item_cut_tobacco_lore",
                Placeholder.unparsed("type", formatTobaccoTypeName(tobaccoType)),
                Placeholder.styling("level_color", levelColor),
                Placeholder.unparsed("level_name", levelName),
                Placeholder.styling("cut_color", cutColor),
                Placeholder.unparsed("cut_label", cutLabel),
                Placeholder.unparsed("elite_text", eliteText)));

        String qualityId = cutQuality >= 2 ? "perfect" : cutQuality >= 1 ? "good" : cutQuality >= 0 ? "rough" : "bad";
        setCMD(meta, "cut_tobacco_" + tobaccoType.name().toLowerCase() + "_" + qualityId);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cut_tobacco");
        pdc.set(TOBACCO_TYPE_KEY, PersistentDataType.STRING, tobaccoType.name());
        pdc.set(DRYING_QUALITY_KEY, PersistentDataType.STRING,
                dryingLevel != null ? dryingLevel.name() : DryingLevel.RAW.name());
        pdc.set(DRYING_SECONDS_KEY, PersistentDataType.INTEGER, dryingSeconds);
        pdc.set(CUT_QUALITY_KEY, PersistentDataType.INTEGER, cutQuality);
        pdc.set(ELITE_LEAF_KEY, PersistentDataType.INTEGER, elite ? 1 : 0);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isCutTobacco(ItemStack item) {
        return isCigaroItem(item, "cut_tobacco") || isCigaroItem(item, "blended_cut_tobacco");
    }

    public int getCutQuality(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(CUT_QUALITY_KEY, PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }

    public boolean isCutElite(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(ELITE_LEAF_KEY, PersistentDataType.INTEGER);
        return val != null && val == 1;
    }

    public DryingQuality getDryingQuality(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(DRYING_QUALITY_KEY, PersistentDataType.STRING);
        if (val == null) return null;
        try { return DryingQuality.valueOf(val); }
        catch (IllegalArgumentException e) { return null; }
    }


    public ItemStack createFlavorBottle(CigaretteType type) {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta pm) {
            pm.setBasePotionType(null);
        }

        meta.displayName(trans("item_flavor_bottle_name",
                Placeholder.styling("flavor_color", type.getColor()),
                Placeholder.unparsed("type", formatTypeName(type))));
        meta.lore(transLore("item_flavor_bottle_lore"));

        setCMD(meta, "flavor_bottle_" + type.name().toLowerCase());
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY,
                PersistentDataType.STRING, "flavor_" + type.name().toLowerCase());
        meta.getPersistentDataContainer().set(CIGARETTE_TYPE_KEY,
                PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRawIngredient(CigaretteType type) {
        ItemStack item = new ItemStack(type.getRawMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(trans("item_raw_ingredient_name",
                Placeholder.styling("flavor_color", type.getColor()),
                Placeholder.unparsed("type", formatTypeName(type))));
        meta.lore(transLore("item_raw_ingredient_lore"));

        setCMD(meta, "raw_ingredient_" + type.name().toLowerCase());
        meta.getPersistentDataContainer().set(CIGARO_ITEM_KEY,
                PersistentDataType.STRING, "raw_ingredient_" + type.name().toLowerCase());

        item.setItemMeta(meta);
        return item;
    }

    public boolean isFlavorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(CIGARO_ITEM_KEY, PersistentDataType.STRING);
        return val != null && val.startsWith("flavor_");
    }

    public CigaretteType getFlavorType(ItemStack item) {
        if (!isFlavorItem(item)) return null;
        PersistentDataContainer pdc = item.getItemMeta()
                .getPersistentDataContainer();

        String raw = pdc.get(CIGARETTE_TYPE_KEY, PersistentDataType.STRING);
        if (raw == null) {
            raw = pdc.get(CIGARO_ITEM_KEY, PersistentDataType.STRING);
        }
        if (raw == null) return null;

        String typeName = raw.startsWith("flavor_")
                ? raw.substring("flavor_".length())
                : raw;
        try {
            return CigaretteType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isRawIngredient(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(CIGARO_ITEM_KEY, PersistentDataType.STRING);
        return val != null && val.startsWith("raw_ingredient_");
    }

    public CigaretteType getRawIngredientType(ItemStack item) {
        if (!isRawIngredient(item)) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(CIGARO_ITEM_KEY, PersistentDataType.STRING);
        if (val == null) return null;
        String typeName = val.replace("raw_ingredient_", "").toUpperCase();
        try { return CigaretteType.valueOf(typeName); }
        catch (IllegalArgumentException e) { return null; }
    }

    public Material getRawIngredientMaterial(CigaretteType type) {
        return type.getRawMaterial();
    }


    public ItemStack createRawBatch(CigaretteType type, boolean premiumTobacco, boolean eliteLeaf) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        TextColor color = CigaroColors.forType(type);
        String qualityLabel = (premiumTobacco && eliteLeaf) ? "★★ "
                : (premiumTobacco || eliteLeaf) ? "★ " : "";

        meta.displayName(trans("item_raw_batch_name",
                Placeholder.styling("color", color),
                Placeholder.unparsed("prefix", qualityLabel),
                Placeholder.unparsed("type", formatTypeName(type))));

        List<Component> lore = new ArrayList<>();
        if (premiumTobacco) {
            lore.add(trans("item_raw_batch_premium"));
        }
        if (eliteLeaf) {
            lore.add(trans("item_raw_batch_elite"));
        }
        lore.add(trans("item_raw_batch_hint"));
        meta.lore(lore);

        setCMD(meta, "raw_batch_" + type.name().toLowerCase());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "raw_batch");
        pdc.set(CIGARETTE_TYPE_KEY, PersistentDataType.STRING, type.name());
        pdc.set(CIGARETTE_QUALITY_KEY, PersistentDataType.STRING, CigaretteQuality.STANDARD.name());
        int standardPuffs = CigaretteQuality.STANDARD.getPuffs();
        pdc.set(PUFFS_LEFT_KEY, PersistentDataType.INTEGER, standardPuffs);
        pdc.set(PUFFS_MAX_KEY, PersistentDataType.INTEGER, standardPuffs);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRawBatch(CigaretteType type) {
        return createRawBatch(type, false, false);
    }

    public boolean isRawBatch(ItemStack item) { return isCigaroItem(item, "raw_batch"); }

    public boolean isBatchPremiumTobacco(ItemStack item) {
        if (!isRawBatch(item)) return false;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(PREMIUM_TOBACCO_KEY, PersistentDataType.INTEGER);
        return val != null && val == 1;
    }

    public boolean isBatchEliteLeaf(ItemStack item) {
        if (!isRawBatch(item)) return false;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(ELITE_LEAF_KEY, PersistentDataType.INTEGER);
        return val != null && val == 1;
    }


    public ItemStack createCigarette(CigaretteType type, CigaretteQuality quality, int amount) {
        int maxPuffs = quality.getPuffs();
        return createCigarette(type, quality, amount, maxPuffs, maxPuffs, FlavorHarmony.NEUTRAL);
    }

    public ItemStack createCigarette(CigaretteType type, CigaretteQuality quality,
                                     int amount, int puffsLeft, int puffsMax) {
        return createCigarette(type, quality, amount, puffsLeft, puffsMax, FlavorHarmony.NEUTRAL);
    }

    public ItemStack createCigarette(CigaretteType type, CigaretteQuality quality,
                                     int amount, int puffsLeft, int puffsMax,
                                     FlavorHarmony harmony) {
        boolean isUsed = puffsLeft < puffsMax;
        if (isUsed) amount = 1;

        ItemStack item = new ItemStack(Material.STICK, amount);
        ItemMeta meta = item.getItemMeta();

        String qualityName = formatQualityName(quality);
        String typeName = formatTypeName(type);
        TextColor qualityColor = CigaroColors.forQuality(quality);
        TextColor typeColor = CigaroColors.forType(type);

        meta.displayName(trans("item_cigarette_name",
                Placeholder.styling("quality_color", qualityColor),
                Placeholder.unparsed("quality", qualityName),
                Placeholder.unparsed("type", typeName)));

        List<Component> lore = new ArrayList<>();

        lore.add(Component.text(typeName).color(typeColor)
                .append(Component.text(" · ").color(CigaroColors.ASH))
                .append(Component.text(qualityName).color(qualityColor))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        String puffBar = buildPuffLore(puffsLeft, puffsMax);
        TextColor puffColor;
        String statusKey;
        if (puffsLeft == puffsMax) {
            puffColor = CigaroColors.SUCCESS;
            statusKey = "cig_status_fresh";
        } else if (puffsLeft > puffsMax / 2) {
            puffColor = CigaroColors.WARNING;
            statusKey = "cig_status_half";
        } else if (puffsLeft > 1) {
            puffColor = CigaroColors.EMBER;
            statusKey = "cig_status_almost_done";
        } else {
            puffColor = CigaroColors.ERROR;
            statusKey = "cig_status_last_drag";
        }

        lore.add(Component.text(puffBar).color(puffColor).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(lang().getRaw(statusKey) + " (" + puffsLeft + "/" + puffsMax + ")")
                .color(CigaroColors.ASH).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(trans(isUsed ? "item_cigarette_lore_used" : "item_cigarette_lore_fresh"));

        meta.lore(lore);

        String smokeState = getSmokeState(puffsLeft, puffsMax);
        setCMD(meta, "cigarette_" + type.name().toLowerCase() + "_" + smokeState);
        meta.setMaxStackSize(isUsed ? 1 : 10);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cigarette");
        pdc.set(CIGARETTE_TYPE_KEY, PersistentDataType.STRING, type.name());
        pdc.set(CIGARETTE_QUALITY_KEY, PersistentDataType.STRING, quality.name());
        pdc.set(PUFFS_LEFT_KEY, PersistentDataType.INTEGER, puffsLeft);
        pdc.set(PUFFS_MAX_KEY, PersistentDataType.INTEGER, puffsMax);
        if (harmony != null && harmony != FlavorHarmony.NEUTRAL) {
            pdc.set(FLAVOR_HARMONY_KEY, PersistentDataType.STRING, harmony.name());
        }

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCigar(CigaretteType type, CigaretteQuality quality, int amount) {
        return createCigar(type, quality, amount, FlavorHarmony.NEUTRAL);
    }

    public ItemStack createCigar(CigaretteType type, CigaretteQuality quality,
                                 int amount, FlavorHarmony harmony) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD, amount);
        ItemMeta meta = item.getItemMeta();

        String qualityName = formatQualityName(quality);
        String typeName = formatTypeName(type);
        TextColor qualityColor = CigaroColors.forQuality(quality);
        TextColor typeColor = CigaroColors.forType(type);

        meta.displayName(trans("item_cigar_name",
                Placeholder.styling("quality_color", qualityColor),
                Placeholder.unparsed("quality", qualityName),
                Placeholder.unparsed("type", typeName)));

        meta.lore(transLore("item_cigar_lore",
                Placeholder.styling("type_color", typeColor),
                Placeholder.unparsed("type", typeName),
                Placeholder.styling("quality_color", qualityColor),
                Placeholder.unparsed("quality", qualityName)));

        setCMD(meta, "cigar_unlit");
        meta.setMaxStackSize(8);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cigar");
        pdc.set(SMOKABLE_TYPE_KEY, PersistentDataType.STRING, SmokableType.CIGAR.getId());
        pdc.set(CIGARETTE_TYPE_KEY, PersistentDataType.STRING, type.name());
        pdc.set(CIGARETTE_QUALITY_KEY, PersistentDataType.STRING, quality.name());
        if (harmony != null && harmony != FlavorHarmony.NEUTRAL) {
            pdc.set(FLAVOR_HARMONY_KEY, PersistentDataType.STRING, harmony.name());
        }

        item.setItemMeta(meta);
        return item;
    }

    public boolean isCigar(ItemStack item) {
        return isCigaroItem(item, "cigar");
    }


    private Component blendLoreLine(BlendData blend) {
        int pct = (int) Math.round(blend.getPrimaryRatio() * 100);
        return Component.text(pct + "% " + formatTypeName(blend.getPrimary()))
                .color(CigaroColors.forType(blend.getPrimary()))
                .append(Component.text(" + ").color(CigaroColors.ASH))
                .append(Component.text((100 - pct) + "% " + formatTypeName(blend.getSecondary()))
                        .color(CigaroColors.forType(blend.getSecondary())))
                .decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack createBlendedCigarette(BlendData blend, CigaretteQuality quality,
                                            int amount, int puffsLeft, int puffsMax) {
        return createBlendedCigarette(blend, quality, amount, puffsLeft, puffsMax,
                FlavorHarmony.NEUTRAL);
    }

    public ItemStack createBlendedCigarette(BlendData blend, CigaretteQuality quality,
                                            int amount, int puffsLeft, int puffsMax,
                                            FlavorHarmony harmony) {
        ItemStack item = createCigarette(blend.getPrimary(), quality, amount, puffsLeft, puffsMax,
                harmony);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(trans("item_blended_cigarette_name",
                Placeholder.styling("quality_color", CigaroColors.forQuality(quality)),
                Placeholder.unparsed("quality", formatQualityName(quality))));

        List<Component> lore = new ArrayList<>(Objects.requireNonNullElseGet(meta.lore(), ArrayList::new));
        lore.add(0, blendLoreLine(blend));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(BLEND_DATA_KEY, PersistentDataType.STRING, blend.serialize());

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBlendedCigar(BlendData blend, CigaretteQuality quality, int amount) {
        return createBlendedCigar(blend, quality, amount, FlavorHarmony.NEUTRAL);
    }

    public ItemStack createBlendedCigar(BlendData blend, CigaretteQuality quality, int amount,
                                        FlavorHarmony harmony) {
        ItemStack item = createCigar(blend.getPrimary(), quality, amount, harmony);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(trans("item_blended_cigar_name",
                Placeholder.styling("quality_color", CigaroColors.forQuality(quality)),
                Placeholder.unparsed("quality", formatQualityName(quality))));

        List<Component> lore = new ArrayList<>(Objects.requireNonNullElseGet(meta.lore(), ArrayList::new));
        lore.add(0, blendLoreLine(blend));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(BLEND_DATA_KEY, PersistentDataType.STRING, blend.serialize());

        item.setItemMeta(meta);
        return item;
    }




    public ItemStack createSmokescreenCigarette(int amount) {
        int maxPuffs = getMaxPuffs(CigaretteQuality.STANDARD);
        return createSmokescreenCigarette(amount, maxPuffs, maxPuffs);
    }

    public ItemStack createSmokescreenCigarette(int amount, int puffsLeft, int puffsMax) {
        boolean isUsed = puffsLeft < puffsMax;
        if (isUsed) amount = 1;

        ItemStack item = new ItemStack(Material.STICK, amount);
        ItemMeta meta = item.getItemMeta();

        String puffBar = buildPuffLore(puffsLeft, puffsMax);
        TextColor puffColor = puffsLeft == puffsMax ? CigaroColors.SUCCESS
                : (puffsLeft > 1 ? CigaroColors.WARNING : CigaroColors.ERROR);

        String statusKey = puffsLeft == puffsMax ? "cig_status_fresh"
                : (puffsLeft > 1 ? "cig_status_smoldering" : "cig_status_last_drag");
        String statusText = lang().getRaw(statusKey);

        meta.displayName(trans("item_smokescreen_name"));
        meta.lore(transLore("item_smokescreen_lore",
                Placeholder.styling("puff_color", puffColor),
                Placeholder.unparsed("puff_bar", puffBar),
                Placeholder.unparsed("status", statusText),
                Placeholder.unparsed("left", String.valueOf(puffsLeft)),
                Placeholder.unparsed("max", String.valueOf(puffsMax))));

        String smokeState = getSmokeState(puffsLeft, puffsMax);
        setCMD(meta, "smokescreen_" + smokeState);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cigarette");
        pdc.set(CIGARETTE_TYPE_KEY, PersistentDataType.STRING, CigaretteType.SHADOW.name());
        pdc.set(CIGARETTE_QUALITY_KEY, PersistentDataType.STRING, CigaretteQuality.STANDARD.name());
        pdc.set(PUFFS_LEFT_KEY, PersistentDataType.INTEGER, puffsLeft);
        pdc.set(PUFFS_MAX_KEY, PersistentDataType.INTEGER, puffsMax);
        pdc.set(SPECIAL_TYPE_KEY, PersistentDataType.STRING, "smokescreen");

        item.setItemMeta(meta);
        return item;
    }


    public ItemStack createPipe() {
        PipeData pipeData = new PipeData(
                PipeData.DEFAULT_MAX_DURABILITY,
                PipeData.DEFAULT_MAX_DURABILITY);
        return createPipeItem(pipeData);
    }

    public ItemStack createPipeItem(PipeData data) {
        ItemStack item = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = item.getItemMeta();

        boolean loaded = data.isLoaded();
        boolean broken = data.isBroken();

        TextColor nameColor = broken ? CigaroColors.ASH
                : loaded ? CigaroColors.EMBER : CigaroColors.DARK;

        meta.displayName(trans("item_pipe_name",
                Placeholder.styling("color", nameColor)));

        List<Component> lore = new ArrayList<>();

        if (loaded) {
            String contentName;
            TextColor contentColor;
            if (data.isBlend()) {
                BlendData blend = data.getBlendData();
                int pct = (int) Math.round(blend.getPrimaryRatio() * 100);
                contentName = pct + "% " + formatTypeName(blend.getPrimary())
                        + " / " + (100 - pct) + "% " + formatTypeName(blend.getSecondary());
                contentColor = CigaroColors.GOLD;
            } else {
                contentName = formatTypeName(data.getLoadedType());
                contentColor = CigaroColors.forType(data.getLoadedType());
            }
            lore.add(Component.text(contentName).color(contentColor)
                    .decoration(TextDecoration.ITALIC, false));

            lore.add(trans("item_pipe_charges",
                    Placeholder.unparsed("left", String.valueOf(data.getChargesLeft())),
                    Placeholder.unparsed("max", String.valueOf(data.getMaxCharges()))));
        } else {
            String statusKey = broken ? "item_pipe_status_broken" : "item_pipe_status_empty";
            lore.add(trans(statusKey));
        }

        lore.add(Component.empty());

        int durPct = (int) ((double) data.getDurability() / data.getMaxDurability() * 100);
        TextColor durColor = durPct > 50 ? CigaroColors.SUCCESS
                : durPct > 20 ? CigaroColors.WARNING : CigaroColors.ERROR;

        lore.add(trans("item_pipe_durability",
                TagResolver.resolver("color", Tag.styling(durColor)),
                Placeholder.unparsed("current", String.valueOf(data.getDurability())),
                Placeholder.unparsed("max", String.valueOf(data.getMaxDurability()))));

        if (broken) {
            lore.addAll(transLore("item_pipe_lore_broken"));
        } else if (!loaded) {
            lore.addAll(transLore("item_pipe_lore_empty"));
        } else {
            lore.addAll(transLore("item_pipe_lore_loaded"));
        }

        meta.lore(lore);
        String cmdState = "pipe_empty";
        if (loaded) {
            cmdState = (data.getChargesLeft() < data.getMaxCharges()) ? "pipe_burning" : "pipe_loaded";
        }
        setCMD(meta, cmdState);
        meta.setMaxStackSize(1);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "pipe");
        pdc.set(SMOKABLE_TYPE_KEY, PersistentDataType.STRING, SmokableType.PIPE.getId());
        pdc.set(PIPE_DATA_KEY, PersistentDataType.STRING, data.serialize());

        item.setItemMeta(meta);
        return item;
    }

    public boolean isPipe(ItemStack item) { return isCigaroItem(item, "pipe"); }

    public PipeData getPipeData(ItemStack item) {
        if (!isPipe(item)) return null;
        String data = item.getItemMeta().getPersistentDataContainer()
                .get(PIPE_DATA_KEY, PersistentDataType.STRING);
        return PipeData.deserialize(data);
    }

    public int getPipeCharges(int cutQuality) {
        if (cutQuality >= 2) return 5;
        if (cutQuality >= 1) return 4;
        return 3;
    }


    public ItemStack createBlendedCutTobacco(BlendData blend, boolean elite,
                                             DryingLevel dryingLevel, int dryingSeconds,
                                             int cutQuality, int amount) {
        ItemStack item = new ItemStack(Material.DRIED_KELP, amount);
        ItemMeta meta = item.getItemMeta();

        int pct = (int) Math.round(blend.getPrimaryRatio() * 100);
        String primaryName = formatTypeName(blend.getPrimary());
        String secondaryName = formatTypeName(blend.getSecondary());

        TextColor nameColor = elite ? CigaroColors.GOLD : CigaroColors.DARK;
        String prefix = elite ? lang().getRaw("word_elite") + " " : "";

        meta.displayName(trans("item_blended_cut_name",
                Placeholder.styling("color", nameColor),
                Placeholder.unparsed("prefix", prefix)));

        List<Component> lore = new ArrayList<>();

        lore.add(Component.text(pct + "% " + primaryName)
                .color(CigaroColors.forType(blend.getPrimary()))
                .append(Component.text(" + ").color(CigaroColors.ASH))
                .append(Component.text((100 - pct) + "% " + secondaryName)
                        .color(CigaroColors.forType(blend.getSecondary())))
                .decoration(TextDecoration.ITALIC, false));

        if (dryingLevel != null) {
            lore.add(Component.text(formatDryingLevelName(dryingLevel))
                    .color(dryingLevel.getColor())
                    .decoration(TextDecoration.ITALIC, false));
        }

        String cutLabelKey = cutQuality >= 2 ? "cut_quality_perfect"
                : cutQuality >= 1 ? "cut_quality_good"
                  : cutQuality >= 0 ? "cut_quality_rough" : "cut_quality_bad";
        String cutLabel = lang().getRaw(cutLabelKey);
        TextColor cutColor = cutQuality >= 2 ? CigaroColors.PERFECT
                : cutQuality >= 1 ? CigaroColors.GOLDEN
                  : cutQuality >= 0 ? CigaroColors.SMOKE : CigaroColors.EMBER;
        lore.add(Component.text(cutLabel).color(cutColor)
                .decoration(TextDecoration.ITALIC, false));

        if (elite) {
            lore.add(Component.text(lang().getRaw("word_elite")).color(CigaroColors.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(trans("item_blended_cut_hint"));

        meta.lore(lore);
        setCMD(meta, "blended_cut_tobacco_" + (cutQuality >= 2 ? "perfect" : cutQuality >= 1 ? "good" : cutQuality >= 0 ? "rough" : "bad"));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "blended_cut_tobacco");
        pdc.set(BLEND_DATA_KEY, PersistentDataType.STRING, blend.serialize());
        pdc.set(DRYING_QUALITY_KEY, PersistentDataType.STRING,
                dryingLevel != null ? dryingLevel.name() : DryingLevel.RAW.name());
        pdc.set(DRYING_SECONDS_KEY, PersistentDataType.INTEGER, dryingSeconds);
        pdc.set(CUT_QUALITY_KEY, PersistentDataType.INTEGER, cutQuality);
        pdc.set(ELITE_LEAF_KEY, PersistentDataType.INTEGER, elite ? 1 : 0);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isBlendedCutTobacco(ItemStack item) {
        return isCigaroItem(item, "blended_cut_tobacco");
    }

    public BlendData getBlendData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String data = item.getItemMeta().getPersistentDataContainer()
                .get(BLEND_DATA_KEY, PersistentDataType.STRING);
        return BlendData.deserialize(data);
    }


    public ItemStack createCigarettePack(List<ItemStack> cigarettes, DyeColor dyeColor) {
        ItemStack item = new ItemStack(Material.BOOK);
        updatePackMeta(item, cigarettes, dyeColor);
        return item;
    }

    public void updatePackMeta(ItemStack item, List<ItemStack> cigarettes, DyeColor dyeColor) {
        ItemMeta meta = item.getItemMeta();

        String colorName = formatDyeColor(dyeColor);
        TextColor color = dyeToTextColor(dyeColor);

        meta.displayName(trans("item_pack_name",
                Placeholder.styling("color", color),
                Placeholder.unparsed("color_name", colorName)));

        List<Component> lore = new ArrayList<>();

        lore.add(Component.text(cigarettes.size() + "/20")
                .color(cigarettes.isEmpty() ? CigaroColors.ASH : CigaroColors.SMOKE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (cigarettes.isEmpty()) {
            lore.addAll(transLore("item_pack_lore_empty"));
        } else {
            Map<String, Integer> contents = new LinkedHashMap<>();
            for (ItemStack cig : cigarettes) {
                CigaretteType type = getCigaretteType(cig);
                CigaretteQuality quality = getCigaretteQuality(cig);
                BlendData blend = getBlendData(cig);

                String cName;
                if (blend != null) {
                    int pct = (int) Math.round(blend.getPrimaryRatio() * 100);
                    cName = pct + "% " + formatTypeName(blend.getPrimary())
                            + " / " + (100 - pct) + "% " + formatTypeName(blend.getSecondary());
                } else if (type != null && quality != null) {
                    cName = formatTypeName(type) + " " + formatQualityName(quality);
                } else {
                    cName = lang().getRaw("word_unknown_cigarette");
                }
                contents.merge(cName, 1, Integer::sum);
            }
            for (var entry : contents.entrySet()) {
                lore.add(Component.text("  " + entry.getValue() + "x " + entry.getKey())
                        .color(CigaroColors.SMOKE)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.addAll(transLore("item_pack_lore_controls"));

        meta.lore(lore);
        setCMD(meta, "cigarettepack_" + dyeColor.name().toLowerCase());
        meta.setMaxStackSize(1);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, "cigarette_pack");
        pdc.set(PACK_COLOR_KEY, PersistentDataType.STRING, dyeColor.name());
        pdc.set(PACK_COUNT_KEY, PersistentDataType.INTEGER, cigarettes.size());
        pdc.set(PACK_CIGARETTES_KEY, PersistentDataType.STRING, serializeCigarettes(cigarettes));

        item.setItemMeta(meta);
    }

    public boolean isCigarettePack(ItemStack item) { return isCigaroItem(item, "cigarette_pack"); }

    public DyeColor getPackColor(ItemStack item) {
        if (!isCigarettePack(item)) return DyeColor.WHITE;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(PACK_COLOR_KEY, PersistentDataType.STRING);
        if (val == null) return DyeColor.WHITE;
        try { return DyeColor.valueOf(val); }
        catch (IllegalArgumentException e) { return DyeColor.WHITE; }
    }

    public List<ItemStack> getPackCigarettes(ItemStack item) {
        if (!isCigarettePack(item)) return new ArrayList<>();
        String data = item.getItemMeta().getPersistentDataContainer()
                .get(PACK_CIGARETTES_KEY, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return new ArrayList<>();
        return deserializeCigarettes(data);
    }

    public int getPackCount(ItemStack item) {
        if (!isCigarettePack(item)) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(PACK_COUNT_KEY, PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }


    private String serializeCigarettes(List<ItemStack> cigarettes) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack cig : cigarettes) {
            CigaretteType type = getCigaretteType(cig);
            CigaretteQuality quality = getCigaretteQuality(cig);
            int puffsLeft = getPuffsLeft(cig);
            int puffsMax = getPuffsMax(cig);

            String special = getSpecialType(cig);
            if (special == null) special = "none";

            BlendData blend = getBlendData(cig);
            String blendField = blend != null ? blend.serialize().replace(':', '+') : "none";

            if (!sb.isEmpty()) sb.append(";");

            sb.append(type != null ? type.name() : "MINT").append(":")
                    .append(quality != null ? quality.name() : "STANDARD").append(":")
                    .append(puffsLeft).append(":")
                    .append(puffsMax).append(":")
                    .append(special).append(":")
                    .append(blendField).append(":")
                    .append(getFlavorHarmony(cig).name()).append(":t=")
                    .append(getProductTobacco(cig) != null ? getProductTobacco(cig).name() : "none");
        }
        return sb.toString();
    }

    private List<ItemStack> deserializeCigarettes(String data) {
        List<ItemStack> list = new ArrayList<>();
        if (data == null || data.isEmpty()) return list;
        String[] entries = data.split(";");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length < 4) continue;
            try {
                CigaretteType type = CigaretteType.valueOf(parts[0]);
                CigaretteQuality quality = CigaretteQuality.valueOf(parts[1]);
                int puffsLeft = Integer.parseInt(parts[2]);
                int puffsMax = Integer.parseInt(parts[3]);
                String special = (parts.length >= 5) ? parts[4] : "none";

                boolean legacy = parts.length >= 8 && !parts[7].startsWith("t=");
                FlavorHarmony harmony = (!legacy && parts.length >= 7)
                        ? FlavorHarmony.fromName(parts[6])
                        : FlavorHarmony.NEUTRAL;

                BlendData blend = legacy
                        ? parseLegacyBlend(parts[5])
                        : null;
                if (!legacy && parts.length >= 6 && !parts[5].equals("none")) {
                    blend = BlendData.deserialize(parts[5].replace('+', ':'));
                }

                ItemStack cig;
                if ("smokescreen".equals(special)) {
                    cig = createSmokescreenCigarette(1, puffsLeft, puffsMax);
                } else if (blend != null) {
                    cig = createBlendedCigarette(blend, quality, 1, puffsLeft, puffsMax, harmony);
                } else {
                    cig = createCigarette(type, quality, 1, puffsLeft, puffsMax, harmony);
                }
                if (!legacy && parts.length >= 8 && parts[7].startsWith("t=")) {
                    String origin = parts[7].substring(2);
                    if (!origin.equals("none")) setProductTobacco(cig, TobaccoType.valueOf(origin));
                }
                list.add(cig);
            } catch (Exception ignored) {}
        }
        return list;
    }

    private BlendData parseLegacyBlend(String genesField) {
        if (genesField == null || genesField.isEmpty() || genesField.equals("none")) {
            return null;
        }
        String genes = genesField.replace("|", ":").replace("/", ";");
        String[] entries = genes.split(";");
        if (entries.length != 2) return null;

        try {
            String[] first = entries[0].split(":");
            String[] second = entries[1].split(":");
            if (first.length != 2 || second.length != 2) return null;

            CigaretteType primary = CigaretteType.valueOf(first[0].trim().toUpperCase(Locale.ROOT));
            CigaretteType secondary = CigaretteType.valueOf(second[0].trim().toUpperCase(Locale.ROOT));
            double primaryRatio = Double.parseDouble(first[1].trim().replace(",", "."));
            double secondaryRatio = Double.parseDouble(second[1].trim().replace(",", "."));
            if (primaryRatio <= 0 || secondaryRatio <= 0) return null;

            double total = primaryRatio + secondaryRatio;
            return new BlendData(primary, secondary, primaryRatio / total);
        } catch (Exception ignored) {
            return null;
        }
    }


    public ItemStack createDriedTobacco(boolean premium, DryingQuality dryQuality) {
        ItemStack item = new ItemStack(Material.DRIED_KELP);
        ItemMeta meta = item.getItemMeta();

        TextColor nameColor = premium ? CigaroColors.GOLD : CigaroColors.DARK;
        String prefix = premium ? lang().getRaw("word_premium") + " " : "";

        meta.displayName(trans("item_dried_tobacco_name",
                Placeholder.styling("color", nameColor),
                Placeholder.unparsed("prefix", prefix)));

        List<Component> lore = new ArrayList<>();
        if (dryQuality != null) {
            TextColor dryColor = switch (dryQuality) {
                case PERFECT -> CigaroColors.PERFECT;
                case UNDERDRIED -> CigaroColors.WARNING;
                case OVERDRIED -> CigaroColors.GOLDEN;
                case RUINED -> CigaroColors.ERROR;
            };
            String drySymbol = switch (dryQuality) {
                case PERFECT -> "✔ ";
                case UNDERDRIED -> "△ ";
                case OVERDRIED -> "▽ ";
                case RUINED -> "✘ ";
            };
            String qualityName = lang().getRaw("drying_quality_" + dryQuality.name().toLowerCase());

            lore.add(trans("item_dried_tobacco_lore",
                    Placeholder.styling("quality_color", dryColor),
                    Placeholder.unparsed("symbol", drySymbol),
                    Placeholder.unparsed("quality_name", qualityName)));
        }
        meta.lore(lore);

        String pdcId = premium ? "premium_dried_tobacco" : "dried_tobacco";
        setCMD(meta, pdcId);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CIGARO_ITEM_KEY, PersistentDataType.STRING, pdcId);
        if (dryQuality != null) {
            pdc.set(DRYING_QUALITY_KEY, PersistentDataType.STRING, dryQuality.name());
        }

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createDriedTobacco(boolean premium) {
        return createDriedTobacco(premium, null);
    }


    public boolean isCigaroItem(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(CIGARO_ITEM_KEY, PersistentDataType.STRING);
        return id.equals(val);
    }

    public boolean isCigarette(ItemStack item) { return isCigaroItem(item, "cigarette"); }

    public CigaretteType getCigaretteType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(CIGARETTE_TYPE_KEY, PersistentDataType.STRING);
        if (val == null) return null;
        try { return CigaretteType.valueOf(val); }
        catch (IllegalArgumentException e) { return null; }
    }

    public CigaretteQuality getCigaretteQuality(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(CIGARETTE_QUALITY_KEY, PersistentDataType.STRING);
        if (val == null) return null;
        try { return CigaretteQuality.valueOf(val); }
        catch (IllegalArgumentException e) { return null; }
    }

    public TobaccoType getProductTobacco(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(TOBACCO_TYPE_KEY, PersistentDataType.STRING);
        if (id == null) return null;
        try { return TobaccoType.valueOf(id); } catch (IllegalArgumentException e) { return null; }
    }

    public void setProductTobacco(ItemStack item, TobaccoType type) {
        if (item == null || type == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(TOBACCO_TYPE_KEY, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
    }

    public TobaccoType getTobaccoType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(TOBACCO_TYPE_KEY, PersistentDataType.STRING);
        if (val == null) return TobaccoType.VIRGINIA;
        try { return TobaccoType.valueOf(val); }
        catch (IllegalArgumentException e) { return TobaccoType.VIRGINIA; }
    }

    public int getPuffsLeft(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(PUFFS_LEFT_KEY, PersistentDataType.INTEGER);
        return val != null ? val : getMaxPuffs(getCigaretteQuality(item));
    }

    public int getPuffsMax(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return CigaretteQuality.STANDARD.getPuffs();
        }
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(PUFFS_MAX_KEY, PersistentDataType.INTEGER);
        return val != null ? val : getMaxPuffs(getCigaretteQuality(item));
    }

    public boolean isUsedCigarette(ItemStack item) {
        if (!isCigarette(item)) return false;
        return getPuffsLeft(item) < getPuffsMax(item);
    }

    public boolean isFreshCigarette(ItemStack item) {
        if (!isCigarette(item)) return false;
        return getPuffsLeft(item) == getPuffsMax(item);
    }

    public int getMaxPuffs(CigaretteQuality quality) {
        if (quality == null) return CigaretteQuality.STANDARD.getPuffs();
        return quality.getPuffs();
    }

    public FlavorHarmony getFlavorHarmony(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return FlavorHarmony.NEUTRAL;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(FLAVOR_HARMONY_KEY, PersistentDataType.STRING);
        return FlavorHarmony.fromName(val);
    }

    public String getSpecialType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(SPECIAL_TYPE_KEY, PersistentDataType.STRING);
    }

    public SmokableType getSmokableType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(SMOKABLE_TYPE_KEY, PersistentDataType.STRING);
        if (val == null) {
            if (isCigarette(item)) return SmokableType.CIGARETTE;
            return null;
        }
        try { return SmokableType.valueOf(val.toUpperCase()); }
        catch (IllegalArgumentException e) {
            return switch (val) {
                case "cigarette" -> SmokableType.CIGARETTE;
                case "cigar" -> SmokableType.CIGAR;
                case "pipe" -> SmokableType.PIPE;
                case "hookah" -> SmokableType.HOOKAH;
                default -> null;
            };
        }
    }

    public CigaretteType tobaccoToCigaretteType(TobaccoType tobacco) {
        if (tobacco == TobaccoType.VIRGINIA) return CigaretteType.VANILLA;
        if (tobacco == TobaccoType.BURLEY) return CigaretteType.COFFEE;
        if (tobacco == TobaccoType.ORIENTAL) return CigaretteType.SPICE;
        if (tobacco == TobaccoType.DARK_FIRED) return CigaretteType.SHADOW;
        if (tobacco == TobaccoType.ANCIENT) return CigaretteType.HERBAL;
        if (tobacco == TobaccoType.CARAVAN) return CigaretteType.CARAMEL;
        CigaretteType registered = com.cigaro.api.CigaroAPI.getRegisteredTobaccoFlavor(tobacco);
        if (registered != null) return registered;
        return CigaretteType.MINT;
    }

    public TobaccoType cigaretteTypeToTobaccoType(CigaretteType type) {
        if (type == CigaretteType.VANILLA) return TobaccoType.VIRGINIA;
        if (type == CigaretteType.COFFEE) return TobaccoType.BURLEY;
        if (type == CigaretteType.SPICE) return TobaccoType.ORIENTAL;
        if (type == CigaretteType.SHADOW) return TobaccoType.DARK_FIRED;
        if (type == CigaretteType.HERBAL) return TobaccoType.ANCIENT;
        if (type == CigaretteType.CARAMEL) return TobaccoType.CARAVAN;
        return null;
    }

    public double getBlendLeafQualityBonus(BlendData blend) {
        if (blend == null) return 0.0;

        TobaccoType primaryTobacco = blend.getPrimaryTobacco() != null ? blend.getPrimaryTobacco() : cigaretteTypeToTobaccoType(blend.getPrimary());
        TobaccoType secondaryTobacco = blend.getSecondaryTobacco() != null ? blend.getSecondaryTobacco() : cigaretteTypeToTobaccoType(blend.getSecondary());
        double primaryBonus = primaryTobacco != null ? primaryTobacco.getQualityBonus() : 0.0;
        double secondaryBonus = secondaryTobacco != null ? secondaryTobacco.getQualityBonus() : 0.0;

        return primaryBonus * blend.getPrimaryRatio()
                + secondaryBonus * blend.getSecondaryRatio();
    }


    public boolean isExtinguished(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(EXTINGUISHED_KEY, PersistentDataType.BYTE);
    }

    public void setExtinguished(ItemStack item, boolean state) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (state) {
            meta.getPersistentDataContainer().set(EXTINGUISHED_KEY, PersistentDataType.BYTE, (byte) 1);
            List<Component> lore = meta.lore();
            if (lore != null) {
                for (int i = 0; i < lore.size(); i++) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
                    if (plain.matches(".*\\(\\d+/\\d+\\).*")) {
                        lore.set(i, trans("cig_status_extinguished",
                                Placeholder.unparsed("left", String.valueOf(getPuffsLeft(item))),
                                Placeholder.unparsed("max", String.valueOf(getPuffsMax(item)))));
                        break;
                    }
                }
                meta.lore(lore);
            }
        } else {
            meta.getPersistentDataContainer().remove(EXTINGUISHED_KEY);
        }
        item.setItemMeta(meta);
    }

    private TextColor dyeToTextColor(DyeColor dye) {
        return switch (dye) {
            case WHITE -> TextColor.color(0xF0F0F0);
            case ORANGE -> TextColor.color(0xD87F33);
            case MAGENTA -> TextColor.color(0xB24CD8);
            case LIGHT_BLUE -> TextColor.color(0x6699D8);
            case YELLOW -> TextColor.color(0xE5E533);
            case LIME -> TextColor.color(0x7FCC19);
            case PINK -> TextColor.color(0xF27FA5);
            case GRAY -> TextColor.color(0x4C4C4C);
            case LIGHT_GRAY -> TextColor.color(0x999999);
            case CYAN -> TextColor.color(0x4C7F99);
            case PURPLE -> TextColor.color(0x7F3FB2);
            case BLUE -> TextColor.color(0x334CB2);
            case BROWN -> TextColor.color(0x664C33);
            case GREEN -> TextColor.color(0x667F33);
            case RED -> TextColor.color(0x993333);
            case BLACK -> TextColor.color(0x191919);
        };
    }

    private String buildPuffLore(int left, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.append(i < left ? "▌" : "░");
        }
        return sb.toString();
    }

    private String getSmokeState(int puffsLeft, int puffsMax) {
        if (puffsLeft == puffsMax) return "unlit";

        if (puffsMax >= 6) {
            if (puffsLeft >= 5) return "full";
            if (puffsLeft >= 3) return "mid";
            return "low";
        } else if (puffsMax == 5) {
            if (puffsLeft >= 4) return "full";
            if (puffsLeft >= 2) return "mid";
            return "low";
        } else if (puffsMax == 4) {
            if (puffsLeft >= 3) return "full";
            if (puffsLeft >= 2) return "mid";
            return "low";
        } else {
            if (puffsLeft >= 3) return "full";
            if (puffsLeft >= 2) return "mid";
            return "low";
        }
    }
}
