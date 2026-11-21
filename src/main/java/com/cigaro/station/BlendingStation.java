package com.cigaro.station;

import org.bukkit.Location;

import java.util.Objects;

public class BlendingStation {

    private final Location location;

    public BlendingStation(Location location) {
        this.location = normalizeLocation(location);
    }

    public Location getLocation() { return location; }

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
        BlendingStation that = (BlendingStation) o;
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
