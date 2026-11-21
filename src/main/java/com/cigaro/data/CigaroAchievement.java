package com.cigaro.data;

import com.cigaro.CigaroColors;
import net.kyori.adventure.text.format.TextColor;

public enum CigaroAchievement {

    FIRST_DRAG(0, "First Drag",
            "Smoke your first cigarette",
            CigaroColors.SMOKE),

    CHAIN_SMOKER(1, "Chain Smoker",
            "Smoke 50 cigarettes",
            CigaroColors.EMBER),

    CHIMNEY(2, "Human Chimney",
            "Smoke 100 cigarettes",
            CigaroColors.ASH),

    ASHTRAY(3, "Walking Ashtray",
            "Smoke 500 cigarettes",
            CigaroColors.DARK),

    CONNOISSEUR(4, "Connoisseur",
            "Try all 8 flavors",
            CigaroColors.GOLD),

    FAVORITE_FOUND(5, "Love at First Puff",
            "Smoke your favorite type",
            CigaroColors.CHERRY),

    BAD_CHOICE(6, "Bad Choice",
            "Smoke your catastrophic type",
            CigaroColors.ERROR),

    HOOKED(7, "Hooked",
            "Reach addiction stage 2",
            CigaroColors.WARNING),

    ROCK_BOTTOM(8, "Rock Bottom",
            "Reach addiction stage 4",
            CigaroColors.ERROR),

    CLEAN(9, "Clean",
            "Fully recover from addiction",
            CigaroColors.SUCCESS),

    RELAPSE(10, "Relapse",
            "Smoke again after quitting",
            CigaroColors.EMBER),

    FIRST_HARVEST(11, "Green Thumb",
            "Harvest your first tobacco plant",
            CigaroColors.HERBAL),

    MASTER_ROLLER(12, "Master Roller",
            "Reach 50% mastery",
            CigaroColors.GOLD),

    PERFECTIONIST(13, "Perfectionist",
            "Roll a Vintage cigarette",
            CigaroColors.VINTAGE),

    HOOKAH_PARTY(14, "Hookah Party",
            "Smoke hookah with 3+ players nearby",
            CigaroColors.SMOKE),

    BLENDER(15, "Mad Scientist",
            "Create your first blend",
            CigaroColors.GOLD),

    COUGH_BLOOD(16, "Seeing Red",
            "Cough blood for the first time",
            CigaroColors.ERROR),

    FAKE_DIAMONDS(17, "Fool's Gold",
            "See fake ores from withdrawal",
            CigaroColors.WARNING),

    INSOMNIA(18, "Sleepless",
            "Fail to sleep due to addiction",
            CigaroColors.SHADOW),

    PIPE_BROKEN(19, "Old Faithful",
            "Break a pipe from overuse",
            CigaroColors.ASH);

    private final int bit;
    private final String displayName;
    private final String description;
    private final TextColor color;

    CigaroAchievement(int bit, String displayName,
                      String description, TextColor color) {
        this.bit = bit;
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public int getBit() { return bit; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public TextColor getColor() { return color; }

    public long getMask() { return 1L << bit; }
}
