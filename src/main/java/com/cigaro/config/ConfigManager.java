package com.cigaro.config;

import com.cigaro.Cigaro;
import com.cigaro.data.DryingLevel;
import org.bukkit.configuration.file.FileConfiguration;
import com.cigaro.data.CigaretteType;
import com.cigaro.data.SmokeEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfigManager {
    private int withdrawalGraceMinutes;
    private double seedDropChance;
    private final Cigaro plugin;
    private FileConfiguration config;
    private int dryingDampThreshold;
    private int dryingDriedThreshold;
    private int dryingPerfectThreshold;
    private int dryingOverdriedThreshold;
    private boolean showPlantMessages;
    private boolean showHarvestMessages;
    private double eliteLeafChance;
    private boolean boneMealEnabled;

    private final Map<CigaretteType, List<SmokeEffect>> smokeEffects
            = new HashMap<>();
    private List<SmokeEffect> badSmokeDebuffs = new ArrayList<>();
    private List<SmokeEffect> catastrophicSmokeDebuffs = new ArrayList<>();
    private int rollingTimeSeconds;
    private int cigarettesPerBatch;

    private long puffCooldownMs;
    private int cigaretteAnimationTicks;
    private int cigarLightingTicks;
    private int sessionTimeoutSeconds;

    private double cherryDropChance;
    private double spiceDropChance;
    private double herbalDropChance;
    private double shadowDropChance;
    private double mintDropChance;
    private double coffeeDropChance;
    private double caramelDropChance;
    private double vanillaDropChance;
    private String language;
    private int stage1Threshold;
    private int stage2Threshold;
    private int stage3Threshold;
    private int stage4Threshold;
    private int withdrawalCheckSeconds;
    private int quitTimeHours;

    private double tolerancePerSmoke;
    private double toleranceDecayPerHour;

    private int patchCooldownMinutes;
    private int lungDamageStage1;
    private int lungDamageStage2;
    private int lungDamageStage3;
    private boolean lungRecoveryEnabled;
    private int lungRecoveryDaysPerHeart;

    public ConfigManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        seedDropChance = config.getDouble("tobacco.seed-drop-chance", 0.005);
        showPlantMessages = config.getBoolean(
                "messages.show-plant-messages", true
        );
        showHarvestMessages = config.getBoolean(
                "messages.show-harvest-messages", true
        );
        language = config.getString("language", "en_us");

        cherryDropChance = config.getDouble("flavors.cherry-drop-chance", 0.06);
        spiceDropChance = config.getDouble("flavors.spice-drop-chance", 0.05);
        herbalDropChance = config.getDouble("flavors.herbal-drop-chance", 0.05);
        shadowDropChance = config.getDouble("flavors.shadow-drop-chance", 0.03);
        withdrawalGraceMinutes = config.getInt(
                "addiction.withdrawal-grace-minutes", 25
        );

        eliteLeafChance = config.getDouble(
                "tobacco.elite-leaf-chance", 0.03
        );
        boneMealEnabled = config.getBoolean(
                "tobacco.bone-meal-enabled", true
        );
        dryingDampThreshold = config.getInt("drying.thresholds.damp", 60);
        dryingDriedThreshold = config.getInt("drying.thresholds.dried", 180);
        dryingPerfectThreshold = config.getInt("drying.thresholds.perfect", 300);
        dryingOverdriedThreshold = config.getInt("drying.thresholds.overdried", 420);

        rollingTimeSeconds = config.getInt("rolling.time-seconds", 30);
        cigarettesPerBatch = config.getInt(
                "rolling.cigarettes-per-batch", 10
        );

        puffCooldownMs = config.getLong("smoking.puff-cooldown-ms", 2500);
        cigaretteAnimationTicks = config.getInt("smoking.cigarette-animation-ticks", 16);
        cigarLightingTicks = config.getInt("smoking.cigar-lighting-ticks", 60);
        sessionTimeoutSeconds = config.getInt("smoking.session-timeout-seconds", 30);

        mintDropChance = config.getDouble(
                "flavors.mint-drop-chance", 0.08
        );
        coffeeDropChance = config.getDouble(
                "flavors.coffee-drop-chance", 0.08
        );
        caramelDropChance = config.getDouble(
                "flavors.caramel-drop-chance", 0.08
        );
        vanillaDropChance = config.getDouble(
                "flavors.vanilla-drop-chance", 0.08
        );

        stage1Threshold = config.getInt(
                "addiction.stage1-threshold", 5
        );
        stage2Threshold = config.getInt(
                "addiction.stage2-threshold", 15
        );
        stage3Threshold = config.getInt(
                "addiction.stage3-threshold", 35
        );
        stage4Threshold = config.getInt(
                "addiction.stage4-threshold", 70
        );
        withdrawalCheckSeconds = Math.max(1, config.getInt(
                "addiction.withdrawal-check-seconds", 60
        ));
        quitTimeHours = config.getInt("addiction.quit-time-hours", 4);

        tolerancePerSmoke = config.getDouble(
                "tolerance.per-smoke", 0.08
        );
        toleranceDecayPerHour = config.getDouble(
                "tolerance.decay-per-hour", 0.01
        );

        patchCooldownMinutes = config.getInt(
                "medical.patch-cooldown-minutes", 40
        );
        lungDamageStage1 = config.getInt(
                "medical.lung-damage.stage1-smokes", 500
        );
        lungDamageStage2 = config.getInt(
                "medical.lung-damage.stage2-smokes", 1000
        );
        lungDamageStage3 = config.getInt(
                "medical.lung-damage.stage3-smokes", 1500
        );
        lungRecoveryEnabled = config.getBoolean(
                "medical.lung-recovery.enabled", true
        );
        lungRecoveryDaysPerHeart = Math.max(1, config.getInt(
                "medical.lung-recovery.clean-days-per-heart", 5
        ));

        loadSmokeEffects();
        plugin.getLogger().info("Config loaded.");
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }
    public DryingLevel getDryingLevel(int seconds) {
        if (seconds >= dryingOverdriedThreshold) return DryingLevel.OVERDRIED;
        if (seconds >= dryingPerfectThreshold) return DryingLevel.PERFECT;
        if (seconds >= dryingDriedThreshold) return DryingLevel.DRIED;
        if (seconds >= dryingDampThreshold) return DryingLevel.DAMP;
        return DryingLevel.RAW;
    }
    private void loadSmokeEffects() {
        smokeEffects.clear();
        badSmokeDebuffs.clear();
        catastrophicSmokeDebuffs.clear();

        for (CigaretteType type : CigaretteType.values()) {
            String key = "effects." + type.name().toLowerCase();
            List<?> effectList = config.getList(key);

            if (effectList == null) {
                smokeEffects.put(type, new ArrayList<>());
                plugin.getLogger().warning(
                        "No effects configured for: " + type.name()
                );
                continue;
            }

            List<SmokeEffect> effects = parseEffectList(key);
            smokeEffects.put(type, effects);
        }

        badSmokeDebuffs = parseEffectList("bad-smoke-debuffs");
        catastrophicSmokeDebuffs = parseEffectList(
                "catastrophic-smoke-debuffs"
        );

        plugin.getLogger().info("Loaded smoke effects for "
                + smokeEffects.size() + " types.");
    }

    private List<SmokeEffect> parseEffectList(String configPath) {
        List<SmokeEffect> result = new ArrayList<>();

        List<Map<?, ?>> list = config.getMapList(configPath);
        for (Map<?, ?> entry : list) {
            String typeName = String.valueOf(entry.get("type"));

            String normalized = typeName.toLowerCase(Locale.ROOT);
            NamespacedKey effectKey = normalized.contains(":")
                    ? NamespacedKey.fromString(normalized)
                    : NamespacedKey.minecraft(normalized);
            PotionEffectType effectType = effectKey == null
                    ? null
                    : Registry.EFFECT.get(effectKey);

            if (effectType == null) {
                plugin.getLogger().warning(
                        "Unknown potion effect: " + typeName
                                + " in " + configPath
                );
                continue;
            }

            int duration = entry.containsKey("duration")
                    ? ((Number) entry.get("duration")).intValue()
                    : 60;
            int amplifier = entry.containsKey("amplifier")
                    ? ((Number) entry.get("amplifier")).intValue()
                    : 0;
            int favoriteBonus = entry.containsKey(
                    "favorite-bonus-amplifier")
                    ? ((Number) entry.get(
                    "favorite-bonus-amplifier")).intValue()
                    : 0;
            boolean sideEffect = entry.containsKey("is-side-effect")
                    && Boolean.parseBoolean(
                    String.valueOf(entry.get("is-side-effect")));

            result.add(new SmokeEffect(
                    effectType, duration, amplifier,
                    favoriteBonus, sideEffect
            ));
        }

        return result;
    }
    public int getThresholdStart(DryingLevel level) {
        return switch (level) {
            case RAW -> 0;
            case DAMP -> dryingDampThreshold;
            case DRIED -> dryingDriedThreshold;
            case PERFECT -> dryingPerfectThreshold;
            case OVERDRIED -> dryingOverdriedThreshold;
        };
    }
    public boolean isShowPlantMessages() { return showPlantMessages; }
    public boolean isShowHarvestMessages() { return showHarvestMessages; }

    public int getWithdrawalGraceMinutes() {
        return withdrawalGraceMinutes;
    }

    public double getCherryDropChance() { return cherryDropChance; }
    public double getSpiceDropChance() { return spiceDropChance; }
    public double getHerbalDropChance() { return herbalDropChance; }
    public double getShadowDropChance() { return shadowDropChance; }
    public double getEliteLeafChance() { return eliteLeafChance; }
    public boolean isBoneMealEnabled() { return boneMealEnabled; }

    public int getRollingTimeSeconds() { return rollingTimeSeconds; }
    public int getCigarettesPerBatch() { return cigarettesPerBatch; }

    public long getPuffCooldownMs() { return puffCooldownMs; }
    public int getCigaretteAnimationTicks() { return cigaretteAnimationTicks; }
    public int getCigarLightingTicks() { return cigarLightingTicks; }
    public int getSessionTimeoutSeconds() { return sessionTimeoutSeconds; }

    public double getSeedDropChance() { return seedDropChance; }
    public List<SmokeEffect> getSmokeEffects(CigaretteType type) {
        List<SmokeEffect> addon = com.cigaro.api.CigaroAPI.getRegisteredFlavorEffects(type);
        if (addon != null) return addon;
        return smokeEffects.getOrDefault(type, new ArrayList<>());
    }
    public String getLanguage() { return language; }
    public List<SmokeEffect> getBadSmokeDebuffs() {
        return badSmokeDebuffs;
    }

    public List<SmokeEffect> getCatastrophicSmokeDebuffs() {
        return catastrophicSmokeDebuffs;
    }
    public int getDryingDampThreshold() { return dryingDampThreshold; }
    public int getDryingDriedThreshold() { return dryingDriedThreshold; }
    public int getDryingPerfectThreshold() { return dryingPerfectThreshold; }
    public int getDryingOverdriedThreshold() { return dryingOverdriedThreshold; }
    public double getMintDropChance() { return mintDropChance; }
    public double getCoffeeDropChance() { return coffeeDropChance; }
    public double getCaramelDropChance() { return caramelDropChance; }
    public double getVanillaDropChance() { return vanillaDropChance; }

    public int getStage1Threshold() { return stage1Threshold; }
    public int getStage2Threshold() { return stage2Threshold; }
    public int getStage3Threshold() { return stage3Threshold; }
    public int getStage4Threshold() { return stage4Threshold; }
    public int getWithdrawalCheckSeconds() {
        return withdrawalCheckSeconds;
    }
    public int getQuitTimeHours() { return quitTimeHours; }

    public double getTolerancePerSmoke() { return tolerancePerSmoke; }
    public double getToleranceDecayPerHour() {
        return toleranceDecayPerHour;
    }

    public int getPatchCooldownMinutes() { return patchCooldownMinutes; }
    public int getLungDamageStage1() { return lungDamageStage1; }
    public int getLungDamageStage2() { return lungDamageStage2; }
    public int getLungDamageStage3() { return lungDamageStage3; }
    public boolean isLungRecoveryEnabled() { return lungRecoveryEnabled; }
    public int getLungRecoveryDaysPerHeart() { return lungRecoveryDaysPerHeart; }

    public long getLungRecoverySecondsPerHeart() {
        return lungRecoveryDaysPerHeart * 1200L;
    }
}
