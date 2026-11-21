package com.cigaro.mechanic;

import com.cigaro.data.CigaretteQuality;
import com.cigaro.data.DryingLevel;
import com.cigaro.data.FlavorHarmony;
import com.cigaro.data.TobaccoType;

public final class CigaretteQualityCalculator {

    private CigaretteQualityCalculator() {}

    public static CigaretteQuality calculate(DryingLevel dryingLevel,
                                             int cutQuality,
                                             boolean eliteLeaf,
                                             TobaccoType leafType,
                                             double mastery,
                                             int flavorPredisposition,
                                             FlavorHarmony harmony) {
        double leafBonus = leafType != null ? leafType.getQualityBonus() : 0.0;
        return calculate(dryingLevel, cutQuality, eliteLeaf, leafBonus,
                mastery, flavorPredisposition, harmony);
    }

    public static CigaretteQuality calculate(DryingLevel dryingLevel,
                                             int cutQuality,
                                             boolean eliteLeaf,
                                             double leafQualityBonus,
                                             double mastery,
                                             int flavorPredisposition,
                                             FlavorHarmony harmony) {
        double points = 0.0;

        if (dryingLevel != null) points += dryingLevel.getQualityBonus();
        points += cutQuality;
        if (eliteLeaf) points += 2.0;
        points += leafQualityBonus;
        points += Math.max(0.0, Math.min(1.0, mastery)) * 8.0;
        points += flavorPredisposition * 0.5;

        CigaretteQuality quality = CigaretteQuality.fromPoints(points);
        return harmony == FlavorHarmony.MATCHED ? quality.upgrade() : quality;
    }
}
