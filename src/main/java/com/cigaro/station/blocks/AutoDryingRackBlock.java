package com.cigaro.station.blocks;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class AutoDryingRackBlock implements InventoryHolder {
    private final Location location;
    private Inventory inventory;

    public AutoDryingRackBlock(Location location) {
        this.location = location;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Location getLocation() {
        return location;
    }

    public Block getBlock() {
        return location.getBlock();
    }
}
