package com.cigaro.station;

import com.cigaro.data.CigaretteType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class RollingPress implements InventoryHolder {

    private final Location location;
    public CigaretteType tobaccoType;
    public int tobaccoAmount;
    public int paperAmount;
    public int progress;
    public int fuelSeconds;
    private double operatorMastery;
    private int operatorFlavorPredisposition;
    private UUID blockDisplayId;
    public boolean hasItems;

    private transient Inventory inventory;
    ItemStack tobaccoItem;
    ItemStack paperItem;
    ItemStack fuelItem;

    public RollingPress(Location location) {
        this.location = location;
        this.tobaccoAmount = 0; this.paperAmount = 0; this.progress = 0; this.fuelSeconds = 0; this.hasItems = false;
        this.tobaccoItem = null; this.paperItem = null; this.fuelItem = null;
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
        if (inventory != null) inventory.setItem(2, fuelItem);
    }

    public boolean hasFuel() { return fuelSeconds > 0; }
    public int getProgress() { return progress; }
    public boolean isFinished() { return progress >= 100; }
    public void resetProgress() { this.progress = 0; }
    public UUID getBlockDisplayId() { return blockDisplayId; }
    public void setBlockDisplayId(UUID id) { this.blockDisplayId = id; }
    public CigaretteType getTobaccoType() { return tobaccoType; }
    public int getTobaccoAmount() { return tobaccoAmount; }
    public int getPaperAmount() { return paperAmount; }
    public double getOperatorMastery() { return operatorMastery; }
    public void setOperatorMastery(double value) {
        this.operatorMastery = Math.max(0.0, Math.min(1.0, value));
    }
    public int getOperatorFlavorPredisposition() { return operatorFlavorPredisposition; }
    public void setOperatorFlavorPredisposition(int value) {
        this.operatorFlavorPredisposition = Math.max(-2, Math.min(2, value));
    }
    public ItemStack getTobaccoItem() { return tobaccoItem; }
    public void setTobaccoItem(ItemStack item) { this.tobaccoItem = item; }
    public ItemStack getPaperItem() { return paperItem; }
    public void setPaperItem(ItemStack item) { this.paperItem = item; }
    public ItemStack getFuelItem() { return fuelItem; }
    public void setFuelItem(ItemStack item) { this.fuelItem = item; updateFuelSlot(); }

    public boolean hasItems() { return hasItems; }
    public void setItems(CigaretteType type, int tobaccoCount, int paperCount, ItemStack tobaccoStack, ItemStack paperStack) {
        this.tobaccoType = type; this.tobaccoAmount = tobaccoCount; this.paperAmount = paperCount;
        this.hasItems = true; this.progress = 0;
        this.tobaccoItem = tobaccoStack; this.paperItem = paperStack;
        if (inventory != null) { inventory.setItem(0, tobaccoStack); inventory.setItem(1, paperStack); }
    }
    public void consumeItems() {
        tobaccoAmount -= 8;
        paperAmount -= 1;
        progress = 0;

        if (tobaccoAmount < 8) {
            tobaccoType = null;
        }

        if (tobaccoAmount < 8 || paperAmount < 1) {
            hasItems = false;
        }

        if (tobaccoItem != null) {
            if (tobaccoAmount > 0) {
                tobaccoItem.setAmount(tobaccoAmount);
            } else {
                tobaccoItem = null;
            }
        }
        if (paperItem != null) {
            if (paperAmount > 0) {
                paperItem.setAmount(paperAmount);
            } else {
                paperItem = null;
            }
        }

        if (inventory != null) {
            updateInventory();
        }
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, 9, Component.text("Rolling Press"));
            if (tobaccoItem != null) inventory.setItem(0, tobaccoItem);
            if (paperItem != null) inventory.setItem(1, paperItem);
            if (fuelItem != null) inventory.setItem(2, fuelItem);
        }
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
        this.tobaccoItem = inventory.getItem(0);
        this.paperItem = inventory.getItem(1);
        this.fuelItem = inventory.getItem(2);
        if (tobaccoItem != null) tobaccoAmount = tobaccoItem.getAmount();
        if (paperItem != null) paperAmount = paperItem.getAmount();
        hasItems = tobaccoItem != null && paperItem != null;
    }

    public void updateInventory() {
        if (inventory != null) {
            inventory.setItem(0, tobaccoItem);
            inventory.setItem(1, paperItem);
            inventory.setItem(2, fuelItem);
        }
    }
}
