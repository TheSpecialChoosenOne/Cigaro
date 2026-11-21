package com.cigaro.data;

public enum DryingQuality {
    UNDERDRIED("Underdried", -2),
    PERFECT("Perfect", 2),
    OVERDRIED("Overdried", -1),
    RUINED("Ruined", -3);

    private final String displayName;
    private final int qualityBonus;

    DryingQuality(String displayName, int qualityBonus) {
        this.displayName = displayName;
        this.qualityBonus = qualityBonus;
    }

    public String getDisplayName() { return displayName; }
    public int getQualityBonus() { return qualityBonus; }
}