package com.cigaro.station;

import com.cigaro.data.TobaccoType;
import com.cigaro.data.DryingLevel;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CuttingMachine implements InventoryHolder {

    private final Location location;
    public TobaccoType tobaccoType;
    public boolean elite;
    public DryingLevel dryingLevel;
    public int progress;
    public int fuelSeconds;
    private UUID blockDisplayId;
    public boolean hasItem;

    private transient Inventory inventory;
    ItemStack leafItem;
    ItemStack fuelItem;

    public CuttingMachine(Location location) {
        this.location = location;
        this.progress = 0;
        this.fuelSeconds = 0;
        this.hasItem = false;
        this.leafItem = null;
        this.fuelItem = null;
    }

    public Location getLocation() { return location; }
    public int getFuelSeconds() { return fuelSeconds; }
    public void setFuelSeconds(int seconds) { this.fuelSeconds = seconds; }
    public void addFuel(int seconds) { this.fuelSeconds += seconds; }

    public void addFuel(int seconds, ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) return;

        int current = fuelItem == null ? 0 : fuelItem.getAmount();
        int accepted = Math.min(stack.getAmount(), 64 - current);
        if (accepted <= 0) return;

        if (accepted < stack.getAmount()) {
            seconds = (int) Math.round((double) seconds * accepted / stack.getAmount());
        }
        this.fuelSeconds += seconds;

        ItemStack toStore = stack.clone();
        toStore.setAmount(accepted);
        if (fuelItem != null && fuelItem.isSimilar(toStore)) {
            fuelItem.setAmount(fuelItem.getAmount() + accepted);
        } else {
            fuelItem = toStore;
        }
        updateFuelSlot();
    }

    public void syncFuelItem() {
        if (fuelItem == null) return;
        int pellets = (fuelSeconds + AutoDryingRack.FUEL_SECONDS_PER_PELLET - 1)
                / AutoDryingRack.FUEL_SECONDS_PER_PELLET;
        if (pellets <= 0) {
            fuelItem = null;
        } else if (fuelItem.getAmount() != pellets) {
            fuelItem.setAmount(pellets);
        }
        updateFuelSlot();
    }

    private void updateFuelSlot() {
        if (inventory != null) inventory.setItem(1, fuelItem);
    }

    public boolean hasFuel() { return fuelSeconds > 0; }
    public int getProgress() { return progress; }
    public boolean isFinished() { return progress >= 100; }
    public UUID getBlockDisplayId() { return blockDisplayId; }
    public void setBlockDisplayId(UUID id) { this.blockDisplayId = id; }
    public ItemStack getLeafItem() { return leafItem; }
    public void setLeafItem(ItemStack item) { this.leafItem = item; }
    public ItemStack getFuelItem() { return fuelItem; }
    public void setFuelItem(ItemStack item) { this.fuelItem = item; updateFuelSlot(); }
    public TobaccoType getTobaccoType() { return tobaccoType; }
    public boolean isElite() { return elite; }
    public DryingLevel getDryingLevel() { return dryingLevel; }

    public boolean hasItem() { return hasItem; }
    public void setItem(TobaccoType type, boolean elite, DryingLevel level, ItemStack item) {
        this.tobaccoType = type; this.elite = elite; this.dryingLevel = level;
        this.hasItem = true; this.progress = 0; this.leafItem = item;
        if (inventory != null) inventory.setItem(0, item);
    }
    public void clearItem() {
        tobaccoType = null; elite = false; dryingLevel = null; hasItem = false; progress = 0; leafItem = null;
        if (inventory != null) inventory.setItem(0, null);
    }
    public ItemStack takeLeaf() { ItemStack item = leafItem; clearItem(); return item; }

    @Override
    @NotNull
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, 9, Component.text("Cutting Machine"));
            if (leafItem != null) inventory.setItem(0, leafItem);
            if (fuelItem != null) inventory.setItem(1, fuelItem);
        }
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
        this.leafItem = inventory.getItem(0);
        this.fuelItem = inventory.getItem(1);
    }

    public void updateInventory() {
        if (inventory != null) {
            inventory.setItem(0, leafItem);
            inventory.setItem(1, fuelItem);
        }
    }
}
