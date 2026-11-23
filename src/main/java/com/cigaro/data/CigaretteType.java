package com.cigaro.data;

import com.cigaro.CigaroColors;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class CigaretteType {
    private static final Map<String, CigaretteType> REGISTRY = new LinkedHashMap<>();
    private static int nextOrdinal = 0;

    private final String id;
    private final String displayName;
    private final TextColor color;
    private final Material rawMaterial;
    private final int textureOffset;
    private final int ordinal;

    public CigaretteType(String id, String displayName, TextColor color, Material rawMaterial, int textureOffset) {
        this.id = id.toUpperCase();
        this.displayName = displayName;
        this.color = color;
        this.rawMaterial = rawMaterial;
        this.textureOffset = textureOffset;
        this.ordinal = nextOrdinal++;
    }

    public static final CigaretteType MINT = register(new CigaretteType("MINT", "Mint", CigaroColors.MINT, Material.FERN, 0));
    public static final CigaretteType COFFEE = register(new CigaretteType("COFFEE", "Coffee", CigaroColors.COFFEE, Material.COCOA_BEANS, 10));
    public static final CigaretteType CARAMEL = register(new CigaretteType("CARAMEL", "Caramel", CigaroColors.CARAMEL, Material.HONEYCOMB, 20));
    public static final CigaretteType VANILLA = register(new CigaretteType("VANILLA", "Vanilla", CigaroColors.VANILLA, Material.WHITE_DYE, 30));
    public static final CigaretteType CHERRY = register(new CigaretteType("CHERRY", "Cherry", CigaroColors.CHERRY, Material.PINK_PETALS, 40));
    public static final CigaretteType SPICE = register(new CigaretteType("SPICE", "Spice", CigaroColors.SPICE, Material.BLAZE_POWDER, 50));
    public static final CigaretteType HERBAL = register(new CigaretteType("HERBAL", "Herbal", CigaroColors.HERBAL, Material.SPORE_BLOSSOM, 60));
    public static final CigaretteType SHADOW = register(new CigaretteType("SHADOW", "Shadow", CigaroColors.SHADOW, Material.ECHO_SHARD, 70));

    public static CigaretteType register(CigaretteType type) {
        if (REGISTRY.putIfAbsent(type.name(), type) != null) {
            throw new IllegalArgumentException("Cigarette type already registered: " + type.name());
        }
        return type;
    }

    public static Collection<CigaretteType> values() { return java.util.Collections.unmodifiableCollection(REGISTRY.values()); }

    public static CigaretteType valueOf(String name) {
        CigaretteType type = REGISTRY.get(name.toUpperCase());
        if (type == null) throw new IllegalArgumentException("No enum constant " + name);
        return type;
    }

    public String name() { return id; }
    public int ordinal() { return ordinal; }
    public String getDisplayName() { return displayName; }
    public TextColor getColor() { return color; }
    public Material getRawMaterial() { return rawMaterial; }
    public int getTextureOffset() { return textureOffset; }
}
