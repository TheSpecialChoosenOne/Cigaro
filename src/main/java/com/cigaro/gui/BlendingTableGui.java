package com.cigaro.gui;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.data.*;
import net.kyori.adventure.text.Component;
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
    private static final int SLOT_INFO = 10, SLOT_INPUT_A = 12, SLOT_INPUT_B = 14;
    private static final int SLOT_PREVIEW = 16, SLOT_RATIO = 30, SLOT_BLEND = 34;

    private static final int MIN_RATIO_PCT = 30;
    private static final int MAX_RATIO_PCT = 70;

    public BlendingTableGui(Player player, Cigaro plugin) {
        super(player, plugin.getLanguageManager().get("gui_blending_title")
                .decoration(TextDecoration.ITALIC, false), 5);
        this.plugin = plugin;
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
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        inventory.setItem(SLOT_INFO, createGuiItem(Material.BOOK,
                msg("gui_blending_info_name"),
                msgLore("gui_blending_info_lore").toArray(new Component[0])));
        inventory.setItem(SLOT_INPUT_A, null);
        inventory.setItem(SLOT_INPUT_B, null);
        updateRatioDisplay();
        updatePreview();
        inventory.setItem(SLOT_BLEND, createGuiItem(Material.IRON_INGOT,
                msg("gui_blending_blend_idle"), msg("gui_blending_blend_idle_lore")));
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
        if (pctA < MIN_RATIO_PCT || pctA > MAX_RATIO_PCT) {
            player.sendMessage(msg("gui_blending_err_ratio",
                    Placeholder.unparsed("min", String.valueOf(MIN_RATIO_PCT)),
                    Placeholder.unparsed("max", String.valueOf(MAX_RATIO_PCT))));
            return;
        }

        BlendData blend = new BlendData(typeA, typeB, ratioA);

        boolean elite = plugin.getItemManager().isCutElite(inputA)
                && plugin.getItemManager().isCutElite(inputB);
        DryingLevel levelA = plugin.getItemManager().getDryingLevel(inputA);
        DryingLevel levelB = plugin.getItemManager().getDryingLevel(inputB);
        DryingLevel worstLevel = levelA.getLevel() < levelB.getLevel() ? levelA : levelB;
        int avgQuality = (int) Math.floor((plugin.getItemManager().getCutQuality(inputA)
                + plugin.getItemManager().getCutQuality(inputB)) / 2.0);
        int avgDrySec = (plugin.getItemManager().getDryingSeconds(inputA)
                + plugin.getItemManager().getDryingSeconds(inputB)) / 2;

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

        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.0f);
        player.sendMessage(msg("gui_blending_success",
                Placeholder.unparsed("pctA", String.valueOf(pctA)),
                Placeholder.unparsed("typeA", formatType(typeA)),
                Placeholder.unparsed("pctB", String.valueOf(100 - pctA)),
                Placeholder.unparsed("typeB", formatType(typeB)),
                Placeholder.unparsed("total", String.valueOf(total))));

        plugin.getAchievementManager().grant(player, CigaroAchievement.BLENDER);
        plugin.getPlayerDataManager().getData(player.getUniqueId()).addMastery(0.008);
    }

    private CigaretteType getEffectiveType(ItemStack item) {
        if (item == null) return null;

        BlendData blend = plugin.getItemManager().getBlendData(item);
        if (blend != null) return blend.getPrimary();

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
            inventory.setItem(SLOT_RATIO, createGuiItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, msg("gui_blending_ratio_empty")));
            return;
        }
        int total = a.getAmount() + b.getAmount();
        int pctA = (int) Math.round((double) a.getAmount() / total * 100);
        inventory.setItem(SLOT_RATIO, createGuiItem(Material.COMPARATOR,
                msg("gui_blending_ratio",
                        Placeholder.unparsed("pctA", String.valueOf(pctA)),
                        Placeholder.unparsed("pctB", String.valueOf(100 - pctA))),
                msg("gui_blending_ratio_lore",
                        Placeholder.unparsed("amountA", String.valueOf(a.getAmount())),
                        Placeholder.unparsed("amountB", String.valueOf(b.getAmount())),
                        Placeholder.unparsed("total", String.valueOf(total)))));
    }

    private void updatePreview() {
        ItemStack a = inventory.getItem(SLOT_INPUT_A), b = inventory.getItem(SLOT_INPUT_B);
        if (a == null || b == null || a.getType() == Material.AIR || b.getType() == Material.AIR) {
            inventory.setItem(SLOT_PREVIEW, createGuiItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    msg("gui_blending_preview_empty"), msg("gui_blending_preview_empty_lore")));
            return;
        }

        CigaretteType typeA = getEffectiveType(a), typeB = getEffectiveType(b);
        if (typeA == null || typeB == null) {
            inventory.setItem(SLOT_PREVIEW, createGuiItem(Material.RED_STAINED_GLASS_PANE,
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
        } else if (pctA < MIN_RATIO_PCT || pctA > MAX_RATIO_PCT) {
            lore.add(msg("gui_blending_preview_bad_ratio",
                    Placeholder.unparsed("min", String.valueOf(MIN_RATIO_PCT)),
                    Placeholder.unparsed("max", String.valueOf(MAX_RATIO_PCT))));
        } else {
            lore.add(msg("gui_blending_preview_ok"));
        }

        lore.add(msg("gui_blending_units", Placeholder.unparsed("total", String.valueOf(total))));

        inventory.setItem(SLOT_PREVIEW, createGuiItem(Material.DRIED_KELP,
                msg("gui_blending_preview_name"), lore.toArray(new Component[0])));
    }

    private void updateBlendButton() {
        ItemStack a = inventory.getItem(SLOT_INPUT_A), b = inventory.getItem(SLOT_INPUT_B);
        boolean valid = a != null && b != null && !a.getType().isAir() && !b.getType().isAir();

        if (valid) {
            inventory.setItem(SLOT_BLEND, createGuiItem(Material.GOLD_INGOT,
                    msg("gui_blending_blend_ready"), msg("gui_blending_blend_ready_lore")));
        } else {
            inventory.setItem(SLOT_BLEND, createGuiItem(Material.IRON_INGOT,
                    msg("gui_blending_blend_idle"), msg("gui_blending_blend_idle_lore")));
        }
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

