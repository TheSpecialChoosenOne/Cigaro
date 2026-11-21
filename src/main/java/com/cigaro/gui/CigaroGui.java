package com.cigaro.gui;

import com.cigaro.Cigaro;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class CigaroGui implements InventoryHolder {
    private static NamespacedKey GUI_ITEM_KEY;
    protected final Player player;
    protected final Inventory inventory;

    private static final Map<UUID, CigaroGui> openGuis = new HashMap<>();

    public static void init(Cigaro plugin) {
        GUI_ITEM_KEY = new NamespacedKey(plugin, "gui_item");
    }

    public CigaroGui(Player player, Component title, int rows) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open() {
        buildGui();
        player.openInventory(inventory);
        openGuis.put(player.getUniqueId(), this);
    }

    public void close() {
        openGuis.remove(player.getUniqueId());
        player.closeInventory();
    }

    public static void closeAll() {
        for (CigaroGui gui : new ArrayList<>(openGuis.values())) {
            try {
                gui.onClose();
                gui.player.closeInventory();
            } catch (Exception ignored) {}
        }
        openGuis.clear();
    }

    public boolean isInputSlot(int rawSlot) {
        return false;
    }

    public void onInventoryChanged() {
    }

    public abstract boolean onClick(int rawSlot, boolean leftClick, boolean shiftClick);

    public abstract void onClose();

    protected abstract void buildGui();

    public static CigaroGui getOpenGui(UUID playerId) {
        return openGuis.get(playerId);
    }

    public static void removeGui(UUID playerId) {
        openGuis.remove(playerId);
    }

    public static boolean isGuiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || GUI_ITEM_KEY == null) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(GUI_ITEM_KEY, PersistentDataType.BYTE);
    }

    protected boolean isGuiSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < inventory.getSize();
    }

    protected boolean isPlayerSlot(int rawSlot) {
        return rawSlot >= inventory.getSize();
    }

    protected boolean isFiller(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return isGuiItem(item);
    }

    protected ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) meta.lore(Arrays.asList(lore));
        if (GUI_ITEM_KEY != null) {
            meta.getPersistentDataContainer().set(GUI_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
        }
        item.setItemMeta(meta);
        return item;
    }

    protected ItemStack createFiller() {
        return createGuiItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
    }

    protected void fillBorder() {
        ItemStack filler = createFiller();
        int size = inventory.getSize();
        int rows = size / 9;

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, filler);
            inventory.setItem(size - 9 + i, filler);
        }
        for (int i = 1; i < rows - 1; i++) {
            inventory.setItem(i * 9, filler);
            inventory.setItem(i * 9 + 8, filler);
        }
    }
}

