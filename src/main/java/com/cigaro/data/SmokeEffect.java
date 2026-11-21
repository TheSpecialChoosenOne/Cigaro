package com.cigaro.data;

import org.bukkit.potion.PotionEffectType;

public class SmokeEffect {

    private final PotionEffectType effectType;
    private final int baseDuration;
    private final int amplifier;
    private final int favoriteBonusAmplifier;
    private final boolean sideEffect;

    public SmokeEffect(PotionEffectType effectType,
                       int baseDuration,
                       int amplifier,
                       int favoriteBonusAmplifier,
                       boolean sideEffect) {
        this.effectType = effectType;
        this.baseDuration = baseDuration;
        this.amplifier = amplifier;
        this.favoriteBonusAmplifier = favoriteBonusAmplifier;
        this.sideEffect = sideEffect;
    }

    public PotionEffectType getEffectType() { return effectType; }
    public int getBaseDuration() { return baseDuration; }
    public int getAmplifier() { return amplifier; }
    public int getFavoriteBonusAmplifier() {
        return favoriteBonusAmplifier;
    }
    public boolean isSideEffect() { return sideEffect; }
}