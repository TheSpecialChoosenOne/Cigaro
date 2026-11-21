package com.cigaro.station;

import com.cigaro.data.BlendData;
import com.cigaro.data.CigaretteType;
import com.cigaro.data.DryingLevel;
import com.cigaro.data.TobaccoType;
import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

public class RollingStation {

    private final Location location;

    private boolean isCigarMode = false;
    private int cigarLeavesCount = 0;

    private boolean hasLeaf;
    private TobaccoType leafType;
    private boolean eliteLeaf;
    private DryingLevel dryingLevel;
    private int cutQuality;

    private BlendData blend;

    private boolean hasFlavor;
    private CigaretteType flavorType;
    private long startTime;

    private UUID displayEntityId;
    private UUID blockDisplayId;

    public RollingStation(Location location) {
        this.location = normalizeLocation(location);
        clear();
    }

    public Location getLocation() { return location; }

    public boolean hasLeaf() { return hasLeaf; }
    public TobaccoType getLeafType() { return leafType; }
    public boolean isEliteLeaf() { return eliteLeaf; }
    public DryingLevel getDryingLevel() { return dryingLevel; }
    public int getCutQuality() { return cutQuality; }

    public void loadLeaf(TobaccoType type, boolean elite, DryingLevel drying) {
        this.hasLeaf = true;
        this.leafType = type;
        this.eliteLeaf = elite;
        this.dryingLevel = drying;
    }

    public void loadBlendedLeaf(TobaccoType type, boolean elite, DryingLevel drying, BlendData blend) {
        loadLeaf(type, elite, drying);
        this.blend = blend;
    }

    public void setCutQuality(int v) { this.cutQuality = v; }

    public boolean hasFlavor() { return hasFlavor; }
    public CigaretteType getFlavorType() { return flavorType; }
    public long getStartTime() { return startTime; }

    public void addFlavor(CigaretteType type) {
        this.hasFlavor = true;
        this.flavorType = type;
        this.startTime = System.currentTimeMillis();
    }

    public boolean isRolling() {
        if (isCigarMode && cigarLeavesCount >= 3 && hasFlavor) return true;
        return hasLeaf && hasFlavor;
    }

    public boolean isDone(int rollingTimeSeconds) {
        if (!isRolling()) return false;
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed >= (rollingTimeSeconds * 1000L);
    }

    public int getSecondsRemaining(int rollingTimeSeconds) {
        if (!isRolling()) return rollingTimeSeconds;
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = (rollingTimeSeconds * 1000L) - elapsed;
        return Math.max(0, (int) (remaining / 1000));
    }

    public boolean isCigarMode() { return isCigarMode; }
    public int getCigarLeavesCount() { return cigarLeavesCount; }

    public void addCigarLeaf() {
        this.cigarLeavesCount++;
        this.isCigarMode = true;
    }

    public void restoreCigarMode(int leavesCount) {
        if (leavesCount <= 0) return;
        this.isCigarMode = true;
        this.cigarLeavesCount = leavesCount;
    }

    public BlendData getBlend() { return blend; }
    public void setBlend(BlendData blend) { this.blend = blend; }
    public boolean isBlend() { return blend != null; }

    public void clear() {
        this.hasLeaf = false;
        this.leafType = null;
        this.eliteLeaf = false;
        this.dryingLevel = null;
        this.cutQuality = 0;
        this.hasFlavor = false;
        this.flavorType = null;
        this.startTime = 0;
        this.isCigarMode = false;
        this.cigarLeavesCount = 0;
        this.blend = null;
    }

    public void setHasLeaf(boolean v) { this.hasLeaf = v; }
    public void setLeafType(TobaccoType type) { this.leafType = type; }
    public void setEliteLeaf(boolean v) { this.eliteLeaf = v; }
    public void setDryingLevel(DryingLevel level) { this.dryingLevel = level; }
    public void setHasFlavor(boolean v) { this.hasFlavor = v; }
    public void setFlavorType(CigaretteType type) { this.flavorType = type; }
    public void setStartTime(long time) { this.startTime = time; }

    public UUID getDisplayEntityId() { return displayEntityId; }
    public void setDisplayEntityId(UUID id) { this.displayEntityId = id; }
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
        RollingStation that = (RollingStation) o;
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