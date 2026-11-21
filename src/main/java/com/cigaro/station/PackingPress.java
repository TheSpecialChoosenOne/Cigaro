package com.cigaro.station;

import com.cigaro.data.CigaretteType;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PackingPress implements InventoryHolder {

    private final Location location;
    public CigaretteType cigaretteType;
    public int cigaretteCount;
    public int cardboardCount;
    public int progress;
    private UUID blockDisplayId;
    public boolean hasItems;
    public DyeColor dyeColor;

    private transient Inventory inventory;
    ItemStack cigaretteItem;
    ItemStack cardboardItem;
    ItemStack dyeItem;

    public static final int CIGARETTES_PER_PACK = 20;

    public PackingPress(Location location) {
        this.location = location;
        this.cigaretteCount = 0;
        this.cardboardCount = 0;
        this.progress = 0;
        this.hasItems = false;
        this.dyeColor = null;
        this.cigaretteItem = null;
        this.cardboardItem = null;
        this.dyeItem = null;
    }

    public Location getLocation() { return location; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public boolean isFinished() { return progress >= 100; }
    public void resetProgress() { this.progress = 0; }
    public UUID getBlockDisplayId() { return blockDisplayId; }
    public void setBlockDisplayId(UUID id) { this.blockDisplayId = id; }
    public CigaretteType getCigaretteType() { return cigaretteType; }
    public int getCigaretteCount() { return cigaretteCount; }
    public int getCardboardCount() { return cardboardCount; }
    public ItemStack getCigaretteItem() { return cigaretteItem; }
    public void setCigaretteItem(ItemStack item) { this.cigaretteItem = item; }
    public ItemStack getCardboardItem() { return cardboardItem; }
    public void setCardboardItem(ItemStack item) { this.cardboardItem = item; }
    public DyeColor getDyeColor() { return dyeColor; }
    public void setDyeColor(DyeColor color) { this.dyeColor = color; }
    public ItemStack getDyeItem() { return dyeItem; }
    public void setDyeItem(ItemStack item) { this.dyeItem = item; }

    public boolean hasItems() { return hasItems; }

    public boolean isReady() {
        return cigaretteCount >= CIGARETTES_PER_PACK
                && cardboardCount >= 1
                && dyeColor != null;
    }

    public void consumeItems() {
        cigaretteCount -= CIGARETTES_PER_PACK;
        cardboardCount -= 1;
        progress = 0;

        if (cigaretteItem != null) {
            if (cigaretteCount > 0) {
                cigaretteItem.setAmount(cigaretteCount);
            } else {
                cigaretteItem = null;
            }
        }
        if (cardboardItem != null) {
            if (cardboardCount > 0) {
                cardboardItem.setAmount(cardboardCount);
            } else {
                cardboardItem = null;
            }
        }
        if (dyeItem != null) {
            int dyeAmount = dyeItem.getAmount() - 1;
            if (dyeAmount > 0) {
                dyeItem.setAmount(dyeAmount);
            } else {
                dyeItem = null;
                dyeColor = null;
            }
        }

        hasItems = isReady();

        if (inventory != null) {
            updateInventory();
        }
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, 9, Component.text("Packing Press"));
            if (cigaretteItem != null) inventory.setItem(0, cigaretteItem);
            if (cardboardItem != null) inventory.setItem(1, cardboardItem);
            if (dyeItem != null) inventory.setItem(2, dyeItem);
        }
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
        this.cigaretteItem = inventory.getItem(0);
        this.cardboardItem = inventory.getItem(1);
        this.dyeItem = inventory.getItem(2);
        if (cigaretteItem != null) cigaretteCount = cigaretteItem.getAmount();
        if (cardboardItem != null) cardboardCount = cardboardItem.getAmount();
        hasItems = isReady();
    }

    public void updateInventory() {
        if (inventory != null) {
            inventory.setItem(0, cigaretteItem);
            inventory.setItem(1, cardboardItem);
            inventory.setItem(2, dyeItem);
        }
    }
}
