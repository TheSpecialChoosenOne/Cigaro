package com.cigaro.data;

public enum SmokableType {
    CIGARETTE("cigarette", 1.0, false),
    CIGAR("cigar", 2.5, true),
    PIPE("pipe", 0.7, false),
    HOOKAH("hookah", 0.6, false);

    private final String id;
    private final double effectMultiplier;
    private final boolean singlePuff;

    SmokableType(String id, double effectMultiplier, boolean singlePuff) {
        this.id = id;
        this.effectMultiplier = effectMultiplier;
        this.singlePuff = singlePuff;
    }

    public String getId() { return id; }
    public double getEffectMultiplier() { return effectMultiplier; }
    public boolean isSinglePuff() { return singlePuff; }
}
