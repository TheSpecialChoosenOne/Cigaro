package com.cigaro.station;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PackingStation {

    private final Location location;

    private final List<ItemStack> loadedCigarettes = new ArrayList<>();
    private DyeColor dyeColor;
    private int cranksRemaining = 0;
    private boolean hasItems = false;

    public PackingStation(Location location) {
        this.location = normalizeLocation(location);
    }

    public Location getLocation() { return location; }

    public List<ItemStack> getLoadedCigarettes() {
        return loadedCigarettes;
    }

    public DyeColor getDyeColor() { return dyeColor; }
    public void setDyeColor(DyeColor color) { this.dyeColor = color; }

    public int getCranksRemaining() { return cranksRemaining; }
    public void setCranksRemaining(int v) { this.cranksRemaining = v; }
    public void decrementCrank() {
        this.cranksRemaining = Math.max(0, cranksRemaining - 1);
    }

    public boolean hasItems() { return hasItems; }
    public void setHasItems(boolean v) { this.hasItems = v; }

    public boolean isReadyToCrank() {
        return hasItems && dyeColor != null && cranksRemaining > 0;
    }

    public boolean isDone() {
        return hasItems && dyeColor != null && cranksRemaining <= 0;
    }

    public void loadCigarettes(List<ItemStack> cigs) {
        loadedCigarettes.clear();
        loadedCigarettes.addAll(cigs);
        hasItems = true;
        cranksRemaining = 5;
    }

    public void clear() {
        loadedCigarettes.clear();
        dyeColor = null;
        cranksRemaining = 0;
        hasItems = false;
    }

    private Location normalizeLocation(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackingStation that = (PackingStation) o;
        return location.getBlockX() == that.location.getBlockX()
                && location.getBlockY() == that.location.getBlockY()
                && location.getBlockZ() == that.location.getBlockZ()
                && Objects.equals(
                location.getWorld(), that.location.getWorld());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }
}