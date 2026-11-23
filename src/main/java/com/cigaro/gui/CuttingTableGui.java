package com.cigaro.gui;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.data.DryingLevel;
import com.cigaro.data.TobaccoType;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CuttingTableGui extends CigaroGui {

    private final Cigaro plugin;
    private final Random random = new Random();
    private int cursorPos = 0;
    private boolean movingRight = true;
    private BukkitTask animationTask;
    private boolean gameStarted = false;
    private int greenStart;
    private int greenEnd;
    private final List<ItemStack> pendingResults = new ArrayList<>();

    private static final int SLOT_INFO = 10;
    private static final int SLOT_INPUT = 12;
    private static final int SLOT_COUNTER = 14;
    private static final int SLOT_OUTPUT = 16;
    private static final int SLOT_CUT = 40;
    private static final int[] BAR_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    public CuttingTableGui(Player player, Cigaro plugin) {
        super(player, Component.text("\ue001\ue000\ue002\ue003")
                .font(Key.key("cigaro:blending"))
                .color(NamedTextColor.WHITE)
                .append(plugin.getLanguageManager().get("gui_cutting_title")
                        .font(Key.key("minecraft:default"))
                        .decoration(TextDecoration.ITALIC, false)), 5);
        this.plugin = plugin;
        randomizeGreenZone();
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
        return rawSlot == SLOT_INPUT && !gameStarted;
    }

    @Override
    public void onInventoryChanged() {
        updateCounter();
    }

    private void randomizeGreenZone() {
        this.greenStart = 1 + random.nextInt(4);
        this.greenEnd = greenStart + 1;
    }

    @Override
    protected void buildGui() {
        ItemStack frame = icon("gui_blend_frame", Component.empty());
        ItemStack panel = icon("gui_blend_panel", Component.empty());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, frame);
        for (int slot : new int[]{1, 2, 3, 4, 5, 6, 7, 11, 13, 15, 19, 20, 21, 22, 23, 24, 25, 37, 38, 39, 41, 42, 43}) {
            inventory.setItem(slot, panel);
        }

        inventory.setItem(SLOT_INFO, icon("gui_blend_info",
                msg("gui_cutting_info_name"),
                msgLore("gui_cutting_info_lore").toArray(new Component[0])));

        inventory.setItem(SLOT_INPUT, null);
        updateCounter();

        inventory.setItem(SLOT_OUTPUT, icon("gui_blend_empty",
                msg("gui_cutting_results_empty"), msg("gui_cutting_results_empty_lore")));

        updateBar();

        inventory.setItem(SLOT_CUT, icon("gui_cut_knife_idle",
                msg("gui_cutting_cut_idle"), msg("gui_cutting_cut_idle_lore")));
    }

    @Override
    public boolean onClick(int slot, boolean leftClick, boolean shiftClick) {
        if (isPlayerSlot(slot)) return false;
        if (!isGuiSlot(slot)) return true;
        if (slot == SLOT_INPUT) return gameStarted;
        if (slot == SLOT_CUT) { handleCut(); return true; }
        if (slot == SLOT_OUTPUT && !pendingResults.isEmpty()) {
            for (ItemStack result : pendingResults) {
                var overflow = player.getInventory().addItem(result);
                for (ItemStack leftover : overflow.values())
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            pendingResults.clear();
            updateOutputDisplay();
            updateCounter();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            return true;
        }
        return true;
    }

    private void handleCut() {
        ItemStack input = inventory.getItem(SLOT_INPUT);
        if (input == null || !plugin.getItemManager().isTobaccoLeaf(input)) {
            player.sendMessage(msg("gui_cutting_err_no_leaf"));
            return;
        }
        if (!plugin.getItemManager().isDriedTobaccoLeaf(input)) {
            player.sendMessage(msg("gui_cutting_err_not_dried"));
            return;
        }

        if (!gameStarted) {
            gameStarted = true;
            randomizeGreenZone();
            startAnimation();
            inventory.setItem(SLOT_CUT, icon("gui_cut_knife_active",
                    msg("gui_cutting_cut_active"), msg("gui_cutting_cut_active_lore")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f);
            return;
        }

        stopAnimation();

        TobaccoType tobaccoType = plugin.getItemManager().getTobaccoType(input);
        boolean elite = plugin.getItemManager().isEliteLeaf(input);
        DryingLevel dryingLevel = plugin.getItemManager().getDryingLevel(input);
        int dryingSeconds = plugin.getItemManager().getDryingSeconds(input);
        if (tobaccoType == null) tobaccoType = TobaccoType.VIRGINIA;

        input.setAmount(input.getAmount() - 1);
        if (input.getAmount() <= 0) inventory.setItem(SLOT_INPUT, null);

        boolean inGreen = cursorPos >= greenStart && cursorPos <= greenEnd;
        boolean nearGreen = !inGreen && (Math.abs(cursorPos - greenStart) <= 1 || Math.abs(cursorPos - greenEnd) <= 1);
        int bonusQuality;
        if (inGreen) { bonusQuality = 2; player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f); }
        else if (nearGreen) { bonusQuality = 1; player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.8f); }
        else { bonusQuality = -1; player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 0.5f); }

        ItemStack cutResult = plugin.getItemManager().createCutTobacco(tobaccoType, elite, dryingLevel, dryingSeconds, bonusQuality);
        pendingResults.add(cutResult);
        plugin.getServer().getPluginManager().callEvent(new com.cigaro.api.CigaroTobaccoEvent(
                com.cigaro.api.CigaroTobaccoEvent.Action.CUT, player, player.getLocation(), cutResult));
        plugin.getPlayerDataManager().getData(player.getUniqueId()).addMastery(0.005);

        updateOutputDisplay();
        updateCounter();

        ItemStack remaining = inventory.getItem(SLOT_INPUT);
        if (remaining != null && plugin.getItemManager().isTobaccoLeaf(remaining)
                && plugin.getItemManager().isDriedTobaccoLeaf(remaining)) {
            randomizeGreenZone();
            startAnimation();
            inventory.setItem(SLOT_CUT, icon("gui_cut_knife_active",
                    msg("gui_cutting_cut_active"), msg("gui_cutting_cut_next_lore")));
        } else {
            gameStarted = false;
            updateBar();
            inventory.setItem(SLOT_CUT, icon("gui_cut_knife_idle",
                    msg("gui_cutting_cut_idle"), msg("gui_cutting_cut_done_lore")));
        }
    }

    private void updateOutputDisplay() {
        if (pendingResults.isEmpty()) {
            inventory.setItem(SLOT_OUTPUT, icon("gui_blend_empty",
                    msg("gui_cutting_results_empty"), msg("gui_cutting_results_empty_lore")));
        } else {
            inventory.setItem(SLOT_OUTPUT, icon("gui_blend_preview",
                    msg("gui_cutting_results_ready",
                            Placeholder.unparsed("count", String.valueOf(pendingResults.size()))),
                    msg("gui_cutting_results_lore")));
        }
    }

    private void updateCounter() {
        ItemStack input = inventory.getItem(SLOT_INPUT);
        int remaining = (input != null && plugin.getItemManager().isTobaccoLeaf(input)) ? input.getAmount() : 0;
        inventory.setItem(SLOT_COUNTER, icon("gui_cut_counter",
                msg("gui_cutting_counter",
                        Placeholder.unparsed("remaining", String.valueOf(remaining)),
                        Placeholder.unparsed("results", String.valueOf(pendingResults.size())))));
    }

    private void startAnimation() {
        cursorPos = 0; movingRight = true; stopAnimation();
        animationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (movingRight) { cursorPos++; if (cursorPos >= 6) movingRight = false; }
            else { cursorPos--; if (cursorPos <= 0) movingRight = true; }
            updateBar();
        }, 0L, 3L);
    }

    private void stopAnimation() {
        if (animationTask != null) { animationTask.cancel(); animationTask = null; }
    }

    private void updateBar() {
        for (int i = 0; i < 7; i++) {
            boolean target = i >= greenStart && i <= greenEnd;
            String model = i == cursorPos && gameStarted
                    ? (target ? "gui_cut_pointer_target" : "gui_cut_pointer_track")
                    : (target ? "gui_cut_target" : "gui_cut_track");
            inventory.setItem(BAR_SLOTS[i], icon(model, Component.empty()));
        }
    }

    @Override
    public void onClose() {
        stopAnimation();
        gameStarted = false;
        ItemStack input = inventory.getItem(SLOT_INPUT);
        if (input != null && !input.getType().isAir() && !isFiller(input)) {
            inventory.setItem(SLOT_INPUT, null);
            var overflow = player.getInventory().addItem(input);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        for (ItemStack result : pendingResults) {
            var overflow = player.getInventory().addItem(result);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        pendingResults.clear();
    }
}

