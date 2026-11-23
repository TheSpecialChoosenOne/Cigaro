package com.cigaro.data;

import org.bukkit.entity.Player;


public class BlendData {

    private final CigaretteType primary;
    private final CigaretteType secondary;
    private final double primaryRatio;
    private final TobaccoType primaryTobacco;
    private final TobaccoType secondaryTobacco;

    public BlendData(CigaretteType primary, CigaretteType secondary,
                     double primaryRatio) {
        this(primary, secondary, primaryRatio, null, null);
    }

    public BlendData(CigaretteType primary, CigaretteType secondary, double primaryRatio,
                     TobaccoType primaryTobacco, TobaccoType secondaryTobacco) {
        this.primary = primary;
        this.secondary = secondary;
        this.primaryRatio = Math.max(0.3, Math.min(0.7, primaryRatio));
        this.primaryTobacco = primaryTobacco;
        this.secondaryTobacco = secondaryTobacco;
    }

    public CigaretteType getPrimary() { return primary; }
    public CigaretteType getSecondary() { return secondary; }
    public double getPrimaryRatio() { return primaryRatio; }
    public double getSecondaryRatio() { return 1.0 - primaryRatio; }
    public TobaccoType getPrimaryTobacco() { return primaryTobacco; }
    public TobaccoType getSecondaryTobacco() { return secondaryTobacco; }

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
        String ratio = Double.toString(primaryRatio);
        if (primaryTobacco != null && secondaryTobacco != null)
            ratio += "~" + primaryTobacco.name() + "~" + secondaryTobacco.name();
        return primary.name() + ":" + secondary.name() + ":" + ratio;
    }

    public static BlendData deserialize(String data) {
        if (data == null || data.isEmpty()) return null;
        String[] parts = data.split(":");
        if (parts.length != 3) return null;
        try {
            CigaretteType a = CigaretteType.valueOf(parts[0]);
            CigaretteType b = CigaretteType.valueOf(parts[1]);
            String[] details = parts[2].split("~");
            if (details.length != 1 && details.length != 3) return null;
            double ratio = Double.parseDouble(details[0]);
            if (!Double.isFinite(ratio)) return null;
            return details.length == 3
                    ? new BlendData(a, b, ratio, TobaccoType.valueOf(details[1]), TobaccoType.valueOf(details[2]))
                    : new BlendData(a, b, ratio);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        int pct = (int) Math.round(primaryRatio * 100);
        return pct + "% " + primary.name() + " / "
                + (100 - pct) + "% " + secondary.name();
    }
}
