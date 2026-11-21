package com.cigaro.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class TobaccoType {
    private static final Map<String, TobaccoType> REGISTRY = new LinkedHashMap<>();
    private static int nextOrdinal = 0;

    private final String id;
    private final String displayName;
    private final int baseGrowthTicks;
    private final int qualityBonus;
    private final String[] nativeBiomes;
    private final double nativeDropChance;
    private final int textureOffset;
    private final int ordinal;

    public static final double GLOBAL_DROP_CHANCE = 0.005;

    public TobaccoType(String id, String displayName, int baseGrowthTicks, int qualityBonus, String[] nativeBiomes, double nativeDropChance, int textureOffset) {
        this.id = id.toUpperCase();
        this.displayName = displayName;
        this.baseGrowthTicks = baseGrowthTicks;
        this.qualityBonus = qualityBonus;
        this.nativeBiomes = nativeBiomes;
        this.nativeDropChance = nativeDropChance;
        this.textureOffset = textureOffset;
        this.ordinal = nextOrdinal++;
    }

    public static final TobaccoType VIRGINIA = register(new TobaccoType("VIRGINIA", "Virginia", 4000, 0, new String[]{"PLAINS", "MEADOW", "FOREST"}, 0.05, 0));
    public static final TobaccoType BURLEY = register(new TobaccoType("BURLEY", "Burley", 6000, 1, new String[]{"BIRCH_FOREST", "FOREST", "FLOWER_FOREST"}, 0.05, 1));
    public static final TobaccoType ORIENTAL = register(new TobaccoType("ORIENTAL", "Oriental", 8000, 2, new String[]{"SAVANNA", "DESERT", "BADLANDS"}, 0.05, 2));
    public static final TobaccoType DARK_FIRED = register(new TobaccoType("DARK_FIRED", "Dark Fired", 10000, 3, new String[]{"TAIGA", "SNOWY_TAIGA", "GROVE"}, 0.05, 3));

    public static TobaccoType register(TobaccoType type) {
        REGISTRY.put(type.name(), type);
        return type;
    }

    public static Collection<TobaccoType> values() { return REGISTRY.values(); }

    public static TobaccoType valueOf(String name) {
        TobaccoType type = REGISTRY.get(name.toUpperCase());
        if (type == null) throw new IllegalArgumentException("No enum constant " + name);
        return type;
    }

    public String name() { return id; }
    public int ordinal() { return ordinal; }
    public String getDisplayName() { return displayName; }
    public int getBaseGrowthTicks() { return baseGrowthTicks; }
    public int getQualityBonus() { return qualityBonus; }
    public int getTextureOffset() { return textureOffset; }

    public boolean isNativeBiome(String biomeName) {
        for (String nativeBiome : nativeBiomes) {
            if (biomeName.contains(nativeBiome)) return true;
        }
        return false;
    }

    public double getDropChanceForBiome(String biomeName) {
        return isNativeBiome(biomeName) ? nativeDropChance : GLOBAL_DROP_CHANCE;
    }

    public static TobaccoType[] getRandomTypes() {
        return REGISTRY.values().toArray(new TobaccoType[0]);
    }
}
