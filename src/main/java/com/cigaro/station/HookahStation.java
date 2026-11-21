package com.cigaro.station;

import com.cigaro.data.BlendData;
import com.cigaro.data.CigaretteType;
import com.cigaro.data.HookahData;
import org.bukkit.Location;

import java.util.*;

public class HookahStation {

    private final Location location;
    private final HookahData data;
    private UUID displayEntityId;

    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private static final long PUFF_COOLDOWN_MS = 3000;

    public HookahStation(Location location) {
        this.location = normalizeLocation(location);
        this.data = new HookahData();
    }

    public HookahStation(Location location, HookahData data) {
        this.location = normalizeLocation(location);
        this.data = data;
    }

    public Location getLocation() { return location; }
    public HookahData getData() { return data; }

    public UUID getDisplayEntityId() { return displayEntityId; }
    public void setDisplayEntityId(UUID id) {
        this.displayEntityId = id;
    }

    public boolean canPuff(UUID playerUuid) {
        Long last = playerCooldowns.get(playerUuid);
        if (last == null) return true;
        return System.currentTimeMillis() - last >= PUFF_COOLDOWN_MS;
    }

    public void recordPuff(UUID playerUuid) {
        playerCooldowns.put(playerUuid,
                System.currentTimeMillis());
    }


    public boolean isReady() { return data.isReady(); }

    public void addWater() { data.addWater(); }

    public void fill(CigaretteType type) { data.fill(type); }

    public void fillBlend(BlendData blend) {
        data.fillBlend(blend);
    }

    public void puff() { data.puff(); }

    public boolean hasWater() { return data.hasWater(); }

    public int getChargesLeft() { return data.getChargesLeft(); }

    public CigaretteType getFlavorType() {
        return data.getFlavorType();
    }

    public BlendData getBlendData() { return data.getBlendData(); }

    public boolean isBlend() { return data.isBlend(); }


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
        HookahStation that = (HookahStation) o;
        return location.getBlockX() == that.location.getBlockX()
                && location.getBlockY() == that.location.getBlockY()
                && location.getBlockZ() == that.location.getBlockZ()
                && Objects.equals(location.getWorld(),
                that.location.getWorld());
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
