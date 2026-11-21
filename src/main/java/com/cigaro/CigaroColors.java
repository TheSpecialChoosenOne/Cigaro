package com.cigaro;

import com.cigaro.data.CigaretteType;
import net.kyori.adventure.text.format.TextColor;

public final class CigaroColors {

    private CigaroColors() {}

    public static final TextColor DARK = TextColor.color(0x8B7355);
    public static final TextColor GOLD = TextColor.color(0xC4A055);
    public static final TextColor SMOKE = TextColor.color(0x9E9E9E);
    public static final TextColor EMBER = TextColor.color(0xB85C38);
    public static final TextColor ASH = TextColor.color(0x6B6B6B);

    public static final TextColor CHERRY = TextColor.color(0xC2185B);
    public static final TextColor SPICE = TextColor.color(0xBF360C);
    public static final TextColor HERBAL = TextColor.color(0x558B2F);
    public static final TextColor SHADOW = TextColor.color(0x37474F);
    public static final TextColor MINT = TextColor.color(0x7BA68C);
    public static final TextColor COFFEE = TextColor.color(0x6F4E37);
    public static final TextColor CARAMEL = TextColor.color(0xC68E47);
    public static final TextColor VANILLA = TextColor.color(0xD4C5A9);

    public static final TextColor STANDARD = TextColor.color(0x808080);
    public static final TextColor GOLDEN = TextColor.color(0x8FAF7E);
    public static final TextColor BLUE = TextColor.color(0x7E9AB0);
    public static final TextColor VINTAGE = TextColor.color(0xC4A055);

    public static final TextColor RAW = TextColor.color(0x7A9E6B);
    public static final TextColor DAMP = TextColor.color(0x9E9E6B);
    public static final TextColor DRIED = TextColor.color(0xA68B5B);
    public static final TextColor PERFECT = TextColor.color(0xC4A055);
    public static final TextColor OVERDRIED = TextColor.color(0x8B4513);

    public static final TextColor SUCCESS = TextColor.color(0x8FAF7E);
    public static final TextColor WARNING = TextColor.color(0xC4A055);
    public static final TextColor ERROR = TextColor.color(0xB85C38);
    public static final TextColor INFO = TextColor.color(0x9E9E9E);

    public static TextColor forType(CigaretteType type) {
        return type.getColor();
    }

    public static TextColor forQuality(com.cigaro.data.CigaretteQuality quality) {
        return switch (quality) {
            case STANDARD -> STANDARD;
            case GOLDEN -> GOLDEN;
            case BLUE -> BLUE;
            case VINTAGE -> VINTAGE;
        };
    }

    public static TextColor forDrying(com.cigaro.data.DryingLevel level) {
        return switch (level) {
            case RAW -> RAW;
            case DAMP -> DAMP;
            case DRIED -> DRIED;
            case PERFECT -> PERFECT;
            case OVERDRIED -> OVERDRIED;
        };
    }
}