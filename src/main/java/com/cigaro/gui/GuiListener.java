package com.cigaro.gui;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.config.LanguageManager;
import com.cigaro.data.CigaretteType;
import com.cigaro.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GuiListener implements Listener {

    private final Cigaro plugin;
    private final Set<UUID> readonlyGuis = new HashSet<>();

    private static final class StatsGuiHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }

    public GuiListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    private CigaroGui resolveGui(Player player, Inventory topInventory) {
        if (topInventory != null && topInventory.getHolder() instanceof CigaroGui holderGui) {
            return holderGui;
        }
        return CigaroGui.getOpenGui(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        Inventory top = event.getView().getTopInventory();
        if (readonlyGuis.contains(uuid) || top.getHolder() instanceof StatsGuiHolder) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            return;
        }

        if (CigaroGui.isGuiItem(event.getCursor())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            player.setItemOnCursor(null);
            return;
        }

        CigaroGui gui = resolveGui(player, top);
        if (gui == null) {
            if (CigaroGui.isGuiItem(event.getCurrentItem())) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                event.setCurrentItem(null);
            }
            return;
        }

        int rawSlot = event.getRawSlot();
        int guiSize = top.getSize();

        if (event.getClick() == ClickType.DOUBLE_CLICK
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            return;
        }

        if (event.isShiftClick() && gui.isPlayerSlot(rawSlot)) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            return;
        }

        if (rawSlot < 0) return;

        if (rawSlot < guiSize) {
            if (!gui.isInputSlot(rawSlot)) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                gui.onClick(rawSlot, event.isLeftClick(), event.isShiftClick());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ItemStack cursor = player.getItemOnCursor();
                    if (CigaroGui.isGuiItem(cursor)) {
                        player.setItemOnCursor(null);
                    }
                    player.updateInventory();
                });
                return;
            }

            boolean handled = gui.onClick(rawSlot, event.isLeftClick(), event.isShiftClick());
            if (handled) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        gui.onInventoryChanged();
                    }
                });
            }
            return;
        }

        if (CigaroGui.isGuiItem(event.getCurrentItem())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            event.setCurrentItem(null);
            return;
        }

        boolean handled = gui.onClick(rawSlot, event.isLeftClick(), event.isShiftClick());
        if (handled) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        Inventory top = event.getView().getTopInventory();
        if (readonlyGuis.contains(uuid) || top.getHolder() instanceof StatsGuiHolder) {
            event.setCancelled(true);
            return;
        }

        CigaroGui gui = resolveGui(player, top);
        if (gui == null) return;

        int guiSize = top.getSize();
        boolean touchedInput = false;
        for (int slot : event.getRawSlots()) {
            if (slot < guiSize) {
                if (!gui.isInputSlot(slot)) {
                    event.setCancelled(true);
                    return;
                }
                touchedInput = true;
            }
        }

        if (touchedInput) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    gui.onInventoryChanged();
                }
            });
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        readonlyGuis.remove(uuid);

        ItemStack cursor = player.getItemOnCursor();
        if (CigaroGui.isGuiItem(cursor)) {
            player.setItemOnCursor(null);
        }

        Inventory top = event.getView().getTopInventory();
        CigaroGui gui = resolveGui(player, top);
        if (gui == null) return;

        CigaroGui.removeGui(uuid);
        gui.onClose();
    }

    public void openStatsGui(Player player, PlayerData data) {
        Inventory gui = Bukkit.createInventory(new StatsGuiHolder(), 9,
                lang().get("gui_stats_title"));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, filler);
        }

        int stage = data.getAddictionStage();
        String stageKey = "gui_stats_stage_" + Math.min(stage, 4);
        String stageName = lang().getRaw(stageKey);

        ItemStack stageItem = new ItemStack(Material.BOOK);
        ItemMeta stageMeta = stageItem.getItemMeta();
        stageMeta.displayName(
                lang().get("gui_stats_stage_name",
                                Placeholder.unparsed("stage", stageName))
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false)
        );
        stageMeta.lore(List.of(
                Component.empty(),
                lang().get("gui_stats_total_line",
                                Placeholder.unparsed("total",
                                        String.valueOf(data.getTotalSmoked())))
                        .decoration(TextDecoration.ITALIC, false),
                lang().get("gui_stats_sober_line",
                                Placeholder.unparsed("sober",
                                        String.valueOf(data.getOnlineSoberSeconds() / 3600)))
                        .decoration(TextDecoration.ITALIC, false)
        ));
        stageItem.setItemMeta(stageMeta);
        gui.setItem(4, stageItem);

        ItemStack tolItem = new ItemStack(Material.MILK_BUCKET);
        ItemMeta tolMeta = tolItem.getItemMeta();
        tolMeta.displayName(
                lang().get("gui_stats_tolerance_name")
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false)
        );
        List<Component> tolLore = new ArrayList<>();
        tolLore.add(Component.empty());
        for (CigaretteType type : CigaretteType.values()) {
            double tol = data.getTolerance(type) * 100;
            tolLore.add(
                    Component.text(plugin.getItemManager().formatTypeName(type) + ": "
                                    + String.format("%.0f", tol) + "%")
                            .color(CigaroColors.SMOKE)
                            .decoration(TextDecoration.ITALIC, false)
            );
        }
        tolMeta.lore(tolLore);
        tolItem.setItemMeta(tolMeta);
        gui.setItem(2, tolItem);

        ItemStack predItem = new ItemStack(Material.COMPASS);
        ItemMeta predMeta = predItem.getItemMeta();
        predMeta.displayName(
                lang().get("gui_stats_predisposition_name")
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false)
        );
        List<Component> predLore = new ArrayList<>();
        predLore.add(Component.empty());
        for (CigaretteType type : CigaretteType.values()) {
            int pred = data.getPredisposition(type);
            String predText = pred > 0 ? "+" + pred : String.valueOf(pred);
            predLore.add(
                    Component.text(plugin.getItemManager().formatTypeName(type) + ": " + predText)
                            .color(pred > 0 ? CigaroColors.SUCCESS
                                    : pred < 0 ? CigaroColors.ERROR
                                      : CigaroColors.SMOKE)
                            .decoration(TextDecoration.ITALIC, false)
            );
        }
        predMeta.lore(predLore);
        predItem.setItemMeta(predMeta);
        gui.setItem(6, predItem);

        player.openInventory(gui);
        readonlyGuis.add(player.getUniqueId());
    }
}

