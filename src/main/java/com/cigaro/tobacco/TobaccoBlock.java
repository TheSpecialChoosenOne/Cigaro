package com.cigaro.tobacco;

import com.cigaro.data.TobaccoType;
import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

public class TobaccoBlock {

    private final Location location;
    private final TobaccoType tobaccoType;
    private int stage;
    private int fertilizerBonus;
    private UUID displayId;
    private UUID interactionId;

    public TobaccoBlock(Location location, TobaccoType tobaccoType, int stage) {
        this.location = new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
        this.tobaccoType = tobaccoType;
        this.stage = stage;
        this.fertilizerBonus = 0;
    }

    public TobaccoBlock(Location location, TobaccoType tobaccoType, int stage, long lastGrowthTime) {
        this(location, tobaccoType, stage);
    }

    public Location getLocation() { return location; }
    public TobaccoType getTobaccoType() { return tobaccoType; }
    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }
    public int getFertilizerBonus() { return fertilizerBonus; }
    public void setFertilizerBonus(int bonus) { this.fertilizerBonus = Math.min(6, bonus); }
    public void addFertilizerBonus(int amount) { this.fertilizerBonus = Math.min(6, this.fertilizerBonus + amount); }
    public UUID getDisplayId() { return displayId; }
    public void setDisplayId(UUID displayId) { this.displayId = displayId; }
    public UUID getInteractionId() { return interactionId; }
    public void setInteractionId(UUID interactionId) { this.interactionId = interactionId; }

    public int getMaxStage() { return 3; }
    public boolean isFullyGrown() { return stage >= getMaxStage(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TobaccoBlock that = (TobaccoBlock) o;
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