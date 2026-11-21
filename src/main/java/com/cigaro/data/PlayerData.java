package com.cigaro.data;

import com.cigaro.Cigaro;
import com.cigaro.config.ConfigManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData {

    private final UUID uuid;

    private static final Set<String> VANILLA_TYPES = Set.of(
            "MINT", "COFFEE", "CARAMEL", "VANILLA", "CHERRY", "SPICE", "HERBAL", "SHADOW"
    );

    private int addictionStage = 0;
    private int totalSmoked = 0;
    private long lastSmokeTime = 0;
    private long onlineSoberSeconds = 0;

    private final Map<String, Double> tolerances = new ConcurrentHashMap<>();
    private final Map<String, Integer> predispositions = new ConcurrentHashMap<>();
    private final Map<String, Integer> smokedCounts = new ConcurrentHashMap<>();

    private int rainSmokes = 0;
    private int groupSmokes = 0;

    private boolean preWarningSent = false;

    private double physicalScore = 0.0;
    private double psychologicalScore = 0.0;
    private int physicalStage = 0;
    private int psychologicalStage = 0;
    private long physicalSoberSeconds = 0;
    private long psychologicalSoberSeconds = 0;

    private int lungRecoveredHearts = 0;
    private long lungRecoverySeconds = 0;

    private boolean predispositionGenerated = false;
    private double mastery = 0.0;

    private long lastSessionStart = 0;
    private int smokedThisSession = 0;
    private int consecutiveDays = 0;
    private long lastDaySmoked = 0;

    private long lastTypeChangeTime = 0;
    private String lastSmokedType = "";
    private int sameTypeStreak = 0;

    private long patchAppliedTime = 0;
    private static final long PATCH_DURATION_MS = 10 * 60 * 1000;

    private boolean wasAddicted = false;
    private long achievements = 0;

    private transient volatile boolean dirty = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }

    public boolean isDirty() { return dirty; }
    public void markDirty() { this.dirty = true; }
    public void clearDirty() { this.dirty = false; }

    public int getAddictionStage() {
        return Math.max(physicalStage, psychologicalStage);
    }

    public void setAddictionStage(int stage) {
        int clamped = Math.max(0, Math.min(4, stage));
        this.addictionStage = clamped;
        this.physicalStage = clamped;
        this.psychologicalStage = clamped;

        if (clamped == 0) {
            this.physicalScore = 0.0;
            this.psychologicalScore = 0.0;
        } else {
            double threshold = stageThreshold(clamped);
            if (physicalScore < threshold) this.physicalScore = threshold;
            if (psychologicalScore < threshold) this.psychologicalScore = threshold;
        }
        markDirty();
    }

    public int getTotalSmoked() { return totalSmoked; }
    public void setTotalSmoked(int total) { this.totalSmoked = total; }
    public void incrementTotalSmoked() { this.totalSmoked++; }

    public long getLastSmokeTime() { return lastSmokeTime; }
    public void setLastSmokeTime(long time) { this.lastSmokeTime = time; }

    public int getLungRecoveredHearts() { return lungRecoveredHearts; }
    public void setLungRecoveredHearts(int hearts) {
        this.lungRecoveredHearts = Math.max(0, Math.min(3, hearts));
        markDirty();
    }

    public long getLungRecoverySeconds() { return lungRecoverySeconds; }
    public void setLungRecoverySeconds(long seconds) {
        this.lungRecoverySeconds = Math.max(0, seconds);
        markDirty();
    }
    public void addLungRecoverySeconds(long seconds) {
        this.lungRecoverySeconds = Math.max(0, this.lungRecoverySeconds + seconds);
        markDirty();
    }
    public void resetLungRecoveryProgress() {
        this.lungRecoverySeconds = 0;
        markDirty();
    }

    public void damageLungRecovery() {
        this.lungRecoverySeconds = 0;
        if (this.lungRecoveredHearts > 0) this.lungRecoveredHearts--;
        markDirty();
    }

    public long getOnlineSoberSeconds() { return onlineSoberSeconds; }
    public void setOnlineSoberSeconds(long seconds) {
        this.onlineSoberSeconds = seconds;
    }
    public void addOnlineSoberSeconds(long seconds) {
        this.onlineSoberSeconds += seconds;
    }
    public void resetOnlineSoberSeconds() {
        this.onlineSoberSeconds = 0;
    }

    public double getTolerance(CigaretteType type) {
        return tolerances.getOrDefault(type.name(), 0.0);
    }
    public void addTolerance(CigaretteType type, double amount) {
        double current = getTolerance(type);
        tolerances.put(type.name(), clamp(current + amount));
    }

    public void setToleranceRaw(String name, double val) {
        tolerances.put(name, val);
    }

    public int getPredisposition(CigaretteType type) {
        return predispositions.getOrDefault(type.name(), 0);
    }
    public void setPredisposition(CigaretteType type, int val) {
        predispositions.put(type.name(), val);
    }
    public void setPredispositionRaw(String name, int val) {
        predispositions.put(name, val);
    }

    public boolean isPredispositionGenerated() {
        return predispositionGenerated;
    }
    public void setPredispositionGenerated(boolean v) {
        this.predispositionGenerated = v;
    }

    public int getRainSmokes() { return rainSmokes; }
    public void setRainSmokes(int val) { this.rainSmokes = val; }
    public int getGroupSmokes() { return groupSmokes; }
    public void setGroupSmokes(int val) { this.groupSmokes = val; }

    public boolean isPreWarningSent() { return preWarningSent; }
    public void setPreWarningSent(boolean val) { this.preWarningSent = val; }

    public double getPhysicalScore() { return physicalScore; }
    public void setPhysicalScore(double val) { this.physicalScore = clampScore(val); }
    public double getPsychologicalScore() { return psychologicalScore; }
    public void setPsychologicalScore(double val) { this.psychologicalScore = clampScore(val); }

    public int getPhysicalStage() { return physicalStage; }
    public void setPhysicalStage(int val) { this.physicalStage = Math.max(0, Math.min(4, val)); }
    public int getPsychologicalStage() { return psychologicalStage; }
    public void setPsychologicalStage(int val) { this.psychologicalStage = Math.max(0, Math.min(4, val)); }

    public long getPhysicalSoberSeconds() { return physicalSoberSeconds; }
    public void setPhysicalSoberSeconds(long val) { this.physicalSoberSeconds = val; }
    public long getPsychologicalSoberSeconds() { return psychologicalSoberSeconds; }
    public void setPsychologicalSoberSeconds(long val) { this.psychologicalSoberSeconds = val; }

    public static double stageThreshold(int stage) {
        Cigaro plugin = Cigaro.getInstance();
        if (plugin != null && plugin.getConfigManager() != null) {
            ConfigManager cfg = plugin.getConfigManager();
            switch (stage) {
                case 1: return cfg.getStage1Threshold();
                case 2: return cfg.getStage2Threshold();
                case 3: return cfg.getStage3Threshold();
                case 4: return cfg.getStage4Threshold();
                default: return 0.0;
            }
        }
        return switch (stage) {
            case 1 -> 5.0;
            case 2 -> 15.0;
            case 3 -> 35.0;
            case 4 -> 70.0;
            default -> 0.0;
        };
    }

    private static int stageForScore(double score) {
        if (score >= stageThreshold(4)) return 4;
        if (score >= stageThreshold(3)) return 3;
        if (score >= stageThreshold(2)) return 2;
        if (score >= stageThreshold(1)) return 1;
        return 0;
    }

    private static double clampScore(double v) {
        return Math.max(0.0, Math.min(v, stageThreshold(4)));
    }

    public int getTargetPhysicalStage() {
        return stageForScore(physicalScore);
    }

    public int getTargetPsychologicalStage() {
        return stageForScore(psychologicalScore);
    }

    public boolean checkAndTriggerPhysicalStageUp(Player player) {
        int target = getTargetPhysicalStage();
        if (target > physicalStage) {
            this.physicalStage = target;
            if (target >= 2) this.wasAddicted = true;
            updateAddictionStageColumn();
            player.sendMessage(Cigaro.getInstance().getLanguageManager().get("withdrawal_stage_up_physical_" + target));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
            return true;
        }
        return false;
    }

    public boolean checkAndTriggerPsychologicalStageUp(Player player) {
        int target = getTargetPsychologicalStage();
        if (target > psychologicalStage) {
            this.psychologicalStage = target;
            if (target >= 2) this.wasAddicted = true;
            updateAddictionStageColumn();
            player.sendMessage(Cigaro.getInstance().getLanguageManager().get("withdrawal_stage_up_psychological_" + target));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
            return true;
        }
        return false;
    }

    public void checkAndTriggerPhysicalStageDown(Player player) {
        int target = getTargetPhysicalStage();
        if (target < physicalStage) {
            this.physicalStage = target;
            updateAddictionStageColumn();
            if (player != null) notifyStageDown(player, target);
        }
    }

    public void checkAndTriggerPsychologicalStageDown(Player player) {
        int target = getTargetPsychologicalStage();
        if (target < psychologicalStage) {
            this.psychologicalStage = target;
            updateAddictionStageColumn();
            if (player != null) notifyStageDown(player, target);
        }
    }

    private void notifyStageDown(Player player, int target) {
        Cigaro plugin = Cigaro.getInstance();
        if (plugin == null) return;

        if (getAddictionStage() == 0) {
            player.sendMessage(plugin.getLanguageManager().get("withdrawal_free"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        } else {
            player.sendMessage(plugin.getLanguageManager().get("withdrawal_eased"));
        }
    }

    public static double scoreDecayPerHour() {
        Cigaro plugin = Cigaro.getInstance();
        int quitHours = 4;
        if (plugin != null && plugin.getConfigManager() != null) {
            quitHours = Math.max(1, plugin.getConfigManager().getQuitTimeHours());
        }
        return stageThreshold(4) / (quitHours * 4.0);
    }

    public static double maxScore() {
        return stageThreshold(4);
    }

    private void updateAddictionStageColumn() {
        this.addictionStage = getAddictionStage();
        markDirty();
    }

    public double getMastery() { return mastery; }
    public void setMastery(double v) { this.mastery = Math.min(1.0, v); }
    public void addMastery(double amount) {
        this.mastery = Math.min(1.0, this.mastery + amount);
    }

    public int getSmoked(CigaretteType type) {
        return smokedCounts.getOrDefault(type.name(), 0);
    }
    public void incrementSmoked(CigaretteType type) {
        smokedCounts.put(type.name(), getSmoked(type) + 1);
    }
    public void setSmokedRaw(String name, int val) {
        smokedCounts.put(name, val);
    }

    public long getLastSessionStart() { return lastSessionStart; }
    public void setLastSessionStart(long v) { this.lastSessionStart = v; }
    public int getSmokedThisSession() { return smokedThisSession; }
    public void setSmokedThisSession(int v) { this.smokedThisSession = v; }
    public int getConsecutiveDays() { return consecutiveDays; }
    public void setConsecutiveDays(int v) { this.consecutiveDays = v; }
    public long getLastDaySmoked() { return lastDaySmoked; }
    public void setLastDaySmoked(long v) { this.lastDaySmoked = v; }

    public String getLastSmokedType() { return lastSmokedType; }
    public void setLastSmokedType(String v) { this.lastSmokedType = v; }
    public int getSameTypeStreak() { return sameTypeStreak; }
    public void setSameTypeStreak(int v) { this.sameTypeStreak = v; }

    public long getPatchAppliedTime() { return patchAppliedTime; }
    public void setPatchAppliedTime(long time) {
        this.patchAppliedTime = time;
    }

    public boolean isPatchActive() {
        if (patchAppliedTime <= 0) return false;
        return System.currentTimeMillis() - patchAppliedTime < PATCH_DURATION_MS;
    }

    public long getPatchRemainingMs() {
        if (!isPatchActive()) return 0;
        return PATCH_DURATION_MS - (System.currentTimeMillis() - patchAppliedTime);
    }

    public boolean wasAddicted() { return wasAddicted; }
    public void setWasAddicted(boolean v) { this.wasAddicted = v; }

    public long getAchievements() { return achievements; }
    public void setAchievements(long v) { this.achievements = v; }

    public String serializeAddonData() {
        JsonObject root = new JsonObject();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(tolerances.keySet());
        allKeys.addAll(predispositions.keySet());
        allKeys.addAll(smokedCounts.keySet());

        for (String key : allKeys) {
            if (VANILLA_TYPES.contains(key)) continue;

            JsonObject entry = new JsonObject();
            Double tol = tolerances.get(key);
            if (tol != null) entry.addProperty("tol", tol);
            Integer pred = predispositions.get(key);
            if (pred != null) entry.addProperty("pred", pred);
            Integer smk = smokedCounts.get(key);
            if (smk != null) entry.addProperty("smk", smk);

            root.add(key, entry);
        }

        root.addProperty("rain_smk", rainSmokes);
        root.addProperty("grp_smk", groupSmokes);

        root.addProperty("phy_sc", physicalScore);
        root.addProperty("psy_sc", psychologicalScore);
        root.addProperty("phy_stg", physicalStage);
        root.addProperty("psy_stg", psychologicalStage);
        root.addProperty("phy_sob", physicalSoberSeconds);
        root.addProperty("psy_sob", psychologicalSoberSeconds);
        root.addProperty("lung_h", lungRecoveredHearts);
        root.addProperty("lung_sec", lungRecoverySeconds);

        return root.toString();
    }

    public void deserializeAddonData(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("rain_smk")) rainSmokes = root.get("rain_smk").getAsInt();
            if (root.has("grp_smk")) groupSmokes = root.get("grp_smk").getAsInt();

            if (root.has("phy_sc")) physicalScore = root.get("phy_sc").getAsDouble();
            if (root.has("psy_sc")) psychologicalScore = root.get("psy_sc").getAsDouble();
            if (root.has("phy_stg")) physicalStage = root.get("phy_stg").getAsInt();
            if (root.has("psy_stg")) psychologicalStage = root.get("psy_stg").getAsInt();
            if (root.has("phy_sob")) physicalSoberSeconds = root.get("phy_sob").getAsLong();
            if (root.has("psy_sob")) psychologicalSoberSeconds = root.get("psy_sob").getAsLong();
            if (root.has("lung_h")) lungRecoveredHearts = Math.max(0, Math.min(3, root.get("lung_h").getAsInt()));
            if (root.has("lung_sec")) lungRecoverySeconds = Math.max(0, root.get("lung_sec").getAsLong());

            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                String key = entry.getKey();

                if (key.equals("rain_smk") || key.equals("grp_smk") || key.equals("phy_sc") ||
                        key.equals("psy_sc") || key.equals("phy_stg") || key.equals("psy_stg") ||
                        key.equals("phy_sob") || key.equals("psy_sob") ||
                        key.equals("lung_h") || key.equals("lung_sec")) continue;

                if (!entry.getValue().isJsonObject()) continue;
                JsonObject data = entry.getValue().getAsJsonObject();

                if (data.has("tol")) tolerances.put(key, data.get("tol").getAsDouble());
                if (data.has("pred")) predispositions.put(key, data.get("pred").getAsInt());
                if (data.has("smk")) smokedCounts.put(key, data.get("smk").getAsInt());
            }
        } catch (Exception ignored) {
        }
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

