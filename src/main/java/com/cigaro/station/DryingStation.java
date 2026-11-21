package com.cigaro.station;

import com.cigaro.data.TobaccoType;
import org.bukkit.Location;

import java.util.*;

public class DryingStation {

    private final Location location;

    private final List<LeafEntry> leafEntries = new ArrayList<>();

    private List<UUID> displayEntityIds = new ArrayList<>();
    private UUID blockDisplayId;

    private Boolean openSkyCache;
    private long openSkyCheckedAt;
    private static final long OPEN_SKY_CACHE_MS = 30_000;

    public DryingStation(Location location) {
        this.location = normalizeLocation(location);
    }

    public boolean isOpenSky() {
        long now = System.currentTimeMillis();
        if (openSkyCache == null || now - openSkyCheckedAt > OPEN_SKY_CACHE_MS) {
            if (location.getWorld() == null) return true;
            openSkyCache = location.getWorld().getHighestBlockYAt(location)
                    <= location.getBlockY() + 1;
            openSkyCheckedAt = now;
        }
        return openSkyCache;
    }

    public void invalidateSkyCache() {
        openSkyCache = null;
        openSkyCheckedAt = 0L;
    }


    public static class LeafEntry {
        private final TobaccoType tobaccoType;
        private final boolean elite;
        private long startTime;

        public LeafEntry(TobaccoType tobaccoType, boolean elite, long startTime) {
            this.tobaccoType = tobaccoType;
            this.elite = elite;
            this.startTime = startTime;
        }

        public TobaccoType getTobaccoType() { return tobaccoType; }
        public boolean isElite() { return elite; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long time) { this.startTime = time; }

        public int getElapsedSeconds() {
            return (int) ((System.currentTimeMillis() - startTime) / 1000);
        }
    }


    public Location getLocation() { return location; }

    public List<LeafEntry> getLeafEntries() { return leafEntries; }

    public int getTotalLeaves() { return leafEntries.size(); }
    public boolean isEmpty() { return leafEntries.isEmpty(); }
    public boolean isFull() { return leafEntries.size() >= 16; }

    public boolean addLeaf(TobaccoType type, boolean elite, int alreadyDriedSeconds) {
        if (isFull()) return false;
        long startTime = System.currentTimeMillis() - (alreadyDriedSeconds * 1000L);
        leafEntries.add(new LeafEntry(type, elite, startTime));
        return true;
    }
    public int addLeaves(TobaccoType type, int amount, boolean elite, int alreadyDriedSeconds) {
        int added = 0;
        for (int i = 0; i < amount; i++) {
            if (addLeaf(type, elite, alreadyDriedSeconds)) added++;
            else break;
        }
        return added;
    }

    public LeafEntry removeLastLeaf() {
        if (leafEntries.isEmpty()) return null;
        return leafEntries.remove(leafEntries.size() - 1);
    }

    public List<LeafEntry> removeAllLeaves() {
        List<LeafEntry> removed = new ArrayList<>(leafEntries);
        leafEntries.clear();
        return removed;
    }

    public void clear() {
        leafEntries.clear();
    }


    private UUID displayEntityId;

    public UUID getDisplayEntityId() { return displayEntityId; }
    public void setDisplayEntityId(UUID id) { this.displayEntityId = id; }

    public List<UUID> getDisplayEntityIds() { return displayEntityIds; }
    public void setDisplayEntityIds(List<UUID> ids) { this.displayEntityIds = ids; }

    public UUID getBlockDisplayId() { return blockDisplayId; }
    public void setBlockDisplayId(UUID id) { this.blockDisplayId = id; }


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
        DryingStation that = (DryingStation) o;
        return location.getBlockX() == that.location.getBlockX()
                && location.getBlockY() == that.location.getBlockY()
                && location.getBlockZ() == that.location.getBlockZ()
                && Objects.equals(location.getWorld(), that.location.getWorld());
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