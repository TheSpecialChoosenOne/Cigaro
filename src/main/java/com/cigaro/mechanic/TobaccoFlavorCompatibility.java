package com.cigaro.mechanic;

import com.cigaro.data.CigaretteType;
import com.cigaro.data.FlavorHarmony;
import com.cigaro.data.TobaccoType;

import java.util.Set;

public final class TobaccoFlavorCompatibility {

    private TobaccoFlavorCompatibility() {}

    private record Profile(Set<CigaretteType> matched, Set<CigaretteType> clashed) {}

    private static final Profile VIRGINIA = new Profile(
            Set.of(CigaretteType.VANILLA, CigaretteType.CHERRY,
                    CigaretteType.CARAMEL, CigaretteType.HERBAL),
            Set.of(CigaretteType.MINT, CigaretteType.COFFEE,
                    CigaretteType.SPICE, CigaretteType.SHADOW)
    );

    private static final Profile BURLEY = new Profile(
            Set.of(CigaretteType.COFFEE, CigaretteType.CARAMEL,
                    CigaretteType.VANILLA, CigaretteType.CHERRY),
            Set.of(CigaretteType.MINT, CigaretteType.SPICE,
                    CigaretteType.HERBAL, CigaretteType.SHADOW)
    );

    private static final Profile ORIENTAL = new Profile(
            Set.of(CigaretteType.SPICE, CigaretteType.HERBAL,
                    CigaretteType.MINT, CigaretteType.CHERRY),
            Set.of(CigaretteType.COFFEE, CigaretteType.CARAMEL,
                    CigaretteType.VANILLA, CigaretteType.SHADOW)
    );

    private static final Profile DARK_FIRED = new Profile(
            Set.of(CigaretteType.SHADOW, CigaretteType.SPICE,
                    CigaretteType.CHERRY, CigaretteType.COFFEE),
            Set.of(CigaretteType.VANILLA, CigaretteType.CARAMEL,
                    CigaretteType.MINT, CigaretteType.HERBAL)
    );

    public static FlavorHarmony evaluate(TobaccoType tobacco, CigaretteType flavor) {
        if (tobacco == null || flavor == null) return FlavorHarmony.NEUTRAL;

        Profile profile = switch (tobacco.name()) {
            case "VIRGINIA" -> VIRGINIA;
            case "BURLEY" -> BURLEY;
            case "ORIENTAL" -> ORIENTAL;
            case "DARK_FIRED" -> DARK_FIRED;
            default -> null;
        };
        if (profile == null) return FlavorHarmony.NEUTRAL;

        if (profile.matched().contains(flavor)) return FlavorHarmony.MATCHED;
        if (profile.clashed().contains(flavor)) return FlavorHarmony.CLASHED;
        return FlavorHarmony.NEUTRAL;
    }
}
