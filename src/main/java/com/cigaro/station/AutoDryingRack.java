package com.cigaro.station;

import com.cigaro.data.TobaccoType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AutoDryingRack implements InventoryHolder {

    public static final int FUEL_SECONDS_PER_PELLET = 20;
    public static final int DRYING_SPEED_MULTIPLIER = 2;

    private final Location location;
    private final List<AutoDryEntry> leafEntries;
    private int fuelSeconds;
    private UUID blockDisplayId;

    private transient Inventory inventory;
    private final List<ItemStack> storedItems;

    public AutoDryingRack(Location location) {
        this.location = location;
        this.leafEntries = new ArrayList<>();
        this.fuelSeconds = 0;
        this.storedItems = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) storedItems.add(null);
    }

    public Location getLocation() { return location; }
    public List<AutoDryEntry> getLeafEntries() { return leafEntries; }
    public int getFuelSeconds() { return fuelSeconds; }
    public void setFuelSeconds(int seconds) { this.fuelSeconds = seconds; }
    public void addFuel(int seconds) { this.fuelSeconds += seconds; }

    public ItemStack getFuelItem() { return storedItems.get(4); }
    public void setFuelItem(ItemStack item) {
        storedItems.set(4, item);
        updateFuelSlot();
    }

    public void addFuel(int seconds, ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) return;

        ItemStack current = getFuelItem();
        int currentAmount = current == null ? 0 : current.getAmount();
        int accepted = Math.min(stack.getAmount(), 64 - currentAmount);
        if (accepted <= 0) return;

        if (accepted < stack.getAmount()) {
            seconds = (int) Math.round((double) seconds * accepted / stack.getAmount());
        }
        this.fuelSeconds += seconds;

        ItemStack toStore = stack.clone();
        toStore.setAmount(accepted);
        if (current != null && current.isSimilar(toStore)) {
            current.setAmount(current.getAmount() + accepted);
            setFuelItem(current);
        } else {
            setFuelItem(toStore);
        }
    }

    public void syncFuelItem() {
        ItemStack current = getFuelItem();
        if (current == null) return;
        int pellets = (fuelSeconds + FUEL_SECONDS_PER_PELLET - 1) / FUEL_SECONDS_PER_PELLET;
        if (pellets <= 0) {
            setFuelItem(null);
        } else if (current.getAmount() != pellets) {
            current.setAmount(Math.min(64, pellets));
            setFuelItem(current);
        }
    }

    private void updateFuelSlot() {
        if (inventory != null) inventory.setItem(4, getFuelItem());
    }

    public boolean hasFuel() { return fuelSeconds > 0; }

    public boolean addLeaf(TobaccoType type, boolean elite, ItemStack item) {
        return addLeaf(type, elite, item, 0);
    }

    public boolean addLeaf(TobaccoType type, boolean elite, ItemStack item, int alreadyDriedSeconds) {
        if (leafEntries.size() >= 4) return false;
        int slot = leafEntries.size();
        long startTime = System.currentTimeMillis()
                - (Math.max(0, alreadyDriedSeconds) * 1000L / DRYING_SPEED_MULTIPLIER);
        leafEntries.add(new AutoDryEntry(type, elite, startTime));
        storedItems.set(slot, item);
        if (inventory != null) inventory.setItem(slot, item);
        return true;
    }

    public boolean hasSpace() { return leafEntries.size() < 4; }
    public int getSlotCount() { return 4; }

    public UUID getBlockDisplayId() { return blockDisplayId; }
    public void setBlockDisplayId(UUID id) { this.blockDisplayId = id; }

    public void removeLeaf(int index) {
        if (index < 0 || index >= leafEntries.size()) return;
        leafEntries.remove(index);
        storedItems.remove(index);
        storedItems.add(3, null);
        updateInventory();
    }

    public ItemStack takeLeaf(int index) {
        if (index == 4) {
            syncFuelItem();
            ItemStack fuel = getFuelItem();
            setFuelItem(null);
            this.fuelSeconds = 0;
            return fuel;
        }
        if (index >= 0 && index < leafEntries.size()) {
            ItemStack item = storedItems.get(index);
            removeLeaf(index);
            return item;
        }
        return null;
    }

    public List<ItemStack> getStoredItems() { return storedItems; }

    @Override
    @NotNull
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, 9, Component.text("Auto Drying Rack"));
            for (int i = 0; i < 5; i++) {
                if (storedItems.get(i) != null) {
                    inventory.setItem(i, storedItems.get(i));
                }
            }
        }
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
        for (int i = 0; i < 5; i++) {
            storedItems.set(i, inventory.getItem(i));
        }
    }

    public void updateInventory() {
        if (inventory != null) {
            for (int i = 0; i < 5; i++) {
                inventory.setItem(i, storedItems.get(i));
            }
        }
    }

    public static class AutoDryEntry {
        private final TobaccoType tobaccoType;
        private final boolean elite;
        private long startTime;

        public AutoDryEntry(TobaccoType tobaccoType, boolean elite, long startTime) {
            this.tobaccoType = tobaccoType;
            this.elite = elite;
            this.startTime = startTime;
        }

        public TobaccoType getTobaccoType() { return tobaccoType; }
        public boolean isElite() { return elite; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long time) { this.startTime = time; }
        public long getElapsedSeconds() { return (System.currentTimeMillis() - startTime) / 1000; }
        public int getEffectiveSeconds() { return (int) (getElapsedSeconds() * DRYING_SPEED_MULTIPLIER); }
    }
}

