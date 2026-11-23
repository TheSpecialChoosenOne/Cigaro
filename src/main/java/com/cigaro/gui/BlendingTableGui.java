package com.cigaro.gui;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.data.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BlendingTableGui extends CigaroGui {

    private final Cigaro plugin;
    private static final int SLOT_INFO = 4, SLOT_INPUT_A = 20, SLOT_INPUT_B = 24;
    private static final int SLOT_PREVIEW = 31, SLOT_RATIO = 22, SLOT_BLEND = 40;

    private static final int MIN_RATIO_PCT = 30;
    private static final int MAX_RATIO_PCT = 70;

    public BlendingTableGui(Player player, Cigaro plugin) {
        super(player, Component.text("\ue001\ue000\ue002\ue003")
                .font(Key.key("cigaro:blending"))
                .color(NamedTextColor.WHITE)
                .append(plugin.getLanguageManager().get("gui_blending_title")
                        .font(Key.key("minecraft:default"))
                        .decoration(TextDecoration.ITALIC, false)), 5);
        this.plugin = plugin;
    }

    private ItemStack icon(String model, Component name, Component... lore) {
        ItemStack item = createGuiItem(Material.PAPER, name, lore);
        var meta = item.getItemMeta();
        var data = meta.getCustomModelDataComponent();
        data.setStrings(List.of(model));
        meta.setCustomModelDataComponent(data);
        item.setItemMeta(meta);
        return item;
    }

    private Component msg(String key, TagResolver... p) {
        return plugin.getLanguageManager().get(key, p).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> msgLore(String key, TagResolver... p) {
        List<Component> raw = plugin.getLanguageManager().getLore(key, p);
        List<Component> result = new ArrayList<>();
        for (Component c : raw) result.add(c.decoration(TextDecoration.ITALIC, false));
        return result;
    }

    @Override
    public boolean isInputSlot(int rawSlot) {
        return rawSlot == SLOT_INPUT_A || rawSlot == SLOT_INPUT_B;
    }

    @Override
    public void onInventoryChanged() {
        refreshDisplays();
    }

    @Override
    protected void buildGui() {
        ItemStack frame = icon("gui_blend_frame", Component.empty());
        ItemStack accent = icon("gui_blend_panel", Component.empty());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, frame);
        for (int slot : new int[]{1, 2, 3, 5, 6, 7, 10, 16, 28, 34, 37, 38, 39, 41, 42, 43}) {
            inventory.setItem(slot, accent);
        }
        inventory.setItem(SLOT_INFO, icon("gui_blend_info",
                msg("gui_blending_info_name"),
                msgLore("gui_blending_info_lore").toArray(new Component[0])));
        inventory.setItem(11, icon("gui_blend_input", msg("gui_blending_input_a")));
        inventory.setItem(15, icon("gui_blend_input", msg("gui_blending_input_b")));
        inventory.setItem(SLOT_INPUT_A, null);
        inventory.setItem(SLOT_INPUT_B, null);
        refreshDisplays();
    }

    @Override
    public boolean onClick(int slot, boolean leftClick, boolean shiftClick) {
        if (isPlayerSlot(slot)) return false;
        if (!isGuiSlot(slot)) return true;

        if (slot == SLOT_INPUT_A || slot == SLOT_INPUT_B) {
            return false;
        }

        if (slot == SLOT_BLEND) {
            handleBlend();
            return true;
        }

        return true;
    }

    private void handleBlend() {
        ItemStack inputA = inventory.getItem(SLOT_INPUT_A);
        ItemStack inputB = inventory.getItem(SLOT_INPUT_B);

        if (inputA == null || inputB == null || inputA.getType().isAir() || inputB.getType().isAir()) {
            player.sendMessage(msg("gui_blending_err_no_input"));
            return;
        }

        if (!plugin.getItemManager().isCutTobacco(inputA)
                || !plugin.getItemManager().isCutTobacco(inputB)) {
            player.sendMessage(msg("gui_blending_err_not_cut"));
            return;
        }

        if (plugin.getItemManager().isBlendedCutTobacco(inputA)
                || plugin.getItemManager().isBlendedCutTobacco(inputB)) {
            player.sendMessage(msg("gui_blending_err_reblend"));
            return;
        }

        CigaretteType typeA = getEffectiveType(inputA);
        CigaretteType typeB = getEffectiveType(inputB);
        if (typeA == null || typeB == null) {
            player.sendMessage(msg("gui_blending_err_unknown_type"));
            return;
        }

        if (typeA == typeB) {
            player.sendMessage(msg("gui_blending_err_same_type"));
            return;
        }

        int amountA = inputA.getAmount();
        int amountB = inputB.getAmount();
        int total = amountA + amountB;
        double ratioA = (double) amountA / total;

        int pctA = (int) Math.round(ratioA * 100);
        if (!validRatio(amountA, amountB)) {
            player.sendMessage(msg("gui_blending_err_ratio",
                    Placeholder.unparsed("min", String.valueOf(MIN_RATIO_PCT)),
                    Placeholder.unparsed("max", String.valueOf(MAX_RATIO_PCT))));
            return;
        }

        BlendData blend = new BlendData(typeA, typeB, ratioA,
                plugin.getItemManager().getTobaccoType(inputA), plugin.getItemManager().getTobaccoType(inputB));

        boolean elite = plugin.getItemManager().isCutElite(inputA)
                && plugin.getItemManager().isCutElite(inputB);
        DryingLevel levelA = plugin.getItemManager().getDryingLevel(inputA);
        DryingLevel levelB = plugin.getItemManager().getDryingLevel(inputB);
        DryingLevel worstLevel = levelA.getQualityBonus() <= levelB.getQualityBonus() ? levelA : levelB;
        int avgQuality = (int) Math.floor(((double) plugin.getItemManager().getCutQuality(inputA) * amountA
                + (double) plugin.getItemManager().getCutQuality(inputB) * amountB) / total);
        int avgDrySec = (int) (((long) plugin.getItemManager().getDryingSeconds(inputA) * amountA
                + (long) plugin.getItemManager().getDryingSeconds(inputB) * amountB) / total);

        inventory.setItem(SLOT_INPUT_A, null);
        inventory.setItem(SLOT_INPUT_B, null);
        refreshDisplays();

        int remaining = total;
        while (remaining > 0) {
            int currentBatch = Math.min(remaining, 64);
            ItemStack result = plugin.getItemManager().createBlendedCutTobacco(
                    blend, elite, worstLevel, avgDrySec, avgQuality, currentBatch);
            var overflow = player.getInventory().addItem(result);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= currentBatch;
        }

        plugin.getServer().getPluginManager().callEvent(new com.cigaro.api.CigaroTobaccoEvent(
                com.cigaro.api.CigaroTobaccoEvent.Action.BLENDED, player, player.getLocation(),
                plugin.getItemManager().createBlendedCutTobacco(blend, elite, worstLevel, avgDrySec, avgQuality, total > 64 ? 64 : total)));
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.0f);
        player.sendMessage(msg("gui_blending_success",
                Placeholder.unparsed("pcta", String.valueOf(pctA)),
                Placeholder.unparsed("typea", formatType(typeA)),
                Placeholder.unparsed("pctb", String.valueOf(100 - pctA)),
                Placeholder.unparsed("typeb", formatType(typeB)),
                Placeholder.unparsed("total", String.valueOf(total))));

        plugin.getAchievementManager().grant(player, CigaroAchievement.BLENDER);
        plugin.getPlayerDataManager().getData(player.getUniqueId()).addMastery(0.008);
    }

    private CigaretteType getEffectiveType(ItemStack item) {
        if (item == null) return null;

        CigaretteType direct = plugin.getItemManager().getCigaretteType(item);
        if (direct != null) return direct;

        TobaccoType tobType = plugin.getItemManager().getTobaccoType(item);
        return tobType != null ? plugin.getItemManager().tobaccoToCigaretteType(tobType) : null;
    }

    private void refreshDisplays() {
        updateRatioDisplay();
        updatePreview();
        updateBlendButton();
    }

    private void updateRatioDisplay() {
        ItemStack a = inventory.getItem(SLOT_INPUT_A), b = inventory.getItem(SLOT_INPUT_B);
        if (a == null || b == null || a.getType() == Material.AIR || b.getType() == Material.AIR) {
            inventory.setItem(SLOT_RATIO, icon("gui_blend_scales", msg("gui_blending_ratio_empty")));
            return;
        }
        int total = a.getAmount() + b.getAmount();
        int pctA = (int) Math.round((double) a.getAmount() / total * 100);
        inventory.setItem(SLOT_RATIO, icon("gui_blend_scales",
                msg("gui_blending_ratio",
                        Placeholder.unparsed("pcta", String.valueOf(pctA)),
                        Placeholder.unparsed("pctb", String.valueOf(100 - pctA))),
                msg("gui_blending_ratio_lore",
                        Placeholder.unparsed("amounta", String.valueOf(a.getAmount())),
                        Placeholder.unparsed("amountb", String.valueOf(b.getAmount())),
                        Placeholder.unparsed("total", String.valueOf(total)))));
    }

    private void updatePreview() {
        ItemStack a = inventory.getItem(SLOT_INPUT_A), b = inventory.getItem(SLOT_INPUT_B);
        if (a == null || b == null || a.getType() == Material.AIR || b.getType() == Material.AIR) {
            inventory.setItem(SLOT_PREVIEW, icon("gui_blend_empty",
                    msg("gui_blending_preview_empty"), msg("gui_blending_preview_empty_lore")));
            return;
        }

        if (!plugin.getItemManager().isCutTobacco(a) || !plugin.getItemManager().isCutTobacco(b)) {
            inventory.setItem(SLOT_PREVIEW, icon("gui_blend_error",
                    msg("gui_blending_preview_invalid"), msg("gui_blending_err_not_cut")));
            return;
        }
        if (plugin.getItemManager().isBlendedCutTobacco(a) || plugin.getItemManager().isBlendedCutTobacco(b)) {
            inventory.setItem(SLOT_PREVIEW, icon("gui_blend_error",
                    msg("gui_blending_preview_invalid"), msg("gui_blending_err_reblend")));
            return;
        }
        CigaretteType typeA = getEffectiveType(a), typeB = getEffectiveType(b);
        if (typeA == null || typeB == null) {
            inventory.setItem(SLOT_PREVIEW, icon("gui_blend_error",
                    msg("gui_blending_preview_invalid"),
                    msg("gui_blending_preview_unknown_type")));
            return;
        }

        int total = a.getAmount() + b.getAmount();
        int pctA = (int) Math.round((double) a.getAmount() / total * 100);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(pctA + "% " + formatType(typeA)).color(CigaroColors.forType(typeA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text((100 - pctA) + "% " + formatType(typeB)).color(CigaroColors.forType(typeB))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (typeA == typeB) {
            lore.add(msg("gui_blending_preview_same_type"));
        } else if (!validRatio(a.getAmount(), b.getAmount())) {
            lore.add(msg("gui_blending_preview_bad_ratio",
                    Placeholder.unparsed("min", String.valueOf(MIN_RATIO_PCT)),
                    Placeholder.unparsed("max", String.valueOf(MAX_RATIO_PCT))));
        } else {
            lore.add(msg("gui_blending_preview_ok"));
        }

        lore.add(msg("gui_blending_units", Placeholder.unparsed("total", String.valueOf(total))));

        inventory.setItem(SLOT_PREVIEW, icon(typeA == typeB || !validRatio(a.getAmount(), b.getAmount())
                        ? "gui_blend_error" : "gui_blend_preview",
                msg("gui_blending_preview_name"), lore.toArray(new Component[0])));
    }

    private void updateBlendButton() {
        ItemStack a = inventory.getItem(SLOT_INPUT_A), b = inventory.getItem(SLOT_INPUT_B);
        boolean valid = a != null && b != null && !a.getType().isAir() && !b.getType().isAir()
                && plugin.getItemManager().isCutTobacco(a) && plugin.getItemManager().isCutTobacco(b)
                && !plugin.getItemManager().isBlendedCutTobacco(a) && !plugin.getItemManager().isBlendedCutTobacco(b)
                && getEffectiveType(a) != null && getEffectiveType(b) != null
                && getEffectiveType(a) != getEffectiveType(b) && validRatio(a.getAmount(), b.getAmount());

        if (valid) {
            inventory.setItem(SLOT_BLEND, icon("gui_blend_ready",
                    msg("gui_blending_blend_ready"), msg("gui_blending_blend_ready_lore")));
        } else {
            inventory.setItem(SLOT_BLEND, icon("gui_blend_idle",
                    msg("gui_blending_blend_idle"), msg("gui_blending_blend_idle_lore")));
        }
    }

    private boolean validRatio(int amountA, int amountB) {
        int total = amountA + amountB;
        return amountA * 100 >= MIN_RATIO_PCT * total && amountA * 100 <= MAX_RATIO_PCT * total;
    }

    private String formatType(CigaretteType type) {
        return plugin.getItemManager().formatTypeName(type);
    }

    @Override
    public void onClose() {
        returnItem(SLOT_INPUT_A);
        returnItem(SLOT_INPUT_B);
    }

    private void returnItem(int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && !item.getType().isAir() && !isFiller(item)) {
            inventory.setItem(slot, null);
            var overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }
}

