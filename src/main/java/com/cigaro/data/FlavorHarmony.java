package com.cigaro.data;

public enum FlavorHarmony {
    MATCHED(1.2),
    NEUTRAL(1.0),
    CLASHED(0.5),
    CARAVAN_MATCHED(1.1),
    CARAVAN_CLASHED(0.75);

    private final double effectMultiplier;

    FlavorHarmony(double effectMultiplier) {
        this.effectMultiplier = effectMultiplier;
    }

    public double getEffectMultiplier() {
        return effectMultiplier;
    }

    public static FlavorHarmony fromName(String name) {
        if (name == null || name.isEmpty()) return NEUTRAL;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return NEUTRAL;
        }
    }
}
