package com.cigaro.data;

public enum CigaretteQuality {
    STANDARD(1.0, 3),
    BLUE(1.3, 4),
    GOLDEN(1.8, 5),
    VINTAGE(3.0, 6);

    private final double multiplier;
    private final int puffs;

    CigaretteQuality(double multiplier, int puffs) {
        this.multiplier = multiplier;
        this.puffs = puffs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getPuffs() {
        return puffs;
    }

    public CigaretteQuality upgrade() {
        return switch (this) {
            case STANDARD -> BLUE;
            case BLUE -> GOLDEN;
            case GOLDEN -> VINTAGE;
            case VINTAGE -> VINTAGE;
        };
    }

    public static CigaretteQuality fromPoints(double points) {
        if (points >= 15.0) return VINTAGE;
        if (points >= 9.0) return GOLDEN;
        if (points >= 4.0) return BLUE;
        return STANDARD;
    }
}
