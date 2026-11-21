package com.cigaro.data;

import org.bukkit.entity.Player;

import java.util.Locale;

public class BlendData {

    private final CigaretteType primary;
    private final CigaretteType secondary;
    private final double primaryRatio;

    public BlendData(CigaretteType primary, CigaretteType secondary,
                     double primaryRatio) {
        this.primary = primary;
        this.secondary = secondary;
        this.primaryRatio = Math.max(0.3, Math.min(0.7, primaryRatio));
    }

    public CigaretteType getPrimary() { return primary; }
    public CigaretteType getSecondary() { return secondary; }
    public double getPrimaryRatio() { return primaryRatio; }
    public double getSecondaryRatio() { return 1.0 - primaryRatio; }

    public int getBlendedPredisposition(PlayerData data) {
        int predA = data.getPredisposition(primary);
        int predB = data.getPredisposition(secondary);
        double blended = predA * primaryRatio + predB * getSecondaryRatio();
        return (int) Math.round(blended);
    }

    public double getBlendedTolerance(PlayerData data) {
        double tolA = data.getTolerance(primary);
        double tolB = data.getTolerance(secondary);
        return tolA * primaryRatio + tolB * getSecondaryRatio();
    }

    public String serialize() {
        return primary.name() + ":" + secondary.name() + ":"
                + String.format(Locale.US, "%.2f", primaryRatio);
    }

    public static BlendData deserialize(String data) {
        if (data == null || data.isEmpty()) return null;
        String[] parts = data.split(":");
        if (parts.length != 3) return null;
        try {
            CigaretteType a = CigaretteType.valueOf(parts[0]);
            CigaretteType b = CigaretteType.valueOf(parts[1]);
            double ratio = Double.parseDouble(parts[2]);
            return new BlendData(a, b, ratio);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        int pct = (int) (primaryRatio * 100);
        return pct + "% " + primary.name() + " / "
                + (100 - pct) + "% " + secondary.name();
    }
}
