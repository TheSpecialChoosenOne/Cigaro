package com.cigaro.data;

import com.cigaro.CigaroColors;
import net.kyori.adventure.text.format.TextColor;

public enum DryingLevel {
    RAW(0, "Raw", "·", -3),
    DAMP(1, "Damp", "·", -1),
    DRIED(2, "Dried", "·", 0),
    PERFECT(3, "Perfect", "·", 2),
    OVERDRIED(4, "Overdried", "·", -2);

    private final int level;
    private final String displayName;
    private final String symbol;
    private final int qualityBonus;

    DryingLevel(int level, String displayName, String symbol, int qualityBonus) {
        this.level = level;
        this.displayName = displayName;
        this.symbol = symbol;
        this.qualityBonus = qualityBonus;
    }

    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public String getSymbol() { return symbol; }
    public int getQualityBonus() { return qualityBonus; }

    public TextColor getColor() {
        return CigaroColors.forDrying(this);
    }
}